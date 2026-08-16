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
    public void keepsLocalMealSnapshotsOutOfSharedSyncUntilRemoteContractExists() {
        assertTrue(SupabaseSyncManager.TABLES.contains("meal_records"));
        assertFalse(SupabaseSyncManager.TABLES.contains("meal_record_items"));
        assertFalse(SupabaseSyncManager.TABLES.contains("meal_record_item_nutrients"));
        assertFalse(SupabaseSyncManager.TABLES.contains("meal_record_item_components"));
        assertFalse(SupabaseSyncManager.TABLES.contains("meal_record_item_component_nutrients"));
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

    @Test
    public void syncsDiningOutIdentityColumnsAfterSharedContractMigration() {
        assertTrue(SupabaseSyncManager.shouldSyncColumn("meal_records", "meal_kind"));
        assertTrue(SupabaseSyncManager.shouldSyncColumn("meal_records", "store_name"));
        assertTrue(SupabaseSyncManager.shouldSyncColumn("meal_records", "branch_name"));
        assertTrue(SupabaseSyncManager.shouldSyncColumn("meal_records", "menu_name"));
        assertTrue(SupabaseSyncManager.shouldSyncColumn("meal_records", "restaurant_id"));
        assertTrue(SupabaseSyncManager.shouldSyncColumn(
                "meal_records",
                "restaurant_location_id"
        ));
        assertTrue(SupabaseSyncManager.shouldSyncColumn(
                "meal_records",
                "restaurant_menu_id"
        ));
        assertTrue(SupabaseSyncManager.shouldSyncColumn(
                "meal_records",
                "catalog_product_id"
        ));
        assertTrue(SupabaseSyncManager.shouldSyncColumn("meal_records", "metadata"));
    }

    @Test
    public void keepsLocalContractMarkerOutOfSharedPayload() {
        assertFalse(SupabaseSyncManager.shouldSyncColumn("meal_records", "contract_version"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn("workout_records", "contract_version"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn("weight_records", "contract_version"));
    }
}
