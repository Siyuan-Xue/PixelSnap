package com.codexue.pixelsnap.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PixelSnapColorScheme = darkColorScheme(
    primary = ClaudeClay,
    onPrimary = ClaudeSlateDark,
    primaryContainer = ClaudeGray750,
    onPrimaryContainer = ClaudeGray050,
    secondary = ClaudeMineral,
    onSecondary = ClaudeSlateDark,
    tertiary = ClaudeSky,
    onTertiary = ClaudeSlateDark,
    background = ClaudeGray950,
    onBackground = ClaudeGray050,
    surface = ClaudeGray850,
    onSurface = ClaudeGray050,
    surfaceVariant = ClaudeGray800,
    onSurfaceVariant = ClaudeIvoryDark,
    outline = ClaudeGray600,
    outlineVariant = ClaudeGray700,
    error = PixelError,
    onError = ClaudeSlateDark,
    errorContainer = ClaudeCoral,
    onErrorContainer = ClaudeSlateDark,
)

@Composable
fun PixelSnapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PixelSnapColorScheme,
        typography = Typography,
        content = content,
    )
}
