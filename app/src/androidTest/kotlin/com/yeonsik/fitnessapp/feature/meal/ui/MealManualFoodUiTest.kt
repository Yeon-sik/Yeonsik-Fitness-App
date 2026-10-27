package com.yeonsik.fitnessapp.feature.meal.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.yeonsik.fitnessapp.feature.nutrition.api.PriceTraceBrandCatalog
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandRow
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import com.yeonsik.fitnessapp.feature.meal.data.MealRecordRepository
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.pricetrace.ProductReadV1Client
import com.yeonsik.fitnessapp.integration.pricetrace.RestaurantMenuReadV1Client
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.concurrent.Executors

/** Plain Meal UI drives the actual state holder and in-memory Room, without remote calls. */
class MealManualFoodUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var room: FitnessRoomDatabase
    private lateinit var model: MealViewModel
    private val modelStore = ViewModelStore()
    private val canonicalBrand = PriceTraceBrandSelection("11111111-1111-4111-8111-111111111111", "CJ", null, null)
    private val brandCatalog = object : PriceTraceBrandCatalog {
        override fun brandCatalogScope() = 0L
        override fun isBrandCatalogConfigured() = true
        override fun searchPriceTraceBrands(query: String, limit: Int, offset: Int) =
            listOf(PriceTraceBrandRow(canonicalBrand, "씨제이", "alias", null, null))
        override fun observeBrandCatalogScope(listener: Runnable) = AutoCloseable { }
    }
    private val executor = Executors.newSingleThreadExecutor()
    private val scope = AccountScope("meal-ui-fitness-owner")
    private val date = LocalDate.now().toString()
    private var hideKeyboard: () -> Unit = {}

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        val catalog = NutritionCatalogRepository(room, "meal-ui-nutrition-owner")
        val repository = MealRecordRepository(room, catalog, scope.ownerId)
        val auth = SupabaseAuthManager(SupabaseConfigStore(context))
        val integration = NutritionIntegrationService(catalog, catalog,
            ProductReadV1Client(SupabaseConfig.empty()), RestaurantMenuReadV1Client(SupabaseConfig.empty()), auth, auth)
        compose.runOnUiThread {
            model = MealViewModel(SavedStateHandle(), repository, catalog, integration, executor = executor, manualBrandCatalog = brandCatalog,
                diningCatalog = com.yeonsik.fitnessapp.feature.meal.api.PriceTraceDiningCatalog { throw java.io.IOException("offline") })
            modelStore.put("meal", model)
            model.enter(scope, date)
            model.startDraft()
        }
    }

    @After fun tearDown() { compose.runOnUiThread { modelStore.clear() }; executor.shutdownNow(); executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS); room.close() }

    @Test fun missingFoodCanBeRegisteredAndRecordedWithoutLeavingEditor() {
        render()
        compose.onNodeWithTag("meal-catalog-query").performScrollTo().performClick().performTextInput("내 새 식품")
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.searching == false }
        compose.onNodeWithText("음식 직접 등록").performScrollTo().performClick()
        field("식품명").assertTextContains("내 새 식품")
        field("칼로리 kcal").performScrollTo().performTextReplacement("200")
        field("탄수화물 (총) g").performScrollTo().performTextReplacement("20")
        field("단백질 g").performScrollTo().performTextReplacement("10")
        field("지방 (총) g").performScrollTo().performTextReplacement("8")
        compose.onNodeWithText("나트륨·당류·포화지방·트랜스지방 입력 (선택)").performScrollTo().performClick()
        field("트랜스지방 g (선택)").performScrollTo().performTextReplacement("0.4")
        compose.onNodeWithText("새 재료 등록").performScrollTo()
        capture("manual-food-light")
        compose.onNodeWithText("내 식품으로 등록").performScrollTo().performClick()
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.selectedFood != null }
        compose.onNodeWithTag("meal-cart-review").performClick()
        compose.onNodeWithContentDescription("내 새 식품 수정").performScrollTo().performClick()
        compose.onNodeWithTag("meal-food-quantity-${(model.uiState.value as MealUiState.Ready).selectedFood!!.id}")
            .performScrollTo().assertTextContains("100").performTextReplacement("150")
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed().performClick()
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.notice != null }
        assertEquals(1L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
        room.openHelper.readableDatabase.query("SELECT calories FROM meal_records WHERE user_id=?",
            arrayOf(scope.ownerId)).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(300.0, cursor.getDouble(0), 0.0) }
        room.openHelper.readableDatabase.query("SELECT trans_fat_grams FROM meal_record_items WHERE user_id=?",
            arrayOf(scope.ownerId)).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0.6, cursor.getDouble(0), 0.001) }
    }

    @Test fun darkLargeTextKeepsDirectEntryAndValidationUsable() {
        compose.runOnUiThread { model.openManualFood() }
        render(dark = true, fontScale = 1.5f)
        field("식품명").performScrollTo().performTextReplacement("검증용 긴 이름의 식품")
        compose.onNodeWithText("내 식품으로 등록").performScrollTo().performClick()
        compose.onAllNodesWithText("칼로리에 0 이상 숫자를 입력하세요.")[0].assertExists()
        field("식품명").assertTextContains("검증용 긴 이름의 식품")
        compose.onNodeWithText("새 재료 등록").performScrollTo()
        capture("manual-food-dark-large")
    }

    @Test fun diningSearchFailureStillAllowsManualMenuAndMealSave() {
        compose.runOnUiThread { model.chooseDiningOut() }
        render()
        compose.onNodeWithTag("meal-dining-query").performScrollTo().assertIsDisplayed().performTextInput("빅맥")
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.diningSearch?.error != null }
        compose.onNodeWithText("offline").assertExists()
        compose.onNodeWithText("추가할 메뉴").performScrollTo().assertIsDisplayed()
        field("상호명 (필수)").performScrollTo().performTextReplacement("검증 식당")
        field("먹은 메뉴 (필수)").performScrollTo().performTextReplacement("검증 메뉴")
        field("칼로리 kcal").performScrollTo().performTextReplacement("500")
        field("탄수화물 (총) g").performScrollTo().performTextReplacement("60")
        field("단백질 g").performScrollTo().performTextReplacement("20")
        field("지방 (총) g").performScrollTo().performTextReplacement("15")
        compose.onNodeWithText("다음에도 쓸 메뉴로 저장").performScrollTo().performClick()
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.catalogNotice != null }
        compose.onNodeWithTag("meal-entry-save").assertIsDisplayed().assertIsEnabled()
        assertEquals(0L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
        capture("dining-manual-light")
        compose.onNodeWithTag("meal-entry-save").performClick()
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.notice != null &&
            (model.uiState.value as? MealUiState.Ready)?.editing == false }
        assertEquals(1L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
    }

    @Test fun productBrandAliasIsSelectedInlineAndStoredDuringInitialRegistration() {
        compose.runOnUiThread { model.chooseCategory(MealCatalogCategory.PRODUCT); model.openManualFood() }
        render()
        field("식품명").performScrollTo().performTextReplacement("새 제품")
        field("브랜드 (선택)").performScrollTo().performTextReplacement("씨제이")
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.manualBrandSearch?.rows?.isNotEmpty() == true }
        compose.onNodeWithText("별칭 · 씨제이", useUnmergedTree = true).assertExists()
        assertNull((model.uiState.value as MealUiState.Ready).manualFoodDraft.priceTraceBrand)
        compose.onNodeWithText("CJ", useUnmergedTree = true).performScrollTo().performClick()
        field("브랜드 (선택)").assertTextContains("CJ")
        compose.onNodeWithText("PriceTrace 연결됨 · CJ").assertExists()
        compose.onNodeWithText("PriceTrace 브랜드 연결").assertDoesNotExist()
        field("제품 1개 용량 · g").performScrollTo().performTextReplacement("100")
        field("칼로리 kcal").performScrollTo().performTextReplacement("100")
        field("탄수화물 (총) g").performScrollTo().performTextReplacement("10")
        field("단백질 g").performScrollTo().performTextReplacement("10")
        field("지방 (총) g").performScrollTo().performTextReplacement("2")
        compose.onNodeWithText("내 식품으로 등록").performScrollTo().performClick()
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.selectedFood != null }
        val food = (model.uiState.value as MealUiState.Ready).selectedFood!!
        assertEquals("CJ", food.brand)
        assertEquals(canonicalBrand, food.priceTraceBrand)
        room.openHelper.readableDatabase.query("SELECT pricetrace_brand_id, pricetrace_brand_name_snapshot FROM nutrition_foods WHERE id=?",
            arrayOf(food.id)).use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals(canonicalBrand.brandId, cursor.getString(0)); assertEquals("CJ", cursor.getString(1))
        }
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun render(dark: Boolean = false, fontScale: Float = 1f) {
        val actions = Proxy.newProxyInstance(MealScreenActions::class.java.classLoader,
            arrayOf(MealScreenActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Meal editor UI callbacks"
                "searchFood" -> model.search(args!![0] as String)
                "openManualFood" -> model.openManualFood()
                "closeManualFood" -> model.closeManualFood()
                "updateManualFood" -> model.updateManualFood(args!![0] as ManualFoodDraft)
                "saveManualFood" -> model.saveManualFood(scope)
                "selectManualFoodBrand" -> model.selectManualFoodBrand(args!![0] as PriceTraceBrandRow)
                "moreManualFoodBrands" -> model.moreManualFoodBrands()
                "retryManualFoodBrands" -> model.retryManualFoodBrands()
                "updateQuantity" -> model.updateQuantity(args!![0] as String)
                "updateFoodQuantity" -> model.updateFoodQuantity(args!![0] as String, args[1] as String)
                "updateTime" -> model.updateTime(args!![0] as String)
                "saveFood" -> model.saveFood(scope) { }
                "updateDiningSearchQuery" -> model.updateDiningSearchQuery(args!![0] as String)
                "searchDiningTargets" -> model.searchDiningTargets()
                "updateStore" -> model.updateStore(args!![0] as String)
                "updateMenu" -> model.updateMenu(args!![0] as String)
                "updateCalories" -> model.updateCalories(args!![0] as String)
                "updateCarbs" -> model.updateCarbs(args!![0] as String)
                "updateProtein" -> model.updateProtein(args!![0] as String)
                "updateFat" -> model.updateFat(args!![0] as String)
                "saveReusableDiningOutMenu" -> model.saveReusableDiningOutMenu(scope)
                else -> null
            }
        } as MealScreenActions
        compose.setContent {
            val density = LocalDensity.current.density
            val keyboard = LocalSoftwareKeyboardController.current
            hideKeyboard = { keyboard?.hide() }
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                FitnessComposeTheme(dark) {
                    val state by model.uiState.observeAsState(MealUiState.Idle)
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .systemBarsPadding().imePadding()
                        .verticalScroll(rememberScrollState()).padding(FitnessSpacing.page),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                        MealScreen(HomeUiState.Ready(HomeSnapshot(scope.ownerId, date, emptyList(), null,
                            emptyList(), emptyMap(), emptyMap(), null, emptyMap(), emptyMap(), emptyMap(), null,
                            null, emptyList(), emptyList())), state, PriceTraceUiState.Idle,
                            NutritionPublicationUiState(), scope.ownerId, date, actions)
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.runOnIdle { hideKeyboard() }
        compose.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(context.getExternalFilesDir("meal-qa"), "$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
