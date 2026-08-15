package com.yeonsik.fitnessapp.development;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FitnessApp의 로컬 기록을 논문 조언 엔진 입력으로 연결하는 adapter.
 * 원격 데이터나 UI 상태를 읽지 않으며, 결측값은 결측으로 보존한다.
 */
public final class PaperAdviceSnapshotAssembler {
    private static final int RECENT_WINDOW_DAYS = 14;
    private static final String SCOPE_FILTER = "scope IN ('fitness', 'both')";
    private static final String COMPLETED_WORKOUT =
            "(source_app = 'os' OR metadata LIKE '%\"status\":\"completed\"%')";

    private final FitnessDatabaseHelper dbHelper;
    private final DevelopmentRepository developmentRepository;
    private final PaperAdviceEngine adviceEngine;

    public PaperAdviceSnapshotAssembler(
            FitnessDatabaseHelper dbHelper,
            DevelopmentRepository developmentRepository
    ) {
        if (dbHelper == null || developmentRepository == null) {
            throw new IllegalArgumentException("논문 조언 adapter에는 저장소가 필요합니다.");
        }
        this.dbHelper = dbHelper;
        this.developmentRepository = developmentRepository;
        this.adviceEngine = new PaperAdviceEngine();
    }

    /** 기준일 이전의 로컬 기록을 읽어 엔진 입력 snapshot을 만든다. */
    public PaperAdviceInput assemble(LocalDate referenceDate) {
        LocalDate safeDate = referenceDate == null ? LocalDate.now() : referenceDate;
        LocalDate recentStart = safeDate.minusDays(RECENT_WINDOW_DAYS - 1L);
        LocalDate weekStart = safeDate.with(DayOfWeek.MONDAY);
        String userId = developmentRepository.currentUserId();

        DevelopmentGoal goal = developmentRepository.developmentGoal();
        Double bodyWeightKg = latestWeightKg(userId, safeDate);
        ProteinSummary protein = loggedProtein(userId, recentStart, safeDate, bodyWeightKg);
        CheckInSnapshot checkIn = latestCheckIn(userId, recentStart, safeDate);
        Map<String, Double> hardSets = recentHardSets(userId, recentStart, safeDate);

        PaperAdviceInput.Builder builder = PaperAdviceInput.builder()
                .referenceDate(safeDate)
                .goal(normalizeGoal(goal))
                .bodyWeightKg(bodyWeightKg)
                .proteinGPerKg(protein.proteinGPerKg)
                .proteinRecordedDays(protein.recordedDays)
                .sleepHours(checkIn.sleepHours)
                .energyScore(checkIn.energyScore)
                .readinessScore(checkIn.readinessScore)
                .resistanceTrainingSessionsPerWeek(
                        completedResistanceSessions(userId, weekStart, safeDate)
                )
                .recentDataDays(recentDataDays(userId, recentStart, safeDate));

        for (Map.Entry<String, Double> entry : hardSets.entrySet()) {
            builder.weeklyHardSets(entry.getKey(), entry.getValue());
        }

        // 현재 schema에 명시 필드가 없으므로 추측하지 않고 null로 남긴다.
        builder.failureSetsRatio(null)
                .coldWaterImmediatelyPostResistance(null)
                .painReported(null);
        return builder.build();
    }

    /** 실제 앱 통로에서 호출할 편의 메서드: snapshot 생성 후 논문 조언을 평가한다. */
    public List<PaperAdvice> evaluate(LocalDate referenceDate) {
        return adviceEngine.evaluate(assemble(referenceDate));
    }

    static String normalizeGoal(DevelopmentGoal goal) {
        if (goal == null || !goal.isConfigured()) {
            return "";
        }
        switch (goal.objective) {
            case DevelopmentGoal.OBJECTIVE_MUSCLE_GAIN:
                return "hypertrophy";
            case DevelopmentGoal.OBJECTIVE_STRENGTH:
                return "max_strength";
            case DevelopmentGoal.OBJECTIVE_FAT_LOSS:
                return "fat_loss";
            case DevelopmentGoal.OBJECTIVE_ENDURANCE:
                return "endurance";
            default:
                return "maintenance";
        }
    }

