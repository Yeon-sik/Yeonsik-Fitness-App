package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutReadApi
import com.yeonsik.fitnessapp.feature.home.api.HomeActivityReadSource
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityCoveragePolicyV1
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class HomeActivityHistoryRepositoryTest {
    @Test fun sourcesAreAggregatedOncePerRangeAndNullEarliestIsIgnored() {
        val exercise = Source(HomeActivityKind.EXERCISE, mapOf("a" to setOf("2026-09-29", "2026-09-30")))
        val weight = Source(HomeActivityKind.WEIGHT, mapOf("a" to setOf("2026-09-30")))
        val meal = Source(HomeActivityKind.MEAL, emptyMap())
        val repository = HomeActivityHistoryRepository(listOf(exercise, weight, meal))
        val scope = AccountScope("a")
        assertEquals("2026-09-29", repository.firstRecordedDate(scope))
        val records = repository.recordedKindsByDate(scope, "2026-09-30", "2026-10-01")
        assertEquals(setOf(HomeActivityKind.EXERCISE, HomeActivityKind.WEIGHT), records.getValue("2026-09-30"))
        assertFalse(records.containsKey("2026-09-29"))
        listOf(exercise, weight, meal).forEach {
            assertEquals(listOf("a"), it.earliestOwners)
            assertEquals(listOf(Triple("a", "2026-09-30", "2026-10-01")), it.ranges)
        }
    }

    @Test fun sameKindFromMultipleSourcesIsOneKindAndDoesNotChangeV1Scale() {
        val sources = (0..4).map { Source(HomeActivityKind.MEAL, mapOf("a" to setOf("2026-09-30"))) }
        val repository = HomeActivityHistoryRepository(sources)
        val kinds = repository.recordedKindsByDate(AccountScope("a"), "2026-09-30", "2026-10-01")
            .getValue("2026-09-30")
        assertEquals(setOf(HomeActivityKind.MEAL), kinds)
        assertEquals(0.33f, HomeActivityCoveragePolicyV1.alpha(kinds), 0f)
    }

    @Test fun allThreeKindsProduceFullCoverageRegardlessOfEmptyExtraSources() {
        val sources = HomeActivityKind.entries.map { Source(it, mapOf("a" to setOf("2026-09-30"))) } +
            Source(HomeActivityKind.MEAL, emptyMap())
        val kinds = HomeActivityHistoryRepository(sources)
            .recordedKindsByDate(AccountScope("a"), "2026-09-30", "2026-10-01").getValue("2026-09-30")
        assertEquals(3, kinds.size)
        assertEquals(1f, HomeActivityCoveragePolicyV1.alpha(kinds), 0f)
    }

    @Test fun scopeIsForwardedAndOwnersRemainIsolated() {
        val source = Source(HomeActivityKind.WEIGHT, mapOf("a" to setOf("2026-09-30"), "b" to setOf("2025-01-01")))
        val repository = HomeActivityHistoryRepository(listOf(source))
        assertEquals("2026-09-30", repository.firstRecordedDate(AccountScope("a")))
        assertEquals("2025-01-01", repository.firstRecordedDate(AccountScope("b")))
        assertTrue(repository.recordedKindsByDate(AccountScope("b"), "2026-09-01", "2026-10-01").isEmpty())
    }

    @Test fun emptySourceListAndOwnersWithNoRecordsAreEmpty() {
        val scope = AccountScope("a")
        listOf(HomeActivityHistoryRepository(emptyList()),
            HomeActivityHistoryRepository(listOf(Source(HomeActivityKind.EXERCISE, emptyMap())))).forEach {
            assertNull(it.firstRecordedDate(scope))
            assertTrue(it.recordedKindsByDate(scope, "2026-09-01", "2026-10-01").isEmpty())
        }
    }

    @Test fun workoutAdapterUsesOnlyWorkoutCompletedDatesForStrengthAndCardio() {
        val calls = mutableListOf<String>()
        val api = Proxy.newProxyInstance(WorkoutReadApi::class.java.classLoader, arrayOf(WorkoutReadApi::class.java)) {
            _, method, args ->
            calls += method.name
            assertEquals(AccountScope("a"), args!![0])
            when (method.name) {
                "earliestCompletedDate" -> "2026-09-30"
                "completedDates" -> listOf("2026-09-30", "2026-09-30", "2026-09-30")
                else -> error("Unexpected read ${method.name}")
            }
        } as WorkoutReadApi
        val source = WorkoutHomeActivityReadSource(api)
        assertEquals(HomeActivityKind.EXERCISE, source.kind)
        assertEquals("2026-09-30", source.firstRecordedDate(AccountScope("a")))
        assertEquals(setOf("2026-09-30"), source.recordedDates(AccountScope("a"), "2026-09-01", "2026-10-01"))
        assertEquals(listOf("earliestCompletedDate", "completedDates"), calls)
    }

    private class Source(
        override val kind: HomeActivityKind,
        private val byOwner: Map<String, Set<String>>
    ) : HomeActivityReadSource {
        val earliestOwners = mutableListOf<String>()
        val ranges = mutableListOf<Triple<String, String, String>>()
        override fun firstRecordedDate(scope: AccountScope): String? {
            earliestOwners += scope.ownerId
            return byOwner[scope.ownerId].orEmpty().minOrNull()
        }
        override fun recordedDates(scope: AccountScope, startDate: String, endDate: String): Set<String> {
            ranges += Triple(scope.ownerId, startDate, endDate)
            return byOwner[scope.ownerId].orEmpty().filter { it in startDate..endDate }.toSet()
        }
    }
}
