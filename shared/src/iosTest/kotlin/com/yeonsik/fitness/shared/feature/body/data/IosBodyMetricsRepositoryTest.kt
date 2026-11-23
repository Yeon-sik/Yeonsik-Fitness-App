package com.yeonsik.fitness.shared.feature.body.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.model.BodyProfile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToURL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
class IosBodyMetricsRepositoryTest {
    @Test
    fun persistsBodyCrudProfileAndOwnerIsolationAcrossInstances() = withStoreFile { file ->
        val ownerA = AccountScope("owner-a")
        val ownerB = AccountScope("owner-b")
        val initial = IosBodyMetricsRepository(file)

        val id = initial.addBodyMetric(ownerA, "2026-09-20", 70.5, "before breakfast")
        val reopened = IosBodyMetricsRepository(file)
        assertEquals(70.5, reopened.bodyMetricForDate(ownerA, "2026-09-20")?.weightKg)
        assertNull(reopened.bodyMetricForDate(ownerB, "2026-09-20"))
        assertEquals(id, reopened.addBodyMetric(ownerA, "2026-09-20", 70.4, "same day"))

        val other = initial.addBodyMetric(ownerA, "2026-09-21", 71.0, "existing date")
        reopened.updateBodyMetric(ownerA, id, "2026-09-21", 70.25, "after breakfast")
        val updated = IosBodyMetricsRepository(file)
        assertEquals("after breakfast", updated.bodyMetricEntryById(ownerA, id)?.memo)
        assertNull(updated.bodyMetricForDate(ownerA, "2026-09-20"))
        assertEquals(listOf(id, other), updated.bodyMetrics(ownerA, "2026-09-21").map { it.id })
        assertEquals(id, updated.addBodyMetric(ownerA, "2026-09-21", 70.0, "same day after move"))

        updated.saveBodyProfile(ownerA, BodyProfile(178, "", ""))
        assertEquals("178cm", initial.bodyProfile(ownerA).heightLabelKo())
        assertEquals("미설정", initial.bodyProfile(ownerB).heightLabelKo())
        reopened.saveBodyProfile(ownerA, BodyProfile.empty())
        assertEquals("미설정", initial.bodyProfile(ownerA).heightLabelKo())

        initial.deleteBodyMetric(ownerB, id)
        assertEquals(id, reopened.bodyMetricEntryById(ownerA, id)?.id)
        initial.deleteBodyMetric(ownerA, id)
        assertNull(IosBodyMetricsRepository(file).bodyMetricEntryById(ownerA, id))
        assertEquals(other, updated.latestBodyMetricOnOrBefore(ownerA, "2026-09-21")?.id)
    }

    @Test
    fun preservesAnUnsupportedFileWhenAWriteIsAttempted() = withStoreFile { file ->
        val contents = """{"schemaVersion":2,"writeOrder":0,"owners":{}}"""
        check(NSString.create(string = contents)
            .writeToURL(file, atomically = true, encoding = NSUTF8StringEncoding, error = null))

        assertFailsWith<IllegalStateException> {
            IosBodyMetricsRepository(file).addBodyMetric(AccountScope("owner-a"), "2026-09-20", 75.0, "")
        }
        assertEquals(contents, NSString.create(
            contentsOfURL = file, encoding = NSUTF8StringEncoding, error = null
        )?.toString())
    }

    private fun withStoreFile(run: (NSURL) -> Unit) {
        val directory = checkNotNull(NSURL.fileURLWithPath(NSTemporaryDirectory())
            .URLByAppendingPathComponent(NSUUID().UUIDString, isDirectory = true))
        val manager = NSFileManager.defaultManager
        check(manager.createDirectoryAtURL(directory, true, null, null))
        try {
            run(checkNotNull(directory.URLByAppendingPathComponent("body-metrics.json")))
        } finally {
            manager.removeItemAtURL(directory, error = null)
        }
    }
}
