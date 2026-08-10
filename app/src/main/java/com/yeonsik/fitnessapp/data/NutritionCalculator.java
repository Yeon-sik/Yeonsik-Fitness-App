package com.yeonsik.fitnessapp.data;

import java.util.List;

/** Pure nutrition math kept separate from Android UI and database code. */
public final class NutritionCalculator {
    private NutritionCalculator() {
    }

    /**
     * 기준량 대비 섭취량 비율로 모든 영양소를 환산한다.
     *
     * <p>필수·권고 typed 영양소와 미네랄·비타민이 동일하게 비례 환산되며, 모르는 값은
     * 0으로 채우지 않고 모름으로 남는다.</p>
     */
    public static NutritionProfile forQuantity(NutritionFood food, double quantity) {
        return forQuantity(food, quantity, food == null ? null : food.basisUnit);
    }

    /** Calculates a meal amount after converting the entered quantity to the food basis unit. */
    public static NutritionProfile forQuantity(
            NutritionFood food,
            double quantity,
            String quantityUnit
    ) {
        if (food == null) {
            throw new IllegalArgumentException("Food is required.");
        }
        if (food.basisAmount <= 0) {
            throw new IllegalArgumentException("Nutrition basis amount must be greater than zero.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        double quantityInBasisUnit = NutritionUnit.convert(
                quantity,
                quantityUnit == null ? food.basisUnit : quantityUnit,
                food.basisUnit
        );
        return food.profile.scaled(quantityInBasisUnit / food.basisAmount);
    }

    /** Calculates nutrition for one practical unit (1g, 1ml, or 1 serving). */
    public static NutritionProfile perUnitProfile(
            NutritionProfile profile,
            double basisAmount,
            String basisUnit
    ) {
        if (profile == null) {
            throw new IllegalArgumentException("Nutrition profile is required.");
        }
        if (basisAmount <= 0) {
            throw new IllegalArgumentException("Nutrition basis amount must be greater than zero.");
        }
        String unit = NutritionUnit.perUnit(basisUnit);
        double basisInUnit = NutritionUnit.convert(basisAmount, basisUnit, unit);
        return profile.scaled(1 / basisInUnit);
    }

    public static String unitNutritionLabel(
            NutritionProfile profile,
            double basisAmount,
            String basisUnit
    ) {
        String unit = NutritionUnit.perUnit(basisUnit);
        NutritionProfile perUnit = perUnitProfile(profile, basisAmount, basisUnit);
        return "단위 영양성분 · 1" + NutritionUnit.display(unit) + " 기준: "
                + trim(perUnit.calories()) + "kcal · P "
                + trim(perUnit.proteinGrams()) + "g · C "
                + trim(perUnit.carbsGrams()) + "g · F "
                + trim(perUnit.fatGrams()) + "g";
    }

    /** 식사 구성 전체의 영양소 합계. 모름 항목 수를 함께 보존한다. */
    public static NutritionTotals sum(List<MealCompositionItem> items) {
        NutritionTotals.Builder builder = NutritionTotals.builder();
        if (items == null) {
            return builder.build();
        }
        for (MealCompositionItem item : items) {
            builder.add(item == null ? null : item.profile);
        }
        return builder.build();
    }

    /** 레시피 1인분의 영양성분. 구성 재료 합계와 동일한 규칙으로 계산한다. */
    public static NutritionProfile recipeProfile(List<MealCompositionItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Recipe needs at least one food.");
        }
        return sum(items).toCompleteProfile();
    }

    public static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    /** 모르는 값은 0이 아니라 물음표로 표시한다. */
    public static String trimNullable(Double value) {
        return value == null ? "?" : trim(value);
    }

    /** 합계 표시용. 일부 항목이 모름이면 "약" 표기와 미상 건수를 덧붙인다. */
    public static String describeTotal(NutritionTotals.Total total) {
        if (total.knownCount() == 0) {
            return "?";
        }
        if (total.isComplete()) {
            return trim(total.knownSum());
        }
        return "≥" + trim(total.knownSum()) + " (미상 " + total.missingCount() + "건)";
    }
}
