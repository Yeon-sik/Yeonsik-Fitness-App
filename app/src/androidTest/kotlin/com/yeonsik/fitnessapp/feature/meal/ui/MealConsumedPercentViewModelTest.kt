package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMenuIntake
import com.yeonsik.fitnessapp.feature.nutrition.api.*
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

class MealConsumedPercentViewModelTest {
    @Test fun foodsKeepIndividualPercentAndSaveTheirOwnConsumedAmounts() = withFixture {
        main {
            vm.startDraft(); vm.selectFood(catalog.food); vm.selectFood(catalog.secondFood)
            vm.updateFoodQuantity("food", "200"); vm.updateFoodConsumedPercent("food", "50")
            assertEquals(800.0, mealFoodNutritionTotals(state().foodItems).calories(), 0.0)
            vm.saveFood(scope) {}; vm.updateFoodConsumedPercent("food", "75")
            assertEquals("50", state().foodItems.first().consumedPercent)
        }
        queue.drain()
        assertEquals(listOf(100.0, 100.0), savedFoods.map { it.quantity })
        assertEquals(listOf(600.0, 200.0), savedFoods.map { it.calories })
        assertEquals(600.0, catalog.food.profile.calories(), 0.0)
        main { assertTrue(state().foodItems.isEmpty()); assertFalse(state().editing) }
    }

    @Test fun twoDiningMenusSaveDifferentFractionsAndFullNutrition() = withFixture {
        main {
            vm.chooseDiningOut(); vm.useDiningOutFood(catalog.menu); vm.updateDiningConsumedPercent("50"); vm.addDiningMenu()
            assertEquals("100", state().diningConsumedPercent)
            vm.useDiningOutFood(catalog.secondMenu); vm.addDiningMenu()
            assertEquals(500.0, diningNutritionTotals(state()).calories(), 0.0)
            assertEquals(listOf("50", "100"), state().diningMenus.map { it.consumedPercent })
            vm.save(scope) {}
        }
        queue.drain()
        assertEquals(listOf(0.5, 1.0), savedDining.map { it.consumedFraction })
        assertEquals(listOf(600, 200), savedDining.map { it.input.calories })
        assertEquals(listOf("menu", "second-menu"), savedDining.map { it.nutritionFoodId })
        assertNull(savedDining.first().input.sodiumMg)
        main { assertTrue(state().diningMenus.isEmpty()) }
    }

    @Test fun manualMenuRegistrationSavesFullNutritionAndPendingMenuIsIncludedOnce() = withFixture {
        main {
            vm.chooseDiningOut(); vm.updateStore("식당"); vm.updateMenu("수동 메뉴")
            vm.updateCalories("600"); vm.updateProtein("40"); vm.updateCarbs("80"); vm.updateFat("20")
            vm.updateDiningConsumedPercent("50"); vm.saveReusableDiningOutMenu(scope)
        }
        queue.drain()
        assertEquals(600, catalog.registeredCalories)
        assertTrue(savedFoods.isEmpty()); assertTrue(savedDining.isEmpty())
        main { assertEquals(300.0, diningNutritionTotals(state()).calories(), 0.0); vm.save(scope) {} }
        queue.drain()
        assertEquals(1, savedDining.size)
        assertEquals(0.5, savedDining.single().consumedFraction, 0.0)
        assertEquals(1.0, savedDining.single().input.quantity, 0.0)
        assertEquals(600, savedDining.single().input.calories)
    }

    @Test fun invalidPercentCannotWriteEitherMealTypeOrClearADraft() = withFixture {
        main {
            vm.startDraft(); vm.selectFood(catalog.food)
            listOf("", "0", "-1", "101", "NaN").forEach { invalid ->
                vm.updateFoodConsumedPercent("food", invalid); vm.saveFood(scope) { assertFalse(it) }
                assertNotNull(state().error); assertFalse(state().saving)
            }
            vm.chooseDiningOut(); vm.useDiningOutFood(catalog.menu); vm.updateDiningConsumedPercent("101")
            vm.addDiningMenu(); assertTrue(state().diningMenus.isEmpty()); assertEquals("밥", state().draft.menu)
            vm.save(scope) { assertFalse(it) }
        }
        queue.drain()
        assertTrue(savedFoods.isEmpty()); assertTrue(savedDining.isEmpty())
    }

    @Test fun selectingAnotherMenuKeepsThePendingMenuAndItsPercentAndDecimalCaloriesForSaving() = withFixture {
        main {
            vm.chooseDiningOut(); vm.useDiningOutFood(catalog.menu); vm.updateDiningConsumedPercent("50"); vm.addDiningMenu()
            vm.useDiningOutFood(catalog.menu); vm.updateDiningConsumedPercent("75")
            vm.useDiningOutFood(catalog.secondMenu)
            assertEquals("100", state().diningConsumedPercent)
            assertEquals(listOf("50", "75"), state().diningMenus.map { it.consumedPercent })
            vm.updateCalories("200.8"); vm.updateDiningConsumedPercent("25")
            assertEquals(800.2, diningNutritionTotals(state()).calories(), 0.001)
            vm.save(scope) {}
        }
        queue.drain()
        assertEquals(listOf(0.5, 0.75, 0.25), savedDining.map { it.consumedFraction })
        assertEquals(200.8, savedDining.last().caloriesKcal, 0.0)
    }

