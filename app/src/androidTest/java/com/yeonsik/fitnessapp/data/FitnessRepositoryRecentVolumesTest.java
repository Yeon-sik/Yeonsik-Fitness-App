package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public final class FitnessRepositoryRecentVolumesTest {
    private static final String DATABASE_PREFIX = "recent_volumes_";
    private static final String USER_ID = "recent-volumes-test-user";

    @Test
    public void filtersInProgressRowsBeforeLimitAndKeepsPersonalOsCompletedRows() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            repository.createSession(
                    "2026-09-02", "Completed A", "strength", "", "",
                    "2026-09-02T10:00:00Z"
            );
            String personalOsRecordId = repository.createSession(
                    "2026-09-03", "Personal OS completed", "strength", "", "",
                    "2026-09-03T10:00:00Z"
            );
            ContentValues personalOs = new ContentValues();
            personalOs.put("source_app", "os");
            helper.getWritableDatabase().update(
                    "workout_records",
                    personalOs,
                    "id = ?",
                    new String[]{personalOsRecordId}
            );
            repository.createSession(
                    "2026-09-04", "In progress", "strength", "",
                    "2026-09-04T10:00:00Z", ""
            );
            String currentRecordId = repository.createSession(
                    "2026-09-05", "Current", "strength", "", "",
                    "2026-09-05T10:00:00Z"
            );

            List<FitnessRepository.VolumePoint> rows = repository
                    .recentCompletedSessionVolumes(currentRecordId, 2);
            assertEquals(2, rows.size());
            assertEquals("2026-09-02", rows.get(0).date);
            assertEquals("2026-09-03", rows.get(1).date);
            assertFalse(containsDate(rows, "2026-09-04"));

            List<FitnessRepository.VolumePoint> compatibilityRows = repository
                    .recentSessionVolumes(currentRecordId, 2);
            assertEquals(2, compatibilityRows.size());
            assertEquals("2026-09-03", compatibilityRows.get(1).date);
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static boolean containsDate(
            List<FitnessRepository.VolumePoint> rows,
            String date
    ) {
        for (FitnessRepository.VolumePoint row : rows) {
            if (row != null && date.equals(row.date)) {
                return true;
            }
        }
        return false;
    }

    private static IsolatedDatabaseContext isolatedContext() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        return context;
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
