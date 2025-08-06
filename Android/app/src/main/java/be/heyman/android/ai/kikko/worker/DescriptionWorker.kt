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
import be.heyman.android.ai.kikko.model.ModelConfiguration
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class DescriptionWorker(val appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val pollenGrainDao: PollenGrainDao = (appContext.applicationContext as KikkoApplication).pollenGrainDao
    private val cardDao: CardDao = (appContext.applicationContext as KikkoApplication).cardDao
    private val llmHelper: ForgeLlmHelper = (appContext.applicationContext as KikkoApplication).forgeLlmHelper
    private val gson = Gson()

    companion object {
        const val TAG = "DescriptionWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Le DescriptionWorker démarre sa tâche.")

        val prefs = appContext.getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedQueenName = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, null)
        val selectedAccelerator = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")!!


        if (selectedQueenName.isNullOrBlank()) {
            Log.e(TAG, "ÉCHEC : Aucune Reine IA n'a été sélectionnée pour la Forge.")
            return Result.failure()
        }

        val grainToProcess = pollenGrainDao.getByStatus(PollenStatus.PENDING_DESCRIPTION).firstOrNull()

        if (grainToProcess == null) {
            Log.d(TAG, "Aucun pollen en attente de description trouvé. Tâche terminée.")
            return Result.success()
        }

        Log.i(TAG, "PollenGrain récupéré pour traitement : ${gson.toJson(grainToProcess)}")

        val cardId = grainToProcess.forgedCardId
        if (cardId == null) {
            Log.e(TAG, "Erreur critique: Le grain ${grainToProcess.id} est en attente de description mais n'a pas de forgedCardId.")
            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.ERROR)
            return Result.failure()
        }

        val cardToUpdate = cardDao.getCardById(cardId)
        if (cardToUpdate == null) {
            Log.e(TAG, "Erreur critique: Impossible de trouver la KnowledgeCard avec l'ID $cardId pour le grain ${grainToProcess.id}.")
            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.ERROR)
            return Result.failure()
        }

        setForeground(createForegroundInfo("La Reine '${selectedQueenName}' forge la description..."))

        try {
            Log.i(TAG, "Génération de la description pour la carte ID: $cardId (Pollen ID: ${grainToProcess.id})")

            val modelFile = File(File(appContext.filesDir, "imported_models"), selectedQueenName)
            if (!modelFile.exists()) throw IOException("Le fichier de la Reine IA '${selectedQueenName}' est introuvable.")
            val queenModel = Model(name = selectedQueenName, url = modelFile.absolutePath, downloadFileName = "", sizeInBytes = 0, llmSupportImage = false)

            val initError = llmHelper.initialize(queenModel, selectedAccelerator, isMultimodal = false)
            if (initError != null) throw RuntimeException("Échec de l'initialisation de la Reine IA : $initError")

            val queenModelConfig = ModelConfiguration(selectedQueenName, selectedAccelerator, 0.2f, 40)
            llmHelper.resetSession(queenModel, isMultimodal = false, temperature = queenModelConfig.temperature, topK = queenModelConfig.topK)

            val prompt = PromptGenerator.generateNarrationDescriptionPrompt(cardToUpdate.specificName, cardToUpdate.deckName, Locale.getDefault())

            val fullResponse = llmHelper.inferenceWithCoroutineAndConfig(prompt, emptyList(), queenModelConfig)

            cardDao.updateDescription(cardId, fullResponse.trim())
            Log.i(TAG, "Description ajoutée à la carte ID: $cardId")

            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.PENDING_STATS)
            Log.i(TAG, "Le grain ${grainToProcess.id} est maintenant en attente de statistiques. Tâche réussie.")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Échec de la génération de description pour la carte ID: $cardId", e)
            pollenGrainDao.updateStatus(grainToProcess.id, PollenStatus.ERROR)
            return Result.failure()
        } finally {
            llmHelper.cleanUp()
            Log.d(TAG, "Nettoyage des ressources de la Reine IA.")
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("La Forge de la Ruche prépare la description...")
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