package be.heyman.android.ai.kikko.util.logging

import kotlinx.serialization.Serializable

/**
 * Représente une seule entrée dans le journal de forge (Journal de Bord).
 * Capture l'intégralité d'une transaction avec un modèle LLM pour analyse et débogage.
 * Déplacé depuis le package `debug` vers un nouveau package `util.logging`
 * car il est une fonctionnalité de logging générale.
 */
@Serializable
data class InferenceLog(
    val inferenceId: String,
    val timestamp: String,
    val useCase: String,
    val model: ModelInfo,
    val prompt: String, // AJOUTÉ : Le prompt exact envoyé au modèle.
    val rawResponse: String, // AJOUTÉ : La réponse brute complète reçue.
    val metadata: InferenceMetadata
)

/**
 * Contient les informations sur le modèle utilisé pour l'inférence.
 */
@Serializable
data class ModelInfo(
    val name: String,
    val configuration: ModelConfiguration
)

/**
 * Contient les paramètres de configuration clés utilisés pour cette inférence spécifique.
 */
@Serializable
data class ModelConfiguration(
    val temperature: Float,
    val topK: Int,
    val maxTokens: Int,
    val accelerator: String
)

/**
 * Contient les métadonnées de performance de l'inférence.
 */
@Serializable
data class InferenceMetadata(
    val latencyMs: Long,
    val tokensPerSecond: Float
)