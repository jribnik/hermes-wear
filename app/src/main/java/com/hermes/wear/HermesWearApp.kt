package com.hermes.wear

import android.app.Application
import com.hermes.wear.data.repository.PreferenceHelper
import com.hermes.wear.service.HermesConnectionService

/**
 * Application class for Hermes Wear.
 * Initializes the background connection service on app start if auto-connect is enabled.
 */
class HermesWearApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val prefs = PreferenceHelper(this)
        if (prefs.autoConnect) {
            HermesConnectionService.start(this)
        }
    }
}
