package com.hamrah.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hamrah.app.network.NearbyConnectionManager

class HamrahViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = NearbyConnectionManager(application)

    val discovered = manager.discovered
    val pending = manager.pending
    val connected = manager.connected
    val status = manager.status
    val running = manager.running

    fun start(deviceName: String) = manager.start(deviceName)
    fun stop() = manager.stop()
    fun connect(endpointId: String) = manager.requestConnection(endpointId)
    fun accept(endpointId: String) = manager.accept(endpointId)
    fun reject(endpointId: String) = manager.reject(endpointId)
    fun disconnect(endpointId: String) = manager.disconnect(endpointId)

    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}