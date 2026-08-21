package com.yeonsik.fitnessapp.development;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 최근 앱 기록을 논문 기반 조언 엔진이 읽는 정규화된 입력 snapshot. */
public final class PaperAdviceInput {
    public final LocalDate referenceDate;
    public final String goal;
    public final Integer trainingAgeMonths;
    public final Double bodyWeightKg;
    public final Double proteinGPerKg;
    public final int proteinRecordedDays;
    public final int proteinWindowDays;
    public final int mealCount;
    public final int estimatedMealCount;
    public final Double sleepHours;
    public final int sleepRecordedDays;
    public final int lowEnergyOrReadinessDays;
    public final Integer energyScore;
    public final Integer readinessScore;
    public final Double currentWeight7DayAverageKg;
    public final int currentWeightRecordedDays;
    public final Double previousWeight7DayAverageKg;
    public final int previousWeightRecordedDays;
    public final Double weeklyWeightChangePct;
    public final int resistanceTrainingSessionsPerWeek;
    public final Map<String, Double> weeklyHardSetsPerMuscle;
    public final Double failureSetsRatio;
    public final Boolean coldWaterImmediatelyPostResistance;
    public final Boolean painReported;
    public final int recentDataDays;

    private PaperAdviceInput(Builder builder) {
        this.referenceDate = builder.referenceDate;
        this.goal = normalize(builder.goal);
        this.trainingAgeMonths = builder.trainingAgeMonths;
        this.bodyWeightKg = builder.bodyWeightKg;
        this.proteinGPerKg = builder.proteinGPerKg;
        this.proteinRecordedDays = builder.proteinRecordedDays;
        this.proteinWindowDays = builder.proteinWindowDays;
        this.mealCount = builder.mealCount;
        this.estimatedMealCount = builder.estimatedMealCount;
        this.sleepHours = builder.sleepHours;
        this.sleepRecordedDays = builder.sleepRecordedDays;
        this.lowEnergyOrReadinessDays = builder.lowEnergyOrReadinessDays;
        this.energyScore = builder.energyScore;
        this.readinessScore = builder.readinessScore;
        this.currentWeight7DayAverageKg = builder.currentWeight7DayAverageKg;
        this.currentWeightRecordedDays = builder.currentWeightRecordedDays;
        this.previousWeight7DayAverageKg = builder.previousWeight7DayAverageKg;
        this.previousWeightRecordedDays = builder.previousWeightRecordedDays;
        this.weeklyWeightChangePct = builder.weeklyWeightChangePct;
        this.resistanceTrainingSessionsPerWeek = builder.resistanceTrainingSessionsPerWeek;
        this.weeklyHardSetsPerMuscle = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.weeklyHardSetsPerMuscle)
        );
        this.failureSetsRatio = builder.failureSetsRatio;
        this.coldWaterImmediatelyPostResistance = builder.coldWaterImmediatelyPostResistance;
        this.painReported = builder.painReported;
        this.recentDataDays = builder.recentDataDays;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasGoal(String expected) {
        return expected != null && expected.equals(goal);
    }

    public boolean hasRecentData() {
        return recentDataDays > 0;
    }

    private void validate() {
        if (referenceDate == null) {
            throw new IllegalArgumentException("기준일이 필요합니다.");
        }
        requireNonNegative(trainingAgeMonths, "훈련 경력");
        requirePositive(bodyWeightKg, "체중");
        requireRange(proteinGPerKg, 0d, 10d, "단백질 섭취량");
        requireRange(sleepHours, 0d, 24d, "수면 시간");
        requirePositive(currentWeight7DayAverageKg, "최근 7일 평균 체중");
        requirePositive(previousWeight7DayAverageKg, "이전 7일 평균 체중");
        requireRange(weeklyWeightChangePct, -100d, 100d, "주간 체중 변화율");
        requireScore(energyScore, "에너지");
        requireScore(readinessScore, "준비도");
        if (resistanceTrainingSessionsPerWeek < 0 || resistanceTrainingSessionsPerWeek > 100) {
            throw new IllegalArgumentException("주간 저항운동 횟수 범위가 올바르지 않습니다.");
        }
        for (Map.Entry<String, Double> entry : weeklyHardSetsPerMuscle.entrySet()) {
            if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("근육군 식별자가 필요합니다.");
            }
            requireRange(entry.getValue(), 0d, 1_000d, "주당 유효 세트");
        }
        requireRange(failureSetsRatio, 0d, 1d, "실패 세트 비율");
        if (proteinRecordedDays < 0 || proteinRecordedDays > 366) {
            throw new IllegalArgumentException("단백질 기록일 수 범위가 올바르지 않습니다.");
        }
        if (proteinWindowDays < 1 || proteinWindowDays > 366
                || proteinRecordedDays > proteinWindowDays) {
            throw new IllegalArgumentException("단백질 관찰 기간 범위가 올바르지 않습니다.");
        }
        requireCount(mealCount, "식사 수");
        requireCount(estimatedMealCount, "추정 식사 수");
        if (estimatedMealCount > mealCount) {
            throw new IllegalArgumentException("추정 식사 수는 전체 식사 수를 넘을 수 없습니다.");
        }
        requireWindowCount(sleepRecordedDays, 7, "수면 기록일 수");
        requireWindowCount(lowEnergyOrReadinessDays, 7, "낮은 컨디션 기록일 수");
        requireWindowCount(currentWeightRecordedDays, 7, "최근 체중 기록일 수");
        requireWindowCount(previousWeightRecordedDays, 7, "이전 체중 기록일 수");
        if (recentDataDays < 0 || recentDataDays > 366) {
            throw new IllegalArgumentException("최근 기록일 수 범위가 올바르지 않습니다.");
        }
    }

    private static void requireNonNegative(Integer value, String label) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(label + "은 음수일 수 없습니다.");
        }
    }

    private static void requirePositive(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value <= 0)) {
            throw new IllegalArgumentException(label + "은 0보다 커야 합니다.");
        }
    }

    private static void requireRange(Double value, double min, double max, String label) {
        if (value != null && (!Double.isFinite(value) || value < min || value > max)) {
            throw new IllegalArgumentException(label + " 범위가 올바르지 않습니다.");
        }
    }

    private static void requireScore(Integer value, String label) {
        if (value != null && (value < 1 || value > 5)) {
            throw new IllegalArgumentException(label + " 점수는 1~5 범위여야 합니다.");
        }
    }

    private static void requireCount(int value, String label) {
        if (value < 0 || value > 100_000) {
            throw new IllegalArgumentException(label + " 범위가 올바르지 않습니다.");
        }
    }

    private static void requireWindowCount(int value, int windowDays, String label) {
        if (value < 0 || value > windowDays) {
            throw new IllegalArgumentException(label + " 범위가 올바르지 않습니다.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private LocalDate referenceDate = LocalDate.of(1970, 1, 1);
        private String goal = "";
        private Integer trainingAgeMonths;
        private Double bodyWeightKg;
        private Double proteinGPerKg;
        private int proteinRecordedDays;
        private int proteinWindowDays = 7;
        private int mealCount;
        private int estimatedMealCount;
        private Double sleepHours;
        private int sleepRecordedDays;
        private int lowEnergyOrReadinessDays;
        private Integer energyScore;
        private Integer readinessScore;
        private Double currentWeight7DayAverageKg;
        private int currentWeightRecordedDays;
        private Double previousWeight7DayAverageKg;
        private int previousWeightRecordedDays;
        private Double weeklyWeightChangePct;
        private int resistanceTrainingSessionsPerWeek;
        private final Map<String, Double> weeklyHardSetsPerMuscle = new LinkedHashMap<>();
        private Double failureSetsRatio;
        private Boolean coldWaterImmediatelyPostResistance;
        private Boolean painReported;
        private int recentDataDays;

        public Builder referenceDate(LocalDate value) { this.referenceDate = value; return this; }
        public Builder goal(String value) { this.goal = value; return this; }
        public Builder trainingAgeMonths(Integer value) { this.trainingAgeMonths = value; return this; }
        public Builder bodyWeightKg(Double value) { this.bodyWeightKg = value; return this; }
        public Builder proteinGPerKg(Double value) { this.proteinGPerKg = value; return this; }
        public Builder proteinRecordedDays(int value) { this.proteinRecordedDays = value; return this; }
        public Builder proteinWindowDays(int value) { this.proteinWindowDays = value; return this; }
        public Builder mealCount(int value) { this.mealCount = value; return this; }
        public Builder estimatedMealCount(int value) { this.estimatedMealCount = value; return this; }
        public Builder sleepHours(Double value) { this.sleepHours = value; return this; }
        public Builder sleepRecordedDays(int value) { this.sleepRecordedDays = value; return this; }
        public Builder lowEnergyOrReadinessDays(int value) {
            this.lowEnergyOrReadinessDays = value;
            return this;
        }
        public Builder energyScore(Integer value) { this.energyScore = value; return this; }
        public Builder readinessScore(Integer value) { this.readinessScore = value; return this; }
        public Builder currentWeight7DayAverageKg(Double value) {
            this.currentWeight7DayAverageKg = value;
            return this;
        }
        public Builder currentWeightRecordedDays(int value) {
            this.currentWeightRecordedDays = value;
            return this;
        }
        public Builder previousWeight7DayAverageKg(Double value) {
            this.previousWeight7DayAverageKg = value;
            return this;
        }
        public Builder previousWeightRecordedDays(int value) {
            this.previousWeightRecordedDays = value;
            return this;
        }
        public Builder weeklyWeightChangePct(Double value) {
            this.weeklyWeightChangePct = value;
            return this;
        }
        public Builder resistanceTrainingSessionsPerWeek(int value) {
            this.resistanceTrainingSessionsPerWeek = value;
            return this;
        }
        public Builder weeklyHardSets(String muscleGroup, Double sets) {
            this.weeklyHardSetsPerMuscle.put(muscleGroup, sets);
            return this;
        }
        public Builder failureSetsRatio(Double value) { this.failureSetsRatio = value; return this; }
        public Builder coldWaterImmediatelyPostResistance(Boolean value) {
            this.coldWaterImmediatelyPostResistance = value;
            return this;
        }
        public Builder painReported(Boolean value) { this.painReported = value; return this; }
        public Builder recentDataDays(int value) { this.recentDataDays = value; return this; }
        public PaperAdviceInput build() { return new PaperAdviceInput(this); }
    }
}
