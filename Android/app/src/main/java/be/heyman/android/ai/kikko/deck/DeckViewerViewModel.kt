package be.heyman.android.ai.kikko.deck

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import be.heyman.android.ai.kikko.GameConstants
import be.heyman.android.ai.kikko.KikkoApplication
import be.heyman.android.ai.kikko.ToolsDialogFragment
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import be.heyman.android.ai.kikko.worker.ForgeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeckUiState(
    val isLoading: Boolean = true,
    val allCards: Map<String, List<KnowledgeCard>> = emptyMap(),
    val selectedDeck: String = GameConstants.MASTER_DECK_LIST.first(),
    val filteredCards: List<KnowledgeCard> = emptyList()
)

class DeckViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val cardDao: CardDao = (application as KikkoApplication).cardDao
    private val pollenGrainDao: PollenGrainDao = (application as KikkoApplication).pollenGrainDao
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(DeckUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAllDecks()
    }

    fun selectDeck(deckName: String) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedDeck = deckName,
                filteredCards = currentState.allCards[deckName] ?: emptyList()
            )
        }
    }

    fun deleteCard(card: KnowledgeCard) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cardDao.delete(card)
            }
            loadAllDecks()
        }
    }

    fun requestTranslation(card: KnowledgeCard) {
        viewModelScope.launch(Dispatchers.IO) {
            val pollenGrain = pollenGrainDao.findByForgedCardId(card.id)
            if (pollenGrain != null) {
                Log.i("DeckViewerViewModel", "PollenGrain trouvé pour la carte ${card.id}. Statut mis à PENDING_TRANSLATION.")
                pollenGrainDao.updateStatus(pollenGrain.id, PollenStatus.PENDING_TRANSLATION)
                launchForgeWorker()
            } else {
                Log.w("DeckViewerViewModel", "Aucun PollenGrain trouvé pour la carte ${card.id}. La traduction ne peut être lancée.")
            }
        }
    }

    private fun loadAllDecks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val groupedDecks = withContext(Dispatchers.IO) {
                cardDao.getAll()
            }.groupBy { it.deckName }
            _uiState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    allCards = groupedDecks,
                    filteredCards = groupedDecks[currentState.selectedDeck] ?: emptyList()
                )
            }
        }
    }

    private fun launchForgeWorker() {
        Log.d("DeckViewerViewModel", "Lancement du ForgeWorker pour la traduction.")

        val prefs = getApplication<Application>().getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val requiresCharging = prefs.getBoolean(ToolsDialogFragment.KEY_REQUIRE_CHARGING, false)
        val requiresIdle = prefs.getBoolean(ToolsDialogFragment.KEY_REQUIRE_IDLE, false)

        val constraints = Constraints.Builder()
            .setRequiresCharging(requiresCharging)
            .setRequiresDeviceIdle(requiresIdle)
            .setRequiredNetworkType(NetworkType.CONNECTED) // La traduction nécessite le réseau
            .build()

        val forgeRequest = OneTimeWorkRequestBuilder<ForgeWorker>()
            .setConstraints(constraints)
            .build()

        workManager.beginUniqueWork(
            "PollenForgeChain",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            forgeRequest
        ).enqueue()

        Log.i("DeckViewerViewModel", "ForgeWorker mis en file d'attente pour la traduction.")
    }
}