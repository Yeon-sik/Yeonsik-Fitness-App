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
    public void keepsDiningOutIdentityColumnsOutOfSharedPayloadUntilMigrationIsVerified() {
        assertFalse(SupabaseSyncManager.shouldSyncColumn("meal_records", "meal_kind"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn("meal_records", "store_name"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn("meal_records", "branch_name"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn("meal_records", "menu_name"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn("meal_records", "restaurant_id"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn(
                "meal_records",
                "restaurant_location_id"
        ));
        assertFalse(SupabaseSyncManager.shouldSyncColumn(
                "meal_records",
                "restaurant_menu_id"
        ));
        assertFalse(SupabaseSyncManager.shouldSyncColumn(
                "meal_records",
                "catalog_product_id"
        ));
        assertTrue(SupabaseSyncManager.shouldSyncColumn("meal_records", "metadata"));
    }

    @Test
    public void keepsLocalContractMarkerOutOfSharedPayload() {
        assertFalse(SupabaseSyncManager.shouldSyncColumn("meal_records", "contract_version"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn(
                "meal_records",
                "composition_template_id"
        ));
        assertFalse(SupabaseSyncManager.shouldSyncColumn(
                "meal_records",
                "composition_template_revision"
        ));
        assertFalse(SupabaseSyncManager.shouldSyncColumn("workout_records", "contract_version"));
        assertFalse(SupabaseSyncManager.shouldSyncColumn("weight_records", "contract_version"));
    }

    @Test
    public void keepsUndeployedWorkoutAggregateOutOfSharedPayload() {
        assertFalse(SupabaseSyncManager.shouldSyncColumn(
                "workout_records",
                "total_volume_kg"
        ));
        assertFalse(SupabaseSyncManager.shouldSyncColumn(
                "workout_sets",
                "volume_kg"
        ));
    }
    @Test
    public void fallsBackOnlyWhenSyncRpcIsMissing() {
        assertTrue(SupabaseSyncManager.isRpcUnavailable(
                404,
                "{\"code\":\"PGRST202\",\"message\":\"sync_fitness_data_v1 missing\"}"
        ));
        assertFalse(SupabaseSyncManager.isRpcUnavailable(
                500,
                "{\"code\":\"PGRST202\",\"message\":\"server failure\"}"
        ));
        assertFalse(SupabaseSyncManager.isRpcUnavailable(
                404,
                "{\"code\":\"PGRST204\",\"message\":\"column missing\"}"
        ));
        assertFalse(SupabaseSyncManager.isRpcUnavailable(
                404,
                "{\"code\":\"PGRST301\",\"message\":\"sync_fitness_data_v1 denied\"}"
        ));
    }


}
