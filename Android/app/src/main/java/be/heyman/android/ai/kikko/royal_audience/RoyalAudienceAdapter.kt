package be.heyman.android.ai.kikko.royal_audience

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.model.KnowledgeCard
import com.bumptech.glide.Glide
import java.io.File

/**
 * BOURDON'S REFORGE V2:
 * La data class est maintenant encore plus flexible pour inclure une image dans les messages.
 */
data class ChatMessage(
    val text: String? = null,
    val card: KnowledgeCard? = null,
    val imageUri: String? = null, // NOUVEAU: URI de l'image jointe par l'utilisateur
    val isFromUser: Boolean,
    var isStreaming: Boolean = false
)

class RoyalAudienceAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_QUEEN = 2
        private const val VIEW_TYPE_CARD_CONTEXT = 3
        private const val VIEW_TYPE_USER_WITH_IMAGE = 4 // NOUVEAU: Type de vue pour les messages avec image
    }

    private val messages: MutableList<ChatMessage> = mutableListOf()

    // --- ViewHolders pour chaque type de message ---

    inner class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView = view.findViewById(R.id.chat_user_message_textview)
    }

    inner class QueenMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageTextView: TextView = view.findViewById(R.id.chat_queen_message_textview)
    }

    inner class CardContextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardImageView: ImageView = view.findViewById(R.id.chat_card_image)
        val cardNameTextView: TextView = view.findViewById(R.id.chat_card_name)
        val cardDescriptionTextView: TextView = view.findViewById(R.id.chat_card_description)

        fun bind(card: KnowledgeCard) {
            cardNameTextView.text = card.specificName
            cardDescriptionTextView.text = card.description ?: "Aucune description disponible."

            card.imagePath?.let { path ->
                val imgFile = File(path)
                if (imgFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                    cardImageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    // NOUVEAU: ViewHolder pour les messages utilisateur avec image
    inner class UserMessageWithImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageTextView: TextView = view.findViewById(R.id.chat_user_message_textview)
        private val imageView: ImageView = view.findViewById(R.id.chat_user_imageview)

        fun bind(message: ChatMessage) {
            // Le texte est optionnel si une image est présente
            if (message.text.isNullOrBlank()) {
                messageTextView.visibility = View.GONE
            } else {
                messageTextView.visibility = View.VISIBLE
                messageTextView.text = message.text
            }

            // L'image est chargée via Glide pour plus d'efficacité
            message.imageUri?.let {
                Glide.with(itemView.context)
                    .load(Uri.parse(it))
                    .into(imageView)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.card != null -> VIEW_TYPE_CARD_CONTEXT
            message.isFromUser && !message.imageUri.isNullOrEmpty() -> VIEW_TYPE_USER_WITH_IMAGE
            message.isFromUser -> VIEW_TYPE_USER
            else -> VIEW_TYPE_QUEEN
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_user, parent, false)
                UserMessageViewHolder(view)
            }
            VIEW_TYPE_QUEEN -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_queen, parent, false)
                QueenMessageViewHolder(view)
            }
            VIEW_TYPE_CARD_CONTEXT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_card_context, parent, false)
                CardContextViewHolder(view)
            }
            VIEW_TYPE_USER_WITH_IMAGE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_user_with_image, parent, false)
                UserMessageWithImageViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder.itemViewType) {
            VIEW_TYPE_USER -> (holder as UserMessageViewHolder).messageTextView.text = message.text
            VIEW_TYPE_QUEEN -> (holder as QueenMessageViewHolder).messageTextView.text = message.text
            VIEW_TYPE_CARD_CONTEXT -> message.card?.let { (holder as CardContextViewHolder).bind(it) }
            VIEW_TYPE_USER_WITH_IMAGE -> (holder as UserMessageWithImageViewHolder).bind(message)
        }
    }

    fun submitList(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}