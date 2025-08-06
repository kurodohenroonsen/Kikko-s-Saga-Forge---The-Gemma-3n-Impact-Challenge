package be.heyman.android.ai.kikko.persistence

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import be.heyman.android.ai.kikko.model.PollenGrain
import be.heyman.android.ai.kikko.model.PollenStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DAO (Data Access Object) pour l'entité PollenGrain.
 * Gère toutes les interactions avec la table "pollen_grains" en utilisant des requêtes SQL pures.
 */
class PollenGrainDao(context: Context) {

    private val dbHelper = DatabaseHelper.getInstance(context) // BOURDON'S FIX: Assure l'initialisation correcte du singleton
    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type

    /**
     * Insère un nouveau PollenGrain dans la base de données.
     * @param pollenGrain L'objet à insérer.
     */
    suspend fun insert(pollenGrain: PollenGrain) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_POLLEN_ID, pollenGrain.id) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_POLLEN_TIMESTAMP, pollenGrain.timestamp) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_POLLEN_STATUS, pollenGrain.status.name) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_POLLEN_USER_INTENT, pollenGrain.userIntent) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_POLLEN_IMAGE_PATHS_JSON, gson.toJson(pollenGrain.pollenImagePaths, stringListType)) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_POLLEN_SWARM_REPORT_JSON, pollenGrain.swarmAnalysisReportJson) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_POLLEN_FORGED_CARD_ID, pollenGrain.forgedCardId) // BOURDON'S FIX: Références qualifiées
        }
        db.insert(DatabaseHelper.TABLE_POLLEN_GRAINS, null, values) // BOURDON'S FIX: Références qualifiées
    }

    /**
     * Met à jour le statut et l'ID de la carte forgée d'un PollenGrain.
     * Utilisé à la fin d'une étape de forge réussie.
     * @param pollenId L'ID du grain à mettre à jour.
     * @param newStatus Le nouveau statut du grain.
     * @param forgedCardId L'ID de la KnowledgeCard nouvellement créée.
     */
    suspend fun updateForgingResult(pollenId: String, newStatus: PollenStatus, forgedCardId: Long?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_POLLEN_STATUS, newStatus.name) // BOURDON'S FIX: Références qualifiées
            put(DatabaseHelper.COLUMN_POLLEN_FORGED_CARD_ID, forgedCardId) // BOURDON'S FIX: Références qualifiées
        }
        db.update(
            DatabaseHelper.TABLE_POLLEN_GRAINS, // BOURDON'S FIX: Références qualifiées
            values,
            "${DatabaseHelper.COLUMN_POLLEN_ID} = ?", // BOURDON'S FIX: Références qualifiées
            arrayOf(pollenId)
        )
    }

    /**
     * Met à jour uniquement le statut d'un PollenGrain.
     * C'est une méthode optimisée pour les workers.
     * @param pollenId L'ID du grain à mettre à jour.
     * @param newStatus Le nouveau statut à appliquer.
     */
    suspend fun updateStatus(pollenId: String, newStatus: PollenStatus) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_POLLEN_STATUS, newStatus.name) // BOURDON'S FIX: Références qualifiées
        }
        db.update(
            DatabaseHelper.TABLE_POLLEN_GRAINS, // BOURDON'S FIX: Références qualifiées
            values,
            "${DatabaseHelper.COLUMN_POLLEN_ID} = ?", // BOURDON'S FIX: Références qualifiées
            arrayOf(pollenId)
        )
    }

    /**
     * BOURDON'S FIX: Nouvelle méthode de suppression pour un grain spécifique.
     * @param pollenGrain Le grain à supprimer de la base de données.
     */
    suspend fun delete(pollenGrain: PollenGrain) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            DatabaseHelper.TABLE_POLLEN_GRAINS,
            "${DatabaseHelper.COLUMN_POLLEN_ID} = ?",
            arrayOf(pollenGrain.id)
        )
        Log.d("PollenGrainDao", "PollenGrain (ID: ${pollenGrain.id}) supprimé de la base de données.")
    }


    /**
     * Récupère tous les PollenGrains ayant un statut spécifique.
     * @param status Le statut à rechercher.
     * @return Une liste de PollenGrains correspondants.
     */
    suspend fun getByStatus(status: PollenStatus): List<PollenGrain> = withContext(Dispatchers.IO) {
        val grains = mutableListOf<PollenGrain>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_POLLEN_GRAINS, // BOURDON'S FIX: Références qualifiées
            null, // Toutes les colonnes
            "${DatabaseHelper.COLUMN_POLLEN_STATUS} = ?", // BOURDON'S FIX: Références qualifiées
            arrayOf(status.name),
            null, null, "${DatabaseHelper.COLUMN_POLLEN_TIMESTAMP} ASC" // BOURDON'S FIX: Références qualifiées
        )
        cursor.use {
            while (it.moveToNext()) {
                grains.add(cursorToPollenGrain(it))
            }
        }
        return@withContext grains
    }

    /**
     * NOUVEAU: Récupère un PollenGrain par l'ID de la carte qu'il a forgée.
     * @param cardId L'ID de la KnowledgeCard.
     * @return Le PollenGrain correspondant ou null si non trouvé.
     */
    suspend fun findByForgedCardId(cardId: Long): PollenGrain? = withContext(Dispatchers.IO) {
        var grain: PollenGrain? = null
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_POLLEN_GRAINS, // BOURDON'S FIX: Références qualifiées
            null,
            "${DatabaseHelper.COLUMN_POLLEN_FORGED_CARD_ID} = ?", // BOURDON'S FIX: Références qualifiées
            arrayOf(cardId.toString()),
            null, null, null, "1"
        )
        cursor.use {
            if (it.moveToFirst()) {
                grain = cursorToPollenGrain(it)
            }
        }
        return@withContext grain
    }

    /**
     * Compte le nombre de grains pour chaque statut.
     * C'est la fonction clé pour alimenter les cocardes de l'interface.
     * @return Une map associant chaque PollenStatus à son nombre d'occurrences.
     */
    suspend fun countByStatus(): Map<PollenStatus, Int> = withContext(Dispatchers.IO) {
        val counts = PollenStatus.values().associateWith { 0 }.toMutableMap()
        val db = dbHelper.readableDatabase
        val query = "SELECT ${DatabaseHelper.COLUMN_POLLEN_STATUS}, COUNT(*) FROM ${DatabaseHelper.TABLE_POLLEN_GRAINS} GROUP BY ${DatabaseHelper.COLUMN_POLLEN_STATUS}" // BOURDON'S FIX: Références qualifiées
        val cursor = db.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                try {
                    val status = PollenStatus.valueOf(it.getString(it.getColumnIndexOrThrow(DatabaseHelper.COLUMN_POLLEN_STATUS))) // BOURDON'S FIX: getColumnIndexOrThrow
                    val count = it.getInt(it.getColumnIndexOrThrow("COUNT(*)")) // BOURDON'S FIX: getColumnIndexOrThrow
                    counts[status] = count
                } catch (e: IllegalArgumentException) {
                    // Ignore les statuts inconnus dans la base de données
                }
            }
        }
        return@withContext counts
    }

    suspend fun getDebugSummary(): String = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(DatabaseHelper.TABLE_POLLEN_GRAINS, null, null, null, null, null, "${DatabaseHelper.COLUMN_POLLEN_TIMESTAMP} DESC") // BOURDON'S FIX: Références qualifiées
        val summary = StringBuilder()
        summary.append("--- Début du Rapport de la Réserve de Pollen ---\n")
        summary.append("Total de Grains: ${cursor.count}\n")
        cursor.use {
            while (it.moveToNext()) {
                val grain = cursorToPollenGrain(it)
                summary.append("  - ID: ${grain.id.substring(0, 8)}... | Status: ${grain.status} | CardID: ${grain.forgedCardId ?: "N/A"}\n")
            }
        }
        summary.append("--- Fin du Rapport ---")
        return@withContext summary.toString()
    }

    /**
     * Supprime TOUTES les entrées de la table pollen_grains. Action irréversible.
     */
    suspend fun nuke() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_POLLEN_GRAINS, null, null) // BOURDON'S FIX: Références qualifiées
        Log.w("PollenGrainDao", "NUKE: La table pollen_grains a été entièrement vidée.")
    }

    /**
     * Helper pour convertir une ligne de curseur en un objet PollenGrain.
     */
    private fun cursorToPollenGrain(cursor: Cursor): PollenGrain {
        fun getStringOrNull(columnName: String): String? {
            val colIndex = cursor.getColumnIndex(columnName)
            return if (colIndex != -1 && !cursor.isNull(colIndex)) cursor.getString(colIndex) else null
        }
        fun getLongOrNull(columnName: String): Long? {
            val colIndex = cursor.getColumnIndex(columnName)
            return if (colIndex != -1 && !cursor.isNull(colIndex)) cursor.getLong(colIndex) else null
        }


        return PollenGrain(
            id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_POLLEN_ID)), // BOURDON'S FIX: getColumnIndexOrThrow
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_POLLEN_TIMESTAMP)), // BOURDON'S FIX: getColumnIndexOrThrow
            status = PollenStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_POLLEN_STATUS))), // BOURDON'S FIX: getColumnIndexOrThrow
            userIntent = getStringOrNull(DatabaseHelper.COLUMN_POLLEN_USER_INTENT), // BOURDON'S FIX: getColumnIndexOrThrow
            pollenImagePaths = gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_POLLEN_IMAGE_PATHS_JSON)), stringListType), // BOURDON'S FIX: getColumnIndexOrThrow
            swarmAnalysisReportJson = getStringOrNull(DatabaseHelper.COLUMN_POLLEN_SWARM_REPORT_JSON), // BOURDON'S FIX: getColumnIndexOrThrow
            forgedCardId = getLongOrNull(DatabaseHelper.COLUMN_POLLEN_FORGED_CARD_ID) // BOURDON'S FIX: getColumnIndexOrThrow
        )
    }
}