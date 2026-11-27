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

data class DiscoveredDevice(val endpointId: String, val name: String)

data class PendingRequest(
    val endpointId: String,
    val name: String,
    val code: String,
    val incoming: Boolean
)

data class ConnectedDevice(val endpointId: String, val name: String)

class NearbyConnectionManager(context: Context) {

    companion object {
        const val SERVICE_ID = "com.hamrah.app.nearby"
    }

    private val client: ConnectionsClient =
        Nearby.getConnectionsClient(context.applicationContext)

    private val strategy = Strategy.P2P_CLUSTER

    private var localName: String = "Hamrah user"

    private val _discovered = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discovered: StateFlow<List<DiscoveredDevice>> = _discovered.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingRequest>>(emptyList())
    val pending: StateFlow<List<PendingRequest>> = _pending.asStateFlow()

    private val _connected = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val connected: StateFlow<List<ConnectedDevice>> = _connected.asStateFlow()

    private val _status = MutableStateFlow("Not started")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // Messages will be handled in Phase 3.
        }

        override fun onPayloadTransferUpdate(
            endpointId: String,
            update: PayloadTransferUpdate
        ) {
            // Not needed yet.
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
}