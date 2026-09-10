package com.yeonsik.fitnessapp.development;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsReadApi;
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsReadRepository;
import com.yeonsik.fitnessapp.feature.body.model.BodyReadEntry;
import com.yeonsik.fitnessapp.feature.body.model.BodyWeightWindow;
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentReadApi;
import com.yeonsik.fitnessapp.feature.development.data.DevelopmentReadRepository;
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentCheckInSummary;
import com.yeonsik.fitnessapp.feature.meal.api.MealReadApi;
import com.yeonsik.fitnessapp.feature.meal.data.MealReadRepository;
import com.yeonsik.fitnessapp.feature.meal.model.MealNutritionReadSummary;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutReadApi;
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutReadRepository;
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentBodyPartSets;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the paper-advice input from feature read APIs. It does not own or execute SQL.
 */
public final class PaperAdviceSnapshotAssembler {
    private static final int RECENT_WINDOW_DAYS = 14;
    private static final int DECISION_WINDOW_DAYS = 7;
    private static final int MIN_WEIGHT_DAYS_PER_WINDOW = 4;

    private final WorkoutReadApi workouts;
    private final MealReadApi meals;
    private final BodyMetricsReadApi body;
    private final DevelopmentReadApi development;
    private final String ownerId;
    private final PaperAdviceEngine adviceEngine;

    /** Public feature composition constructor. */
    public PaperAdviceSnapshotAssembler(
            WorkoutReadApi workouts,
            MealReadApi meals,
            BodyMetricsReadApi body,
            DevelopmentReadApi development,
            String ownerId
    ) {
        if (workouts == null || meals == null || body == null || development == null) {
            throw new IllegalArgumentException("논문 조언 adapter에는 읽기 API가 필요합니다.");
        }
        this.workouts = workouts;
        this.meals = meals;
        this.body = body;
        this.development = development;
        this.ownerId = requireOwner(ownerId);
        this.adviceEngine = new PaperAdviceEngine();
    }

    /** 기준일 이전의 로컬 기록을 읽어 엔진 입력 snapshot을 만든다. */
    public PaperAdviceInput assemble(LocalDate referenceDate) {
        LocalDate safeDate = referenceDate == null ? LocalDate.now() : referenceDate;
        LocalDate recentStart = safeDate.minusDays(RECENT_WINDOW_DAYS - 1L);
        LocalDate decisionStart = safeDate.minusDays(DECISION_WINDOW_DAYS - 1L);
        LocalDate previousWeightStart = safeDate.minusDays((DECISION_WINDOW_DAYS * 2L) - 1L);
        LocalDate previousWeightEnd = safeDate.minusDays(DECISION_WINDOW_DAYS);
        AccountScope scope = new AccountScope(ownerId);

        DevelopmentGoal goal = development.developmentGoal(scope);
        BodyReadEntry latestWeight = body.latestBodyMetricOnOrBefore(scope, safeDate.toString());
        Double bodyWeightKg = latestWeight == null ? null : latestWeight.getWeightKg();
        MealNutritionReadSummary nutrition = meals.nutritionSummary(
                scope, decisionStart.toString(), safeDate.toString()
        );
        Double proteinGPerKg = null;
        if (nutrition.getRecordedDays() > 0 && bodyWeightKg != null && bodyWeightKg > 0) {
            double loggedDayAverage = nutrition.getProteinGrams() / nutrition.getRecordedDays();
            proteinGPerKg = loggedDayAverage / bodyWeightKg;
        }
        DevelopmentCheckInSummary checkIn = development.checkInSummary(
                scope, decisionStart.toString(), safeDate.toString()
        );
        BodyWeightWindow currentWeight = body.weightWindow(
                scope, decisionStart.toString(), safeDate.toString()
        );
        BodyWeightWindow previousWeight = body.weightWindow(
                scope, previousWeightStart.toString(), previousWeightEnd.toString()
        );
        Double weeklyWeightChangePct = weeklyWeightChangePct(currentWeight, previousWeight);
        Map<String, Double> hardSets = recentHardSets(
                scope, recentStart.toString(), safeDate.toString()
        );

        PaperAdviceInput.Builder builder = PaperAdviceInput.builder()
                .referenceDate(safeDate)
                .goal(normalizeGoal(goal))
                .bodyWeightKg(bodyWeightKg)
                .proteinGPerKg(proteinGPerKg)
                .proteinRecordedDays(nutrition.getRecordedDays())
                .proteinWindowDays(DECISION_WINDOW_DAYS)
                .mealCount(nutrition.getMealCount())
                .estimatedMealCount(nutrition.getEstimatedMealCount())
                .sleepHours(checkIn.getAverageSleepHours())
                .sleepRecordedDays(checkIn.getSleepRecordedDays())
                .lowEnergyOrReadinessDays(checkIn.getLowEnergyOrReadinessDays())
                .energyScore(checkIn.getLatestEnergyScore())
                .readinessScore(checkIn.getLatestReadinessScore())
                .currentWeight7DayAverageKg(currentWeight.getAverageKg())
                .currentWeightRecordedDays(currentWeight.getRecordedDays())
                .previousWeight7DayAverageKg(previousWeight.getAverageKg())
                .previousWeightRecordedDays(previousWeight.getRecordedDays())
                .weeklyWeightChangePct(weeklyWeightChangePct)
                .resistanceTrainingSessionsPerWeek(
                        workouts.completedResistanceSessions(scope, decisionStart.toString(), safeDate.toString())
                )
                .recentDataDays(recentDataDays(scope, recentStart.toString(), safeDate.toString()));

        for (Map.Entry<String, Double> entry : hardSets.entrySet()) {
            builder.weeklyHardSets(entry.getKey(), entry.getValue());
        }

        // 현재 schema에 명시 필드가 없으므로 추측하지 않고 null로 남긴다.
        builder.failureSetsRatio(null)
                .coldWaterImmediatelyPostResistance(null)
                .painReported(null);
        return builder.build();
    }

