package com.yeonsik.fitnessapp.feature.workout.api;

import com.yeonsik.fitnessapp.data.FitnessSummaryProjectionV2;

import java.util.List;

/** Workout-owned local summary reconciliation and projection read boundary. */
public interface WorkoutSummaryApi {
    int reconcileSharedWorkoutSummaries(String ownerId);

    boolean updateSharedWorkoutSummary(
            String ownerId,
            String recordId,
            boolean publishToOs
    );

    List<FitnessSummaryProjectionV2> completedFitnessSummaryProjectionsV2(String ownerId);
}
