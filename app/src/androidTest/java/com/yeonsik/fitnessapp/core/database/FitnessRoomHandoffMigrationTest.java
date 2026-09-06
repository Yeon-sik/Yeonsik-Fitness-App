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

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertEquals;
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
            legacy.close();
            legacy = null;

            room = Room.databaseBuilder(context, FitnessRoomDatabase.class,
                            FitnessDatabaseContract.NAME)
                    .addMigrations(FitnessRoomMigrations.INSTANCE.getV50_TO_V51())
                    .build();

            SupportSQLiteDatabase database = room.getOpenHelper().getWritableDatabase();
            assertEquals(FitnessDatabaseContract.ROOM_VERSION, database.getVersion());
            assertEquals("72.4", scalar(database,
                    "SELECT weight_kg FROM weight_records WHERE id = 'weight-v50'"));
            assertEquals("v50 routine", scalar(database,
                    "SELECT name FROM routines WHERE id = 'routine-v50'"));
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
