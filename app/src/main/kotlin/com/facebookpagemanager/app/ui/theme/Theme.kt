package com.facebookpagemanager.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FacebookBlue = Color(0xFF1877F2)

private val LightColors = lightColorScheme(
    primary = FacebookBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9FD),
    onPrimaryContainer = Color(0xFF0A2A5E),
    secondary = Color(0xFF536471),
    surface = Color(0xFFFCFCFF),
    background = Color(0xFFF5F7FB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA8F8),
    onPrimary = Color(0xFF0A1F44),
    primaryContainer = Color(0xFF1B3A6B),
    onPrimaryContainer = Color(0xFFDCE9FD),
    secondary = Color(0xFF9AA7B4),
    surface = Color(0xFF121418),
    background = Color(0xFF0D0F13),
)

@Composable
fun FpmTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
