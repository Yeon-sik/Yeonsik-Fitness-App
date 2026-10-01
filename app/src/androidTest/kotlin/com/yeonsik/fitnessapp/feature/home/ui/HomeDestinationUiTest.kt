package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeDestinationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun briefingHasNoActionWithoutActiveSessionAndQuickActionsFormThreeWideAreas() {
        val actions = RecordingActions()
        showHome(snapshot(), actions)

        compose.onNodeWithText("오늘").assertExists()
        compose.onNodeWithText("아직 완료한 운동이 없어요.").assertExists()
        compose.onNodeWithText("체중 미기록 · 식사 미기록").assertExists()
        compose.onNodeWithText("운동 시작").assertDoesNotExist()
        compose.onNodeWithText("기록 보기").assertDoesNotExist()
        compose.onNodeWithText("운동 이어가기").assertDoesNotExist()
        compose.onNodeWithText("빠른 이동").assertExists()

        val workout = compose.onNodeWithTag("home-quick-workout").fetchSemanticsNode().boundsInRoot
        val strength = compose.onNodeWithTag("home-quick-strength").fetchSemanticsNode().boundsInRoot
        val cardio = compose.onNodeWithTag("home-quick-cardio").fetchSemanticsNode().boundsInRoot
        val weight = compose.onNodeWithTag("home-quick-weight").fetchSemanticsNode().boundsInRoot
        val meal = compose.onNodeWithTag("home-quick-meal").fetchSemanticsNode().boundsInRoot
        assertTrue(strength.width > 0f && cardio.width > 0f)
        assertEquals(strength.width, cardio.width, 1f)
        assertEquals(workout.width, weight.width, 1f)
        assertEquals(weight.width, meal.width, 1f)
        assertTrue(strength.center.x < cardio.center.x)
        assertEquals(workout.center.x, weight.center.x, 1f)
        assertEquals(weight.center.x, meal.center.x, 1f)
        assertTrue(strength.center.y < weight.center.y)
        assertEquals(strength.center.y, cardio.center.y, 1f)
        assertTrue(weight.center.y < meal.center.y)
    }

    @Test
    fun completedBriefingUsesMetricsAndQuickActionsKeepTheirDestinations() {
        val actions = RecordingActions()
        showHome(
            snapshot().copy(
                dayMetrics = mapOf(TODAY to HomeDayWorkoutMetrics(1, 18, 12_400.0, 4_080)),
                mealCounts = mapOf(TODAY to 3),
                todayWeight = HomeBodyMetric("weight", TODAY, 88.4, "")
            ),
            actions
        )

        compose.onNodeWithText("오늘 운동 완료").assertExists()
        compose.onNodeWithText("1회 · 18세트 · 12.4t · 1시간 8분").assertExists()
        compose.onNodeWithText("체중 88.4kg · 식사 3회").assertExists()
        compose.onNodeWithText("운동 시작").assertDoesNotExist()
        compose.onNodeWithText("기록 보기").assertDoesNotExist()

        compose.onNodeWithTag("home-quick-strength").performClick()
        compose.onNodeWithTag("home-quick-cardio").performClick()
        compose.onNodeWithTag("home-quick-weight").performClick()
        compose.onNodeWithTag("home-quick-meal").performClick()
        assertEquals(listOf(FitnessScreen.STRENGTH, FitnessScreen.CARDIO), actions.destinations)
        assertEquals(1, actions.bodyMetricOpens)
        assertEquals(listOf(TODAY to FitnessScreen.HOME), actions.mealOpens)
    }

    @Test
    fun activeSessionPutsContinueActionFirstEvenWhenWorkoutIsCompleted() {
        val actions = RecordingActions()
        showHome(
            snapshot().copy(
                inProgressSessionId = "active",
                dayMetrics = mapOf(TODAY to HomeDayWorkoutMetrics(1, 18, 12_400.0, 4_080))
            ),
            actions
        )

        compose.onNodeWithText("운동 진행 중").assertExists()
        compose.onNodeWithText("진행 중인 운동을 이어서 기록하세요.").assertExists()
        compose.onNodeWithText("오늘 운동 완료").assertDoesNotExist()
        compose.onNodeWithText("운동 이어가기").performClick()
        assertEquals(1, actions.continues)
    }

    @Test
    fun quickActionsFitNarrowLayoutWithLargeText() {
        compose.setContent {
            FitnessComposeTheme(false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                    Box(Modifier.width(280.dp).testTag("narrow-home")) {
                        HomeDestination(HomeUiState.Ready(snapshot()), "owner", TODAY, RecordingActions())
                    }
                }
            }
        }
        compose.waitForIdle()

        val container = compose.onNodeWithTag("narrow-home").fetchSemanticsNode().boundsInRoot
        listOf("strength", "cardio", "weight", "meal").forEach { action ->
            val bounds = compose.onNodeWithTag("home-quick-$action").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width > 0f)
            assertTrue(bounds.left >= container.left - 1f)
            assertTrue(bounds.right <= container.right + 1f)
        }
    }

    private fun showHome(snapshot: HomeSnapshot, actions: RecordingActions) {
        compose.setContent {
            FitnessComposeTheme(false) {
                HomeDestination(
                    homeState = HomeUiState.Ready(snapshot),
                    ownerId = "owner",
                    today = TODAY,
                    actions = actions
                )
            }
        }
        compose.waitForIdle()
    }

    private fun snapshot() = HomeSnapshot(
        ownerId = "owner",
        today = TODAY,
        todaySessions = emptyList(),
        activeRoutineId = null,
        routines = emptyList(),
        routineExercises = emptyMap(),
        latestRoutineDates = emptyMap(),
        inProgressSessionId = null,
        dayMetrics = emptyMap(),
        mealCounts = emptyMap(),
        mealNutritionTotals = emptyMap(),
        nutritionGoal = null,
        todayWeight = null,
        todayBodyMetrics = emptyList(),
        todayMeals = emptyList()
    )

    private class RecordingActions : HomeScreenActions {
        val destinations = mutableListOf<FitnessScreen>()
        val mealOpens = mutableListOf<Pair<String, FitnessScreen>>()
        var continues = 0
        var bodyMetricOpens = 0

        override fun continueWorkout() { continues++ }
        override fun navigate(screen: FitnessScreen) { destinations += screen }
        override fun showBodyMetric() { bodyMetricOpens++ }
        override fun openMealManagement(date: String, returnScreen: FitnessScreen) {
            mealOpens += date to returnScreen
        }
    }

    private companion object { const val TODAY = "2026-10-01" }
}
