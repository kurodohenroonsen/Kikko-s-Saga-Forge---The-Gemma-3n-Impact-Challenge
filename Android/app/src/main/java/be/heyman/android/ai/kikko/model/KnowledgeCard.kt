package be.heyman.android.ai.kikko.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Contient tous les champs traduisibles pour une langue spécifique.
 */
@Serializable
@Parcelize
data class TranslatedContent(
    val description: String?,
    val reasoning: Reasoning?,
    val quiz: List<QuizQuestion>?
) : Parcelable


/**
 * Représente une seule pièce de connaissance.
 * BOURDON'S REFORGE (v3.0): Le modèle est simplifié pour le concours,
 * avec des champs spécifiques aux decks biologiques et des stats flexibles.
 */
@Serializable
@Parcelize
data class KnowledgeCard(
    val id: Long = 0,
    val specificName: String,
    val deckName: String,
    val imagePath: String?,
    val confidence: Float,
    // Contenu original (généré par l'IA)
    val reasoning: Reasoning,
    val description: String?,
    val stats: CardStats?,
    val quiz: List<QuizQuestion>?,
    // Nouveaux champs pour les decks biologiques - BOURDON'S FIX
    val scientificName: String? = null,
    val vernacularName: String? = null,
    // Nouveaux champs pour le deck Food - BOURDON'S FIX
    val allergens: List<String>? = null,
    val ingredients: List<String>? = null,
    // Contenu traduit
    val translations: Map<String, TranslatedContent>? = null
) : Parcelable

@Serializable
@Parcelize
data class Reasoning(
    val visualAnalysis: String,
    val evidenceCorrelation: String
) : Parcelable

@Serializable
@Parcelize
data class CardStats(
    val title: String,
    // La map `items` est flexible pour contenir différentes stats selon le deck
    // Ex: {"Energy": "520 kcal", "Allergens": "Peanuts, Gluten"} pour Food
    // Ex: {"Wingspan": "110 cm", "Diet": "Carnivore"} pour Bird
    val items: Map<String, String>
) : Parcelable

@Serializable
@Parcelize
data class QuizQuestion(
    @SerializedName("q")
    val question: String,
    @SerializedName("o")
    val options: List<String>,
    @SerializedName("c")
    val correctAnswerIndex: Int,
    val explanation: String?
) : Parcelable