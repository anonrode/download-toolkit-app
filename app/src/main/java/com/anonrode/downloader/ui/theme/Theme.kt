package com.anonrode.downloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Authentic Seal & Premium Material 3 Dark Tokens
val PureBlack = Color(0xFF000000)
val DarkBackground = Color(0xFF000000)        // Pure OLED Black
val SurfaceCard = Color(0xFF101216)           // Deep neutral dark surface
val SurfaceElevated = Color(0xFF181B22)       // Elevated card surface
val SurfaceHighlight = Color(0xFF222630)      // Interactive hover / pressed
val CardBorder = Color(0xFF1F232D)            // Ultra-subtle border
val SealPrimary = Color(0xFFFFFFFF)           // Crisp Pure White
val SealSecondary = Color(0xFFE2E8F0)         // Clean Off-White
val EmeraldSuccess = Color(0xFF10B981)        // Subtle Emerald for Success
val TextPrimary = Color(0xFFF8FAFC)           // 95% White
val TextSecondary = Color(0xFF94A3B8)         // Cool Slate Grey
val TextMuted = Color(0xFF64748B)             // Muted Slate Grey

// Aliases for compatibility
val SurfaceDark = SurfaceCard
val SealAccent = SealSecondary
val ElectricCyan = SealSecondary
val EmeraldGreen = EmeraldSuccess

private val DarkColorScheme = darkColorScheme(
    primary = SealPrimary,
    secondary = SealSecondary,
    background = DarkBackground,
    surface = SurfaceCard,
    surfaceVariant = SurfaceElevated,
    outline = CardBorder,
    onPrimary = PureBlack,
    onSecondary = PureBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun AnonDownloaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
