package com.hermes.wear

import android.app.Application
import android.util.Log
import com.hermes.wear.data.network.HermesApiClient
import com.hermes.wear.data.repository.HermesRepository
import com.hermes.wear.data.repository.PreferenceHelper
import com.hermes.wear.service.HermesConnectionService

class HermesWearApp : Application() {

    companion object {
        lateinit var instance: HermesWearApp
            private set
        private const val TAG = "HermesWearApp"
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

        // skip auto-connect here — ViewModel handles it on launch
        // (the foreground service WebSocket path is unused with HTTP-only API Server)
    }
}
