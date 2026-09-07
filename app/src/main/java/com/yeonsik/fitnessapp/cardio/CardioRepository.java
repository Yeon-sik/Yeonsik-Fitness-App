package com.yeonsik.fitnessapp.cardio;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;

import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi;
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot;

import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * GPS 유산소의 실행 상태와 원시 좌표를 로컬 SQLite에 저장한다.
 * 공유 가능한 완료 요약은 기존 Fitness Record Contract 필드에 직접 기록한다.
 */
public final class CardioRepository implements CardioRepositoryApi {
    public static final String STATUS_TRACKING = "tracking";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_COMPLETED = "completed";

    public static final String GPS_SEARCHING = "searching";
    public static final String GPS_READY = "ready";
    public static final String GPS_WEAK = "weak";
    public static final String GPS_PERMISSION_MISSING = "permission_missing";
    public static final String GPS_UNAVAILABLE = "unavailable";
    public static final String GPS_STOPPED = "stopped";

    private final FitnessDatabaseConnection database;
    private String userId;

    public CardioRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this.database = FitnessDatabaseConnection.fromLegacy(dbHelper);
        this.userId = userId;
    }

    public CardioRepository(FitnessDatabaseConnection database, String userId) {
        this.database = database;
        this.userId = userId;
    }

    public CardioRepository(FitnessRoomDatabase roomDatabase, String userId,
                            android.content.Context context) {
        this(FitnessDatabaseConnection.fromRoom(roomDatabase, context), userId);
    }

    public String startSession(CardioActivityType activityType, String date) {
        if (activityType == null) {
            throw new IllegalArgumentException("유산소 유형이 필요합니다.");
        }
        SessionSnapshot active = activeSession();
        if (active != null) {
            return active.recordId;
        }

        FitnessDatabaseConnection database = db();
        database.beginTransaction();
        try {
            String recordId = createCardioRecord(date, activityType);
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put("record_id", recordId);
            values.put("user_id", userId());
            values.put("activity_type", activityType.id());
            values.put("status", STATUS_TRACKING);
            values.put("started_at_epoch_ms", now);
            values.put("last_resumed_at_epoch_ms", now);
            values.put("active_duration_ms", 0);
            values.put("distance_meters", 0d);
            values.put("accepted_point_count", 0);
            values.putNull("last_latitude");
            values.putNull("last_longitude");
            values.putNull("last_location_time_ms");
            values.putNull("last_accuracy_meters");
            values.put("gps_status", GPS_SEARCHING);
            values.put("updated_at_epoch_ms", now);
            database.insertOrThrow("cardio_sessions", null, values);
            database.setTransactionSuccessful();
            return recordId;
        } finally {
            database.endTransaction();
        }
    }

    public SessionSnapshot activeSession() {
        try (Cursor cursor = db().rawQuery(
                "SELECT record_id FROM cardio_sessions " +
                        "WHERE user_id = ? AND status IN (?, ?) "
                        + "ORDER BY started_at_epoch_ms DESC LIMIT 1",
                new String[]{userId(), STATUS_TRACKING, STATUS_PAUSED})) {
            return cursor.moveToFirst() ? session(cursor.getString(0)) : null;
        }
    }

    public boolean isCardioSession(String recordId) {
        if (recordId == null || recordId.trim().isEmpty()) {
            return false;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT 1 FROM cardio_sessions " +
                        "WHERE record_id = ? AND user_id = ? LIMIT 1",
                new String[]{recordId, userId()})) {
            return cursor.moveToFirst();
        }
    }

    public SessionSnapshot session(String recordId) {
        if (recordId == null || recordId.trim().isEmpty()) {
            return null;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT cs.activity_type, cs.status, cs.started_at_epoch_ms, "
                        + "cs.last_resumed_at_epoch_ms, cs.active_duration_ms, cs.distance_meters, "
                        + "cs.accepted_point_count, cs.gps_status, wr.average_heart_rate "
                        + "FROM cardio_sessions cs LEFT JOIN workout_records wr " +
                        "ON wr.id = cs.record_id AND wr.user_id = cs.user_id "
                        + "WHERE cs.record_id = ? AND cs.user_id = ? LIMIT 1",
                new String[]{recordId, userId()})) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new SessionSnapshot(
                    recordId,
                    CardioActivityType.fromId(cursor.getString(0)),
                    cursor.getString(1),
                    cursor.getLong(2),
                    cursor.isNull(3) ? null : cursor.getLong(3),
                    cursor.getLong(4),
                    cursor.getDouble(5),
                    cursor.getInt(6),
                    cursor.getString(7),
                    cursor.isNull(8) ? null : cursor.getDouble(8)
            );
        }
    }

    @Override
    public CardioSessionSnapshot loadSession(AccountScope scope, String recordId) {
        requireScope(scope);
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null) {
            return null;
        }
        return new CardioSessionSnapshot(
                snapshot.recordId,
                snapshot.activityType.id(),
                snapshot.activityType.labelKo(),
                snapshot.status,
                snapshot.startedAtEpochMillis,
                snapshot.lastResumedAtEpochMillis,
                snapshot.activeDurationMillis,
                snapshot.distanceMeters,
                snapshot.acceptedPointCount,
                snapshot.gpsStatus,
                snapshot.averageHeartRateBpm
        );
    }

    /**
     * 완료 화면에서 사용할 경로만 백그라운드에서 읽고 표시용 좌표로 줄인다.
     * 원본 좌표는 SQLite에 남으며 이 메서드는 제한된 투영본만 메모리에 만든다.
     */
    public CardioRouteProjection routeProjection(String recordId) {
        return routeProjection(
                recordId,
                CardioRouteProjection.DEFAULT_MAX_DISPLAY_POINTS
        );
    }

    public CardioRouteProjection routeProjection(
            String recordId,
            int maxDisplayPoints
    ) {
        if (recordId == null || recordId.trim().isEmpty()) {
            return CardioRouteProjection.empty();
        }

        int rawPointCount;
        try (Cursor countCursor = db().rawQuery(
                "SELECT COUNT(*) FROM cardio_route_points "
                        + "WHERE record_id = ? AND user_id = ?",
                new String[]{recordId, userId()})) {
            if (!countCursor.moveToFirst()) {
                return CardioRouteProjection.empty();
            }
            rawPointCount = countCursor.getInt(0);
        }
        if (rawPointCount == 0) {
            return CardioRouteProjection.empty();
        }

        CardioRouteProjection.Builder builder = CardioRouteProjection.builder(
                rawPointCount,
                maxDisplayPoints
        );
        try (Cursor cursor = db().rawQuery(
                "SELECT captured_at_epoch_ms, latitude, longitude "
                        + "FROM cardio_route_points "
                        + "WHERE record_id = ? AND user_id = ? "
                        + "ORDER BY captured_at_epoch_ms ASC, id ASC",
                new String[]{recordId, userId()})) {
            while (cursor.moveToNext()) {
                builder.add(
                        cursor.getLong(0),
                        cursor.getDouble(1),
                        cursor.getDouble(2)
                );
            }
        }
        return builder.build();
    }

    public boolean pause(String recordId) {
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null || !STATUS_TRACKING.equals(snapshot.status)) {
            return false;
        }
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("status", STATUS_PAUSED);
        values.put("active_duration_ms", snapshot.elapsedDurationMillis(now));
        values.putNull("last_resumed_at_epoch_ms");
        values.put("gps_status", GPS_STOPPED);
        values.put("updated_at_epoch_ms", now);
        return db().update("cardio_sessions", values,
                "record_id = ? AND user_id = ? AND status = ?",
                new String[]{recordId, userId(), STATUS_TRACKING}) == 1;
    }

    public boolean resume(String recordId) {
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null || !STATUS_PAUSED.equals(snapshot.status)) {
            return false;
        }
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("status", STATUS_TRACKING);
        values.put("last_resumed_at_epoch_ms", now);
        values.putNull("last_latitude");
        values.putNull("last_longitude");
        values.putNull("last_location_time_ms");
        values.putNull("last_accuracy_meters");
        values.put("gps_status", GPS_SEARCHING);
        values.put("updated_at_epoch_ms", now);
        return db().update("cardio_sessions", values,
                "record_id = ? AND user_id = ? AND status = ?",
                new String[]{recordId, userId(), STATUS_PAUSED}) == 1;
    }

    public CardioDistanceFilter.Result acceptLocation(
            String recordId,
            CardioLocationSample candidate
    ) {
        FitnessDatabaseConnection database = db();
        database.beginTransaction();
        try {
            CardioActivityType activityType;
            double totalDistance;
            int acceptedPointCount;
            Long lastResumedAtEpochMillis;
            CardioLocationSample previous = null;
            try (Cursor cursor = database.rawQuery(
                    "SELECT activity_type, status, distance_meters, accepted_point_count, "
                            + "last_latitude, last_longitude, last_location_time_ms, last_accuracy_meters, "
                            + "last_resumed_at_epoch_ms "
                            + "FROM cardio_sessions WHERE record_id = ? AND user_id = ? LIMIT 1",
                    new String[]{recordId, userId()})) {
                if (!cursor.moveToFirst() || !STATUS_TRACKING.equals(cursor.getString(1))) {
                    return CardioDistanceFilter.Result.rejected(CardioDistanceFilter.Reason.INVALID);
                }
                activityType = CardioActivityType.fromId(cursor.getString(0));
                totalDistance = cursor.getDouble(2);
                acceptedPointCount = cursor.getInt(3);
                lastResumedAtEpochMillis = cursor.isNull(8) ? null : cursor.getLong(8);
                if (!cursor.isNull(4) && !cursor.isNull(5)
                        && !cursor.isNull(6) && !cursor.isNull(7)) {
                    previous = new CardioLocationSample(
                            cursor.getDouble(4),
                            cursor.getDouble(5),
                            cursor.getFloat(7),
                            cursor.getLong(6),
                            null
                    );
                }
            }

            if (candidate != null
                    && candidate.capturedAtMillis != null
                    && lastResumedAtEpochMillis != null
                    && candidate.capturedAtMillis < lastResumedAtEpochMillis) {
                return CardioDistanceFilter.Result.rejected(CardioDistanceFilter.Reason.STALE);
            }

            CardioDistanceFilter.Result result = CardioDistanceFilter.evaluate(
                    activityType, previous, candidate);
            if (!result.accepted) {
                if (result.reason == CardioDistanceFilter.Reason.INACCURATE
                        || result.reason == CardioDistanceFilter.Reason.TOO_FAST) {
                    updateGpsStatus(database, recordId, GPS_WEAK);
                }
                database.setTransactionSuccessful();
                return result;
            }

            ContentValues point = new ContentValues();
            point.put("record_id", recordId);
            point.put("user_id", userId());
            point.put("captured_at_epoch_ms", candidate.capturedAtMillis);
            point.put("latitude", candidate.latitude);
            point.put("longitude", candidate.longitude);
            point.put("accuracy_meters", candidate.accuracyMeters);
            if (candidate.reportedSpeedMetersPerSecond == null) {
                point.putNull("speed_mps");
            } else {
                point.put("speed_mps", candidate.reportedSpeedMetersPerSecond);
            }
            point.put("segment_distance_meters", result.segmentDistanceMeters);
            database.insertOrThrow("cardio_route_points", null, point);

            ContentValues state = new ContentValues();
            state.put("distance_meters", totalDistance + result.segmentDistanceMeters);
            state.put("accepted_point_count", acceptedPointCount + 1);
            state.put("last_latitude", candidate.latitude);
            state.put("last_longitude", candidate.longitude);
            state.put("last_location_time_ms", candidate.capturedAtMillis);
            state.put("last_accuracy_meters", candidate.accuracyMeters);
            state.put("gps_status", GPS_READY);
            state.put("updated_at_epoch_ms", System.currentTimeMillis());
            database.update(
                    "cardio_sessions",
                    state,
                    "record_id = ? AND user_id = ?",
                    new String[]{recordId, userId()}
            );
            database.setTransactionSuccessful();
            return result;
        } finally {
            database.endTransaction();
        }
    }

    public void setGpsStatus(String recordId, String gpsStatus) {
        updateGpsStatus(db(), recordId, gpsStatus);
    }

    public SessionSnapshot finish(String recordId, Integer averageHeartRateBpm) {
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null) {
            return null;
        }
        if (STATUS_COMPLETED.equals(snapshot.status)) {
            return snapshot;
        }
        long now = System.currentTimeMillis();
        long durationMillis = snapshot.elapsedDurationMillis(now);
        int durationSeconds = Math.max(1, safeSeconds(durationMillis));
        FitnessDatabaseConnection database = db();
        database.beginTransaction();
        try {
            completeCardioRecord(recordId, snapshot.activityType, durationSeconds,
                    snapshot.distanceMeters, averageHeartRateBpm);

            ContentValues values = new ContentValues();
            values.put("status", STATUS_COMPLETED);
            values.put("active_duration_ms", durationMillis);
            values.putNull("last_resumed_at_epoch_ms");
            values.put("gps_status", GPS_STOPPED);
            values.put("updated_at_epoch_ms", now);
            database.update(
                    "cardio_sessions",
                    values,
                    "record_id = ? AND user_id = ?",
                    new String[]{recordId, userId()}
            );
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return session(recordId);
    }

    public SessionSnapshot updateAverageHeartRate(
            String recordId,
            Integer averageHeartRateBpm
    ) {
        return updateCardioRecordHeartRate(recordId, averageHeartRateBpm)
                ? session(recordId)
                : null;
    }

    public void cancel(String recordId) {
        if (!isCardioSession(recordId)) {
            return;
        }
        FitnessDatabaseConnection database = db();
        database.beginTransaction();
        try {
            database.delete(
                    "cardio_route_points",
                    "record_id = ? AND user_id = ?",
                    new String[]{recordId, userId()}
            );
            database.delete(
                    "cardio_sessions",
                    "record_id = ? AND user_id = ?",
                    new String[]{recordId, userId()}
            );
            softDeleteWorkoutRecord(recordId);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void deleteLocalData(String recordId) {
        if (!isCardioSession(recordId)) {
            return;
        }
        FitnessDatabaseConnection database = db();
        database.beginTransaction();
        try {
            database.delete(
                    "cardio_route_points",
                    "record_id = ? AND user_id = ?",
                    new String[]{recordId, userId()}
            );
            database.delete(
                    "cardio_sessions",
                    "record_id = ? AND user_id = ?",
                    new String[]{recordId, userId()}
            );
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private void updateGpsStatus(FitnessDatabaseConnection database, String recordId, String gpsStatus) {
        ContentValues values = new ContentValues();
        values.put("gps_status", gpsStatus);
        values.put("updated_at_epoch_ms", System.currentTimeMillis());
        database.update("cardio_sessions", values,
                "record_id = ? AND user_id = ? AND status IN (?, ?)",
                new String[]{recordId, userId(), STATUS_TRACKING, STATUS_PAUSED});
    }

    private String createCardioRecord(String date, CardioActivityType activityType) {
        String recordId = UUID.randomUUID().toString();
        String timestamp = now();
        ContentValues record = new ContentValues();
        record.put("id", recordId);
        record.put("user_id", userId());
        record.put("date", date == null || date.trim().isEmpty() ? timestamp.substring(0, 10) : date);
        record.put("workout_type", "cardio");
        record.put("category", activityType.labelKo());
        record.put("exercise_name", activityType.labelKo());
        record.put("total_volume_kg", 0d);
        record.putNull("duration_seconds");
        record.putNull("average_heart_rate");
        record.put("created_at", timestamp);
        record.put("is_backfilled", 0);
        record.putNull("backfilled_at");
        record.putNull("backfill_reason");
        record.put("updated_at", timestamp);
        record.putNull("deleted_at");
        record.put("device_id", "android-local");
        record.put("source_app", "fitness");
        record.put("scope", "fitness");
        record.put("metadata", cardioMetadata("in_progress", activityType, "", 0, 0d, null));
        db().insertOrThrow("workout_records", null, record);

        ContentValues exercise = new ContentValues();
        exercise.put("id", UUID.randomUUID().toString());
        exercise.put("user_id", userId());
        exercise.put("record_id", recordId);
        exercise.put("order_index", 1);
        exercise.put("exercise_id", "cardio_" + activityType.id());
        exercise.put("exercise_name_snapshot", activityType.labelKo());
        exercise.put("ui_part", "cardio");
        exercise.put("primary_sub_part_snapshot", activityType.labelKo());
        exercise.putNull("equipment_snapshot");
        exercise.put("record_type", FitnessRecordContract.TIME);
        exercise.putNull("family_id");
        exercise.putNull("preset_id");
        exercise.putNull("canonical_variant_key");
        exercise.putNull("visual_variant_key");
        exercise.putNull("memo");
        exercise.put("created_at", timestamp);
        exercise.put("updated_at", timestamp);
        exercise.putNull("deleted_at");
        exercise.put("device_id", "android-local");
        exercise.put("contract_version", 1);
        db().insertOrThrow("workout_exercises", null, exercise);
        return recordId;
    }

    private void completeCardioRecord(
            String recordId,
            CardioActivityType activityType,
            int durationSeconds,
            double distanceMeters,
            Integer averageHeartRateBpm
    ) {
        String exerciseId = null;
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM workout_exercises WHERE record_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL ORDER BY order_index LIMIT 1",
                new String[]{recordId, userId()})) {
            if (cursor.moveToFirst()) exerciseId = cursor.getString(0);
        }
        if (exerciseId == null) throw new IllegalStateException("유산소 세부 종목을 찾지 못했습니다.");

        String timestamp = now();
        String existingSetId = null;
        try (Cursor cursor = db().rawQuery(
                "SELECT id FROM workout_sets WHERE workout_exercise_id = ? AND user_id = ? " +
                        "AND deleted_at IS NULL ORDER BY set_index LIMIT 1",
                new String[]{exerciseId, userId()})) {
            if (cursor.moveToFirst()) existingSetId = cursor.getString(0);
        }
        ContentValues set = new ContentValues();
        set.putNull("target_reps");
        set.putNull("actual_reps");
        set.putNull("weight_kg");
        set.put("volume_kg", 0d);
        set.put("duration_seconds", durationSeconds);
        set.put("distance_meters", distanceMeters);
        set.putNull("rest_seconds");
        set.putNull("assisted_weight_kg");
        set.putNull("added_weight_kg");
        set.putNull("input_load_value");
        set.putNull("input_load_unit");
        set.putNull("load_state");
        set.put("is_completed", 1);
        set.putNull("rpe");
        set.putNull("rir");
        set.putNull("memo");
        set.put("updated_at", timestamp);
        if (existingSetId == null) {
            set.put("id", UUID.randomUUID().toString());
            set.put("user_id", userId());
            set.put("workout_exercise_id", exerciseId);
            set.put("set_index", 1);
            set.put("created_at", timestamp);
            set.putNull("deleted_at");
            set.put("device_id", "android-local");
            set.put("contract_version", 1);
            db().insertOrThrow("workout_sets", null, set);
        } else {
            db().update("workout_sets", set, "id = ? AND user_id = ? AND deleted_at IS NULL",
                    new String[]{existingSetId, userId()});
        }

        ContentValues record = new ContentValues();
        record.put("duration_seconds", durationSeconds);
        record.put("total_volume_kg", 0d);
        if (averageHeartRateBpm == null) record.putNull("average_heart_rate");
        else record.put("average_heart_rate", averageHeartRateBpm);
        record.put("exercise_name", activityType.labelKo());
        record.put("category", activityType.labelKo());
        record.put("scope", "both");
        record.put("updated_at", timestamp);
        String metadata = readWorkoutMetadata(recordId);
        record.put("metadata", cardioMetadata("completed", activityType, timestamp,
                durationSeconds, distanceMeters, averageHeartRateBpm, metadata));
        db().update("workout_records", record, "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{recordId, userId()});
    }

    private boolean updateCardioRecordHeartRate(String recordId, Integer averageHeartRateBpm) {
        if (averageHeartRateBpm != null && averageHeartRateBpm <= 0) {
            throw new IllegalArgumentException("평균 심박수는 0보다 커야 합니다.");
        }
        ContentValues values = new ContentValues();
        if (averageHeartRateBpm == null) values.putNull("average_heart_rate");
        else values.put("average_heart_rate", averageHeartRateBpm);
        values.put("metadata", metadataWithAverageHeartRate(readWorkoutMetadata(recordId), averageHeartRateBpm));
        values.put("updated_at", now());
        return db().update("workout_records", values,
                "id = ? AND user_id = ? AND workout_type = 'cardio' AND deleted_at IS NULL",
                new String[]{recordId, userId()}) == 1;
    }

    private void softDeleteWorkoutRecord(String recordId) {
        ContentValues values = new ContentValues();
        values.put("deleted_at", now());
        values.put("updated_at", now());
        db().update("workout_sets", values,
                "workout_exercise_id IN (SELECT id FROM workout_exercises WHERE record_id = ? AND user_id = ?) " +
                        "AND user_id = ? AND deleted_at IS NULL",
                new String[]{recordId, userId(), userId()});
        db().update("workout_exercises", values,
                "record_id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{recordId, userId()});
        db().update("workout_records", values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{recordId, userId()});
    }

    private String readWorkoutMetadata(String recordId) {
        try (Cursor cursor = db().rawQuery(
                "SELECT metadata FROM workout_records WHERE id = ? AND user_id = ? LIMIT 1",
                new String[]{recordId, userId()})) {
            return cursor.moveToFirst() ? cursor.getString(0) : "{}";
        }
    }

    private static String cardioMetadata(String status, CardioActivityType activityType,
                                         String endedAt, int durationSeconds,
                                         double distanceMeters, Integer averageHeartRateBpm) {
        return cardioMetadata(status, activityType, endedAt, durationSeconds, distanceMeters,
                averageHeartRateBpm, "{}");
    }

    private static String cardioMetadata(String status, CardioActivityType activityType,
                                         String endedAt, int durationSeconds,
                                         double distanceMeters, Integer averageHeartRateBpm,
                                         String existing) {
        try {
            JSONObject object = new JSONObject(existing == null ? "{}" : existing);
            object.put("status", status);
            object.put("activity_type", activityType.id());
            object.put("ended_at", endedAt == null ? "" : endedAt);
            object.put("duration_seconds", durationSeconds);
            object.put("active_duration_seconds", durationSeconds);
            object.put("distance_meters", distanceMeters);
            object.put("average_heart_rate", averageHeartRateBpm == null ? JSONObject.NULL : averageHeartRateBpm);
            object.put("average_pace_seconds_per_km", distanceMeters <= 0 ? JSONObject.NULL :
                    Math.round(durationSeconds / (distanceMeters / 1000d)));
            object.put("contract_version", FitnessRecordContract.VERSION);
            return object.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static String metadataWithAverageHeartRate(String metadata, Integer averageHeartRateBpm) {
        try {
            JSONObject object = new JSONObject(metadata == null ? "{}" : metadata);
            object.put("average_heart_rate", averageHeartRateBpm == null ? JSONObject.NULL : averageHeartRateBpm);
            return object.toString();
        } catch (Exception ignored) {
            return metadata == null ? "{}" : metadata;
        }
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    private FitnessDatabaseConnection db() {
        return database;
    }

    private String userId() {
        return userId;
    }

    private void requireScope(AccountScope scope) {
        if (scope == null || !scope.getOwnerId().equals(userId())) {
            throw new IllegalStateException("The account changed while the cardio operation was pending.");
        }
    }

    private static int safeSeconds(long durationMillis) {
        long seconds = Math.max(0, durationMillis / 1000L);
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    public static final class SessionSnapshot {
        public final String recordId;
        public final CardioActivityType activityType;
        public final String status;
        public final long startedAtEpochMillis;
        public final Long lastResumedAtEpochMillis;
        public final long activeDurationMillis;
        public final double distanceMeters;
        public final int acceptedPointCount;
        public final String gpsStatus;
        public final Double averageHeartRateBpm;

        SessionSnapshot(
                String recordId,
                CardioActivityType activityType,
                String status,
                long startedAtEpochMillis,
                Long lastResumedAtEpochMillis,
                long activeDurationMillis,
                double distanceMeters,
                int acceptedPointCount,
                String gpsStatus,
                Double averageHeartRateBpm
        ) {
            this.recordId = recordId;
            this.activityType = activityType;
            this.status = status;
            this.startedAtEpochMillis = startedAtEpochMillis;
            this.lastResumedAtEpochMillis = lastResumedAtEpochMillis;
            this.activeDurationMillis = activeDurationMillis;
            this.distanceMeters = distanceMeters;
            this.acceptedPointCount = acceptedPointCount;
            this.gpsStatus = gpsStatus;
            this.averageHeartRateBpm = averageHeartRateBpm;
        }

        public long elapsedDurationMillis(long nowEpochMillis) {
            if (!STATUS_TRACKING.equals(status) || lastResumedAtEpochMillis == null) {
                return activeDurationMillis;
            }
            return activeDurationMillis + Math.max(0, nowEpochMillis - lastResumedAtEpochMillis);
        }

        public int elapsedSeconds(long nowEpochMillis) {
            return safeSeconds(elapsedDurationMillis(nowEpochMillis));
        }
    }
}
