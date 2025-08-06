package be.heyman.android.ai.kikko.forge

/**
 * Contient le résultat complet d'une analyse d'essaim sur plusieurs images.
 * @param reports La liste des rapports d'analyse pour chaque image.
 */
data class SwarmAnalysisResult(
    val reports: List<ImageAnalysisReport>
)

/**
 * Contient le rapport d'analyse d'une seule image par les Abeilles Spécialistes.
 * @param detectedObjects La liste des objets détectés.
 * @param globalClassifierResults Une map des résultats des classifieurs, où la clé est le nom du classifieur.
 * @param ocrText Le texte brut reconnu par l'OCR.
 */
data class ImageAnalysisReport(
    val detectedObjects: List<SimpleDetectedObject>,
    val globalClassifierResults: Map<String, List<SimpleImageLabel>>,
    val ocrText: String
)

/**
 * Une représentation simplifiée d'un objet détecté par ML Kit.
 * @param labels La liste des labels associés à cet objet.
 */
data class SimpleDetectedObject(
    val labels: List<SimpleImageLabel>
)

/**
 * Une représentation simplifiée d'un label d'image (pour les objets ou les classifieurs).
 * @param text Le nom du label (ex: "Cat", "Eiffel Tower").
 * @param confidence Le score de confiance de la détection (entre 0.0 et 1.0).
 */
data class SimpleImageLabel(
    val text: String,
    val confidence: Float
)