// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/YuvToRgbConverter.kt ---

package be.heyman.android.ai.kikko.pollen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import java.nio.ByteBuffer

/**
 * Classe utilitaire pour convertir un objet Image au format YUV_420_888 en un bitmap RGB.
 * BOURDON'S REFORGE: Cette version est plus robuste et inspirée des implémentations de référence
 * pour gérer correctement les différents formats de plans YUV.
 */
class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    // Tampon pour les données YUV, réutilisé pour éviter des allocations répétées.
    private var yuvBuffer: ByteBuffer? = null
    private var yuvBufferSize = 0

    // Allocations RenderScript pour l'entrée (YUV) et la sortie (Bitmap RGB).
    private var allocationIn: Allocation? = null
    private var allocationOut: Allocation? = null
    private var lastWidth = -1
    private var lastHeight = -1


    @Synchronized
    fun yuvToRgb(image: Image, output: Bitmap) {
        val imageWidth = image.width
        val imageHeight = image.height

        // S'assurer que le tampon YUV est assez grand.
        val yuvBytes = imageToByteArray(image)

        // Créer les allocations RenderScript si elles n'existent pas ou si la taille a changé.
        if (allocationIn == null || lastWidth != imageWidth || lastHeight != imageHeight) {
            val yuvType = Type.Builder(rs, Element.U8(rs)).setX(yuvBytes.size)
            allocationIn = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)
            val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(imageWidth).setY(imageHeight)
            allocationOut = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)
            lastWidth = imageWidth
            lastHeight = imageHeight
        }

        // Copier les données du tampon vers l'allocation d'entrée.
        allocationIn!!.copyFrom(yuvBytes)

        // Configurer les allocations pour le script de conversion.
        scriptYuvToRgb.setInput(allocationIn)
        scriptYuvToRgb.forEach(allocationOut)

        // Copier le résultat de la conversion dans le bitmap de sortie.
        allocationOut!!.copyTo(output)
    }

    private fun imageToByteArray(image: Image): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        return nv21
    }
}