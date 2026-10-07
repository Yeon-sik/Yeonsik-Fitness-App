package com.yeonsik.fitnessapp.feature.workout.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitnessapp.backup.LocalDataBackupService
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
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

/** Entirely in-memory. These tests never open, clear, or migrate the user's database. */
class ManualExerciseCanonicalLinkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = AccountScope("manual-test-owner")
    private lateinit var room: FitnessRoomDatabase
    private lateinit var storage: WorkoutRoomStorage
    private lateinit var repository: WorkoutRepositoryImplementation
    private val draft = ManualWorkoutExercise("당시 입력한 컬", BodyPart.ARMS, EquipmentType.DUMBBELL, "weight_reps")
    private val input = WorkoutSetInput(10.0, 12, null, null, null, null, 2, 90, true,
        LoadState.EXTERNAL_LOAD, 10.0, MassUnit.KG)

    @Before fun open() {
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        storage = WorkoutRoomStorage(room, context)
        repository = WorkoutRepositoryImplementation(storage)
    }
    @After fun close() { room.close() }

    @Test fun linkingAndRelinkingPreserveEveryOriginalRowAndHistoricalVolumeAcrossAllReadPaths() {
        val recordId = repository.createEmptySession(scope, "2026-10-01")
        assertTrue(repository.addManualExercise(scope, recordId, draft))
        val manualId = storage.exercises(scope, recordId).single().id
        assertNull(storage.exercises(scope, recordId).single().familyIdentity)
        assertTrue(repository.addTypedSet(scope, recordId, manualId, 1, input))
        assertEquals(WorkoutCompletion.COMPLETED, repository.completeIfEligible(scope, recordId))
        val dao = room.workoutRoomDao()
        val recordBefore = dao.visibleRecord(recordId, scope.ownerId)
        val exerciseBefore = dao.visibleExercise(manualId, scope.ownerId)
        val setsBefore = dao.visibleSets(manualId, scope.ownerId)
        val metricsBefore = storage.metrics(scope, recordId)
        assertEquals(120.0, metricsBefore.totalVolumeKg, 0.0)

        val canonicalRecord = repository.createEmptySession(scope, "2026-10-02")
        val catalog = ExerciseFamilyCatalog.load(context)
        val preset = catalog.runtimeCatalog().preset("arms_dumbbell_curl")!!
        assertEquals(2, preset.implementMultiplier)
        assertTrue(repository.addExercise(scope, canonicalRecord, ExerciseMasterAdapter.toWorkoutExerciseReplacement(preset)))
        val canonicalExercise = storage.exercises(scope, canonicalRecord).single()
        assertTrue(storage.recentExerciseHistories(scope, canonicalExercise, canonicalRecord, 5).isEmpty())

        assertTrue(repository.linkManualExerciseToCanonical(scope, recordId, manualId, preset.canonicalPresetId))
        val linked = storage.exercises(scope, recordId).single()
        assertEquals("manual", linked.exerciseId)
        assertEquals(draft.name, linked.name)
        assertEquals(preset.canonicalVariantKey, linked.familyIdentity!!.canonicalVariantKey)
        assertEquals(recordBefore, dao.visibleRecord(recordId, scope.ownerId))
        assertEquals(exerciseBefore, dao.visibleExercise(manualId, scope.ownerId))
        assertEquals(setsBefore, dao.visibleSets(manualId, scope.ownerId))
        assertEquals(metricsBefore, storage.metrics(scope, recordId))
        assertEquals(120.0, repository.loadExerciseDetail(scope, recordId, manualId)!!.volumeBySetId.values.single(), 0.0)
        val history = storage.recentExerciseHistories(scope, canonicalExercise, canonicalRecord, 5).single()
        assertEquals(recordId, history.recordId)
        assertEquals(120.0, history.totalVolumeKg, 0.0)
        assertEquals(120.0, storage.bests(scope, canonicalExercise, canonicalRecord).byLoadState.single().bestSessionVolumeKg, 0.0)
        val performance = storage.exercisePerformance(scope, "2026-10-01", "2026-10-01").single()
        assertTrue(performance.performanceKey.startsWith(preset.familyId + "|" + preset.canonicalVariantKey + "|"))
        assertEquals(120.0, performance.volumeKg!!, 0.0)
        assertEquals(120.0, storage.completedSessionSummaries(scope, "2026-10-01", "2026-10-01").single().totalVolumeKg, 0.0)
        assertTrue(repository.lastPerformedAtByCanonicalPreset(scope).containsKey(preset.canonicalPresetId))

        assertTrue(repository.linkManualExerciseToCanonical(scope, recordId, manualId, "arms_barbell_curl"))
        assertTrue(storage.recentExerciseHistories(scope, canonicalExercise, canonicalRecord, 5).isEmpty())
        assertEquals(1, dao.manualExerciseLinks(recordId, scope.ownerId).size)
        assertEquals(recordBefore, dao.visibleRecord(recordId, scope.ownerId))
        assertEquals(exerciseBefore, dao.visibleExercise(manualId, scope.ownerId))
        assertEquals(setsBefore, dao.visibleSets(manualId, scope.ownerId))
        assertEquals(metricsBefore, storage.metrics(scope, recordId))
    }

    @Test fun invalidLinksAndGenericCompletedReplacementAreRejectedWithoutMutation() {
        val recordId = repository.createEmptySession(scope, "2026-10-01")
        assertTrue(repository.addManualExercise(scope, recordId, draft))
        val exerciseId = storage.exercises(scope, recordId).single().id
        assertFalse(repository.linkManualExerciseToCanonical(scope, recordId, exerciseId, "arms_dumbbell_curl"))
        repository.addTypedSet(scope, recordId, exerciseId, 1, input)
        repository.completeIfEligible(scope, recordId)
        val before = room.workoutRoomDao().visibleExercise(exerciseId, scope.ownerId)
        assertFalse(repository.linkManualExerciseToCanonical(AccountScope("other"), recordId, exerciseId, "arms_dumbbell_curl"))
        assertFalse(repository.linkManualExerciseToCanonical(scope, "wrong-record", exerciseId, "arms_dumbbell_curl"))
        assertFalse(repository.linkManualExerciseToCanonical(scope, recordId, exerciseId, "missing"))
        assertFalse(repository.linkManualExerciseToCanonical(scope, recordId, exerciseId, "knee_push_up"))
        assertFalse(repository.addManualExercise(scope, recordId, draft))
        val preset = ExerciseFamilyCatalog.load(context).runtimeCatalog().preset("arms_dumbbell_curl")!!
        assertFalse(repository.replaceExercise(scope, recordId, exerciseId, ExerciseMasterAdapter.toWorkoutExerciseReplacement(preset)))
        assertEquals(before, room.workoutRoomDao().visibleExercise(exerciseId, scope.ownerId))
        assertTrue(room.workoutRoomDao().manualExerciseLinks(recordId, scope.ownerId).isEmpty())
    }

    @Test fun newKettlebellPresetSearchesSelectsAndRecordsThroughTheExistingRepository() {
        val preset = ExerciseFamilyCatalog.load(context).runtimeCatalog().preset("legs_kettlebell_sumo_squat")!!
        val recordId = repository.createEmptySession(scope, "2026-10-03")
        assertTrue(repository.addExercise(scope, recordId, ExerciseMasterAdapter.toWorkoutExerciseReplacement(preset)))
        val exercise = storage.exercises(scope, recordId).single()
        assertEquals("squat", exercise.familyIdentity!!.familyId)
        assertEquals("케틀벨", exercise.equipment)
        assertTrue(repository.addTypedSet(scope, recordId, exercise.id, 1, input.copy(weightKg = 24.0)))
        assertEquals(WorkoutCompletion.COMPLETED, repository.completeIfEligible(scope, recordId))
        assertEquals(288.0, storage.metrics(scope, recordId).totalVolumeKg, 0.0)
    }

    @Test fun linkedMetadataSurvivesBackupRestoreAndOldBackupsRemainReadable() {
        val recordId = repository.createEmptySession(scope, "2026-10-01")
        repository.addManualExercise(scope, recordId, draft)
        val exerciseId = storage.exercises(scope, recordId).single().id
        repository.addTypedSet(scope, recordId, exerciseId, 1, input)
        repository.completeIfEligible(scope, recordId)
        repository.linkManualExerciseToCanonical(scope, recordId, exerciseId, "arms_dumbbell_curl")
        val exporter = LocalDataBackupService(RoomBackupDatabaseStorage(room), scope.ownerId, scope.ownerId)
        val output = ByteArrayOutputStream()
        exporter.writeBackup(output)
        val root = JSONObject(output.toString("UTF-8"))
        assertEquals(1, root.getJSONObject("tables").getJSONArray("workout_manual_exercise_links").length())
        val target = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        try {
            val restoredScope = AccountScope("restored-owner")
            val importer = LocalDataBackupService(RoomBackupDatabaseStorage(target), restoredScope.ownerId, restoredScope.ownerId)
            importer.restoreBackup(ByteArrayInputStream(output.toByteArray()))
            val restored = WorkoutRoomStorage(target, context).exercises(restoredScope, recordId).single()
            assertEquals("manual", restored.exerciseId)
            assertEquals("arms_dumbbell_curl", restored.familyIdentity!!.canonicalPresetId)
            assertEquals(120.0, WorkoutRoomStorage(target, context).metrics(restoredScope, recordId).totalVolumeKg, 0.0)
            root.put("databaseVersion", 54)
            root.getJSONObject("tables").remove("workout_manual_exercise_links")
            importer.previewBackup(ByteArrayInputStream(root.toString().toByteArray(Charsets.UTF_8)))
        } finally { target.close() }
    }
}
