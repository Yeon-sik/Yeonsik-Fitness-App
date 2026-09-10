package com.yeonsik.fitnessapp.feature.workout.data;

import android.content.Context;

import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner;
import com.yeonsik.fitnessapp.core.database.WorkoutExercisesRoomEntity;
import com.yeonsik.fitnessapp.core.database.WorkoutRecordsRoomEntity;
import com.yeonsik.fitnessapp.core.database.WorkoutRoomDao;
import com.yeonsik.fitnessapp.core.database.WorkoutSetsRoomEntity;
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
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutInterchangeApi;
import com.yeonsik.fitnessapp.integration.workout.WorkoutInterchangeResult;

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

/** Room-backed persistence for workout transfer and FLEEK imports. */
public final class WorkoutInterchangeRepository implements WorkoutInterchangeApi {
    private static final String DEVICE_ID = "android-local";

    private final WorkoutRoomDao workoutDao;
    private final RoomTransactionRunner transactionRunner;
    private final ExerciseFamilyCatalog familyCatalog;

    public WorkoutInterchangeRepository(
            FitnessRoomDatabase roomDatabase,
            Context context,
            RoomTransactionRunner transactionRunner
    ) {
        if (roomDatabase == null || context == null || transactionRunner == null) {
            throw new IllegalArgumentException("운동 전송 저장소를 초기화할 수 없습니다.");
        }
        this.workoutDao = roomDatabase.workoutRoomDao();
        this.transactionRunner = transactionRunner;
        this.familyCatalog = ExerciseFamilyCatalog.load(context);
    }

    public WorkoutInterchangeRepository(FitnessRoomDatabase roomDatabase, Context context) {
        this(roomDatabase, context, new RoomTransactionRunner(roomDatabase));
    }

    @Override
    public WorkoutTransferCodec.Document exportTransfer(String ownerId) {
        String normalizedOwnerId = requireOwner(ownerId);
        recalculateCanonicalVolumes(normalizedOwnerId);
        List<WorkoutTransferCodec.Session> sessions = new ArrayList<>();
        for (WorkoutRecordsRoomEntity record : workoutDao.transferRecords(normalizedOwnerId)) {
            String metadata = record.getMetadata();
            sessions.add(new WorkoutTransferCodec.Session(
                    emptyToDefault(record.getSourceApp(), "fitness"),
                    record.getId(),
                    record.getDate(),
                    record.getExerciseName(),
                    record.getWorkoutType(),
                    record.getCategory(),
                    nullableInt(record.getDurationSeconds()),
                    metadataValue(metadata, "status"),
                    metadataValue(metadata, "started_at"),
                    metadataValue(metadata, "ended_at"),
                    emptyToNull(metadataValue(metadata, "memo")),
                    exportExercises(normalizedOwnerId, record.getId())
            ));
        }
        return new WorkoutTransferCodec.Document(
                WorkoutTransferCodec.V2,
                "yeonsik.fitnessapp",
                now(),
                sessions
        );
    }

    private List<WorkoutTransferCodec.Exercise> exportExercises(
            String ownerId,
            String recordId
    ) {
        List<WorkoutTransferCodec.Exercise> exercises = new ArrayList<>();
        for (WorkoutExercisesRoomEntity exercise
                : workoutDao.visibleExercises(recordId, ownerId)) {
            ExerciseFamilyIdentity identity = identityForRow(
                    exercise.getExerciseId(),
                    exercise.getExerciseNameSnapshot(),
                    exercise.getFamilyId(),
                    exercise.getPresetId(),
                    exercise.getCanonicalVariantKey(),
                    exercise.getVisualVariantKey(),
                    exercise.getRecordType()
            );
            exercises.add(new WorkoutTransferCodec.Exercise(
                    exercise.getExerciseId(),
                    identity == null ? null : identity.presetId,
                    identity == null ? null : identity.canonicalPresetId,
                    identity == null ? null : identity.legacyExerciseId,
                    canonicalName(exercise.getExerciseNameSnapshot(), identity),
                    safeInt(exercise.getOrderIndex()),
                    exercise.getRecordType(),
                    exercise.getUiPart(),
                    exercise.getEquipmentSnapshot(),
                    exportSets(ownerId, exercise.getId())
            ));
        }
        return exercises;
    }

