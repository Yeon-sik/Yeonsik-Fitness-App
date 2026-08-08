package com.yeonsik.fitnessapp.data;

import java.util.List;

/** Pure nutrition math kept separate from Android UI and database code. */
public final class NutritionCalculator {
    private NutritionCalculator() {
    }

    public static NutritionValues forQuantity(NutritionFood food, double quantity) {
        if (food.basisAmount <= 0) {
            throw new IllegalArgumentException("Nutrition basis amount must be greater than zero.");
        }
        double multiplier = quantity / food.basisAmount;
        return new NutritionValues(
                food.calories * multiplier,
                food.proteinGrams * multiplier,
                food.carbsGrams * multiplier,
                food.fatGrams * multiplier
        );
    }

    public static NutritionValues sum(List<MealCompositionItem> items) {
        NutritionValues total = new NutritionValues(0, 0, 0, 0);
        if (items == null) {
            return total;
        }
        for (MealCompositionItem item : items) {
            total = total.plus(new NutritionValues(
                    item.calories,
                    item.proteinGrams,
                    item.carbsGrams,
                    item.fatGrams
            ));
        }
        return total;
    }

    public static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    public static final class NutritionValues {
        public final double calories;
        public final double proteinGrams;
        public final double carbsGrams;
        public final double fatGrams;

        public NutritionValues(
                double calories,
                double proteinGrams,
                double carbsGrams,
                double fatGrams
        ) {
            this.calories = calories;
            this.proteinGrams = proteinGrams;
            this.carbsGrams = carbsGrams;
            this.fatGrams = fatGrams;
        }

        public NutritionValues plus(NutritionValues other) {
            return new NutritionValues(
                    calories + other.calories,
                    proteinGrams + other.proteinGrams,
                    carbsGrams + other.carbsGrams,
                    fatGrams + other.fatGrams
            );
        }
    }
}
