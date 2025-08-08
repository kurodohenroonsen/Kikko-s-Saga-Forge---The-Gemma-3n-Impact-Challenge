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

    // BOURDON'S ULTIMATE FIX: Prompts en dur pour contourner les problèmes de cache des assets.
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

            --- START OF DUEL DATA ---

            [CLASH_QUESTION]:
            "%1${'$'}s"

            [DEVICE_LANGUAGE]:
            "%2${'$'}s"

            [CONTESTANT_1_NAME]:
            "%3${'$'}s"

            [CONTESTANT_1_STATS_JSON]:
            %4${'$'}s

            [CONTESTANT_2_NAME]:
            "%5${'$'}s"

            [CONTESTANT_2_STATS_JSON]:
            %6${'$'}s
            """.trimIndent()
            // Ajoutez ici d'autres prompts si nécessaire
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

    // Les fonctions d'édition, import, export sont temporairement désactivées pour se concentrer sur la stabilité.
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