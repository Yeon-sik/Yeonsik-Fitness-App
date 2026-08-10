package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class FitnessDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "fitness_mvp.db";
    public static final int DATABASE_VERSION = 15;

    public FitnessDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createSharedRecordTables(db);
        createRoutineTables(db);
        createCardioTables(db);
        createMealMenuPresetTable(db);
        createNutritionTables(db);
        createNutritionIndexes(db);
        createProductNutritionLinkTables(db);
        createAthleteNutritionTables(db);
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
        db.execSQL("CREATE TABLE IF NOT EXISTS meal_records (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "menu TEXT NOT NULL, " +
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
                "is_completed INTEGER NOT NULL, " +
                "rpe INTEGER, " +
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
                "order_index INTEGER NOT NULL, " +
                "device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS routines_user_default_idx ON routines(user_id, is_default)");
        db.execSQL("CREATE INDEX IF NOT EXISTS routine_exercises_routine_order_idx ON routine_exercises(routine_id, order_index)");
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
                "kind TEXT NOT NULL, " +
                "category TEXT NOT NULL DEFAULT 'other', " +
                "basis_amount REAL NOT NULL, " +
                "basis_unit TEXT NOT NULL, " +
                "prep_state TEXT NOT NULL DEFAULT 'unspecified', " +
                "cooking_method TEXT NOT NULL DEFAULT 'unspecified', " +
                "calories_kcal REAL NOT NULL DEFAULT 0, " +
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
                "food_id TEXT, " +
                "food_name_snapshot TEXT NOT NULL, " +
                "brand_snapshot TEXT, " +
                "food_kind_snapshot TEXT, " +
                "quantity REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "basis_amount_snapshot REAL, " +
                "basis_unit_snapshot TEXT, " +
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
                "food_id TEXT, " +
                "food_name_snapshot TEXT NOT NULL, " +
                "brand_snapshot TEXT, " +
                "food_kind_snapshot TEXT, " +
                "quantity REAL NOT NULL, " +
                "unit TEXT NOT NULL, " +
                "basis_amount_snapshot REAL, " +
                "basis_unit_snapshot TEXT, " +
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

    private void createNutritionIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_name_idx " +
                "ON nutrition_foods(owner_id, name COLLATE NOCASE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS nutrition_foods_owner_brand_name_idx " +
                "ON nutrition_foods(owner_id, brand COLLATE NOCASE, name COLLATE NOCASE)");
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
                "seller_name TEXT, " +
                "latest_price_krw INTEGER, " +
                "price_observed_at TEXT, " +
                "content_amount REAL, " +
                "content_unit TEXT, " +
                "package_count INTEGER, " +
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
        createNutritionIndexes(db);
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
}
