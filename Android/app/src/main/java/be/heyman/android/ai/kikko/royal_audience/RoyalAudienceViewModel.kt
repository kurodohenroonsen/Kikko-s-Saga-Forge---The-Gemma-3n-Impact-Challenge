package be.heyman.android.ai.kikko.royal_audience

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import be.heyman.android.ai.kikko.KikkoApplication
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.ToolsDialogFragment
import be.heyman.android.ai.kikko.TtsService
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.forge.ForgeLlmHelper
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.ModelConfiguration
import be.heyman.android.ai.kikko.model.SwarmAnalysisResult
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
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
import java.util.Locale

data class RoyalAudienceUiState(
    val isLoading: Boolean = true,
    val cardTitle: String = "Audience Royale",
    val messages: List<ChatMessage> = emptyList(),
    val audienceSettings: AudienceSettings = AudienceSettings(0.2f, 40),
    val availableModels: List<File> = emptyList(),
    val selectedModelName: String? = null
)

class RoyalAudienceViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val cardDao: CardDao = (application as KikkoApplication).cardDao
    private val pollenGrainDao: PollenGrainDao = (application as KikkoApplication).pollenGrainDao
    private val llmHelper: ForgeLlmHelper = (application as KikkoApplication).forgeLlmHelper

    private val _uiState = MutableStateFlow(RoyalAudienceUiState())
    val uiState = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    private var systemPrompt: String = ""
    private val ttsSentenceBuffer = StringBuilder()

    companion object {
        private const val TAG = "RoyalAudienceVM"
        const val CARD_ID_KEY = "cardId"
        const val PREFS_AUDIENCE = "audience_prefs"
        const val KEY_AUDIENCE_TEMP = "audience_temp"
        const val KEY_AUDIENCE_TOP_K = "audience_top_k"
    }

    init {
        val cardId = savedStateHandle.get<Long>(CARD_ID_KEY)
        if (cardId == null || cardId == -1L) {
            loadGenericConversation()
        } else {
            loadCardAndStartConversation(cardId)
        }
    }

    private fun loadGenericConversation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, cardTitle = getApplication<Application>().getString(R.string.audience_title_generic)) }
            val wakingUpMessage = ChatMessage(text = getApplication<Application>().getString(R.string.queen_waking_up), isFromUser = false, isStreaming = true)
            _uiState.update { it.copy(messages = listOf(wakingUpMessage)) }

            systemPrompt = createGenericSystemPrompt()
            initializeQueenForConversation(null)
        }
    }

    private fun loadCardAndStartConversation(cardId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, cardTitle = getApplication<Application>().getString(R.string.audience_title_loading)) }

            val result: Result<Pair<KnowledgeCard, String?>> = withContext(Dispatchers.IO) {
                try {
                    val card = cardDao.getCardById(cardId)
                        ?: return@withContext Result.failure(Exception(getApplication<Application>().getString(R.string.error_card_not_found)))

                    var ocrText: String? = null
                    val originalPollen = pollenGrainDao.findByForgedCardId(card.id)
                    if (originalPollen?.swarmAnalysisReportJson != null) {
                        try {
                            val swarmResult = Gson().fromJson(originalPollen.swarmAnalysisReportJson, SwarmAnalysisResult::class.java)
                            ocrText = swarmResult.reports?.mapNotNull { it.ocrResults?.fullText }?.joinToString("\n")?.trim()
                        } catch (e: Exception) {
                            Log.w(TAG, "Échec du parsing du rapport d'essaim pour l'OCR.", e)
                        }
                    }
                    Result.success(Pair(card, ocrText))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            if (result.isFailure) {
                val errorMessage = result.exceptionOrNull()?.message ?: getApplication<Application>().getString(R.string.error_unknown)
                _uiState.update { it.copy(isLoading = false, cardTitle = getApplication<Application>().getString(R.string.error_title), messages = listOf(ChatMessage(text = errorMessage, isFromUser = false))) }
                return@launch
            }

            val (card, ocrText) = result.getOrThrow()
            systemPrompt = createSystemPrompt(card, ocrText)

            val cardMessage = ChatMessage(card = card, isFromUser = false)
            val wakingUpMessage = ChatMessage(text = getApplication<Application>().getString(R.string.queen_waking_up), isFromUser = false, isStreaming = true)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    cardTitle = card.specificName,
                    messages = listOf(cardMessage, wakingUpMessage)
                )
            }
            initializeQueenForConversation(card)
        }
    }

    private suspend fun initializeQueenForConversation(card: KnowledgeCard?) {
        val initResult: Result<Unit> = withContext(Dispatchers.IO) {
            try {
                val availableModels = loadAvailableModels()
                val forgePrefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
                val audiencePrefs = getApplication<Application>().getSharedPreferences(PREFS_AUDIENCE, Context.MODE_PRIVATE)

                val selectedQueenName = forgePrefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, null)
                    ?: availableModels.firstOrNull()?.name
                    ?: return@withContext Result.failure(Exception(getApplication<Application>().getString(R.string.error_no_queen_installed_or_selected)))

                val temp = audiencePrefs.getFloat(KEY_AUDIENCE_TEMP, 0.2f)
                val topK = audiencePrefs.getInt(KEY_AUDIENCE_TOP_K, 40)
                val audienceSettings = AudienceSettings(temp, topK)
                val selectedAccelerator = forgePrefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")!!

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        availableModels = availableModels,
                        selectedModelName = selectedQueenName,
                        audienceSettings = audienceSettings
                    )}
                }

                val modelFile = File(getApplication<Application>().filesDir, "imported_models").resolve(selectedQueenName)
                if (!modelFile.exists()) return@withContext Result.failure(Exception(getApplication<Application>().getString(R.string.error_queen_model_file_not_found)))

                val queenModel = Model(name = selectedQueenName, url = modelFile.absolutePath, downloadFileName = "", sizeInBytes = 0, llmSupportImage = selectedQueenName.contains("gemma-3n", ignoreCase = true))

                val initError = llmHelper.initialize(queenModel, selectedAccelerator, isMultimodal = queenModel.llmSupportImage)
                if (initError != null) return@withContext Result.failure(Exception(getApplication<Application>().getString(R.string.error_queen_init_failed, initError)))

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val cardMessage = card?.let { ChatMessage(card = it, isFromUser = false) }

        initResult.onSuccess {
            val welcomeMessage = getApplication<Application>().getString(R.string.queen_ready_to_chat)
            TtsService.speak(welcomeMessage, Locale.getDefault())
            _uiState.update {
                val messages = mutableListOf<ChatMessage>()
                if (cardMessage != null) messages.add(cardMessage)
                messages.add(ChatMessage(text = welcomeMessage, isFromUser = false))
                it.copy(isLoading = false, messages = messages)
            }
        }.onFailure { error ->
            val errorMessage = ChatMessage(text = error.message ?: getApplication<Application>().getString(R.string.error_unknown), isFromUser = false)
            _uiState.update {
                val messages = mutableListOf<ChatMessage>()
                if (cardMessage != null) messages.add(cardMessage)
                messages.add(errorMessage)
                it.copy(isLoading = false, messages = messages)
            }
        }
    }

    fun sendMessage(userInput: String, imageUri: Uri? = null) {
        if ((userInput.isBlank() && imageUri == null) || _uiState.value.isLoading) return

        TtsService.stopAndClearQueue()
        ttsSentenceBuffer.clear()

        val userMessage = ChatMessage(text = userInput, imageUri = imageUri?.toString(), isFromUser = true)
        val queenStreamingMessage = ChatMessage(text = "", isFromUser = false, isStreaming = true)

        _uiState.update { it.copy(isLoading = true, messages = it.messages + userMessage + queenStreamingMessage) }

        viewModelScope.launch(Dispatchers.IO) {
            val bitmapList = imageUri?.let { uri ->
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { inputStream ->
                        listOf(BitmapFactory.decodeStream(inputStream))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors du chargement du bitmap depuis l'URI", e)
                    null
                }
            } ?: emptyList<Bitmap>()

            val fullPrompt = buildFullPrompt()
            Log.d(TAG, "--- PROMPT ENVOYÉ À LA REINE ---\n$fullPrompt")

            try {
                val currentState = _uiState.value
                val modelName = currentState.selectedModelName ?: throw IllegalStateException(getApplication<Application>().getString(R.string.error_no_model_selected))
                val accelerator = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")!!
                val config = ModelConfiguration(modelName, accelerator, currentState.audienceSettings.temperature, currentState.audienceSettings.topK)
                val queenModel = Model(name = config.modelName, url = "", downloadFileName = "", sizeInBytes = 0, llmSupportImage = bitmapList.isNotEmpty())

                llmHelper.resetSession(queenModel, bitmapList.isNotEmpty(), config.temperature, config.topK)

                llmHelper.runInference(fullPrompt, bitmapList) { partialResult, done ->
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.update { state ->
                            val currentMessages = state.messages.toMutableList()
                            val lastMessage = currentMessages.lastOrNull()

                            if (lastMessage != null && lastMessage.isStreaming) {
                                val updatedText = lastMessage.text + partialResult
                                currentMessages[currentMessages.lastIndex] = lastMessage.copy(text = updatedText, isStreaming = !done)
                                processTtsBuffer(partialResult, speakRemaining = done)
                                state.copy(messages = currentMessages, isLoading = !done)
                            } else {
                                state
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Erreur d'inférence dans RoyalAudience", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { currentState ->
                        val finalMessages = currentState.messages.toMutableList()
                        if (finalMessages.isNotEmpty() && finalMessages.last().isStreaming) {
                            val errorMessage = e.message ?: getApplication<Application>().getString(R.string.error_unknown)
                            finalMessages[finalMessages.lastIndex] = finalMessages.last().copy(text = getString(R.string.error_generic_prefix, errorMessage), isStreaming = false)
                        }
                        currentState.copy(isLoading = false, messages = finalMessages)
                    }
                }
            }
        }
    }

    fun updateAudienceSettings(newSettings: AudienceSettings) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                getApplication<Application>().getSharedPreferences(PREFS_AUDIENCE, Context.MODE_PRIVATE).edit()
                    .putFloat(KEY_AUDIENCE_TEMP, newSettings.temperature)
                    .putInt(KEY_AUDIENCE_TOP_K, newSettings.topK)
                    .apply()
            }
            _uiState.update { it.copy(audienceSettings = newSettings) }
            _toastEvent.emit(getApplication<Application>().getString(R.string.queen_settings_updated))
        }
    }

    fun updateSelectedQueen(newModelName: String) {
        viewModelScope.launch {
            val oldModelName = _uiState.value.selectedModelName
            val message = getApplication<Application>().getString(R.string.queen_switching_message, oldModelName, newModelName)
            _uiState.update { it.copy(isLoading = true, messages = it.messages + ChatMessage(text = message, isFromUser = false)) }
            withContext(Dispatchers.IO) {
                getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, newModelName)
                    .apply()
                val cardId = savedStateHandle.get<Long>(CARD_ID_KEY)
                if (cardId == null || cardId == -1L) {
                    loadGenericConversation()
                } else {
                    loadCardAndStartConversation(cardId)
                }
            }
        }
    }

    private suspend fun loadAvailableModels(): List<File> {
        return withContext(Dispatchers.IO) {
            val modelsDir = File(getApplication<Application>().filesDir, "imported_models")
            if (modelsDir.exists() && modelsDir.isDirectory) {
                modelsDir.listFiles { _, name -> name.endsWith(".task") }?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        }
    }


    private fun processTtsBuffer(textChunk: String, speakRemaining: Boolean = false) {
        ttsSentenceBuffer.append(textChunk)
        val separators = charArrayOf('.', '。', '!', '?')
        var lastSeparatorIndex: Int

        while (true) {
            lastSeparatorIndex = ttsSentenceBuffer.indexOfAny(separators)
            if (lastSeparatorIndex == -1) break

            val sentenceToSpeak = ttsSentenceBuffer.substring(0, lastSeparatorIndex + 1).trim()
            if (sentenceToSpeak.isNotBlank()) {
                TtsService.speak(sentenceToSpeak, Locale.getDefault())
            }
            ttsSentenceBuffer.delete(0, lastSeparatorIndex + 1)
        }

        if (speakRemaining) {
            val remainingText = ttsSentenceBuffer.toString().trim()
            if (remainingText.isNotBlank()) {
                TtsService.speak(remainingText, Locale.getDefault())
            }
            ttsSentenceBuffer.clear()
        }
    }

    private fun createGenericSystemPrompt(): String {
        val deviceLanguage = Locale.getDefault().displayLanguage
        return """
        ROLE: You are the AI Queen of the Kikko Hive, a wise and helpful guide.
        CONTEXT: You are in a general conversation with a "Forager" (the user).
        TASK: Answer the Forager's questions.
        CRITICAL RULES:
        1.  **Language**: Your entire response MUST be in `$deviceLanguage`.
        2.  **Conciseness**: NEVER repeat the user's question back to them. Only provide your answer.
        3.  **Persona**: Be helpful, knowledgeable, and slightly regal.
        """.trimIndent()
    }

    private fun createSystemPrompt(card: KnowledgeCard, ocrText: String?): String {
        val cardJson = Gson().toJson(card)
        val deviceLanguage = Locale.getDefault().displayLanguage

        val ocrContext = if (!ocrText.isNullOrBlank()) {
            """
            ADDITIONAL CONTEXT: The raw text extracted from the original image's packaging is provided below. This is a HIGH-PRIORITY source for technical details like ingredients or nutritional values.
            [RAW OCR DATA]:
            $ocrText
            """
        } else {
            ""
        }

        return """
        ROLE: You are the AI Queen of the Kikko Hive, a wise and helpful guide.

        CONTEXT: You are in a conversation with a "Forager" (the user) about a specific Knowledge Card they have forged. The full data for this card is provided in [CARD DATA]. This is your primary source of truth.
        $ocrContext

        TASK: Answer the Forager's questions.

        CRITICAL RULES:
        1.  **Language**: Your entire response MUST be in `$deviceLanguage`.
        2.  **Knowledge Source**: You MUST prioritize information from the provided [CARD DATA] and [RAW OCR DATA]. If you use your general knowledge, you MUST state it (e.g., "From my general knowledge...").
        3.  **Conciseness**: NEVER repeat the user's question back to them. Only provide your answer.
        4.  **Persona**: Be helpful, knowledgeable, and slightly regal.

        [CARD DATA]:
        $cardJson
        """.trimIndent()
    }

    private fun buildFullPrompt(): String {
        val history = _uiState.value.messages
            .filter { it.text != null && it.card == null }
            .joinToString("\n") {
                if (it.isStreaming) "" else {
                    if (it.isFromUser) "Forager: ${it.text}" else "Queen: ${it.text}"
                }
            }
        return "$systemPrompt\n\n--- Conversation History ---\n$history\nQueen:"
    }

    // Helper to get string from ViewModel
    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }

    override fun onCleared() {
        super.onCleared()
        llmHelper.cleanUp()
        TtsService.shutdown()
    }
}