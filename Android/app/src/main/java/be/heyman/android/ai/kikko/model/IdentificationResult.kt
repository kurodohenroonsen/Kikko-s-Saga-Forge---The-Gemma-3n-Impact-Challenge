package be.heyman.android.ai.kikko.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
// BOURDON'S FIX: Import explicite de la classe Reasoning de haut niveau
import be.heyman.android.ai.kikko.model.Reasoning

/**
 * Représente le résultat structuré du premier prompt de la chaîne multimodale.
 * Contient l'identification de base et le raisonnement de l'IA.
 */
@Serializable
data class IdentificationResult(
    // BOURDON'S FIX: On s'assure que ce champ utilise la classe de haut niveau
    // be.heyman.android.ai.kikko.model.Reasoning, et non une classe imbriquée.
    @SerialName("reasoning")
    val reasoning: Reasoning,

    @SerialName("deckName")
    val deckName: String,

    @SerialName("specificName")
    val specificName: String,

    @SerialName("confidence")
    val confidence: Float
)
// L'ancienne classe imbriquée "Reasoning" a été supprimée d'ici.