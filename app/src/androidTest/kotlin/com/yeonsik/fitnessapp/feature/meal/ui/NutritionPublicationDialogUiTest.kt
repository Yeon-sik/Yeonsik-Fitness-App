package com.yeonsik.fitnessapp.feature.meal.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class NutritionPublicationDialogUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manySavedMenusStayInsideABoundedScrollableList() {
        var selected: String? = null
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            FitnessComposeTheme(false) { NutritionPublicationDialog(state(100), PriceTraceUiState.Idle,
                actions { name, args -> if (name == "selectNutritionPublicationMenu") selected = args!![0] as String }) }
        }
        val list = compose.onNodeWithTag("saved-dining-menus")
        assertTrue(list.fetchSemanticsNode().boundsInRoot.height <= 288f * density + 1f)
        compose.onNodeWithText("저장 메뉴 100개").assertIsDisplayed()
        savePreview("nutrition-publication-light.png")
        list.performScrollToIndex(99)
        compose.onNodeWithText("샘플 메뉴 99").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("food-99", selected) }
        compose.onNodeWithTag("publication-primary").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun filteringHasAnEmptyResultAndClearActionWithoutDroppingSelection() {
        val state = state(12).copy(selectedFoodId = "food-2")
        compose.setContent { FitnessComposeTheme(false) { NutritionPublicationDialog(state, PriceTraceUiState.Idle, actions()) } }
        compose.onNodeWithText("저장된 외식 메뉴 검색").performTextInput("없는 메뉴")
        compose.onNodeWithText("검색 결과 0 / 12개").assertExists()
        compose.onNodeWithText("검색 결과에 없어도 이 메뉴의 선택은 유지돼요.").assertExists()
        compose.onNodeWithText("검색어 지우기").performScrollTo().performClick()
        compose.onNodeWithText("저장 메뉴 12개").assertExists()
        compose.onNodeWithTag("saved-dining-menus").performScrollToIndex(2)
        compose.onNode(hasText("샘플 메뉴 2") and hasAnyAncestor(hasTestTag("saved-dining-menus"))).assertIsSelected()
    }

    @Test fun emptyCatalogOffersRegistrationAndSync() {
        val called = mutableListOf<String>()
        compose.setContent { FitnessComposeTheme(false) { NutritionPublicationDialog(state(0), PriceTraceUiState.Idle,
            actions { name, _ -> called += name }) } }
        compose.onNodeWithText("아직 저장한 외식 메뉴가 없어요.").assertIsDisplayed()
        compose.onNodeWithText("외식 메뉴 등록하기").performClick()
        compose.onNodeWithText("영양정보 동기화").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(called.containsAll(listOf("createDiningOutMenu", "syncNutritionPublicationCatalog"))) }
    }

    @Test fun loadFailureOffersALocalRetry() {
        var retried = false
        compose.setContent { FitnessComposeTheme(false) { NutritionPublicationDialog(
            state(0).copy(error = "메뉴 목록을 불러오지 못했어요.", failure = NutritionPublicationFailure.LOAD),
            PriceTraceUiState.Idle, actions { name, _ -> if (name == "openNutritionPublication") retried = true }) } }
        compose.onNodeWithText("아직 저장한 외식 메뉴가 없어요.").assertDoesNotExist()
        compose.onNodeWithText("메뉴 다시 불러오기").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun progressLocksSelectionButAllowsClosingTheWindow() {
        var closed = false
        compose.setContent { FitnessComposeTheme(false) { NutritionPublicationDialog(
            state(5).copy(syncing = true), PriceTraceUiState.Idle,
            actions { name, _ -> if (name == "closeNutritionPublication") closed = true }) } }
        compose.onNodeWithText("영양정보 동기화 중").assertIsDisplayed()
        compose.onNodeWithText("창을 닫아도 처리는 계속돼요.").assertIsDisplayed()
        compose.onNodeWithText("샘플 메뉴 0").assertIsNotEnabled()
        compose.onNodeWithTag("publication-primary").assertIsNotEnabled()
        compose.onNodeWithText("닫기").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun emptyRestaurantSearchExplainsHowToContinue() {
        compose.setContent { FitnessComposeTheme(false) { NutritionPublicationDialog(
            state(1).copy(selectedFoodId = "food-0"),
            PriceTraceUiState.Ready("fitness-owner", query = "없는 식당", searchedQuery = "없는 식당"), actions()) } }
        compose.onNodeWithText("‘없는 식당’에 맞는 식당이 없어요. 다른 이름으로 검색해 주세요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("publication-primary").assertIsNotEnabled()
    }

    @Test fun branchAndMenuAreSelectedBeforeTheExplicitPublicationConfirmation() {
        var published: List<Any?>? = null
        compose.setContent { FitnessComposeTheme(false) { NutritionPublicationDialog(state(1).copy(selectedFoodId = "food-0"),
            prices(), actions { name, args -> if (name == "publishNutritionMenu") published = args!!.toList() }) } }
        compose.onNodeWithTag("publication-menu-targets").performScrollTo()
        compose.onNodeWithText("비빔밥").performClick()
        compose.onNodeWithTag("publication-primary").assertIsNotEnabled()
        compose.onNodeWithTag("publication-branch").performScrollTo().performClick()
        compose.onNodeWithText("강남점").performClick()
        compose.onNodeWithTag("publication-primary").assertIsEnabled().performClick()
        compose.onNodeWithText("공개 연결 확인").assertIsDisplayed()
        compose.runOnIdle { assertNull(published) }
        compose.onNodeWithText("공개 연결", substring = false).performClick()
        compose.runOnIdle { assertEquals(listOf("branch-2", "menu-1", "product-1"), published) }
    }

    @Test fun anInterruptedRequestUsesReadOnlyVerificationAsTheNextAction() {
        var action: String? = null
        val target = NutritionPublicationTarget("food-0", "nutrition-owner", "restaurant-1", "branch-2", "menu-1", "product-1", "식당 · 강남점 · 비빔밥")
        compose.setContent { FitnessComposeTheme(false) { NutritionPublicationDialog(
            state(1).copy(selectedFoodId = "food-0", lastTarget = target, needsVerification = true,
                error = "공개 결과를 확인하지 못했어요.", failure = NutritionPublicationFailure.PUBLISH),
            prices(), actions { name, _ -> action = name }) } }
        compose.onNodeWithTag("publication-primary").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("verifyNutritionMenu", action) }
        compose.onNodeWithText("공개 연결 확인").assertDoesNotExist()
    }

    @Test fun darkAppearanceAndLargeTextKeepThePrimaryAndCloseActionsVisible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                FitnessComposeTheme(true) { NutritionPublicationDialog(state(30), PriceTraceUiState.Idle, actions()) }
            }
        }
        compose.onNodeWithTag("publication-primary").assertIsDisplayed()
        compose.onNodeWithText("닫기").assertIsDisplayed()
        val button = compose.onNodeWithTag("publication-primary").fetchSemanticsNode()
        assertEquals("The complete button must fit in the visible window",
            button.size.height.toFloat(), button.boundsInRoot.height, 1f)
        val label = compose.onNodeWithText("공개 연결하기", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("The complete action label must fit in the visible window",
            label.size.height.toFloat(), label.boundsInRoot.height, 1f)
        savePreview("nutrition-publication-dark-large-text.png")
    }

    @Test fun recreationKeepsTheSearchAndChosenBranchAndMenu() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FitnessComposeTheme(false) { NutritionPublicationDialog(
            state(3).copy(selectedFoodId = "food-0"), prices(), actions()) } }
        compose.onNodeWithText("저장된 외식 메뉴 검색").performTextInput("샘플")
        compose.onNodeWithTag("publication-branch").performScrollTo().performClick()
        compose.onNodeWithText("강남점").performClick()
        compose.onNodeWithTag("publication-menu-targets").performScrollTo()
        compose.onNodeWithText("비빔밥").performClick()
        compose.onNodeWithTag("publication-primary").assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("publication-primary").assertIsEnabled().performClick()
        compose.onNodeWithText("식당 · 강남점 · 비빔밥").assertIsDisplayed()
    }

    private fun state(count: Int) = NutritionPublicationUiState(
        ownerId = "fitness-owner", nutritionOwnerId = "nutrition-owner", open = true,
        menus = (0 until count).map { index ->
            NutritionFood.builder().id("food-$index").ownerId("nutrition-owner").name("샘플 메뉴 $index")
                .kind(NutritionFood.KIND_EXTERNAL_MENU).source("manual_estimate", null).basis(1.0, "serving")
                .profile(NutritionProfile.ofMacros(600.0, 30.0, 80.0, 15.0)).build()
        })

    private fun prices(): PriceTraceUiState.Ready {
        // Construct immutable contract fixtures without calling an external service.
        val locations = listOf(
            fixture<NutritionIntegrationService.RestaurantLocation>("branch-1", "본점", "pricetrace", "location-1"),
            fixture<NutritionIntegrationService.RestaurantLocation>("branch-2", "강남점", "pricetrace", "location-2"))
        val menus = listOf(fixture<NutritionIntegrationService.RestaurantMenu>("menu-1", "product-1", "비빔밥"))
        return PriceTraceUiState.Ready("fitness-owner", query = "식당", selectedRestaurantId = "restaurant-1",
            detail = fixture<NutritionIntegrationService.RestaurantDetail>("restaurant-1", "식당", locations, menus))
    }

    private inline fun <reified T> fixture(vararg values: Any): T =
        T::class.java.declaredConstructors.single().apply { isAccessible = true }.newInstance(*values) as T

    private fun actions(onAction: (String, Array<out Any?>?) -> Unit = { _, _ -> }): MealScreenActions =
        Proxy.newProxyInstance(MealScreenActions::class.java.classLoader, arrayOf(MealScreenActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PublicationTestActions"
                else -> { onAction(method.name, args); null }
            }
        } as MealScreenActions

    private fun savePreview(name: String) {
        val folder = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("codex-meal-review")!!
        folder.mkdirs()
        val bitmap = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "외식 영양정보 관리"))
            .captureToImage().asAndroidBitmap()
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
