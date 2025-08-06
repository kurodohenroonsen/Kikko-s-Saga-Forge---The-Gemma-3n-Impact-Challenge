package be.heyman.android.ai.kikko.forge

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import be.heyman.android.ai.kikko.model.ImageAnalysisReport
import be.heyman.android.ai.kikko.model.OcrResult
import be.heyman.android.ai.kikko.model.SimpleDetectedObject
import be.heyman.android.ai.kikko.model.SimpleImageLabel
import be.heyman.android.ai.kikko.model.SwarmAnalysisResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.LocalModel
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Helper pour orchestrer les analyses ML Kit sur une ou plusieurs images pour la Forge.
 * Déplacé depuis le package `debug` car c'est une fonctionnalité essentielle pour la Forge.
 * En tant qu'objet singleton, il n'a pas besoin d'injection Hilt pour son propre fonctionnement.
 */
object ForgeMlKitHelper {
    private const val TAG = "ForgeMlKitHelper"

    const val OCR = "ocr"
    const val OBJECT_DETECTION = "object_detection"
    const val DEFAULT_LABELING = "default_labeling"
    const val BIRDS_CLASSIFIER = "birds_classifier"
    const val INSECTS_CLASSIFIER = "insects_classifier"
    const val PLANTS_CLASSIFIER = "plants_classifier"
    const val FOOD_CLASSIFIER = "food_classifier"
    const val MOBILENET_V1 = "mobilenet_v1"
    const val EFFICIENTNET_LITE0 = "efficientnet_lite0"
    const val EFFICIENTNET_LITE1 = "efficientnet_lite1"
    const val EFFICIENTNET_LITE2 = "efficientnet_lite2"
    const val DEEP_OBJECT_ANALYSIS = "deep_object_analysis"

    /**
     * Exécute une analyse ML Kit complexe sur une liste de bitmaps.
     * Cette fonction agrège les résultats de plusieurs détecteurs/classifieurs.
     *
     * @param context Contexte de l'application.
     * @param images La liste des bitmaps à analyser.
     * @param enabledModels Une map des modèles à activer (ex: "ocr" -> true).
     * @param onResult Callback pour le résultat agrégé de l'essaim.
     */
    fun runAnalysis(
        context: Context, // Contexte nécessaire pour créer les clients ML Kit
        images: List<Bitmap>,
        enabledModels: Map<String, Boolean>,
        onResult: (SwarmAnalysisResult) -> Unit
    ) {
        Log.d(TAG, "Lancement de l'analyse de l'essaim pour ${images.size} images. Modèles activés: ${enabledModels.filter { it.value }.keys}")
        CoroutineScope(Dispatchers.Default).launch {
            val imageReports = mutableListOf<ImageAnalysisReport>()

            images.forEachIndexed { index, bitmap ->
                Log.d(TAG, "Analyse de l'image ${index + 1}/${images.size} de l'essaim...")
                val report = processSingleImage(context, bitmap, enabledModels)
                imageReports.add(report)
            }

            val swarmResult = SwarmAnalysisResult(reports = imageReports)
            Log.i(TAG, "Analyse complète de l'essaim terminée.")

            withContext(Dispatchers.Main) {
                onResult(swarmResult)
            }
        }
    }

    private suspend fun processSingleImage(context: Context, bitmap: Bitmap, enabledModels: Map<String, Boolean>): ImageAnalysisReport {
        Log.d(TAG, "Traitement d'une seule image...")
        val clientsToClose = mutableListOf<Closeable>()
        var detectedObjects = emptyList<SimpleDetectedObject>()
        val classifierResults = mutableMapOf<String, List<SimpleImageLabel>>()
        var ocrText = ""

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val tasks = mutableListOf<com.google.android.gms.tasks.Task<*>>()
            val taskNames = mutableListOf<String>()

            if (enabledModels[OCR] == true) {
                Log.d(TAG, "Ajout de la tâche OCR à l'essaim.")
                // Le contexte est passé à la méthode `getClient`
                val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                clientsToClose.add(recognizer)
                tasks.add(recognizer.process(image))
                taskNames.add(OCR)
            }
            if (enabledModels[OBJECT_DETECTION] == true) {
                Log.d(TAG, "Ajout de la tâche de Détection d'Objets à l'essaim.")
                val options = ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE).enableMultipleObjects().enableClassification().build()
                // Le contexte est passé à la méthode `getClient`
                val detector = ObjectDetection.getClient(options)
                clientsToClose.add(detector)
                tasks.add(detector.process(image))
                taskNames.add(OBJECT_DETECTION)
            }
            if (enabledModels[DEFAULT_LABELING] == true) {
                Log.d(TAG, "Ajout de la tâche de Labellisation par Défaut à l'essaim.")
                // Le contexte est passé à la méthode `getClient`
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                clientsToClose.add(labeler)
                tasks.add(labeler.process(image))
                taskNames.add("Labels (Défaut)")
            }

