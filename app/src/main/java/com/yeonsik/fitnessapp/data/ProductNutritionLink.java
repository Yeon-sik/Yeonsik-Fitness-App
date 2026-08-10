package com.yeonsik.fitnessapp.data;

/** User decision linking one Nutrition food to one PriceTrace standard product. */
public final class ProductNutritionLink {
    public static final String STATUS_SUGGESTED = "suggested";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";

    public static final String SOURCE_MANUAL = "manual_selection";
    public static final String SOURCE_PRICETRACE = "pricetrace_suggestion";

    public final String id;
    public final String ownerId;
    public final String nutritionFoodId;
    public final String catalogProductId;
    public final String standardProductId;
    public final String status;
    public final String sourceType;
    public final String proposalReference;
    public final int revision;
    public final String reviewedAt;
    public final ProductReadV1 product;

    public ProductNutritionLink(
            String id,
            String ownerId,
            String nutritionFoodId,
            String catalogProductId,
            String standardProductId,
            String status,
            String sourceType,
            String proposalReference,
            int revision,
            String reviewedAt,
            ProductReadV1 product
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.nutritionFoodId = nutritionFoodId;
        this.catalogProductId = catalogProductId;
        this.standardProductId = standardProductId;
        this.status = status;
        this.sourceType = sourceType;
        this.proposalReference = proposalReference;
        this.revision = Math.max(1, revision);
        this.reviewedAt = reviewedAt;
        this.product = product;
    }

    public boolean isApproved() {
        return STATUS_APPROVED.equals(status);
    }

    public boolean isSuggestion() {
        return STATUS_SUGGESTED.equals(status);
    }

    public String displayLabel() {
        if (product == null) {
            return "표준상품 정보 새로고침 필요";
        }
        return product.standardProductLabel();
    }
}
