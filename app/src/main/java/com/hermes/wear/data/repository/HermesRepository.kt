package com.hermes.wear.data.repository

import com.hermes.wear.data.model.*
import com.hermes.wear.data.network.HermesApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * Repository that manages the conversation state and mediates between
 * the UI and the Hermes API client.
 */
class HermesRepository(
    private val apiClient: HermesApiClient
) {
    private val _messages = MutableStateFlow<List<HermesMessage>>(emptyList())
    val messages: StateFlow<List<HermesMessage>> = _messages.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pendingApprovals: StateFlow<List<ApprovalRequest>> = _pendingApprovals.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionUiState())
    val connectionState: StateFlow<ConnectionUiState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var longPollingJob: Job? = null

    /**
     * Start receiving messages via WebSocket.
     */
    fun startConnection(serverUrl: String) {
        _connectionState.update { it.copy(status = ConnectionStatus.RECONNECTING, serverUrl = serverUrl) }

        apiClient.connectWebSocket(
            onOpen = {
                _connectionState.update { it.copy(status = ConnectionStatus.CONNECTED, error = null) }
                scope.launch { collectMessages() }
            },
            onClosed = { code, reason ->
                _connectionState.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
                // Attempt long-poll fallback
                startLongPollingFallback()
            },
            onFailure = { error ->
                _connectionState.update {
                    it.copy(status = ConnectionStatus.DISCONNECTED, error = error.message)
                }
                // Fall back to long-polling
                startLongPollingFallback()
            },
            onReconnecting = {
                _connectionState.update { it.copy(status = ConnectionStatus.RECONNECTING) }
            }
        )
    }

    /**
     * Collect WebSocket messages and dispatch to appropriate state holders.
     */
    private suspend fun collectMessages() {
        val channel = apiClient.observeMessages()
        for (payload in channel) {
            when (payload.type) {
                PayloadType.MESSAGE -> {
                    payload.message?.let { msg ->
                        _messages.update { current -> current + msg }
                    }
                }
                PayloadType.APPROVAL -> {
                    payload.approval?.let { approval ->
                        _pendingApprovals.update { current -> current + approval }
                    }
                }
                PayloadType.HEARTBEAT -> {
                    _connectionState.update { it.copy(lastHeartbeat = System.currentTimeMillis()) }
                }
                PayloadType.STATUS -> {
                    payload.status?.let { status ->
                        _connectionState.update { it.copy(status = status) }
                    }
                }
            }
        }
    }

    /**
     * Fallback to HTTP long-polling when WebSocket fails.
     */
    private fun startLongPollingFallback() {
        longPollingJob?.cancel()
        longPollingJob = scope.launch {
            apiClient.startLongPolling(
                onMessage = { payload ->
                    when (payload.type) {
                        PayloadType.MESSAGE -> {
                            payload.message?.let { msg ->
                                _messages.update { current -> current + msg }
                            }
                        }
                        PayloadType.APPROVAL -> {
                            payload.approval?.let { approval ->
                                _pendingApprovals.update { current -> current + approval }
                            }
                        }
                        PayloadType.HEARTBEAT -> {
                            _connectionState.update { it.copy(lastHeartbeat = System.currentTimeMillis()) }
                        }
                        PayloadType.STATUS -> {
                            payload.status?.let { status ->
                                _connectionState.update { it.copy(status = status) }
                            }
                        }
                    }
                },
                onError = { error ->
                    _connectionState.update { it.copy(error = error.message) }
                }
            )
        }
    }

    /**
     * Send a message from the user to Hermes.
     */
    suspend fun sendMessage(text: String): Result<HermesMessage> {
        // Optimistically add the user message
        val userMessage = HermesMessage(
            text = text,
            sender = Sender.USER,
            status = MessageStatus.SENDING
        )
        _messages.update { current -> current + userMessage }

        val result = apiClient.sendMessage(text)
        result.onSuccess { serverMessage ->
            // Update optimistic message status
            _messages.update { current ->
                current.map { if (it.id == userMessage.id) it.copy(status = MessageStatus.SENT) else it }
            }
        }.onFailure {
            _messages.update { current ->
                current.map { msg ->
                    if (msg.id == userMessage.id) msg.copy(status = MessageStatus.ERROR) else msg
                }
            }
        }
        return result
    }

    /**
     * Approve a pending approval request.
     */
    suspend fun approveRequest(approvalId: String): Result<Unit> {
        val result = apiClient.approveRequest(approvalId)
        result.onSuccess {
            _pendingApprovals.update { current -> current.filter { it.id != approvalId } }
        }
        return result
    }

    /**
     * Deny a pending approval request.
     */
    suspend fun denyRequest(approvalId: String): Result<Unit> {
        val result = apiClient.denyRequest(approvalId)
        result.onSuccess {
            _pendingApprovals.update { current -> current.filter { it.id != approvalId } }
        }
        return result
    }

    /**
     * Stop connection and clean up.
     */
    fun stopConnection() {
        apiClient.disconnect()
        longPollingJob?.cancel()
        scope.cancel()
        _connectionState.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
