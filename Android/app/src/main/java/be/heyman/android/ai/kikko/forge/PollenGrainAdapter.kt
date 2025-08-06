package be.heyman.android.ai.kikko.forge

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.PollenGrain
import com.google.android.material.chip.Chip
import java.io.File

/**
 * Adaptateur pour afficher la liste horizontale des PollenGrains dans l'Atelier de la Forge.
 * Gère l'affichage de l'aperçu et l'état de sélection.
 */
class PollenGrainAdapter(
    private var grains: List<PollenGrain>,
    private var selectedCardId: String?,
    private val onClick: (PollenGrain) -> Unit
) : RecyclerView.Adapter<PollenGrainAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.pollen_preview_image)
        val statusChip: Chip = view.findViewById(R.id.pollen_preview_status_chip)
        val nameTextView: TextView = view.findViewById(R.id.pollen_preview_name)
        val selectionBorder: View = view.findViewById(R.id.pollen_preview_selection_border)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pollen_grain_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val grain = grains[position]

        // Affiche le nom s'il existe, sinon l'ID court.
        holder.nameTextView.text = grain.forgedCardId?.toString() ?: "Pollen #${grain.id.substring(0, 4)}"
        holder.statusChip.text = grain.status.name

        // Affiche la première image du grain.
        grain.pollenImagePaths.firstOrNull()?.let { path ->
            val imgFile = File(path)
            if (imgFile.exists()) {
                holder.imageView.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
            } else {
                holder.imageView.setImageResource(R.drawable.ic_placeholder_card)
            }
        } ?: holder.imageView.setImageResource(R.drawable.ic_placeholder_card)

        // Gère la visibilité de la bordure de sélection.
        holder.selectionBorder.visibility = if (grain.id == selectedCardId) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onClick(grain) }
    }

    override fun getItemCount() = grains.size

    /**
     * Met à jour la liste des grains et l'ID de la sélection, puis rafraîchit l'affichage.
     */
    fun updateGrainsAndSelection(newGrains: List<PollenGrain>, newSelectedCardId: String?) {
        this.grains = newGrains
        this.selectedCardId = newSelectedCardId
        notifyDataSetChanged()
    }
}