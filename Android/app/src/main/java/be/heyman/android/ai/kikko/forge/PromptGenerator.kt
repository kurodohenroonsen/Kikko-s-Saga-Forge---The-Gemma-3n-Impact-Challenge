package be.heyman.android.ai.kikko.forge

import be.heyman.android.ai.kikko.model.CardStats
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.Locale

/**
 * Un singleton (objet) pour générer les prompts spécifiques au flux de travail de la Forge.
 * BOURDON'S REFORGE (v3.0): Les prompts sont maintenant spécifiques à chaque deck pour une
 * extraction de données ciblée et efficace, adaptée aux exigences du concours.
 *
 * NOTE: Les prompts liés au Clash ne devraient pas être ici car ils sont dupliqués dans
 * `clash/helpers/ClashPromptGenerator.kt`. Je me suis concentré sur la logique de Forge.
 */
object PromptGenerator {

    // Stats attendues par deck pour le prompt de génération de stats.
    private val deckStats = mapOf(
        "Bird" to listOf("Wingspan (cm)", "Average Weight (g)", "Lifespan (years)", "Clutch Size (eggs)"),
        "Insect" to listOf("Average Length (mm)", "Lifespan (days)", "Number of Legs", "Flying Speed (km/h)"),
        "Plant" to listOf("Maximum Height (m)", "Flowering Period (months)", "Lifespan (years)", "Minimum Temperature (°C)"),
        "Food" to listOf("Energy (kcal per 100g)", "Protein (g per 100g)")
    )

    // Liste des allergènes majeurs pour le prompt de génération de stats.
    private val majorAllergensList = listOf(
        "Cereals containing gluten", "Crustaceans", "Eggs", "Fish", "Peanuts",
        "Soybeans", "Milk", "Nuts", "Celery", "Mustard", "Sesame seeds",
        "Sulphur dioxide and sulphites", "Lupin", "Molluscs"
    )

    /**
     * Génère le prompt pour la tâche d'identification initiale ou de raffinement.
     * @param swarmReportJson Le rapport JSON complet généré par les Abeilles Spécialistes (ML Kit).
     * @return Le prompt complet à envoyer à la Reine IA.
     */
    fun generateIdentificationPrompt(swarmReportJson: String): String {
        val validDecks = "`Bird`, `Insect`, `Plant`, `Food`"

        return """
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
         "deckName": "based on the specificName, select the single most appropriate deck name from this short list: [$validDecks] ",
          "confidence": 0.95
        }

        --- START OF DATA ---
        [INTELLIGENCE REPORTS]:
        $swarmReportJson
        """.trimIndent()
    }

    /**
     * Génère le prompt pour la description narrative d'une carte.
     * @param subject Le nom spécifique du sujet (ex: "Dandelion").
     * @param deckName Le nom du deck (ex: "Plant").
     * @param locale La locale pour la langue de la narration.
     * @return Le prompt complet.
     */
    fun generateNarrationDescriptionPrompt(subject: String, deckName: String, locale: Locale): String {
        val languageName = when (locale.language) {
            "fr" -> "French"
            "ja" -> "Japanese"
            else -> "English"
        }
        val lowerSubject = subject.lowercase(locale)
        return """
        ROLE: You are a creative and engaging storyteller.

        TASK: Write a descriptive paragraph about "$deckName - $lowerSubject". The text must be captivating and easy to read aloud (for Text-to-Speech).
        Crucially, you MUST include:
        1. The origins of the subject.
        2. Exactly four (4) interesting or fun facts seamlessly integrated into the text.

        The entire response must be a single paragraph of plain text, without markdown, lists, or bullet points.
        The response must be in $languageName.
        """.trimIndent()
    }

