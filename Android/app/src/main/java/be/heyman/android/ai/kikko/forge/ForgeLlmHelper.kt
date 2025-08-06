package be.heyman.android.ai.kikko.forge

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import be.heyman.android.ai.kikko.data.Accelerator
import be.heyman.android.ai.kikko.data.MAX_IMAGE_COUNT
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.model.ModelConfiguration
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "ForgeLlmHelper"

class ForgeLlmHelper(
    val context: Context,
) {
    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var currentSessionTemperature: Float? = null
    private var currentSessionTopK: Int? = null
    private var currentSessionIsMultimodal: Boolean = false


    fun initialize(model: Model, accelerator: String, isMultimodal: Boolean): String? {
        cleanUp()
        return try {
            Log.d(TAG, "Initialisation LLM avec modèle: ${model.name}, accélérateur: $accelerator, multimodal: $isMultimodal")
            val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.url)
                .setMaxTokens(4096)
                .setPreferredBackend(
                    if (accelerator == Accelerator.GPU.label) LlmInference.Backend.GPU
                    else LlmInference.Backend.CPU
                )
                .setMaxNumImages(if (isMultimodal && model.llmSupportImage) MAX_IMAGE_COUNT else 0)

            val options = optionsBuilder.build()
            llmInference = LlmInference.createFromOptions(context, options)
            // Ne pas créer de session par défaut. Elle sera créée à la demande.
            null
        } catch (e: Exception) {
            cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error during initialization")
        }
    }

    private fun createNewSession(llmInference: LlmInference, temperature: Float, topK: Int, isMultimodal: Boolean): LlmInferenceSession? {
        return try {
            Log.d(TAG, "Création d'une nouvelle session avec température: $temperature, topK: $topK, multimodal: $isMultimodal")
            currentSessionTemperature = temperature
            currentSessionTopK = topK
            currentSessionIsMultimodal = isMultimodal
            val sessionOptionsBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(temperature)
                .setTopK(topK)
            sessionOptionsBuilder.setGraphOptions(
                GraphOptions.builder()
                    .setEnableVisionModality(isMultimodal)
                    .build()
            )
            LlmInferenceSession.createFromOptions(llmInference, sessionOptionsBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create new session", e)
            null
        }
    }

    fun resetSession(model: Model, isMultimodal: Boolean, temperature: Float = 0.2f, topK: Int = 40) {
        try {
            if (session != null && currentSessionTemperature == temperature && currentSessionTopK == topK && currentSessionIsMultimodal == isMultimodal) {
                Log.d(TAG, "Session déjà configurée avec les mêmes paramètres. Réutilisation.")
                return
            }
            Log.d(TAG, "Réinitialisation de la session pour modèle '${model.name}' avec temp: $temperature, topK: $topK, multimodal: $isMultimodal")
            session?.close()
            val inferenceEngine = llmInference ?: run {
                Log.e(TAG, "Cannot reset session, LlmInference engine is null.")
                initialize(model, Accelerator.GPU.label, isMultimodal)
                llmInference ?: return
            }
            session = createNewSession(inferenceEngine, temperature, topK, isMultimodal)
            if (session == null) {
                throw IllegalStateException("Failed to create LlmInferenceSession after reset.")
            }
            Log.d(TAG, "Session réinitialisée avec succès.")
        } catch(e: Exception) {
            Log.e(TAG, "Failed to reset session for '${model.name}': ${e.message}")
            cleanUp()
        }
    }

    private fun ensureSession(config: ModelConfiguration, isMultimodal: Boolean) {
        val inferenceEngine = llmInference ?: throw IllegalStateException("LlmInference engine not initialized.")
        if (session == null || currentSessionTemperature != config.temperature || currentSessionTopK != config.topK || currentSessionIsMultimodal != isMultimodal) {
            Log.d(TAG, "Configuration de session invalide ou inexistante. Création d'une nouvelle session.")
            session?.close()
            session = createNewSession(inferenceEngine, config.temperature, config.topK, isMultimodal)
                ?: throw IllegalStateException("Failed to create new LlmInferenceSession.")
        }
    }

    fun runInference(
        prompt: String,
        images: List<Bitmap>,
        resultListener: (partialResult: String, done: Boolean) -> Unit
    ) {
        val currentSession = session ?: run {
            resultListener("Erreur: Session non initialisée.", true)
            return
        }
        Log.d(TAG, "--- PROMPT ENVOYÉ À LA REINE ---\n$prompt")
        try {
            if (prompt.trim().isNotEmpty()) {
                currentSession.addQueryChunk(prompt)
            }
            if (images.isNotEmpty()) {
                for (image in images) {
                    val mpImage = BitmapImageBuilder(image).build()
                    currentSession.addImage(mpImage)
                }
            }
            currentSession.generateResponseAsync(resultListener)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur durant l'inférence dans la Forge", e)
            resultListener(cleanUpMediapipeTaskErrorMessage(e.message ?: "Error during inference"), true)
        }
    }

    // BOURDON'S FIX: Restauration de la méthode pour les Workers
    suspend fun inferenceWithCoroutineAndConfig(prompt: String, images: List<Bitmap>, config: ModelConfiguration): String {
        ensureSession(config, images.isNotEmpty())
        return suspendCancellableCoroutine { continuation ->
            val responseBuilder = StringBuilder()
            runInference(prompt, images) { partialResult, done ->
                responseBuilder.append(partialResult)
                if (done) {
                    if (continuation.isActive) {
                        continuation.resume(responseBuilder.toString())
                    }
                }
            }
        }
    }

    fun runInferenceWithConfig(
        prompt: String,
        images: List<Bitmap>,
        config: ModelConfiguration,
        resultListener: (partialResult: String, done: Boolean) -> Unit
    ) {
        ensureSession(config, images.isNotEmpty())
        runInference(prompt, images, resultListener)
    }

    fun cleanUp() {
        session?.close()
        llmInference?.close()
        session = null
        llmInference = null
        currentSessionTemperature = null
        currentSessionTopK = null
        currentSessionIsMultimodal = false
    }
}

private fun cleanUpMediapipeTaskErrorMessage(message: String): String {
    val index = message.indexOf("=== Source Location Trace")
    if (index >= 0) {
        return message.substring(0, index).trim()
    }
    return message.trim()
}