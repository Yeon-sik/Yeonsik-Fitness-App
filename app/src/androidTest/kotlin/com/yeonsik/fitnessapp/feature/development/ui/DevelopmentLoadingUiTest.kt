package com.yeonsik.fitnessapp.feature.development.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.ImmediateLoadingTheme
import com.yeonsik.fitnessapp.development.DevelopmentInsight
import com.yeonsik.fitnessapp.development.PaperAdviceAssessment
import com.yeonsik.fitnessapp.development.PaperAdviceInput
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DevelopmentLoadingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reportLoadingUsesOrb() {
        compose.setContent {
            ImmediateLoadingTheme {
                DevelopmentScreen(
                    DevelopmentUiState.Loading, PaperAdviceUiState.Loading("owner-a"),
                    "owner-a", MassUnit.KG, actions
                )
            }
        }
        compose.onNodeWithContentDescription("최근 기록을 정리하고 있어요").assertExists()
        compose.onNodeWithTag("thinking-orb").assertExists()
    }

    @Test
    fun reportErrorStopsLoadingOrb() {
        compose.setContent {
            ImmediateLoadingTheme {
                DevelopmentScreen(
                    DevelopmentUiState.Error("owner-a", "보고서 오류"), PaperAdviceUiState.Idle,
                    "owner-a", MassUnit.KG, actions
                )
            }
        }
        compose.onNodeWithText("보고서 오류").assertExists()
        compose.onNodeWithTag("thinking-orb").assertDoesNotExist()
    }

    @Test
    fun paperAdviceLoadingIsLocalized() {
        compose.setContent {
            ImmediateLoadingTheme {
                PaperAdviceSection(PaperAdviceUiState.Loading("owner-a"), "owner-a")
            }
        }
        compose.onNodeWithContentDescription("분석 결과를 정리하고 있어요").assertExists()
        compose.onNodeWithTag("thinking-orb").assertExists()
    }

    @Test
    fun paperAdviceErrorUsesExistingCard() {
        compose.setContent {
            ImmediateLoadingTheme {
                PaperAdviceSection(
                    PaperAdviceUiState.Error("owner-a", "논문 오류"), "owner-a"
                )
            }
        }
        compose.onNodeWithText("논문 오류").assertExists()
        compose.onNodeWithTag("thinking-orb").assertDoesNotExist()
    }

    @Test
    fun paperAdviceReadyShowsExistingAssessment() {
        val input = PaperAdviceInput.builder()
            .referenceDate(LocalDate.of(2026, 9, 29))
            .build()
        compose.setContent {
            ImmediateLoadingTheme {
                PaperAdviceSection(
                    PaperAdviceUiState.Ready(
                        "owner-a", PaperAdviceAssessment(input, emptyList())
                    ), "owner-a"
                )
            }
        }
        compose.onNodeWithText("Readiness · 검토 후보").assertExists()
        compose.onNodeWithTag("thinking-orb").assertDoesNotExist()
    }

    private val actions = object : DevelopmentScreenActions {
        override fun showBodyProfile() = Unit
        override fun showGoal() = Unit
        override fun showNutritionGoal() = Unit
        override fun showRecoveryCheckIn() = Unit
        override fun openInsightAction(insight: DevelopmentInsight) = Unit
    }
}
