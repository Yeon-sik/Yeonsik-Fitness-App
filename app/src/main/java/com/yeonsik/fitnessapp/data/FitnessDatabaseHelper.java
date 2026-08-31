package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FitnessDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "fitness_mvp.db";
    public static final int DATABASE_VERSION = 47;
    private final Context appContext;

    public FitnessDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        appContext = context.getApplicationContext() == null ? context : context.getApplicationContext();
    }

    void reconcileVerifiedFoodCatalog(SQLiteDatabase database) {
        VerifiedFoodCatalogSeed.seed(appContext, database);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createSharedRecordTables(db);
        createSyncStateTables(db);
        createDiningOutMealIndexes(db);
        createRoutineTables(db);
        createExercisePickerPreferenceTable(db);
        createCardioTables(db);
        createMealMenuPresetTable(db);
        createNutritionTables(db);
        createCompositionTables(db);
        createDiningOutComponentLinkTable(db);
        createDiningOutConsumptionTables(db);
        createNutritionIndexes(db);
        createProductNutritionLinkTables(db);
        createVerifiedReceiptImportTable(db);
        createAthleteNutritionTables(db);
        createDevelopmentTables(db);
        createSupplementTables(db);
        upgradeExerciseFamilyIdentitySchema(db);
        reconcileVerifiedFoodCatalog(db);
    }

    private void createSharedRecordTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS devices (" +
                "id TEXT NOT NULL, " +
                "user_id TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "last_seen_at TEXT NOT NULL, " +
                "app_version TEXT, " +
                "PRIMARY KEY(user_id, id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS workout_records (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "workout_type TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "exercise_name TEXT NOT NULL, " +
                "duration_seconds INTEGER, " +
                "total_volume_kg REAL NOT NULL DEFAULT 0, " +
                "average_heart_rate REAL, " +
                "created_at TEXT NOT NULL, " +
                "is_backfilled INTEGER NOT NULL, " +
                "backfilled_at TEXT, " +
                "backfill_reason TEXT, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "source_app TEXT NOT NULL, " +
                "scope TEXT NOT NULL, " +
                "metadata TEXT NOT NULL, " +
                "contract_version INTEGER NOT NULL DEFAULT 1)");
        createMealRecordTable(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS weight_records (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "weight_kg REAL NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "is_backfilled INTEGER NOT NULL, " +
                "backfilled_at TEXT, " +
                "backfill_reason TEXT, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "source_app TEXT NOT NULL, " +
                "scope TEXT NOT NULL, " +
                "metadata TEXT NOT NULL, " +
                "contract_version INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE IF NOT EXISTS workout_exercises (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "record_id TEXT NOT NULL, " +
                "order_index INTEGER NOT NULL, " +
                "exercise_id TEXT NOT NULL, " +
                "exercise_name_snapshot TEXT NOT NULL, " +
                "ui_part TEXT NOT NULL, " +
                "primary_sub_part_snapshot TEXT, " +
                "equipment_snapshot TEXT, " +
                "record_type TEXT NOT NULL, " +
                "family_id TEXT, " +
                "preset_id TEXT, " +
                "canonical_variant_key TEXT, " +
                "visual_variant_key TEXT, " +
                "memo TEXT, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "contract_version INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE IF NOT EXISTS workout_sets (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "workout_exercise_id TEXT NOT NULL, " +
                "set_index INTEGER NOT NULL, " +
                "target_reps INTEGER, " +
                "actual_reps INTEGER, " +
                "weight_kg REAL, " +
                "volume_kg REAL, " +
                "duration_seconds INTEGER, " +
                "distance_meters REAL, " +
                "rest_seconds INTEGER, " +
                "assisted_weight_kg REAL, " +
                "added_weight_kg REAL, " +
                "load_state TEXT, " +
                "is_completed INTEGER NOT NULL, " +
                "rpe INTEGER, " +
                "rir INTEGER, " +
                "memo TEXT, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "contract_version INTEGER NOT NULL DEFAULT 1)");

        db.execSQL("CREATE INDEX IF NOT EXISTS devices_user_last_seen_at_idx ON devices(user_id, last_seen_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS workout_records_user_scope_date_idx ON workout_records(user_id, scope, date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS workout_exercises_record_order_idx ON workout_exercises(record_id, order_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS workout_sets_exercise_order_idx ON workout_sets(workout_exercise_id, set_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_records_user_scope_date_idx ON meal_records(user_id, scope, date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS weight_records_user_scope_date_idx ON weight_records(user_id, scope, date)");
    }

    private void createSyncStateTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_state (" +
                "scope_key TEXT NOT NULL, " +
                "table_name TEXT NOT NULL, " +
                "direction TEXT NOT NULL, " +
                "cursor_version TEXT, " +
                "cursor_id TEXT NOT NULL DEFAULT '', " +
                "updated_at TEXT NOT NULL, " +
                "PRIMARY KEY(scope_key, table_name, direction))");

        createSyncIndexIfColumnsExist(
                db,
                "devices_user_sync_push_idx",
                "devices",
                "user_id",
                "last_seen_at",
                "id"
        );
        createSyncIndexIfColumnsExist(
                db,
                "workout_records_user_sync_push_idx",
                "workout_records",
                "user_id",
                "updated_at",
                "id"
        );
        createSyncIndexIfColumnsExist(
                db,
                "workout_exercises_user_sync_push_idx",
                "workout_exercises",
                "user_id",
                "updated_at",
                "id"
        );
        createSyncIndexIfColumnsExist(
                db,
                "workout_sets_user_sync_push_idx",
                "workout_sets",
                "user_id",
                "updated_at",
                "id"
        );
        createSyncIndexIfColumnsExist(
                db,
                "meal_records_user_sync_push_idx",
                "meal_records",
                "user_id",
                "updated_at",
                "id"
        );
        createSyncIndexIfColumnsExist(
                db,
                "weight_records_user_sync_push_idx",
                "weight_records",
                "user_id",
                "updated_at",
                "id"
        );
    }

    private void createSyncIndexIfColumnsExist(
            SQLiteDatabase db,
            String indexName,
            String tableName,
            String... columns
    ) {
        if (!tableExists(db, tableName)) {
            return;
        }
        for (String column : columns) {
            if (!hasColumn(db, tableName, column)) {
                return;
            }
        }
        db.execSQL(
                "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + "("
                        + String.join(", ", columns) + ")"
        );
    }

    private void createMealRecordTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_records (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "menu TEXT NOT NULL, " +
                "meal_kind TEXT NOT NULL DEFAULT 'food', " +
                "fulfillment_mode TEXT, " +
                "store_name TEXT, " +
                "branch_name TEXT, " +
                "menu_name TEXT, " +
                "restaurant_id TEXT, " +
                "restaurant_location_id TEXT, " +
                "restaurant_menu_id TEXT, " +
                "catalog_product_id TEXT, " +
                "composition_template_id TEXT, " +
                "composition_template_revision INTEGER, " +
                "nutrition_calculation_contract TEXT, " +
                "calories INTEGER NOT NULL, " +
                "protein_grams REAL NOT NULL, " +
                "carbs_grams REAL, " +
                "fat_grams REAL, " +
                "created_at TEXT NOT NULL, " +
                "is_backfilled INTEGER NOT NULL, " +
                "backfilled_at TEXT, " +
                "backfill_reason TEXT, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "source_app TEXT NOT NULL, " +
                "scope TEXT NOT NULL, " +
                "metadata TEXT NOT NULL, " +
                "contract_version INTEGER NOT NULL DEFAULT 1)");
    }

    private void createRoutineTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS routines (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "is_default INTEGER NOT NULL, " +
                "device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS routine_exercises (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "routine_id TEXT NOT NULL, " +
                "exercise_id TEXT NOT NULL, " +
                "name_ko TEXT NOT NULL, " +
                "ui_part TEXT NOT NULL, " +
                "primary_sub_part TEXT NOT NULL, " +
                "equipment TEXT NOT NULL, " +
                "record_type TEXT NOT NULL, " +
                "family_id TEXT, " +
                "preset_id TEXT, " +
                "canonical_variant_key TEXT, " +
                "visual_variant_key TEXT, " +
                "order_index INTEGER NOT NULL, " +
                "device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS routines_user_default_idx ON routines(user_id, is_default)");
        db.execSQL("CREATE INDEX IF NOT EXISTS routine_exercises_routine_order_idx ON routine_exercises(routine_id, order_index)");
    }

    private void createExercisePickerPreferenceTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS exercise_picker_preferences (" +
                "user_id TEXT NOT NULL, " +
                "canonical_preset_id TEXT NOT NULL, " +
                "is_favorite INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "PRIMARY KEY(user_id, canonical_preset_id))");
    }

    private void createCardioTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS cardio_sessions (" +
                "record_id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "activity_type TEXT NOT NULL, " +
                "status TEXT NOT NULL, " +
                "started_at_epoch_ms INTEGER NOT NULL, " +
                "last_resumed_at_epoch_ms INTEGER, " +
                "active_duration_ms INTEGER NOT NULL DEFAULT 0, " +
                "distance_meters REAL NOT NULL DEFAULT 0, " +
                "accepted_point_count INTEGER NOT NULL DEFAULT 0, " +
                "last_latitude REAL, " +
                "last_longitude REAL, " +
                "last_location_time_ms INTEGER, " +
                "last_accuracy_meters REAL, " +
                "gps_status TEXT NOT NULL DEFAULT 'searching', " +
                "updated_at_epoch_ms INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS cardio_route_points (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "record_id TEXT NOT NULL, " +
                "user_id TEXT NOT NULL, " +
                "captured_at_epoch_ms INTEGER NOT NULL, " +
                "latitude REAL NOT NULL, " +
                "longitude REAL NOT NULL, " +
                "accuracy_meters REAL NOT NULL, " +
                "speed_mps REAL, " +
                "segment_distance_meters REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS cardio_sessions_user_status_started_idx " +
                "ON cardio_sessions(user_id, status, started_at_epoch_ms)");
        db.execSQL("CREATE INDEX IF NOT EXISTS cardio_route_points_record_time_idx " +
                "ON cardio_route_points(record_id, captured_at_epoch_ms)");
    }

    private void createMealMenuPresetTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_menu_presets (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "name TEXT NOT NULL COLLATE NOCASE, " +
                "calories INTEGER, " +
                "protein_grams REAL, " +
                "carbs_grams REAL, " +
                "fat_grams REAL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "UNIQUE(user_id, name))");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_menu_presets_updated_idx " +
                "ON meal_menu_presets(user_id, updated_at DESC)");
    }

    /**
     * 음식 카탈로그와 식사 기록 스냅샷 테이블.
     *
     * <p>필수(calories/protein/carbs/fat/sodium/saturated_fat/sugars)와 1단계 권고
     * (fiber/added_sugars/trans_fat/cholesterol) 영양소는 typed column으로 두고,
     * 미네랄·비타민은 nutrient_code 기반 확장 테이블에 넣는다.</p>
     *
     * <p>모르는 권고 영양소는 0이 아니라 NULL로 남긴다. 나트륨·포화지방·당류는 신규 저장
     * 시 애플리케이션이 필수로 요구하지만, 4대 영양소만 있던 시절의 행을 0으로 왜곡하지
     * 않으려고 컬럼 자체는 NULL을 허용한다. 필수값이 채워졌는지는 data_version으로 구분한다.</p>
     */
    private void createNutritionTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS nutrition_foods (" +
                "id TEXT PRIMARY KEY, " +
                "owner_id TEXT, " +
                "name TEXT NOT NULL, " +
                "brand TEXT, " +
                "manufacturer_name TEXT, " +
                "brand_name TEXT, " +
                "sub_brand_name TEXT, " +
                "product_name TEXT, " +
                "package_amount REAL, " +
                "package_unit TEXT, " +
                "package_count INTEGER, " +
                "kind TEXT NOT NULL, " +
                "category TEXT NOT NULL DEFAULT 'other', " +
                "basis_amount REAL NOT NULL, " +
                "basis_unit TEXT NOT NULL, " +
                "prep_state TEXT NOT NULL DEFAULT 'unspecified', " +
                "cooking_method TEXT NOT NULL DEFAULT 'unspecified', " +
                "calories_kcal REAL, " +
                "protein_grams REAL, " +
                "carbs_grams REAL, " +
                "fat_grams REAL, " +
                "sodium_mg REAL, " +
                "saturated_fat_grams REAL, " +
                "sugars_grams REAL, " +
                "fiber_grams REAL, " +
                "added_sugars_grams REAL, " +
                "trans_fat_grams REAL, " +
                "cholesterol_mg REAL, " +
                "source_type TEXT NOT NULL, " +
                "source_reference TEXT, " +
                "source_version TEXT, " +
                "data_version INTEGER NOT NULL DEFAULT 1, " +
                "revision INTEGER NOT NULL DEFAULT 1, " +
                "visibility TEXT NOT NULL DEFAULT 'private', " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS nutrition_food_nutrients (" +
                "id TEXT PRIMARY KEY, " +
                "owner_id TEXT, " +
                "food_id TEXT NOT NULL, " +
                "nutrient_code TEXT NOT NULL, " +
                "amount REAL, " +
                "unit TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "UNIQUE(food_id, nutrient_code))");
        db.execSQL("CREATE TABLE IF NOT EXISTS nutrition_food_components (" +
                "id TEXT PRIMARY KEY, " +
                "owner_id TEXT, " +
                "parent_food_id TEXT NOT NULL, " +
                "child_food_id TEXT NOT NULL, " +
                "quantity REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "order_index INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_record_items (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "meal_record_id TEXT NOT NULL, " +
                "composition_template_id TEXT, " +
                "composition_template_revision_snapshot INTEGER, " +
                "food_id TEXT, " +
                "food_name_snapshot TEXT NOT NULL, " +
                "brand_snapshot TEXT, " +
                "manufacturer_name_snapshot TEXT, " +
                "brand_name_snapshot TEXT, " +
                "sub_brand_name_snapshot TEXT, " +
                "product_name_snapshot TEXT, " +
                "package_amount_snapshot REAL, " +
                "package_unit_snapshot TEXT, " +
                "package_count_snapshot INTEGER, " +
                "food_kind_snapshot TEXT, " +
                "quantity REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "basis_amount_snapshot REAL, " +
                "basis_unit_snapshot TEXT, " +
                "portion_basis_snapshot TEXT, " +
                "nominal_servings_snapshot REAL, " +
                "prep_state_snapshot TEXT, " +
                "calories REAL NOT NULL DEFAULT 0, " +
                "protein_grams REAL NOT NULL DEFAULT 0, " +
                "carbs_grams REAL NOT NULL DEFAULT 0, " +
                "fat_grams REAL NOT NULL DEFAULT 0, " +
                "sodium_mg REAL, " +
                "saturated_fat_grams REAL, " +
                "sugars_grams REAL, " +
                "fiber_grams REAL, " +
                "added_sugars_grams REAL, " +
                "trans_fat_grams REAL, " +
                "cholesterol_mg REAL, " +
                "source_type_snapshot TEXT, " +
                "source_reference_snapshot TEXT, " +
                "source_version_snapshot TEXT, " +
                "food_data_version_snapshot INTEGER, " +
                "order_index INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_record_item_nutrients (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "meal_record_id TEXT NOT NULL, " +
                "meal_record_item_id TEXT NOT NULL, " +
                "nutrient_code TEXT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "UNIQUE(meal_record_item_id, nutrient_code))");
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_record_item_components (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "meal_record_id TEXT NOT NULL, " +
                "meal_record_item_id TEXT NOT NULL, " +
                "composition_group_key_snapshot TEXT, " +
                "composition_group_type_snapshot TEXT, " +
                "provision_type_snapshot TEXT, " +
                "composition_role_snapshot TEXT, " +
                "composition_member_id_snapshot TEXT, " +
                "food_id TEXT, " +
                "food_name_snapshot TEXT NOT NULL, " +
                "brand_snapshot TEXT, " +
                "food_kind_snapshot TEXT, " +
                "quantity REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "basis_amount_snapshot REAL, " +
                "basis_unit_snapshot TEXT, " +
                "prep_state_snapshot TEXT, " +
                "consumed_fraction REAL, " +
                "calories REAL, " +
                "protein_grams REAL, " +
                "carbs_grams REAL, " +
                "fat_grams REAL, " +
                "sodium_mg REAL, " +
                "saturated_fat_grams REAL, " +
                "sugars_grams REAL, " +
                "fiber_grams REAL, " +
                "added_sugars_grams REAL, " +
                "trans_fat_grams REAL, " +
                "cholesterol_mg REAL, " +
                "source_type_snapshot TEXT, " +
                "source_reference_snapshot TEXT, " +
                "source_version_snapshot TEXT, " +
                "food_data_version_snapshot INTEGER, " +
                "order_index INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_record_item_component_nutrients (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "meal_record_id TEXT NOT NULL, " +
                "meal_record_item_id TEXT NOT NULL, " +
                "meal_record_item_component_id TEXT NOT NULL, " +
                "nutrient_code TEXT NOT NULL, " +
                "amount REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "UNIQUE(meal_record_item_component_id, nutrient_code))");

    }

    /**
     * Reusable composition definitions. These are local product definitions, not meal history:
     * a template can change while a meal record remains immutable through its snapshots.
     */
    private void createCompositionTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS composition_templates (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "template_kind TEXT NOT NULL, " +
                "root_food_id TEXT, " +
                "source_reference TEXT, " +
                "revision INTEGER NOT NULL DEFAULT 1, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS composition_groups (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "template_id TEXT NOT NULL, " +
                "group_key TEXT NOT NULL, " +
                "group_type TEXT NOT NULL DEFAULT 'other', " +
                "label TEXT NOT NULL, " +
                "selection_mode TEXT NOT NULL, " +
                "min_selected INTEGER NOT NULL DEFAULT 0, " +
                "max_selected INTEGER NOT NULL DEFAULT 99, " +
                "order_index INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "UNIQUE(user_id, template_id, group_key))");
        db.execSQL("CREATE TABLE IF NOT EXISTS composition_members (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "template_id TEXT NOT NULL, " +
                "group_id TEXT, " +
                "nutrition_food_id TEXT, " +
                "name_snapshot TEXT NOT NULL, " +
                "brand_snapshot TEXT, " +
                "quantity REAL NOT NULL DEFAULT 1, " +
                "unit TEXT NOT NULL DEFAULT 'serving', " +
                "default_selected INTEGER NOT NULL DEFAULT 0, " +
                "order_index INTEGER NOT NULL, " +
                "source_reference_snapshot TEXT, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS composition_templates_user_kind_updated_idx " +
                "ON composition_templates(user_id, template_kind, updated_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS composition_groups_template_order_idx " +
                "ON composition_groups(template_id, order_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS composition_members_group_order_idx " +
                "ON composition_members(group_id, order_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS composition_members_food_idx " +
                "ON composition_members(user_id, nutrition_food_id)");
    }

    /** Legacy v38 table retained so old backups and migrations remain readable. */
    private void createDiningOutAddOnLinkTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS dining_out_menu_add_on_links (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "menu_food_id TEXT NOT NULL, " +
                "add_on_food_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "UNIQUE(user_id, menu_food_id, add_on_food_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS dining_out_menu_add_on_links_menu_idx " +
                "ON dining_out_menu_add_on_links(user_id, menu_food_id, deleted_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS dining_out_menu_add_on_links_add_on_idx " +
                "ON dining_out_menu_add_on_links(user_id, add_on_food_id, deleted_at)");
    }

    /** Permanent menu-to-component definitions for every fixed composition group type. */
    private void createDiningOutComponentLinkTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS dining_out_menu_component_links (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "menu_food_id TEXT NOT NULL, " +
                "component_food_id TEXT NOT NULL, " +
                "group_type TEXT NOT NULL DEFAULT 'other', " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "UNIQUE(user_id, menu_food_id, component_food_id, group_type))");
        db.execSQL("CREATE INDEX IF NOT EXISTS dining_out_menu_component_links_menu_idx " +
                "ON dining_out_menu_component_links(user_id, menu_food_id, group_type, deleted_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS dining_out_menu_component_links_component_idx " +
                "ON dining_out_menu_component_links(user_id, component_food_id, group_type, deleted_at)");
    }

    private void createNutritionIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_name_idx " +
                "ON nutrition_foods(owner_id, name COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_brand_name_idx " +
                "ON nutrition_foods(owner_id, brand COLLATE NOCASE, name COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_product_hierarchy_idx " +
                "ON nutrition_foods(owner_id, manufacturer_name COLLATE NOCASE, " +
                "brand_name COLLATE NOCASE, sub_brand_name COLLATE NOCASE, " +
                "product_name COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_category_idx " +
                "ON nutrition_foods(owner_id, category, cooking_method, name COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_visibility_name_idx " +
                "ON nutrition_foods(visibility, name COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_food_nutrients_food_idx " +
                "ON nutrition_food_nutrients(food_id, nutrient_code)");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_food_components_parent_order_idx " +
                "ON nutrition_food_components(parent_food_id, order_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_items_meal_order_idx " +
                "ON meal_record_items(meal_record_id, order_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_nutrients_item_idx " +
                "ON meal_record_item_nutrients(meal_record_item_id, nutrient_code)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_nutrients_meal_idx " +
                "ON meal_record_item_nutrients(meal_record_id, nutrient_code)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_components_parent_order_idx " +
                "ON meal_record_item_components(meal_record_item_id, order_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_components_meal_idx " +
                "ON meal_record_item_components(meal_record_id, meal_record_item_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_component_nutrients_component_idx " +
                "ON meal_record_item_component_nutrients(" +
                "meal_record_item_component_id, nutrient_code)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_component_nutrients_meal_idx " +
                "ON meal_record_item_component_nutrients(meal_record_id, nutrient_code)");

    }

    /** Per-item local consumption allocation for the dining-out sharing contract. */
    private void createDiningOutConsumptionTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_record_item_consumptions (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "meal_record_id TEXT NOT NULL, " +
                "meal_record_item_id TEXT NOT NULL, " +
                "contract_version TEXT NOT NULL, " +
                "consumer_scope TEXT NOT NULL DEFAULT 'self', " +
                "diner_count INTEGER NOT NULL DEFAULT 1, " +
                "consumed_fraction REAL NOT NULL, " +
                "share_method TEXT NOT NULL, " +
                "confidence TEXT NOT NULL DEFAULT 'estimated', " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL, " +
                "CHECK (diner_count >= 1 AND diner_count <= 100), " +
                "CHECK (consumed_fraction > 0 AND consumed_fraction <= 1), " +
                "UNIQUE(user_id, meal_record_item_id, consumer_scope))");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_consumptions_item_idx " +
                "ON meal_record_item_consumptions(meal_record_item_id, user_id, deleted_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_consumptions_record_idx " +
                "ON meal_record_item_consumptions(meal_record_id, user_id, deleted_at)");
    }
    /**
     * 사용자 승인 링크와 PriceTrace 읽기 캐시.
     *
     * <p>product_nutrition_links만 Nutrition DB와 동기화한다. 상품명·판매처·가격·관측시각은
     * PriceTrace가 소유하므로 pricetrace_product_cache에만 보관하고 식사 snapshot에는 넣지
     * 않는다.</p>
     */
    private void createProductNutritionLinkTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS product_nutrition_links (" +
                "id TEXT PRIMARY KEY, " +
                "owner_id TEXT NOT NULL, " +
                "nutrition_food_id TEXT NOT NULL, " +
                "catalog_product_id TEXT NOT NULL, " +
                "standard_product_id TEXT, " +
                "status TEXT NOT NULL, " +
                "source_type TEXT NOT NULL, " +
                "proposal_reference TEXT, " +
                "product_contract_version TEXT NOT NULL DEFAULT 'product-read.v1', " +
                "catalog_product_revision TEXT, " +
                "catalog_content_amount REAL, " +
                "catalog_content_unit TEXT, " +
                "catalog_package_count INTEGER, " +
                "revision INTEGER NOT NULL DEFAULT 1, " +
                "reviewed_at TEXT, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS product_nutrition_links_one_approved_idx " +
                "ON product_nutrition_links(owner_id, nutrition_food_id) " +
                "WHERE status = 'approved' AND deleted_at IS NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS product_nutrition_links_pending_idx " +
                "ON product_nutrition_links(owner_id, nutrition_food_id, created_at DESC) " +
                "WHERE status = 'suggested' AND deleted_at IS NULL");
        db.execSQL("CREATE INDEX IF NOT EXISTS product_nutrition_links_catalog_idx " +
                "ON product_nutrition_links(catalog_product_id)");

        db.execSQL("CREATE TABLE IF NOT EXISTS pricetrace_product_cache (" +
                "catalog_product_id TEXT PRIMARY KEY, " +
                "standard_product_id TEXT, " +
                "product_name TEXT NOT NULL, " +
                "brand_name TEXT, " +
                "manufacturer_name TEXT, " +
                "sub_brand_name TEXT, " +
                "seller_name TEXT, " +
                "latest_price_krw INTEGER, " +
                "price_observed_at TEXT, " +
                "content_amount REAL, " +
                "content_unit TEXT, " +
                "package_count INTEGER, " +
                "catalog_product_revision TEXT, " +
                "contract_version TEXT NOT NULL, " +
                "fetched_at TEXT NOT NULL, " +
                "CHECK ((latest_price_krw IS NULL AND price_observed_at IS NULL) " +
                "OR (latest_price_krw IS NOT NULL AND price_observed_at IS NOT NULL)))");
    }

    /**
     * 선수용 일일 영양 루프의 로컬 데이터.
     *
     * <p>목표값은 사용자가 코치·영양사와 정한 값을 저장할 뿐 자동 처방하지 않는다. 체크인은
     * 식사 스냅샷과 분리해 수분·수면·주관적 컨디션을 날짜별로 한 번만 기록한다.</p>
     */
    private void createAthleteNutritionTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS nutrition_goals (" +
                "user_id TEXT PRIMARY KEY, " +
                "phase TEXT NOT NULL, " +
                "calories_kcal REAL NOT NULL, " +
                "protein_grams REAL NOT NULL, " +
                "carbs_grams REAL NOT NULL, " +
                "fat_grams REAL NOT NULL, " +
                "fiber_grams REAL NOT NULL, " +
                "sodium_mg REAL NOT NULL, " +
                "water_ml INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS nutrition_daily_checkins (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "water_ml INTEGER NOT NULL DEFAULT 0, " +
                "sleep_hours REAL, " +
                "energy_score INTEGER, " +
                "hunger_score INTEGER, " +
                "digestion_score INTEGER, " +
                "training_readiness_score INTEGER, " +
                "note TEXT, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "UNIQUE(user_id, date))");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_daily_checkins_user_date_idx " +
                "ON nutrition_daily_checkins(user_id, date DESC)");
    }

    private void createDevelopmentTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS body_profiles (" +
                "user_id TEXT PRIMARY KEY, " +
                "height_cm INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "CHECK (height_cm BETWEEN 50 AND 300))");
        db.execSQL("CREATE TABLE IF NOT EXISTS development_goals (" +
                "user_id TEXT PRIMARY KEY, " +
                "objective TEXT NOT NULL, " +
                "weekly_sessions_target INTEGER NOT NULL, " +
                "focus_body_part TEXT NOT NULL, " +
                "effective_from TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "CHECK (objective IN ('muscle_gain','strength','fat_loss','endurance','maintenance')), " +
                "CHECK (weekly_sessions_target BETWEEN 1 AND 7), " +
                "CHECK (focus_body_part IN ('chest','back','legs','shoulders','arms','abs')))");
    }

    /** User-defined supplement plans and immutable per-dose intake snapshots. */
    private void createSupplementTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS supplement_items (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, " +
                "supplement_type_code TEXT NOT NULL, supplement_type_name TEXT NOT NULL, " +
                "brand_name TEXT NOT NULL DEFAULT '', product_form TEXT NOT NULL DEFAULT '', " +
                "purpose_code TEXT NOT NULL DEFAULT 'general_health', " +
                "is_active INTEGER NOT NULL DEFAULT 1, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, " +
                "device_id TEXT NOT NULL, CHECK (is_active IN (0, 1)))");
        db.execSQL("CREATE TABLE IF NOT EXISTS supplement_schedules (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, supplement_item_id TEXT NOT NULL, " +
                "dose_amount REAL NOT NULL, dose_unit TEXT NOT NULL, " +
                "serving_amount REAL, serving_unit TEXT, " +
                "active_ingredient_amount REAL, active_ingredient_unit TEXT, " +
                "ingredient_details TEXT NOT NULL DEFAULT '', " +
                "times_per_day INTEGER NOT NULL, timing_label TEXT NOT NULL, " +
                "effective_from TEXT NOT NULL DEFAULT '1970-01-01', effective_to TEXT, " +
                "type_code_snapshot TEXT NOT NULL DEFAULT '', " +
                "type_name_snapshot TEXT NOT NULL DEFAULT '', " +
                "brand_name_snapshot TEXT NOT NULL DEFAULT '', " +
                "product_form_snapshot TEXT NOT NULL DEFAULT '', " +
                "purpose_code_snapshot TEXT NOT NULL DEFAULT 'general_health', " +
                "revision INTEGER NOT NULL DEFAULT 1, " +
                "instructions TEXT NOT NULL DEFAULT '', is_active INTEGER NOT NULL DEFAULT 1, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, " +
                "device_id TEXT NOT NULL, CHECK (dose_amount > 0), " +
                "CHECK (times_per_day BETWEEN 1 AND 6), CHECK (is_active IN (0, 1)))");
        db.execSQL("CREATE TABLE IF NOT EXISTS supplement_schedule_slots (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, schedule_id TEXT NOT NULL, " +
                "slot_index INTEGER NOT NULL, timing_label TEXT NOT NULL, scheduled_time TEXT, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, " +
                "device_id TEXT NOT NULL, UNIQUE(user_id, schedule_id, slot_index), " +
                "CHECK (slot_index BETWEEN 1 AND 6))");
        db.execSQL("CREATE TABLE IF NOT EXISTS supplement_intake_records (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, schedule_id TEXT NOT NULL, " +
                "supplement_item_id TEXT NOT NULL, schedule_slot_id TEXT, " +
                "date TEXT NOT NULL, dose_index INTEGER NOT NULL, " +
                "status TEXT NOT NULL, taken_at TEXT, type_code_snapshot TEXT NOT NULL, " +
                "type_name_snapshot TEXT NOT NULL, brand_name_snapshot TEXT NOT NULL, " +
                "dose_amount_snapshot REAL NOT NULL, dose_unit_snapshot TEXT NOT NULL, " +
                "serving_amount_snapshot REAL, serving_unit_snapshot TEXT, " +
                "active_ingredient_amount_snapshot REAL, active_ingredient_unit_snapshot TEXT, " +
                "ingredient_details_snapshot TEXT NOT NULL DEFAULT '', " +
                "timing_label_snapshot TEXT NOT NULL, instructions_snapshot TEXT NOT NULL DEFAULT '', " +
                "record_source TEXT NOT NULL DEFAULT 'on_date', " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, " +
                "device_id TEXT NOT NULL, UNIQUE(user_id, schedule_id, date, dose_index), " +
                "CHECK (dose_index BETWEEN 1 AND 6), " +
                "CHECK (status IN ('taken', 'skipped')))");
        db.execSQL("CREATE TABLE IF NOT EXISTS supplement_effect_checkins (" +
                "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, supplement_item_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, effect_score INTEGER NOT NULL, " +
                "adverse_effects TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT, " +
                "device_id TEXT NOT NULL, UNIQUE(user_id, supplement_item_id, date), " +
                "CHECK (effect_score BETWEEN 1 AND 5))");
        db.execSQL("CREATE INDEX IF NOT EXISTS supplement_items_user_active_idx " +
                "ON supplement_items(user_id, is_active, created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS supplement_schedules_user_item_active_idx " +
                "ON supplement_schedules(user_id, supplement_item_id, is_active)");
        db.execSQL("CREATE INDEX IF NOT EXISTS supplement_schedules_user_effective_idx " +
                "ON supplement_schedules(user_id, effective_from, effective_to)");
        db.execSQL("CREATE INDEX IF NOT EXISTS supplement_slots_user_schedule_idx " +
                "ON supplement_schedule_slots(user_id, schedule_id, slot_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS supplement_intakes_user_date_idx " +
                "ON supplement_intake_records(user_id, date DESC, created_at DESC)");
        db.execSQL("CREATE INDEX IF NOT EXISTS supplement_effect_user_item_date_idx " +
                "ON supplement_effect_checkins(user_id, supplement_item_id, date DESC)");
    }

    /**
     * v8 카탈로그를 확장 영양소 스키마로 올린다.
     *
     * <p>새 권고 영양소 컬럼은 기본값 없이 추가해 기존 행이 NULL(모름)로 남게 한다.
     * 0으로 채우면 "나트륨 0mg인 음식"과 "나트륨을 모르는 음식"을 영영 구분할 수 없다.</p>
     */
    private void upgradeNutritionTablesToExtendedNutrients(SQLiteDatabase db) {
        createNutritionTables(db);

        addColumnIfMissing(db, "nutrition_foods", "prep_state", "TEXT NOT NULL DEFAULT 'unspecified'");
        addColumnIfMissing(db, "nutrition_foods", "sodium_mg", "REAL");
        addColumnIfMissing(db, "nutrition_foods", "saturated_fat_grams", "REAL");
        addColumnIfMissing(db, "nutrition_foods", "sugars_grams", "REAL");
        addColumnIfMissing(db, "nutrition_foods", "fiber_grams", "REAL");
        addColumnIfMissing(db, "nutrition_foods", "added_sugars_grams", "REAL");
        addColumnIfMissing(db, "nutrition_foods", "trans_fat_grams", "REAL");
        addColumnIfMissing(db, "nutrition_foods", "cholesterol_mg", "REAL");
        addColumnIfMissing(db, "nutrition_foods", "source_version", "TEXT");
        addColumnIfMissing(db, "nutrition_foods", "data_version", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing(db, "nutrition_foods", "revision", "INTEGER NOT NULL DEFAULT 1");

        addColumnIfMissing(db, "meal_record_items", "food_kind_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "basis_amount_snapshot", "REAL");
        addColumnIfMissing(db, "meal_record_items", "basis_unit_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "prep_state_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "sodium_mg", "REAL");
        addColumnIfMissing(db, "meal_record_items", "saturated_fat_grams", "REAL");
        addColumnIfMissing(db, "meal_record_items", "sugars_grams", "REAL");
        addColumnIfMissing(db, "meal_record_items", "fiber_grams", "REAL");
        addColumnIfMissing(db, "meal_record_items", "added_sugars_grams", "REAL");
        addColumnIfMissing(db, "meal_record_items", "trans_fat_grams", "REAL");
        addColumnIfMissing(db, "meal_record_items", "cholesterol_mg", "REAL");
        addColumnIfMissing(db, "meal_record_items", "source_type_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "source_reference_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "source_version_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "food_data_version_snapshot", "INTEGER");

        // 이미 기록된 항목은 당시 음식의 기준량 정보를 잃지 않도록 카탈로그에서 한 번 채워 준다.
        db.execSQL("UPDATE meal_record_items SET " +
                "basis_amount_snapshot = (SELECT f.basis_amount FROM nutrition_foods f " +
                "WHERE f.id = meal_record_items.food_id), " +
                "basis_unit_snapshot = COALESCE((SELECT f.basis_unit FROM nutrition_foods f " +
                "WHERE f.id = meal_record_items.food_id), unit), " +
                "food_kind_snapshot = (SELECT f.kind FROM nutrition_foods f " +
                "WHERE f.id = meal_record_items.food_id), " +
                "food_data_version_snapshot = 1 " +
                "WHERE basis_unit_snapshot IS NULL");
    }

    /**
     * Existing meal items already represent top-level consumed entries. Version 15 formalizes
     * them as menus and adds immutable ingredient snapshots for newly recorded composed menus.
     */
    private void upgradeMealMenuHierarchy(SQLiteDatabase db) {
        addColumnIfMissing(db, "meal_record_items", "brand_snapshot", "TEXT");
        createNutritionTables(db);
        db.execSQL("UPDATE meal_record_items SET brand_snapshot = (" +
                "SELECT f.brand FROM nutrition_foods f " +
                "WHERE f.id = meal_record_items.food_id) " +
                "WHERE brand_snapshot IS NULL AND food_id IS NOT NULL");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createRoutineTables(db);
        }
        if (oldVersion < 3) {
            renameLegacyFitnessTables(db);
            createSharedRecordTables(db);
            migrateLegacyFitnessTables(db);
        }
        if (oldVersion < 4) {
            addColumnIfMissing(db, "workout_records", "total_volume_kg", "REAL NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "workout_sets", "volume_kg", "REAL");
        }
        if (oldVersion < 5) {
            addColumnIfMissing(db, "workout_records", "contract_version", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(db, "meal_records", "contract_version", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(db, "weight_records", "contract_version", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(db, "workout_exercises", "contract_version", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(db, "workout_sets", "contract_version", "INTEGER NOT NULL DEFAULT 1");
            db.execSQL("UPDATE workout_exercises SET record_type = 'weight_reps' " +
                    "WHERE record_type = 'sets_reps_weight'");
        }
        if (oldVersion < 6) {
            createCardioTables(db);
        }
        if (oldVersion < 7) {
            createMealMenuPresetTable(db);
        }
        if (oldVersion < 8) {
            createNutritionTables(db);
        }
        if (oldVersion < 9) {
            upgradeNutritionTablesToExtendedNutrients(db);
        }
        if (oldVersion < 10) {
            addColumnIfMissing(db, "nutrition_foods", "revision", "INTEGER NOT NULL DEFAULT 1");
            createProductNutritionLinkTables(db);
        }
        if (oldVersion < 11) {
            addColumnIfMissing(db, "nutrition_foods", "brand", "TEXT");
            addColumnIfMissing(db, "product_nutrition_links", "standard_product_id", "TEXT");
            addColumnIfMissing(db, "pricetrace_product_cache", "brand_name", "TEXT");
            addColumnIfMissing(db, "pricetrace_product_cache", "manufacturer_name", "TEXT");
            addColumnIfMissing(db, "pricetrace_product_cache", "sub_brand_name", "TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_brand_name_idx " +
                    "ON nutrition_foods(owner_id, brand COLLATE NOCASE, name COLLATE NOCASE)");
        }
        if (oldVersion < 12) {
            addColumnIfMissing(db, "nutrition_foods", "category", "TEXT NOT NULL DEFAULT 'other'");
            addColumnIfMissing(
                    db,
                    "nutrition_foods",
                    "cooking_method",
                    "TEXT NOT NULL DEFAULT 'unspecified'"
            );
            db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_category_idx " +
                    "ON nutrition_foods(owner_id, category, cooking_method, name COLLATE NOCASE)");
        }
        if (oldVersion < 13) {
            createAthleteNutritionTables(db);
        }
        if (oldVersion < 14) {
            upgradeLocalAccountIsolation(db);
        }
        if (oldVersion < 15) {
            upgradeMealMenuHierarchy(db);
        }
        // Idempotently enforce the feature schema for every historical upgrade path. This also
        // repairs development preview databases that reached v16-v18 without these tables.
        createDevelopmentTables(db);
        if (oldVersion < 45) {
            upgradePackagedFoodHierarchySchema(db);
        }
        createNutritionIndexes(db);
        if (oldVersion < 19) {
            reconcileVerifiedFoodCatalog(db);
        }
        if (oldVersion < 20) {
            upgradeDiningOutMealRecords(db);
        }
        if (oldVersion < 21) {
            upgradeProductNutritionLinkPriceTraceMetadata(db);
        }
        if (oldVersion < 22) {
            createVerifiedReceiptImportTable(db);
        }
        if (oldVersion < 24) {
            reconcileVerifiedFoodCatalog(db);
        }
        if (oldVersion < 25) {
            upgradeDiningOutIdentity(db);
        }
        if (oldVersion < 26) {
            reconcileVerifiedFoodCatalog(db);
        }
        if (oldVersion < 27) {
            reconcileVerifiedFoodCatalog(db);
        }
        if (oldVersion < 29) {
            upgradeKnownDiningOutBranchDefaults(db);
        }
        if (oldVersion < 30) {
            createSupplementTables(db);
        }
        if (oldVersion < 31) {
            upgradeSupplementTablesToVersion31(db);
        }
        if (oldVersion < 32) {
            upgradeCompositionSchema(db);
        }
        if (oldVersion < 33) {
            createSyncStateTables(db);
        }
        if (oldVersion < 34) {
            upgradeDiningOutConsumptionSchema(db);
        }
        if (oldVersion < 35) {
            addColumnIfMissing(db, "workout_sets", "rir", "INTEGER");
        }
        if (oldVersion < 36) {
            addColumnIfMissing(db, "meal_record_item_components", "consumed_fraction", "REAL");
        }
        if (oldVersion < 37) {
            upgradeCompositionGroupTypeSchema(db);
        }
        if (oldVersion < 38) {
            createDiningOutAddOnLinkTable(db);
        }
        if (oldVersion < 39) {
            upgradeMealComponentNutritionNullability(db);
        }
        if (oldVersion < 40) {
            upgradeNutritionFoodRequiredNutritionNullability(db);
        }
        if (oldVersion < 41) {
            // v39 already made existing component snapshots nullable. This revision keeps
            // fresh installs on the same schema and is intentionally a no-op for data.
            upgradeMealComponentNutritionNullability(db);
        }
        if (oldVersion < 42) {
            upgradeDiningOutComponentSchema(db);
        }
        if (oldVersion < 43) {
            upgradeExerciseFamilyIdentitySchema(db);
        }
        if (oldVersion < 44) {
            createExercisePickerPreferenceTable(db);
        }
        if (oldVersion < 46) {
            upgradePackagedFoodSnapshotSchema(db);
        }
        if (oldVersion < 47) {
            addColumnIfMissing(db, "meal_records", "fulfillment_mode", "TEXT");
        }
    }

    /** Adds explicit packaged-food hierarchy without guessing legacy brand-only mappings. */
    private void upgradePackagedFoodHierarchySchema(SQLiteDatabase db) {
        addColumnIfMissing(db, "nutrition_foods", "manufacturer_name", "TEXT");
        addColumnIfMissing(db, "nutrition_foods", "brand_name", "TEXT");
        addColumnIfMissing(db, "nutrition_foods", "sub_brand_name", "TEXT");
        addColumnIfMissing(db, "nutrition_foods", "product_name", "TEXT");
        addColumnIfMissing(db, "nutrition_foods", "package_amount", "REAL");
        addColumnIfMissing(db, "nutrition_foods", "package_unit", "TEXT");
        addColumnIfMissing(db, "nutrition_foods", "package_count", "INTEGER");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_product_hierarchy_idx " +
                "ON nutrition_foods(owner_id, manufacturer_name COLLATE NOCASE, " +
                "brand_name COLLATE NOCASE, sub_brand_name COLLATE NOCASE, " +
                "product_name COLLATE NOCASE)");
    }

    /** Adds immutable packaged-food hierarchy values to newly written meal item snapshots. */
    private void upgradePackagedFoodSnapshotSchema(SQLiteDatabase db) {
        addColumnIfMissing(db, "pricetrace_product_cache", "manufacturer_name", "TEXT");
        addColumnIfMissing(db, "pricetrace_product_cache", "sub_brand_name", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "manufacturer_name_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "brand_name_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "sub_brand_name_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "product_name_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "package_amount_snapshot", "REAL");
        addColumnIfMissing(db, "meal_record_items", "package_unit_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "package_count_snapshot", "INTEGER");
    }

    /** Adds family/preset/variant snapshots and keeps load semantics at set granularity. */
    private void upgradeExerciseFamilyIdentitySchema(SQLiteDatabase db) {
        addColumnIfMissing(db, "workout_exercises", "family_id", "TEXT");
        addColumnIfMissing(db, "workout_exercises", "preset_id", "TEXT");
        addColumnIfMissing(db, "workout_exercises", "canonical_variant_key", "TEXT");
        addColumnIfMissing(db, "workout_exercises", "visual_variant_key", "TEXT");
        addColumnIfMissing(db, "workout_sets", "load_state", "TEXT");
        addColumnIfMissing(db, "routine_exercises", "family_id", "TEXT");
        addColumnIfMissing(db, "routine_exercises", "preset_id", "TEXT");
        addColumnIfMissing(db, "routine_exercises", "canonical_variant_key", "TEXT");
        addColumnIfMissing(db, "routine_exercises", "visual_variant_key", "TEXT");
        db.execSQL("CREATE INDEX IF NOT EXISTS workout_exercises_family_variant_idx " +
                "ON workout_exercises(user_id, family_id, canonical_variant_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS routine_exercises_family_variant_idx " +
                "ON routine_exercises(user_id, family_id, canonical_variant_key)");
        db.execSQL("CREATE INDEX IF NOT EXISTS workout_sets_load_state_idx " +
                "ON workout_sets(user_id, load_state)");
    }

    /** Generalizes the v38 add-on relationship and adds the actual-meal provision snapshot. */
    private void upgradeDiningOutComponentSchema(SQLiteDatabase db) {
        addColumnIfMissing(
                db,
                "meal_record_item_components",
                "provision_type_snapshot",
                "TEXT"
        );
        createDiningOutComponentLinkTable(db);
        if (!tableExists(db, "dining_out_menu_add_on_links")) {
            return;
        }
        db.execSQL("INSERT OR IGNORE INTO dining_out_menu_component_links (" +
                "id, user_id, menu_food_id, component_food_id, group_type, " +
                "created_at, updated_at, deleted_at, device_id) " +
                "SELECT id, user_id, menu_food_id, add_on_food_id, 'add_on', " +
                "created_at, updated_at, deleted_at, device_id " +
                "FROM dining_out_menu_add_on_links");
    }

    /** Allows reusable options without nutrition to retain NULL rather than a fake zero. */
    private void upgradeNutritionFoodRequiredNutritionNullability(SQLiteDatabase db) {
        if (!tableExists(db, "nutrition_foods")) {
            return;
        }
        db.execSQL("DROP TABLE IF EXISTS nutrition_foods_v40");
        db.execSQL("CREATE TABLE nutrition_foods_v40 (" +
                "id TEXT PRIMARY KEY, owner_id TEXT, name TEXT NOT NULL, brand TEXT, " +
                "kind TEXT NOT NULL, category TEXT NOT NULL DEFAULT 'other', " +
                "basis_amount REAL NOT NULL, basis_unit TEXT NOT NULL, " +
                "prep_state TEXT NOT NULL DEFAULT 'unspecified', " +
                "cooking_method TEXT NOT NULL DEFAULT 'unspecified', " +
                "calories_kcal REAL, protein_grams REAL, carbs_grams REAL, fat_grams REAL, " +
                "sodium_mg REAL, saturated_fat_grams REAL, sugars_grams REAL, " +
                "fiber_grams REAL, added_sugars_grams REAL, trans_fat_grams REAL, " +
                "cholesterol_mg REAL, source_type TEXT NOT NULL, source_reference TEXT, " +
                "source_version TEXT, data_version INTEGER NOT NULL DEFAULT 1, " +
                "revision INTEGER NOT NULL DEFAULT 1, visibility TEXT NOT NULL DEFAULT 'private', " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, deleted_at TEXT)");
        db.execSQL("INSERT INTO nutrition_foods_v40 (" +
                "id, owner_id, name, brand, kind, category, basis_amount, basis_unit, " +
                "prep_state, cooking_method, calories_kcal, protein_grams, carbs_grams, " +
                "fat_grams, sodium_mg, saturated_fat_grams, sugars_grams, fiber_grams, " +
                "added_sugars_grams, trans_fat_grams, cholesterol_mg, source_type, " +
                "source_reference, source_version, data_version, revision, visibility, " +
                "created_at, updated_at, deleted_at) " +
                "SELECT id, owner_id, name, brand, kind, category, basis_amount, basis_unit, " +
                "prep_state, cooking_method, calories_kcal, protein_grams, carbs_grams, " +
                "fat_grams, sodium_mg, saturated_fat_grams, sugars_grams, fiber_grams, " +
                "added_sugars_grams, trans_fat_grams, cholesterol_mg, source_type, " +
                "source_reference, source_version, data_version, revision, visibility, " +
                "created_at, updated_at, deleted_at FROM nutrition_foods");
        db.execSQL("DROP TABLE nutrition_foods");
        db.execSQL("ALTER TABLE nutrition_foods_v40 RENAME TO nutrition_foods");
        createNutritionIndexes(db);
    }

    /** Allows an option snapshot to retain unknown nutrition as NULL instead of inventing zero. */
    private void upgradeMealComponentNutritionNullability(SQLiteDatabase db) {
        if (!tableExists(db, "meal_record_item_components")) {
            return;
        }
        db.execSQL("DROP TABLE IF EXISTS meal_record_item_components_v39");
        db.execSQL("CREATE TABLE meal_record_item_components_v39 (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "meal_record_id TEXT NOT NULL, " +
                "meal_record_item_id TEXT NOT NULL, " +
                "composition_group_key_snapshot TEXT, " +
                "composition_group_type_snapshot TEXT, " +
                "composition_role_snapshot TEXT, " +
                "composition_member_id_snapshot TEXT, " +
                "food_id TEXT, " +
                "food_name_snapshot TEXT NOT NULL, " +
                "brand_snapshot TEXT, " +
                "food_kind_snapshot TEXT, " +
                "quantity REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "basis_amount_snapshot REAL, " +
                "basis_unit_snapshot TEXT, " +
                "prep_state_snapshot TEXT, " +
                "consumed_fraction REAL, " +
                "calories REAL, " +
                "protein_grams REAL, " +
                "carbs_grams REAL, " +
                "fat_grams REAL, " +
                "sodium_mg REAL, " +
                "saturated_fat_grams REAL, " +
                "sugars_grams REAL, " +
                "fiber_grams REAL, " +
                "added_sugars_grams REAL, " +
                "trans_fat_grams REAL, " +
                "cholesterol_mg REAL, " +
                "source_type_snapshot TEXT, " +
                "source_reference_snapshot TEXT, " +
                "source_version_snapshot TEXT, " +
                "food_data_version_snapshot INTEGER, " +
                "order_index INTEGER NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT, " +
                "device_id TEXT NOT NULL)");
        db.execSQL("INSERT INTO meal_record_item_components_v39 (" +
                "id, user_id, meal_record_id, meal_record_item_id, " +
                "composition_group_key_snapshot, composition_group_type_snapshot, " +
                "composition_role_snapshot, composition_member_id_snapshot, food_id, " +
                "food_name_snapshot, brand_snapshot, food_kind_snapshot, quantity, unit, " +
                "basis_amount_snapshot, basis_unit_snapshot, prep_state_snapshot, " +
                "consumed_fraction, calories, protein_grams, carbs_grams, fat_grams, " +
                "sodium_mg, saturated_fat_grams, sugars_grams, fiber_grams, " +
                "added_sugars_grams, trans_fat_grams, cholesterol_mg, source_type_snapshot, " +
                "source_reference_snapshot, source_version_snapshot, food_data_version_snapshot, " +
                "order_index, created_at, updated_at, deleted_at, device_id) " +
                "SELECT id, user_id, meal_record_id, meal_record_item_id, " +
                "composition_group_key_snapshot, composition_group_type_snapshot, " +
                "composition_role_snapshot, composition_member_id_snapshot, food_id, " +
                "food_name_snapshot, brand_snapshot, food_kind_snapshot, quantity, unit, " +
                "basis_amount_snapshot, basis_unit_snapshot, prep_state_snapshot, " +
                "consumed_fraction, calories, protein_grams, carbs_grams, fat_grams, " +
                "sodium_mg, saturated_fat_grams, sugars_grams, fiber_grams, " +
                "added_sugars_grams, trans_fat_grams, cholesterol_mg, source_type_snapshot, " +
                "source_reference_snapshot, source_version_snapshot, food_data_version_snapshot, " +
                "order_index, created_at, updated_at, deleted_at, device_id " +
                "FROM meal_record_item_components");
        db.execSQL("DROP TABLE meal_record_item_components");
        db.execSQL("ALTER TABLE meal_record_item_components_v39 " +
                "RENAME TO meal_record_item_components");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_components_parent_order_idx " +
                "ON meal_record_item_components(meal_record_item_id, order_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_components_meal_idx " +
                "ON meal_record_item_components(meal_record_id, user_id, deleted_at)");
    }

    private void upgradeCompositionGroupTypeSchema(SQLiteDatabase db) {
        addColumnIfMissing(db, "composition_groups", "group_type", "TEXT NOT NULL DEFAULT 'other'");
        addColumnIfMissing(
                db,
                "meal_record_item_components",
                "composition_group_type_snapshot",
                "TEXT"
        );

        // Existing rows came from free-form labels or the legacy catch-all group. Normalize
        // known labels to the fixed domain and keep unknown historical labels as "other".
        if (tableExists(db, "composition_groups")) {
            try (Cursor cursor = db.query(
                    "composition_groups",
                    new String[]{"id", "group_type", "label"},
                    null,
                    null,
                    null,
                    null,
                    null
            )) {
                while (cursor.moveToNext()) {
                    String storedType = cursor.isNull(1) ? null : cursor.getString(1);
                    String label = cursor.isNull(2) ? null : cursor.getString(2);
                    String normalizedType = CompositionGroupType.normalize(storedType);
                    if (CompositionGroupType.OTHER.value().equals(normalizedType)) {
                        normalizedType = CompositionGroupType.normalize(label);
                    }
                    ContentValues values = new ContentValues();
                    values.put("group_type", normalizedType);
                    db.update(
                            "composition_groups",
                            values,
                            "id = ?",
                            new String[]{cursor.getString(0)}
                    );
                }
            }
        }
        if (tableExists(db, "meal_record_item_components")) {
            try (Cursor cursor = db.query(
                    "meal_record_item_components",
                    new String[]{"id", "composition_group_type_snapshot"},
                    "composition_group_key_snapshot IS NOT NULL",
                    null,
                    null,
                    null,
                    null
            )) {
                while (cursor.moveToNext()) {
                    String storedType = cursor.isNull(1) ? null : cursor.getString(1);
                    ContentValues values = new ContentValues();
                    values.put(
                            "composition_group_type_snapshot",
                            CompositionGroupType.normalize(storedType)
                    );
                    db.update(
                            "meal_record_item_components",
                            values,
                            "id = ?",
                            new String[]{cursor.getString(0)}
                    );
                }
            }
        }
    }

    private void upgradeDiningOutConsumptionSchema(SQLiteDatabase db) {
        addColumnIfMissing(db, "meal_records", "nutrition_calculation_contract", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "portion_basis_snapshot", "TEXT");
        addColumnIfMissing(db, "meal_record_items", "nominal_servings_snapshot", "REAL");
        createDiningOutConsumptionTables(db);
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_consumptions_item_idx " +
                "ON meal_record_item_consumptions(meal_record_item_id, user_id, deleted_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_record_item_consumptions_record_idx " +
                "ON meal_record_item_consumptions(meal_record_id, user_id, deleted_at)");
    }
    private void upgradeCompositionSchema(SQLiteDatabase db) {
        createCompositionTables(db);

        addColumnIfMissing(db, "meal_records", "composition_template_id", "TEXT");
        addColumnIfMissing(
                db,
                "meal_records",
                "composition_template_revision",
                "INTEGER"
        );
        addColumnIfMissing(db, "meal_record_items", "composition_template_id", "TEXT");
        addColumnIfMissing(
                db,
                "meal_record_items",
                "composition_template_revision_snapshot",
                "INTEGER"
        );
        addColumnIfMissing(
                db,
                "meal_record_item_components",
                "composition_group_key_snapshot",
                "TEXT"
        );
        addColumnIfMissing(
                db,
                "meal_record_item_components",
                "composition_role_snapshot",
                "TEXT"
        );
        addColumnIfMissing(
                db,
                "meal_record_item_components",
                "composition_member_id_snapshot",
                "TEXT"
        );

        // Existing dining-out options were already immutable component snapshots. We only add
        // their generic role; names, IDs, and nutrient values are deliberately untouched.
        db.execSQL("UPDATE meal_record_item_components SET " +
                "composition_group_key_snapshot = 'legacy_options', " +
                "composition_role_snapshot = 'optional' " +
                "WHERE (composition_group_key_snapshot IS NULL " +
                "OR trim(composition_group_key_snapshot) = '') " +
                "AND meal_record_id IN (SELECT id FROM meal_records " +
                "WHERE meal_kind = 'dining_out')");

        migrateLegacyDiningOutOptionTemplates(db);
        upgradeDiningOutCompositionMetadata(db);
    }

    /** Converts catalog-backed manual dining-out options into generic template members. */
    private void migrateLegacyDiningOutOptionTemplates(SQLiteDatabase db) {
        if (!tableExists(db, "nutrition_foods")) {
            return;
        }
        Map<String, String> templateIds = new HashMap<>();
        Map<String, String> groupIds = new HashMap<>();
        try (Cursor cursor = db.query(
                "nutrition_foods",
                new String[]{
                        "id", "owner_id", "name", "brand", "basis_unit", "source_reference",
                        "created_at", "updated_at"
                },
                "source_type = ? AND deleted_at IS NULL",
                new String[]{"manual_option"},
                null,
                null,
                "updated_at ASC, id ASC"
        )) {
            while (cursor.moveToNext()) {
                String foodId = cursor.getString(0);
                String userId = valueOrDefault(cursor.getString(1), "local-user");
                String name = valueOrDefault(cursor.getString(2), "외식 옵션");
                String brand = blankToNull(cursor.getString(3));
                String unit = valueOrDefault(cursor.getString(4), "serving");
                String sourceReference = blankToNull(cursor.getString(5));
                JSONObject source = parseJson(sourceReference);

                String identityKey = userId + "|"
                        + jsonText(source, "restaurant_id") + "|"
                        + jsonText(source, "restaurant_location_id") + "|"
                        + jsonText(source, "restaurant_menu_id") + "|"
                        + jsonText(source, "source_namespace") + "|"
                        + jsonText(source, "source_location_code") + "|"
                        + jsonText(source, "restaurant_name") + "|"
                        + jsonText(source, "menu_name");
                String templateId = templateIds.get(identityKey);
                if (templateId == null) {
                    templateId = deterministicId("legacy-dining-out-template:" + identityKey);
                    templateIds.put(identityKey, templateId);
                    String groupId = deterministicId(templateId + ":legacy_options");
                    groupIds.put(identityKey, groupId);

                    ContentValues template = new ContentValues();
                    template.put("id", templateId);
                    template.put("user_id", userId);
                    template.put("name", diningOutTemplateName(source));
                    template.put("template_kind", CompositionTemplate.KIND_DINING_OUT);
                    template.put("source_reference", compositionSourceReference(source));
                    template.put("revision", 1);
                    template.put("created_at", valueOrDefault(cursor.getString(6), OffsetDateTime.now().toString()));
                    template.put("updated_at", valueOrDefault(cursor.getString(7), OffsetDateTime.now().toString()));
                    template.put("device_id", "fitness-migration-v32");
                    db.insertWithOnConflict(
                            "composition_templates",
                            null,
                            template,
                            SQLiteDatabase.CONFLICT_IGNORE
                    );

                    ContentValues group = new ContentValues();
                    group.put("id", groupId);
                    group.put("user_id", userId);
                    group.put("template_id", templateId);
                    group.put("group_key", "legacy_options");
                    group.put("label", "기존 외식 옵션");
                    group.put("selection_mode", CompositionGroup.MODE_OPTIONAL_MANY);
                    group.put("min_selected", 0);
                    group.put("max_selected", 99);
                    group.put("order_index", 0);
                    group.put("created_at", template.getAsString("created_at"));
                    group.put("updated_at", template.getAsString("updated_at"));
                    group.put("device_id", "fitness-migration-v32");
                    db.insertWithOnConflict(
                            "composition_groups",
                            null,
                            group,
                            SQLiteDatabase.CONFLICT_IGNORE
                    );
                }

                String groupId = groupIds.get(identityKey);
                if (groupId == null) {
                    groupId = deterministicId(templateId + ":legacy_options");
                    groupIds.put(identityKey, groupId);
                }
                ContentValues member = new ContentValues();
                member.put("id", deterministicId(templateId + ":member:" + foodId));
                member.put("user_id", userId);
                member.put("template_id", templateId);
                member.put("group_id", groupId);
                member.put("nutrition_food_id", foodId);
                member.put("name_snapshot", name);
                if (brand == null) {
                    member.putNull("brand_snapshot");
                } else {
                    member.put("brand_snapshot", brand);
                }
                member.put("quantity", 1);
                member.put("unit", unit);
                member.put("default_selected", 0);
                member.put("order_index", 0);
                if (sourceReference == null) {
                    member.putNull("source_reference_snapshot");
                } else {
                    member.put("source_reference_snapshot", sourceReference);
                }
                member.put("created_at", valueOrDefault(cursor.getString(6), OffsetDateTime.now().toString()));
                member.put("updated_at", valueOrDefault(cursor.getString(7), OffsetDateTime.now().toString()));
                member.put("device_id", "fitness-migration-v32");
                db.insertWithOnConflict(
                        "composition_members",
                        null,
                        member,
                        SQLiteDatabase.CONFLICT_IGNORE
                );
            }
        }
    }

    private void upgradeDiningOutCompositionMetadata(SQLiteDatabase db) {
        if (!tableExists(db, "meal_records") || !hasColumn(db, "meal_records", "metadata")) {
            return;
        }
        try (Cursor cursor = db.query(
                "meal_records",
                new String[]{"id", "metadata"},
                "meal_kind = ?",
                new String[]{"dining_out"},
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                JSONObject metadata = parseJson(cursor.getString(1));
                if (metadata == null) {
                    continue;
                }
                try {
                    if (metadata.optInt("composition_version", 0) < 3) {
                        metadata.put("composition_version", 3);
                    }
                    metadata.put("composition_contract", CompositionTemplate.CONTRACT_VERSION);
                    if (metadata.optInt("option_count", 0) > 0) {
                        metadata.put("composition_kind", "ad_hoc_selection");
                    } else if (!metadata.has("composition_kind")) {
                        metadata.put("composition_kind", "standalone");
                    }
                } catch (Exception ignored) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("metadata", metadata.toString());
                db.update("meal_records", values, "id = ?", new String[]{cursor.getString(0)});
            }
        }
    }

    private String compositionSourceReference(JSONObject source) {
        try {
            JSONObject result = source == null ? new JSONObject() : new JSONObject(source.toString());
            result.put("schema_version", CompositionTemplate.CONTRACT_VERSION);
            result.put("migration_source", "dining-out-option.v1");
            return result.toString();
        } catch (Exception ignored) {
            return "{\"schema_version\":\"composition-template.v1\",\"migration_source\":\"dining-out-option.v1\"}";
        }
    }

    private String diningOutTemplateName(JSONObject source) {
        String restaurant = jsonText(source, "restaurant_name");
        String menu = jsonText(source, "menu_name");
        if (restaurant.isEmpty() && menu.isEmpty()) {
            return "기존 외식 메뉴";
        }
        if (restaurant.isEmpty()) {
            return menu;
        }
        if (menu.isEmpty()) {
            return restaurant;
        }
        return restaurant + " · " + menu;
    }

    private JSONObject parseJson(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(value);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private String jsonText(JSONObject object, String key) {
        if (object == null) {
            return "";
        }
        String value = object.optString(key, "");
        return value == null ? "" : value.trim();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void upgradeSupplementTablesToVersion31(SQLiteDatabase db) {
        createSupplementTables(db);
        addColumnIfMissing(db, "supplement_items", "product_form", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(db, "supplement_items", "purpose_code",
                "TEXT NOT NULL DEFAULT 'general_health'");

        addColumnIfMissing(db, "supplement_schedules", "serving_amount", "REAL");
        addColumnIfMissing(db, "supplement_schedules", "serving_unit", "TEXT");
        addColumnIfMissing(db, "supplement_schedules", "active_ingredient_amount", "REAL");
        addColumnIfMissing(db, "supplement_schedules", "active_ingredient_unit", "TEXT");
        addColumnIfMissing(db, "supplement_schedules", "ingredient_details",
                "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(db, "supplement_schedules", "effective_from",
                "TEXT NOT NULL DEFAULT '1970-01-01'");
        addColumnIfMissing(db, "supplement_schedules", "effective_to", "TEXT");
        addColumnIfMissing(db, "supplement_schedules", "type_code_snapshot",
                "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(db, "supplement_schedules", "type_name_snapshot",
                "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(db, "supplement_schedules", "brand_name_snapshot",
                "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(db, "supplement_schedules", "product_form_snapshot",
                "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(db, "supplement_schedules", "purpose_code_snapshot",
                "TEXT NOT NULL DEFAULT 'general_health'");
        addColumnIfMissing(db, "supplement_schedules", "revision", "INTEGER NOT NULL DEFAULT 1");

        db.execSQL("UPDATE supplement_schedules SET " +
                "serving_amount = COALESCE(serving_amount, dose_amount), " +
                "serving_unit = COALESCE(serving_unit, dose_unit), " +
                "effective_from = CASE WHEN effective_from = '1970-01-01' " +
                "AND length(created_at) >= 10 THEN substr(created_at, 1, 10) ELSE effective_from END, " +
                "type_code_snapshot = COALESCE(NULLIF(type_code_snapshot, ''), " +
                "(SELECT i.supplement_type_code FROM supplement_items i " +
                "WHERE i.id = supplement_schedules.supplement_item_id)), " +
                "type_name_snapshot = COALESCE(NULLIF(type_name_snapshot, ''), " +
                "(SELECT i.supplement_type_name FROM supplement_items i " +
                "WHERE i.id = supplement_schedules.supplement_item_id)), " +
                "brand_name_snapshot = COALESCE(NULLIF(brand_name_snapshot, ''), " +
                "(SELECT i.brand_name FROM supplement_items i " +
                "WHERE i.id = supplement_schedules.supplement_item_id)), " +
                "product_form_snapshot = COALESCE(NULLIF(product_form_snapshot, ''), " +
                "(SELECT i.product_form FROM supplement_items i " +
                "WHERE i.id = supplement_schedules.supplement_item_id)), " +
                "purpose_code_snapshot = COALESCE(NULLIF(purpose_code_snapshot, 'general_health'), " +
                "(SELECT i.purpose_code FROM supplement_items i " +
                "WHERE i.id = supplement_schedules.supplement_item_id), 'general_health')");

        addColumnIfMissing(db, "supplement_intake_records", "schedule_slot_id", "TEXT");
        addColumnIfMissing(db, "supplement_intake_records", "serving_amount_snapshot", "REAL");
        addColumnIfMissing(db, "supplement_intake_records", "serving_unit_snapshot", "TEXT");
        addColumnIfMissing(db, "supplement_intake_records",
                "active_ingredient_amount_snapshot", "REAL");
        addColumnIfMissing(db, "supplement_intake_records",
                "active_ingredient_unit_snapshot", "TEXT");
        addColumnIfMissing(db, "supplement_intake_records", "ingredient_details_snapshot",
                "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(db, "supplement_intake_records", "record_source",
                "TEXT NOT NULL DEFAULT 'on_date'");
        db.execSQL("UPDATE supplement_intake_records SET " +
                "schedule_slot_id = COALESCE(schedule_slot_id, schedule_id || ':slot:' || dose_index), " +
                "serving_amount_snapshot = COALESCE(serving_amount_snapshot, dose_amount_snapshot), " +
                "serving_unit_snapshot = COALESCE(serving_unit_snapshot, dose_unit_snapshot), " +
                "record_source = CASE WHEN substr(created_at, 1, 10) = date " +
                "THEN 'on_date' ELSE 'backfill' END");

        for (int slot = 1; slot <= 6; slot++) {
            db.execSQL("INSERT OR IGNORE INTO supplement_schedule_slots (" +
                            "id, user_id, schedule_id, slot_index, timing_label, scheduled_time, " +
                            "created_at, updated_at, deleted_at, device_id) " +
                            "SELECT id || ':slot:' || ?, user_id, id, ?, timing_label, NULL, " +
                            "created_at, updated_at, NULL, device_id FROM supplement_schedules " +
                            "WHERE times_per_day >= ?",
                    new Object[]{slot, slot, slot});
        }
        createSupplementTables(db);
    }

    /** Adds explicit dining-out identity while keeping the shared metadata contract intact. */
    private void upgradeDiningOutMealRecords(SQLiteDatabase db) {
        if (!tableExists(db, "meal_records")) {
            createMealRecordTable(db);
        }
        addColumnIfMissing(db, "meal_records", "meal_kind", "TEXT NOT NULL DEFAULT 'food'");
        addColumnIfMissing(db, "meal_records", "store_name", "TEXT");
        addColumnIfMissing(db, "meal_records", "branch_name", "TEXT");
        addColumnIfMissing(db, "meal_records", "menu_name", "TEXT");
        createDiningOutMealIndexes(db);
    }

    private void upgradeDiningOutIdentity(SQLiteDatabase db) {
        if (!tableExists(db, "meal_records")) {
            createMealRecordTable(db);
        }
        addColumnIfMissing(db, "meal_records", "branch_name", "TEXT");
        addColumnIfMissing(db, "meal_records", "restaurant_id", "TEXT");
        addColumnIfMissing(db, "meal_records", "restaurant_location_id", "TEXT");
        addColumnIfMissing(db, "meal_records", "restaurant_menu_id", "TEXT");
        addColumnIfMissing(db, "meal_records", "catalog_product_id", "TEXT");
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_records_dining_out_identity_idx "
                + "ON meal_records(user_id, restaurant_id, restaurant_location_id, restaurant_menu_id)");
    }

    /** Repairs known local branch labels while preserving any exact external identity columns. */
    private void upgradeKnownDiningOutBranchDefaults(SQLiteDatabase db) {
        if (!tableExists(db, "meal_records")
                || !hasColumn(db, "meal_records", "store_name")
                || !hasColumn(db, "meal_records", "branch_name")
                || !hasColumn(db, "meal_records", "metadata")) {
            return;
        }
        String where = "meal_kind = ? "
                + "AND (branch_name IS NULL OR trim(branch_name) = '' "
                + "OR lower(trim(branch_name)) = 'null') "
                + "AND replace(replace(COALESCE(store_name, ''), ' ', ''), '　', '') "
                + "LIKE ?";
        String[] args = {MealRecordKind.DINING_OUT, "%고향엄마손%칼국수%"};
        try (Cursor cursor = db.query(
                "meal_records",
                new String[]{"id", "metadata"},
                where,
                args,
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                ContentValues values = new ContentValues();
                values.put("branch_name", "영등포점");
                values.put("updated_at", OffsetDateTime.now().toString());
                String metadata = cursor.getString(1);
                try {
                    JSONObject object = new JSONObject(metadata);
                    object.put("branch_name", "영등포점");
                    values.put("metadata", object.toString());
                } catch (Exception ignored) {
                    // The dedicated branch column remains repairable even for legacy metadata.
                }
                db.update("meal_records", values, "id = ?", new String[]{id});
            }
        }
    }

    private void upgradeProductNutritionLinkPriceTraceMetadata(SQLiteDatabase db) {
        addColumnIfMissing(db, "product_nutrition_links", "catalog_product_revision", "TEXT");
        addColumnIfMissing(db, "product_nutrition_links", "catalog_content_amount", "REAL");
        addColumnIfMissing(db, "product_nutrition_links", "catalog_content_unit", "TEXT");
        addColumnIfMissing(db, "product_nutrition_links", "catalog_package_count", "INTEGER");
        addColumnIfMissing(db, "pricetrace_product_cache", "catalog_product_revision", "TEXT");
    }

    private void createDiningOutMealIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS meal_records_user_kind_date_idx " +
                "ON meal_records(user_id, meal_kind, date)");
    }

    /** Purchase evidence stays separate from meal_records until the user confirms consumption. */
    private void createVerifiedReceiptImportTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS verified_receipt_items (" +
                "receipt_id TEXT NOT NULL, " +
                "receipt_item_id TEXT NOT NULL, " +
                "ledger_entry_id TEXT NOT NULL, " +
                "user_id TEXT NOT NULL, " +
                "description_snapshot TEXT NOT NULL, " +
                "purchased_quantity REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "total_price_krw INTEGER NOT NULL, " +
                "catalog_product_id TEXT, " +
                "nutrition_food_id TEXT, " +
                "status TEXT NOT NULL DEFAULT 'pending_consumption', " +
                "consumed_quantity REAL, " +
                "meal_record_id TEXT, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "PRIMARY KEY(receipt_id, receipt_item_id))");
        db.execSQL("CREATE INDEX IF NOT EXISTS verified_receipt_items_user_status_idx " +
                "ON verified_receipt_items(user_id, status, created_at)");
    }

    /** Adds explicit ownership to device-local tables and removes the global preset-name key. */
    private void upgradeLocalAccountIsolation(SQLiteDatabase db) {
        addColumnIfMissing(
                db,
                "cardio_sessions",
                "user_id",
                "TEXT NOT NULL DEFAULT 'local-user'"
        );
        db.execSQL("UPDATE cardio_sessions SET user_id = COALESCE((" +
                "SELECT wr.user_id FROM workout_records wr " +
                "WHERE wr.id = cardio_sessions.record_id LIMIT 1" +
                "), user_id)");

        addColumnIfMissing(
                db,
                "cardio_route_points",
                "user_id",
                "TEXT NOT NULL DEFAULT 'local-user'"
        );
        db.execSQL("UPDATE cardio_route_points SET user_id = COALESCE((" +
                "SELECT cs.user_id FROM cardio_sessions cs " +
                "WHERE cs.record_id = cardio_route_points.record_id LIMIT 1" +
                "), user_id)");
        db.execSQL("DROP INDEX IF EXISTS cardio_sessions_status_started_idx");
        db.execSQL("CREATE INDEX IF NOT EXISTS cardio_sessions_user_status_started_idx " +
                "ON cardio_sessions(user_id, status, started_at_epoch_ms)");

        if (tableExists(db, "meal_menu_presets")
                && !hasColumn(db, "meal_menu_presets", "user_id")) {
            db.execSQL("ALTER TABLE meal_menu_presets RENAME TO meal_menu_presets_v13");
            db.execSQL("DROP INDEX IF EXISTS meal_menu_presets_updated_idx");
            createMealMenuPresetTable(db);
            db.execSQL("INSERT INTO meal_menu_presets (" +
                    "id, user_id, name, calories, protein_grams, carbs_grams, fat_grams, " +
                    "created_at, updated_at) " +
                    "SELECT id, 'local-user', name, calories, protein_grams, carbs_grams, " +
                    "fat_grams, created_at, updated_at FROM meal_menu_presets_v13");
            db.execSQL("DROP TABLE meal_menu_presets_v13");
        }
    }

    private void renameLegacyFitnessTables(SQLiteDatabase db) {
        renameTableIfExists(db, "workout_sessions", "legacy_workout_sessions");
        if (tableExists(db, "workout_exercises") && hasColumn(db, "workout_exercises", "session_id")) {
            renameTableIfExists(db, "workout_exercises", "legacy_workout_exercises");
        }
        if (tableExists(db, "workout_sets") && hasColumn(db, "workout_sets", "exercise_id")) {
            renameTableIfExists(db, "workout_sets", "legacy_workout_sets");
        }
        renameTableIfExists(db, "body_metrics", "legacy_body_metrics");
        renameTableIfExists(db, "meals", "legacy_meals");
    }

    private void migrateLegacyFitnessTables(SQLiteDatabase db) {
        if (tableExists(db, "legacy_workout_sessions")) {
            db.execSQL("INSERT OR IGNORE INTO workout_records (" +
                    "id, user_id, date, workout_type, category, exercise_name, duration_seconds, total_volume_kg, average_heart_rate, " +
                    "created_at, is_backfilled, backfilled_at, backfill_reason, updated_at, deleted_at, device_id, " +
                    "source_app, scope, metadata) " +
                    "SELECT id, user_id, date, session_type, session_type, title, NULL, 0, NULL, " +
                    "created_at, 0, NULL, NULL, updated_at, deleted_at, device_id, " +
                    "'fitness', 'fitness', " +
                    "'{\"status\":\"' || status || '\",\"started_at\":\"' || IFNULL(started_at, '') || '\",\"ended_at\":\"' || IFNULL(ended_at, '') || '\",\"memo\":\"' || IFNULL(memo, '') || '\"}' " +
                    "FROM legacy_workout_sessions");
        }

        if (tableExists(db, "legacy_workout_exercises")) {
            db.execSQL("INSERT OR IGNORE INTO workout_exercises (" +
                    "id, user_id, record_id, order_index, exercise_id, exercise_name_snapshot, ui_part, " +
                    "primary_sub_part_snapshot, equipment_snapshot, record_type, memo, created_at, updated_at, deleted_at, device_id) " +
                    "SELECT id, user_id, session_id, order_index, 'manual', exercise_name, category, " +
                    "category, NULL, 'sets_reps_weight', memo, created_at, updated_at, deleted_at, device_id " +
                    "FROM legacy_workout_exercises");
        }

        if (tableExists(db, "legacy_workout_sets")) {
            db.execSQL("INSERT OR IGNORE INTO workout_sets (" +
                    "id, user_id, workout_exercise_id, set_index, target_reps, actual_reps, weight_kg, volume_kg, " +
                    "duration_seconds, distance_meters, rest_seconds, assisted_weight_kg, added_weight_kg, " +
                    "is_completed, rpe, memo, created_at, updated_at, deleted_at, device_id) " +
                    "SELECT id, user_id, exercise_id, set_index, target_reps, actual_reps, weight_kg, " +
                    "COALESCE(weight_kg, 0) * COALESCE(actual_reps, 0), " +
                    "duration_seconds, distance_meters, rest_seconds, NULL, NULL, is_completed, rpe, memo, " +
                    "created_at, updated_at, deleted_at, device_id FROM legacy_workout_sets");
        }

        if (tableExists(db, "legacy_body_metrics")) {
            db.execSQL("INSERT OR IGNORE INTO weight_records (" +
                    "id, user_id, date, weight_kg, created_at, is_backfilled, backfilled_at, backfill_reason, " +
                    "updated_at, deleted_at, device_id, source_app, scope, metadata) " +
                    "SELECT id, user_id, date, weight_kg, created_at, 0, NULL, NULL, updated_at, deleted_at, " +
                    "device_id, 'fitness', 'fitness', " +
                    "'{\"body_fat_percent\":\"' || IFNULL(body_fat_percent, '') || '\",\"muscle_mass_kg\":\"' || IFNULL(muscle_mass_kg, '') || '\",\"waist_cm\":\"' || IFNULL(waist_cm, '') || '\",\"memo\":\"' || IFNULL(memo, '') || '\"}' " +
                    "FROM legacy_body_metrics");
        }

        if (tableExists(db, "legacy_meals")) {
            db.execSQL("INSERT OR IGNORE INTO meal_records (" +
                    "id, user_id, date, menu, calories, protein_grams, carbs_grams, fat_grams, created_at, " +
                    "is_backfilled, backfilled_at, backfill_reason, updated_at, deleted_at, device_id, " +
                    "source_app, scope, metadata) " +
                    "SELECT id, user_id, date, menu_text, COALESCE(calories, 0), COALESCE(protein_grams, 0), " +
                    "carbs_grams, fat_grams, created_at, 0, NULL, NULL, updated_at, deleted_at, device_id, " +
                    "'fitness', 'fitness', " +
                    "'{\"meal_type\":\"' || meal_type || '\",\"estimated\":false}' FROM legacy_meals");
        }
    }

    private void renameTableIfExists(SQLiteDatabase db, String from, String to) {
        if (tableExists(db, from) && !tableExists(db, to)) {
            db.execSQL("ALTER TABLE " + from + " RENAME TO " + to);
        }
    }

    private boolean tableExists(SQLiteDatabase db, String tableName) {
        android.database.Cursor cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{tableName});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private boolean hasColumn(SQLiteDatabase db, String tableName, String columnName) {
        android.database.Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
        try {
            while (cursor.moveToNext()) {
                if (columnName.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db, String tableName, String columnName, String definition) {
        if (tableExists(db, tableName) && !hasColumn(db, tableName, columnName)) {
            db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }

    public Context applicationContext() {
        return appContext;
    }
}
