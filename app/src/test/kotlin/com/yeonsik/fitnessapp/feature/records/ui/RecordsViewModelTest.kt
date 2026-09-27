package com.yeonsik.fitnessapp.feature.records.ui

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.records.api.RecordsReadApi
import com.yeonsik.fitness.shared.feature.records.model.RecordsSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class RecordsViewModelTest {
    private val liveDataExecutor = object : TaskExecutor() {
        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
        override fun postToMainThread(runnable: Runnable) = runnable.run()
        override fun isMainThread() = true
    }

    @Before fun setUp() = ArchTaskExecutor.getInstance().setDelegate(liveDataExecutor)
    @After fun tearDown() = ArchTaskExecutor.getInstance().setDelegate(null)

    @Test
    fun selectionWithinMonthKeepsSnapshotWhileMonthAndStaleReload() {
        val repository = CountingRecordsApi()
        val executor = QueuedExecutor()
        val viewModel = RecordsViewModel(SavedStateHandle(), repository, executor)
        val scope = AccountScope("owner-a")

        viewModel.enterIfNeeded(scope, "2026-09-27", "2026-09-20")
        executor.runNext()
        assertEquals(1, repository.calls.size)
        val ready = viewModel.uiState.value

        viewModel.rememberSelectedDate(scope, "2026-09-21")
        viewModel.enterIfNeeded(scope, "2026-09-27", "2026-09-21")
        assertEquals(1, repository.calls.size)
        assertTrue(viewModel.uiState.value === ready)

        viewModel.nextMonth(scope, "2026-09-27", "2026-09-21")
        assertEquals("2026-10", (viewModel.uiState.value as RecordsUiState.Loading).displayedMonth)
        executor.runNext()
        assertEquals(2, repository.calls.size)

        viewModel.markStale()
        viewModel.enterIfNeeded(scope, "2026-09-27", "2026-09-21")
        executor.runNext()
        assertEquals(3, repository.calls.size)
    }

    @Test
    fun olderMonthResultCannotReplaceLatestMonth() {
        val repository = CountingRecordsApi()
        val executor = QueuedExecutor()
        val scope = AccountScope("owner-a")
        val viewModel = RecordsViewModel(SavedStateHandle(), repository, executor)

        viewModel.enterIfNeeded(scope, "2026-09-27", "2026-09-20")
        viewModel.nextMonth(scope, "2026-09-27", "2026-09-20")
        executor.runLast()
        executor.runNext()

        assertEquals("2026-10", (viewModel.uiState.value as RecordsUiState.Ready).snapshot.displayedMonth)
        assertEquals(listOf("2026-10", "2026-09"), repository.calls)
    }
}

private class CountingRecordsApi : RecordsReadApi {
    val calls = mutableListOf<String>()
    override fun load(scope: AccountScope, displayedMonth: String, today: String): RecordsSnapshot {
        calls += displayedMonth
        return RecordsSnapshot(scope.ownerId, displayedMonth, today, emptyList(), emptyMap(), emptyList())
    }
}

private class QueuedExecutor : AbstractExecutorService() {
    private val tasks = mutableListOf<Runnable>()
    private var stopped = false
    override fun execute(command: Runnable) { tasks += command }
    fun runNext() = tasks.removeAt(0).run()
    fun runLast() = tasks.removeAt(tasks.lastIndex).run()
    override fun shutdown() { stopped = true }
    override fun shutdownNow(): MutableList<Runnable> {
        stopped = true
        return tasks.toMutableList().also { tasks.clear() }
    }
    override fun isShutdown() = stopped
    override fun isTerminated() = stopped && tasks.isEmpty()
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
}
