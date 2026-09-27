package com.yeonsik.fitnessapp.feature.records.ui

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.records.api.RecordsReadApi
import com.yeonsik.fitness.shared.feature.records.model.RecordsCalendarDay
import com.yeonsik.fitness.shared.feature.records.model.RecordsDayDetail
import com.yeonsik.fitness.shared.feature.records.model.RecordsSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.YearMonth

class RecordsViewModelTest {
    private val liveDataExecutor = object : TaskExecutor() {
        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
        override fun postToMainThread(runnable: Runnable) = runnable.run()
        override fun isMainThread() = true
    }

    @Before fun setUp() = ArchTaskExecutor.getInstance().setDelegate(liveDataExecutor)
    @After fun tearDown() = ArchTaskExecutor.getInstance().setDelegate(null)

    @Test
    fun selectionWithinMonthKeepsSnapshotWithoutReload() {
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
    }

    @Test
    fun monthMoveUpdatesSelectedDateAndLoadsOneMatchingSnapshot() {
        val repository = CountingRecordsApi()
        val executor = QueuedExecutor()
        val viewModel = RecordsViewModel(SavedStateHandle(), repository, executor)
        val scope = AccountScope("owner-a")
        viewModel.enterIfNeeded(scope, "2026-09-27", "2026-09-20")
        executor.runNext()
        assertEquals(1, repository.calls.size)

        val navigationTarget = viewModel.nextMonth(scope, "2026-09-27", "2026-09-20")
        assertEquals("2026-10-20", navigationTarget)
        assertEquals("2026-10", (viewModel.uiState.value as RecordsUiState.Loading).displayedMonth)

        // Mirrors the navigation LaunchedEffect after selectRecordsDate(targetDate).
        viewModel.enterIfNeeded(scope, "2026-09-27", navigationTarget)
        assertEquals(1, repository.calls.size)
        executor.runNext()

        val ready = (viewModel.uiState.value as RecordsUiState.Ready).snapshot
        assertEquals(2, repository.calls.size)
        assertEquals(YearMonth.from(LocalDate.parse(navigationTarget)).toString(), ready.displayedMonth)
        assertTrue(navigationTarget in ready.dayDetailsByDate)
        assertEquals(navigationTarget, ready.dayDetailsByDate.getValue(navigationTarget).date)
    }

    @Test
    fun monthMovesClampToTargetMonthIncludingLeapYears() {
        assertEquals("2026-10-20", shiftRecordsDate("2026-09-20", 1))
        assertEquals("2026-09-20", shiftRecordsDate("2026-10-20", -1))
        assertEquals("2026-02-28", shiftRecordsDate("2026-03-31", -1))
        assertEquals("2024-02-29", shiftRecordsDate("2024-01-31", 1))
        assertEquals("2024-02-29", shiftRecordsDate("2024-03-31", -1))
        assertEquals(null, shiftRecordsDate("not-a-date", 1))
    }

    @Test
    fun staleSnapshotReloadsOnce() {
        val repository = CountingRecordsApi()
        val executor = QueuedExecutor()
        val viewModel = RecordsViewModel(SavedStateHandle(), repository, executor)
        val scope = AccountScope("owner-a")
        viewModel.enterIfNeeded(scope, "2026-09-27", "2026-09-20")
        executor.runNext()
        viewModel.markStale()
        viewModel.enterIfNeeded(scope, "2026-09-27", "2026-09-21")
        executor.runNext()
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun olderMonthResultCannotReplaceLatestMonth() {
        val repository = CountingRecordsApi()
        val executor = QueuedExecutor()
        val scope = AccountScope("owner-a")
        val viewModel = RecordsViewModel(SavedStateHandle(), repository, executor)

        viewModel.enterIfNeeded(scope, "2026-09-27", "2026-09-20")
        val navigationTarget = viewModel.nextMonth(scope, "2026-09-27", "2026-09-20")
        assertEquals("2026-10-20", navigationTarget)
        executor.runLast()
        executor.runNext()

        assertEquals("2026-10", (viewModel.uiState.value as RecordsUiState.Ready).snapshot.displayedMonth)
        assertEquals(listOf("2026-10", "2026-09"), repository.calls)
        assertTrue(navigationTarget in (viewModel.uiState.value as RecordsUiState.Ready).snapshot.dayDetailsByDate)
    }
}

private class CountingRecordsApi : RecordsReadApi {
    val calls = mutableListOf<String>()
    override fun load(scope: AccountScope, displayedMonth: String, today: String): RecordsSnapshot {
        calls += displayedMonth
        val month = YearMonth.parse(displayedMonth)
        val first = month.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val dates = (0 until 42).map { start.plusDays(it.toLong()).toString() }
        return RecordsSnapshot(
            scope.ownerId,
            displayedMonth,
            today,
            dates.map { RecordsCalendarDay(it, false, false, false) },
            dates.associateWith { RecordsDayDetail(it, emptyList(), emptyList(), emptyList()) },
            emptyList()
        )
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
