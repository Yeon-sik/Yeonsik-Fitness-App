package com.yeonsik.fitnessapp.development;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DevelopmentRepository {
    private static final List<String> REPORT_BODY_PARTS = Arrays.asList(
            DevelopmentGoal.BODY_PART_CHEST,
            DevelopmentGoal.BODY_PART_BACK,
            DevelopmentGoal.BODY_PART_LEGS,
            DevelopmentGoal.BODY_PART_SHOULDERS,
            DevelopmentGoal.BODY_PART_ARMS,
            DevelopmentGoal.BODY_PART_ABS
    );
    private static final String SCOPE_FILTER = "scope IN ('fitness', 'both')";
    private static final String COMPLETED_OR_OS_WORKOUT =
            "(source_app = 'os' OR metadata LIKE '%\"status\":\"completed\"%')";
    private static final String COMPLETED_FITNESS_STRENGTH =
            "wr.source_app = 'fitness' AND wr.metadata LIKE '%\"status\":\"completed\"%' " +
                    "AND wr.workout_type = 'strength'";

    private final FitnessDatabaseHelper dbHelper;
    private String userId;

    public DevelopmentRepository(FitnessDatabaseHelper dbHelper, String userId) {
        if (dbHelper == null) {
            throw new IllegalArgumentException("DevelopmentRepository에는 데이터베이스 헬퍼가 필요합니다.");
        }
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public String currentUserId() {
        return userId;
    }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        if (AccountOwnerPolicy.shouldClaimLocalRows(this.userId, nextUserId)) {
            SQLiteDatabase database = db();
            database.beginTransaction();
            try {
                claimBodyProfile(database, nextUserId);
                claimDevelopmentGoal(database, nextUserId);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
        this.userId = nextUserId;
    }

    public BodyProfile bodyProfile() {
        try (Cursor cursor = db().rawQuery(
                "SELECT height_cm, created_at, updated_at FROM body_profiles WHERE user_id = ? LIMIT 1",
                new String[]{userId}
        )) {
            if (cursor.moveToFirst()) {
                return new BodyProfile(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getString(2)
                );
            }
        }
        return BodyProfile.empty();
    }

    public void saveBodyProfile(BodyProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("신체 프로필이 필요합니다.");
        }
        if (!profile.isConfigured()) {
            db().delete("body_profiles", "user_id = ?", new String[]{userId});
            return;
        }
        String now = now();
        String createdAt = existingCreatedAt("body_profiles");
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("height_cm", profile.heightCm);
        values.put("created_at", createdAt == null ? now : createdAt);
        values.put("updated_at", now);
        db().insertWithOnConflict("body_profiles", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public DevelopmentGoal developmentGoal() {
        try (Cursor cursor = db().rawQuery(
                "SELECT objective, weekly_sessions_target, focus_body_part, effective_from, created_at, updated_at " +
                        "FROM development_goals WHERE user_id = ? LIMIT 1",
                new String[]{userId}
        )) {
            if (cursor.moveToFirst()) {
                return new DevelopmentGoal(
                        cursor.getString(0),
                        cursor.getInt(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5)
                );
            }
        }
        return DevelopmentGoal.empty();
    }

    public void saveDevelopmentGoal(DevelopmentGoal goal) {
        if (goal == null) {
            throw new IllegalArgumentException("발전 목표가 필요합니다.");
        }
        if (!goal.isConfigured()) {
            db().delete("development_goals", "user_id = ?", new String[]{userId});
            return;
        }
        String now = now();
        String createdAt = existingCreatedAt("development_goals");
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("objective", goal.objective);
        values.put("weekly_sessions_target", goal.weeklySessionsTarget);
        values.put("focus_body_part", goal.focusBodyPart);
        values.put("effective_from", goal.effectiveFrom);
        values.put("created_at", createdAt == null ? now : createdAt);
        values.put("updated_at", now);
        db().insertWithOnConflict("development_goals", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public DevelopmentReport buildReport(LocalDate referenceDate) {
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        LocalDate weekStart = safeReferenceDate.with(DayOfWeek.MONDAY);
        LocalDate recentWindowEnd = safeReferenceDate;
        LocalDate recentWindowStart = recentWindowEnd.minusDays(13);

        BodyProfile profile = bodyProfile();
        DevelopmentGoal goal = developmentGoal();
        Double latestWeightKg = latestWeightOnOrBefore(safeReferenceDate);
        WeekProgress weekProgress = weekProgress(weekStart, safeReferenceDate);
        Map<String, Integer> setsByBodyPart = recentStrengthSetsByBodyPart(recentWindowStart, recentWindowEnd);
        String focusPart = goal.isConfigured() ? goal.focusBodyPart : null;
        int focusSets = focusPart == null ? 0 : valueOrZero(setsByBodyPart.get(focusPart));
        String focusLastTrainedDate = focusPart == null ? null : latestDetailedTrainingDateForBodyPart(focusPart, safeReferenceDate);
        int mealRecordedDays = recentMealRecordedDays(recentWindowStart, recentWindowEnd);
        CheckInStats checkInStats = recentCheckInStats(recentWindowStart, recentWindowEnd);
        int recentWorkoutRecordedDays = recentWorkoutRecordedDays(recentWindowStart, recentWindowEnd);
        int recentWeightRecordedDays = recentWeightRecordedDays(recentWindowStart, recentWindowEnd);
        int daysWithAnyData = recentDaysWithAnyData(recentWindowStart, recentWindowEnd);
        DevelopmentReport.DataCoverage coverage = new DevelopmentReport.DataCoverage(
                14,
                daysWithAnyData,
                recentWorkoutRecordedDays,
                mealRecordedDays,
                checkInStats.recordedDays,
                recentWeightRecordedDays
        );
        List<DevelopmentInsight> insights = DevelopmentInsightRules.build(
                new DevelopmentInsightRules.Input(
                        safeReferenceDate,
                        recentWindowStart,
                        recentWindowEnd,
                        goal.isConfigured(),
                        goal.weeklySessionsTarget == null ? 0 : goal.weeklySessionsTarget,
                        weekProgress.completedDays,
                        weekProgress.completedSessions,
                        goal.focusBodyPartLabelKo(),
                        focusSets,
                        focusLastTrainedDate,
                        mealRecordedDays,
                        checkInStats.lowEnergyOrReadinessDays,
                        daysWithAnyData,
                        checkInStats.recordedDays,
                        recentWeightRecordedDays,
                        latestWeightKg
                )
        );
        return new DevelopmentReport(
                safeReferenceDate,
                weekStart,
                recentWindowStart,
                recentWindowEnd,
                profile,
                goal,
                latestWeightKg,
                weekProgress.completedDays,
                weekProgress.completedSessions,
                setsByBodyPart,
                focusSets,
                focusLastTrainedDate,
                mealRecordedDays,
                checkInStats.recordedDays,
                checkInStats.lowEnergyOrReadinessDays,
                coverage,
                insights
        );
    }

    private Double latestWeightOnOrBefore(LocalDate referenceDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT weight_kg FROM weight_records WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND " + SCOPE_FILTER + " AND date <= ? " +
                        "ORDER BY date DESC, updated_at DESC LIMIT 1",
                new String[]{userId, referenceDate.toString()}
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        }
        return null;
    }

    private WeekProgress weekProgress(LocalDate weekStart, LocalDate referenceDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*), COUNT(DISTINCT date) FROM workout_records " +
                        "WHERE user_id = ? AND deleted_at IS NULL AND " + SCOPE_FILTER + " " +
                        "AND date BETWEEN ? AND ? AND " + COMPLETED_OR_OS_WORKOUT,
                new String[]{userId, weekStart.toString(), referenceDate.toString()}
        )) {
            if (cursor.moveToFirst()) {
                return new WeekProgress(cursor.getInt(0), cursor.getInt(1));
            }
        }
        return new WeekProgress(0, 0);
    }

    private Map<String, Integer> recentStrengthSetsByBodyPart(LocalDate startDate, LocalDate endDate) {
        LinkedHashMap<String, Integer> counts = emptyBodyPartCounts();
        try (Cursor cursor = db().rawQuery(
                "SELECT we.ui_part, COUNT(ws.id) FROM workout_sets ws " +
                        "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id " +
                        "AND we.user_id = ws.user_id AND we.deleted_at IS NULL " +
                        "INNER JOIN workout_records wr ON wr.id = we.record_id " +
                        "AND wr.user_id = we.user_id AND wr.deleted_at IS NULL " +
                        "WHERE ws.user_id = ? AND ws.deleted_at IS NULL AND ws.is_completed = 1 " +
                        "AND wr." + SCOPE_FILTER + " AND " + COMPLETED_FITNESS_STRENGTH + " " +
                        "AND wr.date BETWEEN ? AND ? GROUP BY we.ui_part",
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            while (cursor.moveToNext()) {
                String normalizedPart = normalizeReportBodyPart(cursor.getString(0));
                if (normalizedPart != null) {
                    counts.put(normalizedPart, counts.get(normalizedPart) + cursor.getInt(1));
                }
            }
        }
        return counts;
    }

    private String latestDetailedTrainingDateForBodyPart(String bodyPart, LocalDate referenceDate) {
        String[] aliases = bodyPartAliases(bodyPart);
        StringBuilder placeholders = new StringBuilder();
        String[] args = new String[aliases.length + 2];
        args[0] = userId;
        args[1] = referenceDate.toString();
        for (int i = 0; i < aliases.length; i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            placeholders.append("?");
            args[i + 2] = aliases[i];
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT MAX(wr.date) FROM workout_sets ws " +
                        "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id " +
                        "AND we.user_id = ws.user_id AND we.deleted_at IS NULL " +
                        "INNER JOIN workout_records wr ON wr.id = we.record_id " +
                        "AND wr.user_id = we.user_id AND wr.deleted_at IS NULL " +
                        "WHERE ws.user_id = ? AND ws.deleted_at IS NULL AND ws.is_completed = 1 " +
                        "AND wr." + SCOPE_FILTER + " AND " + COMPLETED_FITNESS_STRENGTH + " " +
                        "AND wr.date <= ? AND lower(trim(we.ui_part)) IN (" + placeholders + ")",
                args
        )) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    private int recentMealRecordedDays(LocalDate startDate, LocalDate endDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(DISTINCT date) FROM meal_records WHERE user_id = ? " +
                        "AND deleted_at IS NULL AND " + SCOPE_FILTER + " AND date BETWEEN ? AND ?",
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private CheckInStats recentCheckInStats(LocalDate startDate, LocalDate endDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*), SUM(CASE " +
                        "WHEN ((energy_score IS NOT NULL AND energy_score <= 2) " +
                        "OR (training_readiness_score IS NOT NULL AND training_readiness_score <= 2)) " +
                        "THEN 1 ELSE 0 END) " +
                        "FROM nutrition_daily_checkins WHERE user_id = ? AND date BETWEEN ? AND ?",
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            if (cursor.moveToFirst()) {
                return new CheckInStats(
                        cursor.getInt(0),
                        cursor.isNull(1) ? 0 : cursor.getInt(1)
                );
            }
        }
        return new CheckInStats(0, 0);
    }

    private int recentWorkoutRecordedDays(LocalDate startDate, LocalDate endDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(DISTINCT date) FROM workout_records WHERE user_id = ? " +
                        "AND deleted_at IS NULL AND " + SCOPE_FILTER + " " +
                        "AND date BETWEEN ? AND ? AND " + COMPLETED_OR_OS_WORKOUT,
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private int recentWeightRecordedDays(LocalDate startDate, LocalDate endDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(DISTINCT date) FROM weight_records WHERE user_id = ? " +
                        "AND deleted_at IS NULL AND " + SCOPE_FILTER + " AND date BETWEEN ? AND ?",
                new String[]{userId, startDate.toString(), endDate.toString()}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private int recentDaysWithAnyData(LocalDate startDate, LocalDate endDate) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*) FROM (" +
                        "SELECT date FROM workout_records WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND " + SCOPE_FILTER + " AND date BETWEEN ? AND ? AND " + COMPLETED_OR_OS_WORKOUT +
                        " GROUP BY date " +
                        "UNION " +
                        "SELECT date FROM meal_records WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND " + SCOPE_FILTER + " AND date BETWEEN ? AND ? GROUP BY date " +
                        "UNION " +
                        "SELECT date FROM weight_records WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND " + SCOPE_FILTER + " AND date BETWEEN ? AND ? GROUP BY date " +
                        "UNION " +
                        "SELECT date FROM nutrition_daily_checkins WHERE user_id = ? AND date BETWEEN ? AND ? GROUP BY date" +
                        ")",
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

    private void claimBodyProfile(SQLiteDatabase database, String nextUserId) {
        database.execSQL(
                "INSERT OR REPLACE INTO body_profiles (user_id, height_cm, created_at, updated_at) " +
                        "SELECT ?, source.height_cm, source.created_at, source.updated_at " +
                        "FROM body_profiles source WHERE source.user_id = ? " +
                        "AND (NOT EXISTS (SELECT 1 FROM body_profiles target WHERE target.user_id = ?) " +
                        "OR julianday(source.updated_at) > julianday((SELECT target.updated_at " +
                        "FROM body_profiles target WHERE target.user_id = ? LIMIT 1)))",
                new Object[]{
                        nextUserId,
                        SupabaseConfig.DEFAULT_USER_ID,
                        nextUserId,
                        nextUserId
                }
        );
        database.delete("body_profiles", "user_id = ?", new String[]{SupabaseConfig.DEFAULT_USER_ID});
    }

    private void claimDevelopmentGoal(SQLiteDatabase database, String nextUserId) {
        database.execSQL(
                "INSERT OR REPLACE INTO development_goals (" +
                        "user_id, objective, weekly_sessions_target, focus_body_part, effective_from, created_at, updated_at" +
                        ") SELECT ?, source.objective, source.weekly_sessions_target, source.focus_body_part, " +
                        "source.effective_from, source.created_at, source.updated_at " +
                        "FROM development_goals source WHERE source.user_id = ? " +
                        "AND (NOT EXISTS (SELECT 1 FROM development_goals target WHERE target.user_id = ?) " +
                        "OR julianday(source.updated_at) > julianday((SELECT target.updated_at " +
                        "FROM development_goals target WHERE target.user_id = ? LIMIT 1)))",
                new Object[]{
                        nextUserId,
                        SupabaseConfig.DEFAULT_USER_ID,
                        nextUserId,
                        nextUserId
                }
        );
        database.delete("development_goals", "user_id = ?", new String[]{SupabaseConfig.DEFAULT_USER_ID});
    }

    private String existingCreatedAt(String tableName) {
        try (Cursor cursor = db().rawQuery(
                "SELECT created_at FROM " + tableName + " WHERE user_id = ? LIMIT 1",
                new String[]{userId}
        )) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    private SQLiteDatabase db() {
        return dbHelper.getWritableDatabase();
    }

    private static LinkedHashMap<String, Integer> emptyBodyPartCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String bodyPart : REPORT_BODY_PARTS) {
            counts.put(bodyPart, 0);
        }
        return counts;
    }

    static String normalizeReportBodyPart(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("가슴".equals(normalized) || "chest".equals(normalized)) return DevelopmentGoal.BODY_PART_CHEST;
        if ("등".equals(normalized) || "back".equals(normalized)) return DevelopmentGoal.BODY_PART_BACK;
        if ("하체".equals(normalized) || "legs".equals(normalized)) return DevelopmentGoal.BODY_PART_LEGS;
        if ("어깨".equals(normalized) || "shoulders".equals(normalized)) return DevelopmentGoal.BODY_PART_SHOULDERS;
        if ("복근".equals(normalized) || "복부".equals(normalized) || "abs".equals(normalized)) {
            return DevelopmentGoal.BODY_PART_ABS;
        }
        if ("팔".equals(normalized)
                || "이두".equals(normalized)
                || "삼두".equals(normalized)
                || "arms".equals(normalized)
                || "biceps".equals(normalized)
                || "triceps".equals(normalized)) {
            return DevelopmentGoal.BODY_PART_ARMS;
        }
        return null;
    }

    private static String[] bodyPartAliases(String bodyPart) {
        if (DevelopmentGoal.BODY_PART_ARMS.equals(bodyPart)) {
            return new String[]{"arms", "팔", "biceps", "이두", "triceps", "삼두"};
        }
        if (DevelopmentGoal.BODY_PART_CHEST.equals(bodyPart)) {
            return new String[]{"chest", "가슴"};
        }
        if (DevelopmentGoal.BODY_PART_BACK.equals(bodyPart)) {
            return new String[]{"back", "등"};
        }
        if (DevelopmentGoal.BODY_PART_LEGS.equals(bodyPart)) {
            return new String[]{"legs", "하체"};
        }
        if (DevelopmentGoal.BODY_PART_SHOULDERS.equals(bodyPart)) {
            return new String[]{"shoulders", "어깨"};
        }
        if (DevelopmentGoal.BODY_PART_ABS.equals(bodyPart)) {
            return new String[]{"abs", "복근", "복부"};
        }
        return new String[]{bodyPart == null ? "" : bodyPart.toLowerCase(Locale.US)};
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    static LocalDate requireReferenceDate(String value) {
        String normalized = value == null ? "" : value.trim();
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("참조일은 YYYY-MM-DD 형식이어야 합니다.", error);
        }
    }

    private static final class WeekProgress {
        final int completedSessions;
        final int completedDays;

        WeekProgress(int completedSessions, int completedDays) {
            this.completedSessions = completedSessions;
            this.completedDays = completedDays;
        }
    }

    private static final class CheckInStats {
        final int recordedDays;
        final int lowEnergyOrReadinessDays;

        CheckInStats(int recordedDays, int lowEnergyOrReadinessDays) {
            this.recordedDays = recordedDays;
            this.lowEnergyOrReadinessDays = lowEnergyOrReadinessDays;
        }
    }
}
