package be.heyman.android.ai.kikko.pollen

import android.content.Context
import android.util.Log
import be.heyman.android.ai.kikko.pollen.vision.GraphicOverlay
import be.heyman.android.ai.kikko.pollen.vision.VisionProcessorBase
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Processeur spécialisé dans la détection de codes-barres.
 */
class PollenBarcodeScannerProcessor(context: Context) : VisionProcessorBase<List<Barcode>>(context) {

    private val barcodeScanner: BarcodeScanner

    init {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_UPC_A
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    override fun stop() {
        super.stop()
        barcodeScanner.close()
    }

    override fun detectInImage(image: InputImage): Task<List<Barcode>> {
        return barcodeScanner.process(image)
    }

    override fun onSuccess(results: List<Barcode>, graphicOverlay: GraphicOverlay) {
        if (results.isEmpty()) {
            return
        }
        for (barcode in results) {
            graphicOverlay.add(PollenBarcodeGraphic(graphicOverlay, barcode))
        }
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "La détection de code-barres a échoué.", e)
    }

    companion object {
        private const val TAG = "BarcodeScannerProc"
    }
}