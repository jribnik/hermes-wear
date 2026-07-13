package com.hermes.wear.data.repository

import com.hermes.wear.data.model.*
import com.hermes.wear.data.network.HermesApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Repository that manages the conversation state. HTTP-only operations
 * (send, approve, deny) through the shared HermesApiClient.
 *
 * The HermesConnectionService owns the WebSocket connection and feeds
 * incoming messages into incomingMessages flow via startObserving().
 */
class HermesRepository(
    private val apiClient: HermesApiClient
) {
    private val _messages = MutableStateFlow<List<HermesMessage>>(emptyList())
    val messages: StateFlow<List<HermesMessage>> = _messages.asStateFlow()

    private val _pendingApprovals = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pendingApprovals: StateFlow<List<ApprovalRequest>> = _pendingApprovals.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Shared flow of incoming payloads — written by Service, read by ViewModel. */
    private val _incomingMessages = MutableSharedFlow<HermesWebhookPayload>(replay = 0)
    val incomingMessages: SharedFlow<HermesWebhookPayload> = _incomingMessages.asSharedFlow()

    private val observingStarted = AtomicBoolean(false)
    private var longPollJob: Job? = null

    /**
     * Called by HermesConnectionService to relay incoming WebSocket payloads
     * into the repository for UI consumption. Safe to call more than once
     * (e.g. across service restarts) — only the first call starts the
     * consumer, since the underlying channel supports only a single reader.
     */
    fun startObserving() {
        if (!observingStarted.compareAndSet(false, true)) return
        scope.launch {
            val channel = apiClient.observeMessages()
            for (payload in channel) {
                _incomingMessages.emit(payload)
            }
        }
    }

    /**
     * Called by the Service on failure — triggers long-poll fallback.
     */
    fun startLongPollingFallback() {
        longPollJob?.cancel()
        longPollJob = scope.launch {
            apiClient.reactivate()
            apiClient.startLongPolling(
                onMessage = { payload -> _incomingMessages.tryEmit(payload) },
                onError = { /* silent retry */ }
            )
        }
    }

    /**
     * Called by the Service once the WebSocket has (re)connected —
     * stops the long-poll fallback so both don't run concurrently.
     */
    fun stopLongPollingFallback() {
        apiClient.stopLongPolling()
        longPollJob?.cancel()
        longPollJob = null
    }

    /** Internal — called by ViewModel after observing. */
    fun addMessage(msg: HermesMessage) {
        _messages.update { current -> current + msg }
    }

    /** Internal — called by ViewModel after observing. */
    fun addApproval(approval: ApprovalRequest) {
        _pendingApprovals.update { current -> current + approval }
    }

    /**
     * Send a message from the user to Hermes via HTTP POST.
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
