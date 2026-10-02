package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.yeonsik.fitnessapp.app.navigation.AppNavigationState
import com.yeonsik.fitnessapp.app.navigation.AppNavigationViewModel
import com.yeonsik.fitnessapp.app.navigation.TopLevelSwipeHost
import com.yeonsik.fitnessapp.app.navigation.destinationScrollStateKey
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.rememberTopLevelEntranceState
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.model.HomeTodayWorkoutStatus
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
        compose.onNodeWithTag("home-hero-domain-workout").assertExists()
        compose.onNodeWithTag("home-hero-domain-meal").assertExists()
        compose.onNodeWithTag("home-hero-domain-body").assertExists()
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
        assertTrue(strength.center.y < meal.center.y)
        assertEquals(strength.center.y, cardio.center.y, 1f)
        assertTrue(meal.center.y < weight.center.y)
    }

    @Test
    fun completedHeroUsesProjectionAndQuickActionsKeepTheirDestinations() {
        val actions = RecordingActions()
        showHome(
            snapshot().copy(
                todayWorkoutStatus = HomeTodayWorkoutStatus(true, true, listOf("등", "이두"), true, 1_920),
                mealCounts = mapOf(TODAY to 3),
                todayWeight = HomeBodyMetric("weight", TODAY, 88.4, "")
            ),
            actions
        )

        compose.onNodeWithText("등 · 이두").assertExists()
        compose.onNodeWithText("유산소 32분").assertExists()
        compose.onNodeWithText("3회").assertExists()
        compose.onNodeWithText("88.4 kg").assertExists()
        compose.onNodeWithText("운동 시작").assertDoesNotExist()
        compose.onNodeWithText("기록 보기").assertDoesNotExist()

        compose.onNodeWithTag("home-quick-strength").performScrollTo().performClick()
        compose.onNodeWithTag("home-quick-cardio").performScrollTo().performClick()
        compose.onNodeWithTag("home-quick-weight").performScrollTo().performClick()
        compose.onNodeWithTag("home-quick-meal").performScrollTo().performClick()
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
                todayWorkoutStatus = HomeTodayWorkoutStatus(true, true, listOf("등", "이두"))
            ),
            actions
        )

        compose.onNodeWithText("오늘").assertExists()
        compose.onNodeWithText("진행 중").assertExists()
        compose.onNodeWithText("등 · 이두").assertExists()
        compose.onNodeWithText("운동 이어가기").performClick()
        assertEquals(1, actions.continues)
    }

    @Test
    fun quickActionsFitNarrowLayoutWithLargeText() {
        compose.setContent {
            FitnessComposeTheme(false) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()).testTag("narrow-home")) {
                        HomeDestination(HomeUiState.Ready(snapshot()), "owner", TODAY, RecordingActions())
                    }
                }
            }
        }
        compose.waitForIdle()

        val container = compose.onNodeWithTag("narrow-home").fetchSemanticsNode().boundsInRoot
        listOf("strength", "cardio", "weight", "meal").forEach { action ->
            compose.onNodeWithTag("home-quick-$action").performScrollTo()
            val bounds = compose.onNodeWithTag("home-quick-$action").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width > 0f)
            assertTrue(bounds.left >= container.left - 1f)
            assertTrue(bounds.right <= container.right + 1f)
        }
    }

    @Test
    fun weightAndMealLabelsAreVerticallyCenteredInTheirCards() {
        showHome(snapshot(), RecordingActions())
        listOf("weight" to "체중", "meal" to "식단").forEach { (tag, title) ->
            compose.onNodeWithTag("home-quick-$tag").performScrollTo()
            val card = compose.onNodeWithTag("home-quick-$tag").fetchSemanticsNode().boundsInRoot
            val label = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag("home-quick-$tag")),
                useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(card.center.y, label.center.y, 1f)
        }
    }

    @Test
    fun firstEntranceAnimatesButPreviewAndCurrentRecreationDoNotReplayIt() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            FitnessComposeTheme(false) {
                val entrance = rememberTopLevelEntranceState("HOME")
                val savedPages = rememberSaveableStateHolder()
                val navigation = remember { AppNavigationViewModel(SavedStateHandle()) }
                val route by navigation.uiState.observeAsState(AppNavigationState())
                TopLevelSwipeHost(
                    route.screen, navigation, Modifier.fillMaxSize().testTag("home-swipe-host"),
                    onSettlingDestinationChange = {}
                ) { page, isActualActive ->
                    savedPages.SaveableStateProvider(destinationScrollStateKey(page)) {
                        if (page == FitnessScreen.HOME) {
                            HomeDestination(
                                HomeUiState.Ready(snapshot()), "owner", TODAY, RecordingActions(),
                                entranceState = entrance,
                                entranceToken = route.topLevelEntrance
                                    ?.takeIf { it.destination == page }?.generation,
                                isActualActive = isActualActive
                            )
                        } else Box { androidx.compose.material3.Text("피트니스") }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val enteringTop = compose.onNodeWithText("오늘 상태").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(1_000)
        val settledTop = compose.onNodeWithText("오늘 상태").fetchSemanticsNode().boundsInRoot.top
        val settledMealTop = compose.onNodeWithTag("home-quick-meal").fetchSemanticsNode().boundsInRoot.top
        assertTrue(enteringTop > settledTop + 1f)
        compose.onNodeWithTag("home-swipe-host").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("오늘 상태").assertDoesNotExist()
        compose.onNodeWithText("피트니스").assertExists()
        compose.onNodeWithTag("home-swipe-host").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeByFrame()
        val previewTop = compose.onNodeWithTag("home-quick-meal").fetchSemanticsNode().boundsInRoot.top
        assertEquals(settledMealTop, previewTop, 1f)
        compose.mainClock.advanceTimeBy(1_000)
        val currentTop = compose.onNodeWithText("오늘 상태").fetchSemanticsNode().boundsInRoot.top
        assertEquals(settledTop, currentTop, 1f)
    }

    private fun showHome(snapshot: HomeSnapshot, actions: RecordingActions) {
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HomeDestination(
                        homeState = HomeUiState.Ready(snapshot),
                        ownerId = "owner",
                        today = TODAY,
                        actions = actions
                    )
                }
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