    private List<WorkoutTransferCodec.SetData> exportSets(String ownerId, String exerciseId) {
        List<WorkoutTransferCodec.SetData> sets = new ArrayList<>();
        for (WorkoutSetsRoomEntity set : workoutDao.visibleSets(exerciseId, ownerId)) {
            String inputUnit = set.getInputLoadUnit();
            if (inputUnit != null && MassUnit.parse(inputUnit) == null) {
                throw new IllegalStateException("저장된 입력 중량 단위를 해석하지 못했습니다.");
            }
            sets.add(new WorkoutTransferCodec.SetData(
                    set.getId(),
                    safeInt(set.getSetIndex()),
                    nullableInt(set.getTargetReps()),
                    nullableInt(set.getActualReps()),
                    set.getWeightKg(),
                    set.getVolumeKg(),
                    nullableInt(set.getDurationSeconds()),
                    set.getDistanceMeters(),
                    nullableInt(set.getRestSeconds()),
                    set.getAssistedWeightKg(),
                    set.getAddedWeightKg(),
                    set.getLoadState(),
                    set.isCompleted() == 1L,
                    nullableInt(set.getRpe()),
                    nullableInt(set.getRir()),
                    set.getMemo(),
                    set.getInputLoadValue(),
                    inputUnit
            ));
        }
        return sets;
    }

    @Override
    public WorkoutInterchangeResult importTransfer(
            String ownerId,
            WorkoutTransferCodec.Document document
    ) {
        String normalizedOwnerId = requireOwner(ownerId);
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

        return transactionRunner.call(() -> {
            WorkoutInterchangeResult result = new WorkoutInterchangeResult();
            int presetIndex = 0;
            for (WorkoutTransferCodec.Session session : document.sessions) {
                if (hasImportedTransfer(
                        normalizedOwnerId,
                        session.sourceApp,
                        session.sourceRecordId
                )) {
                    result.skippedDuplicateSessions++;
                    presetIndex += session.exercises.size();
                    continue;
                }

                String date = requireDate(session.date);
                String recordId = newId();
                String importedAt = now();
                Integer durationSeconds = session.durationSeconds == null
                        ? computeDurationSeconds(date, session.startedAt, session.endedAt)
                        : session.durationSeconds;
                workoutDao.insertRecord(new WorkoutRecordsRoomEntity(
                        recordId,
                        normalizedOwnerId,
                        date,
                        emptyToDefault(session.workoutType, "strength"),
                        emptyToDefault(
                                session.category,
                                session.exercises.isEmpty()
                                        ? "other"
                                        : emptyToDefault(session.exercises.get(0).uiPart, "other")
                        ),
                        emptyToDefault(session.title, "Imported workout"),
                        longValue(durationSeconds),
                        0d,
                        null,
                        importedAt,
                        1L,
                        importedAt,
                        "Workout Transfer JSON import",
                        importedAt,
                        null,
                        DEVICE_ID,
                        session.sourceApp,
                        "fitness",
                        transferMetadata(session, document.formatVersion),
                        (long) FitnessRecordContract.VERSION
                ));

                int fallbackOrder = 1;
                for (WorkoutTransferCodec.Exercise exercise : session.exercises) {
                    RuntimeExercisePreset preset = presets.get(presetIndex++);
                    ExerciseFamilyIdentity identity = familyCatalog.identityForPreset(preset);
                    String recordType = FitnessRecordContract.normalizeRecordType(
                            preset.recordType
                    );
                    String workoutExerciseId = newId();
                    workoutDao.insertExercise(new WorkoutExercisesRoomEntity(
                            workoutExerciseId,
                            normalizedOwnerId,
                            recordId,
                            exercise.orderIndex == null
                                    ? fallbackOrder
                                    : (long) Math.max(1, exercise.orderIndex),
                            emptyToDefault(preset.storageExerciseId, exercise.exerciseId),
                            canonicalName(preset.displayName(), identity),
                            normalizeCategory(emptyToDefault(
                                    identity == null ? null : identity.defaultUiPart,
                                    preset.defaultUiPart
                            )),
                            emptyToDefault(
                                    preset.primarySubPartNameKo,
                                    exercise.uiPart == null ? "" : exercise.uiPart
                            ),
                            preset.equipmentNameKo == null
                                    ? exercise.equipment
                                    : preset.equipmentNameKo,
                            recordType,
                            identity == null ? null : identity.familyId,
                            identity == null ? null : identity.presetId,
                            identity == null ? null : identity.canonicalVariantKey,
                            identity == null ? null : identity.visualVariantKey,
                            null,
                            importedAt,
                            importedAt,
                            null,
                            DEVICE_ID,
                            (long) FitnessRecordContract.VERSION
                    ));
                    fallbackOrder++;
                    result.importedExercises++;

                    for (WorkoutTransferCodec.SetData set : exercise.sets) {
                        TransferSetInput input = TransferSetInput.from(set);
                        LoadState state = resolveLoadState(recordType, input, identity);
                        validateSetInput(recordType, input, identity, state);
                        double volume = volume(identity, recordType, input, state);
                        if (set.volumeKg != null && !massValuesMatch(set.volumeKg, volume)) {
                            throw new IllegalArgumentException(
                                    "전송된 volumeKg가 canonical 중량 계산과 일치하지 않습니다."
                            );
                        }
                        workoutDao.insertSet(buildTransferSetEntity(
                                normalizedOwnerId,
                                workoutExerciseId,
                                importedAt,
                                set,
                                input,
                                state,
                                volume
                        ));
                        result.importedSets++;
                    }
                }
                result.importedSessions++;
            }
            return result;
        });
    }

