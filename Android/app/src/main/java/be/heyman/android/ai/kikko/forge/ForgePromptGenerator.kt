package be.heyman.android.ai.kikko.forge

import be.heyman.android.ai.kikko.prompt.PromptManager
import java.util.Locale

object ForgePromptGenerator {

    /**
     * BOURDON'S FINAL REFACTOR: Le générateur récupère le prompt brut (maintenant en dur
     * dans le PromptManager) et effectue lui-même le formatage.
     */
    fun generateIdentificationTournamentPrompt(swarmReportJson: String): String {
        val rawPrompt = PromptManager.getPrompt("forge_identification")
        // Note: Le '$' dans %1$s est crucial pour éviter les ambiguïtés de formatage.
        return String.format(rawPrompt, swarmReportJson)
    }

    /**
     * BOURDON'S FINAL REFACTOR: La logique de formatage est de retour dans le générateur.
     */
    fun generatePropertyForgePrompt(
        propertyName: String,
        deckName: String,
        specificName: String,
        swarmReportJson: String,
        existingDescription: String?,
        dependencyDataJson: String? = null
    ): String {

        if (propertyName == "description") {
            val rawPrompt = PromptManager.getPrompt("forge_description_multimodal")
            return String.format(
                rawPrompt,
                specificName,
                deckName,
                swarmReportJson
            )
        }

        val rawPrompt = PromptManager.getPrompt("forge_property_base")
        val formattedDescription = if (existingDescription != null) "\n[NATIVE DESCRIPTION]:\n$existingDescription" else ""
        val formattedDependency = if (dependencyDataJson != null) "\n[DEPENDENCY DATA (PREVIOUSLY FORGED)]:\n$dependencyDataJson" else ""

        return String.format(
            rawPrompt,
            deckName,
            specificName,
            propertyName,
            swarmReportJson,
            formattedDescription,
            formattedDependency
        )
    }
}