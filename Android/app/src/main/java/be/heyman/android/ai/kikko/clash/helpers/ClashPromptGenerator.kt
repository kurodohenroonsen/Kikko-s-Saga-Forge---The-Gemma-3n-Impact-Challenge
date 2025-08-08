package be.heyman.android.ai.kikko.clash.helpers

import android.content.Context
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.prompt.PromptManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.Locale

data class LocalizedQuestion(val en: String, val fr: String, val ja: String)

object ClashPromptGenerator {

    private var clashQuestionBank: Map<String, List<LocalizedQuestion>> = emptyMap()
    private var isQuestionBankLoaded = false

    fun loadClashQuestions(context: Context) {
        if (isQuestionBankLoaded) return

        try {
            val jsonString = context.assets.open("clash_questions_i18n.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<Map<String, List<LocalizedQuestion>>>() {}.type
            clashQuestionBank = Gson().fromJson(jsonString, type)
            isQuestionBankLoaded = true
        } catch (ioException: IOException) {
            ioException.printStackTrace()
        }
    }

    fun getRandomClashQuestionSet(deckName: String): LocalizedQuestion? {
        if (!isQuestionBankLoaded) return null
        return clashQuestionBank[deckName]?.randomOrNull()
    }

    fun getLocalizedQuestion(question: LocalizedQuestion): String {
        return when (Locale.getDefault().language) {
            "fr" -> question.fr
            "ja" -> question.ja
            else -> question.en
        }
    }

    fun generateClashVerdictPrompt(question: String, card1: KnowledgeCard, card2: KnowledgeCard): String {
        val player1Json = Gson().toJson(card1.stats?.items ?: emptyMap<String, String>())
        val player2Json = Gson().toJson(card2.stats?.items ?: emptyMap<String, String>())

        val deviceLanguage = when (Locale.getDefault().language) {
            "fr" -> "French"
            "ja" -> "Japanese"
            else -> "English"
        }

        // BOURDON'S REFACTOR: Le prompt est maintenant récupéré dynamiquement.
        return PromptManager.getPrompt(
            "clash_verdict",
            question,
            deviceLanguage,
            card1.specificName,
            player1Json,
            card2.specificName,
            player2Json
        )
    }
}