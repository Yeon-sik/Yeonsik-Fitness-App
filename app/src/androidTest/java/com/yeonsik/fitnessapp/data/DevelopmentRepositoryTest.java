package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.development.BodyProfile;
import com.yeonsik.fitnessapp.development.DevelopmentGoal;
import com.yeonsik.fitnessapp.development.DevelopmentReport;
import com.yeonsik.fitnessapp.development.DevelopmentRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class DevelopmentRepositoryTest {
    private static final String DATABASE_PREFIX = "development_report_";
    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    public void buildReportIncludesReferenceDateRecordsAndDetailedFocusSets() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(context);
            FitnessRepository fitness = new FitnessRepository(helper, USER_ID);
            DevelopmentRepository development = new DevelopmentRepository(helper, USER_ID);

            development.saveBodyProfile(new BodyProfile(181, "", ""));
            development.saveDevelopmentGoal(new DevelopmentGoal(
                    DevelopmentGoal.OBJECTIVE_STRENGTH,
                    1,
                    DevelopmentGoal.BODY_PART_ARMS,
                    "2026-08-10",
                    "",
                    ""
            ));
            fitness.addBodyMetric("2026-08-10", 80.5, "");
            fitness.addMeal("2026-08-10", "테스트 식사", 600, 40.0, 70.0, 20.0);
            fitness.saveAthleteCheckIn(new AthleteDailyCheckIn(
                    "",
                    "2026-08-10",
                    2000,
                    7.0,
                    2,
                    3,
                    4,
                    2,
                    "테스트"
            ));

            String recordId = fitness.createSession(
                    "2026-08-10",
                    "팔 운동",
                    "strength",
                    "",
                    "2026-08-10T10:00:00+09:00",
                    "2026-08-10T11:00:00+09:00"
            );
            String exerciseId = fitness.addExercise(recordId, "덤벨 컬", "이두", 1, "");
            fitness.addSet(recordId, exerciseId, 1, 12.5, 10, true);

            DevelopmentReport report = development.buildReport(LocalDate.of(2026, 8, 10));

            assertEquals(LocalDate.of(2026, 7, 28), report.recentWindowStart);
            assertEquals(LocalDate.of(2026, 8, 10), report.recentWindowEnd);
            assertEquals(1, report.currentWeekCompletedWorkoutSessions);
            assertEquals(1, report.currentWeekCompletedWorkoutDays);
            assertEquals(1, report.strengthSetsForBodyPart(DevelopmentGoal.BODY_PART_ARMS));
            assertEquals(1, report.goalFocusBodyPartCompletedSets);
            assertEquals("2026-08-10", report.goalFocusBodyPartLastTrainedDate);
            assertEquals(1, report.mealRecordedDays);
            assertEquals(1, report.checkInRecordedDays);
            assertEquals(1, report.lowEnergyOrReadinessDays);
            assertEquals(1, report.dataCoverage.daysWithAnyData);
            assertEquals(1, report.dataCoverage.workoutRecordedDays);
            assertEquals(1, report.dataCoverage.mealRecordedDays);
            assertEquals(1, report.dataCoverage.checkInRecordedDays);
            assertEquals(1, report.dataCoverage.weightRecordedDays);
            assertNotNull(report.latestWeightKg);
            assertEquals(80.5, report.latestWeightKg, 0.001);
        } finally {
            if (helper != null) {
                helper.close();
            }
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
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
