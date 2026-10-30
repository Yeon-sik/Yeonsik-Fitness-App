package com.yeonsik.fitnessapp.core.ui

import com.yeonsik.fitnessapp.core.ui.WcagContrast.over
import com.yeonsik.fitnessapp.core.ui.WcagContrast.ratio
import com.yeonsik.fitnessapp.core.ui.WcagContrast.rgb
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val ratio = ratio(foreground, background)
        assertTrue("$name contrast $ratio must be at least 4.5:1", ratio >= 4.5)
    }
}
