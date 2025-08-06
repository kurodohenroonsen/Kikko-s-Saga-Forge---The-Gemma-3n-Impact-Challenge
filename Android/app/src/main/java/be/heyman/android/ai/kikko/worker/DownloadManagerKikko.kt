package be.heyman.android.ai.kikko.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import be.heyman.android.ai.kikko.data.KEY_MODEL_DOWNLOAD_FILE_NAME
import be.heyman.android.ai.kikko.data.KEY_MODEL_IS_ZIP
import be.heyman.android.ai.kikko.data.KEY_MODEL_NAME
import be.heyman.android.ai.kikko.data.KEY_MODEL_TOTAL_BYTES
import be.heyman.android.ai.kikko.data.KEY_MODEL_UNZIPPED_DIR
import be.heyman.android.ai.kikko.data.KEY_MODEL_URL
import be.heyman.android.ai.kikko.data.Model

/**
 * Manager pour initier les téléchargements de modèles via WorkManager.
 * Déplacé depuis le package `debug` car il gère les modèles locaux de l'application.
 */
object DownloadManagerKikko {

    /**
     * Lance le téléchargement d'un modèle spécifié.
     * Le modèle est identifié par son nom unique pour éviter les téléchargements multiples.
     *
     * @param context Contexte de l'application.
     * @param model Le modèle à télécharger.
     */
    fun startDownload(context: Context, model: Model) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        val inputData = Data.Builder()
            .putString(KEY_MODEL_NAME, model.name)
            .putString(KEY_MODEL_URL, model.url)
            .putString(KEY_MODEL_DOWNLOAD_FILE_NAME, model.downloadFileName)
            .putLong(KEY_MODEL_TOTAL_BYTES, model.sizeInBytes)
            .putBoolean(KEY_MODEL_IS_ZIP, model.isZip)
            .putString(KEY_MODEL_UNZIPPED_DIR, model.unzipDir)
            .build()

        val downloadWorkRequest = OneTimeWorkRequestBuilder<KikkoDownloadWorker>()
            .setInputData(inputData)
            .addTag(model.name) // On utilise le nom comme tag unique pour identifier ce travail
            .build()

        workManager.enqueueUniqueWork(
            model.name, // Nom unique pour la tâche de téléchargement
            ExistingWorkPolicy.REPLACE, // Remplace la tâche si elle existe déjà
            downloadWorkRequest
        )
    }
}