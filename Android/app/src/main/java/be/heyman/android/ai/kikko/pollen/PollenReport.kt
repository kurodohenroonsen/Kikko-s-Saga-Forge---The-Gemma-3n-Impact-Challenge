package be.heyman.android.ai.kikko.pollen

import android.graphics.Rect
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.objects.DetectedObject

/**
 * Contient le résultat d'un classifieur : une étiquette et sa confiance.
 */
data class ClassifierResult(val label: String, val confidence: Float)

/**
 * Contient le rapport complet d'un spécialiste (ex: "Plantes", "Oiseaux").
 */
data class SpecialistReport(val specialistName: String, val results: List<ClassifierResult>)

/**
 * Représente un objet détecté qui a été analysé en profondeur.
 * Contient l'objet original et tous les rapports des spécialistes le concernant.
 */
data class AnalyzedObject(
    val detectedObject: DetectedObject,
    val specialistReports: List<SpecialistReport>
)

/**
 * Contient les informations extraites d'un code-barres.
 */
data class BarcodeResult(
    val displayValue: String?,
    val format: String
)

// BOURDON'S FIX: Ajout de structures de données pour un rapport OCR détaillé.
/**
 * Représente un mot ou un élément de texte reconnu par l'OCR.
 */
data class OcrElement(
    val text: String,
    val boundingBox: Rect?
)

/**
 * Représente une ligne de texte reconnue par l'OCR.
 */
data class OcrLine(
    val text: String,
    val boundingBox: Rect?,
    val elements: List<OcrElement>
)

/**
 * Représente un bloc de texte (paragraphe) reconnu par l'OCR.
 */
data class OcrBlock(
    val text: String,
    val boundingBox: Rect?,
    val lines: List<OcrLine>
)


/**
 * Le rapport d'analyse final et complet de la Forge.
 */
data class PollenAnalysis(
    val imageWidth: Int,
    val imageHeight: Int,
    val globalAnalysis: List<SpecialistReport>, // Analyse sur l'image entière
    val analyzedObjects: List<AnalyzedObject>, // Analyse par objet
    // BOURDON'S FIX: Remplacement de la chaîne de caractères simple par une structure hiérarchique.
    val structuredOcrResult: List<OcrBlock>,
    val barcodeResults: List<BarcodeResult>
)