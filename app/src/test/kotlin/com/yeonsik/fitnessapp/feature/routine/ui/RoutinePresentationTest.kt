package com.yeonsik.fitnessapp.feature.routine.ui

import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutinePresentationTest {
    @Test fun majorityPartAndItsMostFrequentSubPartWinOverOtherMuscles() {
        val exercises = listOf(
            exercise("a", 0).copy(uiPart = "가슴", primarySubPart = "중부 대흉근"),
            exercise("b", 1).copy(uiPart = "하체", primarySubPart = "대퇴사두근"),
            exercise("c", 2).copy(uiPart = "legs", primarySubPart = "햄스트링"),
            exercise("d", 3).copy(uiPart = "하체", primarySubPart = "대퇴사두근")
        )
        assertEquals("하체 - 대퇴사두근", routineDominantMuscleLabel(exercises))
    }

    @Test fun tiesUseSavedOrderAndUnknownSubPartsDoNotHideKnownMuscles() {
        val exercises = listOf(
            exercise("b", 2).copy(uiPart = "가슴", primarySubPart = "대흉근"),
            exercise("a", 1).copy(uiPart = "하체", primarySubPart = "세부 부위 없음")
        )
        assertEquals("하체", routineDominantMuscleLabel(exercises))
        assertEquals("부위 미설정", routineDominantMuscleLabel(emptyList()))
        assertEquals("부위 미설정", routineDominantMuscleLabel(listOf(exercise("x", 0).copy(uiPart = ""))))
    }

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
