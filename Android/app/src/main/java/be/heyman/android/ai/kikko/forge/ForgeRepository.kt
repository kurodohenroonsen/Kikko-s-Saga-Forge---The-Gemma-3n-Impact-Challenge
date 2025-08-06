package be.heyman.android.ai.kikko.forge

import android.util.Log
import be.heyman.android.ai.kikko.model.AnalysisResult
import be.heyman.android.ai.kikko.model.AnalysisStatus
import be.heyman.android.ai.kikko.model.CardStats
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.model.ModelConfiguration
import be.heyman.android.ai.kikko.model.PollenGrain
import be.heyman.android.ai.kikko.model.PollenStatus
import be.heyman.android.ai.kikko.persistence.AnalysisResultDao
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour le flux de travail de la Forge.
 * Agit comme une façade pour les différents DAOs liés au processus de forge.
 * C'est la seule source de vérité pour le ForgeWorkshopViewModel.
 */
@Singleton
class ForgeRepository @Inject constructor(
    private val pollenGrainDao: PollenGrainDao,
    private val cardDao: CardDao,
    private val analysisResultDao: AnalysisResultDao
) {
    private val gson = Gson()
    private val stringListType = object : TypeToken<List<String>>() {}.type

    /**
     * Récupère tous les grains de pollen qui ne sont pas encore complètement forgés.
     */
    suspend fun getGrainsForWorkshop(): List<PollenGrain> {
        // Retourne tous les grains sauf ceux qui sont FORGED ou en ERROR.
        val allGrains = pollenGrainDao.getByStatus(PollenStatus.RAW) +
                pollenGrainDao.getByStatus(PollenStatus.IDENTIFYING) +
                pollenGrainDao.getByStatus(PollenStatus.PENDING_DESCRIPTION) +
                pollenGrainDao.getByStatus(PollenStatus.PENDING_STATS) +
                pollenGrainDao.getByStatus(PollenStatus.PENDING_QUIZ) +
                pollenGrainDao.getByStatus(PollenStatus.PENDING_TRANSLATION)
        return allGrains.distinctBy { it.id }.sortedByDescending { it.timestamp }
    }

    /**
     * Récupère la KnowledgeCard associée à un PollenGrain.
     */
    suspend fun getCardForGrain(grain: PollenGrain): KnowledgeCard? {
        return grain.forgedCardId?.let { cardDao.getCardById(it) }
    }

    /**
     * Récupère tous les résultats d'analyse pour une propriété spécifique d'un grain.
     */
    suspend fun getAnalysisResults(pollenGrainId: String, propertyName: String): List<AnalysisResult> {
        return analysisResultDao.getByPollenGrainIdAndProperty(pollenGrainId, propertyName)
    }

    /**
     * BOURDON'S FIX: Nouvelle méthode pour insérer une seule tâche.
     * C'est une correction nécessaire pour la logique du ViewModel.
     */
    suspend fun insertAnalysisResult(result: AnalysisResult) {
        analysisResultDao.insert(result)
    }


    /**
     * Crée et insère dans la base de données les tâches d'analyse pour un "tournoi".
     * @return La liste des tâches créées.
     */
    suspend fun createAnalysisTasksForProperty(pollenGrainId: String, propertyName: String, models: List<String>, accelerator: String): List<AnalysisResult> {
        val tasks = mutableListOf<AnalysisResult>()
        // BOURDON'S FIX: Le décret de 2 configurations par Reine est appliqué ici.
        val configs = listOf(
            Pair(0.2f, 40), // Factuel
            Pair(0.9f, 80)  // Créatif
        )

        models.forEach { modelName ->
            configs.forEach { (temp, topK) ->
                val modelConfig = ModelConfiguration(modelName, accelerator, temp, topK)
                val task = AnalysisResult(
                    pollenGrainId = pollenGrainId,
                    propertyName = propertyName,
                    modelConfigJson = gson.toJson(modelConfig),
                    status = AnalysisStatus.PENDING
                )
                analysisResultDao.insert(task)
                tasks.add(task)
            }
        }
        return tasks
    }

    /**
     * BOURDON'S FIX: Nouvelle méthode pour purger les résultats d'une compétition avant de la relancer.
     */
    suspend fun clearAnalysisResultsForProperty(pollenGrainId: String, propertyName: String) {
        analysisResultDao.deleteByPollenGrainIdAndProperty(pollenGrainId, propertyName)
    }

    /**
     * Met à jour une tâche d'analyse (typiquement après son exécution).
     */
    suspend fun updateAnalysisResult(result: AnalysisResult) {
        analysisResultDao.update(result)
    }

    /**
     * Met à jour le statut d'un grain de pollen.
     */
    suspend fun updatePollenStatus(pollenGrainId: String, newStatus: PollenStatus) {
        pollenGrainDao.updateStatus(pollenGrainId, newStatus)
    }

    /**
     * Met à jour une propriété spécifique de la carte de connaissance.
     * Cette fonction est complète et gère les différents types de propriétés.
     */
    suspend fun updateCardProperty(cardId: Long, propertyName: String, value: String) {
        val card = cardDao.getCardById(cardId) ?: return

        val updatedCard = when {
            propertyName == "description" -> card.copy(description = value)
            propertyName == "biological.scientificName" -> card.copy(scientificName = value)
            propertyName == "biological.vernacularName" -> card.copy(vernacularName = value)
            propertyName == "ingredients" -> card.copy(ingredients = gson.fromJson(value, stringListType))
            propertyName == "allergens" -> card.copy(allergens = gson.fromJson(value, stringListType))
            propertyName.startsWith("stats.") -> {
                val statKey = propertyName.substringAfter("stats.")
                val newStatsItems = card.stats?.items?.toMutableMap() ?: mutableMapOf()
                newStatsItems[statKey] = value
                card.copy(stats = CardStats(title = "Statistics", items = newStatsItems))
            }
            else -> {
                Log.w("ForgeRepository", "Tentative de mise à jour d'une propriété inconnue: '$propertyName'")
                card // Ne rien faire si la propriété est inconnue
            }
        }

        if (updatedCard != card) {
            cardDao.update(updatedCard)
        }
    }

    /**
     * BOURDON'S FIX: Nouvelle fonction pour orchestrer une suppression complète.
     * Supprime le grain, sa carte associée, ses fichiers images et ses résultats d'analyse (via cascade).
     */
    suspend fun deletePollenGrainAndAssociatedData(grain: PollenGrain) {
        // 1. Supprimer la carte associée, si elle existe.
        grain.forgedCardId?.let { cardId ->
            cardDao.getCardById(cardId)?.let { card ->
                cardDao.delete(card)
                Log.d("ForgeRepository", "Carte associée (ID: $cardId) supprimée.")
            }
        }

        // 2. Supprimer les fichiers image physiques.
        grain.pollenImagePaths.forEach { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    if (file.delete()) {
                        Log.d("ForgeRepository", "Fichier image supprimé : $path")
                    } else {
                        Log.w("ForgeRepository", "Échec de la suppression du fichier image : $path")
                    }
                }
            } catch (e: Exception) {
                Log.e("ForgeRepository", "Erreur lors de la suppression du fichier image : $path", e)
            }
        }

        // 3. Supprimer le grain de pollen.
        // La suppression en cascade dans la DB s'occupera des AnalysisResults.
        pollenGrainDao.delete(grain)
        Log.d("ForgeRepository", "Grain de pollen (ID: ${grain.id}) et ses données associées supprimés.")
    }
}