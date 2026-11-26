package com.hamrah.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.nearby.connection.Payload
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    private val serviceId = "com.hamrah.app"
    private val strategy = Strategy.P2P_CLUSTER
    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val messages = mutableStateListOf<String>()
    private var status by mutableStateOf("Not connected")
    private var messageInput by mutableStateOf("")
    private val connected = mutableSetOf<String>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startNearby()
        else status = "Permissions are required to find nearby phones."
    }

    private fun requiredPermissions(): Array<String> {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            p += Manifest.permission.BLUETOOTH_ADVERTISE
            p += Manifest.permission.BLUETOOTH_CONNECT
            p += Manifest.permission.BLUETOOTH_SCAN
        } else {
            p += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        return p.distinct().toTypedArray()
    }

    private fun begin() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        else startNearby()
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let {
                messages.add("Peer: " + String(it, StandardCharsets.UTF_8))
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
            status = "Connecting to ${info.endpointName}..."
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected.add(endpointId)
                status = "Connected to a nearby phone"
            } else status = "Connection failed: ${result.status.statusCode}"
        }
        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            status = "Phone disconnected"
        }
    }

    private fun startNearby() {
        status = "Searching for nearby Hamrah phones..."
        client.startAdvertising("Hamrah user", serviceId, connectionLifecycle,
            AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { status = "Advertising error: ${it.localizedMessage}" }
        client.startDiscovery(serviceId, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                client.requestConnection("Hamrah user", id, connectionLifecycle)
                    .addOnFailureListener { status = "Connection request failed" }
            }
            override fun onEndpointLost(id: String) {}
        }, DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnFailureListener { status = "Discovery error: ${it.localizedMessage}" }
    }

    private fun sendMessage() {
        val msg = messageInput.trim()
        if (msg.isEmpty()) return
        if (connected.isEmpty()) {
            messages.add("You (not sent): $msg")
            status = "No connected phone. Keep both phones searching."
            return
        }
        val payload = Payload.fromBytes(msg.toByteArray(StandardCharsets.UTF_8))
        connected.toList().forEach { client.sendPayload(it, payload) }
        messages.add("You: $msg")
        messageInput = ""
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var draft by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hamrah", style = MaterialTheme.typography.headlineLarge)
                    Text("Offline nearby messaging • Early test build")
                    Text(status, color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { begin() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Find Nearby Devices")
                    }
                    OutlinedTextField(value = draft, onValueChange = { draft = it },
                        label = { Text("Type your message") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { messageInput = draft; sendMessage(); draft = "" },
                        modifier = Modifier.fillMaxWidth()) { Text("Send Message") }
                    Button(onClick = {
                        messageInput = "EMERGENCY: I need help."
                        sendMessage()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Emergency Help") }
                    Text("Messages", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        items(messages) { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                    Text("Test with two phones nearby. Keep the app open on both devices.")
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        client.stopAdvertising()
        client.stopDiscovery()
    }
}
