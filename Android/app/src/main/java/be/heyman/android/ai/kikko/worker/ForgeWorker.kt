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
import be.heyman.android.ai.kikko.model.CardStats
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.ModelConfiguration
import be.heyman.android.ai.kikko.model.PollenGrain
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.model.QuizQuestion
import be.heyman.android.ai.kikko.model.Reasoning
import be.heyman.android.ai.kikko.model.SwarmAnalysisResult
import be.heyman.android.ai.kikko.model.TranslatedContent
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

class ForgeWorker(val appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val pollenGrainDao: PollenGrainDao = (appContext.applicationContext as KikkoApplication).pollenGrainDao
    private val cardDao: CardDao = (appContext.applicationContext as KikkoApplication).cardDao
    private val llmHelper: ForgeLlmHelper = (appContext.applicationContext as KikkoApplication).forgeLlmHelper
    private val gson = Gson()
    private val targetLanguages = mapOf("fr" to "French", "ja" to "Japanese")

    private data class IdentificationResult(
        val reasoning: Reasoning,
        val deckName: String,
        val specificName: String,
        val confidence: Float
    )
    private data class StatsResponse(val stats: Map<String, String>?)
    private data class FoodStatsResult(
        val stats: Map<String, String>?,
        val ingredients: List<String>?,
        val allergens: List<String>?
    )
    private data class BiologicalNamesResult(
        val scientificName: String?,
        val vernacularName: String?
    )

    companion object {
        const val TAG = "ForgeWorker"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "KikkoForgeChannel"
        private const val THUMBNAIL_SIZE = 256

        val processingOrder = listOf(
            PollenStatus.RAW,
            PollenStatus.PENDING_DESCRIPTION,
            PollenStatus.PENDING_STATS,
            PollenStatus.PENDING_QUIZ,
            PollenStatus.PENDING_TRANSLATION
        )
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "WORKER MONOLITHIQUE DÉMARRÉ.")
        setForeground(createForegroundInfo("La Forge s'éveille..."))

        try {
            for (statusToProcess in processingOrder) {
                while (true) {
                    val grain = pollenGrainDao.getByStatus(statusToProcess).firstOrNull() ?: break
                    Log.i(TAG, "Traitement du grain ${grain.id} au statut $statusToProcess")

                    val thumbnail = withContext(Dispatchers.IO) { grain.pollenImagePaths.firstOrNull()?.let { createThumbnail(BitmapFactory.decodeFile(it), THUMBNAIL_SIZE) } }

                    try {
                        when (grain.status) {
                            PollenStatus.RAW -> {
                                setForeground(createForegroundInfo("Étape 1/5: Identification du Pollen...", thumbnail))
                                val card = runIdentification(grain)
                                pollenGrainDao.updateForgingResult(grain.id, PollenStatus.PENDING_DESCRIPTION, card.id)
                            }
                            PollenStatus.PENDING_DESCRIPTION -> {
                                setForeground(createForegroundInfo("Étape 2/5: Génération de la Description...", thumbnail))
                                runDescription(grain.forgedCardId!!)
                                pollenGrainDao.updateStatus(grain.id, PollenStatus.PENDING_STATS)
                            }
                            PollenStatus.PENDING_STATS -> {
                                setForeground(createForegroundInfo("Étape 3/5: Extraction des Statistiques...", thumbnail))
                                runStats(grain.forgedCardId!!, grain.swarmAnalysisReportJson)
                                pollenGrainDao.updateStatus(grain.id, PollenStatus.PENDING_QUIZ)
                            }
                            PollenStatus.PENDING_QUIZ -> {
                                setForeground(createForegroundInfo("Étape 4/5: Création du Quiz...", thumbnail))
                                runQuiz(grain.forgedCardId!!)
                                pollenGrainDao.updateStatus(grain.id, PollenStatus.PENDING_TRANSLATION)
                            }
                            PollenStatus.PENDING_TRANSLATION -> {
                                setForeground(createForegroundInfo("Étape 5/5: Traduction du Miel...", thumbnail))
                                runTranslation(grain.forgedCardId!!)
                                pollenGrainDao.updateStatus(grain.id, PollenStatus.FORGED)
                                Log.i(TAG, "FORGE TERMINÉE AVEC SUCCÈS pour le grain ${grain.id}.")
                            }
                            else -> Log.w(TAG, "Statut inattendu trouvé: ${grain.status}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "ÉCHEC d'une étape pour le grain ${grain.id}", e)
                        pollenGrainDao.updateStatus(grain.id, PollenStatus.ERROR)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Une erreur générale a interrompu le worker", e)
            return Result.failure()
        }

        Log.i(TAG, "Toutes les tâches disponibles sont terminées. Le worker va se rendormir.")
        return Result.success()
    }

    private suspend fun runIdentification(grain: PollenGrain): KnowledgeCard {
        val config = getQueenModelConfig()
        try {
            val modelFile = File(appContext.filesDir, "imported_models").resolve(config.modelName)
            if (!modelFile.exists()) throw IOException("Fichier de la Reine IA '${config.modelName}' introuvable.")
            val queenModel = Model(name = config.modelName, url = modelFile.absolutePath, downloadFileName = "", sizeInBytes = 0, llmSupportImage = config.modelName.contains("gemma-3n", ignoreCase = true))

            val initError = llmHelper.initialize(queenModel, config.accelerator, isMultimodal = true)
            if (initError != null) throw RuntimeException("Échec de l'initialisation (Identification): $initError")

            val swarmReportJson = grain.swarmAnalysisReportJson ?: throw IOException("Rapport d'essaim manquant.")
            val prompt = PromptGenerator.generateIdentificationPrompt(swarmReportJson)
            val bitmaps = withContext(Dispatchers.IO) { grain.pollenImagePaths.mapNotNull { BitmapFactory.decodeFile(it) } }

            val fullResponse = llmHelper.inferenceWithCoroutineAndConfig(prompt, bitmaps, config)
            val result = parseIntelligentJson<IdentificationResult>(fullResponse) ?: throw IOException("Réponse d'identification invalide: $fullResponse")

            val newCard = KnowledgeCard(
                specificName = result.specificName, deckName = result.deckName,
                imagePath = grain.pollenImagePaths.firstOrNull(), confidence = result.confidence,
                reasoning = result.reasoning, description = null, stats = null, allergens = null, ingredients = null, quiz = null, translations = null,
                scientificName = null, vernacularName = null
            )
            val newId = cardDao.insert(newCard)
            return newCard.copy(id = newId)
        } finally {
            llmHelper.cleanUp()
        }
    }

    private suspend fun runDescription(cardId: Long) {
        val config = getQueenModelConfig()
        try {
            val card = cardDao.getCardById(cardId) ?: throw IOException("Carte $cardId introuvable pour la description.")
            val modelFile = File(appContext.filesDir, "imported_models").resolve(config.modelName)
            if (!modelFile.exists()) throw IOException("Fichier de la Reine IA '${config.modelName}' introuvable.")
            val queenModel = Model(name = config.modelName, url = modelFile.absolutePath, downloadFileName = "", sizeInBytes = 0, llmSupportImage = false)

            val initError = llmHelper.initialize(queenModel, config.accelerator, isMultimodal = false)
            if (initError != null) throw RuntimeException("Échec de l'initialisation (Description): $initError")

            val prompt = PromptGenerator.generateNarrationDescriptionPrompt(card.specificName, card.deckName, Locale.getDefault())
            val fullResponse = llmHelper.inferenceWithCoroutineAndConfig(prompt, emptyList(), config)
            cardDao.updateDescription(cardId, fullResponse.trim())
        } finally {
            llmHelper.cleanUp()
        }
    }

    private suspend fun runStats(cardId: Long, swarmReportJson: String?) {
        val config = getQueenModelConfig()
        try {
            val card = cardDao.getCardById(cardId) ?: throw IOException("Carte $cardId introuvable pour les stats.")
            val modelFile = File(appContext.filesDir, "imported_models").resolve(config.modelName)
            if (!modelFile.exists()) throw IOException("Fichier de la Reine IA '${config.modelName}' introuvable.")
            val queenModel = Model(name = config.modelName, url = modelFile.absolutePath, downloadFileName = "", sizeInBytes = 0, llmSupportImage = false)

            val initError = llmHelper.initialize(queenModel, config.accelerator, isMultimodal = false)
            if (initError != null) throw RuntimeException("Échec de l'initialisation (Stats): $initError")

            val ocr = gson.fromJson(swarmReportJson, SwarmAnalysisResult::class.java)?.reports?.joinToString("\n") { it.ocrText }?.trim() ?: ""
            val prompt = PromptGenerator.generateStatsExtractionPrompt(card.specificName, card.deckName, ocr, card.description ?: "")
            if (prompt.isBlank()) {
                Log.w(TAG, "Aucun prompt généré pour le deck ${card.deckName}. Pas de stats à extraire.")
                return
            }

            val fullResponse = llmHelper.inferenceWithCoroutineAndConfig(prompt, emptyList(), config)

            if (card.deckName == "Food") {
                val foodResult = parseIntelligentJson<FoodStatsResult>(fullResponse)
                cardDao.updateStats(cardId, foodResult?.stats?.let { CardStats("Statistics", it) }, foodResult?.allergens, foodResult?.ingredients)
            } else {
                val statsResult = parseIntelligentJson<StatsResponse>(fullResponse)
                cardDao.updateStats(cardId, statsResult?.stats?.let { CardStats("Statistics", it) }, null, null)

                if (card.deckName == "Plant" || card.deckName == "Insect" || card.deckName == "Bird") {
                    val biologicalNamesResult = parseIntelligentJson<BiologicalNamesResult>(fullResponse)
                    cardDao.updateScientificAndVernacularNames(cardId, biologicalNamesResult?.scientificName, biologicalNamesResult?.vernacularName)
                }
            }
        } finally {
            llmHelper.cleanUp()
        }
    }

    private suspend fun runQuiz(cardId: Long) {
        val config = getQueenModelConfig()
        try {
            val card = cardDao.getCardById(cardId) ?: throw IOException("Carte $cardId introuvable pour le quiz.")
            val modelFile = File(appContext.filesDir, "imported_models").resolve(config.modelName)
            if (!modelFile.exists()) throw IOException("Fichier de la Reine IA '${config.modelName}' introuvable.")
            val queenModel = Model(name = config.modelName, url = modelFile.absolutePath, downloadFileName = "", sizeInBytes = 0, llmSupportImage = false)

            val initError = llmHelper.initialize(queenModel, config.accelerator, isMultimodal = false)
            if (initError != null) throw RuntimeException("Échec de l'initialisation (Quiz): $initError")

            val prompt = PromptGenerator.generateQuizPrompt(card.specificName, card.description ?: "", card.stats, Locale.getDefault())
            val fullResponse = llmHelper.inferenceWithCoroutineAndConfig(prompt, emptyList(), config)
            val quiz = parseIntelligentJson<List<QuizQuestion>>(fullResponse) ?: throw IOException("La Reine IA a fourni un quiz vide ou dans un format invalide. Réponse brute: $fullResponse")
            cardDao.updateQuiz(cardId, quiz)
        } finally {
            llmHelper.cleanUp()
        }
    }

    private suspend fun runTranslation(cardId: Long) {
        val config = getQueenModelConfig()
        try {
            val card = cardDao.getCardById(cardId) ?: throw IOException("Carte $cardId introuvable pour la traduction.")
            val modelFile = File(appContext.filesDir, "imported_models").resolve(config.modelName)
            if (!modelFile.exists()) throw IOException("Fichier de la Reine IA '${config.modelName}' introuvable.")
            val queenModel = Model(name = config.modelName, url = modelFile.absolutePath, downloadFileName = "", sizeInBytes = 0, llmSupportImage = false)

            val initError = llmHelper.initialize(queenModel, config.accelerator, isMultimodal = false)
            if (initError != null) throw RuntimeException("Échec de l'initialisation (Traduction): $initError")
            val newTranslations = card.translations?.toMutableMap() ?: mutableMapOf()

            for ((langCode, langName) in targetLanguages) {
                if (newTranslations.containsKey(langCode)) continue
                Log.d(TAG, "Traduction en cours vers '$langName'...")

                val originalContent = TranslatableCardContent(card.description, card.reasoning, card.quiz)
                val originalJson = gson.toJson(originalContent)
                val prompt = PromptGenerator.generateFullCardTranslationPrompt(originalJson, langName)
                val translatedJson = llmHelper.inferenceWithCoroutineAndConfig(prompt, emptyList(), config)
                val translatedContent = parseIntelligentJson<TranslatedContent>(translatedJson)

                if(translatedContent != null) {
                    newTranslations[langCode] = translatedContent
                } else {
                    Log.w(TAG, "Échec du parsing de la traduction pour la langue $langName. Réponse brute: $translatedJson")
                }
            }
            cardDao.updateTranslations(cardId, newTranslations)
        } finally {
            llmHelper.cleanUp()
        }
    }

    private fun getQueenModelConfig(): ModelConfiguration {
        val prefs = appContext.getSharedPreferences(ToolsDialogFragment.PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN, null) ?: throw IllegalStateException("Aucune Reine IA sélectionnée.")
        val accelerator = prefs.getString(ToolsDialogFragment.KEY_SELECTED_FORGE_QUEEN_ACCELERATOR, "GPU")!!
        val temperature = prefs.getFloat("forge_temp", 0.2f)
        val topK = prefs.getInt("forge_topK", 40)
        return ModelConfiguration(name, accelerator, temperature, topK)
    }

    private inline fun <reified T> parseIntelligentJson(rawString: String): T? {
        return try {
            val jsonSubstring = rawString.substringAfter("```json", rawString).substringBeforeLast("```").trim()
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(jsonSubstring, type)
        } catch (e: Exception) {
            Log.e(TAG, "Le parsing intelligent a échoué pour le type ${T::class.java.simpleName}: '$rawString'", e)
            null
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo("La Forge de la Ruche se prépare...")

    private fun createThumbnail(source: Bitmap?, size: Int): Bitmap? {
        if (source == null) return null
        val scaledWidth = if (source.width > source.height) size * source.width / source.height else size
        val scaledHeight = if (source.height >= source.width) size * source.height / source.width else size
        return Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, false)
    }

    private fun createForegroundInfo(progress: String, thumbnail: Bitmap? = null): ForegroundInfo {
        val channelId = NOTIFICATION_CHANNEL_ID
        val channelName = "Forge Kikko en Arrière-Plan"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("La Ruche travaille...")
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setProgress(100, 0, true)

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

    private data class TranslatableCardContent(
        val description: String?,
        val reasoning: Reasoning?,
        val quiz: List<QuizQuestion>?
    )
}