// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/ForgeLiveActivity.kt ---

// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/ForgeLiveActivity.kt ---

package be.heyman.android.ai.kikko.pollen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.TtsService
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SuppressLint("ClickableViewAccessibility")
class ForgeLiveActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {

    private val viewModel: ForgeLiveViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application)).get(ForgeLiveViewModel::class.java)
    }

    // BOURDON'S LOGGING: TAG pour cette activité.
    private val TAG = "ForgeLiveActivity"

    // Vues
    private lateinit var previewView: PreviewView
    private lateinit var facingSwitch: ToggleButton
    // Vues de dialogue
    private lateinit var bourdonDialogueContainer: View
    private lateinit var bourdonMessageTextView: TextView
    // BOURDON'S REFACTOR: Suppression des vues liées à l'intention.
    // private lateinit var pollenStartCaptureButton: Button
    // private lateinit var intentChoiceContainer: View
    // private lateinit var recordVoiceButton: ImageButton
    // private lateinit var pollenSkipIntentButton: Button
    // Vues de capture
    private lateinit var pollenRecyclerView: RecyclerView
    private lateinit var pollenAdapter: PollenPreviewAdapter
    private lateinit var captureButtonContainer: View
    private lateinit var captureButton: Button
    private lateinit var finishHarvestButton: Button
    // Vues de choix final
    private lateinit var finalChoiceContainer: View
    private lateinit var restartHarvestButton: Button
    private lateinit var sendToHiveButton: Button

    // Utilitaires
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var pollenForge: PollenForge

    // BOURDON'S REFACTOR: Le launcher de permission micro est maintenant obsolète.
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(TAG, "Permission caméra accordée. Initialisation.")
            setupCamera()
        } else {
            Toast.makeText(this, "Permission caméra refusée. Impossible de continuer.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forge_live)
        Log.d(TAG, "[CYCLE DE VIE] onCreate")

        cameraExecutor = Executors.newSingleThreadExecutor()
        pollenForge = PollenForge(this)

        TtsService.initialize(this)

        bindViews()
        setupRecyclerView()
        setupListeners()
        checkCameraPermissionAndSetup()
        observeViewModel()
    }

    private fun bindViews() {
        Log.d(TAG, "Liaison des vues...")
        previewView = findViewById(R.id.live_preview_view)
        facingSwitch = findViewById(R.id.facing_switch)
        bourdonDialogueContainer = findViewById(R.id.bourdon_dialogue_container)
        bourdonMessageTextView = findViewById(R.id.bourdon_message_textview)
        // BOURDON'S REFACTOR: Les vues pour l'intention ne sont plus liées.
        pollenRecyclerView = findViewById(R.id.captured_pollen_recyclerview)
        captureButtonContainer = findViewById(R.id.capture_button_container)
        captureButton = findViewById(R.id.capture_and_forge_button)
        finishHarvestButton = findViewById(R.id.finish_harvest_button)
        finalChoiceContainer = findViewById(R.id.final_choice_container)
        restartHarvestButton = findViewById(R.id.restart_harvest_button)
        sendToHiveButton = findViewById(R.id.send_to_hive_button)
        Log.d(TAG, "Vues liées avec succès.")
    }

    private fun setupRecyclerView() {
        // BOURDON'S REFACTOR: La logique de l'adapter reste la même, mais son nom a été corrigé dans d'autres fichiers.
        pollenAdapter = PollenPreviewAdapter(emptyList()) { pollenCapture ->
            val report = pollenCapture.report
            val jsonReport = pollenCapture.jsonReport
            if (pollenCapture.status == PollenAnalysisStatus.DONE && report != null && jsonReport != null) {
                val textReport = generateTextReport(report)
                SpecialistReportDialogFragment.newInstance(getString(R.string.specialist_report_dialog_title), textReport, jsonReport)
                    .show(supportFragmentManager, "SpecialistReportDialog")
            }
        }
        pollenRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ForgeLiveActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = pollenAdapter
        }
    }

    private fun setupListeners() {
        Log.d(TAG, "Configuration des listeners...")
        facingSwitch.setOnCheckedChangeListener(this)

        // BOURDON'S REFACTOR: Suppression des listeners pour les boutons d'intention.
        // pollenStartCaptureButton.setOnClickListener { viewModel.onStartCaptureRequested() }
        captureButton.setOnClickListener { takePhoto() }
        finishHarvestButton.setOnClickListener { viewModel.onStopHarvesting() }
        restartHarvestButton.setOnClickListener { viewModel.onRestartHarvest() }
        sendToHiveButton.setOnClickListener { viewModel.onSendToHive() }
        Log.d(TAG, "Listeners configurés.")
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        Log.d(TAG, "[UI UPDATE] Nouvel état reçu: ${state.currentStep}")
                        updateUiForState(state)
                    }
                }
                launch {
                    viewModel.navigationEvent.collect {
                        Log.i(TAG, "[NAVIGATION] Événement de navigation reçu. Fermeture de l'activité.")
                        Toast.makeText(this@ForgeLiveActivity, R.string.pollen_save_toast, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun updateUiForState(state: ForgeLiveUiState) {
        if (state.bourdonMessage != null) {
            bourdonDialogueContainer.visibility = View.VISIBLE
            bourdonMessageTextView.text = state.bourdonMessage
            // BOURDON'S REFACTOR: TTS utilise la locale du système.
            TtsService.speak(state.bourdonMessage, Locale.getDefault()) {
                runOnUiThread { viewModel.onBourdonFinishedSpeaking() }
            }
        } else {
            bourdonDialogueContainer.visibility = View.GONE
        }

        // BOURDON'S REFACTOR: Visibilité simplifiée.
        captureButtonContainer.visibility = if (state.currentStep == HarvestStep.USER_CAPTURING_POLLEN) View.VISIBLE else View.GONE
        finalChoiceContainer.visibility = if (state.currentStep == HarvestStep.AWAITING_FINAL_CHOICE || state.currentStep == HarvestStep.SAVING || state.currentStep == HarvestStep.SAVED) View.VISIBLE else View.GONE

        if (state.currentStep == HarvestStep.USER_CAPTURING_POLLEN) {
            finishHarvestButton.visibility = if (state.canFinishHarvest) View.VISIBLE else View.GONE
            captureButton.isEnabled = state.capturedPollen.size < 4
            captureButton.text = if (state.capturedPollen.size >= 4) {
                getString(R.string.pollen_capture_button_full)
            } else {
                getString(R.string.pollen_capture_button_format, state.capturedPollen.size)
            }
        }

        if (state.currentStep == HarvestStep.AWAITING_FINAL_CHOICE || state.currentStep == HarvestStep.SAVING || state.currentStep == HarvestStep.SAVED) {
            val isSaving = state.currentStep == HarvestStep.SAVING
            restartHarvestButton.isEnabled = !isSaving
            sendToHiveButton.isEnabled = !isSaving
        }

        pollenAdapter.updatePollen(state.capturedPollen)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[CYCLE DE VIE] onResume")
        bindCameraUseCases()
        viewModel.startInteraction()
    }

    private fun takePhoto() {
        val imageCapture = this.imageCapture ?: return
        if (viewModel.uiState.value.capturedPollen.size >= 4) return
        captureButton.isEnabled = false
        Log.i(TAG, "Déclenchement de la capture photo.")

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                @SuppressLint("UnsafeOptInUsageError")
                override fun onCaptureSuccess(image: ImageProxy) {
                    Log.d(TAG, "Capture réussie. Passage de l'ImageProxy au ViewModel.")
                    // BOURDON'S CRITICAL FIX: Pass the ImageProxy directly to the ViewModel.
                    // The ViewModel is now responsible for closing it.
                    viewModel.startPollenAnalysis(image, pollenForge)
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Échec de la capture d'image : ", exception)
                    Toast.makeText(this@ForgeLiveActivity, R.string.pollen_capture_failed_toast, Toast.LENGTH_SHORT).show()
                    captureButton.isEnabled = true
                }
            }
        )
    }

    private fun checkCameraPermissionAndSetup() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Permission caméra déjà accordée.")
                setupCamera()
            }
            else -> {
                Log.d(TAG, "Demande de la permission caméra.")
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setupCamera() {
        Log.d(TAG, "Configuration de la caméra...")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        Log.d(TAG, "Liaison des cas d'usage de la caméra.")
        cameraProvider.unbindAll()
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        imageCapture = ImageCapture.Builder().setTargetResolution(android.util.Size(1920, 1080)).build()
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            Log.d(TAG, "Cas d'usage liés avec succès.")
        } catch (e: Exception) {
            Log.e(TAG, "La liaison des cas d'usage a échoué", e)
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        lensFacing = if (isChecked) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        Log.d(TAG, "Changement de caméra vers: ${if (isChecked) "AVANT" else "ARRIÈRE"}")
        bindCameraUseCases()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "[CYCLE DE VIE] onPause")
        cameraProvider?.unbindAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "[CYCLE DE VIE] onDestroy")
        cameraExecutor.shutdown()
        TtsService.shutdown()
    }

    private fun generateTextReport(report: PollenAnalysis): String {
        val sb = StringBuilder()
        sb.append(getString(R.string.specialist_report_global_title)).append("\n")
        if (report.globalAnalysis.isEmpty()) {
            sb.append(getString(R.string.specialist_report_no_global)).append("\n")
        } else {
            report.globalAnalysis.forEach { specialistReport ->
                sb.append(getString(R.string.specialist_report_specialist_opinion, specialistReport.specialistName)).append("\n")
                specialistReport.results.take(3).forEach { result ->
                    // BOURDON'S FIX: Manual string construction to avoid format exceptions
                    val confidencePercent = result.confidence * 100
                    val formattedConfidence = "%.1f".format(Locale.US, confidencePercent)
                    sb.append("  - ${result.label} ($formattedConfidence%%)\n")
                }
            }
        }
        sb.append("\n").append(getString(R.string.specialist_report_objects_title, report.analyzedObjects.size)).append("\n")
        if (report.analyzedObjects.isEmpty()) {
            sb.append(getString(R.string.specialist_report_no_objects)).append("\n")
        } else {
            report.analyzedObjects.forEachIndexed { index, analyzedObject ->
                val mainLabel = analyzedObject.detectedObject.labels.firstOrNull()
                // BOURDON'S FIX: Manual string construction
                val objectLabel = mainLabel?.text ?: getString(R.string.specialist_report_unknown_object)
                val objectConfidence = (mainLabel?.confidence ?: 0f) * 100
                val formattedObjectConfidence = "%.1f".format(Locale.US, objectConfidence)
                sb.append("${index + 1}. $objectLabel ($formattedObjectConfidence%%)\n")

                analyzedObject.specialistReports.forEach { specialistReport ->
                    val specialistLabel = specialistReport.results.firstOrNull()?.label ?: "N/A"
                    sb.append("  - ${specialistReport.specialistName}: $specialistLabel\n")
                }
            }
        }
        return sb.toString()
    }
}
// --- END OF FILE app/src/main/java/be/heyman/android/ai/kikko/pollen/ForgeLiveActivity.kt ---