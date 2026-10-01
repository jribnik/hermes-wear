package com.hermes.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
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
 * One conversation entry. User messages are right-aligned in the primary
 * color, Hermes replies left-aligned on the surface color, and SYSTEM notes
 * (e.g. "Hermes ran terminal") are small italic read-only lines.
 *
 * Drawn as a plain rounded box rather than a disabled Chip: a disabled Chip
 * dims its content, which made every message hard to read.
 */
@Composable
fun MessageBubble(message: HermesMessage) {
    if (message.sender == Sender.SYSTEM) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.caption3,
            fontStyle = FontStyle.Italic,
            color = HermesColors.SystemGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
        return
    }

    val isUser = message.sender == Sender.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 0.9f)
                .background(
                    if (isUser) HermesColors.UserBubble else HermesColors.HermesBubble,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isUser) "You" else "Hermes",
                style = MaterialTheme.typography.caption3,
                fontWeight = FontWeight.Bold,
                color = if (isUser) HermesColors.OnPrimary else HermesColors.Secondary
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.body2,
                color = if (isUser) HermesColors.OnPrimary else HermesColors.OnSurface
            )
        }

        when {
            isUser && message.status == MessageStatus.SENDING -> StatusLine("Sending…", HermesColors.SystemGray)
            isUser && message.status == MessageStatus.ERROR -> StatusLine("Not sent", HermesColors.Error)
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = color,
        textAlign = TextAlign.End,
        modifier = Modifier.padding(end = 8.dp)
    )
}

/**
 * Connection status chip at the top of the conversation screen. Tapping it
 * re-runs the reachability/auth check unless already connected or checking.
 */
@Composable
fun ConnectionStatusIndicator(
    status: ConnectionStatus,
    onTap: () -> Unit
) {
    val (text, color) = when (status) {
        ConnectionStatus.CONNECTED -> "● Connected" to HermesColors.Success
        ConnectionStatus.CHECKING -> "◌ Checking…" to HermesColors.Warning
        ConnectionStatus.KEY_REJECTED -> "✕ Key rejected · retry" to HermesColors.Error
        ConnectionStatus.UNREACHABLE -> "○ Unreachable · retry" to HermesColors.SystemGray
        ConnectionStatus.NOT_CONFIGURED -> "○ Set server in Settings" to HermesColors.SystemGray
    }
    val tappable = status != ConnectionStatus.CONNECTED && status != ConnectionStatus.CHECKING

    Chip(
        onClick = { if (tappable) onTap() },
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
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
