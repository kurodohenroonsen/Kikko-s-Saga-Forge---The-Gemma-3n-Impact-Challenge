package be.heyman.android.ai.kikko

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import be.heyman.android.ai.kikko.clash.ui.ClashActivity
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.deck.DeckViewerActivity
import be.heyman.android.ai.kikko.forge.ForgeWorkshopActivity
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import be.heyman.android.ai.kikko.pollen.ForgeLiveActivity
import be.heyman.android.ai.kikko.prompt.PromptEditorActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class StartActivity : AppCompatActivity(), ToolsDialogFragment.ToolsDialogListener {

    private val TAG = "KikkoStart"
    private var modelToTest: File? = null

    private lateinit var backgroundVideoView: VideoView
    private var scratchJob: Job? = null
    private val bellyHotspot = Rect()
    private var isReactionPlaying = false

    private lateinit var pollenGrainDao: PollenGrainDao
    private lateinit var cardDao: CardDao
    private lateinit var rawPollenCounter: TextView
    private lateinit var inForgePollenCounter: TextView
    private lateinit var totalHoneyCounter: TextView
    private lateinit var errorPollenCounter: TextView

    private val importSagaLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val importedCount = SagaArchiver.importSaga(this@StartActivity, uri)
                if (importedCount >= 0) {
                    val message = resources.getQuantityString(R.plurals.import_saga_success, importedCount, importedCount)
                    Toast.makeText(this@StartActivity, message, Toast.LENGTH_LONG).show()
                    updateForgeCounters()
                } else {
                    Toast.makeText(this@StartActivity, R.string.import_saga_failure, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val addModelLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                Toast.makeText(this@StartActivity, R.string.importing_new_model, Toast.LENGTH_SHORT).show()
                val localPath = copyModelToAppStorage(uri, "imported_models")
                if (localPath != null) {
                    Toast.makeText(this@StartActivity, R.string.import_new_model_success, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@StartActivity, R.string.import_new_model_failure, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val importVoskModelLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                handleVoskModelImport(it)
            }
        }
    }
    /*
        private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                modelToTest?.let { testVoskModel(it) }
            } else {
                Toast.makeText(this, R.string.mic_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    */
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        Log.d(TAG, "onCreate: L'activité de démarrage est en cours de création.")

        pollenGrainDao = PollenGrainDao(this)
        cardDao = CardDao(this)
        Log.d(TAG, "onCreate: DAOs have been initialized.")

        backgroundVideoView = findViewById(R.id.background_video_view)
        setupBellyScratchListener()

        bindViewsAndSetupNavigation()
        setupWindowInsets()
        //observeVoskService()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: L'activité est visible, mise à jour des compteurs.")
        updateForgeCounters()

        if (!isReactionPlaying) {
            setupBackgroundVideo()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Mise en pause de l'activité, la vidéo s'arrête.")
        backgroundVideoView.stopPlayback()
    }

    private fun setupWindowInsets() {
        val rootContainer: View = findViewById(R.id.start_root_container)
        val toolsButton: View = findViewById(R.id.start_button_tools)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            toolsButton.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top + 16
            }
            WindowInsetsCompat.CONSUMED
        }
    }


    private fun bindViewsAndSetupNavigation() {
        val buttonDecks: View = findViewById(R.id.button_kikko)
        val buttonPollen: View = findViewById(R.id.button_pollen)
        val buttonForge: View = findViewById(R.id.button_forge)
        val buttonClash: View = findViewById(R.id.button_clash)
        val buttonTools: ImageButton = findViewById(R.id.start_button_tools)

        rawPollenCounter = findViewById(R.id.raw_pollen_counter)
        inForgePollenCounter = findViewById(R.id.in_forge_pollen_counter)
        totalHoneyCounter = findViewById(R.id.total_honey_counter)
        errorPollenCounter = findViewById(R.id.error_pollen_counter)
        Log.d(TAG, "bindViewsAndSetupNavigation: Toutes les vues ont été liées.")

        buttonDecks.setOnClickListener {
            startActivity(Intent(this, DeckViewerActivity::class.java))
        }
        buttonPollen.setOnClickListener {
            startActivity(Intent(this, ForgeLiveActivity::class.java))
        }
        buttonForge.setOnClickListener {
            startActivity(Intent(this, ForgeWorkshopActivity::class.java))
        }
        buttonClash.setOnClickListener {
            startActivity(ClashActivity.newIntent(this))
        }
        buttonTools.setOnClickListener {
            ToolsDialogFragment.newInstance().show(supportFragmentManager, ToolsDialogFragment.TAG)
        }
    }

    private fun updateForgeCounters() {
        lifecycleScope.launch {
            val pollenCounts = pollenGrainDao.countByStatus()
            val allCards = cardDao.getAll()
            val totalCardsCount = allCards.size

            withContext(Dispatchers.Main) {
                val forgingCount = (pollenCounts[PollenStatus.IDENTIFYING] ?: 0) +
                        (pollenCounts[PollenStatus.PENDING_DESCRIPTION] ?: 0) +
                        (pollenCounts[PollenStatus.PENDING_STATS] ?: 0) +
                        (pollenCounts[PollenStatus.PENDING_QUIZ] ?: 0) +
                        (pollenCounts[PollenStatus.PENDING_TRANSLATION] ?: 0)

                updateCounterView(rawPollenCounter, R.string.counter_label_raw, pollenCounts[PollenStatus.RAW])
                updateCounterView(inForgePollenCounter, R.string.counter_label_forging, forgingCount)
                updateCounterView(totalHoneyCounter, R.string.counter_label_honey, totalCardsCount)
                updateCounterView(errorPollenCounter, R.string.counter_label_error, pollenCounts[PollenStatus.ERROR])
            }
        }
    }

    private fun updateCounterView(textView: TextView, labelResId: Int, count: Int?) {
        val countValue = count ?: 0
        textView.text = getString(labelResId, countValue)
        textView.visibility = View.VISIBLE
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
            mediaPlayer.start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBellyScratchListener() {
        Log.d(TAG, "setupBellyScratchListener: Le listener de grattage est maintenant actif.")
        backgroundVideoView.setOnTouchListener { view, event ->
            if (isReactionPlaying) {
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
                    if (bellyHotspot.contains(x, y)) {
                        scratchJob = lifecycleScope.launch {
                            delay(1000)
                            triggerBellyScratchEvent()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!bellyHotspot.contains(x, y)) {
                        scratchJob?.cancel()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scratchJob?.cancel()
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
            Toast.makeText(this, R.string.secret_interaction_unlocked, Toast.LENGTH_SHORT).show()

            playVideo(R.raw.kikko_deck, isLooping = false)

            backgroundVideoView.setOnCompletionListener {
                Log.i(TAG, "onCompletion: La vidéo de réaction est terminée. Retour à la vidéo principale.")
                isReactionPlaying = false
                playVideo(R.raw.kikko_main, isLooping = true)
                backgroundVideoView.setOnCompletionListener(null)
            }
        }
    }
    /*
        private fun observeVoskService() {

            SttVoskService.voskResult.observe(this) { result ->
                val dialog = supportFragmentManager.findFragmentByTag(ToolsDialogFragment.TAG) as? ToolsDialogFragment
                dialog?.updateVoskStatus(result)
            }


        }

        override fun onImportVoskModelRequested() {
            importVoskModelLauncher.launch("application/zip")
        }

        override fun onTestVoskModelRequested(modelDir: File) {
            this.modelToTest = modelDir
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                testVoskModel(modelDir)
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        override fun onStopVoskListeningRequested() {
            SttVoskService.stopListening()
        }

        private fun testVoskModel(modelDir: File) {
            val dialog = supportFragmentManager.findFragmentByTag(ToolsDialogFragment.TAG) as? ToolsDialogFragment
            dialog?.updateVoskStatus(VoskResult(VoskStatus.LOADING, getString(R.string.vosk_loading_model, modelDir.name)))

            val dummyModel = Model(name = modelDir.name, downloadFileName = "", url = "", sizeInBytes = 0)
            SttVoskService.loadModel(dummyModel, File(filesDir, "vosk-models")) { success ->
                if (success) {
                    SttVoskService.startListening()
                } else {
                    dialog?.updateVoskStatus(
                        VoskResult(
                            VoskStatus.ERROR,
                            getString(R.string.vosk_loading_model_failed)
                        )
                    )
                }
            }
        }

        override fun onDeleteVoskModelRequested(modelDir: File) {
            if (modelDir.exists() && modelDir.deleteRecursively()) {
                Toast.makeText(this, getString(R.string.vosk_model_deleted_success, modelDir.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.vosk_model_deleted_failure, modelDir.name), Toast.LENGTH_SHORT).show()
            }
        }
    */
    private suspend fun copyModelToAppStorage(sourceUri: Uri, directory: String): String? = withContext(Dispatchers.IO) {
        val tag = "ModelStorage"
        try {
            val fileName = contentResolver.query(sourceUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "imported_file_${System.currentTimeMillis()}"
            val destDir = File(filesDir, directory)
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, fileName)
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(tag, "Échec de la copie du fichier", e)
            null
        }
    }

    override fun onExportSagaRequested() {
        lifecycleScope.launch {
            val sagaUri = SagaArchiver.exportSaga(this@StartActivity)
            if (sagaUri != null) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, sagaUri)
                    type = "application/zip"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_saga_title)))
            } else {
                Toast.makeText(this@StartActivity, R.string.export_saga_failure, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onImportSagaRequested() {
        importSagaLauncher.launch("*/*")
    }

    override fun onDeleteModelRequested(modelFile: File) {
        if (modelFile.exists() && modelFile.delete()) {
            Toast.makeText(this, getString(R.string.model_deleted_success, modelFile.name), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.model_deleted_failure, modelFile.name), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAddModelRequested() {
        addModelLauncher.launch("*/*")
    }

    override fun onManagePromptsRequested() {
        startActivity(Intent(this, PromptEditorActivity::class.java))
    }

    private suspend fun handleVoskModelImport(uri: Uri) = withContext(Dispatchers.IO) {
        val modelName = getVoskModelNameFromZip(uri)
        if (modelName == null) {
            withContext(Dispatchers.Main) { Toast.makeText(this@StartActivity, R.string.vosk_model_import_invalid_zip, Toast.LENGTH_LONG).show() }
            return@withContext
        }
        val modelDir = File(File(filesDir, "vosk-models"), modelName)
        try {
            if (modelDir.exists()) modelDir.deleteRecursively()
            modelDir.mkdirs()
            contentResolver.openInputStream(uri)?.use { inputStream ->
                unzip(inputStream, modelDir, modelName)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@StartActivity, getString(R.string.vosk_model_import_success, modelName), Toast.LENGTH_LONG).show()
                }
            } ?: throw Exception("Impossible d'ouvrir le flux de données pour l'URI.")
        } catch (e: Exception) {
            Log.e(TAG, "Échec de l'importation du modèle Vosk", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@StartActivity, getString(R.string.vosk_model_import_failure, e.message), Toast.LENGTH_LONG).show()
            }
            if (modelDir.exists()) modelDir.deleteRecursively()
        }
    }

    @Throws(Exception::class)
    private fun getVoskModelNameFromZip(uri: Uri): String? {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                return zis.nextEntry?.name?.substringBefore('/')
            }
        }
        return null
    }

    @Throws(Exception::class)
    private fun unzip(inputStream: InputStream, destination: File, rootFolder: String) {
        ZipInputStream(inputStream).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name.startsWith("$rootFolder/")) {
                    val newName = zipEntry.name.substringAfter("$rootFolder/")
                    if (newName.isNotEmpty()) {
                        val newFile = File(destination, newName)
                        if (!newFile.canonicalPath.startsWith(destination.canonicalPath + File.separator)) {
                            throw SecurityException("Zip Slip Attack détectée : ${zipEntry.name}")
                        }
                        if (zipEntry.isDirectory) {
                            if (!newFile.isDirectory && !newFile.mkdirs()) {
                                throw java.io.IOException("Échec de la création du répertoire ${newFile}")
                            }
                        } else {
                            val parent = newFile.parentFile
                            if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                                throw java.io.IOException("Échec de la création du répertoire ${parent}")
                            }
                            FileOutputStream(newFile).use { fos ->
                                val buffer = ByteArray(1024)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                }
                zipEntry = zis.nextEntry
            }
        }
    }

    override fun onNukeDatabaseRequested() {
        lifecycleScope.launch {
            Log.w("BOURDON_NUKE", "Demande de purge de la base de données reçue.")
            withContext(Dispatchers.IO) {
                pollenGrainDao.nuke()
                cardDao.nuke()
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@StartActivity, R.string.hive_memory_cleared, Toast.LENGTH_LONG).show()
                updateForgeCounters()
            }
        }
    }
}