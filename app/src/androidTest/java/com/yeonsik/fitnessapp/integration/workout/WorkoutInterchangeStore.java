package com.yeonsik.fitnessapp.integration.workout;

import android.content.ContentValues;
import android.database.Cursor;

import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.integration.transfer.FleekCsvImporter;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.integration.transfer.WorkoutTransferCodec;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity;
import com.yeonsik.fitnessapp.exercise.ExerciseVolumeCalculator;
import com.yeonsik.fitnessapp.exercise.LoadState;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseFamily;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;
import com.yeonsik.fitnessapp.integration.personalos.FitnessSummaryStore;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutInterchangeApi;

import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistence boundary for FLEEK and workout-transfer interchange formats. */
public final class WorkoutInterchangeStore implements WorkoutInterchangeApi {
    private static final String DEVICE_ID = "android-local";

    private final FitnessDatabaseConnection database;
    private final ExerciseFamilyCatalog familyCatalog;
    private final FitnessSummaryStore summaryStore;

    public WorkoutInterchangeStore(FitnessDatabaseConnection database, android.content.Context context) {
        if (database == null || context == null) {
            throw new IllegalArgumentException("운동 전송 저장소를 초기화할 수 없습니다.");
        }
        this.database = database;
        this.familyCatalog = ExerciseFamilyCatalog.load(context);
        this.summaryStore = new FitnessSummaryStore(database);
    }

