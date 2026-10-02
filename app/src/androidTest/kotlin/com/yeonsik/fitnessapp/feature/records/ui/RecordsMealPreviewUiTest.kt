package com.yeonsik.fitnessapp.feature.records.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.yeonsik.fitness.shared.feature.meal.model.MealReadSummary
import com.yeonsik.fitness.shared.feature.records.model.RecordsDayDetail
import com.yeonsik.fitness.shared.feature.records.model.RecordsSnapshot
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Rule
import org.junit.Test

class RecordsMealPreviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedMealShowsItsEatenTimeCaloriesAndMacrosAndCanClose() {
        val meals = listOf(
            meal("meal-1", label = "1끼", time = "08:10"),
            meal("meal-2", label = "2끼", time = "19:45", calories = 650,
                protein = 33.4, carbs = 40.5, fat = 18.0, nutritionStatus = "estimated")
        )
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RecordsScreen(state("owner-a", DATE, meals), "owner-a", DATE, MassUnit.KG, DATE, actions)
                }
            }
        }

        compose.onNodeWithTag("records-meal-meal-2").performScrollTo().performClick()
        compose.onNodeWithText("2끼 정보").assertIsDisplayed()
        compose.onNodeWithText("19:45").assertIsDisplayed()
        compose.onNodeWithText("650 kcal").assertIsDisplayed()
        compose.onNodeWithText("40.5g").assertIsDisplayed()
        compose.onNodeWithText("33.4g").assertIsDisplayed()
        compose.onNodeWithText("18g").assertIsDisplayed()
        compose.onNodeWithText("추정 영양 정보").assertIsDisplayed()
        compose.onNodeWithText("08:10").assertDoesNotExist()
        compose.onNodeWithTag("records-meal-dialog-close").performClick()
        compose.onNodeWithTag("records-meal-dialog").assertDoesNotExist()
    }

    @Test fun missingMacrosAndTimeAreNotReplacedByZeroOrSaveTime() {
        val meals = listOf(meal("meal-1", time = "시간 미기록", protein = null, carbs = null, fat = 0.0))
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RecordsScreen(state("owner-a", DATE, meals), "owner-a", DATE, MassUnit.KG, DATE, actions)
                }
            }
        }

        compose.onNodeWithTag("records-meal-meal-1").performScrollTo().performClick()
        compose.onNodeWithText("시간 미기록").assertIsDisplayed()
        compose.onAllNodesWithText("미기록").assertCountEquals(2)
        compose.onNodeWithText("0g").assertIsDisplayed()
        compose.onNodeWithText("22:00").assertDoesNotExist()
    }

    @Test fun unknownNutritionDoesNotShowLegacyFallbackZeroes() {
        val meals = listOf(meal("meal-1", calories = 0, protein = 0.0, carbs = 0.0,
            fat = 0.0, nutritionStatus = "unknown"))
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RecordsScreen(state("owner-a", DATE, meals), "owner-a", DATE, MassUnit.KG, DATE, actions)
                }
            }
        }

        compose.onNodeWithTag("records-meal-meal-1").performScrollTo().performClick()
        compose.onAllNodesWithText("미기록").assertCountEquals(4)
        compose.onNodeWithText("0 kcal").assertDoesNotExist()
        compose.onNodeWithText("0g").assertDoesNotExist()
    }

    @Test fun mealSelectionClearsWhenDateOwnerOrActualDestinationChanges() {
        val owner = mutableStateOf("owner-a")
        val date = mutableStateOf(DATE)
        val active = mutableStateOf(true)
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val meals = listOf(meal("meal-1", date = date.value))
                    RecordsScreen(
                        state(owner.value, date.value, meals), owner.value, DATE, MassUnit.KG,
                        date.value, actions, isActualActive = active.value
                    )
                }
            }
        }

        fun openMeal() {
            compose.onNodeWithTag("records-meal-meal-1").performScrollTo().performClick()
            compose.onNodeWithTag("records-meal-dialog").assertExists()
        }
        openMeal()
        compose.runOnIdle { date.value = "2026-10-01" }
        compose.onNodeWithTag("records-meal-dialog").assertDoesNotExist()
        openMeal()
        compose.runOnIdle { owner.value = "owner-b" }
        compose.onNodeWithTag("records-meal-dialog").assertDoesNotExist()
        openMeal()
        compose.runOnIdle { active.value = false }
        compose.onNodeWithTag("records-meal-dialog").assertDoesNotExist()
        compose.runOnIdle { active.value = true }
        compose.onNodeWithTag("records-meal-dialog").assertDoesNotExist()
    }

    private fun state(owner: String, date: String, meals: List<MealReadSummary>) =
        RecordsUiState.Ready(RecordsSnapshot(
            ownerId = owner,
            displayedMonth = date.take(7),
            today = DATE,
            calendarDays = emptyList(),
            dayDetailsByDate = mapOf(date to RecordsDayDetail(date, emptyList(), emptyList(), meals)),
            weightTrend = emptyList()
        ))

    private fun meal(
        id: String,
        date: String = DATE,
        label: String = "1끼",
        time: String = "12:30",
        calories: Int = 410,
        protein: Double? = 25.0,
        carbs: Double? = 45.0,
        fat: Double? = 12.0,
        nutritionStatus: String = "recorded"
    ) = MealReadSummary(
        id = id,
        date = date,
        mealLabel = label,
        menu = "현미밥",
        calories = calories,
        proteinGrams = protein ?: 0.0,
        carbsGrams = carbs ?: 0.0,
        fatGrams = fat ?: 0.0,
        compositionCount = 1,
        previewTitle = "현미밥",
        mealTime = time,
        mealKind = "food",
        fulfillmentMode = null,
        storeName = "",
        branchName = "",
        menuName = "",
        nutritionStatus = nutritionStatus,
        macroRatio = "",
        macroRatioAccessibility = "",
        timeEditable = true,
        createdAt = "${date}T22:00:00+09:00",
        subtitle = time,
        accessibilityLabel = "현미밥",
        recordedProteinGrams = protein,
        recordedCarbsGrams = carbs,
        recordedFatGrams = fat
    )

    private val actions = object : RecordsScreenActions {
        override fun selectDate(date: String) = Unit
        override fun previousMonth() = Unit
        override fun nextMonth() = Unit
        override fun today() = Unit
        override fun openRecord(recordId: String) = Unit
        override fun deleteRecord(recordId: String) = Unit
        override fun showBodyMetric(date: String, recordId: String?) = Unit
    }

    private companion object { const val DATE = "2026-10-02" }
}
