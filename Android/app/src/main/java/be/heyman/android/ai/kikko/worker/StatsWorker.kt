package be.heyman.android.ai.kikko.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
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
import be.heyman.android.ai.kikko.model.CardStats
import be.heyman.android.ai.kikko.model.ModelConfiguration
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.model.SwarmAnalysisResult
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

class StatsWorker(val appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val pollenGrainDao: PollenGrainDao = (appContext.applicationContext as KikkoApplication).pollenGrainDao
    private val cardDao: CardDao = (appContext.applicationContext as KikkoApplication).cardDao
    private val llmHelper: ForgeLlmHelper = (appContext.applicationContext as KikkoApplication).forgeLlmHelper

    private val gson = Gson()

    private data class FoodStatsResult(
        val stats: Map<String, String>?,
        val ingredients: List<String>?,
        val allergens: List<String>?
    )
    private data class BiologicalNamesResult(
        val scientificName: String?,
        val vernacularName: String?
    )
    private data class StatsResponse(val stats: Map<String, String>?)


    companion object {
        const val TAG = "StatsWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Le StatsWorker démarre sa tâche.")

        val prefs = appContext.getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedQueenName = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, null)
        val selectedAccelerator = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")!!

        if (selectedQueenName.isNullOrBlank()) {
            Log.e(TAG, "ÉCHEC : Aucune Reine IA n'a été sélectionnée pour la Forge.")
            return Result.failure()
        }

        val grainToProcess = pollenGrainDao.getByStatus(PollenStatus.PENDING_STATS).firstOrNull()
        if (grainToProcess == null) {
            Log.d(TAG, "Aucun pollen en attente de stats trouvé. Tâche terminée.")
            return Result.success()
        }

        Log.i(TAG, "PollenGrain récupéré pour traitement : ${gson.toJson(grainToProcess)}")

        val cardId = grainToProcess.forgedCardId
        if (cardId == null) {
            Log.e(TAG, "Erreur critique: Le grain ${grainToProcess.id} est PENDING_STATS mais n'a pas de forgedCardId.")
            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.ERROR)
            return Result.failure()
        }

        val cardToUpdate = cardDao.getCardById(cardId)
        if (cardToUpdate == null || cardToUpdate.description.isNullOrBlank()) {
            Log.e(TAG, "Erreur critique: Impossible de trouver la carte ID $cardId ou sa description est vide.")
            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.ERROR)
            return Result.failure()
        }

        setForeground(createForegroundInfo("La Reine '${selectedQueenName}' forge les statistiques..."))

        try {
            Log.i(TAG, "Extraction des stats pour la carte ID: $cardId (Pollen ID: ${grainToProcess.id})")

            val modelFile = File(appContext.filesDir, "imported_models").resolve(selectedQueenName)
            if (!modelFile.exists()) throw IOException("Le fichier de la Reine IA '${selectedQueenName}' est introuvable.")
            val queenModel = Model(name = selectedQueenName, url = modelFile.absolutePath, downloadFileName = "", sizeInBytes = 0, llmSupportImage = false)

            val initError = llmHelper.initialize(queenModel, selectedAccelerator, isMultimodal = false)
            if (initError != null) throw RuntimeException("Échec de l'initialisation (Stats): $initError")

            val queenModelConfig = ModelConfiguration(selectedQueenName, selectedAccelerator, 0.2f, 40)
            llmHelper.resetSession(queenModel, isMultimodal = false, temperature = queenModelConfig.temperature, topK = queenModelConfig.topK)

            val swarmResult = gson.fromJson(grainToProcess.swarmAnalysisReportJson, SwarmAnalysisResult::class.java)
            val aggregatedOcr = swarmResult?.reports?.mapNotNull { it.ocrResults?.fullText }?.joinToString("\n")?.trim() ?: ""

            val prompt = PromptGenerator.generateStatsExtractionPrompt(cardToUpdate.specificName, cardToUpdate.deckName, aggregatedOcr, cardToUpdate.description)
            if (prompt.isBlank()) {
                Log.w(TAG, "Aucun prompt généré pour le deck ${cardToUpdate.deckName}. Pas de stats à extraire.")
                pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.PENDING_QUIZ)
                return Result.success()
            }

            val fullResponse = inferenceWithCoroutine(prompt, emptyList())

            if (cardToUpdate.deckName == "Food") {
                val foodResult = parseIntelligentJson<FoodStatsResult>(fullResponse)
                cardDao.updateStats(cardId, foodResult?.stats?.let { CardStats("Statistics", it) }, foodResult?.allergens, foodResult?.ingredients)
            } else {
                val statsResult = parseIntelligentJson<StatsResponse>(fullResponse)
                cardDao.updateStats(cardId, statsResult?.stats?.let { CardStats("Statistics", it) }, null, null)

                if (cardToUpdate.deckName == "Plant" || cardToUpdate.deckName == "Insect" || cardToUpdate.deckName == "Bird") {
                    val biologicalNamesResult = parseIntelligentJson<BiologicalNamesResult>(fullResponse)
                    cardDao.updateScientificAndVernacularNames(cardId, biologicalNamesResult?.scientificName, biologicalNamesResult?.vernacularName)
                }
            }

            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.PENDING_QUIZ)
            Log.i(TAG, "Le grain ${grainToProcess.id} est maintenant en attente de quiz. Tâche réussie.")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Échec de l'extraction de stats pour la carte ID: $cardId", e)
            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.ERROR)
            return Result.failure()
        } finally {
            llmHelper.cleanUp()
            Log.d(TAG, "Nettoyage des ressources de la Reine IA.")
        }
    }

    private suspend fun inferenceWithCoroutine(prompt: String, images: List<android.graphics.Bitmap>): String {
        return suspendCancellableCoroutine { continuation ->
            val responseBuilder = StringBuilder()
            llmHelper.runInference(prompt, images) { partialResult, done ->
                responseBuilder.append(partialResult)
                if (done && continuation.isActive) {
                    continuation.resume(responseBuilder.toString())
                }
            }
        }
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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("La Forge de la Ruche prépare les statistiques...")
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val channelId = IdentificationWorker.NOTIFICATION_CHANNEL_ID
        val channelName = "Forge Kikko en Arrière-Plan"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("La Ruche travaille...")
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(IdentificationWorker.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(IdentificationWorker.NOTIFICATION_ID, notification)
        }
    }
}