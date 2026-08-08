package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

        NutritionTotals total = NutritionCalculator.sum(Arrays.asList(
                MealCompositionItem.from(rice, 200),
                MealCompositionItem.from(egg, 2)
        ));

        assertEquals(400, total.calories(), 0.001);
        assertEquals(17.4, total.proteinGrams(), 0.001);
        assertEquals(57, total.carbsGrams(), 0.001);
        assertEquals(10.6, total.fatGrams(), 0.001);
    }

    @Test
    public void scalesEveryRequiredAndRecommendedNutrient() {
        MealCompositionItem item = MealCompositionItem.from(fullyMeasuredChicken(), 250);

        // 250g / 100g 기준 = 2.5배
        assertEquals(412.5, item.profile.calories(), 0.001);
        assertEquals(77.5, item.profile.proteinGrams(), 0.001);
        assertEquals(185, item.profile.sodiumMg(), 0.001);
        assertEquals(2.5, item.profile.saturatedFatGrams(), 0.001);
        assertEquals(0, item.profile.sugarsGrams(), 0.001);
        assertEquals(1.5, item.profile.fiberGrams(), 0.001);
        assertEquals(0.25, item.profile.transFatGrams(), 0.001);
        assertEquals(212.5, item.profile.cholesterolMg(), 0.001);
    }

    @Test
    public void scalesMineralsAndVitamins() {
        MealCompositionItem item = MealCompositionItem.from(fullyMeasuredChicken(), 50);

        assertEquals(2.5, item.profile.micronutrient(NutrientCode.CALCIUM), 0.001);
        assertEquals(0.5, item.profile.micronutrient(NutrientCode.IRON), 0.001);
        assertEquals(0.15, item.profile.micronutrient(NutrientCode.VITAMIN_B12), 0.001);
        assertEquals(3, item.profile.micronutrient(NutrientCode.VITAMIN_C), 0.001);
    }

    @Test
    public void keepsUnmeasuredNutrientsUnknownInsteadOfZero() {
        NutritionFood macrosOnly = new NutritionFood(
                "legacy",
                "user",
                "Legacy food",
                NutritionFood.KIND_EXTERNAL_MENU,
                100,
                "g",
                200,
                10,
                20,
                5,
                "manual",
                null
        );

        MealCompositionItem item = MealCompositionItem.from(macrosOnly, 200);

        assertNull(item.profile.sodiumMg());
        assertNull(item.profile.fiberGrams());
        assertNull(item.profile.micronutrient(NutrientCode.CALCIUM));
        assertFalse(item.profile.isKnown(NutritionProfile.SODIUM_MG));
        assertFalse(item.profile.hasAllRequired());
    }

    @Test
    public void marksTotalIncompleteWhenAnyFoodIsMissingTheNutrient() {
        NutritionFood measured = fullyMeasuredChicken();
        NutritionFood unmeasured = new NutritionFood(
                "unknown-sodium",
                "user",
                "Unknown sodium food",
                NutritionFood.KIND_EXTERNAL_MENU,
                100,
                "g",
                100,
                5,
                10,
                2,
                "manual",
                null
        );

        NutritionTotals total = NutritionCalculator.sum(Arrays.asList(
                MealCompositionItem.from(measured, 100),
                MealCompositionItem.from(unmeasured, 100)
        ));

        NutritionTotals.Total sodium = total.total(NutritionProfile.SODIUM_MG);
        assertEquals(74, sodium.knownSum(), 0.001);
        assertEquals(1, sodium.knownCount());
        assertEquals(1, sodium.missingCount());
        assertFalse(sodium.isComplete());
        assertNull(sodium.completeValue());

        NutritionTotals.Total calories = total.total(NutritionProfile.CALORIES_KCAL);
        assertEquals(265, calories.knownSum(), 0.001);
        assertTrue(calories.isComplete());
    }

    @Test
    public void countsEarlierItemsAsMissingWhenNutrientAppearsLater() {
        NutritionFood unmeasured = new NutritionFood(
                "plain",
                "user",
                "Plain food",
                NutritionFood.KIND_EXTERNAL_MENU,
                100,
                "g",
                100,
                5,
                10,
                2,
                "manual",
                null
        );

        NutritionTotals total = NutritionCalculator.sum(Arrays.asList(
                MealCompositionItem.from(unmeasured, 100),
                MealCompositionItem.from(fullyMeasuredChicken(), 100)
        ));

        NutritionTotals.Total calcium = total.total(NutrientCode.CALCIUM);
        assertEquals(1, calcium.knownCount());
        assertEquals(1, calcium.missingCount());
        assertFalse(calcium.isComplete());
    }

    @Test
    public void recipeProfileKeepsOnlyFullyKnownNutrients() {
        NutritionFood unmeasured = new NutritionFood(
                "plain",
                "user",
                "Plain food",
                NutritionFood.KIND_EXTERNAL_MENU,
                100,
                "g",
                100,
                5,
                10,
                2,
                "manual",
                null
        );

        NutritionProfile recipe = NutritionCalculator.recipeProfile(Arrays.asList(
                MealCompositionItem.from(fullyMeasuredChicken(), 100),
                MealCompositionItem.from(unmeasured, 100)
        ));

        assertEquals(265, recipe.calories(), 0.001);
        assertEquals(36, recipe.proteinGrams(), 0.001);
        assertNull(recipe.sodiumMg());
        assertNull(recipe.micronutrient(NutrientCode.CALCIUM));
    }

    @Test
    public void recipeOfFullyMeasuredFoodsSumsEveryNutrient() {
        NutritionProfile recipe = NutritionCalculator.recipeProfile(Arrays.asList(
                MealCompositionItem.from(fullyMeasuredChicken(), 100),
                MealCompositionItem.from(fullyMeasuredChicken(), 200)
        ));

        assertTrue(recipe.hasAllRequired());
        assertEquals(495, recipe.calories(), 0.001);
        assertEquals(222, recipe.sodiumMg(), 0.001);
        assertEquals(15, recipe.micronutrient(NutrientCode.CALCIUM), 0.001);
    }

    @Test
    public void describesPartialTotalsWithoutPretendingTheyAreExact() {
        NutritionTotals.Total partial = NutritionTotals.builder()
                .add(NutritionProfile.builder().value(NutritionProfile.SODIUM_MG, 100.0).build())
                .add(NutritionProfile.ofMacros(10, 1, 1, 1))
                .build()
                .total(NutritionProfile.SODIUM_MG);

        assertEquals("≥100 (미상 1건)", NutritionCalculator.describeTotal(partial));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroBasisAmount() {
        NutritionCalculator.forQuantity(
                NutritionFood.builder()
                        .id("bad")
                        .name("Bad basis")
                        .basis(0, "g")
                        .profile(NutritionProfile.ofMacros(1, 1, 1, 1))
                        .build(),
                100
        );
    }

    static NutritionFood fullyMeasuredChicken() {
        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, 165.0)
                .value(NutritionProfile.PROTEIN_GRAMS, 31.0)
                .value(NutritionProfile.CARBS_GRAMS, 0.0)
                .value(NutritionProfile.FAT_GRAMS, 3.6)
                .value(NutritionProfile.SODIUM_MG, 74.0)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, 1.0)
                .value(NutritionProfile.SUGARS_GRAMS, 0.0)
                .value(NutritionProfile.FIBER_GRAMS, 0.6)
                .value(NutritionProfile.TRANS_FAT_GRAMS, 0.1)
                .value(NutritionProfile.CHOLESTEROL_MG, 85.0)
                .micronutrient(NutrientCode.CALCIUM, 5.0)
                .micronutrient(NutrientCode.IRON, 1.0)
                .micronutrient(NutrientCode.VITAMIN_B12, 0.3)
                .micronutrient(NutrientCode.VITAMIN_C, 6.0)
                .build();

        return NutritionFood.builder()
                .id("chicken-full")
                .ownerId("user")
                .name("Chicken breast")
                .kind(NutritionFood.KIND_INGREDIENT)
                .basis(100, "g")
                .prepState(NutritionFood.PREP_RAW)
                .profile(profile)
                .source("label", "제품 라벨")
                .sourceVersion("MFDS 2024-03")
                .dataVersion(NutritionFood.DATA_VERSION_REQUIRED_SEVEN)
                .build();
    }
}