    @Test fun anotherMenuAtTheSameStoreKeepsParentIdsWithoutReusingThePreviousMenuIdentity() = withFixture {
        main {
            vm.chooseDiningOut()
            vm.applyPriceTraceSelection("restaurant", "식당", "branch", "본점", "rice", "볶음밥", "product-rice")
            vm.updateCalories("600"); vm.updateProtein("40"); vm.updateCarbs("80"); vm.updateFat("20")
            vm.updateDiningConsumedPercent("50"); vm.addDiningMenu()
            val first = state().diningMenus.single()
            assertEquals("restaurant", state().draft.restaurantId); assertEquals("branch", state().draft.restaurantLocationId)
            assertEquals("", state().draft.restaurantMenuId); assertEquals("", state().draft.catalogProductId)
            vm.startAnotherDiningMenu(first.id)
            assertEquals("식당", state().draft.store); assertEquals("본점", state().draft.branch)
            assertEquals("100", state().diningConsumedPercent); assertEquals("", state().draft.calories)
            vm.updateMenu("만두"); vm.updateCalories("200"); vm.updateProtein("10"); vm.updateCarbs("30"); vm.updateFat("5")
            vm.updateDiningPortion("2"); vm.updateDiningConsumedPercent("25"); vm.addDiningMenu()
            assertEquals(listOf("볶음밥", "만두"), state().diningMenus.map { it.draft.menu })
            assertEquals(400.0, diningNutritionTotals(state()).calories(), 0.0)
            vm.save(scope) {}
        }
        queue.drain()
        assertEquals(listOf("restaurant", "restaurant"), savedDining.map { it.input.restaurantId })
        assertEquals(listOf("branch", "branch"), savedDining.map { it.input.restaurantLocationId })
        assertEquals("rice", savedDining.first().input.restaurantMenuId)
        assertNull(savedDining.last().input.restaurantMenuId); assertNull(savedDining.last().input.catalogProductId)
    }

    @Test fun anIncompletePendingMenuIsKeptWhenAnotherSavedMenuIsSelected() = withFixture {
        main {
            vm.chooseDiningOut(); vm.updateStore("내 식당"); vm.updateMenu("입력 중인 메뉴"); vm.updateCalories("600")
            vm.useDiningOutFood(catalog.secondMenu)
            assertEquals("입력 중인 메뉴", state().draft.menu); assertEquals("600", state().draft.calories)
            assertTrue(state().diningMenus.isEmpty()); assertNull(state().selectedFood)
            assertTrue(state().error!!.startsWith("입력 중인 메뉴를 먼저 완성하거나 취소하세요."))
        }
    }

    @Test fun menusRestoreSeparatePercentNutritionAndQuantityAfterRecreation() = withFixture {
        main {
            vm.chooseDiningOut(); vm.useDiningOutFood(catalog.menu); vm.updateDiningConsumedPercent("66.7"); vm.addDiningMenu()
            vm.useDiningOutFood(catalog.secondMenu); vm.updateDiningPortion("2"); vm.addDiningMenu()
            vm.chooseFood(); vm.selectFood(catalog.food); vm.updateFoodConsumedPercent("food", "25")
            vm = createModel(); vm.enter(scope, date)
        }
        queue.drain()
        main {
            assertEquals(listOf("66.7", "100"), state().diningMenus.map { it.consumedPercent })
            assertEquals(listOf("1", "2"), state().diningMenus.map { it.quantity })
            assertEquals("25", state().foodItems.single().consumedPercent)
            vm.enter(scope, "2026-10-10"); assertTrue(state().diningMenus.isEmpty()); assertTrue(state().foodItems.isEmpty())
            vm.chooseDiningOut(); vm.useDiningOutFood(catalog.menu); vm.addDiningMenu()
            vm.enter(AccountScope("other-owner"), "2026-10-10"); assertTrue(state().diningMenus.isEmpty())
        }
    }

    @Test fun savedCompositionSupportsItsOwnFoodPercentWithoutModifyingTheDefinition() = withFixture {
        main { vm.useComposition("template") }; queue.drain()
        main {
            vm.updateFoodConsumedPercent("food", "50")
            assertEquals("200", state().foodPortions.single().quantity)
            assertEquals(600.0, mealFoodNutritionTotals(state().foodItems).calories(), 0.0)
            vm.saveFood(scope) {}
        }
        queue.drain()
        assertEquals(100.0, savedFoods.single().quantity, 0.0)
        assertEquals(200.0, template.groups.single().members.single().quantity, 0.0)
    }

