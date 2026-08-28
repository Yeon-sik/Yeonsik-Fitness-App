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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public final class DiningOutComponentPersistenceTest {
    private static final String DATABASE_PREFIX = "dining_out_component_";
    private static final String USER_ID = "dining-out-component-user";

    @Test
    public void genericLinksSupportEveryCompositionGroupWithoutPromotingComponentsToMenus() {
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
            NutritionFood menu = catalog.saveDiningOutMenu("식당", "대표 메뉴", 40d, 20d, 10d);
            List<NutritionFood> savedComponents = new ArrayList<>();
            for (CompositionGroupType groupType : CompositionGroupType.values()) {
                DiningOutComponent component = DiningOutComponent.grouped(
                        groupType.label() + " 구성",
                        NutritionProfile.ofMacros(100d, 5d, 12d, 3d),
                        null,
                        null,
                        groupType.value(),
                        groupType.value(),
                        groupType.label(),
                        DiningOutOption.DEFAULT_ROLE,
                        null
                );
                NutritionFood saved = catalog.saveDiningOutComponent(
                        "식당",
                        "대표 메뉴",
                        component
                );
                savedComponents.add(saved);
                catalog.linkDiningOutComponentToMenu(menu.id, saved.id, groupType.value());
            }

            assertEquals(CompositionGroupType.values().length,
                    catalog.diningOutComponentsForMenu(menu.id).size());
            for (int index = 0; index < savedComponents.size(); index++) {
                CompositionGroupType groupType = CompositionGroupType.values()[index];
                List<NutritionFood> linked = catalog.diningOutComponentsForMenu(
                        menu.id,
                        groupType.value()
                );
                assertEquals(1, linked.size());
                assertEquals(savedComponents.get(index).id, linked.get(0).id);
            }
            assertEquals(1, catalog.savedDiningOutMenus().size());
            assertEquals(savedComponents.size(), catalog.savedDiningOutComponents(
                    "식당", null, "", 20
            ).size());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> catalog.linkDiningOutComponentToMenu(
                            menu.id,
                            savedComponents.get(0).id,
                            "review_event"
                    )
            );
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void provisionTypeIsWrittenOnlyToTheActualMealComponentSnapshot() {
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
            NutritionFood menu = catalog.saveDiningOutMenu("식당", "치킨", 40d, 20d, 10d);
            NutritionProfile profile = NutritionProfile.builder()
                    .value(NutritionProfile.CALORIES_KCAL, 180d)
                    .value(NutritionProfile.PROTEIN_GRAMS, 8d)
                    .value(NutritionProfile.CARBS_GRAMS, 20d)
                    .value(NutritionProfile.FAT_GRAMS, 7d)
                    .value(NutritionProfile.SODIUM_MG, 240d)
                    .build();
            NutritionFood saved = catalog.saveDiningOutComponent(
                    "식당",
                    "치킨",
                    DiningOutComponent.grouped(
                            "치즈볼",
                            profile,
                            null,
                            null,
                            CompositionGroupType.SIDE.value(),
                            CompositionGroupType.SIDE.value(),
                            CompositionGroupType.SIDE.label(),
                            DiningOutOption.DEFAULT_ROLE,
                            null
                    )
            );
            assertEquals(240d, saved.profile.value(NutritionProfile.SODIUM_MG), 0.001d);

            DiningOutComponent futureSelection = DiningOutComponent.grouped(
                    saved.name,
                    saved.profile,
                    saved.id,
                    saved.sourceReference,
                    CompositionGroupType.SIDE.value(),
                    CompositionGroupType.SIDE.value(),
                    CompositionGroupType.SIDE.label(),
                    DiningOutOption.DEFAULT_ROLE,
                    null
            );
            assertEquals(DiningOutProvisionType.INCLUDED.value(), futureSelection.provisionType);

            DiningOutComponent actualSelection = futureSelection.withProvisionType(
                    DiningOutProvisionType.REVIEW_EVENT.value()
            );
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String recordId = repository.addDiningOutMealAtTimeWithMenusAndConsumption(
                    LocalDate.now().minusDays(1).toString(),
                    "12:00",
                    "식당",
                    "본점",
                    null,
                    Collections.singletonList(MealMenuSelection.diningOut(
                            MealCompositionItem.from(menu, 1d),
                            USER_ID,
                            "식당",
                            Collections.singletonList(actualSelection)
                    )),
                    1d,
                    DiningOutConsumption.equalByDiners(1)
            );

            FitnessRepository.MealComponentEntry entry = repository.mealComponentsForItem(
                    repository.mealItemsForRecord(recordId).get(0).id
            ).get(0);
            assertEquals(DiningOutProvisionType.REVIEW_EVENT.value(), entry.provisionType);
            assertEquals(240d, entry.profile.value(NutritionProfile.SODIUM_MG), 0.001d);
            assertEquals(0, count(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM dining_out_menu_component_links " +
                            "WHERE group_type = 'review_event'"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void v38AddOnRowsAreCopiedToGenericLinksAsAddOn() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            SQLiteDatabase database = helper.getWritableDatabase();
            database.execSQL("CREATE TABLE dining_out_menu_add_on_links (" +
                    "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, menu_food_id TEXT NOT NULL, " +
                    "add_on_food_id TEXT NOT NULL, created_at TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL, deleted_at TEXT, device_id TEXT NOT NULL, " +
                    "UNIQUE(user_id, menu_food_id, add_on_food_id))");
            database.execSQL("INSERT INTO dining_out_menu_add_on_links " +
                    "(id, user_id, menu_food_id, add_on_food_id, created_at, updated_at, " +
                    "deleted_at, device_id) VALUES (?, ?, ?, ?, ?, ?, NULL, ?)",
                    new Object[]{
                            "legacy-link",
                            USER_ID,
                            "menu-id",
                            "add-on-id",
                            "2026-08-29T12:00:00Z",
                            "2026-08-29T12:00:00Z",
                            "android-local"
                    });

            helper.onUpgrade(database, FitnessDatabaseHelper.DATABASE_VERSION - 1,
                    FitnessDatabaseHelper.DATABASE_VERSION);

            try (Cursor cursor = database.rawQuery(
                    "SELECT component_food_id, group_type FROM dining_out_menu_component_links " +
                            "WHERE id = ?",
                    new String[]{"legacy-link"}
            )) {
                assertEquals(true, cursor.moveToFirst());
                assertEquals("add-on-id", cursor.getString(0));
                assertEquals(CompositionGroupType.ADD_ON.value(), cursor.getString(1));
            }
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
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
