// app/src/main/java/be/heyman/android/ai/kikko/clash/viewmodel/ClashUiState.kt

package be.heyman.android.ai.kikko.clash.viewmodel

import be.heyman.android.ai.kikko.clash.data.ClashSettings
import be.heyman.android.ai.kikko.clash.data.ClashState
import be.heyman.android.ai.kikko.clash.data.PlayerCatalogue
import be.heyman.android.ai.kikko.clash.helpers.LocalizedQuestion
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.model.KnowledgeCard

enum class ClashFlowState { SETUP, DUELING, FINISHED }
enum class ClashMode { SOLO, P2P_DISCOVERING, P2P_AWAITING_SETUP, P2P_CARD_SELECTION, P2P_DUELING }

sealed class DialogState {
    data object None : DialogState()
    data object ReadyToClash : DialogState()
    data object WaitingForHost : DialogState()
}

data class ClashUiState(
    // --- FLUX GLOBAL ---
    val flowState: ClashFlowState = ClashFlowState.SETUP,
    val clashMode: ClashMode = ClashMode.SOLO,
    val errorMessage: String? = null,
    val dialogState: DialogState = DialogState.None,

    // --- CONFIGURATION ---
    val availableModels: List<Model> = emptyList(),
    // BOURDON'S DEFINITIVE FIX: Ajout du champ manquant pour le modèle sélectionné
    val selectedModel: Model? = null,
    val clashSettings: ClashSettings? = null,
    val isJudgeInitializing: Boolean = false,
    val myChampions: Map<String, KnowledgeCard> = emptyMap(),
    val opponentChampions: Map<String, KnowledgeCard> = emptyMap(),
    val isReadyToClash: Boolean = false,

    // BOURDON'S FIX V5: Ajout d'un set pour suivre la réception des images des champions.
    val opponentChampionImagesReady: Set<String> = emptySet(), // Contient les deckNames des champions dont l'image est prête.

    // --- P2P ---
    val p2pStatus: String = "Arène P2P inactive.",
    val isArbitrator: Boolean = false,
    val discoveredPlayers: Map<String, PlayerCatalogue> = emptyMap(),
    val connectedEndpointId: String? = null,
    val duelQuestions: Map<String, LocalizedQuestion> = emptyMap(),

    // --- DUEL ---
    val clashStates: List<ClashState> = emptyList(),
    val currentDuelIndex: Int = -1
)