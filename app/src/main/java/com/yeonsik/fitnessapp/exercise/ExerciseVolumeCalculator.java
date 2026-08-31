package com.yeonsik.fitnessapp.exercise;

import com.yeonsik.fitnessapp.data.FitnessRecordContract;

import java.util.Locale;

/**
 * Central load-accounting contract for anaerobic exercise volume.
 *
 * <p>The entered load remains the user's per-implement/per-side value. Side and implement
 * multipliers are applied only to the derived volume value.</p>
 */
public final class ExerciseVolumeCalculator {
    public static final int DEFAULT_IMPLEMENT_MULTIPLIER = 1;

    private ExerciseVolumeCalculator() {
    }

    public static double calculate(
            String recordType,
            LoadState loadState,
            double weightKg,
            double addedWeightKg,
            int reps,
            String laterality,
            int implementMultiplier
    ) {
        String normalizedType = FitnessRecordContract.normalizeRecordType(recordType);
        if (!FitnessRecordContract.WEIGHT_REPS.equals(normalizedType)
                && !FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(normalizedType)) {
            return 0d;
        }

        LoadState effectiveLoadState = loadState;
        if (effectiveLoadState == null) {
            effectiveLoadState = FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(normalizedType)
                    && addedWeightKg > 0d
                    ? LoadState.ADDED_WEIGHT
                    : FitnessRecordContract.WEIGHT_REPS.equals(normalizedType)
                    ? LoadState.EXTERNAL_LOAD
                    : LoadState.BODYWEIGHT;
        }

        double enteredWeight;
        if (effectiveLoadState == LoadState.EXTERNAL_LOAD) {
            enteredWeight = weightKg;
        } else if (effectiveLoadState == LoadState.ADDED_WEIGHT) {
            enteredWeight = addedWeightKg;
        } else {
            // Assistance, bands, and bodyweight deliberately do not become fake kg volume.
            return 0d;
        }
        if (!Double.isFinite(enteredWeight) || enteredWeight < 0d || reps <= 0) {
            return 0d;
        }

        int sideMultiplier = sideMultiplier(laterality);
        int normalizedImplementMultiplier = normalizeImplementMultiplier(implementMultiplier);
        return enteredWeight * reps * sideMultiplier * normalizedImplementMultiplier;
    }

    public static int sideMultiplier(String laterality) {
        return "unilateral".equals(normalize(laterality)) ? 2 : 1;
    }

    public static int normalizeImplementMultiplier(int implementMultiplier) {
        return implementMultiplier > 0 ? implementMultiplier : DEFAULT_IMPLEMENT_MULTIPLIER;
    }

    public static int totalMultiplier(String laterality, int implementMultiplier) {
        return sideMultiplier(laterality) * normalizeImplementMultiplier(implementMultiplier);
    }

    public static String formulaLabel(String laterality, int implementMultiplier) {
        int multiplier = totalMultiplier(laterality, implementMultiplier);
        return multiplier == 1
                ? "중량 × 횟수"
                : "중량 × 횟수 × " + multiplier;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
