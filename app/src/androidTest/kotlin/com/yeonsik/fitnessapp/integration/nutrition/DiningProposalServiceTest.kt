package com.yeonsik.fitnessapp.integration.nutrition

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.RoomDiningProposalStore
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository
import com.yeonsik.fitnessapp.integration.pricetrace.DiningProposalAccount
import com.yeonsik.fitnessapp.integration.pricetrace.DiningProposalRemote
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.UUID

/** Named fixture database tests process reopening without touching fitness_mvp.db. */
class DiningProposalServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "dining-proposals-${UUID.randomUUID()}.db"
    private lateinit var room: FitnessRoomDatabase
    private lateinit var catalog: NutritionCatalogRepository
    private val accounts = FakeAccounts()
    private val remote = FakeRemote()
    private var detailReads = 0
    private lateinit var foodId: String
    private var originalSource: String? = null
    private val owner = "nutrition-owner"
    private val restaurantId = "11111111-1111-4111-8111-111111111111"
    private val locationId = "22222222-2222-4222-8222-222222222222"

    @Before fun setUp() {
        open()
        val food = catalog.saveDiningOutMenuWithNutrition("확인 가게", "확인 메뉴", 500,
            20.0, 60.0, 15.0, 500.0, 5.0, 2.0, null, null)!!
        foodId = food.id
        originalSource = food.sourceReference
    }
    private fun open() {
        room = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, name).build()
        catalog = NutritionCatalogRepository(room, owner)
    }
    @After fun tearDown() { room.close(); context.deleteDatabase(name) }
    private fun service() = DiningProposalService(catalog, accounts,
        RoomDiningProposalStore(room.diningProposalRoomDao()), { id ->
            detailReads++
            assertEquals(restaurantId, id)
            throw IllegalStateException("Explicit verified detail read")
        }, remote)
    private fun submitMenu() = service().submitMenu(owner, foodId, restaurantId, locationId, null, "확인 메뉴")
    private fun assertPrivateAndUnlinked() {
        assertTrue(catalog.isPrivateDiningOutMenu(foodId, owner))
        assertEquals(originalSource, catalog.findFoodById(foodId)!!.sourceReference)
    }

    @Test fun reusableSaveIsPrivateAndNeverCreatesAProposal() {
        assertPrivateAndUnlinked()
        assertTrue(service().list(owner).isEmpty())
        assertEquals(0, remote.requests.size)
    }
    @Test fun pendingProposalAndExactRequestSurviveReopening() {
        val proposal = submitMenu().single()
        assertEquals("pending", proposal.status)
        assertFalse(proposal.canPublish)
        assertPrivateAndUnlinked()
        room.close(); open()
        assertEquals(proposal, service().list(owner).single())
        assertTrue(service().list("other-owner").isEmpty())
        service().refresh(owner)
        assertEquals(1, remote.requests.size)
        assertPrivateAndUnlinked()
    }
    @Test fun uncertainSubmissionReplaysSameKeyAndFactsAfterRestart() {
        remote.failAfterAcceptance = true
        assertThrows(IOException::class.java) { submitMenu() }
        val reserved = service().list(owner).single()
        assertNull(reserved.candidateId)
        room.close(); open()
        val restored = service().refresh(owner).single()
        assertEquals(remote.menuCandidateId, restored.candidateId)
        assertEquals(reserved.idempotencyKey, restored.idempotencyKey)
        assertEquals(remote.requests[0].toString(), remote.requests[1].toString())
        service().refresh(owner)
        assertEquals(2, remote.requests.size)
        assertPrivateAndUnlinked()
    }
    @Test fun merchantUsesExistingSanitizedContractAndDoesNotTrustPendingNameMatch() {
        val proposal = service().submitMerchant(owner, foodId,
            DiningMerchantFacts("  확인 가게  ", "강남점", phone = "02-123-4567")).single()
        val payload = remote.requests.single().getJSONObject("p_merchant")
        assertEquals("확인 가게", payload.getString("merchant_name"))
        assertEquals("food_service", payload.getString("business_kind"))
        assertFalse(payload.has("name")); assertFalse(payload.has("source_app"))
        assertFalse(payload.has("calories")); assertFalse(payload.has("source_location_code"))
        assertNull(proposal.restaurantId); assertNull(proposal.catalogProductId)
        assertPrivateAndUnlinked()
    }
    @Test fun pendingAndRejectedCannotPublishEvenWithFakeExactIds() {
        submitMenu()
        assertThrows(IllegalStateException::class.java) { service().approvedIdentity(owner, foodId) }
        remote.menuStatus = "rejected"
        val rejected = service().refresh(owner).single()
        assertEquals("rejected", rejected.status); assertNull(rejected.catalogProductId)
        assertThrows(IllegalStateException::class.java) { service().approvedIdentity(owner, foodId) }
        assertEquals(0, detailReads)
        assertPrivateAndUnlinked()
    }
    @Test fun acceptancePersistsServerIdsButDoesNotPublishOrReadPublicDetailUntilUserAction() {
        submitMenu(); remote.menuStatus = "accepted"
        val accepted = service().refresh(owner).single()
        assertTrue(accepted.canPublish)
        assertEquals(restaurantId, accepted.restaurantId)
        assertEquals(remote.catalogId, accepted.catalogProductId)
        assertEquals(0, detailReads)
        assertPrivateAndUnlinked()
        room.close(); open()
        assertEquals(accepted, service().list(owner).single())
        val error = assertThrows(IllegalStateException::class.java) { service().approvedIdentity(owner, foodId) }
        assertEquals("Explicit verified detail read", error.message)
        assertEquals(1, detailReads)
        assertPrivateAndUnlinked()
    }
    @Test fun missingAccountAndForeignFoodFailBeforeRemoteSubmission() {
        accounts.configured = false
        assertFalse(service().available(owner))
        assertThrows(IllegalStateException::class.java) { submitMenu() }
        accounts.configured = true
        assertThrows(IllegalArgumentException::class.java) {
            service().submitMenu("other-owner", foodId, restaurantId, locationId, null, "메뉴")
        }
        assertEquals(0, remote.requests.size)
        assertPrivateAndUnlinked()
    }
    @Test fun anotherPtAccountDoesNotReuseOrDisplayThePreviousAccountProposal() {
        submitMenu()
        accounts.ptUser = "another-pt-user"
        assertTrue(service().list(owner).isEmpty())
        service().refresh(owner)
        assertEquals(1, remote.requests.size)
        accounts.ptUser = "pt-user"
        assertEquals(1, service().list(owner).size)
    }

    @Test fun previouslyAcceptedProposalDisappearingFromOwnerReadFailsClosed() {
        submitMenu(); remote.menuStatus = "accepted"
        assertTrue(service().refresh(owner).single().canPublish)
        remote.ownerReadMissing = true
        val unavailable = service().refresh(owner).single()
        assertEquals("unavailable", unavailable.status)
        assertNull(unavailable.restaurantId); assertNull(unavailable.catalogProductId)
        assertFalse(unavailable.canPublish)
        assertThrows(IllegalStateException::class.java) { service().approvedIdentity(owner, foodId) }
        assertEquals(0, detailReads)
        assertPrivateAndUnlinked()
    }

    @Test fun acceptedButRetiredServerIdentityCannotPublish() {
        submitMenu(); remote.menuStatus = "accepted"
        assertTrue(service().refresh(owner).single().canPublish)
        remote.resolutionStatus = "unresolved"
        val retired = service().refresh(owner).single()
        assertEquals("accepted", retired.status)
        assertNull(retired.restaurantMenuId); assertNull(retired.catalogProductId)
        assertFalse(retired.canPublish)
        assertThrows(IllegalStateException::class.java) { service().approvedIdentity(owner, foodId) }
        assertEquals(0, detailReads)
        assertPrivateAndUnlinked()
    }

    private class FakeAccounts : DiningProposalAccount {
        var configured = true
        var ptUser = "pt-user"
        private fun config() = SupabaseConfig("https://proposal.example.test", "public", ptUser,
            "", "fixture-token", "", "fixture")
        override fun isDiningProposalConfigured(ownerId: String) = configured && ownerId == "nutrition-owner"
        override fun requireDiningProposalAccount(ownerId: String): SupabaseConfig {
            check(isDiningProposalConfigured(ownerId)) { "No configured remote account" }; return config()
        }
        override fun configuredDiningProposalScope(ownerId: String): String? =
            if (isDiningProposalConfigured(ownerId)) "${config().supabaseUrl}|$ptUser" else null
    }
    private class FakeRemote : DiningProposalRemote {
        val requests = mutableListOf<JSONObject>()
        val menuCandidateId = "33333333-3333-4333-8333-333333333333"
        val catalogId = "44444444-4444-4444-8444-444444444444"
        var failAfterAcceptance = false
        var menuStatus = "pending"
        var ownerReadMissing = false
        var resolutionStatus = "exact"
        private val submitted = mutableSetOf<String>()
        override fun submit(config: SupabaseConfig, kind: String, request: JSONObject): JSONObject {
            requests += JSONObject(request.toString()); submitted += kind
            if (failAfterAcceptance) { failAfterAcceptance = false; throw IOException("Response lost after server accepted") }
            return response(kind)
        }
        override fun read(config: SupabaseConfig, kind: String) =
            if (!ownerReadMissing && kind in submitted) listOf(response(kind)) else emptyList()
        private fun response(kind: String) = JSONObject()
            .put("schemaVersion", if (kind == "menu") "restaurant-menu-candidate.v1" else "merchant-only-candidate.v1")
            .put("candidateId", menuCandidateId).put("reviewStatus", if (kind == "menu") menuStatus else "pending")
            .put("resolutionStatus", resolutionStatus) // Simulate existing pending merchant name matching too.
            .put("restaurantId", "11111111-1111-4111-8111-111111111111")
            .put("restaurantLocationId", "22222222-2222-4222-8222-222222222222")
            .put("restaurantMenuId", "55555555-5555-4555-8555-555555555555")
            .put("catalogProductId", catalogId)
    }
}
