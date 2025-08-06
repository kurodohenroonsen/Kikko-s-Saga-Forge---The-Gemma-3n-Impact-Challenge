package be.heyman.android.ai.kikko.clash.data

/**
 * Représente les différents états possibles pour un seul duel dans le tournoi.
 *
 * - PENDING: Le duel est en attente, l'inférence n'a pas encore été lancée.
 * - INFERRING: Le Juge IA (LLM) est en train de délibérer sur le verdict.
 * - TRANSLATING: Le raisonnement brut est reçu, la traduction est en cours.
 * - COMPLETED: Le verdict et le raisonnement traduit sont finaux et prêts à être affichés.
 * - ERROR: Une erreur est survenue durant l'inférence ou la traduction.
 */
enum class ClashStatus {
    PENDING,
    INFERRING,
    TRANSLATING,
    COMPLETED,
    ERROR
}