package be.heyman.android.ai.kikko.clash.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.clash.data.ClashStatus
import be.heyman.android.ai.kikko.clash.viewmodel.ClashMode
import be.heyman.android.ai.kikko.clash.viewmodel.ClashUiState
import be.heyman.android.ai.kikko.clash.viewmodel.ClashViewModel
import be.heyman.android.ai.kikko.model.KnowledgeCard
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

private const val ARG_DUEL_INDEX = "duel_index"

class ClashDuelFragment : Fragment() {

    private var duelIndex: Int = 0
    private val viewModel: ClashViewModel by activityViewModels()

    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null

    private lateinit var questionTextView: TextView
    private lateinit var player1CardView: View
    private lateinit var player2CardView: View
    private lateinit var reasoningTextView: TextView
    private lateinit var streamingReasoningTextView: TextView
    private lateinit var loadingIndicator: CircularProgressIndicator
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var finishButton: Button
    private lateinit var navSpacer: View


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            duelIndex = it.getInt(ARG_DUEL_INDEX)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_duel_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onResume() {
        super.onResume()
        val currentState = viewModel.uiState.value
        val duelState = currentState.clashStates.getOrNull(duelIndex)
        if (duelState != null && currentState.currentDuelIndex == duelIndex && duelState.status == ClashStatus.PENDING) {
            if (currentState.isArbitrator || currentState.clashMode == ClashMode.SOLO) {
                viewModel.runInferenceForCurrentDuel()
            }
        }
    }

    private fun bindViews(view: View) {
        playerView = view.findViewById(R.id.duel_background_video_view)
        questionTextView = view.findViewById(R.id.duel_question_textview)
        player1CardView = view.findViewById(R.id.duel_player1_card)
        player2CardView = view.findViewById(R.id.duel_player2_card)
        reasoningTextView = view.findViewById(R.id.duel_reasoning_textview)
        streamingReasoningTextView = view.findViewById(R.id.duel_streaming_reasoning_textview)
        loadingIndicator = view.findViewById(R.id.duel_loading_indicator)
        previousButton = view.findViewById(R.id.duel_previous_button)
        nextButton = view.findViewById(R.id.duel_next_button)
        finishButton = view.findViewById(R.id.duel_finish_button)
        navSpacer = view.findViewById(R.id.duel_nav_spacer)

        previousButton.setOnClickListener { viewModel.proceedToPreviousDuel() }
        nextButton.setOnClickListener { viewModel.proceedToNextDuel() }
        finishButton.setOnClickListener { viewModel.proceedToNextDuel() }
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(requireContext()).build().also { player ->
            playerView.player = player
            val videoUri = Uri.parse("android.resource://${requireContext().packageName}/${R.raw.kikko_question}")
            val mediaItem = MediaItem.fromUri(videoUri)
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.volume = 0f
            player.playWhenReady = true
            player.prepare()
        }
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                state.clashStates.getOrNull(duelIndex)?.let {
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: ClashUiState) {
        val duelState = state.clashStates.getOrNull(duelIndex) ?: return
        val totalDuels = state.clashStates.size

        questionTextView.text = duelState.question ?: getString(R.string.clash_duel_question_placeholder)
        bindCard(player1CardView, duelState.player1Card)
        bindCard(player2CardView, duelState.player2Card)

        if (state.clashSettings?.isTtsEnabled == true) {
            viewModel.requestTtsForQuestion(duelIndex, autoPlay = true)
        }

        val isHost = state.isArbitrator || state.clashMode == ClashMode.SOLO
        val isCompleted = duelState.status == ClashStatus.COMPLETED || duelState.status == ClashStatus.ERROR

        previousButton.visibility = if (isHost && isCompleted && duelIndex > 0) View.VISIBLE else View.GONE
        nextButton.visibility = if (isHost && isCompleted && duelIndex < totalDuels - 1) View.VISIBLE else View.GONE
        finishButton.visibility = if (isHost && isCompleted && duelIndex == totalDuels - 1) View.VISIBLE else View.GONE
        navSpacer.visibility = if (previousButton.visibility == View.VISIBLE && (nextButton.visibility == View.VISIBLE || finishButton.visibility == View.VISIBLE)) View.VISIBLE else View.GONE
        loadingIndicator.visibility = if (duelState.status == ClashStatus.INFERRING || duelState.status == ClashStatus.TRANSLATING) View.VISIBLE else View.GONE

        streamingReasoningTextView.visibility = if (duelState.status == ClashStatus.INFERRING) View.VISIBLE else View.GONE
        streamingReasoningTextView.text = duelState.streamingReasoning
        reasoningTextView.visibility = if (isCompleted) View.VISIBLE else View.INVISIBLE

        if (isCompleted) {
            reasoningTextView.text = duelState.translatedReasoning ?: duelState.errorMessage ?: ""
            animateWinner(duelState.winner)
            if (state.clashSettings?.isTtsEnabled == true && !duelState.ttsHasBeenPlayed) {
                viewModel.requestTtsForReasoning(duelIndex, autoPlay = true)
            }
        }
    }

    private fun bindCard(cardView: View, card: KnowledgeCard) {
        val cardTitle = cardView.findViewById<TextView>(R.id.card_title)
        val cardDeckName = cardView.findViewById<TextView>(R.id.card_deck_name)
        val cardImage = cardView.findViewById<ImageView>(R.id.card_image)

        cardTitle.text = card.specificName
        cardDeckName.text = card.deckName

        card.imagePath?.let {
            val imgFile = File(it)
            if (imgFile.exists()) {
                val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                cardImage.setImageBitmap(myBitmap)
            }
        }
    }

    private fun animateWinner(winner: String?) {
        val winnerScale = 1.05f
        val loserScale = 0.95f
        val winnerElevation = 16f
        val loserElevation = 4f
        val duration = 500L

        val (winnerView, loserView) = when (winner) {
            "player1" -> player1CardView to player2CardView
            "player2" -> player2CardView to player1CardView
            else -> null to null
        }

        if (winnerView != null && loserView != null) {
            winnerView.bringToFront()

            val winnerScaleX = ObjectAnimator.ofFloat(winnerView, "scaleX", winnerScale)
            val winnerScaleY = ObjectAnimator.ofFloat(winnerView, "scaleY", winnerScale)
            val winnerElevationAnim = ObjectAnimator.ofFloat(winnerView, "cardElevation", winnerElevation)

            val loserScaleX = ObjectAnimator.ofFloat(loserView, "scaleX", loserScale)
            val loserScaleY = ObjectAnimator.ofFloat(loserView, "scaleY", loserScale)
            val loserElevationAnim = ObjectAnimator.ofFloat(loserView, "cardElevation", loserElevation)

            val animatorSet = AnimatorSet().apply {
                playTogether(winnerScaleX, winnerScaleY, winnerElevationAnim, loserScaleX, loserScaleY, loserElevationAnim)
                this.duration = duration
                interpolator = AccelerateDecelerateInterpolator()
            }
            animatorSet.start()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(duelIndex: Int) =
            ClashDuelFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DUEL_INDEX, duelIndex)
                }
            }
    }
}