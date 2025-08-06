package be.heyman.android.ai.kikko

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
import java.io.File

class QueenModelAdapter(
    private var models: List<File>,
    private var selectedModelName: String?,
    private val onModelSelected: (File) -> Unit
) : RecyclerView.Adapter<QueenModelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val radioButton: RadioButton = view.findViewById(R.id.queen_model_radio_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queen_model_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val modelFile = models[position]
        holder.radioButton.text = modelFile.name
        holder.radioButton.isChecked = (modelFile.name == selectedModelName)

        holder.radioButton.setOnClickListener {
            if (modelFile.name != selectedModelName) {
                onModelSelected(modelFile)
                // Le fragment mettra à jour l'adaptateur avec le nouveau nom sélectionné
            }
        }
    }

    override fun getItemCount() = models.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateSelection(newModels: List<File>, newSelectedModelName: String?) {
        models = newModels
        selectedModelName = newSelectedModelName
        // On a besoin de redessiner toute la liste pour décocher l'ancien et cocher le nouveau.
        notifyDataSetChanged()
    }
}