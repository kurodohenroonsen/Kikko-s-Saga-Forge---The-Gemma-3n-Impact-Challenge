package be.heyman.android.ai.kikko.pollen.vision // <-- Nouveau sous-package !

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutionException

/**
 * Un ViewModel pour gérer l'objet ProcessCameraProvider de CameraX de manière sûre
 * par rapport au cycle de vie de l'application.
 */
class CameraXViewModel(application: Application) : AndroidViewModel(application) {

    private var cameraProviderLiveData: MutableLiveData<ProcessCameraProvider>? = null

    // Renvoie un LiveData contenant le ProcessCameraProvider.
    val processCameraProvider: LiveData<ProcessCameraProvider>
        get() {
            if (cameraProviderLiveData == null) {
                cameraProviderLiveData = MutableLiveData()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(getApplication())
                cameraProviderFuture.addListener(
                    {
                        try {
                            cameraProviderLiveData!!.setValue(cameraProviderFuture.get())
                        } catch (e: ExecutionException) {
                            // Gérer l'erreur
                        } catch (e: InterruptedException) {
                            // Gérer l'erreur
                        }
                    },
                    ContextCompat.getMainExecutor(getApplication())
                )
            }
            return cameraProviderLiveData!!
        }
}