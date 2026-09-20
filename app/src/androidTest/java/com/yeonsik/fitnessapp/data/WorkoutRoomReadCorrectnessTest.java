package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog;
import com.yeonsik.fitness.shared.feature.exercise.model.ExerciseFamilyIdentity;
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState;
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRepositoryImplementation;
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRoomStorage;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseBests;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseDetail;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseHistory;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseReplacement;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput;
import com.yeonsik.fitnessapp.test.FitnessRoomTestDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Regression coverage for the production Room-backed workout read path. */
@RunWith(AndroidJUnit4.class)
public final class WorkoutRoomReadCorrectnessTest {
    private static final String OWNER_ID = "room-read-owner";
    private static final String OTHER_OWNER_ID = "room-read-other-owner";

    @Test
    public void productionRoomReadsPreserveOwnershipCompletionDeletionAndStableIdentity() throws Exception {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            helper.getWritableDatabase();
            room = FitnessRoomTestDatabase.open(context);
            final FitnessRoomDatabase initialRoom = room;
            Fixture fixture = runDb(executor, () -> createFixture(initialRoom, context));

            room.close();
            room = null;

            ContentValues alias = new ContentValues();
            alias.put("exercise_id", "chest_machine_chest_fly");
            alias.putNull("family_id");
            alias.putNull("preset_id");
            alias.putNull("canonical_variant_key");
            alias.putNull("visual_variant_key");
            helper.getWritableDatabase().update(
                    "workout_exercises", alias, "id = ?", new String[]{fixture.aliasExerciseId}
            );

            ContentValues otherScope = new ContentValues();
            otherScope.put("scope", "nutrition");
            helper.getWritableDatabase().update(
                    "workout_records", otherScope, "id = ?", new String[]{fixture.otherScopeRecordId}
            );

            room = FitnessRoomTestDatabase.open(context);
            final FitnessRoomDatabase readRoom = room;
            WorkoutRepositoryImplementation repository =
                    new WorkoutRepositoryImplementation(readRoom, context);
            WorkoutExerciseDetail detail = runDb(executor, () -> repository.loadExerciseDetail(
                    new AccountScope(OWNER_ID), fixture.activeRecordId, fixture.activeExerciseId
            ));

            assertNotNull(detail);
            assertEquals(fixture.activeRecordId, detail.getRecordId());

            WorkoutExerciseHistory history = detail.getLastHistory();
            assertNotNull(history);
            assertEquals("2026-09-10", history.date);
            assertEquals(40d, history.sets.get(0).weightKg, 0.001d);

            Set<String> recentDates = new HashSet<>();
            detail.getRecentVolumes().forEach(point -> recentDates.add(point.getDate()));
            assertTrue(recentDates.contains("2026-09-10"));
            assertTrue(recentDates.contains("2026-09-09"));
            assertFalse(recentDates.contains("2026-09-12"));
            assertFalse(recentDates.contains("2026-09-13"));
            assertFalse(recentDates.contains("2026-09-14"));
            assertFalse(recentDates.contains("2026-09-15"));
            assertFalse(recentDates.contains("2026-09-11"));
            assertFalse(recentDates.contains("2026-09-08"));
            assertFalse(recentDates.contains("2026-09-07"));
            assertFalse(recentDates.contains("2026-09-06"));

            List<WorkoutExerciseBests> bests = detail.getBests();
            assertEquals(2, bests.size());
            WorkoutExerciseBests external = findBests(bests, LoadState.EXTERNAL_LOAD);
            WorkoutExerciseBests added = findBests(bests, LoadState.ADDED_WEIGHT);

            assertEquals(40d, external.maxWeightKg, 0.001d);
            assertEquals(5, external.repsAtMaxWeight);
            assertEquals(38d * (1d + 10d / 30d), external.bestE1rmKg, 0.001d);
            assertEquals(1, external.sessionCount);
            assertEquals(5d, added.maxWeightKg, 0.001d);
            assertEquals(6d, added.bestE1rmKg, 0.001d);
            assertEquals(1, added.sessionCount);
            assertFalse(external.performanceKey.equals(added.performanceKey));
            ExerciseFamilyIdentity canonical = ExerciseFamilyCatalog.load(context)
                    .identityForStorageExerciseId("chest_machine_pec_deck_fly");
            assertNotNull(canonical);
            assertEquals(
                    canonical.performanceKey(LoadState.EXTERNAL_LOAD).stableValue(),
                    external.performanceKey
            );
            assertEquals(
                    canonical.performanceKey(LoadState.ADDED_WEIGHT).stableValue(),
                    added.performanceKey
            );
        } finally {
            if (room != null) {
                room.close();
            }
            helper.close();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static Fixture createFixture(FitnessRoomDatabase room, Context context) {
        WorkoutRoomStorage storage = new WorkoutRoomStorage(
                room, context, new RoomTransactionRunner(room)
        );
        AccountScope owner = new AccountScope(OWNER_ID);
        AccountScope otherOwner = new AccountScope(OTHER_OWNER_ID);
        ExerciseFamilyCatalog catalog = ExerciseFamilyCatalog.load(context);
        ExerciseFamilyIdentity canonical = catalog.identityForStorageExerciseId(
                "chest_machine_pec_deck_fly"
        );
        ExerciseFamilyIdentity differentVariant = catalog.identityForStorageExerciseId(
                "chest_dumbbell_flat_fly"
        );
        assertNotNull(canonical);
        assertNotNull(differentVariant);
        assertEquals("chest_fly", canonical.familyId);
        assertEquals("chest_fly", differentVariant.familyId);
        assertFalse(canonical.canonicalVariantKey.equals(differentVariant.canonicalVariantKey));

        Fixture alias = createWorkout(
                storage, owner, "2026-09-10", "alias", canonical,
                LoadState.EXTERNAL_LOAD, 40d, null, 5
        );
        assertTrue(storage.addSet(
                owner, alias.recordId, alias.exerciseId, 2,
                input(38d, 10, LoadState.EXTERNAL_LOAD, null)
        ));

        createWorkout(
                storage, owner, "2026-09-09", "added", canonical,
                LoadState.ADDED_WEIGHT, null, 5d, 6
        );
        createWorkout(
                storage, owner, "2026-09-12", "different variant", differentVariant,
                LoadState.EXTERNAL_LOAD, 90d, null, 5
        );
        Fixture active = createWorkout(
                storage, owner, "2026-09-14", "active", canonical,
                LoadState.EXTERNAL_LOAD, 200d, null, 5, false
        );
        createWorkout(
                storage, owner, "2026-09-15", "other active", canonical,
                LoadState.EXTERNAL_LOAD, 250d, null, 5, false
        );
        Fixture otherScope = createWorkout(
                storage, owner, "2026-09-13", "other scope", canonical,
                LoadState.EXTERNAL_LOAD, 350d, null, 5
        );
        Fixture deletedWorkout = createWorkout(
                storage, owner, "2026-09-11", "deleted workout", canonical,
                LoadState.EXTERNAL_LOAD, 300d, null, 5
        );
        assertTrue(storage.deleteSession(owner, deletedWorkout.recordId));

        Fixture deletedExercise = createWorkout(
                storage, owner, "2026-09-08", "deleted exercise", canonical,
                LoadState.EXTERNAL_LOAD, 301d, null, 5
        );
        assertTrue(storage.deleteExercise(owner, deletedExercise.recordId, deletedExercise.exerciseId));

        Fixture deletedSet = createWorkout(
                storage, owner, "2026-09-07", "deleted set", canonical,
                LoadState.EXTERNAL_LOAD, 302d, null, 5
        );
        assertTrue(storage.deleteSet(owner, deletedSet.recordId, deletedSet.setId));

        createWorkout(
                storage, otherOwner, "2026-09-06", "other owner", canonical,
                LoadState.EXTERNAL_LOAD, 400d, null, 5
        );

        return new Fixture(
                active.recordId,
                active.exerciseId,
                alias.exerciseId,
                otherScope.recordId
        );
    }

    private static Fixture createWorkout(
            WorkoutRoomStorage storage,
            AccountScope scope,
            String date,
            String title,
            ExerciseFamilyIdentity identity,
            LoadState loadState,
            Double weightKg,
            Double addedWeightKg,
            int reps
    ) {
        return createWorkout(
                storage, scope, date, title, identity, loadState,
                weightKg, addedWeightKg, reps, true
        );
    }

    private static Fixture createWorkout(
            WorkoutRoomStorage storage,
            AccountScope scope,
            String date,
            String title,
            ExerciseFamilyIdentity identity,
            LoadState loadState,
            Double weightKg,
            Double addedWeightKg,
            int reps,
            boolean completed
    ) {
        String recordId = storage.createSession(
                scope, date, title, "strength", "", date + "T08:00:00+09:00",
                completed ? date + "T09:00:00+09:00" : ""
        );
        WorkoutExerciseReplacement replacement = new WorkoutExerciseReplacement(
                identity.presetId,
                identity.displayName(),
                null,
                null,
                null,
                null,
                null,
                identity.legacyRecordType,
                identity
        );
        assertTrue(storage.addExercise(scope, recordId, replacement));
        String exerciseId = storage.exercises(scope, recordId).get(0).getId();
        assertTrue(storage.addSet(
                scope, recordId, exerciseId, 1,
                input(weightKg, reps, loadState, addedWeightKg)
        ));
        String setId = storage.sets(scope, exerciseId).get(0).getId();
        return new Fixture(recordId, exerciseId, setId);
    }

    private static WorkoutSetInput input(
            Double weightKg, int reps, LoadState loadState, Double addedWeightKg
    ) {
        return new WorkoutSetInput(
                weightKg, reps, null, null, null, addedWeightKg,
                null, null, true, loadState, null, null
        );
    }

    private static WorkoutExerciseBests findBests(
            List<WorkoutExerciseBests> bests, LoadState loadState
    ) {
        for (WorkoutExerciseBests best : bests) {
            if (best.loadState == loadState) return best;
        }
        throw new AssertionError("Missing LoadState bucket: " + loadState);
    }

    private static <T> T runDb(ExecutorService executor, Callable<T> action) throws Exception {
        return executor.submit(action).get(30, TimeUnit.SECONDS);
    }

    private static IsolatedDatabaseContext isolatedContext() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        return context;
    }

    private static final class Fixture {
        private final String recordId;
        private final String exerciseId;
        private final String aliasExerciseId;
        private final String setId;
        private final String activeRecordId;
        private final String activeExerciseId;
        private final String otherScopeRecordId;

        private Fixture(String recordId, String exerciseId, String setId) {
            this.recordId = recordId;
            this.exerciseId = exerciseId;
            this.aliasExerciseId = null;
            this.setId = setId;
            this.activeRecordId = recordId;
            this.activeExerciseId = exerciseId;
            this.otherScopeRecordId = null;
        }

        private Fixture(
                String activeRecordId,
                String activeExerciseId,
                String aliasExerciseId,
                String otherScopeRecordId
        ) {
            this.recordId = activeRecordId;
            this.exerciseId = activeExerciseId;
            this.aliasExerciseId = aliasExerciseId;
            this.setId = null;
            this.activeRecordId = activeRecordId;
            this.activeExerciseId = activeExerciseId;
            this.otherScopeRecordId = otherScopeRecordId;
        }
    }

    private static final class IsolatedDatabaseContext extends android.content.ContextWrapper {
        private static final String DATABASE_PREFIX = "p1a_room_read_";

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
