package com.hermes.wear

import android.app.Application
import com.hermes.wear.data.network.HermesApiClient
import com.hermes.wear.data.repository.HermesRepository
import com.hermes.wear.data.repository.PreferenceHelper

class HermesWearApp : Application() {

    companion object {
        lateinit var instance: HermesWearApp
            private set
    }

    lateinit var preferenceHelper: PreferenceHelper
        private set

    val apiClient: HermesApiClient by lazy {
        HermesApiClient(preferenceHelper.serverUrl, preferenceHelper.apiKey)
    }

    val repository: HermesRepository by lazy {
        HermesRepository(apiClient)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferenceHelper = PreferenceHelper(this)
        // ViewModel handles connect on launch via HTTP ping.
        // No foreground service / WebSocket — API Server is HTTP-only.
    }
}
