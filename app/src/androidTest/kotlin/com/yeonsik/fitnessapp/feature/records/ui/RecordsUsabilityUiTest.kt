package com.yeonsik.fitnessapp.feature.records.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry
import com.yeonsik.fitness.shared.feature.records.model.*
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** Screen-only fixtures. No database, account, record deletion or network operation is performed. */
class RecordsUsabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = mutableStateOf(DATE)
    private val owner = mutableStateOf("owner-a")
    private val active = mutableStateOf(true)
    private val fontScale = mutableStateOf(1f)
    private val dark = mutableStateOf(true)
    private val calls = mutableListOf<Pair<String, String>>()
    private val actions = object : RecordsScreenActions {
        override fun selectDate(date: String) { calls += "date" to date; this@RecordsUsabilityUiTest.date.value = date }
        override fun previousMonth() { calls += "previous" to "" }
        override fun nextMonth() { calls += "next" to "" }
        override fun today() { calls += "today" to ""; date.value = TODAY }
        override fun retry() { calls += "retry" to "" }
        override fun openRecord(recordId: String) { calls += "open" to recordId }
        override fun deleteRecord(recordId: String) { calls += "delete" to recordId }
        override fun showBodyMetric(date: String, recordId: String?) { calls += "body" to "$date:$recordId" }
        override fun addMeal(date: String) { calls += "meal" to date }
    }

    @Test fun summaryReflectsSelectedDateAndCalendarStillSelectsDates() {
        show()
        compose.onNodeWithText("운동 1건 · 식단 0건 · 체중 1건").assertIsDisplayed()
        compose.onNodeWithText("운동 부위 · 가슴 · 등 · 어깨 · 하체 · 복근").assertIsDisplayed()
        compose.onNodeWithTag("records-open-day").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("2026-10-10").performScrollTo().performClick()
        compose.onNodeWithTag("records-selected-summary").performScrollTo()
        compose.onNodeWithText("10월 10일 토요일").assertIsDisplayed()
        compose.onNodeWithText("저장된 기록이 없습니다").assertIsDisplayed()
        assertEquals("date" to "2026-10-10", calls.last())
    }

    @Test fun detailOpensWithoutCalendarScrollingAndOpeningWorkoutDoesNotDelete() {
        show()
        compose.onNodeWithTag("records-open-day").assertIsDisplayed().performClick()
        compose.onNodeWithTag("records-day-sheet").assertIsDisplayed()
        compose.onNodeWithTag("records-sheet-workout-workout-a").performScrollTo().performClick()
        assertEquals(listOf("open" to "workout-a"), calls)
        compose.onNodeWithTag("records-day-sheet").assertDoesNotExist()
    }

    @Test fun monthNavigationAndTodayKeepTheirExistingCallbacks() {
        show()
        compose.onNodeWithContentDescription("이전 달").performClick()
        compose.onNodeWithContentDescription("다음 달").performClick()
        compose.onNodeWithTag("records-today").assertIsEnabled().performClick()
        assertEquals(listOf("previous" to "", "next" to "", "today" to ""), calls)
        compose.onNodeWithTag("records-today").assertIsNotEnabled()
        compose.onNodeWithText("10월 21일 수요일").assertIsDisplayed()
    }

    @Test fun deletionRemainsExplicitAndUsesExistingConfirmationCallback() {
        show()
        compose.onNodeWithTag("records-open-day").performClick()
        compose.onNodeWithTag("records-sheet-manage-workout-a").performScrollTo().performClick()
        assertTrue(calls.isEmpty())
        compose.onNodeWithText("운동 기록 삭제").performClick()
        assertEquals(listOf("delete" to "workout-a"), calls)
        compose.onNodeWithTag("records-day-sheet").assertDoesNotExist()
    }

    @Test fun emptyDateCanAddUsingTheSelectedDateAndCloseWithoutLosingSelection() {
        date.value = "2030-12-31"
        show()
        compose.onNodeWithTag("records-open-day").performClick()
        compose.onNodeWithTag("records-day-sheet-close").performClick()
        assertEquals("2030-12-31", date.value)
        compose.onNodeWithTag("records-open-day").performClick()
        compose.onNodeWithTag("records-sheet-add").performClick()
        compose.onNode(hasText("식단") and hasClickAction()).performClick()
        assertEquals("meal" to "2030-12-31", calls.last())
        compose.onNodeWithTag("records-day-sheet").assertDoesNotExist()
    }

    @Test fun sheetDoesNotLeakAcrossOwnerDateOrDestinationChanges() {
        show()
        fun open() { compose.onNodeWithTag("records-open-day").performScrollTo().performClick() }
        open()
        compose.runOnIdle { owner.value = "owner-b" }
        compose.onNodeWithTag("records-day-sheet").assertDoesNotExist()
        open()
        compose.runOnIdle { date.value = "2026-10-10" }
        compose.onNodeWithTag("records-day-sheet").assertDoesNotExist()
        open()
        compose.runOnIdle { active.value = false }
        compose.onNodeWithTag("records-day-sheet").assertDoesNotExist()
        compose.runOnIdle { active.value = true }
        compose.onNodeWithTag("records-day-sheet").assertDoesNotExist()
    }

    @Test fun smallWidthAndLargeFontsKeepSummarySheetAndActionsReachableInBothThemes() {
        show()
        for ((scale, isDark) in listOf(1f to true, 1.3f to false, 2f to true, 2f to false)) {
            compose.runOnIdle { fontScale.value = scale; dark.value = isDark }
            compose.onNodeWithTag("records-today").performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("records-open-day").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("records-sheet-workout-workout-a").performScrollTo().assertIsDisplayed()
            capture("records-${scale}-${isDark}.png")
            compose.onNodeWithTag("records-sheet-manage-workout-a").performScrollTo()
                .assertHeightIsAtLeast(48.dp).assertIsDisplayed()
            compose.onNodeWithTag("records-sheet-body-body-a").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("records-day-sheet-close").performClick()
        }
    }

    @Test fun errorOffersRetryWithoutChangingTheSelectedDate() {
        compose.setContent {
            FitnessComposeTheme(false) {
                Column {
                    RecordsScreen(RecordsUiState.Error("owner-a", "2026-10", TODAY, "읽을 수 없습니다"),
                        "owner-a", TODAY, MassUnit.KG, DATE, actions)
                }
            }
        }
        compose.onNodeWithText("다시 불러오기").performClick()
        assertEquals(listOf("retry" to ""), calls)
        assertEquals(DATE, date.value)
    }

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                FitnessComposeTheme(dark.value) {
                    Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                        RecordsScreen(state(), owner.value, TODAY, MassUnit.KG, date.value, actions,
                            isActualActive = active.value)
                    }
                }
            }
        }
    }

    private fun state(): RecordsUiState {
        val details = RecordsDayDetail(DATE,
            listOf(RecordsWorkoutSummary("workout-a", DATE, "가슴·등·어깨 운동과 긴 루틴 이름",
                "strength", 1800, 1234.0, 12, listOf("가슴", "등", "어깨", "하체", "복근"))),
            listOf(BodyReadEntry("body-a", DATE, 70.0, "")), emptyList())
        val days = (0L until 42L).map { index ->
            val day = LocalDate.parse("2026-09-28").plusDays(index).toString()
            RecordsCalendarDay(day, day == DATE, day == DATE, false,
                if (day == DATE) listOf("가슴", "등", "어깨", "하체", "복근") else emptyList())
        }
        return RecordsUiState.Ready(RecordsSnapshot(owner.value, date.value.take(7), TODAY, days,
            mapOf(DATE to details, date.value to if (date.value == DATE) details
                else RecordsDayDetail(date.value, emptyList(), emptyList(), emptyList())), emptyList()))
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Dialog windows can still be composing at SurfaceFlinger after Compose becomes idle.
        android.os.SystemClock.sleep(400)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(instrumentation.targetContext.cacheDir, name)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private companion object {
        const val DATE = "2026-10-09"
        const val TODAY = "2026-10-21"
    }
}