    @Override
    public WorkoutInterchangeResult importFleek(
            String ownerId,
            FleekCsvImporter.ImportPlan plan
    ) {
        String normalizedOwnerId = requireOwner(ownerId);
        if (plan == null || plan.sessions.isEmpty()) {
            throw new IllegalArgumentException("가져올 FLEEK 운동 기록이 없습니다.");
        }

        return transactionRunner.call(() -> {
            WorkoutInterchangeResult result = new WorkoutInterchangeResult();
            result.skippedRows = plan.skippedRows;
            for (FleekCsvImporter.SessionData session : plan.sessions) {
                if (hasImportedFleek(normalizedOwnerId, session.sourceKey)) {
                    result.skippedDuplicateSessions++;
                    continue;
                }

                String recordId = newId();
                String importedAt = now();
                double totalVolume = importedVolume(session);
                workoutDao.insertRecord(new WorkoutRecordsRoomEntity(
                        recordId,
                        normalizedOwnerId,
                        session.date,
                        "strength",
                        firstCategory(session),
                        session.title,
                        (long) session.durationSeconds,
                        totalVolume,
                        null,
                        importedAt,
                        1L,
                        importedAt,
                        "FLEEK CSV import",
                        importedAt,
                        null,
                        DEVICE_ID,
                        "fitness",
                        "fitness",
                        fleekMetadata(session, totalVolume),
                        (long) FitnessRecordContract.VERSION
                ));

                int order = 1;
                for (FleekCsvImporter.ExerciseData exercise : session.exercises) {
                    ExerciseFamilyIdentity identity = familyCatalog
                            .identityForStorageExerciseId(exercise.exerciseId);
                    String workoutExerciseId = newId();
                    String recordType = FitnessRecordContract.normalizeRecordType(
                            exercise.recordType
                    );
                    workoutDao.insertExercise(new WorkoutExercisesRoomEntity(
                            workoutExerciseId,
                            normalizedOwnerId,
                            recordId,
                            (long) order++,
                            exercise.exerciseId,
                            canonicalName(exercise.name, identity),
                            exercise.uiPart,
                            exercise.primarySubPart,
                            exercise.equipment,
                            recordType,
                            identity == null ? null : identity.familyId,
                            identity == null ? null : identity.presetId,
                            identity == null ? null : identity.canonicalVariantKey,
                            identity == null ? null : identity.visualVariantKey,
                            null,
                            importedAt,
                            importedAt,
                            null,
                            DEVICE_ID,
                            (long) FitnessRecordContract.VERSION
                    ));
                    result.importedExercises++;

                    for (FleekCsvImporter.SetData set : exercise.sets) {
                        TransferSetInput input = TransferSetInput.from(set);
                        LoadState state = resolveLoadState(recordType, input, identity);
                        validateSetInput(recordType, input, identity, state);
                        double volume = volume(identity, recordType, input, state);
                        workoutDao.insertSet(buildFleekSetEntity(
                                normalizedOwnerId,
                                workoutExerciseId,
                                importedAt,
                                set,
                                input,
                                state,
                                volume
                        ));
                        result.importedSets++;
                        if (exercise.masterMatched) {
                            result.masterMatchedSets++;
                        }
                    }
                }
                result.importedSessions++;
            }
            return result;
        });
    }