            // Les fonctions `addCustomClassifierTask` prennent déjà le contexte.
            addCustomClassifierTask(context, enabledModels, BIRDS_CLASSIFIER, "aiy-tflite-vision-classifier-birds-v1-v3.tflite", image, tasks, taskNames, clientsToClose)
            addCustomClassifierTask(context, enabledModels, INSECTS_CLASSIFIER, "aiy-tflite-vision-classifier-insects-v1-v3.tflite", image, tasks, taskNames, clientsToClose)
            addCustomClassifierTask(context, enabledModels, PLANTS_CLASSIFIER, "aiy-tflite-vision-classifier-plants-v1-v3.tflite", image, tasks, taskNames, clientsToClose)
            addCustomClassifierTask(context, enabledModels, FOOD_CLASSIFIER, "aiy-tflite-vision-classifier-food-v1-v1.tflite", image, tasks, taskNames, clientsToClose)
            addCustomClassifierTask(context, enabledModels, MOBILENET_V1, "mobilenet_v1_1.0_224_quantized_1_metadata_1.tflite", image, tasks, taskNames, clientsToClose)
            addCustomClassifierTask(context, enabledModels, EFFICIENTNET_LITE0, "efficientnet_lite0.tflite", image, tasks, taskNames, clientsToClose)
            addCustomClassifierTask(context, enabledModels, EFFICIENTNET_LITE1, "efficientnet_lite1_int8_2.tflite", image, tasks, taskNames, clientsToClose)
            addCustomClassifierTask(context, enabledModels, EFFICIENTNET_LITE2, "efficientnet_lite2_int8_2.tflite", image, tasks, taskNames, clientsToClose)


            if (tasks.isNotEmpty()) {
                val results = Tasks.await(Tasks.whenAllSuccess<Any>(tasks))
                Log.i(TAG, "${results.size} tâches ML Kit terminées avec succès.")

                results.forEachIndexed { index, result ->
                    val taskName = taskNames[index]
                    when (result) {
                        is Text -> ocrText = result.text
                        is List<*> -> {
                            if (result.all { it is DetectedObject }) {
                                detectedObjects = (result as List<DetectedObject>).map { obj ->
                                    SimpleDetectedObject(labels = obj.labels.map { SimpleImageLabel(it.text, it.confidence) })
                                }
                            } else if (result.all { it is ImageLabel }) {
                                classifierResults[taskName] = (result as List<ImageLabel>).map { SimpleImageLabel(it.text, it.confidence) }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "L'une des tâches ML a échoué en mode complexe", e)
        } finally {
            Log.d(TAG, "Nettoyage de ${clientsToClose.size} clients ML Kit.")
            clientsToClose.forEach { it.close() }
        }

        // BOURDON'S FIX: On encapsule le String OCR dans l'objet OcrResult attendu par le modèle de données.
        return ImageAnalysisReport(detectedObjects, classifierResults, OcrResult(fullText = ocrText))
    }

    private fun addCustomClassifierTask(
        context: Context, // Contexte ajouté ici pour créer les clients
        enabledModels: Map<String, Boolean>,
        modelKey: String,
        assetPath: String,
        image: InputImage,
        tasks: MutableList<com.google.android.gms.tasks.Task<*>>,
        taskNames: MutableList<String>,
        clientsToClose: MutableList<Closeable>
    ) {
        if (enabledModels[modelKey] == true) {
            Log.d(TAG, "Ajout de la tâche du classifieur personnalisé '$modelKey' à l'essaim.")
            val localModel = LocalModel.Builder().setAssetFilePath(assetPath).build()
            val options = CustomImageLabelerOptions.Builder(localModel).setConfidenceThreshold(0.1f).build()
            // Le contexte est passé à la méthode `getClient`
            val labeler = ImageLabeling.getClient(options)
            clientsToClose.add(labeler)
            tasks.add(labeler.process(image))
            taskNames.add(modelKey.replace("_classifier", "").replaceFirstChar { it.titlecase() })
        }
    }
}