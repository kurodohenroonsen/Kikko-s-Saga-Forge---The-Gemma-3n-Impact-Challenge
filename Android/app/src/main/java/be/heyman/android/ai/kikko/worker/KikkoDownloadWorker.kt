package be.heyman.android.ai.kikko.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.data.KEY_MODEL_DOWNLOAD_FILE_NAME
import be.heyman.android.ai.kikko.data.KEY_MODEL_DOWNLOAD_REMAINING_MS
import be.heyman.android.ai.kikko.data.KEY_MODEL_IS_ZIP
import be.heyman.android.ai.kikko.data.KEY_MODEL_NAME
import be.heyman.android.ai.kikko.data.KEY_MODEL_START_UNZIPPING
import be.heyman.android.ai.kikko.data.KEY_MODEL_TOTAL_BYTES
import be.heyman.android.ai.kikko.data.KEY_MODEL_UNZIPPED_DIR
import be.heyman.android.ai.kikko.data.KEY_MODEL_URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Worker Hilt pour le téléchargement et la décompression de modèles.
 * Déplacé depuis le package `debug` car il est central à la gestion des modèles.
 */
class KikkoDownloadWorker(val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val ACTION_PROGRESS_UPDATE = "com.google.edge.kikko.debug.PROGRESS_UPDATE"
        const val EXTRA_MODEL_NAME = "EXTRA_MODEL_NAME"
        const val EXTRA_RECEIVED_BYTES = "EXTRA_RECEIVED_BYTES"
        const val EXTRA_TOTAL_BYTES = "EXTRA_TOTAL_BYTES"
        const val EXTRA_DOWNLOAD_SPEED_BPS = "EXTRA_DOWNLOAD_SPEED_BPS"
        const val NOTIFICATION_CHANNEL_ID = "kikko_download_channel"
    }

    private val TAG = "KikkoDownloadWorker"
    private val notificationManager = NotificationManagerCompat.from(applicationContext)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Téléchargements de modèles Kikko",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: return Result.failure()
        val fileUrl = inputData.getString(KEY_MODEL_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_MODEL_DOWNLOAD_FILE_NAME) ?: return Result.failure()
        val estimatedTotalBytes = inputData.getLong(KEY_MODEL_TOTAL_BYTES, 0L)
        val isZip = inputData.getBoolean(KEY_MODEL_IS_ZIP, false)
        val unzipDir = inputData.getString(KEY_MODEL_UNZIPPED_DIR)

        val modelBaseDir = File(applicationContext.getExternalFilesDir(null), "imported_models") // BOURDON'S FIX: Utilisation du dossier "imported_models"
        if (!modelBaseDir.exists()) modelBaseDir.mkdirs()
        val modelDir = File(modelBaseDir, modelName)
        if (!modelDir.exists()) modelDir.mkdirs()

        val outputFile = File(modelDir, fileName)

        try {
            setForeground(createForegroundInfo("Démarrage du téléchargement pour $modelName", 0))
            downloadFile(fileUrl, outputFile, estimatedTotalBytes, modelName)

            if (isZip) { // BOURDON'S FIX: Le dossier de décompression est maintenant le dossier du modèle lui-même, pas un sous-dossier spécifié par `unzipDir`
                setForeground(createForegroundInfo("Décompression de $modelName...", 0, true))
                setProgress(Data.Builder().putBoolean(KEY_MODEL_START_UNZIPPING, true).build())
                unzip(outputFile, modelDir) // Décompresse directement dans le dossier du modèle
                outputFile.delete() // Supprime le fichier zip après décompression
            }
            return Result.success()
        } catch (e: Exception) {
            if (e is CancellationException || e is InterruptedException) {
                Log.i(TAG, "Download cancelled for $modelName")
            } else {
                Log.e(TAG, "Download or Unzip failed for $modelName", e)
                if(outputFile.exists()) outputFile.delete()
                return Result.failure()
            }
            return Result.success() // En cas d'annulation, on termine le worker proprement
        }
    }

    private suspend fun downloadFile(downloadUrl: String, outputFile: File, estimatedTotalBytes: Long, modelName: String) {
        val url = URL(downloadUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
        }

        val actualTotalBytes = if (connection.contentLength > 0) connection.contentLength.toLong() else estimatedTotalBytes

        val inputStream = connection.inputStream
        val outputStream = FileOutputStream(outputFile)
        val buffer = ByteArray(8 * 1024)
        var bytesCopied: Long = 0
        var bytesRead: Int
        var lastReportTime = System.currentTimeMillis()
        var lastReportedBytes: Long = 0
        val bytesReadSizeBuffer: MutableList<Long> = mutableListOf()
        val bytesReadLatencyBuffer: MutableList<Long> = mutableListOf()

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isStopped) throw InterruptedException("Download cancelled")
                outputStream.write(buffer, 0, bytesRead)
                bytesCopied += bytesRead

                val currentTime = System.currentTimeMillis()
                if (currentTime - lastReportTime >= 500) { // Update notification less frequently
                    val timeDelta = (currentTime - lastReportTime)
                    val bytesDelta = bytesCopied - lastReportedBytes
                    val speedBps = if (timeDelta > 0) (bytesDelta * 1000 / timeDelta) else 0L

                    var remainingMs = -1L
                    bytesReadSizeBuffer.add(bytesDelta)
                    if (bytesReadSizeBuffer.size > 5) bytesReadSizeBuffer.removeAt(0)
                    bytesReadLatencyBuffer.add(timeDelta)
                    if (bytesReadLatencyBuffer.size > 5) bytesReadLatencyBuffer.removeAt(0)

                    val totalRecentBytes = bytesReadSizeBuffer.sum()
                    val totalRecentTime = bytesReadLatencyBuffer.sum()
                    if (totalRecentTime > 0 && totalRecentBytes > 0) {
                        val avgSpeedBps = totalRecentBytes * 1000 / totalRecentTime
                        if (avgSpeedBps > 0) {
                            remainingMs = (actualTotalBytes - bytesCopied) * 1000 / avgSpeedBps
                        }
                    }

                    val progressPercent = if (actualTotalBytes > 0) (bytesCopied * 100 / actualTotalBytes).toInt() else 0
                    val progressText = "Téléchargement de $modelName ($progressPercent%)"
                    setForeground(createForegroundInfo(progressText, progressPercent))

                    val intent = Intent(ACTION_PROGRESS_UPDATE).apply {
                        putExtra(EXTRA_MODEL_NAME, modelName)
                        putExtra(EXTRA_RECEIVED_BYTES, bytesCopied)
                        putExtra(EXTRA_TOTAL_BYTES, actualTotalBytes)
                        putExtra(EXTRA_DOWNLOAD_SPEED_BPS, speedBps)
                        putExtra(KEY_MODEL_DOWNLOAD_REMAINING_MS, remainingMs)
                    }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

                    lastReportTime = currentTime
                    lastReportedBytes = bytesCopied
                    delay(50)
                }
            }
        } finally {
            outputStream.close()
            inputStream.close()
        }
    }

    private fun unzip(zipFile: File, targetDirectory: File) {
        Log.d(TAG, "Unzipping ${zipFile.name} into ${targetDirectory.name}")
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (isStopped) throw InterruptedException("Unzip cancelled")

                val newFile = File(targetDirectory, entry.name)
                // Protection contre les Zip Slip Attacks
                if (!newFile.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                    throw IOException("Zip Slip Attack détectée: ${entry.name}")
                }

                if (entry.isDirectory) {
                    if (!newFile.isDirectory && !newFile.mkdirs()) {
                        throw IOException("Échec de la création du répertoire ${newFile.absolutePath}")
                    }
                } else {
                    val parent = newFile.parentFile
                    if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                        throw IOException("Échec de la création du répertoire parent ${parent.absolutePath}")
                    }
                    FileOutputStream(newFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Préparation du téléchargement...", 0)
    }

    private fun createForegroundInfo(text: String, progress: Int, indeterminate: Boolean = false): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Forge de la Ruche Kikko")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Assurez-vous d'avoir une icône ici
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .build()

        return ForegroundInfo(id.hashCode(), notification)
    }
}