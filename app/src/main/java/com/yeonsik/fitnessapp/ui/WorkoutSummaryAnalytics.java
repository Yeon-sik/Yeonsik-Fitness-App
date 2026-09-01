package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure summary calculations shared by the renderer and the workout summary screen. */
public final class WorkoutSummaryAnalytics {
    private static final double EFFECTIVE_SET_SATURATION = 4.0d;

    private WorkoutSummaryAnalytics() {
    }

    /** Saturates absolute effective sets so equal workloads have comparable intensity. */
    public static double intensityForEffectiveSets(double effectiveSets) {
        if (!Double.isFinite(effectiveSets) || effectiveSets <= 0d) {
            return 0d;
        }
        return Math.min(1d, 1d - Math.exp(-effectiveSets / EFFECTIVE_SET_SATURATION));
    }

    public static String formatChangePercent(double previous, double current) {
        if (!Double.isFinite(previous) || !Double.isFinite(current)) {
            return "—";
        }
        if (previous == 0d) {
            return current == 0d ? "—" : "신규";
        }
        double percentage = Math.round(
                (current - previous) / Math.abs(previous) * 1000d
        ) / 10d;
        String number = percentage == Math.rint(percentage)
                ? String.valueOf((long) percentage)
                : String.format(Locale.ROOT, "%.1f", percentage);
        return (percentage >= 0d ? "+" : "") + number + "%";
    }

    /**
     * Converts completed sets and the runtime exercise catalog into effective muscle sets.
     * Repository rows are kept at the boundary; the aggregation itself remains pure.
     */
    public static Map<String, Double> effectiveMuscleScores(
            Iterable<FitnessRepository.SessionExerciseEntry> exercises,
            Map<String, ? extends List<FitnessRepository.SessionSetEntry>> setsByExercise,
            RuntimeExerciseCatalog catalog
    ) {
        if (exercises == null || catalog == null) {
            return Collections.emptyMap();
        }
        List<MuscleExercise> completedExercises = new ArrayList<>();
        for (FitnessRepository.SessionExerciseEntry exercise : exercises) {
            if (exercise == null) {
                continue;
            }
            List<FitnessRepository.SessionSetEntry> sets = setsByExercise == null
                    ? Collections.emptyList()
                    : setsByExercise.get(exercise.id);
            RuntimeExercisePreset preset = resolvePreset(catalog, exercise);
            if (preset != null) {
                completedExercises.add(new MuscleExercise(
                        preset.primarySubPart,
                        preset.secondarySubParts,
                        sets
                ));
            }
        }
        return effectiveMuscleScores(completedExercises);
    }

    /** Pure effective-set aggregation used by the repository-facing overload and unit tests. */
    public static Map<String, Double> effectiveMuscleScores(List<MuscleExercise> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        for (MuscleExercise exercise : exercises) {
            int completedSetCount = exercise == null
                    ? 0
                    : completedSetCount(exercise.sets);
            if (completedSetCount <= 0) {
                continue;
            }
            String primary = normalizeMuscle(exercise.primarySubPart);
            addMuscleScore(scores, primary, completedSetCount);

            List<String> countedSecondary = new ArrayList<>();
            if (exercise.secondarySubParts == null) {
                continue;
            }
            for (String secondaryValue : exercise.secondarySubParts) {
                String secondary = normalizeMuscle(secondaryValue);
                if (secondary.isEmpty()
                        || secondary.equals(primary)
                        || countedSecondary.contains(secondary)) {
                    continue;
                }
                countedSecondary.add(secondary);
                addMuscleScore(scores, secondary, completedSetCount * 0.5d);
            }
        }
        return scores;
    }

    private static int completedSetCount(List<FitnessRepository.SessionSetEntry> sets) {
        if (sets == null || sets.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (FitnessRepository.SessionSetEntry set : sets) {
            if (set != null && set.isCompleted) {
                count += 1;
            }
        }
        return count;
    }

    private static RuntimeExercisePreset resolvePreset(
            RuntimeExerciseCatalog catalog,
            FitnessRepository.SessionExerciseEntry exercise
    ) {
        if (catalog == null || exercise == null) {
            return null;
        }
        if (exercise.familyIdentity != null) {
            RuntimeExercisePreset preset = catalog.preset(exercise.familyIdentity.presetId);
            if (preset == null) {
                preset = catalog.preset(exercise.familyIdentity.canonicalPresetId);
            }
            if (preset == null) {
                preset = catalog.presetForStorageExerciseId(
                        exercise.familyIdentity.legacyExerciseId
                );
            }
            if (preset != null) {
                return preset;
            }
        }
        RuntimeExercisePreset preset = catalog.presetForStorageExerciseId(exercise.exerciseId);
        return preset == null ? catalog.presetForExactName(exercise.name) : preset;
    }

    private static void addMuscleScore(Map<String, Double> scores, String muscle, double amount) {
        if (muscle.isEmpty() || amount <= 0d) {
            return;
        }
        scores.put(muscle, scores.getOrDefault(muscle, 0d) + amount);
    }

    private static String normalizeMuscle(String muscle) {
        return muscle == null ? "" : muscle.trim();
    }

    public static final class MuscleExercise {
        public final String primarySubPart;
        public final List<String> secondarySubParts;
        public final List<FitnessRepository.SessionSetEntry> sets;

        public MuscleExercise(
                String primarySubPart,
                List<String> secondarySubParts,
                List<FitnessRepository.SessionSetEntry> sets
        ) {
            this.primarySubPart = primarySubPart;
            this.secondarySubParts = secondarySubParts == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(secondarySubParts));
            this.sets = sets == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(sets));
        }
    }
}
