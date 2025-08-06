package be.heyman.android.ai.kikko.pollen

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/**
 * Orchestre une analyse multi-niveaux avec une escouade complète de classifieurs spécialisés,
 * un OCR et un scanner de codes-barres.
 * BOURDON'S REFACTOR: Cette version est maintenant robuste et charge les modèles séquentiellement.
 */
class PollenForge(private val context: Context) {

    private val TAG = "PollenForge"

    private val specialistModelPaths = mapOf(
        "Défaut ML Kit" to "DEFAULT", // Cas spécial pour le modèle par défaut
        "Labeler Objet" to "object_labeler.tflite",
        "Plantes" to "aiy-tflite-vision-classifier-plants-v1-v3.tflite",
        "Insectes" to "aiy-tflite-vision-classifier-insects-v1-v3.tflite",
        "Oiseaux" to "aiy-tflite-vision-classifier-birds-v1-v3.tflite",
        "Nourriture" to "aiy-tflite-vision-classifier-food-v1-v1.tflite",
        "EfficientNet-L0" to "efficientnet_lite0.tflite",
        "EfficientNet-L1" to "efficientnet_lite1_int8_2.tflite",
        "EfficientNet-L2" to "efficientnet_lite2_int8_2.tflite",
        "MobileNetV1" to "mobilenet_v1_1.0_224_quantized_1_metadata_1.tflite"
    )

    private fun createImageLabeler(modelPath: String): ImageLabeler {
        return if (modelPath == "DEFAULT") {
            ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        } else {
            val localModel = LocalModel.Builder().setAssetFilePath(modelPath).build()
            val options = CustomImageLabelerOptions.Builder(localModel)
                .setConfidenceThreshold(0.1f)
                .setMaxResultCount(4)
                .build()
            ImageLabeling.getClient(options)
        }
    }

    suspend fun processImage(bitmap: Bitmap): Pair<PollenAnalysis, String> = coroutineScope {
        val highResImage = InputImage.fromBitmap(bitmap, 0)
        var objectDetector: com.google.mlkit.vision.objects.ObjectDetector? = null
        var textRecognizer: com.google.mlkit.vision.text.TextRecognizer? = null
        var barcodeScanner: BarcodeScanner? = null
        val closableClients = mutableListOf<Closeable>()

        try {
            // --- Étape 1: Détection d'objets ---
            val objectDetectorOptions = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects().enableClassification().build()
            objectDetector = ObjectDetection.getClient(objectDetectorOptions)
            closableClients.add(objectDetector)
            val detectedObjects = objectDetector.process(highResImage).await()
            Log.d(TAG, "Phase 1: ${detectedObjects.size} objets détectés.")

            // --- Étape 2: Analyse Globale (Spécialistes Séquentiels + OCR + Barcode) ---
            val globalSpecialistResults = mutableListOf<Pair<String, List<ImageLabel>>>()
            for ((name, path) in specialistModelPaths) {
                Log.d(TAG, "Analyse globale avec le spécialiste: $name")
                val labeler = createImageLabeler(path)
                try {
                    val labels = labeler.process(highResImage).await()
                    globalSpecialistResults.add(Pair(name, labels))
                } finally {
                    labeler.close() // Libère les ressources immédiatement
                }
            }

            textRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            closableClients.add(textRecognizer)
            barcodeScanner = BarcodeScanning.getClient()
            closableClients.add(barcodeScanner)

            val globalOcrResult = textRecognizer.process(highResImage).await()
            val barcodeResults = barcodeScanner.process(highResImage).await()
            Log.d(TAG, "Phase 2: Analyse globale terminée.")

            // --- Étape 3: Analyse par Objet (reportée pour la stabilité) ---
            // Pour l'instant, on se concentre sur la stabilisation de l'analyse globale.
            Log.d(TAG, "Phase 3: Analyse par objet désactivée pour cette version.")
            val analyzedObjectsResults = emptyList<AnalyzedObject>()

            // --- Étape 4: Construction des rapports ---
            val pollenAnalysisReport = PollenAnalysis(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                globalAnalysis = globalSpecialistResults.map { SpecialistReport(it.first, it.second.toClassifierResults()) }.filter { it.results.isNotEmpty() },
                analyzedObjects = analyzedObjectsResults,
                structuredOcrResult = globalOcrResult.toStructuredResult(),
                barcodeResults = barcodeResults.map { BarcodeResult(it.displayValue, it.format.toString()) }
            )

            val jsonReport = generateJsonReport(bitmap, barcodeResults, globalOcrResult, detectedObjects, globalSpecialistResults)

            return@coroutineScope Pair(pollenAnalysisReport, jsonReport)

        } finally {
            // Assure que tous les clients principaux sont fermés
            closableClients.forEach {
                try { it.close() } catch (e: Exception) { Log.e(TAG, "Erreur lors de la fermeture d'un client ML Kit.", e) }
            }
        }
    }

    private fun List<ImageLabel>.toClassifierResults(): List<ClassifierResult> {
        return this.map { ClassifierResult(it.text, it.confidence) }
    }

    private fun Text.toStructuredResult(): List<OcrBlock> {
        return this.textBlocks.map { block ->
            OcrBlock(
                text = block.text,
                boundingBox = block.boundingBox,
                lines = block.lines.map { line ->
                    OcrLine(
                        text = line.text,
                        boundingBox = line.boundingBox,
                        elements = line.elements.map { element ->
                            OcrElement(
                                text = element.text,
                                boundingBox = element.boundingBox
                            )
                        }
                    )
                }
            )
        }
    }

    private fun generateJsonReport(
        bitmap: Bitmap,
        barcodes: List<Barcode>,
        ocrResult: Text,
        detectedObjects: List<DetectedObject>,
        specialistResults: List<Pair<String, List<ImageLabel>>>
    ): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val reportMap = mutableMapOf<String, Any>()

        reportMap["image_dimensions"] = mapOf("width" to bitmap.width, "height" to bitmap.height)
        reportMap["barcode_scanner_results"] = barcodes.map { mapOf("format" to it.format, "raw_value" to it.rawValue) }
        reportMap["ocr_results"] = mapOf("full_text" to ocrResult.text)
        reportMap["object_detection_results"] = detectedObjects.map { obj ->
            mapOf(
                "labels" to obj.labels.map { label -> mapOf("text" to label.text, "confidence" to label.confidence) }
            )
        }
        reportMap["global_classification_results"] = specialistResults.associate { (name, labels) ->
            name to labels.map { mapOf("label" to it.text, "confidence" to it.confidence) }
        }

        return gson.toJson(reportMap)
    }
}