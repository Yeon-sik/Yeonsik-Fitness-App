package com.yeonsik.fitnessapp.data;

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
import static org.junit.Assert.assertNull;

/** Regression coverage for the legacy FitnessRepository body-metric facade. */
@RunWith(AndroidJUnit4.class)
public final class BodyMetricsRepositoryFacadeTest {
    private static final String DATABASE_PREFIX = "body_metrics_facade_";
    private static final String FIRST_USER_ID = "body-metrics-first-user";
    private static final String SECOND_USER_ID = "body-metrics-second-user";

    @Test
    public void facadePreservesUpsertFormattingTombstoneAndOwnerIsolation() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, FIRST_USER_ID);

            String firstId = repository.addBodyMetric("2026-09-01", 70d, "before breakfast");
            String sameDateId = repository.addBodyMetric("2026-09-01", 70.25d, "after breakfast");
            assertEquals(firstId, sameDateId);

            FitnessRepository.BodyMetricEntry updated = repository.bodyMetricForDate("2026-09-01");
            assertEquals(firstId, updated.id);
            assertEquals(70.25d, updated.weightKg, 0.001d);
            assertEquals("after breakfast", updated.memo);

            repository.addBodyMetric("2026-09-02", 70.5d, "");
            assertEquals("2026-09-02", repository.latestBodyMetricOnOrBefore("2026-09-03").date);
            List<String> formatted = repository.bodyMetricsForDate("2026-09-02");
            assertEquals(1, formatted.size());
            assertEquals("2026. 09. 02  70.5kg", formatted.get(0));

            repository.setUserId(SECOND_USER_ID);
            assertNull(repository.bodyMetricForDate("2026-09-01"));
            String secondUserId = repository.addBodyMetric("2026-09-01", 80d, "other owner");
            assertFalse(firstId.equals(secondUserId));

            repository.setUserId(FIRST_USER_ID);
            assertEquals(firstId, repository.bodyMetricEntryById(firstId).id);
            assertNull(repository.bodyMetricEntryById(secondUserId));
            repository.deleteBodyMetric(firstId);
            assertNull(repository.bodyMetricEntryById(firstId));
            assertEquals(1, countRows(
                    helper.getWritableDatabase(),
                    "SELECT COUNT(*) FROM weight_records WHERE id = ? AND deleted_at IS NOT NULL",
                    firstId
            ));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static int countRows(SQLiteDatabase database, String sql, String argument) {
        try (android.database.Cursor cursor = database.rawQuery(sql, new String[]{argument})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
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
