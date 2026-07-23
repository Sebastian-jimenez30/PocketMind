package com.pocketmind.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    secondary = Blue80,
    background = Ink10,
    surface = Ink20,
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Blue40,
    background = Sand99,
    onBackground = Ink10,
    surface = White,
    onSurface = Ink10,
    surfaceVariant = Sand95,
    onSurfaceVariant = Ink40,
    error = Red40,
)

@Composable
fun PocketMindTheme(content: @Composable () -> Unit) {

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
