package com.yeonsik.fitnessapp.feature.workout.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** In-memory Room only. Existing exercises and sets must survive rejected additions. */
class WorkoutExerciseUniquenessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = AccountScope("exercise-uniqueness-test")
    private lateinit var room: FitnessRoomDatabase
    private lateinit var storage: WorkoutRoomStorage
    private lateinit var repository: WorkoutRepositoryImplementation
    private fun exercise(id: String) = ExerciseMasterAdapter.toWorkoutExerciseReplacement(
        requireNotNull(ExerciseFamilyCatalog.load(context).runtimeCatalog().preset(id)))
    private val incline get() = exercise("chest_smith_incline_bench_press")
    private val flat get() = exercise("chest_smith_flat_bench_press")

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        storage = WorkoutRoomStorage(room, context)
        repository = WorkoutRepositoryImplementation(storage)
    }
    @After fun close() { room.close() }

    @Test fun repeatedAddAndCanonicalAliasAreRejectedWithoutChangingRowsOrSets() {
        val recordId = repository.createEmptySession(scope, "2026-10-08")
        assertTrue(repository.addExercise(scope, recordId, incline))
        val occurrence = storage.exercises(scope, recordId).single()
        val input = WorkoutSetInput(20.0, 10, null, null, null, null, 2, 90, false,
            LoadState.EXTERNAL_LOAD, 20.0, MassUnit.KG)
        assertTrue(repository.addTypedSet(scope, recordId, occurrence.id, 1, input))
        val rows = room.workoutRoomDao().visibleExercises(recordId, scope.ownerId)
        val sets = room.workoutRoomDao().visibleSets(occurrence.id, scope.ownerId)
        assertFalse(repository.addExercise(scope, recordId, incline))
        assertFalse(repository.addExercise(scope, recordId, incline.copy(masterExerciseId = "legacy-alias", nameKo = "이전 이름")))
        assertEquals(rows, room.workoutRoomDao().visibleExercises(recordId, scope.ownerId))
        assertEquals(sets, room.workoutRoomDao().visibleSets(occurrence.id, scope.ownerId))
    }

    @Test fun differentVariantsAndSeparateSessionsRemainAllowed() {
        val firstRecord = repository.createEmptySession(scope, "2026-10-08")
        assertTrue(repository.addExercise(scope, firstRecord, incline))
        assertTrue(repository.addExercise(scope, firstRecord, flat))
        assertEquals(listOf(1, 2), storage.exercises(scope, firstRecord).map { it.orderIndex })
        val secondRecord = repository.createEmptySession(scope, "2026-10-09")
        assertTrue(repository.addExercise(scope, secondRecord, incline))
        assertEquals(1, storage.exercises(scope, secondRecord).size)
    }

    @Test fun simultaneousAddsCommitOnlyOneOccurrence() {
        val recordId = repository.createEmptySession(scope, "2026-10-08")
        val candidate = incline
        val executor = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        try {
            val results = (1..8).map {
                executor.submit<Boolean> {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    repository.addExercise(scope, recordId, candidate)
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get(10, TimeUnit.SECONDS) })
            assertEquals(1, storage.exercises(scope, recordId).size)
        } finally { executor.shutdownNow() }
    }

    @Test fun replacementCannotDuplicateAnotherOccurrenceButCanKeepItsOwnVariant() {
        val recordId = repository.createEmptySession(scope, "2026-10-08")
        repository.addExercise(scope, recordId, incline)
        repository.addExercise(scope, recordId, flat)
        val occurrences = storage.exercises(scope, recordId)
        val before = room.workoutRoomDao().visibleExercises(recordId, scope.ownerId)
        assertFalse(repository.replaceExercise(scope, recordId, occurrences[1].id, incline))
        assertEquals(before, room.workoutRoomDao().visibleExercises(recordId, scope.ownerId))
        assertTrue(repository.replaceExercise(scope, recordId, occurrences[0].id, incline))
        assertEquals(2, storage.exercises(scope, recordId).size)
    }

    @Test fun manualDuplicatesAndWrongAccountAreRejected() {
        val recordId = repository.createEmptySession(scope, "2026-10-08")
        val manual = ManualWorkoutExercise("수동 컬", BodyPart.ARMS, EquipmentType.DUMBBELL, "weight_reps")
        assertTrue(repository.addManualExercise(scope, recordId, manual))
        assertFalse(repository.addManualExercise(scope, recordId, manual.copy(name = "  수동 컬  ")))
        assertTrue(repository.addManualExercise(scope, recordId, manual.copy(name = "다른 수동 컬")))
        assertFalse(repository.addExercise(AccountScope("other-owner"), recordId, incline))
        assertFalse(repository.addExercise(scope, "missing-record", incline))
        assertEquals(2, storage.exercises(scope, recordId).size)
    }
}
