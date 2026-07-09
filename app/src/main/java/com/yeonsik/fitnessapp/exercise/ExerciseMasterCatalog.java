package com.yeonsik.fitnessapp.exercise;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ExerciseMasterCatalog {
    public final List<ExerciseCategory> categories;
    public final List<WeightExercise> exercises;
    public final Map<String, WeightExercise> exerciseById;

    public ExerciseMasterCatalog(
            List<ExerciseCategory> categories,
            List<WeightExercise> exercises,
            Map<String, WeightExercise> exerciseById
    ) {
        this.categories = Collections.unmodifiableList(categories);
        this.exercises = Collections.unmodifiableList(exercises);
        this.exerciseById = Collections.unmodifiableMap(exerciseById);
    }

    public static ExerciseMasterCatalog empty() {
        return new ExerciseMasterCatalog(Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }
}
