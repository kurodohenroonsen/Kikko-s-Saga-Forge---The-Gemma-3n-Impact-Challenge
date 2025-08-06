package be.heyman.android.ai.kikko.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * Représente une session de récolte de "pollen" complète.
 * C'est un POKO (Plain Old Kotlin Object) utilisé comme modèle de données
 * pour la persistance en SQL pur.
 *
 * @property id Identifiant unique de la session de récolte (UUID).
 * @property timestamp Horodatage de la fin de la capture.
 * @property status L'état actuel du grain dans le cycle de vie de la Forge.
 * @property userIntent L'intention vocale ou textuelle de l'utilisateur lors de la capture.
 * @property pollenImagePaths Les chemins d'accès vers les fichiers images sources de la capture. Sérialisé en JSON.
 * @property swarmAnalysisReportJson Le rapport JSON brut complet de l'analyse par les Abeilles Spécialistes.
 * @property forgedCardId L'ID de la KnowledgeCard résultante une fois la Forge terminée.
 */
@Parcelize
data class PollenGrain(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    var status: PollenStatus = PollenStatus.RAW, // 'var' pour pouvoir le mettre à jour
    val userIntent: String?,
    val pollenImagePaths: List<String>,
    val swarmAnalysisReportJson: String?,
    var forgedCardId: Long? = null // 'var' pour pouvoir le mettre à jour
) : Parcelable