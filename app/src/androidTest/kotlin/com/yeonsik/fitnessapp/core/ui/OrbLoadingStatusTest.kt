package com.yeonsik.fitnessapp.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OrbLoadingStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun textRollsWithinFixedCardWhileOrbKeepsItsNode() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FitnessComposeTheme(false) {
                OrbLoadingStatus(listOf("첫 문구", "두 번째 문구"))
            }
        }

        compose.onNodeWithText("첫 문구").assertExists()
        val orbBefore = compose.onNodeWithTag("thinking-orb").fetchSemanticsNode().id
        val cardBoundsBefore = compose.onNodeWithTag("orb-loading-card")
            .fetchSemanticsNode().boundsInRoot

        compose.mainClock.advanceTimeBy(1_900)
        compose.onNodeWithText("두 번째 문구").assertExists()
        val orbAfter = compose.onNodeWithTag("thinking-orb").fetchSemanticsNode().id
        val cardBoundsAfter = compose.onNodeWithTag("orb-loading-card")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(orbBefore, orbAfter)
        assertEquals(cardBoundsBefore, cardBoundsAfter)
    }

    @Test
    fun slotGrowsWithFontScaleAndKeepsHeightWhileMessagesChange() {
        var fontScale by mutableFloatStateOf(1f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale)
            ) {
                FitnessComposeTheme(false) {
                    OrbLoadingStatus(listOf(
                        "운동·체중·회복 데이터를 확인하고 있어요", "발전 상태를 계산하고 있어요"
                    ))
                }
            }
        }

        val normalHeight = compose.onNodeWithTag("rolling-status-slot")
            .fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { fontScale = 2f }
        val largeHeight = compose.onNodeWithTag("rolling-status-slot")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(largeHeight > normalHeight)

        compose.mainClock.advanceTimeBy(1_900)
        compose.onNodeWithText("발전 상태를 계산하고 있어요").assertExists()
        val afterRollHeight = compose.onNodeWithTag("rolling-status-slot")
            .fetchSemanticsNode().boundsInRoot.height
        assertEquals(largeHeight, afterRollHeight)
    }
}
