package be.heyman.android.ai.kikko.model

/**
 * Définit les différents états possibles pour un PollenGrain dans son cycle de vie,
 * de sa capture brute à sa transformation finale en Miel (KnowledgeCard).
 * Chaque état représente une étape dans la chaîne de production de notre WorkManager.
 *
 * BOURDON'S REFORGE V6.0: Introduction d'un nouvel état pour la validation manuelle.
 */
enum class PollenStatus {
    /**
     * Le grain de pollen vient d'être capturé et est prêt pour la Forge.
     */
    RAW,

    /**
     * Étape 1 : Le grain est en cours de pré-identification par la Reine IA en arrière-plan.
     */
    IDENTIFYING,

    /**
     * NOUVEL ÉTAT: Le grain a une identification préliminaire et attend la validation
     * du Maître Forgeron dans l'Atelier. C'est le point d'entrée pour le raffinage manuel.
     */
    AWAITING_VALIDATION,

    /**
     * En attente de l'Étape 2 : La génération de la description narrative.
     */
    PENDING_DESCRIPTION,

    /**
     * En attente de l'Étape 3 : L'extraction des statistiques.
     */
    PENDING_STATS,

    /**
     * En attente de l'Étape 4 : La création du quiz.
     */
    PENDING_QUIZ,

    /**
     * En attente de l'Étape 5 : La traduction du contenu.
     */
    PENDING_TRANSLATION,

    /**
     * Le processus de Forge est terminé avec succès. La KnowledgeCard est complète.
     */
    FORGED,

    /**
     * Une erreur est survenue à l'une des étapes de la Forge.
     */
    ERROR
}