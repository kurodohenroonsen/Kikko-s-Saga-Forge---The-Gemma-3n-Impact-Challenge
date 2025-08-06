// app/src/main/java/be/heyman/android/ai/kikko/clash/helpers/ClashLlmHelper.kt

package be.heyman.android.ai.kikko.clash.helpers

import android.content.Context
import android.util.Log
import be.heyman.android.ai.kikko.data.Accelerator
import be.heyman.android.ai.kikko.data.Model
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import javax.inject.Inject

private const val TAG = "ClashLlmHelper"

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit

class ClashLlmHelper @Inject constructor(
    val context: Context
) {
    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var currentSessionTemperature: Float? = null

    fun initialize(model: Model, accelerator: String): String? {
        Log.d(TAG, "INITIALISATION ...")
        cleanUp()
        try {
            Log.d(TAG, "INITIALISATION BRUTE DU MOTEUR...")
            val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.url)
                .setMaxTokens(4096)
                .setPreferredBackend(
                    if (accelerator == Accelerator.GPU.label) LlmInference.Backend.GPU
                    else LlmInference.Backend.CPU
                )
            val options = optionsBuilder.build()
            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "MOTEUR CRÉÉ AVEC SUCCÈS.")

            // On crée la session initiale IMMÉDIATEMENT.
            session = createNewSession(0.44f) // Température par défaut
            if (session == null) {
                throw IllegalStateException("Échec de la création de la session LlmInference.")
            }
            Log.d(TAG, "SESSION CRÉÉE AVEC SUCCÈS.")
            return null // Succès
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Erreur inconnue pendant l'initialisation"
            Log.e(TAG, "L'initialisation du ClashLlmHelper a échoué: $errorMsg", e)
            cleanUp()
            return errorMsg
        }
    }

    private fun createNewSession(temperature: Float): LlmInferenceSession? {
        val inferenceEngine = llmInference ?: return null

        return try {
            Log.d(TAG, "Création d'une nouvelle session avec température: $temperature")
            currentSessionTemperature = temperature

            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(temperature)
                .setTopK(40)
                .setGraphOptions(
                    GraphOptions.builder()
                        .setEnableVisionModality(false)
                        .build()
                )
                .build()

            LlmInferenceSession.createFromOptions(inferenceEngine, sessionOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Échec de la création de la nouvelle session", e)
            null
        }
    }

    fun resetSession() {
        Log.d(TAG, "Réinitialisation de la session du Juge.")
        session?.close()
        session = createNewSession(currentSessionTemperature ?: 0.44f)
    }

    fun generateResponse(
        prompt: String,
        temperature: Float,
        resultListener: ResultListener
    ) {
        if (currentSessionTemperature != temperature) {
            session?.close()
            session = createNewSession(temperature)
        }

        val currentSession = session ?: run {
            val errorMsg = "Erreur Critique: La session est nulle."
            Log.e(TAG, errorMsg)
            resultListener(errorMsg, true)
            return
        }

        try {
            currentSession.addQueryChunk(prompt)
            currentSession.generateResponseAsync(resultListener)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur durant l'inférence dans le Clash", e)
            resultListener(e.message ?: "Error during inference", true)
        }
    }

    fun cleanUp() {
        session?.close()
        llmInference?.close()
        session = null
        llmInference = null
        currentSessionTemperature = null
    }
}