package be.heyman.android.ai.kikko.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
import java.io.File

/**
 * Adaptateur pour afficher une liste de modèles IA locaux (fichiers .task).
 * Déplacé depuis le package `debug` car il est essentiel pour `ToolsDialogFragment`.
 *
 * @param models La liste mutable des fichiers de modèle à afficher.
 * @param onDeleteClick Une fonction lambda appelée lorsqu'un utilisateur clique sur le bouton de suppression.
 */
class LocalModelAdapter(
    private var models: MutableList<File>,
    private val onDeleteClick: (File) -> Unit
) : RecyclerView.Adapter<LocalModelAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelName: TextView = view.findViewById(R.id.local_model_name_textview)
        val deleteButton: ImageButton = view.findViewById(R.id.local_model_delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_local_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val modelFile = models[position]
        holder.modelName.text = modelFile.name
        holder.deleteButton.setOnClickListener {
            onDeleteClick(modelFile)
        }
    }

    override fun getItemCount() = models.size

    fun updateModels(newModels: List<File>) {
        models.clear()
        models.addAll(newModels)
        notifyDataSetChanged()
    }
}