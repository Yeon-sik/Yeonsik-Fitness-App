package com.yeonsik.fitnessapp.sync;

import java.util.List;

/**
 * Explicit local storage boundary for the legacy sync protocol.
 *
 * <p>The sync integration exchanges neutral row snapshots with the store. Room entity mapping,
 * optimistic local merge, and SyncState persistence stay behind this boundary.</p>
 */
public interface LegacyFitnessSyncStore {
    List<LegacySyncRow> rows(
            String table,
            String userId,
            String deviceId,
            LegacySyncCursor cursor,
            int limit
    );

    LegacySyncCursor cursor(String scopeKey, String table, String direction);

    void saveCursor(String scopeKey, String table, String direction, LegacySyncCursor cursor);

    boolean hasRow(String table, String id, String userId);

    String localVersion(String table, String id, String userId, String versionColumn);

    void applyRows(String table, List<LegacySyncRow> rows, String userId);
}
