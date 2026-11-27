package com.yeonsik.fitness.shared.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FitnessComposeTokens {
    const val COLOR_D_SURFACE = 0xFF1C1E22.toInt()
    const val COLOR_SURFACE = 0xFFFFFFFF.toInt()
    const val COLOR_D_BACKGROUND = 0xFF090B0E.toInt()
    const val COLOR_BACKGROUND = 0xFFF5F5F7.toInt()
    const val COLOR_D_SUBTLE = 0xFF272A30.toInt()
    const val COLOR_SUBTLE = 0xFFEEEFF2.toInt()
    const val COLOR_D_TEXT = 0xFFF5F5F7.toInt()
    const val COLOR_TEXT = 0xFF1C1C1E.toInt()
    const val COLOR_D_MUTED = 0xFFADB0B8.toInt()
    const val COLOR_MUTED = 0xFF686C75.toInt()
    const val COLOR_D_BORDER = 0xFF393D45.toInt()
    const val COLOR_BORDER = 0xFFE1E3E8.toInt()
    const val COLOR_D_BLUE_INK = 0xFFD9F0FF.toInt()
    const val COLOR_BLUE_INK = 0xFF173B55.toInt()
    const val COLOR_D_BLUE_CONTAINER = 0xFF12394D.toInt()
    const val COLOR_BLUE_CONTAINER = 0xFFE0F2FE.toInt()
    const val COLOR_BRAND_BLUE = 0xFF38BDF8.toInt()
    const val COLOR_D_ON_PASTEL_BLUE = 0xFF0E2938.toInt()
    const val COLOR_D_NEGATIVE = 0xFFFF8A80.toInt()
    const val COLOR_NEGATIVE = 0xFFC0453E.toInt()
    const val INPUT_RADIUS_DP = 14
    const val BUTTON_RADIUS_DP = 14
    const val CARD_RADIUS_DP = 20
    const val SHEET_RADIUS_DP = 24
    const val CARD_GAP_DP = 12
    const val FORM_ITEM_GAP_DP = 8
}

fun fitnessColorScheme(dark: Boolean) = run {
    val surface = Color(if (dark) FitnessComposeTokens.COLOR_D_SURFACE else FitnessComposeTokens.COLOR_SURFACE)
    val background = Color(if (dark) FitnessComposeTokens.COLOR_D_BACKGROUND else FitnessComposeTokens.COLOR_BACKGROUND)
    val subtle = Color(if (dark) FitnessComposeTokens.COLOR_D_SUBTLE else FitnessComposeTokens.COLOR_SUBTLE)
    val ink = Color(if (dark) FitnessComposeTokens.COLOR_D_TEXT else FitnessComposeTokens.COLOR_TEXT)
    val muted = Color(if (dark) FitnessComposeTokens.COLOR_D_MUTED else FitnessComposeTokens.COLOR_MUTED)
    val border = Color(if (dark) FitnessComposeTokens.COLOR_D_BORDER else FitnessComposeTokens.COLOR_BORDER)
    val blueInk = Color(if (dark) FitnessComposeTokens.COLOR_D_BLUE_INK else FitnessComposeTokens.COLOR_BLUE_INK)
    val blueContainer = Color(if (dark) FitnessComposeTokens.COLOR_D_BLUE_CONTAINER else FitnessComposeTokens.COLOR_BLUE_CONTAINER)
    val action = Color(FitnessComposeTokens.COLOR_BRAND_BLUE)
    val onAction = Color(if (dark) FitnessComposeTokens.COLOR_D_ON_PASTEL_BLUE else FitnessComposeTokens.COLOR_BLUE_INK)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = action,
        onPrimary = onAction,
        primaryContainer = blueContainer, onPrimaryContainer = blueInk, inversePrimary = action,
        secondary = muted, onSecondary = surface, secondaryContainer = subtle, onSecondaryContainer = ink,
        tertiary = muted, onTertiary = surface, tertiaryContainer = subtle, onTertiaryContainer = ink,
        background = background, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = subtle, onSurfaceVariant = muted, surfaceDim = background, surfaceBright = surface,
        surfaceContainerLowest = background, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = subtle, surfaceContainerHighest = subtle, surfaceTint = Color.Transparent,
        inverseSurface = ink, inverseOnSurface = surface, outline = muted, outlineVariant = border,
        error = Color(if (dark) FitnessComposeTokens.COLOR_D_NEGATIVE else FitnessComposeTokens.COLOR_NEGATIVE),
        onError = surface, errorContainer = subtle,
        onErrorContainer = Color(if (dark) FitnessComposeTokens.COLOR_D_NEGATIVE else FitnessComposeTokens.COLOR_NEGATIVE)
    )
}

fun fitnessTypography(platformStyle: PlatformTextStyle? = null): Typography {
    fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = weight,
        fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp,
        platformStyle = platformStyle
    )
    return Typography(
        displayLarge = type(40, 48, FontWeight.SemiBold), displayMedium = type(36, 44, FontWeight.SemiBold),
        displaySmall = type(32, 40, FontWeight.SemiBold), headlineLarge = type(30, 38, FontWeight.SemiBold),
        headlineMedium = type(28, 36, FontWeight.SemiBold), headlineSmall = type(24, 32, FontWeight.SemiBold),
        titleLarge = type(20, 28, FontWeight.SemiBold), titleMedium = type(17, 24, FontWeight.SemiBold),
        titleSmall = type(14, 20, FontWeight.SemiBold), bodyLarge = type(16, 24),
        bodyMedium = type(14, 22), bodySmall = type(12, 18), labelLarge = type(14, 20, FontWeight.SemiBold),
        labelMedium = type(12, 18, FontWeight.Medium), labelSmall = type(11, 16, FontWeight.Medium)
    )
}

fun fitnessShapes() = Shapes(
    extraSmall = RoundedCornerShape(FitnessComposeTokens.INPUT_RADIUS_DP.dp),
    small = RoundedCornerShape(FitnessComposeTokens.INPUT_RADIUS_DP.dp),
    medium = RoundedCornerShape(FitnessComposeTokens.CARD_RADIUS_DP.dp),
    large = RoundedCornerShape(FitnessComposeTokens.CARD_RADIUS_DP.dp),
    extraLarge = RoundedCornerShape(FitnessComposeTokens.SHEET_RADIUS_DP.dp)
)
