// --- START OF FILE app/src/main/java/be/heyman/android/ai/kikko/worker/IdentificationWorker.kt ---

package be.heyman.android.ai.kikko.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import be.heyman.android.ai.kikko.KikkoApplication
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.ToolsDialogFragment
import be.heyman.android.ai.kikko.data.Model
import be.heyman.android.ai.kikko.forge.ForgeLlmHelper
import be.heyman.android.ai.kikko.forge.PromptGenerator
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.ModelConfiguration
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.model.Reasoning
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class IdentificationWorker(val appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val pollenGrainDao: PollenGrainDao = (appContext.applicationContext as KikkoApplication).pollenGrainDao
    private val cardDao: CardDao = (appContext.applicationContext as KikkoApplication).cardDao
    private val llmHelper: ForgeLlmHelper = (appContext.applicationContext as KikkoApplication).forgeLlmHelper

    private val gson = Gson()

    private data class IdentificationResult(
        val reasoning: Reasoning,
        val deckName: String,
        val specificName: String,
        val confidence: Float
    )

    companion object {
        const val TAG = "ForgeWorker_Identification"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "KikkoForgeChannel"
        private const val THUMBNAIL_SIZE = 128
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "WORKER DÉMARRÉ. Tentative de forge d'un pollen RAW.")
        setForeground(createForegroundInfo(appContext.getString(R.string.notification_forge_awakens)))

        val prefs = appContext.getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedQueenName = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, null)
        val selectedAccelerator = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")!!

        if (selectedQueenName.isNullOrBlank()) {
            Log.e(TAG, "ÉCHEC : Aucune Reine IA n'a été sélectionnée dans les Outils.")
            return Result.failure()
        }

        val grainToProcess = pollenGrainDao.getByStatus(PollenStatus.RAW).firstOrNull()
        if (grainToProcess == null) {
            Log.i(TAG, "Aucun pollen RAW trouvé. Le worker va se rendormir.")
            return Result.success()
        }

        Log.i(TAG, "PollenGrain récupéré pour traitement : ${gson.toJson(grainToProcess)}")

        val thumbnail = withContext(Dispatchers.IO) {
            grainToProcess.pollenImagePaths.firstOrNull()?.let {
                createThumbnail(BitmapFactory.decodeFile(it), THUMBNAIL_SIZE)
            }
        }

        try {
            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.IDENTIFYING)
            setForeground(createForegroundInfo(appContext.getString(R.string.notification_queen_identifying, selectedQueenName), thumbnail))

            val modelFile = File(appContext.filesDir, "imported_models").resolve(selectedQueenName)
            if (!modelFile.exists()) throw IOException("Le fichier de la Reine IA '${selectedQueenName}' est introuvable.")

            val queenModel = Model(
                name = selectedQueenName,
                url = modelFile.absolutePath,
                downloadFileName = "",
                sizeInBytes = 0,
                llmSupportImage = selectedQueenName.contains("gemma-3n", ignoreCase = true)
            )

            val initError = llmHelper.initialize(queenModel, selectedAccelerator, isMultimodal = true)
            if (initError != null) throw RuntimeException("Échec de l'initialisation de la Reine IA : $initError")

            val queenModelConfig = ModelConfiguration(selectedQueenName, selectedAccelerator, 0.2f, 40)
            llmHelper.resetSession(queenModel, isMultimodal = true, temperature = queenModelConfig.temperature, topK = queenModelConfig.topK)

            val swarmReportJson = grainToProcess.swarmAnalysisReportJson
            if (swarmReportJson.isNullOrBlank()) {
                throw IllegalStateException("Le rapport d'analyse des Abeilles est manquant pour le pollen ${grainToProcess.id}")
            }

            val prompt = PromptGenerator.generateIdentificationPrompt(swarmReportJson)

            val pollenBitmaps = withContext(Dispatchers.IO) {
                grainToProcess.pollenImagePaths.mapNotNull { path ->
                    try {
                        BitmapFactory.decodeFile(path)
                    } catch (e: Exception) {
                        Log.e(TAG, "Impossible de charger l'image du pollen: $path", e)
                        null
                    }
                }
            }
            Log.d(TAG, "${pollenBitmaps.size} images de pollen chargées for l'inférence multimodale.")

            val fullResponse = suspendCancellableCoroutine { continuation ->
                val responseBuilder = StringBuilder()
                var charCount = 0
                var lastLogCharCount = 0

                llmHelper.runInference(prompt, pollenBitmaps) { partialResult, done ->
                    responseBuilder.append(partialResult)
                    charCount += partialResult.length

                    val progressText = appContext.getString(R.string.notification_queen_identifying_streaming, charCount)
                    setForegroundAsync(createForegroundInfo(progressText, thumbnail))

                    if (charCount - lastLogCharCount >= 50) {
                        Log.d(TAG, "STREAMING [${charCount} chars]: ${responseBuilder.toString().replace("\n", " ")}")
                        lastLogCharCount = charCount
                    }

                    if (done && continuation.isActive) {
                        continuation.resume(responseBuilder.toString())
                    }
                }
            }


            val finalResult = parseIntelligentJson<IdentificationResult>(fullResponse)
            if (finalResult == null || finalResult.specificName.isBlank() || finalResult.deckName.isBlank()) {
                throw IOException("Analyse JSON échouée ou incomplète. Réponse brute: $fullResponse")
            }

            // BOURDON'S ROBUSTNESS FIX: Normalisation du deckName reçu de l'IA.
            val normalizedDeckName = finalResult.deckName
                .trim()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                .removeSuffix("s") // Gère les pluriels simples comme "Plants"

            val newCard = KnowledgeCard(
                specificName = finalResult.specificName,
                deckName = normalizedDeckName, // Utilisation du nom normalisé
                imagePath = grainToProcess.pollenImagePaths.firstOrNull(),
                confidence = finalResult.confidence,
                reasoning = finalResult.reasoning,
                description = null,
                stats = null,
                quiz = null,
                translations = null,
                scientificName = null,
                vernacularName = null,
                allergens = null,
                ingredients = null
            )

            val newCardId = cardDao.insert(newCard)
            Log.i(TAG, "Nouvelle KnowledgeCard (préliminaire) créée avec l'ID: $newCardId. Deck normalisé: '$normalizedDeckName'")

            // BOURDON'S REFORGE V6.0: Le worker passe le grain à l'état de validation manuelle.
            pollenGrainDao.updateForgingResult(grainToProcess.id, PollenStatus.AWAITING_VALIDATION, newCardId)
            Log.i(TAG, "WORKER TERMINÉ AVEC SUCCÈS. Le grain ${grainToProcess.id} est maintenant en attente de validation.")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "WORKER EN ÉCHEC pour le grain ID: ${grainToProcess.id}", e)
            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.ERROR)
            return Result.failure()
        } finally {
            llmHelper.cleanUp()
            Log.d(TAG, "Nettoyage des ressources de la Reine IA.")
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(appContext.getString(R.string.notification_forge_preparing))
    }

    private inline fun <reified T> parseIntelligentJson(rawString: String): T? {
        return try {
            val jsonSubstring = rawString.substringAfter("```json", rawString).substringBeforeLast("```").trim()
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(jsonSubstring, type)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Erreur de parsing JSON pour le type ${T::class.java.simpleName} (Syntaxe): '$rawString'", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur inattendue lors du parsing intelligent pour le type ${T::class.java.simpleName}: '$rawString'", e)
            null
        }
    }

    private fun createThumbnail(source: Bitmap?, size: Int): Bitmap? {
        if (source == null) return null
        val scaledWidth = if (source.width > source.height) size * source.width / source.height else size
        val scaledHeight = if (source.height >= source.width) size * source.height / source.width else size
        return Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, false)
    }

    private fun createForegroundInfo(progress: String, thumbnail: Bitmap? = null): ForegroundInfo {
        val channelId = NOTIFICATION_CHANNEL_ID
        val channelName = appContext.getString(R.string.notification_channel_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(appContext.getString(R.string.notification_title))
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(100, 0, true) // Indeterminate progress bar

        if (thumbnail != null) {
            notificationBuilder.setLargeIcon(thumbnail)
        }

        val notification = notificationBuilder.build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
// --- END OF FILE app/src/main/java/be/heyman/android/ai/kikko/worker/IdentificationWorker.kt ---