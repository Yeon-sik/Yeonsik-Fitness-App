package com.yeonsik.fitnessapp.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 사용자가 직접 정한 일일 영양 목표.
 *
 * <p>이 객체는 목표를 계산하거나 처방하지 않는다. 의료 상태, 훈련량, 발한량을 모르는 앱이
 * 임의의 숫자를 정하지 않도록 모든 값은 사용자가 명시적으로 입력해야 한다.</p>
 */
public final class AthleteNutritionGoal {
    public static final String PHASE_GAIN = "gain";
    public static final String PHASE_MAINTENANCE = "maintenance";
    public static final String PHASE_FAT_LOSS = "fat_loss";
    public static final String PHASE_CONTEST_PREP = "contest_prep";

    public static final List<String> PHASES = Collections.unmodifiableList(Arrays.asList(
            PHASE_GAIN,
            PHASE_MAINTENANCE,
            PHASE_FAT_LOSS,
            PHASE_CONTEST_PREP
    ));

    public final String phase;
    public final double caloriesKcal;
    public final double proteinGrams;
    public final double carbsGrams;
    public final double fatGrams;
    public final double fiberGrams;
    public final double sodiumMg;
    public final int waterMl;

    public AthleteNutritionGoal(
            String phase,
            double caloriesKcal,
            double proteinGrams,
            double carbsGrams,
            double fatGrams,
            double fiberGrams,
            double sodiumMg,
            int waterMl
    ) {
        this.phase = normalizePhase(phase);
        this.caloriesKcal = requirePositiveFinite(caloriesKcal, "열량");
        this.proteinGrams = requirePositiveFinite(proteinGrams, "단백질");
        this.carbsGrams = requirePositiveFinite(carbsGrams, "탄수화물");
        this.fatGrams = requirePositiveFinite(fatGrams, "지방");
        this.fiberGrams = requirePositiveFinite(fiberGrams, "식이섬유");
        this.sodiumMg = requirePositiveFinite(sodiumMg, "나트륨");
        if (waterMl <= 0) {
            throw new IllegalArgumentException("수분 목표는 0보다 커야 합니다.");
        }
        this.waterMl = waterMl;
    }

    public String phaseLabel() {
        return phaseLabel(phase);
    }

    public static String phaseLabel(String phase) {
        switch (normalizePhase(phase)) {
            case PHASE_GAIN:
                return "증량";
            case PHASE_FAT_LOSS:
                return "감량";
            case PHASE_CONTEST_PREP:
                return "대회 준비";
            default:
                return "유지";
        }
    }

    public static String normalizePhase(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.US);
        return PHASES.contains(normalized) ? normalized : PHASE_MAINTENANCE;
    }

    private static double requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(label + " 목표는 0보다 큰 숫자여야 합니다.");
        }
        return value;
    }
}
