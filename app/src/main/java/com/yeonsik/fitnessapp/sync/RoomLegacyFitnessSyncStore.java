package com.yeonsik.fitnessapp.sync;

import com.yeonsik.fitnessapp.core.database.DevicesRoomEntity;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.LegacyFitnessSyncRoomDao;
import com.yeonsik.fitnessapp.core.database.MealRecordsRoomEntity;
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner;
import com.yeonsik.fitnessapp.core.database.SyncStateRoomEntity;
import com.yeonsik.fitnessapp.core.database.WeightRecordEntity;
import com.yeonsik.fitnessapp.core.database.WorkoutExercisesRoomEntity;
import com.yeonsik.fitnessapp.core.database.WorkoutRecordsRoomEntity;
import com.yeonsik.fitnessapp.core.database.WorkoutSetsRoomEntity;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Room implementation of the legacy sync storage boundary. */
public final class RoomLegacyFitnessSyncStore implements LegacyFitnessSyncStore {
    private static final String ANDROID_DEVICE_ID = "android-local";

    private final LegacyFitnessSyncRoomDao syncDao;
    private final RoomTransactionRunner transactionRunner;

    public RoomLegacyFitnessSyncStore(FitnessRoomDatabase roomDatabase) {
        this(roomDatabase, new RoomTransactionRunner(roomDatabase));
    }

    public RoomLegacyFitnessSyncStore(
            FitnessRoomDatabase roomDatabase,
            RoomTransactionRunner transactionRunner
    ) {
        if (roomDatabase == null || transactionRunner == null) {
            throw new IllegalArgumentException("Room sync dependencies are required.");
        }
        syncDao = roomDatabase.legacyFitnessSyncRoomDao();
        this.transactionRunner = transactionRunner;
    }

    @Override
    public List<LegacySyncRow> rows(
            String table,
            String userId,
            String deviceId,
            LegacySyncCursor cursor,
            int limit
    ) {
        LegacySyncCursor safeCursor = cursor == null ? LegacySyncCursor.empty() : cursor;
        int safeLimit = limit > 0 ? limit : Integer.MAX_VALUE;
        String cursorVersion = safeCursor.getVersion();
        String cursorId = safeCursor.getId();
        String safeDeviceId = deviceId == null ? ANDROID_DEVICE_ID : deviceId;
        switch (table) {
            case "devices":
                return deviceRows(syncDao.devices(
                        userId, safeDeviceId, cursorVersion, cursorId, safeLimit
                ));
            case "workout_records":
                return workoutRecordRows(syncDao.workoutRecords(
                        userId, safeDeviceId, cursorVersion, cursorId, safeLimit
                ));
            case "workout_exercises":
                return workoutExerciseRows(syncDao.workoutExercises(
                        userId, safeDeviceId, cursorVersion, cursorId, safeLimit
                ));
            case "workout_sets":
                return workoutSetRows(syncDao.workoutSets(
                        userId, safeDeviceId, cursorVersion, cursorId, safeLimit
                ));
            case "meal_records":
                return mealRecordRows(syncDao.mealRecords(
                        userId, safeDeviceId, cursorVersion, cursorId, safeLimit
                ));
            case "weight_records":
                return weightRecordRows(syncDao.weightRecords(
                        userId, safeDeviceId, cursorVersion, cursorId, safeLimit
                ));
            default:
                throw new IllegalArgumentException("Unsupported sync table: " + table);
        }
    }

    @Override
    public LegacySyncCursor cursor(String scopeKey, String table, String direction) {
        SyncStateRoomEntity state = syncDao.syncState(scopeKey, table, direction);
        if (state == null) {
            return LegacySyncCursor.empty();
        }
        return new LegacySyncCursor(state.getCursorVersion(), state.getCursorId());
    }

    @Override
    public void saveCursor(
            String scopeKey,
            String table,
            String direction,
            LegacySyncCursor cursor
    ) {
        if (cursor == null || cursor.getVersion() == null) {
            return;
        }
        syncDao.upsertSyncState(new SyncStateRoomEntity(
                scopeKey,
                table,
                direction,
                cursor.getVersion(),
                cursor.getId(),
                OffsetDateTime.now().toString()
        ));
    }

    @Override
    public boolean hasRow(String table, String id, String userId) {
        return row(table, id, userId) != null;
    }

