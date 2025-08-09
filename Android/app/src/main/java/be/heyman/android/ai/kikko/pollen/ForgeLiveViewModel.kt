// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/ForgeLiveViewModel.kt ---

// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/ForgeLiveViewModel.kt ---

package be.heyman.android.ai.kikko.pollen

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.TtsService
import be.heyman.android.ai.kikko.ToolsDialogFragment
import be.heyman.android.ai.kikko.forge.NanoLlmHelper
import be.heyman.android.ai.kikko.model.PollenGrain as DbPollenGrain
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import be.heyman.android.ai.kikko.worker.ForgeWorker
import com.google.gson.Gson
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID

enum class HarvestStep {
    IDLE,
    BOURDON_INTRO,
    USER_CAPTURING_POLLEN,
    AWAITING_FINAL_CHOICE,
    SAVING,
    SAVED
}

data class ForgeLiveUiState(
    val currentStep: HarvestStep = HarvestStep.IDLE,
    val userIntent: String = "",
    val capturedPollen: List<PollenCapture> = emptyList(),
    val bourdonMessage: String? = null,
    val canFinishHarvest: Boolean = false
)

class ForgeLiveViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ForgeLiveViewModel"

    private val _uiState = MutableStateFlow(ForgeLiveUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Unit>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val pollenGrainDao = PollenGrainDao(application)
    private val gson = Gson()
    private val workManager = WorkManager.getInstance(application)

    private var analysisJob: Job? = null


    fun startInteraction() {
        if (_uiState.value.currentStep != HarvestStep.IDLE) return
        Log.i(TAG, "[FLUX] Démarrage de l'interaction. Passage à BOURDON_INTRO.")
        _uiState.update {
            it.copy(
                currentStep = HarvestStep.BOURDON_INTRO,
                bourdonMessage = getApplication<Application>().getString(R.string.bourdon_welcome_capture)
            )
        }
    }

    fun onBourdonFinishedSpeaking() {
        _uiState.update {
            val nextStep = when (it.currentStep) {
                HarvestStep.BOURDON_INTRO -> HarvestStep.USER_CAPTURING_POLLEN
                else -> it.currentStep
            }
            Log.i(TAG, "[FLUX] Le Bourdon a fini de parler (message de bienvenue). Passage à l'état: $nextStep")
            it.copy(currentStep = nextStep, bourdonMessage = null)
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        // This helper should only be used for UI previews.
        val yuvToRgbConverter = YuvToRgbConverter(getApplication())
        if (image.image == null) return null
        return when (image.format) {
            ImageFormat.YUV_420_888 -> {
                val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                yuvToRgbConverter.yuvToRgb(image.image!!, bitmap)
                val matrix = Matrix()
                matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val matrix = Matrix()
                matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
            else -> null
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun startPollenAnalysis(imageProxy: ImageProxy, pollenForge: PollenForge) {
        if (_uiState.value.capturedPollen.size >= 4) {
            Log.w(TAG, "[ANALYSE] Tentative de capture alors que le maximum (4) est atteint. Ignoré.")
            imageProxy.close()
            return
        }

        TtsService.stopAndClearQueue()

        // Create a bitmap for UI preview ONLY. This bitmap is not passed to the forge.
        val bitmapForUi = imageProxyToBitmap(imageProxy)
        if (bitmapForUi == null) {
            Log.e(TAG, "Failed to create bitmap for UI. Aborting analysis.")
            imageProxy.close()
            return
        }

        val newCapture = PollenCapture(bitmap = bitmapForUi)
        _uiState.update { it.copy(capturedPollen = it.capturedPollen + newCapture, bourdonMessage = "Analyse de l'essaim en cours...") }
        Log.i(TAG, "[ANALYSE] Nouvelle capture ajoutée (ID: ${newCapture.id}). Lancement de l'analyse.")

        analysisJob = viewModelScope.launch {
            try {
                // The heavy lifting is now done in a single call to the forge.
                val (report, jsonReport) = pollenForge.processImage(imageProxy)
                Log.i(TAG, "[ANALYSE] Analyse de l'essaim terminée pour la capture ID: ${newCapture.id}.")

                _uiState.update { currentState ->
                    val updatedList = currentState.capturedPollen.map { capture ->
                        if (capture.id == newCapture.id) {
                            capture.copy(status = PollenAnalysisStatus.DONE, report = report, jsonReport = jsonReport)
                        } else {
                            capture
                        }
                    }

                    val canFinish = updatedList.isNotEmpty()
                    val shouldStopCapture = updatedList.size >= 4 && currentState.currentStep == HarvestStep.USER_CAPTURING_POLLEN
                    val nanoDescription = report.nanoImageDescription

                    if (nanoDescription != null && nanoDescription.isNotBlank() && !nanoDescription.contains("Erreur")) {
                        TtsService.speak(nanoDescription, Locale.getDefault())
                    }

                    if (shouldStopCapture) {
                        Log.i(TAG, "[FLUX] Maximum de captures atteint. Passage à AWAITING_FINAL_CHOICE.")
                        currentState.copy(
                            capturedPollen = updatedList,
                            currentStep = HarvestStep.AWAITING_FINAL_CHOICE,
                            bourdonMessage = nanoDescription ?: getApplication<Application>().getString(R.string.bourdon_harvest_complete),
                            canFinishHarvest = canFinish
                        )
                    } else {
                        currentState.copy(
                            capturedPollen = updatedList,
                            bourdonMessage = nanoDescription,
                            canFinishHarvest = canFinish
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during pollen analysis for ${newCapture.id}", e)
                // Handle error state in UI
            } finally {
                // CRITICAL: Ensure the ImageProxy is always closed after use.
                imageProxy.close()
                Log.d(TAG, "ImageProxy for capture ${newCapture.id} closed.")
            }
        }
    }

    fun onStopHarvesting() {
        Log.i(TAG, "[FLUX] L'utilisateur a terminé la récolte manuellement. Passage à AWAITING_FINAL_CHOICE.")
        analysisJob?.cancel()
        TtsService.stopAndClearQueue()
        _uiState.update {
            it.copy(
                currentStep = HarvestStep.AWAITING_FINAL_CHOICE,
                bourdonMessage = getApplication<Application>().getString(R.string.bourdon_harvest_complete_alt)
            )
        }
    }

    fun onRestartHarvest() {
        Log.i(TAG, "[FLUX] Réinitialisation de la session de récolte.")
        analysisJob?.cancel()
        TtsService.stopAndClearQueue()
        _uiState.value = ForgeLiveUiState()
        startInteraction()
    }

    fun onSendToHive() {
        val currentState = _uiState.value
        if (currentState.capturedPollen.isEmpty() || currentState.currentStep == HarvestStep.SAVING) return

        viewModelScope.launch {
            Log.i(TAG, "[FLUX] Envoi du pollen à la Ruche. Passage à SAVING.")
            _uiState.update { it.copy(currentStep = HarvestStep.SAVING, bourdonMessage = getApplication<Application>().getString(R.string.bourdon_save_pollen)) }

            val savedImagePaths = savePollenImages(currentState.capturedPollen)

            if (savedImagePaths.isEmpty()) {
                Log.e(TAG, "[FLUX] Erreur critique lors de la sauvegarde des images. Le processus est interrompu.")
                _uiState.update { it.copy(currentStep = HarvestStep.AWAITING_FINAL_CHOICE, bourdonMessage = getApplication<Application>().getString(R.string.bourdon_save_error)) }
                return@launch
            }

            val aggregatedReport = aggregateJsonReports(currentState.capturedPollen)

            val pollenToSave = DbPollenGrain(
                userIntent = null,
                pollenImagePaths = savedImagePaths,
                swarmAnalysisReportJson = aggregatedReport,
                status = PollenStatus.RAW
            )

            Log.i(TAG, "CONTENU DU RAPPORT JSON SAUVEGARDÉ :\n$aggregatedReport")

            pollenGrainDao.insert(pollenToSave)
            Log.i(TAG, "Nouveau PollenGrain (ID: ${pollenToSave.id}) inséré dans la base de données.")

            launchForgeWorker()

            _uiState.update { it.copy(currentStep = HarvestStep.SAVED, bourdonMessage = getApplication<Application>().getString(R.string.bourdon_save_success)) }

            kotlinx.coroutines.delay(2000)
            _navigationEvent.emit(Unit)
        }
    }

    private fun launchForgeWorker() {
        Log.d(TAG, "Lancement du ForgeWorker.")

        val prefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val requiresCharging = prefs.getBoolean(ToolsDialogFragment.KEY_REQUIRE_CHARGING, false)
        val requiresIdle = prefs.getBoolean(ToolsDialogFragment.KEY_REQUIRE_IDLE, false)

        val constraints = Constraints.Builder()
            .setRequiresCharging(requiresCharging)
            .setRequiresDeviceIdle(requiresIdle)
            .build()

        val forgeRequest = OneTimeWorkRequestBuilder<ForgeWorker>()
            .setConstraints(constraints)
            .build()

        workManager.beginUniqueWork(
            "PollenForgeChain",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            forgeRequest
        ).enqueue()

        Log.i(TAG, "ForgeWorker mis en file d'attente avec succès.")
    }

    private suspend fun savePollenImages(captures: List<PollenCapture>): List<String> = withContext(Dispatchers.IO) {
        val imagePaths = mutableListOf<String>()
        val pollenDir = File(getApplication<Application>().filesDir, "pollen_captures")
        if (!pollenDir.exists()) pollenDir.mkdirs()
        Log.d(TAG, "Sauvegarde de ${captures.size} images dans le répertoire: ${pollenDir.absolutePath}")

        captures.forEach { capture ->
            val fileName = "pollen_${UUID.randomUUID()}.png"
            val file = File(pollenDir, fileName)
            try {
                FileOutputStream(file).use { out ->
                    capture.bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    imagePaths.add(file.absolutePath)
                }
            } catch (e: IOException) {
                Log.e("ForgeLiveViewModel", "Erreur lors de la sauvegarde de l'image du pollen: ${file.absolutePath}", e)
                return@withContext emptyList<String>()
            }
        }
        Log.i(TAG, "${imagePaths.size} images sauvegardées avec succès.")
        return@withContext imagePaths
    }

    private fun aggregateJsonReports(captures: List<PollenCapture>): String {
        val allReports = captures.mapNotNull { capture ->
            capture.jsonReport?.let {
                if (it.contains("nano_image_description")) {
                    Log.i(TAG, "[AGGREGATE] La description de l'Abeille Scribe a été trouvée dans le rapport JSON de la capture ${capture.id}.")
                }
                gson.fromJson(it, Map::class.java)
            }
        }
        return gson.toJson(mapOf("reports" to allReports))
    }

    fun reset() {
        Log.i(TAG, "ViewModel réinitialisé à son état initial.")
        analysisJob?.cancel()
        TtsService.stopAndClearQueue()
        _uiState.value = ForgeLiveUiState()
    }
}

// --- END OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/ForgeLiveViewModel.kt ---