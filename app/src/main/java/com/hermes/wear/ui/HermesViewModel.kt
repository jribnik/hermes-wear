package com.hermes.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.wear.HermesWearApp
import com.hermes.wear.data.model.*
import com.hermes.wear.data.repository.PreferenceHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Main ViewModel. Uses the shared HermesRepository (from HermesWearApp)
 * so both Service and ViewModel observe the same data.
 */
class HermesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HermesWearApp
    private val prefs = PreferenceHelper(application)
    val repository = app.repository

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableSharedFlow<String?>()
    val error: SharedFlow<String?> = _error.asSharedFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _currentApproval = MutableStateFlow<ApprovalRequest?>(null)
    val currentApproval: StateFlow<ApprovalRequest?> = _currentApproval.asStateFlow()

    init {
        viewModelScope.launch {
            repository.incomingMessages.collect { payload ->
                when (payload.type) {
                    PayloadType.MESSAGE -> {
                        payload.message?.let { msg -> repository.addMessage(msg) }
                    }
                    PayloadType.APPROVAL -> {
                        payload.approval?.let { approval -> repository.addApproval(approval) }
                    }
                    PayloadType.HEARTBEAT -> { /* no-op */ }
                    PayloadType.STATUS -> {
                        payload.status?.let { status -> _connectionStatus.value = status }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.pendingApprovals.collect { approvals ->
                _currentApproval.value = approvals.lastOrNull()
            }
        }
    }

    val messages: StateFlow<List<HermesMessage>> = repository.messages
    val pendingApprovals: StateFlow<List<ApprovalRequest>> = repository.pendingApprovals

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.sendMessage(text)
            result.onFailure { e -> _error.emit("Failed to send: ${e.message}") }
            _isLoading.value = false
        }
    }

    fun approveCurrentRequest() {
        val approval = _currentApproval.value ?: return
        viewModelScope.launch {
            val result = repository.approveRequest(approval.id)
            result.onSuccess { _currentApproval.value = null }
                .onFailure { e -> _error.emit("Failed to approve: ${e.message}") }
        }
    }

    fun denyCurrentRequest() {
        val approval = _currentApproval.value ?: return
        viewModelScope.launch {
            val result = repository.denyRequest(approval.id)
            result.onSuccess { _currentApproval.value = null }
                .onFailure { e -> _error.emit("Failed to deny: ${e.message}") }
        }
    }

    fun updateServerUrl(url: String) {
        prefs.serverUrl = url
        app.apiClient.baseUrl = url
    }

    fun getServerUrl(): String = prefs.serverUrl

    fun connectToHermes() {
        com.hermes.wear.service.HermesConnectionService.start(app)
    }

    fun disconnect() {
        com.hermes.wear.service.HermesConnectionService.stop(app)
    }
}
