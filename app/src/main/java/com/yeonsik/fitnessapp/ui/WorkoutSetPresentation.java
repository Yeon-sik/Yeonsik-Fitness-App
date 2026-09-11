package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.data.MassFormatter;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.exercise.LoadState;
import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens;

import java.util.List;

/** Pure set-input labels and comparison copy; independent from a screen implementation. */
public final class WorkoutSetPresentation {
    private WorkoutSetPresentation() {}

    public static String primaryInputLabel(String recordType, LoadState loadState) {
        return primaryInputLabel(recordType, loadState, MassUnit.KG);
    }

    public static String primaryInputLabel(String recordType, LoadState loadState, MassUnit unit) {
        if (!hasNumericLoad(loadState)) return isTimeRecordType(recordType) ? "초" : "횟수";
        String symbol = MassUnit.orDefault(unit).symbol();
        if (loadState == LoadState.ADDED_WEIGHT) return "추가 " + symbol;
        if (loadState == LoadState.ASSISTED) return "보조 " + symbol;
        if (loadState == LoadState.EXTERNAL_LOAD) return "중량 " + symbol;
        return isTimeRecordType(recordType) ? "초" : "횟수";
    }

    public static String primaryColumnHeaderLabel(String recordType, LoadState loadState) {
        if (!hasNumericLoad(loadState)) return isTimeRecordType(recordType) ? "초" : "횟수";
        if (loadState == LoadState.ADDED_WEIGHT) return "추가";
        if (loadState == LoadState.ASSISTED) return "보조";
        if (loadState == LoadState.EXTERNAL_LOAD) return "중량";
        return isTimeRecordType(recordType) ? "초" : "횟수";
    }

    public static String secondaryInputLabel(String recordType, LoadState loadState) {
        return hasSecondaryInput(recordType, loadState)
                ? (isTimeRecordType(recordType) ? "초" : "횟수") : "";
    }

    /**
     * Formats one persisted completed set for a read-only workout record.
     *
     * <p>All mass values arrive in canonical kilograms, just like the stored workout contract.
     * This method only formats the existing values; it never recalculates volume or changes the
     * meaning of a set.</p>
     */
    public static String completedSetSummary(
            String recordType,
            double weightKg,
            int actualReps,
            int durationSeconds,
            double assistedWeightKg,
            double addedWeightKg,
            LoadState loadState,
            MassUnit unit
    ) {
        String normalized = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.TIME.equals(normalized)) {
            return durationSeconds > 0 ? durationSeconds + "초" : "시간 미기록";
        }
        if (FitnessRecordContract.WEIGHT_TIME.equals(normalized)) {
            return loadLabel(weightKg, loadState, unit) + " × "
                    + (durationSeconds > 0 ? durationSeconds + "초" : "시간 미기록");
        }

        String repetition = actualReps > 0 ? actualReps + "회" : "횟수 미기록";
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(normalized)) {
            return numericOrLabel(assistedWeightKg, "보조 중량 미기록", unit) + " × " + repetition;
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(normalized)) {
            String added = addedWeightKg > 0.0
                    ? "체중 + " + MassFormatter.withUnit(addedWeightKg, unit)
                    : "체중";
            return added + " × " + repetition;
        }
        if (FitnessRecordContract.REPS_ONLY.equals(normalized)) {
            return "체중 × " + repetition;
        }
        return loadLabel(weightKg, loadState, unit) + " × " + repetition;
    }

    private static String loadLabel(double weightKg, LoadState loadState, MassUnit unit) {
        if (weightKg > 0.0) {
            return MassFormatter.withUnit(weightKg, unit);
        }
        if (loadState == LoadState.BODYWEIGHT) {
            return "체중";
        }
        if (loadState == LoadState.BAND_ASSISTED || loadState == LoadState.BAND_RESISTED) {
            return "밴드";
        }
        return "중량 미기록";
    }

    private static String numericOrLabel(double kilograms, String missingLabel, MassUnit unit) {
        return kilograms > 0.0 ? MassFormatter.withUnit(kilograms, unit) : missingLabel;
    }

    public static boolean hasSecondaryInput(String recordType, LoadState loadState) {
        return hasNumericLoad(loadState);
    }

    public static double sumVolumeKg(List<Double> setVolumes) {
        double total = 0d;
        for (Double volume : setVolumes) if (volume != null && Double.isFinite(volume)) total += volume;
        return total;
    }

    public static String totalVolumeComparisonMessage(double currentKg, double previousKg) {
        double delta = currentKg - previousKg;
        if (Math.abs(delta) < 0.0001d) return "전체 세트 기준, 지난 운동과 같은 볼륨이에요";
        return "전체 세트 기준, 지난 운동보다 " + FitnessUiTokens.formatVolume(Math.abs(delta))
                + " KG " + (delta < 0 ? "덜" : "더") + " 들었어요";
    }

    public static String totalVolumeComparisonMessage(double currentKg, double previousKg, MassUnit unit) {
        double delta = currentKg - previousKg;
        if (Math.abs(delta) < 0.0001d) return "전체 세트 기준, 지난 운동과 같은 볼륨이에요";
        return "전체 세트 기준, 지난 운동보다 " + MassFormatter.withUnit(Math.abs(delta), unit)
                + " " + (delta < 0 ? "덜" : "더") + " 들었어요";
    }

    private static boolean isTimeRecordType(String recordType) {
        String normalized = FitnessRecordContract.normalizeRecordType(recordType);
        return FitnessRecordContract.TIME.equals(normalized)
                || FitnessRecordContract.WEIGHT_TIME.equals(normalized);
    }

    private static boolean hasNumericLoad(LoadState state) {
        return state == LoadState.EXTERNAL_LOAD || state == LoadState.ADDED_WEIGHT
                || state == LoadState.ASSISTED;
    }
}
