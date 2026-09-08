package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.integration.workout.WorkoutInterchangeResult;
import com.yeonsik.fitnessapp.integration.workout.WorkoutInterchangeStore;

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
            WorkoutInterchangeStore sourceStore = new WorkoutInterchangeStore(
                    FitnessDatabaseConnection.fromLegacy(sourceHelper),
                    sourceContext
            );
            WorkoutTransferService sourceTransfer = new WorkoutTransferService(sourceStore, USER_ID);
            WorkoutInterchangeResult sourceImported = sourceTransfer.importJson(sourceMixedUnitV2Json());
            assertEquals(1, sourceImported.importedSessions);
            assertEquals(2, sourceImported.importedSets);

            String json = sourceTransfer.exportJson();
            WorkoutTransferCodec.Document document = WorkoutTransferCodec.decode(json);
            assertEquals(WorkoutTransferCodec.V2, document.formatVersion);
            assertNotNull(document.sessions.get(0).sourceRecordId);

            WorkoutInterchangeStore targetStore = new WorkoutInterchangeStore(
                    FitnessDatabaseConnection.fromLegacy(targetHelper),
                    targetContext
            );
            WorkoutTransferService targetTransfer = new WorkoutTransferService(targetStore, USER_ID);
            WorkoutInterchangeResult imported = targetTransfer.importJson(json);
            assertEquals(1, imported.importedSessions);
            assertEquals(2, imported.importedSets);
            assertTwoMixedUnitSets(targetHelper, USER_ID);

            WorkoutInterchangeResult duplicate = targetTransfer.importJson(json);
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
            WorkoutTransferCodec.Document document =
                    WorkoutTransferCodec.decode(actualLegacyV1Json());
            assertEquals(WorkoutTransferCodec.V1, document.formatVersion);
            WorkoutInterchangeStore targetStore = new WorkoutInterchangeStore(
                    FitnessDatabaseConnection.fromLegacy(targetHelper),
                    targetContext
            );
            WorkoutTransferService targetTransfer = new WorkoutTransferService(targetStore, USER_ID);
            WorkoutInterchangeResult imported = targetTransfer.importJson(actualLegacyV1Json());
            assertEquals(1, imported.importedSessions);
            assertEquals(1, imported.importedSets);
            String sessionId = latestSessionId(targetHelper, USER_ID);
            assertNotNull(sessionId);
            assertEquals("2026-09-05", sessionDate(targetHelper, USER_ID, sessionId));
            assertEquals(
                    "chest_dumbbell_decline_bench_press",
                    importedExerciseId(targetHelper, USER_ID, sessionId)
            );
            assertEquals(80d, importedWeightKg(targetHelper, USER_ID, sessionId), 0d);
            assertEquals(8, importedActualReps(targetHelper, USER_ID, sessionId));
            assertNull(importedInputLoadValue(targetHelper, USER_ID, sessionId));
            assertNull(importedInputLoadUnit(targetHelper, USER_ID, sessionId));

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
            WorkoutInterchangeStore firstStore = new WorkoutInterchangeStore(
                    FitnessDatabaseConnection.fromLegacy(firstHelper),
                    firstContext
            );
            WorkoutTransferService firstTransfer = new WorkoutTransferService(firstStore, USER_ID);
            WorkoutInterchangeResult firstImported = firstTransfer.importJson(legacyShapedMixedV2Json());
            assertEquals(1, firstImported.importedSessions);
            assertEquals(3, firstImported.importedSets);
            assertMixedUnitSets(firstHelper, USER_ID);

            String firstExport = new WorkoutTransferService(new WorkoutInterchangeStore(FitnessDatabaseConnection.fromLegacy(firstHelper), firstContext), USER_ID).exportJson();
            JSONObject firstRoot = new JSONObject(firstExport);
            assertTrue(firstRoot.has("workouts"));
            assertFalse(firstRoot.has("sessions"));
            WorkoutInterchangeStore secondStore = new WorkoutInterchangeStore(
                    FitnessDatabaseConnection.fromLegacy(secondHelper),
                    secondContext
            );
            WorkoutTransferService secondTransfer = new WorkoutTransferService(secondStore, USER_ID);
            WorkoutInterchangeResult secondImported = secondTransfer.importJson(firstExport);
            assertEquals(1, secondImported.importedSessions);
            assertEquals(3, secondImported.importedSets);
            String finalJson = new WorkoutTransferService(new WorkoutInterchangeStore(FitnessDatabaseConnection.fromLegacy(secondHelper), secondContext), USER_ID).exportJson();
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

    private static void assertMixedUnitSets(FitnessDatabaseHelper helper, String userId) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT ws.weight_kg, ws.input_load_value, ws.input_load_unit "
                        + "FROM workout_sets ws INNER JOIN workout_exercises we "
                        + "ON we.id = ws.workout_exercise_id "
                        + "INNER JOIN workout_records wr ON wr.id = we.record_id "
                        + "WHERE wr.user_id = ? AND wr.deleted_at IS NULL "
                        + "AND we.deleted_at IS NULL AND ws.deleted_at IS NULL "
                        + "ORDER BY ws.set_index",
                new String[]{userId}
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals(60d, cursor.getDouble(0), 0d);
            assertEquals(60d, cursor.getDouble(1), 0d);
            assertEquals(MassUnit.KG.id(), cursor.getString(2));
            assertTrue(cursor.moveToNext());
            assertEquals(63.5029318d, cursor.getDouble(0), 0d);
            assertEquals(140d, cursor.getDouble(1), 0d);
            assertEquals(MassUnit.LB.id(), cursor.getString(2));
            assertTrue(cursor.moveToNext());
            assertEquals(65d, cursor.getDouble(0), 0d);
            assertEquals(65d, cursor.getDouble(1), 0d);
            assertEquals(MassUnit.KG.id(), cursor.getString(2));
            assertFalse(cursor.moveToNext());
        }
    }

    private static void assertTwoMixedUnitSets(FitnessDatabaseHelper helper, String userId) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT ws.weight_kg, ws.input_load_value, ws.input_load_unit "
                        + "FROM workout_sets ws INNER JOIN workout_exercises we "
                        + "ON we.id = ws.workout_exercise_id "
                        + "INNER JOIN workout_records wr ON wr.id = we.record_id "
                        + "WHERE wr.user_id = ? AND wr.deleted_at IS NULL "
                        + "AND we.deleted_at IS NULL AND ws.deleted_at IS NULL "
                        + "ORDER BY ws.set_index",
                new String[]{userId}
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals(65d, cursor.getDouble(0), 0d);
            assertEquals(65d, cursor.getDouble(1), 0d);
            assertEquals(MassUnit.KG.id(), cursor.getString(2));
            assertTrue(cursor.moveToNext());
            assertEquals(MassUnit.toKg(225d, MassUnit.LB), cursor.getDouble(0), 0d);
            assertEquals(225d, cursor.getDouble(1), 0d);
            assertEquals(MassUnit.LB.id(), cursor.getString(2));
            assertFalse(cursor.moveToNext());
        }
    }

    private static String latestSessionId(FitnessDatabaseHelper helper, String userId) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT id FROM workout_records WHERE user_id = ? "
                        + "AND deleted_at IS NULL ORDER BY rowid DESC LIMIT 1",
                new String[]{userId}
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static String sessionDate(
            FitnessDatabaseHelper helper,
            String userId,
            String sessionId
    ) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT date FROM workout_records WHERE id = ? AND user_id = ? LIMIT 1",
                new String[]{sessionId, userId}
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static String importedExerciseId(
            FitnessDatabaseHelper helper,
            String userId,
            String sessionId
    ) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT exercise_id FROM workout_exercises "
                        + "WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL "
                        + "ORDER BY order_index LIMIT 1",
                new String[]{sessionId, userId}
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static double importedWeightKg(
            FitnessDatabaseHelper helper,
            String userId,
            String sessionId
    ) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT ws.weight_kg FROM workout_sets ws "
                        + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id "
                        + "WHERE we.record_id = ? AND ws.user_id = ? "
                        + "AND ws.deleted_at IS NULL ORDER BY ws.set_index LIMIT 1",
                new String[]{sessionId, userId}
        )) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : Double.NaN;
        }
    }

    private static int importedActualReps(
            FitnessDatabaseHelper helper,
            String userId,
            String sessionId
    ) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT ws.actual_reps FROM workout_sets ws "
                        + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id "
                        + "WHERE we.record_id = ? AND ws.user_id = ? "
                        + "AND ws.deleted_at IS NULL ORDER BY ws.set_index LIMIT 1",
                new String[]{sessionId, userId}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    private static Double importedInputLoadValue(
            FitnessDatabaseHelper helper,
            String userId,
            String sessionId
    ) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT ws.input_load_value FROM workout_sets ws "
                        + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id "
                        + "WHERE we.record_id = ? AND ws.user_id = ? "
                        + "AND ws.deleted_at IS NULL ORDER BY ws.set_index LIMIT 1",
                new String[]{sessionId, userId}
        )) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getDouble(0) : null;
        }
    }

    private static String importedInputLoadUnit(
            FitnessDatabaseHelper helper,
            String userId,
            String sessionId
    ) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT ws.input_load_unit FROM workout_sets ws "
                        + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id "
                        + "WHERE we.record_id = ? AND ws.user_id = ? "
                        + "AND ws.deleted_at IS NULL ORDER BY ws.set_index LIMIT 1",
                new String[]{sessionId, userId}
        )) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getString(0) : null;
        }
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

    private static String sourceMixedUnitV2Json() {
        return "{\"format\":\"yeonsik.workout-transfer\",\"formatVersion\":2,"
                + "\"sourceApp\":\"production.transfer.test\","
                + "\"exportedAt\":\"2026-09-05T12:00:00Z\",\"workouts\":[{"
                + "\"sourceRecordId\":\"production-mixed-workout-1\",\"status\":\"completed\","
                + "\"title\":\"Transfer mixed units\","
                + "\"startedAt\":\"2026-09-05T10:15:55.057Z\","
                + "\"endedAt\":\"2026-09-05T11:00:55.057Z\",\"memo\":null,"
                + "\"exercises\":[{"
                + "\"storageExerciseId\":\"chest_dumbbell_decline_bench_press\","
                + "\"presetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"canonicalPresetId\":\"chest_dumbbell_decline_bench_press\","
                + "\"nameSnapshot\":\"덤벨 디클라인 벤치프레스\","
                + "\"defaultUiPart\":\"chest\",\"equipmentSnapshot\":\"덤벨\","
                + "\"recordType\":\"weight_reps\",\"orderIndex\":1,\"sets\":["
                + "{\"sourceSetId\":\"production-mixed-set-1\",\"setIndex\":1,"
                + "\"weightKg\":65,\"reps\":1,\"loadState\":\"external_load\","
                + "\"completed\":true,\"inputLoadValue\":65,\"inputLoadUnit\":\"kg\"},"
                + "{\"sourceSetId\":\"production-mixed-set-2\",\"setIndex\":2,"
                + "\"weightKg\":102.05828325,\"reps\":1,\"loadState\":\"external_load\","
                + "\"completed\":true,\"inputLoadValue\":225,\"inputLoadUnit\":\"lb\"}"
                + "]}]}]}";
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
