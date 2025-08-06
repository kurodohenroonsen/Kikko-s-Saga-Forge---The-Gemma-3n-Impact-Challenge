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
 */
class AnalysisResultDao(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context) // BOURDON'S FIX: Assure l'initialisation correcte du singleton
    private val gson = Gson()

    /**
     * Insère un nouveau résultat d'analyse (ou une tâche) dans la base de données.
     * @param result L'objet AnalysisResult à insérer.
     */
    suspend fun insert(result: AnalysisResult) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_AR_ID, result.id) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_POLLEN_ID, result.pollenGrainId) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_PROPERTY_NAME, result.propertyName) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_MODEL_CONFIG_JSON, result.modelConfigJson) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_RAW_RESPONSE, result.rawResponse) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_STATUS, result.status.name) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_TIMESTAMP, result.timestamp) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_ERROR_MESSAGE, result.errorMessage) // BOURDON'S FIX: Références qualifiées
        }
        db.insert(DatabaseHelper.TABLE_ANALYSIS_RESULTS, null, values) // BOURDON'S FIX: Références qualifiées
    }

    /**
     * Met à jour un résultat d'analyse existant.
     * @param result L'objet AnalysisResult avec les nouvelles données.
     */
    suspend fun update(result: AnalysisResult) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_AR_RAW_RESPONSE, result.rawResponse) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_STATUS, result.status.name) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_AR_ERROR_MESSAGE, result.errorMessage) // BOURDON'S FIX: Références qualifiées
        }
        db.update(
            DatabaseHelper.TABLE_ANALYSIS_RESULTS, // BOURDON'S FIX: Références qualifiées
            values,
            "${DatabaseHelper.COLUMN_AR_ID} = ?", // BOURDON'S FIX: Références qualifiées
            arrayOf(result.id)
        )
    }

    /**
     * Récupère tous les résultats d'analyse pour un PollenGrain et une propriété spécifiques.
     * @param pollenGrainId L'ID du PollenGrain parent.
     * @param propertyName Le nom de la propriété (ex: "description").
     * @return Une liste d'AnalysisResults.
     */
    suspend fun getByPollenGrainIdAndProperty(pollenGrainId: String, propertyName: String): List<AnalysisResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnalysisResult>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_ANALYSIS_RESULTS, // BOURDON'S FIX: Références qualifiées
            null, // Toutes les colonnes
            "${DatabaseHelper.COLUMN_AR_POLLEN_ID} = ? AND ${DatabaseHelper.COLUMN_AR_PROPERTY_NAME} = ?", // BOURDON'S FIX: Références qualifiées
            arrayOf(pollenGrainId, propertyName),
            null, null, "${DatabaseHelper.COLUMN_AR_TIMESTAMP} ASC" // BOURDON'S FIX: Références qualifiées
        )
        cursor.use {
            while (it.moveToNext()) {
                results.add(cursorToAnalysisResult(it))
            }
        }
        return@withContext results
    }

    /**
     * BOURDON'S FIX: Supprime tous les résultats d'analyse pour une propriété spécifique d'un grain.
     * C'est la méthode nécessaire pour la fonctionnalité de "relance".
     */
    suspend fun deleteByPollenGrainIdAndProperty(pollenGrainId: String, propertyName: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val deletedRows = db.delete(
            DatabaseHelper.TABLE_ANALYSIS_RESULTS,
            "${DatabaseHelper.COLUMN_AR_POLLEN_ID} = ? AND ${DatabaseHelper.COLUMN_AR_PROPERTY_NAME} = ?",
            arrayOf(pollenGrainId, propertyName)
        )
        Log.i("AnalysisResultDao", "$deletedRows résultats d'analyse supprimés pour le grain $pollenGrainId et la propriété $propertyName.")
    }


    /**
     * Supprime TOUTES les entrées de la table analysis_results. Action irréversible.
     */
    suspend fun nuke() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_ANALYSIS_RESULTS, null, null) // BOURDON'S FIX: Références qualifiées
        Log.w("AnalysisResultDao", "NUKE: La table analysis_results a été entièrement vidée.")
    }

    /**
     * Helper pour convertir une ligne de curseur en un objet AnalysisResult.
     */
    private fun cursorToAnalysisResult(cursor: Cursor): AnalysisResult {
        fun getStringOrNull(columnName: String): String? {
            val colIndex = cursor.getColumnIndex(columnName)
            return if (colIndex != -1 && !cursor.isNull(colIndex)) cursor.getString(colIndex) else null
        }

        return AnalysisResult(
            id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_ID)), // BOURDON'S FIX: Références qualifiées
            pollenGrainId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_POLLEN_ID)), // BOURDON'S FIX: Références qualifiées
            propertyName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_PROPERTY_NAME)), // BOURDON'S FIX: Références qualifiées
            modelConfigJson = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_MODEL_CONFIG_JSON)), // BOURDON'S FIX: Références qualifiées
            rawResponse = getStringOrNull(DatabaseHelper.COLUMN_AR_RAW_RESPONSE), // BOURDON'S FIX: Références qualifiées
            status = AnalysisStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_STATUS))), // BOURDON'S FIX: Références qualifiées
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_AR_TIMESTAMP)), // BOURDON'S FIX: Références qualifiées
            errorMessage = getStringOrNull(DatabaseHelper.COLUMN_AR_ERROR_MESSAGE) // BOURDON'S FIX: Références qualifiées
        )
    }
}