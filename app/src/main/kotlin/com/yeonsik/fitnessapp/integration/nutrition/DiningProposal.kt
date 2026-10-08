package com.yeonsik.fitnessapp.integration.nutrition

/** Local correlation, separate from verified Nutrition source_reference and publication state. */
data class DiningProposal(
    val ownerId: String,
    val nutritionFoodId: String,
    val kind: String,
    val remoteScope: String,
    val idempotencyKey: String,
    val requestJson: String,
    val candidateId: String? = null,
    val status: String = "submitting",
    val restaurantId: String? = null,
    val restaurantLocationId: String? = null,
    val restaurantMenuId: String? = null,
    val catalogProductId: String? = null,
    val reviewNote: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val requestVersion: Int = 1
) {
    val canPublish: Boolean get() = kind == "menu" && status == "accepted"
        && listOf(restaurantId, restaurantLocationId, restaurantMenuId, catalogProductId)
            .all { !it.isNullOrBlank() }
}

interface DiningProposalStore {
    fun list(ownerId: String): List<DiningProposal>
    /** Must atomically insert, failing if this request version was already reserved. */
    fun reserve(proposal: DiningProposal)
    fun save(proposal: DiningProposal)
}

data class DiningMerchantFacts(
    val name: String, val branch: String = "", val address: String = "",
    val phone: String = "", val businessRegistrationNumber: String = ""
)

interface DiningProposalApi {
    fun available(ownerId: String): Boolean
    fun list(ownerId: String): List<DiningProposal>
    fun submitMerchant(ownerId: String, foodId: String, facts: DiningMerchantFacts): List<DiningProposal>
    fun submitMenu(ownerId: String, foodId: String, restaurantId: String?, locationId: String?,
        merchantCandidateId: String?, menuName: String): List<DiningProposal>
    fun resubmitMerchant(ownerId: String, foodId: String, previousCandidateId: String,
        facts: DiningMerchantFacts, userVerified: Boolean): List<DiningProposal>
    fun resubmitMenu(ownerId: String, foodId: String, previousCandidateId: String,
        restaurantId: String?, locationId: String?, merchantCandidateId: String?, menuName: String,
        userVerified: Boolean): List<DiningProposal>
    fun refresh(ownerId: String): List<DiningProposal>
    fun approvedIdentity(ownerId: String, foodId: String): com.yeonsik.fitnessapp.data.DiningOutIdentity
}

/** History ordering is explicit; refreshed timestamps never choose an older approval. */
fun List<DiningProposal>.latestDiningProposal(foodId: String, kind: String): DiningProposal? =
    filter { it.nutritionFoodId == foodId && it.kind == kind }.maxByOrNull { it.requestVersion }
