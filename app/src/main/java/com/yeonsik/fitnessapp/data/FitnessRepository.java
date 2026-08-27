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
import java.time.ZoneId;
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
    private final CompositionTemplateRepository compositionTemplateRepository;
    private String userId;

    public FitnessRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
        this.compositionTemplateRepository = new CompositionTemplateRepository(dbHelper, this.userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
        compositionTemplateRepository.setUserId(this.userId);
    }

    public String currentUserId() {
        return userId;
    }

    /** Local reusable meal/menu definitions. Detailed templates are intentionally not in the shared sync set. */
    public CompositionTemplateRepository compositionTemplates() {
        return compositionTemplateRepository;
    }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        if (AccountOwnerPolicy.shouldClaimLocalRows(this.userId, nextUserId)) {
            SQLiteDatabase database = db();
            database.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put("user_id", nextUserId);
                for (String table : claimableOwnerTables()) {
                    database.update(
                            table,
                            values,
                            "user_id = ?",
                            new String[]{SupabaseConfig.DEFAULT_USER_ID}
                    );
                }
                claimSingletonNutritionGoal(database, nextUserId);
                claimConflictFreeDailyRows(database, nextUserId);
                database.delete(
                        "devices",
                        "user_id = ?",
                        new String[]{SupabaseConfig.DEFAULT_USER_ID}
                );
                ensureDevice(database, nextUserId);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } else {
            ensureDevice(nextUserId);
        }
        this.userId = nextUserId;
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
        db().update(
                "workout_records",
                record,
                "id = ? AND user_id = ?",
                new String[]{recordId, userId}
        );
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
        requireOwnedWorkoutRecord(recordId);
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
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{recordId, userId});
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
                "id = ? AND user_id = ? AND workout_type = 'cardio' AND deleted_at IS NULL",
                new String[]{recordId, userId}
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
        // FLEEK exports the historical RPE field. Keep importing that value into the legacy
        // column below, but never reinterpret it as RIR (the scales have opposite meanings).
        return new SetInput(
                set.weightKg,
                set.reps,
                set.durationSeconds,
                set.assistedWeightKg,
                set.addedWeightKg,
                null,
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
        requireOwnedWorkoutRecord(recordId);
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
        requireOwnedWorkoutRecord(recordId);

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

    public String addSet(String recordId, String exerciseId, int setIndex, double weightKg, int reps, Integer rir, boolean completed) {
        return addSet(recordId, exerciseId, setIndex, weightKg, reps, rir, null, completed);
    }

    public String addSet(String recordId, String exerciseId, int setIndex, double weightKg, int reps, Integer rir, Integer restSeconds, boolean completed) {
        return addTypedSet(
                recordId,
                exerciseId,
                setIndex,
                new SetInput(weightKg, reps, null, null, null, rir, restSeconds, completed)
        );
    }

    public String addTypedSet(String recordId, String exerciseId, int setIndex, SetInput input) {
        requireOwnedWorkoutExercise(recordId, exerciseId);
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
        putNullable(values, "rir", input.rir);
        values.putNull("memo");
        db().insertOrThrow("workout_sets", null, values);
        updateSessionTotalVolume(recordId);
        return id;
    }

    public void updateSet(String recordId, String setId, double weightKg, int reps, Integer rir, boolean completed) {
        updateSet(recordId, setId, weightKg, reps, rir, null, completed);
    }

    public void updateSet(String recordId, String setId, double weightKg, int reps, Integer rir, Integer restSeconds, boolean completed) {
        updateTypedSet(
                recordId,
                setId,
                new SetInput(weightKg, reps, null, null, null, rir, restSeconds, completed)
        );
    }

    public void updateTypedSet(String recordId, String setId, SetInput input) {
        if (emptyToNull(recordId) == null || emptyToNull(setId) == null) {
            return;
        }
        requireOwnedWorkoutSet(recordId, setId);

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
        putNullable(values, "rir", input.rir);
        putNullable(values, "rest_seconds", input.restSeconds);
        values.put("is_completed", input.completed ? 1 : 0);
        values.put("updated_at", now());
        db().update(
                "workout_sets",
                values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{setId, userId}
        );
        updateSessionTotalVolume(recordId);
    }

    public void deleteSet(String recordId, String setId) {
        if (emptyToNull(recordId) == null || emptyToNull(setId) == null) {
            return;
        }
        requireOwnedWorkoutSet(recordId, setId);

        String now = now();
        ContentValues values = new ContentValues();
        values.put("deleted_at", now);
        values.put("updated_at", now);
        db().update(
                "workout_sets",
                values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{setId, userId}
        );
        updateSessionTotalVolume(recordId);
    }

    public void deleteExercise(String recordId, String workoutExerciseId) {
        if (emptyToNull(recordId) == null || emptyToNull(workoutExerciseId) == null) {
            return;
        }
        requireOwnedWorkoutExercise(recordId, workoutExerciseId);

        String now = now();
        ContentValues values = new ContentValues();
        values.put("deleted_at", now);
        values.put("updated_at", now);
        db().update(
                "workout_sets",
                values,
                "workout_exercise_id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{workoutExerciseId, userId}
        );
        db().update(
                "workout_exercises",
                values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{workoutExerciseId, userId}
        );
        updateSessionTotalVolume(recordId);
        updateSharedWorkoutSummary(recordId, false);
    }

    public List<SessionSetEntry> setsForExercise(String workoutExerciseId) {
        List<SessionSetEntry> rows = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id, set_index, COALESCE(weight_kg, 0), COALESCE(actual_reps, 0), "
                        + "rir, rest_seconds, is_completed, COALESCE(duration_seconds, 0), "
                        + "COALESCE(distance_meters, 0), COALESCE(assisted_weight_kg, 0), "
                        + "COALESCE(added_weight_kg, 0) FROM workout_sets " +
                        "WHERE workout_exercise_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL ORDER BY set_index",
                new String[]{workoutExerciseId, userId})) {
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
                        + "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId, userId})) {
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
        String recordDate = requireRecordDate(date);
        double validatedWeight = requireBodyWeight(weightKg);
        BodyMetricEntry existing = bodyMetricForDate(recordDate);
        if (existing != null) {
            updateBodyMetric(existing.id, recordDate, validatedWeight, memo);
            return existing.id;
        }

        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("date", recordDate);
        values.put("weight_kg", validatedWeight);
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
                + "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1";
        try (Cursor cursor = db().rawQuery(sql, new String[]{id, userId})) {
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
                + "WHERE user_id = ? AND deleted_at IS NULL AND scope IN ('fitness', 'both')";
        String[] args = new String[]{userId};
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{userId, emptyToToday(date)};
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
        String recordDate = requireRecordDate(date);
        double validatedWeight = requireBodyWeight(weightKg);
        ContentValues values = new ContentValues();
        values.put("date", recordDate);
        values.put("weight_kg", validatedWeight);
        values.put("metadata", json("item_type", "body_weight", "memo", emptyToDefault(memo, "")));
        values.put("updated_at", now());
        db().update(
                "weight_records",
                values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{id, userId}
        );
    }

    public void deleteBodyMetric(String id) {
        if (emptyToNull(id) == null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("deleted_at", now());
        values.put("updated_at", now());
        db().update(
                "weight_records",
                values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{id, userId}
        );
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
                nextMealLabelForDate(date),
                menuText,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                compositionItems
        );
    }

    public String addMeal(String date, String mealLabel, String menuText, Integer calories,
                          Double proteinGrams, Double carbsGrams, Double fatGrams,
                          List<MealCompositionItem> compositionItems) {
        return insertMeal(
                date,
                mealLabel,
                menuText,
                LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                compositionItems
        );
    }

    /** Records an actual meal without asking the user to invent a separate meal name. */
    public String addMealAtTime(String date, String mealTime, Integer calories,
                                Double proteinGrams, Double carbsGrams, Double fatGrams,
                                List<MealCompositionItem> compositionItems) {
        List<MealCompositionItem> items = compositionItems == null
                ? Collections.emptyList()
                : compositionItems;
        String mealLabel = nextMealLabelForDate(date);
        String firstFoodName = items.isEmpty() ? null : items.get(0).food.displayName();
        String compatibilityMenu = MealEntryPolicy.previewTitle(
                firstFoodName,
                items.size(),
                mealLabel + " 식사"
        );
        return insertMeal(
                date,
                mealLabel,
                compatibilityMenu,
                mealTime,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                items
        );
    }

    /** Records one meal whose top-level entries are menus and whose children are ingredients. */
    public String addMealMenusAtTime(
            String date,
            String mealTime,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            List<MealMenuSelection> menuSelections
    ) {
        List<MealMenuSelection> menus = menuSelections == null
                ? Collections.emptyList()
                : menuSelections;
        String mealLabel = nextMealLabelForDate(date);
        String firstMenuName = menus.isEmpty() ? null : menus.get(0).menu.food.displayName();
        String compatibilityMenu = MealEntryPolicy.previewTitle(
                firstMenuName,
                menus.size(),
                mealLabel + " Meal"
        );
        return insertMealMenus(
                date,
                mealLabel,
                compatibilityMenu,
                mealTime,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                menus
        );
    }

    /** Records a dining-out meal without creating a catalog row. */
    public String addDiningOutMealAtTime(
            String date,
            String mealTime,
            String storeName,
            String menuName
    ) {
        return addDiningOutMealAtTime(
                date,
                mealTime,
                storeName,
                menuName,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Records a dining-out meal with optional user-entered macro estimates.
     * These values are explicitly marked as estimates and are not catalog snapshots.
     */
    public String addDiningOutMealAtTime(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams
    ) {
        return addDiningOutMealAtTime(
                date,
                mealTime,
                storeName,
                menuName,
                carbsGrams,
                proteinGrams,
                fatGrams,
                null
        );
    }

    /** Records a dining-out meal with option names kept in the meal snapshot. */
    public String addDiningOutMealAtTimeWithOptions(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams,
            MealCompositionItem menuSnapshot,
            List<String> optionNames
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                null,
                proteinGrams,
                carbsGrams,
                fatGrams,
                null,
                null,
                null,
                null,
                menuSnapshot,
                optionNames,
                true
        );
    }

    /**
     * Records a dining-out meal and optionally snapshots the saved catalog menu into it.
     */
    public String addDiningOutMealAtTime(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams,
            MealCompositionItem menuSnapshot
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                null,
                proteinGrams,
                carbsGrams,
                fatGrams,
                null,
                null,
                null,
                null,
                menuSnapshot,
                Collections.emptyList(),
                true
        );
    }

    /**
     * Records a dining-out meal with a complete, user-entered estimated nutrition profile.
     * Detailed nutrients are stored in the existing meal-item snapshot columns.
     */
    public String addDiningOutMealAtTimeWithNutrition(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            MealCompositionItem menuSnapshot
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                null,
                menuSnapshot,
                Collections.emptyList(),
                false
        );
    }

    /** Records a dining-out meal with complete nutrition and option snapshots. */
    public String addDiningOutMealAtTimeWithNutritionAndOptions(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            MealCompositionItem menuSnapshot,
            List<String> optionNames
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                null,
                menuSnapshot,
                optionNames,
                false
        );
    }

    /** Records complete nutrition and separately snapshots nutrient-bearing options. */
    public String addDiningOutMealAtTimeWithNutritionAndOptionNutrition(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            MealCompositionItem menuSnapshot,
            List<DiningOutOption> options
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                null,
                menuSnapshot,
                options,
                false
        );
    }

    /** Records complete nutrition for a directly registered dining-out branch. */
    public String addDiningOutMealAtTimeWithBranchAndNutritionAndOptionNutrition(
            String date,
            String mealTime,
            String storeName,
            String branchName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            MealCompositionItem menuSnapshot,
            List<DiningOutOption> options
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                branchName,
                null,
                menuSnapshot,
                options,
                false
        );
    }

    /** Records macro nutrition and separately snapshots nutrient-bearing options. */
    public String addDiningOutMealAtTimeWithOptionNutrition(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams,
            MealCompositionItem menuSnapshot,
            List<DiningOutOption> options
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                null,
                proteinGrams,
                carbsGrams,
                fatGrams,
                null,
                null,
                null,
                null,
                menuSnapshot,
                options,
                true
        );
    }

    /** Records macro nutrition for a directly registered dining-out branch. */
    public String addDiningOutMealAtTimeWithBranchAndOptionNutrition(
            String date,
            String mealTime,
            String storeName,
            String branchName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams,
            MealCompositionItem menuSnapshot,
            List<DiningOutOption> options
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                null,
                proteinGrams,
                carbsGrams,
                fatGrams,
                null,
                null,
                null,
                branchName,
                null,
                menuSnapshot,
                options,
                true
        );
    }

    /** Records complete nutrition, exact PriceTrace identity, and option nutrition. */
    public String addDiningOutMealAtTimeWithIdentityAndNutritionAndOptionNutrition(
            String date,
            String mealTime,
            DiningOutIdentity identity,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            MealCompositionItem menuSnapshot,
            List<DiningOutOption> options
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                identity.restaurantName,
                identity.menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                identity,
                menuSnapshot,
                options,
                false
        );
    }

    /** Records macro nutrition and option nutrition with exact PriceTrace identity. */
    public String addDiningOutMealAtTimeWithIdentityAndOptionNutrition(
            String date,
            String mealTime,
            DiningOutIdentity identity,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams,
            MealCompositionItem menuSnapshot,
            List<DiningOutOption> options
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                identity.restaurantName,
                identity.menuName,
                null,
                proteinGrams,
                carbsGrams,
                fatGrams,
                null,
                null,
                null,
                identity,
                menuSnapshot,
                options,
                true
        );
    }

    /** Records a complete-nutrition dining-out meal linked to exact PriceTrace identities. */
    public String addDiningOutMealAtTimeWithNutritionAndIdentity(
            String date,
            String mealTime,
            DiningOutIdentity identity,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            MealCompositionItem menuSnapshot,
            List<String> optionNames
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                identity.restaurantName,
                identity.menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                identity,
                menuSnapshot,
                optionNames,
                false
        );
    }

    /** Records a macro-estimate dining-out meal linked to exact PriceTrace identities. */
    public String addDiningOutMealAtTimeWithIdentity(
            String date,
            String mealTime,
            DiningOutIdentity identity,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams,
            MealCompositionItem menuSnapshot,
            List<String> optionNames
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                identity.restaurantName,
                identity.menuName,
                null,
                proteinGrams,
                carbsGrams,
                fatGrams,
                null,
                null,
                null,
                identity,
                menuSnapshot,
                optionNames,
                true
        );
    }

    /** Records a whole-menu snapshot and one user's share as a separate local allocation. */
    public String addDiningOutMealAtTimeWithConsumption(
            String date,
            String mealTime,
            String storeName,
            String branchName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            DiningOutIdentity identity,
            MealCompositionItem menuSnapshot,
            List<DiningOutOption> options,
            double nominalServings,
            DiningOutConsumption consumption,
            boolean hasCompleteNutrition
    ) {
        if (consumption == null) {
            throw new IllegalArgumentException("공유 외식 섭취 정보가 필요합니다.");
        }
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                branchName,
                identity,
                menuSnapshot,
                options,
                !hasCompleteNutrition,
                consumption,
                nominalServings,
                null
        );
    }

    /** Records one dining-out meal containing multiple top-level menus. */
    public String addDiningOutMealAtTimeWithMenusAndConsumption(
            String date,
            String mealTime,
            String storeName,
            String branchName,
            DiningOutIdentity identity,
            List<MealMenuSelection> menuSelections,
            double nominalServings,
            DiningOutConsumption consumption
    ) {
        if (consumption == null) {
            throw new IllegalArgumentException("공유 외식 섭취 정보가 필요합니다.");
        }
        List<MealMenuSelection> menus = menuSelections == null
                ? Collections.emptyList()
                : menuSelections;
        if (menus.isEmpty()) {
            throw new IllegalArgumentException("외식 메뉴를 하나 이상 추가하세요.");
        }
        NutritionTotals.Builder totalBuilder = NutritionTotals.builder();
        for (MealMenuSelection menu : menus) {
            if (menu == null) {
                throw new IllegalArgumentException("외식 메뉴에 빈 항목이 있습니다.");
            }
            totalBuilder.add(menu.menu.profile);
        }
        NutritionTotals totals = totalBuilder.build();
        Double protein = totals.total(NutritionProfile.PROTEIN_GRAMS).completeValue();
        Double carbs = totals.total(NutritionProfile.CARBS_GRAMS).completeValue();
        Double fat = totals.total(NutritionProfile.FAT_GRAMS).completeValue();
        if (protein == null || carbs == null || fat == null) {
            throw new IllegalArgumentException("외식 메뉴의 탄수화물·단백질·지방을 모두 입력하세요.");
        }
        Double sodium = totals.total(NutritionProfile.SODIUM_MG).completeValue();
        Double sugars = totals.total(NutritionProfile.SUGARS_GRAMS).completeValue();
        Double saturatedFat = totals.total(NutritionProfile.SATURATED_FAT_GRAMS).completeValue();
        String firstMenuName = menus.get(0).menu.food.displayName();
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                firstMenuName,
                (int) Math.round(totals.calories()),
                protein,
                carbs,
                fat,
                sodium,
                sugars,
                saturatedFat,
                branchName,
                identity,
                null,
                Collections.emptyList(),
                false,
                consumption,
                nominalServings,
                menus
        );
    }
    private String insertDiningOutMeal(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Integer caloriesInput,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            DiningOutIdentity identity,
            MealCompositionItem menuSnapshot,
            List<?> optionNames,
            boolean legacyMacroEstimate
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                caloriesInput,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                identity == null ? null : identity.branchName,
                identity,
                menuSnapshot,
                optionNames,
                legacyMacroEstimate
        );
    }

    private String insertDiningOutMeal(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Integer caloriesInput,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            String branchName,
            DiningOutIdentity identity,
            MealCompositionItem menuSnapshot,
            List<?> optionNames,
            boolean legacyMacroEstimate
    ) {
        return insertDiningOutMeal(
                date,
                mealTime,
                storeName,
                menuName,
                caloriesInput,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                branchName,
                identity,
                menuSnapshot,
                optionNames,
                legacyMacroEstimate,
                null,
                null,
                null
        );
    }

    private String insertDiningOutMeal(
            String date,
            String mealTime,
            String storeName,
            String menuName,
            Integer caloriesInput,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            String branchName,
            DiningOutIdentity identity,
            MealCompositionItem menuSnapshot,
            List<?> optionNames,
            boolean legacyMacroEstimate,
            DiningOutConsumption consumption,
            Double nominalServings,
            List<MealMenuSelection> suppliedMenuSelections
    ) {
        String normalizedStoreName = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String normalizedMenuName = MealEntryPolicy.requireDiningOutMenuName(menuName);
        String normalizedBranchName = identity == null
                ? optionalDiningOutBranchName(normalizedStoreName, branchName)
                : identity.branchName;
        List<DiningOutOption> normalizedOptions = normalizeDiningOutOptions(optionNames);
        List<MealMenuSelection> menuSelections = suppliedMenuSelections == null
                ? new ArrayList<>()
                : new ArrayList<>(suppliedMenuSelections);
        if (menuSelections.isEmpty() && menuSnapshot != null) {
            MealMenuSelection menuSelection = normalizedOptions.isEmpty()
                    ? MealMenuSelection.standalone(menuSnapshot)
                    : MealMenuSelection.diningOut(
                            menuSnapshot,
                            userId,
                            normalizedStoreName,
                            normalizedOptions
                    );
            menuSelections.add(menuSelection);
        }
        String compositionTemplateId = compositionTemplateId(normalizedOptions);
        Integer compositionTemplateRevision = compositionTemplateRevision(normalizedOptions);
        double normalizedNominalServings = consumption == null
                ? 1d
                : requireDiningOutNominalServings(nominalServings);
        boolean hasEstimatedNutrition;
        int calories;
        if (legacyMacroEstimate) {
            MealEntryPolicy.requireDiningOutEstimatedMacros(carbsGrams, proteinGrams, fatGrams);
            hasEstimatedNutrition = MealEntryPolicy.hasDiningOutEstimatedMacros(
                    carbsGrams,
                    proteinGrams,
                    fatGrams
            );
            calories = MealEntryPolicy.estimatedDiningOutCalories(
                    carbsGrams,
                    proteinGrams,
                    fatGrams
            );
        } else {
            MealEntryPolicy.requireDiningOutEstimatedNutrition(
                    caloriesInput,
                    proteinGrams,
                    carbsGrams,
                    fatGrams,
                    sodiumMg,
                    sugarsGrams,
                    saturatedFatGrams
            );
            hasEstimatedNutrition = MealEntryPolicy.hasDiningOutEstimatedNutrition(
                    caloriesInput,
                    proteinGrams,
                    carbsGrams,
                    fatGrams,
                    sodiumMg,
                    sugarsGrams,
                    saturatedFatGrams
            );
            calories = hasEstimatedNutrition ? caloriesInput : 0;
        }
        if (consumption != null && !hasEstimatedNutrition) {
            throw new IllegalArgumentException(
                    "공유 외식 기록에는 계산할 영양값을 입력하세요."
            );
        }
        if (menuSelections.isEmpty() && menuSnapshot == null
                && (!normalizedOptions.isEmpty()
                || (!legacyMacroEstimate && hasEstimatedNutrition)
                || (consumption != null && hasEstimatedNutrition))) {
            menuSnapshot = diningOutNutritionSnapshot(
                    normalizedStoreName,
                    normalizedMenuName,
                    calories,
                    proteinGrams,
                    carbsGrams,
                    fatGrams,
                    sodiumMg,
                    sugarsGrams,
                    saturatedFatGrams
            );
            menuSelections.add(normalizedOptions.isEmpty()
                    ? MealMenuSelection.standalone(menuSnapshot)
                    : MealMenuSelection.diningOut(
                            menuSnapshot,
                            userId,
                            normalizedStoreName,
                            normalizedOptions
                    ));
        }
        List<DiningOutOption> allOptions = new ArrayList<>();
        for (MealMenuSelection menuSelection : menuSelections) {
            allOptions.addAll(menuSelection.diningOutOptions);
        }
        if (allOptions.isEmpty()) {
            allOptions.addAll(normalizedOptions);
        }
        if (compositionTemplateId == null) {
            compositionTemplateId = compositionTemplateId(allOptions);
        }
        if (compositionTemplateRevision == null) {
            compositionTemplateRevision = compositionTemplateRevision(allOptions);
        }
        int itemCount = menuSelections.size();
        LocalDate today = LocalDate.now();
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(date, today);
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        boolean isBackfilled = MealEntryPolicy.isBackfilled(recordDate, today);
        String id = newId();
        String now = now();

        ContentValues values = baseValues(id, now);
        values.put("date", recordDate.toString());
        // menu remains populated for the legacy shared-record projection.
        values.put("menu", normalizedMenuName);
        values.put("meal_kind", MealRecordKind.DINING_OUT);
        values.put("store_name", normalizedStoreName);
        if (identity == null) {
            if (normalizedBranchName == null) {
                values.putNull("branch_name");
            } else {
                values.put("branch_name", normalizedBranchName);
            }
            values.putNull("restaurant_id");
            values.putNull("restaurant_location_id");
            values.putNull("restaurant_menu_id");
            values.putNull("catalog_product_id");
        } else {
            values.put("branch_name", identity.branchName);
            values.put("restaurant_id", identity.restaurantId);
            values.put("restaurant_location_id", identity.restaurantLocationId);
            values.put("restaurant_menu_id", identity.restaurantMenuId);
            values.put("catalog_product_id", identity.catalogProductId);
        }
        values.put("menu_name", normalizedMenuName);
        putNullable(values, "composition_template_id", compositionTemplateId);
        if (compositionTemplateRevision == null) {
            values.putNull("composition_template_revision");
        } else {
            values.put("composition_template_revision", compositionTemplateRevision);
        }
        double consumedFraction = consumption == null ? 1d : consumption.consumedFraction;
        double recordedCaloriesValue = calories * consumedFraction;
        double recordedProteinValue = hasEstimatedNutrition
                ? proteinGrams * consumedFraction
                : 0d;
        double recordedCarbsValue = hasEstimatedNutrition
                ? carbsGrams * consumedFraction
                : 0d;
        double recordedFatValue = hasEstimatedNutrition
                ? fatGrams * consumedFraction
                : 0d;
        if (consumption != null) {
            for (DiningOutOption option : allOptions) {
                NutritionProfile consumedOption = option.consumedProfile();
                recordedCaloriesValue += consumedOption.calories();
                if (hasEstimatedNutrition) {
                    recordedProteinValue += consumedOption.proteinGrams();
                    recordedCarbsValue += consumedOption.carbsGrams();
                    recordedFatValue += consumedOption.fatGrams();
                }
            }
        }
        int recordedCalories = (int) Math.round(recordedCaloriesValue);
        Double recordedProtein = hasEstimatedNutrition ? recordedProteinValue : 0d;
        Double recordedCarbs = hasEstimatedNutrition ? recordedCarbsValue : null;
        Double recordedFat = hasEstimatedNutrition ? recordedFatValue : null;
        if (consumption == null) {
            values.putNull("nutrition_calculation_contract");
        } else {
            values.put("nutrition_calculation_contract", DiningOutConsumption.CONTRACT_VERSION);
        }
        values.put("calories", recordedCalories);
        values.put("protein_grams", recordedProtein);
        if (recordedCarbs == null || recordedFat == null) {
            values.putNull("carbs_grams");
            values.putNull("fat_grams");
        } else {
            values.put("carbs_grams", recordedCarbs);
            values.put("fat_grams", recordedFat);
        }
        values.put("is_backfilled", isBackfilled ? 1 : 0);
        if (isBackfilled) {
            values.put("backfilled_at", now);
            values.put("backfill_reason", "manual past dining-out entry");
        } else {
            values.putNull("backfilled_at");
            values.putNull("backfill_reason");
        }
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        String metadata = json(
                "item_type", "meal",
                "meal_kind", MealRecordKind.DINING_OUT,
                "meal_type", normalizeMealLabel(nextMealLabelForDate(date)),
                "eaten_at", eatenAt,
                "store_name", normalizedStoreName,
                "menu_name", normalizedMenuName,
                "nutrition_status", hasEstimatedNutrition ? "estimated" : "unknown",
                "nutrition_source", hasEstimatedNutrition ? "manual_estimate" : "unknown",
                "estimated", hasEstimatedNutrition ? "true" : "false",
                "composition_version", "3",
                "composition_contract", CompositionTemplate.CONTRACT_VERSION,
                "composition_kind", allOptions.isEmpty()
                        ? "standalone"
                        : "ad_hoc_selection",
                "item_count", String.valueOf(itemCount),
                "option_count", String.valueOf(allOptions.size())
        );
        if (identity != null) {
            try {
                JSONObject metadataObject = new JSONObject(metadata);
                metadataObject.put("identity_contract", DiningOutIdentity.CONTRACT_VERSION);
                metadataObject.put("identity_namespace", DiningOutIdentity.NAMESPACE);
                metadataObject.put("restaurant_id", identity.restaurantId);
                metadataObject.put("restaurant_name", identity.restaurantName);
                metadataObject.put("restaurant_location_id", identity.restaurantLocationId);
                if (identity.branchName == null) {
                    metadataObject.put("branch_name", JSONObject.NULL);
                } else {
                    metadataObject.put("branch_name", identity.branchName);
                }
                metadataObject.put("restaurant_menu_id", identity.restaurantMenuId);
                metadataObject.put("menu_name", identity.menuName);
                metadataObject.put("catalog_product_id", identity.catalogProductId);
                metadata = metadataObject.toString();
            } catch (Exception error) {
                throw new IllegalStateException("외식 식별자 메타데이터를 만들지 못했습니다.", error);
            }
        } else if (normalizedBranchName != null) {
            try {
                JSONObject metadataObject = new JSONObject(metadata);
                metadataObject.put("branch_name", normalizedBranchName);
                metadata = metadataObject.toString();
            } catch (Exception error) {
                throw new IllegalStateException("직접 등록 외식 지점 메타데이터를 만들지 못했습니다.", error);
            }
        }
        values.put("metadata", metadata);
        if (compositionTemplateId != null) {
            try {
                JSONObject compositionMetadata = new JSONObject(metadata);
                compositionMetadata.put("composition_template_id", compositionTemplateId);
                if (compositionTemplateRevision == null) {
                    compositionMetadata.put("composition_template_revision", JSONObject.NULL);
                } else {
                    compositionMetadata.put(
                            "composition_template_revision",
                            compositionTemplateRevision
                    );
                }
                metadata = compositionMetadata.toString();
                values.put("metadata", metadata);
            } catch (Exception error) {
                throw new IllegalStateException("구성 템플릿 메타데이터를 만들지 못했습니다.", error);
            }
        }


        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            database.insertOrThrow("meal_records", null, values);
            List<String> menuItemIds = new ArrayList<>();
            for (int menuIndex = 0; menuIndex < menuSelections.size(); menuIndex++) {
                insertMealMenuSnapshot(
                        database,
                        id,
                        menuSelections.get(menuIndex),
                        menuIndex,
                        now,
                        null,
                        compositionTemplateId,
                        compositionTemplateRevision
                );
                String menuItemId = mealItemIdAtOrder(database, id, menuIndex);
                if (menuItemId != null) {
                    menuItemIds.add(menuItemId);
                }
            }
            if (consumption != null) {
                if (menuItemIds.isEmpty()) {
                    throw new IllegalStateException("공유 외식 root 메뉴 스냅샷을 만들지 못했습니다.");
                }
                for (String menuItemId : menuItemIds) {
                    ContentValues itemValues = new ContentValues();
                    itemValues.put("portion_basis_snapshot", "whole_menu");
                    itemValues.put("nominal_servings_snapshot", normalizedNominalServings);
                    int updatedItems = database.update(
                            "meal_record_items",
                            itemValues,
                            "id = ? AND meal_record_id = ? AND user_id = ? "
                                    + "AND deleted_at IS NULL",
                            new String[]{menuItemId, id, userId}
                    );
                    if (updatedItems != 1) {
                        throw new IllegalStateException("공유 외식 메뉴 스냅샷을 갱신하지 못했습니다.");
                    }
                    insertDiningOutConsumption(
                            database,
                            id,
                            menuItemId,
                            consumption,
                            now
                    );
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return id;
    }

    private String mealItemIdAtOrder(
            SQLiteDatabase database,
            String mealRecordId,
            int orderIndex
    ) {
        try (Cursor cursor = database.rawQuery(
                "SELECT id FROM meal_record_items "
                        + "WHERE meal_record_id = ? AND user_id = ? "
                        + "AND deleted_at IS NULL ORDER BY order_index ASC, id ASC LIMIT 1 OFFSET ?",
                new String[]{mealRecordId, userId, String.valueOf(orderIndex)}
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private void insertDiningOutConsumption(
            SQLiteDatabase database,
            String mealRecordId,
            String mealRecordItemId,
            DiningOutConsumption consumption,
            String now
    ) {
        ContentValues values = baseValues(newId(), now);
        values.put("meal_record_id", mealRecordId);
        values.put("meal_record_item_id", mealRecordItemId);
        values.put("contract_version", DiningOutConsumption.CONTRACT_VERSION);
        values.put("consumer_scope", DiningOutConsumption.CONSUMER_SCOPE_SELF);
        values.put("diner_count", consumption.dinerCount);
        values.put("consumed_fraction", consumption.consumedFraction);
        values.put("share_method", consumption.shareMethod);
        values.put("confidence", consumption.confidence);
        database.insertOrThrow("meal_record_item_consumptions", null, values);
    }

    private static double requireDiningOutNominalServings(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)
                || value <= 0d || value > 100d) {
            throw new IllegalArgumentException("메뉴 제공 인분은 0보다 크고 100 이하로 입력하세요.");
        }
        return value;
    }

    private String optionalDiningOutBranchName(String storeName, String value) {
        String normalized = MealEntryPolicy.resolveDiningOutBranchName(storeName, value);
        return normalized.isEmpty() ? null : normalized;
    }

    private List<DiningOutOption> normalizeDiningOutOptions(List<?> optionNames) {
        List<DiningOutOption> normalized = new ArrayList<>();
        if (optionNames == null) {
            return normalized;
        }
        for (Object rawOption : optionNames) {
            DiningOutOption option;
            if (rawOption instanceof DiningOutOption) {
                option = (DiningOutOption) rawOption;
            } else {
                option = DiningOutOption.descriptive(rawOption == null ? "" : rawOption.toString());
            }
            boolean alreadyPresent = false;
            for (DiningOutOption existing : normalized) {
                if (existing.name.equalsIgnoreCase(option.name)
                        && existing.groupKey.equalsIgnoreCase(option.groupKey)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                normalized.add(option);
            }
        }
        return normalized;
    }

    private String compositionTemplateId(List<DiningOutOption> options) {
        if (options == null) {
            return null;
        }
        for (DiningOutOption option : options) {
            if (option == null || option.sourceReference == null) {
                continue;
            }
            try {
                String id = new JSONObject(option.sourceReference)
                        .optString("composition_template_id", "")
                        .trim();
                if (!id.isEmpty()) {
                    return id;
                }
            } catch (Exception ignored) {
                // A legacy source string has no template reference.
            }
        }
        return null;
    }

    private Integer compositionTemplateRevision(List<DiningOutOption> options) {
        if (options == null) {
            return null;
        }
        for (DiningOutOption option : options) {
            if (option == null || option.sourceReference == null) {
                continue;
            }
            try {
                JSONObject source = new JSONObject(option.sourceReference);
                if (!source.has("composition_template_revision")
                        || source.isNull("composition_template_revision")) {
                    continue;
                }
                int revision = source.optInt("composition_template_revision", 0);
                return revision <= 0 ? null : revision;
            } catch (Exception ignored) {
                // A legacy source string has no template revision.
            }
        }
        return null;
    }

    private MealCompositionItem diningOutNutritionSnapshot(
            String storeName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams
    ) {
        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, calories.doubleValue())
                .value(NutritionProfile.PROTEIN_GRAMS, proteinGrams)
                .value(NutritionProfile.CARBS_GRAMS, carbsGrams)
                .value(NutritionProfile.FAT_GRAMS, fatGrams)
                .value(NutritionProfile.SODIUM_MG, sodiumMg)
                .value(NutritionProfile.SUGARS_GRAMS, sugarsGrams)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, saturatedFatGrams)
                .build();
        NutritionFood food = NutritionFood.builder()
                .id(null)
                .ownerId(userId)
                .name(menuName)
                .brand(storeName)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1.0, NutritionUnit.SERVING)
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(profile)
                .source("manual_estimate", "dining_out")
                .dataVersion(NutritionFood.DATA_VERSION_REQUIRED_SEVEN)
                .build();
        return MealCompositionItem.from(food, food.basisAmount);
    }

    private String insertMeal(String date, String mealLabel, String menuText, String mealTime,
                              Integer calories, Double proteinGrams, Double carbsGrams,
                              Double fatGrams, List<MealCompositionItem> compositionItems) {
        List<MealCompositionItem> items = compositionItems == null
                ? Collections.emptyList()
                : compositionItems;
        List<MealMenuSelection> menus = new ArrayList<>();
        for (MealCompositionItem item : items) {
            menus.add(MealMenuSelection.standalone(item));
        }
        return insertMealMenus(
                date,
                mealLabel,
                menuText,
                mealTime,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                menus
        );
    }

    private String insertMealMenus(
            String date,
            String mealLabel,
            String menuText,
            String mealTime,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            List<MealMenuSelection> menuSelections
    ) {
        List<MealMenuSelection> menus = menuSelections == null
                ? Collections.emptyList()
                : menuSelections;
        String id = newId();
        String now = now();
        LocalDate today = LocalDate.now();
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(date, today);
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        boolean isBackfilled = MealEntryPolicy.isBackfilled(recordDate, today);
        ContentValues values = baseValues(id, now);
        values.put("date", recordDate.toString());
        values.put("menu", emptyToDefault(menuText, "Meal"));
        values.put("meal_kind", MealRecordKind.FOOD);
        values.putNull("store_name");
        values.putNull("menu_name");
        values.put("calories", calories == null ? 0 : calories);
        values.put("protein_grams", proteinGrams == null ? 0 : proteinGrams);
        values.put("carbs_grams", carbsGrams == null ? 0 : carbsGrams);
        values.put("fat_grams", fatGrams == null ? 0 : fatGrams);
        values.put("is_backfilled", isBackfilled ? 1 : 0);
        if (isBackfilled) {
            values.put("backfilled_at", now);
            values.put("backfill_reason", "manual past meal entry");
        } else {
            values.putNull("backfilled_at");
            values.putNull("backfill_reason");
        }
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        values.put("metadata", json(
                "item_type", "meal",
                "meal_kind", MealRecordKind.FOOD,
                "meal_type", normalizeMealLabel(mealLabel),
                "eaten_at", eatenAt,
                "estimated", "false",
                "composition_version", "2",
                "item_count", String.valueOf(menus.size())
        ));

        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            database.insertOrThrow("meal_records", null, values);
            for (int menuIndex = 0; menuIndex < menus.size(); menuIndex++) {
                insertMealMenuSnapshot(database, id, menus.get(menuIndex), menuIndex, now);
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
    private void insertMealMenuSnapshot(
            SQLiteDatabase database,
            String mealRecordId,
            MealMenuSelection menu,
            int menuIndex,
            String now
    ) {
        insertMealMenuSnapshot(database, mealRecordId, menu, menuIndex, now, null);
    }

    private void insertMealMenuSnapshot(
            SQLiteDatabase database,
            String mealRecordId,
            MealMenuSelection menu,
            int menuIndex,
            String now,
            List<DiningOutOption> diningOutOptions
    ) {
        insertMealMenuSnapshot(
                database,
                mealRecordId,
                menu,
                menuIndex,
                now,
                diningOutOptions,
                null,
                null
        );
    }

    private void insertMealMenuSnapshot(
            SQLiteDatabase database,
            String mealRecordId,
            MealMenuSelection menu,
            int menuIndex,
            String now,
            List<DiningOutOption> diningOutOptions,
            String compositionTemplateId,
            Integer compositionTemplateRevision
    ) {
        List<DiningOutOption> effectiveOptions = menu.diningOutOptions.isEmpty()
                ? diningOutOptions
                : menu.diningOutOptions;
        String menuItemId = insertMealItemSnapshot(
                database,
                mealRecordId,
                MealItemSnapshot.of(menu.menu, menuIndex),
                now,
                compositionTemplateId,
                compositionTemplateRevision
        );
        for (int componentIndex = 0; componentIndex < menu.components.size(); componentIndex++) {
            DiningOutOption option = effectiveOptions == null
                    || componentIndex >= effectiveOptions.size()
                    ? null
                    : effectiveOptions.get(componentIndex);
            insertMealComponentSnapshot(
                    database,
                    mealRecordId,
                    menuItemId,
                    option == null
                            ? MealItemSnapshot.of(menu.components.get(componentIndex), componentIndex)
                            : MealItemSnapshot.of(
                                    menu.components.get(componentIndex),
                                    componentIndex,
                                    option.groupKey,
                                    option.groupType,
                                    option.role,
                                    option.memberId
                            ),
                    now,
                    option == null ? null : option.consumedFraction
            );
        }
    }

    private String insertMealItemSnapshot(
            SQLiteDatabase database,
            String mealRecordId,
            MealItemSnapshot snapshot,
            String now,
            String compositionTemplateId,
            Integer compositionTemplateRevision
    ) {
        String itemId = newId();
        ContentValues itemValues = snapshotValues(snapshot, now);
        itemValues.put("id", itemId);
        itemValues.put("user_id", userId);
        itemValues.put("meal_record_id", mealRecordId);
        putNullable(itemValues, "composition_template_id", compositionTemplateId);
        if (compositionTemplateRevision == null) {
            itemValues.putNull("composition_template_revision_snapshot");
        } else {
            itemValues.put("composition_template_revision_snapshot", compositionTemplateRevision);
        }
        itemValues.put("device_id", DEVICE_ID);
        database.insertOrThrow("meal_record_items", null, itemValues);

        insertSnapshotMicronutrients(
                database,
                "meal_record_item_nutrients",
                mealRecordId,
                itemId,
                null,
                snapshot,
                now
        );
        return itemId;
    }

    private void insertMealComponentSnapshot(
            SQLiteDatabase database,
            String mealRecordId,
            String mealRecordItemId,
            MealItemSnapshot snapshot,
            String now
    ) {
        insertMealComponentSnapshot(
                database,
                mealRecordId,
                mealRecordItemId,
                snapshot,
                now,
                null
        );
    }

    private void insertMealComponentSnapshot(
            SQLiteDatabase database,
            String mealRecordId,
            String mealRecordItemId,
            MealItemSnapshot snapshot,
            String now,
            Double consumedFraction
    ) {
        String componentId = newId();
        ContentValues values = snapshotValues(snapshot, now);
        values.put("id", componentId);
        values.put("user_id", userId);
        values.put("meal_record_id", mealRecordId);
        values.put("meal_record_item_id", mealRecordItemId);
        putNullable(values, "composition_group_key_snapshot", snapshot.compositionGroupKeySnapshot);
        putNullable(values, "composition_group_type_snapshot", snapshot.compositionGroupTypeSnapshot);
        putNullable(values, "composition_role_snapshot", snapshot.compositionRoleSnapshot);
        putNullable(values, "composition_member_id_snapshot", snapshot.compositionMemberIdSnapshot);
        putNullable(values, "consumed_fraction", consumedFraction);
        values.put("device_id", DEVICE_ID);
        database.insertOrThrow("meal_record_item_components", null, values);

        insertSnapshotMicronutrients(
                database,
                "meal_record_item_component_nutrients",
                mealRecordId,
                mealRecordItemId,
                componentId,
                snapshot,
                now
        );
    }

    private ContentValues snapshotValues(MealItemSnapshot snapshot, String now) {
        ContentValues values = new ContentValues();
        putNullable(values, "food_id", snapshot.foodId);
        values.put("food_name_snapshot", snapshot.foodNameSnapshot);
        putNullable(values, "brand_snapshot", snapshot.brandSnapshot);
        values.put("food_kind_snapshot", snapshot.foodKindSnapshot);
        values.put("quantity", snapshot.quantity);
        values.put("unit", snapshot.unit);
        values.put("basis_amount_snapshot", snapshot.basisAmountSnapshot);
        values.put("basis_unit_snapshot", snapshot.basisUnitSnapshot);
        values.put("prep_state_snapshot", snapshot.prepStateSnapshot);
        for (Map.Entry<String, Double> column : snapshot.typedNutritionColumns().entrySet()) {
            String name = mealItemColumnName(column.getKey());
            if (column.getValue() == null) {
                if (isRequiredSnapshotNutrition(column.getKey())) {
                    values.put(name, 0d);
                } else {
                    values.putNull(name);
                }
            } else {
                values.put(name, column.getValue());
            }
        }
        putNullable(values, "source_type_snapshot", snapshot.sourceTypeSnapshot);
        putNullable(values, "source_reference_snapshot", snapshot.sourceReferenceSnapshot);
        putNullable(values, "source_version_snapshot", snapshot.sourceVersionSnapshot);
        values.put("food_data_version_snapshot", snapshot.foodDataVersionSnapshot);
        values.put("order_index", snapshot.orderIndex);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.putNull("deleted_at");
        return values;
    }

    private static boolean isRequiredSnapshotNutrition(String nutrientKey) {
        return NutritionProfile.CALORIES_KCAL.equals(nutrientKey)
                || NutritionProfile.PROTEIN_GRAMS.equals(nutrientKey)
                || NutritionProfile.CARBS_GRAMS.equals(nutrientKey)
                || NutritionProfile.FAT_GRAMS.equals(nutrientKey);
    }

    private void insertSnapshotMicronutrients(
            SQLiteDatabase database,
            String table,
            String mealRecordId,
            String mealRecordItemId,
            String componentId,
            MealItemSnapshot snapshot,
            String now
    ) {

        for (MealItemSnapshot.MicronutrientRow row : snapshot.micronutrientRows()) {
            ContentValues nutrientValues = new ContentValues();
            nutrientValues.put("id", newId());
            nutrientValues.put("user_id", userId);
            nutrientValues.put("meal_record_id", mealRecordId);
            nutrientValues.put("meal_record_item_id", mealRecordItemId);
            if (componentId != null) {
                nutrientValues.put("meal_record_item_component_id", componentId);
            }
            nutrientValues.put("nutrient_code", row.nutrientCode);
            nutrientValues.put("amount", row.amount);
            nutrientValues.put("unit", row.unit);
            nutrientValues.put("created_at", now);
            nutrientValues.put("updated_at", now);
            nutrientValues.putNull("deleted_at");
            nutrientValues.put("device_id", DEVICE_ID);
            database.insertOrThrow(table, null, nutrientValues);
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
                        "FROM meal_record_items WHERE meal_record_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL " +
                        "ORDER BY order_index ASC",
                new String[]{recordId, userId}
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
                        "WHERE meal_record_id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{mealRecordId, userId}
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
        // meal_type remains in synced metadata for compatibility, but visible labels are derived
        // from the current order so deleting a middle row never leaves a numbering gap.
        int mealIndex = 0;
        try (Cursor cursor = db().rawQuery(
                "SELECT r.id, r.date, r.menu, r.calories, r.protein_grams, " +
                        "r.carbs_grams, r.fat_grams, r.metadata, r.created_at, " +
                        "r.meal_kind, r.store_name, r.branch_name, r.menu_name, " +
                        "(SELECT i.food_name_snapshot FROM meal_record_items i " +
                        "WHERE i.meal_record_id = r.id AND i.user_id = r.user_id " +
                        "AND i.deleted_at IS NULL ORDER BY i.order_index ASC, i.id ASC LIMIT 1), " +
                        "(SELECT COUNT(*) FROM meal_record_items i " +
                        "WHERE i.meal_record_id = r.id AND i.user_id = r.user_id " +
                        "AND i.deleted_at IS NULL), r.device_id " +
                        "FROM meal_records r WHERE r.user_id = ? AND r.deleted_at IS NULL " +
                        "AND r.scope IN ('fitness', 'both') AND r.date = ? " +
                        "ORDER BY r.created_at ASC, r.id ASC",
                new String[]{userId, selectedDate}
        )) {
            while (cursor.moveToNext()) {
                Double proteinGrams = nullableDouble(cursor, 4);
                Double carbsGrams = nullableDouble(cursor, 5);
                Double fatGrams = nullableDouble(cursor, 6);
                String metadata = cursor.getString(7);
                String eatenAt = metadataValue(metadata, "eaten_at", "");
                String mealKind = MealRecordKind.normalize(firstNonBlank(
                        cursor.getString(9),
                        metadataValue(metadata, "meal_kind", MealRecordKind.FOOD)
                ));
                String storeName = firstNonBlank(
                        cursor.getString(10),
                        metadataValue(metadata, "store_name", "")
                );
                String menuName = firstNonBlank(
                        cursor.getString(12),
                        metadataValue(metadata, "menu_name", "")
                );
                String rawBranchName = firstNonBlank(
                        cursor.getString(11),
                        metadataValue(metadata, "branch_name", "")
                );
                String branchName = MealRecordKind.isDiningOut(mealKind)
                        ? MealEntryPolicy.resolveDiningOutBranchName(storeName, rawBranchName)
                        : rawBranchName;
                String nutritionStatus = metadataValue(
                        metadata,
                        "nutrition_status",
                        MealRecordKind.isDiningOut(mealKind) ? "unknown" : "recorded"
                );
                int itemCount = Math.max(0, cursor.getInt(14));
                String previewTitle = MealRecordKind.isDiningOut(mealKind)
                        ? MealEntryPolicy.previewDiningOutTitle(storeName, branchName, menuName)
                        : MealEntryPolicy.previewTitle(
                                cursor.getString(13),
                                itemCount,
                                cursor.getString(2)
                        );
                entries.add(new MealEntry(
                        cursor.getString(0),
                        cursor.getString(1),
                        MealEntryPolicy.labelForIndex(mealIndex++),
                        cursor.getString(2),
                        cursor.getInt(3),
                        proteinGrams,
                        carbsGrams,
                        fatGrams,
                        itemCount,
                        previewTitle,
                        eatenAt,
                        mealKind,
                        storeName,
                        branchName,
                        menuName,
                        nutritionStatus,
                        DEVICE_ID.equals(cursor.getString(15)),
                        cursor.getString(8)
                ));
            }
        }
        entries.sort((left, right) -> {
            boolean leftMissing = "시간 미기록".equals(left.mealTime);
            boolean rightMissing = "시간 미기록".equals(right.mealTime);
            if (leftMissing != rightMissing) {
                return leftMissing ? 1 : -1;
            }
            int timeOrder = left.mealTime.compareTo(right.mealTime);
            if (timeOrder != 0) {
                return timeOrder;
            }
            return String.valueOf(left.createdAt).compareTo(String.valueOf(right.createdAt));
        });
        return entries;
    }

    /** Recent active dining-out records available for local reuse. */
    public List<MealEntry> recentDiningOutEntries(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<String> dates = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT date FROM meal_records WHERE user_id = ? AND meal_kind = ? " +
                        "AND deleted_at IS NULL AND scope IN ('fitness', 'both') " +
                        "GROUP BY date ORDER BY date DESC LIMIT ?",
                new String[]{userId, MealRecordKind.DINING_OUT, String.valueOf(safeLimit)}
        )) {
            while (cursor.moveToNext()) {
                dates.add(cursor.getString(0));
            }
        }

        List<MealEntry> entries = new ArrayList<>();
        for (String date : dates) {
            for (MealEntry entry : mealEntriesForDate(date)) {
                if (entry.isDiningOut()) {
                    entries.add(entry);
                }
            }
        }
        entries.sort((left, right) -> {
            int dateOrder = right.date.compareTo(left.date);
            if (dateOrder != 0) {
                return dateOrder;
            }
            int createdOrder = String.valueOf(right.createdAt)
                    .compareTo(String.valueOf(left.createdAt));
            if (createdOrder != 0) {
                return createdOrder;
            }
            return right.id.compareTo(left.id);
        });
        if (entries.size() > safeLimit) {
            return new ArrayList<>(entries.subList(0, safeLimit));
        }
        return entries;
    }

    /** Exact PT identity saved on a local dining-out record, if present. */
    public DiningOutIdentity diningOutIdentityForRecord(String mealRecordId) {
        String recordId = mealRecordId == null ? "" : mealRecordId.trim();
        if (recordId.isEmpty()) {
            return null;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT restaurant_id, store_name, restaurant_location_id, branch_name, " +
                        "restaurant_menu_id, menu_name, catalog_product_id, metadata " +
                        "FROM meal_records WHERE id = ? AND user_id = ? " +
                        "AND meal_kind = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId, userId, MealRecordKind.DINING_OUT}
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            String metadata = cursor.getString(7);
            String restaurantId = firstNonBlank(
                    cursor.getString(0),
                    metadataValue(metadata, "restaurant_id", "")
            );
            String restaurantName = firstNonBlank(
                    cursor.getString(1),
                    metadataValue(metadata, "restaurant_name", "")
            );
            String locationId = firstNonBlank(
                    cursor.getString(2),
                    metadataValue(metadata, "restaurant_location_id", "")
            );
            String branchName = firstNonBlank(
                    cursor.getString(3),
                    metadataValue(metadata, "branch_name", "")
            );
            String menuId = firstNonBlank(
                    cursor.getString(4),
                    metadataValue(metadata, "restaurant_menu_id", "")
            );
            String menuName = firstNonBlank(
                    cursor.getString(5),
                    metadataValue(metadata, "menu_name", "")
            );
            String productId = firstNonBlank(
                    cursor.getString(6),
                    metadataValue(metadata, "catalog_product_id", "")
            );
            if (restaurantId.isEmpty() || restaurantName.isEmpty() || locationId.isEmpty()
                    || menuId.isEmpty() || menuName.isEmpty() || productId.isEmpty()) {
                return null;
            }
            String sourceNamespace = metadataValue(
                    metadata,
                    "identity_namespace",
                    DiningOutIdentity.NAMESPACE
            );
            if (sourceNamespace.isEmpty()) {
                sourceNamespace = DiningOutIdentity.NAMESPACE;
            }
            String sourceLocationCode = emptyToNull(
                    metadataValue(metadata, "source_location_code", "")
            );
            try {
                return DiningOutIdentity.fromPriceTrace(
                        restaurantId,
                        restaurantName,
                        locationId,
                        sourceNamespace,
                        sourceLocationCode,
                        emptyToNull(branchName),
                        menuId,
                        menuName,
                        productId
                );
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    /** Ingredient snapshots that belonged to one consumed menu at recording time. */
    public List<MealComponentEntry> mealComponentsForItem(String mealRecordItemId) {
        List<MealComponentEntry> components = new ArrayList<>();
        String itemId = mealRecordItemId == null ? "" : mealRecordItemId.trim();
        if (itemId.isEmpty()) {
            return components;
        }
        try (Cursor cursor = db().rawQuery(
                        "SELECT id, food_name_snapshot, quantity, unit, calories, " +
                        "protein_grams, carbs_grams, fat_grams, consumed_fraction, " +
                        "composition_group_key_snapshot, composition_group_type_snapshot, " +
                        "composition_role_snapshot, composition_member_id_snapshot " +
                        "FROM meal_record_item_components " +
                        "WHERE meal_record_item_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL ORDER BY order_index ASC, id ASC",
                new String[]{itemId, userId}
        )) {
            while (cursor.moveToNext()) {
                components.add(new MealComponentEntry(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getDouble(2),
                        cursor.getString(3),
                        cursor.getDouble(4),
                        cursor.getDouble(5),
                        cursor.getDouble(6),
                        cursor.getDouble(7),
                        cursor.isNull(9) ? null : cursor.getString(9),
                        cursor.isNull(10) ? null : cursor.getString(10),
                        cursor.isNull(11) ? null : cursor.getString(11),
                        cursor.isNull(12) ? null : cursor.getString(12),
                        cursor.isNull(8) ? null : cursor.getDouble(8)
                ));
            }
        }
        return components;
    }

    public DiningOutConsumptionEntry diningOutConsumptionForRecord(String mealRecordId) {
        String recordId = mealRecordId == null ? "" : mealRecordId.trim();
        if (recordId.isEmpty()) {
            return null;
        }
        return readDiningOutConsumption(
                "meal_record_id = ?",
                new String[]{recordId}
        );
    }

    private DiningOutConsumptionEntry diningOutConsumptionForItem(String mealRecordItemId) {
        String itemId = mealRecordItemId == null ? "" : mealRecordItemId.trim();
        if (itemId.isEmpty()) {
            return null;
        }
        return readDiningOutConsumption(
                "meal_record_item_id = ?",
                new String[]{itemId}
        );
    }

    private DiningOutConsumptionEntry readDiningOutConsumption(
            String selector,
            String[] selectorArgs
    ) {
        String[] args = new String[selectorArgs.length + 1];
        args[0] = userId;
        System.arraycopy(selectorArgs, 0, args, 1, selectorArgs.length);
        try (Cursor cursor = db().rawQuery(
                "SELECT meal_record_item_id, contract_version, consumer_scope, diner_count, "
                        + "consumed_fraction, share_method, confidence "
                        + "FROM meal_record_item_consumptions "
                        + "WHERE user_id = ? AND deleted_at IS NULL AND " + selector
                        + " LIMIT 1",
                args
        )) {
            if (cursor.moveToFirst()) {
                return new DiningOutConsumptionEntry(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getInt(3),
                        cursor.getDouble(4),
                        cursor.getString(5),
                        cursor.getString(6)
                );
            }
        }
        return null;
    }
    public MealNutritionSummary mealNutritionForDate(String date) {
        try (Cursor cursor = db().rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(calories), 0), " +
                        "COALESCE(SUM(protein_grams), 0), COALESCE(SUM(carbs_grams), 0), " +
                        "COALESCE(SUM(fat_grams), 0) FROM meal_records " +
                        "WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND scope IN ('fitness', 'both') AND date = ?",
                new String[]{userId, emptyToToday(date)}
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

    /**
     * 날짜별 상세 영양소 합계. 구성 항목이 없는 레거시 끼니는 4대 영양소만 알고 나머지는
     * '모름'인 하나의 항목으로 더해, 누락값을 0으로 오인하지 않는다.
     */
    public NutritionTotals mealNutritionTotalsForDate(String date) {
        NutritionTotals.Builder totals = NutritionTotals.builder();
        for (MealEntry meal : mealEntriesForDate(date)) {
            List<MealItemEntry> items = mealItemsForRecord(meal.id);
            if (items.isEmpty()) {
                totals.add(NutritionProfile.ofMacros(
                        meal.calories,
                        meal.proteinGrams,
                        meal.carbsGrams,
                        meal.fatGrams
                ));
                continue;
            }
            for (MealItemEntry item : items) {
                DiningOutConsumptionEntry consumption = meal.isDiningOut()
                        ? diningOutConsumptionForItem(item.id)
                        : null;
                totals.add(consumption == null
                        ? item.profile
                        : item.profile.scaled(consumption.consumedFraction));
                if (meal.isDiningOut()) {
                    for (MealComponentEntry component : mealComponentsForItem(item.id)) {
                        if (consumption != null && !component.hasExplicitConsumedFraction()) {
                            // Components from pre-v36 shared records have no independent
                            // allocation. Keep their historical root-only total unchanged.
                            continue;
                        }
                        NutritionProfile componentProfile = NutritionProfile.ofMacros(
                                component.calories,
                                component.proteinGrams,
                                component.carbsGrams,
                                component.fatGrams
                        );
                        totals.add(consumption == null
                                ? componentProfile
                                : componentProfile.scaled(component.consumedFraction()));
                    }
                }
            }
        }
        return totals.build();
    }

    /** 현재 계정의 사용자 지정 영양 목표. 앱이 목표값을 자동 생성하지 않는다. */
    public AthleteNutritionGoal nutritionGoal() {
        try (Cursor cursor = db().rawQuery(
                "SELECT phase, calories_kcal, protein_grams, carbs_grams, fat_grams, " +
                        "fiber_grams, sodium_mg, water_ml FROM nutrition_goals " +
                        "WHERE user_id = ? LIMIT 1",
                new String[]{userId}
        )) {
            if (cursor.moveToFirst()) {
                return new AthleteNutritionGoal(
                        cursor.getString(0),
                        cursor.getDouble(1),
                        cursor.getDouble(2),
                        cursor.getDouble(3),
                        cursor.getDouble(4),
                        cursor.getDouble(5),
                        cursor.getDouble(6),
                        cursor.getInt(7)
                );
            }
        }
        return null;
    }

    public void saveNutritionGoal(AthleteNutritionGoal goal) {
        if (goal == null) {
            throw new IllegalArgumentException("영양 목표가 필요합니다.");
        }
        String timestamp = now();
        String createdAt = timestamp;
        try (Cursor cursor = db().rawQuery(
                "SELECT created_at FROM nutrition_goals WHERE user_id = ? LIMIT 1",
                new String[]{userId}
        )) {
            if (cursor.moveToFirst()) {
                createdAt = cursor.getString(0);
            }
        }

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("phase", goal.phase);
        values.put("calories_kcal", goal.caloriesKcal);
        values.put("protein_grams", goal.proteinGrams);
        values.put("carbs_grams", goal.carbsGrams);
        values.put("fat_grams", goal.fatGrams);
        values.put("fiber_grams", goal.fiberGrams);
        values.put("sodium_mg", goal.sodiumMg);
        values.put("water_ml", goal.waterMl);
        values.put("created_at", createdAt);
        values.put("updated_at", timestamp);
        db().insertWithOnConflict(
                "nutrition_goals",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public AthleteDailyCheckIn athleteCheckInForDate(String date) {
        String selectedDate = emptyToToday(date);
        try (Cursor cursor = db().rawQuery(
                "SELECT id, date, water_ml, sleep_hours, energy_score, hunger_score, " +
                        "digestion_score, training_readiness_score, note " +
                        "FROM nutrition_daily_checkins WHERE user_id = ? AND date = ? LIMIT 1",
                new String[]{userId, selectedDate}
        )) {
            if (cursor.moveToFirst()) {
                return new AthleteDailyCheckIn(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getInt(2),
                        cursor.isNull(3) ? null : cursor.getDouble(3),
                        cursor.isNull(4) ? null : cursor.getInt(4),
                        cursor.isNull(5) ? null : cursor.getInt(5),
                        cursor.isNull(6) ? null : cursor.getInt(6),
                        cursor.isNull(7) ? null : cursor.getInt(7),
                        cursor.getString(8)
                );
            }
        }
        return AthleteDailyCheckIn.empty(selectedDate);
    }

    public void saveAthleteCheckIn(AthleteDailyCheckIn checkIn) {
        if (checkIn == null) {
            throw new IllegalArgumentException("선수 체크인 기록이 필요합니다.");
        }
        if (checkIn.isEmpty()) {
            db().delete(
                    "nutrition_daily_checkins",
                    "user_id = ? AND date = ?",
                    new String[]{userId, checkIn.date}
            );
            return;
        }
        String timestamp = now();
        String recordId = checkIn.id;
        String createdAt = timestamp;
        try (Cursor cursor = db().rawQuery(
                "SELECT id, created_at FROM nutrition_daily_checkins " +
                        "WHERE user_id = ? AND date = ? LIMIT 1",
                new String[]{userId, checkIn.date}
        )) {
            if (cursor.moveToFirst()) {
                recordId = cursor.getString(0);
                createdAt = cursor.getString(1);
            }
        }
        if (emptyToNull(recordId) == null) {
            recordId = newId();
        }

        ContentValues values = new ContentValues();
        values.put("id", recordId);
        values.put("user_id", userId);
        values.put("date", checkIn.date);
        values.put("water_ml", checkIn.waterMl);
        putNullable(values, "sleep_hours", checkIn.sleepHours);
        putNullable(values, "energy_score", checkIn.energyScore);
        putNullable(values, "hunger_score", checkIn.hungerScore);
        putNullable(values, "digestion_score", checkIn.digestionScore);
        putNullable(values, "training_readiness_score", checkIn.trainingReadinessScore);
        putNullable(values, "note", checkIn.note);
        values.put("created_at", createdAt);
        values.put("updated_at", timestamp);
        db().insertWithOnConflict(
                "nutrition_daily_checkins",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    public AthleteDailyCheckIn addWaterForDate(String date, int amountMl) {
        AthleteDailyCheckIn updated = athleteCheckInForDate(date).withWaterAdded(amountMl);
        saveAthleteCheckIn(updated);
        return athleteCheckInForDate(date);
    }

    /** 선택 날짜에 체중 기록이 없으면 그 이전의 가장 최근 기록을 사용한다. */
    public BodyMetricEntry latestBodyMetricOnOrBefore(String date) {
        String sql = "SELECT id, date, weight_kg, metadata FROM weight_records " +
                "WHERE user_id = ? AND deleted_at IS NULL AND scope IN ('fitness', 'both') " +
                "AND date <= ? ORDER BY date DESC, updated_at DESC LIMIT 1";
        try (Cursor cursor = db().rawQuery(sql, new String[]{userId, emptyToToday(date)})) {
            if (cursor.moveToFirst()) {
                return new BodyMetricEntry(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getDouble(2),
                        metadataValue(cursor.getString(3), "memo", "")
                );
            }
        }
        return null;
    }

    /**
     * Updates the top-level menus of a locally recorded food meal in place.
     *
     * <p>The meal keeps its original snapshot identity. A quantity correction scales the
     * snapshot nutrition, micronutrient rows, and ingredient rows together, so editing a menu
     * cannot silently make the meal total disagree with its menu details.</p>
     */
    public boolean updateMealMenus(String mealRecordId, List<MealMenuEdit> edits) {
        String normalizedRecordId = mealRecordId == null ? "" : mealRecordId.trim();
        if (normalizedRecordId.isEmpty() || edits == null || edits.isEmpty()) {
            return false;
        }

        SQLiteDatabase database = db();
        String metadata;
        try (Cursor cursor = database.rawQuery(
                "SELECT metadata, meal_kind FROM meal_records " +
                        "WHERE id = ? AND user_id = ? AND device_id = ? " +
                        "AND deleted_at IS NULL LIMIT 1",
                new String[]{normalizedRecordId, userId, DEVICE_ID}
        )) {
            if (!cursor.moveToFirst()
                    || MealRecordKind.isDiningOut(cursor.getString(1))) {
                return false;
            }
            metadata = cursor.getString(0);
        }

        Map<String, Double> originalQuantities = new LinkedHashMap<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT id, quantity FROM meal_record_items " +
                        "WHERE meal_record_id = ? AND user_id = ? AND deleted_at IS NULL " +
                        "ORDER BY order_index ASC, id ASC",
                new String[]{normalizedRecordId, userId}
        )) {
            while (cursor.moveToNext()) {
                originalQuantities.put(cursor.getString(0), cursor.getDouble(1));
            }
        }
        if (originalQuantities.size() != edits.size()) {
            return false;
        }

        List<String> seenIds = new ArrayList<>();
        for (MealMenuEdit edit : edits) {
            if (edit == null || !originalQuantities.containsKey(edit.id)
                    || seenIds.contains(edit.id)) {
                return false;
            }
            seenIds.add(edit.id);
        }

        String timestamp = now();
        database.beginTransaction();
        try {
            for (MealMenuEdit edit : edits) {
                double originalQuantity = originalQuantities.get(edit.id);
                if (!Double.isFinite(originalQuantity) || originalQuantity <= 0d) {
                    return false;
                }
                double ratio = edit.quantity / originalQuantity;
                database.execSQL(
                        "UPDATE meal_record_items SET food_name_snapshot = ?, quantity = ?, " +
                                "calories = calories * ?, protein_grams = protein_grams * ?, " +
                                "carbs_grams = carbs_grams * ?, fat_grams = fat_grams * ?, " +
                                "sodium_mg = sodium_mg * ?, saturated_fat_grams = saturated_fat_grams * ?, " +
                                "sugars_grams = sugars_grams * ?, fiber_grams = fiber_grams * ?, " +
                                "added_sugars_grams = added_sugars_grams * ?, " +
                                "trans_fat_grams = trans_fat_grams * ?, " +
                                "cholesterol_mg = cholesterol_mg * ?, updated_at = ? " +
                                "WHERE id = ? AND meal_record_id = ? AND user_id = ? " +
                                "AND deleted_at IS NULL",
                        new Object[]{
                                edit.name,
                                edit.quantity,
                                ratio,
                                ratio,
                                ratio,
                                ratio,
                                ratio,
                                ratio,
                                ratio,
                                ratio,
                                ratio,
                                ratio,
                                ratio,
                                timestamp,
                                edit.id,
                                normalizedRecordId,
                                userId
                        }
                );
                scaleMealComponentSnapshots(
                        database,
                        edit.id,
                        normalizedRecordId,
                        ratio,
                        timestamp
                );
                scaleMealNutrientRows(
                        database,
                        "meal_record_item_nutrients",
                        "meal_record_item_id",
                        edit.id,
                        normalizedRecordId,
                        ratio,
                        timestamp
                );
            }

            double calories = 0d;
            double proteinGrams = 0d;
            double carbsGrams = 0d;
            double fatGrams = 0d;
            try (Cursor cursor = database.rawQuery(
                    "SELECT COALESCE(SUM(calories), 0), COALESCE(SUM(protein_grams), 0), " +
                            "COALESCE(SUM(carbs_grams), 0), COALESCE(SUM(fat_grams), 0) " +
                            "FROM meal_record_items WHERE meal_record_id = ? AND user_id = ? " +
                            "AND deleted_at IS NULL",
                    new String[]{normalizedRecordId, userId}
            )) {
                if (cursor.moveToFirst()) {
                    calories = cursor.getDouble(0);
                    proteinGrams = cursor.getDouble(1);
                    carbsGrams = cursor.getDouble(2);
                    fatGrams = cursor.getDouble(3);
                }
            }

            ContentValues recordValues = new ContentValues();
            recordValues.put(
                    "menu",
                    MealEntryPolicy.previewTitle(edits.get(0).name, edits.size(), "Meal")
            );
            recordValues.put("calories", (int) Math.round(calories));
            recordValues.put("protein_grams", proteinGrams);
            recordValues.put("carbs_grams", carbsGrams);
            recordValues.put("fat_grams", fatGrams);
            recordValues.put("metadata", metadataWithMealItemCount(metadata, edits.size()));
            recordValues.put("updated_at", timestamp);
            int updated = database.update(
                    "meal_records",
                    recordValues,
                    "id = ? AND user_id = ? AND device_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedRecordId, userId, DEVICE_ID}
            );
            database.setTransactionSuccessful();
            return updated > 0;
        } finally {
            database.endTransaction();
        }
    }

    private void scaleMealComponentSnapshots(
            SQLiteDatabase database,
            String mealRecordItemId,
            String mealRecordId,
            double ratio,
            String timestamp
    ) {
        database.execSQL(
                "UPDATE meal_record_item_components SET quantity = quantity * ?, " +
                        "calories = calories * ?, protein_grams = protein_grams * ?, " +
                        "carbs_grams = carbs_grams * ?, fat_grams = fat_grams * ?, " +
                        "sodium_mg = sodium_mg * ?, saturated_fat_grams = saturated_fat_grams * ?, " +
                        "sugars_grams = sugars_grams * ?, fiber_grams = fiber_grams * ?, " +
                        "added_sugars_grams = added_sugars_grams * ?, " +
                        "trans_fat_grams = trans_fat_grams * ?, " +
                        "cholesterol_mg = cholesterol_mg * ?, updated_at = ? " +
                        "WHERE meal_record_item_id = ? AND meal_record_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL",
                new Object[]{
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        ratio,
                        timestamp,
                        mealRecordItemId,
                        mealRecordId,
                        userId
                }
        );
        scaleMealNutrientRows(
                database,
                "meal_record_item_component_nutrients",
                "meal_record_item_id",
                mealRecordItemId,
                mealRecordId,
                ratio,
                timestamp
        );
    }

    private void scaleMealNutrientRows(
            SQLiteDatabase database,
            String table,
            String itemColumn,
            String itemId,
            String mealRecordId,
            double ratio,
            String timestamp
    ) {
        database.execSQL(
                "UPDATE " + table + " SET amount = amount * ?, updated_at = ? " +
                        "WHERE " + itemColumn + " = ? AND meal_record_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL",
                new Object[]{ratio, timestamp, itemId, mealRecordId, userId}
        );
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
                    "id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId, userId}
            );
            database.update(
                    "meal_record_items",
                    values,
                    "meal_record_id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId, userId}
            );
            database.update(
                    "meal_record_item_nutrients",
                    values,
                    "meal_record_id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId, userId}
            );
            database.update(
                    "meal_record_item_components",
                    values,
                    "meal_record_id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId, userId}
            );
            database.update(
                    "meal_record_item_consumptions",
                    values,
                    "meal_record_id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId, userId}
            );
            database.update(
                    "meal_record_item_component_nutrients",
                    values,
                    "meal_record_id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{normalizedId, userId}
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
                "SELECT id FROM meal_menu_presets " +
                        "WHERE user_id = ? AND name = ? COLLATE NOCASE LIMIT 1",
                new String[]{userId, normalizedName})) {
            if (cursor.moveToFirst()) {
                existingId = cursor.getString(0);
            }
        }

        String timestamp = now();
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("name", normalizedName);
        putNullable(values, "calories", calories);
        putNullable(values, "protein_grams", proteinGrams);
        putNullable(values, "carbs_grams", carbsGrams);
        putNullable(values, "fat_grams", fatGrams);
        values.put("updated_at", timestamp);

        if (existingId != null) {
            database.update(
                    "meal_menu_presets",
                    values,
                    "id = ? AND user_id = ?",
                    new String[]{existingId, userId}
            );
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
                        "FROM meal_menu_presets WHERE user_id = ? " +
                        "ORDER BY updated_at DESC, name COLLATE NOCASE ASC",
                new String[]{userId})) {
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
                && db().delete(
                "meal_menu_presets",
                "id = ? AND user_id = ?",
                new String[]{normalizedId, userId}
        ) > 0;
    }

    public String createSessionFromRoutine(String date, String title, List<RoutineExerciseInstance> routineExercises) {
        return createSessionFromRoutine(date, title, null, routineExercises);
    }

    public String createSessionFromRoutine(String date, String title, String routineId,
                                           List<RoutineExerciseInstance> routineExercises) {
        String recordId = createSession(date, title, "strength", "", now(), "");
        populateSessionFromRoutine(recordId, routineId, routineExercises);
        return recordId;
    }

    public String createManualPastSessionFromRoutine(
            String date,
            String title,
            String routineId,
            List<RoutineExerciseInstance> routineExercises,
            String startedAt,
            String endedAt
    ) {
        String recordDate = requireRecordDate(date);
        Integer durationSeconds = computeDurationSeconds(recordDate, startedAt, endedAt);
        if (durationSeconds == null || durationSeconds <= 0) {
            throw new IllegalArgumentException("운동 시작 시각과 운동 시간을 확인하세요.");
        }

        String recordId = createSession(recordDate, title, "strength", "", startedAt, "");
        ContentValues values = new ContentValues();
        values.put("duration_seconds", durationSeconds);
        values.put("is_backfilled", 1);
        values.put("backfilled_at", now());
        values.put("backfill_reason", "manual_entry");
        values.put("metadata", mergedWorkoutMetadata(
                sessionInfoMetadata(recordId),
                "in_progress",
                startedAt,
                endedAt,
                durationSeconds,
                0d
        ));
        db().update(
                "workout_records",
                values,
                "id = ? AND user_id = ?",
                new String[]{recordId, userId}
        );
        populateSessionFromRoutine(recordId, routineId, routineExercises);
        return recordId;
    }

    private void populateSessionFromRoutine(
            String recordId,
            String routineId,
            List<RoutineExerciseInstance> routineExercises
    ) {
        if (routineId != null && !routineId.trim().isEmpty()) {
            ContentValues metadataValues = new ContentValues();
            metadataValues.put("metadata", addMetadataValue(sessionInfoMetadata(recordId), "routine_id", routineId));
            db().update(
                    "workout_records",
                    metadataValues,
                    "id = ? AND user_id = ?",
                    new String[]{recordId, userId}
            );
        }
        if (routineExercises == null || routineExercises.isEmpty()) {
            return;
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
    }

    public String latestCompletedWorkoutDateForRoutine(String routineId, String routineName) {
        if (routineId != null && !routineId.trim().isEmpty()) {
            try (Cursor cursor = db().rawQuery(
                    "SELECT date FROM workout_records "
                            + "WHERE user_id = ? AND deleted_at IS NULL "
                            + "AND scope IN ('fitness', 'both') "
                            + "AND metadata LIKE ? AND metadata LIKE '%\"status\":\"completed\"%' "
                            + "ORDER BY date DESC, updated_at DESC LIMIT 1",
                    new String[]{userId, "%\"routine_id\":\"" + routineId + "\"%"})) {
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
                        + "WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND scope IN ('fitness', 'both') "
                        + "AND metadata LIKE '%\"status\":\"completed\"%' AND exercise_name = ? "
                        + "ORDER BY date DESC, updated_at DESC LIMIT 1",
                new String[]{userId, routineName})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private String sessionInfoMetadata(String recordId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT metadata FROM workout_records WHERE id = ? AND user_id = ? LIMIT 1",
                new String[]{recordId, userId})) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    private String cardioExerciseId(String recordId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM workout_exercises WHERE record_id = ? "
                        + "AND user_id = ? AND exercise_id LIKE 'cardio_%' "
                        + "AND deleted_at IS NULL "
                        + "ORDER BY order_index LIMIT 1",
                new String[]{recordId, userId})) {
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
                        "WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND scope IN ('fitness', 'both') " +
                        "AND " + COMPLETED_OR_OS_WORKOUT + " AND date = ? ORDER BY updated_at DESC",
                new String[]{userId, emptyToToday(date)})) {
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
                        + "AND user_id = ? AND scope IN ('fitness', 'both') AND " + range
                        + " AND " + COMPLETED_OR_OS_WORKOUT + " GROUP BY date",
                new String[]{userId, firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                summaryFor(summaries, cursor.getString(0)).hasWorkout = true;
            }
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT date FROM meal_records WHERE deleted_at IS NULL "
                        + "AND user_id = ? AND scope IN ('fitness', 'both') " +
                        "AND " + range + " GROUP BY date",
                new String[]{userId, firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                summaryFor(summaries, cursor.getString(0)).hasMeal = true;
            }
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT date FROM weight_records WHERE deleted_at IS NULL "
                        + "AND user_id = ? AND scope IN ('fitness', 'both') " +
                        "AND " + range + " GROUP BY date",
                new String[]{userId, firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                summaryFor(summaries, cursor.getString(0)).hasWeight = true;
            }
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT wr.date, we.ui_part FROM workout_records wr "
                        + "INNER JOIN workout_exercises we ON we.record_id = wr.id "
                        + "AND we.user_id = wr.user_id AND we.deleted_at IS NULL "
                        + "WHERE wr.user_id = ? AND wr.deleted_at IS NULL "
                        + "AND wr.scope IN ('fitness', 'both') AND wr." + range
                        + " AND " + COMPLETED_OR_OS_WORKOUT + " "
                        + "AND we.ui_part IS NOT NULL AND we.ui_part != '' ORDER BY wr.date, we.order_index",
                new String[]{userId, firstDate, lastDate})) {
            while (cursor.moveToNext()) {
                CalendarDaySummary summary = summaryFor(summaries, cursor.getString(0));
                appendCalendarPart(summary, displayCategory(cursor.getString(1)));
            }
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT wr.date, wr.workout_type, wr.category FROM workout_records wr "
                        + "WHERE wr.user_id = ? AND wr.deleted_at IS NULL "
                        + "AND wr.scope IN ('fitness', 'both') "
                        + "AND wr." + range + " AND " + COMPLETED_OR_OS_WORKOUT + " "
                        + "AND NOT EXISTS (SELECT 1 FROM workout_exercises we "
                        + "WHERE we.record_id = wr.id AND we.user_id = wr.user_id "
                        + "AND we.deleted_at IS NULL) "
                        + "ORDER BY wr.date, wr.updated_at",
                new String[]{userId, firstDate, lastDate})) {
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
                        "WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND scope IN ('fitness', 'both') " +
                        "ORDER BY date DESC, updated_at DESC LIMIT 20",
                new String[]{userId})) {
            while (cursor.moveToNext()) {
                rows.add(formatDate(cursor.getString(0)) + "  " + cursor.getString(1) + "  "
                        + displaySessionType(cursor.getString(2)));
            }
        }
        return rows;
    }

    public String latestSessionId() {
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM workout_records WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND scope IN ('fitness', 'both') " +
                        "ORDER BY date DESC, updated_at DESC LIMIT 1",
                new String[]{userId})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public String latestInProgressSessionId() {
        try (Cursor cursor = db().rawQuery(
                "SELECT id, metadata FROM workout_records WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND scope IN ('fitness', 'both') ORDER BY updated_at DESC LIMIT 20",
                new String[]{userId})) {
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
                "SELECT date, duration_seconds, metadata, is_backfilled FROM workout_records " +
                        "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId, userId})) {
            if (!cursor.moveToFirst()) {
                return;
            }

            String date = cursor.getString(0);
            String metadata = cursor.getString(2);
            boolean isBackfilled = cursor.getInt(3) == 1;
            String updatedAt = now();
            String endedAt = isBackfilled
                    ? metadataValue(metadata, "ended_at", "")
                    : updatedAt;
            if (endedAt.isEmpty()) {
                endedAt = updatedAt;
            }
            String startedAt = metadataValue(metadata, "started_at", "");
            Integer durationSeconds = isBackfilled && !cursor.isNull(1)
                    ? cursor.getInt(1)
                    : computeDurationSeconds(date, startedAt, endedAt);
            if (durationSeconds == null || durationSeconds <= 0) {
                durationSeconds = cursor.isNull(1) ? 0 : cursor.getInt(1);
            }

            SessionMetrics metrics = sessionMetrics(recordId);
            ContentValues values = new ContentValues();
            values.put("duration_seconds", durationSeconds);
            values.put("total_volume_kg", metrics.totalVolumeKg);
            values.put("updated_at", updatedAt);
            values.put("metadata", mergedWorkoutMetadata(
                    metadata,
                    "completed",
                    startedAt,
                    endedAt,
                    durationSeconds,
                    metrics.totalVolumeKg
            ));
            database.update(
                    "workout_records",
                    values,
                    "id = ? AND user_id = ?",
                    new String[]{recordId, userId}
            );
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
                        + "AND device_id = ? "
                        + "AND metadata LIKE '%\"status\":\"completed\"%'",
                new String[]{userId, DEVICE_ID})) {
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
        if (!ownsWorkoutRecord(recordId)) {
            return;
        }

        SQLiteDatabase database = db();
        String now = now();
        ContentValues values = new ContentValues();
        values.put("deleted_at", now);
        values.put("updated_at", now);

        database.beginTransaction();
        try {
            List<String> workoutExerciseIds = new ArrayList<>();
            try (Cursor cursor = database.rawQuery(
                    "SELECT id FROM workout_exercises " +
                            "WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{recordId, userId})) {
                while (cursor.moveToNext()) {
                    workoutExerciseIds.add(cursor.getString(0));
                }
            }

            for (String workoutExerciseId : workoutExerciseIds) {
                database.update(
                        "workout_sets",
                        values,
                        "workout_exercise_id = ? AND user_id = ? AND deleted_at IS NULL",
                        new String[]{workoutExerciseId, userId}
                );
            }
            database.update(
                    "workout_exercises",
                    values,
                    "record_id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{recordId, userId}
            );
            database.update(
                    "workout_records",
                    values,
                    "id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{recordId, userId}
            );
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /** Updates only the user-owned meal time while preserving the rest of its synced metadata. */
    public boolean updateMealTime(String id, String mealTime) {
        String normalizedId = id == null ? "" : id.trim();
        if (normalizedId.isEmpty()) {
            return false;
        }

        String recordDate;
        String metadata;
        try (Cursor cursor = db().rawQuery(
                "SELECT date, metadata FROM meal_records " +
                        "WHERE id = ? AND user_id = ? AND device_id = ? " +
                        "AND deleted_at IS NULL LIMIT 1",
                new String[]{normalizedId, userId, DEVICE_ID}
        )) {
            if (!cursor.moveToFirst()) {
                return false;
            }
            recordDate = cursor.getString(0);
            metadata = cursor.getString(1);
        }

        LocalDate date = MealEntryPolicy.requireRecordDate(recordDate, LocalDate.now());
        String eatenAt = MealEntryPolicy.eatenAt(date, mealTime, ZoneId.systemDefault());
        ContentValues values = new ContentValues();
        values.put("metadata", metadataWithEatenAt(metadata, eatenAt));
        values.put("updated_at", now());
        return db().update(
                "meal_records",
                values,
                "id = ? AND user_id = ? AND device_id = ? AND deleted_at IS NULL",
                new String[]{normalizedId, userId, DEVICE_ID}
        ) > 0;
    }

    public List<String> sessionDetails(String recordId) {
        List<String> rows = new ArrayList<>();
        try (Cursor exercises = db().rawQuery(
                "SELECT id, order_index, exercise_name_snapshot, ui_part FROM workout_exercises " +
                        "WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL " +
                        "ORDER BY order_index",
                new String[]{recordId, userId})) {
            while (exercises.moveToNext()) {
                String exerciseId = exercises.getString(0);
                rows.add(exercises.getInt(1) + ". " + exercises.getString(2) + "  " + displayCategory(exercises.getString(3)));
                try (Cursor sets = db().rawQuery(
                        "SELECT set_index, weight_kg, actual_reps, is_completed FROM workout_sets " +
                                "WHERE workout_exercise_id = ? AND user_id = ? " +
                                "AND deleted_at IS NULL ORDER BY set_index",
                        new String[]{exerciseId, userId})) {
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
                        "WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL " +
                        "ORDER BY order_index",
                new String[]{recordId, userId})) {
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
                        "WHERE user_id = ? AND deleted_at IS NULL " +
                        "AND scope IN ('fitness', 'both') AND date = ?",
                new String[]{userId, emptyToToday(date)})) {
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
                        "WHERE we.record_id = ? AND we.user_id = ? AND ws.user_id = ? " +
                        "AND we.deleted_at IS NULL AND ws.deleted_at IS NULL " +
                        "AND ws.is_completed = 1",
                new String[]{recordId, userId, userId})) {
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
                + "WHERE user_id = ? AND deleted_at IS NULL "
                + "AND scope IN ('fitness', 'both') AND id != ? "
                + "ORDER BY date DESC, updated_at DESC LIMIT ?";
        try (Cursor cursor = db().rawQuery(
                sql,
                new String[]{userId, currentRecordId, String.valueOf(limit)}
        )) {
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
                + "LEFT JOIN workout_sets ws ON ws.workout_exercise_id = we.id "
                + "AND ws.user_id = we.user_id AND ws.deleted_at IS NULL "
                + "WHERE wr.user_id = ? AND we.user_id = ? AND " + EXERCISE_MATCH
                + " AND we.record_id != ? AND we.deleted_at IS NULL "
                + "AND we.record_type = 'weight_reps' "
                + "GROUP BY we.record_id, wr.date, wr.exercise_name, wr.updated_at "
                + "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT ?";
        try (Cursor cursor = db().rawQuery(sql,
                new String[]{userId, userId, exerciseId, exerciseName, currentRecordId,
                        String.valueOf(limit)})) {
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
                + "WHERE wr.user_id = ? AND we.user_id = ? AND " + EXERCISE_MATCH
                + " AND we.record_id != ? AND we.deleted_at IS NULL "
                + "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT 1";
        try (Cursor cursor = db().rawQuery(
                sql,
                new String[]{userId, userId, exerciseId, exerciseName, currentRecordId}
        )) {
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
                + "WHERE wr.user_id = ? AND we.user_id = ? AND ws.user_id = ? AND "
                + EXERCISE_MATCH + " AND we.record_id != ? "
                + "AND we.record_type = 'weight_reps' "
                + "AND ws.deleted_at IS NULL AND ws.is_completed = 1";
        java.util.Map<String, Double> volumeByRecord = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> dateByRecord = new java.util.LinkedHashMap<>();
        try (Cursor cursor = db().rawQuery(
                sql,
                new String[]{userId, userId, userId, exerciseId, exerciseName, currentRecordId}
        )) {
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
        db().update(
                "workout_records",
                values,
                "id = ? AND user_id = ?",
                new String[]{recordId, userId}
        );
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
                        + "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId, userId})) {
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
                            + "WHERE record_id = ? AND user_id = ? "
                            + "AND deleted_at IS NULL ORDER BY order_index",
                    new String[]{recordId, userId})) {
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
        database.update(
                "workout_records",
                values,
                "id = ? AND user_id = ?",
                new String[]{recordId, userId}
        );
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
                "SELECT COALESCE(MAX(order_index), 0) + 1 FROM workout_exercises " +
                        "WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{recordId, userId})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 1;
        }
    }

    public List<String> bodyMetrics() {
        return bodyMetricsForDate(null);
    }

    public List<String> bodyMetricsForDate(String date) {
        List<String> rows = new ArrayList<>();
        String sql = "SELECT date, weight_kg FROM weight_records " +
                "WHERE user_id = ? AND deleted_at IS NULL AND scope IN ('fitness', 'both')";
        String[] args = new String[]{userId};
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{userId, emptyToToday(date)};
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
                        + "AND user_id = ? AND scope IN ('fitness', 'both') AND date = ?",
                new String[]{userId, emptyToToday(date)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public String nextMealLabelForDate(String date) {
        return MealEntryPolicy.labelForIndex(mealCountForDate(date));
    }

    public List<String> mealsForDate(String date) {
        List<String> rows = new ArrayList<>();
        String sql = "SELECT date, menu, calories, protein_grams, carbs_grams, fat_grams " +
                "FROM meal_records WHERE user_id = ? AND deleted_at IS NULL " +
                "AND scope IN ('fitness', 'both')";
        String[] args = new String[]{userId};
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{userId, emptyToToday(date)};
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

    private boolean ownsWorkoutRecord(String recordId) {
        String normalizedId = emptyToNull(recordId);
        if (normalizedId == null) {
            return false;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT 1 FROM workout_records " +
                        "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{normalizedId, userId}
        )) {
            return cursor.moveToFirst();
        }
    }

    private void requireOwnedWorkoutRecord(String recordId) {
        if (!ownsWorkoutRecord(recordId)) {
            throw new IllegalArgumentException("현재 계정의 운동 기록이 아닙니다.");
        }
    }

    private void requireOwnedWorkoutExercise(String recordId, String workoutExerciseId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT 1 FROM workout_exercises " +
                        "WHERE id = ? AND record_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL LIMIT 1",
                new String[]{workoutExerciseId, recordId, userId}
        )) {
            if (cursor.moveToFirst()) {
                return;
            }
        }
        throw new IllegalArgumentException("현재 계정의 운동 종목이 아닙니다.");
    }

    private void requireOwnedWorkoutSet(String recordId, String setId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT 1 FROM workout_sets ws " +
                        "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id " +
                        "WHERE ws.id = ? AND we.record_id = ? AND ws.user_id = ? " +
                        "AND we.user_id = ? AND ws.deleted_at IS NULL " +
                        "AND we.deleted_at IS NULL LIMIT 1",
                new String[]{setId, recordId, userId, userId}
        )) {
            if (cursor.moveToFirst()) {
                return;
            }
        }
        throw new IllegalArgumentException("현재 계정의 운동 세트가 아닙니다.");
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

    private List<String> claimableOwnerTables() {
        List<String> tables = new ArrayList<>();
        tables.add("workout_records");
        tables.add("workout_exercises");
        tables.add("workout_sets");
        tables.add("meal_records");
        tables.add("meal_record_items");
        tables.add("meal_record_item_nutrients");
        tables.add("meal_record_item_components");
        tables.add("meal_record_item_component_nutrients");
        tables.add("meal_record_item_consumptions");
        tables.add("dining_out_menu_add_on_links");
        tables.add("weight_records");
        tables.add("cardio_sessions");
        tables.add("cardio_route_points");
        tables.add("routines");
        tables.add("routine_exercises");
        return tables;
    }

    private void claimSingletonNutritionGoal(
            SQLiteDatabase database,
            String nextUserId
    ) {
        database.execSQL(
                "INSERT OR REPLACE INTO nutrition_goals (" +
                        "user_id, phase, calories_kcal, protein_grams, carbs_grams, " +
                        "fat_grams, fiber_grams, sodium_mg, water_ml, created_at, updated_at) " +
                        "SELECT ?, source.phase, source.calories_kcal, source.protein_grams, " +
                        "source.carbs_grams, source.fat_grams, source.fiber_grams, " +
                        "source.sodium_mg, source.water_ml, source.created_at, source.updated_at " +
                        "FROM nutrition_goals source WHERE source.user_id = ? " +
                        "AND (NOT EXISTS (SELECT 1 FROM nutrition_goals target " +
                        "WHERE target.user_id = ?) OR julianday(source.updated_at) > julianday((" +
                        "SELECT target.updated_at FROM nutrition_goals target " +
                        "WHERE target.user_id = ? LIMIT 1)))",
                new Object[]{
                        nextUserId,
                        SupabaseConfig.DEFAULT_USER_ID,
                        nextUserId,
                        nextUserId
                }
        );
        database.delete(
                "nutrition_goals",
                "user_id = ?",
                new String[]{SupabaseConfig.DEFAULT_USER_ID}
        );
    }

    private void claimConflictFreeDailyRows(
            SQLiteDatabase database,
            String nextUserId
    ) {
        database.execSQL(
                "INSERT OR REPLACE INTO nutrition_daily_checkins (" +
                        "id, user_id, date, water_ml, sleep_hours, energy_score, hunger_score, " +
                        "digestion_score, training_readiness_score, note, created_at, updated_at) " +
                        "SELECT source.id, ?, source.date, source.water_ml, source.sleep_hours, " +
                        "source.energy_score, source.hunger_score, source.digestion_score, " +
                        "source.training_readiness_score, source.note, source.created_at, " +
                        "source.updated_at FROM nutrition_daily_checkins source " +
                        "WHERE source.user_id = ? AND (NOT EXISTS (" +
                        "SELECT 1 FROM nutrition_daily_checkins target " +
                        "WHERE target.user_id = ? AND target.date = source.date) " +
                        "OR julianday(source.updated_at) > julianday((SELECT target.updated_at " +
                        "FROM nutrition_daily_checkins target WHERE target.user_id = ? " +
                        "AND target.date = source.date LIMIT 1)))",
                new Object[]{
                        nextUserId,
                        SupabaseConfig.DEFAULT_USER_ID,
                        nextUserId,
                        nextUserId
                }
        );
        database.delete(
                "nutrition_daily_checkins",
                "user_id = ?",
                new String[]{SupabaseConfig.DEFAULT_USER_ID}
        );

        database.execSQL(
                "INSERT OR REPLACE INTO meal_menu_presets (" +
                        "id, user_id, name, calories, protein_grams, carbs_grams, fat_grams, " +
                        "created_at, updated_at) SELECT source.id, ?, source.name, source.calories, " +
                        "source.protein_grams, source.carbs_grams, source.fat_grams, " +
                        "source.created_at, source.updated_at FROM meal_menu_presets source " +
                        "WHERE source.user_id = ? AND (NOT EXISTS (" +
                        "SELECT 1 FROM meal_menu_presets target WHERE target.user_id = ? " +
                        "AND target.name = source.name COLLATE NOCASE) " +
                        "OR julianday(source.updated_at) > julianday((" +
                        "SELECT target.updated_at FROM meal_menu_presets target " +
                        "WHERE target.user_id = ? AND target.name = source.name COLLATE NOCASE " +
                        "LIMIT 1)))",
                new Object[]{
                        nextUserId,
                        SupabaseConfig.DEFAULT_USER_ID,
                        nextUserId,
                        nextUserId
                }
        );
        database.delete(
                "meal_menu_presets",
                "user_id = ?",
                new String[]{SupabaseConfig.DEFAULT_USER_ID}
        );
    }

    private void ensureDevice(String nextUserId) {
        ensureDevice(dbHelper.getWritableDatabase(), nextUserId);
    }

    private void ensureDevice(SQLiteDatabase database, String nextUserId) {
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

    static String requireRecordDate(String value) {
        String normalized = emptyToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("날짜를 입력하세요.");
        }
        try {
            return LocalDate.parse(normalized).toString();
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("날짜를 YYYY-MM-DD 형식으로 입력하세요.");
        }
    }

    static double requireBodyWeight(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("체중은 0보다 큰 숫자여야 합니다.");
        }
        return value;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String firstNonBlank(String primary, String fallback) {
        return MealEntryPolicy.isMissingText(primary)
                ? (fallback == null ? "" : fallback.trim())
                : primary.trim();
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
            JSONObject object = new JSONObject(metadata);
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) {
                return fallback;
            }
            String normalized = value.toString().trim();
            return MealEntryPolicy.isMissingText(normalized) ? fallback : normalized;
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

    private static String metadataWithEatenAt(String metadata, String eatenAt) {
        try {
            JSONObject object = metadata == null || metadata.trim().isEmpty()
                    ? new JSONObject()
                    : new JSONObject(metadata);
            object.put("eaten_at", eatenAt);
            return object.toString();
        } catch (Exception exception) {
            return json("item_type", "meal", "eaten_at", eatenAt);
        }
    }

    private static String metadataWithMealItemCount(String metadata, int itemCount) {
        try {
            JSONObject object = metadata == null || metadata.trim().isEmpty()
                    ? new JSONObject()
                    : new JSONObject(metadata);
            object.put("item_count", itemCount);
            object.put("composition_version", "2");
            return object.toString();
        } catch (Exception exception) {
            return json(
                    "item_type", "meal",
                    "composition_version", "2",
                    "item_count", String.valueOf(itemCount)
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

    private static String normalizeMealLabel(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "1끼" : normalized;
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
                "SELECT record_type FROM workout_exercises " +
                        "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{exerciseId, userId}
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
                        + "WHERE ws.id = ? AND ws.user_id = ? AND we.user_id = ? "
                        + "AND ws.deleted_at IS NULL LIMIT 1",
                new String[]{setId, userId, userId}
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
        if (input.rir != null && (input.rir < 0 || input.rir > 5)) {
            throw new IllegalArgumentException("RIR는 0부터 5 사이여야 합니다.");
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
        public final Integer rir;
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
                Integer rir,
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
            this.rir = rir;
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
        public final Integer rir;
        public final Integer restSeconds;
        public final boolean completed;

        public SetInput(
                Double weightKg,
                Integer reps,
                Integer durationSeconds,
                Double assistedWeightKg,
                Double addedWeightKg,
                Integer rir,
                Integer restSeconds,
                boolean completed
        ) {
            this(weightKg, reps, durationSeconds, null, assistedWeightKg, addedWeightKg,
                    rir, restSeconds, completed);
        }

        public SetInput(
                Double weightKg,
                Integer reps,
                Integer durationSeconds,
                Double distanceMeters,
                Double assistedWeightKg,
                Double addedWeightKg,
                Integer rir,
                Integer restSeconds,
                boolean completed
        ) {
            this.weightKg = weightKg;
            this.reps = reps;
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
            this.assistedWeightKg = assistedWeightKg;
            this.addedWeightKg = addedWeightKg;
            this.rir = rir;
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
        public final String mealLabel;
        public final String menu;
        public final int calories;
        public final double proteinGrams;
        public final double carbsGrams;
        public final double fatGrams;
        public final int compositionCount;
        public final String previewTitle;
        public final String eatenAt;
        public final String mealTime;
        public final String mealKind;
        public final String storeName;
        public final String branchName;
        public final String menuName;
        public final String nutritionStatus;
        public final String macroRatio;
        public final String macroRatioAccessibility;
        public final boolean timeEditable;
        public final String createdAt;

        public MealEntry(String id, String date, String mealLabel, String menu, int calories,
                         Double proteinGrams, Double carbsGrams, Double fatGrams,
                         int compositionCount, String previewTitle, String eatenAt,
                         String mealKind, String storeName, String branchName, String menuName,
                         String nutritionStatus,
                         boolean timeEditable, String createdAt) {
            this.id = id;
            this.date = date;
            this.mealLabel = mealLabel;
            this.menu = menu;
            this.calories = calories;
            this.proteinGrams = proteinGrams == null ? 0d : proteinGrams;
            this.carbsGrams = carbsGrams == null ? 0d : carbsGrams;
            this.fatGrams = fatGrams == null ? 0d : fatGrams;
            this.compositionCount = compositionCount;
            this.previewTitle = previewTitle;
            this.eatenAt = eatenAt;
            this.mealTime = MealEntryPolicy.displayMealTime(eatenAt);
            this.mealKind = MealRecordKind.normalize(mealKind);
            this.storeName = MealEntryPolicy.isMissingText(storeName) ? "" : storeName.trim();
            this.branchName = MealEntryPolicy.isMissingText(branchName)
                    ? "" : branchName.trim();
            this.menuName = MealEntryPolicy.isMissingText(menuName) ? "" : menuName.trim();
            this.nutritionStatus = nutritionStatus == null || nutritionStatus.trim().isEmpty()
                    ? "recorded" : nutritionStatus.trim();
            this.macroRatio = MealEntryPolicy.macroRatioLabel(
                    carbsGrams,
                    proteinGrams,
                    fatGrams
            );
            this.macroRatioAccessibility = MealEntryPolicy.macroRatioAccessibilityLabel(
                    carbsGrams,
                    proteinGrams,
                    fatGrams
            );
            this.timeEditable = timeEditable;
            this.createdAt = createdAt;
        }

        public boolean isDiningOut() {
            return MealRecordKind.isDiningOut(mealKind);
        }

        public boolean hasEstimatedNutrition() {
            return "estimated".equals(nutritionStatus);
        }

        public String previewSubtitle() {
            return isDiningOut()
                    ? mealTime + " · 외식 · "
                    + (hasEstimatedNutrition() ? "영양 추정" : "영양 미입력")
                    : mealTime + " · "
                    + (compositionCount > 1 ? compositionCount + "개 메뉴 · " : "")
                    + macroRatio;
        }

        public String previewAccessibilityLabel() {
            return previewTitle + ", " + mealTime + ", " + macroRatioAccessibility;
        }
    }

    /** User corrections applied to one already recorded top-level menu. */
    public static final class MealMenuEdit {
        public final String id;
        public final String name;
        public final double quantity;

        public MealMenuEdit(String id, String name, double quantity) {
            this.id = id == null ? "" : id.trim();
            this.name = name == null ? "" : name.trim();
            this.quantity = quantity;
            if (this.id.isEmpty()) {
                throw new IllegalArgumentException("수정할 메뉴 기록이 필요합니다.");
            }
            if (this.name.isEmpty()) {
                throw new IllegalArgumentException("메뉴 이름을 입력하세요.");
            }
            if (!Double.isFinite(quantity) || quantity <= 0d) {
                throw new IllegalArgumentException("메뉴 섭취량은 0보다 커야 합니다.");
            }
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

    /** One user's immutable allocation of a whole-menu dining-out snapshot. */
    public static final class DiningOutConsumptionEntry {
        public final String mealRecordItemId;
        public final String contractVersion;
        public final String consumerScope;
        public final int dinerCount;
        public final double consumedFraction;
        public final String shareMethod;
        public final String confidence;

        public DiningOutConsumptionEntry(
                String mealRecordItemId,
                String contractVersion,
                String consumerScope,
                int dinerCount,
                double consumedFraction,
                String shareMethod,
                String confidence
        ) {
            this.mealRecordItemId = mealRecordItemId;
            this.contractVersion = contractVersion;
            this.consumerScope = consumerScope;
            this.dinerCount = dinerCount;
            this.consumedFraction = consumedFraction;
            this.shareMethod = shareMethod;
            this.confidence = confidence;
        }

        public double percentage() {
            return consumedFraction * 100d;
        }

        public boolean isEqualSplit() {
            return DiningOutConsumption.SHARE_METHOD_EQUAL_BY_DINERS.equals(shareMethod);
        }
    }
    public static final class MealComponentEntry {
        public final String id;
        public final String foodName;
        public final double quantity;
        public final String unit;
        public final double calories;
        public final double proteinGrams;
        public final double carbsGrams;
        public final double fatGrams;
        /** Null for pre-v36 component snapshots that had no independent allocation. */
        public final Double consumedFraction;
        public final String compositionGroupKey;
        public final String compositionGroupType;
        public final String compositionRole;
        public final String compositionMemberId;

        public MealComponentEntry(
                String id,
                String foodName,
                double quantity,
                String unit,
                double calories,
                double proteinGrams,
                double carbsGrams,
                double fatGrams
        ) {
            this(
                    id,
                    foodName,
                    quantity,
                    unit,
                    calories,
                    proteinGrams,
                    carbsGrams,
                    fatGrams,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public MealComponentEntry(
                String id,
                String foodName,
                double quantity,
                String unit,
                double calories,
                double proteinGrams,
                double carbsGrams,
                double fatGrams,
                String compositionGroupKey,
                String compositionRole,
                String compositionMemberId
        ) {
            this(
                    id,
                    foodName,
                    quantity,
                    unit,
                    calories,
                    proteinGrams,
                    carbsGrams,
                    fatGrams,
                    compositionGroupKey,
                    null,
                    compositionRole,
                    compositionMemberId,
                    null
            );
        }

        public MealComponentEntry(
                String id,
                String foodName,
                double quantity,
                String unit,
                double calories,
                double proteinGrams,
                double carbsGrams,
                double fatGrams,
                String compositionGroupKey,
                String compositionGroupType,
                String compositionRole,
                String compositionMemberId,
                Double consumedFraction
        ) {
            this.id = id;
            this.foodName = foodName;
            this.quantity = quantity;
            this.unit = unit;
            this.calories = calories;
            this.proteinGrams = proteinGrams;
            this.carbsGrams = carbsGrams;
            this.fatGrams = fatGrams;
            this.consumedFraction = consumedFraction;
            this.compositionGroupKey = compositionGroupKey;
            this.compositionGroupType = compositionGroupType;
            this.compositionRole = compositionRole;
            this.compositionMemberId = compositionMemberId;
        }

        public boolean hasExplicitConsumedFraction() {
            return consumedFraction != null;
        }

        public double consumedFraction() {
            return consumedFraction == null ? 1d : consumedFraction;
        }

        public double percentage() {
            return consumedFraction() * 100d;
        }

        public String label() {
            return foodName + " · " + NutritionCalculator.trim(quantity) + unit
                    + " · " + Math.round(calories * consumedFraction()) + "kcal";
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
