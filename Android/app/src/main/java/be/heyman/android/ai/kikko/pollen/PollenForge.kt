// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/PollenForge.kt ---

// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/PollenForge.kt ---

package be.heyman.android.ai.kikko.pollen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.gson.GsonBuilder
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriber
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
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
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.tasks.await
import java.io.Closeable
import java.io.IOException

/**
 * Orchestre une analyse multi-niveaux avec une escouade complète de classifieurs spécialisés.
 * BOURDON'S REFORGE V8: Ajout de logs détaillés et intégration complète des résultats Nano.
 */
class PollenForge(private val context: Context) {

    private val TAG = "PollenForge"

    private val specialistModelPaths = mapOf(
        "Défaut ML Kit" to "DEFAULT",
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

    @SuppressLint("UnsafeOptInUsageError")
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        if (image.image == null) return null
        val yuvToRgbConverter = YuvToRgbConverter(context)
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(image.image!!, bitmap)
        return bitmap
    }

    @SuppressLint("UnsafeOptInUsageError")
    suspend fun processImage(imageProxy: ImageProxy): Pair<PollenAnalysis, String> = coroutineScope {
        val closableClients = mutableListOf<Closeable>()
        var imageDescriber: ImageDescriber? = null
        // Create a single InputImage to be shared safely by all compatible ML Kit Vision APIs
        val inputImage = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        try {
            Log.d(TAG, "Lancement de l'essaim d'analyse en PARALLÈLE avec isolation des ressources...")

            val objectDetectorOptions = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects().enableClassification().build()
            val objectDetector = ObjectDetection.getClient(objectDetectorOptions).also { closableClients.add(it) }
            val objectTask = async(Dispatchers.IO) {
                try {
                    val result = objectDetector.process(inputImage).await()
                    Log.i(TAG, "[ML KIT RESULT - Objects] Detected ${result.size} objects.")
                    result
                }
                catch (e: Exception) { Log.e(TAG, "Object Detection failed", e); emptyList<DetectedObject>() }
            }

            val textRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()).also { closableClients.add(it) }
            val ocrTask = async(Dispatchers.IO) {
                try {
                    val result = textRecognizer.process(inputImage).await()
                    Log.i(TAG, "[ML KIT RESULT - OCR] Full Text length: ${result.text.length}")
                    result
                }
                catch (e: Exception) { Log.e(TAG, "OCR failed", e); null }
            }

            val barcodeScanner = BarcodeScanning.getClient().also { closableClients.add(it) }
            val barcodeTask = async(Dispatchers.IO) {
                try {
                    val result = barcodeScanner.process(inputImage).await()
                    Log.i(TAG, "[ML KIT RESULT - Barcode] Found ${result.size} barcodes.")
                    result
                }
                catch (e: Exception) { Log.e(TAG, "Barcode Scanning failed", e); emptyList<Barcode>() }
            }

            // The ImageDescription API requires a Bitmap, so we convert it here, isolated from other tasks.
            imageDescriber = ImageDescription.getClient(ImageDescriberOptions.builder(context).build())
            val descriptionTask = async(Dispatchers.IO) {
                var bitmapForNano: Bitmap? = null
                try {
                    // Create a local, temporary bitmap only for this task
                    bitmapForNano = imageProxyToBitmap(imageProxy)
                    if (bitmapForNano == null) return@async "Description error: Bitmap conversion failed."

                    val status = imageDescriber!!.checkFeatureStatus().await()
                    if (status == FeatureStatus.AVAILABLE || status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING) {
                        val request = ImageDescriptionRequest.builder(bitmapForNano).build()
                        val descriptionResult = imageDescriber!!.runInference(request).await()
                        Log.i(TAG, "[ML KIT RESULT - Nano Desc] Description generated (length: ${descriptionResult.description.length}).")
                        descriptionResult.description
                    } else { "Description non disponible." }
                } catch (e: Exception) {
                    Log.e(TAG, "Image Description failed", e)
                    "Erreur de description."
                }
            }

            val specialistTasks = specialistModelPaths.map { (name, path) ->
                async(Dispatchers.IO) {
                    val labeler = createImageLabeler(path)
                    try {
                        val labels = labeler.process(inputImage).await()
                        Log.i(TAG, "[ML KIT RESULT - $name] Found ${labels.size} labels. Top: ${labels.firstOrNull()?.text ?: "N/A"}")
                        Pair(name, labels)
                    } catch (e: Exception) {
                        Log.e(TAG, "Classifier '$name' failed", e)
                        Pair(name, emptyList<ImageLabel>())
                    } finally {
                        labeler.close()
                    }
                }
            }

            val detectedObjects = objectTask.await()
            val globalOcrResult = ocrTask.await()
            val barcodeResults = barcodeTask.await()
            val nanoDescription = descriptionTask.await()
            val globalSpecialistResults = awaitAll(*specialistTasks.toTypedArray())
            Log.d(TAG, "Toutes les analyses de l'essaim sont terminées.")

            val pollenAnalysisReport = PollenAnalysis(
                imageWidth = imageProxy.width, imageHeight = imageProxy.height,
                globalAnalysis = globalSpecialistResults.map { SpecialistReport(it.first, it.second.toClassifierResults()) }.filter { it.results.isNotEmpty() },
                analyzedObjects = emptyList(),
                structuredOcrResult = globalOcrResult?.toStructuredResult() ?: emptyList(),
                barcodeResults = barcodeResults.map { BarcodeResult(it.displayValue, it.format.toString()) },
                nanoImageDescription = nanoDescription
            )

            val jsonReport = generateJsonReport(imageProxy, barcodeResults, globalOcrResult, detectedObjects, globalSpecialistResults, nanoDescription)

            return@coroutineScope Pair(pollenAnalysisReport, jsonReport)

        } finally {
            closableClients.forEach {
                try { it.close() } catch (e: Exception) { Log.e(TAG, "Erreur lors de la fermeture d'un client ML Kit.", e) }
            }
            imageDescriber?.close()
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
        imageProxy: ImageProxy,
        barcodes: List<Barcode>,
        ocrResult: Text?,
        detectedObjects: List<DetectedObject>,
        specialistResults: List<Pair<String, List<ImageLabel>>>,
        nanoDescription: String?
    ): String {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val reportMap = mutableMapOf<String, Any>()

        reportMap["image_dimensions"] = mapOf("width" to imageProxy.width, "height" to imageProxy.height)
        reportMap["barcode_scanner_results"] = barcodes.map { mapOf("format" to it.format.toString(), "raw_value" to it.rawValue) }
        reportMap["ocr_results"] = mapOf("full_text" to (ocrResult?.text ?: ""))
        reportMap["nano_image_description"] = nanoDescription ?: ""
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

// --- END OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/PollenForge.kt ---