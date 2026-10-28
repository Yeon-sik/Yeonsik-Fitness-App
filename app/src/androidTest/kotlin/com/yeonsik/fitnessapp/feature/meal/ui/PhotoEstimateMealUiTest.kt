package com.yeonsik.fitnessapp.feature.meal.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.home.model.*
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class PhotoEstimateMealUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun caloriesOnlyCanSaveWithoutAMenuAndUsesAnAutomaticDateTitle() {
        var draft by mutableStateOf(PhotoEstimateDraft(time = "12:30"))
        var saved: PhotoEstimateDraft? = null
        compose.setContent { FitnessComposeTheme(false) {
            PhotoEstimateMealEditor("2026-10-21", draft, false, null, { draft = it }, { saved = draft }, {})
        } }
        compose.onNodeWithText("20261021").assertExists()
        compose.onNodeWithText("먹은 메뉴 (필수)").assertDoesNotExist()
        compose.onNodeWithTag("photo-estimate-save").assertIsNotEnabled()
        compose.onNodeWithTag("photo-estimate-calories").performTextInput("2400")
        compose.onNodeWithTag("photo-estimate-save").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(2400.0, saved!!.input().calories, 0.0)
            assertNull(saved!!.input().proteinGrams)
        }
        savePreview("photo-estimate-editor.png", "photo-estimate-editor")
    }

    @Test fun dailyTotalAndMealListIdentifyPhotoEstimateWithoutShowingZeroProtein() {
        val date = "2026-10-21"
        val meal = HomeMealSummary("photo", date, "첫 번째 식사", "20261021", 2400, 0.0, 0.0, 0.0,
            0, "20261021", "12:30", "food", null, "", "", "", "estimated", "", "", true, null,
            "12:30 · 사진 기반 추정", "20261021, 사진 기반 추정", isPhotoEstimate = true, recordedProteinGrams = null)
        val totals = HomeNutritionTotals(1, mapOf("calories_kcal" to HomeNutritionTotal(2400.0, 1, 0)))
        val home = HomeUiState.Ready(HomeSnapshot("owner", date, emptyList(), null, emptyList(), emptyMap(),
            emptyMap(), null, emptyMap(), emptyMap(), mapOf(date to totals), null, null, emptyList(), listOf(meal)))
        val actions = Proxy.newProxyInstance(MealScreenActions::class.java.classLoader,
            arrayOf(MealScreenActions::class.java)) { proxy, method, args -> when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PhotoEstimateActions"
                else -> null
            } } as MealScreenActions
        compose.setContent { FitnessComposeTheme(false) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                MealScreen(home, MealUiState.Ready("owner", date, false, false, "", emptyList(), DiningOutDraft()),
                    PriceTraceUiState.Idle, NutritionPublicationUiState(), "owner", date, actions)
            }
        } }
        compose.onNodeWithTag("meal-photo-estimate-total").assertTextEquals("사진 기반 추정 기록 1건 포함")
        compose.onNodeWithText("약 2400 kcal · 단백질 미입력").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12:30 · 사진 기반 추정").assertExists()
        savePreview("photo-estimate-list.png", "meal-record-photo")
    }

    @Test fun detailExplainsEntireMealAndUnknownMacrosAtLargeText() {
        val empty = MealSnapshotRead("photo", "2026-10-21", "food",
            """{"recording_method":"photo_estimate","nutrition_status":"estimated"}""", emptyList())
        val snapshot = empty.copy(nutrition = empty.nutrition.copy(calories = 2400.0))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                FitnessComposeTheme(true) { MealSnapshotDetailDialog(MealDetailState.Ready("photo", snapshot), {}) }
            }
        }
        compose.onNodeWithTag("meal-detail-photo-estimate").assertExists()
        compose.onNodeWithText("식사 전체 · 약 2400 kcal").assertExists()
        compose.onNodeWithText("단백질 · 미입력").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("meal-detail-missing").assertDoesNotExist()
        savePreview("photo-estimate-detail.png", "meal-detail-photo")
    }

    private fun savePreview(name: String, tag: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("photo-estimate-tests")!!
        folder.mkdirs()
        File(folder, name).outputStream().use { stream ->
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
