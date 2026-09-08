package com.yeonsik.fitnessapp.cardio;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.CardioRoomDao;
import com.yeonsik.fitnessapp.core.database.CardioSessionsRoomEntity;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.WorkoutExercisesRoomEntity;
import com.yeonsik.fitnessapp.core.database.WorkoutRecordsRoomEntity;
import com.yeonsik.fitnessapp.core.database.WorkoutRoomDao;
import com.yeonsik.fitnessapp.core.database.WorkoutSetsRoomEntity;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi;
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot;

import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GPS 유산소의 실행 상태와 원시 좌표를 로컬 Room 데이터베이스에 저장한다.
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

    private final FitnessRoomDatabase roomDatabase;
    private final CardioRoomDao cardioDao;
    private final WorkoutRoomDao workoutDao;
    private String userId;

    /** Temporary source-compatible adapter for legacy callers; storage still uses Room. */
    @Deprecated
    public CardioRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this(FitnessRoomDatabaseProvider.get(dbHelper.applicationContext()), userId);
    }

    public CardioRepository(FitnessRoomDatabase roomDatabase, String userId) {
        this.roomDatabase = roomDatabase;
        this.cardioDao = roomDatabase.cardioRoomDao();
        this.workoutDao = roomDatabase.workoutRoomDao();
        this.userId = userId;
    }

    /** Temporary source-compatible overload for the foreground service. */
    @Deprecated
    public CardioRepository(FitnessRoomDatabase roomDatabase, String userId,
                            android.content.Context ignoredContext) {
        this(roomDatabase, userId);
    }

    /** Account boundary updates the owner after local rows have been claimed separately. */
    public void setUserId(String userId) {
        String normalized = userId == null ? "" : userId.trim();
        this.userId = normalized.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : normalized;
    }

    public String startSession(CardioActivityType activityType, String date) {
        if (activityType == null) {
            throw new IllegalArgumentException("유산소 유형이 필요합니다.");
        }
        SessionSnapshot active = activeSession();
        if (active != null) {
            return active.recordId;
        }

        final String[] recordId = new String[1];
        roomDatabase.runInTransaction(() -> {
            recordId[0] = createCardioRecord(date, activityType);
            long now = System.currentTimeMillis();
            cardioDao.insertSession(new CardioSessionsRoomEntity(
                    recordId[0],
                    userId(),
                    activityType.id(),
                    STATUS_TRACKING,
                    now,
                    now,
                    0L,
                    0d,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    GPS_SEARCHING,
                    now
            ));
        });
        return recordId[0];
    }

    public SessionSnapshot activeSession() {
        String recordId = cardioDao.activeRecordId(userId());
        return recordId == null ? null : session(recordId);
    }

    public boolean isCardioSession(String recordId) {
        return recordId != null
                && !recordId.trim().isEmpty()
                && cardioDao.ownsSession(recordId, userId()) != null;
    }

    public SessionSnapshot session(String recordId) {
        if (recordId == null || recordId.trim().isEmpty()) {
            return null;
        }
        CardioRoomDao.SessionRow row = cardioDao.session(recordId, userId());
        if (row == null) {
            return null;
        }
        return new SessionSnapshot(
                row.getRecordId(),
                CardioActivityType.fromId(row.getActivityType()),
                row.getStatus(),
                row.getStartedAtEpochMs(),
                row.getLastResumedAtEpochMs(),
                row.getActiveDurationMs(),
                row.getDistanceMeters(),
                safeInt(row.getAcceptedPointCount()),
                row.getGpsStatus(),
                row.getAverageHeartRate()
        );
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
        return routeProjection(recordId, CardioRouteProjection.DEFAULT_MAX_DISPLAY_POINTS);
    }

    public CardioRouteProjection routeProjection(String recordId, int maxDisplayPoints) {
        if (recordId == null || recordId.trim().isEmpty()) {
            return CardioRouteProjection.empty();
        }

        int rawPointCount = cardioDao.routePointCount(recordId, userId());
        if (rawPointCount == 0) {
            return CardioRouteProjection.empty();
        }

        CardioRouteProjection.Builder builder = CardioRouteProjection.builder(
                rawPointCount,
                maxDisplayPoints
        );
        for (CardioRoomDao.RoutePointRow row : cardioDao.routePoints(recordId, userId())) {
            builder.add(row.getCapturedAtEpochMs(), row.getLatitude(), row.getLongitude());
        }
        return builder.build();
    }

    public boolean pause(String recordId) {
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null || !STATUS_TRACKING.equals(snapshot.status)) {
            return false;
        }
        long now = System.currentTimeMillis();
        return cardioDao.pause(recordId, userId(), snapshot.elapsedDurationMillis(now), now) == 1;
    }

    public boolean resume(String recordId) {
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null || !STATUS_PAUSED.equals(snapshot.status)) {
            return false;
        }
        long now = System.currentTimeMillis();
        return cardioDao.resume(recordId, userId(), now, now) == 1;
    }

    public CardioDistanceFilter.Result acceptLocation(
            String recordId,
            CardioLocationSample candidate
    ) {
        final CardioDistanceFilter.Result[] result = new CardioDistanceFilter.Result[1];
        roomDatabase.runInTransaction(() -> result[0] = acceptLocationInTransaction(recordId, candidate));
        return result[0] == null
                ? CardioDistanceFilter.Result.rejected(CardioDistanceFilter.Reason.INVALID)
                : result[0];
    }

    private CardioDistanceFilter.Result acceptLocationInTransaction(
            String recordId,
            CardioLocationSample candidate
    ) {
        CardioRoomDao.SessionRow state = cardioDao.session(recordId, userId());
        if (state == null || !STATUS_TRACKING.equals(state.getStatus())) {
            return CardioDistanceFilter.Result.rejected(CardioDistanceFilter.Reason.INVALID);
        }

        CardioLocationSample previous = null;
        if (state.getLastLatitude() != null
                && state.getLastLongitude() != null
                && state.getLastLocationTimeMs() != null
                && state.getLastAccuracyMeters() != null) {
            previous = new CardioLocationSample(
                    state.getLastLatitude(),
                    state.getLastLongitude(),
                    state.getLastAccuracyMeters().floatValue(),
                    state.getLastLocationTimeMs(),
                    null
            );
        }

        if (candidate != null
                && candidate.capturedAtMillis != null
                && state.getLastResumedAtEpochMs() != null
                && candidate.capturedAtMillis < state.getLastResumedAtEpochMs()) {
            return CardioDistanceFilter.Result.rejected(CardioDistanceFilter.Reason.STALE);
        }

        CardioDistanceFilter.Result result = CardioDistanceFilter.evaluate(
                CardioActivityType.fromId(state.getActivityType()), previous, candidate);
        if (!result.accepted) {
            if (result.reason == CardioDistanceFilter.Reason.INACCURATE
                    || result.reason == CardioDistanceFilter.Reason.TOO_FAST) {
                updateGpsStatus(recordId, GPS_WEAK);
            }
            return result;
        }

        cardioDao.insertRoutePoint(
                recordId,
                userId(),
                candidate.capturedAtMillis,
                candidate.latitude,
                candidate.longitude,
                candidate.accuracyMeters,
                candidate.reportedSpeedMetersPerSecond == null
                        ? null
                        : candidate.reportedSpeedMetersPerSecond.doubleValue(),
                result.segmentDistanceMeters
        );
        cardioDao.acceptLocation(
                recordId,
                userId(),
                state.getDistanceMeters() + result.segmentDistanceMeters,
                state.getAcceptedPointCount() + 1L,
                candidate.latitude,
                candidate.longitude,
                candidate.capturedAtMillis,
                candidate.accuracyMeters,
                System.currentTimeMillis()
        );
        return result;
    }

    public void setGpsStatus(String recordId, String gpsStatus) {
        updateGpsStatus(recordId, gpsStatus);
    }

    private void updateGpsStatus(String recordId, String gpsStatus) {
        cardioDao.updateGpsStatus(recordId, userId(), gpsStatus, System.currentTimeMillis());
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
        roomDatabase.runInTransaction(() -> {
            completeCardioRecord(
                    recordId,
                    snapshot.activityType,
                    durationSeconds,
                    snapshot.distanceMeters,
                    averageHeartRateBpm
            );
            cardioDao.complete(recordId, userId(), durationMillis, now);
        });
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
        roomDatabase.runInTransaction(() -> {
            cardioDao.deleteRoutePoints(recordId, userId());
            cardioDao.deleteSession(recordId, userId());
            softDeleteWorkoutRecord(recordId);
        });
    }

    public void deleteLocalData(String recordId) {
        if (!isCardioSession(recordId)) {
            return;
        }
        roomDatabase.runInTransaction(() -> {
            cardioDao.deleteRoutePoints(recordId, userId());
            cardioDao.deleteSession(recordId, userId());
        });
    }

    private String createCardioRecord(String date, CardioActivityType activityType) {
        String recordId = UUID.randomUUID().toString();
        String timestamp = now();
        String recordDate = date == null || date.trim().isEmpty()
                ? timestamp.substring(0, 10)
                : date;
        workoutDao.insertRecord(new WorkoutRecordsRoomEntity(
                recordId,
                userId(),
                recordDate,
                "cardio",
                activityType.labelKo(),
                activityType.labelKo(),
                null,
                0d,
                null,
                timestamp,
                0L,
                null,
                null,
                timestamp,
                null,
                "android-local",
                "fitness",
                "fitness",
                cardioMetadata("in_progress", activityType, "", 0, 0d, null),
                FitnessRecordContract.VERSION
        ));
        workoutDao.insertExercise(new WorkoutExercisesRoomEntity(
                UUID.randomUUID().toString(),
                userId(),
                recordId,
                1L,
                "cardio_" + activityType.id(),
                activityType.labelKo(),
                "cardio",
                activityType.labelKo(),
                null,
                FitnessRecordContract.TIME,
                null,
                null,
                null,
                null,
                null,
                timestamp,
                timestamp,
                null,
                "android-local",
                FitnessRecordContract.VERSION
        ));
        return recordId;
    }

    private void completeCardioRecord(
            String recordId,
            CardioActivityType activityType,
            int durationSeconds,
            double distanceMeters,
            Integer averageHeartRateBpm
    ) {
        List<WorkoutExercisesRoomEntity> exercises = workoutDao.visibleExercises(recordId, userId());
        if (exercises.isEmpty()) {
            throw new IllegalStateException("유산소 세부 종목을 찾지 못했습니다.");
        }
        WorkoutExercisesRoomEntity exercise = exercises.get(0);
        List<WorkoutSetsRoomEntity> sets = workoutDao.visibleSets(exercise.getId(), userId());
        String timestamp = now();
        if (sets.isEmpty()) {
            workoutDao.insertSet(new WorkoutSetsRoomEntity(
                    UUID.randomUUID().toString(),
                    userId(),
                    exercise.getId(),
                    1L,
                    null,
                    null,
                    null,
                    0d,
                    (long) durationSeconds,
                    distanceMeters,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    1L,
                    null,
                    null,
                    null,
                    timestamp,
                    timestamp,
                    null,
                    "android-local",
                    FitnessRecordContract.VERSION
            ));
        } else {
            workoutDao.updateCardioSet(
                    sets.get(0).getId(),
                    userId(),
                    durationSeconds,
                    distanceMeters,
                    timestamp
            );
        }

        String metadata = cardioMetadata(
                "completed",
                activityType,
                timestamp,
                durationSeconds,
                distanceMeters,
                averageHeartRateBpm,
                readWorkoutMetadata(recordId)
        );
        workoutDao.completeCardioRecord(
                recordId,
                userId(),
                metadata,
                durationSeconds,
                averageHeartRateBpm == null ? null : averageHeartRateBpm.doubleValue(),
                activityType.labelKo(),
                activityType.labelKo(),
                timestamp
        );
    }

    private boolean updateCardioRecordHeartRate(String recordId, Integer averageHeartRateBpm) {
        if (averageHeartRateBpm != null && averageHeartRateBpm <= 0) {
            throw new IllegalArgumentException("평균 심박수는 0보다 커야 합니다.");
        }
        WorkoutRecordsRoomEntity record = workoutDao.visibleRecord(recordId, userId());
        if (record == null || !"cardio".equals(record.getWorkoutType())) {
            return false;
        }
        String metadata = metadataWithAverageHeartRate(record.getMetadata(), averageHeartRateBpm);
        return workoutDao.updateCardioHeartRate(
                recordId,
                userId(),
                averageHeartRateBpm == null ? null : averageHeartRateBpm.doubleValue(),
                metadata,
                now()
        ) == 1;
    }

    private void softDeleteWorkoutRecord(String recordId) {
        String timestamp = now();
        for (WorkoutExercisesRoomEntity exercise : workoutDao.visibleExercises(recordId, userId())) {
            workoutDao.tombstoneSetsForExercise(
                    exercise.getId(), userId(), timestamp, timestamp
            );
        }
        workoutDao.tombstoneExercisesForRecord(recordId, userId(), timestamp, timestamp);
        workoutDao.tombstoneRecord(recordId, userId(), timestamp, timestamp);
    }

    private String readWorkoutMetadata(String recordId) {
        WorkoutRecordsRoomEntity record = workoutDao.visibleRecord(recordId, userId());
        return record == null || record.getMetadata() == null ? "{}" : record.getMetadata();
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
            object.put("average_heart_rate", averageHeartRateBpm == null
                    ? JSONObject.NULL : averageHeartRateBpm);
            object.put("average_pace_seconds_per_km", distanceMeters <= 0
                    ? JSONObject.NULL
                    : Math.round(durationSeconds / (distanceMeters / 1000d)));
            object.put("contract_version", FitnessRecordContract.VERSION);
            return object.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static String metadataWithAverageHeartRate(String metadata, Integer averageHeartRateBpm) {
        try {
            JSONObject object = new JSONObject(metadata == null ? "{}" : metadata);
            object.put("average_heart_rate", averageHeartRateBpm == null
                    ? JSONObject.NULL : averageHeartRateBpm);
            return object.toString();
        } catch (Exception ignored) {
            return metadata == null ? "{}" : metadata;
        }
    }

    private static String now() {
        return OffsetDateTime.now().toString();
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

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
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
