package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.config.SupabaseConfig;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public final class DiningOutFulfillmentModeTest {
    private static final String DATABASE_PREFIX = "dining_out_fulfillment_";
    private static final String USER_ID = "dining-out-fulfillment-user";

    @Test
    public void exposesOnlyStableThreeFulfillmentModes() {
        assertEquals(3, DiningOutFulfillmentMode.values().length);
        assertArrayEquals(
                new String[]{"dine_in", "delivery", "takeout"},
                new String[]{
                        DiningOutFulfillmentMode.DINE_IN.value(),
                        DiningOutFulfillmentMode.DELIVERY.value(),
                        DiningOutFulfillmentMode.TAKEOUT.value()
                }
        );
        assertEquals(
                DiningOutFulfillmentMode.DELIVERY.value(),
                DiningOutFulfillmentMode.normalize(" delivery ")
        );
        assertEquals(
                DiningOutFulfillmentMode.TAKEOUT.value(),
                DiningOutFulfillmentMode.normalize("포장")
        );
        assertNull(DiningOutFulfillmentMode.normalize("주문번호"));
        assertThrows(
                IllegalArgumentException.class,
                () -> DiningOutFulfillmentMode.require("앱 주문")
        );
    }

    @Test
    public void rejectsUnselectedFulfillmentModeForNewSave() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DiningOutFulfillmentMode.require(null)
        );
    }

    @Test
    public void storesFulfillmentModeOnActualMealAndExposesItOnRead() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    SupabaseConfig.empty()
            );
            NutritionFood menu = catalog.saveDiningOutMenuWithNutrition(
                    "식당 A",
                    "메뉴 A",
                    completeProfile(),
                    null,
                    null
            );
            FitnessRepository records = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = records.addDiningOutMealAtTimeWithMenusAndConsumption(
                    date,
                    "12:00",
                    "식당 A",
                    null,
                    null,
                    DiningOutFulfillmentMode.DELIVERY.value(),
                    Collections.singletonList(MealMenuSelection.diningOut(
                            MealCompositionItem.from(menu, 1d),
                            USER_ID,
                            "식당 A",
                            Collections.emptyList()
                    )),
                    1d,
                    DiningOutConsumption.equalByDiners(1)
            );

            List<FitnessRepository.MealEntry> entries = records.mealEntriesForDate(date);
            assertEquals(1, entries.size());
            assertEquals(
                    DiningOutFulfillmentMode.DELIVERY.value(),
                    entries.get(0).fulfillmentMode
            );
            assertEquals("12:00 · 외식 · 배달 · 영양 추정", entries.get(0).previewSubtitle());
            try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                    "SELECT fulfillment_mode FROM meal_records WHERE id = ?",
                    new String[]{recordId}
            )) {
                assertEquals(true, cursor.moveToFirst());
                assertEquals(
                        DiningOutFulfillmentMode.DELIVERY.value(),
                        cursor.getString(0)
                );
            }
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void fulfillmentModeDoesNotSplitMenuCatalogAndLegacyRowsRemainNullable() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    SupabaseConfig.empty()
            );
            NutritionFood menu = catalog.saveDiningOutMenuWithNutrition(
                    "식당 A",
                    "메뉴 A",
                    completeProfile(),
                    null,
                    null
            );
            FitnessRepository records = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            List<MealMenuSelection> selections = Collections.singletonList(
                    MealMenuSelection.diningOut(
                            MealCompositionItem.from(menu, 1d),
                            USER_ID,
                            "식당 A",
                            Collections.emptyList()
                    )
            );
            records.addDiningOutMealAtTimeWithMenusAndConsumption(
                    date,
                    "12:00",
                    "식당 A",
                    null,
                    null,
                    DiningOutFulfillmentMode.DINE_IN.value(),
                    selections,
                    1d,
                    DiningOutConsumption.equalByDiners(1)
            );
            records.addDiningOutMealAtTimeWithMenusAndConsumption(
                    date,
                    "13:00",
                    "식당 A",
                    null,
                    null,
                    DiningOutFulfillmentMode.TAKEOUT.value(),
                    selections,
                    1d,
                    DiningOutConsumption.equalByDiners(1)
            );
            // The source-compatible overload represents a legacy record with no mode.
            records.addDiningOutMealAtTimeWithMenusAndConsumption(
                    date,
                    "14:00",
                    "식당 A",
                    null,
                    null,
                    selections,
                    1d,
                    DiningOutConsumption.equalByDiners(1)
            );

            assertEquals(1, catalog.savedDiningOutMenus().size());
            List<FitnessRepository.MealEntry> entries = records.mealEntriesForDate(date);
            assertEquals(3, entries.size());
            assertEquals(DiningOutFulfillmentMode.DINE_IN.value(), entries.get(0).fulfillmentMode);
            assertEquals(DiningOutFulfillmentMode.TAKEOUT.value(), entries.get(1).fulfillmentMode);
            assertNull(entries.get(2).fulfillmentMode);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static NutritionProfile completeProfile() {
        return NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, 500d)
                .value(NutritionProfile.PROTEIN_GRAMS, 20d)
                .value(NutritionProfile.CARBS_GRAMS, 60d)
                .value(NutritionProfile.FAT_GRAMS, 15d)
                .value(NutritionProfile.SODIUM_MG, 700d)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, 5d)
                .value(NutritionProfile.SUGARS_GRAMS, 8d)
                .build();
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
