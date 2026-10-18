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
import com.yeonsik.fitnessapp.backup.LocalDataBackupService
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.WorkoutSetupNoteRoomEntity
import com.yeonsik.fitnessapp.core.database.backup.RoomBackupDatabaseStorage
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Isolated Room only; never accesses an installed user's data or a remote service. */
class WorkoutNotesStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val owner = AccountScope("note-owner")
    private lateinit var room: FitnessRoomDatabase
    private lateinit var storage: WorkoutRoomStorage
    private lateinit var repository: WorkoutRepositoryImplementation
    private val input = WorkoutSetInput(10.0, 12, null, null, null, null, 2, 90, true,
        LoadState.EXTERNAL_LOAD, 10.0, MassUnit.KG)

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        storage = WorkoutRoomStorage(room, context)
        repository = WorkoutRepositoryImplementation(storage)
    }
    @After fun close() { room.close() }

    private fun record(scope: AccountScope = owner, presetId: String = "arms_dumbbell_curl"): Pair<String, String> {
        val record = repository.createEmptySession(scope, "2026-10-16")
        val preset = ExerciseFamilyCatalog.load(context).runtimeCatalog().preset(presetId)!!
        assertTrue(repository.addExercise(scope, record, ExerciseMasterAdapter.toWorkoutExerciseReplacement(preset)))
        val exercise = storage.exercises(scope, record).single().id
        assertTrue(repository.addTypedSet(scope, record, exercise, 1, input))
        return record to exercise
    }
    private fun detail(record: Pair<String, String>, scope: AccountScope = owner) =
        repository.loadExerciseDetail(scope, record.first, record.second)!!

    @Test fun settingsCarryForwardButCompletedSnapshotsConditionAndRecordedSetsStayUnchanged() {
        val first = record()
        val sets = room.workoutRoomDao().visibleSets(first.second, owner.ownerId)
        assertTrue(repository.updateSetupNote(owner, first.first, first.second, "  시트 4 / 등받이 2  "))
        assertTrue(repository.updateConditionNote(owner, first.first, "수면 6시간"))
        assertEquals(WorkoutCompletion.COMPLETED, repository.completeIfEligible(owner, first.first))
        val completedMetadata = room.workoutRoomDao().visibleRecord(first.first, owner.ownerId)!!.metadata
        assertEquals(WorkoutCompletion.COMPLETED, repository.completeIfEligible(owner, first.first))
        val next = record()
        assertEquals("시트 4 / 등받이 2", detail(next).notes.setup)
        assertTrue(detail(next).notes.condition.isEmpty())
        assertEquals(2, detail(next).inputBasis.implementMultiplier)
        assertTrue(repository.updateSetupNote(owner, next.first, next.second, "시트 5"))
        val reloaded = WorkoutRepositoryImplementation(WorkoutRoomStorage(room, context))
        assertEquals("시트 4 / 등받이 2", reloaded.loadExerciseDetail(owner, first.first, first.second)!!.notes.setup)
        assertEquals("수면 6시간", reloaded.loadSession(owner, first.first)!!.conditionNote)
        assertEquals(completedMetadata, room.workoutRoomDao().visibleRecord(first.first, owner.ownerId)!!.metadata)
        assertEquals(sets, room.workoutRoomDao().visibleSets(first.second, owner.ownerId))
        assertFalse(repository.updateSetupNote(owner, first.first, first.second, "변경 금지"))
        assertFalse(repository.updateConditionNote(owner, first.first, "변경 금지"))
        assertTrue(repository.updateSetupNote(owner, next.first, next.second, ""))
        assertEquals("", detail(record()).notes.setup)
    }

    @Test fun settingsAreOwnerAndExactExerciseScopedAndSurviveSessionDeletion() {
        val first = record()
        assertTrue(repository.updateSetupNote(owner, first.first, first.second, "그립 좁게"))
        val other = AccountScope("other-owner")
        assertEquals("", detail(record(other), other).notes.setup)
        val catalog = ExerciseFamilyCatalog.load(context).runtimeCatalog()
        val firstPreset = catalog.preset("arms_dumbbell_curl")!!
        val otherVariant = catalog.presetsById.values.first {
            it.familyId == firstPreset.familyId && it.canonicalVariantKey != firstPreset.canonicalVariantKey &&
                it.recordType == "weight_reps" && it.allowedLoadStates.contains(LoadState.EXTERNAL_LOAD)
        }
        assertEquals("", detail(record(presetId = otherVariant.presetId)).notes.setup)
        assertEquals("", detail(record(presetId = "legs_barbell_back_squat")).notes.setup)
        assertFalse(repository.updateSetupNote(other, first.first, first.second, "침범"))
        val differentRecord = record()
        assertFalse(repository.updateSetupNote(owner, differentRecord.first, first.second, "잘못된 기록"))
        assertFalse(repository.updateConditionNote(other, first.first, "침범"))
        assertFalse(repository.updateSetupNote(owner, first.first, first.second, "x".repeat(WORKOUT_NOTE_MAX_LENGTH + 1)))
        assertTrue(repository.deleteSession(owner, first.first))
        assertEquals("그립 좁게", detail(record()).notes.setup)
    }

    @Test fun manualExercisesWithTheSameNameDoNotShareSettingsAndDoNotGainCatalogAssumptions() {
        fun manual(): Pair<String, String> {
            val record = repository.createEmptySession(owner, "2026-10-16")
            repository.addManualExercise(owner, record, ManualWorkoutExercise("내 운동", BodyPart.ARMS,
                EquipmentType.DUMBBELL, "weight_reps"))
            return record to storage.exercises(owner, record).single().id
        }
        val first = manual()
        assertTrue(repository.updateSetupNote(owner, first.first, first.second, "이 기구 설정"))
        assertEquals("이 기구 설정", detail(first).notes.setup)
        assertFalse(detail(first).notes.setupCarriesForward)
        assertEquals(WorkoutInputBasis(), detail(first).inputBasis)
        assertEquals("", detail(manual()).notes.setup)
    }

    @Test fun backupRestoresSettingsAndPrivateSessionNotesUnderTheDestinationOwnerAndAcceptsV55() {
        val first = record()
        repository.updateSetupNote(owner, first.first, first.second, "시트 4")
        repository.updateConditionNote(owner, first.first, "피곤함")
        repository.completeIfEligible(owner, first.first)
        val output = ByteArrayOutputStream()
        LocalDataBackupService(RoomBackupDatabaseStorage(room), owner.ownerId, owner.ownerId).writeBackup(output)
        val target = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        try {
            val restoredOwner = AccountScope("restored-owner")
            val importer = LocalDataBackupService(RoomBackupDatabaseStorage(target), restoredOwner.ownerId, restoredOwner.ownerId)
            importer.restoreBackup(ByteArrayInputStream(output.toByteArray()))
            val restored = WorkoutRepositoryImplementation(WorkoutRoomStorage(target, context))
            assertEquals("시트 4", restored.loadExerciseDetail(restoredOwner, first.first, first.second)!!.notes.setup)
            assertEquals("피곤함", restored.loadSession(restoredOwner, first.first)!!.conditionNote)
            target.openHelper.writableDatabase.query("SELECT user_id,note FROM workout_setup_notes").use {
                assertTrue(it.moveToFirst())
                assertEquals(restoredOwner.ownerId, it.getString(0)); assertEquals("시트 4", it.getString(1))
            }
            val legacy = JSONObject(output.toString("UTF-8"))
            legacy.put("databaseVersion", 55)
            legacy.getJSONObject("tables").remove("workout_setup_notes")
            importer.previewBackup(ByteArrayInputStream(legacy.toString().toByteArray(Charsets.UTF_8)))
        } finally { target.close() }
    }

    @Test fun anonymousClaimPreservesExistingAccountSettingsAndMovesOtherKeys() {
        val dao = room.workoutRoomDao()
        dao.saveSetupNote(WorkoutSetupNoteRoomEntity("anonymous", "same", "익명 설정", "time"))
        dao.saveSetupNote(WorkoutSetupNoteRoomEntity(owner.ownerId, "same", "계정 설정", "time"))
        dao.saveSetupNote(WorkoutSetupNoteRoomEntity("anonymous", "other", "새 설정", "time"))
        room.runInTransaction {
            room.accountOwnershipRoomDao().claimWorkoutSetupNotes("anonymous", owner.ownerId)
            room.accountOwnershipRoomDao().deleteAnonymousWorkoutSetupNotes("anonymous")
        }
        assertEquals("계정 설정", dao.setupNote(owner.ownerId, "same")!!.note)
        assertEquals("새 설정", dao.setupNote(owner.ownerId, "other")!!.note)
        assertNull(dao.setupNote("anonymous", "same"))
    }
}
