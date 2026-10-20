package com.yeonsik.fitnessapp.integration.personalos;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutSummaryApi;
import java.util.function.Consumer;

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

    public SupabaseSyncManager(
            LegacyFitnessSyncAdapter legacyAdapter,
            WorkoutSummaryApi summaryStore,
            FitnessSummaryPublisher summaryPublisher
    ) {
        if (legacyAdapter == null || summaryStore == null
                || summaryPublisher == null) {
            throw new IllegalArgumentException("Sync dependencies are required.");
        }
        this.legacyAdapter = legacyAdapter;
        this.summaryStore = summaryStore;
        this.summaryPublisher = summaryPublisher;
    }

    public SyncResult manualSync(SupabaseConfig config) throws Exception {
        return manualSync(config, ignored -> {});
    }

    public SyncResult manualSync(SupabaseConfig config, Consumer<String> progress) throws Exception {
        progress.accept("운동·식단·체중 기록을 동기화하고 있습니다.");
        LegacyFitnessSyncAdapter.SyncResult legacy = legacyAdapter.manualSync(config);
        int published = 0;
        boolean summarySucceeded = true;
        String summaryError = "";
        try {
            progress.accept("완료 운동 요약의 변경분을 확인하고 있습니다.");
            summaryStore.reconcileSharedWorkoutSummaries(config.effectiveUserId());
            published = summaryPublisher.publish(config, summaryStore, (done, total) ->
                    progress.accept(total == 0 ? "완료 운동 요약의 변경이 없습니다."
                            : "완료 운동 요약을 전송하고 있습니다. (" + done + "/" + total + ")"));
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
    public int applyRows(String table, JSONArray rows, String userId) throws JSONException {
        return legacyAdapter.applyRows(table, rows, userId);
    }

    public static boolean isRpcUnavailable(int statusCode, String body) {
        return LegacyFitnessSyncAdapter.isRpcUnavailable(statusCode, body);
    }

    public static boolean shouldSyncColumn(String table, String column) {
        return LegacyFitnessSyncAdapter.shouldSyncColumn(table, column);
    }

    public static int compareVersions(String left, String right) {
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
