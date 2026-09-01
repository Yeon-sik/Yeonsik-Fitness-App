package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.FitnessRepository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WorkoutSummaryAnalyticsTest {
    @Test
    public void intensityUsesAbsoluteSetSaturation() {
        assertEquals(0.2212d,
                WorkoutSummaryAnalytics.intensityForEffectiveSets(1d), 0.0001d);
        assertEquals(0.6321d,
                WorkoutSummaryAnalytics.intensityForEffectiveSets(4d), 0.0001d);
        assertTrue(WorkoutSummaryAnalytics.intensityForEffectiveSets(8d)
                > WorkoutSummaryAnalytics.intensityForEffectiveSets(4d));
    }

    @Test
    public void percentageChangeHandlesIncreaseDecreaseAndZeroBaseline() {
        assertEquals("+25%", WorkoutSummaryAnalytics.formatChangePercent(80d, 100d));
        assertEquals("-25%", WorkoutSummaryAnalytics.formatChangePercent(80d, 60d));
        assertEquals("신규", WorkoutSummaryAnalytics.formatChangePercent(0d, 60d));
        assertEquals("—", WorkoutSummaryAnalytics.formatChangePercent(0d, 0d));
    }

    @Test
    public void primaryCompletedSetsAccumulateAtFullWeight() {
        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveMuscleScores(Arrays.asList(
                exercise("chest", Collections.emptyList(), true, true),
                exercise("chest", Collections.emptyList(), true)
        ));

        assertEquals(3d, scores.get("chest"), 0.0001d);
    }

    @Test
    public void secondaryCompletedSetsAccumulateAtHalfWeight() {
        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveMuscleScores(
                Collections.singletonList(exercise(
                        "chest", Collections.singletonList("triceps"), true, true, true, true))
        );

        assertEquals(4d, scores.get("chest"), 0.0001d);
        assertEquals(2d, scores.get("triceps"), 0.0001d);
    }

    @Test
    public void incompleteSetsAreExcluded() {
        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveMuscleScores(
                Collections.singletonList(exercise("chest", Collections.emptyList(), false))
        );

        assertTrue(scores.isEmpty());
    }

    @Test
    public void sameMuscleAcrossExercisesIsSummed() {
        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveMuscleScores(Arrays.asList(
                exercise("chest", Collections.singletonList("back"), true, true),
                exercise("back", Collections.singletonList("chest"), true, true)
        ));

        assertEquals(3d, scores.get("chest"), 0.0001d);
        assertEquals(3d, scores.get("back"), 0.0001d);
    }

    @Test
    public void primaryAndSecondarySameMuscleIsCountedOnce() {
        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveMuscleScores(
                Collections.singletonList(exercise(
                        "chest", Arrays.asList("chest", "chest"), true, true))
        );

        assertEquals(2d, scores.get("chest"), 0.0001d);
        assertFalse(scores.isEmpty());
    }

    private static WorkoutSummaryAnalytics.MuscleExercise exercise(
            String primary,
            List<String> secondary,
            boolean... completed
    ) {
        List<FitnessRepository.SessionSetEntry> sets = new java.util.ArrayList<>();
        for (int index = 0; index < completed.length; index++) {
            sets.add(new FitnessRepository.SessionSetEntry(
                    "set-" + index,
                    index,
                    0d,
                    0,
                    null,
                    null,
                    completed[index],
                    0,
                    0d,
                    0d,
                    0d
            ));
        }
        return new WorkoutSummaryAnalytics.MuscleExercise(
                primary,
                secondary,
                sets
        );
    }
}
