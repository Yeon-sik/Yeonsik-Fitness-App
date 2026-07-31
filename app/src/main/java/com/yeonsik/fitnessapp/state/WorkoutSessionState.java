package com.yeonsik.fitnessapp.state;

import com.yeonsik.fitnessapp.data.FitnessRepository;

import java.util.List;

/**
 * 진행 중 운동 세션의 화면 간 공유 상태와 세트 진행 판정 로직.
 * UI를 알지 못하며, 렌더러들은 이 클래스를 통해서만 세션 진행 규칙을 판단한다.
 */
public final class WorkoutSessionState {
    private String activeRecordId;
    private String activeExerciseId;
    private int generation;

    public String activeRecordId() {
        return activeRecordId;
    }

    public void setActiveRecordId(String recordId) {
        this.activeRecordId = recordId;
    }

    public String activeExerciseId() {
        return activeExerciseId;
    }

    public void setActiveExerciseId(String exerciseId) {
        this.activeExerciseId = exerciseId;
    }

    public void clearIfMatches(String recordId) {
        if (recordId != null && recordId.equals(activeRecordId)) {
            activeRecordId = null;
            activeExerciseId = null;
        }
    }

    /** 화면이 다시 그려질 때마다 증가한다. 경과시간 티커의 유효성 검사에 사용한다. */
    public int nextGeneration() {
        return ++generation;
    }

    public int generation() {
        return generation;
    }

    public static FitnessRepository.SessionExerciseEntry findActiveExercise(
            List<FitnessRepository.SessionExerciseEntry> exercises,
            String exerciseId
    ) {
        if (exerciseId != null) {
            for (FitnessRepository.SessionExerciseEntry exercise : exercises) {
                if (exercise.id.equals(exerciseId)) {
                    return exercise;
                }
            }
        }
        return exercises.get(0);
    }

    public static FitnessRepository.SessionExerciseEntry nextExercise(
            List<FitnessRepository.SessionExerciseEntry> exercises,
            String exerciseId
    ) {
        for (int index = 0; index < exercises.size(); index++) {
            if (exercises.get(index).id.equals(exerciseId)) {
                return index + 1 < exercises.size() ? exercises.get(index + 1) : null;
            }
        }
        return null;
    }

    public static int completedSetCount(List<FitnessRepository.SessionSetEntry> sets) {
        int count = 0;
        for (FitnessRepository.SessionSetEntry set : sets) {
            if (set.isCompleted) {
                count += 1;
            }
        }
        return count;
    }

    public static boolean allSetsCompleted(List<FitnessRepository.SessionSetEntry> sets) {
        if (sets.isEmpty()) {
            return false;
        }
        return completedSetCount(sets) == sets.size();
    }

    public static boolean canMoveToNextExercise(FitnessRepository repository, String recordId, String exerciseId) {
        FitnessRepository.SessionExerciseEntry next =
                nextExercise(repository.sessionExerciseEntries(recordId), exerciseId);
        return next != null && allSetsCompleted(repository.setsForExercise(exerciseId));
    }
}
