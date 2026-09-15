package com.yeonsik.fitnessapp.feature.recovery.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn;
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.feature.development.data.DevelopmentReadRepository;
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentCheckInSummary;
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentNutritionGoal;
import com.yeonsik.fitnessapp.test.FitnessRoomTestDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public final class RecoveryRepositoryTest {
    private static final String DATABASE_PREFIX = "recovery_repository_";
    private static final String OWNER = "recovery-owner-a";

    @Test
    public void saveReloadAndDevelopmentReadUseTheSameOwnerSource() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            RecoveryRepository repository = new RecoveryRepository(
                    FitnessRoomDatabaseProvider.get(context), OWNER);
            DevelopmentReadRepository developmentRead = new DevelopmentReadRepository(
                    FitnessRoomDatabaseProvider.get(context));
            AccountScope scope = new AccountScope(OWNER);
            String date = LocalDate.now().minusDays(1).toString();
            AthleteNutritionGoal goal = new AthleteNutritionGoal(
                    AthleteNutritionGoal.PHASE_GAIN,
                    2400d,
                    160d,
                    250d,
                    70d,
                    30d,
                    2200d,
                    2500
            );
            AthleteDailyCheckIn checkIn = new AthleteDailyCheckIn(
                    "",
                    date,
                    1500,
                    7.5,
                    4,
                    2,
                    null,
                    5,
                    "회복 확인"
            );

            assertNull(repository.nutritionGoal(scope));
            repository.saveNutritionGoal(scope, goal);
            repository.saveCheckIn(scope, checkIn);

            AthleteNutritionGoal reloadedGoal = repository.nutritionGoal(scope);
            AthleteDailyCheckIn reloadedCheckIn = repository.checkIn(scope, date);
            assertNotNull(reloadedGoal);
            assertEquals(goal.phase, reloadedGoal.phase);
            assertEquals(goal.proteinGrams, reloadedGoal.proteinGrams, 0.001d);
            assertEquals(goal.waterMl, reloadedGoal.waterMl);
            assertEquals(checkIn.waterMl, reloadedCheckIn.waterMl);
            assertEquals(checkIn.sleepHours, reloadedCheckIn.sleepHours);
            assertEquals(checkIn.energyScore, reloadedCheckIn.energyScore);
            assertNull(reloadedCheckIn.digestionScore);

            DevelopmentNutritionGoal projectedGoal = developmentRead.nutritionGoal(scope);
            DevelopmentCheckInSummary projectedCheckIn = developmentRead.checkInSummary(
                    scope, date, date);
            assertNotNull(projectedGoal);
            assertEquals(goal.waterMl, projectedGoal.getWaterMl());
            assertEquals(1, projectedCheckIn.getSleepRecordedDays());
            assertEquals(checkIn.energyScore, projectedCheckIn.getLatestEnergyScore());
            assertEquals(checkIn.trainingReadinessScore, projectedCheckIn.getLatestReadinessScore());

            assertThrows(IllegalStateException.class, () ->
                    repository.saveCheckIn(new AccountScope("recovery-owner-b"), checkIn));
        } finally {
            room.close();
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static IsolatedDatabaseContext isolatedContext() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext());
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
        public boolean deleteDatabase(String name) {
            return SQLiteDatabase.deleteDatabase(getDatabasePath(name));
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
    }
}
