package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.*
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.pricetrace.ProductReadV1Client
import com.yeonsik.fitnessapp.integration.pricetrace.RestaurantMenuReadV1Client
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.*

class MealManualBrandViewModelTest {
    private val owner = AccountScope("fitness-owner")
    private val date = "2026-10-27"
    private val catalog = Catalog()
    private val brands = Brands()
    private val models = mutableListOf<ViewModelStore>()
    private val row = PriceTraceBrandRow(PriceTraceBrandSelection(
        "11111111-1111-4111-8111-111111111111", "CJ",
        "22222222-2222-4222-8222-222222222222", null), "씨제이", "alias", null, null)

    @After fun cleanup() { main { models.forEach(ViewModelStore::clear) } }

    @Test fun freeTextAndFailedLookupStillSaveLocalProductWithoutCanonicalIdentity() {
        brands.reply = { throw IOException("offline") }
        val vm = open()
        main { vm.updateManualFood(validDraft("입력한 브랜드")) }
        await { state(vm).manualBrandSearch?.error != null }
        main { vm.saveManualFood(owner) }
        await { catalog.savedInput != null && !state(vm).saving }
        assertEquals("입력한 브랜드", catalog.savedInput!!.brand)
        assertNull(catalog.savedInput!!.priceTraceBrand)
        main { assertFalse(state(vm).manualFoodEntry); assertEquals(1, state(vm).foodItems.size) }
    }

    @Test fun aliasMatchRequiresSelectionAndInitialCreationStoresCanonicalSnapshot() {
        brands.reply = { listOf(row) }
        val vm = open()
        main { vm.updateManualFood(validDraft("씨제이")) }
        await { state(vm).manualBrandSearch?.rows == listOf(row) }
        main {
            assertNull(state(vm).manualFoodDraft.priceTraceBrand)
            assertEquals("씨제이", state(vm).manualFoodDraft.brand)
            vm.selectManualFoodBrand(row)
            assertEquals("CJ", state(vm).manualFoodDraft.brand)
            assertEquals(row.selection, state(vm).manualFoodDraft.priceTraceBrand)
            assertNull(state(vm).manualBrandSearch)
            vm.saveManualFood(owner)
        }
        await { catalog.savedInput != null && !state(vm).saving }
        assertEquals("CJ", catalog.savedInput!!.brand)
        assertEquals(row.selection, catalog.savedInput!!.priceTraceBrand)
    }

    @Test fun unselectedSearchHitNeverBecomesIdentityWhenSaving() {
        brands.reply = { listOf(row) }
        val vm = open()
        main { vm.updateManualFood(validDraft("씨제이")) }
        await { state(vm).manualBrandSearch?.rows?.isNotEmpty() == true }
        main { vm.saveManualFood(owner) }
        await { catalog.savedInput != null }
        assertEquals("씨제이", catalog.savedInput!!.brand)
        assertNull(catalog.savedInput!!.priceTraceBrand)
    }

    @Test fun manualBrandEditsAndLeavingProductInvalidateSelectionAndSuggestions() {
        brands.reply = { listOf(row) }
        val vm = open()
        select(vm)
        main {
            vm.updateManualFood(state(vm).manualFoodDraft.copy(brand = "CJ "))
            assertNull(state(vm).manualFoodDraft.priceTraceBrand)
            assertEquals("CJ ", state(vm).manualFoodDraft.brand)
        }
        await { state(vm).manualBrandSearch?.rows?.isNotEmpty() == true }
        main {
            vm.selectManualFoodBrand(row)
            vm.updateManualFood(state(vm).manualFoodDraft.copy(registrationKind = NutritionRegistrationKind.INGREDIENT))
            assertNull(state(vm).manualFoodDraft.priceTraceBrand)
            assertNull(state(vm).manualBrandSearch)
        }
    }

    @Test fun selectedBrandAndUnknownParentLabelSurviveSavedStateRecreationForSameOwners() {
        brands.reply = { listOf(row) }
        val handle = SavedStateHandle()
        val vm = open(handle)
        select(vm)
        lateinit var restored: MealViewModel
        main {
            models.first().clear()
            restored = create(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            restored.enter(owner, date)
            assertTrue(state(restored).manualFoodEntry)
            assertEquals(row.selection, state(restored).manualFoodDraft.priceTraceBrand)
            assertEquals("CJ", state(restored).manualFoodDraft.brand)
            assertNull(state(restored).manualFoodDraft.priceTraceBrand!!.parentCanonicalName)
        }
    }

    @Test fun legacyFifteenValueDraftRestoresWithoutBrandSelection() {
        val legacy = arrayListOf("기존 제품", "로컬 브랜드", "100", "g", "100", "10", "10", "2", "", "", "", "PRODUCT", "100", "g", "")
        val handle = SavedStateHandle(mapOf("meal.draft_owner" to owner.ownerId, "meal.draft_date" to date,
            "meal.manual_food_open" to true, "meal.editing" to true, "meal.manual_food" to legacy))
        lateinit var vm: MealViewModel
        main {
            vm = create(handle); vm.enter(owner, date)
            assertEquals("기존 제품", state(vm).manualFoodDraft.name)
            assertEquals("로컬 브랜드", state(vm).manualFoodDraft.brand)
            assertNull(state(vm).manualFoodDraft.priceTraceBrand)
        }
    }

    @Test fun differentNutritionOwnerCannotRestoreOrSavePreviousBrandSelection() {
        brands.reply = { listOf(row) }
        val vm = open()
        select(vm)
        main { catalog.owner = "other-nutrition-owner"; vm.enter(owner, date) }
        main { assertNull(state(vm).manualFoodDraft.priceTraceBrand); vm.saveManualFood(owner) }
        await { catalog.savedInput != null }
        assertNull(catalog.savedInput!!.priceTraceBrand)
    }

    @Test fun staleQueryResponseAndOldResultSelectionAreRejected() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
        brands.reply = { query ->
            if (query == "old") {
                started.countDown()
                while (release.count > 0) try { release.await() } catch (_: InterruptedException) { }
                finished.countDown(); listOf(row)
            } else emptyList()
        }
        val vm = open()
        try {
            main { vm.updateManualFood(validDraft("old")) }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            main { vm.updateManualFood(state(vm).manualFoodDraft.copy(brand = "new")) }
            await { state(vm).manualBrandSearch?.searched == true }
            release.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            main {
                assertEquals("new", state(vm).manualBrandSearch!!.query)
                assertTrue(state(vm).manualBrandSearch!!.rows.isEmpty())
                vm.selectManualFoodBrand(row); assertNull(state(vm).manualFoodDraft.priceTraceBrand)
            }
        } finally { release.countDown() }
    }

