package com.yeonsik.fitnessapp.feature.meal.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.FoodPortionDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class MealEntryDialogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sixthMealSupportsQuantityEditingAndLiveTotals() {
        var state by mutableStateOf(foodState())
        var saved = false
        val actions = actions { name, args ->
            when (name) {
                "updateFoodQuantity" -> state = state.copy(foodPortions = state.foodPortions.map {
                    if (it.food.id == args!![0]) it.copy(quantity = args[1] as String) else it
                })
                "removeFood" -> state = state.copy(foodPortions = emptyList())
                "saveFood" -> saved = true
            }
        }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions, state, PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithText("6끼 기록").assertIsDisplayed()
        compose.onNodeWithText("6끼 기록하기").assertIsDisplayed()
        compose.onNodeWithText("아침").assertDoesNotExist()
        compose.onNodeWithText("점심").assertDoesNotExist()
        compose.onNodeWithText("저녁").assertDoesNotExist()
        savePreview("meal-entry-light.png", "6끼 기록")
        compose.onNodeWithContentDescription("밥 섭취량 늘리기").performScrollTo().performClick()
        compose.onNodeWithText("175").assertExists()
        compose.onNodeWithText("262.5 kcal").assertExists()
        compose.onNodeWithText("섭취량").performClick().performTextReplacement("200")
        compose.onNodeWithText("300 kcal").assertExists()
        compose.onNodeWithText("6끼 기록하기").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(saved) }
        compose.onNodeWithContentDescription("밥 구성에서 삭제").performScrollTo().performClick()
        compose.onNodeWithText("6끼 기록하기").assertIsNotEnabled()
    }

    @Test fun darkAppearanceAndLargeTextKeepTheSaveActionVisible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FitnessComposeTheme(true) { MealEntryDialog(actions { _, _ -> }, foodState(), PriceTraceUiState.Idle, "12끼") }
            }
        }
        compose.onNodeWithText("12끼 기록").assertIsDisplayed()
        compose.onNodeWithText("12끼 기록하기").assertIsDisplayed()
        compose.onNodeWithText("225 kcal").assertIsDisplayed()
        savePreview("meal-entry-dark-large-text.png", "12끼 기록")
    }

    @Test fun diningPortionUpdatesThePersistentNutritionSummary() {
        var state by mutableStateOf(MealUiState.Ready("fitness-owner", "2026-10-03", true, true, "", emptyList(),
            DiningOutDraft(store = "식당", menu = "비빔밥", calories = "600", protein = "40", carbs = "80", fat = "20")))
        val actions = actions { name, args -> if (name == "updateDiningPortion") state = state.copy(diningPortion = args!![0] as String) }
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions, state, PriceTraceUiState.Idle, "7끼") } }
        compose.onNodeWithText("반 인분").performScrollTo().performClick()
        compose.onNodeWithText("300 kcal").assertIsDisplayed()
        compose.onNodeWithText("20 g").assertIsDisplayed()
        compose.onNodeWithText("7끼 기록하기").assertIsDisplayed()
    }

    private fun foodState(): MealUiState.Ready {
        val rice = NutritionFood.builder().id("rice").ownerId("nutrition-owner").name("밥")
            .kind(NutritionFood.KIND_INGREDIENT).basis(100.0, NutritionUnit.GRAM)
            .profile(NutritionProfile.ofMacros(150.0, 3.0, 33.0, 1.0)).build()
        return MealUiState.Ready("fitness-owner", "2026-10-03", true, false, "", emptyList(), DiningOutDraft(time = "12:30"),
            foodPortions = listOf(FoodPortionDraft(rice, "150")))
    }

    private fun actions(onAction: (String, Array<out Any?>?) -> Unit): MealScreenActions = Proxy.newProxyInstance(
        MealScreenActions::class.java.classLoader, arrayOf(MealScreenActions::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "MealEntryTestActions"
            else -> { onAction(method.name, args); null }
        }
    } as MealScreenActions

    private fun savePreview(name: String, title: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("codex-meal-review")!!
        folder.mkdirs()
        val bitmap = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)).captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
