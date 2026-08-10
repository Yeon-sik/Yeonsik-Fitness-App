package com.yeonsik.fitnessapp.data;

/** 화면 표시와 검증에 사용하는 처방 없는 계산 규칙. */
public final class AthleteNutritionPolicy {
    /** ISSN 일반 운동인 일일 단백질 참고 범위 하한. 개인 목표를 대체하지 않는다. */
    public static final double GENERAL_PROTEIN_REFERENCE_LOW_G_PER_KG = 1.4;
    /** ISSN 일반 운동인 일일 단백질 참고 범위 상한. 개인 목표를 대체하지 않는다. */
    public static final double GENERAL_PROTEIN_REFERENCE_HIGH_G_PER_KG = 2.0;
    /** ISSN 1회 단백질 섭취 참고값. */
    public static final double PER_MEAL_PROTEIN_REFERENCE_G_PER_KG = 0.25;

    private AthleteNutritionPolicy() {
    }

    public static double progressRatio(double consumed, double target) {
        if (!Double.isFinite(consumed) || !Double.isFinite(target) || consumed <= 0 || target <= 0) {
            return 0;
        }
        return consumed / target;
    }

    public static double remaining(double consumed, double target) {
        if (!Double.isFinite(consumed) || !Double.isFinite(target) || target <= 0) {
            return 0;
        }
        return Math.max(0, target - Math.max(0, consumed));
    }

    public static double exceeded(double consumed, double target) {
        if (!Double.isFinite(consumed) || !Double.isFinite(target) || target <= 0) {
            return 0;
        }
        return Math.max(0, consumed - target);
    }

    public static Double proteinGramsPerKg(double proteinGrams, Double weightKg) {
        if (weightKg == null || !Double.isFinite(weightKg) || weightKg <= 0
                || !Double.isFinite(proteinGrams) || proteinGrams < 0) {
            return null;
        }
        return proteinGrams / weightKg;
    }

    public static Double perMealProteinReference(Double weightKg) {
        if (weightKg == null || !Double.isFinite(weightKg) || weightKg <= 0) {
            return null;
        }
        return weightKg * PER_MEAL_PROTEIN_REFERENCE_G_PER_KG;
    }
}
