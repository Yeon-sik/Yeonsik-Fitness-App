package com.yeonsik.fitness.shared

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseDraft
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitness.shared.feature.workout.application.CompleteWorkout
import com.yeonsik.fitness.shared.feature.workout.application.InitializeWorkoutExercise
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExercise
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSet
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DomainApiCompatibilityTest {
    @Test
    fun accountScopeRejectsBlankOwner() {
        assertFailsWith<IllegalArgumentException> { AccountScope(" ") }
    }

    @Test
    fun workoutCompletionAndUseCasesPreserveRepositoryBehavior() {
        val repository = RecordingWorkoutRepository(WorkoutCompletion.COMPLETED)
        val scope = AccountScope("owner-a")

        assertEquals(
            listOf(WorkoutCompletion.COMPLETED, WorkoutCompletion.NO_COMPLETED_SETS),
            WorkoutCompletion.values().toList()
        )
        assertSame(
            WorkoutCompletion.COMPLETED,
            CompleteWorkout(repository).execute(scope, "record-a")
        )
        assertTrue(InitializeWorkoutExercise(repository).execute(scope, "record-a", "exercise-a"))
        assertEquals(listOf("owner-a", "record-a", "exercise-a"), repository.initialized)
    }

    @Test
    fun workoutAndRoutineModelsKeepFieldsAndNullableInputs() {
        val exercise = WorkoutExercise(
            id = "exercise-row",
            exerciseId = "exercise-a",
            orderIndex = 2,
            name = "Bench press",
            recordType = "strength"
        )
        assertEquals("exercise-row", exercise.id)
        assertEquals(2, exercise.orderIndex)
        assertEquals("", exercise.uiPart)
        assertNull(exercise.familyIdentity)

        val set = WorkoutSet(
            id = "set-a",
            setIndex = 0,
            weightKg = 20.0,
            actualReps = 8,
            rir = null,
            restSeconds = null,
            isCompleted = false,
            durationSeconds = 0,
            distanceMeters = 0.0,
            assistedWeightKg = 0.0,
            addedWeightKg = 0.0,
            loadState = null,
            inputLoadValue = null,
            inputLoadUnit = null
        )
        assertEquals(20.0, set.weightKg)
        assertNull(set.rir)
        assertNull(set.inputLoadUnit)
        assertEquals(MassUnit.KG, MassUnit.parse("kg"))

        val draft = RoutineExerciseDraft(
            exerciseId = "exercise-a",
            nameKo = "벤치프레스",
            nameEn = "Bench press",
            bodyPartId = null,
            equipmentVariantId = null,
            primarySubPart = null,
            recordType = null,
            familyIdentity = null
        )
        assertEquals("exercise-a", draft.exerciseId)
        assertNull(draft.bodyPartId)
        assertNull(draft.recordType)
    }

    private class RecordingWorkoutRepository(
        private val completion: WorkoutCompletion
    ) : WorkoutRepositoryApi {
        val initialized = mutableListOf<String>()

        override fun loadSession(scope: AccountScope, recordId: String): WorkoutSessionSnapshot? = null

        override fun loadExerciseDetail(
            scope: AccountScope,
            recordId: String,
            activeExerciseId: String?
        ): WorkoutExerciseDetail? = null

        override fun ensureInitialSet(scope: AccountScope, recordId: String, exerciseId: String): Boolean {
            initialized += listOf(scope.ownerId, recordId, exerciseId)
            return true
        }

        override fun completeIfEligible(scope: AccountScope, recordId: String): WorkoutCompletion = completion

        override fun discard(scope: AccountScope, recordId: String) = Unit

        override fun updateTypedSet(
            scope: AccountScope,
            recordId: String,
            setId: String,
            input: WorkoutSetInput
        ): Boolean = false

        override fun addTypedSet(
            scope: AccountScope,
            recordId: String,
            exerciseId: String,
            setIndex: Int,
            input: WorkoutSetInput
        ): Boolean = false

        override fun deleteSet(scope: AccountScope, recordId: String, setId: String): Boolean = false

        override fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String): Boolean = false

        override fun addExercise(
            scope: AccountScope,
            recordId: String,
            exercise: WorkoutExerciseReplacement
        ): Boolean = false

        override fun replaceExercise(
            scope: AccountScope,
            recordId: String,
            exerciseId: String,
            replacement: WorkoutExerciseReplacement
        ): Boolean = false
    }
}
