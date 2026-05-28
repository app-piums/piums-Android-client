package com.piums.cliente.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary        = PiumsOrange,
    background     = PageBackground,
    surface        = CardBackground,
    surfaceVariant = InputBackground,
    onBackground   = Color.White,
    onSurface      = Color.White,
    secondary      = PiumsBlue,
    outline        = Color(0xFF48484A),
    error          = PiumsError
)

private val LightColorScheme = lightColorScheme(
    primary        = PiumsOrange,
    background     = PageBackgroundLight,
    surface        = CardBackgroundLight,
    surfaceVariant = InputBackgroundLight,
    onBackground   = Color(0xFF1C1C1E),
    onSurface      = Color(0xFF1C1C1E),
    secondary      = PiumsBlue,
    outline        = Color(0xFFD1D1D6),
    error          = PiumsError
)

@Composable
fun PiumsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = PiumsTypography,
        content     = content
    )
}
