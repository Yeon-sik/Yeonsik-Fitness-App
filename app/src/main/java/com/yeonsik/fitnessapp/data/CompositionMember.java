package com.yeonsik.fitnessapp.data;

/**
 * A selectable member of a reusable composition template.
 *
 * <p>The member points at a current nutrition food when one exists, but keeps a display
 * snapshot as well. That lets a template remain readable when its catalog item is later
 * renamed or removed. Consumed meals use {@link MealItemSnapshot} and never depend on this
 * mutable definition.</p>
 */
public final class CompositionMember {
    public final String id;
    public final String nutritionFoodId;
    public final String name;
    public final String brand;
    public final double quantity;
    public final String unit;
    public final boolean defaultSelected;
    public final int orderIndex;
    public final String sourceReference;
    public final NutritionProfile profile;

    public CompositionMember(
            String id,
            String nutritionFoodId,
            String name,
            String brand,
            double quantity,
            String unit,
            boolean defaultSelected,
            int orderIndex,
            String sourceReference,
            NutritionProfile profile
    ) {
        this.id = requireText(id, "Composition member id");
        this.nutritionFoodId = blankToNull(nutritionFoodId);
        this.name = requireText(name, "Composition member name");
        this.brand = blankToNull(brand);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Composition member quantity must be positive.");
        }
        this.quantity = quantity;
        this.unit = requireText(unit, "Composition member unit");
        this.defaultSelected = defaultSelected;
        if (orderIndex < 0) {
            throw new IllegalArgumentException("Composition member order cannot be negative.");
        }
        this.orderIndex = orderIndex;
        this.sourceReference = blankToNull(sourceReference);
        this.profile = profile == null ? NutritionProfile.empty() : profile;
    }

    public static CompositionMember fromFood(
            String id,
            NutritionFood food,
            double quantity,
            boolean defaultSelected,
            int orderIndex
    ) {
        if (food == null) {
            throw new IllegalArgumentException("Composition member food is required.");
        }
        NutritionProfile profile = NutritionCalculator.forQuantity(food, quantity);
        return new CompositionMember(
                id,
                food.id,
                food.name,
                food.brand,
                quantity,
                food.basisUnit,
                defaultSelected,
                orderIndex,
                food.sourceReference,
                profile
        );
    }

    public boolean hasNutrition() {
        return profile.isKnown(NutritionProfile.CALORIES_KCAL)
                || profile.isKnown(NutritionProfile.PROTEIN_GRAMS)
                || profile.isKnown(NutritionProfile.CARBS_GRAMS)
                || profile.isKnown(NutritionProfile.FAT_GRAMS);
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
