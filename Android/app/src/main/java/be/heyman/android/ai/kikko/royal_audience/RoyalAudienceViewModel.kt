package be.heyman.android.ai.kikko.royal_audience

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import be.heyman.android.ai.kikko.KikkoApplication
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.ToolsDialogFragment
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.forge.ForgeLlmHelper
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.persistence.CardDao
import com.google.gson.Gson
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
import java.util.Locale

data class AudienceUiState(
    val isLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val availableModels: List<File> = emptyList(),
    val selectedModelName: String? = null,
    val audienceSettings: AudienceSettings = AudienceSettings(temperature = 0.2f, topK = 40)
)

class RoyalAudienceViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val TAG = "RoyalAudienceViewModel"

    private val cardDao: CardDao = (application as KikkoApplication).cardDao
    private val llmHelper: ForgeLlmHelper = (application as KikkoApplication).forgeLlmHelper
    private val gson = Gson()

    private val _uiState = MutableStateFlow(AudienceUiState())
    val uiState = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent = _toastEvent.asSharedFlow()

    private var inferenceJob: Job? = null
    private var cardContext: KnowledgeCard? = null

    init {
        loadInitialState()
        val cardId = savedStateHandle.get<Long>(RoyalAudienceActivity.CARD_ID_KEY) ?: -1L
        if (cardId != -1L) {
            loadCardContext(cardId)
        }
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val models = withContext(Dispatchers.IO) {
                File(getApplication<Application>().filesDir, "imported_models")
                    .listFiles { _, name -> name.endsWith(".task") }?.toList() ?: emptyList()
            }

            val prefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
            val savedModelName = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, null)

            _uiState.update {
                it.copy(
                    availableModels = models,
                    selectedModelName = savedModelName ?: models.firstOrNull()?.name
                )
            }
            initializeQueen()
        }
    }

    private fun loadCardContext(cardId: Long) {
        viewModelScope.launch {
            cardContext = cardDao.getCardById(cardId)
            cardContext?.let { card ->
                val contextMessage = ChatMessage(card = card, isFromUser = false)
                _uiState.update { it.copy(messages = it.messages + contextMessage) }
            }
        }
    }

    private fun initializeQueen() {
        val modelName = _uiState.value.selectedModelName
        if (modelName == null) {
            viewModelScope.launch { _toastEvent.emit(getString(R.string.audience_no_queen_available)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _toastEvent.emit(getString(R.string.queen_waking_up))

            val error = withContext(Dispatchers.IO) {
                val modelFile = _uiState.value.availableModels.firstOrNull { it.name == modelName }
                if (modelFile == null || !modelFile.exists()) {
                    return@withContext getString(R.string.error_queen_model_file_not_found)
                }
                // BOURDON'S FIX: Fourniture de tous les paramètres obligatoires pour le constructeur Model.
                val model = Model(name = modelFile.name, url = modelFile.absolutePath, downloadFileName = modelFile.name, sizeInBytes = modelFile.length(), llmSupportImage = true)
                llmHelper.initialize(model, "GPU", isMultimodal = true)
            }

            if (error == null) {
                val readyMessage = ChatMessage(text = getString(R.string.queen_ready_to_chat), isFromUser = false)
                _uiState.update { it.copy(isLoading = false, messages = it.messages + readyMessage) }
            } else {
                _uiState.update { it.copy(isLoading = false) }
                _toastEvent.emit(getString(R.string.error_queen_init_failed, error))
            }
        }
    }

    fun sendMessage(text: String, imageUri: Uri?) {
        inferenceJob?.cancel()
        val userMessage = ChatMessage(text = text, imageUri = imageUri?.toString(), isFromUser = true)
        val queenPlaceholder = ChatMessage(text = "...", isFromUser = false, isStreaming = true)
        _uiState.update { it.copy(messages = it.messages + userMessage + queenPlaceholder, isLoading = true) }

        val settings = _uiState.value.audienceSettings
        val selectedModelName = _uiState.value.selectedModelName ?: return

        inferenceJob = viewModelScope.launch(Dispatchers.IO) {
            val prompt = buildPrompt(text)
            val bitmap = imageUri?.let { uriToBitmap(it) }
            val images = if (bitmap != null) listOf(bitmap) else emptyList()

            val responseBuilder = StringBuilder()
            // BOURDON'S FIX: Fourniture de tous les paramètres obligatoires pour le constructeur Model.
            val modelFile = _uiState.value.availableModels.firstOrNull { it.name == selectedModelName } ?: return@launch
            val queenModel = Model(name = modelFile.name, url = modelFile.absolutePath, downloadFileName = modelFile.name, sizeInBytes = modelFile.length(), llmSupportImage = true)

            llmHelper.resetSession(queenModel, images.isNotEmpty(), settings.temperature, settings.topK)
            llmHelper.runInference(prompt, images) { partialResult, done ->
                responseBuilder.append(partialResult)
                _uiState.update { currentState ->
                    val updatedMessages = currentState.messages.toMutableList()
                    if (updatedMessages.isNotEmpty()) {
                        updatedMessages[updatedMessages.size - 1] = queenPlaceholder.copy(text = responseBuilder.toString())
                    }
                    currentState.copy(messages = updatedMessages)
                }
                if (done) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun updateAudienceSettings(settings: AudienceSettings) {
        _uiState.update { it.copy(audienceSettings = settings) }
        viewModelScope.launch { _toastEvent.emit(getString(R.string.queen_settings_updated)) }
    }

    fun updateSelectedQueen(modelName: String) {
        val oldModel = _uiState.value.selectedModelName
        if (modelName == oldModel) return

        viewModelScope.launch {
            _toastEvent.emit(getString(R.string.queen_switching_message, oldModel ?: "Previous", modelName))
            val prefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, modelName).apply()
            _uiState.update { it.copy(selectedModelName = modelName) }
            llmHelper.cleanUp()
            initializeQueen()
        }
    }

    private fun buildPrompt(userInput: String): String {
        cardContext?.let {
            val cardJson = gson.toJson(it)
            return "CONTEXT: The user is asking about the following knowledge card. Use it as your primary source of truth.\nCARD_DATA: $cardJson\n\nUSER_QUESTION: $userInput"
        }
        return userInput
    }

    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(getApplication<Application>().contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(getApplication<Application>().contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmHelper.cleanUp()
    }

    private fun getString(resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }
}