    /**
     * Génère le prompt pour l'extraction des statistiques et autres propriétés spécifiques à un deck.
     * @param specificName Le nom spécifique du sujet (ex: "Honey Bee").
     * @param deckName Le nom du deck (ex: "Insect").
     * @param ocrText Le texte OCR agrégé pour des données précises.
     * @param description La description narrative générée précédemment.
     * @return Le prompt complet.
     */
    fun generateStatsExtractionPrompt(specificName: String, deckName: String, ocrText: String, description: String): String {
        val statsToFind = deckStats[deckName]
        if (statsToFind == null && deckName != "Food") return "" // Pas de stats pour les decks non-Food/non-biologiques définis
        val statsString = statsToFind?.joinToString(", ") ?: ""
        val lowerSpecificName = specificName.lowercase(Locale.getDefault())

        val allergenContext = if (deckName == "Food") {
            """
            Additionally, you MUST identify all allergens. Scrutinize the `[RAW_OCR_DATA]` and `[NARRATIVE_DESCRIPTION]` for any mention of allergens from the `[ALLERGEN_LIST]`.

            [ALLERGEN_LIST]:
            [${majorAllergensList.joinToString(", ")}]
            """
        } else {
            ""
        }

        val ingredientsContext = if (deckName == "Food") {
            """
            Finally, you MUST extract the list of ingredients. This list should be extracted primarily from the `[RAW_OCR_DATA]`.
            """
        } else {
            ""
        }

        val exampleOutput = when (deckName) {
            "Food" -> """
                {
                  "stats": {
                    "Energy (kcal per 100g)": "520",
                    "Protein (g per 100g)": "5.8"
                  },
                  "allergens": ["Milk", "Soybeans", "Cereals containing gluten"],
                  "ingredients": ["Wheat flour", "Sugar", "Palm oil", "Cocoa butter", "Cocoa mass", "Glucose syrup"]
                }
            """
            "Bird" -> """
                {
                  "stats": {
                    "Wingspan (cm)": "22.0",
                    "Average Weight (g)": "19.0",
                    "Lifespan (years)": "2.0",
                    "Clutch Size (eggs)": "6.0"
                  }
                }
                """
            "Insect" -> """
                {
                  "stats": {
                    "Average Length (mm)": "15.0",
                    "Lifespan (days)": "30.0",
                    "Number of Legs": "6.0",
                    "Flying Speed (km/h)": "24.0"
                  }
                }
                """
            "Plant" -> """
                {
                  "stats": {
                    "Maximum Height (m)": "0.6",
                    "Flowering Period (months)": "3.0",
                    "Lifespan (years)": "10.0",
                    "Minimum Temperature (°C)": "-15.0"
                  }
                }
                """
            else -> "{}"
        }

        return """
        ROLE: You are a meticulous and resourceful Data Extraction AI. You never fail to find a value.

        CONTEXT: You are analyzing "$lowerSpecificName". You have two sources of information: raw OCR data which is the primary source of truth, and a general narrative description which is secondary.

        TASK: Your mission is to find the values for the following required stats: $statsString.
        $allergenContext
        $ingredientsContext
        
        Follow this strict priority order for your search:
        1.  **PRIORITY 1 (OCR)**: Scrutinize the `[RAW_OCR_DATA]` for precise figures. This is your most reliable source for technical data.
        2.  **PRIORITY 2 (NARRATIVE)**: If a stat is not found in the OCR, search for it within the `[NARRATIVE_DESCRIPTION]`.
        3.  **PRIORITY 3 (INFERENCE)**: If a stat is **still not found**, it is **imperative** that you provide a typical **average value** based on your vast general knowledge about the subject "$lowerSpecificName". You must provide a reasonable estimate. Returning `null` is an option of last resort, only to be used if the concept of an average is nonsensical for the stat in question.

        OUTPUT FORMAT: Your response MUST be a single, valid JSON object with top-level keys: "stats", and if applicable for the "Food" deck, "allergens" and "ingredients". The "allergens" and "ingredients" values MUST be a JSON list of strings. If none are found, return an empty list `[]`. Do not add any other text or markdown code fences.
        **IMPORTANT**: Never use double quotes `"` inside a JSON string value. Use single quotes `'` or other characters instead.

        Example Output for a "$deckName" deck:
        $exampleOutput

        --- START OF DATA ---

        [RAW_OCR_DATA]:
        "$ocrText"

        [NARRATIVE_DESCRIPTION]:
        "$description"
        """.trimIndent()
    }

    /**
     * Génère le prompt pour la création du quiz.
     * @param subject Le nom du sujet pour le quiz.
     * @param description La description narrative.
     * @param stats Les statistiques de la carte.
     * @param locale La locale pour la langue du quiz.
     * @return Le prompt complet.
     */
    fun generateQuizPrompt(subject: String, description: String, stats: CardStats?, locale: Locale): String {
        val statsJson = if (stats != null) Gson().toJson(stats.items) else "{}"
        val languageName = when (locale.language) {
            "fr" -> "French"
            "ja" -> "Japanese"
            else -> "English"
        }
        return """
        ROLE: You are a creative but strictly factual Quiz Master AI.

        CONTEXT: You have a `[DESCRIPTION]` and a `[STATS_DATA]` JSON object containing technical data about a subject. This is your ONLY source of truth.

        TASK: Create exactly 4 multiple-choice questions with 4 possible answers each. Your questions MUST test a mix of information from BOTH the description and the stats data. At least one question must be about a specific numerical value from the stats.

        CRITICAL RULE: DO NOT use any external knowledge. All questions and answers must be directly verifiable from the provided context.

        OUTPUT FORMAT: Your response MUST be a single JSON array, with no other explanatory text. The content of the quiz (questions and answers) must be in $languageName.
        Format: `[{"q": "...", "o": ["...", "...", "...", "..."], "c": correct_index, "explanation": "..."}]`
        **IMPORTANT**: Never use double quotes `"` inside a JSON string value (like in the question "q" or options "o"). Use single quotes `'` or other characters instead.

        --- START OF DATA ---

        [DESCRIPTION]:
        "$description"

        [STATS_DATA]:
        $statsJson
        """.trimIndent()
    }

    /**
     * Génère le prompt pour la traduction complète du contenu d'une carte.
     * @param originalContentJson Le contenu original de la carte (description, raisonnement, quiz) en JSON.
     * @param targetLanguageName Le nom de la langue cible (ex: "French", "Japanese").
     * @return Le prompt complet.
     */
    fun generateFullCardTranslationPrompt(originalContentJson: String, targetLanguageName: String): String {
        return """
        ROLE: You are an expert linguist and translator for the Kikko Hive.

        CONTEXT: You have the JSON content of a Knowledge Card, which includes:
        - `description`: a narrative paragraph.
        - `reasoning`: an object with `visualAnalysis` and `evidenceCorrelation` fields.
        - `quiz`: a list of `QuizQuestion` objects (each with `q`, `o` (options), `c` (correct index), and `explanation`).

        TASK: Translate ALL text content within the JSON to $targetLanguageName.
        - The translation must be natural and fluent for a native speaker.
        - Maintain the EXACT JSON structure. Do NOT add or remove fields.
        - Only translate the string values. Do NOT translate field names (e.g., 'description', 'q', 'o', 'c').
        - Do NOT include any comments or extra text outside the JSON.

        Original JSON content to translate:
        $originalContentJson
        """.trimIndent()
    }
}