package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionUnit;

/** Pure meal display/quantity policy, with no repository or Android View dependency. */
public final class MealPresentation {
    private MealPresentation() {}

    public static String nutrientLabel(String key) {
        return com.yeonsik.fitnessapp.core.ui.NutritionRow.displayLabel(key);
    }

    public static String componentLabel(
            String baseLabel,
            boolean hasExplicitConsumedFraction,
            double percentage,
            String provisionLabel
    ) {
        String result = "· " + baseLabel;
        if (hasExplicitConsumedFraction) result += " · 내 섭취 " + Math.round(percentage) + "%";
        if (provisionLabel != null && !provisionLabel.isEmpty()) result += " · " + provisionLabel;
        return result;
    }

    public static boolean supportsServingPercentage(NutritionFood food) {
        if (food == null || !NutritionUnit.SERVING.equals(NutritionUnit.normalize(food.basisUnit))) return false;
        String kind = NutritionFood.normalizeKind(food.kind);
        return NutritionFood.KIND_RECIPE.equals(kind) || food.isPackagedFood();
    }

    public static double quantityForServingPercent(NutritionFood food, double percent) {
        if (food == null || food.basisAmount <= 0d || !Double.isFinite(percent) || percent <= 0d) {
            throw new IllegalArgumentException("섭취 비율은 0보다 큰 숫자여야 합니다.");
        }
        return food.basisAmount * percent / 100d;
    }

    public static double servingPercentForQuantity(NutritionFood food, double quantity) {
        if (food == null || food.basisAmount <= 0d || !Double.isFinite(quantity) || quantity <= 0d) {
            throw new IllegalArgumentException("섭취량은 0보다 큰 숫자여야 합니다.");
        }
        return quantity * 100d / food.basisAmount;
    }
}
