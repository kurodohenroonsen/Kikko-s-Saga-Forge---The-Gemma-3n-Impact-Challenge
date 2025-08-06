package be.heyman.android.ai.kikko.forge

import java.util.Locale

/**
 * Un singleton (objet) pour générer les prompts spécifiques au flux de travail de la Forge.
 * BOURDON'S REFORGE (v5.1): Refonte majeure pour supporter des tâches de forge granulaires
 * par propriété, avec gestion des dépendances (ingrédients -> allergènes).
 */
object ForgePromptGenerator {

    /**
     * Génère le prompt pour la tâche d'identification initiale.
     */
    fun generateIdentificationTournamentPrompt(swarmReportJson: String): String {
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
     * NOUVEAU (v5.1): Générateur de prompt principal qui délègue à des sous-générateurs
     * en fonction de la propriété demandée. C'est le nouveau point d'entrée unique.
     */
    fun generatePropertyForgePrompt(
        propertyName: String,
        deckName: String,
        specificName: String,
        swarmReportJson: String,
        existingDescription: String?,
        dependencyDataJson: String? = null
    ): String {

        // BOURDON'S REFORGE V10.0: Le prompt de description est maintenant multimodal et beaucoup plus riche.
        if (propertyName == "description") {
            return """
            ROLE: You are a creative and engaging encyclopedist for the Kikko Hive. You are a master at describing subjects based on visual and textual evidence.

            CONTEXT: You are tasked with writing a long, detailed, and captivating description for the subject "$specificName" from the "$deckName" deck. You have been provided with IMAGES of the subject and a JSON report from specialist analysis bees (`swarmReportJson`).

            TASK: Write a rich, multi-paragraph description.
            1.  **Analyze the IMAGES**: Describe the visual characteristics (colors, shapes, textures) of the subject as seen in the images.
            2.  **Incorporate Facts**: Weave in interesting facts and information, using the `swarmReportJson` as a starting point, but also drawing from your general knowledge.
            3.  **Tone**: The tone should be encyclopedic but accessible and engaging for a curious learner.

            OUTPUT FORMAT: Your response MUST be a single, valid JSON object with ONLY the "description" key. The value should be your detailed description. Do not add any other text or markdown.

            --- START OF DATA ---
            [SWARM REPORT JSON]:
            $swarmReportJson
            --- END OF DATA ---

            --- EXPECTED JSON STRUCTURE ---
            {
              "description": "Your long, detailed, and engaging multi-paragraph description of the subject, based on the images and data provided."
            }
            """.trimIndent()
        }

        // Le reste de la logique pour les autres propriétés reste inchangé.
        val baseInstruction = """
        ROLE: You are a meticulous and factual AI Data Extractor for the Kikko Hive. Your focus is absolute.
        CONTEXT: You are analyzing the subject "$specificName" from the "$deckName" deck. You have the initial visual analysis from the specialist bees (`swarmReportJson`) and a narrative description. The OCR text within the swarm report is the highest priority source for technical data.
        TASK: Extract or generate ONLY the value for the property `"$propertyName"`.
        OUTPUT FORMAT: Your response MUST be a single, valid JSON object with ONLY the key `"$propertyName"`. Do not add any other text or markdown. Use 'null' for any value you cannot determine.
        --- START OF DATA ---
        [SWARM REPORT JSON]:
        $swarmReportJson
        ${if (existingDescription != null) "\n[NATIVE DESCRIPTION]:\n$existingDescription" else ""}
        ${if (dependencyDataJson != null) "\n[DEPENDENCY DATA (PREVIOUSLY FORGED)]:\n$dependencyDataJson" else ""}
        --- END OF DATA ---
        """

        val specificPrompt = when (propertyName) {
            "ingredients" -> """
                 --- TASK REFINEMENT ---
                 Carefully analyze the [SWARM REPORT JSON], especially any OCR text, to list all ingredients.
                 --- EXPECTED JSON STRUCTURE ---
                 {
                   "ingredients": ["A list of all identified ingredients as strings."]
                 }
            """
            "allergens" -> """
                 --- TASK REFINEMENT ---
                 Analyze the provided [DEPENDENCY DATA (PREVIOUSLY FORGED)] which contains the list of ingredients. Identify any major allergens from that list.
                 --- EXPECTED JSON STRUCTURE ---
                 {
                   "allergens": ["A list of major allergens found in the ingredients."]
                 }
            """
            "stats.energy" -> """
                 --- EXPECTED JSON STRUCTURE ---
                 {
                   "stats.energy": "The energy value in kcal per 100g as a string, e.g., '520'."
                 }
            """
            "biological.scientificName" -> """
                 --- EXPECTED JSON STRUCTURE ---
                 {
                   "biological.scientificName": "The scientific (Latin) name of the species."
                 }
            """
            "biological.vernacularName" -> """
                 --- EXPECTED JSON STRUCTURE ---
                 {
                   "biological.vernacularName": "The most common vernacular name for the species."
                 }
            """
            "stats.floweringPeriod" -> """
                 --- EXPECTED JSON STRUCTURE ---
                 {
                   "stats.floweringPeriod": "The typical flowering months, e.g., 'June to August'."
                 }
            """
            "stats.diet" -> """
                 --- EXPECTED JSON STRUCTURE ---
                 {
                   "stats.diet": "The primary diet of the insect, e.g., 'Herbivore'."
                 }
            """
            "stats.wingspan" -> """
                 --- EXPECTED JSON STRUCTURE ---
                 {
                   "stats.wingspan": "The average wingspan in cm as a string, e.g., '22.5'."
                 }
            """
            else -> ""
        }

        return baseInstruction + specificPrompt
    }
}