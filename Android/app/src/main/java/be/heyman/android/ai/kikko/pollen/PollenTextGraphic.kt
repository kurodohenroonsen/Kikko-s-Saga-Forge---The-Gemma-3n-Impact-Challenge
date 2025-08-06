package be.heyman.android.ai.kikko.pollen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import be.heyman.android.ai.kikko.pollen.vision.GraphicOverlay
import com.google.mlkit.vision.text.Text

class PollenTextGraphic(
    overlay: GraphicOverlay,
    private val text: Text
) : GraphicOverlay.Graphic(overlay) {

    private val textPaint: Paint = Paint().apply {
        color = TEXT_COLOR
        textSize = TEXT_SIZE
        textAlign = Paint.Align.LEFT
    }

    private val rectPaint: Paint = Paint().apply {
        color = BOX_COLOR
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
    }

    override fun draw(canvas: Canvas) {
        for (block in text.textBlocks) {
            for (line in block.lines) {
                if (line.boundingBox != null) {
                    val rect = RectF(line.boundingBox)
                    // BOURDON'S FIX: Utilisation de overlay.translateX et overlay.translateY
                    canvas.drawRect(
                        overlay.translateX(rect.left),
                        overlay.translateY(rect.top),
                        overlay.translateX(rect.right),
                        overlay.translateY(rect.bottom),
                        rectPaint
                    )

                    canvas.drawText(
                        line.text,
                        overlay.translateX(rect.left),
                        overlay.translateY(rect.bottom),
                        textPaint
                    )
                }
            }
        }
    }

    companion object {
        private const val TEXT_COLOR = Color.WHITE
        private const val BOX_COLOR = Color.GREEN
        private const val TEXT_SIZE = 45.0f
        private const val STROKE_WIDTH = 5.0f
    }
}