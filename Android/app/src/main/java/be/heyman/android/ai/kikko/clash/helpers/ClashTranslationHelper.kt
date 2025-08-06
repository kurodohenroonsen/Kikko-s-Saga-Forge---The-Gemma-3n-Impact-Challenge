package be.heyman.android.ai.kikko.clash.helpers

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

/**
 * Un helper pour encapsuler la logique de traduction de ML Kit pour le Clash.
 */
object ClashTranslationHelper {

    private const val TAG = "ClashTranslationHelper"

    private var translator: Translator? = null
    private var lastOptions: TranslatorOptions? = null
    private val languageIdentifier = LanguageIdentification.getClient()

    /**
     * Traduit un texte donné vers la langue cible spécifiée.
     * Détecte automatiquement la langue source.
     */
    suspend fun translate(textToTranslate: String, targetLanguage: String): String {
        if (textToTranslate.isBlank()) {
            return ""
        }

        return try {
            val languageCode = Tasks.await(languageIdentifier.identifyLanguage(textToTranslate))
            if (languageCode == "und") {
                return "[Erreur: Langue non détectée]"
            }

            if (languageCode == targetLanguage) {
                val langName = Locale.forLanguageTag(languageCode).displayName
                return "[Texte déjà en ${langName}]"
            }

            Log.d(TAG, "Langue détectée: $languageCode, Cible: $targetLanguage")
            executeTranslation(textToTranslate, languageCode, targetLanguage)
        } catch (e: Exception) {
            Log.e(TAG, "L'identification ou la traduction a échoué", e)
            "[Erreur: ${e.message}]"
        }
    }

    private suspend fun executeTranslation(textToTranslate: String, sourceLanguage: String, targetLanguage: String): String {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        // Réutilise ou crée un nouveau traducteur si les options ont changé
        if (lastOptions != options) {
            closeTranslator()
            lastOptions = options
            translator = Translation.getClient(options)
        }
        val localTranslator = translator ?: return "[Erreur: Client de traduction non initialisé]"

        // Télécharge le modèle si nécessaire
        val conditions = DownloadConditions.Builder().requireWifi().build()
        Tasks.await(localTranslator.downloadModelIfNeeded(conditions))
        Log.d(TAG, "Modèle de traduction prêt.")

        // Effectue la traduction
        return Tasks.await(localTranslator.translate(textToTranslate))
    }

    fun closeTranslator() {
        translator?.close()
        translator = null
        lastOptions = null
        Log.d(TAG, "Client de traduction fermé.")
    }
}