    private WorkoutSetsRoomEntity buildTransferSetEntity(
            String ownerId,
            String exerciseId,
            String timestamp,
            WorkoutTransferCodec.SetData set,
            TransferSetInput input,
            LoadState state,
            double volume
    ) {
        StoredSetFields fields = explicitStorageFields(input, state);
        return new WorkoutSetsRoomEntity(
                newId(),
                ownerId,
                exerciseId,
                (long) Math.max(1, set.setIndex),
                longValue(set.targetReps),
                longValue(set.actualReps),
                fields.weightKg,
                volume,
                longValue(set.durationSeconds),
                set.distanceMeters,
                longValue(set.restSeconds),
                fields.assistedWeightKg,
                fields.addedWeightKg,
                fields.inputLoadValue,
                fields.inputLoadUnit,
                state == null ? null : state.id(),
                set.isCompleted ? 1L : 0L,
                longValue(set.rpe),
                longValue(set.rir),
                set.memo,
                timestamp,
                timestamp,
                null,
                DEVICE_ID,
                (long) FitnessRecordContract.VERSION
        );
    }

    private WorkoutSetsRoomEntity buildFleekSetEntity(
            String ownerId,
            String exerciseId,
            String timestamp,
            FleekCsvImporter.SetData set,
            TransferSetInput input,
            LoadState state,
            double volume
    ) {
        StoredSetFields fields = defaultStorageFields(input, state);
        return new WorkoutSetsRoomEntity(
                newId(),
                ownerId,
                exerciseId,
                (long) Math.max(1, set.setIndex),
                longValue(set.reps),
                longValue(set.reps),
                fields.weightKg,
                volume,
                longValue(set.durationSeconds),
                set.distanceMeters,
                null,
                fields.assistedWeightKg,
                fields.addedWeightKg,
                fields.inputLoadValue,
                fields.inputLoadUnit,
                state == null ? null : state.id(),
                1L,
                longValue(set.rpe),
                null,
                fleekSetMemo(set),
                timestamp,
                timestamp,
                null,
                DEVICE_ID,
                (long) FitnessRecordContract.VERSION
        );
    }

