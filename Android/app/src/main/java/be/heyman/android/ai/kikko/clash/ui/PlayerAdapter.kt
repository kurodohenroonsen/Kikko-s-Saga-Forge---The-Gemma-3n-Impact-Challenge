package be.heyman.android.ai.kikko.clash.ui.adapter

import android.content.Context
import android.graphics.PorterDuff
import android.location.Location
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import be.heyman.android.ai.kikko.GameConstants
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.clash.data.PlayerCatalogue

class PlayerAdapter(
    private var players: Map<String, PlayerCatalogue>,
    private var currentUserLocation: Location?,
    private val onClick: (endpointId: String) -> Unit
) : RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder>() {

    private val TAG = "PlayerAdapter"

    class PlayerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playerIcon: ImageView = view.findViewById(R.id.player_icon)
        val playerNameText: TextView = view.findViewById(R.id.player_name_text)
        val decksColumn1Text: TextView = view.findViewById(R.id.decks_column_1_text)
        val decksColumn2Text: TextView = view.findViewById(R.id.decks_column_2_text)
        val playerDistanceText: TextView = view.findViewById(R.id.player_distance_text)
        val playerRecordText: TextView = view.findViewById(R.id.player_record_text)
    }

    fun updatePlayers(newPlayers: Map<String, PlayerCatalogue>, location: Location?) {
        this.players = newPlayers
        this.currentUserLocation = location
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player_card, parent, false)
        return PlayerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        val (endpointId, catalogue) = players.entries.toList()[position]
        val context = holder.itemView.context

        holder.playerNameText.text = catalogue.playerName

        val column1Decks = StringBuilder()
        val column2Decks = StringBuilder()

        catalogue.decks.forEach { deck ->
            val emoji = GameConstants.DECK_EMOJIS[deck.name] ?: "❓"
            val deckString = "$emoji(${deck.cardCount})\n"
            when (deck.name) {
                GameConstants.MASTER_DECK_LIST[0], GameConstants.MASTER_DECK_LIST[1] -> column1Decks.append(deckString)
                GameConstants.MASTER_DECK_LIST[2], GameConstants.MASTER_DECK_LIST[3] -> column2Decks.append(deckString)
            }
        }

        holder.decksColumn1Text.text = column1Decks.toString().trim()
        holder.decksColumn2Text.text = column2Decks.toString().trim()

        holder.playerRecordText.text = context.getString(R.string.card_record_format, catalogue.wins, catalogue.losses)

        if (currentUserLocation != null && catalogue.latitude != null && catalogue.longitude != null) {
            val playerLocation = Location("player").apply {
                latitude = catalogue.latitude
                longitude = catalogue.longitude
            }

            val distance = currentUserLocation!!.distanceTo(playerLocation)

            if (distance < 1.0f) {
                val distanceInCm = distance * 100
                holder.playerDistanceText.text = context.getString(R.string.card_distance_format_cm, distanceInCm)
            } else {
                holder.playerDistanceText.text = context.getString(R.string.card_distance_format_m, distance)
            }
        } else {
            holder.playerDistanceText.text = "?? m"
        }

        holder.playerIcon.setColorFilter(catalogue.color, PorterDuff.Mode.SRC_IN)
        holder.itemView.setOnClickListener { onClick(endpointId) }
    }

    override fun getItemCount() = players.size
}