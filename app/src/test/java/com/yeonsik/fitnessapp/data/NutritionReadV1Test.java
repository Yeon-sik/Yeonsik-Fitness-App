package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NutritionReadV1Test {
    @Test
    public void exposesBasisNutrientsSourceAndRevision() {
        NutritionFood food = NutritionFood.builder()
                .id("nutrition-1")
                .ownerId("user-1")
                .name("닭가슴살")
                .kind(NutritionFood.KIND_INGREDIENT)
                .basis(100, "g")
                .prepState(NutritionFood.PREP_COOKED)
                .profile(NutritionProfile.builder()
                        .value(NutritionProfile.CALORIES_KCAL, 165.0)
                        .value(NutritionProfile.PROTEIN_GRAMS, 31.0)
                        .value(NutritionProfile.CARBS_GRAMS, 0.0)
                        .value(NutritionProfile.FAT_GRAMS, 3.6)
                        .value(NutritionProfile.SODIUM_MG, 74.0)
                        .value(NutritionProfile.SATURATED_FAT_GRAMS, 1.0)
                        .value(NutritionProfile.SUGARS_GRAMS, 0.0)
                        .build())
                .source("label", "제품 라벨")
                .sourceVersion("label-r3")
                .revision(7)
                .build();

        NutritionReadV1 read = NutritionReadV1.from(food);

        assertEquals(NutritionReadV1.CONTRACT_VERSION, "nutrition-read.v1");
        assertEquals(100, read.basisAmount, 0.001);
        assertEquals("g", read.basisUnit);
        assertEquals(165, read.nutritionValues.get(NutritionProfile.CALORIES_KCAL), 0.001);
        assertEquals("제품 라벨", read.sourceReference);
        assertEquals("label-r3", read.sourceRevision);
        assertEquals(7, read.revision);
    }

    @Test
    public void preservesUnknownNutrientsAsPresentNullValues() {
        NutritionFood legacy = new NutritionFood(
                "legacy", "user", "Legacy", NutritionFood.KIND_EXTERNAL_MENU,
                100, "g", 200, 10, 20, 5, "manual", null
        );

        NutritionReadV1 read = NutritionReadV1.from(legacy);

        assertTrue(read.nutritionValues.containsKey(NutritionProfile.SODIUM_MG));
        assertNull(read.nutritionValues.get(NutritionProfile.SODIUM_MG));
        assertFalse(read.micronutrients.containsKey(NutrientCode.VITAMIN_D));
    }
}
