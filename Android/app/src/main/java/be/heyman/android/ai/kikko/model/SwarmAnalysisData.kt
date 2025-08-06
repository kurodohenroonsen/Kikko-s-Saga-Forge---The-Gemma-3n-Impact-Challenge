package be.heyman.android.ai.kikko.model

import com.google.gson.annotations.SerializedName

/**
 * Contient le résultat complet d'une analyse d'essaim sur plusieurs images.
 */
data class SwarmAnalysisResult(
    val reports: List<ImageAnalysisReport>?
)

/**
 * BOURDON'S ROBUSTNESS FIX V3:
 * - Structure de données alignée sur le JSON réel de PollenForge.
 * - Toutes les collections et propriétés potentiellement manquantes sont nullables.
 */
data class ImageAnalysisReport(
    @SerializedName("object_detection_results")
    val detectedObjects: List<SimpleDetectedObject>?,

    @SerializedName("global_classification_results")
    val globalClassifierResults: Map<String, List<SimpleImageLabel>?>?,

    @SerializedName("ocr_results")
    val ocrResults: OcrResult?
) {
    val ocrText: String
        get() = ocrResults?.fullText ?: ""
}

data class OcrResult(
    @SerializedName("full_text")
    val fullText: String?
)

data class SimpleDetectedObject(
    val labels: List<SimpleImageLabel>
)

data class SimpleImageLabel(
    // BOURDON'S FIX: Rendu nullable pour empêcher les crashs de Gson/Kotlin.
    val text: String?,
    val confidence: Float
)