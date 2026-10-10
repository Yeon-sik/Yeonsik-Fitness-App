package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitness.shared.feature.workout.application.InitializeWorkoutExercise
import com.yeonsik.fitness.shared.feature.workout.model.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Repository fixture only. Never opens the user's database. */
class WorkoutDetailRefreshTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val exercise = WorkoutExercise("exercise", "squat", 0, "스쿼트", "legs", "바벨", "WEIGHT_REPS", null)
    private fun set(id: String, index: Int) = WorkoutSet(id, index, 20.0, 10, 2, null,
        false, 0, 0.0, 0.0, 0.0, LoadState.EXTERNAL_LOAD, 20.0, MassUnit.KG)
    private val original = WorkoutExerciseDetail("record", exercise, listOf(exercise),
        listOf(set("first", 1), set("second", 2)),
        allowedLoadStates = mapOf(exercise.id to listOf(LoadState.EXTERNAL_LOAD)))

    @Test fun sameOccurrenceRefreshKeepsReadyAndReusesOnlyUnchangedRows() {
        val fixture = Fixture()
        try {
            onMain { fixture.model.enter(AccountScope("owner"), "record", "exercise") }
            waitReady(fixture.model, "owner", 2)
            val before = onMain { fixture.model.uiState.value as WorkoutExerciseDetailUiState.Ready }
            fixture.next.set(original.copy(sets = listOf(
                original.sets[0].copy(), original.sets[1].copy(weightKg = 25.0), set("third", 3))))
            onMain { fixture.model.enter(AccountScope("owner"), "record", "exercise") }
            assertTrue(fixture.readStarted.await(3, TimeUnit.SECONDS))
            onMain { assertSame(before, fixture.model.uiState.value) }
            fixture.releaseRead.countDown()
            val after = waitReady(fixture.model, "owner", 3)
            assertSame(before.detail.activeExercise, after.detail.activeExercise)
            assertSame(before.detail.exercises, after.detail.exercises)
            assertSame(before.detail.allowedLoadStates, after.detail.allowedLoadStates)
            assertSame(before.detail.sets[0], after.detail.sets[0])
            assertNotSame(before.detail.sets[1], after.detail.sets[1])
            assertEquals(25.0, after.detail.sets[1].weightKg, 0.0)
        } finally { fixture.close() }
    }

    @Test fun changingOwnerDropsThePreviousOwnersReadyContentImmediately() {
        val fixture = Fixture()
        try {
            onMain { fixture.model.enter(AccountScope("owner"), "record", "exercise") }
            waitReady(fixture.model, "owner", 2)
            onMain { fixture.model.enter(AccountScope("other-owner"), "record", "exercise") }
            assertTrue(fixture.readStarted.await(3, TimeUnit.SECONDS))
            onMain { assertSame(WorkoutExerciseDetailUiState.Loading, fixture.model.uiState.value) }
            fixture.releaseRead.countDown()
            waitReady(fixture.model, "other-owner", 2)
        } finally { fixture.close() }
    }

    private fun waitReady(model: WorkoutExerciseDetailViewModel, owner: String, count: Int): WorkoutExerciseDetailUiState.Ready {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            instrumentation.waitForIdleSync()
            val ready = onMain { model.uiState.value as? WorkoutExerciseDetailUiState.Ready }
            if (ready?.ownerId == owner && ready.detail.sets.size == count) return ready
            Thread.sleep(10)
        }
        throw AssertionError("Detail did not become ready for $owner with $count rows")
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private inner class Fixture {
        val readStarted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val next = AtomicReference(original)
        private val reads = AtomicInteger()
        private val executor = Executors.newSingleThreadExecutor()
        private val repository = Proxy.newProxyInstance(WorkoutRepositoryApi::class.java.classLoader,
            arrayOf(WorkoutRepositoryApi::class.java)) { proxy, method, args ->
            when (method.name) {
                "loadExerciseDetail" -> {
                    if (reads.incrementAndGet() > 1) {
                        readStarted.countDown()
                        check(releaseRead.await(5, TimeUnit.SECONDS))
                    }
                    next.get()
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Workout detail refresh fixture"
                else -> throw AssertionError("Unexpected repository call: ${method.name}")
            }
        } as WorkoutRepositoryApi
        val model = onMain { WorkoutExerciseDetailViewModel(SavedStateHandle(), repository,
            InitializeWorkoutExercise(repository), executor, false) }
        fun close() {
            releaseRead.countDown()
            executor.shutdownNow()
        }
    }
}
