package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogSyncStore
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.pricetrace.ProductReadV1Client
import com.yeonsik.fitnessapp.integration.pricetrace.RestaurantMenuReadV1Client
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class MealCatalogSearchViewModelTest {
    @Test fun rapidQueriesShowOnlyTheNewestResults() = withModel { vm, _, queue ->
        main { vm.startDraft(); vm.search("밥"); vm.search("계란"); assertTrue(state(vm).searchLoading); assertTrue(state(vm).searchResults.isEmpty()) }
        queue.drain()
        main { assertEquals("계란", state(vm).query); assertEquals("egg", state(vm).searchResults.single().id); assertFalse(state(vm).searchLoading) }
    }

    @Test fun readFailureIsSeparateFromRecordValidationAndCanBeRetried() = withModel { vm, catalog, queue ->
        main { vm.startDraft() }; queue.drain()
        main { vm.saveFood(AccountScope("fitness-owner")) {}; catalog.failReads = true; vm.search("밥") }
        queue.drain()
        main {
            assertNotNull(state(vm).error); assertNotNull(state(vm).searchError); assertFalse(state(vm).searchLoading)
            assertTrue(state(vm).searchResults.isEmpty()); catalog.failReads = false; vm.search(state(vm).query)
        }
        queue.drain()
        main { assertNull(state(vm).searchError); assertNotNull(state(vm).error); assertEquals("rice", state(vm).searchResults.single().id) }
    }

    @Test fun addingFoodsPreservesTheQueryAndResultsAndSupportsRepeatedAdds() = withModel { vm, catalog, queue ->
        main { vm.startDraft(); vm.search("밥") }; queue.drain()
        main {
            vm.selectFood(catalog.rice); vm.selectFood(catalog.rice)
            assertEquals("밥", state(vm).query); assertEquals("rice", state(vm).searchResults.single().id)
            assertEquals("200", state(vm).foodPortions.single().quantity); assertFalse(state(vm).searchLoading)
        }
    }

    @Test fun switchingToDiningIgnoresThePendingFoodQuery() = withModel { vm, _, queue ->
        main { vm.startDraft(); vm.search("밥"); vm.chooseDiningOut() }
        queue.drain()
        main { assertTrue(state(vm).diningOut); assertEquals("dining", state(vm).searchResults.single().id); assertFalse(state(vm).searchLoading) }
    }

    @Test fun choosingASavedMenuKeepsTheListAvailableAndLoadsItsNutrition() = withModel { vm, catalog, queue ->
        main { vm.startDraft(); vm.chooseDiningOut(); vm.search("비빔밥") }; queue.drain()
        main {
            vm.useDiningOutFood(catalog.menu)
            assertEquals("비빔밥", state(vm).query); assertEquals("dining", state(vm).searchResults.single().id)
            assertEquals("dining", state(vm).selectedFood!!.id); assertEquals("600", state(vm).draft.calories)
        }
    }

    @Test fun closingTheEditorIgnoresPendingResultsAndReopeningLoadsAgain() = withModel { vm, _, queue ->
        main { vm.startDraft(); vm.search("밥"); vm.closeDraft() }; queue.drain()
        main { assertFalse(state(vm).editing); assertFalse(state(vm).searchLoading); assertTrue(state(vm).searchResults.isEmpty()); vm.startDraft() }
        queue.drain()
        main { assertTrue(state(vm).editing); assertEquals("rice", state(vm).searchResults.single().id) }
    }

    @Test fun changingTheFitnessOwnerDiscardsPendingCatalogResults() = withModel { vm, _, queue ->
        main { vm.startDraft(); vm.search("밥"); vm.enter(AccountScope("other-owner"), "2026-10-04") }; queue.drain()
        main { assertEquals("other-owner", state(vm).ownerId); assertFalse(state(vm).searchLoading); assertTrue(state(vm).searchResults.isEmpty()) }
    }

    @Test fun aNewRestaurantMenuClearsOldNutritionButReloadingTheSameTargetKeepsUserInput() = withModel { vm, catalog, _ ->
        main {
            vm.chooseDiningOut(); vm.useDiningOutFood(catalog.menu); vm.updateDiningConsumedPercent("50")
            vm.updateStore("이전 식당")
            vm.applyPriceTraceSelection("restaurant", "식당", "branch", "본점", "new-menu", "새 메뉴", "product")
            assertEquals("", state(vm).draft.calories); assertEquals("", state(vm).draft.protein); assertEquals("1", state(vm).diningPortion)
            assertEquals("100", state(vm).diningConsumedPercent)
            assertEquals("비빔밥", state(vm).diningMenus.single().draft.menu)
            assertEquals("50", state(vm).diningMenus.single().consumedPercent)
            vm.updateCalories("450"); vm.updateProtein("25"); vm.updateDiningPortion("0.5"); vm.updateDiningConsumedPercent("75")
            vm.applyPriceTraceSelection("restaurant", "식당", "branch", "본점", "new-menu", "새 메뉴", "product")
            assertEquals("450", state(vm).draft.calories); assertEquals("25", state(vm).draft.protein); assertEquals("0.5", state(vm).diningPortion)
            assertEquals("75", state(vm).diningConsumedPercent)
            vm.applyPriceTraceSelection("restaurant", "식당", "", "본점", "invalid-menu", "잘못된 메뉴", "product")
            assertEquals("new-menu", state(vm).draft.restaurantMenuId); assertNotNull(state(vm).error)
        }
    }

    private fun withModel(work: (MealViewModel, Catalog, QueueExecutor) -> Unit) {
        val catalog = Catalog()
        val queue = QueueExecutor()
        val meals = proxy<MealRecordRepositoryApi> { name -> check(name == "setUserId") { "Selection must not write intake records: $name" }; null }
        val sync = proxy<NutritionCatalogSyncStore> { error("Catalog search must not access remote sync") }
        val config = SupabaseConfig.empty()
        val integration = NutritionIntegrationService(catalog, sync, ProductReadV1Client(config), RestaurantMenuReadV1Client(config),
            SupabaseAuthManager(null), SupabaseAuthManager(null), config)
        lateinit var vm: MealViewModel
        main { vm = MealViewModel(SavedStateHandle(), meals, catalog, integration, executor = queue, searchDelayMillis = 0); vm.enter(AccountScope("fitness-owner"), "2026-10-04") }
        try { work(vm, catalog, queue) } finally { queue.shutdownNow() }
    }

    private fun state(vm: MealViewModel) = vm.uiState.value as MealUiState.Ready
    private fun main(work: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(work)

    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "MealCatalogRepositoryFixture"
            else -> call(method.name)
        }
    } as T

    private class Catalog : NutritionCatalogRepositoryApi {
        var failReads = false
        override fun currentOwnerId() = "nutrition-owner"
        val rice = food("rice", "밥", NutritionFood.KIND_INGREDIENT)
        val egg = food("egg", "계란", NutritionFood.KIND_INGREDIENT)
        val menu = food("dining", "비빔밥", NutritionFood.KIND_EXTERNAL_MENU)
        override fun searchFoods(query: String): List<NutritionFood> { check(!failReads) { "Storage unavailable" }; return listOf(rice, egg).filter { it.name.contains(query) } }
        override fun savedDiningOutMenus(): List<NutritionFood> { check(!failReads) { "Storage unavailable" }; return listOf(menu) }
        override fun findFoodById(foodId: String) = listOf(rice, egg, menu).firstOrNull { it.id == foodId }
        private fun food(id: String, name: String, kind: String) = NutritionFood.builder().id(id).ownerId(currentOwnerId()).name(name).kind(kind)
            .source("manual_estimate", null).basis(if (kind == NutritionFood.KIND_EXTERNAL_MENU) 1.0 else 100.0,
                if (kind == NutritionFood.KIND_EXTERNAL_MENU) NutritionUnit.SERVING else NutritionUnit.GRAM)
            .profile(NutritionProfile.ofMacros(600.0, 30.0, 80.0, 15.0)).build()
    }

    private class QueueExecutor : AbstractExecutorService() {
        private val queue = ConcurrentLinkedQueue<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { queue.add(command) }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return mutableListOf<Runnable>().also { while (queue.isNotEmpty()) it.add(queue.remove()) } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && queue.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
        fun drain() { while (queue.isNotEmpty()) { queue.remove().run(); InstrumentationRegistry.getInstrumentation().waitForIdleSync() } }
    }
}
