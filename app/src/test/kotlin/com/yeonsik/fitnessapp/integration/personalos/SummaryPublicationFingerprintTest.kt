package com.yeonsik.fitnessapp.integration.personalos

import com.yeonsik.fitnessapp.data.FitnessSummaryProjectionV2
import org.junit.Assert.*
import org.junit.Test

class SummaryPublicationFingerprintTest {
    @Test fun identicalSnapshotsHaveTheSameFingerprint() {
        assertEquals(fingerprint(row()), fingerprint(row()))
    }

    @Test fun childCountsAreDetectedEvenWithoutAParentTimestampChange() {
        assertNotEquals(fingerprint(row()), fingerprint(row(chestSets = 3)))
    }

    @Test fun deletionAndRestorationAreDifferentSnapshots() {
        assertNotEquals(fingerprint(row()), fingerprint(row(deletedAt = "2026-10-20T01:00:00Z")))
    }

    @Test fun unknownDurationDoesNotBecomeZero() {
        assertNotEquals(fingerprint(row(duration = null)), fingerprint(row(duration = 0)))
    }

    @Test fun accountAndProjectScopesAreIndependent() {
        assertNotEquals(scope("https://one.example", "a"), scope("https://one.example", "b"))
        assertNotEquals(scope("https://one.example", "a"), scope("https://two.example", "a"))
        assertEquals(scope("https://one.example/", "a"), scope("https://one.example", "a"))
        assertNotEquals(scope("https://one.example|a", "b"), scope("https://one.example", "a|b"))
    }

    private fun fingerprint(row: FitnessSummaryProjectionV2) = SummaryPublicationFingerprint.of(row)
    private fun scope(url: String, owner: String) = SummaryPublicationFingerprint.scope(url, owner)

    private fun row(chestSets: Int = 2, duration: Int? = null, deletedAt: String? = null) =
        FitnessSummaryProjectionV2("one", "one", "2026-10-20", "completed", chestSets,
            0, 0, 0, 0, 0, 0, duration, null, "2026-10-20T00:00:00Z", false,
            null, null, "2026-10-20T00:00:00Z", deletedAt, "android-local")
}
