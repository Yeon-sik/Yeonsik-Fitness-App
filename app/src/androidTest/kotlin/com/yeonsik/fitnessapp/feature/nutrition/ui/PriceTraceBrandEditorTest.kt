package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.api.*
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import com.yeonsik.fitness.shared.core.account.AccountScope
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.*

class PriceTraceBrandEditorTest {
    private val first = PriceTraceBrandRow(PriceTraceBrandSelection("11111111-1111-4111-8111-111111111111", "브랜드 A", null, null), "별칭 A", "alias", null, null)
    private val second = PriceTraceBrandRow(PriceTraceBrandSelection("22222222-2222-4222-8222-222222222222", "브랜드 B", first.selection.brandId, "브랜드 A"), "브랜드 B", "canonical", null, null)
    private val local = Catalog()
    private val brands = Brands()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var editor: NutritionEditorController
    private fun open(handle: SavedStateHandle = SavedStateHandle()) {
        main { editor = NutritionEditorController(handle, local, null, executor, brands); editor.open() }
        await { !editor.uiState.value!!.loading }
    }
    @After fun cleanup() { if (::editor.isInitialized) main { editor.dispose() }; executor.shutdownNow() }

    @Test fun productSelectionSuspendsManualBrandAndRestoresItAfterRecreationAndUnlink() {
        val handle = SavedStateHandle()
        val product = ProductReadV1("81111111-1111-4111-8111-111111111111",
            "82222222-2222-4222-8222-222222222222", "PT 제품", "PT 브랜드", "제조회사", "서브브랜드",
            null, null, null, 100.0, "g", 1, "sha256:" + "a".repeat(64))
        val lookup = object : NutritionProductLookup { override fun search(query: String) = listOf(product) }
        brands.reply = { listOf(first) }
        main {
            editor = NutritionEditorController(handle, local, null, executor, brands, productLookup = lookup)
            editor.open()
        }
        await { !editor.uiState.value!!.loading }
        main { editor.newFood(); editor.updateField(FoodEntryField.BRAND, "직접 브랜드"); editor.openBrandSearch() }
        await { editor.uiState.value!!.brandSearch?.rows?.isNotEmpty() == true }
        main { editor.selectBrand(first); editor.updateProductQuery("PT"); editor.searchProducts() }
        await { !editor.uiState.value!!.productPicker.loading }
        main { editor.selectProduct(product) }
        await { !editor.uiState.value!!.productPicker.loading }
        main {
            assertNull(editor.uiState.value!!.priceTraceBrand)
            editor.openBrandSearch()
            assertNull(editor.uiState.value!!.brandSearch)
            editor.dispose()
            editor = NutritionEditorController(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }),
                local, null, executor, brands, productLookup = lookup)
            editor.open()
        }
        await { !editor.uiState.value!!.loading }
        main {
            assertNull(editor.uiState.value!!.priceTraceBrand)
            assertEquals(product.catalogProductId, editor.uiState.value!!.productPicker.selected!!.catalogProductId)
            editor.clearProductSelection()
            assertEquals(first.selection, editor.uiState.value!!.priceTraceBrand)
            assertEquals("직접 브랜드", editor.uiState.value!!.field(FoodEntryField.BRAND))
        }
    }

    @Test fun newProductRequiresExplicitSelectionPreservesLabelAndInvalidatesMaterialLabelEditsAndKindChanges() {
        brands.reply = { listOf(first, second) }
        open()
        main { editor.newFood(); editor.updateField(FoodEntryField.BRAND, "라벨 이름"); editor.openBrandSearch() }
        await { editor.uiState.value!!.brandSearch?.rows?.size == 2 }
        main {
            assertNull(editor.uiState.value!!.priceTraceBrand)
            assertEquals("라벨 이름", editor.uiState.value!!.brandSearch!!.query)
            editor.selectBrand(second)
            assertEquals(second.selection, editor.uiState.value!!.priceTraceBrand)
            assertEquals("라벨 이름", editor.uiState.value!!.field(FoodEntryField.BRAND))
            editor.updateField(FoodEntryField.BRAND, " 라벨 이름 ")
            assertEquals(second.selection, editor.uiState.value!!.priceTraceBrand)
            editor.updateField(FoodEntryField.BRAND, "다른 라벨")
            assertNull(editor.uiState.value!!.priceTraceBrand)
            editor.openBrandSearch()
        }
        await { editor.uiState.value!!.brandSearch?.rows?.isNotEmpty() == true }
        main {
            editor.selectBrand(first); editor.updateRegistrationKind(NutritionRegistrationKind.INGREDIENT)
            assertNull(editor.uiState.value!!.priceTraceBrand)
            editor.openBrandSearch(); assertNull(editor.uiState.value!!.brandSearch)
        }
    }

    @Test fun registrationDraftRestoresBrandAndSavesOnlyExplicitSelectionWhileOfflineStillRegisters() {
        brands.reply = { listOf(first) }
        val handle = SavedStateHandle(); open(handle)
        main { editor.newFood(); editor.updateField(FoodEntryField.BRAND, "로컬 브랜드"); editor.openBrandSearch() }
        await { editor.uiState.value!!.brandSearch?.rows?.isNotEmpty() == true }
        main {
            editor.selectBrand(first); editor.dispose()
            editor = NutritionEditorController(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }), local, null, executor, brands)
            editor.open()
        }
        await { !editor.uiState.value!!.loading }
        main {
            assertEquals(first.selection, editor.uiState.value!!.priceTraceBrand)
            editor.updateField(FoodEntryField.NAME, "제품")
            editor.updateField(FoodEntryField.CALORIES, "100"); editor.updateField(FoodEntryField.PROTEIN, "10")
            editor.updateField(FoodEntryField.CARBS, "10"); editor.updateField(FoodEntryField.FAT, "2")
            editor.updatePackageAmount("100"); editor.save()
        }
        await { !editor.uiState.value!!.saving }
        assertEquals(first.selection, local.savedInput!!.priceTraceBrand)
        assertEquals("로컬 브랜드", local.savedInput!!.brand)
        brands.reply = { throw IOException("연결 실패") }
        main { editor.newFood(); editor.openBrandSearch() }
        await { editor.uiState.value!!.brandSearch?.error != null }
        main {
            editor.closeBrandSearch()
            assertNull(editor.uiState.value!!.priceTraceBrand)
            assertFalse(editor.uiState.value!!.loading)
            assertNull(editor.uiState.value!!.error)
        }
    }

    @Test fun existingFoodUsesMostSpecificSeedAndExplicitChangeClearPreservesLocalFactsWhenOffline() {
        local.food = NutritionFood.builder().id("saved-product").ownerId("owner").name("제품").brand("라벨")
            .brandName("상위 라벨").subBrandName("하위 라벨").priceTraceBrand(first.selection).build()
        brands.reply = { listOf(second) }; open()
        main { editor.editFoodBrand(local.food.id) }
        await { editor.uiState.value!!.brandSearch?.rows?.isNotEmpty() == true }
        main { assertEquals("하위 라벨", editor.uiState.value!!.brandSearch!!.query); editor.selectBrand(second) }
        await { !editor.uiState.value!!.saving }
        assertEquals(second.selection, local.food.priceTraceBrand)
        assertEquals("라벨", local.food.brand)
        brands.reply = { throw IOException("offline") }
        main { editor.editFoodBrand(local.food.id) }
        await { editor.uiState.value!!.brandSearch?.error != null }
        main { assertEquals(second.selection, editor.uiState.value!!.editingBrandFood!!.priceTraceBrand); editor.clearBrand() }
        await { !editor.uiState.value!!.saving }
        assertNull(local.food.priceTraceBrand)
        assertEquals(2, local.mutations)
    }

    @Test fun olderNonCancellableSearchCannotReplaceNewerResultsAndConfigChangeRejectsStaleSelection() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val finished = CountDownLatch(1)
        brands.reply = { query ->
            if (query == "A") {
                started.countDown()
                while (release.count > 0) try { release.await() } catch (_: InterruptedException) { /* model uncancellable I/O */ }
                finished.countDown(); listOf(first)
            } else listOf(second)
        }
        try {
            open()
            main { editor.newFood(); editor.updateField(FoodEntryField.BRAND, "A"); editor.openBrandSearch() }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            main { editor.searchBrands("B") }
            await { editor.uiState.value!!.brandSearch?.rows == listOf(second) }
            release.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            main {
                assertEquals(listOf(second), editor.uiState.value!!.brandSearch!!.rows)
                brands.changeScope()
            }
            await { editor.uiState.value!!.brandSearch?.error != null }
            main { editor.selectBrand(second); assertNull(editor.uiState.value!!.priceTraceBrand) }
            main { local.owner = "another"; editor.synchronizeOwner() }
            main { assertNull(editor.uiState.value!!.brandSearch); assertFalse(editor.uiState.value!!.open) }
        } finally { release.countDown() }
    }

    @Test fun closingExistingEditorCannotMakeDraftClearMutateThePreviousFood() {
        brands.reply = { listOf(first) }; open()
        main { editor.editFoodBrand(local.food.id) }
        await { editor.uiState.value!!.brandSearch?.rows?.isNotEmpty() == true }
        main { editor.close(); editor.open() }
        await { !editor.uiState.value!!.loading }
        main { editor.newFood(); editor.clearBrand() }
        assertEquals(0, local.mutations)
        main { assertNull(editor.uiState.value!!.editingBrandFood) }
    }

    @Test fun ownerChangeDuringOutstandingSearchDiscardsLateResponse() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        brands.reply = {
            started.countDown()
            while (release.count > 0) try { release.await() } catch (_: InterruptedException) { }
            listOf(first)
        }
        try {
            open()
            main { editor.newFood(); editor.openBrandSearch() }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            main { local.owner = "another"; editor.synchronizeOwner() }
            release.countDown()
            main { assertEquals("another", editor.uiState.value!!.ownerId); assertNull(editor.uiState.value!!.brandSearch) }
        } finally { release.countDown() }
    }

    private class Catalog : NutritionCatalogRepositoryApi {
        @Volatile var owner = "owner"
        @Volatile var food = NutritionFood.builder().id("food").ownerId("owner").name("제품").build()
        @Volatile var savedInput: NutritionFoodInput? = null
        var mutations = 0
        override fun currentOwnerId() = owner
        override fun searchFoods(query: String) = listOf(food)
        override fun findFoodById(foodId: String) = food.takeIf { it.id == foodId }
        override fun searchPackagedFoods(query: String, limit: Int) = listOf(food)
        override fun canEditPriceTraceBrand(foodId: String, ownerId: String) = ownerId == owner && ownerId == food.ownerId
        override fun setPriceTraceBrand(foodId: String, ownerId: String, selection: PriceTraceBrandSelection?): NutritionFood {
            check(canEditPriceTraceBrand(foodId, ownerId)); mutations++
            food = NutritionFood.builder().id(food.id).ownerId(food.ownerId).name(food.name).brand(food.brand)
                .brandName(food.brandName).subBrandName(food.subBrandName).priceTraceBrand(selection).build()
            return food
        }
        override fun saveManualFoodInput(scope: AccountScope, input: NutritionFoodInput): NutritionFood {
            check(scope.ownerId == owner); savedInput = input
            return NutritionFood.builder().id("new").ownerId(owner).name(input.name).brand(input.brand).priceTraceBrand(input.priceTraceBrand).build()
        }
    }
    private class Brands : PriceTraceBrandCatalog {
        @Volatile var reply: (String) -> List<PriceTraceBrandRow> = { emptyList() }
        private var scope = 0L
        private val listeners = CopyOnWriteArrayList<Runnable>()
        override fun brandCatalogScope() = scope
        override fun isBrandCatalogConfigured() = true
        override fun searchPriceTraceBrands(query: String, limit: Int, offset: Int) = reply(query)
        override fun observeBrandCatalogScope(listener: Runnable): AutoCloseable { listeners.add(listener); return AutoCloseable { listeners.remove(listener) } }
        fun changeScope() { scope++; listeners.forEach(Runnable::run) }
    }
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun await(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            var done = false; main { done = predicate() }
            if (done) return
            Thread.sleep(20)
        }
        fail("Timed out waiting for editor state")
    }
}
