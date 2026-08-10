package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class FitnessRepositoryMealTimeTest {
    private static final String DATABASE_PREFIX = "repository_meal_time_";
    private static final String USER_ID = "meal-time-test-user";

    @Test
    public void addAndUpdateMealTimePreserveMetadataAndBuildCompactPreview() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            List<MealCompositionItem> items = Arrays.asList(
                    MealCompositionItem.from(food("food-1", "닭가슴살", 165, 31, 0, 3.6), 100),
                    MealCompositionItem.from(food("food-2", "현미밥", 110, 2.6, 23, 0.9), 100)
            );
            NutritionTotals totals = NutritionCalculator.sum(items);
            String date = LocalDate.now().minusDays(1).toString();

            String recordId = repository.addMealAtTime(
                    date,
                    "7:05",
                    (int) Math.round(totals.calories()),
                    totals.proteinGrams(),
                    totals.carbsGrams(),
                    totals.fatGrams(),
                    items
            );

            FitnessRepository.MealEntry entry = repository.mealEntriesForDate(date).get(0);
            assertEquals(recordId, entry.id);
            assertEquals("닭가슴살 외 1건", entry.previewTitle);
            assertEquals("07:05", entry.mealTime);
            assertEquals(
                    "07:05 · " + MealEntryPolicy.macroRatioLabel(
                            totals.carbsGrams(),
                            totals.proteinGrams(),
                            totals.fatGrams()
                    ),
                    entry.previewSubtitle()
            );
            assertTrue(entry.timeEditable);

            JSONObject before = metadata(helper.getWritableDatabase(), recordId);
            assertTrue(before.getString("eaten_at").startsWith(date + "T07:05"));
            assertEquals(2, before.getInt("item_count"));

            assertTrue(repository.updateMealTime(recordId, "18:40"));
            FitnessRepository.MealEntry updated = repository.mealEntriesForDate(date).get(0);
            assertEquals("18:40", updated.mealTime);
            JSONObject after = metadata(helper.getWritableDatabase(), recordId);
            assertTrue(after.getString("eaten_at").startsWith(date + "T18:40"));
            assertEquals(2, after.getInt("item_count"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void timeEditIsRejectedForRowsOwnedByAnotherDevice() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addMealAtTime(
                    date,
                    "08:00",
                    100,
                    10d,
                    10d,
                    2d,
                    java.util.Collections.emptyList()
            );

            ContentValues values = new ContentValues();
            values.put("device_id", "personal-os");
            helper.getWritableDatabase().update(
                    "meal_records",
                    values,
                    "id = ?",
                    new String[]{recordId}
            );

            assertFalse(repository.updateMealTime(recordId, "09:30"));
            FitnessRepository.MealEntry entry = repository.mealEntriesForDate(date).get(0);
            assertEquals("08:00", entry.mealTime);
            assertFalse(entry.timeEditable);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void composedMenuStoresOneMenuAndImmutableIngredientSnapshots() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            List<MealCompositionItem> ingredients = Arrays.asList(
                    MealCompositionItem.from(food("rice", "Rice", 130, 2.4, 28, 0.3), 200),
                    MealCompositionItem.from(food("egg", "Egg", 143, 13, 0.7, 9.5), 150)
            );
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    com.yeonsik.fitnessapp.config.SupabaseConfig.empty()
            );
            NutritionFood friedRice = catalog.buildRecipeForMeal("Fried rice", ingredients);
            MealMenuSelection menu = MealMenuSelection.composed(
                    MealCompositionItem.from(friedRice, 1),
                    ingredients
            );
            NutritionTotals totals = NutritionCalculator.sum(
                    Collections.singletonList(menu.menu)
            );

            String recordId = repository.addMealMenusAtTime(
                    LocalDate.now().toString(),
                    "12:10",
                    (int) Math.round(totals.calories()),
                    totals.proteinGrams(),
                    totals.carbsGrams(),
                    totals.fatGrams(),
                    Collections.singletonList(menu)
            );

            SQLiteDatabase database = helper.getReadableDatabase();
            assertEquals("1", scalar(database,
                    "SELECT COUNT(*) FROM meal_record_items WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals("2", scalar(database,
                    "SELECT COUNT(*) FROM meal_record_item_components WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals("Fried rice", scalar(database,
                    "SELECT food_name_snapshot FROM meal_record_items WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals("2", metadata(database, recordId).getString("composition_version"));
            List<FitnessRepository.MealItemEntry> storedMenus =
                    repository.mealItemsForRecord(recordId);
            assertEquals(1, storedMenus.size());
            List<FitnessRepository.MealComponentEntry> storedComponents =
                    repository.mealComponentsForItem(storedMenus.get(0).id);
            assertEquals(2, storedComponents.size());
            assertEquals("Rice", storedComponents.get(0).foodName);
            assertEquals("Fried rice", repository.mealEntriesForDate(
                    LocalDate.now().toString()
            ).get(0).previewTitle);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static NutritionFood food(
            String id,
            String name,
            double calories,
            double protein,
            double carbs,
            double fat
    ) {
        return new NutritionFood(
                id,
                USER_ID,
                name,
                NutritionFood.KIND_INGREDIENT,
                100,
                NutritionUnit.GRAM,
                calories,
                protein,
                carbs,
                fat,
                "test",
                null
        );
    }

    private static JSONObject metadata(SQLiteDatabase database, String recordId) throws Exception {
        try (Cursor cursor = database.rawQuery(
                "SELECT metadata FROM meal_records WHERE id = ?",
                new String[]{recordId}
        )) {
            assertTrue(cursor.moveToFirst());
            return new JSONObject(cursor.getString(0));
        }
    }

    private static String scalar(SQLiteDatabase database, String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
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
