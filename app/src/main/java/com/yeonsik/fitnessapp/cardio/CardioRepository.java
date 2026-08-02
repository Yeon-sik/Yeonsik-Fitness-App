package com.yeonsik.fitnessapp.cardio;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;

/**
 * GPS 유산소의 실행 상태와 원시 좌표를 로컬 SQLite에 저장한다.
 * 공유 가능한 완료 요약은 FitnessRepository를 통해 기존 Fitness Record Contract에 기록한다.
 */
public final class CardioRepository {
    public static final String STATUS_TRACKING = "tracking";
    public static final String STATUS_PAUSED = "paused";
    public static final String STATUS_COMPLETED = "completed";

    public static final String GPS_SEARCHING = "searching";
    public static final String GPS_READY = "ready";
    public static final String GPS_WEAK = "weak";
    public static final String GPS_PERMISSION_MISSING = "permission_missing";
    public static final String GPS_UNAVAILABLE = "unavailable";
    public static final String GPS_STOPPED = "stopped";

    private final FitnessDatabaseHelper dbHelper;
    private final FitnessRepository fitnessRepository;

    public CardioRepository(FitnessDatabaseHelper dbHelper, FitnessRepository fitnessRepository) {
        this.dbHelper = dbHelper;
        this.fitnessRepository = fitnessRepository;
    }

    public String startSession(CardioActivityType activityType, String date) {
        if (activityType == null) {
            throw new IllegalArgumentException("유산소 유형이 필요합니다.");
        }
        SessionSnapshot active = activeSession();
        if (active != null) {
            return active.recordId;
        }

        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            String recordId = fitnessRepository.createCardioSession(date, activityType);
            long now = System.currentTimeMillis();
            ContentValues values = new ContentValues();
            values.put("record_id", recordId);
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
                "SELECT record_id FROM cardio_sessions WHERE status IN (?, ?) "
                        + "ORDER BY started_at_epoch_ms DESC LIMIT 1",
                new String[]{STATUS_TRACKING, STATUS_PAUSED})) {
            return cursor.moveToFirst() ? session(cursor.getString(0)) : null;
        }
    }

    public boolean isCardioSession(String recordId) {
        if (recordId == null || recordId.trim().isEmpty()) {
            return false;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT 1 FROM cardio_sessions WHERE record_id = ? LIMIT 1",
                new String[]{recordId})) {
            return cursor.moveToFirst();
        }
    }

    public SessionSnapshot session(String recordId) {
        if (recordId == null || recordId.trim().isEmpty()) {
            return null;
        }
        try (Cursor cursor = db().rawQuery(
                "SELECT activity_type, status, started_at_epoch_ms, last_resumed_at_epoch_ms, "
                        + "active_duration_ms, distance_meters, accepted_point_count, gps_status "
                        + "FROM cardio_sessions WHERE record_id = ? LIMIT 1",
                new String[]{recordId})) {
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
                    cursor.getString(7)
            );
        }
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
                "record_id = ? AND status = ?", new String[]{recordId, STATUS_TRACKING}) == 1;
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
                "record_id = ? AND status = ?", new String[]{recordId, STATUS_PAUSED}) == 1;
    }

    public CardioDistanceFilter.Result acceptLocation(
            String recordId,
            CardioLocationSample candidate
    ) {
        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            CardioActivityType activityType;
            double totalDistance;
            int acceptedPointCount;
            CardioLocationSample previous = null;
            try (Cursor cursor = database.rawQuery(
                    "SELECT activity_type, status, distance_meters, accepted_point_count, "
                            + "last_latitude, last_longitude, last_location_time_ms, last_accuracy_meters "
                            + "FROM cardio_sessions WHERE record_id = ? LIMIT 1",
                    new String[]{recordId})) {
                if (!cursor.moveToFirst() || !STATUS_TRACKING.equals(cursor.getString(1))) {
                    return CardioDistanceFilter.Result.rejected(CardioDistanceFilter.Reason.INVALID);
                }
                activityType = CardioActivityType.fromId(cursor.getString(0));
                totalDistance = cursor.getDouble(2);
                acceptedPointCount = cursor.getInt(3);
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
            database.update("cardio_sessions", state, "record_id = ?", new String[]{recordId});
            database.setTransactionSuccessful();
            return result;
        } finally {
            database.endTransaction();
        }
    }

    public void setGpsStatus(String recordId, String gpsStatus) {
        updateGpsStatus(db(), recordId, gpsStatus);
    }

    public SessionSnapshot finish(String recordId) {
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long durationMillis = snapshot.elapsedDurationMillis(now);
        int durationSeconds = Math.max(1, safeSeconds(durationMillis));
        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            fitnessRepository.completeCardioSession(
                    recordId,
                    snapshot.activityType,
                    durationSeconds,
                    snapshot.distanceMeters
            );

            ContentValues values = new ContentValues();
            values.put("status", STATUS_COMPLETED);
            values.put("active_duration_ms", durationMillis);
            values.putNull("last_resumed_at_epoch_ms");
            values.put("gps_status", GPS_STOPPED);
            values.put("updated_at_epoch_ms", now);
            database.update("cardio_sessions", values, "record_id = ?", new String[]{recordId});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return session(recordId);
    }

    public void cancel(String recordId) {
        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            database.delete("cardio_route_points", "record_id = ?", new String[]{recordId});
            database.delete("cardio_sessions", "record_id = ?", new String[]{recordId});
            fitnessRepository.deleteSession(recordId);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void deleteLocalData(String recordId) {
        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            database.delete("cardio_route_points", "record_id = ?", new String[]{recordId});
            database.delete("cardio_sessions", "record_id = ?", new String[]{recordId});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private void updateGpsStatus(SQLiteDatabase database, String recordId, String gpsStatus) {
        ContentValues values = new ContentValues();
        values.put("gps_status", gpsStatus);
        values.put("updated_at_epoch_ms", System.currentTimeMillis());
        database.update("cardio_sessions", values,
                "record_id = ? AND status IN (?, ?)",
                new String[]{recordId, STATUS_TRACKING, STATUS_PAUSED});
    }

    private SQLiteDatabase db() {
        return dbHelper.getWritableDatabase();
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

        SessionSnapshot(
                String recordId,
                CardioActivityType activityType,
                String status,
                long startedAtEpochMillis,
                Long lastResumedAtEpochMillis,
                long activeDurationMillis,
                double distanceMeters,
                int acceptedPointCount,
                String gpsStatus
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
