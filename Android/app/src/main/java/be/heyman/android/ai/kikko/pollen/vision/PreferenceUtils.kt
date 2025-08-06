package be.heyman.android.ai.kikko.pollen.vision

import android.content.Context
import android.preference.PreferenceManager
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions // Corrigé: Import ajouté

/**
 * Fonctions utilitaires pour accéder aux préférences partagées liées à la caméra et à ML Kit.
 */
object PreferenceUtils {

    fun isClassificationEnabled(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "pref_key_object_detector_enable_classification"
        return sharedPreferences.getBoolean(key, true)
    }

    fun getObjectDetectorOptionsForLivePreview(context: Context): ObjectDetectorOptions {
        return ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    }
}