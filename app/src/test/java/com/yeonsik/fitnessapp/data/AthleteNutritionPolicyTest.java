package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class AthleteNutritionPolicyTest {
    @Test
    public void goalKeepsUserTargetsWithoutAutomaticPrescription() {
        AthleteNutritionGoal goal = new AthleteNutritionGoal(
                AthleteNutritionGoal.PHASE_GAIN,
                3200,
                190,
                420,
                85,
                35,
                3000,
                4000
        );

        assertEquals("증량", goal.phaseLabel());
        assertEquals(3200, goal.caloriesKcal, 0.001);
        assertEquals(4000, goal.waterMl);
    }

    @Test(expected = IllegalArgumentException.class)
    public void goalRejectsMissingPositiveTarget() {
        new AthleteNutritionGoal(
                AthleteNutritionGoal.PHASE_MAINTENANCE,
                2500,
                0,
                300,
                70,
                30,
                2500,
                3000
        );
    }

    @Test
    public void progressSeparatesRemainingFromExceeded() {
        assertEquals(0.75, AthleteNutritionPolicy.progressRatio(150, 200), 0.001);
        assertEquals(50, AthleteNutritionPolicy.remaining(150, 200), 0.001);
        assertEquals(0, AthleteNutritionPolicy.exceeded(150, 200), 0.001);

        assertEquals(0, AthleteNutritionPolicy.remaining(230, 200), 0.001);
        assertEquals(30, AthleteNutritionPolicy.exceeded(230, 200), 0.001);
    }

    @Test
    public void proteinReferencesRequireARecordedWeight() {
        assertEquals(2.0, AthleteNutritionPolicy.proteinGramsPerKg(160, 80.0), 0.001);
        assertEquals(20.0, AthleteNutritionPolicy.perMealProteinReference(80.0), 0.001);
        assertNull(AthleteNutritionPolicy.proteinGramsPerKg(160, null));
        assertNull(AthleteNutritionPolicy.perMealProteinReference(0.0));
    }

    @Test
    public void checkInPreservesOptionalWellnessAndAddsWater() {
        AthleteDailyCheckIn empty = AthleteDailyCheckIn.empty("2026-08-10");
        assertFalse(empty.hasWellnessData());
        assertTrue(empty.isEmpty());

        AthleteDailyCheckIn updated = new AthleteDailyCheckIn(
                "id",
                "2026-08-10",
                1000,
                7.5,
                4,
                3,
                5,
                4,
                "훈련 양호"
        ).withWaterAdded(500);

        assertTrue(updated.hasWellnessData());
        assertFalse(updated.isEmpty());
        assertEquals(1500, updated.waterMl);
        assertEquals(7.5, updated.sleepHours, 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void checkInRejectsOutOfRangeScore() {
        new AthleteDailyCheckIn(
                "",
                "2026-08-10",
                0,
                null,
                6,
                null,
                null,
                null,
                ""
        );
    }
}
