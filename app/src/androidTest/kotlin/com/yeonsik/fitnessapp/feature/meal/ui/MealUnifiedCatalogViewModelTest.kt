package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMenuIntake
import com.yeonsik.fitnessapp.feature.nutrition.api.*
import com.yeonsik.fitnessapp.feature.nutrition.model.PortionInputMode
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.pricetrace.*
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** All repositories here are fixtures; these tests never open the user's database. */
class MealUnifiedCatalogViewModelTest {
    @Test fun selectedCountAndAmountAreSavedWithoutAnIntermediateDefaultPortion() = withFixture {
        main {
            vm.startDraft()
            vm.selectFood(catalog.product, "2.5", PortionInputMode.COUNT)
            vm.selectFood(catalog.rice, "150", PortionInputMode.BASIS)
            vm.selectFood(catalog.menu, "0.5", PortionInputMode.BASIS)
            assertEquals(listOf("2.5", "150"), state().foodItems.map { it.quantity })
            assertEquals(PortionInputMode.COUNT, state().foodItems.first().amountMode)
            assertEquals("0.5", state().diningMenus.single().quantity)
            assertEquals(listOf("2.5", "150"), handle.get<ArrayList<String>>("meal.portion_amounts"))
            vm.saveFood(scope) {}
        }
        queue.drain()
        assertEquals(listOf(225.0, 150.0), savedFoods.map { it.quantity })
        assertEquals(0.5, savedMenus.single().input.quantity, 0.0)
    }

    @Test fun invalidAmountOrUnsupportedUnitDoesNotAddFood() = withFixture {
        main {
            vm.startDraft()
            listOf("", "0", "-1", "NaN", "Infinity").forEach {
                vm.selectFood(catalog.product, it, PortionInputMode.COUNT)
                assertTrue(state().foodItems.isEmpty())
            }
            vm.selectFood(catalog.rice, "2", PortionInputMode.COUNT)
            assertTrue(state().foodItems.isEmpty())
            vm.selectFood(catalog.rice, "125", PortionInputMode.BASIS)
            assertEquals("125", state().foodItems.single().quantity)
        }
    }

    @Test fun recipeServingAndCookedWeightRemainEquivalentWithPartialConsumption() {
        val recipe = NutritionFood.builder().id("recipe").ownerId("nutrition-owner").name("내 요리")
            .kind(NutritionFood.KIND_RECIPE).basis(1.0, NutritionUnit.SERVING)
            .source("recipe", "{\"recipe_weight_grams\":600,\"recipe_servings\":3}")
            .profile(NutritionProfile.ofMacros(300.0, 20.0, 40.0, 8.0)).build()
        val serving = MealFoodDraftItem("recipe-item", recipe, "2", "25")
        val grams = serving.copy(quantity = "400", amountMode = PortionInputMode.GRAMS)
        assertEquals(0.5, mealFoodConsumedQuantity(serving)!!, 0.0)
        assertEquals(mealFoodConsumedQuantity(serving), mealFoodConsumedQuantity(grams))
        assertEquals("2 인분 중 25% · 실제 0.5 인분 (100 g)", mealFoodPortionSummary(serving))
        assertEquals(150.0, mealFoodNutritionTotals(listOf(grams)).calories(), 0.0)
    }

    @Test fun allSearchMergesFourTypesAndDeduplicatesOnlyById() = withFixture {
        main { vm.startDraft(); vm.search("밥") }; queue.drain()
        main {
            assertEquals(MealCatalogCategory.ALL, state().category)
            assertEquals(setOf("rice", "product", "recipe", "menu"), state().searchResults.map { it.id }.toSet())
            assertEquals(4, state().searchResults.size)
            vm.chooseCategory(MealCatalogCategory.PRODUCT)
        }
        queue.drain()
        main { assertEquals("밥", state().query); assertEquals("product", state().searchResults.single().id) }
    }

    @Test fun filteringPreservesQueryQuantityModeAndPartialConsumption() = withFixture {
        main {
            vm.startDraft(); vm.selectFood(catalog.product); vm.updateFoodQuantity("product", "2.5")
            vm.updateFoodConsumedPercent("product", "50"); vm.search("밥")
            vm.chooseCategory(MealCatalogCategory.RECIPE); vm.chooseCategory(MealCatalogCategory.INGREDIENT)
            vm.chooseCategory(MealCatalogCategory.ALL)
        }
        queue.drain()
        main {
            assertEquals("밥", state().query)
            assertEquals("2.5", state().foodItems.single().quantity)
            assertEquals("50", state().foodItems.single().consumedPercent)
            assertEquals(PortionInputMode.COUNT, state().foodItems.single().amountMode)
            vm.selectFood(catalog.product)
            assertEquals("2.5", state().foodItems.single().quantity)
        }
    }

