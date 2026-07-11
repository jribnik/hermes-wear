package com.hermes.wear.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.model.HermesMessage
import com.hermes.wear.data.model.MessageStatus
import com.hermes.wear.data.model.Sender
import com.hermes.wear.ui.theme.HermesColors

/**
 * A single message bubble in the conversation list.
 * User messages are right-aligned with primary color.
 * Hermes messages are left-aligned with surface color.
 */
@Composable
fun MessageBubble(message: HermesMessage) {
    val isUser = message.sender == Sender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Chip(
            onClick = {},
            label = {
                Column {
                    Text(
                        text = when (message.sender) {
                            Sender.USER -> "You"
                            Sender.HERMES -> "Hermes"
                            Sender.SYSTEM -> "System"
                        },
                        style = MaterialTheme.typography.caption3,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) HermesColors.Primary.copy(alpha = 0.7f)
                                else HermesColors.Secondary
                    )
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.body2,
                        color = if (isUser) HermesColors.OnPrimary
                                else HermesColors.OnSurface
                    )
                }
            },
            colors = ChipDefaults.chipColors(
                backgroundColor = if (isUser) HermesColors.UserBubble
                                  else if (message.sender == Sender.SYSTEM) HermesColors.SurfaceVariant
                                  else HermesColors.HermesBubble,
                contentColor = if (isUser) HermesColors.OnPrimary
                               else HermesColors.OnSurface
            ),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .fillMaxWidth(if (isUser) 0.85f else 0.9f),
            enabled = false
        )

        // Status indicator for user messages
        if (isUser && message.status == MessageStatus.ERROR) {
            Text(
                text = "⚠️ Failed to send",
                style = MaterialTheme.typography.caption3,
                color = HermesColors.Error,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

/**
 * Connection status indicator bar at the top of the conversation screen.
 */
@Composable
fun ConnectionStatusIndicator(
    status: ConnectionStatus,
    onTap: () -> Unit
) {
    val (text, color) = when (status) {
        ConnectionStatus.CONNECTED -> "● Connected" to HermesColors.ApprovalGreen
        ConnectionStatus.DISCONNECTED -> "○ Tap to connect" to HermesColors.SystemGray
        ConnectionStatus.RECONNECTING -> "◌ Reconnecting..." to HermesColors.RiskMedium
    }

    Chip(
        onClick = { if (status != ConnectionStatus.CONNECTED) onTap() },
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.caption3,
                textAlign = TextAlign.Center
            )
        },
        colors = ChipDefaults.chipColors(
            backgroundColor = color.copy(alpha = 0.15f),
            contentColor = color
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        enabled = status != ConnectionStatus.CONNECTED
    )
}
