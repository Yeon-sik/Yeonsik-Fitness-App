package com.yeonsik.fitnessapp.feature.workout.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** In-memory database only. Rest configuration must never rewrite historical set facts. */
class WorkoutExerciseRestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = AccountScope("exercise-rest-test")
    private lateinit var room: FitnessRoomDatabase
    private lateinit var storage: WorkoutRoomStorage
    private lateinit var repository: WorkoutRepositoryImplementation
    private val exercise = ManualWorkoutExercise("수동 컬", BodyPart.ARMS, EquipmentType.DUMBBELL, "weight_reps")
    private val input = WorkoutSetInput(10.0, 12, null, null, null, null, 2, 45, true,
        LoadState.EXTERNAL_LOAD, 10.0, MassUnit.KG)

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        storage = WorkoutRoomStorage(room, context)
        repository = WorkoutRepositoryImplementation(storage)
    }
    @After fun close() { room.close() }

    @Test fun restIsScopedToOccurrenceAndSurvivesReloadWithoutChangingAnySetOrSnapshot() {
        val recordId = repository.createEmptySession(scope, "2026-10-01")
        assertTrue(repository.addManualExercise(scope, recordId, exercise))
        assertTrue(repository.addManualExercise(scope, recordId, exercise))
        val occurrences = storage.exercises(scope, recordId)
        val first = occurrences[0].id
        val second = occurrences[1].id
        assertTrue(repository.addTypedSet(scope, recordId, first, 1, input))
        assertTrue(repository.addTypedSet(scope, recordId, first, 2, input.copy(restSeconds = 120)))
        val dao = room.workoutRoomDao()
        val setsBefore = dao.visibleSets(first, scope.ownerId)
        val exercisesBefore = dao.visibleExercises(recordId, scope.ownerId)
        val metricsBefore = storage.metrics(scope, recordId)
        val metadataBefore = JSONObject(dao.visibleRecord(recordId, scope.ownerId)!!.metadata)
        assertNull(repository.loadExerciseDetail(scope, recordId, first)!!.exerciseRestSeconds)
        assertTrue(repository.updateExerciseRestSeconds(scope, recordId, first, 150))
        assertTrue(repository.updateExerciseRestSeconds(scope, recordId, second, 0))
        val reloaded = WorkoutRepositoryImplementation(WorkoutRoomStorage(room, context))
        assertEquals(150, reloaded.loadExerciseDetail(scope, recordId, first)!!.exerciseRestSeconds)
        assertEquals(0, reloaded.loadExerciseDetail(scope, recordId, second)!!.exerciseRestSeconds)
        assertEquals(setsBefore, dao.visibleSets(first, scope.ownerId))
        assertEquals(exercisesBefore, dao.visibleExercises(recordId, scope.ownerId))
        assertEquals(metricsBefore, storage.metrics(scope, recordId))
        val metadataAfter = JSONObject(dao.visibleRecord(recordId, scope.ownerId)!!.metadata)
        metadataBefore.keys().forEach { key -> assertEquals(metadataBefore.get(key).toString(), metadataAfter.get(key).toString()) }
        val otherRecord = repository.createEmptySession(scope, "2026-10-02")
        assertTrue(repository.addManualExercise(scope, otherRecord, exercise))
        assertNull(reloaded.loadExerciseDetail(scope, otherRecord, storage.exercises(scope, otherRecord).single().id)!!.exerciseRestSeconds)
    }

    @Test fun wrongOwnerRecordMissingOccurrenceNegativeAndCompletedChangesAreRejected() {
        val recordId = repository.createEmptySession(scope, "2026-10-01")
        repository.addManualExercise(scope, recordId, exercise)
        val id = storage.exercises(scope, recordId).single().id
        val dao = room.workoutRoomDao()
        val before = dao.visibleRecord(recordId, scope.ownerId)
        assertFalse(repository.updateExerciseRestSeconds(AccountScope("other"), recordId, id, 30))
        assertFalse(repository.updateExerciseRestSeconds(scope, "wrong", id, 30))
        assertFalse(repository.updateExerciseRestSeconds(scope, recordId, "missing", 30))
        assertFalse(repository.updateExerciseRestSeconds(scope, recordId, id, -1))
        assertEquals(before, dao.visibleRecord(recordId, scope.ownerId))
        repository.addTypedSet(scope, recordId, id, 1, input)
        assertTrue(repository.updateExerciseRestSeconds(scope, recordId, id, 150))
        assertEquals(WorkoutCompletion.COMPLETED, repository.completeIfEligible(scope, recordId))
        val completed = dao.visibleRecord(recordId, scope.ownerId)
        assertFalse(repository.updateExerciseRestSeconds(scope, recordId, id, 90))
        assertEquals(completed, dao.visibleRecord(recordId, scope.ownerId))
        assertEquals(45L, dao.visibleSets(id, scope.ownerId).single().restSeconds)
    }
}
