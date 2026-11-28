package com.hamrah.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }

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
            Text("About Hamrah", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = "HAMRAH  ہمراہ",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Version $versionName (prototype)",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))

            AboutSection(
                title = "What it does",
                body = "Hamrah lets nearby Android phones chat, send emergency alerts " +
                    "and share a location without mobile data or an internet connection."
            )
            AboutSection(
                title = "How it works",
                body = "It uses Google Nearby Connections, which needs Google Play " +
                    "services on the phone and uses Bluetooth and Wi-Fi to find and " +
                    "connect to phones close by. Bluetooth and Wi-Fi must be switched on, " +
                    "and on many phones Location must be on too."
            )
            AboutSection(
                title = "Your messages",
                body = "Chat messages are stored only on this phone. Hamrah does not " +
                    "send them to any server."
            )
            AboutSection(
                title = "Limits to know about",
                body = "An SOS or location reaches only the phones you are connected to " +
                    "at that moment. It does not reach distant phones, and it is not a " +
                    "replacement for calling emergency services. Range depends on your " +
                    "phones and surroundings. Some phones stop apps in the background, " +
                    "which can end the connection. This prototype has not been " +
                    "independently security-reviewed."
            )
        }
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = body, style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(16.dp))
}