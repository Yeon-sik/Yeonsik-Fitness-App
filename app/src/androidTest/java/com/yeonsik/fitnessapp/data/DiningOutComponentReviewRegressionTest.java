package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
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
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public final class DiningOutComponentReviewRegressionTest {
    private static final String DATABASE_PREFIX = "dining_out_component_review_";
    private static final String USER_ID = "dining-out-component-review-user";

    @Test
    public void savedComponentsFilterGroupBeforeApplyingLimit() {
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
            NutritionFood side = catalog.saveDiningOutComponent(
                    "식당",
                    "대표 메뉴",
                    component("사이드 샐러드", CompositionGroupType.SIDE)
            );
            NutritionFood banchan = catalog.saveDiningOutComponent(
                    "식당",
                    "대표 메뉴",
                    component("김치", CompositionGroupType.BANCHAN)
            );

            ContentValues newer = new ContentValues();
            newer.put("updated_at", "9999-12-31T23:59:59Z");
            helper.getWritableDatabase().update(
                    "nutrition_foods",
                    newer,
                    "id = ?",
                    new String[]{banchan.id}
            );

            List<NutritionFood> result = catalog.savedDiningOutComponents(
                    "식당",
                    null,
                    CompositionGroupType.SIDE.value(),
                    "",
                    1
            );
            assertEquals(1, result.size());
            assertEquals(side.id, result.get(0).id);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void ordinaryDiningOutMenusCannotBeUsedAsComponents() {
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
            NutritionFood ordinaryMenu = catalog.saveDiningOutMenu(
                    "식당",
                    "다른 일반 메뉴",
                    30d,
                    15d,
                    8d
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> catalog.linkDiningOutComponentToMenu(menu.id, ordinaryMenu.id)
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> catalog.linkDiningOutComponentToMenu(
                            menu.id,
                            ordinaryMenu.id,
                            CompositionGroupType.SIDE.value()
                    )
            );

            ContentValues rogueLink = new ContentValues();
            rogueLink.put("id", UUID.randomUUID().toString());
            rogueLink.put("user_id", USER_ID);
            rogueLink.put("menu_food_id", menu.id);
            rogueLink.put("component_food_id", ordinaryMenu.id);
            rogueLink.put("group_type", CompositionGroupType.SIDE.value());
            rogueLink.put("created_at", "2026-08-29T12:00:00Z");
            rogueLink.put("updated_at", "2026-08-29T12:00:00Z");
            rogueLink.putNull("deleted_at");
            rogueLink.put("device_id", "android-test");
            helper.getWritableDatabase().insertOrThrow(
                    "dining_out_menu_component_links",
                    null,
                    rogueLink
            );

            assertEquals(
                    0,
                    catalog.diningOutComponentsForMenu(
                            menu.id,
                            CompositionGroupType.SIDE.value()
                    ).size()
            );
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static DiningOutComponent component(
            String name,
            CompositionGroupType groupType
    ) {
        return DiningOutComponent.grouped(
                name,
                NutritionProfile.ofMacros(100d, 5d, 12d, 3d),
                null,
                null,
                groupType.value(),
                groupType.value(),
                groupType.label(),
                DiningOutOption.DEFAULT_ROLE,
                null
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
