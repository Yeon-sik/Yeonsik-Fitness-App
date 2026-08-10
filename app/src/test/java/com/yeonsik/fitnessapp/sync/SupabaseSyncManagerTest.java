package com.yeonsik.fitnessapp.sync;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SupabaseSyncManagerTest {
    @Test
    public void comparesEquivalentInstantsAcrossOffsetsAsEqual() {
        assertEquals(0, SupabaseSyncManager.compareVersions(
                "2026-08-10T09:00:00+09:00",
                "2026-08-10T00:00:00Z"
        ));
    }

    @Test
    public void treatsMissingVersionAsOlder() {
        assertTrue(SupabaseSyncManager.compareVersions(null, "2026-08-10T00:00:00Z") < 0);
        assertTrue(SupabaseSyncManager.compareVersions("2026-08-10T00:00:00Z", null) > 0);
    }

    @Test
    public void ordersRemoteTimestampsByInstant() {
        assertTrue(SupabaseSyncManager.compareVersions(
                "2026-08-10T00:00:01Z",
                "2026-08-10T00:00:00Z"
        ) > 0);
    }

    @Test
    public void syncsMealSnapshotsAfterTheirParentRows() {
        int parent = SupabaseSyncManager.TABLES.indexOf("meal_records");
        int items = SupabaseSyncManager.TABLES.indexOf("meal_record_items");
        int nutrients = SupabaseSyncManager.TABLES.indexOf("meal_record_item_nutrients");

        assertTrue(parent >= 0);
        assertTrue(parent < items);
        assertTrue(items < nutrients);
    }

    @Test
    public void excludesLocalOnlyMenuSnapshotColumnsFromSharedSync() {
        assertFalse(SupabaseSyncManager.shouldSyncColumn(
                "meal_record_items",
                "brand_snapshot"
        ));
        assertTrue(SupabaseSyncManager.shouldSyncColumn(
                "meal_record_items",
                "food_name_snapshot"
        ));
    }
}
