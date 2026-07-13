package com.hermes.wear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.wear.HermesWearApp
import com.hermes.wear.data.model.*
import com.hermes.wear.data.repository.PreferenceHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
                    PayloadType.MESSAGE -> payload.message?.let { repository.addMessage(it) }
                    PayloadType.APPROVAL -> payload.approval?.let { repository.addApproval(it) }
                    PayloadType.STATUS -> payload.status?.let { _connectionStatus.value = it }
                    else -> {}
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
            result.onSuccess { _connectionStatus.value = ConnectionStatus.CONNECTED }
            result.onFailure { e ->
                _error.emit("Failed: ${e.message}")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
            _isLoading.value = false
        }
    }

    fun connectToHermes() {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            // API Server is HTTP-only (no WebSocket). Send a ping to verify reachability.
            val result = repository.sendMessage("ping")
            result.onSuccess { _connectionStatus.value = ConnectionStatus.CONNECTED }
            result.onFailure { _connectionStatus.value = ConnectionStatus.DISCONNECTED }
        }
    }

    fun disconnect() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    fun approveCurrentRequest() {
        val approval = _currentApproval.value ?: return
        viewModelScope.launch {
            repository.approveRequest(approval.id)
                .onSuccess { _currentApproval.value = null }
                .onFailure { e -> _error.emit("Failed to approve: ${e.message}") }
        }
    }

    fun denyCurrentRequest() {
        val approval = _currentApproval.value ?: return
        viewModelScope.launch {
            repository.denyRequest(approval.id)
                .onSuccess { _currentApproval.value = null }
                .onFailure { e -> _error.emit("Failed to deny: ${e.message}") }
        }
    }

    fun updateServerUrl(url: String) {
        prefs.serverUrl = url
        app.apiClient.baseUrl = url
    }

    fun getServerUrl(): String = prefs.serverUrl
}
