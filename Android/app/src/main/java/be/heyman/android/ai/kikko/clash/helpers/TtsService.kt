package be.heyman.android.ai.kikko.clash.helpers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

object TtsService : TextToSpeech.OnInitListener {

    private const val TAG = "KikkoTtsService"

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var isSpeaking = false

    private data class SpeakRequest(val utteranceId: String, val text: String, val locale: Locale, val onDone: (() -> Unit)?)
    private val directSpeakQueue = ConcurrentLinkedQueue<SpeakRequest>()

    fun initialize(context: Context) {
        if (tts == null) {
            Log.d(TAG, "Initialisation du moteur TTS...")
            tts = TextToSpeech(context.applicationContext, this)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    Log.d(TAG, "TTS a commencé à parler: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS a fini de parler: $utteranceId")
                    isSpeaking = false
                    val completedRequest = directSpeakQueue.poll()
                    completedRequest?.onDone?.invoke()
                    processDirectSpeakQueue()
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Erreur TTS pour: $utteranceId")
                    isSpeaking = false
                    directSpeakQueue.poll()
                    processDirectSpeakQueue()
                }
            })
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            Log.d(TAG, "Moteur TTS initialisé avec succès.")
            tts?.language = Locale.getDefault()
            processDirectSpeakQueue()
        } else {
            isTtsInitialized = false
            Log.e(TAG, "Échec de l'initialisation du moteur TTS. Statut: $status")
        }
    }

    fun speak(text: String, locale: Locale, onDone: (() -> Unit)? = null) {
        val utteranceId = "KikkoSpeak-${UUID.randomUUID()}"
        val request = SpeakRequest(utteranceId, text, locale, onDone)
        directSpeakQueue.add(request)

        if (isTtsInitialized) {
            processDirectSpeakQueue()
        }
    }

    private fun processDirectSpeakQueue() {
        if (isSpeaking || !isTtsInitialized || directSpeakQueue.isEmpty()) {
            return
        }

        val request = directSpeakQueue.peek() ?: return
        tts?.language = request.locale
        tts?.speak(request.text, TextToSpeech.QUEUE_ADD, null, request.utteranceId)
    }

    fun stopAndClearQueue() {
        tts?.stop()
        directSpeakQueue.clear()
        isSpeaking = false
        Log.d(TAG, "TTS arrêté et file d'attente vidée.")
    }

    fun shutdown() {
        stopAndClearQueue()
        tts?.shutdown()
        tts = null
        isTtsInitialized = false
        Log.d(TAG, "Moteur TTS libéré.")
    }
}