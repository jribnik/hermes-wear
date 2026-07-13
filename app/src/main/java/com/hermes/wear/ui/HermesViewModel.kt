package com.hermes.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.wear.HermesWearApp
import com.hermes.wear.data.model.*
import com.hermes.wear.data.repository.HermesRepository
import com.hermes.wear.data.repository.PreferenceHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Main ViewModel for the Hermes Wear app.
 * Uses the Application-scoped shared HermesApiClient (created by HermesWearApp)
 * so there is only one WebSocket connection to the gateway.
 */
class HermesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HermesWearApp
    private val prefs = PreferenceHelper(application)
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
        repository = HermesRepository(app.apiClient)

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

    fun approveCurrentRequest() {
        val approval = _currentApproval.value ?: return
        viewModelScope.launch {
            val result = repository.approveRequest(approval.id)
            result.onSuccess {
                _currentApproval.value = null
            }.onFailure { e ->
                _error.emit("Failed to approve: ${e.message}")
            }
        }
    }

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

    fun updateServerUrl(url: String) {
        prefs.serverUrl = url
        disconnect()
        connectToHermes()
    }

    fun getServerUrl(): String = prefs.serverUrl

    fun disconnect() {
        repository.stopConnection()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopConnection()
    }
}
