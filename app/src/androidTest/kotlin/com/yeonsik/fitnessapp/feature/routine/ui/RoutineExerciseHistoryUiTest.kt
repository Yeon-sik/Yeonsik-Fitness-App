package com.yeonsik.fitnessapp.feature.routine.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.routine.model.RoutineSummary
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class RoutineExerciseHistoryUiTest {
    @get:Rule val compose = createComposeRule()
    private val exercise = RoutineExerciseInstance("routine-item", "exercise-id", "기록 테스트 운동",
        "chest", "가슴", "barbell", "weight_reps", 1, null)

    @Test fun tappingTheRoutineExerciseOpensItsReadOnlyHistoryAndBackClosesIt() {
        val history = mutableStateOf<RoutineExerciseHistoryUiState?>(null)
        var clicked: RoutineExerciseInstance? = null
        val actions = Proxy.newProxyInstance(RoutineDetailActions::class.java.classLoader,
            arrayOf(RoutineDetailActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Routine history callbacks"
                "openExerciseHistory" -> {
                    clicked = args!![0] as RoutineExerciseInstance
                    history.value = RoutineExerciseHistoryUiState("owner", clicked!!, loading = false)
                    null
                }
                "closeExerciseHistory" -> { history.value = null; null }
                else -> null
            }
        } as RoutineDetailActions
        val home = HomeSnapshot("owner", "2026-10-09", emptyList(), "routine",
            listOf(RoutineSummary("routine", "테스트 루틴", 1)), mapOf("routine" to listOf(exercise)),
            emptyMap(), null, emptyMap(), emptyMap(), emptyMap(), null, null, emptyList(), emptyList())
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RoutineDetailScreen(HomeUiState.Ready(home), "owner", "routine", actions, history.value)
                }
            }
        }
        compose.onNodeWithTag("routine-exercise-routine-item").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(exercise, clicked) }
        compose.onNodeWithTag("routine-exercise-history").assertIsDisplayed()
        compose.onNodeWithText("이 종목의 완료된 운동 기록이 없습니다.").assertIsDisplayed()
        compose.onNodeWithText("세트 추가").assertDoesNotExist()
        compose.onNode(hasText("‹ 뒤로") and hasAnyAncestor(hasTestTag("routine-exercise-history"))).performClick()
        compose.onNodeWithTag("routine-exercise-history").assertDoesNotExist()
        compose.onNodeWithTag("routine-exercise-routine-item").assertExists()
    }

    @Test fun historyFailureShowsRetryAndKeepsTheExerciseIdentity() {
        var retried = false
        compose.setContent {
            FitnessComposeTheme(false) {
                RoutineExerciseHistoryDialog(RoutineExerciseHistoryUiState("owner", exercise,
                    loading = false, error = "기록 조회 오류"), MassUnit.KG, {}, { retried = true })
            }
        }
        compose.onNodeWithText(exercise.nameKo).assertIsDisplayed()
        compose.onNodeWithText("다시 불러오기").performClick()
        compose.runOnIdle { assertEquals(true, retried) }
    }
}
