package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;

public class CashOsVerifiedReceiptContractTest {
    @Test
    public void parsesExactIdentityAndKeepsPendingConsumption() {
        VerifiedReceiptItem item = CashOsVerifiedReceiptContract.parse(Collections.singletonList(
                new CashOsVerifiedReceiptContract.ReceiptCandidateRow(
                        "receipt-1", "line-1", "ledger-1", "교자", 1, "each", 7000,
                        "catalog-gyoza", "food-gyoza"))).get(0);

        assertEquals("catalog-gyoza", item.catalogProductId);
        assertEquals("food-gyoza", item.nutritionFoodId);
        assertEquals(VerifiedReceiptItem.STATUS_PENDING_CONSUMPTION, item.status);
    }
}
