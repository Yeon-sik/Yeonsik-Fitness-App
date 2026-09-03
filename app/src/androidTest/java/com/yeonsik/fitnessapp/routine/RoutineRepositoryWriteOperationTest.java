package com.yeonsik.fitnessapp.routine;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.BodyPart;
import com.yeonsik.fitnessapp.exercise.EquipmentType;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;

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

@RunWith(AndroidJUnit4.class)
public final class RoutineRepositoryWriteOperationTest {
    private static final String DATABASE_PREFIX = "routine_write_";
    private static final String USER_ID = "routine-write-owner";
    private static final String OTHER_USER_ID = "routine-write-other";

    @Test
    public void renameRoutineChangesOnlyAnOwnedRoutine() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            RoutineRepository owner = new RoutineRepository(helper, USER_ID);
            RoutineRepository other = new RoutineRepository(helper, OTHER_USER_ID);
            String ownedId = owner.createRoutine("원본 루틴", definitions());
            String foreignId = other.createRoutine("다른 사용자 루틴", definitions());

            assertTrue(owner.renameRoutine(ownedId, "이름 변경 루틴"));
            assertEquals("이름 변경 루틴", owner.routineName(ownedId));
            assertFalse(owner.renameRoutine(foreignId, "탈취된 이름"));
            assertEquals("다른 사용자 루틴", other.routineName(foreignId));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void copyRoutineCopiesDefinitionIdentityAndOrderWithoutHistoryOrSourceMutation() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            RoutineRepository routines = new RoutineRepository(helper, USER_ID);
            String sourceId = routines.createRoutine("원본 루틴", definitions());
            List<RoutineExerciseInstance> sourceExercises = routines.routineExercises(sourceId);
            String recordId = createCompletedHistory(helper, sourceId, sourceExercises);
            SQLiteDatabase database = helper.getReadableDatabase();
            long workoutsBefore = count(database,
                    "SELECT COUNT(*) FROM workout_records WHERE id = ?", recordId);
            long workoutExercisesBefore = count(database,
                    "SELECT COUNT(*) FROM workout_exercises WHERE record_id = ?", recordId);
            long setsBefore = count(database,
                    "SELECT COUNT(*) FROM workout_sets WHERE workout_exercise_id IN "
                            + "(SELECT id FROM workout_exercises WHERE record_id = ?)", recordId);

            String copiedId = routines.copyRoutine(sourceId, "복사 루틴");

            assertNotNull(copiedId);
            assertFalse(sourceId.equals(copiedId));
            assertEquals("원본 루틴", routines.routineName(sourceId));
            assertEquals("복사 루틴", routines.routineName(copiedId));
            assertDefinitionRows(database, sourceId);
            assertDefinitionRows(database, copiedId);
            assertEquals(2, routines.routineExercises(sourceId).size());
            assertEquals(2, routines.routineExercises(copiedId).size());

            String copiedRoutineMetadata = "%\"routine_id\":\"" + copiedId + "\"%";
            assertEquals(0, count(database,
                    "SELECT COUNT(*) FROM workout_records "
                            + "WHERE user_id = ? AND metadata LIKE ?",
                    USER_ID, copiedRoutineMetadata));
            assertEquals(0, count(database,
                    "SELECT COUNT(*) FROM workout_exercises we "
                            + "INNER JOIN workout_records wr ON wr.id = we.record_id "
                            + "WHERE wr.user_id = ? AND wr.metadata LIKE ?",
                    USER_ID, copiedRoutineMetadata));
            assertEquals(0, count(database,
                    "SELECT COUNT(*) FROM workout_sets ws "
                            + "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id "
                            + "INNER JOIN workout_records wr ON wr.id = we.record_id "
                            + "WHERE wr.user_id = ? AND wr.metadata LIKE ?",
                    USER_ID, copiedRoutineMetadata));

            assertEquals(workoutsBefore, count(database,
                    "SELECT COUNT(*) FROM workout_records WHERE id = ?", recordId));
            assertEquals(workoutExercisesBefore, count(database,
                    "SELECT COUNT(*) FROM workout_exercises WHERE record_id = ?", recordId));
            assertEquals(setsBefore, count(database,
                    "SELECT COUNT(*) FROM workout_sets WHERE workout_exercise_id IN "
                            + "(SELECT id FROM workout_exercises WHERE record_id = ?)", recordId));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void deleteRoutineSoftDeletesDefinitionAndPreservesCompletedHistory() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            RoutineRepository routines = new RoutineRepository(helper, USER_ID);
            String routineId = routines.createRoutine("삭제 대상 루틴", definitions());
            String recordId = createCompletedHistory(
                    helper,
                    routineId,
                    routines.routineExercises(routineId)
            );
            SQLiteDatabase database = helper.getReadableDatabase();

