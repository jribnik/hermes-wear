package com.hermes.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.ui.HermesViewModel
import com.hermes.wear.ui.components.ConnectionStatusIndicator
import com.hermes.wear.ui.components.MessageBubble
import com.hermes.wear.ui.theme.HermesColors

@Composable
fun ConversationScreen(
    viewModel: HermesViewModel,
    onStartVoiceInput: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val listState = rememberScalingLazyListState()

    // Errors live in the repository until cleared, so one raised while
    // Settings was open is still shown here; hide it after a few seconds.
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearError()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HermesColors.Background)
        ) {
            // Pinned chrome (status bar, action bar) still needs an inset to
            // clear the round bezel; the message list below is full-bleed.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
                ConnectionStatusIndicator(
                    status = connectionStatus,
                    onTap = { viewModel.checkConnection() }
                )
            }

            errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.caption3,
                    color = HermesColors.Error,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                )
            }

            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> "Say something to Hermes"
                            ConnectionStatus.NOT_CONFIGURED -> "Open ⚙️ to set the server"
                            else -> "Tap Speak to talk to Hermes"
                        },
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    autoCentering = null
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }
            }

            if (isSending) {
                SendingBar(onCancel = { viewModel.cancelSend() })
            } else {
                ActionBar(onVoiceInput = onStartVoiceInput, onSettings = onOpenSettings)
            }
        }
    }
}

/** Shown while a turn is in flight (it can take minutes); Cancel aborts the HTTP call. */
@Composable
private fun SendingBar(onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Chip(
            onClick = onCancel,
            label = { Text("Cancel") },
            colors = ChipDefaults.chipColors(
                backgroundColor = HermesColors.SurfaceVariant,
                contentColor = HermesColors.OnSurface
            )
        )
    }
}

@Composable
private fun ActionBar(onVoiceInput: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Chip(
            onClick = onVoiceInput,
            label = { Text("🎤 Speak") },
            colors = ChipDefaults.chipColors(backgroundColor = HermesColors.Primary, contentColor = HermesColors.OnPrimary),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Chip(
            onClick = onSettings,
            label = { Text("⚙️") },
            colors = ChipDefaults.chipColors(backgroundColor = HermesColors.SurfaceVariant, contentColor = HermesColors.OnSurface)
        )
    }
}
