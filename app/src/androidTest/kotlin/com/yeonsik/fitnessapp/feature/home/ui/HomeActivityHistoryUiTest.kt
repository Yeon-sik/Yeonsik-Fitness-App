package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityRecordSummary
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityWindowPolicy
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.state.FitnessScreen
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class HomeActivityHistoryUiTest {
    @get:Rule val compose = createComposeRule()
    private val cellMatcher = SemanticsMatcher("activity date cell") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("home-activity-cell-") == true
    }

    @Test fun activityHistoryIsBelowQuickActionsWithoutChangingTheirContent() {
        val snapshot = HomeSnapshot("owner", TODAY, emptyList(), null, emptyList(), emptyMap(), emptyMap(),
            null, emptyMap(), emptyMap(), emptyMap(), null, null, emptyList(), emptyList())
        compose.setContent {
            FitnessComposeTheme(false) {
                HomeDestination(HomeUiState.Ready(snapshot), "owner", TODAY, NoActions(), activityState = ready())
            }
        }
        compose.onNodeWithText("오늘 상태").assertExists()
        compose.onNodeWithText("빠른 이동").assertExists()
        compose.onNodeWithText("활동 내역").assertExists()
        val weight = compose.onNodeWithTag("home-quick-weight").fetchSemanticsNode().boundsInRoot
        val history = compose.onNodeWithTag("home-activity-history").fetchSemanticsNode().boundsInRoot
        assertTrue(history.top >= weight.bottom)
    }

    @Test fun gridHas13WeekColumnsAnd7WeekdayRowsAtNarrowWidthWithoutOverflow() {
        compose.setContent {
            FitnessComposeTheme(false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                    Box(Modifier.width(280.dp).testTag("narrow-history")) {
                        HomeActivityHistorySection(ready(), {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onAllNodes(cellMatcher, useUnmergedTree = true).assertCountEquals(91)
        val bounds = compose.onNodeWithTag("narrow-history").fetchSemanticsNode().boundsInRoot
        val cells = ready().cells
        cells.forEach { cell ->
            val cellBounds = compose.onNodeWithTag("home-activity-cell-${cell.date}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(cellBounds.width > 0f)
            assertTrue(cellBounds.left >= bounds.left - 1f)
            assertTrue(cellBounds.right <= bounds.right + 1f)
        }
        val first = compose.onNodeWithTag("home-activity-cell-2026-07-06").fetchSemanticsNode().boundsInRoot
        val tuesday = compose.onNodeWithTag("home-activity-cell-2026-07-07").fetchSemanticsNode().boundsInRoot
        val nextMonday = compose.onNodeWithTag("home-activity-cell-2026-07-13").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, tuesday.left, 1f)
        assertEquals(first.top, nextMonday.top, 1f)
        assertTrue(tuesday.top > first.top)
        assertTrue(nextMonday.left > first.left)
    }

    @Test fun currentNextAndOldestPreviousAreDisabledAndSelectorMovesBetweenPages() {
        val state = mutableStateOf<HomeActivityUiState>(ready())
        fun select(page: Int) { state.value = ready(page) }
        compose.setContent {
            FitnessComposeTheme(false) {
                HomeActivityHistorySection(
                    state.value,
                    { select((state.value as HomeActivityUiState.Ready).window.pageOffset + 1) },
                    { select((state.value as HomeActivityUiState.Ready).window.pageOffset - 1) },
                    ::select, {}
                )
            }
        }
        compose.onNodeWithTag("home-activity-next").assertIsNotEnabled()
        compose.onNodeWithTag("home-activity-previous").assertIsEnabled().performClick()
        assertEquals(1, (state.value as HomeActivityUiState.Ready).window.pageOffset)
        compose.onNodeWithTag("home-activity-next").assertIsEnabled()
        compose.onNodeWithTag("home-activity-period").performClick()
        compose.onNodeWithTag("home-activity-period-3").performClick()
        assertEquals(3, (state.value as HomeActivityUiState.Ready).window.pageOffset)
        compose.onNodeWithTag("home-activity-previous").assertIsNotEnabled()
        compose.onNodeWithTag("home-activity-period").performClick()
        compose.onNodeWithTag("home-activity-period-0").performClick()
        compose.onNodeWithTag("home-activity-next").assertIsNotEnabled()
    }

    @Test fun zeroOneTwoThreeKindsAndNonTrackingStatesHaveDistinctSemantics() {
        val state = ready(kinds = mapOf(
            "2026-09-28" to setOf(HomeActivityKind.EXERCISE),
            "2026-09-29" to setOf(HomeActivityKind.EXERCISE, HomeActivityKind.WEIGHT),
            "2026-09-30" to HomeActivityKind.entries.toSet()
        ))
        compose.setContent { FitnessComposeTheme(false) { HomeActivityHistorySection(state, {}, {}, {}, {}) } }
        listOf(
            "2026년 9월 27일, 기록 없음, 0종 기록",
            "2026년 9월 28일, 운동, 1종 기록",
            "2026년 9월 29일, 운동, 체중, 2종 기록",
            "2026년 9월 30일, 운동, 체중, 식단, 3종 기록"
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNodeWithContentDescription("2026년 10월 2일, 미래 날짜").assertIsNotEnabled()
    }

    @Test fun beforeTrackingIsDistinctAndEmptyDoesNotRender91Cells() {
        val state = mutableStateOf<HomeActivityUiState>(ready(first = "2026-09-30"))
        compose.setContent { FitnessComposeTheme(false) { HomeActivityHistorySection(state.value, {}, {}, {}, {}) } }
        compose.onNodeWithContentDescription("2026년 9월 29일, 첫 기록 이전").assertExists()
        compose.onNodeWithContentDescription("2026년 9월 30일, 기록 없음, 0종 기록").assertExists()
        compose.onNodeWithTag("home-activity-previous").assertIsNotEnabled()
        compose.runOnIdle { state.value = HomeActivityUiState.Empty(HomeActivityRequestIdentity("owner", TODAY, 0)) }
        compose.onNodeWithText("아직 활동 기록이 없어요.").assertExists()
        compose.onAllNodes(cellMatcher, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun clickingTrackedCellShowsSmallDateAndRecordSummaryBubble() {
        val date = "2026-09-30"
        val selectedDates = mutableListOf<String>()
        val details = HomeActivityDayDetails(date, listOf(
            HomeActivityRecordSummary(
                HomeActivityKind.EXERCISE,
                name = "하체 루틴",
                category = "하체"
            ),
            HomeActivityRecordSummary(HomeActivityKind.WEIGHT, weightKg = 62.4),
            HomeActivityRecordSummary(HomeActivityKind.MEAL, name = "현미밥", category = "점심")
        ))
        compose.setContent {
            FitnessComposeTheme(false) {
                HomeActivityHistorySection(
                    ready(kinds = mapOf(date to HomeActivityKind.entries.toSet())),
                    {}, {}, {}, {},
                    dayDetails = HomeActivityDayDetailsUiState.Ready("owner", details),
                    onSelectDate = selectedDates::add
                )
            }
        }

        compose.onNodeWithTag("home-activity-cell-$date").performClick()
        compose.onNodeWithTag("home-activity-day-bubble-$date").assertExists()
        assertTrue(
            compose.onNodeWithTag("home-activity-cell-$date").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription)
                .orEmpty().any { it.endsWith("선택됨") }
        )
        compose.onNodeWithText(date).assertExists()
        compose.onNodeWithText("운동 · 하체 루틴 (하체)").assertExists()
        compose.onNodeWithText("체중 · 62.4kg").assertExists()
        compose.onNodeWithText("식단 · 점심 현미밥").assertExists()
        assertEquals(listOf(date), selectedDates)

        compose.onNodeWithTag("home-activity-cell-$TODAY").performClick()
        compose.onNodeWithTag("home-activity-day-bubble-$date").assertDoesNotExist()
        compose.onNodeWithTag("home-activity-day-bubble-$TODAY").assertExists()
        compose.onNodeWithText("기록 없음").assertExists()
        compose.onNodeWithTag("home-activity-cell-2026-10-02").assertIsNotEnabled()
    }

    @Test fun sectionFailureProvidesRetry() {
        var retries = 0
        compose.setContent {
            FitnessComposeTheme(false) {
                HomeActivityHistorySection(
                    HomeActivityUiState.Error(HomeActivityRequestIdentity("owner", TODAY, 0), "read failed"),
                    {}, {}, {}, { retries++ }
                )
            }
        }
        compose.onNodeWithText("활동 내역을 불러오지 못했습니다.").assertExists()
        compose.onNodeWithTag("home-activity-retry").performClick()
        assertEquals(1, retries)
    }

    @Test fun pagingThroughLoadingFailureAndEmptyWindowsKeepsScrollAndSectionHeight() {
        val initial = ready(kinds = mapOf(TODAY to setOf(HomeActivityKind.WEIGHT)))
        val state = mutableStateOf<HomeActivityUiState>(initial)
        val scroll = ScrollState(0)
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.width(320.dp).height(420.dp).verticalScroll(scroll)) {
                    Spacer(Modifier.height(700.dp))
                    HomeActivityHistorySection(state.value, {}, {}, {}, {})
                }
            }
        }
        compose.runOnIdle { runBlocking { scroll.scrollTo(scroll.maxValue) } }
        val offset = scroll.value
        val bounds = compose.onNodeWithTag("home-activity-history").fetchSemanticsNode().boundsInRoot
        assertTrue(offset > 0)
        fun assertUnmoved() {
            compose.waitForIdle()
            val current = compose.onNodeWithTag("home-activity-history").fetchSemanticsNode().boundsInRoot
            assertEquals(offset, scroll.value)
            assertEquals(bounds.top, current.top, 1f)
            assertEquals(bounds.height, current.height, 1f)
        }
        val previousPage = ready(1)
        compose.runOnIdle {
            state.value = HomeActivityUiState.Loading(previousPage.identity, previousPage.window, initial)
        }
        compose.onNodeWithText("활동 내역을 불러오는 중입니다.").assertExists()
        compose.onNodeWithContentDescription("2026년 10월 1일, 체중, 1종 기록").assertDoesNotExist()
        assertUnmoved()
        compose.runOnIdle {
            state.value = HomeActivityUiState.Error(previousPage.identity, "read failed", previousPage.window, initial)
        }
        compose.onNodeWithTag("home-activity-retry").assertExists()
        assertUnmoved()
        compose.runOnIdle { state.value = previousPage }
        compose.onAllNodes(cellMatcher, useUnmergedTree = true).assertCountEquals(91)
        assertUnmoved()
        compose.runOnIdle {
            state.value = HomeActivityUiState.Loading(initial.identity, initial.window, previousPage)
        }
        assertUnmoved()
        compose.runOnIdle { state.value = ready() }
        assertUnmoved()
    }

    private fun ready(
        page: Int = 0, first: String = "2026-01-01", kinds: Map<String, Set<HomeActivityKind>> = emptyMap()
    ): HomeActivityUiState.Ready {
        val window = HomeActivityWindowPolicy.window(LocalDate.parse(TODAY), LocalDate.parse(first), page)
        return HomeActivityUiState.Ready(HomeActivityRequestIdentity("owner", TODAY, page), window, window.cells(kinds))
    }

    private class NoActions : HomeScreenActions {
        override fun continueWorkout() = Unit
        override fun navigate(screen: FitnessScreen) = Unit
        override fun showBodyMetric() = Unit
        override fun openMealManagement(date: String, returnScreen: FitnessScreen) = Unit
    }
    private companion object { const val TODAY = "2026-10-01" }
}
