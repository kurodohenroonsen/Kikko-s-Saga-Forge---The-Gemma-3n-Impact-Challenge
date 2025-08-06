package be.heyman.android.ai.kikko.pollen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import be.heyman.android.ai.kikko.pollen.vision.GraphicOverlay
import com.google.mlkit.vision.objects.DetectedObject
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Objet graphique pour dessiner les objets détectés et leurs étiquettes sur la GraphicOverlay.
 */
class PollenObjectGraphic(
    overlay: GraphicOverlay,
    private val detectedObject: DetectedObject
) : GraphicOverlay.Graphic(overlay) {

    private val boxPaint: Paint = Paint().apply {
        color = OBJECT_COLOR
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
    }

    private val textPaint: Paint = Paint().apply {
        color = Color.WHITE
        textSize = TEXT_SIZE
    }

    private val labelBackgroundPaint: Paint = Paint().apply {
        color = OBJECT_COLOR
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        // La boîte de délimitation fournie par ML Kit
        val rect = RectF(detectedObject.boundingBox)

        // Traduction des coordonnées pour l'affichage
        val mappedRect = RectF(
            overlay.translateX(rect.left),
            overlay.translateY(rect.top),
            overlay.translateX(rect.right),
            overlay.translateY(rect.bottom)
        )

        // Dessine la boîte
        canvas.drawRect(mappedRect, boxPaint)

        // Dessine la première étiquette trouvée (la plus probable) au-dessus de la boîte.
        detectedObject.labels.firstOrNull()?.let { label ->
            val text = "${label.text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} (${"%.0f".format(label.confidence * 100)}%)"
            val textWidth = textPaint.measureText(text)
            val textHeight = TEXT_SIZE

            // Dessine un fond pour le texte pour une meilleure lisibilité
            canvas.drawRect(
                mappedRect.left,
                mappedRect.top - textHeight - (STROKE_WIDTH * 2),
                mappedRect.left + textWidth + (STROKE_WIDTH * 2),
                mappedRect.top,
                labelBackgroundPaint
            )

            // Dessine le texte
            canvas.drawText(
                text,
                mappedRect.left + STROKE_WIDTH,
                mappedRect.top - STROKE_WIDTH,
                textPaint
            )
        }
    }

    companion object {
        private const val OBJECT_COLOR = Color.MAGENTA // Magenta pour bien se distinguer
        private const val TEXT_SIZE = 40.0f
        private const val STROKE_WIDTH = 5.0f
    }
}