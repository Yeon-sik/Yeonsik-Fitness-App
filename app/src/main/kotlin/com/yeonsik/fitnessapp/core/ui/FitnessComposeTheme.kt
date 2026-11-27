package com.yeonsik.fitnessapp.core.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.PlatformTextStyle
import com.yeonsik.fitness.shared.core.ui.fitnessColorScheme
import com.yeonsik.fitness.shared.core.ui.fitnessShapes
import com.yeonsik.fitness.shared.core.ui.fitnessTypography

private val FitnessTypography = fitnessTypography(PlatformTextStyle(includeFontPadding = false))
private val LightColors = fitnessColorScheme(false)
private val DarkColors = fitnessColorScheme(true)

/** Mode persistence and system-bar appearance remain the Activity's responsibility. */
@Composable
fun FitnessComposeTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFitnessColors provides fitnessSemanticColors(dark)) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors, typography = FitnessTypography,
            shapes = fitnessShapes(), content = {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
            }
        )
    }
}
