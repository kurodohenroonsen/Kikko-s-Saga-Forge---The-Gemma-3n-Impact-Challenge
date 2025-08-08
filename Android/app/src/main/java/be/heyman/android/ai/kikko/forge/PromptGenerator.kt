package be.heyman.android.ai.kikko.forge

import be.heyman.android.ai.kikko.model.CardStats
import be.heyman.android.ai.kikko.model.QuizQuestion
import be.heyman.android.ai.kikko.model.Reasoning
import be.heyman.android.ai.kikko.model.TranslatedContent
import be.heyman.android.ai.kikko.prompt.PromptManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.util.Locale

/**
 * BOURDON'S REFORGE (v4.0): Ce générateur est maintenant un HÉRAUT.
 * Il ne crée plus de prompts lui-même, mais récupère les décrets officiels du
 * PromptManager et les formate avec les données spécifiques à la mission.
 * L'ancien code de génération de prompt a été supprimé et remplacé par des
 * appels à String.format.
 */
object PromptGenerator {

    // --- LOGIQUE SPÉCIFIQUE À LA FORGE (NE SONT PAS DES PROMPTS) ---

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
    // --- FIN DE LA LOGIQUE SPÉCIFIQUE ---


    fun generateIdentificationPrompt(swarmReportJson: String): String {
        val rawPrompt = PromptManager.getPrompt("forge_identification")
        return String.format(rawPrompt, swarmReportJson)
    }

    fun generateNarrationDescriptionPrompt(subject: String, deckName: String, locale: Locale): String {
        val rawPrompt = PromptManager.getPrompt("forge_description")
        val languageName = when (locale.language) {
            "fr" -> "French"
            "ja" -> "Japanese"
            else -> "English"
        }
        val lowerSubject = subject.lowercase(locale)
        return String.format(rawPrompt, deckName, lowerSubject, languageName)
    }

    fun generateStatsExtractionPrompt(specificName: String, deckName: String, ocrText: String, description: String): String {
        val statsToFind = deckStats[deckName]
        if (statsToFind == null && deckName != "Food") return "" // Pas de stats pour les decks non-Food/non-biologiques définis
        val statsString = statsToFind?.joinToString(", ") ?: ""
        val lowerSpecificName = specificName.lowercase(Locale.getDefault())

        val allergenContext = if (deckName == "Food") {
            """
            Additionally, you MUST identify all allergens from the `[ALLERGEN_LIST]`.
            [ALLERGEN_LIST]: [${majorAllergensList.joinToString(", ")}]
            Finally, you MUST extract the list of ingredients.
            """
        } else ""

        val exampleOutput = when (deckName) {
            "Food" -> """{"stats": {"Energy (kcal per 100g)": "520", "Protein (g per 100g)": "5.8"}, "allergens": ["Milk", "Soybeans"], "ingredients": ["Wheat flour", "Sugar"]}"""
            "Bird" -> """{"stats": {"Wingspan (cm)": "22.0", "Lifespan (years)": "2.0"}, "scientificName": "...", "vernacularName": "..."}"""
            "Insect" -> """{"stats": {"Average Length (mm)": "15.0", "Lifespan (days)": "30.0"}, "scientificName": "...", "vernacularName": "..."}"""
            "Plant" -> """{"stats": {"Maximum Height (m)": "0.6", "Flowering Period (months)": "3.0"}, "scientificName": "...", "vernacularName": "..."}"""
            else -> "{}"
        }

        val rawPrompt = PromptManager.getPrompt("forge_stats")
        return String.format(rawPrompt, lowerSpecificName, statsString, allergenContext, exampleOutput, ocrText, description)
    }

    fun generateQuizPrompt(subject: String, description: String, stats: CardStats?, locale: Locale): String {
        val rawPrompt = PromptManager.getPrompt("forge_quiz")
        val statsJson = if (stats != null) Gson().toJson(stats.items) else "{}"
        val languageName = when (locale.language) {
            "fr" -> "French"
            "ja" -> "Japanese"
            else -> "English"
        }
        return String.format(rawPrompt, languageName, description, statsJson)
    }

    fun generateFullCardTranslationPrompt(originalContentJson: String, targetLanguageName: String): String {
        val rawPrompt = PromptManager.getPrompt("forge_translation")
        return String.format(rawPrompt, targetLanguageName, originalContentJson)
    }
}