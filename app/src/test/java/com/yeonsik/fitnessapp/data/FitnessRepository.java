package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.exercise.LoadState;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Test-only compatibility surface for legacy contract tests.
 *
 * Production code uses bounded repositories and application services. The
 * Android instrumentation source set owns the full legacy fixture because
 * those tests exercise historical database compatibility directly.
 */
public final class FitnessRepository {
    private FitnessRepository() {
    }

    public static final class SetInput {
        public final Double weightKg;
        public final Integer reps;
        public final Integer durationSeconds;
        public final Double distanceMeters;
        public final Double assistedWeightKg;
        public final Double addedWeightKg;
        public final Integer rir;
        public final Integer restSeconds;
        public final boolean completed;
        public final LoadState loadState;
        public final Double inputLoadValue;
        public final MassUnit inputLoadUnit;

        public SetInput(Double weightKg, Integer reps, Integer durationSeconds,
                        Double assistedWeightKg, Double addedWeightKg, Integer rir,
                        Integer restSeconds, boolean completed) {
            this(weightKg, reps, durationSeconds, null, assistedWeightKg, addedWeightKg,
                    rir, restSeconds, completed, null, null, null);
        }

        public SetInput(Double weightKg, Integer reps, Integer durationSeconds,
                        Double distanceMeters, Double assistedWeightKg, Double addedWeightKg,
                        Integer rir, Integer restSeconds, boolean completed) {
            this(weightKg, reps, durationSeconds, distanceMeters, assistedWeightKg, addedWeightKg,
                    rir, restSeconds, completed, null, null, null);
        }

        public SetInput(Double weightKg, Integer reps, Integer durationSeconds,
                        Double assistedWeightKg, Double addedWeightKg, Integer rir,
                        Integer restSeconds, boolean completed, LoadState loadState) {
            this(weightKg, reps, durationSeconds, null, assistedWeightKg, addedWeightKg,
                    rir, restSeconds, completed, loadState, null, null);
        }

        public SetInput(Double weightKg, Integer reps, Integer durationSeconds,
                        Double distanceMeters, Double assistedWeightKg, Double addedWeightKg,
                        Integer rir, Integer restSeconds, boolean completed,
                        LoadState loadState) {
            this(weightKg, reps, durationSeconds, distanceMeters, assistedWeightKg, addedWeightKg,
                    rir, restSeconds, completed, loadState, null, null);
        }

        public SetInput(Double weightKg, Integer reps, Integer durationSeconds,
                        Double distanceMeters, Double assistedWeightKg, Double addedWeightKg,
                        Integer rir, Integer restSeconds, boolean completed,
                        LoadState loadState, Double inputLoadValue, MassUnit inputLoadUnit) {
            this.weightKg = weightKg;
            this.reps = reps;
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
            this.assistedWeightKg = assistedWeightKg;
            this.addedWeightKg = addedWeightKg;
            this.rir = rir;
            this.restSeconds = restSeconds;
            this.completed = completed;
            this.loadState = loadState;
            this.inputLoadValue = inputLoadValue;
            this.inputLoadUnit = inputLoadUnit;
        }
    }

    public static void validateSetInputForRecordType(String recordType, SetInput input) {
        if (input == null) throw new IllegalArgumentException("세트 입력이 없습니다.");
        validateNonNegative(input.weightKg, "중량");
        validateNonNegative(input.distanceMeters, "거리");
        validateNonNegative(input.assistedWeightKg, "보조 중량");
        validateNonNegative(input.addedWeightKg, "추가 중량");
        if (input.reps != null && input.reps < 0) throw new IllegalArgumentException("횟수는 음수일 수 없습니다.");
        if (input.durationSeconds != null && input.durationSeconds < 0) throw new IllegalArgumentException("시간은 음수일 수 없습니다.");
        if (input.restSeconds != null && input.restSeconds < 0) throw new IllegalArgumentException("휴식 시간은 음수일 수 없습니다.");
        if (input.rir != null && (input.rir < 0 || input.rir > 5)) throw new IllegalArgumentException("RIR는 0부터 5 사이여야 합니다.");
        if (!input.completed) return;
        String normalized = FitnessRecordContract.normalizeRecordType(recordType);
        switch (normalized) {
            case FitnessRecordContract.REPS_ONLY:
                requirePositive(input.reps, "횟수");
                break;
            case FitnessRecordContract.TIME:
                requirePositive(input.durationSeconds, "시간");
                break;
            case FitnessRecordContract.WEIGHT_TIME:
                requirePositive(input.weightKg, "중량");
                requirePositive(input.durationSeconds, "시간");
                break;
            case FitnessRecordContract.ASSISTED_WEIGHT_REPS:
                requirePositive(input.assistedWeightKg, "보조 중량");
                requirePositive(input.reps, "횟수");
                break;
            case FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS:
                validateNonNegative(input.addedWeightKg, "추가 중량");
                requirePositive(input.reps, "횟수");
                break;
            case FitnessRecordContract.WEIGHT_REPS:
            default:
                requirePositive(input.weightKg, "중량");
                requirePositive(input.reps, "횟수");
                break;
        }
        if (input.loadState == LoadState.EXTERNAL_LOAD) requirePositive(input.weightKg, "중량");
    }

