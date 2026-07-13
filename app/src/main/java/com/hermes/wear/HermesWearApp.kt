package com.hermes.wear

import android.app.Application
import com.hermes.wear.data.network.HermesApiClient
import com.hermes.wear.data.repository.PreferenceHelper
import com.hermes.wear.service.HermesConnectionService

/**
 * Application class for Hermes Wear.
 * Creates a single shared HermesApiClient and starts the background
 * connection service if auto-connect is enabled.
 *
 * Architecture note: the service owns the persistent WebSocket connection;
 * the ViewModel uses the same shared client for HTTP requests (send message,
 * approve/deny) but does NOT open a second WebSocket. This prevents
 * duplicate connections with the same client ID.
 */
class HermesWearApp : Application() {

    companion object {
        lateinit var instance: HermesWearApp
            private set
    }

    lateinit var preferenceHelper: PreferenceHelper
        private set

    val apiClient: HermesApiClient by lazy {
        HermesApiClient(preferenceHelper.serverUrl)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferenceHelper = PreferenceHelper(this)

        if (preferenceHelper.autoConnect) {
            HermesConnectionService.start(this)
        }
    }
}
