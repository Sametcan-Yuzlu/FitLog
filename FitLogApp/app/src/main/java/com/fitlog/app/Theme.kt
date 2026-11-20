package com.fitlog.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The logo's lime, used for the main action in both modes. */
val Lime = Color(0xFF84CC16)
/** Dark text that reads on top of Lime. */
val LimeInk = Color(0xFF1A2E05)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4D7C0F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F99D),
    onPrimaryContainer = LimeInk,
    secondary = Color(0xFF55624C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0D),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF451A03),
    background = Color(0xFFFAFBF7),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFAFBF7),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE1E6D7),
    onSurfaceVariant = Color(0xFF44483D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F5EC),
    surfaceContainer = Color(0xFFECF0E5),
    surfaceContainerHigh = Color(0xFFE6EBDE),
    surfaceContainerHighest = Color(0xFFE1E6D7),
    outline = Color(0xFF74796C),
    outlineVariant = Color(0xFFC4C8BA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3E635),
    onPrimary = LimeInk,
    primaryContainer = Color(0xFF3F6212),
    onPrimaryContainer = Color(0xFFECFCCB),
    secondary = Color(0xFFBDCBB0),
    onSecondary = Color(0xFF283420),
    secondaryContainer = Color(0xFF3E4A35),
    onSecondaryContainer = Color(0xFFD9E7CB),
    tertiaryContainer = Color(0xFF5B3A0A),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE2E3DC),
    surface = Color(0xFF11140F),
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF44483D),
    onSurfaceVariant = Color(0xFFC4C8BA),
    surfaceContainerLowest = Color(0xFF0C0F0A),
    surfaceContainerLow = Color(0xFF191D16),
    surfaceContainer = Color(0xFF1D211A),
    surfaceContainerHigh = Color(0xFF282B24),
    surfaceContainerHighest = Color(0xFF33362F),
    outline = Color(0xFF8E9285),
    outlineVariant = Color(0xFF44483D),
)

@Composable
fun FitLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
