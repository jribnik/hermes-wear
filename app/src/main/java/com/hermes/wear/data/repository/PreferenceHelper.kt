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
        const val KEY_SENDER_ID = "sender_id"
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_ENABLE_NOTIFICATIONS = "enable_notifications"
        const val KEY_VIBRATE_ON_MESSAGE = "vibrate_on_message"
        const val KEY_VIBRATE_ON_APPROVAL = "vibrate_on_approval"
        const val KEY_API_KEY = "api_key"
        const val DEFAULT_SERVER_URL = "http://192.168.50.37:8642"
        const val DEFAULT_SENDER_ID = "pixel_watch_4"
        const val DEFAULT_API_KEY = "hermes-wear-2026"
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value) }

    var senderId: String
        get() = prefs.getString(KEY_SENDER_ID, DEFAULT_SENDER_ID) ?: DEFAULT_SENDER_ID
        set(value) = prefs.edit { putString(KEY_SENDER_ID, value) }

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CONNECT, value) }

    var enableNotifications: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_NOTIFICATIONS, true)
        set(value) = prefs.edit { putBoolean(KEY_ENABLE_NOTIFICATIONS, value) }

    var vibrateOnMessage: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ON_MESSAGE, true)
        set(value) = prefs.edit { putBoolean(KEY_VIBRATE_ON_MESSAGE, value) }

    var vibrateOnApproval: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ON_APPROVAL, true)
        set(value) = prefs.edit { putBoolean(KEY_VIBRATE_ON_APPROVAL, value) }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
        set(value) = prefs.edit { putString(KEY_API_KEY, value) }
}
