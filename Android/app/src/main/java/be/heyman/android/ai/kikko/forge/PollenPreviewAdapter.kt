package be.heyman.android.ai.kikko.forge

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R

/**
 * Un adaptateur pour le RecyclerView qui affiche une prévisualisation horizontale
 * des images (pollen) sélectionnées par l'utilisateur.
 *
 * @param pollenImages La liste mutable des bitmaps à afficher.
 * @param onRemoveClick Une fonction lambda appelée lorsqu'un utilisateur clique sur le bouton de suppression
 * d'une image, passant l'index de l'image à supprimer.
 */
class PollenPreviewAdapter(
    private val pollenImages: MutableList<Bitmap>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<PollenPreviewAdapter.PollenViewHolder>() {

    /**
     * ViewHolder qui contient les vues pour un seul item de prévisualisation de pollen.
     */
    inner class PollenViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.pollen_preview_imageview)
       /* val removeButton//: ImageButton = view.findViewById(R.id.pollen_remove_button)

        init {
            removeButton.setOnClickListener {
                // S'assure que la position est valide avant de déclencher le callback
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onRemoveClick(position)
                }
            }
        }

        */
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollenViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pollen_preview, parent, false)
        return PollenViewHolder(view)
    }

    override fun onBindViewHolder(holder: PollenViewHolder, position: Int) {
        val bitmap = pollenImages[position]
        holder.imageView.setImageBitmap(bitmap)
    }

    override fun getItemCount(): Int = pollenImages.size

    /**
     * Met à jour la liste des images affichées par l'adaptateur.
     * @param newImages La nouvelle liste de bitmaps.
     */
    fun updateImages(newImages: List<Bitmap>) {
        pollenImages.clear()
        pollenImages.addAll(newImages)
        notifyDataSetChanged()
    }
}