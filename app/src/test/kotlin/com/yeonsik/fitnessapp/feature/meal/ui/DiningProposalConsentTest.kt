package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.integration.nutrition.DiningProposal
import com.yeonsik.fitnessapp.integration.nutrition.latestDiningProposal
import org.junit.Assert.*
import org.junit.Test

class DiningProposalConsentTest {
    @Test fun proposalCannotBypassReviewViaExistingMenuPublication() {
        val state = NutritionPublicationUiState(selectedFoodId = "food")
        assertTrue(state.allowsExistingMenuPublication)
        for (status in listOf("submitting", "pending", "rejected", "accepted")) {
            val proposal = DiningProposal("owner", "food", "menu", "scope", "key", "{}",
                status = status, createdAt = "now", updatedAt = "now")
            assertFalse(state.copy(proposals = listOf(proposal)).allowsExistingMenuPublication)
            assertFalse(proposal.canPublish)
        }
    }
    @Test fun unconnectedOtherFoodProposalDoesNotBlockAnExactSelection() {
        val proposal = DiningProposal("owner", "other", "menu", "scope", "key", "{}",
            createdAt = "now", updatedAt = "now")
        assertTrue(NutritionPublicationUiState(selectedFoodId = "food", proposals = listOf(proposal))
            .allowsExistingMenuPublication)
    }
    @Test fun latestVersionOverridesOlderApprovalRegardlessOfListOrRefreshOrder() {
        val accepted = DiningProposal("owner", "food", "menu", "scope", "old-key", "{}",
            status = "accepted", restaurantId = "r", restaurantLocationId = "l", restaurantMenuId = "m",
            catalogProductId = "c", createdAt = "old", updatedAt = "newest-refresh")
        val pending = accepted.copy(idempotencyKey = "new-key", status = "pending", requestVersion = 2)
        for (history in listOf(listOf(accepted, pending), listOf(pending, accepted))) {
            assertEquals(pending, history.latestDiningProposal("food", "menu"))
            assertFalse(history.latestDiningProposal("food", "menu")!!.canPublish)
            assertFalse(NutritionPublicationUiState(selectedFoodId = "food", proposals = history).allowsExistingMenuPublication)
        }
    }
}
