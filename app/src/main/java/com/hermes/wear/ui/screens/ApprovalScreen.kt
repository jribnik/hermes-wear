package com.hermes.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.hermes.wear.data.model.ApprovalRequest
import com.hermes.wear.data.model.RiskLevel
import com.hermes.wear.ui.theme.HermesColors

/**
 * Full-screen approval prompt shown when Hermes requests authorization
 * for a shell command. User can Approve or Deny with large tap targets.
 */
@Composable
fun ApprovalScreen(
    approval: ApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit
) {
    // Auto-deny countdown for timeouts
    var secondsRemaining by remember(approval.id) {
        mutableIntStateOf(approval.timeoutSeconds)
    }

    LaunchedEffect(approval.id) {
        while (secondsRemaining > 0) {
            kotlinx.coroutines.delay(1000)
            secondsRemaining--
        }
        // Auto-deny when timer expires
        if (secondsRemaining <= 0) {
            onDismiss()
        }
    }

    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HermesColors.Surface)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
        ) {
            if (secondsRemaining <= 0) {
                // Timed out state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "⏰ Timed Out",
                        style = MaterialTheme.typography.title3,
                        color = HermesColors.SystemGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Approval request expired",
                        style = MaterialTheme.typography.body2,
                        color = HermesColors.OnSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Chip(
                        onClick = onDismiss,
                        label = { Text("Dismiss") },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = HermesColors.SurfaceVariant,
                            contentColor = HermesColors.OnSurface
                        )
                    )
                }
            } else {
                val listState = rememberScalingLazyListState()
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Risk level header
                    item { RiskBadge(riskLevel = approval.riskLevel) }

                    // Command description
                    item {
                        Text(
                            text = "Hermes wants to run:",
                            style = MaterialTheme.typography.caption3,
                            color = HermesColors.SystemGray,
                            textAlign = TextAlign.Center
                        )
                    }

                    // The actual command
                    item {
                        Text(
                            text = approval.command,
                            style = MaterialTheme.typography.body2,
                            color = HermesColors.OnBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Description if available
                    if (approval.description.isNotBlank()) {
                        item {
                            Text(
                                text = approval.description,
                                style = MaterialTheme.typography.caption3,
                                color = HermesColors.OnSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Countdown timer
                    item {
                        Text(
                            text = "⏱ ${secondsRemaining}s",
                            style = MaterialTheme.typography.caption3,
                            color = if (secondsRemaining <= 10) HermesColors.RiskCritical
                                    else HermesColors.SystemGray,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Approve / Deny buttons - large, tappable targets
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Deny button (left)
                            Chip(
                                onClick = onDeny,
                                label = {
                                    Text(
                                        text = "✕ Deny",
                                        style = MaterialTheme.typography.button,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = HermesColors.DenyRed,
                                    contentColor = HermesColors.OnPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )

                            // Approve button (right)
                            Chip(
                                onClick = onApprove,
                                label = {
                                    Text(
                                        text = "✓ Approve",
                                        style = MaterialTheme.typography.button,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = ChipDefaults.chipColors(
                                    backgroundColor = HermesColors.ApprovalGreen,
                                    contentColor = HermesColors.OnPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            )
                        }
                    }

                    // Dismiss / ignore button
                    item {
                        Chip(onClick = onDismiss, label = { Text("Ignore", style = MaterialTheme.typography.caption3, color = HermesColors.SystemGray) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskBadge(riskLevel: RiskLevel) {
    val (color, label) = when (riskLevel) {
        RiskLevel.LOW -> HermesColors.RiskLow to "🟢 Low Risk"
        RiskLevel.MEDIUM -> HermesColors.RiskMedium to "🟡 Medium Risk"
        RiskLevel.HIGH -> HermesColors.RiskHigh to "🟠 High Risk"
        RiskLevel.CRITICAL -> HermesColors.RiskCritical to "🔴 Critical Risk"
    }

    Chip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.caption3) },
        colors = ChipDefaults.chipColors(
            backgroundColor = color.copy(alpha = 0.2f),
            contentColor = color
        ),
        enabled = false
    )
}
