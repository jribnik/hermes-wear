package com.hermes.wear.data.network

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.hermes.wear.BuildConfig
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.model.HermesMessage
import com.hermes.wear.data.model.ResponsesApiRequest
import com.hermes.wear.data.model.ResponsesApiResponse
import com.hermes.wear.data.model.Sender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where to reach the gateway. [baseUrl] is the server root, e.g. `https://hermes.example.com`. */
data class ServerConfig(val baseUrl: String, val apiKey: String)

/** The server answered with a non-2xx status. */
class HermesHttpException(val code: Int, message: String) : IOException(message)

/** Server URL helpers. */
object ServerUrl {
    /**
     * Trims whitespace and trailing slashes, and strips a pasted `/v1/responses`
     * or `/v1` suffix, so "https://host:8080/v1/responses/" becomes
     * "https://host:8080". Returns "" for blank input.
     */
    fun normalize(raw: String): String {
        var url = raw.trim().trimEnd('/')
        for (suffix in listOf("/v1/responses", "/v1")) {
            if (url.endsWith(suffix, ignoreCase = true)) url = url.dropLast(suffix.length).trimEnd('/')
        }
        return url
    }
}

/**
 * Parses a `/v1/responses` body into conversation entries, in order:
 * - each `function_call` item becomes a read-only [Sender.SYSTEM] note
 *   "Hermes ran <name>". The gateway has already executed these tools by the
 *   time the response arrives; they are not requests for approval.
 * - each `message` item's `output_text` parts become one [Sender.HERMES] reply.
 * `reasoning` and `function_call_output` items are ignored.
 *
 * @throws JsonParseException if [body] is not a JSON object.
 */
object ResponsesParser {
    private val gson = Gson()

    fun parse(body: String): List<HermesMessage> {
        val response = try {
            gson.fromJson(body, ResponsesApiResponse::class.java)
        } catch (e: RuntimeException) {
            throw JsonParseException("Unreadable response from server", e)
        } ?: throw JsonParseException("Empty response from server")

        val out = mutableListOf<HermesMessage>()
        // Gson leaves JSON nulls inside lists as nulls despite the non-null
        // element type, so drop them rather than NPE on a malformed item.
        response.output.orEmpty().filterNotNull().forEach { item ->
            when (item.type) {
                "function_call" -> {
                    val name = item.name?.takeIf { it.isNotBlank() } ?: "a tool"
                    out += HermesMessage(text = "Hermes ran $name", sender = Sender.SYSTEM)
                }
                "message" -> {
                    val text = item.content.orEmpty().filterNotNull()
                        .filter { it.type == "output_text" }
                        .mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }
                        .joinToString("\n\n")
                    if (text.isNotBlank()) out += HermesMessage(text = text, sender = Sender.HERMES)
                }
            }
        }
        return out
    }
}

/**
 * HTTP client for the Hermes gateway's OpenAI-compatible API.
 *
 * Calls are enqueued and suspend until done; cancelling the calling coroutine
 * cancels the HTTP call. A turn can take minutes (the agent may run tools), so
 * the whole call is bounded by a 5-minute call timeout instead of an
 * unbounded read.
 */
class HermesApiClient(
    baseClient: OkHttpClient = OkHttpClient(),
) {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(5, TimeUnit.MINUTES)
        .apply {
            // Debug builds log the request line and status only, never bodies
            // (conversation content) or the Authorization header.
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                })
            }
        }
        .build()

    private val healthClient: OkHttpClient = client.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Builds the `POST {baseUrl}/v1/responses` request. Exposed for tests. */
    fun buildResponsesRequest(config: ServerConfig, conversation: String, text: String): Request {
        val json = gson.toJson(ResponsesApiRequest(input = text, conversation = conversation))
        return Request.Builder()
            .url("${ServerUrl.normalize(config.baseUrl)}/v1/responses")
            .post(json.toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .build()
    }

    /**
     * Sends one user turn and returns the resulting conversation entries (see
     * [ResponsesParser]). Failures are returned as [Result.failure]: a
     * [HermesHttpException] for non-2xx statuses, an [IOException] for network
     * problems, a [JsonParseException] for an unreadable body.
     */
    suspend fun send(config: ServerConfig, conversation: String, text: String): Result<List<HermesMessage>> =
        withContext(Dispatchers.IO) {
            runCatchingIo {
                val request = buildResponsesRequest(config, conversation, text)
                client.newCall(request).await().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw httpError(response.code, body)
                    ResponsesParser.parse(body)
                }
            }
        }

    /**
     * Reachability + auth check: `GET {baseUrl}/v1/models`, which the gateway
     * serves only with a valid Bearer key. Nothing is added to the conversation.
     */
    suspend fun checkHealth(config: ServerConfig): ConnectionStatus = withContext(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) return@withContext ConnectionStatus.NOT_CONFIGURED
        try {
            val request = Request.Builder()
                .url("${ServerUrl.normalize(config.baseUrl)}/v1/models")
                .get()
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .build()
            healthClient.newCall(request).await().use { response ->
                when {
                    response.isSuccessful -> ConnectionStatus.CONNECTED
                    response.code == 401 || response.code == 403 -> ConnectionStatus.KEY_REJECTED
                    else -> ConnectionStatus.UNREACHABLE
                }
            }
        } catch (e: IOException) {
            ConnectionStatus.UNREACHABLE
        } catch (e: IllegalArgumentException) {
            ConnectionStatus.UNREACHABLE // malformed URL
        }
    }

    private fun httpError(code: Int, body: String): HermesHttpException {
        val detail = when (code) {
            401, 403 -> "API key rejected"
            else -> body.take(160).ifBlank { "no details" }
        }
        return HermesHttpException(code, "HTTP $code: $detail")
    }

    /** Maps the exceptions a call can raise to [Result.failure], with readable messages. */
    private inline fun <T> runCatchingIo(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: UnknownServiceException) {
        // OkHttp's message when network_security_config blocks plain http://.
        Result.failure(IOException("Plain http:// to this host is blocked; use https:// (see README)", e))
    } catch (e: IOException) {
        Result.failure(e)
    } catch (e: JsonParseException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(IOException("Invalid server URL", e))
    }
}

/** Enqueues the call and suspends until it completes; cancellation cancels the call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}
