package com.hamrah.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hamrah.app.network.SharedLocation
import java.util.Locale

private fun coordinates(location: SharedLocation): String {
    return String.format(
        Locale.US,
        "%.6f, %.6f",
        location.latitude,
        location.longitude
    )
}

private fun openInMaps(context: Context, location: SharedLocation) {
    val lat = String.format(Locale.US, "%.6f", location.latitude)
    val lon = String.format(Locale.US, "%.6f", location.longitude)
    val label = Uri.encode(location.senderName)
    val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        // No maps app installed: the coordinates stay visible on screen.
    }
}

@Composable
fun LocationScreen(
    sharing: Boolean,
    message: String,
    connectedCount: Int,
    locations: List<SharedLocation>,
    onStartShare: () -> Unit,
    onStopShare: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Location sharing", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your location is shared only when you tap Share, only with the " +
                "phones you are connected to, and you can stop at any time.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (sharing) {
            OutlinedButton(
                onClick = onStopShare,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Stop sharing my location") }
        } else {
            Button(
                onClick = onStartShare,
                enabled = connectedCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share my location") }
            if (connectedCount == 0) {
                Text(
                    text = "Connect to at least one nearby phone first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Locations shared with you", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (locations.isEmpty()) {
            Text(
                text = "No one is sharing a location with you right now.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = locations,
                key = { location: SharedLocation -> location.senderName }
            ) { location: SharedLocation ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            location.senderName,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            coordinates(location),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Accuracy about ${location.accuracyMeters.toInt()} m. " +
                                "Updated ${formatTime(location.timestamp)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { openInMaps(context, location) }) {
                            Text("Open in maps")
                        }
                    }
                }
            }
        }
    }
}