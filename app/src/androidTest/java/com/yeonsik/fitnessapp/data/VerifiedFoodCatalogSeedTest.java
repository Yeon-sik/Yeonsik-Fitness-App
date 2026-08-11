package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class VerifiedFoodCatalogSeedTest {
    private static final String DATABASE_PREFIX = "verified_food_seed_";
    private static final String BROCCOLI_ID = "kfind:R106-092000001-0000";
    private static final String CHICKEN_BREAST_ID = "kfind:R209-008000501-0000";
    private static final String LEGACY_COOKED_CHICKEN_ID = "kfind:R209-008000551-0000";
    private static final String RAW_SALMON_ID = "kfind:R211-201174001-0000";
    private static final String RAW_TUNA_ID = "kfind:R211-059074001-0000";
    private static final String RAW_MACKEREL_ID = "kfind:R211-021014001-1208";
    private static final String GRILLED_SALMON_ID = "kfind:R211-201174050-0000";
    private static final String GRILLED_TUNA_ID = "kfind:R211-059074050-0000";
    private static final String GRILLED_MACKEREL_ID = "kfind:R211-021014050-7300";
    private static final String[] SEAFOOD_IDS = {
            RAW_SALMON_ID,
            RAW_TUNA_ID,
            RAW_MACKEREL_ID,
            GRILLED_SALMON_ID,
            GRILLED_TUNA_ID,
            GRILLED_MACKEREL_ID
    };
    private static final int EXPECTED_RAW_COUNT = 51;
    private static final int EXPECTED_GRILLED_COUNT = 3;
    private static final int EXPECTED_SEAFOOD_COUNT = 6;

    @Test
    public void freshDatabaseSeedsExactly54VerifiedFoodsWithCompleteProvenance() {
        Context isolatedContext = isolatedContext();
        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase database = helper.getWritableDatabase();

            assertEquals(VerifiedFoodCatalogSeed.EXPECTED_COUNT, countOfficialFoods(database));
            assertEquals(
                    VerifiedFoodCatalogSeed.EXPECTED_COUNT,
                    scalarInt(
                            database,
                            "SELECT COUNT(DISTINCT id) FROM nutrition_foods " +
                                    "WHERE source_type = ? AND deleted_at IS NULL",
                            VerifiedFoodCatalogSeed.SOURCE_TYPE
                    )
            );
            assertEquals(0, scalarInt(
                    database,
                    "SELECT COUNT(*) FROM nutrition_foods " +
                            "WHERE source_type = ? AND deleted_at IS NULL " +
                            "AND id NOT LIKE 'kfind:%'",
                    VerifiedFoodCatalogSeed.SOURCE_TYPE
            ));
            assertEquals(0, countInvalidBasis(database));
            assertEquals(0, countInvalidOwnership(database));
            assertEquals(1, countMissingRequiredExceptSugars(database));
            assertEquals(5, countMissingSugars(database));
            assertEquals(EXPECTED_SEAFOOD_COUNT, countMissingRequiredSeven(database));
            assertEquals(0, countIncompleteFoodsOutsideSeafood(database));
            assertEquals(0, countInvalidSourceReferences(database));
            assertEquals(0, countInvalidPreparation(database));
            assertEquals(
                    EXPECTED_RAW_COUNT,
                    countPreparation(
                            database,
                            NutritionFood.PREP_RAW,
                            NutritionFood.COOKING_METHOD_RAW
                    )
            );
            assertEquals(
                    EXPECTED_GRILLED_COUNT,
                    countPreparation(
                            database,
                            NutritionFood.PREP_COOKED,
                            NutritionFood.COOKING_METHOD_GRILLED
                    )
            );
            assertEquals(EXPECTED_SEAFOOD_COUNT, countSeafood(database));

            assertFoodMacros(
                    database,
                    CHICKEN_BREAST_ID,
                    106.0,
                    22.97,
                    0.0,
                    0.97,
                    NutritionFood.PREP_RAW,
                    NutritionFood.COOKING_METHOD_RAW
            );

            assertSeafoodMacros(
                    database,
                    RAW_SALMON_ID,
                    141.0,
                    22.5,
                    0.1,
                    4.5,
                    NutritionFood.PREP_RAW,
                    NutritionFood.COOKING_METHOD_RAW,
                    57.0,
                    0.81,
                    null
            );
            assertSeafoodMacros(
                    database,
                    RAW_TUNA_ID,
                    196.0,
                    23.99,
                    0.0,
                    8.09,
                    NutritionFood.PREP_RAW,
                    NutritionFood.COOKING_METHOD_RAW,
                    62.0,
                    3.05,
                    null
            );
            assertSeafoodMacros(
                    database,
                    RAW_MACKEREL_ID,
                    183.0,
                    20.2,
                    0.0,
                    10.4,
                    NutritionFood.PREP_RAW,
                    NutritionFood.COOKING_METHOD_RAW,
                    null,
                    null,
                    0.0
            );
            assertSeafoodMacros(
                    database,
                    GRILLED_SALMON_ID,
                    181.0,
                    28.5,
                    0.1,
                    6.0,
                    NutritionFood.PREP_COOKED,
                    NutritionFood.COOKING_METHOD_GRILLED,
                    72.0,
                    1.06,
                    null
            );
            assertSeafoodMacros(
                    database,
                    GRILLED_TUNA_ID,
                    204.0,
                    29.91,
                    0.0,
                    6.28,
                    NutritionFood.PREP_COOKED,
                    NutritionFood.COOKING_METHOD_GRILLED,
                    50.0,
                    1.61,
                    null
            );
            assertSeafoodMacros(
                    database,
                    GRILLED_MACKEREL_ID,
                    293.0,
                    25.2,
                    0.4,
                    22.4,
                    NutritionFood.PREP_COOKED,
                    NutritionFood.COOKING_METHOD_GRILLED,
                    120.0,
                    5.87,
                    null
            );

            assertMicronutrient(
                    database,
                    BROCCOLI_ID,
                    NutrientCode.CALCIUM,
                    39.0,
                    NutrientCode.UNIT_MG
            );
            assertMicronutrient(
                    database,
                    BROCCOLI_ID,
                    NutrientCode.VITAMIN_C,
                    29.17,
                    NutrientCode.UNIT_MG
            );
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void upgradeFromVersion18DeliversRawAndGrilledSeafoodRows() {
        Context isolatedContext = isolatedContext();
        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase database = helper.getWritableDatabase();
            for (String seafoodId : SEAFOOD_IDS) {
                database.delete(
                        "nutrition_food_nutrients",
                        "food_id = ?",
                        new String[]{seafoodId}
                );
                database.delete(
                        "nutrition_foods",
                        "id = ?",
                        new String[]{seafoodId}
                );
            }
            assertEquals(0, countSeafood(database));
            database.execSQL("DROP TABLE body_profiles");
            database.execSQL("DROP TABLE development_goals");
            database.setVersion(18);
            helper.close();
            helper = null;

            helper = new FitnessDatabaseHelper(isolatedContext);
            database = helper.getWritableDatabase();

            assertEquals(FitnessDatabaseHelper.DATABASE_VERSION, database.getVersion());
            assertTrue(tableExists(database, "body_profiles"));
            assertTrue(tableExists(database, "development_goals"));
            assertEquals(VerifiedFoodCatalogSeed.EXPECTED_COUNT, countOfficialFoods(database));
            assertEquals(EXPECTED_SEAFOOD_COUNT, countSeafood(database));
            assertEquals(5, countMissingSugars(database));
            assertEquals(1, countMissingRequiredExceptSugars(database));
            assertEquals(EXPECTED_SEAFOOD_COUNT, countMissingRequiredSeven(database));
            assertEquals(
                    EXPECTED_RAW_COUNT,
                    countPreparation(
                            database,
                            NutritionFood.PREP_RAW,
                            NutritionFood.COOKING_METHOD_RAW
                    )
            );
            assertEquals(
                    EXPECTED_GRILLED_COUNT,
                    countPreparation(
                            database,
                            NutritionFood.PREP_COOKED,
                            NutritionFood.COOKING_METHOD_GRILLED
                    )
            );
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void repeatedSeedDoesNotCreateDuplicateFoodsOrMicronutrients() {
        Context isolatedContext = isolatedContext();
        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase database = helper.getWritableDatabase();
            int nutrientCountBefore = countOfficialMicronutrients(database);

            VerifiedFoodCatalogSeed.seed(isolatedContext, database);
            VerifiedFoodCatalogSeed.seed(isolatedContext, database);

            assertEquals(VerifiedFoodCatalogSeed.EXPECTED_COUNT, countOfficialFoods(database));
            assertEquals(
                    VerifiedFoodCatalogSeed.EXPECTED_COUNT,
                    scalarInt(
                            database,
                            "SELECT COUNT(DISTINCT id) FROM nutrition_foods " +
                                    "WHERE source_type = ? AND deleted_at IS NULL",
                            VerifiedFoodCatalogSeed.SOURCE_TYPE
                    )
            );
            assertEquals(nutrientCountBefore, countOfficialMicronutrients(database));
            assertEquals(0, scalarInt(
                    database,
                    "SELECT COUNT(*) FROM (" +
                            "SELECT food_id, nutrient_code, COUNT(*) AS duplicate_count " +
                            "FROM nutrition_food_nutrients " +
                            "WHERE food_id LIKE 'kfind:%' AND deleted_at IS NULL " +
                            "GROUP BY food_id, nutrient_code HAVING duplicate_count > 1" +
                            ")"
            ));
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void upgradeFromVersion17RetiresCookedSeedAndPreservesMealSnapshot() {
        Context isolatedContext = isolatedContext();
        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase database = helper.getWritableDatabase();
            database.delete(
                    "nutrition_food_nutrients",
                    "food_id = ?",
                    new String[]{CHICKEN_BREAST_ID}
            );
            database.delete(
                    "nutrition_foods",
                    "id = ?",
                    new String[]{CHICKEN_BREAST_ID}
            );
            insertLegacyCookedChicken(database);
            insertLegacyMealSnapshot(database);
            database.setVersion(17);
            helper.close();
            helper = null;

            helper = new FitnessDatabaseHelper(isolatedContext);
            database = helper.getWritableDatabase();

            assertEquals(FitnessDatabaseHelper.DATABASE_VERSION, database.getVersion());
            assertEquals(VerifiedFoodCatalogSeed.EXPECTED_COUNT, countOfficialFoods(database));
            assertEquals(1, countMissingRequiredExceptSugars(database));
            assertEquals(5, countMissingSugars(database));
            assertEquals(EXPECTED_SEAFOOD_COUNT, countMissingRequiredSeven(database));
            assertEquals(0, countInvalidSourceReferences(database));
            assertEquals(0, countInvalidPreparation(database));
            assertEquals(0, scalarInt(
                    database,
                    "SELECT COUNT(*) FROM nutrition_foods WHERE id = ? AND deleted_at IS NULL",
                    LEGACY_COOKED_CHICKEN_ID
            ));
            assertEquals(1, scalarInt(
                    database,
                    "SELECT COUNT(*) FROM nutrition_foods WHERE id = ? AND deleted_at IS NOT NULL",
                    LEGACY_COOKED_CHICKEN_ID
            ));
            assertFoodMacros(
                    database,
                    CHICKEN_BREAST_ID,
                    106.0,
                    22.97,
                    0.0,
                    0.97,
                    NutritionFood.PREP_RAW,
                    NutritionFood.COOKING_METHOD_RAW
            );
            assertLegacyMealSnapshot(database);
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void stableIdCollisionDoesNotOverwritePrivateNonKfindFood() {
        Context isolatedContext = isolatedContext();
        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase database = helper.getWritableDatabase();
            database.delete(
                    "nutrition_food_nutrients",
                    "food_id = ?",
                    new String[]{BROCCOLI_ID}
            );
            database.delete(
                    "nutrition_foods",
                    "id = ?",
                    new String[]{BROCCOLI_ID}
            );
            insertPrivateCollision(database);

            VerifiedFoodCatalogSeed.seed(isolatedContext, database);

            try (Cursor cursor = database.rawQuery(
                    "SELECT owner_id, name, source_type, source_reference, visibility, " +
                            "basis_amount, calories_kcal, revision " +
                            "FROM nutrition_foods WHERE id = ?",
                    new String[]{BROCCOLI_ID}
            )) {
                assertTrue(cursor.moveToFirst());
                assertEquals("private-user", cursor.getString(0));
                assertEquals("Private collision food", cursor.getString(1));
                assertEquals("manual", cursor.getString(2));
                assertEquals("user-entered", cursor.getString(3));
                assertEquals("private", cursor.getString(4));
                assertEquals(55.0, cursor.getDouble(5), 0.001);
                assertEquals(777.0, cursor.getDouble(6), 0.001);
                assertEquals(77, cursor.getInt(7));
            }
            assertEquals(1, scalarInt(
                    database,
                    "SELECT COUNT(*) FROM nutrition_foods WHERE id = ?",
                    BROCCOLI_ID
            ));
            assertEquals(0, scalarInt(
                    database,
                    "SELECT COUNT(*) FROM nutrition_food_nutrients WHERE food_id = ?",
                    BROCCOLI_ID
            ));
            assertEquals(
                    VerifiedFoodCatalogSeed.EXPECTED_COUNT - 1,
                    countOfficialFoods(database)
            );
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static Context isolatedContext() {
        Context context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        return context;
    }

    private static int countOfficialFoods(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL",
                VerifiedFoodCatalogSeed.SOURCE_TYPE
        );
    }

    private static boolean tableExists(SQLiteDatabase database, String tableName) {
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
                new String[]{tableName}
        )) {
            return cursor.moveToFirst();
        }
    }

    private static int countOfficialMicronutrients(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_food_nutrients n " +
                        "INNER JOIN nutrition_foods f ON f.id = n.food_id " +
                        "WHERE f.source_type = ? AND f.deleted_at IS NULL " +
                        "AND n.deleted_at IS NULL",
                VerifiedFoodCatalogSeed.SOURCE_TYPE
        );
    }

    private static int countInvalidBasis(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL " +
                        "AND (ABS(basis_amount - 100.0) > 0.0001 OR basis_unit <> ?)",
                VerifiedFoodCatalogSeed.SOURCE_TYPE,
                NutritionUnit.GRAM
        );
    }

    private static int countInvalidOwnership(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL " +
                        "AND (owner_id IS NOT NULL OR visibility <> 'public')",
                VerifiedFoodCatalogSeed.SOURCE_TYPE
        );
    }

    private static int countMissingRequiredExceptSugars(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL AND (" +
                        "calories_kcal IS NULL OR protein_grams IS NULL OR " +
                        "carbs_grams IS NULL OR fat_grams IS NULL OR sodium_mg IS NULL OR " +
                        "saturated_fat_grams IS NULL)",
                VerifiedFoodCatalogSeed.SOURCE_TYPE
        );
    }

    private static int countMissingSugars(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL AND sugars_grams IS NULL",
                VerifiedFoodCatalogSeed.SOURCE_TYPE
        );
    }

    private static int countMissingRequiredSeven(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL AND (" +
                        "calories_kcal IS NULL OR protein_grams IS NULL OR " +
                        "carbs_grams IS NULL OR fat_grams IS NULL OR sodium_mg IS NULL OR " +
                        "saturated_fat_grams IS NULL OR sugars_grams IS NULL)",
                VerifiedFoodCatalogSeed.SOURCE_TYPE
        );
    }

    private static int countIncompleteFoodsOutsideSeafood(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL AND category <> ? AND (" +
                        "calories_kcal IS NULL OR protein_grams IS NULL OR " +
                        "carbs_grams IS NULL OR fat_grams IS NULL OR sodium_mg IS NULL OR " +
                        "saturated_fat_grams IS NULL OR sugars_grams IS NULL)",
                VerifiedFoodCatalogSeed.SOURCE_TYPE,
                NutritionFood.CATEGORY_SEAFOOD
        );
    }

    private static int countInvalidSourceReferences(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL " +
                        "AND (source_reference IS NULL OR source_reference NOT LIKE ?)",
                VerifiedFoodCatalogSeed.SOURCE_TYPE,
                VerifiedFoodCatalogSeed.SOURCE_REFERENCE_PREFIX + "%"
        );
    }

    private static int countInvalidPreparation(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL AND (" +
                        "source_version IS NULL OR NOT (" +
                        "(prep_state = ? AND cooking_method = ?) OR " +
                        "(prep_state = ? AND cooking_method = ?)))",
                VerifiedFoodCatalogSeed.SOURCE_TYPE,
                NutritionFood.PREP_RAW,
                NutritionFood.COOKING_METHOD_RAW,
                NutritionFood.PREP_COOKED,
                NutritionFood.COOKING_METHOD_GRILLED
        );
    }

    private static int countPreparation(
            SQLiteDatabase database,
            String prepState,
            String cookingMethod
    ) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL " +
                        "AND prep_state = ? AND cooking_method = ?",
                VerifiedFoodCatalogSeed.SOURCE_TYPE,
                prepState,
                cookingMethod
        );
    }

    private static int countSeafood(SQLiteDatabase database) {
        return scalarInt(
                database,
                "SELECT COUNT(*) FROM nutrition_foods " +
                        "WHERE source_type = ? AND deleted_at IS NULL AND category = ?",
                VerifiedFoodCatalogSeed.SOURCE_TYPE,
                NutritionFood.CATEGORY_SEAFOOD
        );
    }

    private static void assertFoodMacros(
            SQLiteDatabase database,
            String foodId,
            double calories,
            double protein,
            double carbs,
            double fat,
            String expectedPrepState,
            String expectedCookingMethod
    ) {
        try (Cursor cursor = database.rawQuery(
                "SELECT calories_kcal, protein_grams, carbs_grams, fat_grams, " +
                        "basis_amount, basis_unit, prep_state, cooking_method " +
                        "FROM nutrition_foods WHERE id = ? AND deleted_at IS NULL",
                new String[]{foodId}
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals(calories, cursor.getDouble(0), 0.001);
            assertEquals(protein, cursor.getDouble(1), 0.001);
            assertEquals(carbs, cursor.getDouble(2), 0.001);
            assertEquals(fat, cursor.getDouble(3), 0.001);
            assertEquals(100.0, cursor.getDouble(4), 0.001);
            assertEquals(NutritionUnit.GRAM, cursor.getString(5));
            assertEquals(expectedPrepState, cursor.getString(6));
            assertEquals(expectedCookingMethod, cursor.getString(7));
        }
    }

    private static void assertSeafoodMacros(
            SQLiteDatabase database,
            String foodId,
            double calories,
            double protein,
            double carbs,
            double fat,
            String expectedPrepState,
            String expectedCookingMethod,
            Double expectedSodium,
            Double expectedSaturatedFat,
            Double expectedSugars
    ) {
        assertFoodMacros(
                database,
                foodId,
                calories,
                protein,
                carbs,
                fat,
                expectedPrepState,
                expectedCookingMethod
        );
        String code = foodId.substring(VerifiedFoodCatalogSeed.FOOD_ID_PREFIX.length());
        try (Cursor cursor = database.rawQuery(
                "SELECT category, source_reference, source_version, sugars_grams, " +
                        "sodium_mg, saturated_fat_grams " +
                        "FROM nutrition_foods WHERE id = ? AND deleted_at IS NULL",
                new String[]{foodId}
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals(NutritionFood.CATEGORY_SEAFOOD, cursor.getString(0));
            assertEquals(
                    expectedSourceReference(foodId, code),
                    cursor.getString(1)
            );
            assertTrue(cursor.getString(2) != null && !cursor.getString(2).trim().isEmpty());
            assertNullableDouble(cursor, 3, expectedSugars);
            assertNullableDouble(cursor, 4, expectedSodium);
            assertNullableDouble(cursor, 5, expectedSaturatedFat);
        }
    }

    private static String expectedSourceReference(String foodId, String code) {
        String regionCode = RAW_MACKEREL_ID.equals(foodId) ? "33" : "ZZ";
        return VerifiedFoodCatalogSeed.SOURCE_REFERENCE_PREFIX
                + code
                + "&searchMonthCd=AVG&searchRegionCd="
                + regionCode;
    }

    private static void assertNullableDouble(
            Cursor cursor,
            int columnIndex,
            Double expected
    ) {
        if (expected == null) {
            assertTrue(cursor.isNull(columnIndex));
            return;
        }
        assertEquals(expected, cursor.getDouble(columnIndex), 0.001);
    }

    private static void assertMicronutrient(
            SQLiteDatabase database,
            String foodId,
            String nutrientCode,
            double expectedAmount,
            String expectedUnit
    ) {
        try (Cursor cursor = database.rawQuery(
                "SELECT amount, unit, owner_id FROM nutrition_food_nutrients " +
                        "WHERE food_id = ? AND nutrient_code = ? AND deleted_at IS NULL",
                new String[]{foodId, nutrientCode}
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals(expectedAmount, cursor.getDouble(0), 0.001);
            assertEquals(expectedUnit, cursor.getString(1));
            assertTrue(cursor.isNull(2));
            assertEquals(1, cursor.getCount());
        }
    }

    private static void insertPrivateCollision(SQLiteDatabase database) {
        ContentValues values = new ContentValues();
        values.put("id", BROCCOLI_ID);
        values.put("owner_id", "private-user");
        values.put("name", "Private collision food");
        values.put("brand", "Private brand");
        values.put("kind", NutritionFood.KIND_INGREDIENT);
        values.put("category", NutritionFood.CATEGORY_OTHER);
        values.put("basis_amount", 55.0);
        values.put("basis_unit", NutritionUnit.GRAM);
        values.put("prep_state", NutritionFood.PREP_RAW);
        values.put("cooking_method", NutritionFood.COOKING_METHOD_RAW);
        values.put("calories_kcal", 777.0);
        values.put("protein_grams", 17.0);
        values.put("carbs_grams", 27.0);
        values.put("fat_grams", 37.0);
        values.put("sodium_mg", 47.0);
        values.put("saturated_fat_grams", 7.0);
        values.put("sugars_grams", 9.0);
        values.put("source_type", "manual");
        values.put("source_reference", "user-entered");
        values.put("data_version", NutritionFood.DATA_VERSION_REQUIRED_SEVEN);
        values.put("revision", 77);
        values.put("visibility", "private");
        values.put("created_at", "2026-08-11T00:00:00Z");
        values.put("updated_at", "2026-08-11T00:00:00Z");
        values.putNull("deleted_at");
        database.insertOrThrow("nutrition_foods", null, values);
    }

    private static void insertLegacyCookedChicken(SQLiteDatabase database) {
        ContentValues values = new ContentValues();
        values.put("id", LEGACY_COOKED_CHICKEN_ID);
        values.putNull("owner_id");
        values.put("name", "닭가슴살(껍질 제거)");
        values.putNull("brand");
        values.put("kind", NutritionFood.KIND_INGREDIENT);
        values.put("category", NutritionFood.CATEGORY_POULTRY);
        values.put("basis_amount", 100.0);
        values.put("basis_unit", NutritionUnit.GRAM);
        values.put("prep_state", NutritionFood.PREP_COOKED);
        values.put("cooking_method", NutritionFood.COOKING_METHOD_GRILLED);
        values.put("calories_kcal", 164.0);
        values.put("protein_grams", 35.47);
        values.put("carbs_grams", 0.0);
        values.put("fat_grams", 1.48);
        values.put("sodium_mg", 61.0);
        values.put("saturated_fat_grams", 0.58);
        values.put("sugars_grams", 0.0);
        values.put("source_type", VerifiedFoodCatalogSeed.SOURCE_TYPE);
        values.put(
                "source_reference",
                VerifiedFoodCatalogSeed.SOURCE_REFERENCE_PREFIX
                        + "R209-008000551-0000&searchMonthCd=AVG&searchRegionCd=ZZ"
        );
        values.put("source_version", "legacy-v1");
        values.put("data_version", NutritionFood.DATA_VERSION_REQUIRED_SEVEN);
        values.put("revision", 1);
        values.put("visibility", "public");
        values.put("created_at", "2026-08-11T00:00:00Z");
        values.put("updated_at", "2026-08-11T00:00:00Z");
        values.putNull("deleted_at");
        database.insertOrThrow("nutrition_foods", null, values);
    }

    private static void insertLegacyMealSnapshot(SQLiteDatabase database) {
        database.execSQL(
                "INSERT INTO meal_records (id, user_id, date, menu, calories, " +
                        "protein_grams, carbs_grams, fat_grams, created_at, is_backfilled, " +
                        "updated_at, device_id, source_app, scope, metadata) VALUES " +
                        "('legacy-meal', 'local-user', '2026-08-11', 'legacy chicken', " +
                        "164, 35.47, 0, 1.48, '2026-08-11T00:00:00Z', 0, " +
                        "'2026-08-11T00:00:00Z', 'device', 'fitness-app', 'local', '{}')"
        );
        database.execSQL(
                "INSERT INTO meal_record_items (id, user_id, meal_record_id, food_id, " +
                        "food_name_snapshot, food_kind_snapshot, quantity, unit, " +
                        "basis_amount_snapshot, basis_unit_snapshot, prep_state_snapshot, " +
                        "calories, protein_grams, carbs_grams, fat_grams, " +
                        "source_type_snapshot, source_reference_snapshot, " +
                        "source_version_snapshot, food_data_version_snapshot, order_index, " +
                        "created_at, updated_at, device_id) VALUES " +
                        "('legacy-item', 'local-user', 'legacy-meal', ?, " +
                        "'닭가슴살(껍질 제거)', 'ingredient', 100, 'g', 100, 'g', 'cooked', " +
                        "164, 35.47, 0, 1.48, ?, ?, 'legacy-v1', 2, 0, " +
                        "'2026-08-11T00:00:00Z', '2026-08-11T00:00:00Z', 'device')",
                new Object[]{
                        LEGACY_COOKED_CHICKEN_ID,
                        VerifiedFoodCatalogSeed.SOURCE_TYPE,
                        VerifiedFoodCatalogSeed.SOURCE_REFERENCE_PREFIX
                                + "R209-008000551-0000&searchMonthCd=AVG&searchRegionCd=ZZ"
                }
        );
    }

    private static void assertLegacyMealSnapshot(SQLiteDatabase database) {
        try (Cursor cursor = database.rawQuery(
                "SELECT food_id, calories, protein_grams, prep_state_snapshot, " +
                        "source_version_snapshot FROM meal_record_items WHERE id = 'legacy-item'",
                null
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals(LEGACY_COOKED_CHICKEN_ID, cursor.getString(0));
            assertEquals(164.0, cursor.getDouble(1), 0.001);
            assertEquals(35.47, cursor.getDouble(2), 0.001);
            assertEquals(NutritionFood.PREP_COOKED, cursor.getString(3));
            assertEquals("legacy-v1", cursor.getString(4));
        }
    }

    private static int scalarInt(SQLiteDatabase database, String sql, String... args) {
        try (Cursor cursor = database.rawQuery(sql, args.length == 0 ? null : args)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
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
