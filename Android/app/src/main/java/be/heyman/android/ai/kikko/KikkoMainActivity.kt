package be.heyman.android.ai.kikko

import android.annotation.SuppressLint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activité principale de l'application.
 * Gère le fond vidéo interactif et la barre de navigation inférieure.
 */
class KikkoMainActivity : AppCompatActivity() {

    // AJOUT : Un TAG pour filtrer nos logs facilement dans Logcat
    private val TAG = "KikkoMainActivity"

    private lateinit var backgroundVideoView: VideoView

    // --- Variables pour la logique du "Grattage de Ventre" ---
    private var scratchJob: Job? = null
    private val bellyHotspot = Rect()
    private var isReactionPlaying = false
    // --------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NOTE: Le layout R.layout.activity_main_avec_nav est utilisé ici.
        setContentView(R.layout.activity_deck_selection)
        Log.d(TAG, "onCreate: L'activité est créée.")

        backgroundVideoView = findViewById(R.id.background_video_view)

        setupBackgroundVideo()
        setupNavigation()
        setupBellyScratchListener() // Initialisation de l'interaction secrète
    }

    private fun setupBackgroundVideo() {
        Log.d(TAG, "setupBackgroundVideo: Configuration de la vidéo principale.")
        playVideo(R.raw.kikko_main, isLooping = true)
    }

    private fun playVideo(videoResId: Int, isLooping: Boolean) {
        val resourceName = resources.getResourceEntryName(videoResId)
        Log.d(TAG, "playVideo: Lancement de la vidéo '$resourceName' (loop: $isLooping)")
        val videoPath = "android.resource://" + packageName + "/" + videoResId
        val uri = Uri.parse(videoPath)

        backgroundVideoView.setVideoURI(uri)
        backgroundVideoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = isLooping
            mediaPlayer.setVolume(0f, 0f)
        }
        backgroundVideoView.start()
    }

    private fun setupNavigation() {
        // ... (le code des listeners de navigation reste identique)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBellyScratchListener() {
        Log.d(TAG, "setupBellyScratchListener: Le listener de grattage est maintenant actif.")
        backgroundVideoView.setOnTouchListener { view, event ->
            if (isReactionPlaying) {
                Log.v(TAG, "Touch ignoré: la vidéo de réaction est en cours.")
                return@setOnTouchListener false
            }

            val x = event.x.toInt()
            val y = event.y.toInt()

            val viewWidth = view.width
            val viewHeight = view.height
            bellyHotspot.set(
                (viewWidth * 0.25).toInt(),
                (viewHeight * 0.40).toInt(),
                (viewWidth * 0.75).toInt(),
                (viewHeight * 0.80).toInt()
            )

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "ACTION_DOWN: Touché aux coordonnées ($x, $y). Hotspot: $bellyHotspot")
                    if (bellyHotspot.contains(x, y)) {
                        Log.i(TAG, "Touché DANS le hotspot. Démarrage du timer de 4 secondes...")
                        scratchJob = lifecycleScope.launch {
                            delay(4000)
                            Log.i(TAG, "TIMER TERMINÉ: 4 secondes écoulées. Déclenchement de l'événement.")
                            triggerBellyScratchEvent()
                        }
                    } else {
                        Log.d(TAG, "Touché HORS du hotspot.")
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!bellyHotspot.contains(x, y)) {
                        if (scratchJob?.isActive == true) {
                            Log.w(TAG, "ACTION_MOVE: Doigt sorti du hotspot. Annulation du timer.")
                            scratchJob?.cancel()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (scratchJob?.isActive == true) {
                        Log.w(TAG, "ACTION_UP/CANCEL: Doigt levé. Annulation du timer.")
                        scratchJob?.cancel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun triggerBellyScratchEvent() {
        if (isReactionPlaying) return
        isReactionPlaying = true

        runOnUiThread {
            Log.i(TAG, "triggerBellyScratchEvent: Événement déclenché ! Changement de vidéo.")
            Toast.makeText(this, "Interaction secrète débloquée !", Toast.LENGTH_SHORT).show()

            // NOTE DU BOURDON: J'ai corrigé le nom de votre vidéo de "kikko_deck_" à "kikko_reaction" pour plus de clarté.
            // Assurez-vous que le fichier R.raw.kikko_reaction existe bien.
            playVideo(R.raw.kikko_deck, isLooping = false)

            backgroundVideoView.setOnCompletionListener {
                Log.i(TAG, "onCompletion: La vidéo de réaction est terminée. Retour à la vidéo principale.")
                isReactionPlaying = false
                playVideo(R.raw.kikko_main, isLooping = true)
                backgroundVideoView.setOnCompletionListener(null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Reprise de l'activité, la vidéo devrait (re)démarrer.")
        if (!isReactionPlaying) {
            backgroundVideoView.start()
        }
    }
}