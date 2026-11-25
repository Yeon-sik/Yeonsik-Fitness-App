package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.FitnessTextTabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutDetailLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun setsFillDownThenAcrossAndOverflowOpensTheFullRecord() {
        val count = mutableIntStateOf(7)
        compose.setContent {
            val sets = (1..count.intValue).reversed().map {
                WorkoutSet("set-$it", it, if (it == 2) 102.5 else 20.0, it, null, null, true, 0, 0.0, 0.0, 0.0, null, null, null)
            }
            FitnessComposeTheme(false) {
                WorkoutHistoryCards(listOf(WorkoutExerciseHistory("2001-05-15", 200.0, sets, recordId = "fixture")),
                    "테스트 로우", "weight_reps", MassUnit.KG)
            }
        }
        fun row(index: Int) = compose.onNodeWithContentDescription("${index}세트, 부하", substring = true)
        fun bounds(index: Int) = row(index).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("102.5").assertIsDisplayed()
        assertEquals(bounds(1).right, bounds(2).right, 1f)
        compose.onNodeWithContentDescription("1세트, 부하", substring = true).assertTextEquals("20.0")
        val first = bounds(1)
        assertEquals(first.left, bounds(7).left, 1f)
        assertTrue(bounds(7).top > first.top)
        row(8).assertDoesNotExist()
        compose.runOnIdle { count.intValue = 8 }
        assertTrue(bounds(8).left > bounds(1).left)
        assertEquals(bounds(1).top, bounds(8).top, 1f)
        val loadHeaders = compose.onAllNodesWithText("중량").fetchSemanticsNodes().map { it.boundsInRoot }
        val repHeaders = compose.onAllNodesWithText("횟수").fetchSemanticsNodes().map { it.boundsInRoot }
        for ((column, setIndex) in listOf(1, 8).withIndex()) {
            val reps = compose.onNodeWithContentDescription("${setIndex}세트, 횟수 ${setIndex}회")
                .fetchSemanticsNode().boundsInRoot
            assertEquals(loadHeaders[column].left, bounds(setIndex).left, 1f)
            assertEquals(loadHeaders[column].right, bounds(setIndex).right, 1f)
            assertEquals(repHeaders[column].left, reps.left, 1f)
            assertEquals(repHeaders[column].right, reps.right, 1f)
            assertEquals(with(compose.density) { 4.dp.toPx() }, reps.left - bounds(setIndex).right, 1f)
        }
        compose.runOnIdle { count.intValue = 14 }
        assertEquals(bounds(7).top, bounds(14).top, 1f)
        compose.onNodeWithText("더보기").assertDoesNotExist()
        compose.runOnIdle { count.intValue = 15 }
        row(15).assertDoesNotExist()
        compose.onNodeWithText("더보기").performClick()
        compose.onNodeWithText("2001-05-15 · 전체 15세트").assertExists()
        compose.onNodeWithTag("all-workout-sets").performScrollToIndex(14)
        row(15).assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        row(15).assertDoesNotExist()
    }

    @Test fun earlierVisitsScrollIntoViewFromTheRight() {
        val exercise = WorkoutExercise("fixture", "row", 0, "테스트 로우", recordType = "weight_reps")
        val histories = (1..10).map {
            val date = "2001-05-" + it.toString().padStart(2, '0')
            val set = WorkoutSet("s$it", 1, 20.0, 8, null, null, true, 0, 0.0, 0.0, 0.0, null, null, null)
            val sets = if (it == 10) (1..8).map { index -> set.copy(id = "s$it-$index", setIndex = index) }
            else listOf(set)
            WorkoutExerciseHistory(date, 160.0, sets, recordId = "record-$it")
        }
        val detail = WorkoutExerciseDetail("record-10", exercise, listOf(exercise), emptyList(), recentHistories = histories)
        compose.setContent {
            FitnessComposeTheme(false) { Column { WorkoutExerciseHistoryContent(detail, MassUnit.KG) } }
        }
        val list = compose.onNodeWithTag("exercise-history-list")
        val newest = compose.onNodeWithText("2001-05-10").fetchSemanticsNode().boundsInRoot
        val previous = compose.onNodeWithText("2001-05-09").fetchSemanticsNode().boundsInRoot
        assertEquals(newest.top, previous.top, 1f)
        assertTrue(previous.left > newest.right)
        val third = compose.onNodeWithText("2001-05-08").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val latestCard = compose.onNodeWithTag("exercise-history-card-0").fetchSemanticsNode().boundsInRoot
        val shortCard = compose.onNodeWithTag("exercise-history-card-1").fetchSemanticsNode().boundsInRoot
        assertEquals(latestCard.width, shortCard.width, 1f)
        assertEquals(latestCard.height, shortCard.height, 1f)
        assertEquals(with(compose.density) { 4.dp.toPx() }, shortCard.left - latestCard.right, 1f)
        val viewport = list.fetchSemanticsNode().boundsInRoot
        assertEquals(previous.left - newest.left, third.left - previous.left, 1f)
        assertTrue(third.right <= viewport.right)
        assertTrue(previous.left - newest.left <= viewport.width / 3 + 1f)
        repeat(10) {
            list.performTouchInput { swipeLeft() }
            compose.waitForIdle()
        }
        compose.onNodeWithText("2001-05-01").assertIsDisplayed()
        repeat(10) {
            list.performTouchInput { swipeRight() }
            compose.waitForIdle()
        }
        compose.onNodeWithText("2001-05-10").assertIsDisplayed()
    }

    @Test fun volumeChartShowsTheTappedWorkoutsNameDateAndVolumeOnlyOnSelection() {
        val points = listOf(WorkoutVolumePoint("2001-05-14", "테스트 로우", 80.0),
            WorkoutVolumePoint("2001-05-15", "테스트 스쿼트", 144.0))
        val session = WorkoutSessionSnapshot("fixture", "", "completed", "", 0, 0.0, 0, emptyList(), points)
        compose.setContent { FitnessComposeTheme(false) { WorkoutSessionVolumeHistory(session, MassUnit.KG) } }
        val plot = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        compose.onNodeWithText("테스트 로우", substring = true).assertDoesNotExist()
        plot.performTouchInput { click(Offset(1f, 1f)) }
        compose.onNodeWithText("테스트 로우 · 2001-05-14").assertIsDisplayed()
        compose.onNodeWithText("80kg").assertIsDisplayed()
        plot.performTouchInput { click(Offset(width - 1f, height - 1f)) }
        compose.onNodeWithText("테스트 스쿼트 · 2001-05-15").assertIsDisplayed()
        compose.onNodeWithText("144kg").assertIsDisplayed()
        plot.performTouchInput { click(Offset(width - 1f, center.y)) }
        compose.onNodeWithText("테스트 스쿼트", substring = true).assertDoesNotExist()
    }

    @Test fun twoAndThreeTextTabsCommitOnlyOnRelease() {
        val count = mutableIntStateOf(2)
        val selection = mutableIntStateOf(0)
        compose.setContent {
            FitnessComposeTheme(false) {
                FitnessTextTabs(List(count.intValue) { "탭 $it" }, selection.intValue, { selection.intValue = it }, "tabs")
            }
        }
        for (tabs in 2..3) {
            compose.runOnIdle { selection.intValue = 0; count.intValue = tabs }
            val bar = compose.onNodeWithTag("tabs")
            bar.performTouchInput {
                down(Offset(width * 0.15f, center.y))
                moveTo(Offset(width * 0.85f, center.y))
            }
            compose.onNodeWithText("탭 0").assertIsSelected()
            bar.performTouchInput { up() }
            compose.onNodeWithText("탭 ${tabs - 1}").assertIsSelected()
        }
    }
}
