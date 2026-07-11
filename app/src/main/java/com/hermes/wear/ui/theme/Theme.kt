package com.hermes.wear.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

/**
 * Hermes Wear color palette - dark theme optimized for OLED watch screens.
 */
object HermesColors {
    val Background = Color(0xFF0D0D0D)
    val Surface = Color(0xFF1A1A2E)
    val SurfaceVariant = Color(0xFF252540)
    val Primary = Color(0xFF7C83FD)
    val PrimaryVariant = Color(0xFF5A61E0)
    val Secondary = Color(0xFF00D9A6)
    val Error = Color(0xFFCF6679)
    val OnBackground = Color(0xFFE4E4F0)
    val OnSurface = Color(0xFFC8C8DC)
    val OnPrimary = Color(0xFF0D0D0D)
    val ApprovalGreen = Color(0xFF4CAF50)
    val DenyRed = Color(0xFFF44336)
    val UserBubble = Color(0xFF7C83FD)
    val HermesBubble = Color(0xFF2D2D44)
    val SystemGray = Color(0xFF666680)
    val RiskLow = Color(0xFF4CAF50)
    val RiskMedium = Color(0xFFFFC107)
    val RiskHigh = Color(0xFFFF9800)
    val RiskCritical = Color(0xFFF44336)
}

val WearHermesColors = Colors(
    primary = HermesColors.Primary,
    primaryVariant = HermesColors.PrimaryVariant,
    secondary = HermesColors.Secondary,
    background = HermesColors.Background,
    surface = HermesColors.Surface,
    error = HermesColors.Error,
    onPrimary = HermesColors.OnPrimary,
    onSecondary = Color.Black,
    onBackground = HermesColors.OnBackground,
    onSurface = HermesColors.OnSurface,
    onError = Color.Black,
)

val LocalHermesColors = staticCompositionLocalOf { HermesColors }
