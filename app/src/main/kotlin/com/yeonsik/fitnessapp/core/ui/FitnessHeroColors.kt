package com.yeonsik.fitnessapp.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

@Immutable
internal data class FitnessHeroColors(
    val surface: Brush,
    val border: Color,
    val title: Color,
    val value: Color,
    val muted: Color,
    val increase: Color,
    val decrease: Color,
    val track: Color,
    val markerOutline: Color,
    val chipContainer: Color,
    val chipContent: Color
)

internal val LightHeroColors = FitnessHeroColors(
    surface = SolidColor(Color(FitnessUiTokens.COLOR_HERO_SURFACE)),
    border = Color(FitnessUiTokens.COLOR_HERO_BORDER),
    title = Color(FitnessUiTokens.COLOR_TEXT),
    value = Color(FitnessUiTokens.COLOR_TEXT),
    muted = Color(FitnessUiTokens.COLOR_MUTED),
    increase = Color(FitnessUiTokens.COLOR_POSITIVE),
    decrease = Color(FitnessUiTokens.COLOR_NEGATIVE),
    track = Color(FitnessUiTokens.COLOR_TEXT).copy(alpha = 0.10f),
    markerOutline = Color(FitnessUiTokens.COLOR_MUTED),
    chipContainer = Color(FitnessUiTokens.COLOR_BRAND_BLUE),
    chipContent = Color(FitnessUiTokens.COLOR_BLUE_INK)
)

internal val DarkHeroColors = FitnessHeroColors(
    surface = Brush.verticalGradient(listOf(
        Color(FitnessUiTokens.COLOR_D_HERO_SURFACE_TOP),
        Color(FitnessUiTokens.COLOR_D_HERO_SURFACE_BOTTOM)
    )),
    border = Color(FitnessUiTokens.COLOR_D_HERO_BORDER),
    title = Color(FitnessUiTokens.COLOR_D_BLUE_INK),
    value = Color(FitnessUiTokens.COLOR_D_BLUE_INK),
    muted = Color(FitnessUiTokens.COLOR_D_HERO_MUTED),
    increase = Color(FitnessUiTokens.COLOR_D_POSITIVE),
    decrease = Color(FitnessUiTokens.COLOR_D_NEGATIVE),
    track = Color(FitnessUiTokens.COLOR_D_BLUE_INK).copy(alpha = 0.18f),
    markerOutline = Color(FitnessUiTokens.COLOR_D_HERO_MUTED),
    chipContainer = Color(FitnessUiTokens.COLOR_BRAND_BLUE),
    chipContent = Color(FitnessUiTokens.COLOR_D_ON_PASTEL_BLUE)
)

internal val LocalFitnessHeroColors = staticCompositionLocalOf { LightHeroColors }
