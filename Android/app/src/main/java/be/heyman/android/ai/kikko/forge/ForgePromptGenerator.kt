package be.heyman.android.ai.kikko.forge

import be.heyman.android.ai.kikko.prompt.PromptManager

object ForgePromptGenerator {

    /**
     * BOURDON'S REFACTOR: Le prompt est maintenant récupéré dynamiquement depuis le PromptManager.
     * La logique de formatage reste, mais le contenu du prompt est maintenant externe.
     */
    fun generateIdentificationTournamentPrompt(swarmReportJson: String): String {
        return PromptManager.getPrompt("forge_identification", swarmReportJson)
    }

    /**
     * BOURDON'S REFACTOR: Le générateur principal utilise maintenant le PromptManager.
     * Le prompt multimodal pour la description est un cas spécial, et le prompt de base
     * gère toutes les autres extractions de propriétés.
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
            return PromptManager.getPrompt(
                "forge_description_multimodal",
                specificName,
                deckName,
                swarmReportJson
            )
        }

        val formattedDescription = if (existingDescription != null) "\n[NATIVE DESCRIPTION]:\n$existingDescription" else ""
        val formattedDependency = if (dependencyDataJson != null) "\n[DEPENDENCY DATA (PREVIOUSLY FORGED)]:\n$dependencyDataJson" else ""

        return PromptManager.getPrompt(
            "forge_property_base",
            deckName,
            specificName,
            propertyName,
            swarmReportJson,
            formattedDescription,
            formattedDependency
        )
    }
}