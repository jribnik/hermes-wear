package com.hermes.wear

import android.app.Application
import com.hermes.wear.data.network.HermesApiClient
import com.hermes.wear.data.repository.HermesRepository
import com.hermes.wear.data.repository.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the process-wide [HermesRepository]. Its scope outlives any Activity
 * or ViewModel, so a reply that arrives after the user leaves the app is
 * still recorded. There is no service: if the process is killed, an
 * in-flight turn's reply is lost.
 */
class HermesWearApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var preferenceHelper: PreferenceHelper
        private set

    val repository: HermesRepository by lazy {
        HermesRepository(HermesApiClient(), preferenceHelper, appScope)
    }

    override fun onCreate() {
        super.onCreate()
        preferenceHelper = PreferenceHelper(this)
    }
}