    @Test fun configAndFitnessOwnerChangesRejectOldBrandRows() {
        brands.reply = { listOf(row) }
        val vm = open()
        main { vm.updateManualFood(validDraft("씨제이")) }
        await { state(vm).manualBrandSearch?.rows?.isNotEmpty() == true }
        main { brands.changeScope(); vm.selectManualFoodBrand(row); assertNull(state(vm).manualFoodDraft.priceTraceBrand) }
        main { vm.updateManualFood(validDraft("씨제이2")) }
        await { state(vm).manualBrandSearch?.rows?.isNotEmpty() == true }
        main {
            vm.enter(AccountScope("other-fitness-owner"), date)
            vm.selectManualFoodBrand(row)
            assertNull(state(vm).manualFoodDraft.priceTraceBrand)
            assertNull(state(vm).manualBrandSearch)
        }
    }

    private fun select(vm: MealViewModel) {
        main { vm.updateManualFood(validDraft("씨제이")) }
        await { state(vm).manualBrandSearch?.rows == listOf(row) }
        main { vm.selectManualFoodBrand(row) }
    }
    private fun open(handle: SavedStateHandle = SavedStateHandle()): MealViewModel {
        lateinit var vm: MealViewModel
        main { vm = create(handle); vm.enter(owner, date); vm.chooseCategory(MealCatalogCategory.PRODUCT); vm.openManualFood() }
        return vm
    }
    private fun create(handle: SavedStateHandle): MealViewModel {
        val meals = proxy<MealRecordRepositoryApi> { null }
        val sync = proxy<NutritionCatalogSyncStore> { error("Unexpected sync") }
        val config = SupabaseConfig.empty()
        val integration = NutritionIntegrationService(catalog, sync, ProductReadV1Client(config), RestaurantMenuReadV1Client(config),
            SupabaseAuthManager(null), SupabaseAuthManager(null))
        val vm = MealViewModel(handle, meals, catalog, integration, searchDelayMillis = 0, manualBrandCatalog = brands)
        models.add(ViewModelStore().also { it.put("meal", vm) })
        return vm
    }
    private fun validDraft(brand: String) = ManualFoodDraft(name = "제품", brand = brand, calories = "100", carbs = "10",
        protein = "10", fat = "2", registrationKind = NutritionRegistrationKind.PRODUCT, packageAmount = "100")
    private fun state(vm: MealViewModel) = vm.uiState.value as MealUiState.Ready
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun await(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            var done = false; main { done = predicate() }
            if (done) return
            Thread.sleep(20)
        }
        fail("Timed out waiting for Meal Brand state")
    }
    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> call(method.name) } as T
    private class Catalog : NutritionCatalogRepositoryApi {
        @Volatile var owner = "nutrition-owner"
        @Volatile var savedInput: NutritionFoodInput? = null
        override fun currentOwnerId() = owner
        override fun searchFoods(query: String) = emptyList<NutritionFood>()
        override fun findFoodById(foodId: String): NutritionFood? = null
        override fun saveManualFoodInput(scope: AccountScope, input: NutritionFoodInput): NutritionFood {
            check(scope.ownerId == owner); savedInput = input
            return NutritionFood.builder().id("new-food").ownerId(owner).name(input.name).brand(input.brand)
                .basis(input.basisAmount, input.basisUnit).profile(input.profile).priceTraceBrand(input.priceTraceBrand).build()
        }
    }
    private class Brands : PriceTraceBrandCatalog {
        @Volatile var reply: (String) -> List<PriceTraceBrandRow> = { emptyList() }
        private var scope = 0L
        private val listeners = CopyOnWriteArrayList<Runnable>()
        override fun brandCatalogScope() = scope
        override fun isBrandCatalogConfigured() = true
        override fun searchPriceTraceBrands(query: String, limit: Int, offset: Int) = reply(query)
        override fun observeBrandCatalogScope(listener: Runnable): AutoCloseable {
            listeners.add(listener); return AutoCloseable { listeners.remove(listener) }
        }
        fun changeScope() { scope++; listeners.forEach(Runnable::run) }
    }
}
