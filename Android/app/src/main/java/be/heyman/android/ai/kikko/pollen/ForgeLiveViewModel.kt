// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/ForgeLiveViewModel.kt ---

package be.heyman.android.ai.kikko.pollen

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.ToolsDialogFragment
import be.heyman.android.ai.kikko.model.PollenGrain as DbPollenGrain
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import be.heyman.android.ai.kikko.worker.IdentificationWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

// BOURDON'S REFACTOR: Simplification drastique de la machine à états.
enum class HarvestStep {
    IDLE,                      // L'activité vient de démarrer.
    BOURDON_INTRO,             // Le Bourdon parle pour accueillir.
    USER_CAPTURING_POLLEN,     // La caméra est active pour la capture.
    AWAITING_FINAL_CHOICE,     // L'utilisateur a terminé la capture et doit choisir.
    SAVING,                    // Sauvegarde en cours.
    SAVED                      // Sauvegarde terminée, en attente de sortie.
}

data class ForgeLiveUiState(
    val currentStep: HarvestStep = HarvestStep.IDLE,
    // BOURDON'S REFACTOR: userIntent est maintenant obsolète dans ce flux, mais conservé pour la sauvegarde.
    val userIntent: String = "",
    val capturedPollen: List<PollenCapture> = emptyList(),
    val bourdonMessage: String? = null,
    val canFinishHarvest: Boolean = false
)

class ForgeLiveViewModel(application: Application) : AndroidViewModel(application) {

    // BOURDON'S LOGGING: TAG pour le débogage de ce ViewModel.
    private val TAG = "ForgeLiveViewModel"

    private val _uiState = MutableStateFlow(ForgeLiveUiState())
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Unit>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val pollenGrainDao = PollenGrainDao(application)
    private val gson = Gson()
    private val workManager = WorkManager.getInstance(application)


    fun startInteraction() {
        if (_uiState.value.currentStep != HarvestStep.IDLE) return
        Log.d(TAG, "[FLUX] Démarrage de l'interaction. Passage à BOURDON_INTRO.")
        _uiState.update {
            it.copy(
                currentStep = HarvestStep.BOURDON_INTRO,
                // BOURDON'S REFACTOR: Nouveau message d'accueil direct.
                bourdonMessage = getApplication<Application>().getString(R.string.bourdon_welcome_capture)
            )
        }
    }

    fun onBourdonFinishedSpeaking() {
        _uiState.update {
            val nextStep = when (it.currentStep) {
                // BOURDON'S REFACTOR: Après l'intro, on passe directement à la capture.
                HarvestStep.BOURDON_INTRO -> HarvestStep.USER_CAPTURING_POLLEN
                else -> it.currentStep
            }
            Log.d(TAG, "[FLUX] Le Bourdon a fini de parler. Passage à l'état: $nextStep")
            it.copy(currentStep = nextStep, bourdonMessage = null)
        }
    }

    // BOURDON'S REFACTOR: Les méthodes onUserIntentCaptured, onVoiceRecordingStarted, onSkipIntent sont maintenant OBSOLÈTES et supprimées.

