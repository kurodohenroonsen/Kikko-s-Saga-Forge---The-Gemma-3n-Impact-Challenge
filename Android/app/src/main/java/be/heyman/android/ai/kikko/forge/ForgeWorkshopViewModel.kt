// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/forge/ForgeWorkshopViewModel.kt ---
package be.heyman.android.ai.kikko.forge

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import be.heyman.android.ai.kikko.GameConstants
import be.heyman.android.ai.kikko.KikkoApplication
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.ToolsDialogFragment
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.model.AnalysisResult
import be.heyman.android.ai.kikko.model.AnalysisStatus
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.ModelConfiguration
import be.heyman.android.ai.kikko.model.PollenGrain
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.model.Reasoning
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class CompetitionSummaryItem(
    val response: String,
    val voteCount: Int,
    val contributingQueens: List<String>,
    val firstValidTask: AnalysisResult
)

data class CompetitionSummary(
    val propertyName: String,
    val items: List<CompetitionSummaryItem>
)

data class RankedProposal(
    val value: String,
    val voteCount: Int,
    val reasoningSummary: String,
    val sourceTaskId: String
)

data class ArbiterVerdict(
    val arbiterReasoning: String,
    val rankedProposals: List<RankedProposal>
)

// BOURDON'S REFORGE: Nouvelle data class pour encapsuler les métriques.
data class PerformanceMetrics(
    val ttftMs: Long?,
    val totalTimeMs: Long,
    val tokensPerSecond: Float
)

sealed class JudgmentState {
    data object None : JudgmentState()
    data class InProgress(val propertyName: String, val prompt: String, val streamingResponse: String = "") : JudgmentState()
    data class Complete(val propertyName: String, val verdict: ArbiterVerdict) : JudgmentState()
    data class Failed(val propertyName: String, val error: String) : JudgmentState()
}

data class ForgeWorkshopUiState(
    val isLoading: Boolean = true,
    val workshopGrains: List<PollenGrain> = emptyList(),
    val selectedGrain: PollenGrain? = null,
    val selectedCard: KnowledgeCard? = null,
    val analysisResults: Map<String, List<AnalysisResult>> = emptyMap(),
    val competitionSummaries: Map<String, CompetitionSummary> = emptyMap(),
    val judgmentState: JudgmentState = JudgmentState.None,
    val statusMessage: String? = null,
    val activeFilter: String = ForgeWorkshopViewModel.FILTER_RAW
)

class ForgeWorkshopViewModel(application: Application) : AndroidViewModel(application) {

    private val forgeRepository: ForgeRepository = (application as KikkoApplication).forgeRepository
    private val llmHelper: ForgeLlmHelper = (application as KikkoApplication).forgeLlmHelper
    private val pollenGrainDao: PollenGrainDao = (application as KikkoApplication).pollenGrainDao
    private val cardDao: CardDao = (application as KikkoApplication).cardDao

    private val TAG = "KikkoForgeTrace"
    private val _uiState = MutableStateFlow(ForgeWorkshopUiState())
    val uiState = _uiState.asStateFlow()
    private val gson = Gson()

    private var competitionJob: Job? = null
    private var judgmentJob: Job? = null
    private var streamingUpdateJob: Job? = null

    private var allWorkshopItems = listOf<Pair<PollenGrain, KnowledgeCard?>>()

    private val deckProperties = mapOf(
        "Food" to listOf("description", "ingredients", "allergens", "stats.energy"),
        "Plant" to listOf("description", "biological.scientificName", "biological.vernacularName", "stats.floweringPeriod"),
        "Insect" to listOf("description", "biological.scientificName", "biological.vernacularName", "stats.diet"),
        "Bird" to listOf("description", "biological.scientificName", "biological.vernacularName", "stats.wingspan")
    )

    private data class IdentificationResultData(
        val reasoning: Reasoning,
        val deckName: String,
        val specificName: String,
        val confidence: Float
    )

    companion object {
        const val FILTER_ALL = "ALL"
        const val FILTER_RAW = "RAW"
        // BOURDON'S NANO INTEGRATION: Nom constant pour la Reine Nano.
        const val NANO_QUEEN_NAME = "Gemini Nano"
    }

