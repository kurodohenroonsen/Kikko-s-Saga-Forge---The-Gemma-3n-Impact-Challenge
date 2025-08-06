package be.heyman.android.ai.kikko.pollen

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import be.heyman.android.ai.kikko.pollen.vision.GraphicOverlay
import com.google.mlkit.vision.objects.DetectedObject

class SubtleObjectGraphic(
    overlay: GraphicOverlay,
    private val detectedObject: DetectedObject
) : GraphicOverlay.Graphic(overlay) {

    private val boxPaint: Paint = Paint().apply {
        color = Color.argb(100, 0, 255, 0)
        style = Paint.Style.FILL
    }

    private val textPaint: Paint = Paint().apply {
        color = Color.WHITE
        textSize = 35.0f
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        val rect = RectF(detectedObject.boundingBox)

        // BOURDON'S FIX: Utilisation de overlay.translateX et overlay.translateY
        val mappedRect = RectF(
            overlay.translateX(rect.left),
            overlay.translateY(rect.top),
            overlay.translateX(rect.right),
            overlay.translateY(rect.bottom)
        )
        canvas.drawRect(mappedRect, boxPaint)

        detectedObject.labels.firstOrNull()?.let { label ->
            canvas.drawText(
                label.text,
                mappedRect.centerX(),
                mappedRect.centerY() + 15,
                textPaint
            )
        }
    }
}