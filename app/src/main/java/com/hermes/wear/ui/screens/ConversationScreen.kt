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

@Composable
fun ConversationScreen(
    viewModel: HermesViewModel,
    onStartVoiceInput: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val listState = rememberScalingLazyListState()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.error.collect { errorMessage = it }
    }
    // Auto-dismiss the error banner after a few seconds
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            kotlinx.coroutines.delay(4000)
            errorMessage = null
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
                    onTap = { viewModel.connectToHermes() }
                )
            }

            errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.caption3,
                    color = HermesColors.Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                )
            }

            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (connectionStatus == ConnectionStatus.CONNECTED) "Say something to Hermes" else "Connecting to Hermes...",
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

            ActionBar(onVoiceInput = onStartVoiceInput, onSettings = onOpenSettings)
        }
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
