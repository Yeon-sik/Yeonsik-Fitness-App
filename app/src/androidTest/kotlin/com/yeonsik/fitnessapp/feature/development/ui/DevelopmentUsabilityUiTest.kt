package com.yeonsik.fitnessapp.feature.development.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
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
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.development.DevelopmentInsight
import com.yeonsik.fitnessapp.development.DevelopmentReport
import com.yeonsik.fitnessapp.feature.development.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** Isolated screen fixtures: callbacks are captured; no workouts, accounts or records are changed. */
class DevelopmentUsabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf<DevelopmentUiState>(ready())
    private val owner = mutableStateOf("owner-a")
    private val starting = mutableStateOf(false)
    private val scale = mutableStateOf(1f)
    private val dark = mutableStateOf(true)
    private val calls = mutableListOf<String>()
    private var startedProposal: WorkoutSetCountProposal? = null
    private val actions = object : DevelopmentScreenActions {
        override fun showBodyProfile() { calls += "profile" }
        override fun showGoal() { calls += "goal" }
        override fun showNutritionGoal() { calls += "nutrition" }
        override fun showRecoveryCheckIn() { calls += "recovery" }
        override fun openInsightAction(insight: DevelopmentInsight) = Unit
        override fun refresh() { calls += "refresh" }
        override fun openExerciseRecord(recordId: String, exerciseOccurrenceId: String) {
            calls += "record:$recordId:$exerciseOccurrenceId"
        }
        override fun openRoutine(routineId: String?) { calls += "routine:$routineId" }
        override fun followExercises(keys: List<String>) { calls += "follow:${keys.joinToString()}" }
        override fun startSuggestedWorkout(proposal: WorkoutSetCountProposal) {
            startedProposal = proposal
            calls += "start"
        }
        override fun openMeals() { calls += "meals" }
        override fun requestPaperAdvice() { calls += "paper" }
    }

    @Test fun maintainedSetsHaveOneConclusionAndRealStartProposalIsPreserved() {
        state.value = ready(withProposal = true)
        show()
        compose.onNodeWithText("3세트 유지 제안").assertIsDisplayed()
        compose.onNodeWithText("지난 운동").assertDoesNotExist()
        compose.onNodeWithTag("development-start").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(PROPOSAL, startedProposal)
        compose.runOnIdle { starting.value = true }
        compose.onNodeWithTag("development-start").assertIsNotEnabled()
        compose.onNodeWithText("운동 준비 중…").assertExists()
    }

    @Test fun unavailableAutoStartOffersExistingRoutineAndKeepsUncertaintyVisible() {
        show()
        compose.onNodeWithText("세트 자동 적용 없이 루틴에서 준비해 주세요.").assertExists()
        compose.onNodeWithTag("development-start").assertDoesNotExist()
        compose.onNodeWithTag("development-prepare").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf("routine:routine-a"), calls)
    }

    @Test fun changedAndUnavailableSuggestionsKeepTheirDifferentMeaningsAtLargeText() {
        val initial = ready(withProposal = true)
        val guidance = requireNotNull(initial.guidance)
        state.value = initial.copy(guidance = guidance.copy(training = listOf(guidance.training.single().copy(
            adjustment = DevelopmentSetAdjustment.CONSIDER_ADD, suggestedSets = 4,
            proposal = PROPOSAL.copy(normalSetCount = 4)
        ))))
        scale.value = 2f
        show()
        compose.onNodeWithText("지난 운동 3세트").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다음 운동 제안 4세트").assertExists()
        compose.onNodeWithTag("development-start").performScrollTo().performClick()
        assertEquals(4, startedProposal?.normalSetCount)
        compose.runOnIdle {
            state.value = initial.copy(guidance = guidance.copy(training = listOf(guidance.training.single().copy(
                adjustment = DevelopmentSetAdjustment.REVIEW, suggestedSets = null, proposal = null
            ))))
        }
        compose.onNodeWithText("최근 세트 구성 확인").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("development-start").assertDoesNotExist()
    }

    @Test fun recoveryFollowsWorkoutAndKeepsExistingEditorCallback() {
        show()
        compose.onNodeWithTag("development-recovery").performScrollTo().performClick()
        assertEquals(listOf("recovery"), calls)
        val recovery = compose.onNodeWithTag("development-recovery").fetchSemanticsNode().boundsInRoot
        val meal = compose.onNodeWithText("식사 보완", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(recovery.top < meal.top)
    }

    @Test fun evidenceSheetClosesFromTopAndRecordIdentityIsPreserved() {
        show()
        compose.onNodeWithTag("development-suggestion-detail").performScrollTo().performClick()
        compose.onNodeWithTag("development-detail-close").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("최근 · 10/20 · 기록 열기").performScrollTo().performClick()
        assertEquals(listOf("record:record-a:occurrence-a"), calls)
        compose.onNodeWithTag("development-detail").assertDoesNotExist()
        compose.onNodeWithTag("development-suggestion-detail").performScrollTo().performClick()
        compose.onNodeWithTag("development-detail-close").performClick()
        compose.onNodeWithTag("development-detail").assertDoesNotExist()
    }

    @Test fun ownerChangeDoesNotLeavePriorEvidenceOpen() {
        show()
        compose.onNodeWithTag("development-suggestion-detail").performScrollTo().performClick()
        compose.runOnIdle { owner.value = "owner-b"; state.value = ready().copy(ownerId = "owner-b") }
        compose.onNodeWithTag("development-detail").assertDoesNotExist()
    }

    @Test fun settingsAndExerciseSelectionRetainCallbacksAndCancelDoesNotSave() {
        show()
        compose.onNodeWithTag("development-settings").performClick()
        compose.onNodeWithText("영양·단백질 기준").performClick()
        assertEquals(listOf("nutrition"), calls)
        compose.onNodeWithTag("development-picker").performScrollTo().performClick()
        compose.onNodeWithText("취소").performClick()
        assertEquals(listOf("nutrition"), calls)
        compose.onNodeWithTag("development-picker").performClick()
        compose.onNodeWithText("선택 저장").performClick()
        assertEquals("follow:leg-curl", calls.last())
    }

    @Test fun referenceDisclosurePreservesFactsAndResearchAction() {
        show()
        compose.onNodeWithTag("development-context").performScrollTo().performClick()
        compose.onNodeWithText("최근 체중").assertExists()
        compose.onNodeWithText("연구 근거로 추가 점검").performScrollTo().performClick()
        assertEquals(listOf("paper"), calls)
        compose.onNodeWithTag("development-context").performScrollTo().performClick()
        compose.onNodeWithText("최근 체중").assertDoesNotExist()
    }

    @Test fun emptyAndErrorStatesKeepRecoveryActions() {
        state.value = ready().copy(training = DevelopmentTrainingOverview.empty(DATE), guidance = DevelopmentGuidance(DATE))
        show()
        compose.onNodeWithText("운동 선택하기").performScrollTo().performClick()
        assertEquals(listOf("routine:null"), calls)
        compose.runOnIdle { state.value = DevelopmentUiState.Error("owner-a", "불러오기 실패") }
        compose.onNodeWithText("다시 불러오기").performScrollTo().performClick()
        assertEquals("refresh", calls.last())
    }

    @Test fun smallWidthLargeTextAndBothThemesKeepPrimaryActionsAndSheetReachable() {
        show()
        for ((font, isDark) in listOf(1f to true, 1.3f to false, 2f to true, 2f to false)) {
            compose.runOnIdle { scale.value = font; dark.value = isDark }
            compose.onNodeWithTag("development-suggestion-detail").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
            compose.onNodeWithTag("development-detail-close").assertIsDisplayed()
            compose.onNodeWithText("최근 · 10/20 · 기록 열기").performScrollTo().assertIsDisplayed()
            capture("development-$font-$isDark.png")
            compose.onNodeWithTag("development-detail-close").performClick()
            compose.onNodeWithTag("development-picker").performScrollTo().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithTag("development-context").performScrollTo().assertIsDisplayed()
        }
    }

    private fun show() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                FitnessComposeTheme(dark.value) {
                    Column(Modifier.width(360.dp).verticalScroll(rememberScrollState()).padding(20.dp)) {
                        DevelopmentScreen(state.value, PaperAdviceUiState.Idle, owner.value, MassUnit.KG,
                            actions, startingWorkout = starting.value)
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(400)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(instrumentation.targetContext.cacheDir, name)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    companion object {
        private val DATE = LocalDate.of(2026, 10, 21)
        private val PROPOSAL = WorkoutSetCountProposal("record-a", "occurrence-a", "2026-10-20",
            DATE.toString(), "routine-a", "leg-curl", "signature", 3, "local-sets-v1")

        private fun ready(withProposal: Boolean = false): DevelopmentUiState.Ready {
            val session = WorkoutProgressSession("record-a", "occurrence-a", "2026-10-20", "",
                "routine-a", "라이잉 레그 컬 · 긴 운동 이름과 세트 구성 확인", "legs", "weight_reps",
                "leg-curl", LoadState.EXTERNAL_LOAD, null,
                (1..3).map { WorkoutProgressSet("set-$it", it, WorkoutSetType.NORMAL, null,
                    30.0, 10, 0, null, null) })
            val sample = DevelopmentPerformanceSample(session)
            val previous = sample.copy(session = session.copy(recordId = "previous", date = "2026-10-18"))
            val comparison = DevelopmentExerciseComparison(DevelopmentComparisonState.COMPARABLE,
                previous, sample, DevelopmentComparisonMetric.REPETITIONS, 27.0, 30.0, 30.0)
            val progress = DevelopmentExerciseProgress("leg-curl", session.exerciseName,
                listOf(previous, sample), comparison, listOf(27.0, 30.0))
            val report = DevelopmentReport(DATE, DATE.minusDays(2), DATE.minusDays(13), DATE,
                null, null, 75.0, 2, 2, emptyMap(), 0, null, 7, 0, 0, null, emptyList())
            val advice = DevelopmentAdvice("nutrition-review", "기록된 식사에서 보완점을 확인해요",
                "빠진 식사가 있다면 평균은 실제 섭취량과 다를 수 있어요.", listOf("기록된 7일 기준"),
                DevelopmentAdviceAction.MEALS, "식사 기록 보기")
            return DevelopmentUiState.Ready("owner-a", report,
                DevelopmentTrainingOverview(DATE.minusDays(55), DATE, listOf(progress)),
                listOf(progress.key), guidance = DevelopmentGuidance(DATE, listOf(
                    DevelopmentSetSuggestion(progress.key, progress.name, DevelopmentSetAdjustment.MAINTAIN,
                        3, 3, "최근 구성으로 시작해요. 비교 기록이 부족하므로 추가 조정은 보류해요.",
                        listOf("일반 세트 3개", "확인되지 않은 회복 상태는 추정하지 않아요."),
                        if (withProposal) PROPOSAL else null)
                ), listOf(advice)))
        }
    }
}
