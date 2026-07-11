package com.hermes.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.ui.HermesViewModel
import com.hermes.wear.ui.theme.HermesColors

/**
 * Settings screen for configuring the Hermes connection and preferences.
 */
@Composable
fun SettingsScreen(
    viewModel: HermesViewModel,
    onBack: () -> Unit
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    var serverUrl by remember { mutableStateOf(viewModel.getServerUrl()) }

    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(HermesColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Settings",
                style = MaterialTheme.typography.title3,
                color = HermesColors.Primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Connection status
            Chip(
                onClick = {},
                label = {
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> "● Connected"
                            ConnectionStatus.DISCONNECTED -> "○ Disconnected"
                            ConnectionStatus.RECONNECTING -> "◌ Reconnecting..."
                        }
                    )
                },
                colors = ChipDefaults.chipColors(
                    backgroundColor = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> HermesColors.ApprovalGreen.copy(alpha = 0.2f)
                        ConnectionStatus.RECONNECTING -> HermesColors.RiskMedium.copy(alpha = 0.2f)
                        else -> HermesColors.SurfaceVariant
                    },
                    contentColor = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> HermesColors.ApprovalGreen
                        ConnectionStatus.RECONNECTING -> HermesColors.RiskMedium
                        else -> HermesColors.OnSurface
                    }
                ),
                enabled = false
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Server URL (simplified display)
            Text(
                text = "Hermes Server",
                style = MaterialTheme.typography.caption3,
                color = HermesColors.SystemGray,
                textAlign = TextAlign.Center
            )
            Text(
                text = serverUrl,
                style = MaterialTheme.typography.body2,
                color = HermesColors.OnSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                Chip(
                    onClick = {
                        viewModel.disconnect()
                    },
                    label = { Text("Disconnect") },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = HermesColors.DenyRed,
                        contentColor = HermesColors.OnPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Chip(
                    onClick = {
                        viewModel.connectToHermes()
                    },
                    label = { Text("Connect") },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = HermesColors.ApprovalGreen,
                        contentColor = HermesColors.OnPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clear messages
            Chip(
                onClick = {
                    viewModel.repository.clearMessages()
                },
                label = { Text("Clear Messages") },
                colors = ChipDefaults.chipColors(
                    backgroundColor = HermesColors.SurfaceVariant,
                    contentColor = HermesColors.OnSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App info
            Text(
                text = "Hermes Wear v1.0.0",
                style = MaterialTheme.typography.caption3,
                color = HermesColors.SystemGray,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Pixel Watch 4 Companion",
                style = MaterialTheme.typography.caption3,
                color = HermesColors.SystemGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Back button
            TextButton(onClick = onBack) {
                Text(
                    text = "← Back",
                    style = MaterialTheme.typography.button,
                    color = HermesColors.Primary
                )
            }
        }
    }
}
