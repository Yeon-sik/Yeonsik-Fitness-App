package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 섭취 기록 스냅샷이 음식 DB 변경으로부터 과거 기록을 지켜 주는지 검증한다. */
public final class MealItemSnapshotTest {
    @Test
    public void snapshotsEveryNutrientAtConsumptionTime() {
        MealCompositionItem item = MealCompositionItem.from(
                NutritionCalculatorTest.fullyMeasuredChicken(),
                200
        );

        MealItemSnapshot snapshot = MealItemSnapshot.of(item, 0);
        Map<String, Double> columns = snapshot.typedNutritionColumns();

        assertEquals(330, columns.get(NutritionProfile.CALORIES_KCAL), 0.001);
        assertEquals(62, columns.get(NutritionProfile.PROTEIN_GRAMS), 0.001);
        assertEquals(148, columns.get(NutritionProfile.SODIUM_MG), 0.001);
        assertEquals(2, columns.get(NutritionProfile.SATURATED_FAT_GRAMS), 0.001);
        assertEquals(0, columns.get(NutritionProfile.SUGARS_GRAMS), 0.001);
        assertEquals(1.2, columns.get(NutritionProfile.FIBER_GRAMS), 0.001);
        assertEquals(170, columns.get(NutritionProfile.CHOLESTEROL_MG), 0.001);
    }

    @Test
    public void snapshotsBasisUnitPrepStateSourceAndVersion() {
        MealItemSnapshot snapshot = MealItemSnapshot.of(
                MealCompositionItem.from(NutritionCalculatorTest.fullyMeasuredChicken(), 150),
                3
        );

        assertEquals("Chicken breast", snapshot.foodNameSnapshot);
        assertEquals(NutritionFood.KIND_INGREDIENT, snapshot.foodKindSnapshot);
        assertEquals(150, snapshot.quantity, 0.001);
        assertEquals("g", snapshot.unit);
        assertEquals(100, snapshot.basisAmountSnapshot, 0.001);
        assertEquals("g", snapshot.basisUnitSnapshot);
        assertEquals(NutritionFood.PREP_RAW, snapshot.prepStateSnapshot);
        assertEquals("label", snapshot.sourceTypeSnapshot);
        assertEquals("제품 라벨", snapshot.sourceReferenceSnapshot);
        assertEquals("MFDS 2024-03", snapshot.sourceVersionSnapshot);
        assertEquals(NutritionFood.DATA_VERSION_REQUIRED_SEVEN, snapshot.foodDataVersionSnapshot);
        assertEquals(3, snapshot.orderIndex);
    }

    @Test
    public void keepsUnknownNutrientsNullSoTheyAreNotStoredAsZero() {
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

        Map<String, Double> columns = MealItemSnapshot
                .of(MealCompositionItem.from(macrosOnly, 100), 0)
                .typedNutritionColumns();

        // 키는 존재하지만 값이 null이라 호출부가 0 대신 NULL을 기록할 수 있다.
        assertTrue(columns.containsKey(NutritionProfile.SODIUM_MG));
        assertNull(columns.get(NutritionProfile.SODIUM_MG));
        assertNull(columns.get(NutritionProfile.FIBER_GRAMS));
        assertNotNull(columns.get(NutritionProfile.CALORIES_KCAL));
    }

    @Test
    public void writesOnlyMeasuredMicronutrientRows() {
        List<MealItemSnapshot.MicronutrientRow> rows = MealItemSnapshot
                .of(MealCompositionItem.from(NutritionCalculatorTest.fullyMeasuredChicken(), 100), 0)
                .micronutrientRows();

        assertEquals(4, rows.size());
        assertEquals(NutrientCode.CALCIUM, rows.get(0).nutrientCode);
        assertEquals(5, rows.get(0).amount, 0.001);
        assertEquals(NutrientCode.UNIT_MG, rows.get(0).unit);

        for (MealItemSnapshot.MicronutrientRow row : rows) {
            assertEquals(NutrientCode.unitOf(row.nutrientCode), row.unit);
        }
    }

    @Test
    public void snapshotDoesNotChangeWhenCatalogFoodIsLaterEdited() {
        NutritionFood original = NutritionCalculatorTest.fullyMeasuredChicken();
        MealItemSnapshot snapshot = MealItemSnapshot.of(
                MealCompositionItem.from(original, 100),
                0
        );

        // 이후 카탈로그에서 같은 음식의 값을 고쳐도(새 객체로 대체) 스냅샷은 그대로다.
        NutritionFood corrected = NutritionFood.builder()
                .id(original.id)
                .ownerId(original.ownerId)
                .name("Chicken breast (corrected)")
                .kind(original.kind)
                .basis(100, "g")
                .profile(NutritionProfile.builder()
                        .from(original.profile)
                        .value(NutritionProfile.SODIUM_MG, 999.0)
                        .build())
                .build();

        assertEquals(999, corrected.profile.sodiumMg(), 0.001);
        assertEquals(74, snapshot.profile.sodiumMg(), 0.001);
        assertEquals("Chicken breast", snapshot.foodNameSnapshot);
        assertEquals(original.id, snapshot.foodId);
    }

    @Test
    public void assignsSequentialOrderIndexes() {
        List<MealItemSnapshot> snapshots = MealItemSnapshot.of(Arrays.asList(
                MealCompositionItem.from(NutritionCalculatorTest.fullyMeasuredChicken(), 100),
                MealCompositionItem.from(NutritionCalculatorTest.fullyMeasuredChicken(), 50)
        ));

        assertEquals(2, snapshots.size());
        assertEquals(0, snapshots.get(0).orderIndex);
        assertEquals(1, snapshots.get(1).orderIndex);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyCompositionItem() {
        MealItemSnapshot.of((MealCompositionItem) null, 0);
    }
}
