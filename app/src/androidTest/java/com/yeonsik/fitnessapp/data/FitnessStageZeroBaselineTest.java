package com.yeonsik.fitnessapp.data;

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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Stage 0 guardrail for the fresh-install v50 SQLite contract.
 *
 * <p>This intentionally checks the shared storage boundary rather than adding a new schema
 * abstraction. Historical v8 upgrade coverage remains in {@link FitnessDatabaseMigrationTest}.</p>
 */
@RunWith(AndroidJUnit4.class)
public final class FitnessStageZeroBaselineTest {
    private static final String DATABASE_PREFIX = "stage_zero_baseline_";
    private static final Set<String> EXPECTED_TABLES = new LinkedHashSet<>(Arrays.asList(
            "body_profiles",
            "cardio_route_points",
            "cardio_sessions",
            "composition_groups",
            "composition_members",
            "composition_templates",
            "development_goals",
            "devices",
            "dining_out_menu_add_on_links",
            "dining_out_menu_component_links",
            "exercise_picker_preferences",
            "meal_menu_presets",
            "meal_record_item_component_nutrients",
            "meal_record_item_components",
            "meal_record_item_consumptions",
            "meal_record_item_nutrients",
            "meal_record_items",
            "meal_records",
            "nutrition_daily_checkins",
            "nutrition_food_components",
            "nutrition_food_nutrients",
            "nutrition_foods",
            "nutrition_goals",
            "pricetrace_product_cache",
            "product_nutrition_links",
            "routine_exercises",
            "routines",
            "supplement_effect_checkins",
            "supplement_intake_records",
            "supplement_items",
            "supplement_schedule_slots",
            "supplement_schedules",
            "sync_state",
            "verified_receipt_items",
            "weight_records",
            "workout_exercises",
            "workout_records",
            "workout_sets"
    ));

    @Test
    public void newInstallMatchesV50SchemaBaseline() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            SQLiteDatabase database = helper.getWritableDatabase();

            assertEquals(FitnessDatabaseHelper.DATABASE_VERSION, database.getVersion());
            assertEquals(EXPECTED_TABLES, userTables(database));

            assertColumns(database, "weight_records",
                    "id", "user_id", "date", "weight_kg", "is_backfilled", "updated_at",
                    "deleted_at", "device_id", "source_app", "scope", "metadata",
                    "contract_version");
            assertColumns(database, "workout_sets",
                    "id", "user_id", "workout_exercise_id", "weight_kg", "volume_kg",
                    "input_load_value", "input_load_unit", "load_state", "is_completed",
                    "deleted_at", "contract_version");
            assertColumns(database, "meal_records",
                    "id", "user_id", "date", "meal_kind", "restaurant_id",
                    "restaurant_location_id", "restaurant_menu_id", "catalog_product_id",
                    "composition_template_id", "nutrition_calculation_contract", "metadata",
                    "contract_version");
            assertColumns(database, "nutrition_foods",
                    "id", "owner_id", "name", "kind", "basis_amount", "basis_unit",
                    "calories_kcal", "protein_grams", "carbs_grams", "fat_grams",
                    "source_type", "source_reference", "visibility", "deleted_at");
            assertColumns(database, "sync_state",
                    "scope_key", "table_name", "direction", "cursor_version", "cursor_id",
                    "updated_at");

            assertIndex(database, "workout_records_user_scope_date_idx");
            assertIndex(database, "workout_sets_load_state_idx");
            assertIndex(database, "meal_records_dining_out_identity_idx");
            assertIndex(database, "meal_record_item_components_parent_order_idx");
            assertIndex(database, "nutrition_foods_owner_product_hierarchy_idx");
            assertIndex(database, "product_nutrition_links_one_approved_idx");
            assertIndex(database, "cardio_sessions_user_status_started_idx");
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static Set<String> userTables(SQLiteDatabase database) {
        Set<String> tables = new LinkedHashSet<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' "
                        + "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' "
                        + "ORDER BY name",
                null
        )) {
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0));
            }
        }
        return tables;
    }

    private static void assertColumns(SQLiteDatabase database, String table, String... columns) {
        Set<String> actual = new LinkedHashSet<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                actual.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        for (String column : columns) {
            assertTrue("Missing " + table + "." + column, actual.contains(column));
        }
    }

    private static void assertIndex(SQLiteDatabase database, String index) {
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
                new String[]{index}
        )) {
            assertTrue("Missing index " + index, cursor.moveToFirst());
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
