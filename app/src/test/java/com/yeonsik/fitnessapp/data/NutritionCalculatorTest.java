package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class NutritionCalculatorTest {
    @Test
    public void scalesNutritionByConsumedAmount() {
        NutritionFood chicken = new NutritionFood(
                "chicken",
                "user",
                "Chicken breast",
                NutritionFood.KIND_INGREDIENT,
                100,
                "g",
                165,
                31,
                0,
                3.6,
                "manual",
                null
        );

        MealCompositionItem item = MealCompositionItem.from(chicken, 300);

        assertEquals(495, item.calories, 0.001);
        assertEquals(93, item.proteinGrams, 0.001);
        assertEquals(10.8, item.fatGrams, 0.001);
    }

    @Test
    public void sumsMultipleFoods() {
        NutritionFood rice = new NutritionFood(
                "rice",
                "user",
                "Rice",
                NutritionFood.KIND_INGREDIENT,
                100,
                "g",
                130,
                2.7,
                28,
                0.3,
                "manual",
                null
        );
        NutritionFood egg = new NutritionFood(
                "egg",
                "user",
                "Egg",
                NutritionFood.KIND_INGREDIENT,
                1,
                "개",
                70,
                6,
                0.5,
                5,
                "manual",
                null
        );

        NutritionCalculator.NutritionValues total = NutritionCalculator.sum(Arrays.asList(
                MealCompositionItem.from(rice, 200),
                MealCompositionItem.from(egg, 2)
        ));

        assertEquals(400, total.calories, 0.001);
        assertEquals(17.4, total.proteinGrams, 0.001);
        assertEquals(57, total.carbsGrams, 0.001);
        assertEquals(10.6, total.fatGrams, 0.001);
    }
}
