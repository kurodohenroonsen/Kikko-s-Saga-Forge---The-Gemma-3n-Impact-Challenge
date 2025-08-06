package be.heyman.android.ai.kikko.util.logging

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Un logger singleton en mémoire pour conserver une trace de toutes les inférences LLM
 * effectuées pendant une session de débogage.
 *
 * NOTE : Ce logger est volatile. Son contenu est perdu lorsque l'application est fermée.
 * Déplacé depuis le package `debug` vers un nouveau package `util.logging`
 * car il est une fonctionnalité de logging générale.
 */
object InferenceLogger {

    // Configure l'encodeur JSON pour une sortie lisible (pretty print).
    private val json = Json { prettyPrint = true }

    // La liste privée et mutable qui stocke nos entrées de journal.
    private val logEntries = mutableListOf<InferenceLog>()

    /**
     * Ajoute une nouvelle entrée de journal à la liste de manière thread-safe.
     * @param entry L'objet InferenceLog à ajouter.
     */
    @Synchronized
    fun add(entry: InferenceLog) {
        logEntries.add(entry)
    }

    /**
     * Vide complètement le journal de manière thread-safe.
     */
    @Synchronized
    fun clear() {
        logEntries.clear()
    }

    /**
     * Renvoie une copie immuable de toutes les entrées du journal.
     * @return Une List<InferenceLog> des entrées actuelles.
     */
    fun getLogs(): List<InferenceLog> {
        return logEntries.toList()
    }

    /**
     * Compte le nombre d'entrées actuellement dans le journal.
     * @return Le nombre d'inférences enregistrées.
     */
    fun count(): Int {
        return logEntries.size
    }

    /**
     * Sérialise l'ensemble du journal en une chaîne de caractères JSON formatée.
     * @return Une chaîne JSON représentant la liste des entrées, ou un message d'erreur.
     */
    @Synchronized
    fun exportToJson(): String {
        if (logEntries.isEmpty()) {
            return "{\"status\": \"Le journal de forge est vide.\"}"
        }
        return try {
            json.encodeToString(logEntries)
        } catch (e: Exception) {
            "{\"error\": \"Échec de la sérialisation du journal en JSON.\", \"details\": \"${e.message}\"}"
        }
    }
}