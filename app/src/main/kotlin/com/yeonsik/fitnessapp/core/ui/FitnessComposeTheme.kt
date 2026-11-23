package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.core.ui.fitnessColorScheme
import com.yeonsik.fitness.shared.core.ui.fitnessShapes
import com.yeonsik.fitness.shared.core.ui.fitnessTypography
/** View and Compose share the existing tokens; docs/UI_DESIGN_SYSTEM.md owns the direction. */
object FitnessSpacing {
    val micro = 4.dp
    val small = FitnessUiTokens.FORM_ITEM_GAP_DP.dp
    val gap = FitnessUiTokens.CARD_GAP_DP.dp
    val card = 18.dp
    val hero = FitnessUiTokens.HERO_PADDING_DP.dp
    val homeActionMinHeight = FitnessUiTokens.HOME_ACTION_MIN_HEIGHT_DP.dp
    val page = FitnessUiTokens.PAGE_HORIZONTAL_PADDING_DP.dp
    val section = FitnessUiTokens.SECTION_TOP_SPACING_DP.dp
    val touch = FitnessUiTokens.NAV_ITEM_MIN_HEIGHT_DP.dp
}

object FitnessShape {
    val card = RoundedCornerShape(FitnessUiTokens.CARD_RADIUS_DP.dp)
    val hero = RoundedCornerShape(FitnessUiTokens.HERO_RADIUS_DP.dp)
    val input = RoundedCornerShape(FitnessUiTokens.INPUT_RADIUS_DP.dp)
    val button = RoundedCornerShape(FitnessUiTokens.BUTTON_RADIUS_DP.dp)
}

data class FitnessSemanticColors(val action: Color, val onAction: Color, val success: Color, val warning: Color)
private val LightSemantic = FitnessSemanticColors(
    Color(FitnessUiTokens.COLOR_BRAND_BLUE), Color(FitnessUiTokens.COLOR_BLUE_INK),
    Color(FitnessUiTokens.COLOR_POSITIVE), Color(FitnessUiTokens.COLOR_WARNING)
)
private val DarkSemantic = FitnessSemanticColors(
    Color(FitnessUiTokens.COLOR_BRAND_BLUE), Color(FitnessUiTokens.COLOR_D_ON_PASTEL_BLUE),
    Color(FitnessUiTokens.COLOR_D_POSITIVE), Color(FitnessUiTokens.COLOR_D_WARNING)
)
val LocalFitnessColors = staticCompositionLocalOf { LightSemantic }

private val FitnessTypography = fitnessTypography(PlatformTextStyle(includeFontPadding = false))
private val LightColors = fitnessColorScheme(false)
private val DarkColors = fitnessColorScheme(true)

/** Mode persistence and system-bar appearance remain the Activity's responsibility. */
@Composable
fun FitnessComposeTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFitnessColors provides if (dark) DarkSemantic else LightSemantic) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors, typography = FitnessTypography,
            shapes = fitnessShapes(), content = {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
            }
        )
    }
}
