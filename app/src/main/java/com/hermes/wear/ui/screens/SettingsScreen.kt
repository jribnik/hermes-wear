package com.hermes.wear.ui.screens

import android.security.NetworkSecurityPolicy
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.hermes.wear.BuildConfig
import com.hermes.wear.data.model.ConnectionStatus
import com.hermes.wear.data.network.ServerUrl
import com.hermes.wear.ui.HermesViewModel
import com.hermes.wear.ui.theme.HermesColors

/**
 * Settings: server URL and API key (each edited in a text field opened by
 * tapping its chip, using the watch keyboard), a connection check, and
 * "New conversation".
 */
@Composable
fun SettingsScreen(
    viewModel: HermesViewModel,
    onBack: () -> Unit
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    var serverUrl by remember { mutableStateOf(viewModel.getServerUrl()) }
    var editingUrl by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var keySet by remember { mutableStateOf(viewModel.hasApiKey()) }
    var editingKey by remember { mutableStateOf(false) }
    // Held only while editing; cleared on save and whenever the API Key chip is
    // tapped again to close the field. Never logged or displayed in clear.
    var keyInput by remember { mutableStateOf("") }

    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = { TimeText() }
    ) {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(HermesColors.Background)
                .padding(start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.title3,
                    color = HermesColors.Primary,
                    textAlign = TextAlign.Center
                )
            }

            // Connection status; tap to re-check.
            item {
                SettingsChip(
                    text = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> "● Connected"
                        ConnectionStatus.CHECKING -> "◌ Checking…"
                        ConnectionStatus.KEY_REJECTED -> "✕ Key rejected · tap to retry"
                        ConnectionStatus.UNREACHABLE -> "○ Unreachable · tap to retry"
                        ConnectionStatus.NOT_CONFIGURED -> "○ No server set"
                    },
                    onClick = { viewModel.checkConnection() }
                )
            }

            // Server URL
            item { Caption("Hermes server URL") }
            item {
                SettingsChip(
                    text = serverUrl.ifBlank { "Tap to set URL" },
                    onClick = {
                        editingUrl = !editingUrl
                        urlInput = serverUrl
                    }
                )
            }
            if (editingUrl) {
                textFieldItem(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    keyboardType = KeyboardType.Uri,
                    masked = false
                )
                cleartextWarning(urlInput)
                item {
                    SettingsChip(
                        text = "Save URL",
                        enabled = urlInput.isNotBlank(),
                        primary = true,
                        onClick = {
                            viewModel.updateServerUrl(urlInput)
                            serverUrl = viewModel.getServerUrl()
                            editingUrl = false
                        }
                    )
                }
            }

            // API key (masked; the stored value is never shown)
            item { Caption("API key") }
            item {
                SettingsChip(
                    text = if (keySet) "● Key set (tap to change)" else "○ Not set (tap to enter)",
                    onClick = { editingKey = !editingKey; keyInput = "" }
                )
            }
            if (editingKey) {
                textFieldItem(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    keyboardType = KeyboardType.Password,
                    masked = true
                )
                item {
                    SettingsChip(
                        text = "Save key",
                        enabled = keyInput.isNotBlank(),
                        primary = true,
                        onClick = {
                            viewModel.updateApiKey(keyInput)
                            keySet = viewModel.hasApiKey()
                            keyInput = ""
                            editingKey = false
                        }
                    )
                }
                if (keySet) {
                    item {
                        SettingsChip(
                            text = "Clear key",
                            onClick = {
                                viewModel.updateApiKey("")
                                keySet = false
                                keyInput = ""
                                editingKey = false
                            }
                        )
                    }
                }
            }

            // Clears the screen AND starts a new gateway conversation, so the
            // agent forgets the previous turns too.
            item {
                SettingsChip(text = "New conversation", onClick = { viewModel.startNewConversation() })
            }

            item { Caption("Hermes Wear v${BuildConfig.VERSION_NAME}") }

            item {
                Chip(onClick = onBack, label = { Text("← Back", style = MaterialTheme.typography.button, color = HermesColors.Primary) })
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = HermesColors.SystemGray,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SettingsChip(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(text = text, maxLines = 2, style = MaterialTheme.typography.body2) },
        colors = ChipDefaults.chipColors(
            backgroundColor = if (primary) HermesColors.Success else HermesColors.SurfaceVariant,
            contentColor = if (primary) HermesColors.OnPrimary else HermesColors.OnSurface
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun ScalingLazyListScope.textFieldItem(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    masked: Boolean,
) = item {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.body2.copy(color = HermesColors.OnSurface),
        cursorBrush = SolidColor(HermesColors.Primary),
        modifier = Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

/**
 * Warns when [raw] is a plain http:// URL to a host that
 * res/xml/network_security_config.xml does not allow cleartext to; such
 * requests would fail with a "cleartext not permitted" error.
 */
private fun ScalingLazyListScope.cleartextWarning(raw: String) {
    val url = ServerUrl.normalize(raw)
    if (!url.startsWith("http://", ignoreCase = true)) return
    val host = Uri.parse(url).host ?: return
    if (NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(host)) return
    item {
        Text(
            text = "Plain http:// is blocked for this host. Use https://",
            style = MaterialTheme.typography.caption3,
            color = HermesColors.Error,
            textAlign = TextAlign.Center
        )
    }
}
