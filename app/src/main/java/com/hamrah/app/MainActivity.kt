package com.hamrah.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamrah.app.network.ChatMessage
import com.hamrah.app.network.ConnectedDevice
import com.hamrah.app.network.DiscoveredDevice
import com.hamrah.app.network.EmergencyAlert
import com.hamrah.app.network.PendingRequest
import com.hamrah.app.network.SharedLocation
import com.hamrah.app.viewmodel.HamrahViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NearbyScreen()
                }
            }
        }
    }
}

private fun requiredPermissions(): Array<String> {
    val list = mutableListOf<String>()
    list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    list.add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= 31) {
        list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        list.add(Manifest.permission.BLUETOOTH_CONNECT)
        list.add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT >= 33) {
        list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    return list.toTypedArray()
}

internal fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun NearbyScreen(vm: HamrahViewModel = viewModel()) {
    val discovered: List<DiscoveredDevice> by vm.discovered.collectAsStateWithLifecycle()
    val pending: List<PendingRequest> by vm.pending.collectAsStateWithLifecycle()
    val connected: List<ConnectedDevice> by vm.connected.collectAsStateWithLifecycle()
    val messages: List<ChatMessage> by vm.messages.collectAsStateWithLifecycle()
    val alerts: List<EmergencyAlert> by vm.alerts.collectAsStateWithLifecycle()
    val locations: List<SharedLocation> by vm.locations.collectAsStateWithLifecycle()
    val sharing: Boolean by vm.sharingLocation.collectAsStateWithLifecycle()
    val locationMessage: String by vm.locationMessage.collectAsStateWithLifecycle()
    val status: String by vm.status.collectAsStateWithLifecycle()
    val running: Boolean by vm.running.collectAsStateWithLifecycle()

    var deviceName by rememberSaveable { mutableStateOf(Build.MODEL ?: "My phone") }
    var permissionMessage by rememberSaveable { mutableStateOf("") }
    var chatWith by rememberSaveable { mutableStateOf<String?>(null) }
    var showSos by rememberSaveable { mutableStateOf(false) }
    var showLocation by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results: Map<String, Boolean> ->
        if (results.values.all { granted -> granted }) {
            permissionMessage = ""
            vm.start(deviceName)
        } else {
            permissionMessage =
                "Hamrah needs these permissions to find and connect to nearby phones. " +
                    "Tap Start again and allow them."
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results: Map<String, Boolean> ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            vm.startSharingLocation()
        } else {
            vm.locationPermissionDenied()
        }
    }

    val connectedIds: Set<String> = connected.map { device -> device.endpointId }.toSet()
    val nearby: List<DiscoveredDevice> =
        discovered.filter { device -> device.endpointId !in connectedIds }

    val currentChat: String? = chatWith

    val incomingActive: EmergencyAlert? = alerts.lastOrNull { a ->
        !a.mine && !a.cancelled && !a.acknowledgedByMe
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        if (!showSos && incomingActive != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { showSos = true }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "EMERGENCY from ${incomingActive.senderName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = incomingActive.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Tap to open and acknowledge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (showSos) {
                SosScreen(
                    alerts = alerts,
                    connectedCount = connected.size,
                    onSend = { text: String -> vm.sendSos(text) },
                    onCancel = { id: String -> vm.cancelSos(id) },
                    onAcknowledge = { id: String -> vm.acknowledgeAlert(id) },
                    onBack = { showSos = false }
                )
            } else if (showLocation) {
                LocationScreen(
                    sharing = sharing,
                    message = locationMessage,
                    connectedCount = connected.size,
                    locations = locations,
                    onStartShare = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onStopShare = { vm.stopSharingLocation() },
                    onBack = { showLocation = false }
                )
            } else if (currentChat != null) {
                val chatDevice: ConnectedDevice? =
                    connected.firstOrNull { device -> device.endpointId == currentChat }
                val chatPeerName: String = chatDevice?.name ?: ""
                val chatMessages: List<ChatMessage> = messages.filter { message ->
                    message.endpointId == currentChat ||
                        (chatPeerName.isNotEmpty() && message.peerName == chatPeerName)
                }
                ChatScreen(
                    deviceName = chatDevice?.name ?: "Disconnected device",
                    isConnected = chatDevice != null,
                    messages = chatMessages,
                    onSend = { text: String -> vm.send(currentChat, text) },
                    onBack = { chatWith = null }
                )
            } else {
                HomeScreen(
                    deviceName = deviceName,
                    onDeviceNameChange = { value: String -> deviceName = value },
                    running = running,
                    status = status,
                    permissionMessage = permissionMessage,
                    pending = pending,
                    connected = connected,
                    nearby = nearby,
                    onSos = { showSos = true },
                    onLocation = { showLocation = true },
                    onStart = { permissionLauncher.launch(requiredPermissions()) },
                    onStop = { vm.stop() },
                    onAccept = { id: String -> vm.accept(id) },
                    onReject = { id: String -> vm.reject(id) },
                    onConnect = { id: String -> vm.connect(id) },
                    onDisconnect = { id: String -> vm.disconnect(id) },
                    onChat = { id: String -> chatWith = id }
                )
            }
        }
    }
}