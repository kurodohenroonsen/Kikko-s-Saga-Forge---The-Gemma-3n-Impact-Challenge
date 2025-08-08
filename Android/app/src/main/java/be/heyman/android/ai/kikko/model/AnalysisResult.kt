package be.heyman.android.ai.kikko.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * Définit les états possibles pour une seule tâche d'analyse
 * lancée depuis l'Atelier de la Forge.
 */
enum class AnalysisStatus {
    PENDING,   // La tâche est en file d'attente, prête à être lancée.
    RUNNING,   // La tâche est en cours d'exécution.
    PAUSED,    // La tâche a été mise en pause par l'utilisateur.
    CANCELLED, // La tâche a été annulée par l'utilisateur.
    COMPLETED, // La tâche s'est terminée avec succès.
    FAILED     // La tâche a échoué.
}

/**
 * Encapsule la configuration exacte d'un modèle IA pour une tâche d'analyse.
 * Permet de différencier les résultats d'un tournoi.
 */
@Parcelize
data class ModelConfiguration(
    val modelName: String,
    val accelerator: String,
    val temperature: Float,
    val topK: Int
) : Parcelable

/**
 * Représente une seule tâche d'analyse (et son résultat) lancée sur une propriété
 * d'un PollenGrain. Cet objet sera stocké dans une nouvelle table de la base de données.
 *
 * BOURDON'S REFORGE: Le modèle inclut maintenant les métriques de performance.
 */
@Parcelize
data class AnalysisResult(
    val id: String = UUID.randomUUID().toString(),
    val pollenGrainId: String,
    val propertyName: String,
    val modelConfigJson: String,
    var rawResponse: String? = null,
    @Transient var streamingResponse: String? = null,
    var status: AnalysisStatus = AnalysisStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    var errorMessage: String? = null,

    // BOURDON'S REFORGE: Nouveaux champs pour les métriques de performance.
    var ttftMs: Long? = null, // Time To First Token (Temps de Réflexion)
    var totalTimeMs: Long? = null, // Temps total de l'inférence
    var tokensPerSecond: Float? = null // Vitesse de production de la Reine
) : Parcelable