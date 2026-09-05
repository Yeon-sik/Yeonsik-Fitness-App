package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter;
import com.yeonsik.fitnessapp.exercise.LoadState;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class WorkoutTransferRepositoryTest {
    private static final String USER_ID = "workout-transfer-test-user";
    private static final String SOURCE_PREFIX = "workout_transfer_source_";
    private static final String TARGET_PREFIX = "workout_transfer_target_";
    private static final String LEGACY_TARGET_PREFIX = "workout_transfer_legacy_target_";
    private static final String ROUND_TRIP_FIRST_PREFIX = "workout_transfer_round_trip_first_";
    private static final String ROUND_TRIP_SECOND_PREFIX = "workout_transfer_round_trip_second_";

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

    @Test
    public void importsActualLegacyWorkoutsV1FixtureWithDateFallbackAndMetadata() throws Exception {
        Context base = ApplicationProvider.getApplicationContext();
        IsolatedDatabaseContext targetContext = new IsolatedDatabaseContext(
                base,
                LEGACY_TARGET_PREFIX
        );
        targetContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper targetHelper = new FitnessDatabaseHelper(targetContext);
        try {
            FitnessRepository target = new FitnessRepository(targetHelper, USER_ID);
            WorkoutTransferCodec.Document document =
                    WorkoutTransferCodec.decode(actualLegacyV1Json());

            WorkoutTransferRepositoryResultAssert.assertImported(
                    target.importWorkoutTransferDocument(document),
                    1,
                    1
            );
            String sessionId = target.latestSessionId();
            assertNotNull(sessionId);
            assertEquals("2026-09-05", target.sessionInfo(sessionId).date);

            List<FitnessRepository.SessionExerciseEntry> exercises =
                    target.sessionExerciseEntries(sessionId);
            assertEquals(1, exercises.size());
            assertNotNull(exercises.get(0).familyIdentity);
            assertEquals(
                    "chest_dumbbell_decline_bench_press",
                    exercises.get(0).familyIdentity.presetId
            );
            List<FitnessRepository.SessionSetEntry> sets =
                    target.setsForExercise(exercises.get(0).id);
            assertEquals(1, sets.size());
            assertEquals(80d, sets.get(0).weightKg, 0d);
            assertEquals(8, sets.get(0).actualReps);
            assertNull(sets.get(0).inputLoadValue);
            assertNull(sets.get(0).inputLoadUnit);

            JSONObject metadata = new JSONObject(recordMetadata(targetHelper, sessionId));
            assertEquals(
                    "2026-09-05T10:15:55.057Z",
                    metadata.getString("started_at")
            );
            assertEquals(
                    "2026-09-05T11:00:55.057Z",
                    metadata.getString("ended_at")
            );
            assertEquals("legacy memo", metadata.getString("memo"));
        } finally {
            targetHelper.close();
            targetContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void preservesLegacyShapedMixedUnitProvenanceAcrossExportDecodeImportExport()
            throws Exception {
        Context base = ApplicationProvider.getApplicationContext();
        IsolatedDatabaseContext firstContext = new IsolatedDatabaseContext(
                base,
                ROUND_TRIP_FIRST_PREFIX
        );
        IsolatedDatabaseContext secondContext = new IsolatedDatabaseContext(
                base,
                ROUND_TRIP_SECOND_PREFIX
        );
        firstContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        secondContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper firstHelper = new FitnessDatabaseHelper(firstContext);
        FitnessDatabaseHelper secondHelper = new FitnessDatabaseHelper(secondContext);
        try {
            FitnessRepository first = new FitnessRepository(firstHelper, USER_ID);
            WorkoutTransferCodec.Document fixture =
                    WorkoutTransferCodec.decode(legacyShapedMixedV2Json());
            WorkoutTransferRepositoryResultAssert.assertImported(
                    first.importWorkoutTransferDocument(fixture),
                    1,
                    3
            );
            assertMixedUnitSets(first, first.latestSessionId());

            String firstExport = new WorkoutTransferService(first).exportJson();
            JSONObject firstRoot = new JSONObject(firstExport);
            assertTrue(firstRoot.has("workouts"));
            assertFalse(firstRoot.has("sessions"));
            WorkoutTransferCodec.Document decodedExport =
                    WorkoutTransferCodec.decode(firstExport);

            FitnessRepository second = new FitnessRepository(secondHelper, USER_ID);
            WorkoutTransferRepositoryResultAssert.assertImported(
                    second.importWorkoutTransferDocument(decodedExport),
                    1,
                    3
            );
            String finalJson = new WorkoutTransferService(second).exportJson();
            WorkoutTransferCodec.Document finalDocument =
                    WorkoutTransferCodec.decode(finalJson);
            List<WorkoutTransferCodec.SetData> finalSets =
                    finalDocument.sessions.get(0).exercises.get(0).sets;
            assertEquals(3, finalSets.size());
            assertEquals(60d, finalSets.get(0).weightKg, 0d);
            assertEquals(60d, finalSets.get(0).inputLoadValue, 0d);
            assertEquals(MassUnit.KG.id(), finalSets.get(0).inputLoadUnit);
            assertEquals(63.5029318d, finalSets.get(1).weightKg, 0d);
            assertEquals(140d, finalSets.get(1).inputLoadValue, 0d);
            assertEquals(MassUnit.LB.id(), finalSets.get(1).inputLoadUnit);
            assertEquals(65d, finalSets.get(2).weightKg, 0d);
            assertEquals(65d, finalSets.get(2).inputLoadValue, 0d);
            assertEquals(MassUnit.KG.id(), finalSets.get(2).inputLoadUnit);
        } finally {
            firstHelper.close();
            secondHelper.close();
            firstContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
            secondContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static void assertMixedUnitSets(FitnessRepository repository, String sessionId) {
        List<FitnessRepository.SessionExerciseEntry> exercises =
                repository.sessionExerciseEntries(sessionId);
        assertEquals(1, exercises.size());
        List<FitnessRepository.SessionSetEntry> sets =
                repository.setsForExercise(exercises.get(0).id);
        assertEquals(3, sets.size());
        assertEquals(60d, sets.get(0).weightKg, 0d);
        assertEquals(60d, sets.get(0).inputLoadValue, 0d);
        assertEquals(MassUnit.KG, sets.get(0).inputLoadUnit);
        assertEquals(63.5029318d, sets.get(1).weightKg, 0d);
        assertEquals(140d, sets.get(1).inputLoadValue, 0d);
        assertEquals(MassUnit.LB, sets.get(1).inputLoadUnit);
        assertEquals(65d, sets.get(2).weightKg, 0d);
        assertEquals(65d, sets.get(2).inputLoadValue, 0d);
        assertEquals(MassUnit.KG, sets.get(2).inputLoadUnit);
    }

    private static String recordMetadata(
            FitnessDatabaseHelper helper,
            String sessionId
    ) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT metadata FROM workout_records WHERE id = ? LIMIT 1",
                new String[]{sessionId}
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : "{}";
        }
    }

    private static String actualLegacyV1Json() {
        return "{\"format\":\"yeonsik.workout-transfer\",\"formatVersion\":1,"
                + "\"sourceApp\":\"legacy.friend.fitness\","
                + "\"exportedAt\":\"2026-09-05T12:00:00Z\",\"workouts\":[{"
                + "\"sourceRecordId\":\"legacy-workout-1\",\"status\":\"completed\","
                + "\"title\":\"Legacy workout\","
                + "\"startedAt\":\"2026-09-05T10:15:55.057Z\","
                + "\"endedAt\":\"2026-09-05T11:00:55.057Z\","
                + "\"memo\":\"legacy memo\",\"exercises\":[{"
                + "\"storageExerciseId\":\"chest_dumbbell_decline_bench_press\","
                + "\"presetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"canonicalPresetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"nameSnapshot\":\"덤벨 디클라인 벤치프레스\","
                + "\"defaultUiPart\":\"chest\",\"equipmentSnapshot\":\"덤벨\","
                + "\"recordType\":\"weight_reps\",\"orderIndex\":1,\"sets\":[{"
                + "\"sourceSetId\":\"legacy-set-1\",\"setIndex\":1,\"weightKg\":80,"
                + "\"reps\":8,\"restSeconds\":90,\"loadState\":\"external_load\","
                + "\"rir\":2,\"completed\":true}]}]}]}";
    }

    private static String legacyShapedMixedV2Json() {
        return "{\"format\":\"yeonsik.workout-transfer\",\"formatVersion\":2,"
                + "\"sourceApp\":\"legacy.friend.fitness\","
                + "\"exportedAt\":\"2026-09-05T12:00:00Z\",\"workouts\":[{"
                + "\"sourceRecordId\":\"mixed-workout-1\",\"status\":\"completed\","
                + "\"title\":\"Mixed units\","
                + "\"startedAt\":\"2026-09-05T10:15:55.057Z\","
                + "\"endedAt\":\"2026-09-05T11:00:55.057Z\",\"memo\":null,"
                + "\"exercises\":[{"
                + "\"storageExerciseId\":\"chest_dumbbell_decline_bench_press\","
                + "\"presetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"canonicalPresetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"nameSnapshot\":\"덤벨 디클라인 벤치프레스\","
                + "\"defaultUiPart\":\"chest\",\"equipmentSnapshot\":\"덤벨\","
                + "\"recordType\":\"weight_reps\",\"orderIndex\":1,\"sets\":["
                + "{\"sourceSetId\":\"mixed-set-1\",\"setIndex\":1,\"weightKg\":60,"
                + "\"reps\":8,\"loadState\":\"external_load\",\"completed\":true,"
                + "\"inputLoadValue\":60,\"inputLoadUnit\":\"kg\"},"
                + "{\"sourceSetId\":\"mixed-set-2\",\"setIndex\":2,"
                + "\"weightKg\":63.5029318,\"reps\":8,\"loadState\":\"external_load\","
                + "\"completed\":true,\"inputLoadValue\":140,\"inputLoadUnit\":\"lb\"},"
                + "{\"sourceSetId\":\"mixed-set-3\",\"setIndex\":3,\"weightKg\":65,"
                + "\"reps\":8,\"loadState\":\"external_load\",\"completed\":true,"
                + "\"inputLoadValue\":65,\"inputLoadUnit\":\"kg\"}"
                + "]}]}]}";
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
