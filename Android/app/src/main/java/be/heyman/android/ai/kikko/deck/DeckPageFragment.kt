package be.heyman.android.ai.kikko.deck

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
// BOURDON'S FIX: L'import a été corrigé pour pointer vers le package 'deck' au lieu de 'debug'.
import be.heyman.android.ai.kikko.deck.CardDetailsDialogFragment
import be.heyman.android.ai.kikko.model.KnowledgeCard

/**
 * Un Fragment qui affiche une grille de KnowledgeCards pour un seul deck.
 */
class DeckPageFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var deckCardAdapter: DeckCardAdapter
    private var cards: ArrayList<KnowledgeCard>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Récupère les cartes passées en argument lors de la création du fragment.
        arguments?.let {
            @Suppress("DEPRECATION")
            cards = it.getParcelableArrayList(ARG_CARDS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate le layout pour ce fragment.
        return inflater.inflate(R.layout.fragment_deck_page, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.deck_page_recyclerview)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        // Initialise l'adaptateur avec la liste de cartes (ou une liste vide).
        deckCardAdapter = DeckCardAdapter(cards ?: emptyList()) { card ->
            // Affiche le dialogue des détails de la carte lorsqu'une carte est cliquée.
            CardDetailsDialogFragment.newInstance(card).show(parentFragmentManager, "CardDetailsDialog")
        }
        recyclerView.apply {
            // Utilise un GridLayoutManager pour afficher les cartes en grille de 2 colonnes.
            layoutManager = GridLayoutManager(context, 2)
            adapter = deckCardAdapter
        }
    }

    companion object {
        private const val ARG_CARDS = "cards_for_deck"

        /**
         * Méthode factory pour créer une nouvelle instance de ce fragment
         * avec une liste spécifique de cartes.
         *
         * @param cards La liste des cartes à afficher.
         * @return Une nouvelle instance de DeckPageFragment.
         */
        @JvmStatic
        fun newInstance(cards: List<KnowledgeCard>) =
            DeckPageFragment().apply {
                arguments = Bundle().apply {
                    // Les objets personnalisés doivent être Parcelable pour être passés dans un Bundle.
                    putParcelableArrayList(ARG_CARDS, ArrayList(cards))
                }
            }
    }
}