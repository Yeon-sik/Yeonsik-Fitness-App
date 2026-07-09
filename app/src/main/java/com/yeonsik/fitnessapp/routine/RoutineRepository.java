package com.yeonsik.fitnessapp.routine;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RoutineRepository {
    private static final String DEVICE_ID = "android-local";
    private static final String DEFAULT_ROUTINE_NAME = "나만의 루틴";

    private final FitnessDatabaseHelper dbHelper;
    private String userId;

    public RoutineRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        ContentValues values = new ContentValues();
        values.put("user_id", nextUserId);
        db().update("routines", values, null, null);
        db().update("routine_exercises", values, null, null);
        this.userId = nextUserId;
    }

    public String ensureDefaultRoutine() {
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM routines WHERE user_id = ? AND is_default = 1 AND deleted_at IS NULL LIMIT 1",
                new String[]{userId})) {
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        }

        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("name", DEFAULT_ROUTINE_NAME);
        values.put("is_default", 1);
        db().insertOrThrow("routines", null, values);
        return id;
    }

    public List<RoutineExerciseInstance> defaultRoutineExercises() {
        return routineExercises(ensureDefaultRoutine());
    }

    public RoutineExerciseInstance addToDefaultRoutine(RoutineExercise exercise) {
        if (exercise == null) {
            return null;
        }

        String routineId = ensureDefaultRoutine();
        int nextOrder = nextOrder(routineId);
        String id = newId();
        String now = now();

        ContentValues values = baseValues(id, now);
        values.put("routine_id", routineId);
        values.put("exercise_id", exercise.masterExerciseId);
        values.put("name_ko", emptyToDefault(exercise.nameKo, "운동"));
        values.put("ui_part", exercise.bodyPart == null ? "" : exercise.bodyPart.labelKo());
        values.put("primary_sub_part", emptyToDefault(exercise.primarySubPart, "세부 부위 없음"));
        values.put("equipment", exercise.equipmentType == null ? "기타" : exercise.equipmentType.labelKo());
        values.put("record_type", emptyToDefault(exercise.recordType, "sets_reps_weight"));
        values.put("order_index", nextOrder);
        db().insertOrThrow("routine_exercises", null, values);

        return new RoutineExerciseInstance(
                id,
                exercise.masterExerciseId,
                emptyToDefault(exercise.nameKo, "운동"),
                exercise.bodyPart == null ? "" : exercise.bodyPart.labelKo(),
                emptyToDefault(exercise.primarySubPart, "세부 부위 없음"),
                exercise.equipmentType == null ? "기타" : exercise.equipmentType.labelKo(),
                emptyToDefault(exercise.recordType, "sets_reps_weight"),
                nextOrder
        );
    }

    private List<RoutineExerciseInstance> routineExercises(String routineId) {
        List<RoutineExerciseInstance> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, exercise_id, name_ko, ui_part, primary_sub_part, equipment, record_type, order_index " +
                        "FROM routine_exercises WHERE routine_id = ? AND deleted_at IS NULL ORDER BY order_index, created_at",
                new String[]{routineId})) {
            while (cursor.moveToNext()) {
                rows.add(new RoutineExerciseInstance(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getInt(7)
                ));
            }
        }
        return rows;
    }

    private int nextOrder(String routineId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COALESCE(MAX(order_index), 0) + 1 FROM routine_exercises WHERE routine_id = ? AND deleted_at IS NULL",
                new String[]{routineId})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 1;
        }
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

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }
}
