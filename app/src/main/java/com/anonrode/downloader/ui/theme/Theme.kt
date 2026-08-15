package com.anonrode.downloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Authentic Seal & Material 3 Dark Palette (No AI neon glow)
val PureBlack = Color(0xFF000000)
val DarkBackground = Color(0xFF0B0E14)
val SurfaceDark = Color(0xFF141721)
val SurfaceElevated = Color(0xFF1C202C)
val CardBorder = Color(0xFF232838)
val SealPrimary = Color(0xFFE2E8F0) // Clean crisp silver-white
val SealAccent = Color(0xFF60A5FA)  // Refined soft blue (Seal accent)
val EmeraldSuccess = Color(0xFF34D399) // Soft emerald
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

private val DarkColorScheme = darkColorScheme(
    primary = SealPrimary,
    secondary = SealAccent,
    background = DarkBackground,
    surface = SurfaceDark,
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
