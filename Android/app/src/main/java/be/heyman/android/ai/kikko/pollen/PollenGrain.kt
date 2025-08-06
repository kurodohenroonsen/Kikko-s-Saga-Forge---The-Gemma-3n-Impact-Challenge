package be.heyman.android.ai.kikko.pollen

import android.graphics.Bitmap
import java.util.UUID

/**
 * Représente un grain de pollen individuel avec son image, son état et son rapport.
 * BOURDON'S REFACTOR: La classe a été enrichie pour suivre le statut de l'analyse
 * et conserver les rapports générés par la PollenForge.
 */
data class PollenGrain(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val status: PollenAnalysisStatus = PollenAnalysisStatus.PROCESSING,
    val report: PollenAnalysis? = null,
    val jsonReport: String? = null
)