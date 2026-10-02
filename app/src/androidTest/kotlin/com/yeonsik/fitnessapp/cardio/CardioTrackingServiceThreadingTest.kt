package com.yeonsik.fitnessapp.cardio

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType
import com.yeonsik.fitnessapp.BuildConfig
import com.yeonsik.fitnessapp.MainActivity
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.core.database.CardioSessionsRoomEntity
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider
import com.yeonsik.fitnessapp.feature.cardio.data.CardioRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Opt-in emulator regression for the real foreground service with Room's main-thread guard enabled.
 * Build the app and instrumentation with FITNESS_SURFACE=test-friends, then opt in with
 * -e cardioServiceThreadingQa true. Fine/coarse location permission must already be granted
 * on the emulator. No account preference is written and no
 * persistent database is opened: the provider is temporarily pointed at an in-memory fixture.
 */
@RunWith(AndroidJUnit4::class)
class CardioTrackingServiceThreadingTest {
    private lateinit var context: Context
    private lateinit var owner: String
    private lateinit var room: FitnessRoomDatabase
    private lateinit var providerField: Field
    private var previousProvider: FitnessRoomDatabase? = null
    private var providerReplaced = false
    private var scenario: ActivityScenario<MainActivity>? = null
    private val queries = ConcurrentLinkedQueue<QueryThread>()
    private val trackingLooper = AtomicReference<Looper?>()
    private val fixtureExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "CardioThreadingTestFixture")
    }

    @Before
    fun setUp() {
        // Evaluate every safety gate before obtaining app context, preferences, or any database.
        assumeTrue(
            "This service regression requires explicit cardioServiceThreadingQa=true opt-in.",
            InstrumentationRegistry.getArguments().getString("cardioServiceThreadingQa") == "true"
        )
        assumeTrue("This regression is restricted to an Android SDK emulator.", isSdkEmulator())
        assumeTrue(
            "Build with FITNESS_SURFACE=test-friends; personal account configuration is out of scope.",
            BuildConfig.FITNESS_SURFACE == "test-friends"
        )
        context = ApplicationProvider.getApplicationContext()
        assumeTrue(
            "Grant fine and coarse location permission on the disposable emulator before running.",
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )

        // The test-friends surface has no managed rebind. Read the service's same effective owner.
        owner = SupabaseConfigStore(context).load().effectiveUserId()
        context.stopService(Intent(context, CardioTrackingService::class.java))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java)
            .setQueryCallback(object : RoomDatabase.QueryCallback {
                override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                    if (!sqlQuery.contains("cardio_", ignoreCase = true)) return
                    val currentLooper = Looper.myLooper()
                    val serviceWorker = Thread.currentThread().name == "CardioTrackingWorker"
                    if (serviceWorker && currentLooper != null) trackingLooper.set(currentLooper)
                    queries.add(QueryThread(sqlQuery, currentLooper == Looper.getMainLooper(), serviceWorker))
                }
            }, { command -> command.run() })
            .build()
        // Keep the production provider unchanged; restore its exact previous instance in teardown.
        providerField = FitnessRoomDatabaseProvider::class.java.getDeclaredField("instance").apply {
            isAccessible = true
        }
        synchronized(FitnessRoomDatabaseProvider) {
            previousProvider = providerField.get(null) as FitnessRoomDatabase?
            providerField.set(null, room)
            providerReplaced = true
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        if (this::context.isInitialized) {
            context.stopService(Intent(context, CardioTrackingService::class.java))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        // onDestroy queues cleanup before quitSafely; wait before closing the fixture connection.
        trackingLooper.get()?.thread?.join(5_000L)
        scenario?.close()
        if (providerReplaced) {
            synchronized(FitnessRoomDatabaseProvider) {
                providerField.set(null, previousProvider)
            }
        }
        if (this::room.isInitialized) room.close()
        fixtureExecutor.shutdownNow()
    }

    @Test
    fun startPauseResumeAndNotificationTickerRunWithStrictRoom() {
        val recordId = "threading-lifecycle"
        insertSession(recordId, CardioRepository.STATUS_TRACKING)

        // Prove this fixture would reproduce the original Room failure on the main looper.
        val mainFailure = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            mainFailure.set(runCatching { room.cardioRoomDao().session(recordId, owner) }.exceptionOrNull())
        }
        assertTrue(mainFailure.get() is IllegalStateException)
        assertTrue(mainFailure.get()?.message?.contains("main thread", ignoreCase = true) == true)

        sendCommand(CardioTrackingService.ACTION_START, recordId)
        awaitCondition("initial service read and tracking notification") {
            serviceSessionReadCount() > 0 && notificationTitle() == trackingTitle()
        }
        assertEquals(CardioRepository.STATUS_TRACKING, session(recordId)?.status)

        sendCommand(CardioTrackingService.ACTION_PAUSE, recordId)
        awaitCondition("pause persisted") { session(recordId)?.status == CardioRepository.STATUS_PAUSED }
        val pausedDuration = session(recordId)!!.activeDurationMs
        assertNull(session(recordId)?.lastResumedAtEpochMs)

        sendCommand(CardioTrackingService.ACTION_RESUME, recordId)
        awaitCondition("resume persisted") { session(recordId)?.status == CardioRepository.STATUS_TRACKING }
        assertNotNull(session(recordId)?.lastResumedAtEpochMs)
        assertEquals(pausedDuration, session(recordId)?.activeDurationMs)

        sendCommand(CardioTrackingService.ACTION_PAUSE, recordId)
        awaitCondition("paused notification") {
            session(recordId)?.status == CardioRepository.STATUS_PAUSED &&
                notificationTitle() == CardioActivityType.WALKING.labelKo() + " · 일시정지"
        }
        val beforeTicker = serviceSessionReadCount()
        // Let the real five-second ticker execute; do not manually call its runnable.
        awaitCondition("notification ticker Room read", 12_000L) {
            serviceSessionReadCount() > beforeTicker
        }
        assertServiceCardioQueriesAreOffMainThread()
    }

    @Test
    fun serviceRestoresExistingActiveSessionForItsOwnerWithoutRecordExtra() {
        val recordId = "threading-recovery"
        insertSession(recordId, CardioRepository.STATUS_PAUSED, activeDurationMs = 42_000L)
        insertSession(
            "threading-other-owner",
            CardioRepository.STATUS_PAUSED,
            sessionOwner = "threading-isolated-owner",
            startedAt = System.currentTimeMillis() + 60_000L
        )

        // Missing record ID uses the same activeSession recovery branch as a sticky null intent.
        sendCommand(CardioTrackingService.ACTION_START, null)
        awaitCondition("existing owner session recovery") {
            queries.any { it.serviceWorker && it.sql.contains("SELECT record_id FROM cardio_sessions") } &&
                notificationTitle() == CardioActivityType.WALKING.labelKo() + " · 일시정지"
        }
        assertEquals(recordId, onFixtureWorker { room.cardioRoomDao().activeRecordId(owner) })
        assertEquals(42_000L, session(recordId)?.activeDurationMs)
        assertEquals(CardioRepository.STATUS_PAUSED, session(recordId)?.status)
        assertEquals(2, onFixtureWorker {
            room.openHelper.readableDatabase.query("SELECT COUNT(*) FROM cardio_sessions").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        })
        assertServiceCardioQueriesAreOffMainThread()
    }

    @Test
    fun productionLocationCallbackPersistsOnTrackingLooperAndRejectsDetachedCallback() {
        insertSession("threading-worker-bootstrap", CardioRepository.STATUS_PAUSED)
        sendCommand(CardioTrackingService.ACTION_START, "threading-worker-bootstrap")
        awaitCondition("tracking worker available") { trackingLooper.get() != null }

        val recordId = "threading-location"
        insertSession(recordId, CardioRepository.STATUS_TRACKING)
        // Exercise the production callback itself without changing emulator mock-location appops
        // or depending on Play Services' live GPS. Platform lifecycle is covered by the tests above.
        val callbackService = CardioTrackingService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }.invoke(callbackService, context)
        setServiceField(callbackService, "cardioRepository", CardioRepository(room, owner))
        setServiceField(callbackService, "currentRecordId", recordId)
        val callback = CardioTrackingService::class.java
            .getDeclaredMethod("createLocationCallback", String::class.java)
            .apply { isAccessible = true }
            .invoke(callbackService, recordId) as LocationCallback
        setServiceField(callbackService, "locationCallback", callback)
        val capturedAt = System.currentTimeMillis() + 1_000L
        val points = listOf(
            location(37.0, 127.0, capturedAt),
            location(37.000045, 127.0, capturedAt + 3_000L)
        )
        onTrackingWorker { callback.onLocationResult(LocationResult.create(points)) }

        assertEquals(2, onFixtureWorker { room.cardioRoomDao().routePointCount(recordId, owner) })
        assertEquals(2L, session(recordId)?.acceptedPointCount)
        assertTrue(session(recordId)!!.distanceMeters > 4.0)
        assertEquals(CardioRepository.GPS_READY, session(recordId)?.gpsStatus)
        assertEquals(capturedAt + 3_000L, session(recordId)?.lastLocationTimeMs)

        onTrackingWorker {
            // Matches removal on pause or replacement: a queued old callback must be ignored.
            setServiceField(callbackService, "locationCallback", null)
            callback.onLocationResult(LocationResult.create(listOf(
                location(37.00009, 127.0, capturedAt + 6_000L)
            )))
        }
        assertEquals(2, onFixtureWorker { room.cardioRoomDao().routePointCount(recordId, owner) })
        assertServiceCardioQueriesAreOffMainThread()
    }

    private fun insertSession(
        recordId: String,
        status: String,
        activeDurationMs: Long = 0L,
        sessionOwner: String = owner,
        startedAt: Long = System.currentTimeMillis() - 2_000L
    ) = onFixtureWorker {
        val timestamp = "2026-10-03T00:00:00Z"
        // Mirror the shared Workout parent row even though the current Cardio join is a LEFT JOIN.
        val workout = ContentValues().apply {
            put("id", recordId)
            put("user_id", sessionOwner)
            put("date", "2026-10-03")
            put("workout_type", "cardio")
            put("category", "cardio")
            put("exercise_name", CardioActivityType.WALKING.labelKo())
            put("created_at", timestamp)
            put("updated_at", timestamp)
            put("is_backfilled", 0)
            put("device_id", "threading-emulator-fixture")
            put("source_app", "fitness")
            put("scope", "fitness")
            put("metadata", "{\"status\":\"in_progress\"}")
        }
        room.openHelper.writableDatabase.insert("workout_records", 0, workout)
        room.cardioRoomDao().insertSession(CardioSessionsRoomEntity(
            recordId, sessionOwner, CardioActivityType.WALKING.id(), status, startedAt,
            if (status == CardioRepository.STATUS_TRACKING) startedAt else null,
            activeDurationMs, 0.0, 0L, null, null, null, null,
            if (status == CardioRepository.STATUS_TRACKING) CardioRepository.GPS_SEARCHING
            else CardioRepository.GPS_STOPPED,
            startedAt
        ))
    }

    private fun sendCommand(action: String, recordId: String?) {
        scenario!!.onActivity { activity ->
            val intent = Intent(activity, CardioTrackingService::class.java).setAction(action)
            if (recordId != null) intent.putExtra(CardioTrackingService.EXTRA_RECORD_ID, recordId)
            activity.startForegroundService(intent)
        }
    }

    private fun session(recordId: String) = onFixtureWorker {
        room.cardioRoomDao().session(recordId, owner)
    }

    private fun notificationTitle(): String? = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.id == 2401 }
        ?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun trackingTitle() = CardioActivityType.WALKING.labelKo() + " 기록 중"

    private fun serviceSessionReadCount() = queries.count {
        it.serviceWorker && it.sql.contains("FROM cardio_sessions cs", ignoreCase = true)
    }

    private fun assertServiceCardioQueriesAreOffMainThread() {
        assertTrue("The real service must execute cardio SQL.", queries.any { it.serviceWorker })
        assertFalse("Cardio SQL must never reach the main looper.", queries.any { it.mainThread })
    }

    private fun <T> onFixtureWorker(block: () -> T): T =
        fixtureExecutor.submit<T> { block() }.get(10, TimeUnit.SECONDS)

    private fun onTrackingWorker(block: () -> Unit) {
        val task = FutureTask<Unit> { block() }
        assertTrue(Handler(trackingLooper.get()!!).post(task))
        task.get(10, TimeUnit.SECONDS)
    }

    private fun awaitCondition(label: String, timeoutMs: Long = 10_000L, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(25L)
        }
        assertTrue("Timed out waiting for $label", condition())
    }

    private fun location(latitude: Double, longitude: Double, capturedAt: Long) = Location("fixture").apply {
        this.latitude = latitude
        this.longitude = longitude
        accuracy = 5f
        time = capturedAt
    }

    private fun setServiceField(service: CardioTrackingService, name: String, value: Any?) {
        CardioTrackingService::class.java.getDeclaredField(name).apply { isAccessible = true }
            .set(service, value)
    }

    private fun isSdkEmulator(): Boolean =
        Build.HARDWARE in setOf("ranchu", "goldfish") &&
            (Build.PRODUCT.contains("sdk", ignoreCase = true) ||
                Build.MODEL.contains("sdk", ignoreCase = true) ||
                Build.MODEL.contains("gphone", ignoreCase = true))

    private data class QueryThread(val sql: String, val mainThread: Boolean, val serviceWorker: Boolean)
}
