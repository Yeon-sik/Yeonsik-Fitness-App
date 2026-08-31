package com.yeonsik.fitnessapp.exercise;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ExercisePrimaryMuscleLabelTest {
    @Test
    public void mapsCanonicalPrimarySubPartsToCompactLabels() {
        assertEquals("가슴", ExercisePrimaryMuscleLabel.forPrimarySubPart("upper_chest", "chest"));
        assertEquals("어깨", ExercisePrimaryMuscleLabel.forPrimarySubPart("rear_delts", "shoulders"));
        assertEquals("삼두", ExercisePrimaryMuscleLabel.forPrimarySubPart("triceps", "arms"));
        assertEquals("이두", ExercisePrimaryMuscleLabel.forPrimarySubPart("biceps", "arms"));
        assertEquals("하체", ExercisePrimaryMuscleLabel.forPrimarySubPart("hamstrings", "legs"));
        assertEquals("둔근", ExercisePrimaryMuscleLabel.forPrimarySubPart("glutes", "legs"));
    }

    @Test
    public void fallsBackToCanonicalBodyPartWhenSubPartIsNotMapped() {
        assertEquals("가슴", ExercisePrimaryMuscleLabel.forPrimarySubPart("unknown", "chest"));
        assertEquals("", ExercisePrimaryMuscleLabel.forPrimarySubPart("unknown", "unknown"));
    }
}