    fun startPollenAnalysis(bitmap: Bitmap, pollenForge: PollenForge) {
        if (_uiState.value.capturedPollen.size >= 4) {
            Log.w(TAG, "[ANALYSE] Tentative de capture alors que le maximum (4) est atteint. Ignoré.")
            return
        }

        val newCapture = PollenCapture(bitmap = bitmap)
        _uiState.update { it.copy(capturedPollen = it.capturedPollen + newCapture) }
        Log.i(TAG, "[ANALYSE] Nouvelle capture ajoutée (ID: ${newCapture.id}). Lancement de l'analyse par la PollenForge.")

        viewModelScope.launch {
            val (report, jsonReport) = pollenForge.processImage(bitmap)
            Log.i(TAG, "[ANALYSE] Analyse terminée pour la capture ID: ${newCapture.id}.")

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

                if (shouldStopCapture) {
                    Log.d(TAG, "[FLUX] Maximum de captures atteint. Passage à AWAITING_FINAL_CHOICE.")
                    currentState.copy(
                        capturedPollen = updatedList,
                        currentStep = HarvestStep.AWAITING_FINAL_CHOICE,
                        bourdonMessage = getApplication<Application>().getString(R.string.bourdon_harvest_complete),
                        canFinishHarvest = canFinish
                    )
                } else {
                    currentState.copy(
                        capturedPollen = updatedList,
                        canFinishHarvest = canFinish
                    )
                }
            }
        }
    }

    fun onStopHarvesting() {
        Log.d(TAG, "[FLUX] L'utilisateur a terminé la récolte manuellement. Passage à AWAITING_FINAL_CHOICE.")
        _uiState.update {
            it.copy(
                currentStep = HarvestStep.AWAITING_FINAL_CHOICE,
                bourdonMessage = getApplication<Application>().getString(R.string.bourdon_harvest_complete_alt)
            )
        }
    }

    fun onRestartHarvest() {
        Log.i(TAG, "[FLUX] Réinitialisation de la session de récolte.")
        _uiState.value = ForgeLiveUiState()
        startInteraction()
    }

    fun onSendToHive() {
        val currentState = _uiState.value
        if (currentState.capturedPollen.isEmpty() || currentState.currentStep == HarvestStep.SAVING) return

        viewModelScope.launch {
            Log.d(TAG, "[FLUX] Envoi du pollen à la Ruche. Passage à SAVING.")
            _uiState.update { it.copy(currentStep = HarvestStep.SAVING, bourdonMessage = getApplication<Application>().getString(R.string.bourdon_save_pollen)) }

            val savedImagePaths = savePollenImages(currentState.capturedPollen)

            if (savedImagePaths.isEmpty()) {
                Log.e(TAG, "[FLUX] Erreur critique lors de la sauvegarde des images. Le processus est interrompu.")
                _uiState.update { it.copy(currentStep = HarvestStep.AWAITING_FINAL_CHOICE, bourdonMessage = getApplication<Application>().getString(R.string.bourdon_save_error)) }
                return@launch
            }

            val aggregatedReport = aggregateJsonReports(currentState.capturedPollen)

            val pollenToSave = DbPollenGrain(
                // BOURDON'S REFACTOR: L'intention utilisateur est maintenant toujours nulle.
                userIntent = null,
                pollenImagePaths = savedImagePaths,
                swarmAnalysisReportJson = aggregatedReport,
                status = PollenStatus.RAW
            )

            Log.i(TAG, "CONTENU DU RAPPORT JSON SAUVEGARDÉ :\n$aggregatedReport")

            pollenGrainDao.insert(pollenToSave)
            Log.i(TAG, "Nouveau PollenGrain (ID: ${pollenToSave.id}) inséré dans la base de données.")

            launchIdentificationWorker()

            _uiState.update { it.copy(currentStep = HarvestStep.SAVED, bourdonMessage = getApplication<Application>().getString(R.string.bourdon_save_success)) }

            kotlinx.coroutines.delay(2000)
            _navigationEvent.emit(Unit)
        }
    }

    private fun launchIdentificationWorker() {
        Log.d(TAG, "Lancement du IdentificationWorker.")

        val prefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val requiresCharging = prefs.getBoolean(ToolsDialogFragment.KEY_REQUIRE_CHARGING, false)
        val requiresIdle = prefs.getBoolean(ToolsDialogFragment.KEY_REQUIRE_IDLE, false)

        val constraints = Constraints.Builder()
            .setRequiresCharging(requiresCharging)
            .setRequiresDeviceIdle(requiresIdle)
            .build()

        val identificationRequest = OneTimeWorkRequestBuilder<IdentificationWorker>()
            .setConstraints(constraints)
            .build()

        workManager.beginUniqueWork(
            "PollenForgeChain",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            identificationRequest
        ).enqueue()

        Log.i(TAG, "IdentificationWorker mis en file d'attente avec succès.")
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
            capture.jsonReport?.let { gson.fromJson(it, Map::class.java) }
        }
        return gson.toJson(mapOf("reports" to allReports))
    }

    fun reset() {
        Log.i(TAG, "ViewModel réinitialisé à son état initial.")
        _uiState.value = ForgeLiveUiState()
    }
}
// --- END OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/ForgeLiveViewModel.kt ---