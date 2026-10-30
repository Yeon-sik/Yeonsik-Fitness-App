package com.yeonsik.fitnessapp.core.ui

import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_BACKGROUND
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_BLUE_CONTAINER
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_BLUE_INK
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_BRAND_BLUE
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_BRAND_BLUE_TEXT
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_D_ON_PASTEL_BLUE
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_D_SUBTLE
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_D_SURFACE
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_ON_BRAND_BLUE_TEXT
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_SUBTLE
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens.COLOR_SURFACE
import org.junit.Assert.assertTrue
import org.junit.Test

/** Text and fill roles keep WCAG AA (4.5:1) on every surface they are used on. */
class FitnessColorContrastTest {
    @Test
    fun lightPrimaryIsReadableOnEveryLightSurface() {
        for ((name, surface) in listOf(
            "surface" to COLOR_SURFACE, "background" to COLOR_BACKGROUND,
            "subtle" to COLOR_SUBTLE, "blue container" to COLOR_BLUE_CONTAINER
        )) {
            assertText("light primary on $name", COLOR_BRAND_BLUE_TEXT, surface)
        }
        assertText("light onPrimary on primary", COLOR_ON_BRAND_BLUE_TEXT, COLOR_BRAND_BLUE_TEXT)
    }

    @Test
    fun brandFillsKeepReadableContentInBothThemes() {
        assertText("light onAction on action", COLOR_BLUE_INK, COLOR_BRAND_BLUE)
        assertText("dark onAction on action", COLOR_D_ON_PASTEL_BLUE, COLOR_BRAND_BLUE)
    }

    @Test
    fun darkPrimaryIsReadableOnDarkSurfaces() {
        assertText("dark primary on surface", COLOR_BRAND_BLUE, COLOR_D_SURFACE)
        assertText("dark primary on subtle", COLOR_BRAND_BLUE, COLOR_D_SUBTLE)
    }

    @Test
    fun heroTextIsReadableOnItsTonalSurface() {
        val light = mapOf(
            "title/value" to FitnessUiTokens.COLOR_TEXT, "muted" to FitnessUiTokens.COLOR_MUTED,
            "increase" to FitnessUiTokens.COLOR_POSITIVE, "decrease" to FitnessUiTokens.COLOR_NEGATIVE
        )
        for ((role, color) in light) assertText("light hero $role", color, FitnessUiTokens.COLOR_HERO_SURFACE)
        val dark = mapOf(
            "title/value" to FitnessUiTokens.COLOR_D_BLUE_INK, "muted" to FitnessUiTokens.COLOR_D_HERO_MUTED,
            "increase" to FitnessUiTokens.COLOR_D_POSITIVE, "decrease" to FitnessUiTokens.COLOR_D_NEGATIVE
        )
        // The dark surface is a gradient; both ends must pass.
        for (surface in listOf(FitnessUiTokens.COLOR_D_HERO_SURFACE_TOP, FitnessUiTokens.COLOR_D_HERO_SURFACE_BOTTOM)) {
            for ((role, color) in dark) assertText("dark hero $role", color, surface)
        }
    }

    private fun assertText(name: String, foreground: Int, background: Int) {
        val ratio = WcagContrast.ratio(foreground, background)
        assertTrue("$name contrast $ratio must be at least 4.5:1", ratio >= 4.5)
    }
}
