package com.yeonsik.fitnessapp.development;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DevelopmentReport {
    public final LocalDate referenceDate;
    public final LocalDate currentWeekStart;
    public final LocalDate recentWindowStart;
    public final LocalDate recentWindowEnd;
    public final BodyProfile bodyProfile;
    public final DevelopmentGoal goal;
    public final Double latestWeightKg;
    public final int currentWeekCompletedWorkoutDays;
    public final int currentWeekCompletedWorkoutSessions;
    public final Map<String, Integer> recentCompletedStrengthSetsByBodyPart;
    public final int goalFocusBodyPartCompletedSets;
    public final String goalFocusBodyPartLastTrainedDate;
    public final int mealRecordedDays;
    public final int checkInRecordedDays;
    public final int lowEnergyOrReadinessDays;
    public final DataCoverage dataCoverage;
    public final List<DevelopmentInsight> insights;

    public DevelopmentReport(
            LocalDate referenceDate,
            LocalDate currentWeekStart,
            LocalDate recentWindowStart,
            LocalDate recentWindowEnd,
            BodyProfile bodyProfile,
            DevelopmentGoal goal,
            Double latestWeightKg,
            int currentWeekCompletedWorkoutDays,
            int currentWeekCompletedWorkoutSessions,
            Map<String, Integer> recentCompletedStrengthSetsByBodyPart,
            int goalFocusBodyPartCompletedSets,
            String goalFocusBodyPartLastTrainedDate,
            int mealRecordedDays,
            int checkInRecordedDays,
            int lowEnergyOrReadinessDays,
            DataCoverage dataCoverage,
            List<DevelopmentInsight> insights
    ) {
        this.referenceDate = referenceDate;
        this.currentWeekStart = currentWeekStart;
        this.recentWindowStart = recentWindowStart;
        this.recentWindowEnd = recentWindowEnd;
        this.bodyProfile = bodyProfile == null ? BodyProfile.empty() : bodyProfile;
        this.goal = goal == null ? DevelopmentGoal.empty() : goal;
        this.latestWeightKg = latestWeightKg;
        this.currentWeekCompletedWorkoutDays = Math.max(0, currentWeekCompletedWorkoutDays);
        this.currentWeekCompletedWorkoutSessions = Math.max(0, currentWeekCompletedWorkoutSessions);
        this.recentCompletedStrengthSetsByBodyPart = Collections.unmodifiableMap(
                new LinkedHashMap<>(recentCompletedStrengthSetsByBodyPart)
        );
        this.goalFocusBodyPartCompletedSets = Math.max(0, goalFocusBodyPartCompletedSets);
        this.goalFocusBodyPartLastTrainedDate = emptyToNull(goalFocusBodyPartLastTrainedDate);
        this.mealRecordedDays = Math.max(0, mealRecordedDays);
        this.checkInRecordedDays = Math.max(0, checkInRecordedDays);
        this.lowEnergyOrReadinessDays = Math.max(0, lowEnergyOrReadinessDays);
        this.dataCoverage = dataCoverage == null ? new DataCoverage(14, 0, 0, 0, 0, 0) : dataCoverage;
        this.insights = Collections.unmodifiableList(
                insights == null ? Collections.emptyList() : new ArrayList<>(insights)
        );
    }

    public int strengthSetsForBodyPart(String bodyPartId) {
        Integer count = recentCompletedStrengthSetsByBodyPart.get(bodyPartId);
        return count == null ? 0 : count;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public static final class DataCoverage {
        public final int windowDays;
        public final int daysWithAnyData;
        public final int workoutRecordedDays;
        public final int mealRecordedDays;
        public final int checkInRecordedDays;
        public final int weightRecordedDays;

        public DataCoverage(
                int windowDays,
                int daysWithAnyData,
                int workoutRecordedDays,
                int mealRecordedDays,
                int checkInRecordedDays,
                int weightRecordedDays
        ) {
            this.windowDays = Math.max(0, windowDays);
            this.daysWithAnyData = Math.max(0, daysWithAnyData);
            this.workoutRecordedDays = Math.max(0, workoutRecordedDays);
            this.mealRecordedDays = Math.max(0, mealRecordedDays);
            this.checkInRecordedDays = Math.max(0, checkInRecordedDays);
            this.weightRecordedDays = Math.max(0, weightRecordedDays);
        }
    }
}
