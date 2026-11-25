package com.yeonsik.fitnessapp.feature.dailyworkout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.dailyworkout.*
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DailyWorkoutSuggestionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun isPulse(color: Color) =
        color.alpha > 0.5f && color.red - color.green > 0.25f && color.red - color.blue > 0.15f

    @Test fun suggestsOnOpeningAndSharesTheConfirmedWatchBetweenTheDayAndRecordDetail() {
        val day = WorkoutDay(LocalDate.parse("2001-05-15"), TimeZone.currentSystemDefault())
        val start = day.start.toEpochMilliseconds() + 10 * 3_600_000
        val native = DailyWorkoutSource(WorkoutOrigin.FITNESS, "Fitness App", "fixture", "테스트 무산소", DailyWorkoutKind.STRENGTH,
            startMillis = start, endMillis = start + 1_800_000, exercises = listOf(DailyWorkoutExercise("테스트 종목", listOf(DailyWorkoutSet(reps = 8)))))
        val watch = DailyWorkoutSource(WorkoutOrigin.HEALTH_CONNECT, "test.watch", "fixture-watch", "테스트 워치", DailyWorkoutKind.STRENGTH,
            startMillis = start, endMillis = start + 1_800_000)
        val reader = object : DailyWorkoutLocalReader {
            override fun read(day: WorkoutDay) = LocalDailyWorkouts(listOf(native, watch),
                mapOf(watch.key to listOf(DailyHeartRateSample(watch.writer, start, 112.0))))
            override fun findRecord(recordId: String) = LocalWorkoutReference(day.date.toString(), native.origin, native.id)
        }
        val files = mutableMapOf<String, String>()
        val repo = DailyWorkoutRepository(object : DailyWorkoutStorage {
            override fun read(name: String) = files[name]
            override fun write(name: String, text: String) { files[name] = text }
        }, reader)
        val device = object : DailyWorkoutDeviceAccess {
            override suspend fun readWearables(day: WorkoutDay): DailyWearableRecords? = error("Unexpected sync")
            override suspend fun pickFleekCsv(): String? = error("Unexpected picker")
            override suspend fun exportJson(name: String, json: String): Boolean = error("Unexpected export")
        }
        val session = WorkoutSessionSnapshot(native.id, native.title, "completed", "", 1800, 0.0, 1, emptyList(), emptyList())
        val showDay = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (showDay.value) DailyWorkoutScreen(day.date.toString(), repo, device) {}
                else Column { WorkoutRecordWatchSection(repo, "test-owner", session) }
            }
        }
        val list = compose.onNodeWithTag("daily-workout-list")
        fun waitFor(text: String) = compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        waitFor("운동 1회")
        list.performScrollToNode(hasTestTag("daily-workout-entry"))
        val inCard = hasAnyAncestor(hasTestTag("daily-workout-entry"))
        compose.onNode(hasText("1종목 · 1세트") and inCard).assertExists()
        compose.onNode(hasText("심박 1개 측정") and inCard).assertExists()
        compose.onNode(hasContentDescription("측정 순서별 심박 범위", substring = true) and inCard).assertExists()
        compose.onNode(hasText("운동 시간이 30분 겹칩니다.") and inCard).assertExists()

        compose.runOnIdle { showDay.value = false }
        waitFor("연결 제안")
        compose.onNodeWithText("같은 운동으로 연결").performClick()
        waitFor("연결된 워치 기록")
        compose.runOnIdle { showDay.value = true }
        waitFor("운동 1회")
        list.performScrollToNode(hasText("연결된 워치 기록"))
        compose.onNode(hasText("심박 1개 측정") and inCard).assertExists()
        list.performScrollToNode(hasText("연결 취소", substring = true))
        compose.onNodeWithText("연결 취소", substring = true).performClick()
        waitFor("연결 제안")
        compose.onNodeWithText("별개 운동으로 집계").performScrollTo().performClick()
        list.performScrollToIndex(0)
        waitFor("운동 2회")
        compose.onNodeWithText("연결 제안").assertDoesNotExist()
    }

    @Test fun rendersSeparatedRangeBarsAndScrollsWhenFiveSampleGroupsExceedTheWidth() {
        val count = mutableStateOf(15)
        compose.setContent {
            MaterialTheme {
                val samples = List(count.value) { i -> DailyHeartRateSample("test.watch", i * 1000L, (90 + i % 5 * 8).toDouble()) }
                WorkoutHeartRatePlot(samples, null, null, Modifier.width(220.dp))
            }
        }
        val plot = compose.onNodeWithContentDescription("측정 순서별 심박 범위", substring = true)
        fun drawnBars(): List<IntRange> {
            val pixels = plot.captureToImage().toPixelMap()
            val ranges = mutableListOf<IntRange>()
            var start = -1
            val barRow = pixels.height * 2 / 3
            for (x in 0..pixels.width) {
                val painted = x < pixels.width && isPulse(pixels[x, barRow])
                if (painted && start == -1) start = x
                if (!painted && start >= 0) { ranges.add(start until x); start = -1 }
            }
            return ranges
        }
        val sparse = drawnBars()
        assertEquals(3, sparse.size)
        compose.runOnIdle { count.value = 60 }
        val dense = drawnBars()
        assertEquals(12, dense.size)
        assertTrue(dense[1].first - dense[0].last in 2 until (sparse[1].first - sparse[0].last))
        compose.runOnIdle { count.value = 900 }
        compose.onNodeWithText("좌우로 보기", substring = true).assertExists()
        compose.mainClock.autoAdvance = false
        try {
            plot.performTouchInput {
                down(center)
                moveTo(Offset(width - 1f, center.y))
            }
            compose.mainClock.advanceTimeBy(500)
            assertTrue(plot.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f)
            plot.performTouchInput { up() }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun tapsAndScrubsExactExtremesWithoutFillingInAGap() {
        val writer = "test.watch"
        val samples = listOf(93, 81, 108, 89, 94, 132, 157, 143, 119, 131).mapIndexed { index, bpm ->
            DailyHeartRateSample(writer, index * 1_000L + if (index >= 5) 100_000L else 0L, bpm.toDouble())
        }
        compose.setContent { MaterialTheme { WorkoutHeartRatePlot(samples, null, null, Modifier.width(240.dp)) } }
        val plot = compose.onNodeWithContentDescription("측정 순서별 심박 범위", substring = true)
        fun shows(text: String) = plot.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text))
        plot.assertContentDescriptionContains("High는 최고 구간", substring = true)
        val pixels = plot.captureToImage().toPixelMap()
        fun markerIn(columns: IntRange) = columns.any { x ->
            (0 until pixels.height / 5).any { y -> isPulse(pixels[x, y]) }
        }
        assertTrue("Peak label and arrow belong above the rightmost group", markerIn((pixels.width * 2 / 3) until pixels.width))
        assertTrue("A lower group must have no peak mark", !markerIn(0 until pixels.width / 3))
        plot.performTouchInput { click(Offset(width / 6f, center.y)) }
        shows("최저 81 bpm · 최고 108 bpm")
        plot.performTouchInput {
            down(Offset(width / 6f, center.y))
            moveTo(center)
        }
        shows("측정 공백")
        plot.performTouchInput { moveTo(Offset(width * 5f / 6f, center.y)) }
        shows("최저 119 bpm · 최고 157 bpm")
        plot.performTouchInput { up() }
        shows("최저 119 bpm · 최고 157 bpm")
    }
}
