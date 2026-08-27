package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.development.DevelopmentRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Exercises the historical v8 schema against the current SQLiteOpenHelper on a real device. */
@RunWith(AndroidJUnit4.class)
public final class FitnessDatabaseMigrationTest {
    private static final String DATABASE_PREFIX = "migration_v8_to_current_";
    private static final String AUTH_USER_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    public void testV8UpgradeAddsLateColumnsBeforeTheirIndexesAndPreservesLocalOwnership() {
        Context isolatedContext = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessDatabaseHelper helper = null;
        try {
            SQLiteDatabase legacy = isolatedContext.openOrCreateDatabase(
                    FitnessDatabaseHelper.DATABASE_NAME,
                    0,
                    null
            );
            createVersionEightSchema(legacy);
            legacy.setVersion(8);
            legacy.close();

            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase upgraded = helper.getWritableDatabase();

            assertEquals(FitnessDatabaseHelper.DATABASE_VERSION, upgraded.getVersion());
            assertTrue(isPrimaryKeyColumn(upgraded, "body_profiles", "user_id"));
            assertTrue(hasColumn(upgraded, "body_profiles", "height_cm"));
            assertTrue(isPrimaryKeyColumn(upgraded, "development_goals", "user_id"));
            assertTrue(hasColumn(upgraded, "development_goals", "objective"));
            assertTrue(hasColumn(upgraded, "development_goals", "weekly_sessions_target"));
            assertTrue(hasColumn(upgraded, "development_goals", "focus_body_part"));
            assertTrue(hasColumn(upgraded, "development_goals", "effective_from"));
            assertTrue(hasColumn(upgraded, "nutrition_foods", "brand"));
            assertTrue(hasColumn(upgraded, "nutrition_foods", "category"));
            assertTrue(hasColumn(upgraded, "nutrition_foods", "cooking_method"));
            assertTrue(hasColumn(upgraded, "meal_records", "meal_kind"));
            assertTrue(hasColumn(upgraded, "meal_records", "store_name"));
            assertTrue(hasColumn(upgraded, "meal_records", "branch_name"));
            assertTrue(hasColumn(upgraded, "meal_records", "menu_name"));
            assertTrue(hasColumn(upgraded, "meal_records", "restaurant_id"));
            assertTrue(hasColumn(upgraded, "meal_records", "restaurant_location_id"));
            assertTrue(hasColumn(upgraded, "meal_records", "restaurant_menu_id"));
            assertTrue(hasColumn(upgraded, "meal_records", "catalog_product_id"));
            assertTrue(hasColumn(upgraded, "meal_records", "composition_template_id"));
            assertTrue(hasColumn(upgraded, "meal_records", "composition_template_revision"));
            assertTrue(hasColumn(upgraded, "supplement_items", "product_form"));
            assertTrue(hasColumn(upgraded, "supplement_items", "purpose_code"));
            assertTrue(hasColumn(upgraded, "supplement_schedules", "effective_from"));
            assertTrue(hasColumn(upgraded, "supplement_schedules", "active_ingredient_amount"));
            assertTrue(hasColumn(upgraded, "supplement_schedule_slots", "timing_label"));
            assertTrue(hasColumn(upgraded, "supplement_intake_records", "record_source"));
            assertTrue(hasColumn(upgraded, "supplement_effect_checkins", "effect_score"));
            assertTrue(hasColumn(upgraded, "meal_record_items", "brand_snapshot"));
            assertTrue(hasColumn(upgraded, "meal_record_items", "composition_template_id"));
            assertTrue(hasColumn(
                    upgraded,
                    "meal_record_item_components",
                    "composition_group_key_snapshot"
            ));
            assertTrue(hasColumn(
                    upgraded,
                    "meal_record_item_components",
                    "composition_group_type_snapshot"
            ));
            assertTrue(hasColumn(
                    upgraded,
                    "meal_record_item_components",
                    "composition_role_snapshot"
            ));
            assertTrue(hasColumn(
                    upgraded,
                    "meal_record_item_components",
                    "composition_member_id_snapshot"
            ));
            assertTrue(hasColumn(upgraded, "composition_groups", "group_type"));
            assertTrue(hasColumn(
                    upgraded,
                    "meal_record_item_components",
                    "meal_record_item_id"
            ));
            assertTrue(hasColumn(
                    upgraded,
                    "meal_record_item_component_nutrients",
                    "meal_record_item_component_id"
            ));
            assertTrue(indexExists(upgraded, "nutrition_foods_owner_brand_name_idx"));
            assertTrue(indexExists(upgraded, "nutrition_foods_owner_category_idx"));
            assertTrue(indexExists(upgraded, "meal_record_item_components_parent_order_idx"));
            assertTrue(tableExists(upgraded, "composition_templates"));
            assertTrue(tableExists(upgraded, "composition_groups"));
            assertTrue(tableExists(upgraded, "composition_members"));
            assertTrue(tableExists(upgraded, "dining_out_menu_add_on_links"));
            assertTrue(indexExists(upgraded, "composition_templates_user_kind_updated_idx"));
            assertEquals("local-user", scalar(
                    upgraded,
                    "SELECT user_id FROM cardio_sessions WHERE record_id = 'record-1'"
            ));
            assertEquals("local-user", scalar(
                    upgraded,
                    "SELECT user_id FROM cardio_route_points WHERE record_id = 'record-1'"
            ));
            assertEquals("local-user", scalar(
                    upgraded,
                    "SELECT user_id FROM meal_menu_presets WHERE id = 'preset-1'"
            ));
            assertEquals("1", scalar(
                    upgraded,
                    "SELECT COUNT(*) FROM composition_templates " +
                            "WHERE template_kind = 'dining_out'"
            ));
            assertEquals("1", scalar(
                    upgraded,
                    "SELECT COUNT(*) FROM composition_members " +
                            "WHERE nutrition_food_id = 'legacy-option-1'"
            ));
            assertEquals("legacy_options", scalar(
                    upgraded,
                    "SELECT group_key FROM composition_groups LIMIT 1"
            ));
            assertEquals("other", scalar(
                    upgraded,
                    "SELECT group_type FROM composition_groups LIMIT 1"
            ));
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void localClaimResolvesUniqueConflictsAndLeavesNoAnonymousRows() {
        Context isolatedContext = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);

        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase database = helper.getWritableDatabase();
            database.execSQL("INSERT INTO nutrition_goals (user_id, phase, calories_kcal, " +
                    "protein_grams, carbs_grams, fat_grams, fiber_grams, sodium_mg, water_ml, " +
                    "created_at, updated_at) VALUES " +
                    "('local-user', 'maintenance', 2000, 150, 200, 60, 25, 2000, 2500, " +
                    "'2026-08-10T08:59:59+09:00', '2026-08-10T08:59:59+09:00'), " +
                    "(?, 'cut', 2400, 180, 240, 70, 30, 2200, 3000, " +
                    "'2026-08-10T00:00:00Z', '2026-08-10T00:00:00Z')",
                    new Object[]{AUTH_USER_ID});
            database.execSQL("INSERT INTO nutrition_daily_checkins (id, user_id, date, " +
                    "water_ml, created_at, updated_at) VALUES " +
                    "('local-checkin', 'local-user', '2026-08-10', 3500, " +
                    "'2026-08-10T09:00:01+09:00', '2026-08-10T09:00:01+09:00'), " +
                    "('remote-checkin', ?, '2026-08-10', 2000, " +
                    "'2026-08-10T00:00:00Z', '2026-08-10T00:00:00Z')",
                    new Object[]{AUTH_USER_ID});
            database.execSQL("INSERT INTO meal_menu_presets (id, user_id, name, calories, " +
                    "created_at, updated_at) VALUES " +
                    "('local-preset', 'local-user', 'Chicken', 500, " +
                    "'2026-08-10T09:00:01+09:00', '2026-08-10T09:00:01+09:00'), " +
                    "('remote-preset', ?, 'chicken', 300, " +
                    "'2026-08-10T00:00:00Z', '2026-08-10T00:00:00Z')",
                    new Object[]{AUTH_USER_ID});
            database.execSQL("INSERT INTO product_nutrition_links (id, owner_id, " +
                    "nutrition_food_id, catalog_product_id, status, source_type, " +
                    "product_contract_version, revision, reviewed_at, created_at, updated_at) " +
                    "VALUES ('local-link', 'local-user', 'food-1', " +
                    "'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'approved', 'manual', " +
                    "'product-read.v1', 2, '2026-08-10T09:00:01+09:00', " +
                    "'2026-08-10T09:00:01+09:00', '2026-08-10T09:00:01+09:00'), " +
                    "('remote-link', ?, 'food-1', " +
                    "'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'approved', 'manual', " +
                    "'product-read.v1', 1, '2026-08-10T00:00:00Z', " +
                    "'2026-08-10T00:00:00Z', '2026-08-10T00:00:00Z')",
                    new Object[]{AUTH_USER_ID});
            database.execSQL("INSERT INTO body_profiles " +
                    "(user_id, height_cm, created_at, updated_at) VALUES " +
                    "('local-user', 181, '2026-08-10T09:00:01+09:00', " +
                    "'2026-08-10T09:00:01+09:00'), " +
                    "(?, 170, '2026-08-10T00:00:00Z', '2026-08-10T00:00:00Z')",
                    new Object[]{AUTH_USER_ID});
            database.execSQL("INSERT INTO development_goals " +
                    "(user_id, objective, weekly_sessions_target, focus_body_part, " +
                    "effective_from, created_at, updated_at) VALUES " +
                    "('local-user', 'muscle_gain', 4, 'chest', '2026-08-10', " +
                    "'2026-08-10T09:00:01+09:00', '2026-08-10T09:00:01+09:00'), " +
                    "(?, 'strength', 5, 'back', '2026-08-10', " +
                    "'2026-08-10T00:00:02Z', '2026-08-10T00:00:02Z')",
                    new Object[]{AUTH_USER_ID});

            FitnessRepository repository = new FitnessRepository(helper, "local-user");
            repository.normalizeLocalUserId(AUTH_USER_ID);
            NutritionCatalogRepository catalogRepository = new NutritionCatalogRepository(
                    helper,
                    "local-user",
                    SupabaseConfig.empty()
            );
            catalogRepository.normalizeLocalUserId(AUTH_USER_ID);
            DevelopmentRepository developmentRepository = new DevelopmentRepository(
                    helper,
                    "local-user"
            );
            developmentRepository.normalizeLocalUserId(AUTH_USER_ID);

            assertEquals("2400", scalar(database,
                    "SELECT calories_kcal FROM nutrition_goals WHERE user_id = '" +
                            AUTH_USER_ID + "'"));
            assertEquals("3500", scalar(database,
                    "SELECT water_ml FROM nutrition_daily_checkins WHERE user_id = '" +
                            AUTH_USER_ID + "' AND date = '2026-08-10'"));
            assertEquals("500", scalar(database,
                    "SELECT calories FROM meal_menu_presets WHERE user_id = '" +
                            AUTH_USER_ID + "' AND name = 'chicken' COLLATE NOCASE"));
            assertEquals("0", scalar(database,
                    "SELECT COUNT(*) FROM nutrition_goals WHERE user_id = 'local-user'"));
            assertEquals("0", scalar(database,
                    "SELECT COUNT(*) FROM nutrition_daily_checkins WHERE user_id = 'local-user'"));
            assertEquals("0", scalar(database,
                    "SELECT COUNT(*) FROM meal_menu_presets WHERE user_id = 'local-user'"));
            assertEquals("local-link", scalar(database,
                    "SELECT id FROM product_nutrition_links WHERE owner_id = '" +
                            AUTH_USER_ID + "' AND status = 'approved' AND deleted_at IS NULL"));
            assertEquals("0", scalar(database,
                    "SELECT COUNT(*) FROM product_nutrition_links " +
                            "WHERE owner_id = 'local-user'"));
            assertEquals("181", scalar(database,
                    "SELECT height_cm FROM body_profiles WHERE user_id = '" +
                            AUTH_USER_ID + "'"));
            assertEquals("strength", scalar(database,
                    "SELECT objective FROM development_goals WHERE user_id = '" +
                            AUTH_USER_ID + "'"));
            assertEquals("5", scalar(database,
                    "SELECT weekly_sessions_target FROM development_goals WHERE user_id = '" +
                            AUTH_USER_ID + "'"));
            assertEquals("0", scalar(database,
                    "SELECT COUNT(*) FROM body_profiles WHERE user_id = 'local-user'"));
            assertEquals("0", scalar(database,
                    "SELECT COUNT(*) FROM development_goals WHERE user_id = 'local-user'"));
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static void createVersionEightSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE workout_records (id TEXT PRIMARY KEY, user_id TEXT NOT NULL)");
        db.execSQL("INSERT INTO workout_records (id, user_id) VALUES ('record-1', 'local-user')");

        db.execSQL("CREATE TABLE cardio_sessions (" +
                "record_id TEXT PRIMARY KEY, activity_type TEXT NOT NULL, status TEXT NOT NULL, " +
                "started_at_epoch_ms INTEGER NOT NULL, last_resumed_at_epoch_ms INTEGER, " +
                "active_duration_ms INTEGER NOT NULL DEFAULT 0, " +
                "distance_meters REAL NOT NULL DEFAULT 0, " +
                "accepted_point_count INTEGER NOT NULL DEFAULT 0, last_latitude REAL, " +
                "last_longitude REAL, last_location_time_ms INTEGER, last_accuracy_meters REAL, " +
                "gps_status TEXT NOT NULL DEFAULT 'searching', updated_at_epoch_ms INTEGER NOT NULL)");
        db.execSQL("INSERT INTO cardio_sessions (record_id, activity_type, status, " +
                "started_at_epoch_ms, updated_at_epoch_ms) " +
                "VALUES ('record-1', 'running', 'paused', 1, 1)");
        db.execSQL("CREATE INDEX cardio_sessions_status_started_idx " +
                "ON cardio_sessions(status, started_at_epoch_ms)");

        db.execSQL("CREATE TABLE cardio_route_points (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, record_id TEXT NOT NULL, " +
                "captured_at_epoch_ms INTEGER NOT NULL, latitude REAL NOT NULL, " +
                "longitude REAL NOT NULL, accuracy_meters REAL NOT NULL, speed_mps REAL, " +
                "segment_distance_meters REAL NOT NULL DEFAULT 0)");
        db.execSQL("INSERT INTO cardio_route_points (record_id, captured_at_epoch_ms, latitude, " +
                "longitude, accuracy_meters) VALUES ('record-1', 1, 37.0, 127.0, 5.0)");

        db.execSQL("CREATE TABLE meal_menu_presets (" +
                "id TEXT PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE UNIQUE, calories INTEGER, " +
                "protein_grams REAL, carbs_grams REAL, fat_grams REAL, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
        db.execSQL("INSERT INTO meal_menu_presets (id, name, created_at, updated_at) " +
                "VALUES ('preset-1', 'Chicken', '2026-08-08T00:00:00Z', '2026-08-08T00:00:00Z')");
        db.execSQL("CREATE INDEX meal_menu_presets_updated_idx " +
                "ON meal_menu_presets(updated_at DESC)");

        db.execSQL("CREATE TABLE nutrition_foods (" +
                "id TEXT PRIMARY KEY, owner_id TEXT, name TEXT NOT NULL, kind TEXT NOT NULL, " +
                "basis_amount REAL NOT NULL, basis_unit TEXT NOT NULL, " +
                "calories_kcal REAL NOT NULL DEFAULT 0, " +
                "protein_grams REAL NOT NULL DEFAULT 0, carbs_grams REAL NOT NULL DEFAULT 0, " +
                "fat_grams REAL NOT NULL DEFAULT 0, source_type TEXT NOT NULL, " +
                "source_reference TEXT, visibility TEXT NOT NULL DEFAULT 'private', " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT)");
        db.execSQL("INSERT INTO nutrition_foods (" +
                "id, owner_id, name, kind, basis_amount, basis_unit, calories_kcal, " +
                "protein_grams, carbs_grams, fat_grams, source_type, source_reference, " +
                "created_at, updated_at) VALUES (" +
                "'legacy-option-1', 'local-user', '감자튀김', 'external_menu', 1, 'serving', " +
                "320, 4, 42, 15, 'manual_option', " +
                "'{\"restaurant_name\":\"맥도날드\",\"menu_name\":\"고추크림치즈버거 세트\",\"restaurant_id\":\"restaurant-1\",\"restaurant_menu_id\":\"menu-1\"}', " +
                "'2026-08-08T00:00:00Z', '2026-08-08T00:00:00Z')");

        db.execSQL("CREATE TABLE meal_record_items (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, meal_record_id TEXT NOT NULL, " +
                "food_id TEXT, food_name_snapshot TEXT NOT NULL, quantity REAL NOT NULL, " +
                "unit TEXT NOT NULL, calories REAL NOT NULL DEFAULT 0, " +
                "protein_grams REAL NOT NULL DEFAULT 0, carbs_grams REAL NOT NULL DEFAULT 0, " +
                "fat_grams REAL NOT NULL DEFAULT 0, order_index INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, " +
                "device_id TEXT NOT NULL)");
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPrimaryKeyColumn(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return cursor.getInt(cursor.getColumnIndexOrThrow("pk")) > 0;
                }
            }
        }
        return false;
    }

    private static boolean indexExists(SQLiteDatabase db, String index) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
                new String[]{index}
        )) {
            return cursor.moveToFirst();
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{table}
        )) {
            return cursor.moveToFirst();
        }
    }

    private static String scalar(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
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
