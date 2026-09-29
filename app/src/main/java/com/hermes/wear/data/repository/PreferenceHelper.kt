package com.hermes.wear.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages user preferences stored locally on the watch.
 */
class PreferenceHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hermes_wear_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_API_KEY = "api_key"
        // No usable defaults: configure the server URL and API key at runtime.
        // A real key/endpoint in source is published to anyone reading the repo.
        const val DEFAULT_SERVER_URL = ""
        const val DEFAULT_API_KEY = ""
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value) }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
        set(value) = prefs.edit { putString(KEY_API_KEY, value) }
}
