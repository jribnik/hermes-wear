package com.hermes.wear.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hermes.wear.data.repository.PreferenceHelper

/**
 * Starts the Hermes connection service on boot if auto-connect is enabled.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferenceHelper(context)
            if (prefs.autoConnect) {
                HermesConnectionService.start(context)
            }
        }
    }
}
