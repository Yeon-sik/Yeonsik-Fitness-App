package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.NutritionProfile
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

class NutritionPublicationViewModelTest {
    @Test fun localLoadFailureCanBeRetriedWithoutTouchingIntakeRecords() {
        val catalog = Catalog().apply { failReads = true }
        val executor = QueueExecutor()
        lateinit var vm: MealViewModel
        main { vm = model(catalog, executor); vm.enter(AccountScope("fitness-owner"), "2026-10-03"); vm.openNutritionPublication() }
        executor.drain()
        main {
            assertEquals(NutritionPublicationFailure.LOAD, vm.nutritionPublicationState.value!!.failure)
            assertFalse(vm.nutritionPublicationState.value!!.busy)
            catalog.failReads = false
            vm.openNutritionPublication()
        }
        executor.drain()
        main {
            assertNull(vm.nutritionPublicationState.value!!.error)
            assertEquals(catalog.food.id, vm.nutritionPublicationState.value!!.menus.single().id)
        }
    }

    @Test fun dismissingAPendingLocalReadDoesNotReopenTheWindow() {
        val catalog = Catalog()
        val executor = QueueExecutor()
        lateinit var vm: MealViewModel
        main {
            vm = model(catalog, executor); vm.enter(AccountScope("fitness-owner"), "2026-10-03")
            vm.openNutritionPublication(); vm.closeNutritionPublication()
        }
        executor.drain()
        main {
            assertFalse(vm.nutritionPublicationState.value!!.open)
            assertFalse(vm.nutritionPublicationState.value!!.busy)
            vm.openNutritionPublication()
        }
        executor.drain()
        main { assertTrue(vm.nutritionPublicationState.value!!.open); assertEquals(1, vm.nutritionPublicationState.value!!.menus.size) }
    }

    @Test fun closingAndReopeningDuringSyncPreservesThePendingOperation() {
        val catalog = Catalog()
        val executor = QueueExecutor()
        lateinit var vm: MealViewModel
        main { vm = model(catalog, executor); vm.enter(AccountScope("fitness-owner"), "2026-10-03"); vm.openNutritionPublication() }
        executor.drain()
        main {
            vm.syncNutritionPublicationCatalog(); vm.closeNutritionPublication()
            assertTrue(vm.nutritionPublicationState.value!!.syncing)
            vm.openNutritionPublication()
            assertTrue(vm.nutritionPublicationState.value!!.syncing)
        }
        // Configurations are empty: no external request is made by this fixture.
        executor.drain()
        main { assertFalse(vm.nutritionPublicationState.value!!.busy); assertTrue(vm.nutritionPublicationState.value!!.open) }
    }

    @Test fun savedSelectionAndOpenWindowRestoreOnlyForTheSameOwners() {
        val catalog = Catalog()
        val executor = QueueExecutor()
        val handle = SavedStateHandle()
        lateinit var original: MealViewModel
        main { original = model(catalog, executor, handle); original.enter(AccountScope("fitness-owner"), "2026-10-03"); original.openNutritionPublication() }
        executor.drain()
        main { original.selectNutritionPublicationMenu(catalog.food.id) }
        executor.drain()
        lateinit var restored: MealViewModel
        main {
            restored = model(catalog, executor, SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            restored.enter(AccountScope("fitness-owner"), "2026-10-03")
        }
        executor.drain()
        main {
            assertTrue(restored.nutritionPublicationState.value!!.open)
            assertEquals(catalog.food.id, restored.nutritionPublicationState.value!!.selectedFoodId)
            catalog.owner = "nutrition-owner-b"
            restored.enter(AccountScope("fitness-owner"), "2026-10-03")
            assertFalse(restored.nutritionPublicationState.value!!.open)
            assertNull(restored.nutritionPublicationState.value!!.selectedFoodId)
            assertTrue(restored.nutritionPublicationState.value!!.menus.isEmpty())
        }
    }

    @Test fun changingFitnessOwnerIgnoresOldPublicationResults() {
        val catalog = Catalog()
        val executor = QueueExecutor()
        lateinit var vm: MealViewModel
        main {
            vm = model(catalog, executor); vm.enter(AccountScope("fitness-owner"), "2026-10-03")
            vm.openNutritionPublication()
            vm.enter(AccountScope("fitness-owner-b"), "2026-10-03")
        }
        executor.drain()
        main {
            assertEquals("fitness-owner-b", vm.nutritionPublicationState.value!!.ownerId)
            assertFalse(vm.nutritionPublicationState.value!!.open)
            assertTrue(vm.nutritionPublicationState.value!!.menus.isEmpty())
        }
    }

    @Test fun editingARestaurantQueryDiscardsAnOlderPendingSearchResult() {
        val catalog = Catalog()
        val executor = QueueExecutor()
        lateinit var vm: MealViewModel
        main {
            vm = model(catalog, executor); vm.enter(AccountScope("fitness-owner"), "2026-10-03")
            vm.updatePriceTraceQuery("첫 식당"); vm.searchPriceTraceRestaurants()
            vm.updatePriceTraceQuery("다음 식당")
        }
        executor.drain()
        main {
            val prices = vm.priceTraceState.value as PriceTraceUiState.Ready
            assertEquals("다음 식당", prices.query)
            assertFalse(prices.loading)
            assertNull(prices.error)
            assertNull(prices.searchedQuery)
            assertNull(prices.detail)
        }
    }

    private fun model(catalog: Catalog, executor: QueueExecutor, handle: SavedStateHandle = SavedStateHandle()): MealViewModel {
        val meals = proxy<MealRecordRepositoryApi> { name ->
            check(name == "setUserId") { "Publication management must not write intake records: $name" }
            null
        }
        val syncStore = proxy<NutritionCatalogSyncStore> { error("Empty connection must not access the sync store") }
        val config = SupabaseConfig.empty()
        val integration = NutritionIntegrationService(catalog, syncStore, ProductReadV1Client(config),
            RestaurantMenuReadV1Client(config), SupabaseAuthManager(null), SupabaseAuthManager(null), config)
        return MealViewModel(handle, meals, catalog, integration, executor = executor)
    }

    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PublicationRepositoryFixture"
                else -> call(method.name)
            }
        } as T

    private fun main(work: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(work)

    private class Catalog : NutritionCatalogRepositoryApi {
        var owner = "nutrition-owner"
        var failReads = false
        val food = NutritionFood.builder().id("saved-menu").ownerId(owner).name("비빔밥")
            .kind(NutritionFood.KIND_EXTERNAL_MENU).source("manual_estimate", null).basis(1.0, "serving")
            .profile(NutritionProfile.ofMacros(600.0, 30.0, 80.0, 15.0)).build()
        override fun currentOwnerId() = owner
        override fun searchFoods(query: String) = emptyList<NutritionFood>()
        override fun findFoodById(foodId: String) = food.takeIf { it.id == foodId && it.ownerId == owner }
        override fun savedDiningOutMenus(): List<NutritionFood> {
            check(!failReads) { "Storage unavailable" }
            return listOfNotNull(findFoodById(food.id))
        }
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
        fun drain() {
            while (queue.isNotEmpty()) {
                queue.remove().run()
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
        }
    }
}
