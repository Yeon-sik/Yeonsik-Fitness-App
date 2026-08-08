package com.yeonsik.fitnessapp.data;

/** A searchable food or recipe in the nutrition catalog. */
public final class NutritionFood {
    public static final String KIND_INGREDIENT = "ingredient";
    public static final String KIND_EXTERNAL_MENU = "external_menu";
    public static final String KIND_RECIPE = "recipe";

    public final String id;
    public final String ownerId;
    public final String name;
    public final String kind;
    public final double basisAmount;
    public final String basisUnit;
    public final double calories;
    public final double proteinGrams;
    public final double carbsGrams;
    public final double fatGrams;
    public final String sourceType;
    public final String sourceReference;

    public NutritionFood(
            String id,
            String ownerId,
            String name,
            String kind,
            double basisAmount,
            String basisUnit,
            double calories,
            double proteinGrams,
            double carbsGrams,
            double fatGrams,
            String sourceType,
            String sourceReference
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.kind = kind;
        this.basisAmount = basisAmount;
        this.basisUnit = basisUnit;
        this.calories = calories;
        this.proteinGrams = proteinGrams;
        this.carbsGrams = carbsGrams;
        this.fatGrams = fatGrams;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
    }

    public String nutritionLabel() {
        return Math.round(calories) + "kcal · "
                + NutritionCalculator.trim(proteinGrams) + "g P · "
                + NutritionCalculator.trim(carbsGrams) + "g C · "
                + NutritionCalculator.trim(fatGrams) + "g F";
    }
}