    @Test fun allSelectionRoutesDiningThroughItsOwnSnapshotAndSavesOneMixedMeal() = withFixture {
        main {
            vm.startDraft(); vm.selectFood(catalog.product); vm.updateFoodQuantity("product", "2")
            vm.updateFoodConsumedPercent("product", "50")
            vm.selectFood(catalog.menu); vm.selectFood(catalog.menu)
            assertEquals(MealCatalogCategory.ALL, state().category); assertFalse(state().diningOut)
            assertEquals(1, state().diningMenus.size); assertFalse(hasCurrentDiningMenu(state()))
            val menu = state().diningMenus.single()
            vm.updateDiningMenu(menu.copy(quantity = "2", consumedPercent = "25"))
            vm.saveFood(scope) {}
        }
        queue.drain()
        assertEquals(1, saves)
        assertEquals(90.0, savedFoods.single().quantity, 0.0)
        assertEquals("product", savedFoods.single().food.id)
        assertEquals("menu", savedMenus.single().nutritionFoodId)
        assertEquals(2.0, savedMenus.single().input.quantity, 0.0)
        assertEquals(0.25, savedMenus.single().consumedFraction, 0.0)
        assertEquals(600.0, savedMenus.single().caloriesKcal, 0.0)
        assertNull(savedMenus.single().input.sodiumMg)
        main { assertFalse(state().editing); assertEquals(MealCatalogCategory.ALL, state().category) }
    }

    @Test fun removalUndoRestoresPositionModePercentAndMenuIdentity() = withFixture {
        main {
            vm.startDraft(); vm.selectFood(catalog.product); vm.selectFood(catalog.rice)
            vm.updateFoodQuantity("product", "2"); vm.updateFoodConsumedPercent("product", "50")
            vm.removeFood("product"); assertEquals("rice", state().foodItems.single().food.id)
            vm.undoRemoveItem()
            assertEquals(listOf("product", "rice"), state().foodItems.map { it.food.id })
            assertEquals(PortionInputMode.COUNT, state().foodItems.first().amountMode)
            assertEquals("2", state().foodItems.first().quantity); assertEquals("50", state().foodItems.first().consumedPercent)
            vm.selectFood(catalog.menu)
            val original = state().diningMenus.single()
            vm.removeDiningMenu(original.id); assertTrue(state().diningMenus.isEmpty())
            vm.undoRemoveItem(); assertEquals(original, state().diningMenus.single())
        }
    }

    @Test fun draftsAndCategoryRestoreAfterViewModelRecreation() = withFixture {
        main {
            vm.startDraft(); vm.selectFood(catalog.product); vm.updateFoodQuantity("product", "2")
            vm.updateFoodConsumedPercent("product", "66.7"); vm.selectFood(catalog.menu)
            vm.search("밥"); vm.chooseCategory(MealCatalogCategory.RECIPE)
        }
        queue.drain()
        main { vm = createModel(); vm.enter(scope, date) }; queue.drain()
        main {
            assertEquals(MealCatalogCategory.RECIPE, state().category); assertEquals("밥", state().query)
            assertEquals("2", state().foodItems.single().quantity); assertEquals("66.7", state().foodItems.single().consumedPercent)
            assertEquals(PortionInputMode.COUNT, state().foodItems.single().amountMode)
            assertEquals("menu", state().diningMenus.single().nutritionFoodId)
            assertEquals("recipe", state().searchResults.single().id)
        }
    }

    @Test fun changingOwnerClearsUndoAndPendingSearchResults() = withFixture {
        main {
            vm.startDraft(); vm.selectFood(catalog.product); vm.removeFood("product"); vm.search("밥")
            vm.enter(AccountScope("another-owner"), date); vm.undoRemoveItem()
        }
        queue.drain()
        main { assertTrue(state().foodItems.isEmpty()); assertNull(state().removedItem); assertTrue(state().searchResults.isEmpty()) }
    }

    @Test fun aStorageFailureKeepsTheEntireMixedDraftForRetry() = withFixture {
        failSave = true
        main {
            vm.startDraft(); vm.selectFood(catalog.product); vm.updateFoodQuantity("product", "2")
            vm.updateFoodConsumedPercent("product", "50"); vm.selectFood(catalog.menu); vm.saveFood(scope) {}
        }
        queue.drain()
        main {
            assertFalse(state().saving); assertNotNull(state().error)
            assertEquals("2", state().foodItems.single().quantity); assertEquals("50", state().foodItems.single().consumedPercent)
            assertEquals("menu", state().diningMenus.single().nutritionFoodId)
            failSave = false; vm.saveFood(scope) {}
        }
        queue.drain(); assertEquals(1, saves)
    }

