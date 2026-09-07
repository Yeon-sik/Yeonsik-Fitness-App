package com.yeonsik.fitnessapp.core.database

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/** Immutable v50 storage boundary that Room must adopt without rewriting user data. */
object FitnessDatabaseContract {
    const val NAME = "fitness_mvp.db"
    const val LEGACY_VERSION = 50
    const val ROOM_VERSION = 51

    val tableNames: Set<String> = linkedSetOf(
        "body_profiles",
        "cardio_route_points",
        "cardio_sessions",
        "composition_groups",
        "composition_members",
        "composition_templates",
        "development_goals",
        "devices",
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
    )


    /** Present only in legacy v38+ upgrades; never discard it when it already exists. */
    val optionalLegacyTableNames: Set<String> =
        setOf("dining_out_menu_add_on_links")
    /** Refuses a partial or differently named file before Room changes its version marker. */
    fun requireV50Schema(database: SupportSQLiteDatabase) {
        val actual = linkedSetOf<String>()
        database.query(
            SimpleSQLiteQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name"
            )
        ).use { cursor ->
            while (cursor.moveToNext()) {
                actual += cursor.getString(0)
            }
        }
        val allowed = tableNames + optionalLegacyTableNames
        check(actual.containsAll(tableNames) && actual.all { it in allowed }) {
            "fitness_mvp.db v50 schema mismatch. Required $tableNames, optional " +
                "$optionalLegacyTableNames, but found $actual."
        }
    }
}
