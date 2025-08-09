package be.heyman.android.ai.kikko.forge

import android.content.Context
import android.util.Log
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * BOURDON'S NOTE (REFORGED V4 - STREAMING ENABLED):
 * Helper pour Gemini Nano, maintenant doté d'une capacité de streaming via Kotlin Flows.
 */
object NanoLlmHelper {

    private const val TAG = "NanoLlmHelper"
    private var generativeModel: GenerativeModel? = null

    /**
     * Tente d'initialiser le modèle Gemini Nano.
     */
    suspend fun isNanoAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (generativeModel != null) return@withContext true
        try {
            Log.i(TAG, "Tentative d'initialisation de Gemini Nano via le SDK AICore...")
            generativeModel = GenerativeModel(
                generationConfig {
                    this.context = context.applicationContext
                    temperature = 0.7f
                    topK = 40
                }
            )
            generativeModel?.prepareInferenceEngine()
            Log.i(TAG, "SUCCÈS : Gemini Nano est disponible et initialisé.")
            return@withContext true
        } catch (e: Throwable) {
            Log.e(TAG, "ÉCHEC : Gemini Nano n'est pas disponible sur cet appareil. Erreur: ${e.message}", e)
            generativeModel = null
            return@withContext false
        }
    }

    /**
     * Génère une réponse complète en une seule fois.
     */
    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext "Erreur: Modèle Gemini Nano non initialisé."
        try {
            val response = model.generateContent(prompt)
            return@withContext response.text ?: "La Reine Nano est restée silencieuse."
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la génération de la réponse Nano.", e)
            return@withContext "Erreur de la Reine Nano : ${e.message}"
        }
    }

    /**
     * BOURDON'S ADDITION: Génère une réponse en streaming via un Flow Kotlin.
     * C'est la fonction que le ViewModel de l'Audience Royale va collecter.
     */
    fun generateResponseStream(prompt: String): Flow<String> {
        val model = generativeModel ?: throw IllegalStateException("Erreur: Modèle Gemini Nano non initialisé pour le streaming.")

        return model.generateContentStream(prompt)
            .map { response -> response.text ?: "" }
            .catch { e ->
                Log.e(TAG, "Erreur lors du streaming de la réponse Nano.", e)
                emit("Erreur de la Reine Nano : ${e.message}")
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Libère les ressources du modèle.
     */
    fun cleanUp() {
        try {
            generativeModel?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la fermeture du modèle Nano.", e)
        }
        generativeModel = null
        Log.i(TAG, "Les ressources de Gemini Nano ont été libérées.")
    }
}