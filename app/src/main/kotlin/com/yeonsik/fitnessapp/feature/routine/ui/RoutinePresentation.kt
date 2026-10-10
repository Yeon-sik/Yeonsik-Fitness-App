package com.yeonsik.fitnessapp.feature.routine.ui

import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance

/** Counts exercise occurrences; ties follow the saved routine order. */
internal fun routineDominantMuscleLabel(exercises: List<RoutineExerciseInstance>): String {
    val parts = stableRoutineExercises(exercises).mapNotNull { exercise ->
        val storedPart = exercise.uiPart.trim()
        val part = BodyPart.fromId(storedPart)
            ?: BodyPart.entries.firstOrNull { it.labelKo() == storedPart }
            ?: BodyPart.fromId(exercise.familyIdentity?.defaultUiPart)
            ?: return@mapNotNull null
        val subPart = exercise.primarySubPart.trim().takeIf {
            it.isNotEmpty() && it != "세부 부위 없음" && it != "미설정"
        }
        part to subPart
    }
    val dominantPart = parts.map { it.first }.mostFrequent() ?: return "부위 미설정"
    val dominantSubPart = parts.filter { it.first == dominantPart }.mapNotNull { it.second }.mostFrequent()
    return listOfNotNull(dominantPart.labelKo(), dominantSubPart).joinToString(" - ")
}

private fun <T> List<T>.mostFrequent(): T? {
    val counts = groupingBy { it }.eachCount()
    return counts.maxByOrNull { it.value }?.key
}
