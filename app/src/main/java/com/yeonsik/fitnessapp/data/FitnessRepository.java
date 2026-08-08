package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FitnessRepository {
    private static final String DEVICE_ID = "android-local";
    private static final String COMPLETED_OR_OS_WORKOUT =
            "(source_app = 'os' OR metadata LIKE '%\"status\":\"completed\"%')";

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
        if (AccountOwnerPolicy.shouldClaimLocalRows(this.userId, nextUserId)) {
            SQLiteDatabase database = db();
            ContentValues values = new ContentValues();
            values.put("user_id", nextUserId);
            for (String table : tables()) {
                database.update(
                        table,
                        values,
                        "user_id = ?",
                        new String[]{SupabaseConfig.DEFAULT_USER_ID}
                );
            }
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
        values.put("metadata", initialWorkoutMetadata(
                emptyToNull(endedAt) == null ? "in_progress" : "completed",
                startedAt,
                endedAt,
                memo
        ));
        db().insertOrThrow("workout_records", null, values);
        return id;
    }

    /** GPS 유산소용 부모 기록과 거리/시간 세부 종목을 함께 만든다. */
    public String createCardioSession(String date, CardioActivityType activityType) {
        if (activityType == null) {
            throw new IllegalArgumentException("유산소 유형이 필요합니다.");
        }
        String recordId = createSession(
                date,
                activityType.labelKo(),
                "cardio",
                "",
                now(),
                ""
        );

        String createdAt = now();
        ContentValues exercise = baseValues(newId(), createdAt);
        exercise.put("record_id", recordId);
        exercise.put("order_index", 1);
        exercise.put("exercise_id", "cardio_" + activityType.id());
        exercise.put("exercise_name_snapshot", activityType.labelKo());
        exercise.put("ui_part", "cardio");
        exercise.put("primary_sub_part_snapshot", activityType.labelKo());
        exercise.putNull("equipment_snapshot");
        exercise.put("record_type", FitnessRecordContract.TIME);
        exercise.putNull("memo");
        db().insertOrThrow("workout_exercises", null, exercise);

        ContentValues record = new ContentValues();
        record.put("category", activityType.labelKo());
        record.put("metadata", mergedCardioMetadata(
                sessionInfoMetadata(recordId),
                "in_progress",
                activityType,
                "",
                0,
                0d,
                null
        ));
        record.put("updated_at", createdAt);
        db().update("workout_records", record, "id = ?", new String[]{recordId});
        updateSharedWorkoutSummary(recordId, false);
        return recordId;
    }

    /** 완료된 GPS 유산소를 기존 공유 기록 계약의 시간/거리 세트로 확정한다. */
    public void completeCardioSession(
            String recordId,
            CardioActivityType activityType,
            int durationSeconds,
            double distanceMeters,
            Integer averageHeartRateBpm
    ) {
        if (emptyToNull(recordId) == null || activityType == null) {
            throw new IllegalArgumentException("완료할 유산소 세션이 없습니다.");
        }
        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("유산소 시간은 1초 이상이어야 합니다.");
        }
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
            throw new IllegalArgumentException("유산소 거리는 0 이상의 유한한 값이어야 합니다.");
        }
        if (averageHeartRateBpm != null && averageHeartRateBpm <= 0) {
            throw new IllegalArgumentException("평균 심박수는 0보다 커야 합니다.");
        }

        String exerciseId = cardioExerciseId(recordId);
        if (exerciseId == null) {
            throw new IllegalStateException("유산소 세부 종목을 찾지 못했습니다.");
        }
        SetInput summaryInput = new SetInput(
                null,
                null,
                durationSeconds,
                distanceMeters,
                null,
                null,
                null,
                null,
                true
        );
        List<SessionSetEntry> existingSets = setsForExercise(exerciseId);
        if (existingSets.isEmpty()) {
            addTypedSet(recordId, exerciseId, 1, summaryInput);
        } else {
            updateTypedSet(recordId, existingSets.get(0).id, summaryInput);
        }

        String endedAt = now();
        String metadata = sessionInfoMetadata(recordId);
        ContentValues record = new ContentValues();
        record.put("duration_seconds", durationSeconds);
        record.put("total_volume_kg", 0d);
        putNullable(record, "average_heart_rate", averageHeartRateBpm);
        record.put("exercise_name", activityType.labelKo());
        record.put("category", activityType.labelKo());
        record.put("updated_at", endedAt);
        record.put("metadata", mergedCardioMetadata(
                metadata,
                "completed",
                activityType,
                endedAt,
                durationSeconds,
                distanceMeters,
                averageHeartRateBpm
        ));
        db().update("workout_records", record,
                "id = ? AND deleted_at IS NULL", new String[]{recordId});
        updateSharedWorkoutSummary(recordId, true);
    }

    public boolean updateCardioAverageHeartRate(String recordId, Integer averageHeartRateBpm) {
        if (emptyToNull(recordId) == null) {
            return false;
        }
        if (averageHeartRateBpm != null && averageHeartRateBpm <= 0) {
            throw new IllegalArgumentException("평균 심박수는 0보다 커야 합니다.");
        }

        ContentValues values = new ContentValues();
        putNullable(values, "average_heart_rate", averageHeartRateBpm);
        values.put("metadata", metadataWithAverageHeartRate(
                sessionInfoMetadata(recordId), averageHeartRateBpm));
        values.put("updated_at", now());
        boolean updated = db().update(
                "workout_records",
                values,
                "id = ? AND workout_type = 'cardio' AND deleted_at IS NULL",
                new String[]{recordId}
        ) == 1;
        if (updated) {
            updateSharedWorkoutSummary(recordId, true);
        }
        return updated;
    }

    /** 파싱과 운동 매핑이 끝난 FLEEK 기록을 하나의 SQLite 트랜잭션으로 저장한다. */
    public FleekImportResult importFleekData(FleekCsvImporter.ImportPlan plan) {
        if (plan == null || plan.sessions.isEmpty()) {
            throw new IllegalArgumentException("가져올 FLEEK 운동 기록이 없습니다.");
        }

        SQLiteDatabase database = db();
        FleekImportResult result = new FleekImportResult();
        result.skippedRows = plan.skippedRows;
        database.beginTransaction();
        try {
            for (FleekCsvImporter.SessionData session : plan.sessions) {
                if (hasImportedFleekSession(database, session.sourceKey)) {
                    result.skippedDuplicateSessions += 1;
                    continue;
                }

                String recordId = newId();
                String importedAt = now();
                double totalVolumeKg = importedSessionVolume(session);
                ContentValues record = baseValues(recordId, importedAt);
                record.put("date", session.date);
                record.put("workout_type", "strength");
                record.put("category", firstImportedCategory(session));
                record.put("exercise_name", session.title);
                record.put("duration_seconds", session.durationSeconds);
                record.put("total_volume_kg", totalVolumeKg);
                record.putNull("average_heart_rate");
                record.put("is_backfilled", 1);
                record.put("backfilled_at", importedAt);
                record.put("backfill_reason", "FLEEK CSV import");
                record.put("source_app", "fitness");
                record.put("scope", "fitness");
                record.put("metadata", importedWorkoutMetadata(session, totalVolumeKg));
                database.insertOrThrow("workout_records", null, record);

                int exerciseOrder = 1;
                for (FleekCsvImporter.ExerciseData exercise : session.exercises) {
                    String workoutExerciseId = newId();
                    ContentValues exerciseValues = baseValues(workoutExerciseId, importedAt);
                    exerciseValues.put("record_id", recordId);
                    exerciseValues.put("order_index", exerciseOrder++);
                    exerciseValues.put("exercise_id", exercise.exerciseId);
                    exerciseValues.put("exercise_name_snapshot", exercise.name);
                    exerciseValues.put("ui_part", exercise.uiPart);
                    exerciseValues.put("primary_sub_part_snapshot", exercise.primarySubPart);
                    exerciseValues.put("equipment_snapshot", exercise.equipment);
                    exerciseValues.put("record_type", FitnessRecordContract.normalizeRecordType(exercise.recordType));
                    exerciseValues.putNull("memo");
                    database.insertOrThrow("workout_exercises", null, exerciseValues);
                    result.importedExercises += 1;

                    for (FleekCsvImporter.SetData set : exercise.sets) {
                        SetInput input = importedSetInput(set);
                        validateSetInput(exercise.recordType, input);
                        ContentValues setValues = baseValues(newId(), importedAt);
                        setValues.put("workout_exercise_id", workoutExerciseId);
                        setValues.put("set_index", Math.max(1, set.setIndex));
                        putNullable(setValues, "target_reps", set.reps);
                        putNullable(setValues, "actual_reps", set.reps);
                        putNullable(setValues, "weight_kg", set.weightKg);
                        setValues.put("volume_kg", setVolume(exercise.recordType, input));
                        putNullable(setValues, "duration_seconds", set.durationSeconds);
                        putNullable(setValues, "distance_meters", set.distanceMeters);
                        setValues.putNull("rest_seconds");
                        putNullable(setValues, "assisted_weight_kg", set.assistedWeightKg);
                        putNullable(setValues, "added_weight_kg", set.addedWeightKg);
                        setValues.put("is_completed", 1);
                        putNullable(setValues, "rpe", set.rpe);
                        setValues.put("memo", importedSetMemo(set));
                        database.insertOrThrow("workout_sets", null, setValues);
                        result.importedSets += 1;
                        if (exercise.masterMatched) result.masterMatchedSets += 1;
                    }
                }

                updateSharedWorkoutSummary(recordId, false);
                result.importedSessions += 1;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return result;
    }

    private boolean hasImportedFleekSession(SQLiteDatabase database, String sourceKey) {
        String marker = "%\"fleek_source_key\":\"" + sourceKey + "\"%";
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM workout_records WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND metadata LIKE ? LIMIT 1",
                new String[]{userId, marker})) {
            return cursor.moveToFirst();
        }
    }

    private static double importedSessionVolume(FleekCsvImporter.SessionData session) {
        double total = 0;
        for (FleekCsvImporter.ExerciseData exercise : session.exercises) {
            for (FleekCsvImporter.SetData set : exercise.sets) {
                SetInput input = importedSetInput(set);
                validateSetInput(exercise.recordType, input);
                total += setVolume(exercise.recordType, input);
            }
        }
        return total;
    }

    private static SetInput importedSetInput(FleekCsvImporter.SetData set) {
        return new SetInput(
                set.weightKg,
                set.reps,
                set.durationSeconds,
                set.assistedWeightKg,
                set.addedWeightKg,
                set.rpe,
                null,
                true
        );
    }

    private static String firstImportedCategory(FleekCsvImporter.SessionData session) {
        if (session.exercises.isEmpty()) return "arms";
        String category = session.exercises.get(0).uiPart;
        return category == null || category.trim().isEmpty() ? "arms" : category;
    }

    private static String importedWorkoutMetadata(
            FleekCsvImporter.SessionData session,
            double totalVolumeKg
    ) {
        try {
            String endedAt = OffsetDateTime.parse(session.sourceTimestamp)
                    .plusSeconds(Math.max(0, session.durationSeconds))
                    .toString();
            JSONObject object = new JSONObject();
            object.put("contract_version", FitnessRecordContract.VERSION);
            object.put("status", "completed");
            object.put("started_at", session.sourceTimestamp);
            object.put("ended_at", endedAt);
            object.put("duration_seconds", session.durationSeconds);
            object.put("total_volume_kg", totalVolumeKg);
            object.put("memo", "FLEEK CSV에서 가져옴");
            object.put("import_source", "fleek_csv");
            object.put("fleek_source_key", session.sourceKey);
            return object.toString();
        } catch (Exception error) {
            throw new IllegalStateException("FLEEK 세션 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private static String importedSetMemo(FleekCsvImporter.SetData set) {
        try {
            JSONObject object = new JSONObject();
            object.put("import_source", "fleek_csv");
            if (!set.setType.isEmpty()) object.put("set_type", set.setType);
            if (!set.gripType.isEmpty()) object.put("grip_type", set.gripType);
            return object.toString();
        } catch (Exception error) {
            return "FLEEK CSV";
        }
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
        values.put("record_type", FitnessRecordContract.WEIGHT_REPS);
        values.put("memo", emptyToNull(memo));
        db().insertOrThrow("workout_exercises", null, values);
        updateSharedWorkoutSummary(recordId, false);
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
        values.put("record_type", FitnessRecordContract.normalizeRecordType(exercise.recordType));
        values.putNull("memo");
        db().insertOrThrow("workout_exercises", null, values);
        updateSharedWorkoutSummary(recordId, false);
        return id;
    }

    public String addSet(String recordId, String exerciseId, int setIndex, double weightKg, int reps, boolean completed) {
        return addSet(recordId, exerciseId, setIndex, weightKg, reps, null, completed);
    }

    public String addSet(String recordId, String exerciseId, int setIndex, double weightKg, int reps, Integer rpe, boolean completed) {
        return addSet(recordId, exerciseId, setIndex, weightKg, reps, rpe, null, completed);
    }

    public String addSet(String recordId, String exerciseId, int setIndex, double weightKg, int reps, Integer rpe, Integer restSeconds, boolean completed) {
        return addTypedSet(
                recordId,
                exerciseId,
                setIndex,
                new SetInput(weightKg, reps, null, null, null, rpe, restSeconds, completed)
        );
    }

    public String addTypedSet(String recordId, String exerciseId, int setIndex, SetInput input) {
        String recordType = exerciseRecordType(exerciseId);
        validateSetInput(recordType, input);
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("workout_exercise_id", exerciseId);
        values.put("set_index", Math.max(1, setIndex));
        putNullable(values, "target_reps", input.reps);
        putNullable(values, "actual_reps", input.reps);
        putNullable(values, "weight_kg", input.weightKg);
        values.put("volume_kg", setVolume(recordType, input));
        putNullable(values, "duration_seconds", input.durationSeconds);
        putNullable(values, "distance_meters", input.distanceMeters);
        putNullable(values, "rest_seconds", input.restSeconds);
        putNullable(values, "assisted_weight_kg", input.assistedWeightKg);
        putNullable(values, "added_weight_kg", input.addedWeightKg);
        values.put("is_completed", input.completed ? 1 : 0);
        putNullable(values, "rpe", input.rpe);
        values.putNull("memo");
        db().insertOrThrow("workout_sets", null, values);
        updateSessionTotalVolume(recordId);
        return id;
    }

    public void updateSet(String recordId, String setId, double weightKg, int reps, Integer rpe, boolean completed) {
        updateSet(recordId, setId, weightKg, reps, rpe, null, completed);
    }

    public void updateSet(String recordId, String setId, double weightKg, int reps, Integer rpe, Integer restSeconds, boolean completed) {
        updateTypedSet(
                recordId,
                setId,
                new SetInput(weightKg, reps, null, null, null, rpe, restSeconds, completed)
        );
    }

    public void updateTypedSet(String recordId, String setId, SetInput input) {
        if (emptyToNull(recordId) == null || emptyToNull(setId) == null) {
            return;
        }

        String recordType = setRecordType(setId);
        validateSetInput(recordType, input);
        ContentValues values = new ContentValues();
        putNullable(values, "weight_kg", input.weightKg);
        putNullable(values, "target_reps", input.reps);
        putNullable(values, "actual_reps", input.reps);
        values.put("volume_kg", setVolume(recordType, input));
        putNullable(values, "duration_seconds", input.durationSeconds);
        putNullable(values, "distance_meters", input.distanceMeters);
        putNullable(values, "assisted_weight_kg", input.assistedWeightKg);
        putNullable(values, "added_weight_kg", input.addedWeightKg);
        putNullable(values, "rpe", input.rpe);
        putNullable(values, "rest_seconds", input.restSeconds);
        values.put("is_completed", input.completed ? 1 : 0);
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
        updateSharedWorkoutSummary(recordId, false);
    }

    public List<SessionSetEntry> setsForExercise(String workoutExerciseId) {
        List<SessionSetEntry> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, set_index, COALESCE(weight_kg, 0), COALESCE(actual_reps, 0), "
                        + "rpe, rest_seconds, is_completed, COALESCE(duration_seconds, 0), "
                        + "COALESCE(distance_meters, 0), COALESCE(assisted_weight_kg, 0), "
                        + "COALESCE(added_weight_kg, 0) FROM workout_sets " +
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
                        cursor.getInt(6) == 1,
                        cursor.getInt(7),
                        cursor.getDouble(8),
                        cursor.getDouble(9),
                        cursor.getDouble(10)
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
                "SELECT exercise_name, date, duration_seconds, metadata, workout_type, "
                        + "average_heart_rate FROM workout_records "
                        + "WHERE id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId})) {
            if (cursor.moveToFirst()) {
                info.title = emptyToDefault(cursor.getString(0), "");
                info.date = emptyToDefault(cursor.getString(1), "");
                String metadata = cursor.getString(3);
                info.startedAt = metadataValue(metadata, "started_at", "");
                info.status = metadataValue(metadata, "status", "");
                info.workoutType = emptyToDefault(cursor.getString(4), "other");
                info.averageHeartRateBpm = cursor.isNull(5) ? null : cursor.getDouble(5);
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

    public String addMeal(String date, String menuText, Integer calories, Double proteinGrams,
                          Double carbsGrams, Double fatGrams) {
        return addMeal(
                date,
                menuText,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                Collections.emptyList()
        );
    }

    public String addMeal(String date, String menuText, Integer calories, Double proteinGrams,
                          Double carbsGrams, Double fatGrams,
                          List<MealCompositionItem> compositionItems) {
        return addMeal(
                date,
                "식사",
                menuText,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                compositionItems
        );
    }

    public String addMeal(String date, String mealType, String menuText, Integer calories,
                          Double proteinGrams, Double carbsGrams, Double fatGrams,
                          List<MealCompositionItem> compositionItems) {
        List<MealCompositionItem> items = compositionItems == null
                ? Collections.emptyList()
                : compositionItems;
        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("date", emptyToToday(date));
        values.put("menu", emptyToDefault(menuText, "Meal"));
        values.put("calories", calories == null ? 0 : calories);
        values.put("protein_grams", proteinGrams == null ? 0 : proteinGrams);
        values.put("carbs_grams", carbsGrams == null ? 0 : carbsGrams);
        values.put("fat_grams", fatGrams == null ? 0 : fatGrams);
        values.put("is_backfilled", 0);
        values.putNull("backfilled_at");
        values.putNull("backfill_reason");
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        values.put("metadata", json(
                "item_type", "meal",
                "meal_type", normalizeMealType(mealType),
                "estimated", "false",
                "composition_version", "1",
                "item_count", String.valueOf(items.size())
        ));

        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            database.insertOrThrow("meal_records", null, values);
            for (MealItemSnapshot snapshot : MealItemSnapshot.of(items)) {
                insertMealItemSnapshot(database, id, snapshot, now);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return id;
    }

    /**
     * 섭취 당시 영양성분을 통째로 복사해 둔다.
     *
     * <p>food_id는 추적용 참조일 뿐이고, 표시·집계는 전부 스냅샷 컬럼을 쓴다. 나중에 음식
     * DB의 값이 수정되거나 그 음식이 지워져도 이미 남긴 식사 기록은 그대로 남는다.
     * 모르는 영양소는 0이 아니라 NULL로 기록해 "0이었다"는 오해를 만들지 않는다.</p>
     */
    private void insertMealItemSnapshot(
            SQLiteDatabase database,
            String mealRecordId,
            MealItemSnapshot snapshot,
            String now
    ) {
        String itemId = newId();
        ContentValues itemValues = new ContentValues();
        itemValues.put("id", itemId);
        itemValues.put("user_id", userId);
        itemValues.put("meal_record_id", mealRecordId);
        itemValues.put("food_id", snapshot.foodId);
        itemValues.put("food_name_snapshot", snapshot.foodNameSnapshot);
        itemValues.put("food_kind_snapshot", snapshot.foodKindSnapshot);
        itemValues.put("quantity", snapshot.quantity);
        itemValues.put("unit", snapshot.unit);
        itemValues.put("basis_amount_snapshot", snapshot.basisAmountSnapshot);
        itemValues.put("basis_unit_snapshot", snapshot.basisUnitSnapshot);
        itemValues.put("prep_state_snapshot", snapshot.prepStateSnapshot);
        for (Map.Entry<String, Double> column : snapshot.typedNutritionColumns().entrySet()) {
            String name = mealItemColumnName(column.getKey());
            if (column.getValue() == null) {
                itemValues.putNull(name);
            } else {
                itemValues.put(name, column.getValue());
            }
        }
        putNullable(itemValues, "source_type_snapshot", snapshot.sourceTypeSnapshot);
        putNullable(itemValues, "source_reference_snapshot", snapshot.sourceReferenceSnapshot);
        putNullable(itemValues, "source_version_snapshot", snapshot.sourceVersionSnapshot);
        itemValues.put("food_data_version_snapshot", snapshot.foodDataVersionSnapshot);
        itemValues.put("order_index", snapshot.orderIndex);
        itemValues.put("created_at", now);
        itemValues.put("updated_at", now);
        itemValues.putNull("deleted_at");
        itemValues.put("device_id", DEVICE_ID);
        database.insertOrThrow("meal_record_items", null, itemValues);

        for (MealItemSnapshot.MicronutrientRow row : snapshot.micronutrientRows()) {
            ContentValues nutrientValues = new ContentValues();
            nutrientValues.put("id", newId());
            nutrientValues.put("user_id", userId);
            nutrientValues.put("meal_record_id", mealRecordId);
            nutrientValues.put("meal_record_item_id", itemId);
            nutrientValues.put("nutrient_code", row.nutrientCode);
            nutrientValues.put("amount", row.amount);
            nutrientValues.put("unit", row.unit);
            nutrientValues.put("created_at", now);
            nutrientValues.put("updated_at", now);
            nutrientValues.putNull("deleted_at");
            nutrientValues.put("device_id", DEVICE_ID);
            database.insertOrThrow("meal_record_item_nutrients", null, nutrientValues);
        }
    }

    /**
     * 스냅샷 키를 meal_record_items 컬럼명으로 옮긴다.
     *
     * <p>4대 영양소만 카탈로그와 컬럼명이 다르다(칼로리는 calories_kcal 대신 calories).
     * 나머지는 카탈로그와 같은 이름을 쓴다.</p>
     */
    private static String mealItemColumnName(String nutrientKey) {
        return NutritionProfile.CALORIES_KCAL.equals(nutrientKey) ? "calories" : nutrientKey;
    }

    /** 한 식사의 구성 항목을 스냅샷 값 그대로 읽는다. 카탈로그를 다시 조회하지 않는다. */
    public List<MealItemEntry> mealItemsForRecord(String mealRecordId) {
        List<MealItemEntry> entries = new ArrayList<>();
        String recordId = mealRecordId == null ? "" : mealRecordId.trim();
        if (recordId.isEmpty()) {
            return entries;
        }
        Map<String, NutritionProfile.Builder> profiles = new LinkedHashMap<>();
        List<String> itemIds = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, food_name_snapshot, quantity, unit, prep_state_snapshot, " +
                        "calories, protein_grams, carbs_grams, fat_grams, sodium_mg, " +
                        "saturated_fat_grams, sugars_grams, fiber_grams, added_sugars_grams, " +
                        "trans_fat_grams, cholesterol_mg " +
                        "FROM meal_record_items WHERE meal_record_id = ? AND deleted_at IS NULL " +
                        "ORDER BY order_index ASC",
                new String[]{recordId}
        )) {
            while (cursor.moveToNext()) {
                String itemId = cursor.getString(0);
                itemIds.add(itemId);
                rows.add(new String[]{
                        itemId,
                        cursor.getString(1),
                        String.valueOf(cursor.getDouble(2)),
                        cursor.getString(3),
                        cursor.getString(4)
                });
                NutritionProfile.Builder profile = NutritionProfile.builder();
                profile.value(NutritionProfile.CALORIES_KCAL, nullableDouble(cursor, 5));
                profile.value(NutritionProfile.PROTEIN_GRAMS, nullableDouble(cursor, 6));
                profile.value(NutritionProfile.CARBS_GRAMS, nullableDouble(cursor, 7));
                profile.value(NutritionProfile.FAT_GRAMS, nullableDouble(cursor, 8));
                profile.value(NutritionProfile.SODIUM_MG, nullableDouble(cursor, 9));
                profile.value(NutritionProfile.SATURATED_FAT_GRAMS, nullableDouble(cursor, 10));
                profile.value(NutritionProfile.SUGARS_GRAMS, nullableDouble(cursor, 11));
                profile.value(NutritionProfile.FIBER_GRAMS, nullableDouble(cursor, 12));
                profile.value(NutritionProfile.ADDED_SUGARS_GRAMS, nullableDouble(cursor, 13));
                profile.value(NutritionProfile.TRANS_FAT_GRAMS, nullableDouble(cursor, 14));
                profile.value(NutritionProfile.CHOLESTEROL_MG, nullableDouble(cursor, 15));
                profiles.put(itemId, profile);
            }
        }
        readMealItemMicronutrients(recordId, itemIds, profiles);

        for (String[] row : rows) {
            entries.add(new MealItemEntry(
                    row[0],
                    row[1],
                    Double.parseDouble(row[2]),
                    row[3],
                    row[4],
                    profiles.get(row[0]).build()
            ));
        }
        return entries;
    }

    private void readMealItemMicronutrients(
            String mealRecordId,
            List<String> itemIds,
            Map<String, NutritionProfile.Builder> profiles
    ) {
        if (itemIds.isEmpty()) {
            return;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT meal_record_item_id, nutrient_code, amount " +
                        "FROM meal_record_item_nutrients " +
                        "WHERE meal_record_id = ? AND deleted_at IS NULL",
                new String[]{mealRecordId}
        )) {
            while (cursor.moveToNext()) {
                NutritionProfile.Builder profile = profiles.get(cursor.getString(0));
                String code = NutrientCode.normalize(cursor.getString(1));
                if (profile == null || cursor.isNull(2) || !NutrientCode.isKnown(code)) {
                    continue;
                }
                profile.micronutrient(code, cursor.getDouble(2));
            }
        }
    }

    private static Double nullableDouble(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getDouble(index);
    }

    public List<MealEntry> mealEntriesForDate(String date) {
        List<MealEntry> entries = new ArrayList<>();
        String selectedDate = emptyToToday(date);
        try (Cursor cursor = db().rawQuery(
                "SELECT id, date, menu, calories, protein_grams, carbs_grams, fat_grams, " +
                        "metadata, created_at " +
                        "FROM meal_records WHERE deleted_at IS NULL " +
                        "AND scope IN ('fitness', 'both') AND date = ? " +
                        "ORDER BY created_at ASC, id ASC",
                new String[]{selectedDate}
        )) {
            while (cursor.moveToNext()) {
                entries.add(new MealEntry(
                        cursor.getString(0),
                        cursor.getString(1),
                        mealTypeFromMetadata(cursor.getString(7)),
                        cursor.getString(2),
                        cursor.getInt(3),
                        cursor.getDouble(4),
                        cursor.getDouble(5),
                        cursor.getDouble(6),
                        itemCountFromMetadata(cursor.getString(7)),
                        cursor.getString(8)
                ));
            }
        }
        return entries;
    }

    public MealNutritionSummary mealNutritionForDate(String date) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(calories), 0), " +
                        "COALESCE(SUM(protein_grams), 0), COALESCE(SUM(carbs_grams), 0), " +
                        "COALESCE(SUM(fat_grams), 0) FROM meal_records " +
                        "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') AND date = ?",
                new String[]{emptyToToday(date)}
        )) {
            if (cursor.moveToFirst()) {
                return new MealNutritionSummary(
                        cursor.getInt(0),
                        cursor.getDouble(1),
                        cursor.getDouble(2),
                        cursor.getDouble(3),
                        cursor.getDouble(4)
                );
            }
        }
        return new MealNutritionSummary(0, 0, 0, 0, 0);
    }

    public boolean deleteMeal(String id) {
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedId.isEmpty()) {
            return false;
        }
        String timestamp = now();
        ContentValues values = new ContentValues();
        values.put("deleted_at", timestamp);
        values.put("updated_at", timestamp);
        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            int deleted = database.update(
                    "meal_records",
                    values,
                    "id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId}
            );
            database.update(
                    "meal_record_items",
                    values,
                    "meal_record_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId}
            );
            database.update(
                    "meal_record_item_nutrients",
                    values,
                    "meal_record_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId}
            );
            database.setTransactionSuccessful();
            return deleted > 0;
        } finally {
            database.endTransaction();
        }
    }

    /** 반복 입력용 메뉴 템플릿을 기기 로컬에 저장한다. 같은 이름은 최신 영양값으로 갱신한다. */
    public String saveMealMenuPreset(String name, Integer calories, Double proteinGrams,
                                     Double carbsGrams, Double fatGrams) {
        String normalizedName = normalizeMealMenuPresetName(name);

        SQLiteDatabase database = db();
        String existingId = null;
        try (Cursor cursor = database.rawQuery(
                "SELECT id FROM meal_menu_presets WHERE name = ? COLLATE NOCASE LIMIT 1",
                new String[]{normalizedName})) {
            if (cursor.moveToFirst()) {
                existingId = cursor.getString(0);
            }
        }

        String timestamp = now();
        ContentValues values = new ContentValues();
        values.put("name", normalizedName);
        putNullable(values, "calories", calories);
        putNullable(values, "protein_grams", proteinGrams);
        putNullable(values, "carbs_grams", carbsGrams);
        putNullable(values, "fat_grams", fatGrams);
        values.put("updated_at", timestamp);

        if (existingId != null) {
            database.update("meal_menu_presets", values, "id = ?", new String[]{existingId});
            return existingId;
        }

        String id = newId();
        values.put("id", id);
        values.put("created_at", timestamp);
        database.insertOrThrow("meal_menu_presets", null, values);
        return id;
    }

    static String normalizeMealMenuPresetName(String name) {
        String normalizedName = emptyToNull(name);
        if (normalizedName == null) {
            throw new IllegalArgumentException("메뉴 이름을 입력하세요.");
        }
        return normalizedName;
    }

    public List<MealMenuPreset> mealMenuPresets() {
        List<MealMenuPreset> presets = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, name, calories, protein_grams, carbs_grams, fat_grams " +
                        "FROM meal_menu_presets ORDER BY updated_at DESC, name COLLATE NOCASE ASC",
                null)) {
            while (cursor.moveToNext()) {
                presets.add(new MealMenuPreset(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.isNull(2) ? null : cursor.getInt(2),
                        cursor.isNull(3) ? null : cursor.getDouble(3),
                        cursor.isNull(4) ? null : cursor.getDouble(4),
                        cursor.isNull(5) ? null : cursor.getDouble(5)
                ));
            }
        }
        return presets;
    }

    public boolean deleteMealMenuPreset(String id) {
        String normalizedId = emptyToNull(id);
        return normalizedId != null
                && db().delete("meal_menu_presets", "id = ?", new String[]{normalizedId}) > 0;
    }

    public String createSessionFromRoutine(String date, String title, List<RoutineExerciseInstance> routineExercises) {
        return createSessionFromRoutine(date, title, null, routineExercises);
    }

    public String createSessionFromRoutine(String date, String title, String routineId,
                                           List<RoutineExerciseInstance> routineExercises) {
        String recordId = createSession(date, title, "strength", "", now(), "");
        if (routineId != null && !routineId.trim().isEmpty()) {
            ContentValues metadataValues = new ContentValues();
            metadataValues.put("metadata", addMetadataValue(sessionInfoMetadata(recordId), "routine_id", routineId));
            db().update("workout_records", metadataValues, "id = ?", new String[]{recordId});
        }
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
            values.put("record_type", FitnessRecordContract.normalizeRecordType(exercise.recordType));
            values.putNull("memo");
            database.insertOrThrow("workout_exercises", null, values);
        }
        updateSharedWorkoutSummary(recordId, false);
        return recordId;
    }

    public String latestCompletedWorkoutDateForRoutine(String routineId, String routineName) {
        if (routineId != null && !routineId.trim().isEmpty()) {
            try (Cursor cursor = db().rawQuery(
                    "SELECT date FROM workout_records "
                            + "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') "
                            + "AND metadata LIKE ? AND metadata LIKE '%\"status\":\"completed\"%' "
                            + "ORDER BY date DESC, updated_at DESC LIMIT 1",
                    new String[]{"%\"routine_id\":\"" + routineId + "\"%"})) {
                if (cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            }
        }

        if (routineName == null || routineName.trim().isEmpty()) {
            return null;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT date FROM workout_records "
                        + "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') "
                        + "AND metadata LIKE '%\"status\":\"completed\"%' AND exercise_name = ? "
                        + "ORDER BY date DESC, updated_at DESC LIMIT 1",
                new String[]{routineName})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private String sessionInfoMetadata(String recordId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT metadata FROM workout_records WHERE id = ? LIMIT 1", new String[]{recordId})) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    private String cardioExerciseId(String recordId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM workout_exercises WHERE record_id = ? "
                        + "AND exercise_id LIKE 'cardio_%' AND deleted_at IS NULL "
                        + "ORDER BY order_index LIMIT 1",
                new String[]{recordId})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static String addMetadataValue(String metadata, String key, String value) {
        try {
            JSONObject object = metadata == null || metadata.trim().isEmpty()
                    ? new JSONObject() : new JSONObject(metadata);
            object.put(key, value);
            return object.toString();
        } catch (Exception exception) {
            return json(key, value);
        }
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
                "SELECT id, date, exercise_name, workout_type, duration_seconds, metadata, category, "
                        + "source_app, average_heart_rate FROM workout_records " +
                        "WHERE deleted_at IS NULL AND scope IN ('fitness', 'both') " +
                        "AND " + COMPLETED_OR_OS_WORKOUT + " AND date = ? ORDER BY updated_at DESC",
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
                        formatDate(cursor.getString(1)) + "  "
                                + sharedWorkoutDisplayName(
                                cursor.getString(7),
                                cursor.getString(3),
                                cursor.getString(6),
                                cursor.getString(2))
                                + "  "
                                + displaySessionType(cursor.getString(3))
                                + "  " + formatSessionMetrics(metrics.totalVolumeKg, durationSeconds),
                        cursor.getString(7),
                        cursor.getString(3),
                        durationSeconds,
                        cursor.isNull(8) ? null : cursor.getDouble(8)
                ));
            }
        }
        return rows;
    }

    public Map<String, CalendarDaySummary> calendarSummaries(String firstDate, String lastDate) {
        Map<String, CalendarDaySummary> summaries = new HashMap<>();
        String range = "date >= ? AND date <= ?";
        try (Cursor cursor = db().rawQuery(
                "SELECT date FROM workout_records WHERE deleted_at IS NULL "
                        + "AND scope IN ('fitness', 'both') AND " + range
                        + " AND " + COMPLETED_OR_OS_WORKOUT + " GROUP BY date",
                new String[]{firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                summaryFor(summaries, cursor.getString(0)).hasWorkout = true;
            }
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT date FROM meal_records WHERE deleted_at IS NULL "
                        + "AND scope IN ('fitness', 'both') AND " + range + " GROUP BY date",
                new String[]{firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                summaryFor(summaries, cursor.getString(0)).hasMeal = true;
            }
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT date FROM weight_records WHERE deleted_at IS NULL "
                        + "AND scope IN ('fitness', 'both') AND " + range + " GROUP BY date",
                new String[]{firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                summaryFor(summaries, cursor.getString(0)).hasWeight = true;
            }
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT wr.date, we.ui_part FROM workout_records wr "
                        + "INNER JOIN workout_exercises we ON we.record_id = wr.id "
                        + "AND we.deleted_at IS NULL WHERE wr.deleted_at IS NULL "
                        + "AND wr.scope IN ('fitness', 'both') AND wr." + range
                        + " AND " + COMPLETED_OR_OS_WORKOUT + " "
                        + "AND we.ui_part IS NOT NULL AND we.ui_part != '' ORDER BY wr.date, we.order_index",
                new String[]{firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                CalendarDaySummary summary = summaryFor(summaries, cursor.getString(0));
                appendCalendarPart(summary, displayCategory(cursor.getString(1)));
            }
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT wr.date, wr.workout_type, wr.category FROM workout_records wr "
                        + "WHERE wr.deleted_at IS NULL AND wr.scope IN ('fitness', 'both') "
                        + "AND wr." + range + " AND " + COMPLETED_OR_OS_WORKOUT + " "
                        + "AND NOT EXISTS (SELECT 1 FROM workout_exercises we "
                        + "WHERE we.record_id = wr.id AND we.deleted_at IS NULL) "
                        + "ORDER BY wr.date, wr.updated_at",
                new String[]{firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                appendCalendarPart(
                        summaryFor(summaries, cursor.getString(0)),
                        calendarWorkoutLabel(cursor.getString(1), cursor.getString(2))
                );
            }
        }
        return summaries;
    }

    private static CalendarDaySummary summaryFor(Map<String, CalendarDaySummary> summaries, String date) {
        CalendarDaySummary summary = summaries.get(date);
        if (summary == null) {
            summary = new CalendarDaySummary();
            summaries.put(date, summary);
        }
        return summary;
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
        boolean finished = false;
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
            finished = true;
        }
        if (finished) {
            updateSharedWorkoutSummary(recordId, true);
        }
    }

    public int reconcileSharedWorkoutSummaries() {
        List<String> recordIds = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM workout_records WHERE source_app = 'fitness' "
                        + "AND user_id = ? AND deleted_at IS NULL "
                        + "AND metadata LIKE '%\"status\":\"completed\"%'",
                new String[]{userId})) {
            while (cursor.moveToNext()) {
                recordIds.add(cursor.getString(0));
            }
        }
        int changedCount = 0;
        for (String recordId : recordIds) {
            if (updateSharedWorkoutSummary(recordId, true)) {
                changedCount += 1;
            }
        }
        return changedCount;
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
                "SELECT id, exercise_id, order_index, exercise_name_snapshot, ui_part, equipment_snapshot, record_type FROM workout_exercises " +
                        "WHERE record_id = ? AND deleted_at IS NULL ORDER BY order_index",
                new String[]{recordId})) {
            while (cursor.moveToNext()) {
                rows.add(new SessionExerciseEntry(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getInt(2),
                        cursor.getString(3),
                        displayCategory(cursor.getString(4)),
                        cursor.isNull(5) ? "" : cursor.getString(5),
                        cursor.getString(6)
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
                "SELECT COALESCE(SUM(COALESCE(volume_kg, COALESCE(weight_kg, 0) * COALESCE(actual_reps, 0))), 0), "
                        + "COUNT(*), COALESCE(SUM(COALESCE(distance_meters, 0)), 0) " +
                        "FROM workout_sets ws " +
                        "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id " +
                        "WHERE we.record_id = ? AND we.deleted_at IS NULL AND ws.deleted_at IS NULL AND ws.is_completed = 1",
                new String[]{recordId})) {
            if (cursor.moveToFirst()) {
                metrics.totalVolumeKg = cursor.getDouble(0);
                metrics.setCount = cursor.getInt(1);
                metrics.totalDistanceMeters = cursor.getDouble(2);
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

    /**
     * 종목 매칭 조건. 마스터 종목은 exercise_id로, 수동 추가 종목("manual")은
     * 이름 스냅샷으로 같은 종목을 식별한다. 바인딩 인자 2개: exerciseId, exerciseName.
     */
    private static final String EXERCISE_MATCH =
            "((we.exercise_id != 'manual' AND we.exercise_id = ?) "
                    + "OR (we.exercise_id = 'manual' AND we.exercise_name_snapshot = ?))";

    public List<VolumePoint> recentExerciseVolumes(String exerciseId, String exerciseName, String currentRecordId, int limit) {
        List<VolumePoint> rows = new ArrayList<>();
        String sql = "SELECT we.record_id, wr.date, wr.exercise_name, "
                + "COALESCE(SUM(CASE WHEN ws.is_completed = 1 THEN COALESCE(ws.volume_kg, ws.weight_kg * ws.actual_reps) ELSE 0 END), 0) "
                + "FROM workout_exercises we "
                + "INNER JOIN workout_records wr ON wr.id = we.record_id AND wr.deleted_at IS NULL "
                + "LEFT JOIN workout_sets ws ON ws.workout_exercise_id = we.id AND ws.deleted_at IS NULL "
                + "WHERE " + EXERCISE_MATCH + " AND we.record_id != ? AND we.deleted_at IS NULL "
                + "AND we.record_type = 'weight_reps' "
                + "GROUP BY we.record_id, wr.date, wr.exercise_name, wr.updated_at "
                + "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT ?";
        try (Cursor cursor = db().rawQuery(sql,
                new String[]{exerciseId, exerciseName, currentRecordId, String.valueOf(limit)})) {
            while (cursor.moveToNext()) {
                rows.add(new VolumePoint(cursor.getString(1), cursor.getString(2), cursor.getDouble(3)));
            }
        }
        java.util.Collections.reverse(rows);
        return rows;
    }

    /** 직전 세션에서 같은 종목을 수행한 기록. 없으면 null. */
    public ExerciseHistory lastExerciseHistory(String exerciseId, String exerciseName, String currentRecordId) {
        String workoutExerciseId = null;
        String date = null;
        String sql = "SELECT we.id, wr.date FROM workout_exercises we "
                + "INNER JOIN workout_records wr ON wr.id = we.record_id AND wr.deleted_at IS NULL "
                + "WHERE " + EXERCISE_MATCH + " AND we.record_id != ? AND we.deleted_at IS NULL "
                + "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT 1";
        try (Cursor cursor = db().rawQuery(sql, new String[]{exerciseId, exerciseName, currentRecordId})) {
            if (cursor.moveToFirst()) {
                workoutExerciseId = cursor.getString(0);
                date = cursor.getString(1);
            }
        }
        if (workoutExerciseId == null) {
            return null;
        }

        ExerciseHistory history = new ExerciseHistory();
        history.date = date;
        history.sets = new ArrayList<>();
        for (SessionSetEntry set : setsForExercise(workoutExerciseId)) {
            if (set.isCompleted) {
                history.sets.add(set);
                history.totalVolumeKg += set.weightKg * set.actualReps;
            }
        }
        return history.sets.isEmpty() ? null : history;
    }

    /**
     * 이 종목의 역대 기록(현재 세션 제외): 최고 무게, 추정 1RM, 최고 세션 볼륨, 수행 세션 수.
     * 완료된 세트만 집계한다. 기록이 없으면 sessionCount = 0.
     */
    public ExerciseBests exerciseBests(String exerciseId, String exerciseName, String currentRecordId) {
        ExerciseBests bests = new ExerciseBests();
        String sql = "SELECT ws.weight_kg, ws.actual_reps, wr.date, we.record_id, "
                + "COALESCE(ws.volume_kg, COALESCE(ws.weight_kg, 0) * COALESCE(ws.actual_reps, 0)) "
                + "FROM workout_sets ws "
                + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id AND we.deleted_at IS NULL "
                + "INNER JOIN workout_records wr ON wr.id = we.record_id AND wr.deleted_at IS NULL "
                + "WHERE " + EXERCISE_MATCH + " AND we.record_id != ? "
                + "AND we.record_type = 'weight_reps' "
                + "AND ws.deleted_at IS NULL AND ws.is_completed = 1";
        java.util.Map<String, Double> volumeByRecord = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> dateByRecord = new java.util.LinkedHashMap<>();
        try (Cursor cursor = db().rawQuery(sql, new String[]{exerciseId, exerciseName, currentRecordId})) {
            while (cursor.moveToNext()) {
                double weight = cursor.isNull(0) ? 0 : cursor.getDouble(0);
                int reps = cursor.isNull(1) ? 0 : cursor.getInt(1);
                String setDate = cursor.getString(2);
                String recordId = cursor.getString(3);
                double volume = cursor.getDouble(4);

                if (weight > bests.maxWeightKg
                        || (weight == bests.maxWeightKg && reps > bests.repsAtMaxWeight)) {
                    bests.maxWeightKg = weight;
                    bests.repsAtMaxWeight = reps;
                    bests.maxWeightDate = setDate;
                }
                double e1rm = epleyE1rm(weight, reps);
                if (e1rm > bests.bestE1rmKg) {
                    bests.bestE1rmKg = e1rm;
                    bests.bestE1rmDate = setDate;
                }
                Double current = volumeByRecord.get(recordId);
                volumeByRecord.put(recordId, (current == null ? 0 : current) + volume);
                dateByRecord.put(recordId, setDate);
            }
        }
        for (java.util.Map.Entry<String, Double> entry : volumeByRecord.entrySet()) {
            if (entry.getValue() > bests.bestSessionVolumeKg) {
                bests.bestSessionVolumeKg = entry.getValue();
                bests.bestVolumeDate = dateByRecord.get(entry.getKey());
            }
        }
        bests.sessionCount = volumeByRecord.size();
        return bests;
    }

    /** Epley 공식 추정 1RM: weight × (1 + reps/30). 1회는 무게 그대로. */
    public static double epleyE1rm(double weightKg, int reps) {
        if (weightKg <= 0 || reps <= 0) {
            return 0;
        }
        if (reps == 1) {
            return weightKg;
        }
        return weightKg * (1 + reps / 30.0);
    }

    private void updateSessionTotalVolume(String recordId) {
        SessionMetrics metrics = sessionMetrics(recordId);
        ContentValues values = new ContentValues();
        values.put("total_volume_kg", metrics.totalVolumeKg);
        values.put("updated_at", now());
        db().update("workout_records", values, "id = ?", new String[]{recordId});
    }

    private boolean updateSharedWorkoutSummary(String recordId, boolean publishToOs) {
        if (emptyToNull(recordId) == null) {
            return false;
        }

        SQLiteDatabase database = db();
        String workoutType;
        String currentCategory;
        String currentMetadata;
        String sourceApp;
        String currentScope;
        try (Cursor cursor = database.rawQuery(
                "SELECT workout_type, category, metadata, source_app, scope FROM workout_records "
                        + "WHERE id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId})) {
            if (!cursor.moveToFirst()) {
                return false;
            }
            workoutType = cursor.getString(0);
            currentCategory = cursor.getString(1);
            currentMetadata = cursor.getString(2);
            sourceApp = cursor.getString(3);
            currentScope = cursor.getString(4);
        }

        if (!"fitness".equals(sourceApp)) {
            return false;
        }

        List<String> categories = new ArrayList<>();
        if ("cardio".equals(workoutType)) {
            categories.add(sharedCardioCategory(currentCategory));
        } else if ("strength".equals(workoutType)) {
            try (Cursor cursor = database.rawQuery(
                    "SELECT ui_part FROM workout_exercises "
                            + "WHERE record_id = ? AND deleted_at IS NULL ORDER BY order_index",
                    new String[]{recordId})) {
                while (cursor.moveToNext()) {
                    String category = personalOsStrengthCategory(cursor.getString(0));
                    if (!category.isEmpty() && !categories.contains(category)) {
                        categories.add(category);
                    }
                }
            }
            if (categories.isEmpty()) {
                String existingCategory = personalOsStrengthCategory(currentCategory);
                if (!existingCategory.isEmpty()) {
                    categories.add(existingCategory);
                }
            }
        } else {
            categories.add("기타");
        }

        if (categories.isEmpty()) {
            categories.add("기타");
        }

        JSONObject metadata;
        try {
            metadata = currentMetadata == null || currentMetadata.trim().isEmpty()
                    ? new JSONObject() : new JSONObject(currentMetadata);
        } catch (Exception exception) {
            metadata = new JSONObject();
        }
        try {
            metadata.put("os_categories", new JSONArray(categories));
            metadata.put("category_codes", FitnessRecordContract.categoryCodes(categories));
            metadata.put("contract_version", FitnessRecordContract.VERSION);
        } catch (Exception ignored) {
            // The category column still carries the primary OS summary.
        }

        String nextCategory = categories.get(0);
        String nextMetadata = metadata.toString();
        String nextScope = publishToOs ? "both" : currentScope;
        if (nextCategory.equals(currentCategory)
                && nextMetadata.equals(currentMetadata)
                && nextScope.equals(currentScope)) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put("category", nextCategory);
        values.put("metadata", nextMetadata);
        values.put("scope", nextScope);
        values.put("updated_at", now());
        database.update("workout_records", values, "id = ?", new String[]{recordId});
        return true;
    }

    private static String sharedCardioCategory(String category) {
        String value = emptyToDefault(category, "").trim();
        if (value.isEmpty() || "cardio".equalsIgnoreCase(value) || "유산소".equals(value)) {
            return "유산소";
        }
        return value;
    }

    private static String personalOsStrengthCategory(String category) {
        String value = emptyToDefault(category, "").trim().toLowerCase();
        switch (value) {
            case "가슴":
            case "가슴운동":
            case "chest":
                return "가슴";
            case "등":
            case "등운동":
            case "back":
                return "등";
            case "하체":
            case "하체운동":
            case "legs":
                return "하체";
            case "어깨":
            case "어깨운동":
            case "shoulders":
                return "어깨";
            case "복부":
            case "복부운동":
            case "복근":
            case "복근운동":
            case "abs":
                return "복부";
            case "삼두":
            case "삼두운동":
            case "triceps":
                return "삼두";
            case "이두":
            case "이두운동":
            case "biceps":
                return "이두";
            case "팔":
            case "팔운동":
            case "arms":
                return "팔";
            default:
                return "";
        }
    }

    private static String sharedWorkoutDisplayName(
            String sourceApp,
            String workoutType,
            String category,
            String exerciseName
    ) {
        if (!"os".equals(sourceApp)) {
            return emptyToDefault(exerciseName, "운동");
        }
        if ("strength".equals(workoutType)) {
            String label = personalOsStrengthCategory(category);
            return label.isEmpty() ? "근력운동" : label + "운동";
        }
        if ("cardio".equals(workoutType)) {
            String cardio = sharedCardioCategory(category);
            return "유산소".equals(cardio) ? cardio : "유산소 · " + cardio;
        }
        return emptyToDefault(exerciseName, "기타운동");
    }

    private static String calendarWorkoutLabel(String workoutType, String category) {
        if ("cardio".equals(workoutType)) {
            return "유산소";
        }
        if ("strength".equals(workoutType)) {
            String label = personalOsStrengthCategory(category);
            return label.isEmpty() ? "근력" : label;
        }
        return "기타";
    }

    private static void appendCalendarPart(CalendarDaySummary summary, String part) {
        if (part == null || part.trim().isEmpty() || "기타".equals(part)) {
            return;
        }
        if (summary.muscles.length() == 0) {
            summary.muscles = part;
        } else if (!summary.muscles.contains(part)) {
            summary.muscles += " · " + part;
        }
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

    public int mealCountForDate(String date) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*) FROM meal_records WHERE deleted_at IS NULL "
                        + "AND scope IN ('fitness', 'both') AND date = ?",
                new String[]{emptyToToday(date)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public List<String> mealsForDate(String date) {
        List<String> rows = new ArrayList<>();
        String sql = "SELECT date, menu, calories, protein_grams, carbs_grams, fat_grams FROM meal_records WHERE deleted_at IS NULL AND scope IN ('fitness', 'both')";
        String[] args = null;
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{emptyToToday(date)};
        }
        sql += " ORDER BY date DESC, created_at ASC, id ASC LIMIT 20";

        try (Cursor cursor = db().rawQuery(sql, args)) {
            String currentDate = null;
            int mealNumber = 0;
            while (cursor.moveToNext()) {
                String mealDate = cursor.getString(0);
                if (!mealDate.equals(currentDate)) {
                    currentDate = mealDate;
                    mealNumber = 0;
                }
                mealNumber++;
                String calories = cursor.getInt(2) + "kcal";
                String protein = trimDouble(cursor.getDouble(3)) + "g 단백질";
                String carbs = trimDouble(cursor.getDouble(4)) + "g 탄수화물";
                String fat = trimDouble(cursor.getDouble(5)) + "g 지방";
                rows.add(formatDate(mealDate) + "  " + mealNumber + "끼  "
                        + cursor.getString(1) + "  " + calories + "  " + protein
                        + "  " + carbs + "  " + fat);
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
        tables.add("meal_record_items");
        tables.add("meal_record_item_nutrients");
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
            object.put("contract_version", FitnessRecordContract.VERSION);
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

    private static String mergedCardioMetadata(
            String metadata,
            String status,
            CardioActivityType activityType,
            String endedAt,
            int durationSeconds,
            double distanceMeters,
            Integer averageHeartRateBpm
    ) {
        try {
            JSONObject object = metadata == null || metadata.trim().isEmpty()
                    ? new JSONObject()
                    : new JSONObject(metadata);
            object.put("status", status);
            object.put("activity_type", activityType.id());
            object.put("ended_at", emptyToDefault(endedAt, ""));
            object.put("duration_seconds", durationSeconds);
            object.put("active_duration_seconds", durationSeconds);
            object.put("distance_meters", distanceMeters);
            object.put("average_heart_rate",
                    averageHeartRateBpm == null ? JSONObject.NULL : averageHeartRateBpm);
            object.put("average_pace_seconds_per_km",
                    distanceMeters <= 0 ? JSONObject.NULL
                            : Math.round(durationSeconds / (distanceMeters / 1000d)));
            object.put("contract_version", FitnessRecordContract.VERSION);
            return object.toString();
        } catch (Exception exception) {
            return json(
                    "status", status,
                    "activity_type", activityType.id(),
                    "ended_at", emptyToDefault(endedAt, ""),
                    "duration_seconds", String.valueOf(durationSeconds),
                    "distance_meters", String.valueOf(distanceMeters),
                    "average_heart_rate", averageHeartRateBpm == null
                            ? "" : String.valueOf(averageHeartRateBpm)
            );
        }
    }

    private static String metadataWithAverageHeartRate(
            String metadata,
            Integer averageHeartRateBpm
    ) {
        try {
            JSONObject object = metadata == null || metadata.trim().isEmpty()
                    ? new JSONObject()
                    : new JSONObject(metadata);
            object.put("average_heart_rate",
                    averageHeartRateBpm == null ? JSONObject.NULL : averageHeartRateBpm);
            return object.toString();
        } catch (Exception exception) {
            return metadata == null ? "{}" : metadata;
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

    private static String normalizeMealType(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "식사" : normalized;
    }

    private static String mealTypeFromMetadata(String metadata) {
        try {
            return normalizeMealType(new JSONObject(metadata == null ? "{}" : metadata)
                    .optString("meal_type", "식사"));
        } catch (Exception ignored) {
            return "식사";
        }
    }

    private static int itemCountFromMetadata(String metadata) {
        try {
            return Math.max(0, new JSONObject(metadata == null ? "{}" : metadata)
                    .optInt("item_count", 0));
        } catch (Exception ignored) {
            return 0;
        }
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

    private static String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    public static final class SessionRecordEntry {
        public final String id;
        public final String summary;
        public final String sourceApp;
        public final String workoutType;
        public final int durationSeconds;
        public final Double averageHeartRateBpm;

        public SessionRecordEntry(
                String id,
                String summary,
                String sourceApp,
                String workoutType,
                int durationSeconds,
                Double averageHeartRateBpm
        ) {
            this.id = id;
            this.summary = summary;
            this.sourceApp = sourceApp;
            this.workoutType = workoutType;
            this.durationSeconds = durationSeconds;
            this.averageHeartRateBpm = averageHeartRateBpm;
        }
    }

    private static String initialWorkoutMetadata(
            String status,
            String startedAt,
            String endedAt,
            String memo
    ) {
        try {
            JSONObject object = new JSONObject();
            object.put("contract_version", FitnessRecordContract.VERSION);
            object.put("status", status);
            object.put("started_at", emptyToDefault(startedAt, ""));
            object.put("ended_at", emptyToDefault(endedAt, ""));
            object.put("memo", emptyToDefault(memo, ""));
            return object.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("운동 메타데이터를 만들지 못했습니다.", exception);
        }
    }

    private String exerciseRecordType(String exerciseId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT record_type FROM workout_exercises WHERE id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{exerciseId}
        )) {
            if (cursor.moveToFirst()) {
                return FitnessRecordContract.normalizeRecordType(cursor.getString(0));
            }
        }
        return FitnessRecordContract.WEIGHT_REPS;
    }

    private String setRecordType(String setId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT we.record_type FROM workout_sets ws "
                        + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id "
                        + "WHERE ws.id = ? AND ws.deleted_at IS NULL LIMIT 1",
                new String[]{setId}
        )) {
            if (cursor.moveToFirst()) {
                return FitnessRecordContract.normalizeRecordType(cursor.getString(0));
            }
        }
        return FitnessRecordContract.WEIGHT_REPS;
    }

    private static void validateSetInput(String recordType, SetInput input) {
        if (input == null) {
            throw new IllegalArgumentException("세트 입력이 없습니다.");
        }
        validateNonNegative(input.weightKg, "중량");
        validateNonNegative(input.distanceMeters, "거리");
        validateNonNegative(input.assistedWeightKg, "보조 중량");
        validateNonNegative(input.addedWeightKg, "추가 중량");
        if (input.reps != null && input.reps < 0) {
            throw new IllegalArgumentException("횟수는 음수일 수 없습니다.");
        }
        if (input.durationSeconds != null && input.durationSeconds < 0) {
            throw new IllegalArgumentException("시간은 음수일 수 없습니다.");
        }
        if (input.restSeconds != null && input.restSeconds < 0) {
            throw new IllegalArgumentException("휴식 시간은 음수일 수 없습니다.");
        }
        if (input.rpe != null && (input.rpe < 1 || input.rpe > 10)) {
            throw new IllegalArgumentException("RPE는 1부터 10 사이여야 합니다.");
        }
        if (!input.completed) {
            return;
        }

        switch (FitnessRecordContract.normalizeRecordType(recordType)) {
            case FitnessRecordContract.REPS_ONLY:
                requirePositive(input.reps, "횟수");
                break;
            case FitnessRecordContract.TIME:
                requirePositive(input.durationSeconds, "시간");
                break;
            case FitnessRecordContract.WEIGHT_TIME:
                requirePositive(input.weightKg, "중량");
                requirePositive(input.durationSeconds, "시간");
                break;
            case FitnessRecordContract.ASSISTED_WEIGHT_REPS:
                requirePositive(input.assistedWeightKg, "보조 중량");
                requirePositive(input.reps, "횟수");
                break;
            case FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS:
                requirePositive(input.addedWeightKg, "추가 중량");
                requirePositive(input.reps, "횟수");
                break;
            case FitnessRecordContract.WEIGHT_REPS:
            default:
                requirePositive(input.weightKg, "중량");
                requirePositive(input.reps, "횟수");
                break;
        }
    }

    public static void validateSetInputForRecordType(
            String recordType,
            SetInput input
    ) {
        validateSetInput(recordType, input);
    }

    private static void validateNonNegative(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException(label + "은 0 이상의 유한한 값이어야 합니다.");
        }
    }

    private static void requirePositive(Number value, String label) {
        if (value == null || value.doubleValue() <= 0) {
            throw new IllegalArgumentException(label + "을(를) 입력하세요.");
        }
    }

    private static double setVolume(String recordType, SetInput input) {
        String normalized = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.WEIGHT_REPS.equals(normalized)) {
            return valueOrZero(input.weightKg) * intOrZero(input.reps);
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(normalized)) {
            return valueOrZero(input.addedWeightKg) * intOrZero(input.reps);
        }
        return 0;
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }

    private static int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static void putNullable(ContentValues values, String key, Double value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    private static void putNullable(ContentValues values, String key, Integer value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    private static void putNullable(ContentValues values, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            values.putNull(key);
        } else {
            values.put(key, value.trim());
        }
    }

    public static final class SessionMetrics {
        public double totalVolumeKg;
        public int setCount;
        public double totalDistanceMeters;
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

    public static final class CalendarDaySummary {
        public boolean hasWorkout;
        public boolean hasMeal;
        public boolean hasWeight;
        public String muscles = "";
    }

    public static final class SessionExerciseEntry {
        public final String id;
        public final String exerciseId;
        public final int orderIndex;
        public final String name;
        public final String uiPart;
        public final String equipment;
        public final String recordType;

        public SessionExerciseEntry(
                String id,
                String exerciseId,
                int orderIndex,
                String name,
                String uiPart,
                String equipment,
                String recordType
        ) {
            this.id = id;
            this.exerciseId = exerciseId;
            this.orderIndex = orderIndex;
            this.name = name;
            this.uiPart = uiPart;
            this.equipment = equipment;
            this.recordType = FitnessRecordContract.normalizeRecordType(recordType);
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
        public final int durationSeconds;
        public final double distanceMeters;
        public final double assistedWeightKg;
        public final double addedWeightKg;

        public SessionSetEntry(
                String id,
                int setIndex,
                double weightKg,
                int actualReps,
                Integer rpe,
                Integer restSeconds,
                boolean isCompleted,
                int durationSeconds,
                double distanceMeters,
                double assistedWeightKg,
                double addedWeightKg
        ) {
            this.id = id;
            this.setIndex = setIndex;
            this.weightKg = weightKg;
            this.actualReps = actualReps;
            this.rpe = rpe;
            this.restSeconds = restSeconds;
            this.isCompleted = isCompleted;
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
            this.assistedWeightKg = assistedWeightKg;
            this.addedWeightKg = addedWeightKg;
        }
    }

    public static final class SetInput {
        public final Double weightKg;
        public final Integer reps;
        public final Integer durationSeconds;
        public final Double distanceMeters;
        public final Double assistedWeightKg;
        public final Double addedWeightKg;
        public final Integer rpe;
        public final Integer restSeconds;
        public final boolean completed;

        public SetInput(
                Double weightKg,
                Integer reps,
                Integer durationSeconds,
                Double assistedWeightKg,
                Double addedWeightKg,
                Integer rpe,
                Integer restSeconds,
                boolean completed
        ) {
            this(weightKg, reps, durationSeconds, null, assistedWeightKg, addedWeightKg,
                    rpe, restSeconds, completed);
        }

        public SetInput(
                Double weightKg,
                Integer reps,
                Integer durationSeconds,
                Double distanceMeters,
                Double assistedWeightKg,
                Double addedWeightKg,
                Integer rpe,
                Integer restSeconds,
                boolean completed
        ) {
            this.weightKg = weightKg;
            this.reps = reps;
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
            this.assistedWeightKg = assistedWeightKg;
            this.addedWeightKg = addedWeightKg;
            this.rpe = rpe;
            this.restSeconds = restSeconds;
            this.completed = completed;
        }
    }

    public static final class FleekImportResult {
        public int importedSessions;
        public int importedExercises;
        public int importedSets;
        public int masterMatchedSets;
        public int skippedDuplicateSessions;
        public int skippedRows;

        public String summary() {
            List<String> parts = new ArrayList<>();
            parts.add("세션 " + importedSessions + "건");
            parts.add("세트 " + importedSets + "건");
            if (skippedDuplicateSessions > 0) {
                parts.add("중복 세션 " + skippedDuplicateSessions + "건 제외");
            }
            if (skippedRows > 0) {
                parts.add("해석 불가 행 " + skippedRows + "건 제외");
            }
            return String.join(" · ", parts);
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
        public String workoutType = "other";
        public int durationSeconds;
        public Double averageHeartRateBpm;
    }

    /** 직전 세션의 같은 종목 수행 기록. */
    public static final class ExerciseHistory {
        public String date = "";
        public double totalVolumeKg;
        public List<SessionSetEntry> sets = new ArrayList<>();
    }

    /** 종목 역대 기록 요약. */
    public static final class ExerciseBests {
        public double maxWeightKg;
        public int repsAtMaxWeight;
        public String maxWeightDate = "";
        public double bestE1rmKg;
        public String bestE1rmDate = "";
        public double bestSessionVolumeKg;
        public String bestVolumeDate = "";
        public int sessionCount;
    }

    public static final class MealMenuPreset {
        public final String id;
        public final String name;
        public final Integer calories;
        public final Double proteinGrams;
        public final Double carbsGrams;
        public final Double fatGrams;

        public MealMenuPreset(String id, String name, Integer calories, Double proteinGrams,
                              Double carbsGrams, Double fatGrams) {
            this.id = id;
            this.name = name;
            this.calories = calories;
            this.proteinGrams = proteinGrams;
            this.carbsGrams = carbsGrams;
            this.fatGrams = fatGrams;
        }
    }

    public static final class MealEntry {
        public final String id;
        public final String date;
        public final String mealType;
        public final String menu;
        public final int calories;
        public final double proteinGrams;
        public final double carbsGrams;
        public final double fatGrams;
        public final int compositionCount;
        public final String createdAt;

        public MealEntry(String id, String date, String mealType, String menu, int calories,
                         double proteinGrams, double carbsGrams, double fatGrams,
                         int compositionCount, String createdAt) {
            this.id = id;
            this.date = date;
            this.mealType = mealType;
            this.menu = menu;
            this.calories = calories;
            this.proteinGrams = proteinGrams;
            this.carbsGrams = carbsGrams;
            this.fatGrams = fatGrams;
            this.compositionCount = compositionCount;
            this.createdAt = createdAt;
        }
    }

    /** 식사 기록 한 줄. 값은 전부 섭취 당시 스냅샷이며 카탈로그 수정에 영향받지 않는다. */
    public static final class MealItemEntry {
        public final String id;
        public final String foodName;
        public final double quantity;
        public final String unit;
        public final String prepState;
        public final NutritionProfile profile;

        public MealItemEntry(String id, String foodName, double quantity, String unit,
                             String prepState, NutritionProfile profile) {
            this.id = id;
            this.foodName = foodName;
            this.quantity = quantity;
            this.unit = unit;
            this.prepState = prepState;
            this.profile = profile;
        }

        public String label() {
            return foodName + " · " + NutritionCalculator.trim(quantity) + unit
                    + " · " + Math.round(profile.calories()) + "kcal";
        }
    }

    public static final class MealNutritionSummary {
        public final int mealCount;
        public final double calories;
        public final double proteinGrams;
        public final double carbsGrams;
        public final double fatGrams;

        public MealNutritionSummary(int mealCount, double calories, double proteinGrams,
                                    double carbsGrams, double fatGrams) {
            this.mealCount = mealCount;
            this.calories = calories;
            this.proteinGrams = proteinGrams;
            this.carbsGrams = carbsGrams;
            this.fatGrams = fatGrams;
        }
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
