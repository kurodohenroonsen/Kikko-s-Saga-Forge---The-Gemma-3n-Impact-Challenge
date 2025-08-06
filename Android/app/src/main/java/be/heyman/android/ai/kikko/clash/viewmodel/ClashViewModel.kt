// app/src/main/java/be/heyman/android/ai/kikko/clash/viewmodel/ClashViewModel.kt

package be.heyman.android.ai.kikko.clash.viewmodel

import android.app.Application
import android.content.Context
import android.location.Location
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import be.heyman.android.ai.kikko.GameConstants
import be.heyman.android.ai.kikko.KikkoApplication
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.ToolsDialogFragment
import be.heyman.android.ai.kikko.clash.data.ClashSettings
import be.heyman.android.ai.kikko.clash.data.ClashState
import be.heyman.android.ai.kikko.clash.data.ClashStatus
import be.heyman.android.ai.kikko.clash.data.Deck
import be.heyman.android.ai.kikko.clash.data.P2pPayload
import be.heyman.android.ai.kikko.clash.data.PlayerCatalogue
import be.heyman.android.ai.kikko.forge.ForgeLlmHelper
import be.heyman.android.ai.kikko.clash.helpers.ClashPromptGenerator
import be.heyman.android.ai.kikko.TtsService
import be.heyman.android.ai.kikko.clash.services.ClashArenaService
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.persistence.CardDao
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

class ClashViewModel(application: Application) : AndroidViewModel(application), ClashArenaService.ClashArenaListener {

    private val cardDao: CardDao = (application as KikkoApplication).cardDao
    private val llmHelper: ForgeLlmHelper = (application as KikkoApplication).forgeLlmHelper
    private val arenaService: ClashArenaService = (application as KikkoApplication).clashArenaService

    private val TAG = "ClashViewModel"
    private val gson = Gson()

    private val _uiState = MutableStateFlow(ClashUiState())
    val uiState = _uiState.asStateFlow()

    private val _p2pEvent = MutableSharedFlow<P2pEvent>()
    val p2pEvent = _p2pEvent.asSharedFlow()
    sealed class P2pEvent {
        data class ShowConnectionDialog(val endpointId: String, val opponentName: String, val authCode: String) : P2pEvent()
    }

    private var inferenceJob: Job? = null
    private var judgeInitializationJob: Job? = null
    private val pendingImageTransfers = mutableMapOf<Long, KnowledgeCard>()
    private var allCardsCache: List<KnowledgeCard> = emptyList()

