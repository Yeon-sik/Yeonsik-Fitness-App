package com.yeonsik.fitnessapp.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Worst case for the shared glass tab bar: an opaque brand-blue fill directly under a label.
 * Blur and the scroll edge fade only lower that content's weight, so they are left out here.
 */
class FitnessGlassTabBarContrastTest {
    private val brandFill = rgb(FitnessUiTokens.COLOR_BRAND_BLUE)
    private val lightBar = over(
        rgb(FitnessUiTokens.COLOR_SURFACE), FitnessUiTokens.GLASS_BAR_SURFACE_ALPHA_LIGHT, brandFill
    )
    private val darkBar = over(
        rgb(FitnessUiTokens.COLOR_D_SURFACE), FitnessUiTokens.GLASS_BAR_SURFACE_ALPHA_DARK, brandFill
    )

    @Test
    fun unselectedLabelsKeepTextContrastOverBrandFill() {
        assertTextContrast("light unselected", rgb(FitnessUiTokens.COLOR_TEXT), lightBar)
        assertTextContrast("dark unselected", rgb(FitnessUiTokens.COLOR_D_TEXT), darkBar)
    }

    @Test
    fun selectedLabelsKeepTextContrastOnTheSelectionPill() {
        // The pill samples the content itself, not the bar surface beneath it.
        val alpha = FitnessUiTokens.GLASS_SELECTION_TINT_ALPHA
        assertTextContrast(
            "light selected", rgb(FitnessUiTokens.COLOR_BLUE_INK),
            over(rgb(FitnessUiTokens.COLOR_BLUE_CONTAINER), alpha, brandFill)
        )
        assertTextContrast(
            "dark selected", rgb(FitnessUiTokens.COLOR_D_BLUE_INK),
            over(rgb(FitnessUiTokens.COLOR_D_BLUE_CONTAINER), alpha, brandFill)
        )
    }

    private fun assertTextContrast(name: String, foreground: DoubleArray, background: DoubleArray) {
        val ratio = contrast(foreground, background)
        assertTrue("$name contrast $ratio must be at least 4.5:1", ratio >= 4.5)
    }

    private fun rgb(argb: Int) = DoubleArray(3) { ((argb shr (16 - it * 8)) and 0xFF) / 255.0 }

    private fun over(top: DoubleArray, alpha: Float, bottom: DoubleArray) =
        DoubleArray(3) { alpha * top[it] + (1 - alpha) * bottom[it] }

    private fun luminance(color: DoubleArray): Double {
        val linear = color.map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]
    }

    private fun contrast(a: DoubleArray, b: DoubleArray): Double {
        val (high, low) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (high + 0.05) / (low + 0.05)
    }
}
