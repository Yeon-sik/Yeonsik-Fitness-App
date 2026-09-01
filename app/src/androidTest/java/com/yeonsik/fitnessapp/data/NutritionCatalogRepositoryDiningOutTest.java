package com.yeonsik.fitnessapp.data;

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
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class NutritionCatalogRepositoryDiningOutTest {
    private static final String DATABASE_PREFIX = "dining_out_catalog_";
    private static final String USER_ID = "dining-out-catalog-user";

    @Test
    public void upsertsNameFallbackMenusAndKeepsOptionsOutOfMenuList() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository repository = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    SupabaseConfig.empty()
            );
            NutritionFood first = repository.saveDiningOutMenu(
                    " 식당   A ",
                    " 메뉴 A ",
                    50d,
                    20d,
                    10d
            );
            NutritionFood second = repository.saveDiningOutMenu(
                    "식당 A",
                    "메뉴 A",
                    60d,
                    25d,
                    12d
            );

            assertEquals(first.id, second.id);
            assertEquals(1, repository.savedDiningOutMenus().size());
            assertEquals(60d, repository.savedDiningOutMenus().get(0).carbsGrams, 0.001d);

            repository.saveDiningOutOption(
                    "식당 A",
                    "메뉴 A",
                    DiningOutOption.grouped(
                            "사이드 샐러드",
                            NutritionProfile.ofMacros(100, 4, 12, 3),
                            null,
                            null,
                            "side_1",
                            CompositionGroupType.SIDE.value(),
                            CompositionGroupType.SIDE.label(),
                            DiningOutOption.DEFAULT_ROLE,
                            null
                    )
            );

            assertEquals(1, repository.savedDiningOutMenus().size());
            assertEquals(
                    1,
                    repository.savedDiningOutOptions(
                            "식당 A",
                            null,
                            "",
                            20
                    ).size()
            );
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void savesAndReloadsOptionNutrientsWhileKeepingBlankValuesUnknown() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository repository = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    SupabaseConfig.empty()
            );
            NutritionProfile optionProfile = NutritionProfile.builder()
                    .value(NutritionProfile.CALORIES_KCAL, 120d)
                    .value(NutritionProfile.CARBS_GRAMS, 12d)
                    .value(NutritionProfile.PROTEIN_GRAMS, 4d)
                    .value(NutritionProfile.FAT_GRAMS, 5d)
                    .value(NutritionProfile.SUGARS_GRAMS, 0d)
                    .value(NutritionProfile.SATURATED_FAT_GRAMS, 1d)
                    .build();
            repository.saveDiningOutOption(
                    "식당 B",
                    "메뉴 B",
                    DiningOutOption.grouped(
                            "소스",
                            optionProfile,
                            null,
                            null,
                            "side_1",
                            CompositionGroupType.SIDE.value(),
                            CompositionGroupType.SIDE.label(),
                            DiningOutOption.DEFAULT_ROLE,
                            null
                    )
            );

            List<NutritionFood> reloaded = repository.savedDiningOutComponents(
                    "식당 B",
                    null,
                    CompositionGroupType.SIDE.value(),
                    "소스",
                    20
            );
            assertEquals(1, reloaded.size());
            NutritionProfile profile = reloaded.get(0).profile;
            assertEquals(120d, profile.value(NutritionProfile.CALORIES_KCAL), 0.001d);
            assertEquals(12d, profile.value(NutritionProfile.CARBS_GRAMS), 0.001d);
            assertEquals(4d, profile.value(NutritionProfile.PROTEIN_GRAMS), 0.001d);
            assertEquals(5d, profile.value(NutritionProfile.FAT_GRAMS), 0.001d);
            assertEquals(0d, profile.value(NutritionProfile.SUGARS_GRAMS), 0.001d);
            assertEquals(1d, profile.value(NutritionProfile.SATURATED_FAT_GRAMS), 0.001d);
            assertFalse(profile.isKnown(NutritionProfile.SODIUM_MG));
            assertNull(profile.value(NutritionProfile.SODIUM_MG));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void reusableMenuSaveRejectsMissingCaloriesButAcceptsRequiredFour() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository repository = new NutritionCatalogRepository(
                    helper,
                    USER_ID,
                    SupabaseConfig.empty()
            );
            assertThrows(IllegalArgumentException.class, () ->
                    repository.saveDiningOutMenuWithNutrition(
                            "식당 C",
                            "칼로리 누락",
                            null,
                            4d,
                            12d,
                            5d,
                            null,
                            null,
                            null
                    ));
            NutritionFood saved = repository.saveDiningOutMenuWithNutrition(
                    "식당 C",
                    "필수 4종 메뉴",
                    240,
                    4d,
                    12d,
                    5d,
                    null,
                    null,
                    null
            );
            assertEquals(240d, saved.profile.value(NutritionProfile.CALORIES_KCAL), 0.001d);
            assertEquals(12d, saved.profile.value(NutritionProfile.CARBS_GRAMS), 0.001d);
            assertFalse(saved.profile.isKnown(NutritionProfile.SODIUM_MG));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void prioritizesRestaurantMenuIdAndDoesNotReadMealRecordsAsMenus() {
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
            DiningOutIdentity firstIdentity = identity(
                    "11111111-1111-4111-8111-111111111111",
                    "22222222-2222-4222-8222-222222222222",
                    "PT 식당",
                    "메뉴 A",
                    "강남점"
            );
            NutritionFood first = catalog.saveDiningOutMenu(
                    "PT 식당",
                    "메뉴 A",
                    50d,
                    20d,
                    10d,
                    "강남점",
                    firstIdentity
            );
            NutritionFood sameCanonicalMenu = catalog.saveDiningOutMenu(
                    "표시 식당",
                    "표시 메뉴",
                    70d,
                    30d,
                    15d,
                    "다른 지점 표시",
                    identity(
                            firstIdentity.restaurantId,
                            firstIdentity.restaurantMenuId,
                            "표시 식당",
                            "표시 메뉴",
                            "다른 지점 표시"
                    )
            );
            NutritionFood differentMenu = catalog.saveDiningOutMenu(
                    "표시 식당",
                    "표시 메뉴",
                    80d,
                    35d,
                    18d,
                    "다른 지점 표시",
                    identity(
                            firstIdentity.restaurantId,
                            "33333333-3333-4333-8333-333333333333",
                            "표시 식당",
                            "표시 메뉴",
                            "다른 지점 표시"
                    )
            );

            assertEquals(first.id, sameCanonicalMenu.id);
            assertEquals(1, catalog.savedDiningOutMenus().stream()
                    .filter(food -> food.id.equals(first.id)).count());
            assertEquals(2, catalog.savedDiningOutMenus().size());
            assertEquals(70d, catalog.findFoodById(first.id).carbsGrams, 0.001d);

            FitnessRepository records = new FitnessRepository(helper, USER_ID);
            records.addDiningOutMealAtTimeWithOptionNutrition(
                    LocalDate.now().minusDays(1).toString(),
                    "12:00",
                    "표시 식당",
                    "표시 메뉴",
                    1d,
                    2d,
                    3d,
                    null,
                    Collections.emptyList()
            );
            assertEquals(2, catalog.savedDiningOutMenus().size());
            assertEquals(70d, catalog.findFoodById(first.id).carbsGrams, 0.001d);
            assertEquals(
                    "restaurant_id|rest-1|menu|제육 볶음",
                    NutritionCatalogRepository.canonicalDiningOutMenuKey(
                            "지점 A",
                            " 제육   볶음 ",
                            "{\"restaurant_id\":\"REST-1\"}"
                    )
            );
            assertEquals(2, catalog.savedDiningOutMenus().size());
            assertTrue(!differentMenu.id.equals(first.id));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static DiningOutIdentity identity(
            String restaurantId,
            String menuId,
            String restaurantName,
            String menuName,
            String branchName
    ) {
        return DiningOutIdentity.fromPriceTrace(
                restaurantId,
                restaurantName,
                "44444444-4444-4444-8444-444444444444",
                branchName,
                menuId,
                menuName,
                "55555555-5555-4555-8555-555555555555"
        );
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
