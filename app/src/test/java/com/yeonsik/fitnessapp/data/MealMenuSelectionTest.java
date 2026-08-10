package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MealMenuSelectionTest {
    @Test
    public void resizingComposedMenuScalesEveryIngredient() {
        NutritionFood rice = food("rice", "Rice", NutritionUnit.GRAM, 100, 130);
        NutritionFood egg = food("egg", "Egg", NutritionUnit.SERVING, 1, 70);
        MealCompositionItem rice200 = MealCompositionItem.from(rice, 200);
        MealCompositionItem eggs2 = MealCompositionItem.from(egg, 2);
        NutritionProfile recipeProfile = NutritionCalculator.recipeProfile(
                Arrays.asList(rice200, eggs2)
        );
        NutritionFood friedRice = NutritionFood.builder()
                .id("fried-rice")
                .ownerId("user")
                .name("Fried rice")
                .kind(NutritionFood.KIND_RECIPE)
                .category(NutritionFood.CATEGORY_RECIPE)
                .basis(1, NutritionUnit.SERVING)
                .profile(recipeProfile)
                .source("test", null)
                .build();

        MealMenuSelection doubled = MealMenuSelection.composed(
                MealCompositionItem.from(friedRice, 1),
                Arrays.asList(rice200, eggs2)
        ).withQuantity(2);

        assertEquals(2, doubled.menu.quantity, 0.0001);
        assertEquals(400, doubled.components.get(0).quantity, 0.0001);
        assertEquals(4, doubled.components.get(1).quantity, 0.0001);
        assertEquals(recipeProfile.calories() * 2, doubled.menu.calories, 0.0001);
    }

    @Test
    public void ingredientCanBeUsedAsStandaloneMenuByRelationship() {
        NutritionFood egg = food("egg", "Egg", NutritionUnit.SERVING, 1, 70);
        MealMenuSelection selection = MealMenuSelection.standalone(
                MealCompositionItem.from(egg, 2)
        );

        assertEquals(NutritionFood.KIND_INGREDIENT, selection.menu.food.kind);
        assertTrue(selection.components.isEmpty());
    }

    private static NutritionFood food(
            String id,
            String name,
            String unit,
            double basis,
            double calories
    ) {
        return NutritionFood.builder()
                .id(id)
                .ownerId("user")
                .name(name)
                .kind(NutritionFood.KIND_INGREDIENT)
                .basis(basis, unit)
                .profile(NutritionProfile.builder()
                        .value(NutritionProfile.CALORIES_KCAL, calories)
                        .value(NutritionProfile.PROTEIN_GRAMS, 1d)
                        .value(NutritionProfile.CARBS_GRAMS, 1d)
                        .value(NutritionProfile.FAT_GRAMS, 1d)
                        .build())
                .source("test", null)
                .build();
    }
}