    public List<PaperAdvice> evaluate(LocalDate referenceDate) {
        return assess(referenceDate).advice;
    }

    public PaperAdviceAssessment assess(LocalDate referenceDate) {
        PaperAdviceInput input = assemble(referenceDate);
        return new PaperAdviceAssessment(input, adviceEngine.evaluate(input));
    }

    static String normalizeGoal(DevelopmentGoal goal) {
        if (goal == null || !goal.isConfigured()) return "";
        switch (goal.objective) {
            case DevelopmentGoal.OBJECTIVE_MUSCLE_GAIN: return "hypertrophy";
            case DevelopmentGoal.OBJECTIVE_STRENGTH: return "max_strength";
            case DevelopmentGoal.OBJECTIVE_FAT_LOSS: return "fat_loss";
            case DevelopmentGoal.OBJECTIVE_ENDURANCE: return "endurance";
            default: return "maintenance";
        }
    }

    private Map<String, Double> recentHardSets(
            AccountScope scope,
            String startDate,
            String endDate
    ) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (DevelopmentBodyPartSets row : workouts.strengthSetsByBodyPart(scope, startDate, endDate)) {
            String bodyPart = row.getUiPart();
            if (bodyPart == null || bodyPart.trim().isEmpty()) continue;
            String normalized = bodyPart.trim().toLowerCase(Locale.US);
            result.put(normalized, result.containsKey(normalized)
                    ? result.get(normalized) + row.getSetCount()
                    : (double) row.getSetCount());
        }
        return result;
    }

    private int recentDataDays(AccountScope scope, String startDate, String endDate) {
        Set<String> dates = new LinkedHashSet<>();
        dates.addAll(workouts.completedDates(scope, startDate, endDate));
        dates.addAll(meals.dates(scope, startDate, endDate));
        dates.addAll(body.dates(scope, startDate, endDate));
        dates.addAll(development.checkInDates(scope, startDate, endDate));
        return dates.size();
    }

    private static Double weeklyWeightChangePct(BodyWeightWindow current, BodyWeightWindow previous) {
        if (current.getRecordedDays() < MIN_WEIGHT_DAYS_PER_WINDOW
                || previous.getRecordedDays() < MIN_WEIGHT_DAYS_PER_WINDOW
                || current.getAverageKg() == null
                || previous.getAverageKg() == null
                || previous.getAverageKg() <= 0) {
            return null;
        }
        return ((current.getAverageKg() - previous.getAverageKg()) / previous.getAverageKg()) * 100d;
    }

    private static String requireOwner(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("논문 조언 계정 식별자가 필요합니다.");
        return normalized;
    }

}
