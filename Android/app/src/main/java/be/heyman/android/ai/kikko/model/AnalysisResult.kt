package be.heyman.android.ai.kikko.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * Définit les états possibles pour une seule tâche d'analyse
 * lancée depuis l'Atelier de la Forge.
 * BOURDON'S REFORGE: Ajout des états pour un contrôle granulaire des tâches.
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
 * @param id Identifiant unique de la tâche d'analyse.
 * @param pollenGrainId L'ID du PollenGrain parent auquel cette tâche est associée.
 * @param propertyName Le nom de la propriété analysée (ex: "description", "stats.lifespan").
 * @param modelConfigJson La configuration du modèle IA utilisée, sérialisée en JSON.
 * @param rawResponse La réponse brute complète retournée par le modèle IA une fois la tâche terminée.
 * @param streamingResponse Le contenu partiel reçu en temps réel (non persisté).
 * @param status L'état actuel de cette tâche d'analyse.
 * @param timestamp L'horodatage de la création de cette tâche.
 * @param errorMessage Un message d'erreur si la tâche a échoué.
 */
@Parcelize
data class AnalysisResult(
    val id: String = UUID.randomUUID().toString(),
    val pollenGrainId: String,
    val propertyName: String,
    val modelConfigJson: String,
    var rawResponse: String? = null,
    // BOURDON'S ADDITION: Champ pour le texte en streaming.
    // Il est transient car il ne vit que dans l'UI et n'est pas sauvegardé en base de données.
    @Transient var streamingResponse: String? = null,
    var status: AnalysisStatus = AnalysisStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    var errorMessage: String? = null
) : Parcelable