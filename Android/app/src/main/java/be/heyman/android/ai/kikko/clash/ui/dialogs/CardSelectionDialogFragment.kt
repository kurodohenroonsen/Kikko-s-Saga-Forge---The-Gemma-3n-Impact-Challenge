package be.heyman.android.ai.kikko.clash.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.KikkoApplication
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.clash.viewmodel.ClashViewModel
import be.heyman.android.ai.kikko.model.KnowledgeCard
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.ui.adapters.KnowledgeCardAdapter
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class CardSelectionDialogFragment : DialogFragment() {

    private val viewModel: ClashViewModel by activityViewModels()
    private lateinit var adapter: KnowledgeCardAdapter
    private var isPlayerOne: Boolean = true

    // BOURDON'S FIX: Le dialog va chercher ses propres données pour l'instant.
    private val cardDao: CardDao by lazy {
        (requireActivity().application as KikkoApplication).cardDao
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var searchEditText: EditText
    private lateinit var sortSpinner: Spinner
    private lateinit var recyclerView: RecyclerView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isPlayerOne = arguments?.getBoolean(ARG_IS_PLAYER_ONE) ?: true
        setStyle(STYLE_NORMAL, R.style.Theme_Kikko_FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_card_selection, container, false)
        toolbar = view.findViewById(R.id.card_selection_toolbar)
        searchEditText = view.findViewById(R.id.card_selection_search_edittext)
        sortSpinner = view.findViewById(R.id.card_selection_sort_spinner)
        recyclerView = view.findViewById(R.id.card_selection_recyclerview)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        loadCards() // BOURDON'S FIX: Chargement des cartes
        setupSearchAndSort()
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener { dismiss() }
        toolbar.title = if (isPlayerOne) "Sélectionnez votre Champion (Joueur 1)" else "Sélectionnez votre Champion (Joueur 2)"
    }

    private fun setupRecyclerView() {
        adapter = KnowledgeCardAdapter { card ->
            viewModel.selectCardForClash(card, isPlayerOne)
            dismiss()
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(context, 3)
    }

    // BOURDON'S FIX: Nouvelle méthode pour charger les cartes depuis le DAO
    private fun loadCards() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cards = cardDao.getAll()
            adapter.submitList(cards)
        }
    }

    private fun setupSearchAndSort() {
        searchEditText.addTextChangedListener { text ->
            // viewModel.searchCards(text.toString()) // Logique à implémenter dans le ViewModel
        }

        // BOURDON'S NOTE: Il faudra créer un fichier res/values/arrays.xml pour que R.array.sort_options fonctionne.
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.sort_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sortSpinner.adapter = adapter
        }

        sortSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sortBy = parent?.getItemAtPosition(position).toString()
                // viewModel.sortCards(sortBy) // Logique à implémenter dans le ViewModel
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // Rien à faire
            }
        }
    }

    companion object {
        private const val ARG_IS_PLAYER_ONE = "is_player_one"
        const val TAG = "CardSelectionDialog"

        @JvmStatic
        fun newInstance(isPlayerOne: Boolean) =
            CardSelectionDialogFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_PLAYER_ONE, isPlayerOne)
                }
            }
    }
}