package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class FitnessDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "fitness_mvp.db";
    public static final int DATABASE_VERSION = 5;

    public FitnessDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createSharedRecordTables(db);
        createRoutineTables(db);
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
