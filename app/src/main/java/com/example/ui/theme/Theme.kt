package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NexEmeraldLight,
    onPrimary = NexSlateDark,
    primaryContainer = NexOnEmeraldContainer,
    onPrimaryContainer = NexEmeraldContainer,
    secondary = NexAmberLight,
    onSecondary = NexSlateDark,
    secondaryContainer = NexOnAmberContainer,
    onSecondaryContainer = NexAmberContainer,
    background = NexSlateDark,
    onBackground = Color(0xFFF1F5F9),
    surface = NexSlateSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFF87171)
)

private val LightColorScheme = lightColorScheme(
    primary = NexEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = NexEmeraldContainer,
    onPrimaryContainer = NexOnEmeraldContainer,
    secondary = NexAmberAccent,
    onSecondary = Color.White,
    secondaryContainer = NexAmberContainer,
    onSecondaryContainer = NexOnAmberContainer,
    background = NexSlateLightBg,
    onBackground = NexSlateDark,
    surface = NexSlateLightSurface,
    onSurface = NexSlateDark,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    error = NexCrimsonError
)

@Composable
fun NexbizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NexbizTheme(darkTheme = darkTheme, content = content)
}
