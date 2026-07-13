package com.hermes.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.model.HermesMessage
import com.hermes.wear.data.model.Sender
import com.hermes.wear.ui.HermesViewModel
import com.hermes.wear.ui.components.ConnectionStatusIndicator
import com.hermes.wear.ui.components.MessageBubble
import com.hermes.wear.ui.theme.HermesColors

/**
 * Main conversation screen with round-screen-safe layout.
 * Uses ScalingLazyColumn with contentPadding for bezel-safe scrolling,
 * plus edge padding on fixed header/footer elements.
 */
@Composable
fun ConversationScreen(
    viewModel: HermesViewModel,
    onStartVoiceInput: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val listState = rememberScalingLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HermesColors.Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
            ) {
                // Connection status bar
                ConnectionStatusIndicator(
                    status = connectionStatus,
                    onTap = { viewModel.connectToHermes() }
                )

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (connectionStatus == ConnectionStatus.CONNECTED)
                                "Say something to Hermes" else "Connecting to Hermes...",
                            style = MaterialTheme.typography.body2,
                            color = HermesColors.SystemGray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    ScalingLazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        autoCentering = null
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(message = message)
                        }
                    }
                }

                // Bottom action bar
                ActionBar(
                    onVoiceInput = onStartVoiceInput,
                    onSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun ActionBar(
    onVoiceInput: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
