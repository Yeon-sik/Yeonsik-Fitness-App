package com.yeonsik.fitnessapp.data;

/** Local, immutable purchase evidence imported from CashOS. It is not a meal record. */
public final class VerifiedReceiptItem {
    public static final String STATUS_PENDING_CONSUMPTION = "pending_consumption";
    public static final String STATUS_CONSUMED = "consumed";
    public static final String STATUS_SKIPPED = "skipped";

    public final String receiptId;
    public final String receiptItemId;
    public final String ledgerEntryId;
    public final String descriptionSnapshot;
    public final double purchasedQuantity;
    public final String unit;
    public final int totalPriceKrw;
    public final String catalogProductId;
    public final String nutritionFoodId;
    public final String status;

    public VerifiedReceiptItem(
            String receiptId,
            String receiptItemId,
            String ledgerEntryId,
            String descriptionSnapshot,
            double purchasedQuantity,
            String unit,
            int totalPriceKrw,
            String catalogProductId,
            String nutritionFoodId,
            String status
    ) {
        if (isBlank(receiptId) || isBlank(receiptItemId) || isBlank(ledgerEntryId)) {
            throw new IllegalArgumentException("영수증 식별자가 비어 있습니다.");
        }
        if (isBlank(descriptionSnapshot) || purchasedQuantity <= 0 || totalPriceKrw < 0) {
            throw new IllegalArgumentException("영수증 상품 스냅샷이 유효하지 않습니다.");
        }
        if (isBlank(unit) || isBlank(status)) {
            throw new IllegalArgumentException("영수증 상품 단위와 상태가 필요합니다.");
        }
        this.receiptId = receiptId;
        this.receiptItemId = receiptItemId;
        this.ledgerEntryId = ledgerEntryId;
        this.descriptionSnapshot = descriptionSnapshot;
        this.purchasedQuantity = purchasedQuantity;
        this.unit = unit;
        this.totalPriceKrw = totalPriceKrw;
        this.catalogProductId = catalogProductId;
        this.nutritionFoodId = nutritionFoodId;
        this.status = status;
    }

    public boolean hasApprovedNutritionLink() {
        return !isBlank(catalogProductId) && !isBlank(nutritionFoodId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
