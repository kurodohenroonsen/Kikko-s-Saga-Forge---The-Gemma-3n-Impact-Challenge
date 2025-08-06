package be.heyman.android.ai.kikko

/**
 * BOURDON'S NOTE:
 * Un objet singleton pour contenir les constantes de notre jeu.
 * C'est ici que nous définissons les règles qui ne changent pas, comme la liste
 * officielle des decks, pour assurer la cohérence à travers toute l'application.
 */
object GameConstants {

    /**
     * La liste maîtresse, fixe et ordonnée, de TOUS les decks possibles dans le jeu.
     * L'ordre est important pour la sérialisation/désérialisation des catalogues de joueurs.
     */
    val MASTER_DECK_LIST = listOf(
        "Food",
        "Plant",
        "Insect",
        "Bird"
    )

    /**
     * Table de correspondance pour associer un emoji à chaque deck, pour l'affichage.
     */
    val DECK_EMOJIS = mapOf(
        "Food" to "🍔",
        "Plant" to "🌿",
        "Insect" to "🐞",
        "Bird" to "🐦"
    )
}