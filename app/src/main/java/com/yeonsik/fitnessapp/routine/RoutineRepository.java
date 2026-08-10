package com.yeonsik.fitnessapp.routine;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RoutineRepository {
    public static final int MAX_ROUTINES = 5;
    private static final String DEVICE_ID = "android-local";
    private static final String DEFAULT_ROUTINE_NAME = "나만의 루틴";

    private final FitnessDatabaseHelper dbHelper;
    private String userId;
    private String activeRoutineId;

    public RoutineRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
        this.activeRoutineId = null;
    }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        if (AccountOwnerPolicy.shouldClaimLocalRows(this.userId, nextUserId)) {
            SQLiteDatabase database = db();
            ContentValues values = new ContentValues();
            values.put("user_id", nextUserId);
            String[] localOwner = {SupabaseConfig.DEFAULT_USER_ID};
            database.beginTransaction();
            try {
                database.update("routines", values, "user_id = ?", localOwner);
                database.update("routine_exercises", values, "user_id = ?", localOwner);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
        this.userId = nextUserId;
        this.activeRoutineId = null;
    }

    public String ensureDefaultRoutine() {
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM routines WHERE user_id = ? AND is_default = 1 AND deleted_at IS NULL LIMIT 1",
                new String[]{userId})) {
            if (cursor.moveToFirst()) {
                String id = cursor.getString(0);
                if (activeRoutineId == null) {
                    activeRoutineId = id;
                }
                return id;
            }
        }

        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("name", DEFAULT_ROUTINE_NAME);
        values.put("is_default", 1);
        db().insertOrThrow("routines", null, values);
        if (activeRoutineId == null) {
            activeRoutineId = id;
        }
        return id;
    }

    public List<RoutineSummary> routines() {
        List<RoutineSummary> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT r.id, r.name, COUNT(re.id) FROM routines r "
                        + "LEFT JOIN routine_exercises re ON re.routine_id = r.id "
                        + "AND re.user_id = r.user_id AND re.deleted_at IS NULL "
                        + "WHERE r.user_id = ? AND r.deleted_at IS NULL GROUP BY r.id, r.name "
                        + "ORDER BY r.is_default DESC, r.created_at",
                new String[]{userId})) {
            while (cursor.moveToNext()) {
                rows.add(new RoutineSummary(cursor.getString(0), cursor.getString(1), cursor.getInt(2)));
            }
        }
        return rows;
    }

    public boolean canCreateRoutine() {
        return routines().size() < MAX_ROUTINES;
    }

    public void selectRoutine(String routineId) {
        if (routineId == null) {
            return;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM routines WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{routineId, userId})) {
            if (cursor.moveToFirst()) {
                activeRoutineId = routineId;
            }
        }
    }

    public String activeRoutineId() {
        if (activeRoutineId == null) {
            activeRoutineId = ensureDefaultRoutine();
        }
        return activeRoutineId;
    }

    public String activeRoutineName() {
        return routineName(activeRoutineId());
    }

    public List<RoutineExerciseInstance> activeRoutineExercises() {
        return routineExercises(activeRoutineId());
    }

    public String routineName(String routineId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT name FROM routines " +
                        "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{routineId, userId})) {
            if (cursor.moveToFirst()) {
                return emptyToDefault(cursor.getString(0), DEFAULT_ROUTINE_NAME);
            }
        }
        return DEFAULT_ROUTINE_NAME;
    }

    public String createRoutine(String name, List<RoutineExercise> exercises) {
        if (!canCreateRoutine()) {
            return null;
        }
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("name", emptyToDefault(name, DEFAULT_ROUTINE_NAME));
        values.put("is_default", 0);
        db().insertOrThrow("routines", null, values);
        if (exercises != null) {
            for (RoutineExercise exercise : exercises) {
                addToRoutine(id, exercise);
            }
        }
        activeRoutineId = id;
        return id;
    }

    public List<RoutineExerciseInstance> defaultRoutineExercises() {
        return activeRoutineExercises();
    }

    public String defaultRoutineName() {
        return activeRoutineName();
    }

    public void renameDefaultRoutine(String name) {
        String routineId = ensureDefaultRoutine();
        ContentValues values = new ContentValues();
        values.put("name", emptyToDefault(name, DEFAULT_ROUTINE_NAME));
        values.put("updated_at", now());
        db().update(
                "routines",
                values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{routineId, userId}
        );
    }

    public RoutineExerciseInstance addToDefaultRoutine(RoutineExercise exercise) {
        return addToRoutine(activeRoutineId(), exercise);
    }

    private RoutineExerciseInstance addToRoutine(String routineId, RoutineExercise exercise) {
        if (exercise == null) {
            return null;
        }
        if (!ownsRoutine(routineId)) {
            throw new IllegalArgumentException("현재 계정의 루틴이 아닙니다.");
        }

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
        values.put("record_type", FitnessRecordContract.normalizeRecordType(exercise.recordType));
        values.put("order_index", nextOrder);
        db().insertOrThrow("routine_exercises", null, values);

        return new RoutineExerciseInstance(
                id,
                exercise.masterExerciseId,
                emptyToDefault(exercise.nameKo, "운동"),
                exercise.bodyPart == null ? "" : exercise.bodyPart.labelKo(),
                emptyToDefault(exercise.primarySubPart, "세부 부위 없음"),
                exercise.equipmentType == null ? "기타" : exercise.equipmentType.labelKo(),
                FitnessRecordContract.normalizeRecordType(exercise.recordType),
                nextOrder
        );
    }

    public List<RoutineExerciseInstance> routineExercises(String routineId) {
        List<RoutineExerciseInstance> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, exercise_id, name_ko, ui_part, primary_sub_part, equipment, record_type, order_index " +
                        "FROM routine_exercises WHERE routine_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL ORDER BY order_index, created_at",
                new String[]{routineId, userId})) {
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
                "SELECT COALESCE(MAX(order_index), 0) + 1 FROM routine_exercises " +
                        "WHERE routine_id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{routineId, userId})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 1;
        }
    }

    private SQLiteDatabase db() {
        return dbHelper.getWritableDatabase();
    }

    private boolean ownsRoutine(String routineId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT 1 FROM routines WHERE id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL LIMIT 1",
                new String[]{routineId, userId}
        )) {
            return cursor.moveToFirst();
        }
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

    public static final class RoutineSummary {
        public final String id;
        public final String name;
        public final int exerciseCount;

        public RoutineSummary(String id, String name, int exerciseCount) {
            this.id = id;
            this.name = name;
            this.exerciseCount = exerciseCount;
        }
    }
}
