package be.heyman.android.ai.kikko.pollen

import android.content.Context
import android.util.Log
import be.heyman.android.ai.kikko.pollen.vision.GraphicOverlay
import be.heyman.android.ai.kikko.pollen.vision.VisionProcessorBase
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
// BOURDON'S FIX: Importation nécessaire pour les options japonaises.
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions

/**
 * Processeur spécialisé dans la reconnaissance de texte.
 * Cette version est configurée pour le japonais.
 */
class PollenTextRecognitionProcessor(
    context: Context
) : VisionProcessorBase<Text>(context) {

    // BOURDON'S FIX: On remplace les options par défaut par les options japonaises.
    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    override fun stop() {
        super.stop()
        textRecognizer.close()
    }

    override fun detectInImage(image: InputImage): Task<Text> {
        return textRecognizer.process(image)
    }

    override fun onSuccess(results: Text, graphicOverlay: GraphicOverlay) {
        graphicOverlay.add(PollenTextGraphic(graphicOverlay, results))
    }

    override fun onFailure(e: Exception) {
        Log.w(TAG, "La reconnaissance de texte a échoué.$e")
    }

    companion object {
        private const val TAG = "TextRecProcessor"
    }
}