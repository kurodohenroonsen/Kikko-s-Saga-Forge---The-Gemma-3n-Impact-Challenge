package be.heyman.android.ai.kikko

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.persistence.CardDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun exportSaga(context: Context): Uri? = withContext(Dispatchers.IO) {
        val cardDao = CardDao(context)
        val allCards = cardDao.getAll()

        if (allCards.isEmpty()) {
            Log.w(TAG, "Aucune carte à exporter.")
            return@withContext null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val exportFileName = "KikkoSaga_Export_$timestamp.kikkoSaga"

        val exportDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val exportFile = File(exportDir, exportFileName)

        try {
            ZipOutputStream(FileOutputStream(exportFile)).use { zos ->
                val cardsJson = Gson().toJson(allCards)
                addFileToZip("cards.json", cardsJson.toByteArray(), zos)

                allCards.forEach { card ->
                    card.imagePath?.let { path ->
                        val imageFile = File(path)
                        if (imageFile.exists()) {
                            addFileToZip("card_images/${imageFile.name}", imageFile, zos)
                        }
                    }
                }
            }
            Log.i(TAG, "Exportation réussie vers : ${exportFile.absolutePath}")
            return@withContext FileProvider.getUriForFile(context, "${context.packageName}.provider", exportFile)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'exportation de la saga", e)
            exportFile.delete()
            return@withContext null
        }
    }

    suspend fun importSaga(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val cardDao = CardDao(context)
        val existingCardNames = cardDao.getAll().map { it.specificName }.toSet()
        var importedCount = 0
        Log.d(TAG, "Démarrage de l'importation de la Saga depuis : $uri")

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    var cards: List<KnowledgeCard>? = null
                    val imageBytesMap = mutableMapOf<String, ByteArray>()

                    // Première passe : extraire toutes les données en mémoire
                    while (entry != null) {
                        if (entry.name == "cards.json") {
                            val jsonString = zis.bufferedReader().readText()
                            val cardType = object : TypeToken<List<KnowledgeCard>>() {}.type
                            cards = Gson().fromJson(jsonString, cardType)
                            Log.d(TAG, "${cards?.size ?: 0} carte(s) trouvée(s) dans 'cards.json'.")
                        } else if (!entry.isDirectory && entry.name.startsWith("card_images/")) {
                            val imageName = File(entry.name).name
                            imageBytesMap[imageName] = zis.readBytes()
                            Log.d(TAG, "Image '$imageName' extraite de l'archive.")
                        }
                        entry = zis.nextEntry
                    }

                    // Seconde passe : traiter et insérer les données
                    cards?.forEach { importedCard ->
                        if (!existingCardNames.contains(importedCard.specificName)) {
                            val finalCard = importedCard.imagePath?.let { originalPath ->
                                val originalImageName = File(originalPath).name
                                val imageBytes = imageBytesMap[originalImageName]
                                if (imageBytes != null) {
                                    val newImagePath = saveImageToInternalStorage(context, imageBytes)
                                    Log.d(TAG, "Image pour '${importedCard.specificName}' sauvegardée vers : $newImagePath")
                                    importedCard.copy(imagePath = newImagePath)
                                } else {
                                    Log.w(TAG, "Image '${originalImageName}' manquante pour la carte '${importedCard.specificName}'.")
                                    importedCard
                                }
                            } ?: importedCard

                            cardDao.insert(finalCard.copy(id = 0)) // Force un nouvel ID
                            importedCount++
                            Log.i(TAG, "Nouvelle carte importée : '${finalCard.specificName}'")
                        } else {
                            Log.d(TAG, "Carte déjà existante, ignorée : '${importedCard.specificName}'")
                        }
                    }
                }
            }
            Log.i(TAG, "Importation de la Saga terminée. $importedCount cartes ajoutées.")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur durant l'importation de la saga", e)
            return@withContext -1
        }
        return@withContext importedCount
    }

    private fun saveImageToInternalStorage(context: Context, imageBytes: ByteArray): String? {
        return try {
            val cardImagesDir = File(context.filesDir, "card_images")
            if (!cardImagesDir.exists()) cardImagesDir.mkdirs()
            val newImageFile = File(cardImagesDir, "card_${UUID.randomUUID()}.png")
            FileOutputStream(newImageFile).use { fos ->
                fos.write(imageBytes)
            }
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

    private fun addFileToZip(entryName: String, file: File, zos: ZipOutputStream) {
        FileInputStream(file).use { fis ->
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }
}