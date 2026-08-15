package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VerifiedReceiptConsumptionPolicyTest {
    private static VerifiedReceiptItem item(String catalogProductId, String nutritionFoodId) {
        return new VerifiedReceiptItem(
                "receipt-1", "line-1", "ledger-1", "라면", 1.0, "each", 14900,
                catalogProductId, nutritionFoodId, VerifiedReceiptItem.STATUS_PENDING_CONSUMPTION);
    }

    @Test
    public void onlyApprovedExactLinkCanBecomeConsumedMeal() {
        assertEquals(0.5, VerifiedReceiptConsumptionPolicy.requireConsumedQuantity(
                item("catalog-1", "food-1"), 0.5), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingNutritionLinkIsRejected() {
        VerifiedReceiptConsumptionPolicy.requireConsumedQuantity(item("catalog-1", null), 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void consumedQuantityCannotExceedPurchase() {
        VerifiedReceiptConsumptionPolicy.requireConsumedQuantity(item("catalog-1", "food-1"), 2.0);
    }
}
