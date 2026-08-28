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
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class DiningOutMultiMenuTest {
    private static final String DATABASE_PREFIX = "dining_out_multi_menu_";
    private static final String USER_ID = "dining-out-multi-user";

    @Test
    public void oneMealRecordStoresMultipleMenusAndEachComponentSnapshot() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            DiningOutOption addOn = DiningOutOption.grouped(
                    "날치알 추가",
                    NutritionProfile.ofMacros(80, 3, 2, 5),
                    null,
                    null,
                    "add_on_1",
                    CompositionGroupType.ADD_ON.value(),
                    CompositionGroupType.ADD_ON.label(),
                    DiningOutOption.DEFAULT_ROLE,
                    null
            );
            DiningOutOption side = DiningOutOption.grouped(
                    "감자튀김",
                    NutritionProfile.ofMacros(300, 4, 40, 12),
                    null,
                    null,
                    "side_1",
                    CompositionGroupType.SIDE.value(),
                    CompositionGroupType.SIDE.label(),
                    DiningOutOption.DEFAULT_ROLE,
                    null
            );
            MealMenuSelection first = MealMenuSelection.diningOut(
                    MealCompositionItem.from(food("menu-1", "육회비빔밥", 600), 1),
                    USER_ID,
                    "테스트 식당",
                    Collections.singletonList(addOn)
            );
            MealMenuSelection second = MealMenuSelection.diningOut(
                    MealCompositionItem.from(food("menu-2", "생연어 2P", 250), 1),
                    USER_ID,
                    "테스트 식당",
                    Collections.singletonList(side)
            );
            MealMenuSelection third = MealMenuSelection.diningOut(
                    MealCompositionItem.from(food("menu-3", "김치찌개", 400), 1),
                    USER_ID,
                    "테스트 식당",
                    Collections.singletonList(DiningOutOption.descriptive("김치"))
            );

            String recordId = repository.addDiningOutMealAtTimeWithMenusAndConsumption(
                    LocalDate.now().minusDays(1).toString(),
                    "12:30",
                    "테스트 식당",
                    "영등포점",
                    null,
                    Arrays.asList(first, second, third),
                    1d,
                    DiningOutConsumption.equalByDiners(1)
            );
            SQLiteDatabase database = helper.getReadableDatabase();
            assertEquals(3, count(database,
                    "SELECT COUNT(*) FROM meal_record_items WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals(3, count(database,
                    "SELECT COUNT(*) FROM meal_record_item_components WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals(1, count(database,
                    "SELECT COUNT(*) FROM meal_record_item_components " +
                            "WHERE meal_record_id = '" + recordId + "' " +
                            "AND composition_group_type_snapshot = 'add_on'"));
            assertEquals(3, count(database,
                    "SELECT COUNT(*) FROM meal_record_item_consumptions WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals(1, count(database,
                    "SELECT COUNT(*) FROM meal_record_item_components " +
                            "WHERE meal_record_id = '" + recordId + "' " +
                            "AND food_name_snapshot = '김치' " +
                            "AND calories IS NULL AND protein_grams IS NULL " +
                            "AND carbs_grams IS NULL AND fat_grams IS NULL"));
            FitnessRepository.MealComponentEntry unknown =
                    repository.mealComponentsForItem(
                            repository.mealItemsForRecord(recordId).get(2).id
                    ).get(0);
            assertNull(unknown.calories);
            assertFalse(unknown.profile.isKnown(NutritionProfile.CALORIES_KCAL));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void addOnCanLinkToManyMenusButSideCannotCreatePermanentLink() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper, USER_ID, SupabaseConfig.empty()
            );
            NutritionFood menuA = catalog.saveDiningOutMenu("식당", "메뉴 A", 40d, 20d, 10d);
            NutritionFood menuB = catalog.saveDiningOutMenu("식당", "메뉴 B", 30d, 15d, 8d);
            NutritionFood addOn = catalog.saveDiningOutOption(
                    "식당", "메뉴 A", DiningOutOption.grouped(
                            "날치알 추가",
                            NutritionProfile.ofMacros(80, 3, 2, 5),
                            null, null, "add_on_1", "add_on", "추가 구성",
                            DiningOutOption.DEFAULT_ROLE, null
                    )
            );
            NutritionFood side = catalog.saveDiningOutOption(
                    "식당", "메뉴 A", DiningOutOption.grouped(
                            "감자튀김",
                            NutritionProfile.ofMacros(300, 4, 40, 12),
                            null, null, "side_1", "side", "사이드",
                            DiningOutOption.DEFAULT_ROLE, null
                    )
            );
            catalog.linkDiningOutAddOnToMenu(menuA.id, addOn.id);
            catalog.linkDiningOutAddOnToMenu(menuB.id, addOn.id);

            assertEquals(1, catalog.diningOutAddOnsForMenu(menuA.id).size());
            assertEquals(1, catalog.diningOutAddOnsForMenu(menuB.id).size());
            assertThrows(IllegalArgumentException.class,
                    () -> catalog.linkDiningOutAddOnToMenu(menuA.id, side.id));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void unknownRestaurantOptionIsReusableWithoutZeroNutritionOrMenuDuplicate() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper, USER_ID, SupabaseConfig.empty()
            );
            DiningOutIdentity menuAIdentity = DiningOutIdentity.fromPriceTrace(
                    "11111111-1111-4111-8111-111111111111",
                    "식당",
                    "22222222-2222-4222-8222-222222222222",
                    "본점",
                    "33333333-3333-4333-8333-333333333333",
                    "메뉴 A",
                    "44444444-4444-4444-8444-444444444444"
            );
            DiningOutIdentity menuBIdentity = DiningOutIdentity.fromPriceTrace(
                    "11111111-1111-4111-8111-111111111111",
                    "식당",
                    "22222222-2222-4222-8222-222222222222",
                    "본점",
                    "55555555-5555-4555-8555-555555555555",
                    "메뉴 B",
                    "66666666-6666-4666-8666-666666666666"
            );
            NutritionFood first = catalog.saveDiningOutOption(
                    "식당", "메뉴 A", menuAIdentity, DiningOutOption.descriptive("김치")
            );
            NutritionFood second = catalog.saveDiningOutOption(
                    "식당", "메뉴 B", menuBIdentity, DiningOutOption.descriptive("김치")
            );

            assertEquals(first.id, second.id);
            assertEquals(1, catalog.savedDiningOutOptions(
                    "식당", null, "김치", 20
            ).size());
            assertEquals(false, second.profile.isKnown(NutritionProfile.CALORIES_KCAL));
            assertEquals(0, count(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM nutrition_foods WHERE id = '" + first.id +
                            "' AND (calories_kcal = 0 OR protein_grams = 0 OR " +
                            "carbs_grams = 0 OR fat_grams = 0)"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void legacySingleMenuWriterStillStoresOneTopLevelItem() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.addDiningOutMealAtTimeWithConsumption(
                    LocalDate.now().toString(),
                    "18:00",
                    "식당",
                    "본점",
                    "메뉴",
                    500,
                    20d,
                    50d,
                    15d,
                    null,
                    null,
                    null,
                    null,
                    MealCompositionItem.from(food("legacy-menu", "메뉴", 500), 1),
                    Collections.emptyList(),
                    1d,
                    DiningOutConsumption.equalByDiners(1),
                    true
            );
            assertEquals(1, count(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM meal_record_items WHERE meal_record_id = '" +
                            recordId + "'"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void partialNutritionKeepsEnteredCaloriesWhenExtendedValuesAreUnknown() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            NutritionFood menuFood = food("partial-menu", "칼로리 입력 메뉴", 999);
            String date = LocalDate.now().minusDays(2).toString();
            String recordId = repository.addDiningOutMealAtTimeWithMenusAndConsumption(
                    date,
                    "12:00",
                    "식당",
                    "본점",
                    null,
                    Collections.singletonList(MealMenuSelection.standalone(
                            MealCompositionItem.from(menuFood, 1)
                    )),
                    1d,
                    DiningOutConsumption.equalByDiners(1)
            );

            FitnessRepository.MealEntry entry = repository.mealEntriesForDate(date).get(0);
            assertEquals(recordId, entry.id);
            assertEquals(999, entry.calories);
            assertEquals(999d, repository.mealItemsForRecord(recordId).get(0)
                    .profile.value(NutritionProfile.CALORIES_KCAL), 0.001d);
            assertFalse(repository.mealItemsForRecord(recordId).get(0).profile
                    .isKnown(NutritionProfile.SODIUM_MG));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void partialNutritionCanBeSavedAsReusableMenuWithoutFillingUnknownExtendedValues() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper, USER_ID, SupabaseConfig.empty()
            );
            NutritionFood saved = catalog.saveDiningOutMenuWithNutrition(
                    "식당",
                    "부분 영양 메뉴",
                    999,
                    20d,
                    50d,
                    15d,
                    null,
                    null,
                    null
            );

            assertEquals(NutritionFood.DATA_VERSION_MACROS_ONLY, saved.dataVersion);
            assertEquals(999d, saved.profile.value(NutritionProfile.CALORIES_KCAL), 0.001d);
            assertFalse(saved.profile.isKnown(NutritionProfile.SODIUM_MG));
            try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                    "SELECT calories_kcal, sodium_mg, sugars_grams, saturated_fat_grams " +
                            "FROM nutrition_foods WHERE id = ?",
                    new String[]{saved.id}
            )) {
                assertTrue(cursor.moveToFirst());
                assertEquals(999d, cursor.getDouble(0), 0.001d);
                assertTrue(cursor.isNull(1));
                assertTrue(cursor.isNull(2));
                assertTrue(cursor.isNull(3));
            }
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void detailedTotalsIncludeMenuAndNutritionBearingAddOnWithIndependentFractions() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            NutritionProfile menuProfile = NutritionProfile.builder()
                    .value(NutritionProfile.CALORIES_KCAL, 500d)
                    .value(NutritionProfile.PROTEIN_GRAMS, 30d)
                    .value(NutritionProfile.CARBS_GRAMS, 50d)
                    .value(NutritionProfile.FAT_GRAMS, 20d)
                    .value(NutritionProfile.SODIUM_MG, 700d)
                    .value(NutritionProfile.SUGARS_GRAMS, 10d)
                    .value(NutritionProfile.SATURATED_FAT_GRAMS, 8d)
                    .build();
            NutritionProfile addOnProfile = NutritionProfile.builder()
                    .value(NutritionProfile.CALORIES_KCAL, 80d)
                    .value(NutritionProfile.PROTEIN_GRAMS, 3d)
                    .value(NutritionProfile.CARBS_GRAMS, 4d)
                    .value(NutritionProfile.FAT_GRAMS, 5d)
                    .value(NutritionProfile.SODIUM_MG, 100d)
                    .value(NutritionProfile.SUGARS_GRAMS, 2d)
                    .value(NutritionProfile.SATURATED_FAT_GRAMS, 3d)
                    .build();
            DiningOutOption addOn = DiningOutOption.grouped(
                    "날치알 추가",
                    addOnProfile,
                    "add-on",
                    null,
                    "add_on",
                    CompositionGroupType.ADD_ON.value(),
                    CompositionGroupType.ADD_ON.label(),
                    DiningOutOption.DEFAULT_ROLE,
                    null,
                    0.5d
            );
            String date = LocalDate.now().minusDays(3).toString();
            String recordId = repository.addDiningOutMealAtTimeWithMenusAndConsumption(
                    date,
                    "12:00",
                    "식당",
                    "본점",
                    null,
                    Collections.singletonList(MealMenuSelection.diningOut(
                            MealCompositionItem.from(foodWithProfile(
                                    "complete-menu", "메뉴", menuProfile
                            ), 1),
                            USER_ID,
                            "식당",
                            Collections.singletonList(addOn)
                    )),
                    1d,
                    DiningOutConsumption.equalByDiners(2)
            );

            NutritionTotals totals = repository.mealNutritionTotalsForDate(date);
            assertEquals(recordId, repository.mealEntriesForDate(date).get(0).id);
            assertEquals(290d, totals.total(NutritionProfile.CALORIES_KCAL).completeValue(), 0.001d);
            assertEquals(16.5d, totals.total(NutritionProfile.PROTEIN_GRAMS).completeValue(), 0.001d);
            assertEquals(27d, totals.total(NutritionProfile.CARBS_GRAMS).completeValue(), 0.001d);
            assertEquals(12.5d, totals.total(NutritionProfile.FAT_GRAMS).completeValue(), 0.001d);
            assertEquals(400d, totals.total(NutritionProfile.SODIUM_MG).completeValue(), 0.001d);
            assertEquals(6d, totals.total(NutritionProfile.SUGARS_GRAMS).completeValue(), 0.001d);
            assertEquals(5.5d, totals.total(
                    NutritionProfile.SATURATED_FAT_GRAMS).completeValue(), 0.001d);
            assertTrue(repository.mealComponentsForItem(
                    repository.mealItemsForRecord(recordId).get(0).id
            ).get(0).profile.isKnown(NutritionProfile.SODIUM_MG));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static NutritionFood foodWithProfile(
            String id,
            String name,
            NutritionProfile profile
    ) {
        return NutritionFood.builder()
                .id(id)
                .ownerId(USER_ID)
                .name(name)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1, NutritionUnit.SERVING)
                .profile(profile)
                .source("manual_estimate", null)
                .build();
    }

    private static NutritionFood food(String id, String name, double calories) {
        return NutritionFood.builder()
                .id(id)
                .ownerId(USER_ID)
                .name(name)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1, NutritionUnit.SERVING)
                .profile(NutritionProfile.ofMacros(calories, calories / 20, calories / 12, calories / 30))
                .source("manual_estimate", null)
                .build();
    }

    private static int count(SQLiteDatabase database, String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            if (!cursor.moveToFirst()) {
                throw new AssertionError("Expected one row for: " + sql);
            }
            return cursor.getInt(0);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private IsolatedDatabaseContext(Context base) { super(base); }

        @Override public File getDatabasePath(String name) {
            return super.getDatabasePath(DATABASE_PREFIX + name);
        }

        @Override public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }

        @Override public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler) {
            return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory,
                    SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler);
        }

        @Override public boolean deleteDatabase(String name) {
            return SQLiteDatabase.deleteDatabase(getDatabasePath(name));
        }
    }
}
