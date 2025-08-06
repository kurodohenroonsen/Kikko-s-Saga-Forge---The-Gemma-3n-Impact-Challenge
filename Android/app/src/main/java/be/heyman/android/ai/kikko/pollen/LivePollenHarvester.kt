package be.heyman.android.ai.kikko.pollen

import android.annotation.SuppressLint
import androidx.camera.core.ImageProxy
import be.heyman.android.ai.kikko.pollen.vision.GraphicOverlay
import be.heyman.android.ai.kikko.pollen.vision.VisionProcessorBase
import java.util.concurrent.atomic.AtomicInteger

/**
 * Chef d'orchestre qui gère la synchronisation de plusieurs processeurs.
 */
class LivePollenHarvester {

    private val scoutBees = mutableListOf<VisionProcessorBase<*>>()
    private var isShutdown = false

    var isTextRecognitionEnabled = false
    var isBarcodeScanningEnabled = false
    var isObjectDetectionEnabled = false

    @Synchronized
    fun addScoutBee(bee: VisionProcessorBase<*>) {
        scoutBees.add(bee)
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Synchronized
    fun processImageProxy(
        imageProxy: ImageProxy,
        graphicOverlay: GraphicOverlay,
        onAllWorkDone: () -> Unit
    ) {
        if (isShutdown) {
            imageProxy.close()
            return
        }

        val activeProcessors = mutableListOf<VisionProcessorBase<*>>()
        if (isTextRecognitionEnabled) {
            scoutBees.filterIsInstance<PollenTextRecognitionProcessor>().firstOrNull()?.let { activeProcessors.add(it) }
        }
        if (isBarcodeScanningEnabled) {
            scoutBees.filterIsInstance<PollenBarcodeScannerProcessor>().firstOrNull()?.let { activeProcessors.add(it) }
        }
        if (isObjectDetectionEnabled) {
            scoutBees.filterIsInstance<PollenObjectDetectorProcessor>().firstOrNull()?.let { activeProcessors.add(it) }
        }

        if (activeProcessors.isEmpty()) {
            imageProxy.close()
            onAllWorkDone()
            return
        }

        val remainingTasks = AtomicInteger(activeProcessors.size)
        val onFinished = {
            if (remainingTasks.decrementAndGet() == 0) {
                imageProxy.close()
                onAllWorkDone()
            }
        }

        graphicOverlay.clear()

        activeProcessors.forEach { processor ->
            processor.processImageProxy(imageProxy, graphicOverlay, onFinished)
        }
    }

    @Synchronized
    fun stop() {
        isShutdown = true
        scoutBees.forEach { it.stop() }
        scoutBees.clear()
    }
}