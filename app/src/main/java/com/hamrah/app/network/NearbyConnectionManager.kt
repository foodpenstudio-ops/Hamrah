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
import com.hamrah.app.data.local.MessageDao
import com.hamrah.app.data.local.MessageDatabase
import com.hamrah.app.data.local.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val status: MessageStatus,
    val peerName: String = ""
)

data class EmergencyAlert(
    val id: String,
    val endpointId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val mine: Boolean,
    val cancelled: Boolean = false,
    val acknowledgedBy: List<String> = emptyList(),
    val acknowledgedByMe: Boolean = false
)

class NearbyConnectionManager(context: Context) {

    companion object {
        const val SERVICE_ID = "com.hamrah.app.nearby"
        const val MAX_MESSAGE_LENGTH = 1000
        const val MAX_SOS_LENGTH = 200
        const val MAX_PAYLOAD_BYTES = 8192
        const val SOS_COOLDOWN_MS = 10_000L
    }

    private val client: ConnectionsClient =
        Nearby.getConnectionsClient(context.applicationContext)

    private val dao: MessageDao = MessageDatabase.get(context).messageDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val strategy = Strategy.P2P_CLUSTER

    private var localName: String = "Hamrah user"
    private var lastSosTime: Long = 0L

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

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts: StateFlow<List<EmergencyAlert>> = _alerts.asStateFlow()

