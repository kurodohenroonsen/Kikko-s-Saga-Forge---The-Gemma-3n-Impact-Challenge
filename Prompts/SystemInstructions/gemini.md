package be.heyman.android.ai.kikko.royal_audience

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.SttVoskService
import be.heyman.android.ai.kikko.TtsService
import be.heyman.android.ai.kikko.VoskStatus
import com.bumptech.glide.Glide
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.launch
import java.io.File

class RoyalAudienceActivity : AppCompatActivity(),
    AudienceSettingsDialogFragment.AudienceSettingsListener,
    QueenSelectorDialogFragment.QueenSelectorListener {

    private val viewModel: RoyalAudienceViewModel by lazy {
        Log.d(TAG, "👑 Audience Royale: J'utilise le bon Maître des Clés (SavedStateViewModelFactory) pour présenter nos respects à la Reine. Elle a besoin de son registre personnel (SavedStateHandle) !")
        ViewModelProvider(this, SavedStateViewModelFactory(application, this))
            .get(RoyalAudienceViewModel::class.java)
    }
    private lateinit var audienceAdapter: RoyalAudienceAdapter

    private lateinit var rootContainer: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var inputBar: View
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var micButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var modelSelectorContainer: View
    private lateinit var modelNameTextView: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var backButton: ImageButton

    private lateinit var imagePreviewContainer: FrameLayout
    private lateinit var previewImageView: ImageView
    private lateinit var removePreviewButton: ImageButton

    private lateinit var backgroundPlayerView: PlayerView
    private var exoPlayer: ExoPlayer? = null

    private var currentTranscription = ""
    private var selectedImageUri: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startListening()
        } else {
            Toast.makeText(this, R.string.mic_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImagePreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_royal_audience)
        Log.i(TAG, "🏛️ Entrée dans la salle d'audience de la Reine.")

        TtsService.initialize(this)

        bindViews()
        setupWindowInsets()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        observeVoskService()
        ensureVoskModelIsLoaded()

        updateSendButtonState()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun bindViews() {
        rootContainer = findViewById(R.id.audience_root_container)
        recyclerView = findViewById(R.id.audience_recyclerview)
        backgroundPlayerView = findViewById(R.id.audience_background_video_view)

        inputBar = findViewById<View>(R.id.audience_input_bar)
        inputEditText = inputBar.findViewById(R.id.chat_input_edittext)
        sendButton = inputBar.findViewById(R.id.chat_send_button)
        micButton = inputBar.findViewById(R.id.chat_mic_button)
        attachButton = inputBar.findViewById(R.id.chat_attach_button)

        toolbar = findViewById(R.id.audience_toolbar)
        backButton = findViewById(R.id.audience_back_button)
        settingsButton = findViewById(R.id.audience_settings_button)
        modelSelectorContainer = findViewById(R.id.audience_model_selector_container)
        modelNameTextView = findViewById(R.id.audience_model_name)

        imagePreviewContainer = findViewById(R.id.audience_image_preview_container)

        Log.d(TAG, "🖼️ Vues de l'interface liées.")
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )

            inputBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = imeInsets.bottom
            }

            recyclerView.updatePadding(bottom = imeInsets.bottom + systemBars.bottom)

            insets
        }
    }

    private fun initializePlayer() {
        Log.d(TAG, "🎥 Initialisation du fond vidéo pour l'ambiance royale.")
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            backgroundPlayerView.player = player
            val videoUri = Uri.parse("android.resource://$packageName/${R.raw.audience}")
            val mediaItem = MediaItem.fromUri(videoUri)
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.volume = 0f
            player.playWhenReady = true
            player.prepare()
        }
    }

    private fun releasePlayer() {
        Log.d(TAG, "🎬 Libération des ressources vidéo.")
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun setupRecyclerView() {
        audienceAdapter = RoyalAudienceAdapter()
        recyclerView.apply {
            adapter = audienceAdapter
            layoutManager = LinearLayoutManager(this@RoyalAudienceActivity).apply {
                stackFromEnd = true
            }
        }
        Log.d(TAG, "📜 Parchemin de conversation déroulé et prêt.")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        backButton.setOnClickListener { finish() }

        sendButton.setOnClickListener {
            val userInput = inputEditText.text.toString()
            viewModel.sendMessage(userInput, selectedImageUri)
            inputEditText.text.clear()
            clearImagePreview()
        }

        inputEditText.addTextChangedListener {
            updateSendButtonState()
        }

        micButton.setOnClickListener {
            if (SttVoskService.voskResult.value?.status != VoskStatus.LISTENING) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListening()
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        attachButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        settingsButton.setOnClickListener {
            val currentSettings = viewModel.uiState.value.audienceSettings
            AudienceSettingsDialogFragment.newInstance(currentSettings)
                .show(supportFragmentManager, AudienceSettingsDialogFragment.TAG)
        }
        modelSelectorContainer.setOnClickListener {
            val availableModels = viewModel.uiState.value.availableModels
            val selectedModel = viewModel.uiState.value.selectedModelName
            QueenSelectorDialogFragment.newInstance(availableModels, selectedModel)
                .show(supportFragmentManager, QueenSelectorDialogFragment.TAG)
        }
    }

    private fun startListening() {
        Log.i(TAG, "🎤 Démarrage de l'écoute. Le Butineur s'adresse à la Reine de vive voix.")
        if (!SttVoskService.isModelLoaded()) {
            Toast.makeText(this, R.string.audience_no_voice_model, Toast.LENGTH_LONG).show()
            return
        }
        currentTranscription = inputEditText.text.toString()
        if (currentTranscription.isNotBlank() && !currentTranscription.endsWith(" ")) {
            currentTranscription += " "
        }
        SttVoskService.startListening()
    }

    private fun updateMicButtonState(isListening: Boolean) {
        if (isListening) {
            micButton.setImageResource(R.drawable.ic_stop)
        } else {
            micButton.setImageResource(android.R.drawable.ic_btn_speak_now)
        }
        micButton.isEnabled = !viewModel.uiState.value.isLoading
    }

    private fun updateSendButtonState() {
        val hasText = inputEditText.text.isNotBlank()
        val hasImage = selectedImageUri != null
        val shouldShowSend = hasText || hasImage

        if (shouldShowSend) {
            if (SttVoskService.voskResult.value?.status == VoskStatus.LISTENING) {
                SttVoskService.stopListening()
            }
            sendButton.visibility = View.VISIBLE
            micButton.visibility = View.GONE
        } else {
            sendButton.visibility = View.GONE
            micButton.visibility = View.VISIBLE
        }
    }

    private fun showImagePreview() {
        if (imagePreviewContainer.childCount == 0) {
            layoutInflater.inflate(R.layout.layout_chat_image_preview, imagePreviewContainer, true)
            previewImageView = imagePreviewContainer.findViewById(R.id.preview_image_view)
            removePreviewButton = imagePreviewContainer.findViewById(R.id.remove_preview_button)
            removePreviewButton.setOnClickListener { clearImagePreview() }
        }
        imagePreviewContainer.visibility = View.VISIBLE
        Glide.with(this)
            .load(selectedImageUri)
            .into(previewImageView)
        updateSendButtonState()
    }

    private fun clearImagePreview() {
        selectedImageUri = null
        imagePreviewContainer.visibility = View.GONE
        updateSendButtonState()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        audienceAdapter.submitList(state.messages)
                        modelNameTextView.text = state.selectedModelName ?: getString(R.string.audience_no_queen_available_short)

                        if (state.messages.isNotEmpty()) {
                            recyclerView.post { recyclerView.scrollToPosition(state.messages.size - 1) }
                        }

                        val isUserInputEnabled = !state.isLoading
                        inputEditText.isEnabled = isUserInputEnabled
                        attachButton.isEnabled = isUserInputEnabled
                        micButton.isEnabled = isUserInputEnabled
                    }
                }
                launch {
                    viewModel.toastEvent.collect { message ->
                        Toast.makeText(this@RoyalAudienceActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        Log.d(TAG, "👀 Observation de l'état de l'audience (ViewModel) activée.")
    }

    private fun observeVoskService() {
        SttVoskService.voskResult.observe(this) { result ->
            val isCurrentlyListening = result.status == VoskStatus.LISTENING
            updateMicButtonState(isCurrentlyListening)

            if (isCurrentlyListening) {
                val fullText = currentTranscription + result.text
                inputEditText.setText(fullText)
                inputEditText.setSelection(fullText.length)
            } else {
                if(result.status == VoskStatus.FINAL_RESULT) {
                    val fullText = (currentTranscription + result.text).trim()
                    Log.i(TAG, "🎤 Transcription finale reçue : '$fullText'")
                    inputEditText.setText(fullText)
                    inputEditText.setSelection(fullText.length)
                }
            }
        }
    }

    private fun ensureVoskModelIsLoaded() {
        if (SttVoskService.isModelLoaded()) return

        val baseModelDir = File(filesDir, "vosk-models")
        if (baseModelDir.exists() && baseModelDir.isDirectory) {
            val modelDirs = baseModelDir.listFiles { file -> file.isDirectory }
            if (!modelDirs.isNullOrEmpty()) {
                val defaultModelDir = modelDirs.first()
                val dummyModel = Model(name = defaultModelDir.name, downloadFileName = "", url = "", sizeInBytes = 0)
                Log.d(TAG, "🧠 Chargement du modèle vocal par défaut : '${defaultModelDir.name}'")
                SttVoskService.loadModel(dummyModel, baseModelDir) { success ->
                    if (success) {
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.audience_default_voice_model_loaded, defaultModelDir.name), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, R.string.audience_no_voice_model, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ Audience mise en pause.")
        SttVoskService.stopListening()
        TtsService.stopAndClearQueue()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "💥 L'audience est terminée. Libération des ressources.")
        SttVoskService.reset()
        TtsService.shutdown()
    }

    override fun onSettingsConfirmed(settings: AudienceSettings) {
        viewModel.updateAudienceSettings(settings)
    }

    override fun onQueenSelected(modelName: String) {
        viewModel.updateSelectedQueen(modelName)
    }

    companion object {
        private const val TAG = "RoyalAudienceActivity"
        const val CARD_ID_KEY = "cardId"
        fun newIntent(context: Context, cardId: Long = -1L): Intent {
            return Intent(context, RoyalAudienceActivity::class.java).apply {
                putExtra(CARD_ID_KEY, cardId)
            }
        }
    }
}