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
 *
 * NOTE : Cette implémentation est une adaptation standard pour gérer les conversions d'images
 * de la caméra et est nécessaire pour que les modèles ML puissent traiter les captures.
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

    @Synchronized
    fun yuvToRgb(image: Image, output: Bitmap) {
        val imageWidth = image.width
        val imageHeight = image.height

        // S'assurer que le tampon YUV est assez grand.
        if (yuvBufferSize < imageWidth * imageHeight * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8) {
            yuvBufferSize = imageWidth * imageHeight * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8
            yuvBuffer = ByteBuffer.allocateDirect(yuvBufferSize)
        }
        yuvBuffer!!.rewind()

        // Copier les données des 3 plans (Y, U, V) de l'image dans notre tampon.
        imageToByteBuffer(image.planes, yuvBuffer!!)

        // Créer les allocations RenderScript si elles n'existent pas ou si la taille a changé.
        if (allocationIn == null) {
            val yuvType = Type.Builder(rs, Element.U8(rs)).setX(yuvBufferSize)
            allocationIn = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)
            val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(imageWidth).setY(imageHeight)
            allocationOut = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)
        }

        // Copier les données du tampon vers l'allocation d'entrée.
        allocationIn!!.copyFrom(yuvBuffer!!.array())

        // Configurer les allocations pour le script de conversion.
        scriptYuvToRgb.setInput(allocationIn)
        scriptYuvToRgb.forEach(allocationOut)

        // Copier le résultat de la conversion dans le bitmap de sortie.
        allocationOut!!.copyTo(output)
    }

    private fun imageToByteBuffer(planes: Array<Image.Plane>, yuvBuffer: ByteBuffer) {
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val ySize = yBuffer.remaining()
        var position = 0

        // Copier le plan Y
        yuvBuffer.put(yBuffer)
        position += ySize

        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        // Copier les plans U et V. On gère les formats semi-planaires (comme NV21).
        val vuv = ByteArray(vRowStride)
        if (vPixelStride == 2 && uPixelStride == 2 && vRowStride == uRowStride) {
            vBuffer.get(vuv, 0, vBuffer.remaining())
            uBuffer.get(yuvBuffer.array(), position, uBuffer.remaining())
            for (i in 0 until vuv.size / 2) {
                yuvBuffer.array()[position + 2 * i] = vuv[2 * i]
            }
            position += yuvBuffer.remaining()
        } else {
            yuvBuffer.position(position)
            yuvBuffer.put(vBuffer)
            yuvBuffer.position(position + vBuffer.remaining())
            yuvBuffer.put(uBuffer)
        }
    }
}