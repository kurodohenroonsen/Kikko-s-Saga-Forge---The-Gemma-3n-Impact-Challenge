package be.heyman.android.ai.kikko

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * Adaptateur pour afficher une liste de modèles Vosk (reconnaissance vocale).
 * Déplacé depuis le package `debug` car il est utilisé par `ToolsDialogFragment`
 * pour gérer les modèles STT.
 *
 * @param models La liste mutable des fichiers de modèle Vosk à afficher.
 * @param onTestModel Callback lorsqu'un modèle est testé.
 * @param onDeleteModel Callback lorsqu'un modèle est supprimé.
 */
class VoskModelAdapter(
    private var models: MutableList<File>,
    private val onTestModel: (File) -> Unit,
    private val onDeleteModel: (File) -> Unit
) : RecyclerView.Adapter<VoskModelAdapter.ModelViewHolder>() {

    private var isListening = false

    class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelName: TextView = view.findViewById(R.id.vosk_model_name)
        val testButton: ImageButton = view.findViewById(R.id.button_test_vosk_model)
        val deleteButton: ImageButton = view.findViewById(R.id.button_delete_vosk_model)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vosk_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val modelFile = models[position]
        holder.modelName.text = modelFile.name

        // BOURDON'S FIX: Change l'icône en fonction de l'état d'écoute.
        if (isListening) {
            holder.testButton.setImageResource(android.R.drawable.ic_media_pause) // ou une icône "stop"
        } else {
            holder.testButton.setImageResource(android.R.drawable.ic_media_play)
        }

        holder.testButton.setOnClickListener { onTestModel(modelFile) }
        holder.deleteButton.setOnClickListener { onDeleteModel(modelFile) }
    }

    override fun getItemCount() = models.size

    fun updateModels(newModels: List<File>) {
        models.clear()
        models.addAll(newModels)
        notifyDataSetChanged()
    }

    // BOURDON'S FIX: Méthode pour que le fragment puisse informer l'adaptateur de l'état.
    fun setIsListening(listening: Boolean) {
        isListening = listening
        notifyDataSetChanged() // Redessine toute la liste pour mettre à jour les icônes.
    }
}