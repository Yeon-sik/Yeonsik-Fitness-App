package com.yeonsik.fitnessapp.feature.cardio.application;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot;

/**
 * Application use cases for the GPS session lifecycle.
 *
 * CardioTrackingService still talks to the concrete repository because it is the platform
 * location callback owner. Screen hosts receive only this feature snapshot and projections.
 */
public final class CardioSessionApplicationService {
    private final CardioRepository repository;
    private volatile String ownerId;

    public CardioSessionApplicationService(CardioRepository repository, String ownerId) {
        if (repository == null) {
            throw new IllegalArgumentException("유산소 저장소가 필요합니다.");
        }
        this.repository = repository;
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
        return repository.isCardioSession(recordId);
    }

    public CardioRouteProjection route(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.routeProjection(recordId);
    }

    public CardioSessionSnapshot start(
            AccountScope scope,
            CardioActivityType activityType,
            String date
    ) {
        requireScope(scope);
        String recordId = repository.startSession(activityType, date);
        return repository.loadSession(scope, recordId);
    }

    public boolean pause(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.pause(recordId);
    }

    public boolean resume(AccountScope scope, String recordId) {
        requireScope(scope);
        return repository.resume(recordId);
    }

    public CardioSessionSnapshot finish(
            AccountScope scope,
            String recordId,
            Integer averageHeartRateBpm
    ) {
        requireScope(scope);
        repository.finish(recordId, averageHeartRateBpm);
        return repository.loadSession(scope, recordId);
    }

    public CardioSessionSnapshot updateAverageHeartRate(
            AccountScope scope,
            String recordId,
            Integer averageHeartRateBpm
    ) {
        requireScope(scope);
        repository.updateAverageHeartRate(recordId, averageHeartRateBpm);
        return repository.loadSession(scope, recordId);
    }

    public void cancel(AccountScope scope, String recordId) {
        requireScope(scope);
        repository.cancel(recordId);
    }

    public void deleteLocalData(AccountScope scope, String recordId) {
        requireScope(scope);
        repository.deleteLocalData(recordId);
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
