package com.yeonsik.fitness.shared.feature.body.application

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsRepositoryApi
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry
import com.yeonsik.fitness.shared.feature.body.model.BodyWeightWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BodyMetricsApplicationServiceTest {
    @Test
    fun sharedEditorPreservesAddEditDeleteAndDateLookup() {
        val repository = FakeBodyRepository()
        val service = BodyMetricsApplicationService(repository, "owner-a")
        val scope = AccountScope("owner-a")

        val recordId = service.save(scope, null, "2026-09-20", 70.5, "before breakfast")
        assertEquals(recordId, service.load(scope, "2026-09-20", null).recordId)
        assertEquals(70.5, service.load(scope, "2026-09-20", null).weightKg)

        service.save(scope, recordId, "2026-09-21", 70.25, "after breakfast")
        val edited = service.load(scope, "2026-09-21", recordId)
        assertEquals(recordId, edited.recordId)
        assertEquals(70.25, edited.weightKg)
        assertEquals("after breakfast", edited.memo)

        service.delete(scope, recordId)
        assertNull(service.load(scope, "2026-09-21", recordId).recordId)
    }

    @Test
    fun serviceRejectsAStaleOrDifferentOwnerScope() {
        val repository = FakeBodyRepository()
        val service = BodyMetricsApplicationService(repository, "owner-a")
        val ownerB = AccountScope("owner-b")

        assertFailsWith<IllegalStateException> {
            service.load(ownerB, "2026-09-20", null)
        }

        val id = repository.addBodyMetric(ownerB, "2026-09-20", 80.0, "owner b")
        assertEquals("owner b", repository.bodyMetricEntryById(ownerB, id)?.memo)
        assertNull(repository.bodyMetricForDate(AccountScope("owner-a"), "2026-09-20"))
    }

    private class FakeBodyRepository : BodyMetricsRepositoryApi {
        private val entries = mutableMapOf<String, MutableList<BodyReadEntry>>()
        private val profiles = mutableMapOf<String, BodyProfile>()
        private var nextId = 1

        override fun addBodyMetric(
            scope: AccountScope,
            date: String,
            weightKg: Double,
            memo: String
        ): String {
            val ownerEntries = entries.getOrPut(scope.ownerId) { mutableListOf() }
            val existing = ownerEntries.firstOrNull { it.date == date }
            if (existing != null) {
                ownerEntries[ownerEntries.indexOf(existing)] = existing.copy(weightKg = weightKg, memo = memo)
                return existing.id
            }
            val id = "test-${nextId++}"
            ownerEntries += BodyReadEntry(id, date, weightKg, memo)
            return id
        }

        override fun bodyMetricEntryById(scope: AccountScope, id: String): BodyReadEntry? =
            entries[scope.ownerId]?.firstOrNull { it.id == id }

        override fun bodyMetricEntriesForDate(
            scope: AccountScope,
            date: String?
        ): List<BodyReadEntry> = entries[scope.ownerId].orEmpty().filter { date == null || it.date == date }

        override fun updateBodyMetric(
            scope: AccountScope,
            id: String,
            date: String,
            weightKg: Double,
            memo: String
        ) {
            val ownerEntries = entries[scope.ownerId] ?: return
            val index = ownerEntries.indexOfFirst { it.id == id }
            if (index >= 0) ownerEntries[index] = BodyReadEntry(id, date, weightKg, memo)
        }

        override fun deleteBodyMetric(scope: AccountScope, id: String) {
            entries[scope.ownerId]?.removeAll { it.id == id }
        }

        override fun bodyProfile(scope: AccountScope): BodyProfile =
            profiles[scope.ownerId] ?: BodyProfile.empty()

        override fun saveBodyProfile(scope: AccountScope, profile: BodyProfile) {
            profiles[scope.ownerId] = profile
        }

        override fun bodyMetrics(scope: AccountScope, date: String): List<BodyReadEntry> =
            bodyMetricEntriesForDate(scope, date)

        override fun latestBodyMetricOnOrBefore(
            scope: AccountScope,
            date: String
        ): BodyReadEntry? = entries[scope.ownerId].orEmpty()
            .filter { it.date <= date }
            .maxByOrNull { it.date }

        override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int =
            dates(scope, startDate, endDate).size

        override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> =
            entries[scope.ownerId].orEmpty().map { it.date }
                .filter { it in startDate..endDate }
                .distinct()
                .sorted()

        override fun weightWindow(
            scope: AccountScope,
            startDate: String,
            endDate: String
        ): BodyWeightWindow = BodyWeightWindow(null, recordedDays(scope, startDate, endDate))
    }
}
