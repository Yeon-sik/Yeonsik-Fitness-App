package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens

/** View and Compose share the existing tokens; docs/UI_DESIGN_SYSTEM.md owns the direction. */
object FitnessSpacing {
    val micro = 4.dp
    val small = FitnessComposeTokens.FORM_ITEM_GAP_DP.dp
    val gap = FitnessComposeTokens.CARD_GAP_DP.dp
    val card = 18.dp
    val hero = FitnessComposeTokens.HERO_PADDING_DP.dp
    val homeActionMinHeight = FitnessComposeTokens.HOME_ACTION_MIN_HEIGHT_DP.dp
    val page = FitnessComposeTokens.PAGE_HORIZONTAL_PADDING_DP.dp
    val section = FitnessComposeTokens.SECTION_TOP_SPACING_DP.dp
    val touch = FitnessComposeTokens.NAV_ITEM_MIN_HEIGHT_DP.dp
}

object FitnessShape {
    val card = RoundedCornerShape(FitnessComposeTokens.CARD_RADIUS_DP.dp)
    val hero = RoundedCornerShape(FitnessComposeTokens.HERO_RADIUS_DP.dp)
    val input = RoundedCornerShape(FitnessComposeTokens.INPUT_RADIUS_DP.dp)
    val button = RoundedCornerShape(FitnessComposeTokens.BUTTON_RADIUS_DP.dp)
}

data class FitnessSemanticColors(val action: Color, val onAction: Color, val success: Color, val warning: Color)
private val LightSemantic = FitnessSemanticColors(
    Color(FitnessComposeTokens.COLOR_BRAND_BLUE), Color(FitnessComposeTokens.COLOR_BLUE_INK),
    Color(FitnessComposeTokens.COLOR_POSITIVE), Color(FitnessComposeTokens.COLOR_WARNING)
)
private val DarkSemantic = FitnessSemanticColors(
    Color(FitnessComposeTokens.COLOR_BRAND_BLUE), Color(FitnessComposeTokens.COLOR_D_ON_PASTEL_BLUE),
    Color(FitnessComposeTokens.COLOR_D_POSITIVE), Color(FitnessComposeTokens.COLOR_D_WARNING)
)
fun fitnessSemanticColors(dark: Boolean) = if (dark) DarkSemantic else LightSemantic

val LocalFitnessColors = staticCompositionLocalOf { LightSemantic }
