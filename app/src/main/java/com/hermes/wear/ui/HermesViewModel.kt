package com.hermes.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.hermes.wear.HermesWearApp
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.model.HermesMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin UI facade over the app-scoped repository. It holds no state of its
 * own, so nothing is lost when the Activity (and this ViewModel) goes away.
 */
class HermesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HermesWearApp
    private val prefs = app.preferenceHelper
    private val repository = app.repository

    val messages: StateFlow<List<HermesMessage>> = repository.messages
    val isSending: StateFlow<Boolean> = repository.isSending
    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val error: StateFlow<String?> = repository.error

    init {
        repository.checkConnection()
    }

    fun sendMessage(text: String) {
        repository.sendMessage(text)
    }

    fun cancelSend() = repository.cancelSend()

    fun checkConnection() {
        repository.checkConnection()
    }

    fun clearError() = repository.clearError()

    fun startNewConversation() = repository.startNewConversation()

    fun getServerUrl(): String = prefs.serverUrl

    /** Saves the (normalized) server URL and re-checks the connection. */
    fun updateServerUrl(url: String) {
        prefs.serverUrl = url
        repository.checkConnection()
    }

    /** Saves the API key and re-checks the connection. Never log the value. */
    fun updateApiKey(key: String) {
        prefs.apiKey = key
        repository.checkConnection()
    }

    fun hasApiKey(): Boolean = prefs.apiKey.isNotBlank()
}
