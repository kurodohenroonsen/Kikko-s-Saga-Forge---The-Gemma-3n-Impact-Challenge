package be.heyman.android.ai.kikko.royal_audience

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.QueenModelAdapter
import be.heyman.android.ai.kikko.R
import java.io.File

class QueenSelectorDialogFragment : DialogFragment() {

    interface QueenSelectorListener {
        fun onQueenSelected(modelName: String)
    }

    private var listener: QueenSelectorListener? = null
    private lateinit var queenSelectorAdapter: QueenModelAdapter

    private lateinit var availableModels: List<File>
    private var selectedModelName: String? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? QueenSelectorListener
            ?: throw ClassCastException("$context must implement QueenSelectorListener")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION")
            val modelPaths = it.getStringArrayList(ARG_MODELS) ?: emptyList<String>()
            availableModels = modelPaths.map { path -> File(path) }
            selectedModelName = it.getString(ARG_SELECTED_MODEL)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_queen_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setTitle("Choisir la Reine")

        val recyclerView: RecyclerView = view.findViewById(R.id.queen_selector_recyclerview)
        val emptyState: TextView = view.findViewById(R.id.queen_selector_empty_state)

        if (availableModels.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE

            queenSelectorAdapter = QueenModelAdapter(
                models = availableModels,
                selectedModelName = selectedModelName
            ) { selectedFile ->
                listener?.onQueenSelected(selectedFile.name)
                dismiss()
            }
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = queenSelectorAdapter
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        const val TAG = "QueenSelectorDialog"
        private const val ARG_MODELS = "available_models"
        private const val ARG_SELECTED_MODEL = "selected_model"

        fun newInstance(availableModels: List<File>, selectedModelName: String?): QueenSelectorDialogFragment {
            val args = Bundle().apply {
                putStringArrayList(ARG_MODELS, ArrayList(availableModels.map { it.absolutePath }))
                putString(ARG_SELECTED_MODEL, selectedModelName)
            }
            return QueenSelectorDialogFragment().apply {
                arguments = args
            }
        }
    }
}