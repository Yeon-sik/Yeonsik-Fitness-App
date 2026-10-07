package com.yeonsik.fitnessapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutInterchangeRepository
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRepositoryImplementation
import com.yeonsik.fitnessapp.integration.transfer.WorkoutTransferCodec
import com.yeonsik.fitnessapp.integration.transfer.WorkoutTransferService
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production JSON -> Room -> workout read/write path in a disposable DB. */
@RunWith(AndroidJUnit4::class)
class WorkoutTransferRoomImportTest {
    private lateinit var context: Context
    private lateinit var room: FitnessRoomDatabase
    private lateinit var transfer: WorkoutTransferService
    private lateinit var repository: WorkoutRepositoryImplementation
    private val scope = AccountScope("workout-transfer-history-test-owner")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        transfer = WorkoutTransferService(WorkoutInterchangeRepository(room, context), scope.ownerId)
        repository = WorkoutRepositoryImplementation(room, context)
    }

    @After
    fun tearDown() {
        room.close()
    }

    @Test
    fun completedHistoryPreservesZeroMissingRepsAndPlankDuration() {
        val json = historicalJson()
        assertImportedValuesMatch(WorkoutTransferCodec.decode(json), json)
        val record = room.workoutRoomDao().transferRecords(scope.ownerId).single()
        val exercises = room.workoutRoomDao().visibleExercises(record.id, scope.ownerId)
        val legRaise = exercises.first()
        val sets = room.workoutRoomDao().visibleSets(legRaise.id, scope.ownerId)
        assertEquals(0L, sets[0].actualReps)
        assertNull(sets[1].actualReps)
        assertEquals(1L, sets[0].isCompleted)
        assertEquals(1L, sets[1].isCompleted)
        val plank = exercises[1]
        assertEquals(FitnessRecordContract.TIME, plank.recordType)
        val plankSet = room.workoutRoomDao().visibleSets(plank.id, scope.ownerId).single()
        assertNull(plankSet.actualReps)
        assertEquals(60L, plankSet.durationSeconds)

        val exported = transfer.exportJson()
        val otherOwner = "workout-transfer-round-trip-owner"
        val otherTransfer = WorkoutTransferService(
            WorkoutInterchangeRepository(room, context), otherOwner
        )
        assertEquals(3, otherTransfer.importJson(exported).importedSets)
        val roundTrip = WorkoutTransferCodec.decode(otherTransfer.exportJson())
        assertNull(roundTrip.sessions.single().durationSeconds)
        assertEquals(0, roundTrip.sessions.single().exercises[0].sets[0].actualReps)
        assertNull(roundTrip.sessions.single().exercises[0].sets[1].actualReps)
        assertNull(roundTrip.sessions.single().exercises[0].sets[0].targetReps)
        assertNull(roundTrip.sessions.single().exercises[0].sets[0].durationSeconds)
        assertNull(roundTrip.sessions.single().exercises[0].sets[0].restSeconds)
        assertNull(roundTrip.sessions.single().exercises[0].sets[0].rpe)
        assertNull(roundTrip.sessions.single().exercises[0].sets[0].rir)
        assertEquals(60, roundTrip.sessions.single().exercises[1].sets[0].durationSeconds)
        assertNull(repository.loadSession(AccountScope(otherOwner), record.id))
        assertNull(repository.latestInProgressSession(scope))
    }

    @Test
    fun importedHistoryIsAvailableWhenRecordingTheNextWorkout() {
        transfer.importJson(historicalJson())
        val historyRecord = room.workoutRoomDao().transferRecords(scope.ownerId).single()
        val identity = ExerciseFamilyCatalog.load(context)
            .identityForStorageExerciseId("abs_bodyweight_leg_raise")!!
        val recordId = repository.createEmptySession(scope, "2026-10-07")
        assertTrue(repository.addExercise(scope, recordId, WorkoutExerciseReplacement(
            masterExerciseId = "abs_bodyweight_leg_raise",
            nameKo = "레그 레이즈",
            nameEn = null,
            bodyPart = null,
            equipmentType = null,
            equipmentVariantId = null,
            primarySubPart = null,
            recordType = FitnessRecordContract.REPS_ONLY,
            familyIdentity = identity
        )))
        val detail = repository.loadExerciseDetail(scope, recordId, null)!!
        assertEquals(historyRecord.id, detail.lastHistory!!.recordId)
        assertEquals(2, detail.lastHistory!!.sets.size)
        assertTrue(repository.addTypedSet(
            scope, recordId, detail.activeExercise.id, 1, bodyweightInput(15)
        ))
        assertEquals(15, repository.loadSession(scope, recordId)!!
            .exercises.single().completedSets.single().actualReps)
        assertEquals(WorkoutCompletion.COMPLETED, repository.completeIfEligible(scope, recordId))
        val original = room.workoutRoomDao().visibleExercises(historyRecord.id, scope.ownerId).first()
        val originalSets = room.workoutRoomDao().visibleSets(original.id, scope.ownerId)
        assertEquals(0L, originalSets[0].actualReps)
        assertNull(originalSets[1].actualReps)
    }

    @Test
    fun importedInProgressSessionCanBeResumedAndExtended() {
        val json = JSONObject(historicalJson())
        json.getJSONArray("workouts").getJSONObject(0).put("status", "in_progress")
        transfer.importJson(json.toString())
        val recordId = repository.latestInProgressSession(scope)!!
        val detail = repository.loadExerciseDetail(scope, recordId, null)!!
        assertTrue(repository.updateTypedSet(scope, recordId, detail.sets[0].id, bodyweightInput(12)))
        assertTrue(repository.addTypedSet(
            scope, recordId, detail.activeExercise.id, 3, bodyweightInput(15)
        ))
        val stored = room.workoutRoomDao().visibleSets(detail.activeExercise.id, scope.ownerId)
        assertEquals(listOf(12L, null, 15L), stored.map { it.actualReps })
        assertEquals("in_progress", repository.loadSession(scope, recordId)!!.status)
    }

    @Test
    fun malformedValuesStillFailAndStorageFailuresRollBackTheWholeImport() {
        val negative = JSONObject(historicalJson())
        firstSet(negative).put("reps", -1)
        assertThrows(IllegalArgumentException::class.java) { transfer.importJson(negative.toString()) }
        assertTrue(room.workoutRoomDao().transferRecords(scope.ownerId).isEmpty())

        val conflicting = JSONObject(historicalJson())
        firstSet(conflicting).put("weightKg", 10).put("loadState", "bodyweight")
        assertThrows(IllegalArgumentException::class.java) { transfer.importJson(conflicting.toString()) }
        assertTrue(room.workoutRoomDao().transferRecords(scope.ownerId).isEmpty())

        val wrongVolume = JSONObject(historicalJson())
        wrongVolume.getJSONArray("workouts").getJSONObject(0).getJSONArray("exercises")
            .getJSONObject(1).getJSONArray("sets").getJSONObject(0).put("volumeKg", 1)
        val error = assertThrows(IllegalArgumentException::class.java) {
            transfer.importJson(wrongVolume.toString())
        }
        assertTrue(error.message!!.contains("volumeKg"))
        assertTrue(room.workoutRoomDao().transferRecords(scope.ownerId).isEmpty())
    }

    @Test
    fun importsProvidedExternalFixtureWithoutPublishingPersonalRecords() {
        // Private fixtures are supplied as build-only assets, never checked into the repository.
        val assetName = InstrumentationRegistry.getArguments().getString("workoutTransferFixtureAsset")
        assumeTrue("No external fixture supplied", !assetName.isNullOrBlank())
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open(assetName!!)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertImportedValuesMatch(WorkoutTransferCodec.decode(json), json)
    }

    private fun assertImportedValuesMatch(source: WorkoutTransferCodec.Document, json: String) {
        val result = transfer.importJson(json)
        assertEquals(source.sessions.size, result.importedSessions)
        assertEquals(source.sessions.sumOf { it.exercises.size }, result.importedExercises)
        assertEquals(source.sessions.sumOf { session -> session.exercises.sumOf { it.sets.size } },
            result.importedSets)
        val records = room.workoutRoomDao().transferRecords(scope.ownerId)
        val catalog = ExerciseFamilyCatalog.load(context).runtimeCatalog()
        assertEquals(source.sessions.size, records.size)
        source.sessions.forEach { session ->
            val record = records.single {
                JSONObject(it.metadata).getString("transfer_source_record_id") == session.sourceRecordId
            }
            assertEquals(session.date, record.date)
            val metadata = JSONObject(record.metadata)
            assertEquals(session.status, metadata.getString("status"))
            assertEquals(session.startedAt ?: "", metadata.getString("started_at"))
            assertEquals(session.endedAt ?: "", metadata.getString("ended_at"))
            val exercises = room.workoutRoomDao().visibleExercises(record.id, scope.ownerId)
            assertEquals(session.exercises.size, exercises.size)
            session.exercises.zip(exercises).forEach { (exercise, stored) ->
                val preset = catalog.preset(exercise.presetId)
                    ?: catalog.presetForStorageExerciseId(exercise.exerciseId)!!
                assertEquals(preset.storageExerciseId, stored.exerciseId)
                val sets = room.workoutRoomDao().visibleSets(stored.id, scope.ownerId)
                assertEquals(exercise.sets.size, sets.size)
                exercise.sets.zip(sets).forEach { (set, row) ->
                    assertEquals(set.setIndex.toLong(), row.setIndex)
                    assertEquals(set.targetReps?.toLong(), row.targetReps)
                    assertEquals(set.actualReps?.toLong(), row.actualReps)
                    assertEquals(set.durationSeconds?.toLong(), row.durationSeconds)
                    assertEquals(set.weightKg, row.weightKg)
                    assertEquals(set.assistedWeightKg, row.assistedWeightKg)
                    assertEquals(set.addedWeightKg, row.addedWeightKg)
                    assertEquals(set.inputLoadValue, row.inputLoadValue)
                    assertEquals(set.inputLoadUnit, row.inputLoadUnit)
                    assertEquals(set.loadState, row.loadState)
                    assertEquals(if (set.isCompleted) 1L else 0L, row.isCompleted)
                    assertEquals(set.distanceMeters, row.distanceMeters)
                    assertEquals(set.restSeconds?.toLong(), row.restSeconds)
                    assertEquals(set.rir?.toLong(), row.rir)
                    assertEquals(set.rpe?.toLong(), row.rpe)
                    assertEquals(set.memo, row.memo)
                }
            }
            val loaded = repository.loadSession(scope, record.id)!!
            assertEquals(session.exercises.sumOf { exercise -> exercise.sets.count { it.isCompleted } },
                loaded.completedSetCount)
        }
        val duplicate = transfer.importJson(json)
        assertEquals(0, duplicate.importedSessions)
        assertEquals(source.sessions.size, duplicate.skippedDuplicateSessions)
        assertEquals(source.sessions.size, room.workoutRoomDao().transferRecords(scope.ownerId).size)
    }

    private fun bodyweightInput(reps: Int) = WorkoutSetInput(
        weightKg = null, reps = reps, durationSeconds = null, distanceMeters = null,
        assistedWeightKg = null, addedWeightKg = null, rir = null, restSeconds = null,
        completed = true, loadState = LoadState.BODYWEIGHT, inputLoadValue = null, inputLoadUnit = null
    )

    private fun firstSet(json: JSONObject): JSONObject = json.getJSONArray("workouts")
        .getJSONObject(0).getJSONArray("exercises").getJSONObject(0)
        .getJSONArray("sets").getJSONObject(0)

    private fun historicalJson() = """
        {"format":"yeonsik.workout-transfer","formatVersion":2,"sourceApp":"liftlog",
         "exportedAt":"2026-10-04T07:30:00Z","workouts":[{
          "sourceRecordId":"history-fixture","status":"completed","title":"History",
          "startedAt":"2026-10-03T03:00:00Z","endedAt":null,"memo":null,
          "exercises":[
           {"storageExerciseId":"abs_bodyweight_leg_raise","orderIndex":1,
            "recordType":"weight_reps","nameSnapshot":"레그 레이즈","defaultUiPart":"abs",
            "sets":[
             {"setIndex":1,"loadState":"bodyweight","weightKg":0,"inputLoadValue":0,
              "inputLoadUnit":"kg","reps":0,"durationSeconds":null,"completed":true},
             {"setIndex":2,"loadState":"bodyweight","reps":null,"completed":true}]},
           {"storageExerciseId":"abs_bodyweight_plank","orderIndex":2,
            "recordType":"weight_reps","nameSnapshot":"플랭크","defaultUiPart":"abs",
            "sets":[{"setIndex":1,"loadState":"bodyweight","inputLoadValue":null,
                     "inputLoadUnit":"kg","reps":null,"durationSeconds":60,"completed":true}]}
          ]}]
        }
    """.trimIndent()
}