    private fun withFixture(work: Fixture.() -> Unit) {
        val fixture = Fixture()
        main { fixture.vm = fixture.createModel(); fixture.vm.enter(fixture.scope, fixture.date) }
        try { fixture.work() } finally { fixture.queue.shutdownNow() }
    }

    private class Fixture {
        val scope = AccountScope("fitness-owner")
        val date = "2026-10-12"
        val handle = SavedStateHandle()
        val queue = QueueExecutor()
        val catalog = Catalog()
        lateinit var vm: MealViewModel
        var saves = 0
        var failSave = false
        var savedFoods = emptyList<MealCompositionItem>()
        var savedMenus = emptyList<DiningOutMenuIntake>()
        fun state() = vm.uiState.value as MealUiState.Ready
        fun createModel(): MealViewModel {
            val meals = proxy<MealRecordRepositoryApi> { name, args -> when (name) {
                "setUserId" -> null
                "saveMealItems" -> {
                    check(!failSave) { "Fixture storage failure" }
                    @Suppress("UNCHECKED_CAST") val foods = args!![3] as List<MealCompositionItem>
                    @Suppress("UNCHECKED_CAST") val menus = args[4] as List<DiningOutMenuIntake>
                    savedFoods = foods; savedMenus = menus; saves++; "record"
                }
                else -> error("Unexpected storage operation: $name")
            } }
            val config = SupabaseConfig.empty()
            val sync = proxy<NutritionCatalogSyncStore> { _, _ -> error("Search and intake must remain local") }
            val integration = NutritionIntegrationService(catalog, sync, ProductReadV1Client(config), RestaurantMenuReadV1Client(config),
                SupabaseAuthManager(null), SupabaseAuthManager(null), config)
            return MealViewModel(handle, meals, catalog, integration, executor = queue, searchDelayMillis = 0)
        }
    }

    private class Catalog : NutritionCatalogRepositoryApi {
        override fun currentOwnerId() = "nutrition-owner"
        override fun activeOwnerId() = currentOwnerId()
        val rice = entry("rice", NutritionFood.KIND_INGREDIENT)
        val product = entry("product", NutritionFood.KIND_EXTERNAL_MENU, packageAmount = 90.0)
        val recipe = entry("recipe", NutritionFood.KIND_RECIPE)
        val menu = entry("menu", NutritionFood.KIND_EXTERNAL_MENU, "manual_estimate", NutritionUnit.SERVING)
        private val component = entry("component", NutritionFood.KIND_EXTERNAL_MENU, "manual_option")
        override fun searchFoods(query: String) = listOf(rice, recipe, component).filter { it.name.contains(query) }
        override fun searchPackagedFoods(query: String, limit: Int) = listOf(product).filter { it.name.contains(query) }
        override fun savedRecipes() = listOf(recipe)
        override fun savedDiningOutMenus() = listOf(menu)
        override fun findFoodById(foodId: String) = listOf(rice, product, recipe, menu).firstOrNull { it.id == foodId }
        private fun entry(id: String, kind: String, source: String = "manual", unit: String = NutritionUnit.GRAM,
            packageAmount: Double? = null) = NutritionFood.builder().id(id).ownerId(currentOwnerId()).name("밥")
            .brand(if (source == "manual_estimate") "식당" else null).kind(kind).source(source, null)
            .basis(if (unit == NutritionUnit.SERVING) 1.0 else 100.0, unit)
            .packageAmount(packageAmount).packageUnit(if (packageAmount == null) null else NutritionUnit.GRAM)
            .profile(NutritionProfile.ofMacros(600.0, 40.0, 80.0, 20.0)).build()
    }

    private class QueueExecutor : AbstractExecutorService() {
        private val tasks = ConcurrentLinkedQueue<Runnable>()
        private var stopped = false
        override fun execute(command: Runnable) { tasks.add(command) }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return mutableListOf<Runnable>().also { while (tasks.isNotEmpty()) it.add(tasks.remove()) } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
        fun drain() { while (tasks.isNotEmpty()) { tasks.remove().run(); InstrumentationRegistry.getInstrumentation().waitForIdleSync() } }
    }

    companion object {
        private fun main(work: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(work)
        private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args -> when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "UnifiedMealFixture"
                else -> call(method.name, args)
            } } as T
    }
}
