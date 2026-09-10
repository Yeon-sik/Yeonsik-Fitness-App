package com.yeonsik.fitnessapp.feature.cardio.application;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner;
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi;
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi;

/**
 * Coordinates the two halves of a cardio completion without making either
 * feature repository depend on the other.
 */
public final class CompleteCardio {
    private final CardioRepositoryApi cardioRepository;
    private final WorkoutRepositoryApi workoutRepository;
    private final RoomTransactionRunner transactionRunner;

    public CompleteCardio(
            CardioRepositoryApi cardioRepository,
            WorkoutRepositoryApi workoutRepository,
            RoomTransactionRunner transactionRunner
    ) {
        if (cardioRepository == null || workoutRepository == null || transactionRunner == null) {
            throw new IllegalArgumentException("유산소 완료 서비스 의존성이 없습니다.");
        }
        this.cardioRepository = cardioRepository;
        this.workoutRepository = workoutRepository;
        this.transactionRunner = transactionRunner;
    }

    public boolean execute(
            AccountScope scope,
            CardioSessionSnapshot snapshot,
            Integer averageHeartRateBpm
    ) {
        if (scope == null || snapshot == null) {
            return false;
        }
        if (CardioSessionSnapshot.STATUS_COMPLETED.equals(snapshot.getStatus())) {
            return true;
        }
        if (averageHeartRateBpm != null && averageHeartRateBpm <= 0) {
            throw new IllegalArgumentException("평균 심박수는 0보다 커야 합니다.");
        }

        final int durationSeconds = Math.max(
                1,
                snapshot.elapsedSeconds(System.currentTimeMillis())
        );
        final String activityLabel = snapshot.getActivityLabel();
        transactionRunner.run(() -> {
            boolean workoutCompleted = workoutRepository.completeCardioSession(
                    scope,
                    snapshot.getRecordId(),
                    snapshot.getActivityId(),
                    activityLabel,
                    durationSeconds,
                    snapshot.getDistanceMeters(),
                    averageHeartRateBpm
            );
            if (!workoutCompleted) {
                throw new IllegalStateException("공통 유산소 기록을 완료하지 못했습니다.");
            }

            boolean cardioCompleted = cardioRepository.completeSession(
                    scope,
                    snapshot.getRecordId(),
                    durationSeconds * 1000L
            );
            if (!cardioCompleted) {
                throw new IllegalStateException("GPS 유산소 세션을 완료하지 못했습니다.");
            }
        });
        return true;
    }
}
