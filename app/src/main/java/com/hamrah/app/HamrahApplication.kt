package com.hamrah.app

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hamrah.app.network.NearbyConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HamrahApplication : Application() {

    val connectionManager: NearbyConnectionManager by lazy {
        NearbyConnectionManager(this)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val seenAlertIds = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            connectionManager.alerts.collect { list ->
                for (alert in list) {
                    if (!alert.mine && seenAlertIds.add(alert.id) && !alert.cancelled) {
                        alertUser()
                    }
                }
            }
        }
    }

    private fun alertUser() {
        try {
            vibrate()
        } catch (e: Exception) {
            // Vibration is a bonus; the on-screen alert still appears.
        }
        try {
            playSound()
        } catch (e: Exception) {
            // Sound is a bonus; the on-screen alert still appears.
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return

        val pattern = longArrayOf(0, 500, 300, 500, 300, 500, 300, 500)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun playSound() {
        ringtone?.stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val sound: Ringtone = RingtoneManager.getRingtone(this, uri) ?: return
        sound.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        ringtone = sound
        sound.play()
        handler.postDelayed({ sound.stop() }, 5000L)
    }
}