package be.heyman.android.ai.kikko.pollen

/**
 * Définit les différents états possibles pour l'analyse d'un grain de pollen
 * pendant la phase de capture en direct.
 */
enum class PollenAnalysisStatus {
    /**
     * L'analyse par les Abeilles Spécialistes est en cours.
     */
    PROCESSING,

    /**
     * L'analyse est terminée avec succès et un rapport est disponible.
     */
    DONE,

    /**
     * Une erreur est survenue durant l'analyse.
     */
    ERROR
}