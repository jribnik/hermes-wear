package com.hermes.wear.data.repository

import com.google.gson.JsonParseException
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
 *
 * Cancelling a send (or hitting the 5-minute timeout) only abandons the
 * request on the watch. The gateway keeps running the turn and records it
 * under the conversation, so the agent may still act on it and will remember
 * it; the watch just never shows the reply. A message left in
 * [MessageStatus.ERROR] therefore means "no reply received", not "not
 * delivered".
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

    /** Makes "is this turn still current?" + the write that follows atomic against [startNewConversation]. */
    private val stateLock = Any()

    private var sendJob: Job? = null
    private var checkJob: Job? = null

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
            // "New conversation" replaces this turn's conversation: the history
            // it belonged to was cleared, so nothing of it may be written back
            // (not a late reply, and not the cancel error). The check and the
            // write happen together under [stateLock], and
            // [startNewConversation] rotates and clears under the same lock,
            // so a tap can't slip in between the check and the write.
            fun ifCurrent(block: () -> Unit) {
                synchronized(stateLock) {
                    if (settings.conversationId == conversation) block()
                }
            }
            try {
                val result = apiClient.send(config(), conversation, trimmed)
                ifCurrent {
                    result.onSuccess { replies ->
                        setStatus(userMessage.id, MessageStatus.SENT)
                        _messages.update { it + replies }
                        _connectionStatus.value = ConnectionStatus.CONNECTED
                        when {
                            replies.isEmpty() -> _error.value = "Empty reply from Hermes"
                            // Tool notes only: the turn did something, so this is
                            // shown as a note in the conversation, not as an error.
                            replies.none { it.sender == Sender.HERMES } ->
                                _messages.update { it + HermesMessage(text = NO_MESSAGE_NOTE, sender = Sender.SYSTEM) }
                        }
                    }.onFailure { e ->
                        setStatus(userMessage.id, MessageStatus.ERROR)
                        _error.value = failureText(e)
                        _connectionStatus.value = statusFor(e) ?: _connectionStatus.value
                    }
                }
            } catch (e: CancellationException) {
                ifCurrent {
                    setStatus(userMessage.id, MessageStatus.ERROR)
                    _error.value = "Stopped waiting. Hermes may still finish this turn."
                }
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

    /**
     * Re-runs the reachability/auth check. A check still running is cancelled
     * first, so a slow stale result (e.g. for the previous URL) can never
     * overwrite a newer one.
     */
    fun checkConnection(): Job {
        checkJob?.cancel()
        return scope.launch {
            if (settings.serverUrl.isBlank()) {
                _connectionStatus.value = ConnectionStatus.NOT_CONFIGURED
                return@launch
            }
            _connectionStatus.value = ConnectionStatus.CHECKING
            _connectionStatus.value = apiClient.checkHealth(config())
        }.also { checkJob = it }
    }

    /**
     * Clears the on-watch history and switches to a new gateway conversation,
     * so the agent also starts without the previous context.
     */
    fun startNewConversation() {
        cancelSend()
        synchronized(stateLock) {
            settings.rotateConversationId()
            _messages.value = emptyList()
            _error.value = null
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun setStatus(id: String, status: MessageStatus) {
        _messages.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }

    // OkHttp's connect timeout is a SocketTimeoutException, itself an
    // InterruptedIOException, but the request never reached the gateway then,
    // so "it may still finish" would be wrong. OkHttp has no dedicated type for
    // it; it is recognised by the socket layer's "connect timed out" message.
    internal fun failureText(e: Throwable): String = when {
        e is java.net.SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true ->
            "Couldn't reach Hermes (connect timed out)"
        // Read/call timeouts: the gateway may still be running the turn.
        e is java.io.InterruptedIOException -> "Timed out waiting for Hermes. It may still finish this turn."
        e is JsonParseException -> "Unreadable reply from Hermes"
        else -> "Failed: ${e.message ?: e.javaClass.simpleName}"
    }

    private fun statusFor(e: Throwable): ConnectionStatus? = when {
        e is HermesHttpException && (e.code == 401 || e.code == 403) -> ConnectionStatus.KEY_REJECTED
        e is HermesHttpException -> null // server answered; reachability unchanged
        e is IOException -> ConnectionStatus.UNREACHABLE
        else -> null
    }

    private companion object {
        const val NO_MESSAGE_NOTE = "Hermes ran tools but sent no message"
    }
}
