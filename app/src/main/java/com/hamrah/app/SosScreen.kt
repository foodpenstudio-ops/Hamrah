package com.hamrah.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hamrah.app.network.EmergencyAlert

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
                SosForm(
                    presets = presets,
                    message = message,
                    onMessageChange = { value: String -> message = value },
                    connectedCount = connectedCount,
                    hasAlerts = sortedAlerts.isNotEmpty(),
                    onSendClick = { showConfirm = true }
                )
            }
            items(
                items = sortedAlerts,
                key = { alert: EmergencyAlert -> alert.id }
            ) { alert: EmergencyAlert ->
                AlertCard(
                    alert = alert,
                    onCancel = onCancel,
                    onAcknowledge = onAcknowledge
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Send emergency alert?") },
            text = {
                Text("This will alert $connectedCount connected phone(s) with:\n\n\"$message\"")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onSend(message)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Send SOS") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SosForm(
    presets: List<String>,
    message: String,
    onMessageChange: (String) -> Unit,
    connectedCount: Int,
    hasAlerts: Boolean,
    onSendClick: () -> Unit
) {
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
                            onClick = { onMessageChange(preset) },
                            modifier = Modifier.weight(1f)
                        ) { Text(preset, style = MaterialTheme.typography.labelMedium) }
                    } else {
                        OutlinedButton(
                            onClick = { onMessageChange(preset) },
                            modifier = Modifier.weight(1f)
                        ) { Text(preset, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = message,
            onValueChange = { value: String ->
                if (value.length <= 200) onMessageChange(value)
            },
            label = { Text("Emergency message") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSendClick,
            enabled = connectedCount > 0 && message.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            Text(
                text = "SEND SOS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Alerts", style = MaterialTheme.typography.titleMedium)
        if (!hasAlerts) {
            Text("No alerts yet", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AlertCard(
    alert: EmergencyAlert,
    onCancel: (String) -> Unit,
    onAcknowledge: (String) -> Unit
) {
    val highlight = !alert.mine && !alert.cancelled
    val containerColor = if (highlight) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (highlight) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (alert.mine) "Your alert" else "EMERGENCY from ${alert.senderName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = alert.text,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
            Text(
                text = formatTime(alert.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )

            val stateText: String = if (alert.cancelled) {
                "Cancelled"
            } else if (alert.mine) {
                if (alert.acknowledgedBy.isEmpty()) {
                    "Not acknowledged yet"
                } else {
                    "Acknowledged by: " + alert.acknowledgedBy.joinToString(", ")
                }
            } else if (alert.acknowledgedByMe) {
                "You acknowledged this alert"
            } else {
                ""
            }
            if (stateText.isNotEmpty()) {
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }

            if (!alert.mine && !alert.cancelled && !alert.acknowledgedByMe) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { onAcknowledge(alert.id) }) { Text("Acknowledge") }
            }
            if (alert.mine && !alert.cancelled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { onCancel(alert.id) }) { Text("Cancel alert") }
            }
        }
    }
}