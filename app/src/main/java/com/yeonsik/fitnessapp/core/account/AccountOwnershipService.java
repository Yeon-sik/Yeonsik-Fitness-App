package com.yeonsik.fitnessapp.core.account;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** Owns the one-time claim of local rows when the shared account becomes authenticated. */
public final class AccountOwnershipService {
    private static final String DEVICE_ID = "android-local";

    private final FitnessDatabaseConnection database;
    private String ownerId;

    public AccountOwnershipService(FitnessDatabaseConnection database, String ownerId) {
        if (database == null) {
            throw new IllegalArgumentException("Fitness database connection is required.");
        }
        this.database = database;
        this.ownerId = normalizeUserId(ownerId);
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = normalizeUserId(ownerId);
    }

    public void claimLocalRows(String nextOwnerId) {
        String normalizedOwnerId = normalizeUserId(nextOwnerId);
        if (AccountOwnerPolicy.shouldClaimLocalRows(ownerId, normalizedOwnerId)) {
            database.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put("user_id", normalizedOwnerId);
                for (String table : claimableOwnerTables()) {
                    database.update(
                            table,
                            values,
                            "user_id = ?",
                            new String[]{SupabaseConfig.DEFAULT_USER_ID}
                    );
                }
                claimSingletonNutritionGoal(normalizedOwnerId);
                claimConflictFreeDailyRows(normalizedOwnerId);
                database.delete(
                        "devices",
                        "user_id = ?",
                        new String[]{SupabaseConfig.DEFAULT_USER_ID}
                );
                ensureDevice(normalizedOwnerId);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } else {
            ensureDevice(normalizedOwnerId);
        }
        ownerId = normalizedOwnerId;
    }

    private static List<String> claimableOwnerTables() {
        return Arrays.asList(
                "workout_records",
                "workout_exercises",
                "workout_sets",
                "meal_records",
                "dining_out_menu_component_links",
                "meal_record_items",
                "meal_record_item_nutrients",
                "meal_record_item_components",
                "meal_record_item_component_nutrients",
                "meal_record_item_consumptions",
                "weight_records",
                "cardio_sessions",
                "cardio_route_points",
                "routines",
                "routine_exercises",
                "exercise_picker_preferences"
        );
    }

    private void claimSingletonNutritionGoal(String nextOwnerId) {
        database.execSQL(
                "INSERT OR REPLACE INTO nutrition_goals (" +
                        "user_id, phase, calories_kcal, protein_grams, carbs_grams, " +
                        "fat_grams, fiber_grams, sodium_mg, water_ml, created_at, updated_at) " +
                        "SELECT ?, source.phase, source.calories_kcal, source.protein_grams, " +
                        "source.carbs_grams, source.fat_grams, source.fiber_grams, " +
                        "source.sodium_mg, source.water_ml, source.created_at, source.updated_at " +
                        "FROM nutrition_goals source WHERE source.user_id = ? " +
                        "AND (NOT EXISTS (SELECT 1 FROM nutrition_goals target " +
                        "WHERE target.user_id = ?) OR julianday(source.updated_at) > julianday((" +
                        "SELECT target.updated_at FROM nutrition_goals target " +
                        "WHERE target.user_id = ? LIMIT 1)))",
                new Object[]{
                        nextOwnerId,
                        SupabaseConfig.DEFAULT_USER_ID,
                        nextOwnerId,
                        nextOwnerId
                }
        );
        database.delete(
                "nutrition_goals",
                "user_id = ?",
                new String[]{SupabaseConfig.DEFAULT_USER_ID}
        );
    }

    private void claimConflictFreeDailyRows(String nextOwnerId) {
        database.execSQL(
                "INSERT OR REPLACE INTO nutrition_daily_checkins (" +
                        "id, user_id, date, water_ml, sleep_hours, energy_score, hunger_score, " +
                        "digestion_score, training_readiness_score, note, created_at, updated_at) " +
                        "SELECT source.id, ?, source.date, source.water_ml, source.sleep_hours, " +
                        "source.energy_score, source.hunger_score, source.digestion_score, " +
                        "source.training_readiness_score, source.note, source.created_at, " +
                        "source.updated_at FROM nutrition_daily_checkins source " +
                        "WHERE source.user_id = ? AND (NOT EXISTS (" +
                        "SELECT 1 FROM nutrition_daily_checkins target " +
                        "WHERE target.user_id = ? AND target.date = source.date) " +
                        "OR julianday(source.updated_at) > julianday((SELECT target.updated_at " +
                        "FROM nutrition_daily_checkins target WHERE target.user_id = ? " +
                        "AND target.date = source.date LIMIT 1)))",
                new Object[]{
                        nextOwnerId,
                        SupabaseConfig.DEFAULT_USER_ID,
                        nextOwnerId,
                        nextOwnerId
                }
        );
        database.delete(
                "nutrition_daily_checkins",
                "user_id = ?",
                new String[]{SupabaseConfig.DEFAULT_USER_ID}
        );

        database.execSQL(
                "INSERT OR REPLACE INTO meal_menu_presets (" +
                        "id, user_id, name, calories, protein_grams, carbs_grams, fat_grams, " +
                        "created_at, updated_at) SELECT source.id, ?, source.name, source.calories, " +
                        "source.protein_grams, source.carbs_grams, source.fat_grams, " +
                        "source.created_at, source.updated_at FROM meal_menu_presets source " +
                        "WHERE source.user_id = ? AND (NOT EXISTS (" +
                        "SELECT 1 FROM meal_menu_presets target WHERE target.user_id = ? " +
                        "AND target.name = source.name COLLATE NOCASE) " +
                        "OR julianday(source.updated_at) > julianday((" +
                        "SELECT target.updated_at FROM meal_menu_presets target " +
                        "WHERE target.user_id = ? AND target.name = source.name COLLATE NOCASE " +
                        "LIMIT 1)))",
                new Object[]{
                        nextOwnerId,
                        SupabaseConfig.DEFAULT_USER_ID,
                        nextOwnerId,
                        nextOwnerId
                }
        );
        database.delete(
                "meal_menu_presets",
                "user_id = ?",
                new String[]{SupabaseConfig.DEFAULT_USER_ID}
        );
    }

    private void ensureDevice(String ownerId) {
        String now = OffsetDateTime.now().toString();
        ContentValues values = new ContentValues();
        values.put("id", DEVICE_ID);
        values.put("user_id", normalizeUserId(ownerId));
        values.put("name", "Fitness Android");
        values.put("last_seen_at", now);
        values.put("app_version", "0.1.0");
        database.insertWithOnConflict(
                "devices",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }
}
