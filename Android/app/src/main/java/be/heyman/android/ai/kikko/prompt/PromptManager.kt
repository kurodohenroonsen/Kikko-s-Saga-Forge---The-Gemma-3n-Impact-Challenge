package be.heyman.android.ai.kikko.prompt

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.MissingFormatArgumentException

object PromptManager {

    private const val TAG = "PromptManagerTrace"
    private val promptsCache = mutableMapOf<String, String>()
    private var isInitialized = false

    private fun getDefaultPrompts(): Map<String, String> {
        return mapOf(
            "forge_identification" to """
            ROLE: You are the AI Queen of the Kikko Hive, an expert in multimodal synthesis. Your reasoning must be flawless and strictly follow the evidence provided.
            CONTEXT: You are receiving a JSON **array** of intelligence reports from your Specialist Bees. Each object in the array corresponds to one visual clue (image). The `ocr_results` are the most reliable source of truth when available.
            TASK: **Synthesize ALL reports in the array** to determine a **single, unified subject identity**.
            1.  **Evidence Prioritization (CRITICAL):**
                -   **IF** the `ocr_results.full_text` from any report contains a specific product name or species: This OCR data is your **PRIMARY EVIDENCE**.
                -   **ELSE**: Use the highest-confidence labels from other classifiers across all reports as your PRIMARY EVIDENCE.
            2.  **Final Ruling:** Synthesize all information to determine one final `specificName` and one final `deckName`.
            CRITICAL RULES:
            -   You MUST IGNORE low-confidence labels that contradict the primary evidence.
            -   If the evidence is inconclusive, respond with `{"error": "Inconclusive analysis"}`.
            OUTPUT FORMAT: Your response MUST be a **single, valid JSON object**. Do NOT add any other text or markdown.
            **IMPORTANT**: Never use double quotes `"` inside a JSON string value. Use single quotes `'` or other characters instead.
            {
              "reasoning": {
                "visualAnalysis": "Your brief analysis synthesizing what is visible across ALL images.",
                "evidenceCorrelation": "Your analysis of the JSON reports, explaining how you combined evidence from all reports to reach a single conclusion."
              },
             "specificName": "The most specific and functional name of the subject.",
             "deckName": "based on the specificName, select the single most appropriate deck name from this short list: [`Bird`, `Insect`, `Plant`, `Food`] ",
              "confidence": 0.95
            }
            --- START OF DATA ---
            [INTELLIGENCE REPORTS]:
            %1${'$'}s
            """.trimIndent(),

            "forge_description" to """
            ROLE: You are a creative and engaging storyteller.
            TASK: Write a descriptive paragraph about "%1${'$'}s - %2${'$'}s". The text must be captivating and easy to read aloud (for Text-to-Speech).
            Crucially, you MUST include:
            1. The origins of the subject.
            2. Exactly four (4) interesting or fun facts seamlessly integrated into the text.
            The entire response must be a single paragraph of plain text, without markdown, lists, or bullet points.
            The response must be in %3${'$'}s.
            """.trimIndent(),

            "forge_stats" to """
            ROLE: You are a meticulous and resourceful Data Extraction AI. You never fail to find a value.
            CONTEXT: You are analyzing "%1${'$'}s". You have two sources of information: raw OCR data which is the primary source of truth, and a general narrative description which is secondary.
            TASK: Your mission is to find the values for the required stats: [%2${'$'}s]. %3${'$'}s
            Follow this strict priority order for your search:
            1.  **PRIORITY 1 (OCR)**: Scrutinize the `[RAW_OCR_DATA]` for precise figures.
            2.  **PRIORITY 2 (NARRATIVE)**: If a stat is not found in the OCR, search for it within the `[NARRATIVE_DESCRIPTION]`.
            3.  **PRIORITY 3 (INFERENCE)**: If a stat is **still not found**, it is **imperative** that you provide a typical **average value** for "%1${'$'}s".
            OUTPUT FORMAT: Your response MUST be a single, valid JSON object. Do not add any other text or markdown.
            **IMPORTANT**: Never use double quotes `"` inside a JSON string value. Use single quotes `'` instead.
            Example Output: %4${'$'}s
            --- START OF DATA ---
            [RAW_OCR_DATA]: "%5${'$'}s"
            [NARRATIVE_DESCRIPTION]: "%6${'$'}s"
            """.trimIndent(),

            "forge_quiz" to """
            ROLE: You are a creative but strictly factual Quiz Master AI.
            CONTEXT: You have a `[DESCRIPTION]` and a `[STATS_DATA]` JSON object. This is your ONLY source of truth.
            TASK: Create exactly 4 multiple-choice questions with 4 possible answers each from BOTH the description and the stats data. At least one question must be about a specific numerical value.
            CRITICAL RULE: DO NOT use any external knowledge. All questions and answers must be directly verifiable from the provided context.
            OUTPUT FORMAT: Your response MUST be a single JSON array, with no other explanatory text. The content of the quiz must be in %1${'$'}s.
            Format: `[{"q": "...", "o": ["...", "...", "...", "..."], "c": correct_index, "explanation": "..."}]`
            **IMPORTANT**: Never use double quotes `"` inside a JSON string value (like in "q" or "o"). Use single quotes `'` instead.
            --- START OF DATA ---
            [DESCRIPTION]: "%2${'$'}s"
            [STATS_DATA]: %3${'$'}s
            """.trimIndent(),

            "forge_translation" to """
            ROLE: You are an expert linguist and translator for the Kikko Hive.
            CONTEXT: You have the JSON content of a Knowledge Card.
            TASK: Translate ALL text content within the JSON to %1${'$'}s.
            - Maintain the EXACT JSON structure. Do NOT add or remove fields.
            - Only translate the string values. Do NOT translate field names.
            - DO NOT include any comments or extra text outside the JSON.
            **IMPORTANT**: Never use double quotes `"` inside a JSON string value. Use single quotes `'` instead.
            Original JSON content to translate:
            %2${'$'}s
            """.trimIndent(),

            "clash_verdict" to """
            ROLE: You are a versatile AI Judge for the Saga Arena.
            CONTEXT: You must judge a duel based on the `[CLASH_QUESTION]`. You have data for two contestants. Your verdict MUST be based solely on the provided data.
            TASK: You must provide two distinct outputs. Both generated text fields MUST be in the requested `[DEVICE_LANGUAGE]`.
            1.  **Reasoning Field**: Act as a meticulous, logical Judge. Provide a concise, technical justification for your verdict in 1-2 factual sentences.
            2.  **TTS Script Field**: Act as a fun, enthusiastic game show announcer (the Bourdon). Announce the winner and explain the main reason for the victory in simple, exciting terms for a child.
            OUTPUT FORMAT: Your response MUST be a single, valid JSON object and NOTHING ELSE.
            - The `reasoning` key: your concise, factual justification in the `[DEVICE_LANGUAGE]`.
            - The `tts_script` key: your short, exciting announcement in the `[DEVICE_LANGUAGE]`.
            - The `winner` key: CRITICAL. It MUST contain ONLY ONE of these exact three strings: "player1", "player2", or "tie".
            **IMPORTANT**: Never use double quotes `"` inside a JSON string value. Use single quotes `'` instead.
            --- START OF DUEL DATA ---
            [CLASH_QUESTION]: "%1${'$'}s"
            [DEVICE_LANGUAGE]: "%2${'$'}s"
            [CONTESTANT_1_NAME]: "%3${'$'}s"
            [CONTESTANT_1_STATS_JSON]: %4${'$'}s
            [CONTESTANT_2_NAME]: "%5${'$'}s"
            [CONTESTANT_2_STATS_JSON]: %6${'$'}s
            """.trimIndent(),

            // BOURDON'S REFORGE: Le nouveau décret pour un Arbitre plus intelligent et robuste.
            "forge_judgment_arbiter" to """
            ROLE: You are the impartial AI Arbiter of the Forge. Your role is to synthesize and rank, not to choose a single winner.
            CONTEXT: A competition was held among several AI Queens to determine the value for the property '%1${'$'}s'. You are provided with a JSON array of their proposals. Each proposal has a unique `id`.
            TASK:
            1. Analyze all proposals in the `[PROPOSALS_JSON]` array.
            2. Extract the main value (e.g., the `specificName`, the `description` text) from the `rawResponse` of EACH proposal.
            3. Group identical or near-identical values together and count the "votes" for each unique value.
            4. Select up to the top 3 most popular, distinct values.
            5. For each of these top values, provide a brief synthesis of the reasoning presented by the AIs that proposed it.
            OUTPUT FORMAT: Your response MUST be a single, valid JSON object containing a `rankedProposals` array.
            **IMPORTANT**: Never use double quotes `"` inside a JSON string value. Use single quotes `'` instead.
            {
              "arbiterReasoning": "A very brief, one-sentence summary of the competition's outcome.",
              "rankedProposals": [
                {
                  "value": "The first most popular value (e.g., 'Dandelion').",
                  "voteCount": 3,
                  "reasoningSummary": "A brief summary of why the AIs chose this value.",
                  "sourceTaskId": "The `id` of the FIRST proposal in the input that suggested this value."
                },
                {
                  "value": "The second most popular value (e.g., 'Taraxacum officinale').",
                  "voteCount": 2,
                  "reasoningSummary": "A brief summary for this second choice.",
                  "sourceTaskId": "The `id` of the FIRST proposal for this second value."
                }
              ]
            }
            --- START OF EVIDENCE ---
            [PROPOSALS_JSON]:
            %2${'$'}s
            """.trimIndent()
        )
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            promptsCache.clear()
            promptsCache.putAll(getDefaultPrompts())
            Log.i(TAG, "${promptsCache.size} prompts chargés en mémoire depuis la source en dur.")
            isInitialized = true
        }
    }

    fun getPrompt(key: String): String {
        val rawPrompt = promptsCache[key]
        if (rawPrompt == null) {
            Log.e(TAG, "!!! ÉCHEC: Clé de prompt introuvable: '$key'")
            return "ERREUR: Prompt '$key' non trouvé."
        }
        return rawPrompt
    }

    fun getAllPrompts(): Map<String, String> = promptsCache.toMap()

    suspend fun savePrompts(context: Context, updatedPrompts: Map<String, String>) {
        Log.w(TAG, "La sauvegarde des prompts est désactivée dans cette version de débogage.")
    }

    suspend fun restoreDefaults(context: Context) {
        synchronized(this) {
            promptsCache.clear()
            promptsCache.putAll(getDefaultPrompts())
        }
        Log.i(TAG, "Prompts restaurés aux valeurs par défaut en mémoire.")
    }

    suspend fun exportPrompts(context: Context): Uri? {
        Log.w(TAG, "L'export des prompts est désactivé dans cette version de débogage.")
        return null
    }

    suspend fun importPrompts(context: Context, uri: Uri): Boolean {
        Log.w(TAG, "L'import des prompts est désactivé dans cette version de débogage.")
        return false
    }
}