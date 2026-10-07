package com.yeonsik.fitnessapp.feature.meal.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.meal.data.MealRecordRepository
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogSyncStore
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.feature.nutrition.analysis.api.NutritionAnalysisApi
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisReport
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.pricetrace.ProductReadV1Client
import com.yeonsik.fitnessapp.integration.pricetrace.RestaurantMenuReadV1Client
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

/** Real main-thread state + production repositories, with controllable background delivery. */
class MealEditorStateTest {
    private lateinit var room: FitnessRoomDatabase
    private lateinit var catalog: NutritionCatalogRepository
    private lateinit var meals: MealRecordRepository
    private lateinit var integration: NutritionIntegrationService
    private val background = QueuedExecutor()
    private val main = ArrayDeque<Runnable>()
    private val scope = AccountScope("meal-state-fitness-owner")
    private val nutritionOwner = "meal-state-nutrition-owner"
    private val date = LocalDate.now().toString()
    private lateinit var model: MealViewModel
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        catalog = NutritionCatalogRepository(room, nutritionOwner)
        meals = MealRecordRepository(room, catalog, scope.ownerId)
        val auth = SupabaseAuthManager(SupabaseConfigStore(context))
        integration = NutritionIntegrationService(catalog, catalog as NutritionCatalogSyncStore,
            ProductReadV1Client(SupabaseConfig.empty()), RestaurantMenuReadV1Client(SupabaseConfig.empty()), auth, auth)
    }

    @After fun tearDown() { room.close(); background.shutdownNow() }

    private fun createModel(source: NutritionCatalogRepositoryApi = catalog, analysis: NutritionAnalysisApi? = null,
        handle: SavedStateHandle = SavedStateHandle(), searchDelayMillis: Long = 0) {
        onMain {
            model = MealViewModel(handle, meals, source, integration, nutritionAnalysisApi = analysis, executor = background,
                mainExecutor = Executor { main.addLast(it) }, searchDelayMillis = searchDelayMillis)
            model.enter(scope, date)
            model.startDraft()
        }
    }

    @Test fun delayedResultCannotOverwriteNewQueryOrOtherDraftInputs() {
        createModel()
        onMain { model.search("김") }
        background.runNext()
        // An old completion is queued for main, then the user types again before delivery.
        onMain { model.search("김밥"); model.updateTime("12:34"); model.updateQuantity("150") }
        flushMain()
        onMain {
            assertEquals("김밥", state().query)
            assertEquals("12:34", state().draft.time)
            assertEquals("150", state().quantity)
            assertTrue(state().searching)
        }
        background.runNext(); flushMain()
        onMain { assertFalse(state().searching); assertEquals("김밥", state().query) }
    }

    @Test fun rapidTypingQueriesOnlyTheLatestTermAfterDebounce() {
        val queries = mutableListOf<String>()
        createModel(source = object : NutritionCatalogRepositoryApi by catalog {
            override fun searchFoods(query: String): List<NutritionFood> {
                queries += query
                return catalog.searchFoods(query)
            }
        }, searchDelayMillis = 250)
        onMain { model.search("김"); model.search("김바"); model.search("김밥") }
        assertTrue(background.scheduled.await(3, TimeUnit.SECONDS))
        assertEquals(1, background.tasks.size)
        background.runNext(); flushMain()
        assertEquals(listOf("김밥"), queries)
        onMain { assertEquals("김밥", state().query); assertFalse(state().searching) }
    }

    @Test fun oldSearchDoesNotReturnAfterChangingEditorModeOrDate() {
        createModel()
        onMain { model.search("식품") }
        background.runNext()
        onMain { model.chooseDiningOut() }
        flushMain()
        onMain { assertTrue(state().diningOut); assertEquals("", state().query) }
        background.runNext()
        onMain { model.enter(scope, LocalDate.now().minusDays(1).toString()) }
        flushMain()
        onMain { assertEquals(LocalDate.now().minusDays(1).toString(), state().date); assertTrue(state().searchResults.isEmpty()) }
    }

    @Test fun nutritionAccountSwitchDropsQueuedResultsAndEndsLoading() {
        createModel()
        onMain { model.search("식품") }
        background.runNext()
        catalog.setUserId("different-nutrition-owner")
        flushMain()
        onMain {
            assertTrue(state().searchResults.isEmpty())
            assertFalse(state().searching)
            assertNotNull(state().error)
        }
    }

    @Test fun analysisCompletionMergesIntoLatestTextAndQuantity() {
        createModel(analysis = object : NutritionAnalysisApi {
            override fun analyze(scope: AccountScope, startDate: String, endDate: String): NutritionAnalysisReport {
                throw IllegalStateException("분석 테스트 오류")
            }
        })
        background.runNext()
        onMain { model.search("닭가슴살"); model.updateTime("13:10"); model.updateQuantity("120") }
        flushMain()
        onMain {
            assertEquals("닭가슴살", state().query)
            assertEquals("13:10", state().draft.time)
            assertEquals("120", state().quantity)
            assertEquals("분석 테스트 오류", state().nutritionAnalysisError)
        }
    }

    @Test fun manualFoodRegistrationAndMealSaveUseSeparateOwnersAndSnapshots() {
        createModel()
        onMain {
            model.openManualFood()
            model.updateManualFood(ManualFoodDraft(name = "새 식품", basisAmount = "100", calories = "200",
                carbs = "20", protein = "10", fat = "8", sugars = "0"))
            model.updateTime("12:30")
            model.saveManualFood(scope)
        }
        background.runNext(); flushMain()
        lateinit var food: NutritionFood
        onMain {
            food = state().selectedFood!!
            assertEquals(nutritionOwner, food.ownerId)
            assertEquals("100", state().quantity)
            assertFalse(state().manualFoodEntry)
            assertNull(state().notice) // Registering food must not reload the Meal history.
            assertNotNull(state().catalogNotice)
            model.updateQuantity("150")
            model.saveFood(scope) { assertTrue(it) }
        }
        background.runNext(); flushMain()
        instrumentation.waitForIdleSync()
        val dao = room.mealRoomDao()
        assertEquals(1L, dao.mealCountForDate(scope.ownerId, date))
        assertEquals(0L, dao.mealCountForDate(nutritionOwner, date))
        val recordId = room.openHelper.readableDatabase.query("SELECT id FROM meal_records WHERE user_id=?",
            arrayOf(scope.ownerId)).use { cursor -> assertTrue(cursor.moveToFirst()); cursor.getString(0) }
        val snapshot = dao.visibleMealItems(recordId, scope.ownerId).single()
        assertEquals(150.0, snapshot.quantity, 0.0)
        assertEquals(300.0, snapshot.calories, 0.0)
        assertEquals(15.0, snapshot.proteinGrams, 0.0)
        assertNull(snapshot.sodiumMg)
        assertNull(snapshot.saturatedFatGrams)
        assertEquals(0.0, snapshot.sugarsGrams!!, 0.0)
        // Catalog changes must never alter the consumed snapshot.
        room.openHelper.writableDatabase.execSQL("UPDATE nutrition_foods SET calories_kcal=999 WHERE id=?", arrayOf(food.id))
        assertEquals(300.0, dao.visibleMealItems(recordId, scope.ownerId).single().calories, 0.0)
        catalog.setUserId("other-nutrition-owner")
        assertNull(catalog.findFoodById(food.id))
        assertTrue(catalog.searchFoods("새 식품").isEmpty())
    }

    @Test fun invalidManualFoodRetainsDraftWithoutWritingCatalogOrMeals() {
        createModel()
        onMain {
            model.openManualFood()
            model.updateManualFood(ManualFoodDraft(name = "입력 중", calories = "NaN", carbs = "1", protein = "2", fat = "3"))
            model.saveManualFood(scope)
            assertTrue(state().manualFoodEntry)
            assertEquals("입력 중", state().manualFoodDraft.name)
            assertNotNull(state().error)
            assertFalse(state().saving)
        }
        assertTrue(background.tasks.isEmpty())
        assertTrue(catalog.searchFoods("입력 중").isEmpty())
        assertEquals(0L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
    }

    @Test fun manualDraftRestoresOnlyForItsOwnerAndDate() {
        val handle = SavedStateHandle()
        createModel(handle = handle)
        onMain {
            model.openManualFood()
            model.updateManualFood(ManualFoodDraft(name = "작성 중인 식품", basisAmount = "250", basisUnit = "ml", calories = "123"))
            val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
            model = MealViewModel(restored, meals, catalog, integration, executor = background,
                mainExecutor = Executor { main.addLast(it) }, searchDelayMillis = 0)
            model.enter(scope, date)
            assertTrue(state().editing)
            assertTrue(state().manualFoodEntry)
            assertEquals("작성 중인 식품", state().manualFoodDraft.name)
            assertEquals("250", state().manualFoodDraft.basisAmount)
            assertEquals("ml", state().manualFoodDraft.basisUnit)
            model.enter(AccountScope("different-fitness-owner"), date)
            assertFalse(state().manualFoodEntry)
            assertEquals("", state().manualFoodDraft.name)
        }
    }

    @Test fun reusableDiningMenuDoesNotReloadHistoryAndKeepsNullableNutrition() {
        createModel()
        onMain {
            model.chooseDiningOut()
            model.updateStore("새 식당"); model.updateMenu("새 메뉴")
            model.updateCalories("500"); model.updateCarbs("60"); model.updateProtein("20"); model.updateFat("15")
        }
        background.runNext(); flushMain()
        onMain { model.saveReusableDiningOutMenu(scope) }
        background.runNext(); flushMain()
        onMain {
            assertNull(state().notice)
            assertNotNull(state().catalogNotice)
            assertEquals("새 식당", state().draft.store)
            assertNull(state().selectedFood!!.profile.value(NutritionProfile.SODIUM_MG))
            model.search("새 메뉴")
        }
        background.runNext(); flushMain()
        onMain { assertEquals("새 메뉴", state().searchResults.single().name) }
        assertEquals(0L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
    }

    @Test fun reenteringSameDateDuringMenuSaveDoesNotLeaveEditorBusy() {
        createModel()
        onMain {
            model.chooseDiningOut()
            model.updateStore("새 식당"); model.updateMenu("새 메뉴")
            model.updateCalories("500"); model.updateCarbs("60"); model.updateProtein("20"); model.updateFat("15")
        }
        background.runNext(); flushMain()
        onMain { model.saveReusableDiningOutMenu(scope); model.enter(scope, date) }
        background.runNext(); flushMain()
        onMain { assertFalse(state().saving); assertNotNull(state().selectedFood); assertNotNull(state().catalogNotice) }
    }

    @Test fun manualMenuNameEditDetachesOldIdentityAndPreservesTypedNutrition() {
        createModel()
        val food = NutritionFood("linked-menu", nutritionOwner, "원래 메뉴", NutritionFood.KIND_EXTERNAL_MENU,
            1.0, "serving", 500.0, 20.0, 60.0, 15.0, "manual_estimate",
            """{"restaurant_name":"원래 식당","restaurant_id":"restaurant-id","restaurant_location_id":"location-id","restaurant_menu_id":"menu-id","catalog_product_id":"product-id"}""")
        onMain {
            model.useDiningOutFood(food)
            assertEquals("product-id", state().draft.catalogProductId)
            model.updateMenu("직접 입력한 새 메뉴")
            assertEquals("", state().draft.restaurantId)
            assertEquals("", state().draft.restaurantLocationId)
            assertEquals("", state().draft.restaurantMenuId)
            assertEquals("", state().draft.catalogProductId)
            assertNull(state().selectedFood)
            assertEquals("500", state().draft.calories)
            assertEquals("20", state().draft.protein)
        }
    }

    @Test fun nutritionAccountSwitchBeforeManualWriteRejectsStaleOwner() {
        createModel()
        onMain {
            model.openManualFood()
            model.updateManualFood(ManualFoodDraft(name = "계정 전환 식품", calories = "200", carbs = "20", protein = "10", fat = "8"))
            model.saveManualFood(scope)
        }
        catalog.setUserId("different-nutrition-owner")
        background.runNext(); flushMain()
        onMain {
            assertNull(state().selectedFood)
            assertTrue(state().manualFoodEntry)
            assertFalse(state().saving)
            assertNotNull(state().error)
        }
        assertTrue(catalog.searchFoods("계정 전환 식품").isEmpty())
        catalog.setUserId(nutritionOwner)
        assertTrue(catalog.searchFoods("계정 전환 식품").isEmpty())
    }

    private fun state() = model.uiState.value as MealUiState.Ready
    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun flushMain() = onMain { while (main.isNotEmpty()) main.removeFirst().run() }

    private class QueuedExecutor : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        val scheduled = CountDownLatch(1)
        private var stopped = false
        override fun execute(command: Runnable) { tasks.addLast(command); scheduled.countDown() }
        fun runNext() { check(tasks.isNotEmpty()) { "No background task was scheduled" }; tasks.removeFirst().run() }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return tasks.toMutableList().also { tasks.clear() } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }
}
