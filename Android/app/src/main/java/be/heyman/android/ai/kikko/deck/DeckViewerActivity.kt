package be.heyman.android.ai.kikko.deck

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.GameConstants
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.quiz.QuizActivity
import kotlinx.coroutines.launch

class DeckViewerActivity : AppCompatActivity(), CardDetailsDialogFragment.CardDetailsListener {

    private val viewModel: DeckViewerViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application))
            .get(DeckViewerViewModel::class.java)
    }

    private lateinit var backgroundVideoView: VideoView
    private lateinit var recyclerView: RecyclerView
    private lateinit var deckCardAdapter: DeckCardAdapter

    private lateinit var deckButtonFood: LinearLayout
    private lateinit var deckButtonPlant: LinearLayout
    private lateinit var deckButtonInsect: LinearLayout
    private lateinit var deckButtonBird: LinearLayout
    private lateinit var deckButtons: Map<String, View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deck_viewer)
        hideSystemUI()

        bindViews()
        setupBackgroundVideo()
        setupRecyclerView()
        setupDeckSelectionListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        backgroundVideoView.start()
    }

    override fun onPause() {
        super.onPause()
        backgroundVideoView.stopPlayback()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    private fun bindViews() {
        backgroundVideoView = findViewById(R.id.background_video_view)
        recyclerView = findViewById(R.id.deck_cards_recyclerview)
        deckButtonFood = findViewById(R.id.deck_button_food)
        deckButtonPlant = findViewById(R.id.deck_button_plant)
        deckButtonInsect = findViewById(R.id.deck_button_insect)
        deckButtonBird = findViewById(R.id.deck_button_bird)

        deckButtons = mapOf(
            GameConstants.MASTER_DECK_LIST[0] to deckButtonFood,
            GameConstants.MASTER_DECK_LIST[1] to deckButtonPlant,
            GameConstants.MASTER_DECK_LIST[2] to deckButtonInsect,
            GameConstants.MASTER_DECK_LIST[3] to deckButtonBird
        )
    }

    private fun setupBackgroundVideo() {
        val videoPath = "android.resource://" + packageName + "/" + R.raw.kikko_deck
        val uri = Uri.parse(videoPath)
        backgroundVideoView.setVideoURI(uri)
        backgroundVideoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
            mediaPlayer.setVolume(0f, 0f)
            mediaPlayer.start()
        }
    }

    private fun setupRecyclerView() {
        deckCardAdapter = DeckCardAdapter(emptyList()) { card ->
            CardDetailsDialogFragment.newInstance(card).show(supportFragmentManager, "CardDetailsDialog")
        }
        recyclerView.apply {
            layoutManager = GridLayoutManager(this@DeckViewerActivity, 3)
            adapter = deckCardAdapter
        }
    }

    private fun setupDeckSelectionListeners() {
        deckButtons.forEach { (deckName, buttonView) ->
            buttonView.setOnClickListener {
                viewModel.selectDeck(deckName)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.isLoading) {
                        // Optionnel : Gérer l'état de chargement
                    } else {
                        deckCardAdapter.updateCards(state.filteredCards)
                        updateSelectedDeckButton(state.selectedDeck)
                    }
                }
            }
        }
    }

    private fun updateSelectedDeckButton(selectedDeckName: String) {
        deckButtons.forEach { (deckName, buttonView) ->
            if (deckName == selectedDeckName) {
                buttonView.setBackgroundColor(ContextCompat.getColor(this, R.color.kikko_gold_light))
                buttonView.alpha = 1.0f
            } else {
                buttonView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                buttonView.alpha = 0.7f
            }
        }
    }

    override fun onDeleteCard(card: KnowledgeCard) {
        viewModel.deleteCard(card)
        Toast.makeText(this, getString(R.string.card_deleted_toast, card.specificName), Toast.LENGTH_SHORT).show()
    }

    override fun onLaunchQuiz(card: KnowledgeCard) {
        val intent = QuizActivity.newIntent(this, card)
        startActivity(intent)
    }

    override fun onTranslateCard(card: KnowledgeCard) {
        viewModel.requestTranslation(card)
        Toast.makeText(this, getString(R.string.translation_requested_toast, card.specificName), Toast.LENGTH_LONG).show()
    }
}