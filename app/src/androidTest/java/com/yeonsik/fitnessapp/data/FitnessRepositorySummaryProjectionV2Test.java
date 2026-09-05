package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public final class FitnessRepositorySummaryProjectionV2Test {
    private static final String USER_ID = "summary-projection-user";
    private static final String SESSION_ID = "summary-session";
    private static final String DATABASE_PREFIX = "summary_projection_v2_";
    private static final String CREATED_AT = "2026-09-05T01:00:00Z";
    private static final String DATE = "2026-09-05";

    private Context context;
    private FitnessDatabaseHelper helper;
    private FitnessRepository repository;
    private SQLiteDatabase database;

    @Before
    public void setUp() {
        context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        helper = new FitnessDatabaseHelper(context);
        database = helper.getWritableDatabase();
        repository = new FitnessRepository(helper, USER_ID);
    }

    @After
    public void tearDown() {
        helper.close();
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void completedSessionCreatesNormalProjection() throws Exception {
        insertCompletedSession();

        FitnessSummaryProjectionV2 projection = onlyProjection();
        assertEquals(SESSION_ID, projection.id);
        assertEquals(SESSION_ID, projection.sourceFitnessSessionId);
        assertEquals(DATE, projection.date);
        assertEquals("completed", projection.completionStatus);
        assertEquals(2, projection.chestSets);
        assertEquals(1, projection.bicepsSets);
        assertEquals(Integer.valueOf(1800), projection.totalDurationSeconds);
        assertNull(projection.deletedAt);
        assertOnlySummaryFields(projection);
    }

    @Test
    public void softDeletingCompletedSessionCreatesSameIdentityTombstone() throws Exception {
        insertCompletedSession();
        FitnessSummaryProjectionV2 beforeDelete = onlyProjection();

        repository.deleteSession(SESSION_ID);

        FitnessSummaryProjectionV2 tombstone = onlyProjection();
        assertEquals(beforeDelete.id, tombstone.id);
        assertEquals(beforeDelete.sourceFitnessSessionId, tombstone.sourceFitnessSessionId);
        assertNotNull(tombstone.deletedAt);
        assertEquals(tombstone.deletedAt, tombstone.updatedAt);
        assertEquals(tombstone.deletedAt, scalar("deleted_at"));
        assertEquals(tombstone.updatedAt, scalar("updated_at"));
        assertOnlySummaryFields(tombstone);
    }

    @Test
    public void deletedProjectionContainsNoExerciseOrSetDetailFields() throws Exception {
        insertCompletedSession();
        repository.deleteSession(SESSION_ID);

        assertOnlySummaryFields(onlyProjection());
    }

    private FitnessSummaryProjectionV2 onlyProjection() {
        List<FitnessSummaryProjectionV2> projections =
                repository.completedFitnessSummaryProjectionsV2();
        assertEquals(1, projections.size());
        return projections.get(0);
    }

    private String scalar(String column) {
        try (Cursor cursor = database.rawQuery(
                "SELECT " + column + " FROM workout_records WHERE id = ?",
                new String[]{SESSION_ID}
        )) {
            if (!cursor.moveToFirst()) {
                throw new AssertionError("Expected source workout record");
            }
            return cursor.getString(0);
        }
    }

    private void insertCompletedSession() {
        ContentValues session = new ContentValues();
        session.put("id", SESSION_ID);
        session.put("user_id", USER_ID);
        session.put("date", DATE);
        session.put("workout_type", "strength");
        session.put("category", "strength");
        session.put("exercise_name", "상세 운동 원본");
        session.put("duration_seconds", 1800);
        session.put("total_volume_kg", 640d);
        session.putNull("average_heart_rate");
        session.put("created_at", CREATED_AT);
        session.put("is_backfilled", 0);
        session.putNull("backfilled_at");
        session.putNull("backfill_reason");
        session.put("updated_at", CREATED_AT);
        session.putNull("deleted_at");
        session.put("device_id", "android-local");
        session.put("source_app", "fitness");
        session.put("scope", "fitness");
        session.put("metadata", "{\"status\":\"completed\"}");
        session.put("contract_version", 1);
        database.insertOrThrow("workout_records", null, session);

        insertExercise("exercise-chest", "벤치 프레스", "chest", "chest");
        insertSet("set-chest-1", "exercise-chest", 1);
        insertSet("set-chest-2", "exercise-chest", 2);

        insertExercise("exercise-biceps", "컬", "arms", "biceps");
        insertSet("set-biceps-1", "exercise-biceps", 1);
    }

    private void insertExercise(
            String id,
            String name,
            String uiPart,
            String primarySubPart
    ) {
        ContentValues exercise = new ContentValues();
        exercise.put("id", id);
        exercise.put("user_id", USER_ID);
        exercise.put("record_id", SESSION_ID);
        exercise.put("order_index", 1);
        exercise.put("exercise_id", "master-" + id);
        exercise.put("exercise_name_snapshot", name);
        exercise.put("ui_part", uiPart);
        exercise.put("primary_sub_part_snapshot", primarySubPart);
        exercise.put("record_type", "sets_reps_weight");
        exercise.put("created_at", CREATED_AT);
        exercise.put("updated_at", CREATED_AT);
        exercise.putNull("deleted_at");
        exercise.put("device_id", "android-local");
        exercise.put("contract_version", 1);
        database.insertOrThrow("workout_exercises", null, exercise);
    }

    private void insertSet(String id, String exerciseId, int index) {
        ContentValues set = new ContentValues();
        set.put("id", id);
        set.put("user_id", USER_ID);
        set.put("workout_exercise_id", exerciseId);
        set.put("set_index", index);
        set.put("target_reps", 8);
        set.put("actual_reps", 8);
        set.put("weight_kg", 80d);
        set.put("volume_kg", 640d);
        set.put("is_completed", 1);
        set.put("rpe", 9);
        set.put("rir", 1);
        set.put("memo", "set-level detail");
        set.put("created_at", CREATED_AT);
        set.put("updated_at", CREATED_AT);
        set.putNull("deleted_at");
        set.put("device_id", "android-local");
        set.put("contract_version", 1);
        database.insertOrThrow("workout_sets", null, set);
    }

    private static void assertOnlySummaryFields(FitnessSummaryProjectionV2 projection)
            throws Exception {
        JSONObject payload = projection.toRpcJson();
        String[] forbiddenKeys = new String[]{
                "exercise_id",
                "exercise_name",
                "exercise_name_snapshot",
                "family_id",
                "canonical_variant_key",
                "visual_variant_key",
                "weight_kg",
                "target_reps",
                "actual_reps",
                "reps",
                "rpe",
                "rir",
                "set_index",
                "workout_exercise_id",
                "set_id",
                "memo"
        };
        for (String key : forbiddenKeys) {
            assertFalse("v2 must not expose " + key, payload.has(key));
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
