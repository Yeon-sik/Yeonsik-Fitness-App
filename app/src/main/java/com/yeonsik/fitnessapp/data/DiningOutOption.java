package com.yeonsik.fitnessapp.data;

/** One optional add-on attached to a dining-out menu. */
public final class DiningOutOption {
    public final String name;
    public final NutritionProfile profile;
    public final String catalogFoodId;
    public final String sourceReference;

    private DiningOutOption(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference
    ) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Dining-out option name is required.");
        }
        this.name = normalizedName;
        this.profile = profile == null ? NutritionProfile.empty() : profile;
        this.catalogFoodId = catalogFoodId;
        this.sourceReference = sourceReference;
    }

    public static DiningOutOption descriptive(String name) {
        return new DiningOutOption(name, NutritionProfile.empty(), null, null);
    }

    public static DiningOutOption withProfile(String name, NutritionProfile profile) {
        return new DiningOutOption(name, profile, null, null);
    }

    public static DiningOutOption fromFood(NutritionFood food) {
        if (food == null) {
            throw new IllegalArgumentException("Option food is required.");
        }
        return new DiningOutOption(food.name, food.profile, food.id, food.sourceReference);
    }

    public boolean hasNutrition() {
        return profile.isKnown(NutritionProfile.CALORIES_KCAL)
                || profile.isKnown(NutritionProfile.PROTEIN_GRAMS)
                || profile.isKnown(NutritionProfile.CARBS_GRAMS)
                || profile.isKnown(NutritionProfile.FAT_GRAMS);
    }

    public boolean hasCompleteMacros() {
        return profile.isKnown(NutritionProfile.PROTEIN_GRAMS)
                && profile.isKnown(NutritionProfile.CARBS_GRAMS)
                && profile.isKnown(NutritionProfile.FAT_GRAMS);
    }
}
