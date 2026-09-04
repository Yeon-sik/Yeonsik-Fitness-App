package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
    public void diningOutStoresStoreAndMenuSeparatelyWithoutFoodSnapshotRows() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTime(
                    date,
                    "19:20",
                    "강남식당",
                    "제육볶음"
            );

            FitnessRepository.MealEntry entry = repository.mealEntriesForDate(date).get(0);
            assertEquals(recordId, entry.id);
            assertTrue(entry.isDiningOut());
            assertEquals("강남식당", entry.storeName);
            assertEquals("제육볶음", entry.menuName);
            assertEquals("강남식당 · 제육볶음", entry.previewTitle);
            assertEquals("19:20 · 외식 · 영양 미입력", entry.previewSubtitle());
            assertEquals(recordId, repository.recentDiningOutEntries(10).get(0).id);
            assertEquals(0, entry.compositionCount);
            assertEquals("dining_out", scalar(helper.getReadableDatabase(),
                    "SELECT meal_kind FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals("강남식당", scalar(helper.getReadableDatabase(),
                    "SELECT store_name FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals("제육볶음", scalar(helper.getReadableDatabase(),
                    "SELECT menu_name FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals("0", scalar(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM meal_record_items WHERE meal_record_id = '" + recordId + "'"));
            assertEquals("dining_out", metadata(helper.getReadableDatabase(), recordId)
                    .getString("meal_kind"));
            assertEquals("강남식당", metadata(helper.getReadableDatabase(), recordId)
                    .getString("store_name"));
            assertEquals("제육볶음", metadata(helper.getReadableDatabase(), recordId)
                    .getString("menu_name"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void directlyRegisteredDiningOutStoresEditableBranchWithoutPriceTraceIdentity()
            throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTimeWithBranchAndOptionNutrition(
                    date,
                    "19:20",
                    "강남식당",
                    "테스트 지점",
                    "제육볶음",
                    70d,
                    40d,
                    20d,
                    null,
                    Collections.emptyList()
            );

            FitnessRepository.MealEntry entry = repository.mealEntriesForDate(date).get(0);
            assertEquals("강남식당 · 테스트 지점 · 제육볶음", entry.previewTitle);
            assertEquals("테스트 지점", scalar(helper.getReadableDatabase(),
                    "SELECT branch_name FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals("테스트 지점", metadata(helper.getReadableDatabase(), recordId)
                    .getString("branch_name"));
            assertEquals("1", scalar(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM meal_records WHERE id = '" + recordId
                            + "' AND restaurant_id IS NULL AND restaurant_location_id IS NULL"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void knownDiningOutStoreUsesYeongdeungpoDefaultWhenDirectBranchIsMissing()
            throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTimeWithBranchAndOptionNutrition(
                    date,
                    "19:20",
                    "고향 엄마손 생바지락 칼국수",
                    "null",
                    "얼큰 바지락 칼국수",
                    70d,
                    40d,
                    20d,
                    null,
                    Collections.emptyList()
            );

            FitnessRepository.MealEntry entry = repository.mealEntriesForDate(date).get(0);
            assertEquals("고향 엄마손 생바지락 칼국수 · 영등포점 · 얼큰 바지락 칼국수",
                    entry.previewTitle);
            assertEquals("영등포점", scalar(helper.getReadableDatabase(),
                    "SELECT branch_name FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals("영등포점", metadata(helper.getReadableDatabase(), recordId)
                    .getString("branch_name"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void diningOutStoresUserEstimatedMacrosAndCalculatedCalories() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTime(
                    date,
                    "19:20",
                    "강남식당",
                    "제육볶음",
                    70d,
                    40d,
                    20d
            );

            FitnessRepository.MealEntry entry = repository.mealEntriesForDate(date).get(0);
            assertEquals(recordId, entry.id);
            assertTrue(entry.hasEstimatedNutrition());
            assertEquals(70d, entry.carbsGrams, 0.001d);
            assertEquals(40d, entry.proteinGrams, 0.001d);
            assertEquals(20d, entry.fatGrams, 0.001d);
            assertEquals(620, entry.calories);
            assertEquals("19:20 · 외식 · 영양 추정", entry.previewSubtitle());
            assertEquals(620d, repository.mealNutritionForDate(date).calories, 0.001d);

            JSONObject metadata = metadata(helper.getReadableDatabase(), recordId);
            assertEquals("estimated", metadata.getString("nutrition_status"));
            assertEquals("manual_estimate", metadata.getString("nutrition_source"));
            assertTrue(metadata.getBoolean("estimated"));
            assertEquals("0", scalar(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM meal_record_items WHERE meal_record_id = '" + recordId + "'"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void diningOutStoresMenuOptionsInsideTheMealSnapshot() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTimeWithOptions(
                    date,
                    "19:20",
                    "텐진라면",
                    "텐진라멘",
                    70d,
                    40d,
                    20d,
                    null,
                    Arrays.asList("면 추가", "고기 추가", "면 추가")
            );

            List<FitnessRepository.MealItemEntry> menus = repository.mealItemsForRecord(recordId);
            assertEquals(1, menus.size());
            List<FitnessRepository.MealComponentEntry> options =
                    repository.mealComponentsForItem(menus.get(0).id);
            assertEquals(2, options.size());
            assertEquals("면 추가", options.get(0).foodName);
            assertEquals("고기 추가", options.get(1).foodName);
            assertEquals("2", metadata(helper.getReadableDatabase(), recordId)
                    .getString("option_count"));
            assertEquals(620d, repository.mealNutritionForDate(date).calories, 0.001d);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void nutrientBearingDiningOutOptionIsAddedToSnapshotTotal() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            DiningOutOption option = DiningOutOption.withProfile(
                    "고기 추가",
                    NutritionProfile.ofMacros(180, 16, 2, 11)
            );
            String recordId = repository.addDiningOutMealAtTimeWithOptionNutrition(
                    date,
                    "19:20",
                    "텐진라멘",
                    "텐진라멘",
                    70d,
                    40d,
                    20d,
                    null,
                    Collections.singletonList(option)
            );

            List<FitnessRepository.MealItemEntry> menus = repository.mealItemsForRecord(recordId);
            assertEquals(1, menus.size());
            List<FitnessRepository.MealComponentEntry> options =
                    repository.mealComponentsForItem(menus.get(0).id);
            assertEquals(1, options.size());
            assertEquals(180d, options.get(0).calories, 0.001d);
            assertEquals(800d, repository.mealNutritionTotalsForDate(date)
                    .total(NutritionProfile.CALORIES_KCAL).knownSum(), 0.001d);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void linkedDiningOutStoresExactPriceTraceIdentityAndBranchName() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            DiningOutIdentity identity = DiningOutIdentity.fromPriceTrace(
                    "11111111-1111-4111-8111-111111111111",
                    "텐진라면",
                    "22222222-2222-4222-8222-222222222222",
                    "public-receipt",
                    "gangnam-code",
                    "강남점",
                    "33333333-3333-4333-8333-333333333333",
                    "텐진라멘",
                    "44444444-4444-4444-8444-444444444444"
            );
            String recordId = repository.addDiningOutMealAtTimeWithIdentity(
                    date,
                    "19:20",
                    identity,
                    70d,
                    40d,
                    20d,
                    null,
                    Arrays.asList("면 추가")
            );

            assertEquals("강남점", scalar(helper.getReadableDatabase(),
                    "SELECT branch_name FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals(identity.restaurantId, scalar(helper.getReadableDatabase(),
                    "SELECT restaurant_id FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals(identity.restaurantLocationId, scalar(helper.getReadableDatabase(),
                    "SELECT restaurant_location_id FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals(identity.restaurantMenuId, scalar(helper.getReadableDatabase(),
                    "SELECT restaurant_menu_id FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals(identity.catalogProductId, scalar(helper.getReadableDatabase(),
                    "SELECT catalog_product_id FROM meal_records WHERE id = '" + recordId + "'"));
            JSONObject savedMetadata = metadata(helper.getReadableDatabase(), recordId);
            assertEquals(DiningOutIdentity.CONTRACT_VERSION,
                    savedMetadata.getString("identity_contract"));
            assertEquals(identity.restaurantLocationId,
                    savedMetadata.getString("restaurant_location_id"));
            assertEquals(DiningOutIdentity.NAMESPACE,
                    savedMetadata.getString("identity_namespace"));
            assertEquals(identity.locationSourceNamespace,
                    savedMetadata.getString("location_source_namespace"));
            assertEquals(identity.sourceLocationCode,
                    savedMetadata.getString("source_location_code"));
            assertEquals("강남점", savedMetadata.getString("branch_name"));
            assertEquals("텐진라멘", savedMetadata.getString("menu_name"));
            DiningOutIdentity restored = repository.diningOutIdentityForRecord(recordId);
            assertTrue(restored != null);
            assertEquals(identity.restaurantId, restored.restaurantId);
            assertEquals(identity.restaurantLocationId, restored.restaurantLocationId);
            assertEquals(identity.restaurantMenuId, restored.restaurantMenuId);
            assertEquals(identity.catalogProductId, restored.catalogProductId);
            assertEquals(identity.locationSourceNamespace, restored.locationSourceNamespace);
            assertEquals(identity.sourceLocationCode, restored.sourceLocationCode);
            assertEquals("강남점", restored.branchName);

            JSONObject legacyMetadata = new JSONObject(savedMetadata.toString());
            legacyMetadata.remove("location_source_namespace");
            legacyMetadata.put("identity_namespace", "public-receipt");
            ContentValues legacyValues = new ContentValues();
            legacyValues.put("metadata", legacyMetadata.toString());
            assertEquals(1, helper.getWritableDatabase().update(
                    "meal_records",
                    legacyValues,
                    "id = ?",
                    new String[]{recordId}
            ));
            DiningOutIdentity legacyRestored = repository.diningOutIdentityForRecord(recordId);
            assertTrue(legacyRestored != null);
            assertEquals(DiningOutIdentity.NAMESPACE,
                    new JSONObject(legacyRestored.metadataJson()).getString("namespace"));
            assertEquals("public-receipt", legacyRestored.locationSourceNamespace);
            assertEquals("gangnam-code", legacyRestored.sourceLocationCode);
            assertEquals(recordId, repository.recentDiningOutEntries(10).get(0).id);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void savedDiningOutMenuIsCatalogedAndSnapshottedIntoTheMeal() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    com.yeonsik.fitnessapp.config.SupabaseConfig.empty()
            );
            NutritionFood savedMenu = catalog.saveDiningOutMenu(
                    "강남식당",
                    "제육볶음",
                    70d,
                    40d,
                    20d,
                    "강남점",
                    null
            );
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTime(
                    date,
                    "19:20",
                    "강남식당",
                    "제육볶음",
                    70d,
                    40d,
                    20d,
                    MealCompositionItem.from(savedMenu, savedMenu.basisAmount)
            );

            SQLiteDatabase database = helper.getReadableDatabase();
            assertEquals("1", scalar(database,
                    "SELECT COUNT(*) FROM nutrition_foods WHERE id = '" + savedMenu.id + "'"));
            assertEquals("강남식당", scalar(database,
                    "SELECT brand FROM nutrition_foods WHERE id = '" + savedMenu.id + "'"));
            assertEquals("external_menu", scalar(database,
                    "SELECT kind FROM nutrition_foods WHERE id = '" + savedMenu.id + "'"));
            assertEquals("manual_estimate", scalar(database,
                    "SELECT source_type FROM nutrition_foods WHERE id = '" + savedMenu.id + "'"));
            JSONObject savedSource = new JSONObject(savedMenu.sourceReference);
            assertEquals("강남점", savedSource.getString("branch_name"));
            List<NutritionFood> savedDiningOutMenus = catalog.savedDiningOutMenus();
            assertEquals(1, savedDiningOutMenus.size());
            assertEquals(savedMenu.id, savedDiningOutMenus.get(0).id);
            assertEquals(recordId, repository.recentDiningOutEntries(10).get(0).id);
            assertEquals("1", scalar(database,
                    "SELECT COUNT(*) FROM meal_record_items WHERE meal_record_id = '" + recordId + "'"));
            assertEquals(savedMenu.id, scalar(database,
                    "SELECT food_id FROM meal_record_items WHERE meal_record_id = '" + recordId + "'"));
            assertEquals("제육볶음", scalar(database,
                    "SELECT food_name_snapshot FROM meal_record_items WHERE meal_record_id = '" + recordId + "'"));
            assertEquals("1", metadata(database, recordId).getString("item_count"));
            assertEquals(1, repository.mealItemsForRecord(recordId).size());
            assertEquals(1, repository.mealEntriesForDate(date).get(0).compositionCount);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void completeDiningOutNutritionIsStoredInCatalogAndMealSnapshot() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    com.yeonsik.fitnessapp.config.SupabaseConfig.empty()
            );
            NutritionFood savedMenu = catalog.saveDiningOutMenuWithNutrition(
                    "강남식당",
                    "제육볶음",
                    620,
                    40d,
                    70d,
                    20d,
                    900d,
                    12d,
                    8d
            );
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTimeWithNutrition(
                    date,
                    "19:20",
                    "강남식당",
                    "제육볶음",
                    620,
                    40d,
                    70d,
                    20d,
                    900d,
                    12d,
                    8d,
                    MealCompositionItem.from(savedMenu, savedMenu.basisAmount)
            );

            SQLiteDatabase database = helper.getReadableDatabase();
            assertEquals(620d, Double.parseDouble(scalar(database,
                    "SELECT calories_kcal FROM nutrition_foods WHERE id = '" + savedMenu.id + "'")),
                    0.001d);
            assertEquals(900d, Double.parseDouble(scalar(database,
                    "SELECT sodium_mg FROM nutrition_foods WHERE id = '" + savedMenu.id + "'")),
                    0.001d);
            assertEquals(12d, Double.parseDouble(scalar(database,
                    "SELECT sugars_grams FROM nutrition_foods WHERE id = '" + savedMenu.id + "'")),
                    0.001d);
            assertEquals(8d, Double.parseDouble(scalar(database,
                    "SELECT saturated_fat_grams FROM nutrition_foods WHERE id = '" + savedMenu.id + "'")),
                    0.001d);
            assertEquals(620d, Double.parseDouble(scalar(database,
                    "SELECT calories FROM meal_record_items WHERE meal_record_id = '" + recordId + "'")),
                    0.001d);
            assertEquals(900d, Double.parseDouble(scalar(database,
                    "SELECT sodium_mg FROM meal_record_items WHERE meal_record_id = '" + recordId + "'")),
                    0.001d);
            assertEquals(12d, Double.parseDouble(scalar(database,
                    "SELECT sugars_grams FROM meal_record_items WHERE meal_record_id = '" + recordId + "'")),
                    0.001d);
            assertEquals(8d, Double.parseDouble(scalar(database,
                    "SELECT saturated_fat_grams FROM meal_record_items WHERE meal_record_id = '" + recordId + "'")),
                    0.001d);

            NutritionTotals totals = repository.mealNutritionTotalsForDate(date);
            assertEquals(900d, totals.total(NutritionProfile.SODIUM_MG).knownSum(), 0.001d);
            assertEquals(12d, totals.total(NutritionProfile.SUGARS_GRAMS).knownSum(), 0.001d);
            assertEquals(8d, totals.total(NutritionProfile.SATURATED_FAT_GRAMS).knownSum(), 0.001d);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void canonicalDiningOutWriteRejectsMissingCaloriesInsteadOfEstimatingFromMacros()
            throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> repository.addDiningOutMealAtTimeWithNutrition(
                            date,
                            "19:20",
                            "강남식당",
                            "칼로리 누락 메뉴",
                            null,
                            40d,
                            70d,
                            20d,
                            null,
                            null,
                            null,
                            null
                    )
            );
            assertEquals(0, repository.mealEntriesForDate(date).size());
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
