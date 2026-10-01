package com.yeonsik.fitnessapp.feature.records.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordsLoadingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun changingCalendarMonthShowsLargeCenteredOrbAboveLoadingText() {
        compose.setContent {
            FitnessComposeTheme(false) {
                RecordsScreen(
                    state = RecordsUiState.Loading("owner-a", "2026-10", "2026-09-29"),
                    ownerId = "owner-a",
                    today = "2026-09-29",
                    unit = MassUnit.KG,
                    selectedDate = "2026-10-29",
                    actions = actions
                )
            }
        }

        compose.onNodeWithText("기록을 불러오는 중").assertExists()
        compose.onNodeWithTag("orb-loading-card").assertDoesNotExist()
        compose.onNodeWithTag("thinking-orb").assertWidthIsEqualTo(104.dp)

        val region = compose.onNodeWithTag("records-calendar-loading-region")
            .fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("records-calendar-loading-content")
            .fetchSemanticsNode().boundsInRoot
        val orb = compose.onNodeWithTag("thinking-orb").fetchSemanticsNode().boundsInRoot
        val message = compose.onNodeWithText("기록을 불러오는 중")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(region.center.x, content.center.x, 1f)
        assertEquals(region.center.y, content.center.y, 1f)
        assertTrue(message.top >= orb.bottom)
    }

    private val actions = object : RecordsScreenActions {
        override fun selectDate(date: String) = Unit
        override fun previousMonth() = Unit
        override fun nextMonth() = Unit
        override fun today() = Unit
        override fun openRecord(recordId: String) = Unit
        override fun deleteRecord(recordId: String) = Unit
        override fun showBodyMetric(date: String, recordId: String?) = Unit
    }
}
