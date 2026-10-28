package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.api.*
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodInput
import com.yeonsik.fitness.shared.core.account.AccountScope
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class NutritionEditorControllerTest {
    @Test fun compositionDraftRestoresFoodAmountsNameAndMemo() {
        val catalog = Catalog()
        val executor = QueueExecutor()
        val handle = SavedStateHandle()
        lateinit var original: NutritionEditorController
        main { original = NutritionEditorController(handle, catalog, Templates(), executor); original.open() }
        executor.drain()
        main { original.newComposition() }
        executor.drain()
        main {
            original.addFood(catalog.food); original.updateAmount(catalog.food.id, "175")
            original.updateName("운동 후 한 끼"); original.updateMemo("미리 준비"); original.updateFavorite(true); original.close()
        }
        lateinit var restored: NutritionEditorController
        main {
            val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            restored = NutritionEditorController(restoredHandle, catalog, Templates(), executor)
            restored.open()
        }
        executor.drain()
        main {
            val state = restored.uiState.value!!
            assertEquals(NutritionEditorMode.COMPOSITION, state.mode)
            assertEquals("운동 후 한 끼", state.name)
            assertEquals("미리 준비", state.memo)
            assertEquals("175", state.portions.single().quantity)
            assertTrue(state.favorite)
            assertFalse(state.loading)
        }
    }

    @Test fun savingCompositionCompletesWithTheNutritionOwner() {
        val catalog = Catalog()
        val templates = Templates()
        val executor = QueueExecutor()
        lateinit var editor: NutritionEditorController
        main { editor = NutritionEditorController(SavedStateHandle(), catalog, templates, executor); editor.open() }
        executor.drain()
        main { editor.newComposition() }
        executor.drain()
        main { editor.addFood(catalog.food); editor.updateName("기본 도시락"); editor.save() }
        main { assertTrue(editor.uiState.value!!.saving) }
        executor.drain()
        main {
            assertFalse(editor.uiState.value!!.saving)
            assertEquals(NutritionEditorMode.LIBRARY, editor.uiState.value!!.mode)
            assertEquals(1, templates.rows.size)
            assertEquals("nutrition-owner", templates.rows.single().userId)
        }
    }

    @Test fun changingNutritionOwnerDiscardsStaleDraftAndPendingResults() {
        val catalog = Catalog()
        val executor = QueueExecutor()
        lateinit var editor: NutritionEditorController
        main {
            editor = NutritionEditorController(SavedStateHandle(), catalog, Templates(), executor)
            editor.open(); editor.newFood(); editor.updateField(FoodEntryField.NAME, "이전 계정 식품")
            catalog.owner = "nutrition-owner-b"; editor.synchronizeOwner()
        }
        executor.drain()
        main {
            assertEquals("nutrition-owner-b", editor.uiState.value!!.ownerId)
            assertEquals("", editor.uiState.value!!.field(FoodEntryField.NAME))
            assertFalse(editor.uiState.value!!.open)
        }
    }

    @Test fun foodEditorValidatesTotalsAndRestoresOptionalTransFatBeforeRegistration() {
        val catalog = Catalog()
        val executor = QueueExecutor()
        val handle = SavedStateHandle()
        lateinit var editor: NutritionEditorController
        main { editor = NutritionEditorController(handle, catalog, Templates(), executor); editor.open() }
        executor.drain()
        main {
            editor.newFood(); editor.updateField(FoodEntryField.NAME, "라벨 식품")
            editor.updatePackageAmount("100")
            mapOf(FoodEntryField.CALORIES to "600", FoodEntryField.PROTEIN to "20",
                FoodEntryField.CARBS to "43", FoodEntryField.FAT to "28", FoodEntryField.SUGARS to "50",
                FoodEntryField.SATURATED_FAT to "9", FoodEntryField.TRANS_FAT to "0.4").forEach { (field, value) ->
                editor.updateField(field, value)
            }
            editor.save()
            assertEquals("당류는 탄수화물 총량을 초과할 수 없습니다.", editor.uiState.value!!.error)
            assertFalse(editor.uiState.value!!.saving)
            assertNull(catalog.savedInput)
            editor.updateField(FoodEntryField.SUGARS, "8")
            editor = NutritionEditorController(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }),
                catalog, Templates(), executor)
            editor.open()
        }
        executor.drain()
        main { assertEquals("0.4", editor.uiState.value!!.field(FoodEntryField.TRANS_FAT)); editor.save() }
        executor.drain()
        val profile = catalog.savedInput!!.profile
        assertEquals(43.0, profile.carbsGrams(), 0.0)
        assertEquals(28.0, profile.fatGrams(), 0.0)
        assertEquals(0.4, profile.transFatGrams()!!, 0.0)
        assertNull(profile.sodiumMg())
    }

    private fun main(work: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(work)

    @Test fun brandSelectionRestoresCanonicalIdentityAndAllowsBothRegistrationKinds() {
        val catalog = Catalog(); val local = QueueExecutor(); val network = QueueExecutor(); val handle = SavedStateHandle()
        val brand = BrandReadV1("81111111-1111-4111-8111-111111111111", "농심", "Nongshim")
        val lookup = NutritionBrandLookup { listOf(brand) }
        lateinit var editor: NutritionEditorController
        main { editor = NutritionEditorController(handle, catalog, Templates(), local, brandLookup = lookup, brandExecutor = network); editor.open() }
        local.drain()
        main {
            editor.newFood(); validFields().forEach(editor::updateField)
            editor.updateField(FoodEntryField.BRAND, "Nongshim"); editor.searchRegistrationBrands()
        }
        network.drain()
        main {
            assertNull(editor.uiState.value!!.priceTraceBrand)
            editor.selectRegistrationBrand(brand)
            assertEquals("농심", editor.uiState.value!!.field(FoodEntryField.BRAND))
            editor.updateField(FoodEntryField.NAME, "직접 수정한 이름")
            editor.updateRegistrationKind(com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRegistrationKind.INGREDIENT)
            assertEquals(brand.brandId, editor.uiState.value!!.priceTraceBrand!!.brandId)
            editor.dispose()
        }
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val nextNetwork = QueueExecutor()
        main { editor = NutritionEditorController(restored, catalog, Templates(), local, brandLookup = lookup, brandExecutor = nextNetwork); editor.open() }
        local.drain()
        main {
            assertEquals(brand.brandId, editor.uiState.value!!.priceTraceBrand!!.brandId)
            assertEquals("직접 수정한 이름", editor.uiState.value!!.field(FoodEntryField.NAME))
            editor.clearRegistrationBrand()
            assertNull(editor.uiState.value!!.priceTraceBrand)
            assertEquals("농심", editor.uiState.value!!.field(FoodEntryField.BRAND))
            editor.searchRegistrationBrands()
        }
        nextNetwork.drain()
        main { editor.selectRegistrationBrand(brand); editor.save() }
        local.drain()
        assertEquals(brand.brandId, catalog.savedInput!!.priceTraceBrandId)
        assertEquals("농심", catalog.savedInput!!.brand)
        main { editor.dispose() }
    }

    @Test fun brandSearchVersionAndOwnerGuardsRejectStaleResultsAndSelections() {
        val catalog = Catalog(); val local = QueueExecutor(); val network = QueueExecutor()
        val first = BrandReadV1("81111111-1111-4111-8111-111111111111", "첫 브랜드", null)
        val second = BrandReadV1("82222222-2222-4222-8222-222222222222", "다음 브랜드", null)
        lateinit var editor: NutritionEditorController
        main { editor = NutritionEditorController(SavedStateHandle(), catalog, Templates(), local,
            brandLookup = NutritionBrandLookup { if (it == "A") listOf(first) else listOf(second) }, brandExecutor = network); editor.open() }
        local.drain()
        main { editor.newFood(); editor.updateField(FoodEntryField.BRAND, "A"); editor.searchRegistrationBrands()
            editor.updateField(FoodEntryField.BRAND, "B"); editor.searchRegistrationBrands() }
        network.drain()
        main {
            assertEquals(listOf(second), editor.uiState.value!!.registrationBrandSearch.results)
            editor.selectRegistrationBrand(first); assertNull(editor.uiState.value!!.priceTraceBrand)
            catalog.owner = "another"; editor.synchronizeOwner()
            editor.selectRegistrationBrand(second); assertNull(editor.uiState.value!!.priceTraceBrand)
            assertFalse(editor.uiState.value!!.open)
            assertTrue(editor.uiState.value!!.registrationBrandSearch.results.isEmpty())
            editor.dispose()
        }
    }

    @Test fun localSaveDoesNotWaitForBrandNetworkAndOfflineTextIsPreserved() {
        val catalog = Catalog(); val local = QueueExecutor(); val network = QueueExecutor()
        lateinit var editor: NutritionEditorController
        main { editor = NutritionEditorController(SavedStateHandle(), catalog, Templates(), local,
            brandLookup = NutritionBrandLookup { throw java.io.IOException("offline") }, brandExecutor = network); editor.open() }
        local.drain()
        main {
            editor.newFood(); validFields().forEach(editor::updateField)
            editor.updateField(FoodEntryField.BRAND, "직접 브랜드"); editor.updatePackageAmount("100")
            editor.searchRegistrationBrands()
            assertTrue(editor.uiState.value!!.registrationBrandSearch.loading)
            editor.save()
        }
        local.drain()
        assertEquals("직접 브랜드", catalog.savedInput!!.brand)
        assertNull(catalog.savedInput!!.priceTraceBrandId)
        network.drain()
        main {
            editor.newFood(); editor.updateField(FoodEntryField.BRAND, "다른 브랜드"); editor.searchRegistrationBrands()
        }
        network.drain()
        main {
            assertNotNull(editor.uiState.value!!.registrationBrandSearch.error)
            assertNull(editor.uiState.value!!.error)
            validFields().forEach(editor::updateField); editor.updatePackageAmount("100"); editor.save()
        }
        local.drain()
        assertEquals("다른 브랜드", catalog.savedInput!!.brand)
        assertNull(catalog.savedInput!!.priceTraceBrandId)
        main { editor.dispose() }
    }

    private fun validFields() = mapOf(FoodEntryField.NAME to "이름", FoodEntryField.BASIS to "100",
        FoodEntryField.CALORIES to "400", FoodEntryField.PROTEIN to "20", FoodEntryField.CARBS to "30", FoodEntryField.FAT to "20")

    private class Catalog : NutritionCatalogRepositoryApi {
        var owner = "nutrition-owner"
        var savedInput: NutritionFoodInput? = null
        var linkedProduct: ProductReadV1? = null
        var failLinked = false
        val food = NutritionFood.builder().id("apple").ownerId(owner).name("사과").basis(100.0, "g")
            .profile(NutritionProfile.ofMacros(52.0, 0.3, 14.0, 0.2)).build()
        override fun currentOwnerId() = owner
        override fun searchFoods(query: String) = listOf(food)
        override fun findFoodById(foodId: String) = food.takeIf { it.id == foodId }
        override fun saveManualFoodInput(scope: AccountScope, input: NutritionFoodInput): NutritionFood {
            savedInput = input
            return food
        }
        override fun savePriceTraceProductInput(scope: AccountScope, input: NutritionFoodInput, product: ProductReadV1): NutritionFood? {
            linkedProduct = product
            assertEquals(owner, scope.ownerId)
            return if (failLinked) null else food
        }
    }

    private class Templates : NutritionTemplateRepositoryApi {
        val rows = mutableListOf<CompositionTemplate>()
        override fun currentOwnerId() = "nutrition-owner"
        override fun findTemplate(templateId: String) = rows.firstOrNull { it.id == templateId }
        override fun listTemplates(kind: String?) = rows.toList()
        override fun saveTemplate(template: CompositionTemplate): String { rows += template; return template.id }
        override fun deleteTemplate(templateId: String) { rows.removeAll { it.id == templateId } }
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