    private static void requirePositive(Number value, String label) {
        if (value == null || value.doubleValue() <= 0d) {
            throw new IllegalArgumentException(label + "은 0보다 커야 합니다.");
        }
    }

    private static void validateNonNegative(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value < 0d)) {
            throw new IllegalArgumentException(label + "은 0 이상의 유한한 값이어야 합니다.");
        }
    }

    static String normalizeMealMenuPresetName(String name) {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("메뉴 이름을 입력하세요.");
        return name.trim();
    }

    static String requireRecordDate(String date) {
        if (date == null || date.trim().isEmpty()) throw new IllegalArgumentException("날짜를 입력하세요.");
        String normalized = date.trim();
        try {
            LocalDate.parse(normalized);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다.", error);
        }
        return normalized;
    }

    static double requireBodyWeight(double weightKg) {
        if (!Double.isFinite(weightKg) || weightKg <= 0d) {
            throw new IllegalArgumentException("체중은 0보다 커야 합니다.");
        }
        return weightKg;
    }

    public static final class MealComponentEntry {
        public final String id;
        public final String foodName;
        public final double quantity;
        public final String unit;
        public final Double calories;
        public final Double proteinGrams;
        public final Double carbsGrams;
        public final Double fatGrams;
        public final Double consumedFraction;
        public final String compositionGroupKey;
        public final String compositionGroupType;
        public final String compositionRole;
        public final String compositionMemberId;
        public final String provisionType;

        public MealComponentEntry(String id, String foodName, double quantity, String unit,
                                  Double calories, Double proteinGrams, Double carbsGrams,
                                  Double fatGrams, Double sodiumMg, Double saturatedFatGrams,
                                  Double sugarsGrams, String compositionGroupKey,
                                  String compositionGroupType, String compositionRole,
                                  String compositionMemberId, Double consumedFraction,
                                  String provisionType) {
            this.id = id;
            this.foodName = foodName;
            this.quantity = quantity;
            this.unit = unit;
            this.calories = calories;
            this.proteinGrams = proteinGrams;
            this.carbsGrams = carbsGrams;
            this.fatGrams = fatGrams;
            this.consumedFraction = consumedFraction;
            this.compositionGroupKey = compositionGroupKey;
            this.compositionGroupType = compositionGroupType;
            this.compositionRole = compositionRole;
            this.compositionMemberId = compositionMemberId;
            this.provisionType = provisionType;
        }

        public boolean hasExplicitConsumedFraction() {
            return consumedFraction != null;
        }

        public double percentage() {
            return (consumedFraction == null ? 1d : consumedFraction) * 100d;
        }

        public String label() {
            String caloriesLabel = calories == null ? "?" : trim(calories);
            return foodName + " · " + trim(quantity) + unit + " · " + caloriesLabel + "kcal";
        }

        public String provisionDisplayLabel() {
            if (provisionType == null || DiningOutProvisionType.INCLUDED.value().equals(provisionType)) return "";
            return CompositionGroupType.labelOf(compositionGroupType)
                    + " · " + DiningOutProvisionType.labelOf(provisionType);
        }

        private static String trim(double value) {
            return value == Math.rint(value)
                    ? Long.toString((long) value)
                    : Double.toString(value);
        }
    }
}
