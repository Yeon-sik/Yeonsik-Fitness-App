package com.yeonsik.fitnessapp.feature.workout.application;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner;
import com.yeonsik.fitnessapp.feature.cardio.application.CardioSessionApplicationService;
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi;

import java.util.List;

/** Cross-feature workout session use cases used by navigation and record actions. */
public final class WorkoutSessionApplicationService {
    private final WorkoutRepositoryApi workoutRepository;
    private final CardioSessionApplicationService cardio;
    private final RoomTransactionRunner transactionRunner;

    public WorkoutSessionApplicationService(
            WorkoutRepositoryApi workoutRepository,
            CardioSessionApplicationService cardio,
            RoomTransactionRunner transactionRunner
    ) {
        if (workoutRepository == null || cardio == null || transactionRunner == null) {
            throw new IllegalArgumentException("운동 세션 서비스 의존성이 없습니다.");
        }
        this.workoutRepository = workoutRepository;
        this.cardio = cardio;
        this.transactionRunner = transactionRunner;
    }

    public String latestInProgress(AccountScope scope) {
        return workoutRepository.latestInProgressSession(scope);
    }

    public boolean isCardioSession(AccountScope scope, String recordId) {
        return cardio.isCardioSession(scope, recordId);
    }

    public String createEmptySession(AccountScope scope, String date) {
        return workoutRepository.createEmptySession(scope, date);
    }

    public String createSessionFromRoutine(
            AccountScope scope,
            String date,
            String title,
            String routineId,
            List<RoutineExerciseInstance> exercises
    ) {
        return workoutRepository.createSessionFromRoutine(scope, date, title, routineId, exercises);
    }

    public String createManualPastSessionFromRoutine(
            AccountScope scope,
            String date,
            String title,
            String routineId,
            List<RoutineExerciseInstance> exercises,
            String startedAt,
            String endedAt
    ) {
        return workoutRepository.createManualPastSessionFromRoutine(
                scope, date, title, routineId, exercises, startedAt, endedAt
        );
    }

    public DeleteResult delete(AccountScope scope, String recordId) {
        boolean cardioSession = cardio.isCardioSession(scope, recordId);
        final boolean[] deleted = new boolean[1];
        transactionRunner.run(() -> {
            if (cardioSession && !cardio.deleteLocalData(scope, recordId)) {
                throw new IllegalStateException("GPS 유산소 데이터를 삭제하지 못했습니다.");
            }
            deleted[0] = workoutRepository.deleteSession(scope, recordId);
            if (cardioSession && !deleted[0]) {
                throw new IllegalStateException("공통 유산소 기록을 삭제하지 못했습니다.");
            }
        });
        return new DeleteResult(cardioSession, deleted[0]);
    }

    public static final class DeleteResult {
        public final boolean cardioSession;
        public final boolean deleted;

        private DeleteResult(boolean cardioSession, boolean deleted) {
            this.cardioSession = cardioSession;
            this.deleted = deleted;
        }
    }
}
