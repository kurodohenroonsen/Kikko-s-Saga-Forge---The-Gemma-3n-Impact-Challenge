package be.heyman.android.ai.kikko.deck

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
 * BOURDON'S REFACTOR V3:
 * L'adaptateur utilise maintenant le layout correct `item_clash_deck_thumbnail.xml`
 * pour une présentation en grille élégante et compacte.
 *
 * @param onCardClicked Une fonction lambda appelée lorsqu'une carte est cliquée.
 */
class DeckCardAdapter(
    private var cards: List<KnowledgeCard>,
    private val onCardClicked: (KnowledgeCard) -> Unit
) : RecyclerView.Adapter<DeckCardAdapter.CardViewHolder>() {

    /**
     * ViewHolder dédié qui contient les vues pour la vignette de champion.
     */
    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // BOURDON'S FIX: Les IDs sont maintenant ceux de `item_clash_deck_thumbnail.xml`
        val cardNameView: TextView = view.findViewById(R.id.card_thumbnail_name)
        val cardImageView: ImageView = view.findViewById(R.id.card_thumbnail_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        // BOURDON'S FIX: Utilisation du nouveau layout correct.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clash_deck_thumbnail, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        holder.cardNameView.text = card.specificName

        card.imagePath?.let { path ->
            val imgFile = File(path)
            if (imgFile.exists()) {
                val myBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                holder.cardImageView.setImageBitmap(myBitmap)
            } else {
                holder.cardImageView.setImageResource(R.drawable.ic_placeholder_card)
            }
        } ?: holder.cardImageView.setImageResource(R.drawable.ic_placeholder_card)

        holder.itemView.setOnClickListener {
            onCardClicked(card)
        }
    }

    override fun getItemCount(): Int = cards.size

    /**
     * Met à jour la liste des cartes affichées par l'adaptateur et notifie le changement.
     */
    fun updateCards(newCards: List<KnowledgeCard>) {
        this.cards = newCards
        notifyDataSetChanged()
    }
}