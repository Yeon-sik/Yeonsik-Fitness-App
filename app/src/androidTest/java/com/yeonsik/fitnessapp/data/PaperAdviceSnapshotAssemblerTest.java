package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.development.DevelopmentGoal;
import com.yeonsik.fitnessapp.feature.development.data.DevelopmentRepository;
import com.yeonsik.fitnessapp.development.PaperAdvice;
import com.yeonsik.fitnessapp.development.PaperAdviceInput;
import com.yeonsik.fitnessapp.feature.development.application.PaperAdviceSnapshotAssembler;
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsReadRepository;
import com.yeonsik.fitnessapp.feature.development.data.DevelopmentReadRepository;
import com.yeonsik.fitnessapp.feature.meal.data.MealReadRepository;
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutReadRepository;
import com.yeonsik.fitness.shared.core.account.AccountScope;
import com.yeonsik.fitness.shared.feature.meal.model.MealNutritionReadSummary;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class PaperAdviceSnapshotAssemblerTest {
    private static final String DATABASE_PREFIX = "paper_advice_snapshot_";
    private static final String USER_ID = "22222222-2222-4222-8222-222222222222";
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 10);

    @Test
    public void assemblesLocalGoalWeightNutritionCheckInAndTrainingData() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessRoomDatabase room = openRoom(context);
        try {

            FitnessRepository fitness = new FitnessRepository(room, context, USER_ID);
            DevelopmentRepository development = new DevelopmentRepository(
                    room, USER_ID
            );
            development.saveDevelopmentGoal(new DevelopmentGoal(
                    DevelopmentGoal.OBJECTIVE_MUSCLE_GAIN,
                    3,
                    DevelopmentGoal.BODY_PART_CHEST,
                    "2026-08-01",
                    "",
                    ""
            ));
            for (int day = 28; day <= 31; day++) {
                new com.yeonsik.fitnessapp.feature.body.data.BodyMetricsRepository(room, USER_ID).addBodyMetric(String.format("2026-07-%02d", day), 82.0, "");
            }
            for (int day = 4; day <= 6; day++) {
                new com.yeonsik.fitnessapp.feature.body.data.BodyMetricsRepository(room, USER_ID).addBodyMetric(String.format("2026-08-%02d", day), 80.0, "");
            }
            new com.yeonsik.fitnessapp.feature.body.data.BodyMetricsRepository(room, USER_ID).addBodyMetric("2026-08-10", 80.0, "");
            for (int day = 4; day <= 10; day++) {
                fitness.addMeal(
                        String.format("2026-08-%02d", day),
                        "기록 식사",
                        700,
                        100.0,
                        80.0,
                        20.0
                );
            }
            for (int day = 8; day <= 10; day++) {
                fitness.saveAthleteCheckIn(new AthleteDailyCheckIn(
                        "",
                        String.format("2026-08-%02d", day),
                        2000,
                        6.2,
                        2,
                        3,
                        4,
                        2,
                        ""
                ));
            }
            String recordId = fitness.createSession(
                    "2026-08-10",
                    "가슴 운동",
                    "strength",
                    "",
                    "2026-08-10T10:00:00+09:00",
                    "2026-08-10T11:00:00+09:00"
            );
            String exerciseId = fitness.addExercise(recordId, "벤치프레스", "가슴", 1, "");
            fitness.addSet(recordId, exerciseId, 1, 60.0, 8, true);

            PaperAdviceSnapshotAssembler adapter = paperAdviceAssembler(room, context, USER_ID);
            PaperAdviceInput input = adapter.assemble(REFERENCE_DATE);
            List<PaperAdvice> advice = adapter.evaluate(REFERENCE_DATE);

            assertEquals("hypertrophy", input.goal);
            assertNotNull(input.bodyWeightKg);
            assertEquals(80.0, input.bodyWeightKg, 0.001);
            assertEquals(1.25, input.proteinGPerKg, 0.001);
            assertEquals(7, input.proteinRecordedDays);
            assertEquals(7, input.proteinWindowDays);
            assertEquals(7, input.mealCount);
            assertEquals(6.2, input.sleepHours, 0.001);
            assertEquals(3, input.sleepRecordedDays);
            assertEquals(3, input.lowEnergyOrReadinessDays);
            assertEquals(Integer.valueOf(2), input.energyScore);
            assertEquals(Integer.valueOf(2), input.readinessScore);
            assertEquals(4, input.currentWeightRecordedDays);
            assertEquals(4, input.previousWeightRecordedDays);
            assertEquals(80.0, input.currentWeight7DayAverageKg, 0.001);
            assertEquals(82.0, input.previousWeight7DayAverageKg, 0.001);
            assertEquals(-2.439, input.weeklyWeightChangePct, 0.001);
            assertEquals(1, input.resistanceTrainingSessionsPerWeek);
            assertEquals(1.0, input.weeklyHardSetsPerMuscle.get("chest"), 0.001);
            assertTrue(input.recentDataDays >= 7);
            assertAdvice(advice, "REC_SLEEP_001");
            assertAdvice(advice, "NUT_PRO_001");
        } finally {
            room.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void preservesUnavailableClinicalAndRecoveryFieldsAsUnknown() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessRoomDatabase room = openRoom(context);
        try {

            PaperAdviceInput input = paperAdviceAssembler(room, context, USER_ID)
                    .assemble(REFERENCE_DATE);

            assertNull(input.painReported);
            assertNull(input.coldWaterImmediatelyPostResistance);
            assertNull(input.failureSetsRatio);
        } finally {
            room.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static PaperAdviceSnapshotAssembler paperAdviceAssembler(
            FitnessRoomDatabase roomDatabase,
            Context context,
            String ownerId
    ) {

        return new PaperAdviceSnapshotAssembler(
                new WorkoutReadRepository(roomDatabase, context),
                new MealReadRepository(roomDatabase),
                new BodyMetricsReadRepository(roomDatabase),
                new DevelopmentReadRepository(roomDatabase),
                ownerId
        );
    }

    @Test public void allUnknownProteinRemainsUnknownThroughRepositoryAssemblerAndEngine() {
        assertProteinCoverage(null, false, false);
    }

    @Test public void partiallyUnknownProteinDoesNotBecomeAnAdequateObservationWindow() {
        assertProteinCoverage(20.0, true, false);
    }

    @Test public void realZeroProteinRemainsAKnownRecordedValue() {
        assertProteinCoverage(0.0, false, false);
    }

    @Test public void estimatedProteinRemainsInformationalThroughTheWholePath() {
        assertProteinCoverage(20.0, false, true);
    }

    private static void assertProteinCoverage(Double protein, boolean partial, boolean estimated) {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessRoomDatabase room = openRoom(context);
        try {
            FitnessRepository fitness = new FitnessRepository(room, context, USER_ID);
            new DevelopmentRepository(room, USER_ID)
                    .saveDevelopmentGoal(new DevelopmentGoal(DevelopmentGoal.OBJECTIVE_MUSCLE_GAIN,
                            3, DevelopmentGoal.BODY_PART_CHEST, "2026-08-01", "", ""));
            new com.yeonsik.fitnessapp.feature.body.data.BodyMetricsRepository(room, USER_ID).addBodyMetric("2026-08-10", 80.0, "");
            for (int day = 4; day <= 10; day++) {
                String mealId = fitness.addMeal(String.format("2026-08-%02d", day), "기록 식사", 700,
                        partial && day == 10 ? null : protein, null, null);
                // Current legacy macro columns are NOT NULL. Unknown persisted meals carry
                // explicit unknown provenance; pure tests separately cover nullable snapshots.
                if (protein == null || (partial && day == 10)) room.getOpenHelper().getWritableDatabase()
                        .execSQL("UPDATE meal_records SET metadata = ? WHERE id = ? AND user_id = ?",
                                new Object[]{"{\"nutrition_status\":\"unknown\"}", mealId, USER_ID});
            }
            new FitnessRepository(room, context, "other-owner").addMeal("2026-08-10", "다른 계정", 700, 200.0, null, null);
            if (estimated) room.getOpenHelper().getWritableDatabase().execSQL(
                    "UPDATE meal_records SET metadata = ? WHERE user_id = ?",
                    new Object[]{"{\"nutrition_status\" : \"estimated\"}", USER_ID});
            String recordId = fitness.createSession("2026-08-10", "가슴 운동", "strength", "",
                    "2026-08-10T10:00:00+09:00", "2026-08-10T11:00:00+09:00");
            String exerciseId = fitness.addExercise(recordId, "벤치프레스", "가슴", 1, "");
            fitness.addSet(recordId, exerciseId, 1, 60.0, 8, true);
            MealNutritionReadSummary summary = new MealReadRepository(room)
                    .nutritionSummary(new AccountScope(USER_ID), "2026-08-04", "2026-08-10");
            PaperAdviceSnapshotAssembler assembler = paperAdviceAssembler(room, context, USER_ID);
            PaperAdviceInput input = assembler.assemble(REFERENCE_DATE);
            assertEquals(7, summary.getMealCount());
            assertEquals(7, summary.getRecordedDays());
            assertEquals(protein == null ? 7 : partial ? 1 : 0, summary.getProteinUnknownProvenanceMealCount());
            if (protein == null) assertNull(summary.getProteinGrams());
            if (protein == null || partial) assertNull(input.proteinGPerKg);
            else assertEquals(protein / 80.0, input.proteinGPerKg, 0.001);
            List<PaperAdvice> advice = assembler.evaluate(REFERENCE_DATE);
            List<PaperAdvice> proteinAdvice = new java.util.ArrayList<>();
            for (PaperAdvice item : advice) if ("NUT_PRO_001".equals(item.adviceId)) proteinAdvice.add(item);
            if (protein == null || partial) assertTrue(proteinAdvice.isEmpty());
            else assertEquals(estimated ? PaperAdvice.Status.INFORMATIONAL : PaperAdvice.Status.ACTIONABLE,
                    proteinAdvice.get(0).status);
        } finally {
            room.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static FitnessRoomDatabase openRoom(Context context) {
        return Room.databaseBuilder(context, FitnessRoomDatabase.class,
                FitnessDatabaseHelper.DATABASE_NAME).build();
    }

    private static void assertAdvice(List<PaperAdvice> advice, String adviceId) {
        for (PaperAdvice item : advice) {
            if (adviceId.equals(item.adviceId)) {
                return;
            }
        }
        throw new AssertionError("Missing advice: " + adviceId);
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
