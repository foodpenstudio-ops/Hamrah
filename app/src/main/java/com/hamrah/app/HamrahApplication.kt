package com.hamrah.app

import android.app.Application
import com.hamrah.app.network.NearbyConnectionManager

class HamrahApplication : Application() {

    val connectionManager: NearbyConnectionManager by lazy {
        NearbyConnectionManager(this)
    }
}