
// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/forge/ForgeWorkshopViewModel.kt ---
package be.heyman.android.ai.kikko.forge

import android.app.Application
import android.content.Context
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
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException

// BOURDON'S ADDITION: Data classes pour le résumé de la compétition
data class CompetitionSummaryItem(
    val response: String,
    val voteCount: Int,
    val firstValidTask: AnalysisResult // On garde une tâche source pour la validation
)

data class CompetitionSummary(
    val propertyName: String,
    val items: List<CompetitionSummaryItem>
)

data class ForgeWorkshopUiState(
    val isLoading: Boolean = true,
    val workshopGrains: List<PollenGrain> = emptyList(),
    val selectedGrain: PollenGrain? = null,
    val selectedCard: KnowledgeCard? = null,
    val analysisResults: Map<String, List<AnalysisResult>> = emptyMap(),
    // BOURDON'S ADDITION: Le champ pour stocker les résumés
    val competitionSummaries: Map<String, CompetitionSummary> = emptyMap(),
    val statusMessage: String? = null,
    // BOURDON'S REFACTOR: Le filtre par défaut est maintenant RAW.
    val activeFilter: String = ForgeWorkshopViewModel.FILTER_RAW
)

class ForgeWorkshopViewModel(application: Application) : AndroidViewModel(application) {

    private val forgeRepository: ForgeRepository = (application as KikkoApplication).forgeRepository
    private val llmHelper: ForgeLlmHelper = (application as KikkoApplication).forgeLlmHelper
    private val pollenGrainDao: PollenGrainDao = (application as KikkoApplication).pollenGrainDao

    // BOURDON'S LOGGING: Un TAG unique et descriptif pour filtrer les logs de ce ViewModel.
    private val TAG = "KikkoForgeTrace"
    private val _uiState = MutableStateFlow(ForgeWorkshopUiState())
    val uiState = _uiState.asStateFlow()
    private val gson = Gson()

