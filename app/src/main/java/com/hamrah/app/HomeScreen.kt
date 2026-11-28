package com.hamrah.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hamrah.app.network.ConnectedDevice
import com.hamrah.app.network.DiscoveredDevice
import com.hamrah.app.network.PendingRequest

@Composable
fun HomeScreen(
    deviceName: String,
    onDeviceNameChange: (String) -> Unit,
    running: Boolean,
    status: String,
    permissionMessage: String,
    pending: List<PendingRequest>,
    connected: List<ConnectedDevice>,
    nearby: List<DiscoveredDevice>,
    onSos: () -> Unit,
    onLocation: () -> Unit,
    onSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onChat: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Hamrah",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ہمراہ",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (running) "${connected.size} connected" else "Not searching",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Status: $status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (permissionMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = permissionMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = onDeviceNameChange,
                    label = { Text("Your device name") },
                    singleLine = true,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (running) {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Stop") }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start finding nearby phones") }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSos,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text(
                text = "SOS - Emergency",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onLocation,
                modifier = Modifier.weight(1f)
            ) { Text("Location") }
            FilledTonalButton(
                onClick = onSettings,
                modifier = Modifier.weight(1f)
            ) { Text("Settings") }
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionTitle("Connection requests", pending.size) }
            if (pending.isEmpty()) {
                item { EmptyHint("No requests right now") }
            }
            items(
                items = pending,
                key = { request: PendingRequest -> "p" + request.endpointId }
            ) { request: PendingRequest ->
                PendingCard(request, onAccept, onReject)
            }

            item { SectionTitle("Connected", connected.size) }
            if (connected.isEmpty()) {
                item { EmptyHint("Not connected to anyone yet") }
            }
            items(
                items = connected,
                key = { device: ConnectedDevice -> "c" + device.endpointId }
            ) { device: ConnectedDevice ->
                ConnectedCard(device, onChat, onDisconnect)
            }

            item { SectionTitle("Nearby phones", nearby.size) }
            if (nearby.isEmpty()) {
                item {
                    EmptyHint(if (running) "Searching..." else "Tap Start to search")
                }
            }
            items(
                items = nearby,
                key = { device: DiscoveredDevice -> "d" + device.endpointId }
            ) { device: DiscoveredDevice ->
                NearbyCard(device, onConnect)
            }
        }
    }
}