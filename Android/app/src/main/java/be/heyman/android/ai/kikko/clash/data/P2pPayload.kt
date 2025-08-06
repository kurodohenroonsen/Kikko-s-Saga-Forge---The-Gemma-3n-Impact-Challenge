package be.heyman.android.ai.kikko.clash.data

import be.heyman.android.ai.kikko.clash.helpers.LocalizedQuestion
import be.heyman.android.ai.kikko.model.KnowledgeCard

sealed class P2pPayload {
    // Échangé par les deux joueurs pendant la sélection.
    // BOURDON'S FIX : Ajout de l'ID du payload de l'image. La carte sera "allégée" (sans imagePath).
    data class CardSelectionPayload(
        val deckName: String,
        val selectedCard: KnowledgeCard,
        val imagePayloadId: Long? = null
    ) : P2pPayload()

    // Envoyé par l'Hôte pour démarrer le Clash sur les deux appareils.
    data class StartClashPayload(val command: String = "START_CLASH") : P2pPayload()

    // Envoyé par l'Hôte à l'Invité pour synchroniser la question du duel.
    data class QuestionPayload(val deckName: String, val question: LocalizedQuestion) : P2pPayload()

    // Envoyé par l'Hôte à l'Invité avec le résultat de chaque duel.
    data class DuelResultPayload(val deckName: String, val winner: String, val reasoning: String, val ttsScript: String) : P2pPayload()

    // Envoyé par l'Hôte pour passer au duel suivant.
    data class NextDuelPayload(val command: String = "NEXT_DUEL") : P2pPayload()
}