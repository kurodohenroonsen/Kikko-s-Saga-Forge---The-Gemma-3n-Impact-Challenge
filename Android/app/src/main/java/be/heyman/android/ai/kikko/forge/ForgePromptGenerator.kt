package be.heyman.android.ai.kikko.forge

import be.heyman.android.ai.kikko.model.AnalysisResult
import be.heyman.android.ai.kikko.prompt.PromptManager
import com.google.gson.Gson
import java.util.Locale

object ForgePromptGenerator {

    // BOURDON'S ADDITION: Un GSON local pour la sérialisation des propositions.
    private val gson = Gson()

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
            // NOTE: Ceci est un prompt qui a été abandonné, mais je le garde pour la compatibilité.
            // Le système de forge actuel n'utilise plus ce chemin.
            val rawPrompt = PromptManager.getPrompt("forge_description_multimodal")
            return String.format(
                rawPrompt,
                specificName,
                deckName,
                swarmReportJson
            )
        }

        // NOTE: Ce prompt est également obsolète et n'est plus appelé par le worker monolithique.
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

    /**
     * BOURDON'S ADDITION: La nouvelle méthode pour forger le prompt de l'Arbitre.
     * Elle prend les résultats d'un tournoi, les sérialise en JSON et les injecte dans le
     * prompt 'forge_judgment_arbiter'.
     *
     * @param propertyName Le nom de la propriété jugée (ex: "description").
     * @param proposals La liste des tâches 'AnalysisResult' terminées à soumettre à l'Arbitre.
     * @return Le prompt complet et formaté pour l'inférence de l'Arbitre.
     */
    fun generateJudgmentPrompt(propertyName: String, proposals: List<AnalysisResult>): String {
        val rawPrompt = PromptManager.getPrompt("forge_judgment_arbiter")
        val proposalsJson = gson.toJson(proposals)
        return String.format(rawPrompt, propertyName, proposalsJson)
    }
}