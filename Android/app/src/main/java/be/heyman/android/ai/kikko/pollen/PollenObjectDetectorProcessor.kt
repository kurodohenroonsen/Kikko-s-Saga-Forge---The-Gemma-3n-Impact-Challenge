package be.heyman.android.ai.kikko.pollen

import android.content.Context
import android.util.Log
import be.heyman.android.ai.kikko.pollen.vision.GraphicOverlay
import be.heyman.android.ai.kikko.pollen.vision.VisionProcessorBase
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * Un processeur qui utilise le DÉTECTEUR D'OBJETS PAR DÉFAUT de ML Kit.
 * C'est la version corrigée qui est capable de fournir des boîtes englobantes.
 */
class PollenObjectDetectorProcessor(context: Context) : VisionProcessorBase<List<DetectedObject>>(context) {

    private val objectDetector: ObjectDetector

    init {
        // Configure l'ObjectDetector pour le mode streaming (caméra en direct),
        // en activant la détection de plusieurs objets et leur classification.
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        objectDetector = ObjectDetection.getClient(options)
    }

    override fun stop() {
        super.stop()
        objectDetector.close()
    }

    override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
        return objectDetector.process(image)
    }

    override fun onSuccess(results: List<DetectedObject>, graphicOverlay: GraphicOverlay) {
        for (detectedObject in results) {
            graphicOverlay.add(PollenObjectGraphic(graphicOverlay, detectedObject))
        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "La détection d'objet a échoué.", e)
    }

    companion object {
        private const val TAG = "ObjectDetectorProcessor"
    }
}