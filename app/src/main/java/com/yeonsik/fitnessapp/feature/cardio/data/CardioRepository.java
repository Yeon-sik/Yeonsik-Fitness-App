package com.yeonsik.fitnessapp.feature.cardio.data;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.cardio.CardioDistanceFilter;
import com.yeonsik.fitnessapp.cardio.CardioLocationSample;
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.CardioRoomDao;
import com.yeonsik.fitnessapp.core.database.CardioSessionsRoomEntity;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi;
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * GPS 유산소의 실행 상태와 원시 좌표를 로컬 Room 데이터베이스에 저장한다.
 * 공유 가능한 Workout 기록은 Workout API가 소유하며 이 저장소는 GPS 행만 변경한다.
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

    private final CardioRoomDao cardioDao;
    private final RoomTransactionRunner transactionRunner;
    private String userId;

    /** Temporary source-compatible adapter for legacy callers; storage still uses Room. */
    @Deprecated
    public CardioRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this(FitnessRoomDatabaseProvider.get(dbHelper.applicationContext()), userId);
    }

    public CardioRepository(FitnessRoomDatabase roomDatabase, String userId) {
        this(roomDatabase, userId, new RoomTransactionRunner(roomDatabase));
    }

    public CardioRepository(
            FitnessRoomDatabase roomDatabase,
            String userId,
            RoomTransactionRunner transactionRunner
    ) {
        this.cardioDao = roomDatabase.cardioRoomDao();
        this.transactionRunner = transactionRunner;
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

    /**
     * Legacy adapter retained for the foreground/service fixture. New callers must
     * create the shared Workout record first and use the scoped API below.
     */
    @Deprecated
    public String startSession(CardioActivityType activityType, String date) {
        if (activityType == null) {
            throw new IllegalArgumentException("유산소 유형이 필요합니다.");
        }
        SessionSnapshot active = activeSession();
        if (active != null) {
            return active.recordId;
        }

        String recordId = UUID.randomUUID().toString();
        transactionRunner.run(() -> startSessionInTransaction(recordId, activityType));
        return recordId;
    }

    @Override
    public boolean startSession(
            AccountScope scope,
            String recordId,
            CardioActivityType activityType
    ) {
        requireScope(scope);
        if (recordId == null || recordId.trim().isEmpty() || activityType == null) {
            return false;
        }
        SessionSnapshot existing = session(recordId);
        if (existing != null) {
            return true;
        }
        SessionSnapshot active = activeSession();
        if (active != null && !recordId.equals(active.recordId)) {
            return false;
        }
        startSessionInTransaction(recordId, activityType);
        return true;
    }

    private void startSessionInTransaction(String recordId, CardioActivityType activityType) {
        long now = System.currentTimeMillis();
        cardioDao.insertSession(new CardioSessionsRoomEntity(
                recordId,
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

    @Override
    public boolean isCardioSession(AccountScope scope, String recordId) {
        requireScope(scope);
        return isCardioSession(recordId);
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

    @Override
    public CardioRouteProjection routeProjection(AccountScope scope, String recordId) {
        requireScope(scope);
        return routeProjection(recordId);
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

    @Override
    public boolean pause(AccountScope scope, String recordId) {
        requireScope(scope);
        return pause(recordId);
    }

    public boolean resume(String recordId) {
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null || !STATUS_PAUSED.equals(snapshot.status)) {
            return false;
        }
        long now = System.currentTimeMillis();
        return cardioDao.resume(recordId, userId(), now, now) == 1;
    }

    @Override
    public boolean resume(AccountScope scope, String recordId) {
        requireScope(scope);
        return resume(recordId);
    }

    public CardioDistanceFilter.Result acceptLocation(
            String recordId,
            CardioLocationSample candidate
    ) {
        final CardioDistanceFilter.Result[] result = new CardioDistanceFilter.Result[1];
        transactionRunner.run(() -> result[0] = acceptLocationInTransaction(recordId, candidate));
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

        long durationMillis = snapshot.elapsedDurationMillis(System.currentTimeMillis());
        transactionRunner.run(() -> {
            if (!completeSessionInTransaction(recordId, durationMillis)) {
                throw new IllegalStateException("GPS 유산소 세션을 완료하지 못했습니다.");
            }
        });
        return session(recordId);
    }

    @Override
    public boolean completeSession(
            AccountScope scope,
            String recordId,
            long activeDurationMillis
    ) {
        requireScope(scope);
        return completeSessionInTransaction(recordId, activeDurationMillis);
    }

    private boolean completeSessionInTransaction(String recordId, long activeDurationMillis) {
        SessionSnapshot snapshot = session(recordId);
        if (snapshot == null) {
            return false;
        }
        if (STATUS_COMPLETED.equals(snapshot.status)) {
            return true;
        }
        return cardioDao.complete(
                recordId,
                userId(),
                Math.max(0L, activeDurationMillis),
                System.currentTimeMillis()
        ) == 1;
    }

    @Deprecated
    public SessionSnapshot updateAverageHeartRate(
            String recordId,
            Integer averageHeartRateBpm
    ) {
        throw new UnsupportedOperationException(
                "Use WorkoutRepositoryApi.updateCardioAverageHeartRate()."
        );
    }

    @Deprecated
    public void cancel(String recordId) {
        if (!isCardioSession(recordId)) {
            return;
        }
        transactionRunner.run(() -> deleteLocalDataInTransaction(recordId));
    }

    public void deleteLocalData(String recordId) {
        if (!isCardioSession(recordId)) {
            return;
        }
        transactionRunner.run(() -> deleteLocalDataInTransaction(recordId));
    }

    @Override
    public boolean deleteLocalData(AccountScope scope, String recordId) {
        requireScope(scope);
        if (!isCardioSession(recordId)) {
            return false;
        }
        return deleteLocalDataInTransaction(recordId);
    }

    private boolean deleteLocalDataInTransaction(String recordId) {
        int routeRows = cardioDao.deleteRoutePoints(recordId, userId());
        int sessionRows = cardioDao.deleteSession(recordId, userId());
        return routeRows > 0 || sessionRows > 0;
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