    private boolean hasImportedTransfer(String ownerId, String sourceApp, String sourceRecordId) {
        for (WorkoutRecordsRoomEntity record : workoutDao.recordsBySource(ownerId, sourceApp)) {
            if (sourceRecordId.equals(record.getId())) {
                return true;
            }
            String metadata = record.getMetadata();
            if (sourceApp.equals(metadataValue(metadata, "transfer_source_app"))
                    && sourceRecordId.equals(metadataValue(
                            metadata,
                            "transfer_source_record_id"
                    ))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasImportedFleek(String ownerId, String sourceKey) {
        for (WorkoutRecordsRoomEntity record : workoutDao.recordsBySource(ownerId, "fitness")) {
            if (sourceKey.equals(metadataValue(record.getMetadata(), "fleek_source_key"))
                    && record.getDeletedAt() == null) {
                return true;
            }
        }
        return false;
    }

    private RuntimeExercisePreset requireTransferPreset(WorkoutTransferCodec.Exercise exercise) {
        RuntimeExerciseCatalog catalog = familyCatalog.runtimeCatalog();
        RuntimeExercisePreset preset = null;
        preset = mergePreset(preset, catalog.presetForStorageExerciseId(exercise.exerciseId));
        preset = mergePreset(preset, catalog.presetForStorageExerciseId(exercise.canonicalExerciseId));
        preset = mergePreset(preset, catalog.preset(exercise.presetId));
        preset = mergePreset(preset, catalog.preset(exercise.canonicalPresetId));
        if (preset == null) {
            throw new IllegalArgumentException(
                    "운동 catalog ID를 찾지 못했습니다: " + exercise.exerciseId
            );
        }
        return preset;
    }

    private static RuntimeExercisePreset mergePreset(
            RuntimeExercisePreset current,
            RuntimeExercisePreset candidate
    ) {
        if (candidate == null) {
            return current;
        }
        if (current != null && !current.identityId().equals(candidate.identityId())) {
            throw new IllegalArgumentException("운동 전송의 catalog ID가 서로 다른 운동을 가리킵니다.");
        }
        return candidate;
    }

    private double importedVolume(FleekCsvImporter.SessionData session) {
        double total = 0d;
        for (FleekCsvImporter.ExerciseData exercise : session.exercises) {
            ExerciseFamilyIdentity identity = familyCatalog
                    .identityForStorageExerciseId(exercise.exerciseId);
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
            String recordType,
            TransferSetInput input,
            ExerciseFamilyIdentity identity
    ) {
        LoadState state = input.loadState;
        String normalized = FitnessRecordContract.normalizeRecordType(recordType);
        if (state == null
                && FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(normalized)
                && input.addedWeightKg != null
                && input.addedWeightKg > 0) {
            state = LoadState.ADDED_WEIGHT;
        }
        if (state == null && identity != null && identity.defaultLoadStateValue() != null) {
            state = identity.defaultLoadStateValue();
        }
        if (state == null
                && FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(normalized)) {
            state = LoadState.BODYWEIGHT;
        } else if (state == null
                && FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(normalized)) {
            state = LoadState.ASSISTED;
        } else if (state == null
                && (FitnessRecordContract.WEIGHT_REPS.equals(normalized)
                || FitnessRecordContract.WEIGHT_TIME.equals(normalized))) {
            state = LoadState.EXTERNAL_LOAD;
        }
        if (identity != null && state != null) {
            RuntimeExerciseFamily family = familyCatalog.runtimeCatalog().family(identity.familyId);
            if (family != null
                    && !family.allowedLoadStates.isEmpty()
                    && !family.supportsLoadState(state)) {
                throw new IllegalArgumentException(
                        "이 운동 family에서는 " + state.id() + " 상태를 사용할 수 없습니다."
                );
            }
        }
        return state;
    }

    private static void validateSetInput(
            String recordType,
            TransferSetInput input,
            ExerciseFamilyIdentity identity,
            LoadState state
    ) {
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
        validateProvenance(input, state);
        if (!input.completed) {
            return;
        }
        String normalized = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.TIME.equals(normalized)
                || FitnessRecordContract.WEIGHT_TIME.equals(normalized)) {
            requirePositive(input.durationSeconds, "시간");
        } else {
            requirePositive(input.reps, "횟수");
        }
        if (state == LoadState.EXTERNAL_LOAD) {
            requirePositive(input.weightKg, "중량");
        }
        if (state == LoadState.ADDED_WEIGHT) {
            requirePositive(input.addedWeightKg, "추가 중량");
        }
        if (state == LoadState.ASSISTED) {
            requirePositive(input.assistedWeightKg, "보조 중량");
        }
    }

    private static void validateProvenance(TransferSetInput input, LoadState state) {
        boolean hasValue = input.inputLoadValue != null;
        boolean hasUnit = input.inputLoadUnit != null;
        if (hasValue != hasUnit) {
            throw new IllegalArgumentException("입력 중량의 값과 단위를 함께 저장해야 합니다.");
        }
        validateNonNegative(input.inputLoadValue, "입력 중량");
        if (!hasValue) {
            return;
        }
        if (!isNumericState(state)) {
            throw new IllegalArgumentException("저항 상태가 중량을 저장하지 않습니다.");
        }
        Double canonical = canonicalLoad(input, state);
        if (canonical == null || !massValuesMatch(
                MassUnit.toKg(input.inputLoadValue, input.inputLoadUnit),
                canonical
        )) {
            throw new IllegalArgumentException("입력 중량과 canonical 중량이 일치하지 않습니다.");
        }
    }

    private double volume(
            ExerciseFamilyIdentity identity,
            String recordType,
            TransferSetInput input,
            LoadState state
    ) {
        RuntimeExercisePreset preset = presetForIdentity(identity);
        String laterality = preset == null ? variantLaterality(identity) : preset.laterality();
        int multiplier = preset == null
                ? ExerciseVolumeCalculator.DEFAULT_IMPLEMENT_MULTIPLIER
                : preset.implementMultiplier;
        return ExerciseVolumeCalculator.calculate(
                recordType,
                state,
                value(input.weightKg),
                value(input.addedWeightKg),
                integer(input.reps),
                laterality,
                multiplier
        );
    }

    private RuntimeExercisePreset presetForIdentity(ExerciseFamilyIdentity identity) {
        if (identity == null) {
            return null;
        }
        RuntimeExerciseCatalog catalog = familyCatalog.runtimeCatalog();
        RuntimeExercisePreset preset = catalog.preset(identity.presetId);
        if (preset == null) {
            preset = catalog.preset(identity.canonicalPresetId);
        }
        if (preset == null) {
            preset = catalog.presetForStorageExerciseId(identity.legacyExerciseId);
        }
        return preset;
    }

    private static String variantLaterality(ExerciseFamilyIdentity identity) {
        if (identity == null || identity.variantJson == null) {
            return null;
        }
        try {
            return new JSONObject(identity.variantJson).optString("laterality", null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void recalculateCanonicalVolumes(String ownerId) {
        List<VolumeUpdate> updates = new ArrayList<>();
        Map<String, Double> totals = new LinkedHashMap<>();
        for (WorkoutRoomDao.TransferSetRow row : workoutDao.transferSetRows(ownerId)) {
            String type = FitnessRecordContract.normalizeRecordType(row.getRecordType());
            ExerciseFamilyIdentity identity = identityForRow(
                    row.getExerciseId(),
                    row.getExerciseNameSnapshot(),
                    row.getFamilyId(),
                    row.getPresetId(),
                    row.getCanonicalVariantKey(),
                    row.getVisualVariantKey(),
                    type
            );
            double stored = row.getVolumeKg() == null ? 0d : row.getVolumeKg();
            LoadState state = readLoadState(
                    type,
                    identity,
                    row.getLoadState(),
                    row.getAddedWeightKg() == null ? 0d : row.getAddedWeightKg()
            );
            TransferSetInput input = new TransferSetInput(
                    row.getWeightKg(),
                    nullableInt(row.getActualReps()),
                    null,
                    null,
                    row.getAssistedWeightKg(),
                    row.getAddedWeightKg(),
                    null,
                    null,
                    row.isCompleted() == 1L,
                    state,
                    null,
                    null
            );
            double calculated = identity == null ? stored : volume(identity, type, input, state);
            if (!massValuesMatch(stored, calculated)) {
                updates.add(new VolumeUpdate(row.getId(), calculated));
            }
            if (row.isCompleted() == 1L) {
                totals.put(
                        row.getRecordId(),
                        totals.getOrDefault(row.getRecordId(), 0d) + calculated
                );
            }
        }

        String updatedAt = now();
        transactionRunner.run(() -> {
            for (VolumeUpdate update : updates) {
                workoutDao.updateTransferSetVolume(
                        update.id,
                        ownerId,
                        update.volume,
                        updatedAt
                );
            }
            for (Map.Entry<String, Double> total : totals.entrySet()) {
                workoutDao.updateRecordTotal(
                        total.getKey(),
                        ownerId,
                        total.getValue(),
                        updatedAt
                );
            }
        });
    }

    private ExerciseFamilyIdentity identityForRow(
            String exerciseId,
            String name,
            String familyId,
            String presetId,
            String canonicalKey,
            String visualKey,
            String recordType
    ) {
        ExerciseFamilyIdentity mapped = familyCatalog.identityForStorageExerciseId(exerciseId);
        if (mapped != null) {
            return mapped;
        }
        if (emptyToNull(familyId) == null) {
            return null;
        }
        String displayName = emptyToDefault(name, "Exercise");
        return new ExerciseFamilyIdentity(
                exerciseId,
                familyId,
                presetId,
                presetId,
                displayName,
                displayName,
                displayName,
                displayName,
                null,
                canonicalKey,
                visualKey,
                null,
                null,
                FitnessRecordContract.normalizeRecordType(recordType),
                null
        );
    }

    private LoadState readLoadState(
            String recordType,
            ExerciseFamilyIdentity identity,
            String stored,
            double addedWeightKg
    ) {
        LoadState state = LoadState.fromId(stored);
        if (state != null) {
            return state;
        }
        return resolveLoadState(
                recordType,
                new TransferSetInput(
                        null,
                        null,
                        null,
                        null,
                        null,
                        addedWeightKg > 0 ? addedWeightKg : null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null
                ),
                identity
        );
    }

    private static String transferMetadata(
            WorkoutTransferCodec.Session session,
            int formatVersion
    ) {
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

    private static String fleekMetadata(
            FleekCsvImporter.SessionData session,
            double volume
    ) {
        try {
            String endedAt = OffsetDateTime.parse(session.sourceTimestamp)
                    .plusSeconds(Math.max(0, session.durationSeconds))
                    .toString();
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
            throw new IllegalStateException("FLEEK 운동 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private static String fleekSetMemo(FleekCsvImporter.SetData set) {
        try {
            JSONObject object = new JSONObject().put("import_source", "fleek_csv");
            if (!set.setType.isEmpty()) {
                object.put("set_type", set.setType);
            }
            if (!set.gripType.isEmpty()) {
                object.put("grip_type", set.gripType);
            }
            return object.toString();
        } catch (Exception ignored) {
            return "FLEEK CSV";
        }
    }

    private static String firstCategory(FleekCsvImporter.SessionData session) {
        if (session.exercises.isEmpty()) {
            return "arms";
        }
        String category = session.exercises.get(0).uiPart;
        return emptyToNull(category) == null ? "arms" : category;
    }

    private static String normalizeCategory(String value) {
        switch (emptyToDefault(value, "chest").toLowerCase()) {
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

    private static String canonicalName(String fallback, ExerciseFamilyIdentity identity) {
        return identity == null || emptyToNull(identity.displayName()) == null
                ? emptyToDefault(fallback, "Exercise")
                : identity.displayName();
    }

    private static String metadataValue(String metadata, String key) {
        try {
            return new JSONObject(emptyToDefault(metadata, "{}")).optString(key, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isNumericState(LoadState state) {
        return state == LoadState.EXTERNAL_LOAD
                || state == LoadState.ADDED_WEIGHT
                || state == LoadState.ASSISTED;
    }

    private static Double canonicalLoad(TransferSetInput input, LoadState state) {
        if (state == LoadState.EXTERNAL_LOAD) {
            return input.weightKg;
        }
        if (state == LoadState.ADDED_WEIGHT) {
            return input.addedWeightKg;
        }
        if (state == LoadState.ASSISTED) {
            return input.assistedWeightKg;
        }
        if (input.weightKg != null) {
            return input.weightKg;
        }
        if (input.addedWeightKg != null) {
            return input.addedWeightKg;
        }
        return input.assistedWeightKg;
    }

    private static StoredSetFields explicitStorageFields(
            TransferSetInput input,
            LoadState state
    ) {
        StoredSetFields fields = loadFields(input, state);
        if (!isNumericState(state)
                || input.inputLoadValue == null
                || input.inputLoadUnit == null) {
            return fields;
        }
        fields.inputLoadValue = input.inputLoadValue;
        fields.inputLoadUnit = input.inputLoadUnit.id();
        return fields;
    }

    private static StoredSetFields defaultStorageFields(
            TransferSetInput input,
            LoadState state
    ) {
        StoredSetFields fields = loadFields(input, state);
        if (!isNumericState(state)) {
            return fields;
        }
        Double canonical = canonicalLoad(input, state);
        if (canonical == null) {
            return fields;
        }
        fields.inputLoadValue = input.inputLoadValue == null
                ? canonical
                : input.inputLoadValue;
        fields.inputLoadUnit = input.inputLoadUnit == null
                ? MassUnit.KG.id()
                : input.inputLoadUnit.id();
        return fields;
    }

    private static StoredSetFields loadFields(TransferSetInput input, LoadState state) {
        Double weightKg = null;
        Double assistedWeightKg = null;
        Double addedWeightKg = null;
        if (state == LoadState.EXTERNAL_LOAD) {
            weightKg = input.weightKg;
        } else if (state == LoadState.ADDED_WEIGHT) {
            addedWeightKg = input.addedWeightKg;
        } else if (state == LoadState.ASSISTED) {
            assistedWeightKg = input.assistedWeightKg;
        } else if (state != LoadState.BODYWEIGHT
                && state != LoadState.BAND_ASSISTED
                && state != LoadState.BAND_RESISTED) {
            weightKg = input.weightKg;
            assistedWeightKg = input.assistedWeightKg;
            addedWeightKg = input.addedWeightKg;
        }
        return new StoredSetFields(weightKg, assistedWeightKg, addedWeightKg);
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
        if (startTime == null || endTime == null) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(date);
            long seconds = Duration.between(
                    LocalDateTime.of(localDate, startTime),
                    LocalDateTime.of(localDate, endTime)
            ).getSeconds();
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
        if (candidate == null) {
            return null;
        }
        for (String pattern : new String[]{"H:mm", "HH:mm", "H:mm:ss", "HH:mm:ss"}) {
            try {
                return LocalTime.parse(candidate, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // Try the next legacy representation.
            }
        }
        return null;
    }

    private static String requireDate(String value) {
        String candidate = emptyToNull(value);
        if (candidate == null) {
            throw new IllegalArgumentException("날짜를 입력하세요.");
        }
        try {
            return LocalDate.parse(candidate).toString();
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("날짜를 YYYY-MM-DD 형식으로 입력하세요.");
        }
    }

    private static int nullableInt(Long value) {
        return value == null ? 0 : safeInt(value);
    }

    private static Integer nullableInteger(Long value) {
        return value == null ? null : safeInt(value);
    }

    private static Long longValue(Integer value) {
        return value == null ? null : value.longValue();
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

    private static double value(Double value) {
        return value == null ? 0d : value;
    }

    private static int integer(Integer value) {
        return value == null ? 0 : value;
    }

    private static String requireOwner(String ownerId) {
        String normalized = ownerId == null ? "" : ownerId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("운동 데이터 계정 식별자가 필요합니다.");
        }
        return normalized;
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static final class StoredSetFields {
        final Double weightKg;
        final Double assistedWeightKg;
        final Double addedWeightKg;
        Double inputLoadValue;
        String inputLoadUnit;

        StoredSetFields(
                Double weightKg,
                Double assistedWeightKg,
                Double addedWeightKg
        ) {
            this.weightKg = weightKg;
            this.assistedWeightKg = assistedWeightKg;
            this.addedWeightKg = addedWeightKg;
        }
    }

    private static final class VolumeUpdate {
        final String id;
        final double volume;

        VolumeUpdate(String id, double volume) {
            this.id = id;
            this.volume = volume;
        }
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

        TransferSetInput(
                Double weightKg,
                Integer reps,
                Integer durationSeconds,
                Double distanceMeters,
                Double assistedWeightKg,
                Double addedWeightKg,
                Integer rir,
                Integer restSeconds,
                boolean completed,
                LoadState loadState,
                Double inputLoadValue,
                MassUnit inputLoadUnit
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
            this.loadState = loadState;
            this.inputLoadValue = inputLoadValue;
            this.inputLoadUnit = inputLoadUnit;
        }

        static TransferSetInput from(WorkoutTransferCodec.SetData set) {
            return new TransferSetInput(
                    set.weightKg,
                    set.actualReps,
                    set.durationSeconds,
                    set.distanceMeters,
                    set.assistedWeightKg,
                    set.addedWeightKg,
                    set.rir,
                    set.restSeconds,
                    set.isCompleted,
                    LoadState.fromId(set.loadState),
                    set.inputLoadValue,
                    MassUnit.parse(set.inputLoadUnit)
            );
        }

        static TransferSetInput from(FleekCsvImporter.SetData set) {
            return new TransferSetInput(
                    set.weightKg,
                    set.reps,
                    set.durationSeconds,
                    set.distanceMeters,
                    set.assistedWeightKg,
                    set.addedWeightKg,
                    null,
                    null,
                    true,
                    null,
                    null,
                    null
            );
        }
    }
}
