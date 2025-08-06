package be.heyman.android.ai.kikko.ui.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.KnowledgeCard
import java.io.File

/**
 * BOURDON'S REFACTOR: Cet adaptateur est maintenant aligné sur celui du DeckViewer.
 * Il utilise le layout de vignette compact pour une expérience utilisateur cohérente.
 * Déplacé depuis le package `debug` car il est utilisé par des fonctionnalités principales
 * (ex: sélection de cartes dans le Clash, mais avec le layout `item_clash_champion_thumbnail`).
 *
 * @param cards La liste mutable des KnowledgeCard à afficher.
 * @param onCardClickListener Callback lors du clic sur une carte.
 */
class CardPreviewAdapter(
    private val cards: MutableList<KnowledgeCard> = mutableListOf(),
    private val onCardClickListener: (KnowledgeCard) -> Unit
) : RecyclerView.Adapter<CardPreviewAdapter.ViewHolder>() {

    private var selectedCard: KnowledgeCard? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // BOURDON'S FIX: Les IDs correspondent maintenant à item_clash_champion_thumbnail.xml.
        val nameTextView: TextView = view.findViewById(R.id.card_thumbnail_name)
        val imageView: ImageView = view.findViewById(R.id.card_thumbnail_image)
        // La vue des stats n'existe plus dans ce layout, donc elle est retirée.
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // BOURDON'S FIX: Utilisation du layout correct et compact pour les vignettes de champion.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clash_champion_thumbnail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = cards[position]
        holder.nameTextView.text = card.specificName

        card.imagePath?.let { path ->
            val imgFile = File(path)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                holder.imageView.setImageBitmap(bitmap)
            } else {
                holder.imageView.setImageResource(R.drawable.ic_placeholder_card)
            }
        } ?: holder.imageView.setImageResource(R.drawable.ic_placeholder_card)

        holder.itemView.setOnClickListener { onCardClickListener(card) }
    }

    override fun getItemCount() = cards.size

    fun updateCards(newCards: List<KnowledgeCard>) {
        cards.clear()
        cards.addAll(newCards)
        notifyDataSetChanged()
    }

    fun setSelectedCard(card: KnowledgeCard?) {
        selectedCard = card
        notifyDataSetChanged()
    }
}