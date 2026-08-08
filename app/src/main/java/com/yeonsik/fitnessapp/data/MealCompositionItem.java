package com.yeonsik.fitnessapp.data;

/** One catalog food and the number of catalog basis units eaten in a meal. */
public final class MealCompositionItem {
    public final NutritionFood food;
    public final double quantity;
    public final double calories;
    public final double proteinGrams;
    public final double carbsGrams;
    public final double fatGrams;

    private MealCompositionItem(
            NutritionFood food,
            double quantity,
            NutritionCalculator.NutritionValues values
    ) {
        this.food = food;
        this.quantity = quantity;
        this.calories = values.calories;
        this.proteinGrams = values.proteinGrams;
        this.carbsGrams = values.carbsGrams;
        this.fatGrams = values.fatGrams;
    }

    public static MealCompositionItem from(NutritionFood food, double quantity) {
        if (food == null) {
            throw new IllegalArgumentException("Food is required.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        return new MealCompositionItem(
                food,
                quantity,
                NutritionCalculator.forQuantity(food, quantity)
        );
    }

    public String label() {
        return food.name + " · " + NutritionCalculator.trim(quantity) + food.basisUnit
                + " · " + Math.round(calories) + "kcal";
    }
}
