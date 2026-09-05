package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter;
import com.yeonsik.fitnessapp.exercise.LoadState;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class WorkoutTransferRepositoryTest {
    private static final String USER_ID = "workout-transfer-test-user";
    private static final String SOURCE_PREFIX = "workout_transfer_source_";
    private static final String TARGET_PREFIX = "workout_transfer_target_";

    @Test
    public void exportsImportsMixedUnitsAndSkipsTheSameSourceIdentityOnRepeat() {
        Context base = ApplicationProvider.getApplicationContext();
        IsolatedDatabaseContext sourceContext = new IsolatedDatabaseContext(base, SOURCE_PREFIX);
        IsolatedDatabaseContext targetContext = new IsolatedDatabaseContext(base, TARGET_PREFIX);
        sourceContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        targetContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessDatabaseHelper sourceHelper = new FitnessDatabaseHelper(sourceContext);
        FitnessDatabaseHelper targetHelper = new FitnessDatabaseHelper(targetContext);
        try {
            FitnessRepository source = new FitnessRepository(sourceHelper, USER_ID);
            String recordId = source.createSession(
                    "2026-09-05", "Transfer mixed units", "strength", "", "", ""
            );
            String exerciseId = addMasterExercise(
                    source,
                    recordId,
                    "chest_dumbbell_decline_bench_press"
            );
            source.addTypedSet(
                    recordId,
                    exerciseId,
                    1,
                    new FitnessRepository.SetInput(
                            65d,
                            1,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            true,
                            LoadState.EXTERNAL_LOAD,
                            65d,
                            MassUnit.KG
                    )
            );
            source.addTypedSet(
                    recordId,
                    exerciseId,
                    2,
                    new FitnessRepository.SetInput(
                            MassUnit.toKg(225d, MassUnit.LB),
                            1,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            true,
                            LoadState.EXTERNAL_LOAD,
                            225d,
                            MassUnit.LB
                    )
            );

            String json = new WorkoutTransferService(source).exportJson();
            WorkoutTransferCodec.Document document = WorkoutTransferCodec.decode(json);
            assertEquals(WorkoutTransferCodec.V2, document.formatVersion);
            assertEquals(recordId, document.sessions.get(0).sourceRecordId);

            FitnessRepository target = new FitnessRepository(targetHelper, USER_ID);
            WorkoutTransferRepositoryResultAssert.assertImported(
                    target.importWorkoutTransferDocument(document),
                    1,
                    2
            );
            List<FitnessRepository.SessionExerciseEntry> importedExercises =
                    target.sessionExerciseEntries(
                            target.latestSessionId()
                    );
            assertEquals(1, importedExercises.size());
            List<FitnessRepository.SessionSetEntry> importedSets =
                    target.setsForExercise(importedExercises.get(0).id);
            assertEquals(2, importedSets.size());
            assertEquals(MassUnit.KG, importedSets.get(0).inputLoadUnit);
            assertEquals(MassUnit.LB, importedSets.get(1).inputLoadUnit);
            assertEquals(225d, importedSets.get(1).inputLoadValue, 0d);
            assertEquals(MassUnit.toKg(225d, MassUnit.LB), importedSets.get(1).weightKg, 0d);

            FitnessRepository.WorkoutTransferImportResult duplicate =
                    target.importWorkoutTransferDocument(document);
            assertEquals(0, duplicate.importedSessions);
            assertEquals(1, duplicate.skippedDuplicateSessions);
        } finally {
            sourceHelper.close();
            targetHelper.close();
            sourceContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
            targetContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
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

    private static final class WorkoutTransferRepositoryResultAssert {
        private static void assertImported(
                FitnessRepository.WorkoutTransferImportResult result,
                int sessions,
                int sets
        ) {
            assertEquals(sessions, result.importedSessions);
            assertEquals(sets, result.importedSets);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private final String prefix;

        private IsolatedDatabaseContext(Context base, String prefix) {
            super(base);
            this.prefix = prefix;
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(prefix + name);
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
