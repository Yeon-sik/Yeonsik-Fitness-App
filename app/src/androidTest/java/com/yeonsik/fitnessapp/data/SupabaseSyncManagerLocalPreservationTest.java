package com.yeonsik.fitnessapp.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;
import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class SupabaseSyncManagerLocalPreservationTest {
    private static final String DATABASE_PREFIX = "sync_local_preservation_";
    private static final String USER_ID = "sync-local-preservation-user";

    @Test
    public void newerRemoteRowDoesNotReplaceLocalOnlyMealColumns() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTimeWithBranchAndNutritionAndOptionNutrition(
                    date,
                    "19:20",
                    "로컬 식당",
                    "로컬 지점",
                    "로컬 메뉴",
                    620,
                    40d,
                    70d,
                    20d,
                    900d,
                    12d,
                    8d,
                    null,
                    Collections.emptyList()
            );

            JSONObject remoteRow = new JSONObject();
            remoteRow.put("id", recordId);
            remoteRow.put("user_id", USER_ID);
            remoteRow.put("updated_at", "2099-01-01T00:00:00Z");
            JSONArray remoteRows = new JSONArray().put(remoteRow);

            int applied = new SupabaseSyncManager(helper).applyRows(
                    helper.getWritableDatabase(),
                    "meal_records",
                    remoteRows,
                    USER_ID
            );

            assertEquals(1, applied);
            SQLiteDatabase database = helper.getReadableDatabase();
            assertEquals("로컬 지점", scalar(database,
                    "SELECT branch_name FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals("로컬 메뉴", scalar(database,
                    "SELECT menu_name FROM meal_records WHERE id = '" + recordId + "'"));
            assertEquals("dining_out", scalar(database,
                    "SELECT meal_kind FROM meal_records WHERE id = '" + recordId + "'"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void pullWithNoRemoteMealDoesNotDeleteLocalMeal() throws Exception {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            String date = LocalDate.now().minusDays(1).toString();
            String recordId = repository.addDiningOutMealAtTimeWithOptionNutrition(
                    date,
                    "19:20",
                    "로컬 식당",
                    "로컬 메뉴",
                    70d,
                    40d,
                    20d,
                    null,
                    Collections.emptyList()
            );

            int applied = new SupabaseSyncManager(helper).applyRows(
                    helper.getWritableDatabase(),
                    "meal_records",
                    new JSONArray(),
                    USER_ID
            );

            assertEquals(0, applied);
            assertEquals(recordId, scalar(helper.getReadableDatabase(),
                    "SELECT id FROM meal_records WHERE id = '" + recordId
                            + "' AND deleted_at IS NULL"));
            assertTrue(scalar(helper.getReadableDatabase(),
                    "SELECT COUNT(*) FROM meal_records WHERE user_id = '" + USER_ID + "'")
                    .equals("1"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static String scalar(SQLiteDatabase database, String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return cursor.isNull(0) ? null : cursor.getString(0);
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
