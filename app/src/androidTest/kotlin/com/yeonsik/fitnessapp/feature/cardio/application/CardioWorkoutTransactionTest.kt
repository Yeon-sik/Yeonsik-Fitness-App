package com.yeonsik.fitnessapp.feature.cardio.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yeonsik.fitnessapp.cardio.CardioActivityType
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.CardioRoomDao
import com.yeonsik.fitnessapp.core.database.CardioSessionsRoomEntity
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.feature.workout.application.WorkoutSessionApplicationService
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRepositoryImplementation
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRoomStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that composed cardio/workout writes share one Room rollback boundary. */
@RunWith(AndroidJUnit4::class)
class CardioWorkoutTransactionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val owner = "cardio-transaction-owner"
    private val scope = AccountScope(owner)
    private lateinit var room: FitnessRoomDatabase
    private lateinit var transactionRunner: RoomTransactionRunner
    private lateinit var workoutRepository: WorkoutRepositoryApi

    @Before
    fun setUp() {
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionRunner = RoomTransactionRunner(room)
        workoutRepository = WorkoutRepositoryImplementation(
            WorkoutRoomStorage(room, context, transactionRunner)
        )
    }

    @After
    fun tearDown() {
        if (this::room.isInitialized) {
            room.close()
        }
    }

    @Test
    fun startRollsBackWorkoutRowsWhenCardioWriteFails() {
        val failingCardio = FailingStartCardioRepository(room.cardioRoomDao())
        val service = CardioSessionApplicationService(
            failingCardio,
            workoutRepository,
            transactionRunner,
            owner
        )

        assertThrows(IllegalStateException::class.java) {
            service.start(scope, CardioActivityType.WALKING, "2026-09-10")
        }

        val recordId = failingCardio.attemptedRecordId
        assertNotNull(recordId)
        assertNull(room.workoutRoomDao().visibleRecord(recordId!!, owner))
        assertNull(room.cardioRoomDao().session(recordId, owner))
    }

    @Test
    fun completeRollsBackWorkoutRowsWhenCardioWriteFails() {
        val recordId = createWorkoutCardioRecord()
        insertCardioSession(recordId, CardioSessionSnapshot.STATUS_PAUSED, 10_000L)
        val recordBefore = room.workoutRoomDao().visibleRecord(recordId, owner)
        val sessionBefore = room.cardioRoomDao().session(recordId, owner)
        val snapshot = CardioSessionSnapshot(
            recordId,
            CardioActivityType.WALKING.id(),
            CardioActivityType.WALKING.labelKo(),
            CardioSessionSnapshot.STATUS_PAUSED,
            1_000L,
            null,
            10_000L,
            0.0,
            0,
            "searching",
            null
        )
        val failingCardio = FailingCompleteCardioRepository(room.cardioRoomDao(), snapshot)
        val complete = CompleteCardio(failingCardio, workoutRepository, transactionRunner)

        assertThrows(IllegalStateException::class.java) {
            complete.execute(scope, snapshot, null)
        }

        assertEquals(recordBefore, room.workoutRoomDao().visibleRecord(recordId, owner))
        assertEquals(sessionBefore, room.cardioRoomDao().session(recordId, owner))
        assertEquals(0, room.workoutRoomDao().visibleSets(
            room.workoutRoomDao().visibleExercises(recordId, owner).first().id,
            owner
        ).size)
    }

    @Test
    fun cancelRollsBackGpsRowsWhenWorkoutDeleteFails() {
        val recordId = createWorkoutCardioRecord()
        insertCardioSession(recordId, CardioSessionSnapshot.STATUS_TRACKING, 0L)
        room.cardioRoomDao().insertRoutePoint(
            recordId, owner, 2_000L, 37.0, 127.0, 5.0, null, 0.0
        )
        val sessionBefore = room.cardioRoomDao().session(recordId, owner)
        val failingWorkout = FailingDeleteWorkoutRepository(workoutRepository)
        val service = CardioSessionApplicationService(
            DeletingCardioRepository(room.cardioRoomDao()),
            failingWorkout,
            transactionRunner,
            owner
        )

        assertThrows(IllegalStateException::class.java) {
            service.cancel(scope, recordId)
        }

        assertEquals(sessionBefore, room.cardioRoomDao().session(recordId, owner))
        assertEquals(1, room.cardioRoomDao().routePointCount(recordId, owner))
        assertNotNull(room.workoutRoomDao().visibleRecord(recordId, owner))
    }

    @Test
    fun workoutDeleteRollsBackGpsRowsWhenCommonDeleteReturnsFalse() {
        val recordId = createWorkoutCardioRecord()
        insertCardioSession(recordId, CardioSessionSnapshot.STATUS_TRACKING, 0L)
        val sessionBefore = room.cardioRoomDao().session(recordId, owner)
        val failingWorkout = FailingDeleteWorkoutRepository(workoutRepository)
        val cardioService = CardioSessionApplicationService(
            DeletingCardioRepository(room.cardioRoomDao()),
            failingWorkout,
            transactionRunner,
            owner
        )
        val service = WorkoutSessionApplicationService(
            failingWorkout,
            cardioService,
            transactionRunner
        )

        assertThrows(IllegalStateException::class.java) {
            service.delete(scope, recordId)
        }

        assertEquals(sessionBefore, room.cardioRoomDao().session(recordId, owner))
        assertNotNull(room.workoutRoomDao().visibleRecord(recordId, owner))
    }

    private fun createWorkoutCardioRecord(): String = workoutRepository.createCardioSession(
        scope,
        "2026-09-10",
        CardioActivityType.WALKING.id(),
        CardioActivityType.WALKING.labelKo()
    )

    private fun insertCardioSession(recordId: String, status: String, activeDurationMillis: Long) {
        val now = 1_000L
        room.cardioRoomDao().insertSession(
            CardioSessionsRoomEntity(
                recordId,
                owner,
                CardioActivityType.WALKING.id(),
                status,
                now,
                if (status == CardioSessionSnapshot.STATUS_TRACKING) now else null,
                activeDurationMillis,
                0.0,
                0L,
                null,
                null,
                null,
                null,
                "searching",
                now
            )
        )
    }

    private class FailingStartCardioRepository(
        private val dao: CardioRoomDao
    ) : CardioRepositoryApi {
        var attemptedRecordId: String? = null

        override fun loadSession(scope: AccountScope, recordId: String): CardioSessionSnapshot? = null

        override fun startSession(
            scope: AccountScope,
            recordId: String,
            activityType: CardioActivityType
        ): Boolean {
            attemptedRecordId = recordId
            dao.insertSession(
                CardioSessionsRoomEntity(
                    recordId,
                    scope.ownerId,
                    activityType.id(),
                    CardioSessionSnapshot.STATUS_TRACKING,
                    1_000L,
                    1_000L,
                    0L,
                    0.0,
                    0L,
                    null,
                    null,
                    null,
                    null,
                    "searching",
                    1_000L
                )
            )
            return false
        }
    }

    private class FailingCompleteCardioRepository(
        private val dao: CardioRoomDao,
        private val snapshot: CardioSessionSnapshot
    ) : CardioRepositoryApi {
        override fun loadSession(scope: AccountScope, recordId: String): CardioSessionSnapshot = snapshot

        override fun completeSession(
            scope: AccountScope,
            recordId: String,
            activeDurationMillis: Long
        ): Boolean {
            dao.complete(recordId, scope.ownerId, activeDurationMillis, 2_000L)
            return false
        }
    }

    private class DeletingCardioRepository(
        private val dao: CardioRoomDao
    ) : CardioRepositoryApi {
        override fun loadSession(scope: AccountScope, recordId: String): CardioSessionSnapshot? = null

        override fun isCardioSession(scope: AccountScope, recordId: String): Boolean =
            dao.ownsSession(recordId, scope.ownerId) != null

        override fun deleteLocalData(scope: AccountScope, recordId: String): Boolean {
            val routeRows = dao.deleteRoutePoints(recordId, scope.ownerId)
            val sessionRows = dao.deleteSession(recordId, scope.ownerId)
            return routeRows > 0 || sessionRows > 0
        }
    }

    private class FailingDeleteWorkoutRepository(
        private val delegate: WorkoutRepositoryApi
    ) : WorkoutRepositoryApi by delegate {
        override fun deleteSession(scope: AccountScope, recordId: String): Boolean = false
    }
}
