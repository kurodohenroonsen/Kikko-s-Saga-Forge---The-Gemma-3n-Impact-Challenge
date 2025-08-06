package be.heyman.android.ai.kikko.forge

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import be.heyman.android.ai.kikko.model.IdentificationResult

/**
 * Un analyseur JSON intelligent et tolérant aux pannes, conçu pour interpréter
 * la sortie parfois imparfaite des LLMs en streaming.
 */
class StreamingJsonParser {

    private var buffer = ""
    private val gson = Gson()

    /**
     * Contient le résultat partiel ou complet d'une analyse JSON en streaming.
     */
    data class ParsedResult(
        val specificName: String = "",
        val deckName: String = "",
        val reasoning: Map<String, String> = emptyMap(),
        val confidence: Float = 0.0f,
        val isComplete: Boolean = false
    )

    fun process(chunk: String): ParsedResult {
        buffer += chunk

        // Tentative 1: Essayer de parser le JSON le plus complet possible.
        val firstBrace = buffer.indexOf('{')
        val lastBrace = buffer.lastIndexOf('}')

        if (firstBrace != -1 && lastBrace > firstBrace) {
            val potentialJson = buffer.substring(firstBrace, lastBrace + 1)
            try {
                // Essai avec Gson, le plus strict
                val result: IdentificationResult = gson.fromJson(potentialJson, IdentificationResult::class.java)
                if (result.specificName.isNotBlank() && result.deckName.isNotBlank()) {
                    return ParsedResult(
                        specificName = result.specificName,
                        deckName = result.deckName,
                        reasoning = mapOf(
                            "visualAnalysis" to result.reasoning.visualAnalysis,
                            "evidenceCorrelation" to result.reasoning.evidenceCorrelation
                        ),
                        confidence = result.confidence,
                        isComplete = true
                    )
                }
            } catch (e: JsonSyntaxException) {
                // Le JSON est malformé. On passe à la tentative 2.
            }
        }

        // Tentative 2: Extraction manuelle des champs clés, tolérante aux erreurs.
        val specificName = extractStringValue("specificName")
        val deckName = extractStringValue("deckName")

        val isPotentiallyComplete = specificName.isNotBlank() && deckName.isNotBlank() && buffer.contains("confidence")

        return ParsedResult(
            specificName = specificName,
            deckName = deckName,
            isComplete = isPotentiallyComplete
        )
    }

    private fun extractStringValue(key: String): String {
        val keyPattern = "\"$key\"\\s*:\\s*\"(.*?)\""
        val regex = keyPattern.toRegex()
        val match = regex.find(buffer)
        return match?.groups?.get(1)?.value ?: ""
    }

    fun reset() {
        buffer = ""
    }
}