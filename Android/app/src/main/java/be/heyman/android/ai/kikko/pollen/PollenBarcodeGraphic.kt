package be.heyman.android.ai.kikko.pollen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import be.heyman.android.ai.kikko.pollen.vision.GraphicOverlay
import com.google.mlkit.vision.barcode.common.Barcode

class PollenBarcodeGraphic(
    overlay: GraphicOverlay,
    private val barcode: Barcode?
) : GraphicOverlay.Graphic(overlay) {

    private var rectPaint: Paint = Paint().apply {
        color = BOX_COLOR
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
    }

    private var barcodePaint: Paint = Paint().apply {
        color = TEXT_COLOR
        textSize = TEXT_SIZE
    }

    override fun draw(canvas: Canvas) {
        if (barcode == null || barcode.boundingBox == null) return

        val rect = RectF(barcode.boundingBox)

        // BOURDON'S FIX: Utilisation de overlay.translateX et overlay.translateY
        val mappedRect = RectF(
            overlay.translateX(rect.left),
            overlay.translateY(rect.top),
            overlay.translateX(rect.right),
            overlay.translateY(rect.bottom)
        )
        canvas.drawRect(mappedRect, rectPaint)

        barcode.rawValue?.let {
            canvas.drawText(it, mappedRect.left, mappedRect.bottom + TEXT_SIZE, barcodePaint)
        }
    }

    companion object {
        private const val BOX_COLOR = Color.CYAN
        private const val TEXT_COLOR = Color.CYAN
        private const val STROKE_WIDTH = 5.0f
        private const val TEXT_SIZE = 40.0f
    }
}