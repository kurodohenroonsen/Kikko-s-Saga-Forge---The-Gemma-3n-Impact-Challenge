package be.heyman.android.ai.kikko.ui.adapters

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R // BOURDON'S FIX: Import R
import be.heyman.android.ai.kikko.model.KnowledgeCard
import com.bumptech.glide.Glide
import java.io.File // BOURDON'S FIX: Import File

// BOURDON'S FIX: Changement de ListAdapter à RecyclerView.Adapter pour simplifier la gestion.
// ListAdapter nécessite un DiffUtil qui n'était pas la source directe de l'erreur,
// mais revenons à la base pour s'assurer de la correction du ViewBinding.
class KnowledgeCardAdapter(
    private val onCardClicked: (KnowledgeCard) -> Unit
) : RecyclerView.Adapter<KnowledgeCardAdapter.ViewHolder>() { // BOURDON'S FIX: Hérite de RecyclerView.Adapter

    // BOURDON'S FIX: Le ViewHolder prend maintenant une View et utilise findViewById
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardName: TextView = view.findViewById(R.id.card_title) // BOURDON'S FIX: ID correct pour item_knowledge_card_reforged
        val cardDeck: TextView = view.findViewById(R.id.card_deck_name) // BOURDON'S FIX: ID correct
        val cardImage: ImageView = view.findViewById(R.id.card_image) // BOURDON'S FIX: ID correct

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onCardClicked(currentList[adapterPosition]) // BOURDON'S FIX: Accès à currentList
                }
            }
        }

        fun bind(card: KnowledgeCard) {
            cardName.text = card.specificName
            cardDeck.text = card.deckName
            // BOURDON'S FIX: Utilisation de Glide avec le contexte de la vue et gestion des chemins locaux
            card.imagePath?.let { path ->
                val imgFile = File(path)
                if (imgFile.exists()) {
                    Glide.with(itemView.context)
                        .load(imgFile)
                        .into(cardImage)
                } else {
                    cardImage.setImageResource(R.drawable.ic_placeholder_card) // Image par défaut si le fichier n'existe pas
                }
            } ?: cardImage.setImageResource(R.drawable.ic_placeholder_card)
        }
    }

    // BOURDON'S FIX: Ajout d'une liste interne pour gérer les données, comme dans ListAdapter
    private var currentList: List<KnowledgeCard> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // BOURDON'S FIX: Inflate le layout de carte refait pour le Clash
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_knowledge_card_reforged, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val card = currentList[position] // BOURDON'S FIX: Accès à currentList
        holder.bind(card)
    }

    override fun getItemCount() = currentList.size // BOURDON'S FIX: Taille de currentList

    // BOURDON'S FIX: Méthode pour soumettre une nouvelle liste de cartes
    fun submitList(newList: List<KnowledgeCard>) {
        currentList = newList
        notifyDataSetChanged() // BOURDON'S FIX: Notifie le changement de données
    }
}

// BOURDON'S FIX: DiffUtil est conservé si on souhaite le remettre en place avec ListAdapter plus tard.
// Pour l'instant, il n'est plus directement utilisé par KnowledgeCardAdapter.
class KnowledgeCardDiffCallback : DiffUtil.ItemCallback<KnowledgeCard>() {
    override fun areItemsTheSame(oldItem: KnowledgeCard, newItem: KnowledgeCard): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: KnowledgeCard, newItem: KnowledgeCard): Boolean {
        return oldItem == newItem
    }
}