package com.hermes.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.model.HermesMessage
import com.hermes.wear.data.model.Sender
import com.hermes.wear.ui.HermesViewModel
import com.hermes.wear.ui.components.ConnectionStatusIndicator
import com.hermes.wear.ui.components.MessageBubble
import com.hermes.wear.ui.theme.HermesColors

/**
 * Main conversation screen showing the message history with Hermes.
 * Scrollable list of messages with voice input button at the bottom.
 */
@Composable
fun ConversationScreen(
    viewModel: HermesViewModel,
    onStartVoiceInput: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = {
            if (messages.size > 3) {
                PositionIndicator(scalingLazyListState = listState)
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HermesColors.Background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Connection status bar
                ConnectionStatusIndicator(
                    status = connectionStatus,
                    onTap = { viewModel.connectToHermes() }
                )

                // Message list
                if (messages.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (connectionStatus == ConnectionStatus.CONNECTED) {
                                "Say something to Hermes"
                            } else {
                                "Connecting to Hermes..."
                            },
                            style = MaterialTheme.typography.body2,
                            color = HermesColors.SystemGray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(message = message)
                        }
                    }
                }

                // Bottom action bar
                ActionBar(
                    onVoiceInput = onStartVoiceInput,
                    onSettings = onOpenSettings,
                    isConnected = connectionStatus == ConnectionStatus.CONNECTED
                )
            }
        }
    }
}

@Composable
private fun ActionBar(
    onVoiceInput: () -> Unit,
    onSettings: () -> Unit,
    isConnected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Voice input button
        Chip(
            onClick = onVoiceInput,
            label = { Text("🎤 Speak") },
            colors = ChipDefaults.chipColors(
                backgroundColor = HermesColors.Primary,
                contentColor = HermesColors.OnPrimary
            ),
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Settings button
        Chip(
            onClick = onSettings,
            label = { Text("⚙️") },
            colors = ChipDefaults.chipColors(
                backgroundColor = HermesColors.SurfaceVariant,
                contentColor = HermesColors.OnSurface
            )
        )
    }
}
