package be.heyman.android.ai.kikko.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "kikko.db"
        // BOURDON'S REFORGE: Version incrémentée à 8 pour ajouter les métriques de performance.
        private const val DATABASE_VERSION = 8

        // --- Table "knowledge_cards" Definition ---
        const val TABLE_CARDS = "knowledge_cards"
        const val COLUMN_CARD_ID = "id"
        const val COLUMN_CARD_SPECIFIC_NAME = "specificName"
        const val COLUMN_CARD_DECK_NAME = "deckName"
        const val COLUMN_CARD_IMAGE_PATH = "imagePath"
        const val COLUMN_CARD_CONFIDENCE = "confidence"
        const val COLUMN_CARD_DESCRIPTION = "description"
        const val COLUMN_CARD_REASONING_JSON = "reasoning"
        const val COLUMN_CARD_STATS_JSON = "stats"
        const val COLUMN_CARD_QUIZ_JSON = "quiz"
        const val COLUMN_CARD_TRANSLATIONS_JSON = "translations"
        const val COLUMN_CARD_SCIENTIFIC_NAME = "scientificName"
        const val COLUMN_CARD_VERNACULAR_NAME = "vernacularName"
        const val COLUMN_CARD_ALLERGENS_JSON = "allergens"
        const val COLUMN_CARD_INGREDIENTS_JSON = "ingredients"


        private const val TABLE_CARDS_CREATE =
            "CREATE TABLE $TABLE_CARDS (" +
                    "$COLUMN_CARD_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_CARD_SPECIFIC_NAME TEXT, " +
                    "$COLUMN_CARD_DECK_NAME TEXT, " +
                    "$COLUMN_CARD_IMAGE_PATH TEXT, " +
                    "$COLUMN_CARD_CONFIDENCE REAL, " +
                    "$COLUMN_CARD_DESCRIPTION TEXT, " +
                    "$COLUMN_CARD_SCIENTIFIC_NAME TEXT, " +
                    "$COLUMN_CARD_VERNACULAR_NAME TEXT, " +
                    "$COLUMN_CARD_REASONING_JSON TEXT, " +
                    "$COLUMN_CARD_STATS_JSON TEXT, " +
                    "$COLUMN_CARD_QUIZ_JSON TEXT, " +
                    "$COLUMN_CARD_TRANSLATIONS_JSON TEXT, " +
                    "$COLUMN_CARD_ALLERGENS_JSON TEXT, " +
                    "$COLUMN_CARD_INGREDIENTS_JSON TEXT);"

        // --- Table "pollen_grains" Definition (inchangée) ---
        const val TABLE_POLLEN_GRAINS = "pollen_grains"
        const val COLUMN_POLLEN_ID = "id"
        const val COLUMN_POLLEN_TIMESTAMP = "timestamp"
        const val COLUMN_POLLEN_STATUS = "status"
        const val COLUMN_POLLEN_USER_INTENT = "user_intent"
        const val COLUMN_POLLEN_IMAGE_PATHS_JSON = "image_paths_json"
        const val COLUMN_POLLEN_SWARM_REPORT_JSON = "swarm_report_json"
        const val COLUMN_POLLEN_FORGED_CARD_ID = "forged_card_id"

        private const val TABLE_POLLEN_CREATE =
            "CREATE TABLE $TABLE_POLLEN_GRAINS (" +
                    "$COLUMN_POLLEN_ID TEXT PRIMARY KEY NOT NULL, " +
                    "$COLUMN_POLLEN_TIMESTAMP INTEGER NOT NULL, " +
                    "$COLUMN_POLLEN_STATUS TEXT NOT NULL, " +
                    "$COLUMN_POLLEN_USER_INTENT TEXT, " +
                    "$COLUMN_POLLEN_IMAGE_PATHS_JSON TEXT NOT NULL, " +
                    "$COLUMN_POLLEN_SWARM_REPORT_JSON TEXT, " +
                    "$COLUMN_POLLEN_FORGED_CARD_ID INTEGER, " +
                    "FOREIGN KEY($COLUMN_POLLEN_FORGED_CARD_ID) REFERENCES $TABLE_CARDS($COLUMN_CARD_ID));"

        // --- Table "analysis_results" Definition ---
        const val TABLE_ANALYSIS_RESULTS = "analysis_results"
        const val COLUMN_AR_ID = "id"
        const val COLUMN_AR_POLLEN_ID = "pollen_grain_id"
        const val COLUMN_AR_PROPERTY_NAME = "property_name"
        const val COLUMN_AR_MODEL_CONFIG_JSON = "model_config_json"
        const val COLUMN_AR_RAW_RESPONSE = "raw_response"
        const val COLUMN_AR_STATUS = "status"
        const val COLUMN_AR_TIMESTAMP = "timestamp"
        const val COLUMN_AR_ERROR_MESSAGE = "error_message"
        // BOURDON'S REFORGE: Ajout des nouvelles colonnes pour les métriques de performance.
        const val COLUMN_AR_TTFT_MS = "ttft_ms"
        const val COLUMN_AR_TOTAL_TIME_MS = "total_time_ms"
        const val COLUMN_AR_TOKENS_PER_SEC = "tokens_per_second"


        private const val TABLE_ANALYSIS_RESULTS_CREATE =
            "CREATE TABLE $TABLE_ANALYSIS_RESULTS (" +
                    "$COLUMN_AR_ID TEXT PRIMARY KEY NOT NULL, " +
                    "$COLUMN_AR_POLLEN_ID TEXT NOT NULL, " +
                    "$COLUMN_AR_PROPERTY_NAME TEXT NOT NULL, " +
                    "$COLUMN_AR_MODEL_CONFIG_JSON TEXT NOT NULL, " +
                    "$COLUMN_AR_RAW_RESPONSE TEXT, " +
                    "$COLUMN_AR_STATUS TEXT NOT NULL, " +
                    "$COLUMN_AR_TIMESTAMP INTEGER NOT NULL, " +
                    "$COLUMN_AR_ERROR_MESSAGE TEXT, " +
                    // BOURDON'S REFORGE: Ajout des nouvelles colonnes au schéma de création.
                    "$COLUMN_AR_TTFT_MS INTEGER, " +
                    "$COLUMN_AR_TOTAL_TIME_MS INTEGER, " +
                    "$COLUMN_AR_TOKENS_PER_SEC REAL, " +
                    "FOREIGN KEY($COLUMN_AR_POLLEN_ID) REFERENCES $TABLE_POLLEN_GRAINS($COLUMN_POLLEN_ID) ON DELETE CASCADE);"


        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        Log.i("DatabaseHelper", "Création des tables de la base de données (v$DATABASE_VERSION)...")
        db?.execSQL(TABLE_CARDS_CREATE)
        db?.execSQL(TABLE_POLLEN_CREATE)
        db?.execSQL(TABLE_ANALYSIS_RESULTS_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.w("DatabaseHelper", "Mise à jour de la base de données de la version $oldVersion à $newVersion, les anciennes données seront détruites.")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_ANALYSIS_RESULTS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_POLLEN_GRAINS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_CARDS")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.w("DatabaseHelper", "Retour en arrière de la BDD de v$oldVersion à v$newVersion. Purge complète des données...")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_ANALYSIS_RESULTS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_POLLEN_GRAINS")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_CARDS")
        onCreate(db)
    }
}