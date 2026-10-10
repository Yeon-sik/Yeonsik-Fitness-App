package com.yeonsik.fitnessapp.feature.meal.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.FoodPortionDraft
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class MealCatalogPickerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allFoodResultsStayInABoundedListAndTheHundredthCanBeAdded() {
        var state by mutableStateOf(foodState())
        var addedId: String? = null
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "selectFood") {
                val food = args!![0] as NutritionFood
                addedId = food.id
                state = state.copy(foodPortions = state.foodPortions + FoodPortionDraft(food, "100"))
            }
        }, state, PriceTraceUiState.Idle, "6끼") } }
        val list = compose.onNodeWithTag("meal-food-results").performScrollTo()
        assertBounded("meal-food-results", 288f)
        compose.onNodeWithText("식품 100개 · 목록 안에서 스크롤").assertExists()
        list.performScrollToIndex(99)
        compose.onNodeWithTag("food-result-food-99").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("food-99", addedId) }
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("식품 2개").assertIsDisplayed()
        list.performScrollTo().performScrollToIndex(0)
        savePreview("meal-food-scroll-list.png", "6끼 기록")
    }

    @Test fun aLongSelectedCompositionCanBeEditedInsideItsOwnScrollBox() {
        var state by mutableStateOf(foodState().copy(foodPortions = foods(40).map { FoodPortionDraft(it, "100") }))
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "updateFoodQuantity") state = state.copy(foodPortions = state.foodPortions.map {
                if (it.food.id == args!![0]) it.copy(quantity = args[1] as String) else it
            })
        }, state, PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithTag("meal-food-portions").performScrollTo().performScrollToIndex(39)
        assertBounded("meal-food-portions", 360f)
        compose.onNodeWithContentDescription("식품 39 전체 양 늘리기").performClick()
        compose.runOnIdle { assertEquals("125", state.foodPortions.last().quantity) }
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed()
    }

    @Test fun loadingAndReadFailureHaveDistinctMessagesAndRetryTheCurrentQuery() {
        var state by mutableStateOf(foodState().copy(query = "밥", searchResults = emptyList(), searchLoading = true))
        var retriedQuery: String? = null
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "searchFood") retriedQuery = args!![0] as String
        }, state, PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithText("식품 목록을 불러오는 중").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("‘밥’에 맞는 식품이 없어요.").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(searchLoading = false, searchError = "목록을 불러오지 못했어요.") }
        compose.onNodeWithText("목록 다시 불러오기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("밥", retriedQuery) }
        compose.onNodeWithTag("meal-food-results").assertDoesNotExist()
    }

    @Test fun noFoodMatchesOfferSearchResetAndFoodRegistration() {
        var cleared = false
        var opened = false
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "searchFood") cleared = args!![0] == ""
            if (name == "openNutritionEditor") opened = true
        }, foodState().copy(query = "없는 이름", searchResults = emptyList()), PriceTraceUiState.Idle, "6끼") } }
        compose.onNodeWithText("‘없는 이름’에 맞는 식품이 없어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("식품 등록 · 관리").performScrollTo().performClick()
        compose.onNodeWithText("검색 지우기").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cleared); assertTrue(opened) }
    }

    @Test fun savedDiningMenusAreBoundedAndLoadTheExactSelectedFood() {
        val menus = foods(100, dining = true)
        var selectedId: String? = null
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "useDiningOutFood") selectedId = (args!![0] as NutritionFood).id
        }, diningState().copy(searchResults = menus, selectedFood = menus.first()), PriceTraceUiState.Idle, "7끼") } }
        compose.onNodeWithText("저장된 메뉴에서 선택").performScrollTo().performClick()
        compose.onNodeWithTag("meal-dining-results").performScrollTo()
        compose.onNodeWithTag("food-result-food-0").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "선택됨"))
        assertBounded("meal-dining-results", 288f)
        compose.onNodeWithTag("meal-dining-results").performScrollToIndex(99)
        compose.onNodeWithTag("food-result-food-99").performClick()
        compose.runOnIdle { assertEquals("food-99", selectedId) }
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed()
    }

    @Test fun restaurantResultsScrollWithoutGrowingTheForm() {
        var restaurantId: String? = null
        val prices = PriceTraceUiState.Ready("fitness-owner", query = "식당", searchedQuery = "식당",
            restaurants = (0 until 100).map { fixture<NutritionIntegrationService.RestaurantSummary>("restaurant-$it", "식당 $it", emptyList<NutritionIntegrationService.RestaurantLocation>()) })
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "loadPriceTraceRestaurant") restaurantId = args!![0] as String
        }, diningState(), prices, "7끼") } }
        compose.onNodeWithText("식당 · 메뉴 검색").performScrollTo().performClick()
        compose.onNodeWithTag("meal-restaurants").performScrollTo().performScrollToIndex(99)
        assertBounded("meal-restaurants", 200f)
        compose.onNodeWithText("식당 99").performClick()
        compose.runOnIdle { assertEquals("restaurant-99", restaurantId) }
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed()
    }

    @Test fun branchAndMenuSearchRestoreAndLoadTheExactIdentity() {
        val restoration = StateRestorationTester(compose)
        var applied: Array<out Any?>? = null
        restoration.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "applyPriceTraceSelection") applied = args
        }, diningState(), prices(), "7끼") } }
        compose.onNodeWithText("식당 · 메뉴 검색").performScrollTo().performClick()
        compose.onNodeWithTag("meal-load-restaurant-menu").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("meal-branch").performScrollTo().performClick()
        compose.onNodeWithText("강남점").performClick()
        compose.onNodeWithText("메뉴 이름으로 좁히기").performScrollTo().performTextInput("메뉴 99")
        compose.onNodeWithText("메뉴 이름으로 좁히기").performImeAction()
        compose.onNodeWithTag("meal-menu-targets").performScrollTo()
        assertBounded("meal-menu-targets", 240f)
        compose.onNode(hasText("메뉴 99") and hasAnyAncestor(hasTestTag("meal-menu-targets"))).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("meal-load-restaurant-menu").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("restaurant-1", "식당", "branch-2", "강남점", "menu-99", "메뉴 99", "product-99"), applied!!.toList())
        }
        savePreview("meal-dining-scroll-list.png", "7끼 기록")
    }

    @Test fun restaurantFailureRetriesTheChosenRestaurantAndDisablesRepeatedRequestsWhileLoading() {
        var prices by mutableStateOf(PriceTraceUiState.Ready("fitness-owner", query = "식당", selectedRestaurantId = "restaurant-1", error = "연결을 확인해 주세요."))
        var retries = 0
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions { name, args ->
            if (name == "loadPriceTraceRestaurant") {
                assertEquals("restaurant-1", args!![0]); retries++
                prices = prices.copy(error = null, loading = true)
            }
        }, diningState(), prices, "7끼") } }
        compose.onNodeWithText("식당 · 메뉴 검색").performScrollTo().performClick()
        compose.onNodeWithText("식당 정보 다시 시도").performScrollTo().performClick()
        compose.onNodeWithText("지점과 메뉴를 불러오는 중").assertExists()
        compose.onNodeWithText("식당 검색", substring = false).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun missingRestaurantBranchesKeepMenuLoadingUnavailable() {
        compose.setContent { FitnessComposeTheme(false) { MealEntryDialog(actions(), diningState(), prices().copy(
            detail = fixture<NutritionIntegrationService.RestaurantDetail>("restaurant-1", "식당", emptyList<NutritionIntegrationService.RestaurantLocation>(), emptyList<NutritionIntegrationService.RestaurantMenu>())
        ), "7끼") } }
        compose.onNodeWithText("식당 · 메뉴 검색").performScrollTo().performClick()
        compose.onNodeWithText("등록된 지점이 없어요. 메뉴를 직접 입력하거나 지점 정보를 다시 불러오세요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("meal-load-restaurant-menu").assertDoesNotExist()
        compose.onNodeWithText("지점·메뉴 새로고침").assertExists()
    }

    @Test fun largeTextAndDarkAppearanceKeepTheCompleteSaveButtonVisible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FitnessComposeTheme(true) { MealEntryDialog(actions(), foodState(), PriceTraceUiState.Idle, "12끼") }
            }
        }
        compose.onNodeWithTag("meal-food-results").performScrollTo()
        assertBounded("meal-food-results", 288f)
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed()
        val button = compose.onNodeWithTag("meal-entry-save").fetchSemanticsNode()
        assertEquals(button.size.height.toFloat(), button.boundsInRoot.height, 1f)
        val label = compose.onNodeWithText("12끼 기록하기", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(label.size.height.toFloat(), label.boundsInRoot.height, 1f)
        savePreview("meal-food-scroll-dark-large-text.png", "12끼 기록")
    }

    private fun foods(count: Int, dining: Boolean = false) = (0 until count).map {
        NutritionFood.builder().id("food-$it").ownerId("nutrition-owner").name(if (dining) "외식 메뉴 $it" else "식품 $it")
            .kind(if (dining) NutritionFood.KIND_EXTERNAL_MENU else NutritionFood.KIND_INGREDIENT)
            .source("manual_estimate", null).basis(if (dining) 1.0 else 100.0, if (dining) NutritionUnit.SERVING else NutritionUnit.GRAM)
            .profile(NutritionProfile.ofMacros(150.0, 3.0, 33.0, 1.0)).build()
    }

    private fun foodState() = MealUiState.Ready("fitness-owner", "2026-10-04", true, false, "", foods(100), DiningOutDraft(time = "12:30"),
        foodPortions = listOf(FoodPortionDraft(foods(1).first(), "100")))

    private fun diningState() = MealUiState.Ready("fitness-owner", "2026-10-04", true, true, "", emptyList(),
        DiningOutDraft(store = "식당", menu = "비빔밥", calories = "600", protein = "40", carbs = "80", fat = "20"))

    private fun prices(): PriceTraceUiState.Ready = PriceTraceUiState.Ready("fitness-owner", query = "식당", selectedRestaurantId = "restaurant-1",
        detail = fixture<NutritionIntegrationService.RestaurantDetail>("restaurant-1", "식당", listOf(
            fixture<NutritionIntegrationService.RestaurantLocation>("branch-1", "본점", "pricetrace", "location-1"),
            fixture<NutritionIntegrationService.RestaurantLocation>("branch-2", "강남점", "pricetrace", "location-2")),
            (0 until 100).map { fixture<NutritionIntegrationService.RestaurantMenu>("menu-$it", "product-$it", "메뉴 $it") }))

    private inline fun <reified T> fixture(vararg values: Any): T =
        T::class.java.declaredConstructors.single().apply { isAccessible = true }.newInstance(*values) as T

    private fun actions(onAction: (String, Array<out Any?>?) -> Unit = { _, _ -> }): MealScreenActions =
        Proxy.newProxyInstance(MealScreenActions::class.java.classLoader, arrayOf(MealScreenActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "MealCatalogActions"
                else -> { onAction(method.name, args); null }
            }
        } as MealScreenActions

    private fun assertBounded(tag: String, maxDp: Float) {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("$tag must stay within $maxDp dp", compose.onNodeWithTag(tag).fetchSemanticsNode().size.height <= maxDp * density + 1f)
    }

    private fun savePreview(name: String, title: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("codex-meal-review")!!
        folder.mkdirs()
        val bitmap = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)).captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