    private val _status = MutableStateFlow("Not started")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        scope.launch {
            try {
                val saved: List<MessageEntity> = dao.getAll()
                val loaded: List<ChatMessage> = saved.map { entity ->
                    var status = try {
                        MessageStatus.valueOf(entity.status)
                    } catch (e: Exception) {
                        MessageStatus.FAILED
                    }
                    // A message still pending from an earlier session was never confirmed.
                    if (status == MessageStatus.PENDING) {
                        status = MessageStatus.FAILED
                        dao.updateStatus(entity.id, MessageStatus.FAILED.name)
                    }
                    ChatMessage(
                        id = entity.id,
                        endpointId = "",
                        senderName = entity.senderName,
                        text = entity.text,
                        timestamp = entity.timestamp,
                        mine = entity.mine,
                        status = status,
                        peerName = entity.peerName
                    )
                }
                _messages.update { current ->
                    val currentIds = current.map { m -> m.id }.toSet()
                    (loaded.filter { m -> m.id !in currentIds } + current)
                        .sortedBy { m -> m.timestamp }
                }
            } catch (e: Exception) {
                _status.value = "Could not load saved messages"
            }
        }
    }

    private fun saveMessage(message: ChatMessage) {
        scope.launch {
            try {
                dao.insert(
                    MessageEntity(
                        id = message.id,
                        peerName = message.peerName,
                        senderName = message.senderName,
                        text = message.text,
                        timestamp = message.timestamp,
                        mine = message.mine,
                        status = message.status.name
                    )
                )
            } catch (e: Exception) {
                // Saving failed; the message is still shown on screen.
            }
        }
    }

    private fun setMessageStatus(messageId: String, newStatus: MessageStatus) {
        _messages.update { list ->
            list.map { m -> if (m.id == messageId) m.copy(status = newStatus) else m }
        }
        scope.launch {
            try {
                dao.updateStatus(messageId, newStatus.name)
            } catch (e: Exception) {
                // Ignore storage problems.
            }
        }
    }

    private fun peerNameFor(endpointId: String): String {
        return _connected.value.firstOrNull { d -> d.endpointId == endpointId }?.name
            ?: "Unknown"
    }

    private fun updateAlert(alertId: String, change: (EmergencyAlert) -> EmergencyAlert) {
        _alerts.update { list ->
            list.map { a -> if (a.id == alertId) change(a) else a }
        }
    }

    private fun handleChatMessage(endpointId: String, json: JSONObject) {
        val id = json.optString("id").trim()
        val text = json.optString("text").trim()
        if (id.isEmpty() || id.length > 64) return
        if (text.isEmpty() || text.length > MAX_MESSAGE_LENGTH) return
        if (_messages.value.any { m -> m.id == id }) return

        val senderName = peerNameFor(endpointId)
        val message = ChatMessage(
            id = id,
            endpointId = endpointId,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis(),
            mine = false,
            status = MessageStatus.RECEIVED,
            peerName = senderName
        )
        _messages.update { list -> list + message }
        saveMessage(message)
    }

    private fun handleSos(endpointId: String, json: JSONObject) {
        val id = json.optString("id").trim()
        val text = json.optString("text").trim()
        if (id.isEmpty() || id.length > 64) return
        if (text.isEmpty() || text.length > MAX_SOS_LENGTH) return
        if (_alerts.value.any { a -> a.id == id }) return

        val senderName = peerNameFor(endpointId)
        val alert = EmergencyAlert(
            id = id,
            endpointId = endpointId,
            senderName = senderName,
            text = text,
            timestamp = System.currentTimeMillis(),
            mine = false
        )
        _alerts.update { list -> list + alert }
        _status.value = "EMERGENCY from $senderName"
    }

    private fun handleAck(endpointId: String, json: JSONObject) {
        val id = json.optString("id").trim()
        val ackName = peerNameFor(endpointId)
        val alert = _alerts.value.firstOrNull { a -> a.id == id && a.mine } ?: return
        if (alert.acknowledgedBy.contains(ackName)) return
        updateAlert(id) { a -> a.copy(acknowledgedBy = a.acknowledgedBy + ackName) }
    }

    private fun handleCancel(endpointId: String, json: JSONObject) {
        val id = json.optString("id").trim()
        val alert = _alerts.value.firstOrNull { a -> a.id == id && !a.mine } ?: return
        // Only the phone that sent the alert may cancel it.
        if (alert.endpointId != endpointId) return
        updateAlert(id) { a -> a.copy(cancelled = true) }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            if (bytes.size > MAX_PAYLOAD_BYTES) return

            try {
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                when (json.optString("t")) {
                    "msg" -> handleChatMessage(endpointId, json)
                    "sos" -> handleSos(endpointId, json)
                    "ack" -> handleAck(endpointId, json)
                    "cancel" -> handleCancel(endpointId, json)
                    else -> {
                        // Unknown type: ignore.
                    }
                }
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

        val peerName = peerNameFor(endpointId)

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
            status = MessageStatus.PENDING,
            peerName = peerName
        )
        _messages.update { list -> list + message }
        saveMessage(message)
        outgoing[payload.id] = id

        client.sendPayload(endpointId, payload)
            .addOnFailureListener {
                outgoing.remove(payload.id)
                setMessageStatus(id, MessageStatus.FAILED)
            }
    }

    fun sendSos(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_SOS_LENGTH) return

        val targets: List<String> = _connected.value.map { d -> d.endpointId }
        if (targets.isEmpty()) {
            _status.value = "SOS not sent: no connected phones"
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSosTime < SOS_COOLDOWN_MS) {
            _status.value = "SOS was just sent. Please wait a few seconds."
            return
        }
        lastSosTime = now

        val id = UUID.randomUUID().toString()
        val json = JSONObject()
            .put("t", "sos")
            .put("id", id)
            .put("text", clean)
        val payload = Payload.fromBytes(json.toString().toByteArray(Charsets.UTF_8))

        val alert = EmergencyAlert(
            id = id,
            endpointId = "",
            senderName = localName,
            text = clean,
            timestamp = now,
            mine = true
        )
        _alerts.update { list -> list + alert }
        _status.value = "SOS sent to ${targets.size} connected phone(s)"

        client.sendPayload(targets, payload)
            .addOnFailureListener { e ->
                _status.value = "SOS could not be sent: ${e.message}"
            }
    }

    fun cancelSos(alertId: String) {
        val alert = _alerts.value.firstOrNull { a -> a.id == alertId && a.mine } ?: return
        if (alert.cancelled) return
        updateAlert(alertId) { a -> a.copy(cancelled = true) }

        val targets: List<String> = _connected.value.map { d -> d.endpointId }
        if (targets.isEmpty()) return

        val json = JSONObject()
            .put("t", "cancel")
            .put("id", alertId)
        val payload = Payload.fromBytes(json.toString().toByteArray(Charsets.UTF_8))
        client.sendPayload(targets, payload)
    }

    fun acknowledgeAlert(alertId: String) {
        val alert = _alerts.value.firstOrNull { a -> a.id == alertId && !a.mine } ?: return
        if (alert.acknowledgedByMe) return
        updateAlert(alertId) { a -> a.copy(acknowledgedByMe = true) }

        val json = JSONObject()
            .put("t", "ack")
            .put("id", alertId)
        val payload = Payload.fromBytes(json.toString().toByteArray(Charsets.UTF_8))
        client.sendPayload(alert.endpointId, payload)
    }
}