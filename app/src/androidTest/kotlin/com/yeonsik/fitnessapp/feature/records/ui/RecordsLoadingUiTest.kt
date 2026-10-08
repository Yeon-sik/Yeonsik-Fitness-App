package com.yeonsik.fitnessapp.feature.records.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.rememberTopLevelEntranceState
import com.yeonsik.fitness.shared.feature.records.model.RecordsCalendarDay
import com.yeonsik.fitness.shared.feature.records.model.RecordsDayDetail
import com.yeonsik.fitness.shared.feature.records.model.RecordsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordsLoadingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun addWeightAndMealUseTheSelectedPastOrFutureDateAndWeightIsAlwaysNew() {
        val date = mutableStateOf("2020-01-02")
        val weights = mutableListOf<Pair<String, String?>>()
        val meals = mutableListOf<String>()
        val addActions = object : RecordsScreenActions by actions {
            override fun showBodyMetric(date: String, recordId: String?) { weights += date to recordId }
            override fun addMeal(date: String) { meals += date }
        }
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val selected = date.value
                    RecordsScreen(RecordsUiState.Ready(RecordsSnapshot("owner-a", selected.take(7), selected,
                        listOf(RecordsCalendarDay(selected, false, false, false)),
                        mapOf(selected to RecordsDayDetail(selected, emptyList(), emptyList(), emptyList())), emptyList())),
                        "owner-a", "2026-10-08", MassUnit.KG, selected, addActions)
                }
            }
        }
        for (selected in listOf("2020-01-02", "2030-12-31")) {
            compose.runOnIdle { date.value = selected }
            compose.onNodeWithTag("records-add").performScrollTo().performClick()
            compose.onNode(hasText("체중") and hasClickAction()).performClick()
            compose.onNodeWithTag("records-add").performClick()
            compose.onNode(hasText("식단") and hasClickAction()).performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf("2020-01-02" to null, "2030-12-31" to null), weights)
            assertEquals(listOf("2020-01-02", "2030-12-31"), meals)
        }
    }

    @Test
    fun calendarAndDetailEnterWhileHeaderMonthAndTodayControlsStayFixed() {
        compose.mainClock.autoAdvance = false
        val date = "2026-10-02"
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RecordsScreen(
                        RecordsUiState.Ready(RecordsSnapshot(
                            "owner-a", "2026-10", date,
                            listOf(RecordsCalendarDay(date, false, false, false)),
                            mapOf(date to RecordsDayDetail(date, emptyList(), emptyList(), emptyList())),
                            emptyList()
                        )),
                        "owner-a", date, MassUnit.KG, date, actions,
                        entranceState = rememberTopLevelEntranceState("RECORDS"),
                        entranceToken = 1L
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val header = compose.onNodeWithText("기록").fetchSemanticsNode().boundsInRoot
        val previous = compose.onNodeWithContentDescription("이전 달").fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithContentDescription("다음 달").fetchSemanticsNode().boundsInRoot
        val today = compose.onNodeWithText("오늘로 이동").fetchSemanticsNode().boundsInRoot
        val legend = compose.onNodeWithTag("records-legend-workout").fetchSemanticsNode().boundsInRoot
        val calendar = compose.onNodeWithText("월").fetchSemanticsNode().boundsInRoot
        val detail = compose.onNodeWithText("$date 상세").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(600)
        assertEquals(header, compose.onNodeWithText("기록").fetchSemanticsNode().boundsInRoot)
        assertEquals(previous, compose.onNodeWithContentDescription("이전 달").fetchSemanticsNode().boundsInRoot)
        assertEquals(next, compose.onNodeWithContentDescription("다음 달").fetchSemanticsNode().boundsInRoot)
        assertEquals(today, compose.onNodeWithText("오늘로 이동").fetchSemanticsNode().boundsInRoot)
        assertTrue(legend.top > compose.onNodeWithTag("records-legend-workout").fetchSemanticsNode().boundsInRoot.top + 1f)
        assertTrue(calendar.top > compose.onNodeWithText("월").fetchSemanticsNode().boundsInRoot.top + 1f)
        assertTrue(detail.top > compose.onNodeWithText("$date 상세").fetchSemanticsNode().boundsInRoot.top + 1f)
    }

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
        override fun addMeal(date: String) = Unit
    }
}
