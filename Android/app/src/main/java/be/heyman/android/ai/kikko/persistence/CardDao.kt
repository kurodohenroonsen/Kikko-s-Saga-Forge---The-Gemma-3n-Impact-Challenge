package be.heyman.android.ai.kikko.persistence

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import be.heyman.android.ai.kikko.model.CardStats
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.QuizQuestion
import be.heyman.android.ai.kikko.model.Reasoning
import be.heyman.android.ai.kikko.model.TranslatedContent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * DAO (Data Access Object) for the KnowledgeCard entity.
 * BOURDON'S REFORGE (v6.0): Added a surgical update method for validation.
 */
class CardDao(context: Context) {
    private val dbHelper = DatabaseHelper.getInstance(context)
    private val gson = Gson()
    private val TAG = "KikkoForgeTrace" // BOURDON'S LOGGING: TAG unifié

    private val quizListType = object : TypeToken<List<QuizQuestion>>() {}.type
    private val statsType = object : TypeToken<CardStats>() {}.type
    private val reasoningType = object : TypeToken<Reasoning>() {}.type
    private val translationsType = object : TypeToken<Map<String, TranslatedContent>>() {}.type
    private val stringListType = object : TypeToken<List<String>>() {}.type

    suspend fun insert(card: KnowledgeCard): Long = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_CARD_SPECIFIC_NAME, card.specificName)
            put(DatabaseHelper.COLUMN_CARD_DECK_NAME, card.deckName)
            put(DatabaseHelper.COLUMN_CARD_IMAGE_PATH, card.imagePath)
            put(DatabaseHelper.COLUMN_CARD_CONFIDENCE, card.confidence)
            put(DatabaseHelper.COLUMN_CARD_DESCRIPTION, card.description)
            put(DatabaseHelper.COLUMN_CARD_SCIENTIFIC_NAME, card.scientificName)
            put(DatabaseHelper.COLUMN_CARD_VERNACULAR_NAME, card.vernacularName)
            put(DatabaseHelper.COLUMN_CARD_REASONING_JSON, gson.toJson(card.reasoning))
            put(DatabaseHelper.COLUMN_CARD_STATS_JSON, gson.toJson(card.stats))
            put(DatabaseHelper.COLUMN_CARD_QUIZ_JSON, gson.toJson(card.quiz))
            put(DatabaseHelper.COLUMN_CARD_TRANSLATIONS_JSON, gson.toJson(card.translations))
            put(DatabaseHelper.COLUMN_CARD_ALLERGENS_JSON, gson.toJson(card.allergens, stringListType))
            put(DatabaseHelper.COLUMN_CARD_INGREDIENTS_JSON, gson.toJson(card.ingredients, stringListType))
        }
        val id = db.insert(DatabaseHelper.TABLE_CARDS, null, values)
        Log.i(TAG, "[DAO-INSERT] Carte insérée avec ID: $id. Nom: '${card.specificName}', Deck: '${card.deckName}'")
        return@withContext id
    }

    suspend fun getAll(): List<KnowledgeCard> = withContext(Dispatchers.IO) {
        val cards = mutableListOf<KnowledgeCard>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_CARDS, null, null, null, null, null,
            "${DatabaseHelper.COLUMN_CARD_ID} DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                cards.add(cursorToKnowledgeCard(it))
            }
        }
        return@withContext cards
    }

    suspend fun getCardById(id: Long): KnowledgeCard? = withContext(Dispatchers.IO) {
        var card: KnowledgeCard? = null
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_CARDS, null, "${DatabaseHelper.COLUMN_CARD_ID} = ?",
            arrayOf(id.toString()), null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                card = cursorToKnowledgeCard(it)
            }
        }
        return@withContext card
    }

    suspend fun update(card: KnowledgeCard) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_CARD_SPECIFIC_NAME, card.specificName)
            put(DatabaseHelper.COLUMN_CARD_DECK_NAME, card.deckName)
            put(DatabaseHelper.COLUMN_CARD_IMAGE_PATH, card.imagePath)
            put(DatabaseHelper.COLUMN_CARD_CONFIDENCE, card.confidence)
            put(DatabaseHelper.COLUMN_CARD_DESCRIPTION, card.description)
            put(DatabaseHelper.COLUMN_CARD_SCIENTIFIC_NAME, card.scientificName)
            put(DatabaseHelper.COLUMN_CARD_VERNACULAR_NAME, card.vernacularName)
            put(DatabaseHelper.COLUMN_CARD_REASONING_JSON, gson.toJson(card.reasoning))
            put(DatabaseHelper.COLUMN_CARD_STATS_JSON, gson.toJson(card.stats))
            put(DatabaseHelper.COLUMN_CARD_QUIZ_JSON, gson.toJson(card.quiz))
            put(DatabaseHelper.COLUMN_CARD_TRANSLATIONS_JSON, gson.toJson(card.translations))
            put(DatabaseHelper.COLUMN_CARD_ALLERGENS_JSON, gson.toJson(card.allergens, stringListType))
            put(DatabaseHelper.COLUMN_CARD_INGREDIENTS_JSON, gson.toJson(card.ingredients, stringListType))
        }
        val rows = db.update(
            DatabaseHelper.TABLE_CARDS, values,
            "${DatabaseHelper.COLUMN_CARD_ID} = ?", arrayOf(card.id.toString())
        )
        Log.i(TAG, "[DAO-UPDATE] ${rows} ligne(s) mise(s) à jour pour la carte ID ${card.id}.")
    }

    suspend fun updateIdentification(cardId: Long, specificName: String?, deckName: String?, reasoning: Reasoning, confidence: Float) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        Log.d(TAG, "[DAO-UPDATE-ID] Ordre reçu pour Carte ID $cardId: Nom='$specificName', Deck='$deckName'")
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_CARD_SPECIFIC_NAME, specificName)
            put(DatabaseHelper.COLUMN_CARD_DECK_NAME, deckName)
            put(DatabaseHelper.COLUMN_CARD_REASONING_JSON, gson.toJson(reasoning, reasoningType))
            put(DatabaseHelper.COLUMN_CARD_CONFIDENCE, confidence)
        }
        val rows = db.update(DatabaseHelper.TABLE_CARDS, values, "${DatabaseHelper.COLUMN_CARD_ID} = ?", arrayOf(cardId.toString()))
        Log.i(TAG, "[DAO-UPDATE-ID] ${rows} ligne(s) mise(s) à jour pour la carte ID $cardId. L'opération a-t-elle réussi ? (rows > 0)")
    }

    suspend fun updateDescription(cardId: Long, description: String?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_CARD_DESCRIPTION, description)
        }
        db.update(DatabaseHelper.TABLE_CARDS, values, "${DatabaseHelper.COLUMN_CARD_ID} = ?", arrayOf(cardId.toString()))
    }

    suspend fun updateStats(cardId: Long, stats: CardStats?, allergens: List<String>?, ingredients: List<String>?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_CARD_STATS_JSON, gson.toJson(stats))
            put(DatabaseHelper.COLUMN_CARD_ALLERGENS_JSON, gson.toJson(allergens, stringListType))
            put(DatabaseHelper.COLUMN_CARD_INGREDIENTS_JSON, gson.toJson(ingredients, stringListType))
        }
        db.update(DatabaseHelper.TABLE_CARDS, values, "${DatabaseHelper.COLUMN_CARD_ID} = ?", arrayOf(cardId.toString()))
    }

    suspend fun updateScientificAndVernacularNames(cardId: Long, scientificName: String?, vernacularName: String?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_CARD_SCIENTIFIC_NAME, scientificName)
            put(DatabaseHelper.COLUMN_CARD_VERNACULAR_NAME, vernacularName)
        }
        db.update(DatabaseHelper.TABLE_CARDS, values, "${DatabaseHelper.COLUMN_CARD_ID} = ?", arrayOf(cardId.toString()))
    }

    suspend fun updateQuiz(cardId: Long, quiz: List<QuizQuestion>?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_CARD_QUIZ_JSON, gson.toJson(quiz))
        }
        db.update(DatabaseHelper.TABLE_CARDS, values, "${DatabaseHelper.COLUMN_CARD_ID} = ?", arrayOf(cardId.toString()))
    }

    suspend fun updateTranslations(cardId: Long, translations: Map<String, TranslatedContent>?) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_CARD_TRANSLATIONS_JSON, gson.toJson(translations))
        }
        db.update(DatabaseHelper.TABLE_CARDS, values, "${DatabaseHelper.COLUMN_CARD_ID} = ?", arrayOf(cardId.toString()))
    }

    suspend fun delete(card: KnowledgeCard) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(
            DatabaseHelper.TABLE_CARDS,
            "${DatabaseHelper.COLUMN_CARD_ID} = ?",
            arrayOf(card.id.toString())
        )
    }

    suspend fun nuke() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_CARDS, null, null)
        Log.w("CardDao", "NUKE: La table knowledge_cards a été entièrement vidée.")
    }

    private fun cursorToKnowledgeCard(cursor: Cursor): KnowledgeCard {
        fun getStringOrNull(columnName: String): String? {
            val colIndex = cursor.getColumnIndex(columnName)
            return if (colIndex != -1 && !cursor.isNull(colIndex)) cursor.getString(colIndex) else null
        }
        fun getLongOrNull(columnName: String): Long? {
            val colIndex = cursor.getColumnIndex(columnName)
            return if (colIndex != -1 && !cursor.isNull(colIndex)) cursor.getLong(colIndex) else null
        }

        val specificNameRaw = getStringOrNull(DatabaseHelper.COLUMN_CARD_SPECIFIC_NAME)
        val deckNameRaw = getStringOrNull(DatabaseHelper.COLUMN_CARD_DECK_NAME)
        val cardId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CARD_ID))
        Log.d(TAG, "[DAO-READ] Lecture Carte ID $cardId: Nom='${specificNameRaw}', Deck='${deckNameRaw}'")

        val specificName = specificNameRaw ?: "Erreur de Nom"
        val deckName = deckNameRaw ?: "Erreur de Deck"

        return KnowledgeCard(
            id = cardId,
            specificName = specificName,
            deckName = deckName,
            imagePath = getStringOrNull(DatabaseHelper.COLUMN_CARD_IMAGE_PATH),
            confidence = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CARD_CONFIDENCE)),
            description = getStringOrNull(DatabaseHelper.COLUMN_CARD_DESCRIPTION),
            scientificName = getStringOrNull(DatabaseHelper.COLUMN_CARD_SCIENTIFIC_NAME),
            vernacularName = getStringOrNull(DatabaseHelper.COLUMN_CARD_VERNACULAR_NAME),
            reasoning = gson.fromJson(getStringOrNull(DatabaseHelper.COLUMN_CARD_REASONING_JSON), reasoningType) ?: Reasoning("N/A","N/A"),
            stats = gson.fromJson(getStringOrNull(DatabaseHelper.COLUMN_CARD_STATS_JSON), statsType),
            quiz = gson.fromJson(getStringOrNull(DatabaseHelper.COLUMN_CARD_QUIZ_JSON), quizListType),
            translations = gson.fromJson(getStringOrNull(DatabaseHelper.COLUMN_CARD_TRANSLATIONS_JSON), translationsType),
            allergens = gson.fromJson(getStringOrNull(DatabaseHelper.COLUMN_CARD_ALLERGENS_JSON), stringListType),
            ingredients = gson.fromJson(getStringOrNull(DatabaseHelper.COLUMN_CARD_INGREDIENTS_JSON), stringListType)
        )
    }
    fun getAllCardsFlow(): Flow<List<KnowledgeCard>> {
        return flow {
            emit(getAll())
        }
    }
}