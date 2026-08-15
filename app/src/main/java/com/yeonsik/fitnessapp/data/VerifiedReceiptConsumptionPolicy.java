package com.yeonsik.fitnessapp.data;

/** Rules separating a purchased receipt item from a confirmed consumed meal item. */
public final class VerifiedReceiptConsumptionPolicy {
    private VerifiedReceiptConsumptionPolicy() {}

    public static double requireConsumedQuantity(VerifiedReceiptItem item, double consumedQuantity) {
        if (item == null) throw new IllegalArgumentException("영수증 상품이 필요합니다.");
        if (!item.hasApprovedNutritionLink()) {
            throw new IllegalArgumentException("승인된 PriceTrace-Nutrition 연결이 없는 상품입니다.");
        }
        if (consumedQuantity <= 0 || consumedQuantity > item.purchasedQuantity) {
            throw new IllegalArgumentException("섭취 수량은 구매 수량보다 클 수 없습니다.");
        }
        return consumedQuantity;
    }

    public static String sourceType() {
        return "receipt_verified";
    }
}
