package com.yeonsik.fitnessapp.feature.records.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Rule
import org.junit.Test

class RecordsLoadingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun changingCalendarMonthShowsOrbBesideLoadingText() {
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
        compose.onNodeWithTag("thinking-orb").assertExists()
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
