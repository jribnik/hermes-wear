package com.hermes.wear.data.network

import com.google.gson.Gson
import com.hermes.wear.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the HTTP and WebSocket connection to the Hermes Gateway API.
 */
class HermesApiClient(
    baseUrl: String
) {
    @Volatile var baseUrl: String = baseUrl
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for long-lived connections
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private var webSocket: WebSocket? = null
    private val incomingMessages = Channel<HermesWebhookPayload>(Channel.BUFFERED)
    private val active = AtomicBoolean(true)

    /**
     * Connect to Hermes via WebSocket for real-time messages.
     */
    fun connectWebSocket(
        onOpen: () -> Unit = {},
        onClosed: (code: Int, reason: String) -> Unit = { _, _ -> },
        onFailure: (Throwable) -> Unit = {},
        onReconnecting: () -> Unit = {}
    ) {
        val wsUrl = baseUrl.replace("http", "ws") + "/ws/watch"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("X-Client-Type", "wear_os")
            .addHeader("X-Client-ID", "pixel_watch_4")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val payload = gson.fromJson(text, HermesWebhookPayload::class.java)
                    incomingMessages.trySend(payload)
                } catch (e: Exception) {
                    // Could not parse message; ignore malformed data
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                onClosed(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onClosed(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t)
            }
        })
    }

    /**
     * Returns a channel of incoming messages from the WebSocket.
     */
    fun observeMessages(): Channel<HermesWebhookPayload> = incomingMessages

    /**
     * Send a text message to Hermes via HTTP POST.
     */
    suspend fun sendMessage(text: String): Result<HermesMessage> = withContext(Dispatchers.IO) {
        try {
            val requestBody = SendMessageRequest(text = text)
            val json = gson.toJson(requestBody)
            val body = json.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/api/message")
                .post(body)
                .addHeader("X-Client-Type", "wear_os")
                .addHeader("X-Client-ID", "pixel_watch_4")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "{}"
                val message = gson.fromJson(responseBody, HermesMessage::class.java)
                Result.success(message)
            } else {
                Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Approve an approval request.
     */
    suspend fun approveRequest(approvalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        sendApprovalDecision(approvalId, ApprovalDecision.APPROVE)
    }

    /**
     * Deny an approval request.
     */
    suspend fun denyRequest(approvalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        sendApprovalDecision(approvalId, ApprovalDecision.DENY)
    }

    private suspend fun sendApprovalDecision(
        approvalId: String,
        decision: ApprovalDecision
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val approvalResponse = ApprovalResponse(
                approvalId = approvalId,
                decision = decision
            )
            val json = gson.toJson(approvalResponse)
            val body = json.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/api/approval/respond")
                .post(body)
                .addHeader("X-Client-Type", "wear_os")
                .addHeader("X-Client-ID", "pixel_watch_4")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * HTTP long-poll fallback if WebSocket is not available.
     * Continuously polls for new messages.
     */
    suspend fun startLongPolling(
        onMessage: (HermesWebhookPayload) -> Unit,
        onError: (Exception) -> Unit
    ) {
        while (active.get()) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/poll/watch?client_id=pixel_watch_4")
                    .addHeader("X-Client-Type", "wear_os")
                    .addHeader("X-Client-ID", "pixel_watch_4")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val payload = gson.fromJson(body, HermesWebhookPayload::class.java)
                    onMessage(payload)
                }
            } catch (e: Exception) {
                onError(e)
                delay(5000) // Wait before retry
            }
        }
    }

    /**
     * Disconnect WebSocket (does NOT close the message channel —
     * that channel is reused across reconnections to avoid
     * permanently breaking the singleton client).
     */
    fun disconnect() {
        active.set(false)
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    /**
     * Full shutdown — use only at process exit.
     */
    fun shutdown() {
        disconnect()
        incomingMessages.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
