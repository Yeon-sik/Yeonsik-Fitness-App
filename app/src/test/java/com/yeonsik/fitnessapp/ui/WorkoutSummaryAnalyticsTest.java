package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.FitnessRepository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    @Test
    public void overlappingPrimaryAndSecondaryLayerUsesPrimaryWeightOnce() {
        Map<String, List<String>> layers = new LinkedHashMap<>();
        layers.put("upper_chest", Collections.singletonList("pectoralis_major_clavicular"));
        layers.put("overall_chest", Collections.singletonList("pectoralis_major_clavicular"));

        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveAnatomicalLayerScores(
                Collections.singletonList(exercise(
                        "upper_chest",
                        Collections.singletonList("overall_chest"),
                        true
                )),
                layers
        );

        assertEquals(1d, scores.get("pectoralis_major_clavicular"), 0.0001d);
    }

    @Test
    public void nonOverlappingSecondaryLayerKeepsHalfWeight() {
        Map<String, List<String>> layers = new LinkedHashMap<>();
        layers.put("upper_chest", Collections.singletonList("pectoralis_major_clavicular"));
        layers.put("front_deltoid", Collections.singletonList("deltoid_anterior"));

        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveAnatomicalLayerScores(
                Collections.singletonList(exercise(
                        "upper_chest",
                        Collections.singletonList("front_deltoid"),
                        true
                )),
                layers
        );

        assertEquals(1d, scores.get("pectoralis_major_clavicular"), 0.0001d);
        assertEquals(0.5d, scores.get("deltoid_anterior"), 0.0001d);
    }

    @Test
    public void sameLayerAcrossExercisesIsSummed() {
        Map<String, List<String>> layers = new LinkedHashMap<>();
        layers.put("upper_chest", Collections.singletonList("pectoralis_major_clavicular"));
        layers.put("overall_chest", Collections.singletonList("pectoralis_major_clavicular"));

        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveAnatomicalLayerScores(
                Arrays.asList(
                        exercise("upper_chest", Collections.emptyList(), true),
                        exercise("overall_chest", Collections.emptyList(), true)
                ),
                layers
        );

        assertEquals(2d, scores.get("pectoralis_major_clavicular"), 0.0001d);
    }

    @Test
    public void multipleCompletedSetsAccumulatePerLayer() {
        Map<String, List<String>> layers = new LinkedHashMap<>();
        layers.put("upper_chest", Collections.singletonList("pectoralis_major_clavicular"));

        Map<String, Double> scores = WorkoutSummaryAnalytics.effectiveAnatomicalLayerScores(
                Collections.singletonList(exercise(
                        "upper_chest",
                        Collections.emptyList(),
                        true, false, true
                )),
                layers
        );

        assertEquals(2d, scores.get("pectoralis_major_clavicular"), 0.0001d);
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
