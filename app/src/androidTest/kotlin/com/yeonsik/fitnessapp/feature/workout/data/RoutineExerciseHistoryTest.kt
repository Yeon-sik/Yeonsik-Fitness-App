package com.yeonsik.fitnessapp.feature.workout.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** In-memory data only: exact variant lookup must never write a workout or initial set. */
class RoutineExerciseHistoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val owner = AccountScope("routine-history-owner")
    private lateinit var room: FitnessRoomDatabase
    private lateinit var storage: WorkoutRoomStorage
    private lateinit var repository: WorkoutRepositoryImplementation

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        storage = WorkoutRoomStorage(room, context)
        repository = WorkoutRepositoryImplementation(storage)
    }
    @After fun close() { room.close() }

    private fun exercise(id: String): RoutineExerciseInstance {
        val preset = requireNotNull(ExerciseFamilyCatalog.load(context).runtimeCatalog().preset(id))
        val draft = ExerciseMasterAdapter.toRoutineExerciseDraft(preset)
        return RoutineExerciseInstance("routine-$id", draft.exerciseId, draft.nameKo,
            draft.bodyPartId.orEmpty(), draft.primarySubPart.orEmpty(), "", draft.recordType!!, 1, draft.familyIdentity)
    }

    private fun record(scope: AccountScope, date: String, exercise: RoutineExerciseInstance,
        complete: Boolean = true): String {
        val recordId = repository.createSessionFromRoutine(scope, date, "기록 테스트", "routine", listOf(exercise))
        val occurrence = storage.exercises(scope, recordId).single()
        assertTrue(repository.addTypedSet(scope, recordId, occurrence.id, 1,
            WorkoutSetInput(40.0, 10, null, null, null, null, 2, 90, true,
                LoadState.EXTERNAL_LOAD, 40.0, MassUnit.KG)))
        if (complete) repository.completeIfEligible(scope, recordId)
        return recordId
    }

    private fun counts(): List<Int> = listOf("workout_records", "workout_exercises", "workout_sets").map { table ->
        room.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    }

    @Test fun latestCompletedHistoryResolvesCanonicalAliasAndPreservesEveryRow() {
        val incline = exercise("chest_smith_incline_bench_press")
        record(owner, "2026-10-05", incline)
        val latest = record(owner, "2026-10-06", incline)
        record(owner, "2026-10-07", exercise("chest_smith_flat_bench_press"))
        record(owner, "2026-10-08", incline, complete = false)
        val before = counts()
        val beforeSets = room.workoutRoomDao().visibleSets(storage.exercises(owner, latest).single().id, owner.ownerId)
        val history = requireNotNull(repository.loadRoutineExerciseHistory(owner,
            incline.copy(exerciseId = "legacy-alias", nameKo = "동일 운동의 다른 이름")))
        assertEquals(latest, history.recordId)
        assertEquals(listOf("2026-10-05", "2026-10-06"), history.recentHistories.map { it.date })
        assertEquals(10, history.sets.single().actualReps)
        assertEquals(before, counts())
        assertEquals(beforeSets, room.workoutRoomDao().visibleSets(history.activeExercise.id, owner.ownerId))
    }

    @Test fun unmatchedVariantsOtherAccountsAndNoHistoryReturnEmptyWithoutWriting() {
        val incline = exercise("chest_smith_incline_bench_press")
        val noHistoryCounts = counts()
        assertNull(repository.loadRoutineExerciseHistory(owner, incline))
        assertEquals(noHistoryCounts, counts())
        record(owner, "2026-10-06", incline)
        val before = counts()
        assertNull(repository.loadRoutineExerciseHistory(AccountScope("other-owner"), incline))
        assertNull(repository.loadRoutineExerciseHistory(owner, exercise("chest_smith_flat_bench_press")))
        assertNull(repository.loadRoutineExerciseHistory(owner, incline.copy(recordType = "reps_only")))
        assertEquals(before, counts())
    }
}
