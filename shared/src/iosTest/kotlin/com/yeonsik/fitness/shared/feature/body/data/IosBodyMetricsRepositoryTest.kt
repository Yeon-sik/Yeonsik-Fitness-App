package com.yeonsik.fitness.shared.feature.body.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosBodyMetricsRepositoryTest {
    @Test
    fun supportsBodyCrudProfileAndOwnerIsolationInMemory() {
        val repository = IosBodyMetricsRepository()
        val ownerA = AccountScope("owner-a")
        val ownerB = AccountScope("owner-b")

        val id = repository.addBodyMetric(ownerA, "2026-09-20", 70.5, "before breakfast")
        assertEquals(70.5, repository.bodyMetricForDate(ownerA, "2026-09-20")?.weightKg)
        assertNull(repository.bodyMetricForDate(ownerB, "2026-09-20"))

        repository.updateBodyMetric(ownerA, id, "2026-09-21", 70.25, "after breakfast")
        assertEquals("after breakfast", repository.bodyMetricEntryById(ownerA, id)?.memo)
        assertNull(repository.bodyMetricForDate(ownerA, "2026-09-20"))

        repository.saveBodyProfile(ownerA, BodyProfile(178, "", ""))
        assertEquals("178cm", repository.bodyProfile(ownerA).heightLabelKo())
        assertEquals("미설정", repository.bodyProfile(ownerB).heightLabelKo())
        repository.saveBodyProfile(ownerA, BodyProfile.empty())
        assertEquals("미설정", repository.bodyProfile(ownerA).heightLabelKo())

        repository.deleteBodyMetric(ownerA, id)
        assertNull(repository.bodyMetricEntryById(ownerA, id))
    }
}
