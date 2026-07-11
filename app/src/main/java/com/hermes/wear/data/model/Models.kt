package com.hermes.wear.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a message in the conversation between the user and Hermes Agent.
 */
data class HermesMessage(
    @SerializedName("id")
    val id: String = java.util.UUID.randomUUID().toString(),

    @SerializedName("text")
    val text: String,

    @SerializedName("sender")
    val sender: Sender,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("status")
    val status: MessageStatus = MessageStatus.SENT,

    @SerializedName("platform")
    val platform: String = "wear_os"
)

enum class Sender {
    @SerializedName("user")
    USER,

    @SerializedName("hermes")
    HERMES,

    @SerializedName("system")
    SYSTEM
}

enum class MessageStatus {
    @SerializedName("sending")
    SENDING,

    @SerializedName("sent")
    SENT,

    @SerializedName("delivered")
    DELIVERED,

    @SerializedName("error")
    ERROR
}

/**
 * An approval request from Hermes that requires user action.
 * These come when Hermes needs authorization to run shell commands.
 */
data class ApprovalRequest(
    @SerializedName("id")
    val id: String,

    @SerializedName("command")
    val command: String,

    @SerializedName("description")
    val description: String,

    @SerializedName("risk_level")
    val riskLevel: RiskLevel = RiskLevel.MEDIUM,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerializedName("timeout_seconds")
    val timeoutSeconds: Int = 60
)

enum class RiskLevel {
    @SerializedName("low")
    LOW,

    @SerializedName("medium")
    MEDIUM,

    @SerializedName("high")
    HIGH,

    @SerializedName("critical")
    CRITICAL
}

/**
 * Webhook payload sent from Hermes to the watch.
 */
data class HermesWebhookPayload(
    @SerializedName("type")
    val type: PayloadType,

    @SerializedName("message")
    val message: HermesMessage? = null,

    @SerializedName("approval")
    val approval: ApprovalRequest? = null,

    @SerializedName("status")
    val status: ConnectionStatus? = null
)

enum class PayloadType {
    @SerializedName("message")
    MESSAGE,

    @SerializedName("approval")
    APPROVAL,

    @SerializedName("status")
    STATUS,

    @SerializedName("heartbeat")
    HEARTBEAT
}

enum class ConnectionStatus {
    @SerializedName("connected")
    CONNECTED,

    @SerializedName("disconnected")
    DISCONNECTED,

    @SerializedName("reconnecting")
    RECONNECTING
}

/**
 * Request body for sending a message to Hermes.
 */
data class SendMessageRequest(
    @SerializedName("text")
    val text: String,

    @SerializedName("platform")
    val platform: String = "wear_os",

    @SerializedName("sender_id")
    val senderId: String = "pixel_watch_4",

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Response body for approving or denying an approval request.
 */
data class ApprovalResponse(
    @SerializedName("approval_id")
    val approvalId: String,

    @SerializedName("decision")
    val decision: ApprovalDecision,

    @SerializedName("platform")
    val platform: String = "wear_os",

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

enum class ApprovalDecision {
    @SerializedName("approve")
    APPROVE,

    @SerializedName("deny")
    DENY
}

/**
 * Represents the connection state for the UI.
 */
data class ConnectionUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val serverUrl: String = "",
    val lastHeartbeat: Long = 0L,
    val error: String? = null
)
