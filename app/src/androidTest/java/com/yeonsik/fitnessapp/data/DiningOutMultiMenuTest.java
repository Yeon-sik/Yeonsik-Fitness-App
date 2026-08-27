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
import static org.junit.Assert.assertThrows;

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

            String recordId = repository.addDiningOutMealAtTimeWithMenusAndConsumption(
                    LocalDate.now().minusDays(1).toString(),
                    "12:30",
                    "테스트 식당",
                    "영등포점",
                    null,
                    Arrays.asList(first, second),
                    1d,
                    DiningOutConsumption.equalByDiners(1)
            );
            SQLiteDatabase database = helper.getReadableDatabase();
            assertEquals(2, count(database,
                    "SELECT COUNT(*) FROM meal_record_items WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals(2, count(database,
                    "SELECT COUNT(*) FROM meal_record_item_components WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals(1, count(database,
                    "SELECT COUNT(*) FROM meal_record_item_components " +
                            "WHERE meal_record_id = '" + recordId + "' " +
                            "AND composition_group_type_snapshot = 'add_on'"));
            assertEquals(2, count(database,
                    "SELECT COUNT(*) FROM meal_record_item_consumptions WHERE meal_record_id = '" +
                            recordId + "'"));
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
