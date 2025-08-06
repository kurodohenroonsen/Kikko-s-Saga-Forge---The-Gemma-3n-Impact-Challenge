package be.heyman.android.ai.kikko.deck

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import be.heyman.android.ai.kikko.model.KnowledgeCard

/**
 * Un adaptateur qui fournit des fragments (DeckPageFragment) pour chaque deck de cartes
 * à un ViewPager2.
 *
 * @param fa L'activité hôte du fragment.
 * @param decks La map contenant les decks, où la clé est le nom du deck et la valeur est la liste des cartes.
 */
class DeckPagerAdapter(fa: FragmentActivity, private val decks: Map<String, List<KnowledgeCard>>) : FragmentStateAdapter(fa) {

    // Crée une liste des noms de decks pour un accès par indice.
    private val deckNames = decks.keys.toList()

    /**
     * Retourne le nombre total de decks (et donc de pages).
     */
    override fun getItemCount(): Int {
        return decks.size
    }

    /**
     * Crée et retourne un DeckPageFragment pour la position donnée.
     */
    override fun createFragment(position: Int): Fragment {
        val deckName = deckNames[position]
        val cardsForDeck = decks[deckName] ?: emptyList()
        // Utilise la méthode factory du fragment pour lui passer la liste de cartes.
        return DeckPageFragment.newInstance(cardsForDeck)
    }

    /**
     * Retourne le titre du deck pour l'onglet à la position donnée.
     */
    fun getPageTitle(position: Int): CharSequence {
        return deckNames[position]
    }
}