            assertTrue(routines.deleteRoutine(routineId));
            assertTrue(routines.routines().isEmpty());
            assertTrue(routines.routineExercises(routineId).isEmpty());
            assertEquals(1, count(database,
                    "SELECT COUNT(*) FROM routines WHERE id = ? AND deleted_at IS NOT NULL",
                    routineId));
            assertEquals(2, count(database,
                    "SELECT COUNT(*) FROM routine_exercises WHERE routine_id = ? "
                            + "AND deleted_at IS NOT NULL", routineId));
            assertEquals(1, count(database,
                    "SELECT COUNT(*) FROM workout_records WHERE id = ? AND deleted_at IS NULL",
                    recordId));
            assertEquals(2, count(database,
                    "SELECT COUNT(*) FROM workout_exercises WHERE record_id = ? "
                            + "AND deleted_at IS NULL", recordId));
            assertEquals(1, count(database,
                    "SELECT COUNT(*) FROM workout_sets WHERE workout_exercise_id IN "
                            + "(SELECT id FROM workout_exercises WHERE record_id = ?) "
                            + "AND deleted_at IS NULL", recordId));
            assertEquals("completed", new FitnessRepository(helper, USER_ID)
                    .sessionInfo(recordId).status);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void renameCopyAndDeleteRejectAnotherUsersRoutine() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            RoutineRepository owner = new RoutineRepository(helper, USER_ID);
            RoutineRepository other = new RoutineRepository(helper, OTHER_USER_ID);
            String foreignId = other.createRoutine("다른 사용자 루틴", definitions());

            assertFalse(owner.renameRoutine(foreignId, "변경 불가"));
            assertNull(owner.copyRoutine(foreignId, "복사 불가"));
            assertFalse(owner.deleteRoutine(foreignId));
            assertEquals("다른 사용자 루틴", other.routineName(foreignId));
            assertEquals(1, other.routines().size());
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static String createCompletedHistory(
            FitnessDatabaseHelper helper,
            String routineId,
            List<RoutineExerciseInstance> exercises
    ) {
        FitnessRepository repository = new FitnessRepository(helper, USER_ID);
        String recordId = repository.createSessionFromRoutine(
                "2026-08-30",
                "원본 운동 기록",
                routineId,
                exercises
        );
        List<FitnessRepository.SessionExerciseEntry> sessionExercises =
                repository.sessionExerciseEntries(recordId);
        assertEquals(2, sessionExercises.size());
        repository.addTypedSet(
                recordId,
                sessionExercises.get(0).id,
                1,
                new FitnessRepository.SetInput(40d, 8, null, null, null, null, null, true)
        );
        repository.finishSession(recordId);
        assertEquals("completed", repository.sessionInfo(recordId).status);
        return recordId;
    }

    private static void assertDefinitionRows(SQLiteDatabase database, String routineId) {
        try (Cursor cursor = database.rawQuery(
                "SELECT exercise_id, family_id, preset_id, canonical_variant_key, "
                        + "visual_variant_key, order_index FROM routine_exercises "
                        + "WHERE routine_id = ? AND user_id = ? AND deleted_at IS NULL "
                        + "ORDER BY order_index",
                new String[]{routineId, USER_ID}
        )) {
            assertTrue(cursor.moveToFirst());
            assertDefinitionRow(cursor, "exercise-a", "family-a", "preset-a",
                    "canonical-a", "visual-a", 1);
            assertTrue(cursor.moveToNext());
            assertDefinitionRow(cursor, "exercise-b", "family-b", "preset-b",
                    "canonical-b", "visual-b", 2);
            assertFalse(cursor.moveToNext());
        }
    }

    private static void assertDefinitionRow(
            Cursor cursor,
            String exerciseId,
            String familyId,
            String presetId,
            String canonicalVariantKey,
            String visualVariantKey,
            int orderIndex
    ) {
        assertEquals(exerciseId, cursor.getString(0));
        assertEquals(familyId, cursor.getString(1));
        assertEquals(presetId, cursor.getString(2));
        assertEquals(canonicalVariantKey, cursor.getString(3));
        assertEquals(visualVariantKey, cursor.getString(4));
        assertEquals(orderIndex, cursor.getInt(5));
    }

    private static List<RoutineExercise> definitions() {
        return Arrays.asList(
                new RoutineExercise(
                        "exercise-a",
                        "운동 A",
                        "Exercise A",
                        BodyPart.CHEST,
                        EquipmentType.BARBELL,
                        "barbell",
                        "가슴",
                        FitnessRecordContract.WEIGHT_REPS,
                        identity("exercise-a", "family-a", "preset-a", "canonical-a", "visual-a")
                ),
                new RoutineExercise(
                        "exercise-b",
                        "운동 B",
                        "Exercise B",
                        BodyPart.BACK,
                        EquipmentType.DUMBBELL,
                        "dumbbell",
                        "등",
                        FitnessRecordContract.REPS_ONLY,
                        identity("exercise-b", "family-b", "preset-b", "canonical-b", "visual-b")
                )
        );
    }

    private static ExerciseFamilyIdentity identity(
            String legacyExerciseId,
            String familyId,
            String presetId,
            String canonicalVariantKey,
            String visualVariantKey
    ) {
        return new ExerciseFamilyIdentity(
                legacyExerciseId,
                familyId,
                presetId,
                presetId,
                "표시 " + presetId,
                "Display " + presetId,
                "레거시 " + presetId,
                "Legacy " + presetId,
                "chest",
                canonicalVariantKey,
                visualVariantKey,
                "illustration-" + presetId,
                null,
                FitnessRecordContract.WEIGHT_REPS,
                null
        );
    }

    private static long count(SQLiteDatabase database, String sql, String... args) {
        try (Cursor cursor = database.rawQuery(sql, args)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        }
    }

    private static IsolatedDatabaseContext isolatedContext() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        return context;
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
