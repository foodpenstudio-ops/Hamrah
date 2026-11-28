package com.hamrah.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamrah.app.network.ChatMessage
import com.hamrah.app.network.ConnectedDevice
import com.hamrah.app.network.DiscoveredDevice
import com.hamrah.app.network.EmergencyAlert
import com.hamrah.app.network.MessageStatus
import com.hamrah.app.network.PendingRequest
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

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun NearbyScreen(vm: HamrahViewModel = viewModel()) {
    val discovered: List<DiscoveredDevice> by vm.discovered.collectAsStateWithLifecycle()
    val pending: List<PendingRequest> by vm.pending.collectAsStateWithLifecycle()
    val connected: List<ConnectedDevice> by vm.connected.collectAsStateWithLifecycle()
    val messages: List<ChatMessage> by vm.messages.collectAsStateWithLifecycle()
    val alerts: List<EmergencyAlert> by vm.alerts.collectAsStateWithLifecycle()
    val status: String by vm.status.collectAsStateWithLifecycle()
    val running: Boolean by vm.running.collectAsStateWithLifecycle()

    var deviceName by rememberSaveable { mutableStateOf(Build.MODEL ?: "My phone") }
    var permissionMessage by rememberSaveable { mutableStateOf("") }
    var chatWith by rememberSaveable { mutableStateOf<String?>(null) }
    var showSos by rememberSaveable { mutableStateOf(false) }

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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "HAMRAH  ہمراہ",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showSos = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "SOS - Emergency",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = deviceName,
                        onValueChange = { newValue: String -> deviceName = newValue },
                        label = { Text("Your device name") },
                        singleLine = true,
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (running) {
                        OutlinedButton(
                            onClick = { vm.stop() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Stop") }
                    } else {
                        Button(
                            onClick = { permissionLauncher.launch(requiredPermissions()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start finding nearby phones") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Status: $status", style = MaterialTheme.typography.bodyMedium)
                    if (permissionMessage.isNotEmpty()) {
                        Text(
                            text = permissionMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "Connection requests",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        if (pending.isEmpty()) {
                            item { Text("None", style = MaterialTheme.typography.bodyMedium) }
                        }
                        items(
                            items = pending,
                            key = { request: PendingRequest -> "p" + request.endpointId }
                        ) { request: PendingRequest ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        request.name,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "Code: ${request.code}. Check that both phones show the same code.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { vm.accept(request.endpointId) }) {
                                            Text("Accept")
                                        }
                                        OutlinedButton(
                                            onClick = { vm.reject(request.endpointId) }
                                        ) {
                                            Text("Decline")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Connected", style = MaterialTheme.typography.titleMedium)
                        }
                        if (connected.isEmpty()) {
                            item { Text("None", style = MaterialTheme.typography.bodyMedium) }
                        }
                        items(
                            items = connected,
                            key = { device: ConnectedDevice -> "c" + device.endpointId }
                        ) { device: ConnectedDevice ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        device.name,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { chatWith = device.endpointId }) {
                                            Text("Chat")
                                        }
                                        OutlinedButton(
                                            onClick = { vm.disconnect(device.endpointId) }
                                        ) {
                                            Text("Disconnect")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Nearby phones", style = MaterialTheme.typography.titleMedium)
                        }
                        if (nearby.isEmpty()) {
                            item {
                                Text(
                                    if (running) "Searching..." else "Tap Start to search",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        items(
                            items = nearby,
                            key = { device: DiscoveredDevice -> "d" + device.endpointId }
                        ) { device: DiscoveredDevice ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        device.name,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Button(onClick = { vm.connect(device.endpointId) }) {
                                        Text("Connect")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SosScreen(
    alerts: List<EmergencyAlert>,
    connectedCount: Int,
    onSend: (String) -> Unit,
    onCancel: (String) -> Unit,
    onAcknowledge: (String) -> Unit,
    onBack: () -> Unit
) {
    var message by rememberSaveable { mutableStateOf("I NEED HELP") }
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    BackHandler { onBack() }

    val presets: List<String> = listOf(
        "I NEED HELP",
        "MEDICAL EMERGENCY",
        "NEED ASSISTANCE",
        "PLEASE CONTACT ME"
    )
    val sortedAlerts: List<EmergencyAlert> = alerts.sortedByDescending { a -> a.timestamp }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Emergency SOS", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column {
                    Text(
                        text = if (connectedCount > 0) {
                            "An SOS reaches only the $connectedCount phone(s) you are connected to right now."
                        } else {
                            "Connect to at least one nearby phone first. An SOS reaches only phones you are connected to right now."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (connectedCount > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    presets.chunked(2).forEach { rowItems: List<String> ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { preset: String ->
                                if (message == preset) {
                                    Button(
                                        onClick = { message = preset },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(preset, style = MaterialTheme.typography.labelMedium) }
                                } else {
                                    OutlinedButton(
                                        onClick = { message = prese