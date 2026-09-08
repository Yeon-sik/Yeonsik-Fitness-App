package com.yeonsik.fitnessapp.feature.workout.application;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.feature.cardio.application.CardioSessionApplicationService;
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi;

import java.util.List;

/** Cross-feature workout session use cases used by navigation and record actions. */
public final class WorkoutSessionApplicationService {
    private final WorkoutRepositoryApi workoutRepository;
    private final CardioSessionApplicationService cardio;

    public WorkoutSessionApplicationService(
            WorkoutRepositoryApi workoutRepository,
            CardioSessionApplicationService cardio
    ) {
        if (workoutRepository == null || cardio == null) {
            throw new IllegalArgumentException("운동 세션 서비스 의존성이 없습니다.");
        }
        this.workoutRepository = workoutRepository;
        this.cardio = cardio;
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
        if (cardioSession) {
            cardio.deleteLocalData(scope, recordId);
        }
        boolean deleted = workoutRepository.deleteSession(scope, recordId);
        return new DeleteResult(cardioSession, deleted);
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
