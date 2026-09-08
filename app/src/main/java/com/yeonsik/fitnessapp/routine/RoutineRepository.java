package com.yeonsik.fitnessapp.routine;

import android.content.Context;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.RoutineEntity;
import com.yeonsik.fitnessapp.core.database.RoutineExerciseEntity;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.exercise.BodyPart;
import com.yeonsik.fitnessapp.exercise.EquipmentType;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi;
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseDraft;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Room DAO implementation for the routine definition owned by the routine feature. */
public final class RoutineRepository implements RoutineRepositoryApi {
    public static final int MAX_ROUTINES = 5;
    private static final String DEVICE_ID = "android-local";
    private static final String DEFAULT_ROUTINE_NAME = "나만의 루틴";

    private final FitnessRoomDatabase roomDatabase;
    private final ExerciseFamilyCatalog familyCatalog;
    private String userId;
    private String activeRoutineId;

    /** Compatibility fixture constructor; it still uses the app's Room provider. */
    @Deprecated
    public RoutineRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this(FitnessRoomDatabaseProvider.get(dbHelper.applicationContext()),
                dbHelper.applicationContext(), userId);
    }

    public RoutineRepository(FitnessRoomDatabase roomDatabase, Context context, String userId) {
        if (roomDatabase == null || context == null) {
            throw new IllegalArgumentException("RoutineRepository requires Room and context.");
        }
        this.roomDatabase = roomDatabase;
        this.familyCatalog = ExerciseFamilyCatalog.load(context);
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
        this.activeRoutineId = null;
    }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        if (AccountOwnerPolicy.shouldClaimLocalRows(this.userId, nextUserId)) {
            roomDatabase.runInTransaction(() -> {
                roomDatabase.routineRoomDao().claimRoutines(
                        SupabaseConfig.DEFAULT_USER_ID, nextUserId);
                roomDatabase.routineRoomDao().claimRoutineExercises(
                        SupabaseConfig.DEFAULT_USER_ID, nextUserId);
            });
        }
        this.userId = nextUserId;
        this.activeRoutineId = null;
    }

    public String ensureDefaultRoutine() {
        for (RoutineEntity routine : roomDatabase.routineRoomDao().visibleRoutines(userId)) {
            if (routine.isDefault()) {
                if (activeRoutineId == null) activeRoutineId = routine.getId();
                return routine.getId();
            }
        }

        String id = newId();
        String now = now();
        roomDatabase.routineRoomDao().insertRoutine(new RoutineEntity(
                id, userId, DEFAULT_ROUTINE_NAME, true, DEVICE_ID,
                now, now, null
        ));
        if (activeRoutineId == null) activeRoutineId = id;
        return id;
    }

    public List<RoutineSummary> routines() {
        List<RoutineSummary> rows = new ArrayList<>();
        for (RoutineRoomSummary row : summaryRows()) {
            rows.add(new RoutineSummary(row.id, row.name, row.exerciseCount));
        }
        return rows;
    }

    private List<RoutineRoomSummary> summaryRows() {
        List<RoutineRoomSummary> rows = new ArrayList<>();
        for (com.yeonsik.fitnessapp.core.database.RoutineRoomDao.RoutineSummaryProjection row
                : roomDatabase.routineRoomDao().visibleRoutineSummaries(userId)) {
            rows.add(new RoutineRoomSummary(row.getId(), row.getName(), row.getExerciseCount()));
        }
        return rows;
    }

    public boolean canCreateRoutine() {
        return routines().size() < MAX_ROUTINES;
    }

    public void selectRoutine(String routineId) {
        if (ownsRoutine(routineId)) activeRoutineId = routineId;
    }

    public String activeRoutineId() {
        if (activeRoutineId == null) activeRoutineId = ensureDefaultRoutine();
        return activeRoutineId;
    }

    @Override
    public String ensureActiveRoutine(AccountScope scope) {
        requireScope(scope);
        return activeRoutineId();
    }

    @Override
    public List<com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary> routines(
            AccountScope scope
    ) {
        requireScope(scope);
        List<com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary> result = new ArrayList<>();
        for (RoutineSummary row : routines()) {
            result.add(new com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary(
                    row.id, row.name, row.exerciseCount));
        }
        return result;
    }

    @Override
    public List<com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance> routineExercises(
            AccountScope scope, String routineId
    ) {
        requireScope(scope);
        if (!ownsRoutine(routineId)) return new ArrayList<>();
        List<com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance> result = new ArrayList<>();
        for (RoutineExerciseInstance row : routineExercises(routineId)) result.add(row.toFeatureModel());
        return result;
    }

    @Override
    public String activeRoutineId(AccountScope scope) {
        requireScope(scope);
        return activeRoutineId();
    }

    @Override
    public String routineName(AccountScope scope, String routineId) {
        requireScope(scope);
        return ownsRoutine(routineId) ? routineName(routineId) : null;
    }

    @Override
    public String createRoutine(AccountScope scope, String name) {
        requireScope(scope);
        return createRoutine(name, null);
    }

    @Override
    public boolean renameRoutine(AccountScope scope, String routineId, String name) {
        requireScope(scope);
        return renameRoutine(routineId, name);
    }

    @Override
    public String copyRoutine(AccountScope scope, String routineId, String name) {
        requireScope(scope);
        return copyRoutine(routineId, name);
    }

    @Override
    public boolean deleteRoutine(AccountScope scope, String routineId) {
        requireScope(scope);
        return deleteRoutine(routineId);
    }

    @Override
    public boolean selectRoutine(AccountScope scope, String routineId) {
        requireScope(scope);
        if (!ownsRoutine(routineId)) return false;
        activeRoutineId = routineId;
        return true;
    }

    @Override
    public boolean addExercise(AccountScope scope, String routineId, RoutineExerciseDraft exercise) {
        requireScope(scope);
        if (exercise == null || !ownsRoutine(routineId)) return false;
        EquipmentType equipmentType = EquipmentType.fromId(exercise.equipmentVariantId);
        if (equipmentType == null) equipmentType = EquipmentType.OTHER;
        RoutineExercise legacyExercise = new RoutineExercise(
                exercise.exerciseId,
                exercise.nameKo,
                exercise.nameEn,
                BodyPart.fromId(exercise.bodyPartId),
                equipmentType,
                exercise.equipmentVariantId,
                exercise.primarySubPart,
                exercise.recordType,
                exercise.familyIdentity
        );
        return addToRoutine(routineId, legacyExercise) != null;
    }

    public String activeRoutineName() {
        return routineName(activeRoutineId());
    }

    public List<RoutineExerciseInstance> activeRoutineExercises() {
        return routineExercises(activeRoutineId());
    }

    public String routineName(String routineId) {
        RoutineEntity routine = roomDatabase.routineRoomDao().visibleRoutine(routineId, userId);
        return routine == null
                ? DEFAULT_ROUTINE_NAME
                : emptyToDefault(routine.getName(), DEFAULT_ROUTINE_NAME);
    }

    public String createRoutine(String name, List<RoutineExercise> exercises) {
        if (!canCreateRoutine()) return null;
        String id = newId();
        String now = now();
        roomDatabase.routineRoomDao().insertRoutine(new RoutineEntity(
                id, userId, emptyToDefault(name, DEFAULT_ROUTINE_NAME), false,
                DEVICE_ID, now, now, null
        ));
        if (exercises != null) for (RoutineExercise exercise : exercises) addToRoutine(id, exercise);
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
        renameRoutine(ensureDefaultRoutine(), name);
    }

    public boolean renameRoutine(String routineId, String name) {
        if (!ownsRoutine(routineId)) return false;
        return roomDatabase.routineRoomDao().renameRoutine(
                routineId, userId, emptyToDefault(name, DEFAULT_ROUTINE_NAME), now()) > 0;
    }

    /** Copies only the routine definition; completed workout history is not copied. */
    public String copyRoutine(String sourceRoutineId, String name) {
        if (!canCreateRoutine() || !ownsRoutine(sourceRoutineId)) return null;
        String sourceName = routineName(sourceRoutineId);
        String targetId = newId();
        String now = now();
        List<RoutineExerciseEntity> source = roomDatabase.routineRoomDao()
                .visibleExercises(sourceRoutineId, userId);
        roomDatabase.runInTransaction(() -> {
            roomDatabase.routineRoomDao().insertRoutine(new RoutineEntity(
                    targetId, userId, emptyToDefault(name, sourceName + " 복사"), false,
                    DEVICE_ID, now, now, null
            ));
            for (RoutineExerciseEntity row : source) {
                roomDatabase.routineRoomDao().insertExercise(new RoutineExerciseEntity(
                        newId(), userId, targetId, row.getExerciseId(), row.getNameKo(),
                        row.getUiPart(), row.getPrimarySubPart(), row.getEquipment(),
                        row.getRecordType(), row.getFamilyId(), row.getPresetId(),
                        row.getCanonicalVariantKey(), row.getVisualVariantKey(), row.getOrderIndex(),
                        DEVICE_ID, now, now, null
                ));
            }
        });
        activeRoutineId = targetId;
        return targetId;
    }

    /** Soft-deletes a routine definition and its exercises without touching workouts. */
    public boolean deleteRoutine(String routineId) {
        if (!ownsRoutine(routineId)) return false;
        String timestamp = now();
        final int[] updated = {0};
        roomDatabase.runInTransaction(() -> {
            roomDatabase.routineRoomDao().tombstoneRoutineExercises(
                    routineId, userId, timestamp, timestamp);
            updated[0] = roomDatabase.routineRoomDao().tombstoneRoutine(
                    routineId, userId, timestamp, timestamp);
        });
        if (routineId.equals(activeRoutineId)) activeRoutineId = null;
        return updated[0] > 0;
    }

    public RoutineExerciseInstance addToDefaultRoutine(RoutineExercise exercise) {
        return addToRoutine(activeRoutineId(), exercise);
    }

    private RoutineExerciseInstance addToRoutine(String routineId, RoutineExercise exercise) {
        if (exercise == null) return null;
        if (!ownsRoutine(routineId)) throw new IllegalArgumentException("현재 계정의 루틴이 아닙니다.");

        int nextOrder = roomDatabase.routineRoomDao().nextExerciseOrder(routineId, userId);
        String id = newId();
        String timestamp = now();
        ExerciseFamilyIdentity identity = resolvedIdentity(exercise.familyIdentity, exercise.masterExerciseId);
        roomDatabase.routineRoomDao().insertExercise(new RoutineExerciseEntity(
                id, userId, routineId, exercise.masterExerciseId, displayName(exercise),
                exercise.bodyPart == null ? "" : exercise.bodyPart.labelKo(),
                emptyToDefault(exercise.primarySubPart, "세부 부위 없음"),
                exercise.equipmentType == null ? "기타" : exercise.equipmentType.labelKo(),
                FitnessRecordContract.normalizeRecordType(exercise.recordType),
                identity == null ? null : identity.familyId,
                identity == null ? null : identity.presetId,
                identity == null ? null : identity.canonicalVariantKey,
                identity == null ? null : identity.visualVariantKey,
                nextOrder, DEVICE_ID, timestamp, timestamp, null
        ));
        return new RoutineExerciseInstance(
                id, exercise.masterExerciseId, displayName(exercise),
                exercise.bodyPart == null ? "" : exercise.bodyPart.labelKo(),
                emptyToDefault(exercise.primarySubPart, "세부 부위 없음"),
                exercise.equipmentType == null ? "기타" : exercise.equipmentType.labelKo(),
                FitnessRecordContract.normalizeRecordType(exercise.recordType), nextOrder, identity
        );
    }

    public List<RoutineExerciseInstance> routineExercises(String routineId) {
        List<RoutineExerciseInstance> rows = new ArrayList<>();
        for (RoutineExerciseEntity row : roomDatabase.routineRoomDao().visibleExercises(routineId, userId)) {
            ExerciseFamilyIdentity identity = identityForRow(
                    row.getExerciseId(), row.getNameKo(), row.getFamilyId(), row.getPresetId(),
                    row.getCanonicalVariantKey(), row.getVisualVariantKey(), row.getRecordType()
            );
            rows.add(new RoutineExerciseInstance(
                    row.getId(), row.getExerciseId(),
                    identity == null ? row.getNameKo() : identity.displayName(),
                    row.getUiPart(), row.getPrimarySubPart(), row.getEquipment(), row.getRecordType(),
                    row.getOrderIndex(), identity
            ));
        }
        return rows;
    }

    private boolean ownsRoutine(String routineId) {
        return routineId != null && roomDatabase.routineRoomDao().visibleRoutine(routineId, userId) != null;
    }

    private void requireScope(AccountScope scope) {
        if (scope == null || !scope.getOwnerId().equals(userId)) {
            throw new IllegalStateException("The account changed while the routine operation was pending.");
        }
    }

    private ExerciseFamilyIdentity resolvedIdentity(ExerciseFamilyIdentity supplied, String legacyExerciseId) {
        return supplied != null ? supplied : familyCatalog.identityForStorageExerciseId(legacyExerciseId);
    }

    private ExerciseFamilyIdentity identityForRow(
            String legacyExerciseId,
            String nameKo,
            String familyId,
            String presetId,
            String canonicalVariantKey,
            String visualVariantKey,
            String recordType
    ) {
        ExerciseFamilyIdentity mapped = familyCatalog.identityForStorageExerciseId(legacyExerciseId);
        if (mapped != null || familyId == null || familyId.trim().isEmpty()) return mapped;
        return new ExerciseFamilyIdentity(
                legacyExerciseId, familyId, presetId, presetId, nameKo, nameKo, nameKo, nameKo,
                null, canonicalVariantKey, visualVariantKey, null, null, recordType, null
        );
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String displayName(RoutineExercise exercise) {
        if (exercise != null && exercise.familyIdentity != null) {
            String canonicalName = exercise.familyIdentity.displayName();
            if (canonicalName != null && !canonicalName.trim().isEmpty()) return canonicalName;
        }
        return emptyToDefault(exercise == null ? null : exercise.nameKo, "운동");
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    private static final class RoutineRoomSummary {
        final String id;
        final String name;
        final int exerciseCount;

        RoutineRoomSummary(String id, String name, int exerciseCount) {
            this.id = id;
            this.name = name;
            this.exerciseCount = exerciseCount;
        }
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
