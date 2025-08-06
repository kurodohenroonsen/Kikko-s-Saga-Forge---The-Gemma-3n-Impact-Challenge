package be.heyman.android.ai.kikko

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import be.heyman.android.ai.kikko.data.Model
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException

enum class VoskStatus {
    IDLE,
    LOADING,
    READY,
    LISTENING,
    FINAL_RESULT,
    ERROR
}

data class VoskResult(
    val status: VoskStatus,
    val text: String = "",
    val isPartial: Boolean = false
)

object SttVoskService {
    private const val TAG = "SttVoskService"

    private var voskModel: org.vosk.Model? = null
    private var speechService: SpeechService? = null
    private var currentModelPath: String? = null

    private val _voskResult = MutableLiveData<VoskResult>()
    val voskResult: LiveData<VoskResult> = _voskResult

    private val voskTranscript = StringBuilder()

    fun isModelLoaded(): Boolean {
        return voskModel != null
    }

    fun loadModel(modelToLoad: Model, baseDir: File, onFinished: (Boolean) -> Unit) {
        val modelPath = File(baseDir, modelToLoad.name).absolutePath
        if (currentModelPath == modelPath && voskModel != null) {
            onFinished(true)
            return
        }

        _voskResult.postValue(VoskResult(VoskStatus.LOADING, "Chargement du modèle ${modelToLoad.name}..."))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                voskModel?.close()
                voskModel = org.vosk.Model(modelPath)
                currentModelPath = modelPath
                withContext(Dispatchers.Main) {
                    _voskResult.postValue(VoskResult(VoskStatus.READY, "Modèle ${modelToLoad.name} prêt."))
                    onFinished(true)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    _voskResult.postValue(VoskResult(VoskStatus.ERROR, "Erreur chargement modèle: ${e.message}"))
                    onFinished(false)
                }
            }
        }
    }

    fun startListening() {
        if (speechService != null) return
        val localModel = voskModel ?: run {
            _voskResult.postValue(VoskResult(VoskStatus.ERROR, "Aucun modèle Vosk n'est chargé."))
            return
        }

        voskTranscript.clear()
        _voskResult.postValue(VoskResult(VoskStatus.LISTENING, "Écoute..."))

        try {
            val recognizer = Recognizer(localModel, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(recognitionListener)
        } catch (e: IOException) {
            _voskResult.postValue(VoskResult(VoskStatus.ERROR, "Erreur au démarrage: ${e.message}"))
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
        if (_voskResult.value?.status == VoskStatus.LISTENING) {
            _voskResult.postValue(VoskResult(VoskStatus.IDLE, voskTranscript.toString()))
        }
    }

    fun reset() {
        stopListening()
        voskTranscript.clear()
        _voskResult.postValue(VoskResult(VoskStatus.IDLE, ""))
        Log.d(TAG, "Service Vosk réinitialisé.")
    }

    fun shutdown() {
        speechService?.shutdown()
        voskModel?.close()
        speechService = null
        voskModel = null
        currentModelPath = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResult(hypothesis: String?) {
            try {
                val resultText = hypothesis?.let { JSONObject(it).getString("text") } ?: ""
                if (resultText.isNotBlank()) {
                    voskTranscript.append(resultText).append(" ")
                    _voskResult.postValue(VoskResult(VoskStatus.FINAL_RESULT, voskTranscript.toString().trim()))
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }

        override fun onPartialResult(hypothesis: String?) {
            try {
                val partialText = hypothesis?.let { JSONObject(it).getString("partial") } ?: ""
                _voskResult.postValue(VoskResult(VoskStatus.LISTENING, voskTranscript.toString() + partialText, isPartial = true))
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            onResult(hypothesis)
            onListeningStopped()
        }

        override fun onError(e: Exception?) {
            _voskResult.postValue(VoskResult(VoskStatus.ERROR, "Erreur: ${e?.message}"))
            onListeningStopped()
        }

        override fun onTimeout() {
            onListeningStopped()
        }

        private fun onListeningStopped(){
            if(speechService != null){
                stopListening()
            }
        }
    }
}