package com.yeonsik.fitnessapp.integration.nutrition

import com.yeonsik.fitnessapp.config.SupabaseConfig
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import com.yeonsik.fitnessapp.integration.pricetrace.DiningProposalAccount
import com.yeonsik.fitnessapp.integration.pricetrace.DiningProposalClient
import com.yeonsik.fitnessapp.integration.pricetrace.DiningProposalRemote
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class DiningProposalService(
    private val catalog: NutritionCatalogRepositoryApi,
    private val accounts: DiningProposalAccount,
    private val store: DiningProposalStore,
    private val readRestaurant: (String) -> NutritionIntegrationService.RestaurantDetail,
    private val remote: DiningProposalRemote = DiningProposalClient()
) : DiningProposalApi {
    override fun available(ownerId: String) = accounts.isDiningProposalConfigured(ownerId)
    override fun list(ownerId: String): List<DiningProposal> {
        val configuredScope = accounts.configuredDiningProposalScope(ownerId)
        return store.list(ownerId).filter { configuredScope == null || it.remoteScope == configuredScope }
    }

    override fun submitMerchant(ownerId: String, foodId: String, facts: DiningMerchantFacts): List<DiningProposal> {
        val merchant = JSONObject().put("merchant_name", text(facts.name, 200, required = true))
            .put("business_kind", "food_service")
        mapOf("branch_name" to facts.branch, "address" to facts.address, "phone" to facts.phone,
            "business_registration_number" to facts.businessRegistrationNumber).forEach { (key, value) ->
            text(value, if (key == "address") 500 else 200)?.let { merchant.put(key, it) }
        }
        return submit(ownerId, foodId, "merchant", JSONObject().put("p_merchant", merchant)
            .put("p_user_verified", true))
    }

    override fun submitMenu(ownerId: String, foodId: String, restaurantId: String?, locationId: String?,
        merchantCandidateId: String?, menuName: String): List<DiningProposal> {
        require((merchantCandidateId != null && restaurantId == null && locationId == null)
            || (merchantCandidateId == null && restaurantId != null && locationId != null)) {
            "PT 지점 또는 서버가 반환한 가게 제안을 선택하세요."
        }
        listOfNotNull(restaurantId, locationId, merchantCandidateId).forEach(::uuid)
        val request = JSONObject().put("p_restaurant_id", restaurantId ?: JSONObject.NULL)
            .put("p_restaurant_location_id", locationId ?: JSONObject.NULL)
            .put("p_merchant_candidate_id", merchantCandidateId ?: JSONObject.NULL)
            .put("p_menu_name", text(menuName, 200, required = true))
            .put("p_metadata", JSONObject()).put("p_user_verified", true)
        return submit(ownerId, foodId, "menu", request)
    }

    @Synchronized
    private fun submit(ownerId: String, foodId: String, kind: String, request: JSONObject): List<DiningProposal> {
        requirePrivateFood(ownerId, foodId)
        val config = accounts.requireDiningProposalAccount(ownerId)
        val scope = scope(config)
        val existing = store.list(ownerId).singleOrNull {
            it.nutritionFoodId == foodId && it.kind == kind && it.remoteScope == scope
        }
        if (existing != null && existing.candidateId != null) return refresh(ownerId)
        // Save the key and exact sanitized request BEFORE I/O. A timeout/process restart replays
        // the same submission, including the same facts, instead of inventing a second proposal.
        val now = Instant.now().toString()
        val proposal = existing ?: DiningProposal(ownerId, foodId, kind, scope,
            "fitness-dining-${UUID.randomUUID()}", request.toString(), createdAt = now, updatedAt = now)
        store.save(proposal)
        val payload = JSONObject(proposal.requestJson).put("p_idempotency_key", proposal.idempotencyKey)
        val response = remote.submit(config, kind, payload)
        store.save(applyResponse(proposal, response))
        return list(ownerId)
    }

    @Synchronized
    override fun refresh(ownerId: String): List<DiningProposal> {
        val config = accounts.requireDiningProposalAccount(ownerId)
        val proposals = store.list(ownerId).filter { it.remoteScope == scope(config) }
        // Retry only an explicitly submitted durable request. Approval never causes publication.
        proposals.filter { it.candidateId == null }.forEach { proposal ->
            requirePrivateFood(ownerId, proposal.nutritionFoodId)
            val response = remote.submit(config, proposal.kind,
                JSONObject(proposal.requestJson).put("p_idempotency_key", proposal.idempotencyKey))
            store.save(applyResponse(proposal, response))
        }
        proposals.map { it.kind }.distinct().forEach { kind ->
            val responses = remote.read(config, kind).associateBy { uuid(it.getString("candidateId")) }
            store.list(ownerId).filter { it.remoteScope == scope(config) && it.kind == kind }.forEach { proposal ->
                val response = responses[proposal.candidateId]
                if (response != null) store.save(applyResponse(proposal, response))
                else store.save(proposal.copy(status = "unavailable", restaurantId = null,
                    restaurantLocationId = null, restaurantMenuId = null, catalogProductId = null,
                    reviewNote = "PT 서버에서 제안 상태를 확인할 수 없습니다.", updatedAt = Instant.now().toString()))
            }
        }
        return list(ownerId)
    }

    override fun approvedIdentity(ownerId: String, foodId: String): DiningOutIdentity {
        requirePrivateFood(ownerId, foodId)
        val config = accounts.requireDiningProposalAccount(ownerId)
        val proposal = refresh(ownerId).singleOrNull {
            it.nutritionFoodId == foodId && it.kind == "menu" && it.remoteScope == scope(config)
        } ?: error("승인된 메뉴 제안이 없습니다.")
        check(proposal.canPublish) { "관리자 승인 전에는 공개할 수 없습니다." }
        // Re-read public PT detail before final explicit publication; no local/name-based identity.
        val detail = readRestaurant(proposal.restaurantId!!)
        val location = detail.locations.singleOrNull { it.restaurantLocationId == proposal.restaurantLocationId }
            ?: error("승인된 PT 지점을 공개 조회에서 확인하지 못했습니다.")
        val menu = detail.menus.singleOrNull {
            it.restaurantMenuId == proposal.restaurantMenuId && it.catalogProductId == proposal.catalogProductId
        } ?: error("승인된 PT 메뉴를 공개 조회에서 확인하지 못했습니다.")
        return DiningOutIdentity.fromPriceTrace(detail.restaurantId, detail.restaurantName,
            location.restaurantLocationId, location.locationSourceNamespace, location.sourceLocationCode,
            location.branchName, menu.restaurantMenuId, menu.menuName, menu.catalogProductId)
    }

    private fun requirePrivateFood(ownerId: String, foodId: String) {
        val food = catalog.findFoodById(foodId)
        require(food != null && food.ownerId == ownerId && food.isDiningOutMenu
            && catalog.isPrivateDiningOutMenu(foodId, ownerId)) {
            "현재 소유자의 비공개 Nutrition 외식 메뉴를 선택하세요."
        }
    }

    internal fun applyResponse(proposal: DiningProposal, response: JSONObject): DiningProposal {
        check(response.getString("schemaVersion") == if (proposal.kind == "menu")
            "restaurant-menu-candidate.v1" else "merchant-only-candidate.v1") { "PT 제안 응답 계약이 다릅니다." }
        val candidateId = uuid(response.getString("candidateId"))
        check(proposal.candidateId == null || proposal.candidateId == candidateId) { "PT 제안 ID가 다릅니다." }
        val status = response.getString("reviewStatus")
        check(status in setOf("pending", "accepted", "rejected")) { "PT 검토 상태가 올바르지 않습니다." }
        val exact = status == "accepted" && response.optString("resolutionStatus") == "exact"
        fun exactId(key: String): String? = if (exact) uuid(response.getString(key)) else null
        return proposal.copy(candidateId = candidateId, status = status,
            restaurantId = exactId("restaurantId"), restaurantLocationId = exactId("restaurantLocationId"),
            restaurantMenuId = if (proposal.kind == "menu") exactId("restaurantMenuId") else null,
            catalogProductId = if (proposal.kind == "menu") exactId("catalogProductId") else null,
            reviewNote = response.optString("reviewNote").takeIf { !response.isNull("reviewNote") && it.isNotBlank() },
            updatedAt = Instant.now().toString())
    }

    private fun scope(config: SupabaseConfig) = "${config.supabaseUrl.trimEnd('/')}|${config.userId}"
    private fun uuid(value: String): String {
        require(value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
            "PT 서버 identity가 올바르지 않습니다."
        }
        return UUID.fromString(value).toString()
    }
    private fun text(value: String, limit: Int, required: Boolean = false): String? {
        val sanitized = value.trim().takeIf { it.isNotEmpty() }
        require((!required || sanitized != null) && (sanitized?.length ?: 0) <= limit) { "가게·메뉴 제안 입력을 확인하세요." }
        return sanitized
    }
}
