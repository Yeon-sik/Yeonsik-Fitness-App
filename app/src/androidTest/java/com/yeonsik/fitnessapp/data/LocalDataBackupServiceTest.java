package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class LocalDataBackupServiceTest {
    private static final String SOURCE_RECORD_USER = "source-record-user";
    private static final String SOURCE_NUTRITION_USER = "source-nutrition-user";
    private static final String TARGET_RECORD_USER = "target-record-user";
    private static final String TARGET_NUTRITION_USER = "target-nutrition-user";
    private static final String OTHER_NUTRITION_USER = "other-nutrition-user";

    @Test
    public void roundTripExportsPublicNutritionClosureAndSecondRestoreSkipsDuplicates()
            throws Exception {
        TestDatabase source = openDatabase("local_backup_source_");
        TestDatabase target = openDatabase("local_backup_target_");
        try {
            seedSource(source);
            LocalDataBackupService exporter = new LocalDataBackupService(
                    source.helper,
                    SOURCE_RECORD_USER,
                    SOURCE_NUTRITION_USER
            );

            ByteArrayOutputStream backupOutput = new ByteArrayOutputStream();
            LocalDataBackupService.BackupPreview exported = exporter.writeBackup(backupOutput);
            byte[] payload = backupOutput.toByteArray();

            JSONObject backup = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            JSONObject tables = backup.getJSONObject("tables");

            assertEquals(countRows(tables), exported.getTotalRows());
            assertEquals(3, tables.getJSONArray("nutrition_foods").length());
            assertEquals(2, tables.getJSONArray("nutrition_food_nutrients").length());
            assertEquals(2, tables.getJSONArray("nutrition_food_components").length());
            assertEquals(1, tables.getJSONArray("meal_record_item_components").length());
            assertEquals(
                    1,
                    tables.getJSONArray("meal_record_item_component_nutrients").length()
            );
            assertEquals(1, tables.getJSONArray("body_profiles").length());
            assertEquals(1, tables.getJSONArray("development_goals").length());
            assertFalse(tables.getJSONArray("body_profiles").getJSONObject(0).has("user_id"));
            assertFalse(tables.getJSONArray("development_goals").getJSONObject(0).has("user_id"));
            assertEquals(
                    "2026-08-10T12:00:00+09:00",
                    new JSONObject(tables.getJSONArray("meal_records").getJSONObject(0)
                            .getString("metadata")).getString("eaten_at")
            );
            assertFalse(tables.has("devices"));
            assertFalse(tables.has("pricetrace_product_cache"));
            assertOwnerScope(tables.getJSONArray("nutrition_foods"), "owned-recipe", "current");
            assertOwnerScope(tables.getJSONArray("nutrition_foods"), "public-food", "public");
            assertOwnerScope(
                    tables.getJSONArray("nutrition_food_components"),
                    "owned-component-public",
                    "current"
            );
            assertOwnerScope(
                    tables.getJSONArray("nutrition_food_components"),
                    "public-component-nested",
                    "public"
            );

            String rawBackup = backup.toString();
            assertFalse(rawBackup.contains(SOURCE_RECORD_USER));
            assertFalse(rawBackup.contains(SOURCE_NUTRITION_USER));
            assertFalse(rawBackup.contains("https://secret.example"));
            assertFalse(rawBackup.contains("secret-token"));
            assertFalse(rawBackup.contains("secret@example.com"));
            assertFalse(rawBackup.contains("cache product"));
            assertFalse(rawBackup.contains("other-owner-food"));
            assertFalse(rawBackup.contains("ignored-public-food"));

            LocalDataBackupService restorer = new LocalDataBackupService(
                    target.helper,
                    TARGET_RECORD_USER,
                    TARGET_NUTRITION_USER
            );
            LocalDataBackupService.BackupPreview preview = restorer.previewBackup(
                    new ByteArrayInputStream(payload)
            );
            assertEquals(exported.getTotalRows(), preview.getTotalRows());
            assertEquals(exported.getDatabaseVersion(), preview.getDatabaseVersion());

            LocalDataBackupService.RestoreResult first = restorer.restoreBackup(
                    new ByteArrayInputStream(payload)
            );
            assertEquals(exported.getTotalRows(), first.getImportedRows());
            assertEquals(0, first.getSkippedRows());

            assertEquals(TARGET_RECORD_USER, scalar(
                    target.database,
                    "SELECT user_id FROM workout_records WHERE id = 'workout-1'"
            ));
            assertEquals(TARGET_RECORD_USER, scalar(
                    target.database,
                    "SELECT user_id FROM meal_record_item_components " +
                            "WHERE id = 'meal-component-1'"
            ));
            assertEquals("Rice", scalar(
                    target.database,
                    "SELECT food_name_snapshot FROM meal_record_item_components " +
                            "WHERE id = 'meal-component-1'"
            ));
            assertEquals(
                    "2026-08-10T12:00:00+09:00",
                    new JSONObject(scalar(
                            target.database,
                            "SELECT metadata FROM meal_records WHERE id = 'meal-1'"
                    )).getString("eaten_at")
            );
            assertEquals(TARGET_NUTRITION_USER, scalar(
                    target.database,
                    "SELECT owner_id FROM nutrition_foods WHERE id = 'owned-recipe'"
            ));
            assertNull(nullableScalar(
                    target.database,
                    "SELECT owner_id FROM nutrition_foods WHERE id = 'public-food'"
            ));
            assertEquals("1", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM nutrition_foods WHERE id = 'public-nested-food' AND owner_id IS NULL"
            ));
            assertEquals("1", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM nutrition_food_nutrients " +
                            "WHERE food_id = 'public-food' AND owner_id IS NULL"
            ));
            assertEquals("1", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM nutrition_food_components c " +
                            "JOIN nutrition_foods f ON f.id = c.child_food_id " +
                            "WHERE c.id = 'owned-component-public' " +
                            "AND c.owner_id = '" + TARGET_NUTRITION_USER + "' " +
                            "AND f.id = 'public-food' AND f.owner_id IS NULL"
            ));
            assertEquals("1", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM nutrition_food_components c " +
                            "JOIN nutrition_foods parent ON parent.id = c.parent_food_id " +
                            "JOIN nutrition_foods child ON child.id = c.child_food_id " +
                            "WHERE c.id = 'public-component-nested' " +
                            "AND c.owner_id IS NULL " +
                            "AND parent.id = 'public-food' AND parent.owner_id IS NULL " +
                            "AND child.id = 'public-nested-food' AND child.owner_id IS NULL"
            ));
            assertEquals(TARGET_RECORD_USER, scalar(
                    target.database,
                    "SELECT user_id FROM body_profiles"
            ));
            assertEquals("181", scalar(
                    target.database,
                    "SELECT height_cm FROM body_profiles WHERE user_id = '" +
                            TARGET_RECORD_USER + "'"
            ));
            assertEquals(TARGET_RECORD_USER, scalar(
                    target.database,
                    "SELECT user_id FROM development_goals"
            ));
            assertEquals("muscle_gain", scalar(
                    target.database,
                    "SELECT objective FROM development_goals WHERE user_id = '" +
                            TARGET_RECORD_USER + "'"
            ));
            assertEquals("4", scalar(
                    target.database,
                    "SELECT weekly_sessions_target FROM development_goals WHERE user_id = '" +
                            TARGET_RECORD_USER + "'"
            ));

            LocalDataBackupService.RestoreResult second = restorer.restoreBackup(
                    new ByteArrayInputStream(payload)
            );
            assertEquals(0, second.getImportedRows());
            assertEquals(exported.getTotalRows(), second.getSkippedRows());
        } finally {
            source.closeAndDelete();
            target.closeAndDelete();
        }
    }

    @Test
    public void csvUsesFixedKoreanHeaderAndExcludesMetadataAndOwnerValues() throws Exception {
        TestDatabase source = openDatabase("local_backup_csv_");
        try {
            seedSource(source);
            LocalDataBackupService service = new LocalDataBackupService(
                    source.helper,
                    SOURCE_RECORD_USER,
                    SOURCE_NUTRITION_USER
            );

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            service.writeRecordsSummaryCsv(output);
            byte[] bytes = output.toByteArray();
            assertTrue(bytes.length > 3);
            assertEquals(0xEF, bytes[0] & 0xFF);
            assertEquals(0xBB, bytes[1] & 0xFF);
            assertEquals(0xBF, bytes[2] & 0xFF);

            String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
            assertTrue(csv.startsWith(
                    "\uAE30\uB85D \uC720\uD615,\uB0A0\uC9DC,\uC774\uB984,\uBD84\uB958"
            ));
            assertTrue(csv.contains("\uC6B4\uB3D9,2026-08-10,Bench Press"));
            assertTrue(csv.contains("\uC2DD\uB2E8,2026-08-10,Chicken Bowl"));
            assertTrue(csv.contains("\uCCB4\uC911,2026-08-10"));
            assertFalse(csv.contains("private-workout-metadata"));
            assertFalse(csv.contains("private-meal-metadata"));
            assertFalse(csv.contains("private-weight-metadata"));
            assertFalse(csv.contains(SOURCE_RECORD_USER));
            assertFalse(csv.contains(SOURCE_NUTRITION_USER));
        } finally {
            source.closeAndDelete();
        }
    }

    @Test
    public void corruptedAndFutureBackupsAreRejectedAtomically() throws Exception {
        TestDatabase source = openDatabase("local_backup_invalid_source_");
        TestDatabase target = openDatabase("local_backup_invalid_target_");
        try {
            seedSource(source);
            seedTargetBaseline(target.database);

            LocalDataBackupService exporter = new LocalDataBackupService(
                    source.helper,
                    SOURCE_RECORD_USER,
                    SOURCE_NUTRITION_USER
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            exporter.writeBackup(output);
            byte[] validPayload = output.toByteArray();

            LocalDataBackupService restorer = new LocalDataBackupService(
                    target.helper,
                    TARGET_RECORD_USER,
                    TARGET_NUTRITION_USER
            );

            expectRejected(restorer, "{".getBytes(StandardCharsets.UTF_8));
            assertBaselineIntact(target.database);

            JSONObject future = new JSONObject(new String(validPayload, StandardCharsets.UTF_8));
            future.put("formatVersion", 2);
            expectRejected(restorer, future.toString().getBytes(StandardCharsets.UTF_8));
            assertBaselineIntact(target.database);
        } finally {
            source.closeAndDelete();
            target.closeAndDelete();
        }
    }

    @Test
    public void versionFourteenBackupWithoutHierarchyTablesRemainsReadable() throws Exception {
        TestDatabase source = openDatabase("local_backup_legacy_source_");
        TestDatabase target = openDatabase("local_backup_legacy_target_");
        try {
            seedSource(source);
            LocalDataBackupService exporter = new LocalDataBackupService(
                    source.helper,
                    SOURCE_RECORD_USER,
                    SOURCE_NUTRITION_USER
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            exporter.writeBackup(output);
            JSONObject legacy = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            legacy.put("databaseVersion", 14);
            JSONObject tables = legacy.getJSONObject("tables");
            tables.remove("meal_record_item_components");
            tables.remove("meal_record_item_component_nutrients");
            tables.remove("meal_record_item_consumptions");
            tables.remove("composition_templates");
            tables.remove("composition_groups");
            tables.remove("composition_members");
            tables.remove("supplement_items");
            tables.remove("supplement_schedules");
            tables.remove("supplement_schedule_slots");
            tables.remove("supplement_intake_records");
            tables.remove("supplement_effect_checkins");
            tables.remove("body_profiles");
            tables.remove("development_goals");
            JSONArray mealItems = tables.getJSONArray("meal_record_items");
            for (int index = 0; index < mealItems.length(); index++) {
                mealItems.getJSONObject(index).remove("brand_snapshot");
            }

            LocalDataBackupService restorer = new LocalDataBackupService(
                    target.helper,
                    TARGET_RECORD_USER,
                    TARGET_NUTRITION_USER
            );
            LocalDataBackupService.BackupPreview preview = restorer.previewBackup(
                    new ByteArrayInputStream(legacy.toString().getBytes(StandardCharsets.UTF_8))
            );
            assertEquals(14, preview.getDatabaseVersion());
            LocalDataBackupService.RestoreResult result = restorer.restoreBackup(
                    new ByteArrayInputStream(legacy.toString().getBytes(StandardCharsets.UTF_8))
            );
            assertTrue(result.getImportedRows() > 0);
            assertEquals("1", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM meal_record_items WHERE id = 'meal-menu-1'"
            ));
        } finally {
            source.closeAndDelete();
            target.closeAndDelete();
        }
    }

    @Test
    public void restoreRetiresLegacyCookedOfficialFoodButKeepsHistoricalMealSnapshot()
            throws Exception {
        TestDatabase source = openDatabase("local_backup_raw_policy_source_");
        TestDatabase target = openDatabase("local_backup_raw_policy_target_");
        try {
            seedSource(source);
            seedLegacyCookedCatalogUsage(source.database);

            LocalDataBackupService exporter = new LocalDataBackupService(
                    source.helper,
                    SOURCE_RECORD_USER,
                    SOURCE_NUTRITION_USER
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            exporter.writeBackup(output);

            LocalDataBackupService restorer = new LocalDataBackupService(
                    target.helper,
                    TARGET_RECORD_USER,
                    TARGET_NUTRITION_USER
            );
            restorer.restoreBackup(new ByteArrayInputStream(output.toByteArray()));

            assertEquals("1", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM nutrition_foods "
                            + "WHERE id = 'kfind:R209-008000551-0000' "
                            + "AND deleted_at IS NOT NULL"
            ));
            assertEquals("106|22.97|raw", scalar(
                    target.database,
                    "SELECT printf('%.0f|%.2f|%s', calories_kcal, protein_grams, "
                            + "cooking_method) FROM nutrition_foods "
                            + "WHERE id = 'kfind:R209-008000501-0000'"
            ));
            assertEquals("R209-008000551-0000|164|35.47|cooked|legacy-v1", scalar(
                    target.database,
                    "SELECT printf('%s|%.0f|%.2f|%s|%s', "
                            + "substr(food_id, 7), calories, protein_grams, "
                            + "prep_state_snapshot, source_version_snapshot) "
                            + "FROM meal_record_items WHERE id = 'legacy-meal-item'"
            ));
        } finally {
            source.closeAndDelete();
            target.closeAndDelete();
        }
    }

    @Test
    public void versionFifteenBackupWithoutDevelopmentTablesRemainsReadableButUnknownTablesFail()
            throws Exception {
        TestDatabase source = openDatabase("local_backup_v15_source_");
        TestDatabase target = openDatabase("local_backup_v15_target_");
        try {
            seedSource(source);
            LocalDataBackupService exporter = new LocalDataBackupService(
                    source.helper,
                    SOURCE_RECORD_USER,
                    SOURCE_NUTRITION_USER
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            exporter.writeBackup(output);

            JSONObject legacy = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            legacy.put("databaseVersion", 15);
            JSONObject tables = legacy.getJSONObject("tables");
            tables.remove("meal_record_item_consumptions");
            tables.remove("composition_templates");
            tables.remove("composition_groups");
            tables.remove("composition_members");
            tables.remove("supplement_items");
            tables.remove("supplement_schedules");
            tables.remove("supplement_schedule_slots");
            tables.remove("supplement_intake_records");
            tables.remove("supplement_effect_checkins");
            tables.remove("body_profiles");
            tables.remove("development_goals");

            LocalDataBackupService restorer = new LocalDataBackupService(
                    target.helper,
                    TARGET_RECORD_USER,
                    TARGET_NUTRITION_USER
            );
            JSONObject unknownTableBackup = new JSONObject(legacy.toString());
            unknownTableBackup.getJSONObject("tables").put("future_table", new JSONArray());
            expectRejected(
                    restorer,
                    unknownTableBackup.toString().getBytes(StandardCharsets.UTF_8)
            );

            LocalDataBackupService.BackupPreview preview = restorer.previewBackup(
                    new ByteArrayInputStream(legacy.toString().getBytes(StandardCharsets.UTF_8))
            );
            assertEquals(15, preview.getDatabaseVersion());
            LocalDataBackupService.RestoreResult result = restorer.restoreBackup(
                    new ByteArrayInputStream(legacy.toString().getBytes(StandardCharsets.UTF_8))
            );
            assertTrue(result.getImportedRows() > 0);
            assertEquals("0", scalar(target.database, "SELECT COUNT(*) FROM body_profiles"));
            assertEquals("0", scalar(target.database, "SELECT COUNT(*) FROM development_goals"));
        } finally {
            source.closeAndDelete();
            target.closeAndDelete();
        }
    }

    @Test
    public void tamperedPublicScopePayloadIsRejectedBeforeRestoreStarts() throws Exception {
        TestDatabase source = openDatabase("local_backup_public_scope_source_");
        TestDatabase target = openDatabase("local_backup_public_scope_target_");
        try {
            seedSource(source);
            seedTargetBaseline(target.database);

            LocalDataBackupService exporter = new LocalDataBackupService(
                    source.helper,
                    SOURCE_RECORD_USER,
                    SOURCE_NUTRITION_USER
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            exporter.writeBackup(output);
            JSONObject tampered = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));

            JSONArray foods = tampered.getJSONObject("tables").getJSONArray("nutrition_foods");
            for (int i = 0; i < foods.length(); i++) {
                JSONObject row = foods.getJSONObject(i);
                if ("public-food".equals(row.optString("id"))) {
                    row.put("visibility", "private");
                }
                if ("public-nested-food".equals(row.optString("id"))) {
                    foods.remove(i);
                    i--;
                }
            }

            LocalDataBackupService restorer = new LocalDataBackupService(
                    target.helper,
                    TARGET_RECORD_USER,
                    TARGET_NUTRITION_USER
            );
            expectRejected(restorer, tampered.toString().getBytes(StandardCharsets.UTF_8));
            assertBaselineIntact(target.database);
        } finally {
            source.closeAndDelete();
            target.closeAndDelete();
        }
    }

    @Test
    public void runtimeInsertFailureRollsBackEarlierRows() throws Exception {
        TestDatabase source = openDatabase("local_backup_runtime_source_");
        TestDatabase target = openDatabase("local_backup_runtime_target_");
        try {
            seedSource(source);
            seedTargetBaseline(target.database);

            LocalDataBackupService exporter = new LocalDataBackupService(
                    source.helper,
                    SOURCE_RECORD_USER,
                    SOURCE_NUTRITION_USER
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            exporter.writeBackup(output);
            JSONObject payload = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));

            JSONObject tables = payload.getJSONObject("tables");
            tables.getJSONArray("cardio_sessions").put(new JSONObject()
                    .put("record_id", "runtime-cardio")
                    .put("activity_type", "running")
                    .put("status", "paused")
                    .put("started_at_epoch_ms", 1L)
                    .put("last_resumed_at_epoch_ms", JSONObject.NULL)
                    .put("active_duration_ms", 0L)
                    .put("distance_meters", 0.0)
                    .put("accepted_point_count", 0L)
                    .put("last_latitude", JSONObject.NULL)
                    .put("last_longitude", JSONObject.NULL)
                    .put("last_location_time_ms", JSONObject.NULL)
                    .put("last_accuracy_meters", JSONObject.NULL)
                    .put("gps_status", "searching")
                    .put("updated_at_epoch_ms", 1L));
            tables.getJSONArray("cardio_route_points").put(new JSONObject()
                    .put("record_id", "runtime-cardio")
                    .put("captured_at_epoch_ms", 1L)
                    .put("latitude", 37.0)
                    .put("longitude", 127.0)
                    .put("accuracy_meters", 5.0)
                    .put("speed_mps", JSONObject.NULL)
                    .put("segment_distance_meters", JSONObject.NULL));

            LocalDataBackupService restorer = new LocalDataBackupService(
                    target.helper,
                    TARGET_RECORD_USER,
                    TARGET_NUTRITION_USER
            );
            expectRuntimeFailure(restorer, payload.toString().getBytes(StandardCharsets.UTF_8));
            assertBaselineIntact(target.database);
            assertEquals("0", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM workout_records WHERE id = 'workout-1'"
            ));
            assertEquals("0", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM nutrition_foods WHERE id = 'owned-recipe'"
            ));
            assertEquals("0", scalar(
                    target.database,
                    "SELECT COUNT(*) FROM cardio_sessions WHERE record_id = 'runtime-cardio'"
            ));
        } finally {
            source.closeAndDelete();
            target.closeAndDelete();
        }
    }

    private static void seedSource(TestDatabase database) {
        SQLiteDatabase db = database.database;
        SharedPreferences preferences = database.context.getSharedPreferences("backup-test", Context.MODE_PRIVATE);
        preferences.edit()
                .putString("url", "https://secret.example")
                .putString("token", "secret-token")
                .putString("email", "secret@example.com")
                .putString("user_id", SOURCE_RECORD_USER)
                .commit();

        insertWorkoutRecord(db, "workout-1", SOURCE_RECORD_USER, "Bench Press", "private-workout-metadata");
        insertMealRecord(db, "meal-1", SOURCE_RECORD_USER, "Chicken Bowl", "private-meal-metadata");
        insertMealHierarchy(db);
        insertWeightRecord(db, "weight-1", SOURCE_RECORD_USER, 78.4, "private-weight-metadata");
        insertDevelopmentData(db);

        insertNutritionFood(db, "owned-recipe", SOURCE_NUTRITION_USER, "Owned Recipe", "recipe");
        insertNutritionFood(db, "public-food", null, "Public Food", "ingredient");
        insertNutritionFood(db, "public-nested-food", null, "Public Nested Food", "ingredient");
        insertNutritionFood(db, "other-owner-food", OTHER_NUTRITION_USER, "other-owner-food", "ingredient");
        insertNutritionFood(db, "ignored-public-food", null, "ignored-public-food", "ingredient");

        insertNutritionNutrient(db, "public-nutrient-1", null, "public-food", "sodium", 120.0);
        insertNutritionNutrient(db, "public-nutrient-2", null, "public-nested-food", "fiber", 4.0);
        insertNutritionNutrient(db, "other-nutrient", OTHER_NUTRITION_USER, "other-owner-food", "sugars", 9.0);

        insertNutritionComponent(
                db,
                "owned-component-public",
                SOURCE_NUTRITION_USER,
                "owned-recipe",
                "public-food",
                1.0,
                0
        );
        insertNutritionComponent(
                db,
                "public-component-nested",
                null,
                "public-food",
                "public-nested-food",
                1.0,
                0
        );

        insertDevice(db, SOURCE_RECORD_USER, "device-1");
        insertPriceTraceCache(db, "cache product");
    }

    private static void seedLegacyCookedCatalogUsage(SQLiteDatabase db) {
        String legacyFoodId = "kfind:R209-008000551-0000";
        String legacyReference = VerifiedFoodCatalogSeed.SOURCE_REFERENCE_PREFIX
                + "R209-008000551-0000&searchMonthCd=AVG&searchRegionCd=ZZ";

        insertNutritionFood(
                db,
                legacyFoodId,
                null,
                "닭가슴살(껍질 제거, 구이)",
                NutritionFood.KIND_INGREDIENT
        );
        ContentValues official = new ContentValues();
        official.put("category", NutritionFood.CATEGORY_POULTRY);
        official.put("prep_state", NutritionFood.PREP_COOKED);
        official.put("cooking_method", NutritionFood.COOKING_METHOD_GRILLED);
        official.put("calories_kcal", 164.0);
        official.put("protein_grams", 35.47);
        official.put("carbs_grams", 0.0);
        official.put("fat_grams", 2.28);
        official.put("source_type", VerifiedFoodCatalogSeed.SOURCE_TYPE);
        official.put("source_reference", legacyReference);
        official.put("source_version", "legacy-v1");
        official.put("data_version", NutritionFood.DATA_VERSION_REQUIRED_SEVEN);
        db.update("nutrition_foods", official, "id = ?", new String[]{legacyFoodId});

        insertNutritionFood(
                db,
                "legacy-owned-recipe",
                SOURCE_NUTRITION_USER,
                "Legacy meal recipe",
                NutritionFood.KIND_RECIPE
        );
        insertNutritionComponent(
                db,
                "legacy-owned-component",
                SOURCE_NUTRITION_USER,
                "legacy-owned-recipe",
                legacyFoodId,
                100.0,
                0
        );

        insertMealRecord(
                db,
                "legacy-meal",
                SOURCE_RECORD_USER,
                "Legacy chicken",
                "legacy-raw-policy"
        );
        ContentValues item = new ContentValues();
        item.put("id", "legacy-meal-item");
        item.put("user_id", SOURCE_RECORD_USER);
        item.put("meal_record_id", "legacy-meal");
        item.put("food_id", legacyFoodId);
        item.put("food_name_snapshot", "닭가슴살(껍질 제거, 구이)");
        item.put("food_kind_snapshot", NutritionFood.KIND_INGREDIENT);
        item.put("quantity", 100.0);
        item.put("unit", NutritionUnit.GRAM);
        item.put("basis_amount_snapshot", 100.0);
        item.put("basis_unit_snapshot", NutritionUnit.GRAM);
        item.put("prep_state_snapshot", NutritionFood.PREP_COOKED);
        item.put("calories", 164.0);
        item.put("protein_grams", 35.47);
        item.put("carbs_grams", 0.0);
        item.put("fat_grams", 2.28);
        item.put("source_type_snapshot", VerifiedFoodCatalogSeed.SOURCE_TYPE);
        item.put("source_reference_snapshot", legacyReference);
        item.put("source_version_snapshot", "legacy-v1");
        item.put("food_data_version_snapshot", NutritionFood.DATA_VERSION_REQUIRED_SEVEN);
        item.put("order_index", 0);
        item.put("created_at", "2026-08-10T12:00:00Z");
        item.put("updated_at", "2026-08-10T12:00:00Z");
        item.putNull("deleted_at");
        item.put("device_id", "device-1");
        db.insertOrThrow("meal_record_items", null, item);
    }

    private static void seedTargetBaseline(SQLiteDatabase db) {
        insertWorkoutRecord(db, "baseline-workout", TARGET_RECORD_USER, "Baseline", "baseline-metadata");
    }

    private static void assertBaselineIntact(SQLiteDatabase db) {
        assertEquals("1", scalar(
                db,
                "SELECT COUNT(*) FROM workout_records WHERE id = 'baseline-workout'"
        ));
        assertEquals("0", scalar(
                db,
                "SELECT COUNT(*) FROM nutrition_foods WHERE id = 'public-food'"
        ));
        assertEquals("0", scalar(
                db,
                "SELECT COUNT(*) FROM nutrition_food_components WHERE id = 'owned-component-public'"
        ));
    }

    private static void expectRejected(LocalDataBackupService service, byte[] payload) throws Exception {
        try {
            service.restoreBackup(new ByteArrayInputStream(payload));
            fail("Expected backup to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    private static void expectRuntimeFailure(LocalDataBackupService service, byte[] payload)
            throws Exception {
        try {
            service.restoreBackup(new ByteArrayInputStream(payload));
            fail("Expected restore to fail inside the insert loop");
        } catch (IllegalArgumentException parseFailure) {
            fail("Expected runtime insert failure, but validation rejected early: "
                    + parseFailure.getMessage());
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage() == null || !expected.getMessage().isEmpty());
        }
    }

    private static int countRows(JSONObject tables) throws Exception {
        int total = 0;
        for (java.util.Iterator<String> iterator = tables.keys(); iterator.hasNext(); ) {
            total += tables.getJSONArray(iterator.next()).length();
        }
        return total;
    }

    private static void assertOwnerScope(JSONArray rows, String id, String expectedScope) throws Exception {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (id.equals(row.optString("id"))) {
                assertEquals(expectedScope, row.getString("_ownerScope"));
                return;
            }
        }
        fail("Missing row " + id);
    }

    private static void insertWorkoutRecord(
            SQLiteDatabase db,
            String id,
            String userId,
            String exerciseName,
            String metadataTag
    ) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("user_id", userId);
        values.put("date", "2026-08-10");
        values.put("workout_type", "strength");
        values.put("category", "chest");
        values.put("exercise_name", exerciseName);
        values.put("duration_seconds", 1800);
        values.put("total_volume_kg", 2400.0);
        values.put("average_heart_rate", 118.0);
        values.put("created_at", "2026-08-10T10:00:00Z");
        values.put("is_backfilled", 0);
        values.putNull("backfilled_at");
        values.putNull("backfill_reason");
        values.put("updated_at", "2026-08-10T10:30:00Z");
        values.putNull("deleted_at");
        values.put("device_id", "device-1");
        values.put("source_app", "fitness");
        values.put("scope", "both");
        values.put("metadata", "{\"tag\":\"" + metadataTag + "\"}");
        values.put("contract_version", 1);
        db.insertOrThrow("workout_records", null, values);
    }

    private static void insertMealRecord(
            SQLiteDatabase db,
            String id,
            String userId,
            String menu,
            String metadataTag
    ) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("user_id", userId);
        values.put("date", "2026-08-10");
        values.put("menu", menu);
        values.put("calories", 540);
        values.put("protein_grams", 42.0);
        values.put("carbs_grams", 38.0);
        values.put("fat_grams", 16.0);
        values.put("created_at", "2026-08-10T12:00:00Z");
        values.put("is_backfilled", 0);
        values.putNull("backfilled_at");
        values.putNull("backfill_reason");
        values.put("updated_at", "2026-08-10T12:00:00Z");
        values.putNull("deleted_at");
        values.put("device_id", "device-1");
        values.put("source_app", "fitness");
        values.put("scope", "both");
        values.put("metadata", "{\"tag\":\"" + metadataTag
                + "\",\"eaten_at\":\"2026-08-10T12:00:00+09:00\"}");
        values.put("contract_version", 1);
        db.insertOrThrow("meal_records", null, values);
    }

    private static void insertMealHierarchy(SQLiteDatabase db) {
        ContentValues menu = new ContentValues();
        menu.put("id", "meal-menu-1");
        menu.put("user_id", SOURCE_RECORD_USER);
        menu.put("meal_record_id", "meal-1");
        menu.put("food_name_snapshot", "Fried Rice");
        menu.put("brand_snapshot", "Home");
        menu.put("food_kind_snapshot", NutritionFood.KIND_RECIPE);
        menu.put("quantity", 1.0);
        menu.put("unit", NutritionUnit.SERVING);
        menu.put("order_index", 0);
        menu.put("created_at", "2026-08-10T12:00:00Z");
        menu.put("updated_at", "2026-08-10T12:00:00Z");
        menu.putNull("deleted_at");
        menu.put("device_id", "device-1");
        db.insertOrThrow("meal_record_items", null, menu);

        ContentValues component = new ContentValues();
        component.put("id", "meal-component-1");
        component.put("user_id", SOURCE_RECORD_USER);
        component.put("meal_record_id", "meal-1");
        component.put("meal_record_item_id", "meal-menu-1");
        component.put("food_name_snapshot", "Rice");
        component.put("food_kind_snapshot", NutritionFood.KIND_INGREDIENT);
        component.put("quantity", 200.0);
        component.put("unit", NutritionUnit.GRAM);
        component.put("order_index", 0);
        component.put("created_at", "2026-08-10T12:00:00Z");
        component.put("updated_at", "2026-08-10T12:00:00Z");
        component.putNull("deleted_at");
        component.put("device_id", "device-1");
        db.insertOrThrow("meal_record_item_components", null, component);

        ContentValues nutrient = new ContentValues();
        nutrient.put("id", "meal-component-nutrient-1");
        nutrient.put("user_id", SOURCE_RECORD_USER);
        nutrient.put("meal_record_id", "meal-1");
        nutrient.put("meal_record_item_id", "meal-menu-1");
        nutrient.put("meal_record_item_component_id", "meal-component-1");
        nutrient.put("nutrient_code", "vitamin_c_mg");
        nutrient.put("amount", 1.5);
        nutrient.put("unit", "mg");
        nutrient.put("created_at", "2026-08-10T12:00:00Z");
        nutrient.put("updated_at", "2026-08-10T12:00:00Z");
        nutrient.putNull("deleted_at");
        nutrient.put("device_id", "device-1");
        db.insertOrThrow("meal_record_item_component_nutrients", null, nutrient);
    }

    private static void insertWeightRecord(
            SQLiteDatabase db,
            String id,
            String userId,
            double weightKg,
            String metadataTag
    ) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("user_id", userId);
        values.put("date", "2026-08-10");
        values.put("weight_kg", weightKg);
        values.put("created_at", "2026-08-10T07:00:00Z");
        values.put("is_backfilled", 0);
        values.putNull("backfilled_at");
        values.putNull("backfill_reason");
        values.put("updated_at", "2026-08-10T07:00:00Z");
        values.putNull("deleted_at");
        values.put("device_id", "device-1");
        values.put("source_app", "fitness");
        values.put("scope", "both");
        values.put("metadata", "{\"tag\":\"" + metadataTag + "\"}");
        values.put("contract_version", 1);
        db.insertOrThrow("weight_records", null, values);
    }

    private static void insertDevelopmentData(SQLiteDatabase db) {
        ContentValues profile = new ContentValues();
        profile.put("user_id", SOURCE_RECORD_USER);
        profile.put("height_cm", 181);
        profile.put("created_at", "2026-08-10T00:00:00Z");
        profile.put("updated_at", "2026-08-10T00:00:00Z");
        db.insertOrThrow("body_profiles", null, profile);

        ContentValues goal = new ContentValues();
        goal.put("user_id", SOURCE_RECORD_USER);
        goal.put("objective", "muscle_gain");
        goal.put("weekly_sessions_target", 4);
        goal.put("focus_body_part", "chest");
        goal.put("effective_from", "2026-08-10");
        goal.put("created_at", "2026-08-10T00:00:00Z");
        goal.put("updated_at", "2026-08-10T00:00:00Z");
        db.insertOrThrow("development_goals", null, goal);
    }

    private static void insertNutritionFood(
            SQLiteDatabase db,
            String id,
            String ownerId,
            String name,
            String kind
    ) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        if (ownerId == null) {
            values.putNull("owner_id");
        } else {
            values.put("owner_id", ownerId);
        }
        values.put("name", name);
        values.putNull("brand");
        values.put("kind", kind);
        values.put("category", "other");
        values.put("basis_amount", 100.0);
        values.put("basis_unit", "g");
        values.put("prep_state", "unspecified");
        values.put("cooking_method", "unspecified");
        values.put("calories_kcal", 120.0);
        values.put("protein_grams", 5.0);
        values.put("carbs_grams", 20.0);
        values.put("fat_grams", 3.0);
        values.putNull("sodium_mg");
        values.putNull("saturated_fat_grams");
        values.putNull("sugars_grams");
        values.putNull("fiber_grams");
        values.putNull("added_sugars_grams");
        values.putNull("trans_fat_grams");
        values.putNull("cholesterol_mg");
        values.put("source_type", ownerId == null ? "synced" : "manual");
        values.putNull("source_reference");
        values.putNull("source_version");
        values.put("data_version", 1);
        values.put("revision", 1);
        values.put("visibility", ownerId == null ? "public" : "private");
        values.put("created_at", "2026-08-10T00:00:00Z");
        values.put("updated_at", "2026-08-10T00:00:00Z");
        values.putNull("deleted_at");
        db.insertOrThrow("nutrition_foods", null, values);
    }

    private static void insertNutritionNutrient(
            SQLiteDatabase db,
            String id,
            String ownerId,
            String foodId,
            String nutrientCode,
            double amount
    ) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        if (ownerId == null) {
            values.putNull("owner_id");
        } else {
            values.put("owner_id", ownerId);
        }
        values.put("food_id", foodId);
        values.put("nutrient_code", nutrientCode);
        values.put("amount", amount);
        values.put("unit", "g");
        values.put("created_at", "2026-08-10T00:00:00Z");
        values.put("updated_at", "2026-08-10T00:00:00Z");
        values.putNull("deleted_at");
        db.insertOrThrow("nutrition_food_nutrients", null, values);
    }

    private static void insertNutritionComponent(
            SQLiteDatabase db,
            String id,
            String ownerId,
            String parentFoodId,
            String childFoodId,
            double quantity,
            int orderIndex
    ) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        if (ownerId == null) {
            values.putNull("owner_id");
        } else {
            values.put("owner_id", ownerId);
        }
        values.put("parent_food_id", parentFoodId);
        values.put("child_food_id", childFoodId);
        values.put("quantity", quantity);
        values.put("unit", "serving");
        values.put("order_index", orderIndex);
        values.put("created_at", "2026-08-10T00:00:00Z");
        values.put("updated_at", "2026-08-10T00:00:00Z");
        values.putNull("deleted_at");
        db.insertOrThrow("nutrition_food_components", null, values);
    }

    private static void insertDevice(SQLiteDatabase db, String userId, String id) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("user_id", userId);
        values.put("name", "Android");
        values.put("last_seen_at", "2026-08-10T00:00:00Z");
        values.put("app_version", "1.0.0");
        db.insertOrThrow("devices", null, values);
    }

    private static void insertPriceTraceCache(SQLiteDatabase db, String productName) {
        ContentValues values = new ContentValues();
        values.put("catalog_product_id", "catalog-1");
        values.putNull("standard_product_id");
        values.put("product_name", productName);
        values.putNull("brand_name");
        values.putNull("seller_name");
        values.putNull("latest_price_krw");
        values.putNull("price_observed_at");
        values.putNull("content_amount");
        values.putNull("content_unit");
        values.putNull("package_count");
        values.put("contract_version", "product-read.v1");
        values.put("fetched_at", "2026-08-10T00:00:00Z");
        db.insertOrThrow("pricetrace_product_cache", null, values);
    }

    private static TestDatabase openDatabase(String prefix) {
        Context context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext(),
                prefix
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        return new TestDatabase(context, helper, helper.getWritableDatabase());
    }

    private static String scalar(SQLiteDatabase database, String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

    private static String nullableScalar(SQLiteDatabase database, String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getString(0);
        }
    }

    private static final class TestDatabase {
        private final Context context;
        private final FitnessDatabaseHelper helper;
        private final SQLiteDatabase database;

        private TestDatabase(Context context, FitnessDatabaseHelper helper, SQLiteDatabase database) {
            this.context = context;
            this.helper = helper;
            this.database = database;
        }

        private void closeAndDelete() {
            helper.close();
            SharedPreferences preferences = context.getSharedPreferences("backup-test", Context.MODE_PRIVATE);
            preferences.edit().clear().commit();
            context.deleteSharedPreferences("backup-test");
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private final String prefix;

        private IsolatedDatabaseContext(Context base, String prefix) {
            super(base);
            this.prefix = prefix;
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(prefix + name);
        }

        @Override
        public boolean deleteDatabase(String name) {
            return SQLiteDatabase.deleteDatabase(getDatabasePath(name));
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
    }
}
