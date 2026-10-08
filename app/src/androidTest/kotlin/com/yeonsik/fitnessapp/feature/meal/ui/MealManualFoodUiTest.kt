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
            model = MealViewModel(SavedStateHandle(), repository, catalog, integration, executor = executor)
            model.enter(scope, date)
            model.startDraft()
        }
    }

    @After fun tearDown() { executor.shutdownNow(); executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS); room.close() }

    @Test fun missingFoodCanBeRegisteredAndRecordedWithoutLeavingEditor() {
        render()
        field("식품 검색").performScrollTo().performClick().performTextInput("내 새 식품")
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.searching == false }
        compose.onNodeWithText("영양정보 직접 입력").performScrollTo().performClick()
        field("식품명").assertTextContains("내 새 식품")
        field("칼로리 kcal").performScrollTo().performTextReplacement("200")
        field("탄수화물 g").performScrollTo().performTextReplacement("20")
        field("단백질 g").performScrollTo().performTextReplacement("10")
        field("지방 g").performScrollTo().performTextReplacement("8")
        compose.onNodeWithText("새 식품 영양정보").performScrollTo()
        capture("manual-food-light")
        compose.onNodeWithText("내 식품으로 등록").performScrollTo().performClick()
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.selectedFood != null }
        field("섭취량 g").performScrollTo().assertTextContains("100").performTextReplacement("150")
        compose.onNodeWithText("끼니 기록하기").performScrollTo().performClick()
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.notice != null }
        assertEquals(1L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
        room.openHelper.readableDatabase.query("SELECT calories FROM meal_records WHERE user_id=?",
            arrayOf(scope.ownerId)).use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(300.0, cursor.getDouble(0), 0.0) }
    }

    @Test fun darkLargeTextKeepsDirectEntryAndValidationUsable() {
        compose.runOnUiThread { model.openManualFood() }
        render(dark = true, fontScale = 1.5f)
        field("식품명").performScrollTo().performTextReplacement("검증용 긴 이름의 식품")
        compose.onNodeWithText("내 식품으로 등록").performScrollTo().performClick()
        compose.onNodeWithText("칼로리에 0 이상 숫자를 입력하세요.").assertExists()
        field("식품명").assertTextContains("검증용 긴 이름의 식품")
        compose.onNodeWithText("새 식품 영양정보").performScrollTo()
        capture("manual-food-dark-large")
    }

    @Test fun diningDirectEntryAndReusableMenuSaveAreVisible() {
        compose.runOnUiThread { model.chooseDiningOut() }
        render()
        compose.onNodeWithText("영양정보 직접 입력").performScrollTo().performClick()
        compose.onNodeWithText("외식 영양정보 직접 입력").performScrollTo().assertIsDisplayed()
        field("상호명").performTextReplacement("검증 식당")
        field("메뉴명").performScrollTo().performTextReplacement("검증 메뉴")
        field("칼로리 kcal").performScrollTo().performTextReplacement("500")
        field("탄수화물 g").performScrollTo().performTextReplacement("60")
        field("단백질 g").performScrollTo().performTextReplacement("20")
        field("지방 g").performScrollTo().performTextReplacement("15")
        compose.onNodeWithText("내 메뉴로 저장 · 다음에 재사용").performScrollTo().performClick()
        compose.waitUntil(5000) { (model.uiState.value as? MealUiState.Ready)?.catalogNotice != null }
        compose.onNodeWithText("외식 기록하기").performScrollTo().assertIsEnabled()
        assertEquals(0L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
        capture("dining-manual-light")
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
                "updateQuantity" -> model.updateQuantity(args!![0] as String)
                "updateTime" -> model.updateTime(args!![0] as String)
                "saveFood" -> model.saveFood(scope) { }
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
