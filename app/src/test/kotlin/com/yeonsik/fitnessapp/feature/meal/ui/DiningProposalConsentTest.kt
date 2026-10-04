package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.integration.nutrition.DiningProposal
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
}
