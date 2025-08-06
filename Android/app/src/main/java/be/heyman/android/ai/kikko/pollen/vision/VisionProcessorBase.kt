package be.heyman.android.ai.kikko.pollen.vision

import android.content.Context
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.vision.common.InputImage

/**
 * Classe de base simplifiée et corrigée.
 */
abstract class VisionProcessorBase<T>(val context: Context) {

    private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)
    private var isShutdown = false

    @ExperimentalGetImage
    fun processImageProxy(
        imageProxy: ImageProxy,
        graphicOverlay: GraphicOverlay,
        onFinished: () -> Unit // Un simple callback pour dire "j'ai fini"
    ) {
        if (isShutdown) {
            onFinished()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onFinished()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detectInImage(inputImage)
            .addOnSuccessListener(executor) { results: T ->
                this.onSuccess(results, graphicOverlay)
                graphicOverlay.postInvalidate()
            }
            .addOnFailureListener(executor) { e: Exception ->
                this.onFailure(e)
            }
            .addOnCompleteListener {
                onFinished()
            }
    }

    open fun stop() {
        executor.shutdown()
        isShutdown = true
    }

    protected abstract fun detectInImage(image: InputImage): Task<T>

    protected abstract fun onSuccess(results: T, graphicOverlay: GraphicOverlay)

    protected abstract fun onFailure(e: Exception)

    companion object {
        private const val TAG = "VisionProcessorBase"
    }
}