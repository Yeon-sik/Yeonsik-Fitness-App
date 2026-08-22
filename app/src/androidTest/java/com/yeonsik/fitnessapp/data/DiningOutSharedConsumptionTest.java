package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class DiningOutSharedConsumptionTest {
    private static final String DATABASE_PREFIX = "dining_out_shared_";
    private static final String USER_ID = "dining-out-shared-test-user";

    @Test
    public void legacyRecordsKeepHistoricalAggregationAndNewRecordsUsePersonalShare()
            throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String legacyDate = LocalDate.now().minusDays(2).toString();
            String newDate = LocalDate.now().minusDays(1).toString();
            DiningOutOption option = DiningOutOption.withProfile(
                    "당면",
                    NutritionProfile.ofMacros(200, 10, 30, 5)
            );

            String legacyId = repository.addDiningOutMealAtTimeWithBranchAndNutritionAndOptionNutrition(
                    legacyDate,
                    "18:00",
                    "레거시 식당",
                    "본점",
                    "해물찜 2인",
                    1000,
                    50d,
                    100d,
                    20d,
                    1000d,
                    10d,
                    5d,
                    null,
                    Collections.singletonList(option)
            );
            String newId = repository.addDiningOutMealAtTimeWithConsumption(
                    newDate,
                    "19:00",
                    "새 식당",
                    "본점",
                    "해물찜 2인",
                    1000,
                    50d,
                    100d,
                    20d,
                    1000d,
                    10d,
                    5d,
                    null,
                    null,
                    Collections.singletonList(option),
                    2d,
                    DiningOutConsumption.equalByDiners(2),
                    true
            );

            assertEquals(1200d, repository.mealNutritionTotalsForDate(legacyDate).calories(), 0.001d);
            assertEquals(500d, repository.mealNutritionTotalsForDate(newDate).calories(), 0.001d);
            assertEquals(500d, repository.mealNutritionForDate(newDate).calories, 0.001d);
            assertEquals(1000d, scalarDouble(
                    helper.getReadableDatabase(),
                    "SELECT calories FROM meal_record_items WHERE meal_record_id = ?",
                    newId
            ), 0.001d);
            assertEquals(1L, scalarLong(
                    helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM meal_record_item_consumptions "
                            + "WHERE meal_record_id = ? AND deleted_at IS NULL",
                    newId
            ));
            assertEquals(0L, scalarLong(
                    helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM meal_record_item_consumptions "
                            + "WHERE meal_record_id = ?",
                    legacyId
            ));
            assertEquals(
                    DiningOutConsumption.CONTRACT_VERSION,
                    scalar(
                            helper.getReadableDatabase(),
                            "SELECT nutrition_calculation_contract FROM meal_records WHERE id = ?",
                            newId
                    )
            );
            assertNull(scalarNullable(
                    helper.getReadableDatabase(),
                    "SELECT nutrition_calculation_contract FROM meal_records WHERE id = ?",
                    legacyId
            ));
            assertEquals(2d, scalarDouble(
                    helper.getReadableDatabase(),
                    "SELECT nominal_servings_snapshot FROM meal_record_items "
                            + "WHERE meal_record_id = ?",
                    newId
            ), 0.001d);
            assertEquals(0.5d, repository.diningOutConsumptionForRecord(newId).consumedFraction, 0.001d);

            assertTrue(repository.deleteMeal(newId));
            assertEquals(1L, scalarLong(
                    helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM meal_record_item_consumptions "
                            + "WHERE meal_record_id = ? AND deleted_at IS NOT NULL",
                    newId
            ));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static String scalar(SQLiteDatabase database, String sql, String arg) {
        try (Cursor cursor = database.rawQuery(sql, new String[]{arg})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private static String scalarNullable(SQLiteDatabase database, String sql, String arg) {
        try (Cursor cursor = database.rawQuery(sql, new String[]{arg})) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getString(0);
        }
    }

    private static double scalarDouble(SQLiteDatabase database, String sql, String arg) {
        try (Cursor cursor = database.rawQuery(sql, new String[]{arg})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getDouble(0);
        }
    }

    private static long scalarLong(SQLiteDatabase database, String sql, String arg) {
        try (Cursor cursor = database.rawQuery(sql, new String[]{arg})) {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
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
