package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.config.SupabaseConfig;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class NutritionCatalogRepositoryVerifiedSearchTest {
    private static final String DATABASE_PREFIX = "verified_search_";
    private static final String USER_ID = "verified-search-user";
    private static final String VERIFIED_REFERENCE_PREFIX =
            "https://various.foodsafetykorea.go.kr/nutrient/general/food/detail.do?searchFoodCd=";
    private static final String RAW_SALMON_ID = "kfind:R211-201174001-0000";
    private static final String RAW_TUNA_ID = "kfind:R211-059074001-0000";
    private static final String RAW_MACKEREL_ID = "kfind:R211-021014001-1208";
    private static final String GRILLED_SALMON_ID = "kfind:R211-201174050-0000";
    private static final String GRILLED_TUNA_ID = "kfind:R211-059074050-0000";
    private static final String GRILLED_MACKEREL_ID = "kfind:R211-021014050-7300";

    @Test
    public void searchVerifiedFoodsFiltersInSqlAndIgnoresImpostors() {
        Context isolatedContext = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase database = helper.getWritableDatabase();
            NutritionCatalogRepository repository = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    SupabaseConfig.empty()
            );

            for (int index = 0; index < 120; index++) {
                insertFood(
                        database,
                        "manual-" + index,
                        USER_ID,
                        "private",
                        "manual",
                        null,
                        "브로콜리",
                        String.format(Locale.US, "잡음%03d", index)
                );
            }

            insertFood(
                    database,
                    "public-fake-source-reference",
                    null,
                    "public",
                    VerifiedFoodCatalogSeed.SOURCE_TYPE,
                    "https://example.com/not-kfind",
                    "브로콜리",
                    "가짜공개1"
            );
            insertFood(
                    database,
                    "public-fake-id",
                    null,
                    "public",
                    VerifiedFoodCatalogSeed.SOURCE_TYPE,
                    VERIFIED_REFERENCE_PREFIX + "R106-092000001-0000",
                    "브로콜리",
                    "가짜공개2"
            );
            insertFood(
                    database,
                    "kfind:fake-manual",
                    null,
                    "public",
                    "manual",
                    VERIFIED_REFERENCE_PREFIX + "R106-092000001-0000",
                    "브로콜리",
                    "가짜공개3"
            );
            insertFood(
                    database,
                    "kfind:private-fake",
                    USER_ID,
                    "private",
                    VerifiedFoodCatalogSeed.SOURCE_TYPE,
                    VERIFIED_REFERENCE_PREFIX + "R106-092000001-0000",
                    "브로콜리",
                    "가짜비공개"
            );
            insertFood(
                    database,
                    "kfind:R999-000000001-0000",
                    null,
                    "public",
                    VerifiedFoodCatalogSeed.SOURCE_TYPE,
                    VERIFIED_REFERENCE_PREFIX + "R999-000000001-0000",
                    "브로콜리",
                    "가짜미선정"
            );

            List<NutritionFood> foods = repository.searchVerifiedFoods("브로콜리", 0);

            assertFalse(foods.isEmpty());
            assertEquals(1, foods.size());

            NutritionFood broccoli = foods.get(0);
            assertEquals("kfind:R106-092000001-0000", broccoli.id);
            assertEquals(VerifiedFoodCatalogSeed.SOURCE_TYPE, broccoli.sourceType);
            assertTrue(broccoli.id.startsWith("kfind:"));
            assertTrue(broccoli.sourceReference.startsWith(VERIFIED_REFERENCE_PREFIX));
            assertEquals(NutritionFood.KIND_INGREDIENT, broccoli.kind);
            assertNull(broccoli.ownerId);

            assertNotNull(findById(foods, "kfind:R106-092000001-0000"));
            assertNull(findById(foods, "public-fake-source-reference"));
            assertNull(findById(foods, "public-fake-id"));
            assertNull(findById(foods, "kfind:fake-manual"));
            assertNull(findById(foods, "kfind:private-fake"));
            assertNull(findById(foods, "kfind:R999-000000001-0000"));
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void searchVerifiedSeafoodReturnsRawAndGrilledVariants() {
        Context isolatedContext = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(isolatedContext);
            NutritionCatalogRepository repository = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    SupabaseConfig.empty()
            );

            assertSeafoodSearch(
                    repository,
                    "연어",
                    RAW_SALMON_ID,
                    "연어회(홍연어·생것 기준)",
                    GRILLED_SALMON_ID,
                    "연어구이(홍연어)"
            );
            assertSeafoodSearch(
                    repository,
                    "참치",
                    RAW_TUNA_ID,
                    "참치회(참다랑어·생것 기준)",
                    GRILLED_TUNA_ID,
                    "참치구이(참다랑어)"
            );
            assertSeafoodSearch(
                    repository,
                    "고등어",
                    RAW_MACKEREL_ID,
                    "고등어회(생것·부산 평균)",
                    GRILLED_MACKEREL_ID,
                    "고등어구이(수입·일본 평균)"
            );
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static void assertSeafoodSearch(
            NutritionCatalogRepository repository,
            String query,
            String rawId,
            String rawName,
            String grilledId,
            String grilledName
    ) {
        List<NutritionFood> foods = repository.searchVerifiedFoods(query, 10);

        assertEquals(2, foods.size());
        assertSeafoodVariant(
                findById(foods, rawId),
                rawId,
                rawName,
                NutritionFood.PREP_RAW,
                NutritionFood.COOKING_METHOD_RAW
        );
        assertSeafoodVariant(
                findById(foods, grilledId),
                grilledId,
                grilledName,
                NutritionFood.PREP_COOKED,
                NutritionFood.COOKING_METHOD_GRILLED
        );
    }

    private static void assertSeafoodVariant(
            NutritionFood food,
            String expectedId,
            String expectedName,
            String expectedPrepState,
            String expectedCookingMethod
    ) {
        assertNotNull(food);
        assertEquals(expectedId, food.id);
        assertEquals(expectedName, food.name);
        assertEquals(NutritionFood.CATEGORY_SEAFOOD, food.category);
        assertEquals(NutritionFood.KIND_INGREDIENT, food.kind);
        assertEquals(100.0, food.basisAmount, 0.001);
        assertEquals(NutritionUnit.GRAM, food.basisUnit);
        assertEquals(expectedPrepState, food.prepState);
        assertEquals(expectedCookingMethod, food.cookingMethod);
        assertEquals(VerifiedFoodCatalogSeed.SOURCE_TYPE, food.sourceType);
        assertEquals(
                expectedSourceReference(expectedId),
                food.sourceReference
        );
        assertEquals("2026-01-13", food.sourceVersion);
        assertNull(food.ownerId);
        assertTrue(food.identityLabel().contains(
                NutritionFood.cookingMethodLabel(expectedCookingMethod)
        ));
    }

    private static String expectedSourceReference(String foodId) {
        String code = foodId.substring(VerifiedFoodCatalogSeed.FOOD_ID_PREFIX.length());
        String regionCode = RAW_MACKEREL_ID.equals(foodId) ? "33" : "ZZ";
        return VERIFIED_REFERENCE_PREFIX
                + code
                + "&searchMonthCd=AVG&searchRegionCd="
                + regionCode;
    }

    private static NutritionFood findById(List<NutritionFood> foods, String id) {
        for (NutritionFood food : foods) {
            if (id.equals(food.id)) {
                return food;
            }
        }
        return null;
    }

    private static void insertFood(
            SQLiteDatabase database,
            String id,
            String ownerId,
            String visibility,
            String sourceType,
            String sourceReference,
            String name,
            String brand
    ) {
        String now = "2026-08-10T00:00:00Z";
        ContentValues values = new ContentValues();
        values.put("id", id);
        if (ownerId == null) {
            values.putNull("owner_id");
        } else {
            values.put("owner_id", ownerId);
        }
        values.put("name", name);
        values.put("brand", brand);
        values.put("kind", NutritionFood.KIND_INGREDIENT);
        values.put("category", NutritionFood.CATEGORY_VEGETABLE);
        values.put("basis_amount", 100.0);
        values.put("basis_unit", NutritionUnit.GRAM);
        values.put("prep_state", NutritionFood.PREP_RAW);
        values.put("cooking_method", NutritionFood.COOKING_METHOD_RAW);
        values.put("calories_kcal", 1.0);
        values.put("protein_grams", 1.0);
        values.put("carbs_grams", 1.0);
        values.put("fat_grams", 1.0);
        values.put("sodium_mg", 1.0);
        values.put("saturated_fat_grams", 1.0);
        values.put("sugars_grams", 1.0);
        values.put("source_type", sourceType);
        if (sourceReference == null) {
            values.putNull("source_reference");
        } else {
            values.put("source_reference", sourceReference);
        }
        values.put("data_version", NutritionFood.DATA_VERSION_REQUIRED_SEVEN);
        values.put("revision", 1);
        values.put("visibility", visibility);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.putNull("deleted_at");
        database.insertOrThrow("nutrition_foods", null, values);
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
