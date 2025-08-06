package be.heyman.android.ai.kikko.clash.data

import be.heyman.android.ai.kikko.model.KnowledgeCard

/**
 * Représente l'état complet d'un seul duel dans l'Arène.
 * Contient les cartes combattantes, la question, le statut, les raisonnements et le verdict.
 */
data class ClashState(
    val deckName: String,
    val player1Card: KnowledgeCard,
    val player2Card: KnowledgeCard,
    var status: ClashStatus = ClashStatus.PENDING,
    var question: String? = null,
    var winner: String = "tie",
    var rawReasoning: String = "",
    var streamingReasoning: String? = "",
    var translatedReasoning: String? = "",
    // BOURDON'S REFACTOR: Ajout du champ pour le script TTS généré par l'IA.
    var ttsScript: String? = "",
    var errorMessage: String? = "",
    // BOURDON'S FIX V6: Ajout du drapeau pour le contrôle du TTS.
    var ttsHasBeenPlayed: Boolean = false,
    // BOURDON'S FIX V7: Ajout du drapeau pour le TTS de la question.
    var ttsQuestionHasBeenPlayed: Boolean = false
)