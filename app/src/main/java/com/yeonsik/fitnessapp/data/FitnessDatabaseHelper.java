package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class FitnessDatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "fitness_mvp.db";
    public static final int DATABASE_VERSION = 1;

    public FitnessDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE workout_sessions (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "started_at TEXT, " +
                "ended_at TEXT, " +
                "status TEXT NOT NULL, " +
                "session_type TEXT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "memo TEXT, " +
                "created_from_quick_record_id TEXT, " +
                "device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE TABLE workout_exercises (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "session_id TEXT NOT NULL, " +
                "order_index INTEGER NOT NULL, " +
                "exercise_name TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "exercise_type TEXT NOT NULL, " +
                "memo TEXT, " +
                "device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE TABLE workout_sets (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "exercise_id TEXT NOT NULL, " +
                "session_id TEXT NOT NULL, " +
                "set_index INTEGER NOT NULL, " +
                "target_reps INTEGER, " +
                "actual_reps INTEGER, " +
                "weight_kg REAL, " +
                "duration_seconds INTEGER, " +
                "distance_meters REAL, " +
                "rest_seconds INTEGER, " +
                "is_completed INTEGER NOT NULL, " +
                "rpe INTEGER, " +
                "memo TEXT, " +
                "device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE TABLE body_metrics (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "weight_kg REAL NOT NULL, " +
                "body_fat_percent REAL, " +
                "muscle_mass_kg REAL, " +
                "waist_cm REAL, " +
                "memo TEXT, " +
                "source TEXT NOT NULL, " +
                "created_from_quick_record_id TEXT, " +
                "device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");
        db.execSQL("CREATE TABLE meals (" +
                "id TEXT PRIMARY KEY, " +
                "user_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "meal_type TEXT NOT NULL, " +
                "menu_text TEXT NOT NULL, " +
                "calories INTEGER, " +
                "protein_grams REAL, " +
                "carbs_grams REAL, " +
                "fat_grams REAL, " +
                "source TEXT NOT NULL, " +
                "created_from_quick_record_id TEXT, " +
                "device_id TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "updated_at TEXT NOT NULL, " +
                "deleted_at TEXT)");

        db.execSQL("CREATE INDEX workout_sessions_user_date_idx ON workout_sessions(user_id, date)");
        db.execSQL("CREATE INDEX workout_exercises_session_order_idx ON workout_exercises(session_id, order_index)");
        db.execSQL("CREATE INDEX workout_sets_exercise_order_idx ON workout_sets(exercise_id, set_index)");
        db.execSQL("CREATE INDEX body_metrics_user_date_idx ON body_metrics(user_id, date)");
        db.execSQL("CREATE INDEX meals_user_date_idx ON meals(user_id, date)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No migrations exist for version " + oldVersion + " -> " + newVersion);
    }
}