    @Override
    public String localVersion(String table, String id, String userId, String versionColumn) {
        LegacySyncRow local = row(table, id, userId);
        if (local == null) {
            return null;
        }
        Object value = local.value(versionColumn);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public void applyRows(String table, List<LegacySyncRow> rows, String userId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        transactionRunner.run(() -> {
            for (LegacySyncRow row : rows) {
                applyRow(table, row, userId);
            }
        });
    }

    private void applyRow(String table, LegacySyncRow row, String userId) {
        String id = string(row, "id", "");
        if (id.isEmpty()) {
            return;
        }
        switch (table) {
            case "devices":
                syncDao.upsertDevice(mergeDevice(syncDao.device(id, userId), row, userId));
                return;
            case "workout_records":
                syncDao.upsertWorkoutRecord(
                        mergeWorkoutRecord(syncDao.workoutRecord(id, userId), row, userId)
                );
                return;
            case "workout_exercises":
                syncDao.upsertWorkoutExercise(
                        mergeWorkoutExercise(syncDao.workoutExercise(id, userId), row, userId)
                );
                return;
            case "workout_sets":
                syncDao.upsertWorkoutSet(
                        mergeWorkoutSet(syncDao.workoutSet(id, userId), row, userId)
                );
                return;
            case "meal_records":
                syncDao.upsertMealRecord(
                        mergeMealRecord(syncDao.mealRecord(id, userId), row, userId)
                );
                return;
            case "weight_records":
                syncDao.upsertWeightRecord(
                        mergeWeightRecord(syncDao.weightRecord(id, userId), row, userId)
                );
                return;
            default:
                throw new IllegalArgumentException("Unsupported sync table: " + table);
        }
    }

    private LegacySyncRow row(String table, String id, String userId) {
        switch (table) {
            case "devices":
                return deviceRow(syncDao.device(id, userId));
            case "workout_records":
                return workoutRecordRow(syncDao.workoutRecord(id, userId));
            case "workout_exercises":
                return workoutExerciseRow(syncDao.workoutExercise(id, userId));
            case "workout_sets":
                return workoutSetRow(syncDao.workoutSet(id, userId));
            case "meal_records":
                return mealRecordRow(syncDao.mealRecord(id, userId));
            case "weight_records":
                return weightRecordRow(syncDao.weightRecord(id, userId));
            default:
                throw new IllegalArgumentException("Unsupported sync table: " + table);
        }
    }

    private DevicesRoomEntity mergeDevice(
            DevicesRoomEntity current,
            LegacySyncRow row,
            String userId
    ) {
        return new DevicesRoomEntity(
                string(row, "id", current == null ? "" : current.getId()),
                userId,
                string(row, "name", current == null ? "" : current.getName()),
                string(row, "last_seen_at", current == null ? "" : current.getLastSeenAt()),
                nullableString(row, "app_version", current == null ? null : current.getAppVersion())
        );
    }

    private WorkoutRecordsRoomEntity mergeWorkoutRecord(
            WorkoutRecordsRoomEntity current,
            LegacySyncRow row,
            String userId
    ) {
        return new WorkoutRecordsRoomEntity(
                string(row, "id", current == null ? "" : current.getId()),
                userId,
                string(row, "date", current == null ? "" : current.getDate()),
                string(row, "workout_type", current == null ? "" : current.getWorkoutType()),
                string(row, "category", current == null ? "" : current.getCategory()),
                string(row, "exercise_name", current == null ? "" : current.getExerciseName()),
                longValue(row, "duration_seconds", current == null ? null : current.getDurationSeconds()),
                doubleValue(row, "total_volume_kg", current == null ? 0d : current.getTotalVolumeKg()),
                doubleValue(row, "average_heart_rate", current == null ? null : current.getAverageHeartRate()),
                string(row, "created_at", current == null ? "" : current.getCreatedAt()),
                booleanLong(row, "is_backfilled", current == null ? 0L : current.isBackfilled()),
                nullableString(row, "backfilled_at", current == null ? null : current.getBackfilledAt()),
                nullableString(row, "backfill_reason", current == null ? null : current.getBackfillReason()),
                string(row, "updated_at", current == null ? "" : current.getUpdatedAt()),
                nullableString(row, "deleted_at", current == null ? null : current.getDeletedAt()),
                string(row, "device_id", current == null ? ANDROID_DEVICE_ID : current.getDeviceId()),
                string(row, "source_app", current == null ? "fitness" : current.getSourceApp()),
                string(row, "scope", current == null ? "fitness" : current.getScope()),
                string(row, "metadata", current == null ? "{}" : current.getMetadata()),
                longValue(row, "contract_version", current == null ? 1L : current.getContractVersion())
        );
    }

    private WorkoutExercisesRoomEntity mergeWorkoutExercise(
            WorkoutExercisesRoomEntity current,
            LegacySyncRow row,
            String userId
    ) {
        return new WorkoutExercisesRoomEntity(
                string(row, "id", current == null ? "" : current.getId()),
                userId,
                string(row, "record_id", current == null ? "" : current.getRecordId()),
                longValue(row, "order_index", current == null ? 0L : current.getOrderIndex()),
                string(row, "exercise_id", current == null ? "" : current.getExerciseId()),
                string(row, "exercise_name_snapshot", current == null ? "" : current.getExerciseNameSnapshot()),
                string(row, "ui_part", current == null ? "" : current.getUiPart()),
                nullableString(row, "primary_sub_part_snapshot", current == null ? null : current.getPrimarySubPartSnapshot()),
                nullableString(row, "equipment_snapshot", current == null ? null : current.getEquipmentSnapshot()),
                string(row, "record_type", current == null ? "strength" : current.getRecordType()),
                nullableString(row, "family_id", current == null ? null : current.getFamilyId()),
                nullableString(row, "preset_id", current == null ? null : current.getPresetId()),
                nullableString(row, "canonical_variant_key", current == null ? null : current.getCanonicalVariantKey()),
                nullableString(row, "visual_variant_key", current == null ? null : current.getVisualVariantKey()),
                nullableString(row, "memo", current == null ? null : current.getMemo()),
                string(row, "created_at", current == null ? "" : current.getCreatedAt()),
                string(row, "updated_at", current == null ? "" : current.getUpdatedAt()),
                nullableString(row, "deleted_at", current == null ? null : current.getDeletedAt()),
                string(row, "device_id", current == null ? ANDROID_DEVICE_ID : current.getDeviceId()),
                longValue(row, "contract_version", current == null ? 1L : current.getContractVersion())
        );
    }

    private WorkoutSetsRoomEntity mergeWorkoutSet(
            WorkoutSetsRoomEntity current,
            LegacySyncRow row,
            String userId
    ) {
        return new WorkoutSetsRoomEntity(
                string(row, "id", current == null ? "" : current.getId()),
                userId,
                string(row, "workout_exercise_id", current == null ? "" : current.getWorkoutExerciseId()),
                longValue(row, "set_index", current == null ? 0L : current.getSetIndex()),
                longValue(row, "target_reps", current == null ? null : current.getTargetReps()),
                longValue(row, "actual_reps", current == null ? null : current.getActualReps()),
                doubleValue(row, "weight_kg", current == null ? null : current.getWeightKg()),
                doubleValue(row, "volume_kg", current == null ? null : current.getVolumeKg()),
                longValue(row, "duration_seconds", current == null ? null : current.getDurationSeconds()),
                doubleValue(row, "distance_meters", current == null ? null : current.getDistanceMeters()),
                longValue(row, "rest_seconds", current == null ? null : current.getRestSeconds()),
                doubleValue(row, "assisted_weight_kg", current == null ? null : current.getAssistedWeightKg()),
                doubleValue(row, "added_weight_kg", current == null ? null : current.getAddedWeightKg()),
                doubleValue(row, "input_load_value", current == null ? null : current.getInputLoadValue()),
                nullableString(row, "input_load_unit", current == null ? null : current.getInputLoadUnit()),
                nullableString(row, "load_state", current == null ? null : current.getLoadState()),
                booleanLong(row, "is_completed", current == null ? 0L : current.isCompleted()),
                longValue(row, "rpe", current == null ? null : current.getRpe()),
                longValue(row, "rir", current == null ? null : current.getRir()),
                nullableString(row, "memo", current == null ? null : current.getMemo()),
                string(row, "created_at", current == null ? "" : current.getCreatedAt()),
                string(row, "updated_at", current == null ? "" : current.getUpdatedAt()),
                nullableString(row, "deleted_at", current == null ? null : current.getDeletedAt()),
                string(row, "device_id", current == null ? ANDROID_DEVICE_ID : current.getDeviceId()),
                longValue(row, "contract_version", current == null ? 1L : current.getContractVersion())
        );
    }

    private MealRecordsRoomEntity mergeMealRecord(
            MealRecordsRoomEntity current,
            LegacySyncRow row,
            String userId
    ) {
        String metadata = string(row, "metadata", current == null ? "{}" : current.getMetadata());
        String mealKind = nullableString(row, "meal_kind", current == null ? "food" : current.getMealKind());
        String storeName = nullableString(row, "store_name", current == null ? null : current.getStoreName());
        String branchName = nullableString(row, "branch_name", current == null ? null : current.getBranchName());
        String menuName = nullableString(row, "menu_name", current == null ? null : current.getMenuName());
        String restaurantId = nullableString(row, "restaurant_id", current == null ? null : current.getRestaurantId());
        String restaurantLocationId = nullableString(
                row, "restaurant_location_id", current == null ? null : current.getRestaurantLocationId()
        );
        String restaurantMenuId = nullableString(
                row, "restaurant_menu_id", current == null ? null : current.getRestaurantMenuId()
        );
        String catalogProductId = nullableString(
                row, "catalog_product_id", current == null ? null : current.getCatalogProductId()
        );
        try {
            JSONObject metadataObject = new JSONObject(metadata);
            mealKind = metadataValue(metadataObject, "meal_kind", mealKind);
            storeName = metadataValue(metadataObject, "store_name", storeName);
            branchName = metadataValue(metadataObject, "branch_name", branchName);
            menuName = metadataValue(metadataObject, "menu_name", menuName);
            restaurantId = metadataValue(metadataObject, "restaurant_id", restaurantId);
            restaurantLocationId = metadataValue(metadataObject, "restaurant_location_id", restaurantLocationId);
            restaurantMenuId = metadataValue(metadataObject, "restaurant_menu_id", restaurantMenuId);
            catalogProductId = metadataValue(metadataObject, "catalog_product_id", catalogProductId);
        } catch (JSONException ignored) {
            // Keep legacy non-JSON metadata readable.
        }
        return new MealRecordsRoomEntity(
                string(row, "id", current == null ? "" : current.getId()),
                userId,
                string(row, "date", current == null ? "" : current.getDate()),
                string(row, "menu", current == null ? "" : current.getMenu()),
                mealKind == null ? "food" : mealKind,
                nullableString(row, "fulfillment_mode", current == null ? null : current.getFulfillmentMode()),
                storeName,
                branchName,
                menuName,
                restaurantId,
                restaurantLocationId,
                restaurantMenuId,
                catalogProductId,
                nullableString(row, "composition_template_id", current == null ? null : current.getCompositionTemplateId()),
                longValue(row, "composition_template_revision", current == null ? null : current.getCompositionTemplateRevision()),
                nullableString(row, "nutrition_calculation_contract", current == null ? null : current.getNutritionCalculationContract()),
                longValue(row, "calories", current == null ? 0L : current.getCalories()),
                doubleValue(row, "protein_grams", current == null ? 0d : current.getProteinGrams()),
                doubleValue(row, "carbs_grams", current == null ? null : current.getCarbsGrams()),
                doubleValue(row, "fat_grams", current == null ? null : current.getFatGrams()),
                string(row, "created_at", current == null ? "" : current.getCreatedAt()),
                booleanLong(row, "is_backfilled", current == null ? 0L : current.isBackfilled()),
                nullableString(row, "backfilled_at", current == null ? null : current.getBackfilledAt()),
                nullableString(row, "backfill_reason", current == null ? null : current.getBackfillReason()),
                string(row, "updated_at", current == null ? "" : current.getUpdatedAt()),
                nullableString(row, "deleted_at", current == null ? null : current.getDeletedAt()),
                string(row, "device_id", current == null ? ANDROID_DEVICE_ID : current.getDeviceId()),
                string(row, "source_app", current == null ? "fitness" : current.getSourceApp()),
                string(row, "scope", current == null ? "fitness" : current.getScope()),
                metadata,
                longValue(row, "contract_version", current == null ? 1L : current.getContractVersion())
        );
    }

    private WeightRecordEntity mergeWeightRecord(
            WeightRecordEntity current,
            LegacySyncRow row,
            String userId
    ) {
        return new WeightRecordEntity(
                string(row, "id", current == null ? "" : current.getId()),
                userId,
                string(row, "date", current == null ? "" : current.getDate()),
                doubleValue(row, "weight_kg", current == null ? 0d : current.getWeightKg()),
                string(row, "created_at", current == null ? "" : current.getCreatedAt()),
                booleanValue(row, "is_backfilled", current == null || current.isBackfilled()),
                nullableString(row, "backfilled_at", current == null ? null : current.getBackfilledAt()),
                nullableString(row, "backfill_reason", current == null ? null : current.getBackfillReason()),
                string(row, "updated_at", current == null ? "" : current.getUpdatedAt()),
                nullableString(row, "deleted_at", current == null ? null : current.getDeletedAt()),
                string(row, "device_id", current == null ? ANDROID_DEVICE_ID : current.getDeviceId()),
                string(row, "source_app", current == null ? "fitness" : current.getSourceApp()),
                string(row, "scope", current == null ? "fitness" : current.getScope()),
                string(row, "metadata", current == null ? "{}" : current.getMetadata()),
                intValue(row, "contract_version", current == null ? 1 : current.getContractVersion())
        );
    }

    private static String metadataValue(JSONObject object, String key, String fallback) {
        String value = object.optString(key, "").trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? fallback : value;
    }

    private static String string(LegacySyncRow row, String key, String fallback) {
        if (!row.contains(key) || row.value(key) == null) {
            return fallback;
        }
        return String.valueOf(row.value(key));
    }

    private static String nullableString(LegacySyncRow row, String key, String fallback) {
        if (!row.contains(key)) {
            return fallback;
        }
        Object value = row.value(key);
        if (value == null) {
            return null;
        }
        String string = String.valueOf(value).trim();
        return string.isEmpty() || "null".equalsIgnoreCase(string) ? null : string;
    }

    private static long booleanLong(LegacySyncRow row, String key, long fallback) {
        if (!row.contains(key) || row.value(key) == null) {
            return fallback;
        }
        Object value = row.value(key);
        if (value instanceof Boolean) {
            return (Boolean) value ? 1L : 0L;
        }
        return longValue(row, key, fallback);
    }

    private static boolean booleanValue(LegacySyncRow row, String key, boolean fallback) {
        if (!row.contains(key) || row.value(key) == null) {
            return fallback;
        }
        Object value = row.value(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return longValue(row, key, fallback ? 1L : 0L) == 1L;
    }

    private static Long longValue(LegacySyncRow row, String key, Long fallback) {
        if (!row.contains(key) || row.value(key) == null) {
            return fallback;
        }
        Object value = row.value(key);
        if (value instanceof Boolean) {
            return (Boolean) value ? 1L : 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double doubleValue(LegacySyncRow row, String key, double fallback) {
        Double value = doubleValue(row, key, Double.valueOf(fallback));
        return value == null ? fallback : value;
    }

    private static Double doubleValue(LegacySyncRow row, String key, Double fallback) {
        if (!row.contains(key) || row.value(key) == null) {
            return fallback;
        }
        Object value = row.value(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intValue(LegacySyncRow row, String key, int fallback) {
        Long value = longValue(row, key, (long) fallback);
        return value == null ? fallback : value.intValue();
    }

    private static LegacySyncRow deviceRow(DevicesRoomEntity entity) {
        if (entity == null) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "id", entity.getId());
        put(values, "user_id", entity.getUserId());
        put(values, "name", entity.getName());
        put(values, "last_seen_at", entity.getLastSeenAt());
        put(values, "app_version", entity.getAppVersion());
        return new LegacySyncRow(values);
    }

    private static LegacySyncRow workoutRecordRow(WorkoutRecordsRoomEntity entity) {
        if (entity == null) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "id", entity.getId());
        put(values, "user_id", entity.getUserId());
        put(values, "date", entity.getDate());
        put(values, "workout_type", entity.getWorkoutType());
        put(values, "category", entity.getCategory());
        put(values, "exercise_name", entity.getExerciseName());
        put(values, "duration_seconds", entity.getDurationSeconds());
        put(values, "total_volume_kg", entity.getTotalVolumeKg());
        put(values, "average_heart_rate", entity.getAverageHeartRate());
        put(values, "created_at", entity.getCreatedAt());
        put(values, "is_backfilled", entity.isBackfilled() == 1L);
        put(values, "backfilled_at", entity.getBackfilledAt());
        put(values, "backfill_reason", entity.getBackfillReason());
        put(values, "updated_at", entity.getUpdatedAt());
        put(values, "deleted_at", entity.getDeletedAt());
        put(values, "device_id", entity.getDeviceId());
        put(values, "source_app", entity.getSourceApp());
        put(values, "scope", entity.getScope());
        put(values, "metadata", entity.getMetadata());
        put(values, "contract_version", entity.getContractVersion());
        return new LegacySyncRow(values);
    }

    private static LegacySyncRow workoutExerciseRow(WorkoutExercisesRoomEntity entity) {
        if (entity == null) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "id", entity.getId());
        put(values, "user_id", entity.getUserId());
        put(values, "record_id", entity.getRecordId());
        put(values, "order_index", entity.getOrderIndex());
        put(values, "exercise_id", entity.getExerciseId());
        put(values, "exercise_name_snapshot", entity.getExerciseNameSnapshot());
        put(values, "ui_part", entity.getUiPart());
        put(values, "primary_sub_part_snapshot", entity.getPrimarySubPartSnapshot());
        put(values, "equipment_snapshot", entity.getEquipmentSnapshot());
        put(values, "record_type", entity.getRecordType());
        put(values, "family_id", entity.getFamilyId());
        put(values, "preset_id", entity.getPresetId());
        put(values, "canonical_variant_key", entity.getCanonicalVariantKey());
        put(values, "visual_variant_key", entity.getVisualVariantKey());
        put(values, "memo", entity.getMemo());
        put(values, "created_at", entity.getCreatedAt());
        put(values, "updated_at", entity.getUpdatedAt());
        put(values, "deleted_at", entity.getDeletedAt());
        put(values, "device_id", entity.getDeviceId());
        put(values, "contract_version", entity.getContractVersion());
        return new LegacySyncRow(values);
    }

    private static LegacySyncRow workoutSetRow(WorkoutSetsRoomEntity entity) {
        if (entity == null) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "id", entity.getId());
        put(values, "user_id", entity.getUserId());
        put(values, "workout_exercise_id", entity.getWorkoutExerciseId());
        put(values, "set_index", entity.getSetIndex());
        put(values, "target_reps", entity.getTargetReps());
        put(values, "actual_reps", entity.getActualReps());
        put(values, "weight_kg", entity.getWeightKg());
        put(values, "volume_kg", entity.getVolumeKg());
        put(values, "duration_seconds", entity.getDurationSeconds());
        put(values, "distance_meters", entity.getDistanceMeters());
        put(values, "rest_seconds", entity.getRestSeconds());
        put(values, "assisted_weight_kg", entity.getAssistedWeightKg());
        put(values, "added_weight_kg", entity.getAddedWeightKg());
        put(values, "input_load_value", entity.getInputLoadValue());
        put(values, "input_load_unit", entity.getInputLoadUnit());
        put(values, "load_state", entity.getLoadState());
        put(values, "is_completed", entity.isCompleted() == 1L);
        put(values, "rpe", entity.getRpe());
        put(values, "rir", entity.getRir());
        put(values, "memo", entity.getMemo());
        put(values, "created_at", entity.getCreatedAt());
        put(values, "updated_at", entity.getUpdatedAt());
        put(values, "deleted_at", entity.getDeletedAt());
        put(values, "device_id", entity.getDeviceId());
        put(values, "contract_version", entity.getContractVersion());
        return new LegacySyncRow(values);
    }

    private static LegacySyncRow mealRecordRow(MealRecordsRoomEntity entity) {
        if (entity == null) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "id", entity.getId());
        put(values, "user_id", entity.getUserId());
        put(values, "date", entity.getDate());
        put(values, "menu", entity.getMenu());
        put(values, "meal_kind", entity.getMealKind());
        put(values, "fulfillment_mode", entity.getFulfillmentMode());
        put(values, "store_name", entity.getStoreName());
        put(values, "branch_name", entity.getBranchName());
        put(values, "menu_name", entity.getMenuName());
        put(values, "restaurant_id", entity.getRestaurantId());
        put(values, "restaurant_location_id", entity.getRestaurantLocationId());
        put(values, "restaurant_menu_id", entity.getRestaurantMenuId());
        put(values, "catalog_product_id", entity.getCatalogProductId());
        put(values, "composition_template_id", entity.getCompositionTemplateId());
        put(values, "composition_template_revision", entity.getCompositionTemplateRevision());
        put(values, "nutrition_calculation_contract", entity.getNutritionCalculationContract());
        put(values, "calories", entity.getCalories());
        put(values, "protein_grams", entity.getProteinGrams());
        put(values, "carbs_grams", entity.getCarbsGrams());
        put(values, "fat_grams", entity.getFatGrams());
        put(values, "created_at", entity.getCreatedAt());
        put(values, "is_backfilled", entity.isBackfilled() == 1L);
        put(values, "backfilled_at", entity.getBackfilledAt());
        put(values, "backfill_reason", entity.getBackfillReason());
        put(values, "updated_at", entity.getUpdatedAt());
        put(values, "deleted_at", entity.getDeletedAt());
        put(values, "device_id", entity.getDeviceId());
        put(values, "source_app", entity.getSourceApp());
        put(values, "scope", entity.getScope());
        put(values, "metadata", entity.getMetadata());
        put(values, "contract_version", entity.getContractVersion());
        return new LegacySyncRow(values);
    }

    private static LegacySyncRow weightRecordRow(WeightRecordEntity entity) {
        if (entity == null) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        put(values, "id", entity.getId());
        put(values, "user_id", entity.getUserId());
        put(values, "date", entity.getDate());
        put(values, "weight_kg", entity.getWeightKg());
        put(values, "created_at", entity.getCreatedAt());
        put(values, "is_backfilled", entity.isBackfilled());
        put(values, "backfilled_at", entity.getBackfilledAt());
        put(values, "backfill_reason", entity.getBackfillReason());
        put(values, "updated_at", entity.getUpdatedAt());
        put(values, "deleted_at", entity.getDeletedAt());
        put(values, "device_id", entity.getDeviceId());
        put(values, "source_app", entity.getSourceApp());
        put(values, "scope", entity.getScope());
        put(values, "metadata", entity.getMetadata());
        put(values, "contract_version", entity.getContractVersion());
        return new LegacySyncRow(values);
    }

    private static List<LegacySyncRow> deviceRows(List<DevicesRoomEntity> entities) {
        List<LegacySyncRow> rows = new ArrayList<>();
        for (DevicesRoomEntity entity : entities) rows.add(deviceRow(entity));
        return rows;
    }

    private static List<LegacySyncRow> workoutRecordRows(List<WorkoutRecordsRoomEntity> entities) {
        List<LegacySyncRow> rows = new ArrayList<>();
        for (WorkoutRecordsRoomEntity entity : entities) rows.add(workoutRecordRow(entity));
        return rows;
    }

    private static List<LegacySyncRow> workoutExerciseRows(List<WorkoutExercisesRoomEntity> entities) {
        List<LegacySyncRow> rows = new ArrayList<>();
        for (WorkoutExercisesRoomEntity entity : entities) rows.add(workoutExerciseRow(entity));
        return rows;
    }

    private static List<LegacySyncRow> workoutSetRows(List<WorkoutSetsRoomEntity> entities) {
        List<LegacySyncRow> rows = new ArrayList<>();
        for (WorkoutSetsRoomEntity entity : entities) rows.add(workoutSetRow(entity));
        return rows;
    }

    private static List<LegacySyncRow> mealRecordRows(List<MealRecordsRoomEntity> entities) {
        List<LegacySyncRow> rows = new ArrayList<>();
        for (MealRecordsRoomEntity entity : entities) rows.add(mealRecordRow(entity));
        return rows;
    }

    private static List<LegacySyncRow> weightRecordRows(List<WeightRecordEntity> entities) {
        List<LegacySyncRow> rows = new ArrayList<>();
        for (WeightRecordEntity entity : entities) rows.add(weightRecordRow(entity));
        return rows;
    }

    private static void put(Map<String, Object> values, String key, Object value) {
        values.put(key, value);
    }
}
