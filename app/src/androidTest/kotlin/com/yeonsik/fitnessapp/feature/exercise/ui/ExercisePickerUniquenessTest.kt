package com.yeonsik.fitnessapp.feature.exercise.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.routine.api.RoutineRepositoryApi
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionExercise
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset
import com.yeonsik.fitnessapp.feature.exercise.api.ExerciseMasterRepositoryApi
import com.yeonsik.fitnessapp.state.FitnessScreen
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Catalog and repository fixtures only; never opens personal workout data. */
class ExercisePickerUniquenessTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val catalog get() = ExerciseFamilyCatalog.load(instrumentation.targetContext)
    private val incline get() = requireNotNull(catalog.runtimeCatalog().preset("chest_smith_incline_bench_press"))
    private val flat get() = requireNotNull(catalog.runtimeCatalog().preset("chest_smith_flat_bench_press"))

    @Test fun sameFamilyVariantsStayInTheCartUntilExplicitConfirmation() {
        Fixture(emptyList()).use { fixture ->
            enter(fixture)
            waitReady(fixture)
            onMain {
                fixture.model.selectFamily(flat.familyId)
                assertNull((fixture.model.uiState.value as ExercisePickerUiState.Ready).selectedPresetId)
                fixture.model.choose(flat)
                fixture.model.choose(incline)
                fixture.model.choose(flat)
                fixture.model.search("펙덱")
                assertEquals(2, (fixture.model.uiState.value as ExercisePickerUiState.Ready).pendingPresets.size)
                assertEquals(0, fixture.writes.get())
                fixture.model.confirmPendingSelection()
            }
            await { onMain { fixture.model.uiState.value is ExercisePickerUiState.Saved } }
            assertEquals(2, fixture.writes.get())
        }
    }

    @Test fun routineCartRestoresForItsTargetAndDoesNotLeakToAnotherOwner() {
        val handle = SavedStateHandle()
        Fixture(emptyList(), handle = handle).use { first ->
            onMain { first.model.enter(AccountScope("owner"), FitnessScreen.ROUTINE_ADD, null, null, "routine") }
            waitReady(first)
            onMain { first.model.choose(flat); first.model.choose(incline); first.model.removePendingPreset(flat.presetId) }
            assertEquals(0, first.writes.get())
            Fixture(emptyList(), handle = handle).use { restored ->
                onMain { restored.model.enter(AccountScope("owner"), FitnessScreen.ROUTINE_ADD, null, null, "routine") }
                assertEquals(listOf(incline.presetId), waitReady(restored).pendingPresets.map { it.presetId })
                onMain { restored.model.enter(AccountScope("other-owner"), FitnessScreen.ROUTINE_ADD, null, null, "routine") }
                assertTrue(waitReady(restored).pendingPresets.isEmpty())
                onMain { restored.model.choose(flat); restored.model.confirmPendingSelection() }
                await { onMain { restored.model.uiState.value is ExercisePickerUiState.Saved } }
                assertEquals(1, restored.writes.get())
            }
        }
    }

    @Test fun partialSaveKeepsOnlyUnwrittenItemsForRetry() {
        Fixture(emptyList(), failWriteAt = 2).use { fixture ->
            enter(fixture)
            waitReady(fixture)
            onMain { fixture.model.choose(flat); fixture.model.choose(incline); fixture.model.confirmPendingSelection() }
            val failed = waitReady(fixture) { it.selectionError != null }
            assertEquals(listOf(incline.presetId), failed.pendingPresets.map { it.presetId })
            assertFalse(failed.isSaving)
            onMain { fixture.model.confirmPendingSelection() }
            await { onMain { fixture.model.uiState.value is ExercisePickerUiState.Saved } }
            assertEquals(3, fixture.writes.get())
        }
    }

    @Test fun loadedExistingVariantCannotBeSelectedOrWrittenWhileOtherVariantsRemainAvailable() {
        Fixture(listOf(incline)).use { fixture ->
            enter(fixture)
            val ready = waitReady(fixture)
            assertTrue(ready.isAlreadyAdded(incline))
            assertFalse(ready.isAlreadyAdded(flat))
            onMain {
                fixture.model.selectPreset(incline.familyId, incline.presetId)
                fixture.model.choose(incline)
            }
            instrumentation.waitForIdleSync()
            onMain { assertNull((fixture.model.uiState.value as ExercisePickerUiState.Ready).selectedPresetId) }
            assertEquals(0, fixture.writes.get())
        }
    }

    @Test fun repeatedTapAndTapAfterSavedDispatchOnlyOneWrite() {
        Fixture(listOf(incline), blockWrite = true).use { fixture ->
            enter(fixture)
            waitReady(fixture)
            onMain {
                fixture.model.choose(flat)
                fixture.model.choose(flat)
                assertEquals(1, (fixture.model.uiState.value as ExercisePickerUiState.Ready).pendingPresets.size)
                assertEquals(0, fixture.writes.get())
                fixture.model.confirmPendingSelection()
                fixture.model.confirmPendingSelection()
                assertTrue((fixture.model.uiState.value as ExercisePickerUiState.Ready).isSaving)
            }
            assertTrue(fixture.writeStarted.await(3, TimeUnit.SECONDS))
            assertEquals(1, fixture.writes.get())
            fixture.releaseWrite.countDown()
            await { onMain { fixture.model.uiState.value is ExercisePickerUiState.Saved } }
            onMain { fixture.model.choose(flat); fixture.model.confirmPendingSelection() }
            assertEquals(1, fixture.writes.get())
        }
    }

    @Test fun staleSelectionRefreshesAlreadyAddedStateAndKeepsThePickerUsable() {
        Fixture(listOf(incline), saveSucceeds = false).use { fixture ->
            enter(fixture)
            waitReady(fixture)
            fixture.existing.set(listOf(incline, flat))
            onMain { fixture.model.choose(flat); fixture.model.confirmPendingSelection() }
            val ready = waitReady(fixture) { it.selectionError != null }
            assertTrue(ready.selectionError!!.contains("추가하지 못했습니다"))
            assertTrue(ready.isAlreadyAdded(flat))
            assertFalse(ready.isSaving)
            assertNull(ready.selectedPresetId)
            assertEquals(1, fixture.writes.get())
        }
    }

    @Test fun replacementExcludesItsOwnOccurrenceButBlocksOtherExistingVariants() {
        Fixture(listOf(incline, flat)).use { fixture ->
            enter(fixture, "existing-1")
            val ready = waitReady(fixture)
            assertTrue(ready.isAlreadyAdded(incline))
            assertFalse(ready.isAlreadyAdded(flat))
        }
    }

    private fun enter(fixture: Fixture, replacementId: String? = null) = onMain {
        fixture.model.enter(AccountScope("owner"), FitnessScreen.WORKOUT_EXERCISE_ADD,
            "record", replacementId, null)
    }

    private fun waitReady(fixture: Fixture, predicate: (ExercisePickerUiState.Ready) -> Boolean = { true }): ExercisePickerUiState.Ready {
        var result: ExercisePickerUiState.Ready? = null
        await {
            result = onMain { fixture.model.uiState.value as? ExercisePickerUiState.Ready }
            result?.let(predicate) == true
        }
        return requireNotNull(result)
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            Thread.sleep(10)
        }
        throw AssertionError("Picker did not reach the expected state")
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private inner class Fixture(
        initial: List<RuntimeExercisePreset>,
        private val blockWrite: Boolean = false,
        private val saveSucceeds: Boolean = true,
        private val failWriteAt: Int? = null,
        val handle: SavedStateHandle = SavedStateHandle()
    ) : AutoCloseable {
        val existing = AtomicReference(initial)
        val writes = AtomicInteger()
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        private val executor = Executors.newSingleThreadExecutor()
        private val repository = Proxy.newProxyInstance(WorkoutRepositoryApi::class.java.classLoader,
            arrayOf(WorkoutRepositoryApi::class.java)) { proxy, method, args ->
            when (method.name) {
                "lastPerformedAtByCanonicalPreset" -> emptyMap<String, String>()
                "loadSession" -> WorkoutSessionSnapshot("record", "운동", "in_progress",
                    "2026-10-08T12:00:00+09:00", 0, 0.0, 0,
                    existing.get().mapIndexed { index, preset ->
                        WorkoutSessionExercise("existing-$index", preset.storageExerciseId, index,
                            preset.displayName(), preset.defaultUiPart, preset.equipmentNameKo,
                            preset.recordType, "중량 · 반복", catalog.identityForPreset(preset), 0, 0)
                    }, emptyList())
                "addExercise" -> {
                    val count = writes.incrementAndGet()
                    writeStarted.countDown()
                    if (blockWrite) check(releaseWrite.await(5, TimeUnit.SECONDS))
                    saveSucceeds && count != failWriteAt
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Picker uniqueness fixture"
                else -> throw AssertionError("Unexpected workout call: ${method.name}")
            }
        } as WorkoutRepositoryApi
        private val routineRepository = Proxy.newProxyInstance(RoutineRepositoryApi::class.java.classLoader,
            arrayOf(RoutineRepositoryApi::class.java)) { _, method, _ ->
            when (method.name) {
                "addExercise" -> { writes.incrementAndGet(); true }
                "activeRoutineId" -> "routine"
                else -> throw AssertionError("Unexpected routine call: ${method.name}")
            }
        } as RoutineRepositoryApi
        private val masterRepository = object : ExerciseMasterRepositoryApi {
            override fun runtimeCatalog() = catalog.runtimeCatalog()
        }
        val model = onMain { ExercisePickerViewModel(handle, masterRepository,
            routineRepository, repository, executor) }
        override fun close() {
            releaseWrite.countDown()
            executor.shutdownNow()
        }
    }
}
