package com.hermes.wear.data.model

import com.google.gson.annotations.SerializedName

/** One entry in the on-watch conversation list. Kept in memory only. */
data class HermesMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val sender: Sender,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
)

enum class Sender {
    USER,

    /** An assistant reply (`message` output item). */
    HERMES,

    /** A read-only note, e.g. "Hermes ran terminal" for a tool call the gateway already executed. */
    SYSTEM,
}

/** Delivery state of a [Sender.USER] message. */
enum class MessageStatus {
    SENDING,
    SENT,
    ERROR,
}

/** Result of the reachability/auth check shown in the status chip. */
enum class ConnectionStatus {
    /** No server URL saved yet. */
    NOT_CONFIGURED,
    CHECKING,
    CONNECTED,

    /** The server answered 401/403: the API key is missing or wrong. */
    KEY_REJECTED,
    UNREACHABLE,
}

/**
 * Request body for `POST /v1/responses` on the Hermes gateway.
 *
 * [conversation] is a stable name the gateway maps to one agent session, so
 * consecutive turns share context. Without it every request starts a fresh
 * session with no memory of earlier turns.
 */
data class ResponsesApiRequest(
    @SerializedName("model")
    val model: String = "hermes-agent",

    @SerializedName("input")
    val input: String,

    @SerializedName("conversation")
    val conversation: String,
)

/** The subset of the `/v1/responses` response body this app reads. */
data class ResponsesApiResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("output")
    val output: List<ResponsesOutputItem>? = null,
)

/**
 * One item of the response `output` array. The gateway emits `reasoning`,
 * `function_call` / `function_call_output` (tool calls it has ALREADY run
 * server-side, replayed for display only) and a final `message`.
 */
data class ResponsesOutputItem(
    @SerializedName("type")
    val type: String? = null,

    @SerializedName("content")
    val content: List<ResponsesContentPart>? = null,

    @SerializedName("name")
    val name: String? = null,
)

data class ResponsesContentPart(
    @SerializedName("type")
    val type: String? = null,

    @SerializedName("text")
    val text: String? = null,
)
