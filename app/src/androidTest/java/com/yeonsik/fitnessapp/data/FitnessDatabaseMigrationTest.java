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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Exercises the historical v8 schema against the current SQLiteOpenHelper on a real device. */
@RunWith(AndroidJUnit4.class)
public final class FitnessDatabaseMigrationTest {
    private static final String DATABASE_PREFIX = "migration_v8_to_current_";
    private static final String AUTH_USER_ID = "11111111-1111-4111-8111-111111111111";
    private static final String[] PACKAGED_FOOD_HIERARCHY_COLUMNS = {
            "manufacturer_name",
            "brand_name",
            "sub_brand_name",
            "product_name",
            "package_amount",
            "package_unit",
            "package_count"
    };

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
            assertTrue(hasColumn(upgraded, "workout_sets", "load_state"));
            assertTrue(hasColumn(upgraded, "workout_sets", "input_load_value"));
            assertTrue(hasColumn(upgraded, "workout_sets", "input_load_unit"));
            assertEquals("80.0", scalar(
                    upgraded,
                    "SELECT weight_kg FROM workout_sets WHERE id = 'set-1'"
            ));
            assertNull(scalarNullable(
                    upgraded,
                    "SELECT input_load_value FROM workout_sets WHERE id = 'set-1'"
            ));
            assertNull(scalarNullable(
                    upgraded,
                    "SELECT input_load_unit FROM workout_sets WHERE id = 'set-1'"
            ));
            assertTrue(tableExists(upgraded, "exercise_picker_preferences"));
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
            assertNutritionHierarchySchema(upgraded);
            assertTrue(isNullableColumn(upgraded, "nutrition_foods", "calories_kcal"));
            assertTrue(isNullableColumn(upgraded, "nutrition_foods", "protein_grams"));
            assertTrue(isNullableColumn(upgraded, "nutrition_foods", "carbs_grams"));
            assertTrue(isNullableColumn(upgraded, "nutrition_foods", "fat_grams"));
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
            NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                    helper,
                    "local-user",
                    SupabaseConfig.empty()
            );
            NutritionFood legacyFood = catalog.findFoodById("legacy-option-1");
            assertNotNull(legacyFood);
            assertEquals("감자튀김", legacyFood.name);
        } finally {
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void testV49UpgradeAddsNullableInputProvenanceWithoutBackfill() {
        Context isolatedContext = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext(),
                "migration_v49_to_v50_"
        );
        FitnessDatabaseHelper creator = null;
        FitnessDatabaseHelper helper = null;
        try {
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
            creator = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase database = creator.getWritableDatabase();
            database.execSQL("INSERT INTO workout_sets (" +
                    "id, user_id, workout_exercise_id, set_index, actual_reps, weight_kg, " +
                    "volume_kg, load_state, is_completed, created_at, updated_at, " +
                    "device_id, contract_version) VALUES (" +
                    "'v49-set', 'migration-user', 'v49-exercise', 1, 5, 80, 400, " +
                    "'external_load', 1, '2026-09-05T00:00:00Z', " +
                    "'2026-09-05T00:00:00Z', 'device-1', 1)");

            database.execSQL("CREATE TABLE workout_sets_v49 AS SELECT " +
                    "id, user_id, workout_exercise_id, set_index, target_reps, actual_reps, " +
                    "weight_kg, volume_kg, duration_seconds, distance_meters, rest_seconds, " +
                    "assisted_weight_kg, added_weight_kg, load_state, is_completed, rpe, rir, " +
                    "memo, created_at, updated_at, deleted_at, device_id, contract_version " +
                    "FROM workout_sets");
            database.execSQL("DROP TABLE workout_sets");
            database.execSQL("ALTER TABLE workout_sets_v49 RENAME TO workout_sets");
            database.setVersion(49);
            creator.close();
            creator = null;

            helper = new FitnessDatabaseHelper(isolatedContext);
            SQLiteDatabase upgraded = helper.getWritableDatabase();
            assertEquals(FitnessDatabaseHelper.DATABASE_VERSION, upgraded.getVersion());
            assertTrue(hasColumn(upgraded, "workout_sets", "input_load_value"));
            assertTrue(hasColumn(upgraded, "workout_sets", "input_load_unit"));
            assertEquals("80.0", scalar(
                    upgraded,
                    "SELECT weight_kg FROM workout_sets WHERE id = 'v49-set'"
            ));
            assertNull(scalarNullable(
                    upgraded,
                    "SELECT input_load_value FROM workout_sets WHERE id = 'v49-set'"
            ));
            assertNull(scalarNullable(
                    upgraded,
                    "SELECT input_load_unit FROM workout_sets WHERE id = 'v49-set'"
            ));
        } finally {
            if (creator != null) {
                creator.close();
            }
            if (helper != null) {
                helper.close();
            }
            isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void testV45ThroughV48IncompletePreviewDatabasesAreRepairedIdempotently() {
        for (int previewVersion = 45; previewVersion <= 48; previewVersion++) {
            Context isolatedContext = new IsolatedDatabaseContext(
                    ApplicationProvider.getApplicationContext(),
                    "migration_v" + previewVersion + "_incomplete_"
            );
            FitnessDatabaseHelper helper = null;
            FitnessDatabaseHelper creator = null;
            String diningOutId = "preview-dining-menu-" + previewVersion;
            String packagedId = "preview-packaged-food-" + previewVersion;
            try {
                isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
                creator = new FitnessDatabaseHelper(isolatedContext);
                SQLiteDatabase complete = creator.getWritableDatabase();
                insertPreviewRows(complete, diningOutId, packagedId);
                creator.close();
                creator = null;

                SQLiteDatabase preview = isolatedContext.openOrCreateDatabase(
                        FitnessDatabaseHelper.DATABASE_NAME,
                        0,
                        null
                );
                makeNutritionFoodsIncomplete(preview);
                preview.setVersion(previewVersion);
                preview.close();

                helper = new FitnessDatabaseHelper(isolatedContext);
                SQLiteDatabase upgraded = helper.getWritableDatabase();
                assertEquals(FitnessDatabaseHelper.DATABASE_VERSION, upgraded.getVersion());
                assertNutritionHierarchySchema(upgraded);
                assertEquals("Preview Menu", scalar(
                        upgraded,
                        "SELECT name FROM nutrition_foods WHERE id = '" + diningOutId + "'"
                ));
                assertEquals("2", scalar(
                        upgraded,
                        "SELECT COUNT(*) FROM nutrition_foods WHERE id IN ('" + diningOutId +
                                "', '" + packagedId + "')"
                ));

                NutritionCatalogRepository catalog = new NutritionCatalogRepository(
                        helper,
                        "preview-user",
                        SupabaseConfig.empty()
                );
                assertNotNull(catalog.findFoodById(diningOutId));
                assertTrue(catalog.savedDiningOutMenus().stream()
                        .anyMatch(food -> diningOutId.equals(food.id)));
                assertEquals(2, catalog.searchFoods("Preview").size());
                assertEquals(1, catalog.searchPackagedFoods("Preview Product", 20).size());

                // Re-open the repaired shape through the same v48 -> v49 path. IF NOT EXISTS and
                // addColumnIfMissing must make the repair safe when it is encountered again.
                helper.close();
                helper = null;
                SQLiteDatabase idempotencyFixture = isolatedContext.openOrCreateDatabase(
                        FitnessDatabaseHelper.DATABASE_NAME,
                        0,
                        null
                );
                idempotencyFixture.setVersion(48);
                idempotencyFixture.close();
                helper = new FitnessDatabaseHelper(isolatedContext);
                SQLiteDatabase reopened = helper.getWritableDatabase();
                assertEquals(FitnessDatabaseHelper.DATABASE_VERSION, reopened.getVersion());
                assertNutritionHierarchySchema(reopened);
                assertEquals("1", scalar(
                        reopened,
                        "SELECT COUNT(*) FROM nutrition_foods WHERE id = '" + diningOutId + "'"
                ));
            } finally {
                if (creator != null) {
                    creator.close();
                }
                if (helper != null) {
                    helper.close();
                }
                isolatedContext.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
            }
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

    private static void assertNutritionHierarchySchema(SQLiteDatabase db) {
        for (String column : PACKAGED_FOOD_HIERARCHY_COLUMNS) {
            assertTrue("Missing nutrition_foods column: " + column,
                    hasColumn(db, "nutrition_foods", column));
        }
        assertTrue(indexExists(db, "nutrition_foods_owner_product_hierarchy_idx"));
    }

    private static void insertPreviewRows(
            SQLiteDatabase db,
            String diningOutId,
            String packagedId
    ) {
        db.execSQL("INSERT INTO nutrition_foods (" +
                "id, owner_id, name, brand, manufacturer_name, brand_name, sub_brand_name, " +
                "product_name, package_amount, package_unit, package_count, kind, category, " +
                "basis_amount, basis_unit, calories_kcal, protein_grams, carbs_grams, " +
                "fat_grams, source_type, source_reference, visibility, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                        diningOutId,
                        "preview-user",
                        "Preview Menu",
                        "Preview Restaurant",
                        "Preview Restaurant",
                        "Preview Brand",
                        null,
                        "Preview Menu",
                        1d,
                        "serving",
                        1,
                        NutritionFood.KIND_EXTERNAL_MENU,
                        NutritionFood.CATEGORY_OTHER,
                        1d,
                        NutritionUnit.SERVING,
                        500d,
                        20d,
                        60d,
                        15d,
                        "manual_estimate",
                        "{\"restaurant_name\":\"Preview Restaurant\",\"menu_name\":\"Preview Menu\"}",
                        "private",
                        "2026-08-31T00:00:00Z",
                        "2026-08-31T00:00:00Z"
                });
        db.execSQL("INSERT INTO nutrition_foods (" +
                "id, owner_id, name, brand, manufacturer_name, brand_name, sub_brand_name, " +
                "product_name, package_amount, package_unit, package_count, kind, category, " +
                "basis_amount, basis_unit, calories_kcal, protein_grams, carbs_grams, " +
                "fat_grams, source_type, visibility, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new Object[]{
                        packagedId,
                        "preview-user",
                        "Preview Product",
                        "Preview Brand",
                        "Preview Manufacturer",
                        "Preview Brand",
                        "Preview Sub Brand",
                        "Preview Product",
                        450d,
                        "g",
                        1,
                        NutritionFood.KIND_EXTERNAL_MENU,
                        NutritionFood.CATEGORY_PROCESSED,
                        100d,
                        NutritionUnit.GRAM,
                        300d,
                        12d,
                        30d,
                        10d,
                        "manual",
                        "private",
                        "2026-08-31T00:00:00Z",
                        "2026-08-31T00:00:00Z"
                });
    }

    private static void makeNutritionFoodsIncomplete(SQLiteDatabase db) {
        db.execSQL("DROP INDEX IF EXISTS nutrition_foods_owner_name_idx");
        db.execSQL("DROP INDEX IF EXISTS nutrition_foods_owner_brand_name_idx");
        db.execSQL("DROP INDEX IF EXISTS nutrition_foods_owner_product_hierarchy_idx");
        db.execSQL("DROP INDEX IF EXISTS nutrition_foods_owner_category_idx");
        db.execSQL("DROP INDEX IF EXISTS nutrition_foods_visibility_name_idx");
        db.execSQL("DROP TABLE IF EXISTS nutrition_foods_incomplete");
        db.execSQL("CREATE TABLE nutrition_foods_incomplete AS SELECT " +
                "id, owner_id, name, brand, kind, category, basis_amount, basis_unit, " +
                "prep_state, cooking_method, calories_kcal, protein_grams, carbs_grams, " +
                "fat_grams, sodium_mg, saturated_fat_grams, sugars_grams, fiber_grams, " +
                "added_sugars_grams, trans_fat_grams, cholesterol_mg, source_type, " +
                "source_reference, source_version, data_version, revision, visibility, " +
                "created_at, updated_at, deleted_at FROM nutrition_foods");
        db.execSQL("DROP TABLE nutrition_foods");
        db.execSQL("ALTER TABLE nutrition_foods_incomplete RENAME TO nutrition_foods");
    }

    private static void createVersionEightSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE workout_records (id TEXT PRIMARY KEY, user_id TEXT NOT NULL)");
        db.execSQL("INSERT INTO workout_records (id, user_id) VALUES ('record-1', 'local-user')");

        // These tables existed in the v8 application schema. Keep the fixture minimal while
        // retaining the columns required by later historical migrations.
        db.execSQL("CREATE TABLE workout_exercises (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, record_id TEXT NOT NULL, " +
                "order_index INTEGER NOT NULL, exercise_id TEXT NOT NULL, " +
                "exercise_name_snapshot TEXT NOT NULL, ui_part TEXT NOT NULL, " +
                "primary_sub_part_snapshot TEXT, equipment_snapshot TEXT, " +
                "record_type TEXT NOT NULL, memo TEXT, created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, deleted_at TEXT, device_id TEXT NOT NULL)");
        db.execSQL("CREATE TABLE workout_sets (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, workout_exercise_id TEXT NOT NULL, " +
                "set_index INTEGER NOT NULL, target_reps INTEGER, actual_reps INTEGER, " +
                "weight_kg REAL, duration_seconds INTEGER, distance_meters REAL, " +
                "rest_seconds INTEGER, is_completed INTEGER NOT NULL, rpe INTEGER, memo TEXT, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, " +
                "device_id TEXT NOT NULL)");
        db.execSQL("INSERT INTO workout_sets (" +
                "id, user_id, workout_exercise_id, set_index, target_reps, actual_reps, " +
                "weight_kg, is_completed, created_at, updated_at, device_id) VALUES (" +
                "'set-1', 'local-user', 'exercise-1', 1, 5, 5, 80, 1, " +
                "'2026-08-08T00:00:00Z', '2026-08-08T00:00:00Z', 'device-1')");
        db.execSQL("CREATE TABLE routines (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, name TEXT NOT NULL, " +
                "is_default INTEGER NOT NULL, device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT)");
        db.execSQL("CREATE TABLE routine_exercises (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, routine_id TEXT NOT NULL, " +
                "exercise_id TEXT NOT NULL, name_ko TEXT NOT NULL, ui_part TEXT NOT NULL, " +
                "primary_sub_part TEXT NOT NULL, equipment TEXT NOT NULL, " +
                "record_type TEXT NOT NULL, order_index INTEGER NOT NULL, " +
                "device_id TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");

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

    private static boolean isNullableColumn(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 0;
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

    private static String scalarNullable(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getString(0);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private final String databasePrefix;

        private IsolatedDatabaseContext(Context base) {
            this(base, DATABASE_PREFIX);
        }

        private IsolatedDatabaseContext(Context base, String databasePrefix) {
            super(base);
            this.databasePrefix = databasePrefix;
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(databasePrefix + name);
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
