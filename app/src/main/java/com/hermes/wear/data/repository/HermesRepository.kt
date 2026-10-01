package com.hermes.wear.data.repository

import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.model.HermesMessage
import com.hermes.wear.data.model.MessageStatus
import com.hermes.wear.data.model.Sender
import com.hermes.wear.data.network.HermesApiClient
import com.hermes.wear.data.network.HermesHttpException
import com.hermes.wear.data.network.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Conversation state for the whole app process.
 *
 * Sends run in [scope], an application-level scope, not a ViewModel scope:
 * if the user swipes the app away mid-turn the request still completes and
 * its reply is written straight into [messages], so it is there on the next
 * launch (as long as the process lives; nothing is persisted to disk).
 *
 * One turn at a time: [sendMessage] ignores new text while a send is in
 * flight, which also absorbs double taps.
 */
class HermesRepository(
    private val apiClient: HermesApiClient,
    private val settings: HermesSettings,
    private val scope: CoroutineScope,
) {
    private val _messages = MutableStateFlow<List<HermesMessage>>(emptyList())
    val messages: StateFlow<List<HermesMessage>> = _messages.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _connectionStatus = MutableStateFlow(
        if (settings.serverUrl.isBlank()) ConnectionStatus.NOT_CONFIGURED else ConnectionStatus.CHECKING
    )
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    /**
     * Latest error text, or null. A StateFlow rather than a one-shot event so
     * an error raised while another screen is showing is still there when
     * the conversation screen comes back; the UI calls [clearError].
     */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var sendJob: Job? = null

    private fun config() = ServerConfig(settings.serverUrl, settings.apiKey)

    /**
     * Sends [text] as one turn. Returns the job, or null if the text was blank,
     * a send is already in flight, or no server URL is set.
     */
    fun sendMessage(text: String): Job? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (settings.serverUrl.isBlank()) {
            _connectionStatus.value = ConnectionStatus.NOT_CONFIGURED
            _error.value = "Set the server URL in Settings first"
            return null
        }
        if (!_isSending.compareAndSet(expect = false, update = true)) return null

        val userMessage = HermesMessage(text = trimmed, sender = Sender.USER, status = MessageStatus.SENDING)
        _messages.update { it + userMessage }
        val conversation = settings.conversationId

        return scope.launch {
            try {
                val result = apiClient.send(config(), conversation, trimmed)
                result.onSuccess { replies ->
                    setStatus(userMessage.id, MessageStatus.SENT)
                    _messages.update { it + replies }
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                }.onFailure { e ->
                    setStatus(userMessage.id, MessageStatus.ERROR)
                    _error.value = "Failed: ${e.message ?: e.javaClass.simpleName}"
                    _connectionStatus.value = statusFor(e) ?: _connectionStatus.value
                }
            } catch (e: CancellationException) {
                setStatus(userMessage.id, MessageStatus.ERROR)
                _error.value = "Cancelled"
                throw e
            } finally {
                _isSending.value = false
            }
        }.also { sendJob = it }
    }

    /** Cancels the in-flight send, if any; the HTTP call is cancelled too. */
    fun cancelSend() {
        sendJob?.cancel()
    }

    /** Re-runs the reachability/auth check. */
    fun checkConnection(): Job = scope.launch {
        if (settings.serverUrl.isBlank()) {
            _connectionStatus.value = ConnectionStatus.NOT_CONFIGURED
            return@launch
        }
        _connectionStatus.value = ConnectionStatus.CHECKING
        _connectionStatus.value = apiClient.checkHealth(config())
    }

    /**
     * Clears the on-watch history and switches to a new gateway conversation,
     * so the agent also starts without the previous context.
     */
    fun startNewConversation() {
        cancelSend()
        settings.rotateConversationId()
        _messages.value = emptyList()
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    private fun setStatus(id: String, status: MessageStatus) {
        _messages.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }

    private fun statusFor(e: Throwable): ConnectionStatus? = when {
        e is HermesHttpException && (e.code == 401 || e.code == 403) -> ConnectionStatus.KEY_REJECTED
        e is HermesHttpException -> null // server answered; reachability unchanged
        e is IOException -> ConnectionStatus.UNREACHABLE
        else -> null
    }
}
