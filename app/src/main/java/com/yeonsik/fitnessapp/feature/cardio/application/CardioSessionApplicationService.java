package com.yeonsik.fitnessapp.feature.cardio.application;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner;
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi;
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi;

/**
 * Application use cases for the GPS session lifecycle.
 *
 * CardioTrackingService still talks to the concrete repository because it is the platform
 * location callback owner. Screen hosts receive only this feature snapshot and projections.
 */
public final class CardioSessionApplicationService {
    private final CardioRepositoryApi repository;
    private final WorkoutRepositoryApi workoutRepository;
    private final RoomTransactionRunner transactionRunner;
    private final CompleteCardio completeCardio;
    private volatile String ownerId;

    public CardioSessionApplicationService(
            CardioRepositoryApi repository,
            WorkoutRepositoryApi workoutRepository,
            RoomTransactionRunner transactionRunner,
            String ownerId
    ) {
        if (repository == null || workoutRepository == null || transactionRunner == null) {
            throw new IllegalArgumentException("유산소 서비스 의존성이 없습니다.");
        }
        this.repository = repository;
        this.workoutRepository = workoutRepository;
        this.transactionRunner = transactionRunner;
        this.completeCardio = new CompleteCardio(repository, workoutRepository, transactionRunner);
        this.ownerId = requireOwner(ownerId);
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = requireOwner(ownerId);
    }

    public CardioSessionSnapshot load(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.loadSession(scope, recordId);
    }

    public boolean isCardioSession(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.isCardioSession(scope, recordId);
    }

    public CardioRouteProjection route(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.routeProjection(scope, recordId);
    }

    public CardioSessionSnapshot start(
            AccountScope scope,
            CardioActivityType activityType,
            String date
    ) {
        requireScope(scope);
        final String[] recordId = new String[1];
        transactionRunner.run(() -> {
            recordId[0] = workoutRepository.createCardioSession(
                    scope,
                    date,
                    activityType.id(),
                    activityType.labelKo()
            );
            if (!repository.startSession(scope, recordId[0], activityType)) {
                throw new IllegalStateException("GPS 유산소 세션을 시작하지 못했습니다.");
            }
        });
        return repository.loadSession(scope, recordId[0]);
    }

    public boolean pause(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.pause(scope, recordId);
    }

    public boolean resume(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.resume(scope, recordId);
    }

    public CardioSessionSnapshot finish(
            AccountScope scope,
            String recordId,
            Integer averageHeartRateBpm
    ) {
        requireScope(scope);
        CardioSessionSnapshot snapshot = repository.loadSession(scope, recordId);
        if (snapshot == null) {
            return null;
        }
        completeCardio.execute(scope, snapshot, averageHeartRateBpm);
        return repository.loadSession(scope, recordId);
    }

    public CardioSessionSnapshot updateAverageHeartRate(
            AccountScope scope,
            String recordId,
            Integer averageHeartRateBpm
    ) {
        requireScope(scope);
        if (!workoutRepository.updateCardioAverageHeartRate(
                scope,
                recordId,
                averageHeartRateBpm
        )) {
            return null;
        }
        return repository.loadSession(scope, recordId);
    }

    public void cancel(AccountScope scope, String recordId) {
        requireScope(scope);
        if (!repository.isCardioSession(scope, recordId)) {
            return;
        }
        transactionRunner.run(() -> {
            if (!repository.deleteLocalData(scope, recordId)) {
                throw new IllegalStateException("GPS 유산소 데이터를 취소하지 못했습니다.");
            }
            if (!workoutRepository.deleteSession(scope, recordId)) {
                throw new IllegalStateException("공통 유산소 기록을 취소하지 못했습니다.");
            }
        });
    }

    public boolean deleteLocalData(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.deleteLocalData(scope, recordId);
    }

    private void requireScope(AccountScope scope) {
        if (scope == null || !ownerId.equals(scope.getOwnerId())) {
            throw new IllegalStateException("계정이 변경된 뒤 유산소 작업이 도착했습니다.");
        }
    }

    private static String requireOwner(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("유산소 계정 식별자가 필요합니다.");
        }
        return normalized;
    }
}