    init {
        arenaService.setListener(this)
        TtsService.initialize(getApplication())
        ClashPromptGenerator.loadClashQuestions(getApplication())
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                allCardsCache = cardDao.getAll()
            }
            loadAvailableModelsAndInitializeJudge()
        }
    }

    suspend fun getCardsForDeck(deckName: String): List<KnowledgeCard> {
        return withContext(Dispatchers.Default) {
            allCardsCache.filter { it.deckName == deckName }
        }
    }

    fun selectCardForClash(card: KnowledgeCard, isPlayerOne: Boolean) {
        val deckName = card.deckName
        handleChampionSelection(deckName, card, if (isPlayerOne) 1 else 2)
    }

    fun handleChampionSelection(deckName: String, card: KnowledgeCard, playerIndex: Int = 1) {
        if (_uiState.value.clashMode != ClashMode.SOLO && playerIndex == 1) {
            sendCardSelectionToOpponent(deckName, card)
        }

        _uiState.update { currentState ->
            val updatedChampions = if (playerIndex == 1) {
                currentState.myChampions.toMutableMap()
            } else {
                currentState.opponentChampions.toMutableMap()
            }
            updatedChampions[deckName] = card

            if (playerIndex == 1) {
                currentState.copy(myChampions = updatedChampions)
            } else {
                currentState.copy(opponentChampions = updatedChampions)
            }
        }
        checkReadyState()
    }

    private fun sendCardSelectionToOpponent(deckName: String, card: KnowledgeCard) {
        _uiState.value.connectedEndpointId?.let { endpointId ->
            var imagePayloadId: Long? = null
            card.imagePath?.let { path ->
                val imageFile = File(path)
                if (imageFile.exists()) {
                    val filePayload = Payload.fromFile(imageFile)
                    imagePayloadId = filePayload.id
                    Log.i(TAG, "[P2P SEND] Envoi du payload image pour '${card.specificName}' (Payload ID: ${filePayload.id}) vers $endpointId")
                    arenaService.sendPayload(endpointId, filePayload)
                }
            }
            val lightweightCard = card.copy(imagePath = null)
            val metadataPayload = P2pPayload.CardSelectionPayload(deckName, lightweightCard, imagePayloadId)
            val payloadJson = gson.toJson(metadataPayload)
            Log.i(TAG, "[P2P SEND] Envoi du payload de métadonnées pour '${card.specificName}' (associé à l'image ID: $imagePayloadId) vers $endpointId")
            arenaService.sendPayload(endpointId, payloadJson)
        }
    }

    fun startP2PDiscovery(myLocation: Location?) {
        viewModelScope.launch {
            _uiState.update { it.copy(clashMode = ClashMode.P2P_DISCOVERING, discoveredPlayers = emptyMap()) }
            val myCatalogue = withContext(Dispatchers.IO) {
                val decks = allCardsCache.groupBy { it.deckName }.map { (deckName, cards) -> Deck(deckName, cards.size) }
                val defaultPlayerName = getApplication<Application>().getString(R.string.p2p_default_player_name, (100..999).random())
                PlayerCatalogue(defaultPlayerName, decks, myLocation?.latitude, myLocation?.longitude, 15, 4)
            }
            arenaService.startAdvertising(myCatalogue)
            arenaService.startDiscovery()
        }
    }

    fun stopP2P() {
        arenaService.stopAllEndpoints()
        _uiState.update { it.copy(
            clashMode = ClashMode.SOLO, isArbitrator = false, discoveredPlayers = emptyMap(),
            connectedEndpointId = null, p2pStatus = getApplication<Application>().getString(R.string.p2p_status_inactive)
        )}
    }

    fun connectToPlayer(endpointId: String) {
        _uiState.update { it.copy(isArbitrator = true) }
        arenaService.requestConnection(endpointId)
    }

    fun acceptConnection(endpointId: String) {
        if (!_uiState.value.isArbitrator) {
            _uiState.update { it.copy(isArbitrator = false) }
        }
        arenaService.acceptConnection(endpointId)
    }

    fun rejectConnection(endpointId: String) {
        arenaService.rejectConnection(endpointId)
    }

    override fun onStatusUpdate(message: String) {
        _uiState.update { it.copy(p2pStatus = message) }
    }

    override fun onEndpointFound(endpointId: String, catalogue: PlayerCatalogue) {
        _uiState.update {
            val updatedPlayers = it.discoveredPlayers.toMutableMap().apply { put(endpointId, catalogue) }
            it.copy(discoveredPlayers = updatedPlayers)
        }
    }

    override fun onEndpointLost(endpointId: String) {
        _uiState.update {
            val updatedPlayers = it.discoveredPlayers.toMutableMap().apply { remove(endpointId) }
            it.copy(discoveredPlayers = updatedPlayers)
        }
    }

    override fun onConnectionInitiated(endpointId: String, opponentName: String, authDigits: String) {
        viewModelScope.launch { _p2pEvent.emit(P2pEvent.ShowConnectionDialog(endpointId, opponentName, authDigits)) }
    }

    override fun onConnectionResult(endpointId: String, isSuccess: Boolean) {
        if (isSuccess) {
            _uiState.update { it.copy(
                discoveredPlayers = emptyMap(),
                clashMode = ClashMode.P2P_CARD_SELECTION,
                connectedEndpointId = endpointId,
                p2pStatus = getApplication<Application>().getString(R.string.p2p_status_connected)
            )}
        } else {
            _uiState.update { it.copy(
                p2pStatus = getApplication<Application>().getString(R.string.p2p_status_connection_failed),
                clashMode = ClashMode.P2P_DISCOVERING,
                isArbitrator = false)
            }
        }
    }

    override fun onDisconnected(endpointId: String) {
        _uiState.update { it.copy(
            connectedEndpointId = null,
            p2pStatus = getApplication<Application>().getString(R.string.p2p_status_disconnected),
            clashMode = ClashMode.SOLO)
        }
    }

    override fun onPayloadReceived(endpointId: String, payloadString: String) {
        try {
            val jsonObject = JsonParser.parseString(payloadString).asJsonObject
            when {
                jsonObject.has("selectedCard") -> handleCardSelectionPayload(payloadString)
                jsonObject.has("question") -> handleQuestionPayload(payloadString)
                jsonObject.has("winner") -> handleDuelResultPayload(payloadString)
                jsonObject.has("command") -> {
                    val command = jsonObject.get("command").asString
                    Log.i(TAG, "[P2P RECV] Commande '$command' reçue de $endpointId.")
                    when (command) {
                        "START_CLASH" -> startDuels()
                        "NEXT_DUEL" -> proceedToNextDuel()
                        "PREVIOUS_DUEL" -> proceedToPreviousDuel()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de désérialisation du payload", e)
        }
    }

    override fun onFilePayloadReceived(endpointId: String, payload: Payload) {
        Log.i(TAG, "[P2P RECV] Fichier complet reçu (Payload ID: ${payload.id}). Traitement de l'image...")
        val cardToUpdate = pendingImageTransfers.remove(payload.id)
        if (cardToUpdate != null) {
            processAndSaveImage(payload, cardToUpdate)
        } else {
            Log.w(TAG, "[P2P RECV] Fichier reçu (Payload ID: ${payload.id}) mais aucune carte en attente trouvée.")
        }
    }

    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        if (update.status == PayloadTransferUpdate.Status.FAILURE || update.status == PayloadTransferUpdate.Status.CANCELED) {
            Log.e(TAG, "[P2P RECV] Echec du transfert pour le payload ID ${update.payloadId}. Annulation de l'attente.")
            pendingImageTransfers.remove(update.payloadId)
            checkReadyState()
        }
    }

    private fun handleCardSelectionPayload(payloadString: String) {
        val payload = gson.fromJson(payloadString, P2pPayload.CardSelectionPayload::class.java)
        if (payload.imagePayloadId != null) {
            Log.i(TAG, "[P2P RECV] Métadonnées reçues pour '${payload.selectedCard.specificName}'. En attente de l'image (Payload ID: ${payload.imagePayloadId}).")
            pendingImageTransfers[payload.imagePayloadId] = payload.selectedCard
        } else {
            Log.i(TAG, "[P2P RECV] Métadonnées reçues pour '${payload.selectedCard.specificName}'. Aucune image associée.")
        }
        _uiState.update { it.copy(opponentChampions = it.opponentChampions.toMutableMap().apply { set(payload.deckName, payload.selectedCard) }) }
        checkReadyState()
    }

    private fun processAndSaveImage(filePayload: Payload, card: KnowledgeCard) {
        viewModelScope.launch(Dispatchers.IO) {
            val pfd: ParcelFileDescriptor? = filePayload.asFile()?.asParcelFileDescriptor()
            if (pfd != null) {
                val inputStream = FileInputStream(pfd.fileDescriptor)
                val destDir = File(getApplication<Application>().filesDir, "clash_images")
                if (!destDir.exists()) destDir.mkdirs()
                val newFile = File(destDir, "clash_${card.id}_${System.currentTimeMillis()}.png")
                try {
                    FileOutputStream(newFile).use { it.write(inputStream.readBytes()) }
                    Log.i(TAG, "[P2P RECV] Image pour '${card.specificName}' sauvegardée vers : ${newFile.absolutePath}")
                } finally {
                    inputStream.close()
                    pfd.close()
                }
                val updatedCard = card.copy(imagePath = newFile.absolutePath)
                withContext(Dispatchers.Main) {
                    _uiState.update { state ->
                        val key = state.opponentChampions.entries.find { it.value.id == card.id }?.key
                        state.copy(opponentChampions = state.opponentChampions.toMutableMap().apply { if(key!=null) set(key, updatedCard) })
                    }
                    checkReadyState()
                }
            }
        }
    }

    private fun checkReadyState() {
        _uiState.update { currentState ->
            val isReady = currentState.myChampions.size == GameConstants.MASTER_DECK_LIST.size &&
                    currentState.opponentChampions.size == GameConstants.MASTER_DECK_LIST.size

            var newDialogState = currentState.dialogState
            if (currentState.clashMode != ClashMode.SOLO) {
                val allImagesReady = pendingImageTransfers.isEmpty()
                if (isReady && allImagesReady) {
                    Log.i(TAG, "[P2P STATUS] Toutes les cartes et images sont prêtes. Mode: ${if (currentState.isArbitrator) "Hôte" else "Invité"}")
                }
                if(isReady && allImagesReady && currentState.dialogState == DialogState.None) {
                    newDialogState = if (currentState.isArbitrator) DialogState.ReadyToClash else DialogState.WaitingForHost
                } else if (!isReady || !allImagesReady) {
                    newDialogState = DialogState.None
                }
            }
            currentState.copy(isReadyToClash = isReady, dialogState = newDialogState)
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = DialogState.None) }
    }

    fun confirmAndStartP2pClash() {
        _uiState.value.connectedEndpointId?.let {
            if (_uiState.value.isArbitrator) {
                arenaService.sendPayload(it, gson.toJson(P2pPayload.StartClashPayload()))
            }
        }
        startDuels()
    }

    fun confirmSetupAndStartSoloClash() {
        if (!_uiState.value.isReadyToClash) return
        if (_uiState.value.isJudgeInitializing) {
            _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.judge_awakening_please_wait)) }
            return
        }
        _uiState.update { it.copy(clashMode = ClashMode.SOLO) }
        startDuels()
    }

    private fun startDuels() {
        viewModelScope.launch {
            judgeInitializationJob?.join()
            if (_uiState.value.errorMessage != null) {
                return@launch
            }

            _uiState.update { currentState ->
                val duelStates = GameConstants.MASTER_DECK_LIST.mapNotNull { deck ->
                    val p1 = currentState.myChampions[deck]
                    val p2 = currentState.opponentChampions[deck]
                    if (p1 != null && p2 != null) ClashState(deck, p1, p2) else null
                }
                currentState.copy(flowState = ClashFlowState.DUELING, clashStates = duelStates, currentDuelIndex = 0, dialogState = DialogState.None)
            }
        }
    }

    fun runInferenceForCurrentDuel() {
        if (!_uiState.value.isArbitrator && _uiState.value.clashMode != ClashMode.SOLO) return
        inferenceJob?.cancel()
        val clashIndex = _uiState.value.currentDuelIndex.takeIf { it != -1 } ?: return
        val clashState = _uiState.value.clashStates.getOrNull(clashIndex)?.takeIf { it.status == ClashStatus.PENDING } ?: return

        inferenceJob = viewModelScope.launch(Dispatchers.IO) {
            val settings = _uiState.value.clashSettings ?: return@launch
            val questionSet = ClashPromptGenerator.getRandomClashQuestionSet(clashState.deckName)!!

            withContext(Dispatchers.Main) {
                updateClashState(clashIndex) { it.copy(status = ClashStatus.INFERRING, question = ClashPromptGenerator.getLocalizedQuestion(questionSet)) }
                _uiState.value.connectedEndpointId?.let {
                    arenaService.sendPayload(it, gson.toJson(P2pPayload.QuestionPayload(clashState.deckName, questionSet)))
                }
            }

            try {
                llmHelper.resetSession(
                    model = _uiState.value.selectedModel!!,
                    isMultimodal = false,
                    temperature = settings.temperature,
                    topK = 40
                )
                val prompt = ClashPromptGenerator.generateClashVerdictPrompt(ClashPromptGenerator.getLocalizedQuestion(questionSet), clashState.player1Card, clashState.player2Card)
                Log.d(TAG, "[PROMPT ENVOYÉ AU JUGE]:\n$prompt")
                val responseBuilder = StringBuilder()

                llmHelper.runInference(prompt, emptyList()) { partial, done ->
                    responseBuilder.append(partial)

                    viewModelScope.launch(Dispatchers.Main) {
                        updateClashState(clashIndex) { it.copy(streamingReasoning = responseBuilder.toString()) }
                    }

                    if (done) {
                        val rawResponse = responseBuilder.toString()
                        Log.d(TAG, "[RÉPONSE BRUTE DU JUGE]:\n$rawResponse")
                        val (winner, reasoning, ttsScript) = parseVerdict(rawResponse)

                        viewModelScope.launch(Dispatchers.Main) {
                            updateClashState(clashIndex) { it.copy(winner = winner, rawReasoning = reasoning, translatedReasoning = reasoning, ttsScript = ttsScript, status = ClashStatus.COMPLETED, streamingReasoning = null) }
                            _uiState.value.connectedEndpointId?.let {
                                val resultPayload = P2pPayload.DuelResultPayload(clashState.deckName, winner, reasoning, ttsScript)
                                arenaService.sendPayload(it, gson.toJson(resultPayload))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMessage = e.message ?: getApplication<Application>().getString(R.string.inference_error_generic)
                    updateClashState(clashIndex) { it.copy(status = ClashStatus.ERROR, errorMessage = errorMessage) }
                }
            }
        }
    }

    private fun handleQuestionPayload(payloadString: String) {
        val payload = gson.fromJson(payloadString, P2pPayload.QuestionPayload::class.java)
        val duelIndex = _uiState.value.clashStates.indexOfFirst { it.deckName == payload.deckName }
        if (duelIndex != -1) updateClashState(duelIndex) { it.copy(question = ClashPromptGenerator.getLocalizedQuestion(payload.question), status = ClashStatus.INFERRING) }
    }

    private fun handleDuelResultPayload(payloadString: String) {
        val payload = gson.fromJson(payloadString, P2pPayload.DuelResultPayload::class.java)
        val duelIndex = _uiState.value.clashStates.indexOfFirst { it.deckName == payload.deckName }
        if (duelIndex != -1) {
            val winner = when(payload.winner) { "player1" -> "player2"; "player2" -> "player1"; else -> "tie" }
            updateClashState(duelIndex) { it.copy(winner = winner, rawReasoning = payload.reasoning, translatedReasoning = payload.reasoning, ttsScript = payload.ttsScript, status = ClashStatus.COMPLETED) }
        }
    }

    private suspend fun loadAvailableModelsAndInitializeJudge() {
        withContext(Dispatchers.IO) {
            val modelsDir = File(getApplication<Application>().filesDir, "imported_models")
            val modelFiles = modelsDir.listFiles { _, name -> name.endsWith(".task") } ?: emptyArray()
            val llmModels = modelFiles.map { Model(name = it.name, downloadFileName = it.name, url = it.absolutePath, sizeInBytes = it.length(), imported = true, llmSupportImage = it.name.contains("gemma-3n", ignoreCase = true)) }.sortedBy { it.name }

            withContext(Dispatchers.Main) {
                val prefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
                val savedModelName = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, null)
                val savedAccelerator = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")!!

                val currentSettings = _uiState.value.clashSettings ?: ClashSettings(
                    queenModelName = savedModelName ?: llmModels.firstOrNull()?.name ?: "",
                    brain = savedAccelerator
                )
                _uiState.update { it.copy(
                    availableModels = llmModels,
                    clashSettings = currentSettings,
                    selectedModel = llmModels.find { m -> m.name == currentSettings.queenModelName }
                )}

                initializeJudge()
            }
        }
    }

    fun updateClashSettings(settings: ClashSettings) {
        val oldSettings = _uiState.value.clashSettings
        if (settings != oldSettings) {
            _uiState.update { it.copy(
                clashSettings = settings,
                selectedModel = it.availableModels.find { m -> m.name == settings.queenModelName }
            ) }
            judgeInitializationJob?.cancel()
            judgeInitializationJob = null
            viewModelScope.launch {
                withContext(Dispatchers.IO) { llmHelper.cleanUp() }
                initializeJudge()
            }
        }
    }

    private fun initializeJudge() {
        if (judgeInitializationJob != null) return

        val settings = _uiState.value.clashSettings ?: return
        val model = _uiState.value.selectedModel ?: run {
            _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.error_no_model_selected)) }
            return
        }

        _uiState.update { it.copy(isJudgeInitializing = true) }
        judgeInitializationJob = viewModelScope.launch {
            Log.i(TAG, "Lancement de l'initialisation du Juge IA pour le Clash...")
            val error = withContext(Dispatchers.IO) {
                llmHelper.initialize(model, settings.brain, isMultimodal = false)
            }
            if (error == null) {
                Log.i(TAG, "Initialisation du Juge IA terminée avec succès pour le Clash.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.judge_ready_toast, model.name), Toast.LENGTH_SHORT).show()
                }
                _uiState.update { it.copy(isJudgeInitializing = false) }
            } else {
                Log.e(TAG, "Échec de l'initialisation du Juge IA: $error")
                _uiState.update { it.copy(isJudgeInitializing = false, errorMessage = getApplication<Application>().getString(R.string.judge_error_toast, error)) }
            }
        }
    }

    fun generateRandomTeams() {
        viewModelScope.launch(Dispatchers.IO) {
            val p1Champions = mutableMapOf<String, KnowledgeCard>()
            val p2Champions = mutableMapOf<String, KnowledgeCard>()
            var errorDeck = ""
            for (deckName in GameConstants.MASTER_DECK_LIST) {
                val cardsInDeck = allCardsCache.filter { it.deckName == deckName }
                if (cardsInDeck.size < 2) { errorDeck = deckName; break }
                val shuffled = cardsInDeck.shuffled()
                p1Champions[deckName] = shuffled[0]
                p2Champions[deckName] = shuffled[1]
            }
            withContext(Dispatchers.Main) {
                if (errorDeck.isNotEmpty()) {
                    _uiState.update { it.copy(errorMessage = getApplication<Application>().getString(R.string.error_not_enough_cards_in_deck, errorDeck)) }
                } else {
                    _uiState.update { it.copy(myChampions = p1Champions, opponentChampions = p2Champions) }
                    checkReadyState()
                }
            }
        }
    }

    fun onDuelSelected(index: Int) {
        if (index != _uiState.value.currentDuelIndex) _uiState.update { it.copy(currentDuelIndex = index) }
    }

    fun proceedToPreviousDuel() {
        if (_uiState.value.isArbitrator || _uiState.value.clashMode == ClashMode.SOLO) {
            _uiState.value.connectedEndpointId?.let { endpointId ->
                val payloadJson = gson.toJson(mapOf("command" to "PREVIOUS_DUEL"))
                Log.i(TAG, "[P2P SEND] Envoi de la commande PREVIOUS_DUEL vers $endpointId")
                arenaService.sendPayload(endpointId, payloadJson)
            }
        }

        val previousIndex = _uiState.value.currentDuelIndex - 1
        if (previousIndex >= 0) {
            _uiState.update { it.copy(currentDuelIndex = previousIndex) }
        }
    }

    fun proceedToNextDuel() {
        if(_uiState.value.isArbitrator || _uiState.value.clashMode == ClashMode.SOLO) {
            _uiState.value.connectedEndpointId?.let { endpointId ->
                val payloadJson = gson.toJson(P2pPayload.NextDuelPayload())
                Log.i(TAG, "[P2P SEND] Envoi de la commande NEXT_DUEL vers $endpointId")
                arenaService.sendPayload(endpointId, payloadJson)
            }
        }
        val nextIndex = _uiState.value.currentDuelIndex + 1
        if (nextIndex < _uiState.value.clashStates.size) {
            _uiState.update { it.copy(currentDuelIndex = nextIndex) }
        } else {
            _uiState.update { it.copy(flowState = ClashFlowState.FINISHED) }
        }
    }

    private fun parseVerdict(rawResponse: String): Triple<String, String, String> {
        return try {
            val cleanJson = rawResponse.substringAfter("{").substringBeforeLast("}")
            val jsonObject = JsonParser.parseString("{$cleanJson}").asJsonObject
            val winner = jsonObject.get("winner")?.asString ?: "tie"
            val reasoning = jsonObject.get("reasoning")?.asString ?: getApplication<Application>().getString(R.string.verdict_parse_reasoning_fallback)
            val ttsScript = jsonObject.get("tts_script")?.asString ?: getApplication<Application>().getString(R.string.verdict_parse_tts_fallback_script)
            Triple(winner, reasoning, ttsScript)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur de parsing du verdict: '$rawResponse'", e)
            Triple("tie", getApplication<Application>().getString(R.string.verdict_parse_error_reasoning), getApplication<Application>().getString(R.string.verdict_parse_error_tts))
        }
    }

    private fun updateClashState(index: Int, updateAction: (ClashState) -> ClashState) {
        _uiState.update { currentState ->
            val updatedClashes = currentState.clashStates.toMutableList()
            if (index in updatedClashes.indices) updatedClashes[index] = updateAction(updatedClashes[index])
            currentState.copy(clashStates = updatedClashes)
        }
    }

    fun requestTtsForQuestion(clashIndex: Int, autoPlay: Boolean = false) {
        val clash = uiState.value.clashStates.getOrNull(clashIndex) ?: return
        val settings = uiState.value.clashSettings
        val questionToSpeak = clash.question

        if (!questionToSpeak.isNullOrBlank() && (settings?.isTtsEnabled == true || !autoPlay) && !clash.ttsQuestionHasBeenPlayed) {
            TtsService.speak(questionToSpeak, Locale.getDefault())
            updateClashState(clashIndex) { it.copy(ttsQuestionHasBeenPlayed = true) }
        }
    }

    fun requestTtsForReasoning(clashIndex: Int, autoPlay: Boolean = false) {
        val clash = uiState.value.clashStates.getOrNull(clashIndex) ?: return
        val settings = uiState.value.clashSettings

        if (!clash.ttsHasBeenPlayed) {
            val scriptToSpeak = clash.ttsScript
            if (clash.status == ClashStatus.COMPLETED && !scriptToSpeak.isNullOrBlank() && (settings?.isTtsEnabled == true || !autoPlay)) {
                TtsService.speak(scriptToSpeak, Locale.getDefault())
                updateClashState(clashIndex) { it.copy(ttsHasBeenPlayed = true) }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        arenaService.stopAllEndpoints()
        llmHelper.cleanUp()
        TtsService.shutdown()
        inferenceJob?.cancel()
        judgeInitializationJob?.cancel()
    }
}