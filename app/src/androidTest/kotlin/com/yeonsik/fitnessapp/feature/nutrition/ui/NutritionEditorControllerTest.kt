package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.api.*
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

    private fun main(work: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(work)

    private class Catalog : NutritionCatalogRepositoryApi {
        var owner = "nutrition-owner"
        val food = NutritionFood.builder().id("apple").ownerId(owner).name("사과").basis(100.0, "g")
            .profile(NutritionProfile.ofMacros(52.0, 0.3, 14.0, 0.2)).build()
        override fun currentOwnerId() = owner
        override fun searchFoods(query: String) = listOf(food)
        override fun findFoodById(foodId: String) = food.takeIf { it.id == foodId }
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