    public WorkoutTransferCodec.Document exportTransfer(String ownerId) {
        recalculateCanonicalVolumes(ownerId);
        List<WorkoutTransferCodec.Session> sessions = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT id, date, workout_type, category, exercise_name, duration_seconds, "
                        + "metadata, source_app FROM workout_records "
                        + "WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND scope IN ('fitness', 'both') ORDER BY date ASC, created_at ASC",
                new String[]{ownerId})) {
            while (cursor.moveToNext()) {
                String recordId = cursor.getString(0);
                String metadata = cursor.getString(6);
                sessions.add(new WorkoutTransferCodec.Session(
                        emptyToDefault(cursor.getString(7), "fitness"), recordId,
                        cursor.getString(1), cursor.getString(4), cursor.getString(2),
                        cursor.getString(3), nullableInt(cursor, 5),
                        metadataValue(metadata, "status"), metadataValue(metadata, "started_at"),
                        metadataValue(metadata, "ended_at"),
                        emptyToNull(metadataValue(metadata, "memo")),
                        exportExercises(ownerId, recordId)));
            }
        }
        return new WorkoutTransferCodec.Document(
                WorkoutTransferCodec.V2, "yeonsik.fitnessapp", now(), sessions);
    }

    private List<WorkoutTransferCodec.Exercise> exportExercises(String ownerId, String recordId) {
        List<WorkoutTransferCodec.Exercise> exercises = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT id, exercise_id, order_index, exercise_name_snapshot, ui_part, "
                        + "equipment_snapshot, record_type, family_id, preset_id, "
                        + "canonical_variant_key, visual_variant_key FROM workout_exercises "
                        + "WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL "
                        + "ORDER BY order_index",
                new String[]{recordId, ownerId})) {
            while (cursor.moveToNext()) {
                ExerciseFamilyIdentity identity = identityForRow(
                        cursor.getString(1), cursor.getString(3), cursor.getString(7),
                        cursor.getString(8), cursor.getString(9), cursor.getString(10),
                        cursor.getString(6));
                exercises.add(new WorkoutTransferCodec.Exercise(
                        cursor.getString(1), identity == null ? null : identity.presetId,
                        identity == null ? null : identity.canonicalPresetId,
                        identity == null ? null : identity.legacyExerciseId,
                        canonicalName(cursor.getString(3), identity), cursor.getInt(2),
                        cursor.getString(6), cursor.getString(4),
                        cursor.isNull(5) ? null : cursor.getString(5),
                        exportSets(ownerId, cursor.getString(0))));
            }
        }
        return exercises;
    }

    private List<WorkoutTransferCodec.SetData> exportSets(String ownerId, String exerciseId) {
        List<WorkoutTransferCodec.SetData> sets = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT id, set_index, target_reps, actual_reps, weight_kg, volume_kg, "
                        + "duration_seconds, distance_meters, rest_seconds, assisted_weight_kg, "
                        + "added_weight_kg, load_state, is_completed, rpe, rir, memo, "
                        + "input_load_value, input_load_unit FROM workout_sets "
                        + "WHERE workout_exercise_id = ? AND user_id = ? AND deleted_at IS NULL "
                        + "ORDER BY set_index",
                new String[]{exerciseId, ownerId})) {
            while (cursor.moveToNext()) {
                String inputUnit = cursor.isNull(17) ? null : cursor.getString(17);
                if (inputUnit != null && MassUnit.parse(inputUnit) == null) {
                    throw new IllegalStateException("저장된 입력 중량 단위를 해석하지 못했습니다.");
                }
                sets.add(new WorkoutTransferCodec.SetData(
                        cursor.getString(0), cursor.getInt(1), nullableInt(cursor, 2),
                        nullableInt(cursor, 3), nullableDouble(cursor, 4), nullableDouble(cursor, 5),
                        nullableInt(cursor, 6), nullableDouble(cursor, 7), nullableInt(cursor, 8),
                        nullableDouble(cursor, 9), nullableDouble(cursor, 10),
                        cursor.isNull(11) ? null : cursor.getString(11), cursor.getInt(12) == 1,
                        nullableInt(cursor, 13), nullableInt(cursor, 14),
                        cursor.isNull(15) ? null : cursor.getString(15),
                        nullableDouble(cursor, 16), inputUnit));
            }
        }
        return sets;
    }

    public WorkoutInterchangeResult importTransfer(
            String ownerId, WorkoutTransferCodec.Document document) {
        WorkoutTransferCodec.validate(document);
        if (document.sessions.isEmpty()) {
            throw new IllegalArgumentException("가져올 운동 전송 기록이 없습니다.");
        }
        List<RuntimeExercisePreset> presets = new ArrayList<>();
        for (WorkoutTransferCodec.Session session : document.sessions) {
            for (WorkoutTransferCodec.Exercise exercise : session.exercises) {
                presets.add(requireTransferPreset(exercise));
            }
        }
        WorkoutInterchangeResult result = new WorkoutInterchangeResult();
        database.beginTransaction();
        try {
            int presetIndex = 0;
            for (WorkoutTransferCodec.Session session : document.sessions) {
                if (hasImportedTransfer(ownerId, session.sourceApp, session.sourceRecordId)) {
                    result.skippedDuplicateSessions++;
                    presetIndex += session.exercises.size();
                    continue;
                }
                String date = requireDate(session.date);
                String recordId = newId();
                String importedAt = now();
                ContentValues record = baseValues(ownerId, recordId, importedAt);
                record.put("date", date);
                record.put("workout_type", emptyToDefault(session.workoutType, "strength"));
                record.put("category", emptyToDefault(session.category,
                        session.exercises.isEmpty() ? "other"
                                : emptyToDefault(session.exercises.get(0).uiPart, "other")));
                record.put("exercise_name", emptyToDefault(session.title, "Imported workout"));
                putNullable(record, "duration_seconds", session.durationSeconds == null
                        ? computeDurationSeconds(date, session.startedAt, session.endedAt)
                        : session.durationSeconds);
                record.put("total_volume_kg", 0d);
                record.putNull("average_heart_rate");
                record.put("is_backfilled", 1);
                record.put("backfilled_at", importedAt);
                record.put("backfill_reason", "Workout Transfer JSON import");
                record.put("source_app", session.sourceApp);
                record.put("scope", "fitness");
                record.put("metadata", transferMetadata(session, document.formatVersion));
                database.insertOrThrow("workout_records", null, record);

                int fallbackOrder = 1;
                for (WorkoutTransferCodec.Exercise exercise : session.exercises) {
                    RuntimeExercisePreset preset = presets.get(presetIndex++);
                    ExerciseFamilyIdentity identity = familyCatalog.identityForPreset(preset);
                    String recordType = FitnessRecordContract.normalizeRecordType(preset.recordType);
                    String workoutExerciseId = newId();
                    ContentValues values = baseValues(ownerId, workoutExerciseId, importedAt);
                    values.put("record_id", recordId);
                    values.put("order_index", exercise.orderIndex == null
                            ? fallbackOrder : Math.max(1, exercise.orderIndex));
                    fallbackOrder++;
                    values.put("exercise_id", emptyToDefault(preset.storageExerciseId, exercise.exerciseId));
                    values.put("exercise_name_snapshot", canonicalName(preset.displayName(), identity));
                    values.put("ui_part", normalizeCategory(emptyToDefault(
                            identity == null ? null : identity.defaultUiPart, preset.defaultUiPart)));
                    values.put("primary_sub_part_snapshot", emptyToDefault(
                            preset.primarySubPartNameKo, exercise.uiPart == null ? "" : exercise.uiPart));
                    putNullable(values, "equipment_snapshot",
                            preset.equipmentNameKo == null ? exercise.equipment : preset.equipmentNameKo);
                    values.put("record_type", recordType);
                    putIdentity(values, identity);
                    values.putNull("memo");
                    database.insertOrThrow("workout_exercises", null, values);
                    result.importedExercises++;

                    for (WorkoutTransferCodec.SetData set : exercise.sets) {
                        TransferSetInput input = TransferSetInput.from(set);
                        LoadState state = resolveLoadState(recordType, input, identity);
                        validateSetInput(recordType, input, identity, state);
                        double volume = volume(identity, recordType, input, state);
                        if (set.volumeKg != null && !massValuesMatch(set.volumeKg, volume)) {
                            throw new IllegalArgumentException(
                                    "전송된 volumeKg가 canonical 중량 계산과 일치하지 않습니다.");
                        }
                        insertTransferSet(ownerId, workoutExerciseId, importedAt,
                                set, input, state, volume);
                        result.importedSets++;
                    }
                }
                summaryStore.updateSharedWorkoutSummary(ownerId, recordId, false);
                result.importedSessions++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return result;
    }

    public WorkoutInterchangeResult importFleek(String ownerId, FleekCsvImporter.ImportPlan plan) {
        if (plan == null || plan.sessions.isEmpty()) {
            throw new IllegalArgumentException("가져올 FLEEK 운동 기록이 없습니다.");
        }
        WorkoutInterchangeResult result = new WorkoutInterchangeResult();
        result.skippedRows = plan.skippedRows;
        database.beginTransaction();
        try {
            for (FleekCsvImporter.SessionData session : plan.sessions) {
                if (hasImportedFleek(ownerId, session.sourceKey)) {
                    result.skippedDuplicateSessions++;
                    continue;
                }
                String recordId = newId();
                String importedAt = now();
                double totalVolume = importedVolume(session);
                ContentValues record = baseValues(ownerId, recordId, importedAt);
                record.put("date", session.date);
                record.put("workout_type", "strength");
                record.put("category", firstCategory(session));
                record.put("exercise_name", session.title);
                record.put("duration_seconds", session.durationSeconds);
                record.put("total_volume_kg", totalVolume);
                record.putNull("average_heart_rate");
                record.put("is_backfilled", 1);
                record.put("backfilled_at", importedAt);
                record.put("backfill_reason", "FLEEK CSV import");
                record.put("source_app", "fitness");
                record.put("scope", "fitness");
                record.put("metadata", fleekMetadata(session, totalVolume));
                database.insertOrThrow("workout_records", null, record);

                int order = 1;
                for (FleekCsvImporter.ExerciseData exercise : session.exercises) {
                    ExerciseFamilyIdentity identity =
                            familyCatalog.identityForStorageExerciseId(exercise.exerciseId);
                    String workoutExerciseId = newId();
                    ContentValues values = baseValues(ownerId, workoutExerciseId, importedAt);
                    values.put("record_id", recordId);
                    values.put("order_index", order++);
                    values.put("exercise_id", exercise.exerciseId);
                    values.put("exercise_name_snapshot", canonicalName(exercise.name, identity));
                    values.put("ui_part", exercise.uiPart);
                    values.put("primary_sub_part_snapshot", exercise.primarySubPart);
                    values.put("equipment_snapshot", exercise.equipment);
                    values.put("record_type", FitnessRecordContract.normalizeRecordType(exercise.recordType));
                    putIdentity(values, identity);
                    values.putNull("memo");
                    database.insertOrThrow("workout_exercises", null, values);
                    result.importedExercises++;

                    for (FleekCsvImporter.SetData set : exercise.sets) {
                        TransferSetInput input = TransferSetInput.from(set);
                        LoadState state = resolveLoadState(exercise.recordType, input, identity);
                        validateSetInput(exercise.recordType, input, identity, state);
                        ContentValues setValues = baseValues(ownerId, newId(), importedAt);
                        setValues.put("workout_exercise_id", workoutExerciseId);
                        setValues.put("set_index", Math.max(1, set.setIndex));
                        putNullable(setValues, "target_reps", set.reps);
                        putNullable(setValues, "actual_reps", set.reps);
                        putLoadFields(setValues, input, state);
                        putDefaultProvenance(setValues, input, state);
                        setValues.put("volume_kg", volume(identity, exercise.recordType, input, state));
                        putNullable(setValues, "duration_seconds", set.durationSeconds);
                        putNullable(setValues, "distance_meters", set.distanceMeters);
                        setValues.putNull("rest_seconds");
                        putLoadState(setValues, state);
                        setValues.put("is_completed", 1);
                        putNullable(setValues, "rpe", set.rpe);
                        setValues.putNull("rir");
                        setValues.put("memo", fleekSetMemo(set));
                        database.insertOrThrow("workout_sets", null, setValues);
                        result.importedSets++;
                        if (exercise.masterMatched) result.masterMatchedSets++;
                    }
                }
                summaryStore.updateSharedWorkoutSummary(ownerId, recordId, false);
                result.importedSessions++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return result;
    }

    private void insertTransferSet(String ownerId, String exerciseId, String importedAt,
            WorkoutTransferCodec.SetData set, TransferSetInput input, LoadState state,
            double volume) {
        ContentValues values = baseValues(ownerId, newId(), importedAt);
        values.put("workout_exercise_id", exerciseId);
        values.put("set_index", Math.max(1, set.setIndex));
        putNullable(values, "target_reps", set.targetReps);
        putNullable(values, "actual_reps", set.actualReps);
        putLoadFields(values, input, state);
        putExplicitProvenance(values, input, state);
        values.put("volume_kg", volume);
        putNullable(values, "duration_seconds", set.durationSeconds);
        putNullable(values, "distance_meters", set.distanceMeters);
        putNullable(values, "rest_seconds", set.restSeconds);
        putLoadState(values, state);
        values.put("is_completed", set.isCompleted ? 1 : 0);
        putNullable(values, "rpe", set.rpe);
        putNullable(values, "rir", set.rir);
        putNullable(values, "memo", set.memo);
        database.insertOrThrow("workout_sets", null, values);
    }

    private boolean hasImportedTransfer(String ownerId, String sourceApp, String sourceRecordId) {
        try (Cursor cursor = database.rawQuery(
                "SELECT id, metadata FROM workout_records WHERE user_id = ? AND source_app = ?",
                new String[]{ownerId, sourceApp})) {
            while (cursor.moveToNext()) {
                if (sourceRecordId.equals(cursor.getString(0))) return true;
                String metadata = cursor.getString(1);
                if (sourceApp.equals(metadataValue(metadata, "transfer_source_app"))
                        && sourceRecordId.equals(metadataValue(metadata, "transfer_source_record_id"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasImportedFleek(String ownerId, String sourceKey) {
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM workout_records WHERE user_id = ? AND deleted_at IS NULL "
                        + "AND metadata LIKE ? LIMIT 1",
                new String[]{ownerId, "%\"fleek_source_key\":\"" + sourceKey + "\"%"})) {
            return cursor.moveToFirst();
        }
    }

    private RuntimeExercisePreset requireTransferPreset(WorkoutTransferCodec.Exercise exercise) {
        RuntimeExerciseCatalog catalog = familyCatalog.runtimeCatalog();
        RuntimeExercisePreset preset = null;
        preset = mergePreset(preset, catalog.presetForStorageExerciseId(exercise.exerciseId));
        preset = mergePreset(preset, catalog.presetForStorageExerciseId(exercise.canonicalExerciseId));
        preset = mergePreset(preset, catalog.preset(exercise.presetId));
        preset = mergePreset(preset, catalog.preset(exercise.canonicalPresetId));
        if (preset == null) {
            throw new IllegalArgumentException("운동 catalog ID를 찾지 못했습니다: " + exercise.exerciseId);
        }
        return preset;
    }

    private static RuntimeExercisePreset mergePreset(
            RuntimeExercisePreset current, RuntimeExercisePreset candidate) {
        if (candidate == null) return current;
        if (current != null && !current.identityId().equals(candidate.identityId())) {
            throw new IllegalArgumentException("운동 전송의 catalog ID가 서로 다른 운동을 가리킵니다.");
        }
        return candidate;
    }

    private double importedVolume(FleekCsvImporter.SessionData session) {
        double total = 0d;
        for (FleekCsvImporter.ExerciseData exercise : session.exercises) {
            ExerciseFamilyIdentity identity =
                    familyCatalog.identityForStorageExerciseId(exercise.exerciseId);
            for (FleekCsvImporter.SetData set : exercise.sets) {
                TransferSetInput input = TransferSetInput.from(set);
                LoadState state = resolveLoadState(exercise.recordType, input, identity);
                validateSetInput(exercise.recordType, input, identity, state);
                total += volume(identity, exercise.recordType, input, state);
            }
        }
        return total;
    }
    private LoadState resolveLoadState(
            String recordType, TransferSetInput input, ExerciseFamilyIdentity identity) {
        LoadState state = input.loadState;
        String normalized = FitnessRecordContract.normalizeRecordType(recordType);
        if (state == null && FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(normalized)
                && input.addedWeightKg != null && input.addedWeightKg > 0) {
            state = LoadState.ADDED_WEIGHT;
        }
        if (state == null && identity != null && identity.defaultLoadStateValue() != null) {
            state = identity.defaultLoadStateValue();
        }
        if (state == null && FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(normalized)) {
            state = LoadState.BODYWEIGHT;
        } else if (state == null && FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(normalized)) {
            state = LoadState.ASSISTED;
        } else if (state == null && (FitnessRecordContract.WEIGHT_REPS.equals(normalized)
                || FitnessRecordContract.WEIGHT_TIME.equals(normalized))) {
            state = LoadState.EXTERNAL_LOAD;
        }
        if (identity != null && state != null) {
            RuntimeExerciseFamily family = familyCatalog.runtimeCatalog().family(identity.familyId);
            if (family != null && !family.allowedLoadStates.isEmpty()
                    && !family.supportsLoadState(state)) {
                throw new IllegalArgumentException(
                        "이 운동 family에서는 " + state.id() + " 상태를 사용할 수 없습니다.");
            }
        }
        return state;
    }

    private static void validateSetInput(String recordType, TransferSetInput input,
            ExerciseFamilyIdentity identity, LoadState state) {
        validateNonNegative(input.weightKg, "중량");
        validateNonNegative(input.distanceMeters, "거리");
        validateNonNegative(input.assistedWeightKg, "보조 중량");
        validateNonNegative(input.addedWeightKg, "추가 중량");
        if (input.reps != null && input.reps < 0) throw new IllegalArgumentException("횟수는 음수일 수 없습니다.");
        if (input.durationSeconds != null && input.durationSeconds < 0) throw new IllegalArgumentException("시간은 음수일 수 없습니다.");
        if (input.restSeconds != null && input.restSeconds < 0) throw new IllegalArgumentException("휴식 시간은 음수일 수 없습니다.");
        if (input.rir != null && (input.rir < 0 || input.rir > 5)) throw new IllegalArgumentException("RIR는 0부터 5 사이여야 합니다.");
        validateProvenance(input, state);
        if (!input.completed) return;
        String normalized = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.TIME.equals(normalized)
                || FitnessRecordContract.WEIGHT_TIME.equals(normalized)) {
            requirePositive(input.durationSeconds, "시간");
        } else {
            requirePositive(input.reps, "횟수");
        }
        if (state == LoadState.EXTERNAL_LOAD) requirePositive(input.weightKg, "중량");
        if (state == LoadState.ADDED_WEIGHT) requirePositive(input.addedWeightKg, "추가 중량");
        if (state == LoadState.ASSISTED) requirePositive(input.assistedWeightKg, "보조 중량");
    }

    private static void validateProvenance(TransferSetInput input, LoadState state) {
        boolean hasValue = input.inputLoadValue != null;
        boolean hasUnit = input.inputLoadUnit != null;
        if (hasValue != hasUnit) {
            throw new IllegalArgumentException("입력 중량의 값과 단위를 함께 저장해야 합니다.");
        }
        validateNonNegative(input.inputLoadValue, "입력 중량");
        if (!hasValue) return;
        if (!isNumericState(state)) {
            throw new IllegalArgumentException("저항 상태가 중량을 저장하지 않습니다.");
        }
        Double canonical = canonicalLoad(input, state);
        if (canonical == null || !massValuesMatch(
                MassUnit.toKg(input.inputLoadValue, input.inputLoadUnit), canonical)) {
            throw new IllegalArgumentException("입력 중량과 canonical 중량이 일치하지 않습니다.");
        }
    }

    private double volume(ExerciseFamilyIdentity identity, String recordType,
            TransferSetInput input, LoadState state) {
        RuntimeExercisePreset preset = presetForIdentity(identity);
        String laterality = preset == null ? variantLaterality(identity) : preset.laterality();
        int multiplier = preset == null ? ExerciseVolumeCalculator.DEFAULT_IMPLEMENT_MULTIPLIER
                : preset.implementMultiplier;
        return ExerciseVolumeCalculator.calculate(recordType, state,
                value(input.weightKg), value(input.addedWeightKg), integer(input.reps),
                laterality, multiplier);
    }

    private RuntimeExercisePreset presetForIdentity(ExerciseFamilyIdentity identity) {
        if (identity == null) return null;
        RuntimeExerciseCatalog catalog = familyCatalog.runtimeCatalog();
        RuntimeExercisePreset preset = catalog.preset(identity.presetId);
        if (preset == null) preset = catalog.preset(identity.canonicalPresetId);
        if (preset == null) preset = catalog.presetForStorageExerciseId(identity.legacyExerciseId);
        return preset;
    }

    private static String variantLaterality(ExerciseFamilyIdentity identity) {
        if (identity == null || identity.variantJson == null) return null;
        try {
            return new JSONObject(identity.variantJson).optString("laterality", null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void putDefaultProvenance(ContentValues values, TransferSetInput input, LoadState state) {
        values.putNull("input_load_value");
        values.putNull("input_load_unit");
        if (!isNumericState(state)) return;
        Double canonical = canonicalLoad(input, state);
        if (canonical == null) return;
        values.put("input_load_value", input.inputLoadValue == null ? canonical : input.inputLoadValue);
        values.put("input_load_unit", input.inputLoadUnit == null
                ? MassUnit.KG.id() : input.inputLoadUnit.id());
    }

    private static void putExplicitProvenance(
            ContentValues values, TransferSetInput input, LoadState state) {
        values.putNull("input_load_value");
        values.putNull("input_load_unit");
        if (!isNumericState(state) || input.inputLoadValue == null || input.inputLoadUnit == null) return;
        values.put("input_load_value", input.inputLoadValue);
        values.put("input_load_unit", input.inputLoadUnit.id());
    }

    private static void putLoadFields(
            ContentValues values, TransferSetInput input, LoadState state) {
        if (state == LoadState.EXTERNAL_LOAD) {
            putNullable(values, "weight_kg", input.weightKg);
            values.putNull("assisted_weight_kg"); values.putNull("added_weight_kg");
        } else if (state == LoadState.ADDED_WEIGHT) {
            values.putNull("weight_kg"); values.putNull("assisted_weight_kg");
            putNullable(values, "added_weight_kg", input.addedWeightKg);
        } else if (state == LoadState.ASSISTED) {
            values.putNull("weight_kg");
            putNullable(values, "assisted_weight_kg", input.assistedWeightKg);
            values.putNull("added_weight_kg");
        } else if (state == LoadState.BODYWEIGHT || state == LoadState.BAND_ASSISTED
                || state == LoadState.BAND_RESISTED) {
            values.putNull("weight_kg"); values.putNull("assisted_weight_kg");
            values.putNull("added_weight_kg");
        } else {
            putNullable(values, "weight_kg", input.weightKg);
            putNullable(values, "assisted_weight_kg", input.assistedWeightKg);
            putNullable(values, "added_weight_kg", input.addedWeightKg);
        }
    }

    private void recalculateCanonicalVolumes(String ownerId) {
        List<VolumeUpdate> updates = new ArrayList<>();
        Map<String, Double> totals = new LinkedHashMap<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT ws.id, ws.volume_kg, ws.weight_kg, ws.actual_reps, "
                        + "COALESCE(ws.added_weight_kg, 0), COALESCE(ws.assisted_weight_kg, 0), "
                        + "ws.load_state, we.record_id, we.exercise_id, we.exercise_name_snapshot, "
                        + "we.family_id, we.preset_id, we.canonical_variant_key, we.visual_variant_key, "
                        + "we.record_type, ws.is_completed FROM workout_sets ws "
                        + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id "
                        + "WHERE ws.user_id = ? AND we.user_id = ? "
                        + "AND ws.deleted_at IS NULL AND we.deleted_at IS NULL",
                new String[]{ownerId, ownerId})) {
            while (cursor.moveToNext()) {
                String type = FitnessRecordContract.normalizeRecordType(cursor.getString(14));
                ExerciseFamilyIdentity identity = identityForRow(
                        cursor.getString(8), cursor.getString(9), cursor.getString(10),
                        cursor.getString(11), cursor.getString(12), cursor.getString(13), type);
                double stored = cursor.isNull(1) ? 0d : cursor.getDouble(1);
                LoadState state = readLoadState(type, identity,
                        cursor.isNull(6) ? null : cursor.getString(6), cursor.getDouble(4));
                TransferSetInput input = new TransferSetInput(
                        nullableDouble(cursor, 2), nullableInt(cursor, 3), null, null,
                        cursor.getDouble(5), cursor.getDouble(4), null, null,
                        cursor.getInt(15) == 1, state, null, null);
                double calculated = identity == null ? stored : volume(identity, type, input, state);
                if (!massValuesMatch(stored, calculated)) {
                    updates.add(new VolumeUpdate(cursor.getString(0), calculated));
                }
                if (cursor.getInt(15) == 1) {
                    totals.put(cursor.getString(7),
                            totals.getOrDefault(cursor.getString(7), 0d) + calculated);
                }
            }
        }
        database.beginTransaction();
        try {
            for (VolumeUpdate update : updates) {
                ContentValues values = new ContentValues();
                values.put("volume_kg", update.volume);
                database.update("workout_sets", values,
                        "id = ? AND user_id = ? AND deleted_at IS NULL",
                        new String[]{update.id, ownerId});
            }
            for (Map.Entry<String, Double> total : totals.entrySet()) {
                ContentValues values = new ContentValues();
                values.put("total_volume_kg", total.getValue());
                database.update("workout_records", values,
                        "id = ? AND user_id = ? AND deleted_at IS NULL",
                        new String[]{total.getKey(), ownerId});
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }
    private ExerciseFamilyIdentity identityForRow(String exerciseId, String name,
            String familyId, String presetId, String canonicalKey, String visualKey,
            String recordType) {
        ExerciseFamilyIdentity mapped = familyCatalog.identityForStorageExerciseId(exerciseId);
        if (mapped != null) return mapped;
        if (emptyToNull(familyId) == null) return null;
        String displayName = emptyToDefault(name, "Exercise");
        return new ExerciseFamilyIdentity(exerciseId, familyId, presetId, presetId,
                displayName, displayName, displayName, displayName, null,
                canonicalKey, visualKey, null, null,
                FitnessRecordContract.normalizeRecordType(recordType), null);
    }

    private LoadState readLoadState(String recordType, ExerciseFamilyIdentity identity,
            String stored, double addedWeightKg) {
        LoadState state = LoadState.fromId(stored);
        if (state != null) return state;
        return resolveLoadState(recordType,
                new TransferSetInput(null, null, null, null, null,
                        addedWeightKg > 0 ? addedWeightKg : null,
                        null, null, false, null, null, null), identity);
    }

    private static void putIdentity(ContentValues values, ExerciseFamilyIdentity identity) {
        putNullable(values, "family_id", identity == null ? null : identity.familyId);
        putNullable(values, "preset_id", identity == null ? null : identity.presetId);
        putNullable(values, "canonical_variant_key",
                identity == null ? null : identity.canonicalVariantKey);
        putNullable(values, "visual_variant_key",
                identity == null ? null : identity.visualVariantKey);
    }

    private static String transferMetadata(
            WorkoutTransferCodec.Session session, int formatVersion) {
        try {
            return new JSONObject()
                    .put("contract_version", FitnessRecordContract.VERSION)
                    .put("status", emptyToDefault(session.status, "completed"))
                    .put("started_at", emptyToDefault(session.startedAt, ""))
                    .put("ended_at", emptyToDefault(session.endedAt, ""))
                    .put("memo", emptyToDefault(session.memo, ""))
                    .put("transfer_format", WorkoutTransferCodec.FORMAT)
                    .put("transfer_format_version", formatVersion)
                    .put("transfer_source_app", session.sourceApp)
                    .put("transfer_source_record_id", session.sourceRecordId)
                    .toString();
        } catch (Exception error) {
            throw new IllegalStateException("운동 전송 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private static String fleekMetadata(FleekCsvImporter.SessionData session, double volume) {
        try {
            String endedAt = OffsetDateTime.parse(session.sourceTimestamp)
                    .plusSeconds(Math.max(0, session.durationSeconds)).toString();
            return new JSONObject()
                    .put("contract_version", FitnessRecordContract.VERSION)
                    .put("status", "completed")
                    .put("started_at", session.sourceTimestamp)
                    .put("ended_at", endedAt)
                    .put("duration_seconds", session.durationSeconds)
                    .put("total_volume_kg", volume)
                    .put("memo", "FLEEK CSV에서 가져옴")
                    .put("import_source", "fleek_csv")
                    .put("fleek_source_key", session.sourceKey)
                    .toString();
        } catch (Exception error) {
            throw new IllegalStateException("FLEEK 세션 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private static String fleekSetMemo(FleekCsvImporter.SetData set) {
        try {
            JSONObject object = new JSONObject().put("import_source", "fleek_csv");
            if (!set.setType.isEmpty()) object.put("set_type", set.setType);
            if (!set.gripType.isEmpty()) object.put("grip_type", set.gripType);
            return object.toString();
        } catch (Exception ignored) {
            return "FLEEK CSV";
        }
    }

    private static String firstCategory(FleekCsvImporter.SessionData session) {
        if (session.exercises.isEmpty()) return "arms";
        String category = session.exercises.get(0).uiPart;
        return emptyToNull(category) == null ? "arms" : category;
    }

    private static ContentValues baseValues(String ownerId, String id, String timestamp) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("user_id", ownerId);
        values.put("device_id", DEVICE_ID);
        values.put("created_at", timestamp);
        values.put("updated_at", timestamp);
        values.putNull("deleted_at");
        return values;
    }

    private static Integer computeDurationSeconds(String date, String start, String end) {
        OffsetDateTime startOffset = parseOffset(start);
        OffsetDateTime endOffset = parseOffset(end);
        if (startOffset != null && endOffset != null) {
            long seconds = Duration.between(startOffset, endOffset).getSeconds();
            return seconds < 0 ? null : safeInt(seconds);
        }
        LocalTime startTime = parseTime(start);
        LocalTime endTime = parseTime(end);
        if (startTime == null || endTime == null) return null;
        try {
            LocalDate localDate = LocalDate.parse(date);
            long seconds = Duration.between(
                    LocalDateTime.of(localDate, startTime),
                    LocalDateTime.of(localDate, endTime)).getSeconds();
            return seconds < 0 ? null : safeInt(seconds);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static OffsetDateTime parseOffset(String value) {
        try {
            return emptyToNull(value) == null ? null : OffsetDateTime.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        String candidate = emptyToNull(value);
        if (candidate == null) return null;
        for (String pattern : new String[]{"H:mm", "HH:mm", "H:mm:ss", "HH:mm:ss"}) {
            try {
                return LocalTime.parse(candidate, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // Try next legacy representation.
            }
        }
        return null;
    }

    private static String requireDate(String value) {
        String candidate = emptyToNull(value);
        if (candidate == null) throw new IllegalArgumentException("날짜를 입력하세요.");
        try {
            return LocalDate.parse(candidate).toString();
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("날짜를 YYYY-MM-DD 형식으로 입력하세요.");
        }
    }

    private static String normalizeCategory(String value) {
        switch (emptyToDefault(value, "chest").toLowerCase()) {
            case "가슴": case "chest": return "chest";
            case "등": case "back": return "back";
            case "하체": case "legs": return "legs";
            case "이두": case "biceps": return "biceps";
            case "삼두": case "triceps": return "triceps";
            case "어깨": case "shoulders": return "shoulders";
            case "복근": case "abs": return "abs";
            case "팔": case "arms": return "arms";
            default: return "chest";
        }
    }

    private static String canonicalName(String fallback, ExerciseFamilyIdentity identity) {
        return identity == null || emptyToNull(identity.displayName()) == null
                ? emptyToDefault(fallback, "Exercise") : identity.displayName();
    }

    private static String metadataValue(String metadata, String key) {
        try {
            return new JSONObject(emptyToDefault(metadata, "{}")).optString(key, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isNumericState(LoadState state) {
        return state == LoadState.EXTERNAL_LOAD || state == LoadState.ADDED_WEIGHT
                || state == LoadState.ASSISTED;
    }

    private static Double canonicalLoad(TransferSetInput input, LoadState state) {
        if (state == LoadState.EXTERNAL_LOAD) return input.weightKg;
        if (state == LoadState.ADDED_WEIGHT) return input.addedWeightKg;
        if (state == LoadState.ASSISTED) return input.assistedWeightKg;
        if (input.weightKg != null) return input.weightKg;
        if (input.addedWeightKg != null) return input.addedWeightKg;
        return input.assistedWeightKg;
    }

    private static void putLoadState(ContentValues values, LoadState state) {
        if (state == null) values.putNull("load_state"); else values.put("load_state", state.id());
    }

    private static void putNullable(ContentValues values, String key, Integer value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static void putNullable(ContentValues values, String key, Double value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static void putNullable(ContentValues values, String key, String value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static Integer nullableInt(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getInt(index);
    }

    private static Double nullableDouble(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getDouble(index);
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

    private static boolean massValuesMatch(double left, double right) {
        double tolerance = Math.max(0.01d,
                Math.max(Math.abs(left), Math.abs(right)) * 0.0001d);
        return Math.abs(left - right) <= tolerance;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static int safeInt(long value) {
        return value <= 0 ? 0 : value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static double value(Double value) { return value == null ? 0d : value; }
    private static int integer(Integer value) { return value == null ? 0 : value; }
    private static String now() { return OffsetDateTime.now().toString(); }
    private static String newId() { return UUID.randomUUID().toString(); }

    private static final class VolumeUpdate {
        final String id;
        final double volume;
        VolumeUpdate(String id, double volume) { this.id = id; this.volume = volume; }
    }

    private static final class TransferSetInput {
        final Double weightKg;
        final Integer reps;
        final Integer durationSeconds;
        final Double distanceMeters;
        final Double assistedWeightKg;
        final Double addedWeightKg;
        final Integer rir;
        final Integer restSeconds;
        final boolean completed;
        final LoadState loadState;
        final Double inputLoadValue;
        final MassUnit inputLoadUnit;

        TransferSetInput(Double weightKg, Integer reps, Integer durationSeconds,
                Double distanceMeters, Double assistedWeightKg, Double addedWeightKg,
                Integer rir, Integer restSeconds, boolean completed, LoadState loadState,
                Double inputLoadValue, MassUnit inputLoadUnit) {
            this.weightKg = weightKg;
            this.reps = reps;
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
            this.assistedWeightKg = assistedWeightKg;
            this.addedWeightKg = addedWeightKg;
            this.rir = rir;
            this.restSeconds = restSeconds;
            this.completed = completed;
            this.loadState = loadState;
            this.inputLoadValue = inputLoadValue;
            this.inputLoadUnit = inputLoadUnit;
        }

        static TransferSetInput from(WorkoutTransferCodec.SetData set) {
            return new TransferSetInput(set.weightKg, set.actualReps, set.durationSeconds,
                    set.distanceMeters, set.assistedWeightKg, set.addedWeightKg, set.rir,
                    set.restSeconds, set.isCompleted, LoadState.fromId(set.loadState),
                    set.inputLoadValue, MassUnit.parse(set.inputLoadUnit));
        }

        static TransferSetInput from(FleekCsvImporter.SetData set) {
            return new TransferSetInput(set.weightKg, set.reps, set.durationSeconds,
                    set.distanceMeters, set.assistedWeightKg, set.addedWeightKg, null,
                    null, true, null, null, null);
        }
    }
}
