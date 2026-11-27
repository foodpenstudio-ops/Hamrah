package com.hamrah.app.network

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.util.UUID

data class DiscoveredDevice(val endpointId: String, val name: String)

data class PendingRequest(
    val endpointId: String,
    val name: String,
    val code: String,
    val incoming: Boolean
)

data class ConnectedDevice(val endpointId: String, val name: String)

enum class MessageStatus { PENDING, DELIVERED, FAILED, RECEIVED }

data class ChatMessage(
    val id: String,
    val endpointId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val mine: Boolean,
    val status: MessageStatus
)

class NearbyConnectionManager(context: Context) {

    companion object {
        const val SERVICE_ID = "com.hamrah.app.nearby"
        const val MAX_MESSAGE_LENGTH = 1000
        const val MAX_PAYLOAD_BYTES = 8192
    }

    private val client: ConnectionsClient =
        Nearby.getConnectionsClient(context.applicationContext)

    private val strategy = Strategy.P2P_CLUSTER

    private var localName: String = "Hamrah user"

    // Remembers which sent payload belongs to which message.
    private val outgoing = mutableMapOf<Long, String>()

    private val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discovered: StateFlow<List<DiscoveredDevice>> = _discovered.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingRequest>>(emptyList())
    val pending: StateFlow<List<PendingRequest>> = _pending.asStateFlow()

    private val _connected = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connected: StateFlow<List<ConnectedDevice>> = _connected.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow("Not started")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private fun setMessageStatus(messageId: String, newStatus: MessageStatus) {
        _messages.update { list ->
            list.map { m -> if (m.id == messageId) m.copy(status = newStatus) else m }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            if (bytes.size > MAX_PAYLOAD_BYTES) return

            try {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                if (json.optString("t") != "msg") return

                val id = json.optString("id").trim()
                val text = json.optString("text").trim()
                if (id.isEmpty() || id.length > 64) return
                if (text.isEmpty() || text.length > MAX_MESSAGE_LENGTH) return

                if (_messages.value.any { m -> m.id == id }) return

                val senderName = _connected.value
                    .firstOrNull { d -> d.endpointId == endpointId }?.name ?: "Unknown"

                val message = ChatMessage(
                    id = id,
                    endpointId = endpointId,
                    senderName = senderName,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    mine = false,
                    status = MessageStatus.RECEIVED
                )
                _messages.update { list -> list + message }
            } catch (e: Exception) {
                // Ignore anything that is not a valid Hamrah message.
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            val messageId = outgoing[update.payloadId] ?: return
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    setMessageStatus(messageId, MessageStatus.DELIVERED)
                    outgoing.remove(update.payloadId)
                }
                PayloadTransferUpdate.Status.FAILURE,
                PayloadTransferUpdate.Status.CANCELED -> {
                    setMessageStatus(messageId, MessageStatus.FAILED)
                    outgoing.remove(update.payloadId)
                }
                else -> {
                    // Still in progress.
                }
            }
        }
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val request = PendingRequest(
                endpointId = endpointId,
                name = info.endpointName,
                code = info.authenticationDigits,
                incoming = info.isIncomingConnection
            )
            _pending.update { list ->
                list.filter { it.endpointId != endpointId } + request
            }
            _status.value = "Connection request from ${info.endpointName}"
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val pendingItem = _pending.value.firstOrNull { it.endpointId == endpointId }
            val deviceName = pendingItem?.name ?: "device"
            _pending.update { list -> list.filter { it.endpointId != endpointId } }

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    _connected.update { list ->
                        list.filter { it.endpointId != endpointId } +
                            ConnectedDevice(endpointId, deviceName)
                    }
                    _status.value = "Connected to $deviceName"
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    _status.value = "Connection with $deviceName was declined"
                }
                else -> {
                    _status.value = "Could not connect to $deviceName"
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val name = _connected.value.firstOrNull { it.endpointId == endpointId }?.name
                ?: "device"
            _connected.update { list -> list.filter { it.endpointId != endpointId } }
            _status.value = "Disconnected from $name"
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _discovered.update { list ->
                list.filter { it.endpointId != endpointId } +
                    DiscoveredDevice(endpointId, info.endpointName)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _discovered.update { list -> list.filter { it.endpointId != endpointId } }
        }
    }

    fun start(deviceName: String) {
        if (_running.value) return
        localName = deviceName.trim().ifEmpty { "Hamrah user" }
        _running.value = true
        _status.value = "Starting..."

        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()

        client.startAdvertising(localName, SERVICE_ID, lifecycleCallback, advertisingOptions)
            .addOnSuccessListener {
                _status.value = "Visible to nearby phones. Searching..."
            }
            .addOnFailureListener { e ->
                _running.value = false
                _status.value = "Could not become visible: ${e.message}"
            }

        client.startDiscovery(SERVICE_ID, discoveryCallback, discoveryOptions)
            .addOnFailureListener { e ->
                _running.value = false
                _status.value = "Could not search: ${e.message}"
            }
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _discovered.value = emptyList()
        _pending.value = emptyList()
        _connected.value = emptyList()
        outgoing.clear()
        _running.value = false
        _status.value = "Stopped"
    }

    fun requestConnection(endpointId: String) {
        _status.value = "Sending connection request..."
        client.requestConnection(localName, endpointId, lifecycleCallback)
            .addOnFailureListener { e ->
                _status.value = "Request failed: ${e.message}"
            }
    }

    fun accept(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener { e ->
                _status.value = "Accept failed: ${e.message}"
            }
    }

    fun reject(endpointId: String) {
        client.rejectConnection(endpointId)
        _pending.update { list -> list.filter { it.endpointId != endpointId } }
    }

    fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
        _connected.update { list -> list.filter { it.endpointId != endpointId } }
    }

    fun sendMessage(endpointId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_MESSAGE_LENGTH) return

        val id = UUID.randomUUID().toString()
        val json = JSONObject()
            .put("t", "msg")
            .put("id", id)
            .put("text", clean)
        val payload = Payload.fromBytes(json.toString().toByteArray(Charsets.UTF_8))

        val message = ChatMessage(
            id = id,
            endpointId = endpointId,
            senderName = localName,
            text = clean,
            timestamp = System.currentTimeMillis(),
            mine = true,
            status = MessageStatus.PENDING
        )
        _messages.update { list -> list + message }
        outgoing[payload.id] = id

        client.sendPayload(endpointId, payload)
            .addOnFailureListener {
                outgoing.remove(payload.id)
                setMessageStatus(id, MessageStatus.FAILED)
            }
    }
}