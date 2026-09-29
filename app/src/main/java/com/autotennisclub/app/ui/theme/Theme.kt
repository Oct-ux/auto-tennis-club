package com.autotennisclub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = Navy,
    onSecondary = Color.White,
    background = Neutral,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Navy,
    surfaceVariant = Neutral,
    onSurfaceVariant = TextSecondary,
    outline = BorderGray,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Green,
    onPrimary = Navy,
    secondary = BorderGray,
    onSecondary = Navy,
    background = Navy,
    onBackground = Neutral,
    surface = Color(0xFF13293D),
    onSurface = Neutral,
    surfaceVariant = Color(0xFF1C3347),
    onSurfaceVariant = BorderGray,
    outline = BorderGray,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun AutoTennisClubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
