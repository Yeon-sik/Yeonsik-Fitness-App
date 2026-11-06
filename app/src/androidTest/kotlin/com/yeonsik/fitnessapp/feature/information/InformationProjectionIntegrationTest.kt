package com.yeonsik.fitnessapp.feature.information

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.backup.LocalDataBackupService
import com.yeonsik.fitnessapp.core.database.*
import com.yeonsik.fitnessapp.core.database.backup.RoomBackupDatabaseStorage
import com.yeonsik.fitnessapp.feature.body.data.*
import com.yeonsik.fitnessapp.feature.information.data.*
import com.yeonsik.fitnessapp.feature.information.model.*
import com.yeonsik.fitnessapp.feature.meal.data.MealReadRepository
import com.yeonsik.fitnessapp.feature.statistics.data.StatisticsReadRepository
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriod
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutReadRepository
import com.yeonsik.fitnessapp.integration.nutrition.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

class InformationProjectionIntegrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val profile = InformationProfile("projection-owner", "local")
    private val date = LocalDate.now().minusDays(1).toString()
    private val scope = AccountScope(profile.userId)
    private fun source(id: String = UUID.randomUUID().toString(), supersedes: String? = null): String = buildJsonObject {
        put("schema_version", "information-record.v1"); put("record_id", id); put("owner_app", "fitness")
        put("record_type", "fitness.observation"); put("title", "범용 자료 반영"); put("captured_at", "2026-11-06T00:00:00Z")
        supersedes?.let { put("supersedes_record_id", it) }
        putJsonArray("sources") { add(buildJsonObject { put("source_id", "manual"); put("kind", "manual") }) }
        putJsonArray("fields") { add(buildJsonObject { put("key", "raw"); put("value_type", "text"); put("value", "원문 그대로 보존") }) }
        putJsonArray("references") {}
    }.toString()
    private fun weight(value: String = "72.4") = InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WEIGHT,
        mapOf("date" to date, "weight_kg" to value, "memo" to "검토한 자료")))
    private fun withDb(action: (FitnessRoomDatabase, InformationInboxRepository, InformationProjectionRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        try { action(db, InformationInboxRepository(db, profile), InformationProjectionRepository(db, context, profile)) }
        finally { db.close() }
    }

    @Test fun genericWeightAppliesOnceAndReceiptSurvivesBackupWithoutChangingOriginal() = withDb { db, inbox, projections ->
        val text = source(); val record = InformationRecordCodec.decode(text)
        inbox.importRecord(text, true)
        val before = inbox.export(record.id)
        assertTrue(BodyMetricsReadRepository(db).weightEntries(scope, date, date).isEmpty())
        val preview = weight()
        assertThrows(IllegalArgumentException::class.java) { projections.apply(record.id, preview, false) {} }
        val first = projections.apply(record.id, preview, true) {}
        assertEquals(first, projections.apply(record.id, preview, true) {})
        assertEquals(72.4, BodyMetricsReadRepository(db).weightEntries(scope, date, date).single().weightKg, 0.0)
        assertEquals(before, inbox.export(record.id))
        assertEquals("reviewed", inbox.record(record.id).status)
        assertThrows(IllegalArgumentException::class.java) { projections.apply(record.id, weight("80"), true) {} }
        assertNull(InformationProjectionRepository(db, context, profile.copy(userId = "other")).receipt(record.id))
        assertThrows(IllegalStateException::class.java) { InformationProjectionRepository(db, context, profile.copy(project = "other")).apply(record.id, preview, true) {} }
        val correction = InformationRecordCodec.decode(source(supersedes = record.id))
        inbox.importRecord(correction.canonical, true)
        assertThrows(IllegalArgumentException::class.java) { projections.apply(correction.id, weight("80"), true) {} }
        val nextCorrection = InformationRecordCodec.decode(source(supersedes = correction.id))
        inbox.importRecord(nextCorrection.canonical, true)
        assertThrows(IllegalArgumentException::class.java) { projections.apply(nextCorrection.id, weight("80"), true) {} }
        val backup = LocalDataBackupService(RoomBackupDatabaseStorage(db), profile.userId, profile.userId)
        val bytes = ByteArrayOutputStream().also { backup.writeBackup(it) }.toByteArray()
        db.openHelper.writableDatabase.execSQL("DELETE FROM fitness_information_applications")
        backup.restoreBackup(ByteArrayInputStream(bytes))
        assertEquals(first, projections.apply(record.id, preview, true) {})
        val v60 = org.json.JSONObject(bytes.toString(Charsets.UTF_8)).put("databaseVersion", 60)
        v60.getJSONObject("tables").getJSONArray("fitness_information_applications").getJSONObject(0).remove("lineage_root_id")
        db.openHelper.writableDatabase.execSQL("DELETE FROM fitness_information_applications")
        backup.restoreBackup(ByteArrayInputStream(v60.toString().toByteArray()))
        assertEquals(first, projections.apply(record.id, preview, true) {})
        assertEquals(1, BodyMetricsReadRepository(db).weightEntries(scope, date, date).size)
    }

    @Test fun completeMealChangesReadTotalsExactlyOnceAndOptionalUnknownsStayNull() = withDb { db, inbox, projections ->
        val record = InformationRecordCodec.decode(source()); inbox.importRecord(record.canonical, true)
        val fields = mapOf("date" to date, "time" to "12:00", "name" to "검토한 한 끼", "calories_kcal" to "500", "protein_grams" to "30", "carbs_grams" to "60", "fat_grams" to "15")
        assertThrows(IllegalArgumentException::class.java) { InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.MEAL, fields - "fat_grams")) }
        assertEquals(0, MealReadRepository(db).mealCount(scope, date))
        val preview = InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.MEAL, fields))
        projections.apply(record.id, preview, true) {}
        projections.apply(record.id, preview, true) {}
        val meals = MealReadRepository(db)
        assertEquals(1, meals.mealCount(scope, date))
        db.openHelper.readableDatabase.query("SELECT calories,protein_grams,sodium_mg,sugars_grams FROM meal_record_items").use {
            assertTrue(it.moveToFirst()); assertEquals(500.0, it.getDouble(0), 0.0); assertEquals(30.0, it.getDouble(1), 0.0)
            assertTrue(it.isNull(2)); assertTrue(it.isNull(3))
        }
        val stats = StatisticsReadRepository(WorkoutReadRepository(db, context), BodyMetricsReadRepository(db), meals).load(scope, date, StatisticsPeriod.SEVEN_DAYS)
        assertEquals(1, stats.sufficiency.mealDays)
        assertEquals(0, stats.sufficiency.workoutSessions)
    }

    @Test fun completedWorkoutCreatesNativeSetsAndStatisticsWithoutInventingGpsOrHeartRate() = withDb { db, inbox, projections ->
        val record = InformationRecordCodec.decode(source()); inbox.importRecord(record.canonical, true)
        val preview = InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WORKOUT, mapOf(
            "date" to date, "activity_name" to "검토한 중량 운동", "start_time" to "10:00", "duration_seconds" to "600", "mode" to "resistance", "sets" to "3", "reps" to "10", "load_kg" to "20")))
        val receipt = projections.apply(record.id, preview, true) {}
        projections.apply(record.id, preview, true) {}
        val workouts = WorkoutReadRepository(db, context)
        val session = workouts.completedSessionSummaries(scope, date, date).single()
        assertEquals(receipt.nativeId, session.id)
        assertEquals(600.0, session.totalVolumeKg, 0.0)
        assertEquals(600, session.durationSeconds)
        val stats = StatisticsReadRepository(workouts, BodyMetricsReadRepository(db), MealReadRepository(db)).load(scope, date, StatisticsPeriod.SEVEN_DAYS)
        assertEquals(1, stats.sufficiency.workoutSessions)
        db.openHelper.readableDatabase.query("SELECT count(*),sum(actual_reps),sum(volume_kg) FROM workout_sets WHERE is_completed=1").use {
            assertTrue(it.moveToFirst()); assertEquals(3, it.getInt(0)); assertEquals(30, it.getInt(1)); assertEquals(600.0, it.getDouble(2), 0.0)
        }
        db.openHelper.readableDatabase.query("SELECT average_heart_rate FROM workout_records").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
        db.openHelper.readableDatabase.query("SELECT count(*) FROM cardio_route_points").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        val timed = InformationRecordCodec.decode(source()); inbox.importRecord(timed.canonical, true)
        val timedPreview = InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WORKOUT, mapOf(
            "date" to date, "activity_name" to "시간만 확인한 활동", "start_time" to "11:00", "duration_seconds" to "120", "mode" to "timed")))
        projections.apply(timed.id, timedPreview, true) {}
        val timedSession = workouts.completedSessionSummaries(scope, date, date).single { it.title == "시간만 확인한 활동" }
        assertEquals(120, timedSession.durationSeconds)
        assertEquals(0.0, timedSession.totalVolumeKg, 0.0)
        assertEquals(1, timedSession.completedSetCount)
    }

    @Test fun changedAccountRollsBackNativeWriteAndReceiptAndDateCollisionPreservesPriorWeight() = withDb { db, inbox, projections ->
        val record = InformationRecordCodec.decode(source()); inbox.importRecord(record.canonical, true)
        var checks = 0
        assertThrows(IllegalStateException::class.java) { projections.apply(record.id, weight(), true) { check(++checks < 2) { "account changed" } } }
        assertNull(projections.receipt(record.id))
        assertEquals("pending", inbox.record(record.id).status)
        assertTrue(BodyMetricsReadRepository(db).weightEntries(scope, date, date).isEmpty())
        BodyMetricsRepository(db, profile.userId).addBodyMetric(scope, date, 90.0, "기존 기록")
        assertThrows(IllegalArgumentException::class.java) { projections.apply(record.id, weight(), true) {} }
        assertEquals(90.0, BodyMetricsReadRepository(db).weightEntries(scope, date, date).single().weightKg, 0.0)
        assertNull(projections.receipt(record.id))
    }

    @Test fun authenticatedRemoteInboxArrivesPendingAndPreservesDraftWithoutNativeChanges() = withDb { db, inbox, projections ->
        val record = InformationRecordCodec.decode(source())
        val row = buildJsonObject {
            put("user_id", "remote-owner"); put("record_id", record.id); put("payload_text", record.canonical)
            put("content_fingerprint", record.fingerprint); put("received_at", "2026-11-06T00:00:00Z"); put("state", "reviewed")
        }
        val page = NutritionMealReviewClient.decodeInformationPage(JsonArray(listOf(row)).toString(), "remote-owner")
        val receiver = NutritionInformationImporter(db, profile)
        inbox.saveDraft("{unfinished draft}")
        assertEquals(1, receiver.importPage(NutritionMealSource("https://remote.example", "remote-owner"), page) {}.inserted)
        assertEquals(1, receiver.importPage(NutritionMealSource("https://remote.example", "remote-owner"), page) {}.duplicates)
        assertEquals("pending", inbox.record(record.id).status)
        assertEquals(record.canonical, inbox.export(record.id))
        assertEquals("{unfinished draft}", inbox.draft())
        assertNull(projections.receipt(record.id))
        assertTrue(BodyMetricsReadRepository(db).weightEntries(scope, date, date).isEmpty())
        db.openHelper.readableDatabase.query("SELECT source_project,source_user_id FROM fitness_information_deliveries").use {
            assertTrue(it.moveToFirst()); assertEquals("https://remote.example", it.getString(0)); assertEquals("remote-owner", it.getString(1))
        }
    }

    @Test fun fileThenRemoteThenDifferentInboxProjectStillAppliesOncePerNativeOwner() = withDb { db, inbox, projections ->
        val record = InformationRecordCodec.decode(source()); inbox.importRecord(record.canonical, true)
        val first = projections.apply(record.id, weight(), true) {}
        val delivery = NutritionInformationDelivery(record, record.fingerprint, "2026-11-06T00:00:00Z")
        NutritionInformationImporter(db, profile).importPage(NutritionMealSource("https://remote.example", "remote-account"),
            NutritionInformationPage(listOf(delivery), null)) {}
        assertEquals(first, projections.apply(record.id, weight(), true) {})
        val anotherProject = profile.copy(project = "https://another-local-connection.example")
        InformationInboxRepository(db, anotherProject).importRecord(record.canonical, true)
        assertEquals(first, InformationProjectionRepository(db, context, anotherProject).apply(record.id, weight(), true) {})
        val conflictingProject = profile.copy(project = "https://conflicting-local-connection.example")
        InformationInboxRepository(db, conflictingProject).importRecord(record.canonical.replace("범용 자료 반영", "다른 원문"), true)
        assertThrows(IllegalArgumentException::class.java) {
            InformationProjectionRepository(db, context, conflictingProject).apply(record.id, weight(), true) {}
        }
        assertThrows(Exception::class.java) { db.informationProjectionDao().insertApplication(first.copy(project = "bypass-attempt")) }
        assertEquals(1, BodyMetricsReadRepository(db).weightEntries(scope, date, date).size)
        db.openHelper.readableDatabase.query("SELECT count(*) FROM fitness_information_applications").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
    }

    @Test fun incompleteCyclicDeepAndArchivedSourcesCannotCreateNativeFacts() = withDb { db, inbox, projections ->
        val missing = InformationRecordCodec.decode(source(supersedes = UUID.randomUUID().toString()))
        inbox.importRecord(missing.canonical, true)
        assertThrows(IllegalStateException::class.java) { projections.apply(missing.id, weight(), true) {} }
        val cycleA = UUID.randomUUID().toString(); val cycleB = UUID.randomUUID().toString()
        inbox.importRecord(source(cycleA, cycleB), true); inbox.importRecord(source(cycleB, cycleA), true)
        assertThrows(IllegalArgumentException::class.java) { projections.apply(cycleA, weight(), true) {} }
        var parent: String? = null
        repeat(65) {
            val record = InformationRecordCodec.decode(source(supersedes = parent))
            inbox.importRecord(record.canonical, true); parent = record.id
        }
        assertThrows(IllegalArgumentException::class.java) { projections.apply(parent!!, weight(), true) {} }
        val archived = InformationRecordCodec.decode(source()); inbox.importRecord(archived.canonical, true)
        inbox.changeState(archived.id, InformationReviewState.ARCHIVED)
        assertThrows(IllegalArgumentException::class.java) { projections.apply(archived.id, weight(), true) {} }
        assertEquals(archived.canonical, inbox.export(archived.id))
        assertTrue(BodyMetricsReadRepository(db).weightEntries(scope, date, date).isEmpty())
        db.openHelper.readableDatabase.query("SELECT count(*) FROM fitness_information_applications").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        // Reopening the source permits its first reviewed write; archived replay never creates another fact.
        inbox.changeState(archived.id, InformationReviewState.PENDING)
        val applied = projections.apply(archived.id, weight(), true) {}
        inbox.changeState(archived.id, InformationReviewState.ARCHIVED)
        assertEquals(applied, projections.apply(archived.id, weight(), true) {})
        assertEquals(1, BodyMetricsReadRepository(db).weightEntries(scope, date, date).size)
    }
    @Test fun appliedSiblingBlocksWholeKnownCorrectionFamily() = withDb { db, inbox, projections ->
        val parent = InformationRecordCodec.decode(source()); inbox.importRecord(parent.canonical, true)
        val siblingOne = InformationRecordCodec.decode(source(supersedes = parent.id)); inbox.importRecord(siblingOne.canonical, true)
        val siblingTwo = InformationRecordCodec.decode(source(supersedes = parent.id)); inbox.importRecord(siblingTwo.canonical, true)
        val first = projections.apply(siblingOne.id, weight(), true) {}
        assertEquals(parent.id, first.lineageRootId)
        // A different date has no weight collision, so only the correction-family guard can reject it.
        val anotherDate = InformationProjection.preview(InformationProjectionDraft(InformationProjectionKind.WEIGHT,
            mapOf("date" to LocalDate.parse(date).minusDays(1).toString(), "weight_kg" to "73")))
        assertThrows(IllegalArgumentException::class.java) { projections.apply(siblingTwo.id, anotherDate, true) {} }
        assertThrows(IllegalArgumentException::class.java) { projections.apply(parent.id, anotherDate, true) {} }
        assertEquals(first, projections.apply(siblingOne.id, weight(), true) {})
        assertThrows(Exception::class.java) { db.informationProjectionDao().insertApplication(first.copy(recordId = UUID.randomUUID().toString())) }
        assertEquals(1, BodyMetricsReadRepository(db).weightEntries(scope, date, date).size)
    }
}
