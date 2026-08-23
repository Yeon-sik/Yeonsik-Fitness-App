package com.yeonsik.fitnessapp.data;

/** One optional add-on attached to a dining-out menu. */
public final class DiningOutOption {
    /** Backward-compatible group for options entered before group-aware templates existed. */
    public static final String DEFAULT_GROUP_KEY = "legacy_options";
    public static final String DEFAULT_GROUP_LABEL = "기존 외식 옵션";
    public static final String DEFAULT_ROLE = "optional";

    public final String name;
    public final NutritionProfile profile;
    public final String catalogFoodId;
    public final String sourceReference;
    public final String groupKey;
    public final String groupLabel;
    public final String role;
    public final String memberId;
    /** This user's fraction of the selected option; templates keep the default of 100%. */
    public final double consumedFraction;

    private DiningOutOption(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupLabel,
            String role,
            String memberId,
            double consumedFraction
    ) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Dining-out option name is required.");
        }
        this.name = normalizedName;
        this.profile = profile == null ? NutritionProfile.empty() : profile;
        this.catalogFoodId = catalogFoodId;
        this.sourceReference = sourceReference;
        this.groupKey = normalize(groupKey, DEFAULT_GROUP_KEY);
        this.groupLabel = normalize(groupLabel, DEFAULT_GROUP_LABEL);
        this.role = normalize(role, DEFAULT_ROLE);
        this.memberId = blankToNull(memberId);
        this.consumedFraction = requireConsumedFraction(consumedFraction);
    }

    public static DiningOutOption descriptive(String name) {
        return grouped(
                name,
                NutritionProfile.empty(),
                null,
                null,
                DEFAULT_GROUP_KEY,
                DEFAULT_GROUP_LABEL,
                DEFAULT_ROLE,
                null,
                1d
        );
    }

    public static DiningOutOption withProfile(String name, NutritionProfile profile) {
        return grouped(
                name,
                profile,
                null,
                null,
                DEFAULT_GROUP_KEY,
                DEFAULT_GROUP_LABEL,
                DEFAULT_ROLE,
                null,
                1d
        );
    }

    public static DiningOutOption fromFood(NutritionFood food) {
        if (food == null) {
            throw new IllegalArgumentException("Option food is required.");
        }
        return grouped(
                food.name,
                food.profile,
                food.id,
                food.sourceReference,
                DEFAULT_GROUP_KEY,
                DEFAULT_GROUP_LABEL,
                DEFAULT_ROLE,
                null,
                1d
        );
    }

    /** Creates the same option with generic template group/role metadata. */
    public static DiningOutOption grouped(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupLabel,
            String role,
            String memberId
    ) {
        return grouped(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupLabel,
                role,
                memberId,
                1d
        );
    }

    /** Creates a selected option with the user's independent consumed fraction. */
    public static DiningOutOption grouped(
            String name,
            NutritionProfile profile,
            String catalogFoodId,
            String sourceReference,
            String groupKey,
            String groupLabel,
            String role,
            String memberId,
            double consumedFraction
    ) {
        return new DiningOutOption(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupLabel,
                role,
                memberId,
                consumedFraction
        );
    }

    /** Returns a copy with a meal-specific consumed fraction. */
    public DiningOutOption withConsumedFraction(double fraction) {
        return grouped(
                name,
                profile,
                catalogFoodId,
                sourceReference,
                groupKey,
                groupLabel,
                role,
                memberId,
                fraction
        );
    }

    /** Returns this option's nutrition after applying its meal-specific fraction. */
    public NutritionProfile consumedProfile() {
        return profile.scaled(consumedFraction);
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

    private static String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static double requireConsumedFraction(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(
                    "Option consumed fraction must be greater than 0 and at most 1."
            );
        }
        return value;
    }
}
