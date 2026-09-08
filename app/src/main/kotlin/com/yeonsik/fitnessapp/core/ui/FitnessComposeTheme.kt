package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitnessapp.ui.FitnessUi

/** View and Compose share the existing tokens; docs/UI_DESIGN_SYSTEM.md owns the direction. */
object FitnessSpacing {
    val micro = 4.dp
    val small = FitnessUi.FORM_ITEM_GAP_DP.dp
    val gap = FitnessUi.CARD_GAP_DP.dp
    val card = 16.dp
    val page = FitnessUi.PAGE_HORIZONTAL_PADDING_DP.dp
    val section = FitnessUi.SECTION_TOP_SPACING_DP.dp
    val touch = FitnessUi.NAV_ITEM_MIN_HEIGHT_DP.dp
}

object FitnessShape {
    val card = RoundedCornerShape(FitnessUi.CARD_RADIUS_DP.dp)
    val input = RoundedCornerShape(FitnessUi.INPUT_RADIUS_DP.dp)
    val button = RoundedCornerShape(FitnessUi.BUTTON_RADIUS_DP.dp)
}

data class FitnessSemanticColors(val action: Color, val onAction: Color, val success: Color, val warning: Color)
private val LightSemantic = FitnessSemanticColors(
    Color(FitnessUi.COLOR_PASTEL_BLUE), Color(FitnessUi.COLOR_BLUE_INK),
    Color(FitnessUi.COLOR_POSITIVE), Color(FitnessUi.COLOR_WARNING)
)
private val DarkSemantic = FitnessSemanticColors(
    Color(FitnessUi.COLOR_D_PASTEL_BLUE), Color(FitnessUi.COLOR_D_ON_PASTEL_BLUE),
    Color(FitnessUi.COLOR_D_POSITIVE), Color(FitnessUi.COLOR_D_WARNING)
)
val LocalFitnessColors = staticCompositionLocalOf { LightSemantic }

private fun colors(dark: Boolean) = run {
    val surface = Color(if (dark) FitnessUi.COLOR_D_SURFACE else FitnessUi.COLOR_SURFACE)
    val background = Color(if (dark) FitnessUi.COLOR_D_BACKGROUND else FitnessUi.COLOR_BACKGROUND)
    val subtle = Color(if (dark) FitnessUi.COLOR_D_SUBTLE else FitnessUi.COLOR_SUBTLE)
    val ink = Color(if (dark) FitnessUi.COLOR_D_TEXT else FitnessUi.COLOR_TEXT)
    val muted = Color(if (dark) FitnessUi.COLOR_D_MUTED else FitnessUi.COLOR_MUTED)
    val border = Color(if (dark) FitnessUi.COLOR_D_BORDER else FitnessUi.COLOR_BORDER)
    val blueInk = Color(if (dark) FitnessUi.COLOR_D_BLUE_INK else FitnessUi.COLOR_BLUE_INK)
    val blueContainer = Color(if (dark) FitnessUi.COLOR_D_BLUE_CONTAINER else FitnessUi.COLOR_BLUE_CONTAINER)
    val semantic = if (dark) DarkSemantic else LightSemantic
    val base = if (dark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = if (dark) semantic.action else blueInk,
        onPrimary = if (dark) semantic.onAction else surface,
        primaryContainer = blueContainer, onPrimaryContainer = blueInk, inversePrimary = semantic.action,
        secondary = muted, onSecondary = surface, secondaryContainer = subtle, onSecondaryContainer = ink,
        tertiary = muted, onTertiary = surface, tertiaryContainer = subtle, onTertiaryContainer = ink,
        background = background, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = subtle, onSurfaceVariant = muted, surfaceDim = background, surfaceBright = surface,
        surfaceContainerLowest = background, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = subtle, surfaceContainerHighest = subtle, surfaceTint = Color.Transparent,
        inverseSurface = ink, inverseOnSurface = surface, outline = muted, outlineVariant = border,
        error = Color(if (dark) FitnessUi.COLOR_D_NEGATIVE else FitnessUi.COLOR_NEGATIVE),
        onError = surface, errorContainer = subtle,
        onErrorContainer = Color(if (dark) FitnessUi.COLOR_D_NEGATIVE else FitnessUi.COLOR_NEGATIVE)
    )
}

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)
private val FitnessTypography = Typography(
    displayLarge = type(40, 48, FontWeight.SemiBold), displayMedium = type(36, 44, FontWeight.SemiBold),
    displaySmall = type(32, 40, FontWeight.SemiBold), headlineLarge = type(30, 38, FontWeight.SemiBold),
    headlineMedium = type(28, 36, FontWeight.SemiBold), headlineSmall = type(24, 32, FontWeight.SemiBold),
    titleLarge = type(20, 28, FontWeight.SemiBold), titleMedium = type(16, 24, FontWeight.SemiBold),
    titleSmall = type(14, 20, FontWeight.SemiBold), bodyLarge = type(16, 24),
    bodyMedium = type(14, 22), bodySmall = type(12, 18), labelLarge = type(14, 20, FontWeight.SemiBold),
    labelMedium = type(12, 18, FontWeight.Medium), labelSmall = type(11, 16, FontWeight.Medium)
)
private val LightColors = colors(false)
private val DarkColors = colors(true)

/** Mode persistence and system-bar appearance remain the Activity's responsibility. */
@Composable
fun FitnessComposeTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFitnessColors provides if (dark) DarkSemantic else LightSemantic) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors, typography = FitnessTypography,
            shapes = Shapes(
                extraSmall = FitnessShape.input, small = FitnessShape.input,
                medium = FitnessShape.card, large = FitnessShape.card,
                extraLarge = RoundedCornerShape(FitnessUi.SHEET_RADIUS_DP.dp)
            ), content = {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
            }
        )
    }
}
