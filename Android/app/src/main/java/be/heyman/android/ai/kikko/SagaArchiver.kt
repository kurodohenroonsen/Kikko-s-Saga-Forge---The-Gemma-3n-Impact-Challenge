package be.heyman.android.ai.kikko

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import be.heyman.android.ai.kikko.model.AnalysisResult
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.PollenGrain
import be.heyman.android.ai.kikko.persistence.AnalysisResultDao
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object SagaArchiver {

    private const val TAG = "SagaArchiver"
    private const val IMAGE_MAX_SIZE = 1024

    suspend fun exportSaga(context: Context): Uri? = withContext(Dispatchers.IO) {
        val cardDao = CardDao(context)
        val pollenGrainDao = PollenGrainDao(context)
        val analysisResultDao = AnalysisResultDao(context)

        val allCards = cardDao.getAll()
        if (allCards.isEmpty()) {
            Log.w(TAG, "Aucune carte à exporter.")
            return@withContext null
        }

        // BOURDON'S NOTE: On récupère maintenant TOUTES les données.
        val allPollenGrains = allCards.mapNotNull { it.id }.mapNotNull { pollenGrainDao.findByForgedCardId(it) }
        val allAnalysisResults = allPollenGrains.flatMap { analysisResultDao.getByPollenGrainIdAndProperty(it.id, "identification") } // Exemple, à étendre si nécessaire

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val exportFileName = "KikkoSaga_Export_$timestamp.kikkoSaga"
        val exportDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val exportFile = File(exportDir, exportFileName)

        try {
            ZipOutputStream(FileOutputStream(exportFile)).use { zos ->
                // Sérialisation des trois types de données
                addFileToZip("cards.json", Gson().toJson(allCards).toByteArray(), zos)
                addFileToZip("pollen_grains.json", Gson().toJson(allPollenGrains).toByteArray(), zos)
                addFileToZip("analysis_results.json", Gson().toJson(allAnalysisResults).toByteArray(), zos)

                // BOURDON'S NOTE: Redimensionnement et ajout des images
                allCards.forEach { card ->
                    card.imagePath?.let { path ->
                        val imageFile = File(path)
                        if (imageFile.exists()) {
                            resizeAndAddImageToZip(imageFile, "card_images/${imageFile.name}", zos)
                        }
                    }
                }
            }
            Log.i(TAG, "Exportation complète de la Saga réussie vers : ${exportFile.absolutePath}")
            return@withContext FileProvider.getUriForFile(context, "${context.packageName}.provider", exportFile)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'exportation complète de la saga", e)
            exportFile.delete()
            return@withContext null
        }
    }

    suspend fun importSaga(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val cardDao = CardDao(context)
        val pollenGrainDao = PollenGrainDao(context)
        val analysisResultDao = AnalysisResultDao(context)

        val existingCardNames = cardDao.getAll().map { it.specificName }.toSet()
        var importedCardCount = 0
        Log.d(TAG, "Démarrage de l'importation relationnelle de la Saga depuis : $uri")

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    // Étape 1: Extraire toutes les données en mémoire
                    val imageBytesMap = mutableMapOf<String, ByteArray>()
                    var cardsJson: String? = null
                    var pollenJson: String? = null
                    var analysisJson: String? = null

                    var entry = zis.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "cards.json" -> cardsJson = zis.bufferedReader().readText()
                            entry.name == "pollen_grains.json" -> pollenJson = zis.bufferedReader().readText()
                            entry.name == "analysis_results.json" -> analysisJson = zis.bufferedReader().readText()
                            !entry.isDirectory && entry.name.startsWith("card_images/") -> {
                                imageBytesMap[File(entry.name).name] = zis.readBytes()
                            }
                        }
                        entry = zis.nextEntry
                    }

                    if (cardsJson == null || pollenJson == null || analysisJson == null) {
                        Log.e(TAG, "L'archive est invalide ou incomplète.")
                        return@withContext -1
                    }

                    // Étape 2: Désérialiser et préparer les données
                    val gson = Gson()
                    val importedCards: List<KnowledgeCard> = gson.fromJson(cardsJson, object : TypeToken<List<KnowledgeCard>>() {}.type)
                    val importedPollen: List<PollenGrain> = gson.fromJson(pollenJson, object : TypeToken<List<PollenGrain>>() {}.type)
                    val importedAnalysis: List<AnalysisResult> = gson.fromJson(analysisJson, object : TypeToken<List<AnalysisResult>>() {}.type)

                    // BOURDON'S NOTE: Logique de greffe relationnelle
                    val oldCardIdToNewId = mutableMapOf<Long, Long>()
                    val oldPollenIdToNewId = mutableMapOf<String, String>()

                    // Insertion des cartes
                    for (card in importedCards) {
                        if (existingCardNames.contains(card.specificName)) continue

                        val newImagePath = card.imagePath?.let {
                            val imageName = File(it).name
                            imageBytesMap[imageName]?.let { bytes -> saveImageToInternalStorage(context, bytes) }
                        }
                        val oldId = card.id
                        val newId = cardDao.insert(card.copy(id = 0, imagePath = newImagePath))
                        oldCardIdToNewId[oldId] = newId
                        importedCardCount++
                    }

                    // Insertion des grains de pollen
                    for (pollen in importedPollen) {
                        val newCardId = oldCardIdToNewId[pollen.forgedCardId] ?: continue // Si la carte a été sautée, on saute le pollen aussi
                        val oldPollenId = pollen.id
                        val newPollenId = UUID.randomUUID().toString()
                        pollenGrainDao.insert(pollen.copy(id = newPollenId, forgedCardId = newCardId))
                        oldPollenIdToNewId[oldPollenId] = newPollenId
                    }

                    // Insertion des résultats d'analyse
                    for (analysis in importedAnalysis) {
                        val newPollenId = oldPollenIdToNewId[analysis.pollenGrainId] ?: continue
                        analysisResultDao.insert(analysis.copy(id = UUID.randomUUID().toString(), pollenGrainId = newPollenId))
                    }
                }
            }
            Log.i(TAG, "Importation de la Saga terminée. $importedCardCount cartes (et leurs données associées) ont été ajoutées.")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur durant l'importation de la saga", e)
            return@withContext -1
        }
        return@withContext importedCardCount
    }

    private fun saveImageToInternalStorage(context: Context, imageBytes: ByteArray): String? {
        return try {
            val cardImagesDir = File(context.filesDir, "card_images")
            if (!cardImagesDir.exists()) cardImagesDir.mkdirs()
            val newImageFile = File(cardImagesDir, "card_${UUID.randomUUID()}.png")
            FileOutputStream(newImageFile).use { it.write(imageBytes) }
            newImageFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la sauvegarde de l'image importée", e)
            null
        }
    }

    private fun addFileToZip(entryName: String, data: ByteArray, zos: ZipOutputStream) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    // BOURDON'S NOTE: Nouvelle fonction pour redimensionner les images avant de les zipper.
    private fun resizeAndAddImageToZip(imageFile: File, entryName: String, zos: ZipOutputStream) {
        try {
            val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (originalBitmap == null) {
                Log.w(TAG, "Impossible de décoder le bitmap pour ${imageFile.path}")
                return
            }

            val (newWidth, newHeight) = calculateNewDimensions(originalBitmap.width, originalBitmap.height)

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            originalBitmap.recycle()

            ByteArrayOutputStream().use { baos ->
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
                addFileToZip(entryName, baos.toByteArray(), zos)
            }
            resizedBitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du redimensionnement de l'image ${imageFile.path}", e)
        }
    }

    private fun calculateNewDimensions(width: Int, height: Int): Pair<Int, Int> {
        if (width <= IMAGE_MAX_SIZE && height <= IMAGE_MAX_SIZE) {
            return Pair(width, height)
        }
        return if (width > height) {
            val newHeight = (height.toFloat() / width.toFloat() * IMAGE_MAX_SIZE).toInt()
            Pair(IMAGE_MAX_SIZE, newHeight)
        } else {
            val newWidth = (width.toFloat() / height.toFloat() * IMAGE_MAX_SIZE).toInt()
            Pair(newWidth, IMAGE_MAX_SIZE)
        }
    }
}