package com.yeonsik.fitnessapp.data;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The narrow Fitness-owned read model shared with Personal OS.
 *
 * <p>This type intentionally contains no exercise identity, load, repetition,
 * RPE/RIR, or set-level data. The source session remains fully owned by the
 * Fitness database; this object is only the completed-session projection.</p>
 */
public final class FitnessSummaryProjectionV2 {
    public static final int CONTRACT_VERSION = 2;

    public final String id;
    public final String sourceFitnessSessionId;
    public final String date;
    public final String completionStatus;
    public final int chestSets;
    public final int backSets;
    public final int legsSets;
    public final int shouldersSets;
    public final int absSets;
    public final int tricepsSets;
    public final int bicepsSets;
    public final Integer totalDurationSeconds;
    public final Integer cardioDurationSeconds;
    public final String createdAt;
    public final boolean isBackfilled;
    public final String backfilledAt;
    public final String backfillReason;
    public final String updatedAt;
    public final String deletedAt;
    public final String deviceId;

    public FitnessSummaryProjectionV2(
            String id,
            String sourceFitnessSessionId,
            String date,
            String completionStatus,
            int chestSets,
            int backSets,
            int legsSets,
            int shouldersSets,
            int absSets,
            int tricepsSets,
            int bicepsSets,
            Integer totalDurationSeconds,
            Integer cardioDurationSeconds,
            String createdAt,
            boolean isBackfilled,
            String backfilledAt,
            String backfillReason,
            String updatedAt,
            String deletedAt,
            String deviceId
    ) {
        this.id = id;
        this.sourceFitnessSessionId = sourceFitnessSessionId;
        this.date = date;
        this.completionStatus = completionStatus;
        this.chestSets = chestSets;
        this.backSets = backSets;
        this.legsSets = legsSets;
        this.shouldersSets = shouldersSets;
        this.absSets = absSets;
        this.tricepsSets = tricepsSets;
        this.bicepsSets = bicepsSets;
        this.totalDurationSeconds = totalDurationSeconds;
        this.cardioDurationSeconds = cardioDurationSeconds;
        this.createdAt = createdAt;
        this.isBackfilled = isBackfilled;
        this.backfilledAt = backfilledAt;
        this.backfillReason = backfillReason;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.deviceId = deviceId;
    }

    /** JSON body for Fitness-owned upsert_fitness_summary_projection_v2. */
    public JSONObject toRpcJson() throws JSONException {
        JSONObject projection = new JSONObject();
        projection.put("id", id);
        projection.put("source_fitness_session_id", sourceFitnessSessionId);
        projection.put("date", date);
        projection.put("completion_status", completionStatus);
        projection.put("chest_sets", chestSets);
        projection.put("back_sets", backSets);
        projection.put("legs_sets", legsSets);
        projection.put("shoulders_sets", shouldersSets);
        projection.put("abs_sets", absSets);
        projection.put("triceps_sets", tricepsSets);
        projection.put("biceps_sets", bicepsSets);
        putNullable(projection, "total_duration_seconds", totalDurationSeconds);
        putNullable(projection, "cardio_duration_seconds", cardioDurationSeconds);
        projection.put("contract_version", CONTRACT_VERSION);
        putNullable(projection, "created_at", createdAt);
        projection.put("is_backfilled", isBackfilled);
        putNullable(projection, "backfilled_at", backfilledAt);
        putNullable(projection, "backfill_reason", backfillReason);
        putNullable(projection, "updated_at", updatedAt);
        putNullable(projection, "deleted_at", deletedAt);
        projection.put("device_id", deviceId);
        return projection;
    }

    private static void putNullable(JSONObject object, String key, Object value)
            throws JSONException {
        object.put(key, value == null ? JSONObject.NULL : value);
    }
}
