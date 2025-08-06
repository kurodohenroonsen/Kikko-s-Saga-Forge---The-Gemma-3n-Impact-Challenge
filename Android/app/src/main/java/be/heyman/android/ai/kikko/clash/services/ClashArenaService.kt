package be.heyman.android.ai.kikko.clash.services

import android.content.Context
import android.util.Log
import be.heyman.android.ai.kikko.GameConstants
import be.heyman.android.ai.kikko.R
import be.heyman.android.ai.kikko.clash.data.Deck
import be.heyman.android.ai.kikko.clash.data.PlayerCatalogue
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class ClashArenaService @Inject constructor(
    private val context: Context
) {
    private val TAG = "ClashArenaService"
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val STRATEGY = Strategy.P2P_STAR
    private val SERVICE_ID = "be.heyman.android.ai.kikko.SERVICE_ID"

    private var listener: ClashArenaListener? = null
    private var myName: String = "KikkoUser${(100..999).random()}"
    private val turtleColorPalette: List<Int> = context.resources.getIntArray(R.array.turtle_colors).toList()

    private val incomingFilePayloads = mutableMapOf<Long, Payload>()

    interface ClashArenaListener {
        fun onStatusUpdate(message: String)
        fun onEndpointFound(endpointId: String, catalogue: PlayerCatalogue)
        fun onEndpointLost(endpointId: String)
        fun onConnectionInitiated(endpointId: String, opponentName: String, authDigits: String)
        fun onConnectionResult(endpointId: String, isSuccess: Boolean)
        fun onDisconnected(endpointId: String)
        fun onPayloadReceived(endpointId: String, payloadString: String)
        fun onFilePayloadReceived(endpointId: String, payload: Payload)
        fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate)
    }

    fun setListener(listener: ClashArenaListener) {
        this.listener = listener
    }

    private fun detailedFailureListener(action: String): OnFailureListener {
        return OnFailureListener { e ->
            val errorMessage = "Erreur $action: ${e.localizedMessage}"
            Log.e(TAG, errorMessage, e)
            listener?.onStatusUpdate(errorMessage)
        }
    }

    fun startAdvertising(myCatalogue: PlayerCatalogue) {
        listener?.onStatusUpdate("Devenir visible...")
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val endpointNameCompact = buildEndpointName(myCatalogue)

        connectionsClient.startAdvertising(endpointNameCompact, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
            .addOnSuccessListener { listener?.onStatusUpdate("Vous êtes maintenant visible.") }
            .addOnFailureListener(detailedFailureListener("publicité"))
    }

    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        listener?.onStatusUpdate("Visibilité arrêtée.")
    }

    fun startDiscovery() {
        listener?.onStatusUpdate("Recherche d'adversaires...")
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { listener?.onStatusUpdate("Recherche en cours...") }
            .addOnFailureListener(detailedFailureListener("découverte"))
    }

    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        listener?.onStatusUpdate("Recherche arrêtée.")
    }

    fun requestConnection(endpointId: String) {
        connectionsClient.requestConnection(myName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener(detailedFailureListener("demande de connexion"))
    }

    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener(detailedFailureListener("acceptation de connexion"))
    }

    fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
            .addOnFailureListener(detailedFailureListener("refus de connexion"))
    }

    fun sendPayload(endpointId: String, payloadString: String) {
        val payload = Payload.fromBytes(payloadString.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
            .addOnFailureListener(detailedFailureListener("envoi de payload"))
    }

    fun sendPayload(endpointId: String, payload: Payload) {
        connectionsClient.sendPayload(endpointId, payload)
            .addOnFailureListener(detailedFailureListener("envoi de payload fichier"))
    }

    fun disconnect(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
    }

    fun stopAllEndpoints() {
        connectionsClient.stopAllEndpoints()
    }

    private fun buildEndpointName(myCatalogue: PlayerCatalogue): String {
        val deckCountsCsv = GameConstants.MASTER_DECK_LIST.joinToString(",") { deckName ->
            myCatalogue.decks.find { it.name == deckName }?.cardCount?.toString() ?: "0"
        }
        val recordCsv = "${myCatalogue.wins},${myCatalogue.losses}"
        val latFormatted = String.format(Locale.US, "%.6f", myCatalogue.latitude ?: 0.0)
        val lonFormatted = String.format(Locale.US, "%.6f", myCatalogue.longitude ?: 0.0)
        return "$myName|$deckCountsCsv|$recordCsv|$latFormatted $lonFormatted"
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            try {
                val parts = info.endpointName.split('|')
                if (parts.size == 4) {
                    val name = parts[0]
                    val deckCountsCsv = parts[1].split(',')
                    val recordParts = parts[2].split(',')
                    val locationParts = parts[3].split(' ')

                    val wins = recordParts.getOrNull(0)?.toIntOrNull() ?: 0
                    val losses = recordParts.getOrNull(1)?.toIntOrNull() ?: 0
                    val lat = locationParts.getOrNull(0)?.toDoubleOrNull()
                    val lon = locationParts.getOrNull(1)?.toDoubleOrNull()

                    val reconstructedDecks = GameConstants.MASTER_DECK_LIST.mapIndexedNotNull { index, deckName ->
                        val count = deckCountsCsv.getOrNull(index)?.toIntOrNull() ?: 0
                        if (count > 0) Deck(deckName, count) else null
                    }
                    val colorIndex = abs(endpointId.hashCode()) % turtleColorPalette.size
                    val assignedColor = turtleColorPalette[colorIndex]
                    val partialCatalogue = PlayerCatalogue(name, reconstructedDecks, lat, lon, wins, losses, assignedColor)
                    listener?.onEndpointFound(endpointId, partialCatalogue)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur parsing endpointName: ${info.endpointName}", e)
            }
        }
        override fun onEndpointLost(endpointId: String) { listener?.onEndpointLost(endpointId) }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val opponentName = info.endpointName.split('|').firstOrNull() ?: "Adversaire"
            listener?.onConnectionInitiated(endpointId, opponentName, info.authenticationDigits)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val isSuccess = result.status.statusCode == ConnectionsStatusCodes.STATUS_OK
            if (!isSuccess) {
                Log.e(TAG, "Échec de connexion. Code: ${result.status.statusCode} - ${result.status.statusMessage}")
            }
            listener?.onConnectionResult(endpointId, isSuccess)
        }
        override fun onDisconnected(endpointId: String) { listener?.onDisconnected(endpointId) }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val receivedJson = String(payload.asBytes()!!, StandardCharsets.UTF_8)
                    Log.i(TAG, "[P2P RECV] Payload de type BYTES (métadonnées JSON) reçu de $endpointId.")
                    listener?.onPayloadReceived(endpointId, receivedJson)
                }
                Payload.Type.FILE -> {
                    Log.i(TAG, "[P2P RECV] Payload de type FILE (image) reçu de $endpointId. En attente du transfert complet. Payload ID: ${payload.id}")
                    incomingFilePayloads[payload.id] = payload
                }
                Payload.Type.STREAM -> { /* Non utilisé */ }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payload = incomingFilePayloads.remove(payloadId)
                if (payload != null && payload.type == Payload.Type.FILE) {
                    Log.i(TAG, "[P2P RECV] Transfert du payload ID $payloadId terminé avec succès. Notification du listener pour le traitement du fichier.")
                    listener?.onFilePayloadReceived(endpointId, payload)
                }
            } else if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                Log.e(TAG, "[P2P RECV] Échec du transfert pour le payload ID $payloadId. Suppression du payload en attente.")
                incomingFilePayloads.remove(payloadId)
            }
            listener?.onPayloadTransferUpdate(endpointId, update)
        }
    }
}