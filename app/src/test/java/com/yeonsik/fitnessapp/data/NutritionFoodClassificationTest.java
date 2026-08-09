package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class NutritionFoodClassificationTest {
    @Test
    public void keepsFoodKindCategoryAndCookingMethodSeparate() {
        NutritionFood food = NutritionFood.builder()
                .name("오겹살")
                .kind(NutritionFood.KIND_INGREDIENT)
                .category(NutritionFood.CATEGORY_MEAT)
                .basis(100, NutritionUnit.GRAM)
                .cookingMethod(NutritionFood.COOKING_METHOD_GRILLED)
                .prepState(NutritionFood.PREP_COOKED)
                .build();

        assertEquals(NutritionFood.KIND_INGREDIENT, food.kind);
        assertEquals(NutritionFood.CATEGORY_MEAT, food.category);
        assertEquals(NutritionFood.COOKING_METHOD_GRILLED, food.cookingMethod);
        assertEquals("오겹살 · 육류 · 구이", food.identityLabel());
    }

    @Test
    public void mapsUnknownValuesToSafeDefaults() {
        assertEquals(
                NutritionFood.CATEGORY_OTHER,
                NutritionFood.normalizeCategory("unknown-category")
        );
        assertEquals(
                NutritionFood.COOKING_METHOD_UNSPECIFIED,
                NutritionFood.normalizeCookingMethod("unknown-method")
        );
    }

    @Test
    public void preservesLegacyPrepStateCompatibility() {
        assertEquals(
                NutritionFood.COOKING_METHOD_RAW,
                NutritionFood.cookingMethodForPrepState(NutritionFood.PREP_RAW)
        );
        assertEquals(
                NutritionFood.PREP_COOKED,
                NutritionFood.prepStateForCookingMethod(NutritionFood.COOKING_METHOD_BOILED)
        );
    }
}