    private val runningTasks = mutableMapOf<String, Job>()

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
        // BOURDON'S REFACTOR: FILTER_ALL n'est plus utilisé par l'UI mais gardé pour la logique.
        const val FILTER_ALL = "ALL"
        const val FILTER_RAW = "RAW"
    }

    init {
        // BOURDON'S LOGGING: Trace l'initialisation du ViewModel.
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
        // BOURDON'S LOGGING: Trace la logique de récupération des propriétés pour un deck.
        Log.d(TAG, "[PROPERTIES] Demande pour deck '$deckName', normalisé en '$normalizedDeckName'.")
        val properties = deckProperties[normalizedDeckName] ?: emptyList()
        Log.d(TAG, "[PROPERTIES] Propriétés trouvées: ${properties.joinToString(", ")}")
        return properties
    }


    fun selectGrain(grain: PollenGrain) {
        viewModelScope.launch {
            cancelAllRunningTasks()
            // BOURDON'S LOGGING: Trace la sélection d'un nouveau grain, une action utilisateur majeure.
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
                statusMessage = newStatusMessage
            ) }

            if (card != null) {
                // BOURDON'S LOGGING: Confirme le chargement réussi de la carte associée.
                Log.i(TAG, "[SELECT] Carte chargée depuis la cache - ID: ${card.id}, Nom: '${card.specificName}', Deck: '${card.deckName}'")
            } else {
                // BOURDON'S LOGGING: Signale un cas potentiellement problématique.
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
            if (_uiState.value.activeFilter == filterType) {
                Log.d(TAG, "[FILTER] Filtre '$filterType' déjà actif. Aucune action.")
                return@launch
            }

            _uiState.update { it.copy(activeFilter = filterType) }
            updateFilteredGrains()
        }
    }

    private fun updateFilteredGrains() {
        val currentFilter = _uiState.value.activeFilter
        // BOURDON'S LOGGING: Trace quelle action de filtrage est effectuée.
        Log.d(TAG, "[FILTER] Application du filtre: $currentFilter")

        val filteredItems = when (currentFilter) {
            FILTER_RAW -> allWorkshopItems.filter { (grain, _) ->
                grain.status == PollenStatus.RAW ||
                        grain.status == PollenStatus.IDENTIFYING ||
                        grain.status == PollenStatus.AWAITING_VALIDATION ||
                        grain.status == PollenStatus.ERROR
            }
            // BOURDON'S REFACTOR: Tous les autres filtres sont maintenant des noms de deck.
            else -> allWorkshopItems.filter { (_, card) -> card?.deckName == currentFilter }
        }

        val filteredGrains = filteredItems.map { it.first }
        Log.d(TAG, "[FILTER] ${filteredGrains.size} grains correspondent au filtre '$currentFilter'.")
        _uiState.update { it.copy(workshopGrains = filteredGrains) }

        val selectedGrainStillVisible = filteredGrains.any { it.id == _uiState.value.selectedGrain?.id }
        if (!selectedGrainStillVisible) {
            Log.d(TAG, "[FILTER] Le grain sélectionné n'est plus visible. Sélection du premier grain de la nouvelle liste.")
            val newGrainToSelect = filteredGrains.firstOrNull()
            if (newGrainToSelect != null) {
                selectGrain(newGrainToSelect)
            } else {
                Log.w(TAG, "[FILTER] La liste filtrée est vide. Désélection de tout grain.")
                _uiState.update { it.copy(selectedGrain = null, selectedCard = null, statusMessage = getString(R.string.workshop_no_grains_in_deck)) }
            }
        }
    }


    fun createAnalysisTournament(propertyName: String) {
        val grain = _uiState.value.selectedGrain ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = getString(R.string.workshop_preparing_competition, propertyName)) }

            val modelsToCompete = withContext(Dispatchers.IO) {
                val modelsDir = File(getApplication<Application>().filesDir, "imported_models")
                if (modelsDir.exists() && modelsDir.isDirectory) {
                    modelsDir.listFiles { _, name -> name.endsWith(".task") }?.map { it.name } ?: emptyList()
                } else {
                    emptyList()
                }
            }

            if (modelsToCompete.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, statusMessage = getString(R.string.workshop_no_queens_installed)) }
                return@launch
            }
            // BOURDON'S LOGGING: Liste les modèles qui vont participer au tournoi.
            Log.i(TAG, "Reines en compétition: $modelsToCompete")

            val accelerator = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU") ?: "GPU"

            // BOURDON'S FIX: Le ViewModel délègue la création des tâches au Repository.
            val tasks = forgeRepository.createAnalysisTasksForProperty(grain.id, propertyName, modelsToCompete, accelerator)

            refreshAnalysisResults(grain.id, propertyName)
            val message = getApplication<Application>().resources.getQuantityString(R.plurals.workshop_tasks_ready, tasks.size, tasks.size)
            _uiState.update { it.copy(isLoading = false, statusMessage = message) }
        }
    }

    /**
     * BOURDON'S FIX: Nouvelle méthode pour relancer une compétition.
     * Elle supprime d'abord les anciens résultats, puis en crée de nouveaux.
     */
    fun relaunchAnalysisTournament(propertyName: String) {
        val grain = _uiState.value.selectedGrain ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Clearing old results for '$propertyName'...") }
            forgeRepository.clearAnalysisResultsForProperty(grain.id, propertyName)
            refreshAnalysisResults(grain.id, propertyName) // Met à jour l'UI pour montrer que c'est vide

            // Lance la création comme avant
            createAnalysisTournament(propertyName)
        }
    }


    fun validateFromSummary(summaryItem: CompetitionSummaryItem) {
        // BOURDON'S LOGGING: Trace l'action de validation à partir d'un résumé, une optimisation UX.
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
                // BOURDON'S LOGGING: Affiche la réponse brute avant le parsing, essentiel pour le débogage.
                Log.d(TAG, "[VALIDATE-ID] Réponse brute de la Reine à parser: $rawResponse")
                var resultData = parseIntelligentJson<IdentificationResultData>(rawResponse)

                if (resultData == null) {
                    // BOURDON'S LOGGING: Trace la tentative de fallback si le parsing initial échoue.
                    Log.w(TAG, "[VALIDATE-ID] Parsing JSON intelligent a échoué. Tentative de fallback avec Regex.")
                    val name = """"specificName"\s*:\s*"(.*?)"""".toRegex().find(rawResponse)?.groups?.get(1)?.value
                    val deck = """"(deckName|DeckName)"\s*:\s*"(.*?)"""".toRegex(RegexOption.IGNORE_CASE).find(rawResponse)?.groups?.get(2)?.value

                    if (name != null && deck != null) {
                        Log.i(TAG, "[VALIDATE-ID] Fallback Regex a réussi! Nom: '$name', Deck: '$deck'")
                        resultData = IdentificationResultData(
                            specificName = name, deckName = deck,
                            reasoning = Reasoning(getString(R.string.fallback_reasoning_visual), getString(R.string.fallback_reasoning_correlation)),
                            confidence = 0.5f
                        )
                    } else {
                        throw IOException(getString(R.string.error_validation_parsing_failed))
                    }
                }
                // BOURDON'S LOGGING: Affiche les données extraites après un parsing réussi.
                Log.i(TAG, "[VALIDATE-ID] Parsing réussi. Données extraites: Nom='${resultData.specificName}', Deck='${resultData.deckName}'")

                var cardId = grain.forgedCardId
                if (cardId == null) {
                    // BOURDON'S LOGGING: Détecte et signale un cas important : la création d'une nouvelle carte.
                    Log.w(TAG, "[VALIDATE-ID] Grain orphelin. Création d'une nouvelle carte.")
                    val newCard = KnowledgeCard(
                        specificName = resultData.specificName, deckName = resultData.deckName,
                        imagePath = grain.pollenImagePaths.firstOrNull(), confidence = resultData.confidence,
                        reasoning = resultData.reasoning, description = null, stats = null, quiz = null, translations = null,
                        scientificName = null, vernacularName = null, allergens = null, ingredients = null
                    )
                    cardId = (getApplication<Application>().applicationContext as KikkoApplication).cardDao.insert(newCard)
                    pollenGrainDao.updateForgingResult(grain.id, grain.status, cardId)
                } else {
                    (getApplication<Application>().applicationContext as KikkoApplication).cardDao.updateIdentification(
                        cardId = cardId, specificName = resultData.specificName, deckName = resultData.deckName,
                        reasoning = resultData.reasoning, confidence = resultData.confidence
                    )
                }

                pollenGrainDao.updateStatus(grain.id, PollenStatus.PENDING_DESCRIPTION)
                // BOURDON'S LOGGING: Confirme le changement de statut, étape clé du workflow.
                Log.i(TAG, "[VALIDATE-ID] Statut du grain ID ${grain.id} passé à PENDING_DESCRIPTION.")

                withContext(Dispatchers.Main) {
                    loadWorkshopGrains()
                }

            } catch (e: Exception) {
                // BOURDON'S LOGGING: Capture et logue les erreurs critiques.
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
                Log.i(TAG, "[VALIDATE-PROP] Propriété '${task.propertyName}' mise à jour pour la carte ID $cardId.")

                val nextStatus = getNextStatus(grain.status)
                pollenGrainDao.updateStatus(grain.id, nextStatus)
                Log.i(TAG, "[VALIDATE-PROP] Statut du grain ID ${grain.id} passé à $nextStatus.")

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

    fun runAnalysisTask(task: AnalysisResult) {
        if (runningTasks.containsKey(task.id)) {
            Log.w(TAG, "La tâche ${task.id} est déjà en cours.")
            return
        }

        val job = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Lancement de la tâche ${task.id} pour la propriété '${task.propertyName}'.")
            updateTaskStatusInUi(task.id, task.propertyName) { it.copy(status = AnalysisStatus.RUNNING, streamingResponse = "") }

            try {
                if (task.propertyName != "identification" && (_uiState.value.selectedGrain?.status == PollenStatus.AWAITING_VALIDATION || _uiState.value.selectedGrain?.status == PollenStatus.IDENTIFYING)) {
                    throw IllegalStateException(getString(R.string.error_identification_not_validated))
                }

                var dependencyDataJson: String? = null
                if (task.propertyName == "allergens") {
                    val ingredientTasks = forgeRepository.getAnalysisResults(task.pollenGrainId, "ingredients")
                    val completedIngredientResult = ingredientTasks.find { it.status == AnalysisStatus.COMPLETED }
                    if (completedIngredientResult?.rawResponse == null) {
                        throw IllegalStateException(getString(R.string.error_ingredients_not_forged))
                    }
                    dependencyDataJson = completedIngredientResult.rawResponse
                    Log.d(TAG, "Dépendance satisfaite: Ingrédients trouvés pour l'analyse des allergènes.")
                }

                val config = gson.fromJson(task.modelConfigJson, ModelConfiguration::class.java)
                val modelFile = File(getApplication<Application>().filesDir, "imported_models").resolve(config.modelName)
                if (!modelFile.exists()) throw IOException(getString(R.string.error_queen_model_file_not_found_format, config.modelName))

                val isMultimodalTask = task.propertyName == "identification" || task.propertyName == "description"
                val queenModel = Model(
                    name = config.modelName,
                    url = modelFile.absolutePath,
                    downloadFileName = "",
                    sizeInBytes = 0,
                    llmSupportImage = config.modelName.contains("gemma-3n", ignoreCase = true)
                )

                Log.i(TAG, "Initialisation complète de la Reine IA '${config.modelName}' pour la tâche ${task.id}...")
                val initError = llmHelper.initialize(queenModel, config.accelerator, isMultimodalTask)
                if (initError != null) throw RuntimeException(getString(R.string.error_queen_init_failed_format, initError))
                Log.i(TAG, "Reine IA initialisée avec succès.")

                val parentGrain = _uiState.value.selectedGrain ?: throw IllegalStateException(getString(R.string.error_pollen_parent_not_found))
                val swarmReportJson = parentGrain.swarmAnalysisReportJson ?: throw IOException(getString(R.string.error_swarm_report_missing))
                val images = if (isMultimodalTask) {
                    parentGrain.pollenImagePaths.mapNotNull { BitmapFactory.decodeFile(it) }
                } else {
                    emptyList()
                }

                val prompt = when(task.propertyName) {
                    "identification" -> ForgePromptGenerator.generateIdentificationTournamentPrompt(swarmReportJson)
                    else -> {
                        val card = _uiState.value.selectedCard ?: throw IllegalStateException(getString(R.string.error_card_not_found_for_refinement))
                        ForgePromptGenerator.generatePropertyForgePrompt(
                            propertyName = task.propertyName,
                            deckName = card.deckName,
                            specificName = card.specificName,
                            swarmReportJson = swarmReportJson,
                            existingDescription = card.description,
                            dependencyDataJson = dependencyDataJson
                        )
                    }
                }

                val responseBuilder = StringBuilder()
                val uiUpdateBuffer = StringBuilder()
                val updateThreshold = 50

                llmHelper.runInferenceWithConfig(prompt, images, config) { partialResult, done ->
                    responseBuilder.append(partialResult)
                    uiUpdateBuffer.append(partialResult)

                    if (uiUpdateBuffer.length >= updateThreshold || done) {
                        val currentFullText = responseBuilder.toString()
                        viewModelScope.launch(Dispatchers.Main) {
                            updateTaskStatusInUi(task.id, task.propertyName) {
                                it.copy(streamingResponse = currentFullText)
                            }
                        }
                        uiUpdateBuffer.clear()
                    }

                    if (done) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val finalResponse = responseBuilder.toString()
                                Log.i(TAG, "Réponse finale BRUTE pour la tâche ${task.id} (${task.propertyName}):\n$finalResponse")
                                val updatedTask = task.copy(status = AnalysisStatus.COMPLETED, rawResponse = finalResponse, streamingResponse = null)
                                forgeRepository.updateAnalysisResult(updatedTask)
                                withContext(Dispatchers.Main) {
                                    refreshAnalysisResults(task.pollenGrainId, task.propertyName)
                                }
                            } finally {
                                Log.i(TAG, "Nettoyage des ressources de la Reine IA après succès pour la tâche ${task.id}.")
                                llmHelper.cleanUp()
                                runningTasks.remove(task.id)
                            }
                        }
                    }
                }

            } catch (e: CancellationException) {
                Log.i(TAG, "Tâche ${task.id} annulée par l'utilisateur.")
                llmHelper.cleanUp()
                runningTasks.remove(task.id)
                updateTaskStatusInDb(task.id, task.pollenGrainId, task.propertyName, AnalysisStatus.CANCELLED)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Échec de l'exécution de la tâche ${task.id}", e)
                llmHelper.cleanUp()
                runningTasks.remove(task.id)
                val updatedTask = task.copy(status = AnalysisStatus.FAILED, errorMessage = e.message)
                forgeRepository.updateAnalysisResult(updatedTask)
                withContext(Dispatchers.Main) {
                    refreshAnalysisResults(task.pollenGrainId, task.propertyName)
                }
            }
        }
        runningTasks[task.id] = job
    }

    fun cancelAnalysisTask(task: AnalysisResult) {
        runningTasks[task.id]?.let {
            Log.i(TAG, "Demande d'annulation pour la tâche ${task.id}.")
            it.cancel()
        }
    }

    fun retryAnalysisTask(task: AnalysisResult) {
        if (task.status != AnalysisStatus.FAILED) return
        Log.i(TAG, "Tentative de relance pour la tâche échouée ${task.id}.")
        runAnalysisTask(task.copy(status = AnalysisStatus.PENDING, rawResponse = null, errorMessage = null))
    }

    fun deleteSelectedGrain() {
        val grainToDelete = _uiState.value.selectedGrain ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                (getApplication<Application>() as KikkoApplication).forgeRepository.deletePollenGrainAndAssociatedData(grainToDelete)
            }
            loadWorkshopGrains()
        }
    }

    private suspend fun updateTaskStatusInDb(taskId: String, grainId: String, propertyName: String, newStatus: AnalysisStatus) {
        val currentTask = forgeRepository.getAnalysisResults(grainId, propertyName).find { it.id == taskId }
        if (currentTask != null) {
            val updatedTask = currentTask.copy(status = newStatus)
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

    private fun cancelAllRunningTasks() {
        if (runningTasks.isNotEmpty()) {
            Log.w(TAG, "Annulation de ${runningTasks.size} tâches en cours en raison d'un changement de contexte.")
            runningTasks.values.forEach { it.cancel() }
            runningTasks.clear()
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
            _uiState.update {
                it.copy(
                    selectedGrain = null,
                    selectedCard = null,
                    statusMessage = getString(R.string.workshop_no_grains_to_forge)
                )
            }
        }
    }

    private suspend fun refreshAnalysisResults(pollenGrainId: String, propertyName: String) {
        val results = forgeRepository.getAnalysisResults(pollenGrainId, propertyName)
        _uiState.update { currentState ->
            val newAnalysisMap = currentState.analysisResults.toMutableMap().apply { this[propertyName] = results }
            val newSummariesMap = currentState.competitionSummaries.toMutableMap().apply {
                val summary = createCompetitionSummary(propertyName, results)
                if (summary.items.isNotEmpty()) {
                    this[propertyName] = summary
                } else {
                    this.remove(propertyName)
                }
            }
            currentState.copy(analysisResults = newAnalysisMap, competitionSummaries = newSummariesMap)
        }
    }

    private fun createCompetitionSummary(propertyName: String, results: List<AnalysisResult>): CompetitionSummary {
        val completedTasks = results.filter { it.status == AnalysisStatus.COMPLETED && !it.rawResponse.isNullOrBlank() }
        if (completedTasks.isEmpty()) {
            return CompetitionSummary(propertyName, emptyList())
        }

        val responseGroups = completedTasks.groupBy { task ->
            extractValueFromResponse(task.rawResponse!!, propertyName)
        }.filterKeys { it.isNotBlank() }

        val summaryItems = responseGroups.map { (response, tasks) ->
            CompetitionSummaryItem(
                response = response,
                voteCount = tasks.size,
                firstValidTask = tasks.first()
            )
        }.sortedByDescending { it.voteCount }
        // BOURDON'S LOGGING: Logue la création du résumé pour suivre la logique de regroupement.
        Log.d(TAG, "[SUMMARY] Création du résumé pour '$propertyName': ${summaryItems.size} proposition(s) unique(s) trouvée(s).")
        return CompetitionSummary(propertyName, summaryItems)
    }

    private fun extractValueFromResponse(rawResponse: String, propertyName: String): String {
        // BOURDON'S FIX: La logique de fallback par Regex est maintenant ici.
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
            Log.w(TAG, "Le parsing JSON a échoué pour '$propertyName', tentative de fallback. Erreur: ${e.message}")
        }

        // Fallback pour l'identification
        if (propertyName == "identification") {
            val name = """"specificName"\s*:\s*"(.*?)"""".toRegex().find(rawResponse)?.groups?.get(1)?.value
            val deck = """"(deckName|DeckName)"\s*:\s*"(.*?)"""".toRegex(RegexOption.IGNORE_CASE).find(rawResponse)?.groups?.get(2)?.value
            if (name != null && deck != null) {
                Log.i(TAG, "[EXTRACT-REGEX] Fallback réussi pour identification: '$deck: $name'")
                return "$deck: $name"
            }
        }

        Log.w(TAG, "Impossible d'extraire la valeur pour '$propertyName' de la réponse: $rawResponse")
        return ""
    }


    private inline fun <reified T> parseIntelligentJson(rawString: String): T? {
        val firstBrace = rawString.indexOf('{')
        if (firstBrace == -1) {
            Log.e(TAG, "[PARSER] Aucun objet JSON trouvé dans la réponse brute.")
            return null
        }

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

        if (lastBrace == -1) {
            Log.e(TAG, "[PARSER] L'objet JSON dans la réponse brute est incomplet (accolades non fermées).")
            return null
        }

        val jsonSubstring = rawString.substring(firstBrace, lastBrace + 1)
        // BOURDON'S LOGGING: Affiche le JSON exact qui sera parsé.
        Log.d(TAG, "[PARSER] JSON isolé pour l'analyse: $jsonSubstring")


        return try {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(jsonSubstring, type)
        } catch (e: JsonSyntaxException) {
            // BOURDON'S LOGGING: Logue spécifiquement les erreurs de syntaxe JSON.
            Log.e(TAG, "[PARSER] Erreur de syntaxe Gson pour le type ${T::class.java.simpleName}: '$jsonSubstring'", e)
            throw IOException(getString(R.string.error_json_malformed_or_incomplete), e)
        } catch (e: Exception) {
            // BOURDON'S LOGGING: Logue toute autre erreur de parsing inattendue.
            Log.e(TAG, "[PARSER] Erreur inattendue lors du parsing pour le type ${T::class.java.simpleName}: '$jsonSubstring'", e)
            throw IOException(getString(R.string.error_json_malformed_or_incomplete), e)
        }
    }

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllRunningTasks()
        llmHelper.cleanUp()
    }
}
// --- END OF FILE app/src/main/java/be/heyman/android/ai/kikko/forge/ForgeWorkshopViewModel.kt ---
