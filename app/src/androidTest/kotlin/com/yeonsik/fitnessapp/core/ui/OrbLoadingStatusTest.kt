package com.yeonsik.fitnessapp.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
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
}