    private Double latestWeightKg(String userId, LocalDate referenceDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT weight_kg FROM weight_records WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND " + SCOPE_FILTER + " AND date <= ? "
                        + "ORDER BY date DESC, updated_at DESC LIMIT 1",
                new String[]{userId, referenceDate.toString()}
        )) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : null;
        }
    }

    private ProteinSummary loggedProtein(
            String userId,
            LocalDate startDate,
            LocalDate endDate,
            Double bodyWeightKg
    ) {
        if (bodyWeightKg == null || bodyWeightKg <= 0) {
            return ProteinSummary.empty();
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT SUM(protein_grams), COUNT(DISTINCT date) FROM meal_records "
                        + "WHERE user_id = ? AND deleted_at IS NULL AND " + SCOPE_FILTER
                        + " AND date BETWEEN ? AND ?",
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return ProteinSummary.empty();
            }
            int recordedDays = cursor.getInt(1);
            if (recordedDays == 0) {
                return ProteinSummary.empty();
            }
            double loggedDayAverage = cursor.getDouble(0) / recordedDays;
            return new ProteinSummary(loggedDayAverage / bodyWeightKg, recordedDays);
        }
    }

    private CheckInSnapshot latestCheckIn(
            String userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        try (Cursor cursor = db().rawQuery(
                "SELECT sleep_hours, energy_score, training_readiness_score "
                        + "FROM nutrition_daily_checkins WHERE user_id = ? AND date BETWEEN ? AND ? "
                        + "ORDER BY date DESC, updated_at DESC LIMIT 1",
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            if (!cursor.moveToFirst()) {
                return CheckInSnapshot.empty();
            }
            Double sleepHours = cursor.isNull(0) ? null : cursor.getDouble(0);
            Integer energy = cursor.isNull(1) ? null : cursor.getInt(1);
            Integer readiness = cursor.isNull(2) ? null : cursor.getInt(2);
            return new CheckInSnapshot(sleepHours, energy, readiness);
        }
    }

    private int completedResistanceSessions(
            String userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*) FROM workout_records WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND " + SCOPE_FILTER + " AND workout_type = 'strength' "
                        + "AND date BETWEEN ? AND ? AND " + COMPLETED_WORKOUT,
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private Map<String, Double> recentHardSets(
            String userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<String, Double> result = new LinkedHashMap<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT lower(trim(we.ui_part)), COUNT(ws.id) FROM workout_sets ws "
                        + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id "
                        + "AND we.user_id = ws.user_id AND we.deleted_at IS NULL "
                        + "INNER JOIN workout_records wr ON wr.id = we.record_id "
                        + "AND wr.user_id = we.user_id AND wr.deleted_at IS NULL "
                        + "WHERE ws.user_id = ? AND ws.deleted_at IS NULL AND ws.is_completed = 1 "
                        + "AND wr." + SCOPE_FILTER + " AND wr.workout_type = 'strength' "
                        + "AND wr.date BETWEEN ? AND ? AND " + COMPLETED_WORKOUT
                        + " GROUP BY lower(trim(we.ui_part))",
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            while (cursor.moveToNext()) {
                String bodyPart = cursor.getString(0);
                if (bodyPart != null && !bodyPart.trim().isEmpty()) {
                    result.put(bodyPart, (double) cursor.getInt(1));
                }
            }
        }
        return result;
    }

    private int recentDataDays(String userId, LocalDate startDate, LocalDate endDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*) FROM ("
                        + "SELECT date FROM workout_records WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND " + SCOPE_FILTER + " AND date BETWEEN ? AND ? AND " + COMPLETED_WORKOUT
                        + " GROUP BY date UNION "
                        + "SELECT date FROM meal_records WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND " + SCOPE_FILTER + " AND date BETWEEN ? AND ? GROUP BY date UNION "
                        + "SELECT date FROM weight_records WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND " + SCOPE_FILTER + " AND date BETWEEN ? AND ? GROUP BY date UNION "
                        + "SELECT date FROM nutrition_daily_checkins WHERE user_id = ? "
                        + "AND date BETWEEN ? AND ? GROUP BY date)",
                new String[]{
                        userId, startDate.toString(), endDate.toString(),
                        userId, startDate.toString(), endDate.toString(),
                        userId, startDate.toString(), endDate.toString(),
                        userId, startDate.toString(), endDate.toString()
                }
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private SQLiteDatabase db() {
        return dbHelper.getReadableDatabase();
    }

    private static final class ProteinSummary {
        final Double proteinGPerKg;
        final int recordedDays;

        ProteinSummary(Double proteinGPerKg, int recordedDays) {
            this.proteinGPerKg = proteinGPerKg;
            this.recordedDays = recordedDays;
        }

        static ProteinSummary empty() {
            return new ProteinSummary(null, 0);
        }
    }

    private static final class CheckInSnapshot {
        final Double sleepHours;
        final Integer energyScore;
        final Integer readinessScore;

        CheckInSnapshot(Double sleepHours, Integer energyScore, Integer readinessScore) {
            this.sleepHours = sleepHours;
            this.energyScore = energyScore;
            this.readinessScore = readinessScore;
        }

        static CheckInSnapshot empty() {
            return new CheckInSnapshot(null, null, null);
        }
    }
}