    @Test fun editingOneDiningMenuKeepsTheOtherAndDoesNotChangeItsCatalog() = withFixture {
        main {
            vm.chooseDiningOut(); vm.useDiningOutFood(catalog.menu); vm.addDiningMenu()
            vm.useDiningOutFood(catalog.secondMenu); vm.addDiningMenu()
            val first = state().diningMenus.first()
            vm.updateDiningMenu(first.copy(consumedPercent = "50", draft = first.draft.copy(calories = "800")))
            assertEquals("200", state().diningMenus[1].draft.calories)
            assertEquals("100", state().diningMenus[1].consumedPercent)
            assertEquals(600.0, diningNutritionTotals(state()).calories(), 0.0)
            assertEquals(600.0, catalog.menu.profile.calories(), 0.0)
            vm.removeDiningMenu(first.id); assertEquals(200.0, diningNutritionTotals(state()).calories(), 0.0)
        }
    }

    private fun withFixture(work: Fixture.() -> Unit) {
        val fixture = Fixture()
        try { main { fixture.vm = fixture.createModel(); fixture.vm.enter(fixture.scope, fixture.date) }; fixture.work() }
        finally { fixture.queue.shutdownNow() }
    }

    private class Fixture {
        val scope = AccountScope("fitness-owner")
        val date = "2026-10-09"
        val handle = SavedStateHandle()
        val queue = QueueExecutor()
        val catalog = Catalog()
        var savedFoods = emptyList<MealCompositionItem>()
        var savedDining = emptyList<DiningOutMenuIntake>()
        lateinit var vm: MealViewModel
        val template = CompositionTemplate("template", catalog.currentOwnerId(), "저장 식단", CompositionTemplate.KIND_MEAL,
            null, null, 1, listOf(CompositionGroup("group", "main", "주식", CompositionGroup.MODE_OPTIONAL_MANY, 0, 1, 0,
                listOf(CompositionMember("member", "food", "밥", null, 200.0, NutritionUnit.GRAM, true, 0, null, catalog.food.profile)))))
        fun state() = vm.uiState.value as MealUiState.Ready
        fun createModel(): MealViewModel {
            val meals = proxy<MealRecordRepositoryApi> { name, args -> when (name) {
                "setUserId" -> null
                "saveFoodMealItems" -> { @Suppress("UNCHECKED_CAST") val items = args!![3] as List<MealCompositionItem>; savedFoods = items; "food-record" }
                "saveDiningOutMenuItems" -> { @Suppress("UNCHECKED_CAST") val menus = args!![3] as List<DiningOutMenuIntake>; savedDining = menus; "dining-record" }
                else -> error("Unexpected meal operation: $name")
            } }
            val sync = proxy<NutritionCatalogSyncStore> { _, _ -> error("Consumption must remain local") }
            val config = SupabaseConfig.empty()
            val integration = NutritionIntegrationService(catalog, sync, ProductReadV1Client(config), RestaurantMenuReadV1Client(config),
                SupabaseAuthManager(null), SupabaseAuthManager(null), config)
            val templates = object : NutritionTemplateRepositoryApi {
                override fun currentOwnerId() = catalog.currentOwnerId()
                override fun findTemplate(templateId: String) = template.takeIf { it.id == templateId }
                override fun listTemplates(kind: String?) = listOf(template)
                override fun saveTemplate(template: CompositionTemplate): String = error("Intake must not modify definitions")
                override fun deleteTemplate(templateId: String) = error("Intake must not modify definitions")
            }
            return MealViewModel(handle, meals, catalog, integration, executor = queue, searchDelayMillis = 0,
                nutritionTemplates = templates)
        }
    }

    private class Catalog : NutritionCatalogRepositoryApi {
        override fun currentOwnerId() = "nutrition-owner"
        val food = entry("food", NutritionFood.KIND_INGREDIENT, 100.0, NutritionUnit.GRAM)
        val menu = entry("menu", NutritionFood.KIND_EXTERNAL_MENU, 1.0, NutritionUnit.SERVING)
        val secondFood = entry("second-food", NutritionFood.KIND_INGREDIENT, 100.0, NutritionUnit.GRAM, 200.0)
        val secondMenu = entry("second-menu", NutritionFood.KIND_EXTERNAL_MENU, 1.0, NutritionUnit.SERVING, 200.0)
        var registeredCalories: Int? = null
        override fun searchFoods(query: String) = listOf(food)
        override fun findFoodById(foodId: String) = listOf(food, secondFood, menu, secondMenu).firstOrNull { it.id == foodId }
        override fun savedDiningOutMenus() = listOf(menu)
        override fun saveDiningOutMenuWithNutrition(storeName: String, menuName: String, calories: Int?, proteinGrams: Double?,
            carbsGrams: Double?, fatGrams: Double?, sodiumMg: Double?, sugarsGrams: Double?, saturatedFatGrams: Double?,
            branchName: String?, identity: DiningOutIdentity?): NutritionFood { registeredCalories = calories; return menu }
        private fun entry(id: String, kind: String, amount: Double, unit: String, calories: Double = 600.0) = NutritionFood.builder()
            .id(id).ownerId(currentOwnerId()).name("밥").brand("식당").kind(kind).basis(amount, unit)
            .profile(NutritionProfile.ofMacros(calories, 40.0, 80.0, 20.0)).build()
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
                "toString" -> "MealConsumptionFixture"
                else -> call(method.name, args)
            } } as T
    }
}
