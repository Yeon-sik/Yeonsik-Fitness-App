package com.yeonsik.fitnessapp.feature.dailyworkout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yeonsik.fitness.shared.feature.dailyworkout.*
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test

class DailyWorkoutSuggestionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        compose.onNode(hasContentDescription("시간대별 심박 측정점") and inCard).assertExists()
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
}
