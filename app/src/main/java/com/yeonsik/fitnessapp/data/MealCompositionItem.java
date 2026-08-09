package com.yeonsik.fitnessapp.data;

/** One catalog food and the number of catalog basis units eaten in a meal. */
public final class MealCompositionItem {
    public final NutritionFood food;
    public final double quantity;
    /** 섭취량만큼 환산된 전체 영양성분. 기록 시 그대로 snapshot된다. */
    public final NutritionProfile profile;

    public final double calories;
    public final double proteinGrams;
    public final double carbsGrams;
    public final double fatGrams;

    private MealCompositionItem(NutritionFood food, double quantity, NutritionProfile profile) {
        this.food = food;
        this.quantity = quantity;
        this.profile = profile;
        this.calories = profile.calories();
        this.proteinGrams = profile.proteinGrams();
        this.carbsGrams = profile.carbsGrams();
        this.fatGrams = profile.fatGrams();
    }

    public static MealCompositionItem from(NutritionFood food, double quantity) {
        return from(food, quantity, food == null ? null : food.basisUnit);
    }

    public static MealCompositionItem from(
            NutritionFood food,
            double quantity,
            String quantityUnit
    ) {
        if (food == null) {
            throw new IllegalArgumentException("Food is required.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        double normalizedQuantity = NutritionUnit.convert(
                quantity,
                quantityUnit == null ? food.basisUnit : quantityUnit,
                food.basisUnit
        );
        return new MealCompositionItem(
                food,
                normalizedQuantity,
                NutritionCalculator.forQuantity(food, normalizedQuantity)
        );
    }

    public String label() {
        return food.identityLabel() + " · " + NutritionCalculator.trim(quantity)
                + NutritionUnit.display(food.basisUnit)
                + " · " + Math.round(calories) + "kcal";
    }

    /** 목록 두 번째 줄에 붙이는 상세 영양 요약. 모르는 값은 "?"로 남는다. */
    public String detailLabel() {
        return NutritionCalculator.trim(proteinGrams) + "g P · "
                + NutritionCalculator.trim(carbsGrams) + "g C · "
                + NutritionCalculator.trim(fatGrams) + "g F · 나트륨 "
                + NutritionCalculator.trimNullable(profile.sodiumMg()) + "mg · 포화지방 "
                + NutritionCalculator.trimNullable(profile.saturatedFatGrams()) + "g · 당류 "
                + NutritionCalculator.trimNullable(profile.sugarsGrams()) + "g";
    }
}
