package com.hamrah.app.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.hamrah.app.network.NearbyConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HamrahViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = NearbyConnectionManager(application)
    private val locationManager: LocationManager =
        application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val discovered = manager.discovered
    val pending = manager.pending
    val connected = manager.connected
    val messages = manager.messages
    val alerts = manager.alerts
    val locations = manager.locations
    val status = manager.status
    val running = manager.running

    private val _sharingLocation = MutableStateFlow(false)
    val sharingLocation: StateFlow<Boolean> = _sharingLocation.asStateFlow()

    private val _locationMessage = MutableStateFlow("")
    val locationMessage: StateFlow<String> = _locationMessage.asStateFlow()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            manager.sendLocation(location.latitude, location.longitude, location.accuracy)
        }

        @Deprecated("Needed for older Android versions")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
        }
    }

    fun start(deviceName: String) = manager.start(deviceName)

    fun stop() {
        stopSharingLocation()
        manager.stop()
    }

    fun connect(endpointId: String) = manager.requestConnection(endpointId)
    fun accept(endpointId: String) = manager.accept(endpointId)
    fun reject(endpointId: String) = manager.reject(endpointId)
    fun disconnect(endpointId: String) = manager.disconnect(endpointId)
    fun send(endpointId: String, text: String) = manager.sendMessage(endpointId, text)

    fun sendSos(text: String) = manager.sendSos(text)
    fun cancelSos(alertId: String) = manager.cancelSos(alertId)
    fun acknowledgeAlert(alertId: String) = manager.acknowledgeAlert(alertId)

    @SuppressLint("MissingPermission")
    fun startSharingLocation() {
        if (_sharingLocation.value) return
        try {
            val providers = mutableListOf<String>()
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                providers.add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                providers.add(LocationManager.NETWORK_PROVIDER)
            }
            if (providers.isEmpty()) {
                _locationMessage.value =
                    "Location is switched off. Turn on Location in your phone settings."
                return
            }

            for (provider in providers) {
                locationManager.requestLocationUpdates(
                    provider,
                    10_000L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            val last: Location? = providers
                .mapNotNull { p -> locationManager.getLastKnownLocation(p) }
                .maxByOrNull { l -> l.time }
            if (last != null) {
                manager.sendLocation(last.latitude, last.longitude, last.accuracy)
            }

            _sharingLocation.value = true
            _locationMessage.value =
                "Sharing your location with connected phones. Waiting for a GPS position..."
        } catch (e: SecurityException) {
            _locationMessage.value = "Location permission was not granted."
        } catch (e: Exception) {
            _locationMessage.value = "Could not start location: ${e.message}"
        }
    }

    fun stopSharingLocation() {
        if (!_sharingLocation.value) return
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Nothing to clean up.
        }
        _sharingLocation.value = false
        _locationMessage.value = "You stopped sharing your location."
        manager.sendLocationStopped()
    }

    fun locationPermissionDenied() {
        _locationMessage.value =
            "Location permission is needed to share your location. Chat and SOS still work without it."
    }

    override fun onCleared() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            // Nothing to clean up.
        }
        manager.stop()
        super.onCleared()
    }
}