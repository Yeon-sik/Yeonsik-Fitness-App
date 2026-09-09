package com.yeonsik.fitnessapp.sync;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutSummaryApi;
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutSummaryRepository;
import com.yeonsik.fitnessapp.integration.personalos.FitnessSummaryPublisher;

import org.json.JSONArray;
import org.json.JSONException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Compatibility orchestrator for the two existing publication paths.
 *
 * Detailed record synchronization remains in [LegacyFitnessSyncAdapter]. Summary Projection v2
 * is published by [FitnessSummaryPublisher] after the local summary store has been reconciled.
 * No event store or outbox is introduced by this boundary.
 */
public final class SupabaseSyncManager {
    public static final List<String> TABLES = LegacyFitnessSyncAdapter.TABLES;

    private final LegacyFitnessSyncAdapter legacyAdapter;
    private final WorkoutSummaryApi summaryStore;
    private final FitnessSummaryPublisher summaryPublisher;

    public SupabaseSyncManager(FitnessDatabaseHelper dbHelper) {
        this(FitnessDatabaseConnection.fromLegacy(dbHelper));
    }

    public SupabaseSyncManager(FitnessRoomDatabase roomDatabase, Context context) {
        this(
                FitnessDatabaseConnection.fromRoom(roomDatabase, context),
                new LegacyFitnessSyncAdapter(roomDatabase, context),
                new WorkoutSummaryRepository(roomDatabase),
                new FitnessSummaryPublisher()
        );
    }

    public SupabaseSyncManager(FitnessDatabaseConnection database) {
        this(
                database,
                new LegacyFitnessSyncAdapter(database),
                new EmptyWorkoutSummaryApi(),
                new FitnessSummaryPublisher()
        );
    }

    SupabaseSyncManager(
            FitnessDatabaseConnection database,
            LegacyFitnessSyncAdapter legacyAdapter,
            WorkoutSummaryApi summaryStore,
            FitnessSummaryPublisher summaryPublisher
    ) {
        if (database == null || legacyAdapter == null || summaryStore == null
                || summaryPublisher == null) {
            throw new IllegalArgumentException("Sync dependencies are required.");
        }
        this.legacyAdapter = legacyAdapter;
        this.summaryStore = summaryStore;
        this.summaryPublisher = summaryPublisher;
    }

    /** Compatibility constructor used only by legacy sync instrumentation fixtures. */
    private static final class EmptyWorkoutSummaryApi implements WorkoutSummaryApi {
        @Override
        public int reconcileSharedWorkoutSummaries(String ownerId) {
            return 0;
        }

        @Override
        public boolean updateSharedWorkoutSummary(
                String ownerId,
                String recordId,
                boolean publishToOs
        ) {
            return false;
        }

        @Override
        public java.util.List<com.yeonsik.fitnessapp.data.FitnessSummaryProjectionV2>
                completedFitnessSummaryProjectionsV2(String ownerId) {
            return java.util.Collections.emptyList();
        }
    }

    public SyncResult manualSync(SupabaseConfig config) throws Exception {
        LegacyFitnessSyncAdapter.SyncResult legacy = legacyAdapter.manualSync(config);
        int published = 0;
        boolean summarySucceeded = true;
        String summaryError = "";
        try {
            summaryStore.reconcileSharedWorkoutSummaries(config.effectiveUserId());
            published = summaryPublisher.publish(config, summaryStore);
        } catch (Exception error) {
            summarySucceeded = false;
            summaryError = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
        }
        return new SyncResult(
                legacy.pushedRows + published,
                legacy.pulledRows,
                legacy.syncedAt,
                published,
                summarySucceeded,
                summaryError
        );
    }

    /** Compatibility seam retained for existing legacy-sync instrumentation coverage. */
    int applyRows(
            FitnessDatabaseConnection database,
            String table,
            JSONArray rows,
            String userId
    ) throws JSONException {
        return legacyAdapter.applyRows(database, table, rows, userId);
    }

    /** Compatibility seam retained for existing legacy-sync instrumentation coverage. */
    int applyRows(
            SQLiteDatabase database,
            String table,
            JSONArray rows,
            String userId
    ) throws JSONException {
        return legacyAdapter.applyRows(database, table, rows, userId);
    }

    static boolean isRpcUnavailable(int statusCode, String body) {
        return LegacyFitnessSyncAdapter.isRpcUnavailable(statusCode, body);
    }

    static boolean shouldSyncColumn(String table, String column) {
        return LegacyFitnessSyncAdapter.shouldSyncColumn(table, column);
    }

    static int compareVersions(String left, String right) {
        return LegacyFitnessSyncAdapter.compareVersions(left, right);
    }

    public static final class SyncResult {
        public final int pushedRows;
        public final int pulledRows;
        public final String syncedAt;
        public final int summaryPublishedRows;
        public final boolean summaryPublicationSucceeded;
        public final String summaryPublicationError;

        public SyncResult(int pushedRows, int pulledRows, String syncedAt) {
            this(pushedRows, pulledRows, syncedAt, 0, true, "");
        }

        public SyncResult(
                int pushedRows,
                int pulledRows,
                String syncedAt,
                int summaryPublishedRows,
                boolean summaryPublicationSucceeded,
                String summaryPublicationError
        ) {
            this.pushedRows = pushedRows;
            this.pulledRows = pulledRows;
            this.syncedAt = syncedAt == null ? OffsetDateTime.now().toString() : syncedAt;
            this.summaryPublishedRows = summaryPublishedRows;
            this.summaryPublicationSucceeded = summaryPublicationSucceeded;
            this.summaryPublicationError = summaryPublicationError == null
                    ? "" : summaryPublicationError;
        }
    }
}
