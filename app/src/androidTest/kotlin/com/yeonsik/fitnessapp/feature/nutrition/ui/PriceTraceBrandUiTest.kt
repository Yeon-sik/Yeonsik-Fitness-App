package com.yeonsik.fitnessapp.feature.nutrition.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class PriceTraceBrandUiTest {
    @get:Rule val compose = createComposeRule()
    private val selected = PriceTraceBrandSelection("11111111-1111-4111-8111-111111111111", "라벨과 구별되는 구체 브랜드",
        "22222222-2222-4222-8222-222222222222", null)

    @Test fun registrationLibraryAndPickerExposeExplicitActionsAndReadOnlySnapshots() {
        var state by mutableStateOf(NutritionEditorState(open = true, ownerId = "owner", mode = NutritionEditorMode.FOOD,
            fields = mapOf(FoodEntryField.NAME to "제품", FoodEntryField.BRAND to "라벨 브랜드"), priceTraceBrand = selected))
        val calls = mutableListOf<String>()
        val actions = actions { name -> calls.add(name) }
        compose.setContent { FitnessComposeTheme(false) { NutritionEditorDialog(state, actions, {}) } }
        compose.onNodeWithText("라벨 브랜드").assertExists()
        compose.onNodeWithText("PriceTrace · ${selected.canonicalName}").assertExists()
        compose.onNodeWithText("상위 브랜드 이름 미확인").assertExists()
        compose.onNodeWithText("연결 브랜드 변경").performScrollTo().performClick()
        assertTrue("openBrandSearch" in calls)
        capture("제품 · 재료 등록", "brand-registration")
        compose.runOnIdle { state = state.copy(registrationKind = NutritionRegistrationKind.INGREDIENT) }
        compose.onNodeWithText("PriceTrace 브랜드 연결").assertDoesNotExist()
        val privateFood = NutritionFood.builder().id("private").ownerId("owner").name("내 제품").priceTraceBrand(selected).build()
        val publicFood = NutritionFood.builder().id("public").name("공개 제품").priceTraceBrand(selected).build()
        compose.runOnIdle { state = state.copy(mode = NutritionEditorMode.LIBRARY, packagedFoods = listOf(privateFood, publicFood),
            brandEditableFoodIds = setOf("private")) }
        compose.onAllNodesWithText("PriceTrace 브랜드 변경 · 해제").assertCountEquals(1)
        compose.onNodeWithText("PriceTrace 브랜드 변경 · 해제").performScrollTo().performClick()
        assertTrue("editFoodBrand" in calls)
        compose.onNodeWithText("공개 제품").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("PriceTrace · ${selected.canonicalName}").onLast().performScrollTo().assertIsDisplayed()
        capture("내 식단 · 식품", "brand-existing-readonly")
        val row = PriceTraceBrandRow(selected, "검색한 별칭", "alias", null, null)
        compose.runOnIdle { state = state.copy(editingBrandFood = privateFood,
            brandSearch = PriceTraceBrandSearchState("검색한 별칭", listOf(row), searched = true)) }
        compose.onNodeWithText("별칭 · 검색한 별칭").assertExists()
        compose.onNodeWithText("${selected.canonicalName} 선택").performScrollTo().performClick()
        assertTrue("selectBrand" in calls)
        capture("브랜드 연결", "brand-picker-alias-parent")
    }

    @Test fun offlineFailureAndEmptyResultsKeepSnapshotAndOfferRetryAndExplicitClear() {
        var state by mutableStateOf(NutritionEditorState(open = true, mode = NutritionEditorMode.FOOD, priceTraceBrand = selected,
            brandSearch = PriceTraceBrandSearchState("검색", searched = true, error = "브랜드를 불러오지 못했습니다.")))
        val calls = mutableListOf<String>()
        compose.setContent { FitnessComposeTheme(false) { NutritionEditorDialog(state, actions { calls.add(it) }, {}) } }
        compose.onNodeWithText("PriceTrace · ${selected.canonicalName}").assertExists()
        compose.onNodeWithText("다시 검색").performClick()
        assertEquals(listOf("searchBrands"), calls)
        compose.runOnIdle { state = state.copy(brandSearch = PriceTraceBrandSearchState("검색", searched = true)) }
        compose.onNodeWithText("현재 PriceTrace 공개 카탈로그에서 확인되지 않음 · 저장된 연결은 유지됩니다.").assertExists()
        compose.onNodeWithText("브랜드 연결 해제").performClick()
        assertEquals("clearBrand", calls.last())
        assertEquals(selected, state.priceTraceBrand)
    }

    private fun actions(call: (String) -> Unit) = Proxy.newProxyInstance(NutritionEditorActions::class.java.classLoader,
        arrayOf(NutritionEditorActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "BrandFixtureActions"
            else -> { call(method.name); null }
        }
    } as NutritionEditorActions
    private fun capture(title: String, name: String) {
        val image = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
