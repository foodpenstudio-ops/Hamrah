package com.hamrah.app

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object ThemePreference {
    private const val FILE = "hamrah_settings"
    private const val KEY = "theme_mode"

    // 0 = follow the phone, 1 = light, 2 = dark
    fun load(context: Context): Int {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY, 0)
    }

    fun save(context: Context, mode: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY, mode)
            .apply()
    }
}

@Composable
fun SettingsScreen(
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val options: List<Pair<Int, String>> = listOf(
        0 to "System",
        1 to "Light",
        2 to "Dark"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option: Pair<Int, String> ->
                    if (themeMode == option.first) {
                        Button(
                            onClick = { onThemeChange(option.first) },
                            modifier = Modifier.weight(1f)
                        ) { Text(option.second) }
                    } else {
                        OutlinedButton(
                            onClick = { onThemeChange(option.first) },
                            modifier = Modifier.weight(1f)
                        ) { Text(option.second) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text("Staying connected", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "If Hamrah disconnects when you leave the app, do not swipe it " +
                    "away. Press Home instead. Some phones also let you lock the app in " +
                    "the recent apps screen, or allow background activity in the phone's " +
                    "battery settings. The menu names differ from phone to phone.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onAbout,
                modifier = Modifier.fillMaxWidth()
            ) { Text("About Hamrah") }
        }
    }
}