package com.yeonsik.fitnessapp.feature.workout.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutCompletion;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseDetail;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;

import org.junit.Test;

public final class CompleteWorkoutTest {
    @Test
    public void completesEligibleWorkoutWithoutDiscardingIt() {
        FakeWorkoutRepository repository = new FakeWorkoutRepository(WorkoutCompletion.COMPLETED);
        CompleteWorkout service = new CompleteWorkout(repository);

        WorkoutCompletion result = service.execute(new AccountScope("owner-a"), "record-a");

        assertEquals(WorkoutCompletion.COMPLETED, result);
        assertEquals(1, repository.completeCalls);
        assertEquals(0, repository.discardCalls);
    }

    @Test
    public void discardsOnlyWhenCallerChoosesEmptySessionCleanup() {
        FakeWorkoutRepository repository = new FakeWorkoutRepository(WorkoutCompletion.NO_COMPLETED_SETS);
        CompleteWorkout service = new CompleteWorkout(repository);
        AccountScope scope = new AccountScope("owner-a");

        assertEquals(WorkoutCompletion.NO_COMPLETED_SETS, service.execute(scope, "record-a"));
        service.discardEmptySession(scope, "record-a");

        assertEquals(1, repository.completeCalls);
        assertEquals(1, repository.discardCalls);
        assertTrue(repository.discarded);
    }

    private static final class FakeWorkoutRepository implements WorkoutRepositoryApi {
        private final WorkoutCompletion completion;
        int completeCalls;
        int discardCalls;
        boolean discarded;

        FakeWorkoutRepository(WorkoutCompletion completion) {
            this.completion = completion;
        }

        @Override
        public WorkoutSessionSnapshot loadSession(AccountScope scope, String recordId) {
            return null;
        }

        @Override
        public WorkoutExerciseDetail loadExerciseDetail(
                AccountScope scope,
                String recordId,
                String activeExerciseId
        ) {
            return null;
        }

        @Override
        public boolean ensureInitialSet(AccountScope scope, String recordId, String exerciseId) {
            return false;
        }

        @Override
        public WorkoutCompletion completeIfEligible(AccountScope scope, String recordId) {
            completeCalls++;
            return completion;
        }

        @Override
        public void discard(AccountScope scope, String recordId) {
            discardCalls++;
            discarded = true;
        }

        @Override
        public boolean updateTypedSet(AccountScope scope, String recordId, String setId,
                                      FitnessRepository.SetInput input) { return false; }

        @Override
        public boolean addTypedSet(AccountScope scope, String recordId, String exerciseId,
                                   int setIndex, FitnessRepository.SetInput input) { return false; }

        @Override
        public boolean deleteSet(AccountScope scope, String recordId, String setId) { return false; }

        @Override
        public boolean deleteExercise(AccountScope scope, String recordId, String exerciseId) { return false; }

        @Override
        public boolean replaceExercise(AccountScope scope, String recordId, String exerciseId,
                                       RoutineExercise replacement) { return false; }
    }
}
