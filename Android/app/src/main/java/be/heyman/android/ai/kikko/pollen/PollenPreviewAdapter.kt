package be.heyman.android.ai.kikko.pollen

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R

/**
 * BOURDON'S REFACTOR: L'adaptateur gère maintenant l'affichage des `PollenCapture`
 * et les clics sur les items terminés.
 *
 * @param pollenCaptures La liste des pollens (modèle UI) à afficher.
 * @param onPollenClick Le callback à exécuter lorsqu'un grain de pollen analysé est cliqué.
 */
class PollenPreviewAdapter(
    private var pollenCaptures: List<PollenCapture>,
    private val onPollenClick: (PollenCapture) -> Unit
) : RecyclerView.Adapter<PollenPreviewAdapter.PollenViewHolder>() {

    class PollenViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.pollen_preview_imageview)
        val progressIndicator: View = view.findViewById(R.id.pollen_progress_indicator)
        val doneIcon: View = view.findViewById(R.id.pollen_done_icon)
        val removeButton: View = view.findViewById(R.id.pollen_remove_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollenViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pollen_preview, parent, false)
        return PollenViewHolder(view)
    }

    override fun onBindViewHolder(holder: PollenViewHolder, position: Int) {
        val capture = pollenCaptures[position]
        holder.imageView.setImageBitmap(capture.bitmap)
        // La logique du bouton de suppression est pour l'instant masquée.
        holder.removeButton.visibility = View.GONE

        when (capture.status) {
            PollenAnalysisStatus.PROCESSING -> {
                holder.progressIndicator.visibility = View.VISIBLE
                holder.doneIcon.visibility = View.GONE
                holder.itemView.isClickable = false
            }
            PollenAnalysisStatus.DONE -> {
                holder.progressIndicator.visibility = View.GONE
                holder.doneIcon.visibility = View.VISIBLE
                holder.itemView.isClickable = true
                holder.itemView.setOnClickListener { onPollenClick(capture) }
            }
            PollenAnalysisStatus.ERROR -> {
                holder.progressIndicator.visibility = View.GONE
                holder.doneIcon.visibility = View.GONE // Idéalement, afficher une icône d'erreur ici.
                holder.itemView.isClickable = false
            }
        }
    }

    override fun getItemCount() = pollenCaptures.size

    fun updatePollen(newCaptures: List<PollenCapture>) {
        this.pollenCaptures = newCaptures
        notifyDataSetChanged()
    }
}