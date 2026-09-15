package com.yeonsik.fitnessapp.feature.routine.ui

import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutinePresentationTest {
    @Test
    fun stableRoutineExercisesUsesStoredOrderAndIdentityTieBreak() {
        val exercises = listOf(
            exercise(id = "z", order = 2),
            exercise(id = "b", order = 1),
            exercise(id = "a", order = 1)
        )

        assertEquals(
            listOf("a", "b", "z"),
            stableRoutineExercises(exercises).map { it.id }
        )
    }

    @Test
    fun routineExerciseMetadataPreservesBodySubPartEquipmentAndRecordType() {
        val exercise = RoutineExerciseInstance(
            "exercise-1",
            "barbell-squat",
            "바벨 스쿼트",
            "legs",
            "대퇴사두근",
            "바벨",
            FitnessRecordContract.WEIGHT_REPS,
            1,
            null
        )

        assertEquals(
            "하체 · 대퇴사두근 · 바벨 · 중량 · 반복",
            routineExerciseMetadata(exercise)
        )
    }

    private fun exercise(id: String, order: Int) = RoutineExerciseInstance(
        id,
        "exercise-$id",
        id,
        "legs",
        "",
        "",
        FitnessRecordContract.WEIGHT_REPS,
        order,
        null
    )
}
