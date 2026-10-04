package com.yeonsik.fitnessapp.feature.cardio.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.cardio.model.*
import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.feature.cardio.data.CardioRepository
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRepositoryImplementation
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRoomStorage
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** All fixtures use a separate in-memory database; production data is never opened or cleared. */
@RunWith(AndroidJUnit4::class)
class CardioIndoorRecordingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = AccountScope("cardio-indoor-fixture")
    private lateinit var room: FitnessRoomDatabase
    private lateinit var cardio: CardioRepository
    private lateinit var workout: WorkoutRepositoryImplementation
    private lateinit var service: CardioSessionApplicationService

    @Before fun setUp() {
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        val transaction = RoomTransactionRunner(room)
        cardio = CardioRepository(room, scope.ownerId, transaction)
        workout = WorkoutRepositoryImplementation(WorkoutRoomStorage(room, context, transaction))
        service = CardioSessionApplicationService(cardio, workout, transaction, scope.ownerId)
    }
    @After fun tearDown() { room.close() }

    @Test fun indoorRunRejectsGpsAndCompletesWithAnUnmeasuredDistance() {
        val started = service.start(scope, CardioActivityType.RUNNING, "2026-10-04", CardioEnvironment.INDOOR)!!
        assertEquals("실내 달리기", started.activityLabel)
        assertFalse(started.usesGps)
        assertEquals("not_used", started.gpsStatus)
        assertFalse(cardio.acceptLocation(started.recordId, sample(started.startedAtEpochMillis + 1000L)).accepted)
        assertEquals(0, cardio.routeProjection(scope, started.recordId).rawPointCount)
        assertTrue(service.pause(scope, started.recordId))
        assertTrue(service.resume(scope, started.recordId))
        assertEquals("not_used", service.load(scope, started.recordId)!!.gpsStatus)
        val completed = service.finish(scope, started.recordId, null, null)!!
        assertEquals(CardioSessionSnapshot.STATUS_COMPLETED, completed.status)
        assertNull(completed.recordedDistanceMeters)
        val record = room.workoutRoomDao().visibleRecord(started.recordId, scope.ownerId)!!
        assertTrue(JSONObject(record.metadata).isNull("distance_meters"))
        val exercise = room.workoutRoomDao().visibleExercises(started.recordId, scope.ownerId).single()
        assertNull(room.workoutRoomDao().visibleSets(exercise.id, scope.ownerId).single().distanceMeters)
    }

    @Test fun indoorEquipmentDistanceAndHeartRateAreWrittenWithTheCompletedWorkout() {
        val started = service.start(scope, CardioActivityType.ROWING, "2026-10-04", CardioEnvironment.INDOOR)!!
        val completed = service.finish(scope, started.recordId, 132, 2500.0)!!
        assertEquals(CardioEnvironment.INDOOR, completed.environment)
        assertEquals(2500.0, completed.recordedDistanceMeters!!, 0.0)
        assertEquals(132.0, completed.averageHeartRateBpm!!, 0.0)
        val record = room.workoutRoomDao().visibleRecord(started.recordId, scope.ownerId)!!
        assertEquals(2500.0, JSONObject(record.metadata).getDouble("distance_meters"), 0.0)
        val exercise = room.workoutRoomDao().visibleExercises(started.recordId, scope.ownerId).single()
        assertEquals(2500.0, room.workoutRoomDao().visibleSets(exercise.id, scope.ownerId).single().distanceMeters!!, 0.0)
        assertEquals(0, cardio.routeProjection(scope, started.recordId).rawPointCount)
    }

    @Test fun fixedEquipmentRejectsOutdoorBeforeAnyWorkoutIsCreated() {
        for (type in CardioActivityType.entries.filterNot { it.supportsOutdoorGps }) {
            assertThrows(IllegalArgumentException::class.java) {
                service.start(scope, type, "2026-10-04", CardioEnvironment.OUTDOOR)
            }
            assertNull(workout.latestInProgressSession(scope))
            val session = service.start(scope, type, "2026-10-04", CardioEnvironment.INDOOR)!!
            assertFalse(session.usesGps)
            service.cancel(scope, session.recordId)
            assertNull(service.load(scope, session.recordId))
        }
    }

    @Test fun outdoorGpsRecordingStillAccumulatesDistanceAndRejectsManualEquipmentInput() {
        val session = service.start(scope, CardioActivityType.RUNNING, "2026-10-04", CardioEnvironment.OUTDOOR)!!
        assertTrue(session.usesGps)
        assertTrue(cardio.acceptLocation(session.recordId, sample(session.startedAtEpochMillis + 1000L)).accepted)
        assertTrue(cardio.acceptLocation(session.recordId,
            sample(session.startedAtEpochMillis + 11000L, 37.0002)).accepted)
        assertTrue(service.load(scope, session.recordId)!!.distanceMeters > 0)
        assertFalse(cardio.updateManualDistance(scope, session.recordId, 500.0))
        assertEquals(2, cardio.routeProjection(scope, session.recordId).rawPointCount)
        assertTrue(service.pause(scope, session.recordId))
        assertFalse(cardio.acceptLocation(session.recordId,
            sample(session.startedAtEpochMillis + 21000L, 37.0004)).accepted)
        assertTrue(service.resume(scope, session.recordId))
        val measured = service.load(scope, session.recordId)!!.distanceMeters
        val completed = service.finish(scope, session.recordId, null, null)!!
        assertEquals(measured, completed.recordedDistanceMeters!!, 0.0)
        val record = room.workoutRoomDao().visibleRecord(session.recordId, scope.ownerId)!!
        assertEquals(measured, JSONObject(record.metadata).getDouble("distance_meters"), 0.0)
        val exercise = room.workoutRoomDao().visibleExercises(session.recordId, scope.ownerId).single()
        assertEquals(measured, room.workoutRoomDao().visibleSets(exercise.id, scope.ownerId).single().distanceMeters!!, 0.0)
    }

    @Test fun recentUseIsAccountScopedAndExcludesDeletedWorkouts() {
        val first = service.start(scope, CardioActivityType.WALKING, "2026-10-04", CardioEnvironment.INDOOR)!!
        service.finish(scope, first.recordId, null, null)
        assertTrue(cardio.lastStartedAtByActivity(scope).containsKey("walking"))
        assertThrows(IllegalStateException::class.java) { cardio.lastStartedAtByActivity(AccountScope("another-owner")) }
        assertTrue(workout.deleteSession(scope, first.recordId))
        assertFalse(cardio.lastStartedAtByActivity(scope).containsKey("walking"))
    }

    @Test fun repeatedStartUsesTheExistingSessionAndInvalidInputDoesNotCompleteIt() {
        val first = service.start(scope, CardioActivityType.CYCLING, "2026-10-04", CardioEnvironment.INDOOR)!!
        val repeated = service.start(scope, CardioActivityType.CYCLING, "2026-10-04", CardioEnvironment.INDOOR)!!
        assertEquals(first.recordId, repeated.recordId)
        for (distance in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { service.finish(scope, first.recordId, null, distance) }
            assertEquals(CardioSessionSnapshot.STATUS_TRACKING, service.load(scope, first.recordId)!!.status)
            assertNull(service.load(scope, first.recordId)!!.manualDistanceMeters)
        }
    }

    private fun sample(time: Long, latitude: Double = 37.0) =
        CardioLocationSample(latitude, 127.0, 5f, time, null)
}
