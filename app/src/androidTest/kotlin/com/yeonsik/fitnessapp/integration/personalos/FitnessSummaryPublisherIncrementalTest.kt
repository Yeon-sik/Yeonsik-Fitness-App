package com.yeonsik.fitnessapp.integration.personalos

import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.FitnessSummaryProjectionV2
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutSummaryApi
import com.yeonsik.fitnessapp.sync.AuthTestConnection
import com.yeonsik.fitnessapp.sync.AuthTestContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

/** All HTTP responses and preference files are isolated from the user's accounts. */
class FitnessSummaryPublisherIncrementalTest {
    private lateinit var context: AuthTestContext
    private val api = SummaryFixture()
    private val requests = mutableListOf<AuthTestConnection>()

    @Before fun prepare() { context = AuthTestContext(ApplicationProvider.getApplicationContext()) }
    @After fun clean() { context.clean() }

    @Test fun unchangedSnapshotsAreSkippedAfterPublisherRecreation() {
        api.rows = listOf(row("a"), row("b"))
        assertEquals(2, publisher().publish(config(), api))
        assertEquals(0, publisher().publish(config(), api))
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.disconnected })
    }

    @Test fun aLongInitialPublicationCheckpointsSuccessesInBoundedBatches() {
        api.rows = (1..121).map { row("session-$it") }
        val batches = mutableListOf<Int>()
        val persistent = PreferencesSummaryPublicationStore(context)
        val recording = object : SummaryPublicationStore {
            override fun fingerprint(scopeKey: String, sessionId: String) = persistent.fingerprint(scopeKey, sessionId)
            override fun acknowledge(scopeKey: String, fingerprints: Map<String, String>) {
                batches.add(fingerprints.size)
                persistent.acknowledge(scopeKey, fingerprints)
            }
        }
        val publishing = FitnessSummaryPublisher(recording) { url -> AuthTestConnection(url, 200, "[{}]") }
        assertEquals(121, publishing.publish(config(), api))
        assertEquals(listOf(50, 50, 21), batches)
        assertEquals(0, publisher().publish(config(), api))
    }

    @Test fun onlyChangedSummaryAndDeletionAreSentWithTheSameV2Contract() {
        api.rows = listOf(row("a"), row("b"))
        publisher().publish(config(), api)
        api.rows = listOf(row("a", chest = 4), row("b"))
        assertEquals(1, publisher().publish(config(), api))
        api.rows = listOf(row("a", chest = 4, deleted = "2026-10-20T01:00:00Z"), row("b"))
        assertEquals(1, publisher().publish(config(), api))
        val payload = JSONObject(requests.last().sentBody.toString("UTF-8")).getJSONObject("p_projection")
        assertEquals(2, payload.getInt("contract_version"))
        assertEquals("a", payload.getString("source_fitness_session_id"))
        assertEquals(4, payload.getInt("chest_sets"))
        assertEquals("2026-10-20T01:00:00Z", payload.getString("deleted_at"))
        assertFalse(payload.has("workout_sets"))
        assertEquals(0, publisher().publish(config(), api))
    }

    @Test fun successfulPrefixIsRetainedWhenALaterRequestFails() {
        api.rows = listOf(row("a"), row("b"))
        var attempt = 0
        val interrupted = FitnessSummaryPublisher(PreferencesSummaryPublicationStore(context)) { url ->
            if (++attempt == 2) throw SocketTimeoutException("fixture timeout")
            AuthTestConnection(url, 200, "[{}]").also { requests.add(it) }
        }
        try {
            interrupted.publish(config(), api)
            fail("Expected the second request to fail")
        } catch (_: SocketTimeoutException) { }
        assertEquals(1, publisher().publish(config(), api))
        assertEquals("b", JSONObject(requests.last().sentBody.toString("UTF-8"))
            .getJSONObject("p_projection").getString("id"))
        assertEquals(0, publisher().publish(config(), api))
    }

    @Test fun emptyRpcResultIsNotAcknowledgedAsAnAcceptedSnapshot() {
        api.rows = listOf(row("a"))
        val ignored = FitnessSummaryPublisher(PreferencesSummaryPublicationStore(context)) { url ->
            AuthTestConnection(url, 200, "[]")
        }
        assertEquals(0, ignored.publish(config(), api))
        assertEquals(1, publisher().publish(config(), api))
    }

    @Test fun editingDuringARequestLeavesTheNewSnapshotPending() {
        api.rows = listOf(row("a"))
        val editing = FitnessSummaryPublisher(PreferencesSummaryPublicationStore(context)) { url ->
            AuthTestConnection(url, 200, "[{}]") { api.rows = listOf(row("a", chest = 5)) }
        }
        assertEquals(1, editing.publish(config(), api))
        assertEquals(1, publisher().publish(config(), api))
        assertEquals(0, publisher().publish(config(), api))
    }

    @Test fun anotherAccountOrProjectDoesNotReuseTheAcknowledgement() {
        api.rows = listOf(row("a"))
        assertEquals(1, publisher().publish(config(), api))
        assertEquals(1, publisher().publish(config(owner = "other"), api))
        assertEquals(1, publisher().publish(config(url = "https://other.example"), api))
        assertEquals(0, publisher().publish(config(), api))
    }

    private fun publisher() = FitnessSummaryPublisher(PreferencesSummaryPublicationStore(context)) { url ->
        AuthTestConnection(url, 200, "[{}]").also { requests.add(it) }
    }

    private fun config(url: String = "https://shared.example", owner: String = "fixture-owner") =
        SupabaseConfig(url, "test-key", owner, "fixture@example.com", "test-access", "test-refresh",
            SupabaseConfig.LOCAL_SETTINGS_SOURCE)

    private fun row(id: String, chest: Int = 2, deleted: String? = null) = FitnessSummaryProjectionV2(
        id, id, "2026-10-20", "completed", chest, 0, 0, 0, 0, 0, 0, null, null,
        "2026-10-20T00:00:00Z", false, null, null, "2026-10-20T00:00:00Z", deleted, "android-local")

    private class SummaryFixture : WorkoutSummaryApi {
        var rows = emptyList<FitnessSummaryProjectionV2>()
        override fun completedFitnessSummaryProjectionsV2(ownerId: String) = rows
        override fun reconcileSharedWorkoutSummaries(ownerId: String) = 0
        override fun updateSharedWorkoutSummary(ownerId: String, recordId: String, publishToOs: Boolean) = false
    }
}
