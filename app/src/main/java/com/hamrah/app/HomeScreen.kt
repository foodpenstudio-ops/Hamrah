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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
        Text(
            text = "HAMRAH  ہمراہ",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onSos,
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
                Text("Connection requests", style = MaterialTheme.typography.titleMedium)
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
                        Text(request.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Code: ${request.code}. Check that both phones show the same code.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAccept(request.endpointId) }) {
                                Text("Accept")
                            }
                            OutlinedButton(onClick = { onReject(request.endpointId) }) {
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
                        Text(device.name, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onChat(device.endpointId) }) {
                                Text("Chat")
                            }
                            OutlinedButton(onClick = { onDisconnect(device.endpointId) }) {
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
                        Text(device.name, style = MaterialTheme.typography.titleSmall)
                        Button(onClick = { onConnect(device.endpointId) }) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}