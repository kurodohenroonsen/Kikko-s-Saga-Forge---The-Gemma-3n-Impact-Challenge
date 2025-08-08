package be.heyman.android.ai.kikko.persistence

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import be.heyman.android.ai.kikko.model.AnalysisResult
import be.heyman.android.ai.kikko.model.AnalysisStatus
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DAO (Data Access Object) pour l'entité AnalysisResult.
 * Gère toutes les interactions avec la table "analysis_results".
 * BOURDON'S REFORGE: Le DAO gère maintenant les nouvelles colonnes de métriques de performance.
 */
class AnalysisResultDao(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context)
    private val gson = Gson()

    suspend fun insert(result: AnalysisResult) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_AR_ID, result.id)
            put(DatabaseHelper.COLUMN_AR_POLLEN_ID, result.pollenGrainId)
            put(DatabaseHelper.COLUMN_AR_PROPERTY_NAME, result.propertyName)
            put(DatabaseHelper.COLUMN_AR_MODEL_CONFIG_JSON, result.modelConfigJson)
            put(DatabaseHelper.COLUMN_AR_RAW_RESPONSE, result.rawResponse)
            put(DatabaseHelper.COLUMN_AR_STATUS, result.status.name)
            put(DatabaseHelper.COLUMN_AR_TIMESTAMP, result.timestamp)
            put(DatabaseHelper.COLUMN_AR_ERROR_MESSAGE, result.errorMessage)
            // BOURDON'S REFORGE: Ajout des nouvelles métriques.
            put(DatabaseHelper.COLUMN_AR_TTFT_MS, result.ttftMs)
            put(DatabaseHelper.COLUMN_AR_TOTAL_TIME_MS, result.totalTimeMs)
            put(DatabaseHelper.COLUMN_AR_TOKENS_PER_SEC, result.tokensPerSecond)
        }
        db.insert(DatabaseHelper.TABLE_ANALYSIS_RESULTS, null, values)
    }

    suspend fun update(result: AnalysisResult) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_AR_RAW_RESPONSE, result.rawResponse)
            put(DatabaseHelper.COLUMN_AR_STATUS, result.status.name)
            put(DatabaseHelper.COLUMN_AR_ERROR_MESSAGE, result.errorMessage)
            // BOURDON'S REFORGE: Ajout des nouvelles métriques à la mise à jour.
            put(DatabaseHelper.COLUMN_AR_TTFT_MS, result.ttftMs)
            put(DatabaseHelper.COLUMN_AR_TOTAL_TIME_MS, result.totalTimeMs)
            put(DatabaseHelper.COLUMN_AR_TOKENS_PER_SEC, result.tokensPerSecond)
        }
        db.update(
            DatabaseHelper.TABLE_ANALYSIS_RESULTS,
            values,
            "${DatabaseHelper.COLUMN_AR_ID} = ?",
            arrayOf(result.id)
        )
    }

    suspend fun getByPollenGrainIdAndProperty(pollenGrainId: String, propertyName: String): List<AnalysisResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnalysisResult>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_ANALYSIS_RESULTS,
            null, // Toutes les colonnes
            "${DatabaseHelper.COLUMN_AR_POLLEN_ID} = ? AND ${DatabaseHelper.COLUMN_AR_PROPERTY_NAME} = ?",
            arrayOf(pollenGrainId, propertyName),
            null, null, "${DatabaseHelper.COLUMN_AR_TIMESTAMP} ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(cursorToAnalysisResult(it))
            }
        }
        return@withContext results
    }

    suspend fun deleteByPollenGrainIdAndProperty(pollenGrainId: String, propertyName: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val deletedRows = db.delete(
            DatabaseHelper.TABLE_ANALYSIS_RESULTS,
            "${DatabaseHelper.COLUMN_AR_POLLEN_ID} = ? AND ${DatabaseHelper.COLUMN_AR_PROPERTY_NAME} = ?",
            arrayOf(pollenGrainId, propertyName)
        )
        Log.i("AnalysisResultDao", "$deletedRows résultats d'analyse supprimés pour le grain $pollenGrainId et la propriété $propertyName.")
    }


    suspend fun nuke() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_ANALYSIS_RESULTS, null, null)
        Log.w("AnalysisResultDao", "NUKE: La table analysis_results a été entièrement vidée.")
    }

    private fun cursorToAnalysisResult(cursor: Cursor): AnalysisResult {
        fun getStringOrNull(columnName: String): String? {
            val colIndex = cursor.getColumnIndex(columnName)
            return if (colIndex != -1 && !cursor.isNull(colIndex)) cursor.getString(colIndex) else null
        }
        // BOURDON'S REFORGE: Fonctions helper pour les types numériques nullables.
        fun getLongOrNull(columnName: String): Long? {
            val colIndex = cursor.getColumnIndex(columnName)
            return if (colIndex != -1 && !cursor.isNull(colIndex)) cursor.getLong(colIndex) else null
        }
        fun getFloatOrNull(columnName: String): Float? {
            val colIndex = cursor.getColumnIndex(columnName)
            return if (colIndex != -1 && !cursor.isNull(colIndex)) cursor.getFloat(colIndex) else null
        }

        return AnalysisResult(
            id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_ID)),
            pollenGrainId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_POLLEN_ID)),
            propertyName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_PROPERTY_NAME)),
            modelConfigJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_MODEL_CONFIG_JSON)),
            rawResponse = getStringOrNull(DatabaseHelper.COLUMN_AR_RAW_RESPONSE),
            status = AnalysisStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_STATUS))),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_TIMESTAMP)),
            errorMessage = getStringOrNull(DatabaseHelper.COLUMN_AR_ERROR_MESSAGE),
            // BOURDON'S REFORGE: Lecture des nouvelles métriques depuis le curseur.
            ttftMs = getLongOrNull(DatabaseHelper.COLUMN_AR_TTFT_MS),
            totalTimeMs = getLongOrNull(DatabaseHelper.COLUMN_AR_TOTAL_TIME_MS),
            tokensPerSecond = getFloatOrNull(DatabaseHelper.COLUMN_AR_TOKENS_PER_SEC)
        )
    }
}