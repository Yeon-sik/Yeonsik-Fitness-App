package com.yeonsik.fitnessapp.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF00695C),
    surface = Color(0xFFFAF9F6),
    background = Color(0xFFF6F5F1)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCBFF),
    secondary = Color(0xFF80CBC4),
    surface = Color(0xFF1D1F20),
    background = Color(0xFF151718)
)

/** Shared Compose palette. Persisting the selected mode remains the Activity's responsibility. */
@Composable
fun FitnessComposeTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
