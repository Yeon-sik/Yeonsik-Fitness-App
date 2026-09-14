package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.application.CompleteWorkout
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WorkoutSessionViewModelTest {
    private val scope = AccountScope("view-model-owner")

    @Test
    fun nullTargetInvalidatesInFlightReadAndTerminalEvent() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val repository = BlockingSessionRepository(firstStarted, releaseFirst)
        val executor = Executors.newSingleThreadExecutor()
        val viewModel = viewModel(repository, executor)
        val states = Collections.synchronizedList(mutableListOf<WorkoutSessionUiState>())
        val terminalEvents = Collections.synchronizedList(mutableListOf<WorkoutSessionTerminalEvent>())
        val stateObserver = Observer<WorkoutSessionUiState> { state -> state?.let { states += it } }
        val terminalObserver = Observer<WorkoutSessionTerminalEvent> { event -> event?.let { terminalEvents += it } }

        viewModel.uiState.observeForever(stateObserver)
        viewModel.terminalEvents.observeForever(terminalObserver)
        try {
            onMain { viewModel.enter(scope, "old-record") }
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

            onMain { viewModel.enter(scope, null) }
            releaseFirst.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            onMain { }

            assertEquals(WorkoutSessionUiState.Idle, viewModel.uiState.value)
            assertFalse(states.any { it is WorkoutSessionUiState.Ready })
            assertFalse(states.any { it is WorkoutSessionUiState.Missing })
            assertFalse(states.any { it is WorkoutSessionUiState.Error })
            assertTrue(terminalEvents.isEmpty())
        } finally {
            onMain {
                viewModel.uiState.removeObserver(stateObserver)
                viewModel.terminalEvents.removeObserver(terminalObserver)
            }
            executor.shutdownNow()
        }
    }

    @Test
    fun changingRecordTargetPreventsPreviousAsyncResultFromPublishing() {
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val repository = BlockingSessionRepository(firstStarted, releaseFirst)
        val executor = Executors.newSingleThreadExecutor()
        val viewModel = viewModel(repository, executor)
        val states = Collections.synchronizedList(mutableListOf<WorkoutSessionUiState>())
        val stateObserver = Observer<WorkoutSessionUiState> { state -> state?.let { states += it } }

        viewModel.uiState.observeForever(stateObserver)
        try {
            onMain { viewModel.enter(scope, "old-record") }
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            onMain { viewModel.enter(scope, "new-record") }
            releaseFirst.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            onMain { }

            val readyRecordIds = states
                .filterIsInstance<WorkoutSessionUiState.Ready>()
                .map { it.session.recordId }
            assertFalse(readyRecordIds.contains("old-record"))
            assertTrue(readyRecordIds.contains("new-record"))
        } finally {
            onMain { viewModel.uiState.removeObserver(stateObserver) }
            executor.shutdownNow()
        }
    }

    private fun viewModel(
        repository: WorkoutRepositoryApi,
        executor: ExecutorService
    ) = WorkoutSessionViewModel(
        SavedStateHandle(),
        repository,
        CompleteWorkout(repository),
        executor = executor,
        shutdownExecutorOnCleared = false
    )

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { action() }
    }

    private class BlockingSessionRepository(
        private val firstStarted: CountDownLatch,
        private val releaseFirst: CountDownLatch
    ) : WorkoutRepositoryApi {
        private var calls = 0

        override fun loadSession(
            scope: AccountScope,
            recordId: String
        ): WorkoutSessionSnapshot {
            val first = synchronized(this) {
                val value = calls == 0
                calls += 1
                value
            }
            if (first) {
                firstStarted.countDown()
                check(releaseFirst.await(5, TimeUnit.SECONDS)) { "Timed out releasing first read" }
            }
            return WorkoutSessionSnapshot(
                recordId = recordId,
                title = recordId,
                status = "completed",
                startedAt = "",
                durationSeconds = 0,
                totalVolumeKg = 0.0,
                completedSetCount = 0,
                exercises = emptyList(),
                recentVolumes = emptyList()
            )
        }

        override fun loadExerciseDetail(
            scope: AccountScope,
            recordId: String,
            activeExerciseId: String?
        ): WorkoutExerciseDetail? = null

        override fun ensureInitialSet(scope: AccountScope, recordId: String, exerciseId: String) = false

        override fun completeIfEligible(scope: AccountScope, recordId: String) =
            WorkoutCompletion.NO_COMPLETED_SETS

        override fun discard(scope: AccountScope, recordId: String) = Unit

        override fun updateTypedSet(
            scope: AccountScope,
            recordId: String,
            setId: String,
            input: WorkoutSetInput
        ) = false

        override fun addTypedSet(
            scope: AccountScope,
            recordId: String,
            exerciseId: String,
            setIndex: Int,
            input: WorkoutSetInput
        ) = false

        override fun deleteSet(scope: AccountScope, recordId: String, setId: String) = false

        override fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String) = false

        override fun addExercise(
            scope: AccountScope,
            recordId: String,
            exercise: WorkoutExerciseReplacement
        ) = false

        override fun replaceExercise(
            scope: AccountScope,
            recordId: String,
            exerciseId: String,
            replacement: WorkoutExerciseReplacement
        ) = false
    }
}
