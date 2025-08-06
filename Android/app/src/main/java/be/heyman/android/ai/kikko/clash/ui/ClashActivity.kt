package be.heyman.android.ai.kikko.clash.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.clash.data.ClashSettings
import be.heyman.android.ai.kikko.clash.ui.adapter.PlayerAdapter
import be.heyman.android.ai.kikko.clash.ui.dialogs.ClashSetupDialogFragment
import be.heyman.android.ai.kikko.clash.viewmodel.ClashFlowState
import be.heyman.android.ai.kikko.clash.viewmodel.ClashMode
import be.heyman.android.ai.kikko.clash.viewmodel.ClashUiState
import be.heyman.android.ai.kikko.clash.viewmodel.ClashViewModel
import be.heyman.android.ai.kikko.clash.viewmodel.DialogState
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.ui.adapters.CardPreviewAdapter
import com.google.android.gms.location.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch
import java.io.File


class ClashActivity : AppCompatActivity(),
    ClashSetupDialogFragment.ClashSetupListener {

    private val TAG = "ClashActivity"
    private val viewModel: ClashViewModel by viewModels()

    private lateinit var setupGroup: Group
    private lateinit var viewPager: ViewPager2
    private lateinit var backgroundPlayerView: PlayerView
    private var exoPlayer: ExoPlayer? = null

    private lateinit var randomButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var radarButton: ImageButton
    private lateinit var startSoloButton: ExtendedFloatingActionButton
    private lateinit var queenStatusTextView: TextView
    private lateinit var judgeLoadingContainer: View

    private lateinit var p1SlotFood: FrameLayout
    private lateinit var p1SlotPlant: FrameLayout
    private lateinit var p1SlotInsect: FrameLayout
    private lateinit var p1SlotBird: FrameLayout
    private lateinit var p2SlotFood: FrameLayout
    private lateinit var p2SlotPlant: FrameLayout
    private lateinit var p2SlotInsect: FrameLayout
    private lateinit var p2SlotBird: FrameLayout

    private lateinit var p2pPanel: View
    private lateinit var p2pPanelCloseButton: ImageButton
    private lateinit var p2pPanelRadarView: be.heyman.android.ai.kikko.clash.ui.views.RadarView
    private lateinit var p2pPanelStatusText: TextView
    private lateinit var p2pPanelRecyclerView: RecyclerView
    private lateinit var p2pPanelPlayerAdapter: PlayerAdapter

    private lateinit var cardSelectorPanel: View
    private lateinit var selectorDeckIcon: ImageView
    private lateinit var selectorDeckName: TextView
    private lateinit var selectorCloseButton: ImageButton
    private lateinit var selectorRecyclerView: RecyclerView
    private lateinit var selectorAdapter: CardPreviewAdapter

    private var pagerAdapter: ClashPagerAdapter? = null
    private var clashDialog: AlertDialog? = null
    private var connectionRequestDialog: AlertDialog? = null

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var myLocation: Location? = null
    private val locationCallback: LocationCallback

    init {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    myLocation = it
                    if (p2pPanel.translationX == 0f) {
                        viewModel.uiState.value.let { state ->
                            p2pPanelPlayerAdapter.updatePlayers(state.discoveredPlayers, myLocation)
                            p2pPanelRadarView.updatePlayers(state.discoveredPlayers, myLocation)
                        }
                    }
                }
            }
        }
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startP2PWithLocation()
            } else {
                Toast.makeText(this, R.string.p2p_permissions_required, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_clash_arena)
        hideSystemUI()

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        bindViews()
        setupListeners()
        observeViewModel()
        setupViewPagerOnce()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
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
        setupGroup = findViewById(R.id.clash_setup_group)
        viewPager = findViewById(R.id.tournament_viewpager)
        backgroundPlayerView = findViewById(R.id.clash_background_video_view)

        randomButton = findViewById(R.id.clash_button_random)
        settingsButton = findViewById(R.id.clash_button_settings)
        radarButton = findViewById(R.id.clash_button_radar)
        startSoloButton = findViewById(R.id.clash_button_start_solo)
        queenStatusTextView = findViewById(R.id.clash_queen_status)
        judgeLoadingContainer = findViewById(R.id.clash_judge_loading_container)


        p1SlotFood = findViewById(R.id.p1_slot_food)
        p1SlotPlant = findViewById(R.id.p1_slot_plant)
        p1SlotInsect = findViewById(R.id.p1_slot_insect)
        p1SlotBird = findViewById(R.id.p1_slot_bird)
        p2SlotFood = findViewById(R.id.p2_slot_food)
        p2SlotPlant = findViewById(R.id.p2_slot_plant)
        p2SlotInsect = findViewById(R.id.p2_slot_insect)
        p2SlotBird = findViewById(R.id.p2_slot_bird)

        cardSelectorPanel = findViewById(R.id.card_selector_panel_include)
        selectorDeckIcon = cardSelectorPanel.findViewById(R.id.selector_panel_deck_icon)
        selectorDeckName = cardSelectorPanel.findViewById(R.id.selector_panel_deck_name)
        selectorCloseButton = cardSelectorPanel.findViewById(R.id.selector_panel_close_button)
        selectorRecyclerView = cardSelectorPanel.findViewById(R.id.selector_panel_recyclerview)

        p2pPanel = findViewById(R.id.p2p_panel)
        p2pPanelCloseButton = findViewById(R.id.p2p_panel_close_button)
        p2pPanelRadarView = findViewById(R.id.p2p_panel_radar_view)
        p2pPanelStatusText = findViewById(R.id.p2p_panel_status_text)
        p2pPanelRecyclerView = findViewById(R.id.p2p_panel_recycler_view)

        p2pPanelPlayerAdapter = PlayerAdapter(emptyMap(), null) { endpointId ->
            viewModel.connectToPlayer(endpointId)
        }
        p2pPanelRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        p2pPanelRecyclerView.adapter = p2pPanelPlayerAdapter
    }

    private fun setupListeners() {
        startSoloButton.setOnClickListener { viewModel.confirmSetupAndStartSoloClash() }
        settingsButton.setOnClickListener { showClashSetupDialog() }
        randomButton.setOnClickListener { viewModel.generateRandomTeams() }

        radarButton.setOnClickListener {
            if (p2pPanel.translationX > 0f) {
                showP2pPanel()
            } else {
                hideP2pPanel()
            }
        }
        p2pPanelCloseButton.setOnClickListener { hideP2pPanel() }


        p1SlotFood.setOnClickListener { onDeckSlotClicked("Food", 1) }
        p1SlotPlant.setOnClickListener { onDeckSlotClicked("Plant", 1) }
        p1SlotInsect.setOnClickListener { onDeckSlotClicked("Insect", 1) }
        p1SlotBird.setOnClickListener { onDeckSlotClicked("Bird", 1) }

        p2SlotFood.setOnClickListener { onDeckSlotClicked("Food", 2) }
        p2SlotPlant.setOnClickListener { onDeckSlotClicked("Plant", 2) }
        p2SlotInsect.setOnClickListener { onDeckSlotClicked("Insect", 2) }
        p2SlotBird.setOnClickListener { onDeckSlotClicked("Bird", 2) }

        selectorCloseButton.setOnClickListener { hideCardSelector() }
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            backgroundPlayerView.player = player
            val videoUri = Uri.parse("android.resource://$packageName/${R.raw.kikko_clash}")
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

    private fun showP2pPanel() {
        val panelWidth = p2pPanel.width.toFloat()
        ObjectAnimator.ofFloat(p2pPanel, "translationX", panelWidth, 0f).apply {
            duration = 300
            start()
        }
        checkPermissionsAndStartDiscovery()
    }

    private fun hideP2pPanel() {
        val panelWidth = p2pPanel.width.toFloat()
        ObjectAnimator.ofFloat(p2pPanel, "translationX", 0f, panelWidth).apply {
            duration = 300
            start()
        }
        if (viewModel.uiState.value.clashMode == ClashMode.P2P_DISCOVERING) {
            viewModel.stopP2P()
        }
    }

    private fun onDeckSlotClicked(deckName: String, playerIndex: Int) {
        Log.d(TAG, "Deck slot clicked: $deckName for player $playerIndex")

        if (viewModel.uiState.value.clashMode != ClashMode.SOLO && playerIndex == 2) {
            Toast.makeText(this, R.string.clash_waiting_for_opponent, Toast.LENGTH_SHORT).show()
            return
        }

        if (viewModel.uiState.value.isJudgeInitializing) {
            Toast.makeText(this, R.string.clash_waiting_for_judge, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val cards = viewModel.getCardsForDeck(deckName)
            if (cards.isEmpty()) {
                Toast.makeText(this@ClashActivity, getString(R.string.clash_no_cards_in_deck, deckName), Toast.LENGTH_SHORT).show()
                return@launch
            }
            showCardSelectorForDeck(deckName, cards, playerIndex)
        }
    }

    private fun showCardSelectorForDeck(deckName: String, cards: List<KnowledgeCard>, playerIndex: Int) {
        selectorDeckName.text = getString(R.string.clash_selector_title_format, playerIndex, deckName)
        selectorDeckIcon.setImageResource(getDeckIcon(deckName))

        selectorAdapter = CardPreviewAdapter(cards.toMutableList()) { selectedCard ->
            viewModel.handleChampionSelection(deckName, selectedCard, playerIndex)
            hideCardSelector()
        }
        selectorRecyclerView.layoutManager = GridLayoutManager(this, 4)
        selectorRecyclerView.adapter = selectorAdapter

        cardSelectorPanel.visibility = View.VISIBLE
    }

    private fun hideCardSelector() {
        cardSelectorPanel.visibility = View.GONE
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        Log.d(TAG, "Nouvel état UI reçu: flowState=${state.flowState}, clashMode=${state.clashMode}, isJudgeInitializing=${state.isJudgeInitializing}")
                        when (state.flowState) {
                            ClashFlowState.SETUP -> showSetupUI(state)
                            ClashFlowState.DUELING -> showDuelingUI(state)
                            ClashFlowState.FINISHED -> showFinishedUI()
                        }
                        handleDialogState(state.dialogState)
                        state.errorMessage?.let {
                            Toast.makeText(this@ClashActivity, it, Toast.LENGTH_LONG).show()
                            viewModel.clearErrorMessage()
                        }
                    }
                }

                launch {
                    viewModel.p2pEvent.collect { event ->
                        when (event) {
                            is ClashViewModel.P2pEvent.ShowConnectionDialog -> {
                                showConnectionRequestDialog(event.endpointId, event.opponentName, event.authCode)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showSetupUI(state: ClashUiState) {
        setupGroup.visibility = View.VISIBLE
        viewPager.visibility = View.GONE

        judgeLoadingContainer.visibility = if (state.isJudgeInitializing) View.VISIBLE else View.GONE
        val isUiEnabled = !state.isJudgeInitializing

        if (state.clashMode == ClashMode.P2P_CARD_SELECTION && p2pPanel.translationX == 0f) {
            hideP2pPanel()
        }

        queenStatusTextView.text = state.clashSettings?.let {
            getString(R.string.clash_judge_status_format, it.queenModelName, it.brain, it.temperature)
        } ?: getString(R.string.clash_judge_status_placeholder)

        startSoloButton.isEnabled = state.isReadyToClash && isUiEnabled
        randomButton.isEnabled = isUiEnabled
        settingsButton.isEnabled = isUiEnabled
        radarButton.isEnabled = isUiEnabled

        updateAllSelectionSlots(state)

        val isPlayer1Clickable = isUiEnabled
        val isPlayer2Clickable = (state.clashMode == ClashMode.SOLO) && isUiEnabled

        p1SlotFood.isClickable = isPlayer1Clickable
        p1SlotPlant.isClickable = isPlayer1Clickable
        p1SlotInsect.isClickable = isPlayer1Clickable
        p1SlotBird.isClickable = isPlayer1Clickable

        p2SlotFood.isClickable = isPlayer2Clickable
        p2SlotPlant.isClickable = isPlayer2Clickable
        p2SlotInsect.isClickable = isPlayer2Clickable
        p2SlotBird.isClickable = isPlayer2Clickable

        p2pPanelStatusText.text = state.p2pStatus
        p2pPanelPlayerAdapter.updatePlayers(state.discoveredPlayers, myLocation)
        p2pPanelRadarView.updatePlayers(state.discoveredPlayers, myLocation)
    }

    private fun updateAllSelectionSlots(state: ClashUiState) {
        updateSelectionSlot(p1SlotFood, state.myChampions["Food"])
        updateSelectionSlot(p1SlotPlant, state.myChampions["Plant"])
        updateSelectionSlot(p1SlotInsect, state.myChampions["Insect"])
        updateSelectionSlot(p1SlotBird, state.myChampions["Bird"])

        updateSelectionSlot(p2SlotFood, state.opponentChampions["Food"])
        updateSelectionSlot(p2SlotPlant, state.opponentChampions["Plant"])
        updateSelectionSlot(p2SlotInsect, state.opponentChampions["Insect"])
        updateSelectionSlot(p2SlotBird, state.opponentChampions["Bird"])
    }

    private fun updateSelectionSlot(slotView: FrameLayout, card: KnowledgeCard?) {
        val placeholderButton = slotView.getChildAt(0)
        val cardThumbnail = slotView.getChildAt(1)

        if (card == null) {
            placeholderButton.visibility = View.VISIBLE
            cardThumbnail.visibility = View.GONE
        } else {
            placeholderButton.visibility = View.GONE
            cardThumbnail.visibility = View.VISIBLE
            val cardNameView: TextView = cardThumbnail.findViewById(R.id.card_thumbnail_name)
            val cardImageView: ImageView = cardThumbnail.findViewById(R.id.card_thumbnail_image)
            cardNameView.text = card.specificName
            card.imagePath?.let { path ->
                val imgFile = File(path)
                if (imgFile.exists()) {
                    cardImageView.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
                } else {
                    cardImageView.setImageResource(R.drawable.ic_placeholder_card)
                }
            } ?: cardImageView.setImageResource(R.drawable.ic_placeholder_card)
        }
    }


    private fun showDuelingUI(state: ClashUiState) {
        Log.d(TAG, "showDuelingUI: Passage à l'écran de duel.")
        setupGroup.visibility = View.GONE
        viewPager.visibility = View.VISIBLE

        if (pagerAdapter == null || pagerAdapter?.itemCount != state.clashStates.size) {
            pagerAdapter = ClashPagerAdapter(this, state.clashStates.size)
            viewPager.adapter = pagerAdapter
        }

        if (viewPager.currentItem != state.currentDuelIndex && state.currentDuelIndex != -1) {
            viewPager.setCurrentItem(state.currentDuelIndex, true)
        }
    }


    private fun showFinishedUI() {
        Toast.makeText(this, R.string.clash_tournament_finished, Toast.LENGTH_LONG).show()
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000)
            viewModel.stopP2P()
            finish()
        }
    }

    private fun checkPermissionsAndStartDiscovery() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            startP2PWithLocation()
        } else {
            requestMultiplePermissions.launch(missingPermissions.toTypedArray())
        }
    }


    private fun startP2PWithLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
            myLocation = location
            viewModel.startP2PDiscovery(myLocation)
        }
        startLocationUpdates()
    }


    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    private fun setupViewPagerOnce() {
        viewPager.isUserInputEnabled = true
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.onDuelSelected(position)
            }
        })
    }

    private fun showClashSetupDialog() {
        if (supportFragmentManager.findFragmentByTag(ClashSetupDialogFragment.TAG) == null) {
            val currentState = viewModel.uiState.value
            val dialog = ClashSetupDialogFragment.newInstance(
                availableModels = currentState.availableModels,
                currentSettings = currentState.clashSettings
            )
            dialog.show(supportFragmentManager, ClashSetupDialogFragment.TAG)
        }
    }

    private fun handleDialogState(dialogState: DialogState) {
        if (dialogState == DialogState.None) {
            clashDialog?.dismiss()
            clashDialog = null
            return
        }
        if (clashDialog != null) { return }
        when (dialogState) {
            DialogState.ReadyToClash -> {
                clashDialog = AlertDialog.Builder(this@ClashActivity, R.style.KikkoAlertDialogTheme)
                    .setTitle(R.string.dialog_arena_ready_title)
                    .setMessage(R.string.dialog_arena_ready_message)
                    .setPositiveButton(R.string.dialog_launch) { _, _ -> viewModel.confirmAndStartP2pClash() }
                    .setNegativeButton(R.string.dialog_cancel) { _, _ -> viewModel.dismissDialog() }
                    .setOnDismissListener { clashDialog = null }
                    .setCancelable(false)
                    .show()
            }
            DialogState.WaitingForHost -> {
                clashDialog = AlertDialog.Builder(this@ClashActivity, R.style.KikkoAlertDialogTheme)
                    .setTitle(R.string.dialog_teams_complete_title)
                    .setMessage(R.string.dialog_waiting_for_host_message)
                    .setCancelable(false)
                    .setOnDismissListener { clashDialog = null }
                    .show()
            }
            else -> {}
        }
    }

    private fun showConnectionRequestDialog(endpointId: String, opponentName: String, authCode: String) {
        connectionRequestDialog?.dismiss()
        connectionRequestDialog = AlertDialog.Builder(this, R.style.KikkoAlertDialogTheme)
            .setTitle(getString(R.string.dialog_connection_title, opponentName))
            .setMessage(getString(R.string.dialog_connection_message, authCode))
            .setPositiveButton(R.string.dialog_accept) { _, _ ->
                viewModel.acceptConnection(endpointId)
            }
            .setNegativeButton(R.string.dialog_decline) { _, _ ->
                viewModel.rejectConnection(endpointId)
            }
            .setOnDismissListener { connectionRequestDialog = null }
            .setCancelable(false)
            .show()
    }

    private fun getDeckIcon(deckName: String): Int {
        return when (deckName) {
            "Food" -> R.drawable.ic_deck_food
            "Plant" -> R.drawable.ic_deck_plant
            "Insect" -> R.drawable.ic_deck_insect
            "Bird" -> R.drawable.ic_deck_bird
            else -> R.drawable.ic_deck_default
        }
    }

    override fun onClashSettingsConfirmed(settings: ClashSettings) {
        viewModel.updateClashSettings(settings)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: L'activité de Clash est détruite.")
        clashDialog?.dismiss()
        connectionRequestDialog?.dismiss()
        viewModel.stopP2P()
        stopLocationUpdates()
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, ClashActivity::class.java)
        }
    }
}