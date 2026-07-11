package com.hermes.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.wear.data.model.*
import com.hermes.wear.data.network.HermesApiClient
import com.hermes.wear.data.repository.HermesRepository
import com.hermes.wear.data.repository.PreferenceHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the Hermes Wear app.
 * Manages conversation state, connection, and approval lifecycle.
 */
class HermesViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceHelper(application)
    private val apiClient: HermesApiClient
    val repository: HermesRepository

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableSharedFlow<String?>()
    val error: SharedFlow<String?> = _error.asSharedFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    // Approval dialog state
    private val _currentApproval = MutableStateFlow<ApprovalRequest?>(null)
    val currentApproval: StateFlow<ApprovalRequest?> = _currentApproval.asStateFlow()

    init {
        apiClient = HermesApiClient(prefs.serverUrl)
        repository = HermesRepository(apiClient)

        // Observe connection state
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _connectionStatus.value = state.status
            }
        }

        // Observe pending approvals - surface the most recent one
        viewModelScope.launch {
            repository.pendingApprovals.collect { approvals ->
                _currentApproval.value = approvals.lastOrNull()
            }
        }

        // Auto-connect if enabled
        if (prefs.autoConnect) {
            connectToHermes()
        }
    }

    val messages: StateFlow<List<HermesMessage>> = repository.messages
    val pendingApprovals: StateFlow<List<ApprovalRequest>> = repository.pendingApprovals

    /**
     * Connect to the Hermes Gateway API.
     */
    fun connectToHermes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.startConnection(prefs.serverUrl)
            } catch (e: Exception) {
                _error.emit("Connection failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Send a text message to Hermes.
     * Called after voice input or text input.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.sendMessage(text)
            result.onFailure { e ->
                _error.emit("Failed to send: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    /**
     * Approve the current approval request.
     */
    fun approveCurrentRequest() {
        val approval = _currentApproval.value ?: return
        viewModelScope.launch {
            val result = repository.approveRequest(approval.id)
            result.onSuccess {
                _currentApproval.value = null
                // Add a system message confirming the action
            }.onFailure { e ->
                _error.emit("Failed to approve: ${e.message}")
            }
        }
    }

    /**
     * Deny the current approval request.
     */
    fun denyCurrentRequest() {
        val approval = _currentApproval.value ?: return
        viewModelScope.launch {
            val result = repository.denyRequest(approval.id)
            result.onSuccess {
                _currentApproval.value = null
            }.onFailure { e ->
                _error.emit("Failed to deny: ${e.message}")
            }
        }
    }

    /**
     * Update the server URL and reconnect.
     */
    fun updateServerUrl(url: String) {
        prefs.serverUrl = url
        disconnect()
        connectToHermes()
    }

    /**
     * Get the current server URL.
     */
    fun getServerUrl(): String = prefs.serverUrl

    /**
     * Disconnect from Hermes.
     */
    fun disconnect() {
        repository.stopConnection()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopConnection()
    }
}
