package com.hermes.wear

import android.app.Application
import com.hermes.wear.data.network.HermesApiClient
import com.hermes.wear.data.repository.HermesRepository
import com.hermes.wear.data.repository.PreferenceHelper
import com.hermes.wear.service.HermesConnectionService

/**
 * Application class for Hermes Wear.
 * Owns process-lifetime singletons: one HermesApiClient, one HermesRepository.
 *
 * Architecture:
 * - Service owns WebSocket connection, consumes observeMessages() once,
 *   feeds payloads into repository.incomingMessages SharedFlow.
 * - ViewModel observes repository.incomingMessages, uses HTTP methods only.
 * - Both share the same repository instance — messages reach UI.
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

    val repository: HermesRepository by lazy {
        HermesRepository(apiClient)
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
