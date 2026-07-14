package com.hermes.wear.data.repository

import com.hermes.wear.data.model.*
import com.hermes.wear.data.network.HermesApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Repository that manages the conversation state.
 * HTTP-only — no WebSocket, no foreground service.
 */
class HermesRepository(
    private val apiClient: HermesApiClient
) {
    private val _messages = MutableStateFlow<List<HermesMessage>>(emptyList())
    val messages: StateFlow<List<HermesMessage>> = _messages.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pendingApprovals: StateFlow<List<ApprovalRequest>> = _pendingApprovals.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bridge: apiClient channel → repository SharedFlow (consumed by ViewModel). */
    private val _incomingMessages = MutableSharedFlow<HermesWebhookPayload>(
        replay = 0,
        extraBufferCapacity = 4
    )
    val incomingMessages: SharedFlow<HermesWebhookPayload> = _incomingMessages.asSharedFlow()

    private var observingJob: Job? = null

    /**
     * Start bridging the apiClient's internal message channel into the
     * repository's SharedFlow so that the ViewModel sees incoming messages.
     * Idempotent — safe to call from ViewModel init.
     */
    fun startObserving() {
        if (observingJob?.isActive == true) return
        observingJob = scope.launch {
            val channel = apiClient.observeMessages()
            for (payload in channel) {
                _incomingMessages.emit(payload)   // suspend emit, never drops
            }
        }
    }

    fun addMessage(msg: HermesMessage) {
        _messages.update { current -> current + msg }
    }

    fun addApproval(approval: ApprovalRequest) {
        _pendingApprovals.update { current -> current + approval }
    }

    /** Reachability check — does not add anything to the conversation. */
    suspend fun checkHealth(): Result<Unit> = apiClient.checkHealth()

    suspend fun sendMessage(text: String): Result<HermesMessage> {
        val userMessage = HermesMessage(
            text = text,
            sender = Sender.USER,
            status = MessageStatus.SENDING
        )
        _messages.update { current -> current + userMessage }

        val result = apiClient.sendMessage(text)
        result.onSuccess {
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

    suspend fun approveRequest(approvalId: String): Result<Unit> {
        val result = apiClient.approveRequest(approvalId)
        result.onSuccess {
            _pendingApprovals.update { current -> current.filter { it.id != approvalId } }
        }
        return result
    }

    suspend fun denyRequest(approvalId: String): Result<Unit> {
        val result = apiClient.denyRequest(approvalId)
        result.onSuccess {
            _pendingApprovals.update { current -> current.filter { it.id != approvalId } }
        }
        return result
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
