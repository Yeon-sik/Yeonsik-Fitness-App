package com.yeonsik.fitnessapp.data;

import java.time.LocalDate;

/** 날짜별 수분·회복 상태 기록. 점수는 1(매우 낮음)~5(매우 좋음)이다. */
public final class AthleteDailyCheckIn {
    public final String id;
    public final String date;
    public final int waterMl;
    public final Double sleepHours;
    public final Integer energyScore;
    public final Integer hungerScore;
    public final Integer digestionScore;
    public final Integer trainingReadinessScore;
    public final String note;

    public AthleteDailyCheckIn(
            String id,
            String date,
            int waterMl,
            Double sleepHours,
            Integer energyScore,
            Integer hungerScore,
            Integer digestionScore,
            Integer trainingReadinessScore,
            String note
    ) {
        this.id = id == null ? "" : id.trim();
        this.date = requireDate(date);
        if (waterMl < 0 || waterMl > 100_000) {
            throw new IllegalArgumentException("수분 섭취량은 0~100000ml 범위여야 합니다.");
        }
        this.waterMl = waterMl;
        this.sleepHours = requireSleepHours(sleepHours);
        this.energyScore = requireScore(energyScore, "에너지");
        this.hungerScore = requireScore(hungerScore, "허기");
        this.digestionScore = requireScore(digestionScore, "소화");
        this.trainingReadinessScore = requireScore(trainingReadinessScore, "훈련 준비도");
        this.note = note == null ? "" : note.trim();
    }

    public static AthleteDailyCheckIn empty(String date) {
        return new AthleteDailyCheckIn("", date, 0, null, null, null, null, null, "");
    }

    public AthleteDailyCheckIn withWaterAdded(int amountMl) {
        if (amountMl <= 0) {
            throw new IllegalArgumentException("추가 수분량은 0보다 커야 합니다.");
        }
        long total = (long) waterMl + amountMl;
        if (total > 100_000) {
            throw new IllegalArgumentException("수분 섭취량이 기록 범위를 넘었습니다.");
        }
        return new AthleteDailyCheckIn(
                id,
                date,
                (int) total,
                sleepHours,
                energyScore,
                hungerScore,
                digestionScore,
                trainingReadinessScore,
                note
        );
    }

    public boolean hasWellnessData() {
        return sleepHours != null
                || energyScore != null
                || hungerScore != null
                || digestionScore != null
                || trainingReadinessScore != null
                || !note.isEmpty();
    }

    public boolean isEmpty() {
        return waterMl == 0 && !hasWellnessData();
    }

    private static String requireDate(String date) {
        String normalized = date == null ? "" : date.trim();
        try {
            return LocalDate.parse(normalized).toString();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("체크인 날짜 형식이 올바르지 않습니다.", error);
        }
    }

    private static Double requireSleepHours(Double value) {
        if (value == null) {
            return null;
        }
        if (!Double.isFinite(value) || value < 0 || value > 24) {
            throw new IllegalArgumentException("수면 시간은 0~24시간 범위여야 합니다.");
        }
        return value;
    }

    private static Integer requireScore(Integer value, String label) {
        if (value == null) {
            return null;
        }
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException(label + " 점수는 1~5 범위여야 합니다.");
        }
        return value;
    }
}
