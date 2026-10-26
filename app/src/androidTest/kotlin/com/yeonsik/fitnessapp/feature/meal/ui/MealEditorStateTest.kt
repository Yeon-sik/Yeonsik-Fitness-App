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
import com.yeonsik.fitnessapp.feature.meal.data.MealReadRepository
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogSyncStore
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodInput
import com.yeonsik.fitnessapp.data.NutritionUnit
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
        handle: SavedStateHandle = SavedStateHandle(), searchDelayMillis: Long = 0,
        readApi: MealReadApi = MealReadRepository(room)) {
        onMain {
            model = MealViewModel(handle, meals, source, integration, nutritionAnalysisApi = analysis, executor = background,
                mainExecutor = Executor { main.addLast(it) }, searchDelayMillis = searchDelayMillis, mealReadApi = readApi)
            model.enter(scope, date)
            model.startDraft()
        }
        // Resolve the initial empty-query search before controlling later requests.
        if (searchDelayMillis == 0L && analysis == null) { background.runAll(); flushMain() }
    }

    @Test fun delayedResultCannotOverwriteNewQueryOrOtherDraftInputs() {
        createModel()
        val food = registerBaseFood()
        onMain { model.selectFood(food) }
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
        onMain { assertTrue(state().diningOut); assertEquals("식품", state().query) }
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
        val food = registerBaseFood()
        onMain { model.selectFood(food) }
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
                carbs = "20", protein = "10", fat = "8", sugars = "0", transFat = "0.4"))
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
        assertEquals(0.6, snapshot.transFatGrams!!, 0.001)
        // Catalog changes must never alter the consumed snapshot.
        room.openHelper.writableDatabase.execSQL("UPDATE nutrition_foods SET calories_kcal=999,trans_fat_grams=7 WHERE id=?", arrayOf(food.id))
        assertEquals(300.0, dao.visibleMealItems(recordId, scope.ownerId).single().calories, 0.0)
        assertEquals(0.6, dao.visibleMealItems(recordId, scope.ownerId).single().transFatGrams!!, 0.001)
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

    @Test fun catalogWriteRejectsInconsistentProfilesEvenWithoutTheEditor() {
        val profile = NutritionProfile.builder().value(NutritionProfile.CALORIES_KCAL, 600.0)
            .value(NutritionProfile.PROTEIN_GRAMS, 20.0).value(NutritionProfile.CARBS_GRAMS, 43.0)
            .value(NutritionProfile.FAT_GRAMS, 28.0).value(NutritionProfile.SUGARS_GRAMS, 8.0).build()
        listOf(NutritionProfile.SUGARS_GRAMS to 50.0, NutritionProfile.ADDED_SUGARS_GRAMS to 9.0,
            NutritionProfile.SATURATED_FAT_GRAMS to 30.0, NutritionProfile.TRANS_FAT_GRAMS to 30.0).forEach { (key, value) ->
            val invalid = NutritionProfile.builder().from(profile).value(key, value).build()
            assertThrows(IllegalArgumentException::class.java) {
                catalog.saveManualFoodInput(AccountScope(nutritionOwner),
                    NutritionFoodInput("잘못된 라벨", null, 100.0, NutritionUnit.GRAM, invalid, "manual"))
            }
        }
        assertTrue(catalog.searchFoods("잘못된 라벨").isEmpty())
        assertEquals(0L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
    }

    @Test fun diningDraftSerializationPreservesTransFatAndReadsOlderDraftsAsUnknown() {
        val menu = MealDiningDraftItem("menu", DiningOutDraft(store = "식당", menu = "메뉴", transFat = "0.4"))
        assertEquals(menu, decodeDiningMenu(encodeDiningMenu(menu)))
        val old = org.json.JSONObject(encodeDiningMenu(menu))
        old.getJSONArray("draft").remove(17)
        assertEquals("", decodeDiningMenu(old.toString())!!.draft.transFat)
        val unknown = menu.copy(draft = menu.draft.copy(transFat = ""))
        val zero = menu.copy(draft = menu.draft.copy(transFat = "0"))
        assertEquals("", decodeDiningMenu(encodeDiningMenu(unknown))!!.draft.transFat)
        assertEquals("0", decodeDiningMenu(encodeDiningMenu(zero))!!.draft.transFat)
    }

    @Test fun manualDraftRestoresOnlyForItsOwnerAndDate() {
        val handle = SavedStateHandle()
        createModel(handle = handle)
        onMain {
            model.openManualFood()
            model.updateManualFood(ManualFoodDraft(name = "작성 중인 식품", basisAmount = "250", basisUnit = "ml", calories = "123", transFat = "0.4"))
            val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
            model = MealViewModel(restored, meals, catalog, integration, executor = background,
                mainExecutor = Executor { main.addLast(it) }, searchDelayMillis = 0)
            model.enter(scope, date)
            assertTrue(state().editing)
            assertTrue(state().manualFoodEntry)
            assertEquals("작성 중인 식품", state().manualFoodDraft.name)
            assertEquals("250", state().manualFoodDraft.basisAmount)
            assertEquals("ml", state().manualFoodDraft.basisUnit)
            assertEquals("0.4", state().manualFoodDraft.transFat)
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
            model.updateTransFat("0.4")
        }
        background.runNext(); flushMain()
        onMain { model.saveReusableDiningOutMenu(scope) }
        background.runNext(); flushMain()
        onMain {
            assertNull(state().notice)
            assertNotNull(state().catalogNotice)
            assertEquals("새 식당", state().draft.store)
            assertNull(state().selectedFood!!.profile.value(NutritionProfile.SODIUM_MG))
            assertEquals(0.4, state().selectedFood!!.profile.transFatGrams()!!, 0.0)
            assertEquals("0.4", state().draft.transFat)
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
            assertEquals("restaurant-id", state().draft.restaurantId)
            assertEquals("location-id", state().draft.restaurantLocationId)
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

    @Test fun dateTimeEditRefreshNoticeAndReadsMoveTheOriginalRecordToTheDestination() {
        createModel()
        val food = registerBaseFood()
        val id = meals.saveFoodMeal(scope, date, "12:30", food.id, 100.0)
        val read = MealReadRepository(room)
        val before = read.mealSnapshot(scope, id)!!
        val target = LocalDate.now().minusDays(2).toString()
        val meal = HomeMealSummary(id, date, "1끼", food.name, 100, 10.0, 10.0, 1.0, 1,
            food.name, "12:30", "food", null, "", "", "", "recorded", "", "", true, null, "", "")
        onMain {
            model.closeDraft()
            model.openMealDateTimeEditor(meal)
            model.saveMealDateTime(scope, id, target, "19:20")
            assertTrue(state().recordActionSaving)
        }
        background.runNext(); flushMain()
        onMain {
            assertFalse(state().recordActionSaving)
            assertNull(state().recordEditor)
            assertNotNull(state().notice) // ComposeAppScreen observes this to reload Home/Meal history.
        }
        assertTrue(read.meals(scope, date).isEmpty())
        assertEquals(id, read.meals(scope, target).single().id)
        assertEquals("19:20", read.meals(scope, target).single().mealTime)
        assertEquals(before.items, read.mealSnapshot(scope, id)!!.items)
    }

    @Test fun detailUsesTheRequestedRecordSnapshotEvenAfterCatalogNamesChange() {
        val read = MealReadRepository(room)
        val requests = mutableListOf<Pair<String, String>>()
        createModel(readApi = object : MealReadApi by read {
            override fun mealSnapshot(scope: AccountScope, recordId: String): MealSnapshotRead? {
                requests += scope.ownerId to recordId
                return read.mealSnapshot(scope, recordId)
            }
        })
        val food = registerBaseFood()
        val id = meals.saveFoodMeal(scope, date, "12:30", food.id, 150.0)
        room.openHelper.writableDatabase.execSQL("UPDATE nutrition_foods SET name='현재 이름' WHERE id=?", arrayOf(food.id))
        onMain { model.openMealDetail(scope, id); assertEquals(MealDetailState.Loading(id), state().recordDetail) }
        background.runNext(); flushMain()
        onMain {
            val detail = state().recordDetail as MealDetailState.Ready
            assertEquals(id, detail.recordId)
            assertEquals("기준 식품", detail.snapshot.items.single().foodName)
            assertEquals(150.0, detail.snapshot.items.single().quantity, 0.0)
            assertEquals("g", detail.snapshot.items.single().unit)
            model.closeMealDetail()
            assertEquals(MealDetailState.Idle, state().recordDetail)
        }
        assertEquals(listOf(scope.ownerId to id), requests)
    }

    @Test fun detailRejectsAnotherOwnerAndKeepsLegacyMissingItemsExplicit() {
        createModel()
        val food = registerBaseFood()
        val id = meals.saveFoodMeal(scope, date, "12:30", food.id, 100.0)
        onMain { model.enter(AccountScope("other-owner"), date); model.openMealDetail(AccountScope("other-owner"), id) }
        background.runNext(); flushMain()
        onMain { assertTrue(state().recordDetail is MealDetailState.Error) }
        onMain { model.enter(scope, date) }
        room.openHelper.writableDatabase.execSQL("DELETE FROM meal_record_items WHERE meal_record_id=?", arrayOf(id))
        onMain { model.openMealDetail(scope, id) }
        background.runNext(); flushMain()
        onMain { assertTrue((state().recordDetail as MealDetailState.Ready).snapshot.items.isEmpty()) }
    }

    @Test fun staleDetailCannotReplaceANewerRecordOwnerDateOrDismissedDialog() {
        createModel()
        val food = registerBaseFood()
        val first = meals.saveFoodMeal(scope, date, "12:30", food.id, 100.0)
        val second = meals.saveFoodMeal(scope, date, "18:30", food.id, 200.0)
        onMain { model.openMealDetail(scope, first) }
        background.runNext() // Hold the first publication until another record is requested.
        onMain { model.openMealDetail(scope, second) }
        flushMain()
        onMain { assertEquals(MealDetailState.Loading(second), state().recordDetail) }
        background.runNext(); flushMain()
        onMain { assertEquals(second, (state().recordDetail as MealDetailState.Ready).recordId) }

        onMain { model.openMealDetail(scope, first) }
        background.runNext()
        onMain { model.enter(AccountScope("other-owner"), date) }
        flushMain()
        onMain { assertEquals(MealDetailState.Idle, state().recordDetail) }

        onMain { model.enter(scope, date); model.openMealDetail(scope, first) }
        background.runNext()
        onMain { model.enter(scope, LocalDate.now().minusDays(1).toString()) }
        flushMain()
        onMain { assertEquals(MealDetailState.Idle, state().recordDetail) }

        onMain { model.enter(scope, date); model.openMealDetail(scope, first) }
        background.runNext()
        onMain { model.closeMealDetail() }
        flushMain()
        onMain { assertEquals(MealDetailState.Idle, state().recordDetail) }
    }

    @Test fun searchRemovalKeepsQueryResultsAndDraftCountWithOrdinaryAndMixedMenuItems() {
        createModel()
        val food = registerBaseFood()
        onMain { model.search("기준") }
        background.runNext(); flushMain()
        onMain {
            val results = state().searchResults
            model.selectFood(food)
            assertEquals(1, mealFoodItems(state()).size)
            model.removeFood(food.id)
            assertTrue(mealFoodItems(state()).isEmpty())
            assertEquals("기준", state().query)
            assertEquals(results, state().searchResults)
            model.selectFood(food)
            assertEquals(1, mealFoodItems(state()).size)
            val menu = NutritionFood.builder().id("mixed-menu").ownerId(nutritionOwner).name("외식 메뉴")
                .brand("식당").kind(NutritionFood.KIND_EXTERNAL_MENU).source("manual_estimate", null).basis(1.0, NutritionUnit.SERVING)
                .profile(NutritionProfile.ofMacros(200.0, 20.0, 25.0, 4.0)).build()
            model.selectFood(menu)
            assertFalse(state().diningOut)
            assertEquals(1, diningMealMenus(state()).size)
            model.removeFood(menu.id)
            assertTrue(diningMealMenus(state()).isEmpty())
            assertEquals(1, mealFoodItems(state()).size)
            assertEquals("기준", state().query)
        }
    }

    private fun registerBaseFood(): NutritionFood {
        val profile = NutritionProfile.builder().from(NutritionProfile.ofMacros(100.0, 10.0, 10.0, 1.0))
            .value(NutritionProfile.SODIUM_MG, 0.0)
            .value(NutritionProfile.SATURATED_FAT_GRAMS, 0.0)
            .value(NutritionProfile.SUGARS_GRAMS, 0.0).build()
        return catalog.registerFood(nutritionOwner,
            NutritionFoodInput("기준 식품", null, 100.0, NutritionUnit.GRAM, profile, "manual"))
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
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return tasks.toMutableList().also { tasks.clear() } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }

    @Test fun productRegistrationReturnsToTheSameMealWithoutDroppingItsExistingCart() {
        createModel()
        val existing = registerBaseFood()
        onMain {
            model.selectFood(existing)
            model.openManualFood()
            model.updateManualFood(ManualFoodDraft(name = "추가 제품", brand = "브랜드", basisAmount = "100",
                calories = "400", protein = "20", carbs = "30", fat = "20",
                registrationKind = com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRegistrationKind.PRODUCT,
                packageAmount = "100"))
            model.openProductRegistration()
            assertTrue(model.nutritionEditor.uiState.value!!.returnToMeal)
            model.nutritionEditor.save()
        }
        background.runAll()
        instrumentation.waitForIdleSync()
        flushMain()
        onMain {
            assertFalse(model.nutritionEditor.uiState.value!!.open)
            assertFalse(state().manualFoodEntry)
            assertEquals(2, mealFoodItems(state()).size)
            assertTrue(mealFoodItems(state()).any { it.food.id == existing.id })
            assertTrue(mealFoodItems(state()).any { it.food.name == "추가 제품" })
            assertTrue(state().editing)
        }
        assertEquals(0L, room.mealRoomDao().mealCountForDate(scope.ownerId, date))
    }

}
