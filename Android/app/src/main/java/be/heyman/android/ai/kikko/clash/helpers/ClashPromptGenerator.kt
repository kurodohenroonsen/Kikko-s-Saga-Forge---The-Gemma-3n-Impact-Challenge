// app/src/main/java/be/heyman/android/ai/kikko/clash/helpers/ClashPromptGenerator.kt

package be.heyman.android.ai.kikko.clash.helpers

import android.content.Context
import be.heyman.android.ai.kikko.model.KnowledgeCard
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

        return """
        ROLE: You are a versatile AI Judge for the Saga Arena.

        CONTEXT: You must judge a duel based on the `[CLASH_QUESTION]`. You have data for two contestants. Your verdict MUST be based solely on the provided data.

        TASK: You must provide two distinct outputs. Both generated text fields MUST be in the requested `[DEVICE_LANGUAGE]`.
        1.  **Reasoning Field**: Act as a meticulous, logical Judge. Provide a concise, technical justification for your verdict in 1-2 factual sentences. This part is for on-screen display.
        2.  **TTS Script Field**: Act as a fun, enthusiastic game show announcer (the Bourdon). Announce the winner and explain the main reason for the victory in simple, exciting terms for a child. This text is for being read aloud.

        OUTPUT FORMAT: Your response MUST be a single, valid JSON object and NOTHING ELSE. Do NOT add markdown code fences like ```json.
        - The `reasoning` key: your concise, factual justification in the `[DEVICE_LANGUAGE]`.
        - The `tts_script` key: your short, exciting announcement in the `[DEVICE_LANGUAGE]`.
        - The `winner` key: CRITICAL. It MUST contain ONLY ONE of these exact three strings: "player1", "player2", or "tie". DO NOT use the name of the contestants or any other text.

        Example of a PERFECT output (if [DEVICE_LANGUAGE] was "English"):
        {
          "reasoning": "The raspberry bush wins. Its perennial root system is superior for preventing soil erosion compared to the annual root system of a tomato plant.",
          "tts_script": "And the winner is... the mighty raspberry bush! Its super-strong roots are way better at holding the soil together! What a grounded victory!",
          "winner": "player1"
        }

        --- START OF DUEL DATA ---

        [CLASH_QUESTION]:
        "$question"

        [DEVICE_LANGUAGE]:
        "$deviceLanguage"

        [CONTESTANT_1_NAME]:
        "${card1.specificName}"
        
        [CONTESTANT_1_STATS_JSON]:
        $player1Json

        [CONTESTANT_2_NAME]:
        "${card2.specificName}"

        [CONTESTANT_2_STATS_JSON]:
        $player2Json
        """.trimIndent()
    }
}