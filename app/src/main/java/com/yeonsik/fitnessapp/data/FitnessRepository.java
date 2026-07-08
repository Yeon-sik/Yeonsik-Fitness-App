package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.SupabaseConfig;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FitnessRepository {
    private static final String DEVICE_ID = "android-local";

    private final FitnessDatabaseHelper dbHelper;
    private String userId;

    public FitnessRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        SQLiteDatabase database = db();
        ContentValues values = new ContentValues();
        values.put("user_id", nextUserId);
        for (String table : tables()) {
            database.update(table, values, null, null);
        }
        this.userId = nextUserId;
    }

    public String createSession(String date, String title, String sessionType, String memo, String startedAt, String endedAt) {
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("date", emptyToToday(date));
        values.put("started_at", emptyToNull(startedAt));
        values.put("ended_at", emptyToNull(endedAt));
        values.put("status", emptyToNull(endedAt) == null ? "in_progress" : "completed");
        values.put("session_type", emptyToDefault(sessionType, "strength"));
        values.put("title", emptyToDefault(title, "Workout"));
        values.put("memo", emptyToNull(memo));
        values.putNull("created_from_quick_record_id");
        db().insertOrThrow("workout_sessions", null, values);
        return id;
    }

    public String addExercise(String sessionId, String name, String category, int orderIndex, String memo) {
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("session_id", sessionId);
        values.put("order_index", orderIndex);
        values.put("exercise_name", emptyToDefault(name, "Exercise"));
        values.put("category", normalizeCategory(category));
        values.put("exercise_type", "strength");
        values.put("memo", emptyToNull(memo));
        db().insertOrThrow("workout_exercises", null, values);
        return id;
    }

    public String addSet(String sessionId, String exerciseId, int setIndex, double weightKg, int reps, boolean completed) {
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("exercise_id", exerciseId);
        values.put("session_id", sessionId);
        values.put("set_index", setIndex);
        values.put("target_reps", reps);
        values.put("actual_reps", reps);
        values.put("weight_kg", weightKg);
        values.putNull("duration_seconds");
        values.putNull("distance_meters");
        values.putNull("rest_seconds");
        values.put("is_completed", completed ? 1 : 0);
        values.putNull("rpe");
        values.putNull("memo");
        db().insertOrThrow("workout_sets", null, values);
        return id;
    }

    public String addBodyMetric(String date, double weightKg, String memo) {
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("date", emptyToToday(date));
        values.put("weight_kg", weightKg);
        values.putNull("body_fat_percent");
        values.putNull("muscle_mass_kg");
        values.putNull("waist_cm");
        values.put("memo", emptyToNull(memo));
        values.put("source", "manual");
        values.putNull("created_from_quick_record_id");
        db().insertOrThrow("body_metrics", null, values);
        return id;
    }

    public String addMeal(String date, String mealType, String menuText, Integer calories, Double proteinGrams) {
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("date", emptyToToday(date));
        values.put("meal_type", normalizeMealType(mealType));
        values.put("menu_text", emptyToDefault(menuText, "Meal"));
        if (calories == null) values.putNull("calories"); else values.put("calories", calories);
        if (proteinGrams == null) values.putNull("protein_grams"); else values.put("protein_grams", proteinGrams);
        values.putNull("carbs_grams");
        values.putNull("fat_grams");
        values.put("source", "manual");
        values.putNull("created_from_quick_record_id");
        db().insertOrThrow("meals", null, values);
        return id;
    }

    public List<String> sessionsForDate(String date) {
        List<String> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT date, title, session_type, status, memo FROM workout_sessions " +
                        "WHERE deleted_at IS NULL AND date = ? ORDER BY updated_at DESC",
                new String[]{emptyToToday(date)})) {
            while (cursor.moveToNext()) {
                rows.add(formatDate(cursor.getString(0)) + "  " + cursor.getString(1) + "  "
                        + displaySessionType(cursor.getString(2)) + "  " + displayStatus(cursor.getString(3))
                        + optionalMemo(cursor.getString(4)));
            }
        }
        return rows;
    }

    public List<String> recentSessions() {
        List<String> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT date, title, session_type, status, memo FROM workout_sessions " +
                        "WHERE deleted_at IS NULL ORDER BY date DESC, updated_at DESC LIMIT 20", null)) {
            while (cursor.moveToNext()) {
                rows.add(formatDate(cursor.getString(0)) + "  " + cursor.getString(1) + "  "
                        + displaySessionType(cursor.getString(2)) + "  " + displayStatus(cursor.getString(3))
                        + optionalMemo(cursor.getString(4)));
            }
        }
        return rows;
    }

    public String latestSessionId() {
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM workout_sessions WHERE deleted_at IS NULL ORDER BY date DESC, updated_at DESC LIMIT 1", null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public List<String> sessionDetails(String sessionId) {
        List<String> rows = new ArrayList<>();
        try (Cursor exercises = db().rawQuery(
                "SELECT id, order_index, exercise_name, category FROM workout_exercises " +
                        "WHERE session_id = ? AND deleted_at IS NULL ORDER BY order_index", new String[]{sessionId})) {
            while (exercises.moveToNext()) {
                String exerciseId = exercises.getString(0);
                rows.add(exercises.getInt(1) + ". " + exercises.getString(2) + "  " + displayCategory(exercises.getString(3)));
                try (Cursor sets = db().rawQuery(
                        "SELECT set_index, weight_kg, actual_reps, is_completed FROM workout_sets " +
                                "WHERE exercise_id = ? AND deleted_at IS NULL ORDER BY set_index", new String[]{exerciseId})) {
                    while (sets.moveToNext()) {
                        rows.add("   " + sets.getInt(0) + "세트  " + trimDouble(sets.getDouble(1))
                                + "kg · " + sets.getInt(2) + "회  " + (sets.getInt(3) == 1 ? "완료" : "미완료"));
                    }
                }
            }
        }
        return rows;
    }

    public List<String> bodyMetrics() {
        return bodyMetricsForDate(null);
    }

    public List<String> bodyMetricsForDate(String date) {
        List<String> rows = new ArrayList<>();
        String sql = "SELECT date, weight_kg FROM body_metrics WHERE deleted_at IS NULL";
        String[] args = null;
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{emptyToToday(date)};
        }
        sql += " ORDER BY date DESC LIMIT 20";

        try (Cursor cursor = db().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                rows.add(formatDate(cursor.getString(0)) + "  " + trimDouble(cursor.getDouble(1)) + "kg");
            }
        }
        return rows;
    }

    public List<String> meals() {
        return mealsForDate(null);
    }

    public List<String> mealsForDate(String date) {
        List<String> rows = new ArrayList<>();
        String sql = "SELECT date, meal_type, menu_text, calories, protein_grams FROM meals WHERE deleted_at IS NULL";
        String[] args = null;
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{emptyToToday(date)};
        }
        sql += " ORDER BY date DESC, updated_at DESC LIMIT 20";

        try (Cursor cursor = db().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                String calories = cursor.isNull(3) ? "-" : cursor.getInt(3) + "kcal";
                String protein = cursor.isNull(4) ? "-" : trimDouble(cursor.getDouble(4)) + "g 단백질";
                rows.add(formatDate(cursor.getString(0)) + "  " + displayMealType(cursor.getString(1))
                        + "  " + cursor.getString(2) + "  " + calories + "  " + protein);
            }
        }
        return rows;
    }

    private SQLiteDatabase db() {
        return dbHelper.getWritableDatabase();
    }

    private ContentValues baseValues(String id, String now) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("user_id", userId);
        values.put("device_id", DEVICE_ID);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.putNull("deleted_at");
        return values;
    }

    private List<String> tables() {
        List<String> tables = new ArrayList<>();
        tables.add("workout_sessions");
        tables.add("workout_exercises");
        tables.add("workout_sets");
        tables.add("body_metrics");
        tables.add("meals");
        return tables;
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }

    private static String normalizeCategory(String category) {
        String value = emptyToDefault(category, "chest").trim().toLowerCase();
        switch (value) {
            case "가슴":
            case "chest":
                return "chest";
            case "등":
            case "back":
                return "back";
            case "하체":
            case "legs":
                return "legs";
            case "이두":
            case "biceps":
                return "biceps";
            case "삼두":
            case "triceps":
                return "triceps";
            case "어깨":
            case "shoulders":
                return "shoulders";
            case "복근":
            case "abs":
                return "abs";
            default:
                return "chest";
        }
    }

    private static String normalizeMealType(String mealType) {
        String value = emptyToDefault(mealType, "unknown").trim().toLowerCase();
        switch (value) {
            case "아침":
            case "breakfast":
                return "breakfast";
            case "점심":
            case "lunch":
                return "lunch";
            case "저녁":
            case "dinner":
                return "dinner";
            case "간식":
            case "snack":
                return "snack";
            default:
                return "unknown";
        }
    }

    private static String emptyToToday(String value) {
        return emptyToDefault(value, LocalDate.now().toString());
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    private static String optionalMemo(String memo) {
        return memo == null || memo.isEmpty() ? "" : "  " + memo;
    }

    private static String formatDate(String date) {
        return date == null ? "" : date.replace("-", ". ");
    }

    private static String displayStatus(String status) {
        if ("in_progress".equals(status)) return "진행 중";
        if ("completed".equals(status)) return "완료";
        if ("planned".equals(status)) return "예정";
        if ("cancelled".equals(status)) return "취소";
        return status == null ? "상태 없음" : status;
    }

    private static String displaySessionType(String type) {
        if ("strength".equals(type)) return "근력";
        if ("cardio".equals(type)) return "유산소";
        if ("mixed".equals(type)) return "혼합";
        return "기타";
    }

    private static String displayCategory(String category) {
        if ("chest".equals(category)) return "가슴";
        if ("back".equals(category)) return "등";
        if ("legs".equals(category)) return "하체";
        if ("biceps".equals(category)) return "이두";
        if ("triceps".equals(category)) return "삼두";
        if ("shoulders".equals(category)) return "어깨";
        if ("abs".equals(category)) return "복근";
        return "기타";
    }

    private static String displayMealType(String mealType) {
        if ("breakfast".equals(mealType)) return "아침";
        if ("lunch".equals(mealType)) return "점심";
        if ("dinner".equals(mealType)) return "저녁";
        if ("snack".equals(mealType)) return "간식";
        return "식단";
    }

    private static String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
