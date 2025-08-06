package be.heyman.android.ai.kikko.clash.data

import android.graphics.Color

/**
 * BOURDON'S NOTE:
 * Ce fichier contient les data classes utilisées spécifiquement pour la phase de DÉCOUVERTE P2P.
 * Elles sont une copie de celles du prototype et servent de "carte de visite" légère
 * qu'un joueur envoie pour se présenter sur le réseau. Elles sont distinctes de l'entité
 * principale `KnowledgeCard` qui est bien plus riche.
 */

/**
 * Représente un seul deck de cartes dans le catalogue partiel d'un joueur pour la découverte.
 */
data class Deck(
    val name: String,
    val cardCount: Int
)

/**
 * Représente le catalogue partiel d'un joueur, encodé dans l'endpointName de Nearby.
 */
data class PlayerCatalogue(
    val playerName: String,
    val decks: List<Deck>,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val wins: Int = 0,
    val losses: Int = 0,
    var color: Int = Color.WHITE
)