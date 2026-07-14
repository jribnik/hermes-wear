package com.hermes.wear.data.network

import com.google.gson.Gson
import com.hermes.wear.BuildConfig
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
    baseUrl: String,
    apiKey: String = "hermes-wear-2026"
) {
    @Volatile var baseUrl: String = baseUrl
    @Volatile var apiKey: String = apiKey
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Never log request/response bodies in release builds — they contain the
    // Bearer token and conversation content.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("Authorization")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for long-lived connections
        .writeTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .addHeader("ngrok-skip-browser-warning", "true")
                .build())
        }
        .build()

    private var webSocket: WebSocket? = null
    private val incomingMessages = Channel<HermesWebhookPayload>(Channel.BUFFERED)
    private val active = AtomicBoolean(true)
    private var longPollCall: Call? = null

    /**
     * Connect to Hermes via WebSocket for real-time messages.
     */
    fun connectWebSocket(
        onOpen: () -> Unit = {},
        onClosed: (code: Int, reason: String) -> Unit = { _, _ -> },
        onFailure: (Throwable) -> Unit = {}
    ) {
        val wsUrl = baseUrl.replace("http", "ws") + "/ws/watch"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
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
     * Lightweight reachability check — issues a HEAD request against the
     * server root without posting anything to the conversation. Any HTTP
     * response (even an error status) means the server is reachable.
     */
    suspend fun checkHealth(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(baseUrl)
                .head()
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("X-Client-Type", "wear_os")
                .addHeader("X-Client-ID", "pixel_watch_4")
                .build()
            // The shared client has no read timeout (long-lived connections);
            // bound the health check so it can't hang indefinitely.
            val healthClient = client.newBuilder()
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            healthClient.newCall(request).execute().use { }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send a text message to Hermes via the OpenAI Responses API and relay
     * any assistant reply / function-call approvals through the shared
     * incoming-messages channel (the same channel WebSocket/long-poll use).
     */
    suspend fun sendMessage(text: String): Result<HermesMessage> = withContext(Dispatchers.IO) {
        postToResponsesApi(text)
    }

    /**
     * Approve an approval request. The Responses API has no inline
     * approve/deny endpoint, so the decision is sent as a follow-up
     * /v1/responses call whose input carries the decision as text.
     */
    suspend fun approveRequest(approvalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        sendApprovalDecision(approvalId, ApprovalDecision.APPROVE)
    }

    /**
     * Deny an approval request (see [approveRequest]).
     */
    suspend fun denyRequest(approvalId: String): Result<Unit> = withContext(Dispatchers.IO) {
        sendApprovalDecision(approvalId, ApprovalDecision.DENY)
    }

    private suspend fun sendApprovalDecision(
        approvalId: String,
        decision: ApprovalDecision
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val decisionText = if (decision == ApprovalDecision.APPROVE) "approve" else "deny"
        postToResponsesApi("$decisionText $approvalId").map { }
    }

    /**
     * POST {"model": "hermes-agent", "input": text} to /v1/responses and
     * relay the parsed output — assistant messages and function-call
     * approvals both surface via [incomingMessages], since neither has a
     * dedicated endpoint under the Responses API.
     */
    private fun postToResponsesApi(text: String): Result<HermesMessage> {
        return try {
            val requestBody = ResponsesApiRequest(input = text)
            val json = gson.toJson(requestBody)
            val body = json.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/v1/responses")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("X-Client-Type", "wear_os")
                .addHeader("X-Client-ID", "pixel_watch_4")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
            }

            val responseBody = response.body?.string() ?: "{}"
            val apiResponse = gson.fromJson(responseBody, ResponsesApiResponse::class.java)

            var replyMessage: HermesMessage? = null
            apiResponse.output?.forEach { item ->
                when (item.type) {
                    "message" -> {
                        val replyText = item.content
                            ?.filter { it.type == "output_text" }
                            ?.mapNotNull { it.text }
                            ?.joinToString("\n\n")
                        if (!replyText.isNullOrBlank()) {
                            val message = HermesMessage(text = replyText, sender = Sender.HERMES)
                            replyMessage = message
                            incomingMessages.trySend(
                                HermesWebhookPayload(type = PayloadType.MESSAGE, message = message)
                            )
                        }
                    }
                    "function_call" -> {
                        val approval = ApprovalRequest(
                            id = item.callId ?: item.id ?: java.util.UUID.randomUUID().toString(),
                            command = item.arguments ?: "",
                            description = item.name ?: "Approval requested"
                        )
                        incomingMessages.trySend(
                            HermesWebhookPayload(type = PayloadType.APPROVAL, approval = approval)
                        )
                    }
                }
            }

            Result.success(replyMessage ?: HermesMessage(text = "", sender = Sender.HERMES))
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
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("X-Client-Type", "wear_os")
                    .addHeader("X-Client-ID", "pixel_watch_4")
                    .get()
                    .build()

                val call = client.newCall(request)
                longPollCall = call
                val response = call.execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val payload = gson.fromJson(body, HermesWebhookPayload::class.java)
                    onMessage(payload)
                }
            } catch (e: Exception) {
                if (!active.get()) break // cancelled via stopLongPolling(), not a real error
                onError(e)
                delay(5000) // Wait before retry
            }
        }
    }

    /**
     * Stop the long-poll loop (e.g., once the WebSocket has reconnected)
     * without touching the WebSocket connection itself.
     */
    fun stopLongPolling() {
        active.set(false)
        longPollCall?.cancel()
    }

    /**
     * Disconnect WebSocket (does NOT close the message channel —
     * that channel is reused across reconnections to avoid
     * permanently breaking the singleton client).
     */
    fun disconnect() {
        stopLongPolling()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    /**
     * Re-enable the long-poll loop after a disconnect
     * (e.g., before falling back to long-polling on WS failure).
     */
    fun reactivate() {
        active.set(true)
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
