package com.yeonsik.fitnessapp.core.database;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessDatabaseMigrationTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Proves that Room adopts an existing v50 file without re-creating its user tables. */
@RunWith(AndroidJUnit4.class)
public final class FitnessRoomHandoffMigrationTest {
    private static final String PREFIX = "room_handoff_v50_";

    @Test
    public void roomAdoptsExistingV50WithoutLosingWeightOrRoutineRows() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseContract.NAME);

        FitnessDatabaseHelper legacy = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = null;
        try {
            SQLiteDatabase legacyDatabase = legacy.getWritableDatabase();
            legacyDatabase.execSQL(
                    "INSERT INTO weight_records (id, user_id, date, weight_kg, created_at, " +
                            "is_backfilled, updated_at, device_id, source_app, scope, metadata, " +
                            "contract_version) VALUES ('weight-v50', 'room-user', '2026-09-07', " +
                            "72.4, '2026-09-07T00:00:00Z', 0, '2026-09-07T00:00:00Z', " +
                            "'device', 'fitness', 'fitness', '{}', 1)"
            );
            legacyDatabase.execSQL(
                    "INSERT INTO routines (id, user_id, name, is_default, device_id, created_at, " +
                            "updated_at) VALUES ('routine-v50', 'room-user', 'v50 routine', 1, " +
                            "'device', '2026-09-07T00:00:00Z', '2026-09-07T00:00:00Z')"
            );
            legacyDatabase.execSQL(
                    "INSERT INTO workout_exercises (id, user_id, record_id, order_index, exercise_id, " +
                            "exercise_name_snapshot, ui_part, record_type, family_id, preset_id, " +
                            "canonical_variant_key, visual_variant_key, created_at, updated_at, " +
                            "device_id, contract_version) VALUES ('exercise-v50', 'room-user', " +
                    "'record-v50', 0, 'bench-press', 'Bench snapshot', 'chest', 'weight', " +
                            "'barbell-bench', 'flat-bench', 'barbell-flat', 'front', " +
                            "'2026-09-07T00:00:00Z', '2026-09-07T00:00:00Z', 'device', 1)"
            );
            legacyDatabase.execSQL(
                    "INSERT INTO workout_sets (id, user_id, workout_exercise_id, set_index, " +
                            "input_load_value, input_load_unit, load_state, is_completed, created_at, " +
                            "updated_at, deleted_at, device_id, contract_version) VALUES ('set-v50', " +
                            "'room-user', 'exercise-v50', 0, 135.5, 'lb', 'entered', 1, " +
                            "'2026-09-07T00:00:00Z', '2026-09-07T00:00:00Z', " +
                            "'2026-09-08T00:00:00Z', 'device', 1)"
            );
            legacyDatabase.execSQL(
                    "INSERT INTO sync_state (scope_key, table_name, direction, cursor_version, " +
                            "cursor_id, updated_at) VALUES ('room-user', 'workout_sets', 'pull', " +
                            "'snapshot-v1', 'cursor-v50', '2026-09-07T00:00:00Z')"
            );
            legacyDatabase.execSQL(
                    "INSERT INTO meal_records (id, user_id, date, menu, calories, protein_grams, " +
                            "created_at, is_backfilled, updated_at, device_id, source_app, scope, metadata, " +
                            "contract_version) VALUES ('meal-v50', 'room-user', '2026-09-07', 'snapshot meal', " +
                            "640, 42, '2026-09-07T00:00:00Z', 0, '2026-09-07T00:00:00Z', 'device', " +
                            "'fitness', 'fitness', '{}', 1)"
            );
            legacyDatabase.execSQL(
                    "INSERT INTO meal_record_items (id, user_id, meal_record_id, food_id, " +
                            "food_name_snapshot, brand_snapshot, quantity, unit, calories, protein_grams, " +
                            "carbs_grams, fat_grams, source_type_snapshot, source_reference_snapshot, " +
                            "source_version_snapshot, order_index, created_at, updated_at, device_id) VALUES " +
                            "('item-v50', 'room-user', 'meal-v50', 'catalog-food-1', 'saved snapshot', " +
                            "'saved brand', 1, 'serving', 640, 42, 60, 20, 'legacy_source', " +
                            "'legacy-reference', 'legacy-v1', 0, '2026-09-07T00:00:00Z', " +
                            "'2026-09-07T00:00:00Z', 'device')"
            );
            legacy.close();
            legacy = null;

            room = Room.databaseBuilder(context, FitnessRoomDatabase.class,
                            FitnessDatabaseContract.NAME)
                    .addMigrations(FitnessRoomMigrations.all(context))
                    .build();

            SupportSQLiteDatabase database = room.getOpenHelper().getWritableDatabase();
            assertEquals(FitnessDatabaseContract.ROOM_VERSION, database.getVersion());
            assertEquals("72.4", scalar(database,
                    "SELECT weight_kg FROM weight_records WHERE id = 'weight-v50'"));
            assertEquals("v50 routine", scalar(database,
                    "SELECT name FROM routines WHERE id = 'routine-v50'"));
            assertEquals("Bench snapshot", scalar(database,
                    "SELECT exercise_name_snapshot FROM workout_exercises WHERE id = 'exercise-v50'"));
            assertEquals("barbell-bench", scalar(database,
                    "SELECT family_id FROM workout_exercises WHERE id = 'exercise-v50'"));
            assertEquals("barbell-flat", scalar(database,
                    "SELECT canonical_variant_key FROM workout_exercises WHERE id = 'exercise-v50'"));
            assertEquals("lb", scalar(database,
                    "SELECT input_load_unit FROM workout_sets WHERE id = 'set-v50'"));
            assertEquals("2026-09-08T00:00:00Z", scalar(database,
                    "SELECT deleted_at FROM workout_sets WHERE id = 'set-v50'"));
            assertEquals("cursor-v50", scalar(database,
                    "SELECT cursor_id FROM sync_state WHERE scope_key = 'room-user'"));
            assertEquals("saved snapshot", scalar(database,
                    "SELECT food_name_snapshot FROM meal_record_items WHERE id = 'item-v50'"));
            assertEquals("legacy-v1", scalar(database,
                    "SELECT source_version_snapshot FROM meal_record_items WHERE id = 'item-v50'"));
            assertNull(nullableScalar(database,
                    "SELECT sodium_mg FROM meal_record_items WHERE id = 'item-v50'"));
            assertAllPrimaryKeysAreNotNull(database);
            assertTrue(tableExists(database, "sync_state"));
            assertTrue(tableExists(database, "meal_record_items"));
        } finally {
            if (legacy != null) {
                legacy.close();
            }
            if (room != null) {
                room.close();
            }
            context.deleteDatabase(FitnessDatabaseContract.NAME);
        }
    }

    @Test
    public void roomFailsClosedWhenLegacyNullablePrimaryKeyContainsNull() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseContract.NAME);

        FitnessDatabaseHelper legacy = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = null;
        try {
            SQLiteDatabase legacyDatabase = legacy.getWritableDatabase();
            legacyDatabase.execSQL(
                    "INSERT INTO body_profiles (user_id, height_cm, created_at, updated_at) " +
                            "VALUES (NULL, 175, '2026-09-07T00:00:00Z', '2026-09-07T00:00:00Z')"
            );
            legacy.close();
            legacy = null;

            boolean failedClosed = false;
            room = Room.databaseBuilder(context, FitnessRoomDatabase.class,
                            FitnessDatabaseContract.NAME)
                    .addMigrations(FitnessRoomMigrations.all(context))
                    .build();
            try {
                room.getOpenHelper().getWritableDatabase();
            } catch (IllegalStateException expected) {
                failedClosed = expected.getMessage() != null
                        && expected.getMessage().contains("body_profiles.user_id");
            }
            assertTrue("A legacy NULL primary-key row must abort the Room handoff.", failedClosed);
        } finally {
            if (legacy != null) {
                legacy.close();
            }
            if (room != null) {
                room.close();
            }
            context.deleteDatabase(FitnessDatabaseContract.NAME);
        }
    }

    @Test
    public void freshRoomCreatesTheFullV51SchemaBeforeRoomOwnershipBegins() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseContract.NAME);
        FitnessRoomDatabase room = null;
        try {
            room = Room.databaseBuilder(context, FitnessRoomDatabase.class,
                            FitnessDatabaseContract.NAME)
                    .openHelperFactory(new FitnessRoomOpenHelperFactory())
                    .addMigrations(FitnessRoomMigrations.all(context))
                    .build();
            SupportSQLiteDatabase database = room.getOpenHelper().getWritableDatabase();

            assertEquals(FitnessDatabaseContract.ROOM_VERSION, database.getVersion());
            assertTrue(tableExists(database, "meal_record_item_component_nutrients"));
            assertTrue(tableExists(database, "dining_out_menu_add_on_links"));
            assertEquals("1", scalar(database,
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                            "AND name = 'verified_receipt_items'"));
            assertTrue(scalar(database,
                    "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'body_profiles'")
                    .contains("CHECK (height_cm BETWEEN 50 AND 300)"));
            assertTrue(scalar(database,
                    "SELECT sql FROM sqlite_master WHERE type = 'index' " +
                            "AND name = 'product_nutrition_links_one_approved_idx'")
                    .contains("WHERE status = 'approved' AND deleted_at IS NULL"));
            assertAllPrimaryKeysAreNotNull(database);
        } finally {
            if (room != null) {
                room.close();
            }
            context.deleteDatabase(FitnessDatabaseContract.NAME);
        }
    }

    @Test
    public void roomMigratesVersionEightDirectlyToV51AndPreservesHistoricalValues() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseContract.NAME);
        FitnessRoomDatabase room = null;
        try {
            SQLiteDatabase legacy = context.openOrCreateDatabase(
                    FitnessDatabaseContract.NAME,
                    0,
                    null
            );
            FitnessDatabaseMigrationTest.createVersionEightSchema(legacy);
            legacy.execSQL("CREATE TABLE meal_records (" +
                    "id TEXT PRIMARY KEY, user_id TEXT NOT NULL, date TEXT NOT NULL, menu TEXT NOT NULL, " +
                    "calories INTEGER NOT NULL, protein_grams REAL NOT NULL, carbs_grams REAL, fat_grams REAL, " +
                    "created_at TEXT NOT NULL, is_backfilled INTEGER NOT NULL, updated_at TEXT NOT NULL, " +
                    "deleted_at TEXT, device_id TEXT NOT NULL, source_app TEXT NOT NULL, scope TEXT NOT NULL, " +
                    "metadata TEXT NOT NULL)");
            legacy.execSQL("INSERT INTO meal_records (id, user_id, date, menu, calories, protein_grams, " +
                    "carbs_grams, fat_grams, created_at, is_backfilled, updated_at, device_id, source_app, " +
                    "scope, metadata) VALUES ('meal-v8', 'local-user', '2026-08-08', 'legacy meal', 550, 30, " +
                    "60, 20, '2026-08-08T00:00:00Z', 0, '2026-08-08T00:00:00Z', 'device-1', 'fitness', " +
                    "'fitness', '{}')");
            legacy.execSQL("INSERT INTO meal_record_items (id, user_id, meal_record_id, food_id, " +
                    "food_name_snapshot, quantity, unit, calories, protein_grams, carbs_grams, fat_grams, " +
                    "order_index, created_at, updated_at, device_id) VALUES ('item-v8', 'local-user', " +
                    "'meal-v8', 'legacy-option-1', '감자튀김 snapshot', 1, 'serving', 320, 4, 42, 15, 0, " +
                    "'2026-08-08T00:00:00Z', '2026-08-08T00:00:00Z', 'device-1')");
            legacy.setVersion(FitnessRoomMigrations.MINIMUM_SUPPORTED_LEGACY_VERSION);
            legacy.close();

            room = Room.databaseBuilder(context, FitnessRoomDatabase.class,
                            FitnessDatabaseContract.NAME)
                    .addMigrations(FitnessRoomMigrations.all(context))
                    .build();
            SupportSQLiteDatabase database = room.getOpenHelper().getWritableDatabase();

            assertEquals(FitnessDatabaseContract.ROOM_VERSION, database.getVersion());
            assertEquals("80.0", scalar(database,
                    "SELECT weight_kg FROM workout_sets WHERE id = 'set-1'"));
            assertNull(nullableScalar(database,
                    "SELECT input_load_value FROM workout_sets WHERE id = 'set-1'"));
            assertEquals("감자튀김 snapshot", scalar(database,
                    "SELECT food_name_snapshot FROM meal_record_items WHERE id = 'item-v8'"));
            assertEquals("serving", scalar(database,
                    "SELECT basis_unit_snapshot FROM meal_record_items WHERE id = 'item-v8'"));
            assertEquals("legacy-option-1", scalar(database,
                    "SELECT food_id FROM meal_record_items WHERE id = 'item-v8'"));
            assertEquals("local-user", scalar(database,
                    "SELECT user_id FROM cardio_sessions WHERE record_id = 'record-1'"));
            assertTrue(tableExists(database, "sync_state"));
            assertTrue(tableExists(database, "exercise_picker_preferences"));
            assertAllPrimaryKeysAreNotNull(database);
        } finally {
            if (room != null) {
                room.close();
            }
            context.deleteDatabase(FitnessDatabaseContract.NAME);
        }
    }

    private static void assertAllPrimaryKeysAreNotNull(SupportSQLiteDatabase database) {
        try (Cursor tables = database.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                        "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' " +
                        "AND name != 'room_master_table'"
        )) {
            while (tables.moveToNext()) {
                String tableName = tables.getString(0);
                String quotedName = "\"" + tableName.replace("\"", "\"\"") + "\"";
                int primaryKeyCount = 0;
                try (Cursor columns = database.query("PRAGMA table_info(" + quotedName + ")")) {
                    while (columns.moveToNext()) {
                        if (columns.getInt(5) > 0) {
                            primaryKeyCount++;
                            assertEquals(
                                    "Room handoff must normalize " + tableName + "." +
                                            columns.getString(1),
                                    1,
                                    columns.getInt(3)
                            );
                        }
                    }
                }
                assertTrue("Expected primary key for " + tableName, primaryKeyCount > 0);
            }
        }
    }
    private static boolean tableExists(SupportSQLiteDatabase database, String tableName) {
        try (Cursor cursor = database.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                new Object[]{tableName}
        )) {
            return cursor.moveToFirst();
        }
    }

    private static String scalar(SupportSQLiteDatabase database, String sql) {
        try (Cursor cursor = database.query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private static String nullableScalar(SupportSQLiteDatabase database, String sql) {
        try (Cursor cursor = database.query(sql)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getString(0);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private IsolatedDatabaseContext(Context base) {
            super(base);
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(PREFIX + name);
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
