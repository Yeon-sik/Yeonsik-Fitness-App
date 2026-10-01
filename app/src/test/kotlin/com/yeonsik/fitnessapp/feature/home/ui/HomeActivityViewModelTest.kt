package com.yeonsik.fitnessapp.feature.home.ui

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.api.HomeActivityHistoryApi
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class HomeActivityViewModelTest {
    @Before fun setUp() = ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
        override fun postToMainThread(runnable: Runnable) = runnable.run()
        override fun isMainThread() = true
    })
    @After fun tearDown() = ArchTaskExecutor.getInstance().setDelegate(null)

    @Test fun pagesLoadIndependentlyAndEachWindowIsReadOnlyOnce() {
        val f = Fixture()
        f.enter()
        val mainReady = f.vm.uiState.value
        assertEquals(1, f.home.calls)
        assertEquals(listOf("a"), f.activity.earliestOwners)
        assertEquals(listOf(Triple("a", "2026-07-06", TODAY)), f.activity.ranges)
        f.vm.previousActivityPage()
        f.worker.runAll()
        assertEquals(1, f.ready().identity.pageOffset)
        assertEquals(Triple("a", "2026-04-06", "2026-07-05"), f.activity.ranges.last())
        f.vm.nextActivityPage()
        assertEquals(0, f.ready().identity.pageOffset)
        f.vm.previousActivityPage()
        assertEquals(1, f.ready().identity.pageOffset)
        assertEquals(2, f.activity.ranges.size)
        assertEquals(1, f.activity.earliestOwners.size)
        assertEquals(1, f.home.calls)
        assertSame(mainReady, f.vm.uiState.value)
        assertEquals(0, f.worker.pending)
    }

    @Test fun rangeFailureLeavesMainHomeReadyAndRetryOnlyReloadsActivity() {
        val f = Fixture()
        f.enter()
        val ready = f.vm.uiState.value
        f.activity.failRange = true
        f.vm.previousActivityPage()
        f.worker.runAll()
        assertTrue(f.vm.activityState.value is HomeActivityUiState.Error)
        assertSame(ready, f.vm.uiState.value)
        f.activity.failRange = false
        f.vm.retryActivityHistory()
        f.worker.runAll()
        assertEquals(1, f.ready().identity.pageOffset)
        assertEquals(1, f.home.calls)
        assertEquals(1, f.activity.earliestOwners.size)
    }

    @Test fun pagingAndRetryRetainTheLastReadyPageUntilTheReadCompletes() {
        val f = Fixture()
        f.enter()
        val previous = f.ready()
        f.activity.failRange = true
        f.vm.previousActivityPage()
        val loading = f.vm.activityState.value as HomeActivityUiState.Loading
        assertEquals(1, loading.identity.pageOffset)
        assertSame(previous, loading.previous)
        f.worker.runAll()
        assertSame(previous, (f.vm.activityState.value as HomeActivityUiState.Error).previous)
        f.activity.failRange = false
        f.vm.retryActivityHistory()
        assertSame(previous, (f.vm.activityState.value as HomeActivityUiState.Loading).previous)
        f.worker.runAll()
        assertEquals(1, f.ready().identity.pageOffset)
    }

    @Test fun retainedPageIsClearedWhenOwnerOrTodayChanges() {
        val f = Fixture()
        f.enter()
        f.vm.enterIfNeeded(AccountScope("b"), TODAY)
        assertNull((f.vm.activityState.value as HomeActivityUiState.Loading).previous)
        f.worker.runAll()
        f.vm.enterIfNeeded(AccountScope("b"), "2026-10-02")
        assertNull((f.vm.activityState.value as HomeActivityUiState.Loading).previous)
    }

    @Test fun initialRangeFailureCachesSuccessfulEarliestReadForRetry() {
        val f = Fixture()
        f.activity.failRange = true
        f.enter()
        assertTrue(f.vm.uiState.value is HomeUiState.Ready)
        assertTrue(f.vm.activityState.value is HomeActivityUiState.Error)
        f.activity.failRange = false
        f.vm.retryActivityHistory()
        f.worker.runAll()
        assertEquals(1, f.activity.earliestOwners.size)
        assertEquals(91, f.ready().cells.size)
    }

    @Test fun earliestFailureIsSectionLocalAndCanRetry() {
        val f = Fixture()
        f.activity.failEarliest = true
        f.enter()
        assertTrue(f.vm.uiState.value is HomeUiState.Ready)
        assertTrue(f.vm.activityState.value is HomeActivityUiState.Error)
        assertTrue(f.activity.ranges.isEmpty())
        f.activity.failEarliest = false
        f.vm.retryActivityHistory()
        f.worker.runAll()
        assertEquals(91, f.ready().cells.size)
        assertEquals(1, f.home.calls)
    }

    @Test fun olderPageResultCannotOverwriteNewPage() {
        val f = Fixture()
        f.enter()
        f.vm.selectActivityPage(1)
        f.vm.selectActivityPage(2)
        f.worker.runAt(1)
        assertEquals(2, f.ready().identity.pageOffset)
        val ready = f.vm.activityState.value
        f.worker.runAt(0)
        assertSame(ready, f.vm.activityState.value)
        f.vm.selectActivityPage(0)
        assertEquals(0, f.ready().identity.pageOffset)
    }

    @Test fun oldOwnerAndFailedOldOwnerResultsCannotOverwriteNewOwner() {
        val f = Fixture()
        f.vm.enterIfNeeded(AccountScope("a"), TODAY)
        f.vm.enterIfNeeded(AccountScope("b"), TODAY)
        f.worker.runAt(2)
        f.worker.runAt(2)
        assertEquals("b", f.ready().identity.ownerId)
        val ready = f.vm.activityState.value
        f.activity.failRange = true
        f.worker.runAll()
        assertSame(ready, f.vm.activityState.value)
        assertEquals("b", (f.vm.uiState.value as HomeUiState.Ready).snapshot.ownerId)
    }

    @Test fun publicationAlreadyQueuedOnMainThreadIsAlsoGuarded() {
        val main = QueueExecutor()
        val f = Fixture(main)
        f.vm.enterIfNeeded(AccountScope("a"), TODAY)
        f.worker.runAll()
        f.vm.enterIfNeeded(AccountScope("b"), TODAY)
        main.runAll()
        assertTrue(f.vm.uiState.value is HomeUiState.Loading)
        assertEquals("b", f.vm.activityState.value?.identity?.ownerId)
        assertTrue(f.vm.activityState.value is HomeActivityUiState.Loading)
        f.worker.runAll()
        main.runAll()
        assertEquals("b", f.ready().identity.ownerId)
    }

    @Test fun changedTodayRejectsOldResultAndReusesOwnerEarliestDate() {
        val f = Fixture()
        f.enter()
        f.vm.selectActivityPage(1)
        f.vm.enterIfNeeded(AccountScope("a"), "2026-10-02")
        f.worker.runAt(1)
        f.worker.runAt(1)
        val ready = f.vm.activityState.value
        assertEquals("2026-10-02", f.ready().identity.today)
        assertEquals(0, f.ready().identity.pageOffset)
        f.worker.runAll()
        assertSame(ready, f.vm.activityState.value)
        assertEquals(1, f.activity.earliestOwners.size)
    }

    @Test fun markStaleClearsWindowAndEarliestCachesAndReloadsEditedPastRecords() {
        val f = Fixture()
        f.enter()
        f.vm.selectActivityPage(1)
        f.worker.runAll()
        f.activity.firstByOwner["a"] = "2025-01-01"
        f.vm.markStale()
        assertEquals(HomeActivityUiState.Idle, f.vm.activityState.value)
        f.vm.enterIfNeeded(AccountScope("a"), TODAY)
        f.worker.runAll()
        assertEquals(2, f.activity.earliestOwners.size)
        assertEquals("2025-01-01", f.ready().window.firstRecordedDate.toString())
        f.vm.selectActivityPage(1)
        f.worker.runAll()
        assertEquals(4, f.activity.ranges.size)
        assertEquals(2, f.home.calls)
    }

    @Test fun markStaleRejectsInFlightAndAlreadyQueuedResults() {
        val main = QueueExecutor()
        val f = Fixture(main)
        f.vm.enterIfNeeded(AccountScope("a"), TODAY)
        f.worker.runAll()
        f.vm.markStale()
        main.runAll()
        assertEquals(HomeActivityUiState.Idle, f.vm.activityState.value)
        f.vm.enterIfNeeded(AccountScope("a"), TODAY)
        f.worker.runAll()
        main.runAll()
        assertEquals(2, f.activity.earliestOwners.size)
        assertEquals(0, f.ready().identity.pageOffset)
    }

    @Test fun ownerSwitchInvalidatesPreviouslyCachedPages() {
        val f = Fixture()
        f.enter()
        f.vm.enterIfNeeded(AccountScope("b"), TODAY)
        f.worker.runAll()
        f.vm.enterIfNeeded(AccountScope("a"), TODAY)
        f.worker.runAll()
        assertEquals(listOf("a", "b", "a"), f.activity.earliestOwners)
        assertEquals(3, f.activity.ranges.size)
        assertEquals("a", f.ready().identity.ownerId)
    }

    @Test fun selectedTrackedDayLoadsOwnerScopedDetailsAndRejectsOlderSelection() {
        val f = Fixture()
        f.enter()
        val firstDate = "2026-09-29"
        val secondDate = "2026-09-30"
        f.activity.detailsByDate[secondDate] = listOf(
            com.yeonsik.fitnessapp.feature.home.model.HomeActivityRecordSummary(
                HomeActivityKind.EXERCISE, name = "상체 운동"
            )
        )

        f.vm.selectActivityDay(firstDate)
        f.vm.selectActivityDay(secondDate)
        assertEquals(2, f.worker.pending)
        assertEquals(HomeActivityDayDetailsUiState.Loading("a", secondDate), f.vm.activityDayDetails.value)
        f.worker.runAt(0)
        assertEquals(HomeActivityDayDetailsUiState.Loading("a", secondDate), f.vm.activityDayDetails.value)
        f.worker.runAt(0)
        assertEquals(
            HomeActivityDayDetailsUiState.Ready(
                "a",
                com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails(
                    secondDate, f.activity.detailsByDate.getValue(secondDate)
                )
            ),
            f.vm.activityDayDetails.value
        )
        f.vm.selectActivityDay(secondDate)
        assertEquals(2, f.activity.detailDates.size)
        f.vm.selectActivityDay("2025-12-31") // before this account's first tracked date
        assertEquals(0, f.worker.pending)
    }

    @Test fun noRecordsShowsEmptyWithoutRangeReadOrRepeatedEarliestQuery() {
        val f = Fixture()
        f.activity.firstByOwner["a"] = null
        f.enter()
        assertTrue(f.vm.activityState.value is HomeActivityUiState.Empty)
        assertTrue(f.activity.ranges.isEmpty())
        f.vm.enterIfNeeded(AccountScope("a"), TODAY)
        f.worker.runAll()
        assertEquals(1, f.activity.earliestOwners.size)
        assertEquals(1, f.home.calls)
    }

    @Test fun navigationBoundsCannotTriggerReadsOutsideHistory() {
        val f = Fixture()
        f.activity.firstByOwner["a"] = "2026-09-30"
        f.enter()
        f.vm.previousActivityPage()
        f.vm.nextActivityPage()
        f.vm.selectActivityPage(50)
        assertEquals(1, f.activity.ranges.size)
        assertEquals(0, f.worker.pending)
    }

    private class Fixture(main: Executor = Executor { it.run() }) {
        val home = CountingHomeApi()
        val activity = CountingActivityApi()
        val worker = QueueExecutor()
        val vm = HomeViewModel(SavedStateHandle(), home, activity, worker, main)
        fun enter() {
            vm.enterIfNeeded(AccountScope("a"), TODAY)
            worker.runAll()
        }
        fun ready() = vm.activityState.value as HomeActivityUiState.Ready
    }

    private class CountingActivityApi : HomeActivityHistoryApi {
        val firstByOwner = mutableMapOf<String, String?>("a" to "2026-01-01", "b" to "2026-02-01")
        val earliestOwners = mutableListOf<String>()
        val ranges = mutableListOf<Triple<String, String, String>>()
        val detailDates = mutableListOf<String>()
        val detailsByDate = mutableMapOf<String, List<com.yeonsik.fitnessapp.feature.home.model.HomeActivityRecordSummary>>()
        var failRange = false
        var failEarliest = false
        override fun firstRecordedDate(scope: AccountScope): String? {
            earliestOwners += scope.ownerId
            if (failEarliest) error("earliest failed")
            return firstByOwner[scope.ownerId]
        }
        override fun recordedKindsByDate(scope: AccountScope, startDate: String, endDate: String): Map<String, Set<HomeActivityKind>> {
            ranges += Triple(scope.ownerId, startDate, endDate)
            if (failRange) error("range failed")
            return mapOf(endDate to setOf(HomeActivityKind.EXERCISE))
        }
        override fun detailsForDate(
            scope: AccountScope,
            date: String
        ) = com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails(
            date,
            detailsByDate[date].orEmpty().also { detailDates += date }
        )
    }

    private class CountingHomeApi : HomeRepositoryApi {
        var calls = 0
        override fun load(scope: AccountScope, today: String): HomeSnapshot {
            calls++
            return HomeSnapshot(scope.ownerId, today, emptyList(), null, emptyList(), emptyMap(), emptyMap(),
                null, emptyMap(), emptyMap(), emptyMap(), null, null, emptyList(), emptyList())
        }
    }

    private class QueueExecutor : AbstractExecutorService() {
        private val tasks = mutableListOf<Runnable>()
        private var stopped = false
        val pending: Int get() = tasks.size
        override fun execute(command: Runnable) { tasks += command }
        fun runAt(index: Int) = tasks.removeAt(index).run()
        fun runAll() { while (tasks.isNotEmpty()) runAt(0) }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { stopped = true; return tasks.toMutableList().also { tasks.clear() } }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }

    private companion object { const val TODAY = "2026-10-01" }
}
