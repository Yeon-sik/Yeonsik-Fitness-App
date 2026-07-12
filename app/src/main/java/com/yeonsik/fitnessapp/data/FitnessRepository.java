package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;

import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        ensureDevice(nextUserId);
    }

    public String createSession(String date, String title, String sessionType, String memo, String startedAt, String endedAt) {
        String id = newId();
        String now = now();
        String recordDate = emptyToToday(date);
        ContentValues values = baseValues(id, now);
        values.put("date", recordDate);
        values.put("workout_type", normalizeWorkoutType(sessionType));
        values.put("category", normalizeWorkoutType(sessionType));
        values.put("exercise_name", emptyToDefault(title, "Workout"));
        values.put("total_volume_kg", 0);
        Integer durationSeconds = computeDurationSeconds(recordDate, startedAt, endedAt);
        if (durationSeconds == null) {
            values.putNull("duration_seconds");
        } else {
            values.put("duration_seconds", durationSeconds);
        }
        values.putNull("average_heart_rate");
        values.put("is_backfilled", 0);
        values.putNull("backfilled_at");
        values.putNull("backfill_reason");
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        values.put("metadata", json(
                "status", emptyToNull(endedAt) == null ? "in_progress" : "completed",
                "started_at", emptyToDefault(startedAt, ""),
                "ended_at", emptyToDefault(endedAt, ""),
                "memo", emptyToDefault(memo, "")
        ));
        db().insertOrThrow("workout_records", null, values);
        return id;
    }

    public String addExercise(String recordId, String name, String category, int orderIndex, String memo) {
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("record_id", recordId);
        values.put("order_index", orderIndex);
        values.put("exercise_id", "manual");
        values.put("exercise_name_snapshot", emptyToDefault(name, "Exercise"));
        values.put("ui_part", normalizeCategory(category));
        values.put("primary_sub_part_snapshot", displayCategory(normalizeCategory(category)));
        values.putNull("equipment_snapshot");
        values.put("record_type", "sets_reps_weight");
        values.put("memo", emptyToNull(memo));
        db().insertOrThrow("workout_exercises", null, values);
        return id;
    }

    public String addExerciseFromMaster(String recordId, RoutineExercise exercise) {
        if (emptyToNull(recordId) == null || exercise == null) {
            return null;
        }

        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("record_id", recordId);
        values.put("order_index", nextWorkoutExerciseOrder(recordId));
        values.put("exercise_id", emptyToDefault(exercise.masterExerciseId, "manual"));
        values.put("exercise_name_snapshot", emptyToDefault(exercise.nameKo, "Exercise"));
        values.put("ui_part", exercise.bodyPart == null ? "chest" : normalizeCategory(exercise.bodyPart.labelKo()));
        values.put("primary_sub_part_snapshot", emptyToDefault(exercise.primarySubPart, displayCategory(exercise.bodyPart == null ? "chest" : normalizeCategory(exercise.bodyPart.labelKo()))));
        values.put("equipment_snapshot", exercise.equipmentType == null ? null : exercise.equipmentType.labelKo());
        values.put("record_type", emptyToDefault(exercise.recordType, "sets_reps_weight"));
        values.putNull("memo");
        db().insertOrThrow("workout_exercises", null, values);
        return id;
    }

    public String addSet(String recordId, String exerciseId, int setIndex, double weightKg, int reps, boolean completed) {
        return addSet(recordId, exerciseId, setIndex, weightKg, reps, null, completed);
    }

    public String addSet(String recordId, String exerciseId, int setIndex, double weightKg, int reps, Integer rpe, boolean completed) {
        return addSet(recordId, exerciseId, setIndex, weightKg, reps, rpe, null, completed);
    }

    public String addSet(String recordId, String exerciseId, int setIndex, double weightKg, int reps, Integer rpe, Integer restSeconds, boolean completed) {
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("workout_exercise_id", exerciseId);
        values.put("set_index", setIndex);
        values.put("target_reps", reps);
        values.put("actual_reps", reps);
        values.put("weight_kg", weightKg);
        values.put("volume_kg", weightKg * reps);
        values.putNull("duration_seconds");
        values.putNull("distance_meters");
        if (restSeconds == null) {
            values.putNull("rest_seconds");
        } else {
            values.put("rest_seconds", Math.max(0, restSeconds));
        }
        values.putNull("assisted_weight_kg");
        values.putNull("added_weight_kg");
        values.put("is_completed", completed ? 1 : 0);
        if (rpe == null) {
            values.putNull("rpe");
        } else {
            values.put("rpe", rpe);
        }
        values.putNull("memo");
        db().insertOrThrow("workout_sets", null, values);
        updateSessionTotalVolume(recordId);
        return id;
    }

    public void updateSet(String recordId, String setId, double weightKg, int reps, Integer rpe, boolean completed) {
        updateSet(recordId, setId, weightKg, reps, rpe, null, completed);
    }

    public void updateSet(String recordId, String setId, double weightKg, int reps, Integer rpe, Integer restSeconds, boolean completed) {
        if (emptyToNull(recordId) == null || emptyToNull(setId) == null) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put("weight_kg", weightKg);
        values.put("target_reps", reps);
        values.put("actual_reps", reps);
        values.put("volume_kg", weightKg * reps);
        if (rpe == null) {
            values.putNull("rpe");
        } else {
            values.put("rpe", rpe);
        }
        if (restSeconds == null) {
            values.putNull("rest_seconds");
        } else {
            values.put("rest_seconds", Math.max(0, restSeconds));
        }
        values.put("is_completed", completed ? 1 : 0);
        values.put("updated_at", now());
        db().update("workout_sets", values, "id = ? AND deleted_at IS NULL", new String[]{setId});
        updateSessionTotalVolume(recordId);
    }

    public void deleteSet(String recordId, String setId) {
        if (emptyToNull(recordId) == null || emptyToNull(setId) == null) {
            return;
        }

        String now = now();
        ContentValues values = new ContentValues();
        values.put("deleted_at", now);
        values.put("updated_at", now);
        db().update("workout_sets", values, "id = ? AND deleted_at IS NULL", new String[]{setId});
        updateSessionTotalVolume(recordId);
    }

    public void deleteExercise(String recordId, String workoutExerciseId) {
        if (emptyToNull(recordId) == null || emptyToNull(workoutExerciseId) == null) {
            return;
        }

        String now = now();
        ContentValues values = new ContentValues();
        values.put("deleted_at", now);
        values.put("updated_at", now);
        db().update("workout_sets", values, "workout_exercise_id = ? AND deleted_at IS NULL", new String[]{workoutExerciseId});
        db().update("workout_exercises", values, "id = ? AND deleted_at IS NULL", new String[]{workoutExerciseId});
        updateSessionTotalVolume(recordId);
    }

    public List<SessionSetEntry> setsForExercise(String workoutExerciseId) {
        List<SessionSetEntry> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, set_index, COALESCE(weight_kg, 0), COALESCE(actual_reps, 0), rpe, rest_seconds, is_completed FROM workout_sets " +
                        "WHERE workout_exercise_id = ? AND deleted_at IS NULL ORDER BY set_index",
                new String[]{workoutExerciseId})) {
            while (cursor.moveToNext()) {
                rows.add(new SessionSetEntry(
                        cursor.getString(0),
                        cursor.getInt(1),
                        cursor.getDouble(2),
                        cursor.getInt(3),
                        cursor.isNull(4) ? null : cursor.getInt(4),
                        cursor.isNull(5) ? null : cursor.getInt(5),
                        cursor.getInt(6) == 1
                ));
            }
        }
        return rows;
    }

    public SessionSetEntry lastSetForExercise(String workoutExerciseId) {
        List<SessionSetEntry> sets = setsForExercise(workoutExerciseId);
        return sets.isEmpty() ? null : sets.get(sets.size() - 1);
    }

    public SessionInfo sessionInfo(String recordId) {
        SessionInfo info = new SessionInfo();
        if (emptyToNull(recordId) == null) {
            return info;
        }

        try (Cursor cursor = db().rawQuery(
                "SELECT exercise_name, date, duration_seconds, metadata FROM workout_records WHERE id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId})) {
            if (cursor.moveToFirst()) {
                info.title = emptyToDefault(cursor.getString(0), "");
                info.date = emptyToDefault(cursor.getString(1), "");
                String metadata = cursor.getString(3);
                info.startedAt = metadataValue(metadata, "started_at", "");
                info.status = metadataValue(metadata, "status", "");
                info.durationSeconds = resolvedDurationSeconds(
                        cursor.getString(1),
                        cursor.isNull(2) ? null : cursor.getInt(2),
                        metadata
                );
            }
        }
        return info;
    }

    public static int elapsedSecondsFrom(String startedAt) {
        OffsetDateTime started = parseOffsetDateTime(startedAt);
        if (started == null) {
            return 0;
        }
        long seconds = Duration.between(started, OffsetDateTime.now()).getSeconds();
        return seconds < 0 ? 0 : safeInt(seconds);
    }

    public String addBodyMetric(String date, double weightKg, String memo) {
        BodyMetricEntry existing = bodyMetricForDate(date);
        if (existing != null) {
            updateBodyMetric(existing.id, date, weightKg, memo);
            return existing.id;
        }

        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("date", emptyToToday(date));
        values.put("weight_kg", weightKg);
        values.put("is_backfilled", 0);
        values.putNull("backfilled_at");
        values.putNull("backfill_reason");
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        values.put("metadata", json("item_type", "body_weight", "memo", emptyToDefault(memo, "")));
        db().insertOrThrow("weight_records", null, values);
        return id;
    }

    public BodyMetricEntry bodyMetricForDate(String date) {
        List<BodyMetricEntry> entries = bodyMetricEntriesForDate(date);
        return entries.isEmpty() ? null : entries.get(0);
    }

    public BodyMetricEntry bodyMetricEntryById(String id) {
        if (emptyToNull(id) == null) {
            return null;
        }
        String sql = "SELECT id, date, weight_kg, metadata FROM weight_records "
                + "WHERE id = ? AND deleted_at IS NULL LIMIT 1";
        try (Cursor cursor = db().rawQuery(sql, new String[]{id})) {
            if (cursor.moveToFirst()) {
                return new BodyMetricEntry(cursor.getString(0), cursor.getString(1), cursor.getDouble(2),
                        metadataValue(cursor.getString(3), "memo", ""));
            }
        }
        return null;
    }

    public List<BodyMetricEntry> bodyMetricEntriesForDate(String date) {
        List<BodyMetricEntry> rows = new ArrayList<>();
        String sql = "SELECT id, date, weight_kg, metadata FROM weight_records "
                + "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both')";
        String[] args = null;
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{emptyToToday(date)};
        }
        sql += " ORDER BY date DESC, updated_at DESC LIMIT 20";
        try (Cursor cursor = db().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                rows.add(new BodyMetricEntry(cursor.getString(0), cursor.getString(1), cursor.getDouble(2),
                        metadataValue(cursor.getString(3), "memo", "")));
            }
        }
        return rows;
    }

    public void updateBodyMetric(String id, String date, double weightKg, String memo) {
        if (emptyToNull(id) == null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("date", emptyToToday(date));
        values.put("weight_kg", weightKg);
        values.put("metadata", json("item_type", "body_weight", "memo", emptyToDefault(memo, "")));
        values.put("updated_at", now());
        db().update("weight_records", values, "id = ? AND deleted_at IS NULL", new String[]{id});
    }

    public void deleteBodyMetric(String id) {
        if (emptyToNull(id) == null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("deleted_at", now());
        values.put("updated_at", now());
        db().update("weight_records", values, "id = ? AND deleted_at IS NULL", new String[]{id});
    }

    public String addMeal(String date, String mealType, String menuText, Integer calories, Double proteinGrams) {
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("date", emptyToToday(date));
        String normalizedMealType = normalizeMealType(mealType);
        values.put("menu", emptyToDefault(menuText, "Meal"));
        values.put("calories", calories == null ? 0 : calories);
        values.put("protein_grams", proteinGrams == null ? 0 : proteinGrams);
        values.putNull("carbs_grams");
        values.putNull("fat_grams");
        values.put("is_backfilled", 0);
        values.putNull("backfilled_at");
        values.putNull("backfill_reason");
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        values.put("metadata", json("item_type", "meal", "meal_type", normalizedMealType, "estimated", "false"));
        db().insertOrThrow("meal_records", null, values);
        return id;
    }

    public String createSessionFromRoutine(String date, String title, List<RoutineExerciseInstance> routineExercises) {
        String recordId = createSession(date, title, "strength", "", now(), "");
        if (routineExercises == null || routineExercises.isEmpty()) {
            return recordId;
        }

        SQLiteDatabase database = db();
        for (RoutineExerciseInstance exercise : routineExercises) {
            if (exercise == null) {
                continue;
            }

            String exerciseId = newId();
            String now = now();
            ContentValues values = baseValues(exerciseId, now);
            values.put("record_id", recordId);
            values.put("order_index", exercise.order);
            values.put("exercise_id", emptyToDefault(exercise.exerciseId, "manual"));
            values.put("exercise_name_snapshot", emptyToDefault(exercise.nameKo, "Exercise"));
            values.put("ui_part", normalizeCategory(exercise.uiPart));
            values.put("primary_sub_part_snapshot", emptyToDefault(exercise.primarySubPart, displayCategory(normalizeCategory(exercise.uiPart))));
            values.put("equipment_snapshot", emptyToNull(exercise.equipment));
            values.put("record_type", emptyToDefault(exercise.recordType, "sets_reps_weight"));
            values.putNull("memo");
            database.insertOrThrow("workout_exercises", null, values);
        }
        return recordId;
    }

    public List<String> sessionsForDate(String date) {
        List<String> rows = new ArrayList<>();
        for (SessionRecordEntry entry : sessionEntriesForDate(date)) {
            rows.add(entry.summary);
        }
        return rows;
    }

    public List<SessionRecordEntry> sessionEntriesForDate(String date) {
        List<SessionRecordEntry> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, date, exercise_name, workout_type, duration_seconds, metadata FROM workout_records " +
                        "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') " +
                        "AND metadata LIKE '%\"status\":\"completed\"%' AND date = ? ORDER BY updated_at DESC",
                new String[]{emptyToToday(date)})) {
            while (cursor.moveToNext()) {
                SessionMetrics metrics = sessionMetrics(cursor.getString(0));
                int durationSeconds = resolvedDurationSeconds(
                        cursor.getString(1),
                        cursor.isNull(4) ? null : cursor.getInt(4),
                        cursor.getString(5)
                );
                rows.add(new SessionRecordEntry(
                        cursor.getString(0),
                        formatDate(cursor.getString(1)) + "  " + cursor.getString(2) + "  "
                                + displaySessionType(cursor.getString(3))
                                + "  " + formatSessionMetrics(metrics.totalVolumeKg, durationSeconds)
                ));
            }
        }
        return rows;
    }

    public List<String> recentSessions() {
        List<String> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT date, exercise_name, workout_type FROM workout_records " +
                        "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') ORDER BY date DESC, updated_at DESC LIMIT 20", null)) {
            while (cursor.moveToNext()) {
                rows.add(formatDate(cursor.getString(0)) + "  " + cursor.getString(1) + "  "
                        + displaySessionType(cursor.getString(2)));
            }
        }
        return rows;
    }

    public String latestSessionId() {
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM workout_records WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') ORDER BY date DESC, updated_at DESC LIMIT 1", null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public String latestInProgressSessionId() {
        try (Cursor cursor = db().rawQuery(
                "SELECT id, metadata FROM workout_records WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') ORDER BY updated_at DESC LIMIT 20",
                null)) {
            while (cursor.moveToNext()) {
                if ("in_progress".equals(metadataValue(cursor.getString(1), "status", ""))) {
                    return cursor.getString(0);
                }
            }
        }
        return null;
    }

    public void finishSession(String recordId) {
        if (emptyToNull(recordId) == null) {
            return;
        }

        SQLiteDatabase database = db();
        try (Cursor cursor = database.rawQuery(
                "SELECT date, duration_seconds, metadata FROM workout_records WHERE id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId})) {
            if (!cursor.moveToFirst()) {
                return;
            }

            String date = cursor.getString(0);
            String metadata = cursor.getString(2);
            String endedAt = now();
            String startedAt = metadataValue(metadata, "started_at", "");
            Integer durationSeconds = computeDurationSeconds(date, startedAt, endedAt);
            if (durationSeconds == null || durationSeconds <= 0) {
                durationSeconds = cursor.isNull(1) ? 0 : cursor.getInt(1);
            }

            SessionMetrics metrics = sessionMetrics(recordId);
            ContentValues values = new ContentValues();
            values.put("duration_seconds", durationSeconds);
            values.put("total_volume_kg", metrics.totalVolumeKg);
            values.put("updated_at", endedAt);
            values.put("metadata", mergedWorkoutMetadata(
                    metadata,
                    "completed",
                    startedAt,
                    endedAt,
                    durationSeconds,
                    metrics.totalVolumeKg
            ));
            database.update("workout_records", values, "id = ?", new String[]{recordId});
        }
    }

    public void deleteSession(String recordId) {
        if (emptyToNull(recordId) == null) {
            return;
        }

        SQLiteDatabase database = db();
        String now = now();
        ContentValues values = new ContentValues();
        values.put("deleted_at", now);
        values.put("updated_at", now);

        List<String> workoutExerciseIds = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT id FROM workout_exercises WHERE record_id = ? AND deleted_at IS NULL",
                new String[]{recordId})) {
            while (cursor.moveToNext()) {
                workoutExerciseIds.add(cursor.getString(0));
            }
        }

        for (String workoutExerciseId : workoutExerciseIds) {
            database.update("workout_sets", values, "workout_exercise_id = ? AND deleted_at IS NULL", new String[]{workoutExerciseId});
        }
        database.update("workout_exercises", values, "record_id = ? AND deleted_at IS NULL", new String[]{recordId});
        database.update("workout_records", values, "id = ? AND deleted_at IS NULL", new String[]{recordId});
    }

    public List<String> sessionDetails(String recordId) {
        List<String> rows = new ArrayList<>();
        try (Cursor exercises = db().rawQuery(
                "SELECT id, order_index, exercise_name_snapshot, ui_part FROM workout_exercises " +
                        "WHERE record_id = ? AND deleted_at IS NULL ORDER BY order_index", new String[]{recordId})) {
            while (exercises.moveToNext()) {
                String exerciseId = exercises.getString(0);
                rows.add(exercises.getInt(1) + ". " + exercises.getString(2) + "  " + displayCategory(exercises.getString(3)));
                try (Cursor sets = db().rawQuery(
                        "SELECT set_index, weight_kg, actual_reps, is_completed FROM workout_sets " +
                                "WHERE workout_exercise_id = ? AND deleted_at IS NULL ORDER BY set_index", new String[]{exerciseId})) {
                    while (sets.moveToNext()) {
                        rows.add("   " + sets.getInt(0) + "세트  " + trimDouble(sets.getDouble(1))
                                + "kg · " + sets.getInt(2) + "회  " + (sets.getInt(3) == 1 ? "완료" : "미완료"));
                    }
                }
            }
        }
        return rows;
    }

    public List<SessionExerciseEntry> sessionExerciseEntries(String recordId) {
        List<SessionExerciseEntry> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, exercise_id, order_index, exercise_name_snapshot, ui_part, equipment_snapshot FROM workout_exercises " +
                        "WHERE record_id = ? AND deleted_at IS NULL ORDER BY order_index",
                new String[]{recordId})) {
            while (cursor.moveToNext()) {
                rows.add(new SessionExerciseEntry(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getInt(2),
                        cursor.getString(3),
                        displayCategory(cursor.getString(4)),
                        cursor.isNull(5) ? "" : cursor.getString(5)
                ));
            }
        }
        return rows;
    }

    public DayWorkoutMetrics dayWorkoutMetrics(String date) {
        DayWorkoutMetrics metrics = new DayWorkoutMetrics();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, date, duration_seconds, metadata FROM workout_records " +
                        "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') AND date = ?",
                new String[]{emptyToToday(date)})) {
            while (cursor.moveToNext()) {
                SessionMetrics sessionMetrics = sessionMetrics(cursor.getString(0));
                metrics.sessionCount += 1;
                metrics.totalSetCount += sessionMetrics.setCount;
                metrics.totalVolumeKg += sessionMetrics.totalVolumeKg;
                metrics.totalDurationSeconds += resolvedDurationSeconds(
                        cursor.getString(1),
                        cursor.isNull(2) ? null : cursor.getInt(2),
                        cursor.getString(3)
                );
            }
        }
        return metrics;
    }

    public SessionMetrics sessionMetrics(String recordId) {
        SessionMetrics metrics = new SessionMetrics();
        try (Cursor cursor = db().rawQuery(
                "SELECT COALESCE(SUM(COALESCE(volume_kg, COALESCE(weight_kg, 0) * COALESCE(actual_reps, 0))), 0), COUNT(*) " +
                        "FROM workout_sets ws " +
                        "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id " +
                        "WHERE we.record_id = ? AND we.deleted_at IS NULL AND ws.deleted_at IS NULL AND ws.is_completed = 1",
                new String[]{recordId})) {
            if (cursor.moveToFirst()) {
                metrics.totalVolumeKg = cursor.getDouble(0);
                metrics.setCount = cursor.getInt(1);
            }
        }
        return metrics;
    }

    public boolean hasCompletedWorkout(String recordId) {
        return sessionMetrics(recordId).setCount > 0;
    }

    public List<VolumePoint> recentSessionVolumes(String currentRecordId, int limit) {
        List<VolumePoint> rows = new ArrayList<>();
        String sql = "SELECT id, date, exercise_name FROM workout_records "
                + "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') AND id != ? "
                + "ORDER BY date DESC, updated_at DESC LIMIT ?";
        try (Cursor cursor = db().rawQuery(sql, new String[]{currentRecordId, String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                rows.add(new VolumePoint(cursor.getString(1), cursor.getString(2),
                        sessionMetrics(cursor.getString(0)).totalVolumeKg));
            }
        }
        java.util.Collections.reverse(rows);
        return rows;
    }

    public List<VolumePoint> recentExerciseVolumes(String exerciseId, String currentRecordId, int limit) {
        List<VolumePoint> rows = new ArrayList<>();
        String sql = "SELECT we.record_id, wr.date, wr.exercise_name, "
                + "COALESCE(SUM(CASE WHEN ws.is_completed = 1 THEN COALESCE(ws.volume_kg, ws.weight_kg * ws.actual_reps) ELSE 0 END), 0) "
                + "FROM workout_exercises we "
                + "INNER JOIN workout_records wr ON wr.id = we.record_id AND wr.deleted_at IS NULL "
                + "LEFT JOIN workout_sets ws ON ws.workout_exercise_id = we.id AND ws.deleted_at IS NULL "
                + "WHERE we.exercise_id = ? AND we.record_id != ? AND we.deleted_at IS NULL "
                + "GROUP BY we.record_id, wr.date, wr.exercise_name, wr.updated_at "
                + "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT ?";
        try (Cursor cursor = db().rawQuery(sql,
                new String[]{exerciseId, currentRecordId, String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                rows.add(new VolumePoint(cursor.getString(1), cursor.getString(2), cursor.getDouble(3)));
            }
        }
        java.util.Collections.reverse(rows);
        return rows;
    }

    private void updateSessionTotalVolume(String recordId) {
        SessionMetrics metrics = sessionMetrics(recordId);
        ContentValues values = new ContentValues();
        values.put("total_volume_kg", metrics.totalVolumeKg);
        values.put("updated_at", now());
        db().update("workout_records", values, "id = ?", new String[]{recordId});
    }

    private int nextWorkoutExerciseOrder(String recordId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COALESCE(MAX(order_index), 0) + 1 FROM workout_exercises WHERE record_id = ? AND deleted_at IS NULL",
                new String[]{recordId})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 1;
        }
    }

    public List<String> bodyMetrics() {
        return bodyMetricsForDate(null);
    }

    public List<String> bodyMetricsForDate(String date) {
        List<String> rows = new ArrayList<>();
        String sql = "SELECT date, weight_kg FROM weight_records WHERE deleted_at IS NULL AND scope IN ('fitness', 'both')";
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
        String sql = "SELECT date, menu, calories, protein_grams, metadata FROM meal_records WHERE deleted_at IS NULL AND scope IN ('fitness', 'both')";
        String[] args = null;
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{emptyToToday(date)};
        }
        sql += " ORDER BY date DESC, updated_at DESC LIMIT 20";

        try (Cursor cursor = db().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                String calories = cursor.getInt(2) + "kcal";
                String protein = trimDouble(cursor.getDouble(3)) + "g 단백질";
                rows.add(formatDate(cursor.getString(0)) + "  " + cursor.getString(1) + "  " + calories + "  " + protein);
            }
        }
        return rows;
    }

    private SQLiteDatabase db() {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ensureDevice(userId);
        return database;
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
        tables.add("workout_records");
        tables.add("workout_exercises");
        tables.add("workout_sets");
        tables.add("meal_records");
        tables.add("weight_records");
        return tables;
    }

    private void ensureDevice(String nextUserId) {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        String now = now();
        ContentValues values = new ContentValues();
        values.put("id", DEVICE_ID);
        values.put("user_id", normalizeUserId(nextUserId));
        values.put("name", "Fitness Android");
        values.put("last_seen_at", now);
        values.put("app_version", "0.1.0");
        database.insertWithOnConflict("devices", null, values, SQLiteDatabase.CONFLICT_REPLACE);
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
            case "팔":
            case "arms":
                return "arms";
            default:
                return "chest";
        }
    }

    private static String normalizeWorkoutType(String type) {
        String value = emptyToDefault(type, "strength").trim().toLowerCase();
        if ("cardio".equals(value) || "유산소".equals(value)) return "cardio";
        if ("other".equals(value) || "기타".equals(value)) return "other";
        return "strength";
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

    private static Integer computeDurationSeconds(String recordDate, String startedAt, String endedAt) {
        OffsetDateTime startedDateTime = parseOffsetDateTime(startedAt);
        OffsetDateTime endedDateTime = parseOffsetDateTime(endedAt);
        if (startedDateTime != null && endedDateTime != null) {
            long seconds = Duration.between(startedDateTime, endedDateTime).getSeconds();
            return seconds < 0 ? null : safeInt(seconds);
        }

        LocalTime startedTime = parseLocalTime(startedAt);
        LocalTime endedTime = parseLocalTime(endedAt);
        if (startedTime == null || endedTime == null) {
            return null;
        }

        try {
            LocalDate date = LocalDate.parse(emptyToToday(recordDate));
            long seconds = Duration.between(
                    LocalDateTime.of(date, startedTime),
                    LocalDateTime.of(date, endedTime)
            ).getSeconds();
            return seconds < 0 ? null : safeInt(seconds);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static int resolvedDurationSeconds(String recordDate, Integer storedDurationSeconds, String metadata) {
        if (storedDurationSeconds != null && storedDurationSeconds > 0) {
            return storedDurationSeconds;
        }
        Integer derived = durationFromMetadata(recordDate, metadata);
        return derived == null ? 0 : derived;
    }

    private static Integer durationFromMetadata(String recordDate, String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(metadata);
            String startedAt = object.optString("started_at", "");
            String endedAt = object.optString("ended_at", "");
            if (!endedAt.isEmpty()) {
                return computeDurationSeconds(recordDate, startedAt, endedAt);
            }

            OffsetDateTime startedDateTime = parseOffsetDateTime(startedAt);
            if (startedDateTime != null) {
                long seconds = Duration.between(startedDateTime, OffsetDateTime.now()).getSeconds();
                return seconds < 0 ? null : safeInt(seconds);
            }

            LocalTime startedTime = parseLocalTime(startedAt);
            if (startedTime == null) {
                return null;
            }

            LocalDate date = LocalDate.parse(emptyToToday(recordDate));
            long seconds = Duration.between(
                    LocalDateTime.of(date, startedTime),
                    LocalDateTime.of(LocalDate.now(), LocalTime.now())
            ).getSeconds();
            return seconds < 0 ? null : safeInt(seconds);
        } catch (Exception exception) {
            return null;
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        try {
            String candidate = emptyToNull(value);
            return candidate == null ? null : OffsetDateTime.parse(candidate);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static LocalTime parseLocalTime(String value) {
        String candidate = emptyToNull(value);
        if (candidate == null) {
            return null;
        }
        DateTimeFormatter[] formatters = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H:mm:ss"),
                DateTimeFormatter.ofPattern("HH:mm:ss")
        };
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalTime.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static String metadataValue(String metadata, String key, String fallback) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return fallback;
        }
        try {
            return new JSONObject(metadata).optString(key, fallback);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String mergedWorkoutMetadata(
            String metadata,
            String status,
            String startedAt,
            String endedAt,
            int durationSeconds,
            double totalVolumeKg
    ) {
        try {
            JSONObject object = metadata == null || metadata.trim().isEmpty()
                    ? new JSONObject()
                    : new JSONObject(metadata);
            object.put("status", status);
            object.put("started_at", emptyToDefault(startedAt, ""));
            object.put("ended_at", emptyToDefault(endedAt, ""));
            object.put("duration_seconds", durationSeconds);
            object.put("total_volume_kg", totalVolumeKg);
            return object.toString();
        } catch (Exception exception) {
            return json(
                    "status", status,
                    "started_at", emptyToDefault(startedAt, ""),
                    "ended_at", emptyToDefault(endedAt, ""),
                    "duration_seconds", String.valueOf(durationSeconds),
                    "total_volume_kg", String.valueOf(totalVolumeKg)
            );
        }
    }

    private static int safeInt(long value) {
        if (value <= 0) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static String formatSessionMetrics(double totalVolumeKg, int durationSeconds) {
        List<String> parts = new ArrayList<>();
        if (totalVolumeKg > 0) {
            parts.add("볼륨 " + trimDouble(totalVolumeKg) + "kg");
        }
        if (durationSeconds > 0) {
            parts.add("시간 " + formatDuration(durationSeconds));
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "· " + String.join(" · ", parts);
    }

    private static String formatDuration(int durationSeconds) {
        int hours = durationSeconds / 3600;
        int minutes = (durationSeconds % 3600) / 60;
        if (hours > 0) {
            return minutes > 0 ? hours + "시간 " + minutes + "분" : hours + "시간";
        }
        if (minutes > 0) {
            return minutes + "분";
        }
        return durationSeconds + "초";
    }

    private static String json(String... pairs) {
        StringBuilder builder = new StringBuilder("{");
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escapeJson(pairs[index])).append("\":");
            String value = pairs[index + 1] == null ? "" : pairs[index + 1];
            if ("true".equals(value) || "false".equals(value)) {
                builder.append(value);
            } else {
                builder.append("\"").append(escapeJson(value)).append("\"");
            }
        }
        builder.append("}");
        return builder.toString();
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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
        if ("arms".equals(category)) return "팔";
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

    public static final class SessionRecordEntry {
        public final String id;
        public final String summary;

        public SessionRecordEntry(String id, String summary) {
            this.id = id;
            this.summary = summary;
        }
    }

    public static final class SessionMetrics {
        public double totalVolumeKg;
        public int setCount;
    }

    public static final class VolumePoint {
        public final String date;
        public final String label;
        public final double volumeKg;

        public VolumePoint(String date, String label, double volumeKg) {
            this.date = date;
            this.label = label;
            this.volumeKg = volumeKg;
        }
    }

    public static final class DayWorkoutMetrics {
        public int sessionCount;
        public int totalSetCount;
        public double totalVolumeKg;
        public int totalDurationSeconds;
    }

    public static final class SessionExerciseEntry {
        public final String id;
        public final String exerciseId;
        public final int orderIndex;
        public final String name;
        public final String uiPart;
        public final String equipment;

        public SessionExerciseEntry(String id, String exerciseId, int orderIndex, String name, String uiPart, String equipment) {
            this.id = id;
            this.exerciseId = exerciseId;
            this.orderIndex = orderIndex;
            this.name = name;
            this.uiPart = uiPart;
            this.equipment = equipment;
        }

        public String label() {
            return orderIndex + ". " + name + "  " + uiPart;
        }
    }

    public static final class SessionSetEntry {
        public final String id;
        public final int setIndex;
        public final double weightKg;
        public final int actualReps;
        public final Integer rpe;
        public final Integer restSeconds;
        public final boolean isCompleted;

        public SessionSetEntry(String id, int setIndex, double weightKg, int actualReps, Integer rpe, Integer restSeconds, boolean isCompleted) {
            this.id = id;
            this.setIndex = setIndex;
            this.weightKg = weightKg;
            this.actualReps = actualReps;
            this.rpe = rpe;
            this.restSeconds = restSeconds;
            this.isCompleted = isCompleted;
        }
    }

    public String createEmptySession(String date) {
        return createSession(date, "루틴 없이 운동", "strength", "", now(), "");
    }

    public static final class SessionInfo {
        public String title = "";
        public String date = "";
        public String startedAt = "";
        public String status = "";
        public int durationSeconds;
    }

    public static final class BodyMetricEntry {
        public final String id;
        public final String date;
        public final double weightKg;
        public final String memo;

        public BodyMetricEntry(String id, String date, double weightKg, String memo) {
            this.id = id;
            this.date = date;
            this.weightKg = weightKg;
            this.memo = memo;
        }
    }
}
