package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.development.DevelopmentGoal;
import com.yeonsik.fitnessapp.development.DevelopmentRepository;
import com.yeonsik.fitnessapp.development.PaperAdvice;
import com.yeonsik.fitnessapp.development.PaperAdviceInput;
import com.yeonsik.fitnessapp.development.PaperAdviceSnapshotAssembler;

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

        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(context);
            FitnessRepository fitness = new FitnessRepository(helper, USER_ID);
            DevelopmentRepository development = new DevelopmentRepository(helper, USER_ID);
            development.saveDevelopmentGoal(new DevelopmentGoal(
                    DevelopmentGoal.OBJECTIVE_MUSCLE_GAIN,
                    3,
                    DevelopmentGoal.BODY_PART_CHEST,
                    "2026-08-01",
                    "",
                    ""
            ));
            fitness.addBodyMetric("2026-08-10", 80.0, "");
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
            fitness.saveAthleteCheckIn(new AthleteDailyCheckIn(
                    "",
                    "2026-08-10",
                    2000,
                    6.2,
                    2,
                    3,
                    4,
                    2,
                    ""
            ));
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

            PaperAdviceSnapshotAssembler adapter = new PaperAdviceSnapshotAssembler(
                    helper,
                    development
            );
            PaperAdviceInput input = adapter.assemble(REFERENCE_DATE);
            List<PaperAdvice> advice = adapter.evaluate(REFERENCE_DATE);

            assertEquals("hypertrophy", input.goal);
            assertNotNull(input.bodyWeightKg);
            assertEquals(80.0, input.bodyWeightKg, 0.001);
            assertEquals(1.25, input.proteinGPerKg, 0.001);
            assertEquals(7, input.proteinRecordedDays);
            assertEquals(6.2, input.sleepHours, 0.001);
            assertEquals(Integer.valueOf(2), input.energyScore);
            assertEquals(Integer.valueOf(2), input.readinessScore);
            assertEquals(1, input.resistanceTrainingSessionsPerWeek);
            assertEquals(1.0, input.weeklyHardSetsPerMuscle.get("chest"), 0.001);
            assertTrue(input.recentDataDays >= 7);
            assertAdvice(advice, "REC_SLEEP_001");
            assertAdvice(advice, "NUT_PRO_001");
        } finally {
            if (helper != null) {
                helper.close();
            }
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void preservesUnavailableClinicalAndRecoveryFieldsAsUnknown() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(context);
            DevelopmentRepository development = new DevelopmentRepository(helper, USER_ID);
            PaperAdviceInput input = new PaperAdviceSnapshotAssembler(helper, development)
                    .assemble(REFERENCE_DATE);

            assertNull(input.painReported);
            assertNull(input.coldWaterImmediatelyPostResistance);
            assertNull(input.failureSetsRatio);
        } finally {
            if (helper != null) {
                helper.close();
            }
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
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