    init {
        Log.d(TAG, "ViewModel initialisé. Lancement du chargement initial des grains de pollen.")
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = getString(R.string.workshop_loading_grains)) }
            loadWorkshopGrains()
        }
    }

    fun getPropertiesForDeck(deckName: String?): List<String> {
        val normalizedDeckName = deckName
            ?.trim()
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            ?.removeSuffix("s")
        val properties = deckProperties[normalizedDeckName] ?: emptyList()
        return properties
    }


    fun selectGrain(grain: PollenGrain) {
        viewModelScope.launch {
            competitionJob?.cancel()
            judgmentJob?.cancel()
            streamingUpdateJob?.cancel()
            Log.i(TAG, "[SELECT] Sélection du grain ID: ${grain.id} | Statut: ${grain.status} | CardID lié: ${grain.forgedCardId}")

            val card = allWorkshopItems.find { it.first.id == grain.id }?.second

            val newStatusMessage = if (grain.status == PollenStatus.AWAITING_VALIDATION || grain.status == PollenStatus.IDENTIFYING) {
                getString(R.string.workshop_awaiting_validation)
            } else {
                getString(R.string.workshop_ready_to_refine)
            }

            _uiState.update { it.copy(
                selectedGrain = grain,
                selectedCard = card,
                analysisResults = emptyMap(),
                competitionSummaries = emptyMap(),
                judgmentState = JudgmentState.None,
                statusMessage = newStatusMessage
            ) }

            if (card != null) {
                Log.d(TAG, "[SELECT] Carte chargée depuis la cache - ID: ${card.id}, Nom: '${card.specificName}', Deck: '${card.deckName}'")
            } else {
                Log.w(TAG, "[SELECT] Aucune carte n'a pu être chargée pour ce grain.")
            }

            refreshAnalysisResults(grain.id, "identification")
            if (card?.deckName != "Unknown") {
                val properties = getPropertiesForDeck(card?.deckName)
                properties.forEach { propertyName ->
                    refreshAnalysisResults(grain.id, propertyName)
                }
            }
        }
    }

    fun setFilter(filterType: String) {
        viewModelScope.launch {
            if (_uiState.value.activeFilter == filterType) return@launch
            _uiState.update { it.copy(activeFilter = filterType) }
            updateFilteredGrains()
        }
    }

    private fun updateFilteredGrains() {
        val currentFilter = _uiState.value.activeFilter
        Log.d(TAG, "[FILTER] Application du filtre: $currentFilter")

        val filteredItems = when (currentFilter) {
            FILTER_RAW -> allWorkshopItems.filter { (grain, _) ->
                grain.status == PollenStatus.RAW ||
                        grain.status == PollenStatus.IDENTIFYING ||
                        grain.status == PollenStatus.AWAITING_VALIDATION ||
                        grain.status == PollenStatus.ERROR
            }
            else -> allWorkshopItems.filter { (_, card) -> card?.deckName == currentFilter }
        }

        val filteredGrains = filteredItems.map { it.first }
        _uiState.update { it.copy(workshopGrains = filteredGrains) }

        val selectedGrainStillVisible = filteredGrains.any { it.id == _uiState.value.selectedGrain?.id }
        if (!selectedGrainStillVisible) {
            val newGrainToSelect = filteredGrains.firstOrNull()
            if (newGrainToSelect != null) {
                selectGrain(newGrainToSelect)
            } else {
                _uiState.update { it.copy(selectedGrain = null, selectedCard = null, statusMessage = getString(R.string.workshop_no_grains_in_deck)) }
            }
        }
    }


    fun createAnalysisTournament(propertyName: String) {
        val grain = _uiState.value.selectedGrain ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = getString(R.string.workshop_preparing_competition, propertyName)) }

            val prefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
            val useNano = prefs.getBoolean(ToolsDialogFragment.KEY_USE_GEMINI_NANO, false)

            val tasks = if (useNano) {
                Log.i(TAG, "[COMPETITION] La Reine Gemini Nano est sélectionnée. Création d'une tâche unique.")
                val nanoConfig = ModelConfiguration(NANO_QUEEN_NAME, "N/A", 0.7f, 40)
                val task = AnalysisResult(
                    pollenGrainId = grain.id,
                    propertyName = propertyName,
                    modelConfigJson = gson.toJson(nanoConfig),
                    status = AnalysisStatus.PENDING
                )
                forgeRepository.insertAnalysisResult(task)
                listOf(task)
            } else {
                val modelsToCompete = withContext(Dispatchers.IO) {
                    File(getApplication<Application>().filesDir, "imported_models")
                        .listFiles { _, name -> name.endsWith(".task") }?.map { it.name } ?: emptyList()
                }

                if (modelsToCompete.isEmpty()) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = getString(R.string.workshop_no_queens_installed)) }
                    return@launch
                }
                Log.i(TAG, "[COMPETITION] Reines en compétition: ${modelsToCompete.joinToString()}")
                val accelerator = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU") ?: "GPU"
                forgeRepository.createAnalysisTasksForProperty(grain.id, propertyName, modelsToCompete, accelerator)
            }

            Log.i(TAG, "[COMPETITION] Création de ${tasks.size} tâches pour la propriété '$propertyName'.")
            refreshAnalysisResults(grain.id, propertyName)
            val message = getApplication<Application>().resources.getQuantityString(R.plurals.workshop_tasks_ready, tasks.size, tasks.size)
            _uiState.update { it.copy(isLoading = false, statusMessage = message) }
            launchCompetitionExecution(propertyName)
        }
    }

    fun relaunchAnalysisTournament(propertyName: String) {
        val grain = _uiState.value.selectedGrain ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Clearing old results for '$propertyName'...") }
            forgeRepository.clearAnalysisResultsForProperty(grain.id, propertyName)
            refreshAnalysisResults(grain.id, propertyName)
            createAnalysisTournament(propertyName)
        }
    }


    fun validateFromSummary(summaryItem: CompetitionSummaryItem) {
        Log.i(TAG, "[VALIDATE-SUMMARY] Validation demandée pour la réponse '${summaryItem.response}' avec ${summaryItem.voteCount} votes.")
        validateProperty(summaryItem.firstValidTask)
    }

    fun validateAndCreateCardFromIdentification(task: AnalysisResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val grain = _uiState.value.selectedGrain ?: return@launch
            val rawResponse = task.rawResponse ?: return@launch

            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = true, statusMessage = getString(R.string.workshop_validating_identification)) }
            }

            try {
                var resultData = parseIntelligentJson<IdentificationResultData>(rawResponse)

                if (resultData == null) {
                    val name = """"specificName"\s*:\s*"(.*?)"""".toRegex().find(rawResponse)?.groups?.get(1)?.value
                    val deck = """"(deckName|DeckName)"\s*:\s*"(.*?)"""".toRegex(RegexOption.IGNORE_CASE).find(rawResponse)?.groups?.get(2)?.value

                    if (name != null && deck != null) {
                        resultData = IdentificationResultData(
                            reasoning = Reasoning(getString(R.string.fallback_reasoning_visual), getString(R.string.fallback_reasoning_correlation)),
                            deckName = deck,
                            specificName = name,
                            confidence = 0.5f
                        )
                    } else {
                        throw IOException(getString(R.string.error_validation_parsing_failed))
                    }
                }

                var cardId = grain.forgedCardId
                if (cardId == null) {
                    val newCard = KnowledgeCard(
                        specificName = resultData.specificName, deckName = resultData.deckName,
                        imagePath = grain.pollenImagePaths.firstOrNull(), confidence = resultData.confidence,
                        reasoning = resultData.reasoning, description = null, stats = null, quiz = null, translations = null,
                        scientificName = null, vernacularName = null, allergens = null, ingredients = null
                    )
                    cardId = cardDao.insert(newCard)
                    pollenGrainDao.updateForgingResult(grain.id, grain.status, cardId)
                } else {
                    cardDao.updateIdentification(
                        cardId = cardId, specificName = resultData.specificName, deckName = resultData.deckName,
                        reasoning = resultData.reasoning, confidence = resultData.confidence
                    )
                }

                pollenGrainDao.updateStatus(grain.id, PollenStatus.PENDING_DESCRIPTION)

                withContext(Dispatchers.Main) {
                    loadWorkshopGrains()
                }

            } catch (e: Exception) {
                Log.e(TAG, "[VALIDATE-ID] Échec critique du processus de validation.", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = getString(R.string.workshop_validation_error, e.message ?: getString(R.string.error_unknown))) }
                }
            }
        }
    }

    fun validateProperty(task: AnalysisResult) {
        if (task.propertyName == "identification") {
            validateAndCreateCardFromIdentification(task)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val grain = _uiState.value.selectedGrain ?: return@launch
            val cardId = grain.forgedCardId ?: return@launch
            val rawResponse = task.rawResponse ?: return@launch
            Log.i(TAG, "[VALIDATE-PROP] Validation de la propriété '${task.propertyName}' avec la réponse de la tâche ${task.id}.")


            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isLoading = true, statusMessage = getString(R.string.workshop_validating_property, task.propertyName)) }
            }

            try {
                val propertyJson = parseIntelligentJson<JsonObject>(rawResponse)
                    ?: throw IOException(getString(R.string.error_json_malformed))

                val valueElement = propertyJson.get(task.propertyName)
                    ?: throw IOException(getString(R.string.error_json_key_missing, task.propertyName))

                val valueAsString = if (valueElement.isJsonPrimitive) valueElement.asString else gson.toJson(valueElement)

                forgeRepository.updateCardProperty(cardId, task.propertyName, valueAsString)

                val nextStatus = getNextStatus(grain.status)
                pollenGrainDao.updateStatus(grain.id, nextStatus)

                withContext(Dispatchers.Main) {
                    loadWorkshopGrains()
                }

            } catch (e: Exception) {
                Log.e(TAG, "[VALIDATE-PROP] Échec de la validation pour la propriété '${task.propertyName}'.", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, statusMessage = getString(R.string.workshop_validation_error, e.message ?: getString(R.string.error_unknown))) }
                }
            }
        }
    }

    private fun getNextStatus(currentStatus: PollenStatus): PollenStatus {
        return when (currentStatus) {
            PollenStatus.PENDING_DESCRIPTION -> PollenStatus.PENDING_STATS
            PollenStatus.PENDING_STATS -> PollenStatus.PENDING_QUIZ
            PollenStatus.PENDING_QUIZ -> PollenStatus.PENDING_TRANSLATION
            PollenStatus.PENDING_TRANSLATION -> PollenStatus.FORGED
            else -> currentStatus
        }
    }

    private fun launchCompetitionExecution(propertyName: String, singleTaskToRun: AnalysisResult? = null) {
        competitionJob?.cancel()
        competitionJob = viewModelScope.launch(Dispatchers.IO) {
            val tasksToRun = singleTaskToRun?.let { listOf(it) }
                ?: _uiState.value.analysisResults[propertyName]?.filter { it.status == AnalysisStatus.PENDING }
                ?: emptyList()

            if (tasksToRun.isEmpty()) {
                Log.w(TAG, "[ORCHESTRATOR] Lancement demandé pour '$propertyName', mais aucune tâche en attente trouvée.")
                return@launch
            }

            val tasksByQueen = tasksToRun.groupBy {
                gson.fromJson(it.modelConfigJson, ModelConfiguration::class.java).modelName
            }
            Log.i(TAG, "[ORCHESTRATOR] Lancement pour ${tasksToRun.size} tâches, réparties sur ${tasksByQueen.size} Reine(s).")

            for ((modelName, tasksForQueen) in tasksByQueen) {
                if (!isActive) break
                if (modelName == NANO_QUEEN_NAME) continue // La logique Nano est gérée séparément.

                Log.i(TAG, "[ORCHESTRATOR] Convocation de la Reine '$modelName' pour ${tasksForQueen.size} tâche(s).")
                val modelFile = File(getApplication<Application>().filesDir, "imported_models").resolve(modelName)
                if (!modelFile.exists()) {
                    Log.e(TAG, "[ORCHESTRATOR] Échec : Fichier de la Reine IA '$modelName' introuvable.")
                    tasksForQueen.forEach { updateTaskStatusInDb(it.id, it.pollenGrainId, it.propertyName, AnalysisStatus.FAILED, "Model file not found") }
                    continue
                }

                val isMultimodal = tasksForQueen.any { it.propertyName == "identification" || it.propertyName == "description" }
                val queenModel = Model(name = modelName, url = modelFile.absolutePath, downloadFileName = modelName, sizeInBytes = modelFile.length(), llmSupportImage = modelName.contains("gemma-3n", ignoreCase = true))
                val accelerator = gson.fromJson(tasksForQueen.first().modelConfigJson, ModelConfiguration::class.java).accelerator

                val initError = llmHelper.initialize(queenModel, accelerator, isMultimodal)
                if (initError != null) {
                    Log.e(TAG, "[ORCHESTRATOR] Échec de l'initialisation de la Reine '$modelName': $initError")
                    tasksForQueen.forEach { updateTaskStatusInDb(it.id, it.pollenGrainId, it.propertyName, AnalysisStatus.FAILED, "Initialization failed: $initError") }
                    continue
                }

                try {
                    for (task in tasksForQueen) {
                        if (!isActive) break
                        val success = runSingleTask(task)
                        if (!success) {
                            Log.w(TAG, "[ORCHESTRATOR] La tâche ${task.id.substring(0,4)} a échoué. La Reine '$modelName' continue avec la tâche suivante.")
                        }
                    }
                } finally {
                    Log.i(TAG, "[ORCHESTRATOR] La Reine '$modelName' a terminé son service. Nettoyage des ressources.")
                    llmHelper.cleanUp()
                }
            }

            // BOURDON'S NANO INTEGRATION: Gérer la tâche Nano s'il y en a une.
            tasksByQueen[NANO_QUEEN_NAME]?.firstOrNull()?.let { nanoTask ->
                if (isActive) {
                    Log.i(TAG, "[ORCHESTRATOR] Convocation de la Reine Gemini Nano pour la tâche ${nanoTask.id}.")
                    runSingleTask(nanoTask)
                }
            }

            Log.i(TAG, "[ORCHESTRATOR] Toutes les Reines ont terminé leur service pour la propriété '$propertyName'.")
        }
    }

    private suspend fun runSingleTask(task: AnalysisResult): Boolean {
        return try {
            withContext(Dispatchers.Main) { updateTaskStatusInUi(task.id, task.propertyName) { it.copy(status = AnalysisStatus.RUNNING, streamingResponse = "") } }
            val config = gson.fromJson(task.modelConfigJson, ModelConfiguration::class.java)

            // BOURDON'S NANO INTEGRATION: Aiguillage de la logique d'inférence.
            if (config.modelName == NANO_QUEEN_NAME) {
                runNanoInference(task)
            } else {
                runMediaPipeInference(task, config)
            }
            true
        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.i(TAG, "[TASK-CANCEL] Tâche ${task.id.substring(0,4)} annulée.")
                updateTaskStatusInDb(task.id, task.pollenGrainId, task.propertyName, AnalysisStatus.CANCELLED)
            } else {
                Log.e(TAG, "[TASK-FAIL] Échec Tâche ${task.id.substring(0,4)}", e)
                updateTaskStatusInDb(task.id, task.pollenGrainId, task.propertyName, AnalysisStatus.FAILED, e.message)
            }
            false
        }
    }

    private suspend fun runMediaPipeInference(task: AnalysisResult, config: ModelConfiguration) {
        val isMultimodalTask = task.propertyName == "identification" || task.propertyName == "description"
        val parentGrain = _uiState.value.selectedGrain ?: throw IllegalStateException("Parent pollen grain not found")
        val swarmReportJson = parentGrain.swarmAnalysisReportJson ?: throw IOException("Swarm report missing")
        val images = if (isMultimodalTask) parentGrain.pollenImagePaths.mapNotNull { BitmapFactory.decodeFile(it) } else emptyList<Bitmap>()

        val prompt = getPromptForTask(task, parentGrain, swarmReportJson)
        val modelFile = File(getApplication<Application>().filesDir, "imported_models").resolve(config.modelName)
        val queenModel = Model(name = config.modelName, url = modelFile.absolutePath, downloadFileName = config.modelName, sizeInBytes = modelFile.length())
        llmHelper.resetSession(queenModel, isMultimodalTask, config.temperature, config.topK)

        val (fullResponse, metrics) = executeStreamingInferenceAndCaptureMetrics(
            prompt = prompt,
            images = images
        ) { streamingText ->
            updateTaskStatusInUi(task.id, task.propertyName) { it.copy(streamingResponse = streamingText) }
        }

        val updatedTask = task.copy(
            status = AnalysisStatus.COMPLETED, rawResponse = fullResponse, streamingResponse = null,
            ttftMs = metrics.ttftMs, totalTimeMs = metrics.totalTimeMs, tokensPerSecond = metrics.tokensPerSecond
        )
        forgeRepository.updateAnalysisResult(updatedTask)
        withContext(Dispatchers.Main) { refreshAnalysisResults(task.pollenGrainId, task.propertyName) }
    }

    private suspend fun runNanoInference(task: AnalysisResult) {
        if (!NanoLlmHelper.isNanoAvailable(getApplication())) {
            throw IOException("Gemini Nano is not available.")
        }

        val parentGrain = _uiState.value.selectedGrain ?: throw IllegalStateException("Parent pollen grain not found")
        val swarmReportJson = parentGrain.swarmAnalysisReportJson ?: throw IOException("Swarm report missing")
        val prompt = getPromptForTask(task, parentGrain, swarmReportJson)

        val startTime = System.currentTimeMillis()
        val fullResponse = NanoLlmHelper.generateResponse(prompt)
        val endTime = System.currentTimeMillis()

        if (fullResponse.startsWith("Erreur")) throw IOException(fullResponse)

        val updatedTask = task.copy(
            status = AnalysisStatus.COMPLETED, rawResponse = fullResponse, streamingResponse = null,
            totalTimeMs = endTime - startTime
        )
        forgeRepository.updateAnalysisResult(updatedTask)
        withContext(Dispatchers.Main) { refreshAnalysisResults(task.pollenGrainId, task.propertyName) }
    }

    private suspend fun getPromptForTask(task: AnalysisResult, parentGrain: PollenGrain, swarmReportJson: String): String {
        return when (task.propertyName) {
            "identification" -> ForgePromptGenerator.generateIdentificationTournamentPrompt(swarmReportJson)
            else -> {
                val card = forgeRepository.getCardForGrain(parentGrain) ?: throw IllegalStateException("Card not found for refinement")
                ForgePromptGenerator.generatePropertyForgePrompt(
                    propertyName = task.propertyName, deckName = card.deckName, specificName = card.specificName,
                    swarmReportJson = swarmReportJson, existingDescription = card.description, dependencyDataJson = null
                )
            }
        }
    }


    fun launchFinalJudgment(propertyName: String) {
        judgmentJob?.cancel()
        val proposals = _uiState.value.analysisResults[propertyName]?.filter { it.status == AnalysisStatus.COMPLETED }
        if (proposals.isNullOrEmpty()) {
            viewModelScope.launch { _uiState.update { it.copy(statusMessage = "Aucune proposition valide à juger.") } }
            return
        }

        val maxResponseLength = 2048
        val safeProposals = proposals.map {
            val localRawResponse = it.rawResponse
            if (localRawResponse != null && localRawResponse.length > maxResponseLength) {
                Log.w(TAG, "[SAFETY VALVE] Troncation de la réponse anormalement longue de la tâche ${it.id} à $maxResponseLength caractères.")
                it.copy(rawResponse = localRawResponse.substring(0, maxResponseLength) + " ...TRUNCATED...\"}")
            } else {
                it
            }
        }

        judgmentJob = viewModelScope.launch(Dispatchers.IO) {
            val prompt = ForgePromptGenerator.generateJudgmentPrompt(propertyName, safeProposals)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(judgmentState = JudgmentState.InProgress(propertyName, prompt)) }
            }

            try {
                val prefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
                val useNano = prefs.getBoolean(ToolsDialogFragment.KEY_USE_GEMINI_NANO, false)
                val fullResponse: String

                if (useNano) {
                    if (!NanoLlmHelper.isNanoAvailable(getApplication())) throw IOException("Gemini Nano is not available.")
                    fullResponse = NanoLlmHelper.generateResponse(prompt)
                } else {
                    val modelName = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, null) ?: throw IOException("Aucune Reine sélectionnée pour être l'Arbitre.")
                    val accelerator = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")!!
                    val modelFile = File(getApplication<Application>().filesDir, "imported_models").resolve(modelName)
                    if (!modelFile.exists()) throw IOException("Fichier de la Reine Arbitre introuvable.")
                    val arbiterModel = Model(name = modelName, url = modelFile.absolutePath, downloadFileName = modelName, sizeInBytes = modelFile.length())
                    val config = ModelConfiguration(modelName, accelerator, 0.1f, 1)

                    val initError = llmHelper.initialize(arbiterModel, accelerator, false)
                    if(initError != null) throw RuntimeException("Échec de l'initialisation de l'Arbitre: $initError")

                    fullResponse = llmHelper.inferenceWithCoroutineAndConfig(prompt, emptyList(), config)
                }

                val arbiterVerdict = parseIntelligentJson<ArbiterVerdict>(fullResponse) ?: throw IOException("Impossible de parser le verdict de l'Arbitre: $fullResponse")
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(judgmentState = JudgmentState.Complete(propertyName, arbiterVerdict)) }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(judgmentState = JudgmentState.Failed(propertyName, e.message ?: "Erreur inconnue de l'Arbitre."))}
                }
            } finally {
                llmHelper.cleanUp()
            }
        }
    }

    fun confirmJudgment() {
        val currentState = _uiState.value.judgmentState
        if (currentState is JudgmentState.Complete && currentState.verdict.rankedProposals.isNotEmpty()) {
            val winningTaskId = currentState.verdict.rankedProposals.first().sourceTaskId
            val winningTask = _uiState.value.analysisResults[currentState.propertyName]?.find { it.id == winningTaskId }
            if (winningTask != null) {
                validateProperty(winningTask)
            } else {
                Log.e(TAG, "Tâche gagnante avec l'ID $winningTaskId non trouvée!")
            }
            dismissJudgment()
        }
    }

    fun dismissJudgment() {
        _uiState.update { it.copy(judgmentState = JudgmentState.None) }
    }

    fun runAnalysisTask(task: AnalysisResult) {
        Log.i(TAG, "Lancement manuel de la tâche unique ${task.id} pour la propriété '${task.propertyName}'")
        launchCompetitionExecution(task.propertyName, singleTaskToRun = task)
    }

    fun cancelAnalysisTask(task: AnalysisResult) {
        Log.i(TAG, "Demande d'annulation pour la tâche ${task.id} (propriété '${task.propertyName}')")
        competitionJob?.cancel()
        viewModelScope.launch {
            updateTaskStatusInDb(task.id, task.pollenGrainId, task.propertyName, AnalysisStatus.CANCELLED, "Cancelled by user")
        }
    }


    fun retryAnalysisTask(task: AnalysisResult) {
        launchCompetitionExecution(task.propertyName, singleTaskToRun = task)
    }

    fun deleteSelectedGrain() {
        val grainToDelete = _uiState.value.selectedGrain ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                forgeRepository.deletePollenGrainAndAssociatedData(grainToDelete)
            }
            loadWorkshopGrains()
        }
    }

    private suspend fun updateTaskStatusInDb(taskId: String, grainId: String, propertyName: String, newStatus: AnalysisStatus, errorMessage: String? = null) {
        val currentTask = forgeRepository.getAnalysisResults(grainId, propertyName).find { it.id == taskId }
        if (currentTask != null) {
            val updatedTask = currentTask.copy(status = newStatus, errorMessage = errorMessage)
            forgeRepository.updateAnalysisResult(updatedTask)
            withContext(Dispatchers.Main) {
                refreshAnalysisResults(grainId, propertyName)
            }
        }
    }

    private fun updateTaskStatusInUi(taskId: String, propertyName: String, updateAction: (AnalysisResult) -> AnalysisResult) {
        _uiState.update { currentState ->
            val newMap = currentState.analysisResults.toMutableMap()
            val propertyTasks = newMap[propertyName]?.toMutableList()
            val taskIndex = propertyTasks?.indexOfFirst { it.id == taskId }

            if (propertyTasks != null && taskIndex != null && taskIndex != -1) {
                propertyTasks[taskIndex] = updateAction(propertyTasks[taskIndex])
                newMap[propertyName] = propertyTasks
                currentState.copy(analysisResults = newMap)
            } else {
                currentState
            }
        }
    }

    private suspend fun loadWorkshopGrains() {
        _uiState.update { it.copy(isLoading = true, statusMessage = getString(R.string.workshop_loading_grains)) }

        val grains = forgeRepository.getGrainsForWorkshop() + pollenGrainDao.getByStatus(PollenStatus.AWAITING_VALIDATION) + pollenGrainDao.getByStatus(PollenStatus.ERROR)
        val items = grains.distinctBy { it.id }.sortedByDescending { it.timestamp }.map { grain ->
            val card = forgeRepository.getCardForGrain(grain)
            Pair(grain, card)
        }
        allWorkshopItems = items

        _uiState.update { it.copy(isLoading = false) }
        updateFilteredGrains()

        val grainToSelect = _uiState.value.workshopGrains.firstOrNull()
        if (grainToSelect != null) {
            selectGrain(grainToSelect)
        } else {
            _uiState.update { it.copy(selectedGrain = null, selectedCard = null, statusMessage = getString(R.string.workshop_no_grains_to_forge)) }
        }
    }

    private suspend fun refreshAnalysisResults(pollenGrainId: String, propertyName: String) {
        val results = forgeRepository.getAnalysisResults(pollenGrainId, propertyName)

        _uiState.update { currentState ->
            val newAnalysisMap = currentState.analysisResults.toMutableMap().apply { this[propertyName] = results }
            val newSummariesMap = currentState.competitionSummaries.toMutableMap()

            val hasCompletedTasks = results.any { it.status == AnalysisStatus.COMPLETED }
            if (hasCompletedTasks) {
                Log.d(TAG, "[SUMMARY] Au moins une tâche pour '$propertyName' est terminée. Calcul du résumé.")
                val summary = createCompetitionSummary(propertyName, results)
                if (summary.items.isNotEmpty()) {
                    newSummariesMap[propertyName] = summary
                } else {
                    newSummariesMap.remove(propertyName)
                }
            } else {
                newSummariesMap.remove(propertyName)
            }
            currentState.copy(analysisResults = newAnalysisMap, competitionSummaries = newSummariesMap)
        }
    }

    private fun createCompetitionSummary(propertyName: String, results: List<AnalysisResult>): CompetitionSummary {
        val completedTasks = results.filter { it.status == AnalysisStatus.COMPLETED && !it.rawResponse.isNullOrBlank() }
        if (completedTasks.isEmpty()) return CompetitionSummary(propertyName, emptyList())

        val responseGroups = completedTasks.groupBy { task ->
            // BOURDON'S FIX: Copie la propriété mutable dans une variable locale immuable avant de l'utiliser.
            val localRawResponse = task.rawResponse
            if (localRawResponse.isNullOrBlank()) {
                ""
            } else {
                extractValueFromResponse(localRawResponse, propertyName)
            }
        }.filterKeys { it.isNotBlank() }

        val summaryItems = responseGroups.map { (response, tasks) ->
            val queenNames = tasks.map { gson.fromJson(it.modelConfigJson, ModelConfiguration::class.java).modelName }
            CompetitionSummaryItem(response = response, voteCount = tasks.size, contributingQueens = queenNames, firstValidTask = tasks.first())
        }.sortedByDescending { it.voteCount }
        Log.d(TAG, "[SUMMARY] Création du résumé pour '$propertyName': ${summaryItems.size} proposition(s) unique(s) trouvée(s).")
        return CompetitionSummary(propertyName, summaryItems)
    }

    private fun extractValueFromResponse(rawResponse: String, propertyName: String): String {
        try {
            val propertyJson = parseIntelligentJson<JsonObject>(rawResponse)
            if (propertyJson != null) {
                return when (propertyName) {
                    "identification" -> {
                        val name = propertyJson.get("specificName")?.asString ?: ""
                        val deck = propertyJson.get("deckName")?.asString ?: ""
                        if (name.isNotBlank() && deck.isNotBlank()) "$deck: $name" else ""
                    }
                    else -> {
                        val valueElement = propertyJson.get(propertyName)
                        if (valueElement != null) {
                            if (valueElement.isJsonPrimitive) valueElement.asString else gson.toJson(valueElement)
                        } else ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[PARSER] Le parsing JSON a échoué pour '$propertyName', tentative de fallback. Erreur: ${e.message}")
        }

        if (propertyName == "identification") {
            val name = """"specificName"\s*:\s*"(.*?)"""".toRegex().find(rawResponse)?.groups?.get(1)?.value
            val deck = """"(deckName|DeckName)"\s*:\s*"(.*?)"""".toRegex(RegexOption.IGNORE_CASE).find(rawResponse)?.groups?.get(2)?.value
            if (name != null && deck != null) {
                Log.i(TAG, "[EXTRACT-REGEX] Fallback réussi pour identification: '$deck: $name'")
                return "$deck: $name"
            }
        }
        return ""
    }


    private inline fun <reified T> parseIntelligentJson(rawString: String): T? {
        val firstBrace = rawString.indexOf('{')
        if (firstBrace == -1) return null

        var braceCount = 0
        var lastBrace = -1
        for (i in firstBrace until rawString.length) {
            when (rawString[i]) {
                '{' -> braceCount++
                '}' -> braceCount--
            }
            if (braceCount == 0) {
                lastBrace = i
                break
            }
        }

        if (lastBrace == -1) return null
        val jsonSubstring = rawString.substring(firstBrace, lastBrace + 1)
        Log.d(TAG, "[PARSER] JSON isolé pour l'analyse: $jsonSubstring")

        return try {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(jsonSubstring, type)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "[PARSER] Erreur de syntaxe Gson pour le type ${T::class.java.simpleName}: '$jsonSubstring'", e)
            throw IOException(getString(R.string.error_json_malformed_or_incomplete), e)
        } catch (e: Exception) {
            Log.e(TAG, "[PARSER] Erreur inattendue lors du parsing pour le type ${T::class.java.simpleName}: '$jsonSubstring'", e)
            throw IOException(getString(R.string.error_json_malformed_or_incomplete), e)
        }
    }

    // BOURDON'S REFORGE (Performance): Wrapper pour l'inférence avec chronométrage et throttling.
    private suspend fun executeStreamingInferenceAndCaptureMetrics(
        prompt: String,
        images: List<Bitmap>,
        uiUpdateCallback: (String) -> Unit
    ): Pair<String, PerformanceMetrics> {
        return suspendCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            var firstTokenTime: Long? = null
            val responseBuilder = StringBuilder()

            streamingUpdateJob = viewModelScope.launch(Dispatchers.Main) {
                while(isActive) {
                    uiUpdateCallback(responseBuilder.toString())
                    delay(150) // Limite les mises à jour UI
                }
            }

            llmHelper.runInference(prompt, images) { partialResult, done ->
                if (partialResult.isNotBlank() && firstTokenTime == null) {
                    firstTokenTime = System.currentTimeMillis()
                }
                responseBuilder.append(partialResult)

                if (done) {
                    streamingUpdateJob?.cancel()
                    // Dernière mise à jour pour s'assurer que tout le texte est affiché
                    viewModelScope.launch(Dispatchers.Main) {
                        uiUpdateCallback(responseBuilder.toString())
                    }

                    if (continuation.context.isActive) {
                        val endTime = System.currentTimeMillis()
                        val totalTimeMs = endTime - startTime
                        val ttftMs = firstTokenTime?.let { it - startTime }
                        val totalChars = responseBuilder.length.toFloat()
                        val tokensPerSecond = if (totalTimeMs > 0) {
                            (totalChars / 4.0f) / (totalTimeMs / 1000.0f)
                        } else {
                            0.0f
                        }
                        val metrics = PerformanceMetrics(ttftMs, totalTimeMs, tokensPerSecond)
                        continuation.resume(Pair(responseBuilder.toString(), metrics))
                    }
                }
            }
        }
    }


    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }

    override fun onCleared() {
        super.onCleared()
        competitionJob?.cancel()
        judgmentJob?.cancel()
        streamingUpdateJob?.cancel()
        llmHelper.cleanUp()
    }
}