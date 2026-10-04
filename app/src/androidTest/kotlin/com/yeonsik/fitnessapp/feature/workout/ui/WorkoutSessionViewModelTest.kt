package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitness.shared.feature.workout.application.CompleteWorkout
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
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

        onMain {
            viewModel.uiState.observeForever(stateObserver)
            viewModel.terminalEvents.observeForever(terminalObserver)
        }
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

        onMain { viewModel.uiState.observeForever(stateObserver) }
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

    @Test
    fun cancellingAStartedWorkoutWithCompletedSetsDeletesWithoutCompletingIt() {
        val repository = BlockingSessionRepository(
            CountDownLatch(0), CountDownLatch(0), "in_progress", completedSetCount = 2
        )
        val executor = Executors.newSingleThreadExecutor()
        val viewModel = viewModel(repository, executor)
        try {
            onMain { viewModel.cancel(scope, "draft-with-sets") }
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            onMain { }
            assertEquals(1, repository.deleteCalls)
            assertEquals(0, repository.completeCalls)
            assertEquals(scope.ownerId, repository.deletedOwnerId)
            assertEquals("draft-with-sets", repository.deletedRecordId)
            assertEquals(WorkoutSessionTerminalOutcome.CANCELLED, viewModel.terminalEvents.value?.outcome)
            assertTrue(viewModel.uiState.value is WorkoutSessionUiState.Cancelled)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun failedCancellationPreservesTheInputScreenAndPublishesFailure() {
        val repository = BlockingSessionRepository(
            CountDownLatch(0), CountDownLatch(0), "in_progress", deleteSucceeds = false
        )
        val executor = Executors.newSingleThreadExecutor()
        val viewModel = viewModel(repository, executor)
        val loaded = CountDownLatch(1)
        val observer = Observer<WorkoutSessionUiState> {
            if (it is WorkoutSessionUiState.Ready) loaded.countDown()
        }
        onMain { viewModel.uiState.observeForever(observer); viewModel.enter(scope, "draft") }
        try {
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
            onMain { viewModel.cancel(scope, "draft") }
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            onMain { }
            assertTrue(viewModel.uiState.value is WorkoutSessionUiState.Ready)
            assertEquals(WorkoutSessionTerminalOutcome.FAILURE, viewModel.terminalEvents.value?.outcome)
            assertEquals(0, repository.completeCalls)
        } finally {
            onMain { viewModel.uiState.removeObserver(observer) }
            executor.shutdownNow()
        }
    }

    @Test
    fun completedWorkoutCannotBeRemovedByTheCancelAction() {
        val repository = BlockingSessionRepository(CountDownLatch(0), CountDownLatch(0))
        val executor = Executors.newSingleThreadExecutor()
        val viewModel = viewModel(repository, executor)
        try {
            onMain { viewModel.cancel(scope, "completed-record") }
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            onMain { }
            assertEquals(0, repository.deleteCalls)
            assertEquals(WorkoutSessionTerminalOutcome.FAILURE, viewModel.terminalEvents.value?.outcome)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun cancelConfirmationCanBeDismissedAndRejectsADifferentOwner() {
        val repository = BlockingSessionRepository(CountDownLatch(0), CountDownLatch(0), "in_progress")
        val executor = Executors.newSingleThreadExecutor()
        val viewModel = viewModel(repository, executor)
        try {
            onMain {
                viewModel.openCancelConfirmation(scope, "draft")
                viewModel.confirmDelete(AccountScope("another-owner"))
                val prompt = viewModel.deleteConfirmationState.value as WorkoutDeleteConfirmationUiState.Ready
                assertTrue(prompt.cancelWorkout)
                viewModel.dismissDeleteConfirmation()
                viewModel.confirmDelete(scope)
            }
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertEquals(0, repository.deleteCalls)
            assertEquals(WorkoutDeleteConfirmationUiState.Idle, viewModel.deleteConfirmationState.value)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun cancellationConfirmationRestoresItsIntentAfterRecreation() {
        val repository = BlockingSessionRepository(CountDownLatch(0), CountDownLatch(0), "in_progress")
        val executor = Executors.newSingleThreadExecutor()
        val handle = SavedStateHandle()
        val first = viewModel(repository, executor, handle)
        try {
            onMain { first.openCancelConfirmation(scope, "draft") }
            val restored = viewModel(repository, executor, handle)
            onMain {
                val prompt = restored.deleteConfirmationState.value as WorkoutDeleteConfirmationUiState.Ready
                assertTrue(prompt.cancelWorkout)
                restored.confirmDelete(scope)
            }
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            onMain { }
            assertEquals(WorkoutSessionTerminalOutcome.CANCELLED, restored.terminalEvents.value?.outcome)
            assertEquals(1, repository.deleteCalls)
            assertEquals(0, repository.completeCalls)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun repeatedCancelAndFinishWhileCancellingCannotCompleteOrDeleteTwice() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val repository = BlockingSessionRepository(started, release, "in_progress")
        val executor = Executors.newSingleThreadExecutor()
        val viewModel = viewModel(repository, executor)
        try {
            onMain { viewModel.cancel(scope, "draft") }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            onMain {
                viewModel.cancel(scope, "draft")
                viewModel.finish(scope, "draft")
            }
            release.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            onMain { }
            assertEquals(1, repository.deleteCalls)
            assertEquals(0, repository.completeCalls)
            assertEquals(WorkoutSessionTerminalOutcome.CANCELLED, viewModel.terminalEvents.value?.outcome)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    private fun viewModel(
        repository: WorkoutRepositoryApi,
        executor: ExecutorService,
        handle: SavedStateHandle = SavedStateHandle()
    ) = WorkoutSessionViewModel(
        handle,
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
        private val releaseFirst: CountDownLatch,
        private val sessionStatus: String = "completed",
        private val completedSetCount: Int = 0,
        private val deleteSucceeds: Boolean = true
    ) : WorkoutRepositoryApi {
        private var calls = 0
        var deleteCalls = 0
        var completeCalls = 0
        var deletedOwnerId: String? = null
        var deletedRecordId: String? = null

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
                status = sessionStatus,
                startedAt = "",
                durationSeconds = 0,
                totalVolumeKg = 0.0,
                completedSetCount = completedSetCount,
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

        override fun completeIfEligible(scope: AccountScope, recordId: String): WorkoutCompletion {
            completeCalls += 1
            return WorkoutCompletion.NO_COMPLETED_SETS
        }

        override fun deleteSession(scope: AccountScope, recordId: String): Boolean {
            deleteCalls += 1
            deletedOwnerId = scope.ownerId
            deletedRecordId = recordId
            return deleteSucceeds
        }

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
