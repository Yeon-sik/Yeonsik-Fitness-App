package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter;
import com.yeonsik.fitnessapp.exercise.LoadState;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseFamily;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class FitnessRepositoryLoadStateTest {
    private static final String DATABASE_PREFIX = "repository_load_state_";
    private static final String USER_ID = "load-state-test-user";

    @Test
    public void storesLoadStateOnEachSetAndCalculatesOnlyExternalOrAddedVolume() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.createSession(
                    "2026-08-30", "Load state", "strength", "", "", ""
            );
            String exerciseId = addMasterExercise(repository, recordId,
                    "chest_bodyweight_chest_dip");
            List<LoadState> allowed = repository.allowedLoadStatesForExercise(exerciseId);
            assertTrue(allowed.contains(LoadState.BODYWEIGHT));
            assertTrue(allowed.contains(LoadState.ADDED_WEIGHT));
            assertTrue(allowed.contains(LoadState.BAND_ASSISTED));

            String setId = repository.addTypedSet(
                    recordId,
                    exerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            null, 8, null, null, null, null, null, true,
                            LoadState.BAND_ASSISTED
                    )
            );
            FitnessRepository.SessionSetEntry set = repository.setsForExercise(exerciseId).get(0);
            assertEquals(setId, set.id);
            assertEquals(LoadState.BAND_ASSISTED, set.loadState);
            assertEquals(0d, set.weightKg, 0.001d);
            assertEquals(0d, set.addedWeightKg, 0.001d);
            assertEquals(0d, repository.sessionMetrics(recordId).totalVolumeKg, 0.001d);

            repository.updateTypedSet(
                    recordId,
                    setId,
                    new FitnessRepository.SetInput(
                            99d, 5, null, 3d, 10d, null, null, true,
                            LoadState.ADDED_WEIGHT
                    )
            );
            set = repository.setsForExercise(exerciseId).get(0);
            assertEquals(LoadState.ADDED_WEIGHT, set.loadState);
            assertEquals(0d, set.weightKg, 0.001d);
            assertEquals(10d, set.addedWeightKg, 0.001d);
            assertEquals(0d, set.assistedWeightKg, 0.001d);
            assertEquals(50d, repository.sessionMetrics(recordId).totalVolumeKg, 0.001d);

            repository.updateTypedSet(
                    recordId,
                    setId,
                    new FitnessRepository.SetInput(
                            99d, 5, null, 3d, 10d, null, null, true,
                            LoadState.BODYWEIGHT
                    )
            );
            set = repository.setsForExercise(exerciseId).get(0);
            assertEquals(LoadState.BODYWEIGHT, set.loadState);
            assertEquals(0d, set.weightKg, 0.001d);
            assertEquals(0d, set.assistedWeightKg, 0.001d);
            assertEquals(0d, set.addedWeightKg, 0.001d);
            assertEquals(0d, repository.sessionMetrics(recordId).totalVolumeKg, 0.001d);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void rejectsDisallowedStatesAndIncompleteCompletedLoad() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.createSession(
                    "2026-08-30", "Load state", "strength", "", "", ""
            );
            String dipId = addMasterExercise(repository, recordId,
                    "chest_bodyweight_chest_dip");
            expectIllegalArgument(() -> repository.addTypedSet(
                    recordId,
                    dipId,
                    1,
                    new FitnessRepository.SetInput(
                            null, 8, null, null, null, null, null, true,
                            LoadState.EXTERNAL_LOAD
                    )
            ));

            String benchId = addMasterExercise(repository, recordId,
                    "chest_dumbbell_decline_bench_press");
            expectIllegalArgument(() -> repository.addTypedSet(
                    recordId,
                    benchId,
                    1,
                    new FitnessRepository.SetInput(
                            null, 8, null, null, null, null, null, true,
                            LoadState.EXTERNAL_LOAD
                    )
            ));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void rejectsZeroAddedWeightAndDefaultsImplicitZeroToBodyweight() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.createSession(
                    "2026-08-30", "Load state", "strength", "", "", ""
            );
            String exerciseId = addMasterExercise(
                    repository, recordId, "chest_bodyweight_weighted_push_up"
            );

            expectIllegalArgument(() -> repository.addTypedSet(
                    recordId,
                    exerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            null, 8, null, null, 0d, null, null, true,
                            LoadState.ADDED_WEIGHT
                    )
            ));

            repository.addTypedSet(
                    recordId,
                    exerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            null, 8, null, null, 0d, null, null, true
                    )
            );
            assertEquals(
                    LoadState.BODYWEIGHT,
                    repository.setsForExercise(exerciseId).get(0).loadState
            );
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void persistsExternalAssistedAndBandResistedStatesPerSet() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.createSession(
                    "2026-08-30", "Load state", "strength", "", "", ""
            );

            String externalExerciseId = addMasterExercise(
                    repository, recordId, "chest_dumbbell_decline_bench_press"
            );
            repository.addTypedSet(
                    recordId,
                    externalExerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            20d, 8, null, null, null, null, null, true,
                            LoadState.EXTERNAL_LOAD
                    )
            );
            FitnessRepository.SessionSetEntry external = repository
                    .setsForExercise(externalExerciseId).get(0);
            assertEquals(LoadState.EXTERNAL_LOAD, external.loadState);
            assertEquals(20d, external.weightKg, 0.001d);
            assertEquals(0d, external.assistedWeightKg, 0.001d);
            assertEquals(0d, external.addedWeightKg, 0.001d);

            String assistedExerciseId = addMasterExercise(
                    repository, recordId, "chest_bodyweight_chest_dip"
            );
            repository.addTypedSet(
                    recordId,
                    assistedExerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            null, 8, null, 12d, null, null, null, true,
                            LoadState.ASSISTED
                    )
            );
            FitnessRepository.SessionSetEntry assisted = repository
                    .setsForExercise(assistedExerciseId).get(0);
            assertEquals(LoadState.ASSISTED, assisted.loadState);
            assertEquals(0d, assisted.weightKg, 0.001d);
            assertEquals(12d, assisted.assistedWeightKg, 0.001d);
            assertEquals(0d, assisted.addedWeightKg, 0.001d);

            String bandExerciseId = addMasterExercise(
                    repository, recordId, "barbell_seal_row"
            );
            repository.addTypedSet(
                    recordId,
                    bandExerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            80d, 8, null, 12d, 10d, null, null, true,
                            LoadState.BAND_RESISTED
                    )
            );
            FitnessRepository.SessionSetEntry band = repository
                    .setsForExercise(bandExerciseId).get(0);
            assertEquals(LoadState.BAND_RESISTED, band.loadState);
            assertEquals(0d, band.weightKg, 0.001d);
            assertEquals(0d, band.assistedWeightKg, 0.001d);
            assertEquals(0d, band.addedWeightKg, 0.001d);
            assertEquals(320d, repository.sessionMetrics(recordId).totalVolumeKg, 0.001d);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void persistsApprovedPresetIdentityAndRestoresFromSavedSnapshot() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.createSession(
                    "2026-08-30", "Approved preset", "strength", "", "", ""
            );
            String exerciseId = addMasterExercise(
                    repository, recordId, "trap_bar_deadlift_high_handles"
            );
            FitnessRepository.SessionExerciseEntry saved = repository
                    .sessionExerciseEntries(recordId).get(0);
            assertEquals("트랩바 데드리프트 - 하이 핸들", saved.name);
            assertNotNull(saved.familyIdentity);
            assertEquals("deadlift", saved.familyIdentity.familyId);
            assertEquals("trap_bar_deadlift_high_handles", saved.familyIdentity.presetId);
            assertEquals(
                    "{\"equipment\":\"trap_bar\",\"handleHeight\":\"high\",\"stance\":\"standard\"}",
                    saved.familyIdentity.canonicalVariantKey
            );

            repository.addTypedSet(
                    recordId,
                    exerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            60d, 5, null, null, null, null, null, true,
                            LoadState.EXTERNAL_LOAD
                    )
            );
            assertEquals(300d, repository.sessionMetrics(recordId).totalVolumeKg, 0.001d);

            ContentValues legacySnapshotRead = new ContentValues();
            legacySnapshotRead.put("exercise_id", "runtime-id-no-longer-present");
            helper.getWritableDatabase().update(
                    "workout_exercises",
                    legacySnapshotRead,
                    "id = ?",
                    new String[]{exerciseId}
            );

            FitnessRepository.SessionExerciseEntry restored = repository
                    .sessionExerciseEntries(recordId).get(0);
            assertEquals(saved.name, restored.name);
            assertNotNull(restored.familyIdentity);
            assertEquals("deadlift", restored.familyIdentity.familyId);
            assertEquals("trap_bar_deadlift_high_handles", restored.familyIdentity.presetId);
            assertEquals(
                    saved.familyIdentity.canonicalVariantKey,
                    restored.familyIdentity.canonicalVariantKey
            );
            assertEquals(
                    saved.familyIdentity.visualVariantKey,
                    restored.familyIdentity.visualVariantKey
            );
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void keepsCanonicalAliasRowsInOneHistoryAndPerformanceBucket() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String previousRecordId = repository.createSession(
                    "2026-08-29", "Previous", "strength", "", "", ""
            );
            String previousExerciseId = addMasterExercise(
                    repository, previousRecordId, "chest_machine_pec_deck_fly"
            );
            repository.addTypedSet(
                    previousRecordId,
                    previousExerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            40d, 5, null, null, null, null, null, true,
                            LoadState.EXTERNAL_LOAD
                    )
            );

            ContentValues oldAlias = new ContentValues();
            oldAlias.put("exercise_id", "chest_machine_chest_fly");
            oldAlias.putNull("family_id");
            oldAlias.putNull("preset_id");
            oldAlias.putNull("canonical_variant_key");
            oldAlias.putNull("visual_variant_key");
            helper.getWritableDatabase().update(
                    "workout_exercises",
                    oldAlias,
                    "id = ?",
                    new String[]{previousExerciseId}
            );

            String currentRecordId = repository.createSession(
                    "2026-08-30", "Current", "strength", "", "", ""
            );
            addMasterExercise(repository, currentRecordId, "chest_machine_pec_deck_fly");
            FitnessRepository.ExerciseBests bests = repository.exerciseBests(
                    "chest_machine_pec_deck_fly",
                    "머신 체스트 플라이",
                    currentRecordId
            );
            assertEquals(40d, bests.maxWeightKg, 0.001d);
            assertEquals(200d, bests.bestSessionVolumeKg, 0.001d);
            assertEquals(1, bests.sessionCount);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void preservesRuntimeSubPartWhenSavingAReusableRoutine() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            RuntimeExercisePreset preset = repository.familyCatalog()
                    .runtimeCatalog()
                    .presetForStorageExerciseId("back_bodyweight_pull_up");
            assertNotNull(preset);
            RoutineRepository routines = new RoutineRepository(helper, USER_ID);
            String routineId = routines.createRoutine(
                    "Runtime routine",
                    Arrays.asList(ExerciseMasterAdapter.toRoutineExercise(preset))
            );
            assertNotNull(routineId);
            List<RoutineExerciseInstance> exercises = routines.routineExercises(routineId);
            assertEquals(1, exercises.size());
            assertEquals("풀업", exercises.get(0).nameKo);
            assertEquals("광배근", exercises.get(0).primarySubPart);
            assertNotNull(exercises.get(0).familyIdentity);
            assertEquals("pull_up", exercises.get(0).familyIdentity.familyId);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void replacesWorkoutExerciseAndKeepsExistingSetProgress() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.createSession(
                    "2026-08-30", "Replace exercise", "strength", "", "", ""
            );
            String pullUpId = addMasterExercise(
                    repository, recordId, "back_bodyweight_pull_up"
            );
            String bodyweightSetId = repository.addTypedSet(
                    recordId,
                    pullUpId,
                    1,
                    new FitnessRepository.SetInput(
                            null, 8, null, null, null, null, null, true,
                            LoadState.BODYWEIGHT
                    )
            );
            String addedWeightSetId = repository.addTypedSet(
                    recordId,
                    pullUpId,
                    2,
                    new FitnessRepository.SetInput(
                            null, 6, null, null, 5d, null, null, true,
                            LoadState.ADDED_WEIGHT
                    )
            );

            RuntimeExercisePreset assistedPullUp = repository.familyCatalog()
                    .runtimeCatalog()
                    .presetForStorageExerciseId("back_machine_assisted_pull_up");
            assertNotNull(assistedPullUp);
            assertTrue(repository.replaceExerciseFromMaster(
                    recordId,
                    pullUpId,
                    ExerciseMasterAdapter.toRoutineExercise(assistedPullUp)
            ));

            List<FitnessRepository.SessionExerciseEntry> exercises =
                    repository.sessionExerciseEntries(recordId);
            assertEquals(1, exercises.size());
            FitnessRepository.SessionExerciseEntry replaced = exercises.get(0);
            assertEquals(pullUpId, replaced.id);
            assertEquals("back_machine_assisted_pull_up", replaced.exerciseId);
            assertEquals("어시스트 풀업", replaced.name);
            assertEquals("assisted_weight_reps", replaced.recordType);
            assertNotNull(replaced.familyIdentity);
            assertEquals("pull_up", replaced.familyIdentity.familyId);
            assertEquals("back_machine_assisted_pull_up", replaced.familyIdentity.presetId);

            List<FitnessRepository.SessionSetEntry> sets = repository.setsForExercise(pullUpId);
            assertEquals(2, sets.size());
            assertEquals(bodyweightSetId, sets.get(0).id);
            assertEquals(8, sets.get(0).actualReps);
            assertTrue(sets.get(0).isCompleted);
            assertEquals(LoadState.BODYWEIGHT, sets.get(0).loadState);
            assertEquals(addedWeightSetId, sets.get(1).id);
            assertEquals(6, sets.get(1).actualReps);
            assertTrue(sets.get(1).isCompleted);
            assertEquals(LoadState.ADDED_WEIGHT, sets.get(1).loadState);
            assertEquals(5d, sets.get(1).addedWeightKg, 0.001d);
            assertEquals(30d, repository.sessionMetrics(recordId).totalVolumeKg, 0.001d);

            RuntimeExercisePreset differentFamilyPreset = null;
            for (RuntimeExerciseFamily family : repository.familyCatalog().runtimeCatalog().families) {
                if (!"pull_up".equals(family.familyId) && !family.presets.isEmpty()) {
                    differentFamilyPreset = family.presets.get(0);
                    break;
                }
            }
            assertNotNull(differentFamilyPreset);
            assertFalse(repository.replaceExerciseFromMaster(
                    recordId,
                    pullUpId,
                    ExerciseMasterAdapter.toRoutineExercise(differentFamilyPreset)
            ));
            assertEquals("back_machine_assisted_pull_up",
                    repository.sessionExerciseEntries(recordId).get(0).exerciseId);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void readsLegacyNullLoadStateWithoutRewritingTheHistoricalRow() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.createSession(
                    "2026-08-30", "Legacy load state", "strength", "", "", ""
            );
            String exerciseId = addMasterExercise(repository, recordId,
                    "chest_bodyweight_chest_dip");
            String setId = repository.addTypedSet(
                    recordId,
                    exerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            null, null, null, null, null, null, null, false
                    )
            );

            ContentValues legacy = new ContentValues();
            legacy.putNull("load_state");
            legacy.putNull("weight_kg");
            legacy.put("added_weight_kg", 7d);
            legacy.put("actual_reps", 4);
            legacy.put("target_reps", 4);
            legacy.put("volume_kg", 28d);
            legacy.put("is_completed", 1);
            helper.getWritableDatabase().update(
                    "workout_sets",
                    legacy,
                    "id = ?",
                    new String[]{setId}
            );

            FitnessRepository.SessionSetEntry read = repository.setsForExercise(exerciseId).get(0);
            assertEquals(LoadState.ADDED_WEIGHT, read.loadState);
            assertEquals(7d, read.addedWeightKg, 0.001d);
            assertEquals(0d, read.weightKg, 0.001d);
            assertNull(scalarNullable(
                    helper.getReadableDatabase(),
                    "SELECT load_state FROM workout_sets WHERE id = ?",
                    setId
            ));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static String addMasterExercise(
            FitnessRepository repository,
            String recordId,
            String presetId
    ) {
        RuntimeExercisePreset preset = repository.familyCatalog()
                .runtimeCatalog()
                .presetForStorageExerciseId(presetId);
        assertNotNull(preset);
        RoutineExercise exercise = ExerciseMasterAdapter.toRoutineExercise(preset);
        String exerciseId = repository.addExerciseFromMaster(recordId, exercise);
        assertNotNull(exerciseId);
        return exerciseId;
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static IsolatedDatabaseContext isolatedContext() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        return context;
    }

    private static String scalarNullable(SQLiteDatabase database, String sql, String argument) {
        try (Cursor cursor = database.rawQuery(sql, new String[]{argument})) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return cursor.getString(0);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private IsolatedDatabaseContext(Context base) {
            super(base);
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(DATABASE_PREFIX + name);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name,
                int mode,
                SQLiteDatabase.CursorFactory factory
        ) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name,
                int mode,
                SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler
        ) {
            return SQLiteDatabase.openDatabase(
                    getDatabasePath(name).getPath(),
                    factory,
                    SQLiteDatabase.CREATE_IF_NECESSARY,
                    errorHandler
            );
        }

        @Override
        public boolean deleteDatabase(String name) {
            return SQLiteDatabase.deleteDatabase(getDatabasePath(name));
        }
    }
}
