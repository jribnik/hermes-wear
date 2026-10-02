package com.hermes.wear.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.hermes.wear.data.network.ServerUrl
import java.util.UUID

/** Settings the repository needs; [PreferenceHelper] is the real implementation. */
interface HermesSettings {
    /** Normalized server root, e.g. `https://hermes.example.com`; "" when unset. */
    var serverUrl: String
    var apiKey: String

    /** Stable gateway conversation name; created on first use. */
    val conversationId: String

    /** Replaces [conversationId] with a fresh one and returns it. */
    fun rotateConversationId(): String
}

/**
 * Settings stored in the app-private `hermes_wear_prefs` SharedPreferences
 * file. Plaintext: `allowBackup="false"` keeps it out of backups, but anyone
 * with root or `run-as` on a debug build can read the API key.
 *
 * There are deliberately no defaults: a real URL or key in source is
 * published to anyone who reads the repo.
 */
class PreferenceHelper(context: Context) : HermesSettings {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREFS_NAME = "hermes_wear_prefs"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_CONVERSATION_ID = "conversation_id"

        fun newConversationId(): String = "hermes-wear-${UUID.randomUUID()}"
    }

    override var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_SERVER_URL, ServerUrl.normalize(value)) }

    override var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_API_KEY, value.trim()) }

    override val conversationId: String
        get() = prefs.getString(KEY_CONVERSATION_ID, null)?.takeIf { it.isNotBlank() }
            ?: rotateConversationId()

    override fun rotateConversationId(): String =
        newConversationId().also { id -> prefs.edit { putString(KEY_CONVERSATION_ID, id) } }
}
