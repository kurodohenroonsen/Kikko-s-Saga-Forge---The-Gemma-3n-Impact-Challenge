package be.heyman.android.ai.kikko.pollen.vision

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * Une vue qui dessine par-dessus l'aperçu de la caméra pour afficher les résultats de la vision.
 * BOURDON'S FIX: Cette version est maintenant alignée sur la logique de la démo ML Kit
 * pour gérer correctement l'inversion (miroir) et le redimensionnement.
 */
class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val lock = Any()
    private val graphics: MutableList<Graphic> = ArrayList()
    private var imageWidth = 0
    private var imageHeight = 0
    private var isImageFlipped = false

    // Facteurs de transformation calculés pour passer des coordonnées de l'image à la vue.
    private var scaleFactor = 1.0f
    private var postScaleWidthOffset = 0f
    private var postScaleHeightOffset = 0f


    abstract class Graphic(protected val overlay: GraphicOverlay) {
        protected val context: Context = overlay.context
        abstract fun draw(canvas: Canvas)
    }

    fun clear() {
        synchronized(lock) {
            graphics.clear()
        }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        synchronized(lock) {
            graphics.add(graphic)
        }
    }

    /**
     * Définit les informations de la source de l'image pour calculer la transformation.
     * Cette méthode est la clé pour corriger les problèmes d'orientation et de miroir.
     */
    fun setImageSourceInfo(width: Int, height: Int, isFlipped: Boolean) {
        synchronized(lock) {
            imageWidth = width
            imageHeight = height
            isImageFlipped = isFlipped
        }
        postInvalidate()
    }

    fun translateX(x: Float): Float {
        return if (isImageFlipped) {
            width - (x * scaleFactor + postScaleWidthOffset)
        } else {
            x * scaleFactor + postScaleWidthOffset
        }
    }

    fun translateY(y: Float): Float {
        return y * scaleFactor + postScaleHeightOffset
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            if (imageWidth != 0 && imageHeight != 0) {
                val viewWidth = width.toFloat()
                val viewHeight = height.toFloat()

                val scaleX = viewWidth / imageWidth
                val scaleY = viewHeight / imageHeight
                scaleFactor = scaleX.coerceAtLeast(scaleY)

                postScaleWidthOffset = (viewWidth - imageWidth * scaleFactor) / 2
                postScaleHeightOffset = (viewHeight - imageHeight * scaleFactor) / 2
            }

            for (graphic in graphics) {
                graphic.draw(canvas)
            }
        }
    }
}