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
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class FoodCatalogNormalizationTest {
    private static final String DATABASE_PREFIX = "food_catalog_normalization_";
    private static final String USER_ID = "food-catalog-normalization-user";
    private static final String STANDARD_PRODUCT_ID =
            "22222222-2222-4222-8222-222222222222";
    private static final String REVISION =
            "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void diningOutMenuPickerKeepsOneCanonicalRowAndHydratesFullProfile() {
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
            NutritionFood first = catalog.saveDiningOutMenuWithNutrition(
                    " 식당   A ",
                    " 메뉴 A ",
                    fullProfile(0d),
                    "본점",
                    null
            );
            NutritionFood second = catalog.saveDiningOutMenuWithNutrition(
                    "식당 A",
                    "메뉴 A",
                    fullProfile(10d),
                    "다른 지점",
                    null
            );

            assertEquals(first.id, second.id);
            List<NutritionFood> menus = catalog.savedDiningOutMenus();
            assertEquals(1, menus.size());
            assertProfileEquals(fullProfile(10d), menus.get(0).profile);
            assertEquals(
                    "store|식당 a|menu|메뉴 a",
                    NutritionCatalogRepository.canonicalDiningOutMenuKey(
                            "식당 A",
                            " 메뉴   A ",
                            "{\"restaurant_location_id\":\"location-2\"}"
                    )
            );
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void diningOutComponentsDeduplicateByRestaurantGroupAndName() {
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
            NutritionFood first = catalog.saveDiningOutComponent(
                    "식당 A",
                    "메뉴 A",
                    DiningOutComponent.grouped(
                            " 날치알   추가 ",
                            fullProfile(0d),
                            null,
                            null,
                            "add-on",
                            CompositionGroupType.ADD_ON.value(),
                            CompositionGroupType.ADD_ON.label(),
                            DiningOutOption.DEFAULT_ROLE,
                            null,
                            DiningOutProvisionType.PAID.value()
                    )
            );
            NutritionFood second = catalog.saveDiningOutComponent(
                    " 식당   A ",
                    "다른 메뉴",
                    DiningOutComponent.grouped(
                            "날치알 추가",
                            fullProfile(10d),
                            null,
                            null,
                            "different-key",
                            CompositionGroupType.ADD_ON.value(),
                            CompositionGroupType.ADD_ON.label(),
                            DiningOutOption.DEFAULT_ROLE,
                            null,
                            DiningOutProvisionType.REVIEW_EVENT.value()
                    )
            );

            assertEquals(first.id, second.id);
            assertEquals(1, catalog.savedDiningOutComponents(
                    "식당 A",
                    null,
                    CompositionGroupType.ADD_ON.value(),
                    "",
                    20
            ).size());
            assertTrue(second.sourceReference != null);
            assertFalse(second.sourceReference.contains("provision"));
            assertEquals(
                    DiningOutProvisionType.INCLUDED.value(),
                    DiningOutComponent.fromFood(second).provisionType
            );
            assertEquals(6, CompositionGroupType.values().length);
            assertArrayEquals(
                    new String[]{"banchan", "side", "add_on", "beverage", "sauce", "other"},
                    new String[]{
                            CompositionGroupType.BANCHAN.value(),
                            CompositionGroupType.SIDE.value(),
                            CompositionGroupType.ADD_ON.value(),
                            CompositionGroupType.BEVERAGE.value(),
                            CompositionGroupType.SAUCE.value(),
                            CompositionGroupType.OTHER.value()
                    }
            );
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void packagedPickerReturnsOneCanonicalProductAndSeparateVariants() {
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
            NutritionFood first = savePackaged(
                    catalog,
                    "11111111-1111-4111-8111-111111111111",
                    450d,
                    1,
                    fullProfile(0d)
            );
            NutritionFood second = savePackaged(
                    catalog,
                    "33333333-3333-4333-8333-333333333333",
                    900d,
                    1,
                    fullProfile(10d)
            );

            List<NutritionFood> products = catalog.searchPackagedFoods("호밍스", 20);
            assertEquals(1, products.size());
            assertEquals(
                    "청정원 · 호밍스 · 우삼겹 스키야끼",
                    products.get(0).packagedProductLabel()
            );
            assertEquals(2, catalog.packagedFoodVariants(products.get(0)).size());
            assertEquals(
                    catalog.canonicalPackagedProductKey(first),
                    catalog.canonicalPackagedProductKey(second)
            );
            ProductNutritionLink link = catalog.approvedProductLink(first.id);
            assertNotNull(link);
            assertEquals(
                    "11111111-1111-4111-8111-111111111111",
                    link.catalogProductId
            );
            assertEquals(STANDARD_PRODUCT_ID, link.standardProductId);
            assertProfileEquals(fullProfile(0d), first.profile);
            assertProfileEquals(fullProfile(10d), second.profile);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void legacyBrandOnlyPackagedRowsRemainSearchable() {
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
            NutritionFood legacy = catalog.saveFood(
                    "햇반",
                    "청정원",
                    NutritionFood.KIND_EXTERNAL_MENU,
                    NutritionFood.CATEGORY_PROCESSED,
                    1d,
                    NutritionUnit.SERVING,
                    NutritionFood.COOKING_METHOD_UNSPECIFIED,
                    fullProfile(0d),
                    "manual",
                    null,
                    null
            );

            List<NutritionFood> products = catalog.searchPackagedFoods("청정원", 20);
            assertEquals(1, products.size());
            assertEquals(legacy.id, products.get(0).id);
            assertEquals("청정원 · 햇반", products.get(0).packagedProductLabel());
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void packagedHierarchyIsSnapshottedWithoutRetroactiveCatalogChanges() {
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
            NutritionFood product = savePackaged(
                    catalog,
                    "11111111-1111-4111-8111-111111111111",
                    450d,
                    1,
                    fullProfile(0d)
            );
            FitnessRepository records = new FitnessRepository(helper, USER_ID);
            String recordId = records.addMealAtTime(
                    LocalDate.now().minusDays(1).toString(),
                    "12:00",
                    1,
                    2d,
                    3d,
                    4d,
                    java.util.Collections.singletonList(MealCompositionItem.from(product, 1d))
            );

            ContentValues changed = new ContentValues();
            changed.put("calories_kcal", 999d);
            changed.put("manufacturer_name", "변경 회사");
            changed.put("brand_name", "변경 브랜드");
            changed.put("product_name", "변경 제품");
            helper.getWritableDatabase().update(
                    "nutrition_foods",
                    changed,
                    "id = ?",
                    new String[]{product.id}
            );

            FitnessRepository.MealItemEntry entry = records.mealItemsForRecord(recordId).get(0);
            assertEquals(500d, entry.profile.value(NutritionProfile.CALORIES_KCAL), 0.001d);
            assertEquals("대상", entry.manufacturerNameSnapshot);
            assertEquals("호밍스", entry.brandNameSnapshot);
            assertEquals("우삼겹 스키야끼", entry.productNameSnapshot);
            assertEquals(450d, entry.packageAmountSnapshot, 0.001d);
            assertEquals("g", entry.packageUnitSnapshot);
            assertEquals(Integer.valueOf(1), entry.packageCountSnapshot);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void componentsStayOutOfGeneralFoodPickerAndUnknownIsNotZero() {
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
            NutritionProfile unknownSodium = NutritionProfile.ofMacros(100d, 5d, 12d, 3d);
            NutritionFood component = catalog.saveDiningOutComponent(
                    "식당 A",
                    "메뉴 A",
                    DiningOutComponent.grouped(
                            "계란찜",
                            unknownSodium,
                            null,
                            null,
                            "side-1",
                            CompositionGroupType.SIDE.value(),
                            CompositionGroupType.SIDE.label(),
                            DiningOutOption.DEFAULT_ROLE,
                            null
                    )
            );

            assertEquals(0, catalog.searchFoods("계란찜").size());
            assertFalse(unknownSodium.isKnown(NutritionProfile.SODIUM_MG));
            assertTrue(NutritionProfile.builder()
                    .from(unknownSodium)
                    .value(NutritionProfile.SODIUM_MG, 0d)
                    .build()
                    .isKnown(NutritionProfile.SODIUM_MG));
            assertNull(component.profile.value(NutritionProfile.SODIUM_MG));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static NutritionFood savePackaged(
            NutritionCatalogRepository catalog,
            String catalogProductId,
            double packageAmount,
            int packageCount,
            NutritionProfile profile
    ) {
        ProductReadV1 product = new ProductReadV1(
                catalogProductId,
                STANDARD_PRODUCT_ID,
                "우삼겹 스키야끼",
                "호밍스",
                "청정원",
                "호밍스",
                null,
                null,
                null,
                packageAmount,
                "g",
                packageCount,
                REVISION
        );
        return catalog.savePackagedFood(
                "대상",
                "청정원",
                "호밍스",
                "우삼겹 스키야끼",
                packageAmount,
                "g",
                packageCount,
                1d,
                NutritionUnit.SERVING,
                NutritionFood.PREP_AS_SERVED,
                profile,
                "pricetrace_manual",
                null,
                REVISION,
                product
        );
    }

    private static NutritionProfile fullProfile(double offset) {
        return NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, 500d + offset)
                .value(NutritionProfile.PROTEIN_GRAMS, 20d + offset)
                .value(NutritionProfile.CARBS_GRAMS, 60d + offset)
                .value(NutritionProfile.FAT_GRAMS, 15d + offset)
                .value(NutritionProfile.SODIUM_MG, 700d + offset)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, 5d + offset)
                .value(NutritionProfile.SUGARS_GRAMS, 8d + offset)
                .value(NutritionProfile.FIBER_GRAMS, 4d + offset)
                .value(NutritionProfile.ADDED_SUGARS_GRAMS, 2d + offset)
                .value(NutritionProfile.TRANS_FAT_GRAMS, offset)
                .value(NutritionProfile.CHOLESTEROL_MG, 70d + offset)
                .build();
    }

    private static void assertProfileEquals(
            NutritionProfile expected,
            NutritionProfile actual
    ) {
        for (String key : MealItemSnapshot.TYPED_KEYS) {
            assertEquals(key, expected.value(key), actual.value(key));
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
