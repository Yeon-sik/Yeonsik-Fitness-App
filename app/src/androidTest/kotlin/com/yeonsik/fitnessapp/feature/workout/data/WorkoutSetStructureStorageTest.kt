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
import com.yeonsik.fitnessapp.sync.RoomLegacyFitnessSyncStore
import com.yeonsik.fitnessapp.sync.LegacySyncRow
import com.yeonsik.fitnessapp.integration.personalos.LegacyFitnessSyncAdapter
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Isolated in-memory SQLite only; never opens the user's installed database. */
class WorkoutSetStructureStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = AccountScope("set-structure-test")
    private lateinit var room: FitnessRoomDatabase
    private lateinit var storage: WorkoutRoomStorage
    private lateinit var repository: WorkoutRepositoryImplementation
    private val input = WorkoutSetInput(20.0, 10, null, null, null, null, 2, 90, true,
        LoadState.EXTERNAL_LOAD, 20.0, MassUnit.KG)

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        storage = WorkoutRoomStorage(room, context)
        repository = WorkoutRepositoryImplementation(storage)
    }
    @After fun close() { room.close() }

    private fun record(date: String = "2026-10-15"): Pair<String, String> {
        val record = repository.createEmptySession(scope, date)
        repository.addManualExercise(scope, record,
            ManualWorkoutExercise("컬", BodyPart.ARMS, EquipmentType.DUMBBELL, "weight_reps"))
        val exercise = storage.exercises(scope, record).single().id
        repository.addTypedSet(scope, record, exercise, 1, input)
        return record to exercise
    }

    @Test fun typesAndDropParentSurviveReloadAndRootDeletionPromotesTheNextStep() {
        val (record, exercise) = record()
        assertTrue(repository.updateTypedSet(scope, record, storage.sets(scope, exercise).single().id, input.copy(completed = false)))
        assertTrue(repository.addTypedSet(scope, record, exercise, 2, input))
        val root = storage.sets(scope, exercise).last().id
        assertTrue(repository.updateSetType(scope, record, root, WorkoutSetType.DROP))
        val converted = repository.loadExerciseDetail(scope, record, exercise)!!.sets.last()
        assertEquals(WorkoutSetType.DROP, converted.setType)
        assertNull(converted.dropParentSetId)
        assertEquals(20.0, converted.weightKg, 0.0)
        assertTrue(repository.addDropSet(scope, record, exercise, root))
        val drop = repository.loadExerciseDetail(scope, record, exercise)!!.sets.last()
        assertEquals(WorkoutSetType.DROP, drop.setType)
        assertEquals(root, drop.dropParentSetId)
        assertFalse(drop.isCompleted)
        assertTrue(repository.updateTypedSet(scope, record, drop.id, input.copy(weightKg = 10.0, inputLoadValue = 10.0)))
        assertTrue(repository.addDropSet(scope, record, exercise, drop.id))
        val reloaded = WorkoutRepositoryImplementation(WorkoutRoomStorage(room, context))
        assertEquals(1, reloaded.loadSession(scope, record)!!.routineTotals!!.workingSetCount)
        assertEquals(300.0, reloaded.loadSession(scope, record)!!.routineTotals!!.volumeKg, 0.0)
        assertTrue(repository.deleteSet(scope, record, root))
        val remaining = reloaded.loadExerciseDetail(scope, record, exercise)!!.sets.drop(1)
        assertEquals(WorkoutSetType.DROP, remaining.first().setType)
        assertEquals(remaining.first().id, remaining.last().dropParentSetId)
    }

    @Test fun firstSetCannotBeDeletedAndRemovingLaterSetPreservesTheEntryRow() {
        val (record, exercise) = record()
        val first = storage.sets(scope, exercise).single().id
        assertFalse(repository.deleteSet(scope, record, first))
        assertTrue(repository.addTypedSet(scope, record, exercise, 2, input))
        val second = storage.sets(scope, exercise).last().id
        assertFalse(repository.deleteSet(scope, record, first))
        assertTrue(repository.deleteSet(scope, record, second))
        assertEquals(first, storage.sets(scope, exercise).single().id)
        assertFalse(repository.deleteSet(scope, record, first))
    }

    @Test fun convertingASetCreatesItsOwnGroupAndUngroupingPreservesAllInputs() {
        val (record, exercise) = record()
        repository.addTypedSet(scope, record, exercise, 2, input.copy(weightKg = 15.0, inputLoadValue = 15.0))
        val original = repository.loadExerciseDetail(scope, record, exercise)!!.sets
        val root = original.last()
        assertTrue(repository.updateSetType(scope, record, root.id, WorkoutSetType.DROP))
        assertTrue(repository.addDropSet(scope, record, exercise, root.id))
        val grouped = repository.loadExerciseDetail(scope, record, exercise)!!.sets
        assertEquals(2, workoutSetGroups(grouped).size)
        assertEquals(root.id, grouped.last().dropParentSetId)
        assertTrue(repository.updateSetType(scope, record, root.id, WorkoutSetType.NORMAL))
        val ungrouped = repository.loadExerciseDetail(scope, record, exercise)!!.sets
        assertTrue(ungrouped.all { it.setType == WorkoutSetType.NORMAL && it.dropParentSetId == null })
        assertEquals(grouped.map { it.copy(setType = WorkoutSetType.NORMAL, dropParentSetId = null) }, ungrouped)
        assertEquals(original.first().id, ungrouped.first().id)
    }

    private fun addPartner(record: String, name: String): String {
        assertTrue(repository.addManualExercise(scope, record,
            ManualWorkoutExercise(name, BodyPart.ARMS, EquipmentType.DUMBBELL, "weight_reps")))
        return storage.exercises(scope, record).last().id
    }

    @Test fun compoundTypePersistsAndSwitchingGroupTypeKeepsTheGroupAndRecordedSets() {
        val (record, exercise) = record()
        val partner = addPartner(record, "해머 컬")
        val originalSets = room.workoutRoomDao().visibleSets(exercise, scope.ownerId)
        assertTrue(repository.linkExerciseGroup(scope, record, exercise, partner, WorkoutExerciseGroupType.COMPOUND))
        val reloaded = WorkoutRepositoryImplementation(WorkoutRoomStorage(room, context))
        val grouped = reloaded.loadExerciseDetail(scope, record, exercise)!!
        val groupId = grouped.activeExercise.supersetGroupId
        assertNotNull(groupId)
        assertTrue(grouped.exercises.all { it.supersetGroupId == groupId && it.groupType == WorkoutExerciseGroupType.COMPOUND })
        assertTrue(reloaded.loadSession(scope, record)!!.exercises.all { it.groupType == WorkoutExerciseGroupType.COMPOUND })
        assertEquals(originalSets, room.workoutRoomDao().visibleSets(exercise, scope.ownerId))
        assertTrue(repository.linkExerciseGroup(scope, record, exercise, partner, WorkoutExerciseGroupType.SUPERSET))
        val changed = reloaded.loadExerciseDetail(scope, record, exercise)!!
        assertTrue(changed.exercises.all { it.supersetGroupId == groupId && it.groupType == WorkoutExerciseGroupType.SUPERSET })
        assertEquals(grouped.sets, changed.sets)
    }

    @Test fun changingDropToNormalKeepsExerciseLinksAndUnlinkingAPairPreservesEveryRecordedSet() {
        WorkoutExerciseGroupType.entries.forEach { type ->
            val (record, exercise) = record()
            val partner = addPartner(record, "해머 컬")
            val root = storage.sets(scope, exercise).single().id
            assertTrue(repository.updateSetType(scope, record, root, WorkoutSetType.DROP))
            assertTrue(repository.addDropSet(scope, record, exercise, root))
            assertTrue(repository.linkExerciseGroup(scope, record, exercise, partner, type))
            assertTrue(repository.updateSetType(scope, record, root, WorkoutSetType.NORMAL))
            val stillLinked = repository.loadExerciseDetail(scope, record, exercise)!!
            assertTrue(stillLinked.exercises.all { it.supersetGroupId != null })
            assertTrue(stillLinked.sets.all { it.setType == WorkoutSetType.NORMAL && it.dropParentSetId == null })
            val before = stillLinked.exercises.associate { it.id to room.workoutRoomDao().visibleSets(it.id, scope.ownerId) }
            assertTrue(repository.linkExerciseGroup(scope, record, exercise, null, type))
            val unlinked = repository.loadExerciseDetail(scope, record, exercise)!!
            assertTrue(unlinked.exercises.all { it.supersetGroupId == null })
            assertEquals(before, unlinked.exercises.associate { it.id to room.workoutRoomDao().visibleSets(it.id, scope.ownerId) })
        }
    }

    @Test fun unlinkingOneOfThreeExercisesRetainsTheOtherTwoAndTheirCompoundType() {
        val (record, exercise) = record()
        val second = addPartner(record, "해머 컬")
        val third = addPartner(record, "컨센트레이션 컬")
        assertTrue(repository.linkExerciseGroup(scope, record, exercise, second, WorkoutExerciseGroupType.COMPOUND))
        assertTrue(repository.linkExerciseGroup(scope, record, exercise, third, WorkoutExerciseGroupType.COMPOUND))
        val before = repository.loadExerciseDetail(scope, record, exercise)!!
        val groupId = before.activeExercise.supersetGroupId
        assertTrue(repository.linkExerciseGroup(scope, record, exercise, null, WorkoutExerciseGroupType.COMPOUND))
        val after = repository.loadExerciseDetail(scope, record, exercise)!!
        assertNull(after.activeExercise.supersetGroupId)
        assertTrue(after.exercises.filter { it.id != exercise }.all {
            it.supersetGroupId == groupId && it.groupType == WorkoutExerciseGroupType.COMPOUND
        })
        assertEquals(before.sessionSets, after.sessionSets)
    }

    @Test fun existingGroupsWithoutAStoredTypeRemainSupersets() {
        val (record, exercise) = record()
        val partner = addPartner(record, "해머 컬")
        assertTrue(repository.linkSuperset(scope, record, exercise, partner))
        val dao = room.workoutRoomDao()
        val row = dao.visibleRecord(record, scope.ownerId)!!
        val metadata = JSONObject(row.metadata)
        metadata.getJSONObject("workout_structure_v1").remove("group_types")
        dao.updateRecord(row.copy(metadata = metadata.toString()))
        val exercises = repository.loadExerciseDetail(scope, record, exercise)!!.exercises
        assertTrue(exercises.all { it.supersetGroupId != null && it.groupType == WorkoutExerciseGroupType.SUPERSET })
    }

    @Test fun warmupIsExcludedAndSupersetLinksPersistWithoutChangingSetValues() {
        val (record, exercise) = record()
        repository.addManualExercise(scope, record,
            ManualWorkoutExercise("다른 컬", BodyPart.ARMS, EquipmentType.DUMBBELL, "weight_reps"))
        val partner = storage.exercises(scope, record).last().id
        val set = storage.sets(scope, exercise).single().id
        val before = room.workoutRoomDao().visibleSets(exercise, scope.ownerId)
        assertTrue(repository.updateSetType(scope, record, set, WorkoutSetType.WARMUP))
        assertEquals(0.0, repository.loadSession(scope, record)!!.routineTotals!!.volumeKg, 0.0)
        assertFalse(repository.addDropSet(scope, record, exercise, set))
        assertTrue(repository.linkSuperset(scope, record, exercise, partner))
        val detail = repository.loadExerciseDetail(scope, record, exercise)!!
        assertNotNull(detail.activeExercise.supersetGroupId)
        assertEquals(1, detail.exercises.map { it.supersetGroupId }.distinct().size)
        assertEquals(before, room.workoutRoomDao().visibleSets(exercise, scope.ownerId))
        assertTrue(repository.linkSuperset(scope, record, exercise, null))
        assertTrue(repository.loadExerciseDetail(scope, record, exercise)!!.exercises.all { it.supersetGroupId == null })
    }

    @Test fun wrongOwnerWrongRecordAndCompletedStructureChangesAreRejected() {
        val (record, exercise) = record()
        val set = storage.sets(scope, exercise).single().id
        assertFalse(repository.updateSetType(AccountScope("other"), record, set, WorkoutSetType.WARMUP))
        assertFalse(repository.updateSetType(scope, "wrong", set, WorkoutSetType.WARMUP))
        assertFalse(repository.linkSuperset(scope, record, exercise, "missing"))
        assertFalse(repository.linkExerciseGroup(AccountScope("other"), record, exercise, null, WorkoutExerciseGroupType.COMPOUND))
        assertFalse(repository.linkExerciseGroup(scope, record, exercise, "missing", WorkoutExerciseGroupType.COMPOUND))
        assertEquals(WorkoutCompletion.COMPLETED, repository.completeIfEligible(scope, record))
        assertFalse(repository.updateSetType(scope, record, set, WorkoutSetType.WARMUP))
        assertFalse(repository.addDropSet(scope, record, exercise, set))
        assertFalse(repository.linkExerciseGroup(scope, record, exercise, null, WorkoutExerciseGroupType.COMPOUND))
    }

    @Test fun incomingLegacyMetadataCannotEraseLocalSetTypes() {
        val (record, exercise) = record()
        val set = storage.sets(scope, exercise).single().id
        repository.updateSetType(scope, record, set, WorkoutSetType.WARMUP)
        repository.updateSetupNote(scope, record, exercise, "시트 4")
        repository.updateConditionNote(scope, record, "개인 컨디션")
        val incoming = LegacySyncRow(mapOf<String, Any>("id" to record, "metadata" to "{\"status\":\"in_progress\",\"memo\":\"remote note\"}"))
        RoomLegacyFitnessSyncStore(room).applyRows("workout_records", listOf(incoming), scope.ownerId)
        assertEquals(WorkoutSetType.WARMUP, repository.loadExerciseDetail(scope, record, exercise)!!.sets.single().setType)
        assertEquals("시트 4", repository.loadExerciseDetail(scope, record, exercise)!!.notes.setup)
        assertEquals("개인 컨디션", repository.loadSession(scope, record)!!.conditionNote)
        assertEquals("remote note", JSONObject(room.workoutRoomDao().visibleRecord(record, scope.ownerId)!!.metadata).getString("memo"))
    }

    @Test fun legacyPayloadOmitsLocalStructureAndLeavesSqliteUntouched() {
        val (record, exercise) = record()
        repository.updateSetType(scope, record, storage.sets(scope, exercise).single().id, WorkoutSetType.WARMUP)
        repository.updateSetupNote(scope, record, exercise, "개인 기구 설정")
        repository.updateConditionNote(scope, record, "개인 컨디션")
        val before = room.workoutRoomDao().visibleRecord(record, scope.ownerId)!!.metadata
        val encoder = LegacyFitnessSyncAdapter::class.java.getDeclaredMethod("tableRowsToJson", String::class.java, String::class.java)
        encoder.isAccessible = true
        val payload = encoder.invoke(LegacyFitnessSyncAdapter(room), "workout_records", scope.ownerId) as JSONArray
        assertFalse(payload.getJSONObject(0).getJSONObject("metadata").has("workout_structure_v1"))
        assertEquals(before, room.workoutRoomDao().visibleRecord(record, scope.ownerId)!!.metadata)
    }

    @Test fun comparisonUsesTheEarlierCompletedSameRoutineAndIgnoresFutureSessions() {
        fun routineRecord(date: String, warmup: Boolean, complete: Boolean): String {
            val (record, exercise) = record(date)
            val dao = room.workoutRoomDao()
            val row = dao.visibleRecord(record, scope.ownerId)!!
            dao.updateRecord(row.copy(metadata = JSONObject(row.metadata).put("routine_id", "routine").toString()))
            if (warmup) repository.updateSetType(scope, record, storage.sets(scope, exercise).single().id, WorkoutSetType.WARMUP)
            if (complete) repository.completeIfEligible(scope, record)
            return record
        }
        val previous = routineRecord("2026-10-13", false, true)
        val current = routineRecord("2026-10-14", true, false)
        routineRecord("2026-10-15", false, true)
        val session = repository.loadSession(scope, current)!!
        assertEquals(previous, session.previousRoutine!!.recordId)
        assertEquals(200.0, session.previousRoutine!!.totals!!.volumeKg, 0.0)
        assertEquals(0.0, session.routineTotals!!.volumeKg, 0.0)
    }
}
