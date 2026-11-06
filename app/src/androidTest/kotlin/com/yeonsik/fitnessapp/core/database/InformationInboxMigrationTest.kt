package com.yeonsik.fitnessapp.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.backup.LocalDataBackupService
import com.yeonsik.fitnessapp.core.database.backup.RoomBackupDatabaseStorage
import com.yeonsik.fitnessapp.feature.information.data.*
import com.yeonsik.fitnessapp.feature.information.model.*
import com.yeonsik.fitnessapp.integration.healthtrace.LegacyHealthExportRepository
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class InformationInboxMigrationTest {
    private val payload = """{"schema_version":"information-record.v1","record_id":"12345678-1234-4234-8234-1234567890ab","owner_app":"fitness","record_type":"fitness.activity_observation","title":"정밀도 보존","captured_at":"2026-11-06T12:00:00Z","sources":[{"source_id":"manual","kind":"manual","text":"출처 원문"}],"fields":[{"key":"mass","value_type":"decimal","value":"12345678901234567890.0010","unit":"kg"},{"key":"raw","value_type":"json","value":{"nested":[false,"",null,12345678901234567890.0010]}}],"references":[]}"""

    @Test fun v57MigrationReplayIsolationRestartAndBackupPreserveOriginals() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "information-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open(
            "com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase/57.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(57) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        entity.optJSONArray("indices")?.let { indices ->
                            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        fun open() = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, name)
            .addMigrations(FitnessRoomMigrations.V57_TO_V58, FitnessRoomMigrations.V58_TO_V59).build()
        val profile = InformationProfile("owner", "https://project.example")
        try {
            helper.writableDatabase.execSQL("INSERT INTO weight_records(id,user_id,date,weight_kg,created_at,is_backfilled,updated_at,device_id,source_app,scope,metadata) " +
                "VALUES ('weight','owner','2026-11-01',72.25,'created',0,'updated','device','fitness','fitness','{\"memo\":\"원본\"}')")
            val originalWeight = rows(helper.writableDatabase, "weight_records")
            helper.close()
            var db = open()
            try {
                assertEquals(FitnessDatabaseContract.ROOM_VERSION, db.openHelper.writableDatabase.version)
                assertEquals(originalWeight, rows(db.openHelper.writableDatabase, "weight_records"))
                val repository = InformationInboxRepository(db, profile)
                assertThrows(IllegalArgumentException::class.java) { repository.importRecord(payload, false) }
                assertTrue(repository.importRecord(payload, true))
                assertFalse(repository.importRecord(" \n$payload", true))
                assertThrows(IllegalArgumentException::class.java) { repository.importRecord(payload.replace("정밀도 보존", "다른 내용"), true) }
                assertEquals(1, repository.records().size)
                val other = InformationInboxRepository(db, InformationProfile("other", profile.project))
                assertTrue(other.records().isEmpty())
                assertThrows(IllegalStateException::class.java) { other.export("12345678-1234-4234-8234-1234567890ab") }
                assertTrue(InformationInboxRepository(db, InformationProfile("owner", "https://other.example")).records().isEmpty())
                val canonical = InformationRecordCodec.decode(payload).canonical
                repository.changeState(repository.records().single().recordId, InformationReviewState.REVIEWED)
                assertEquals(canonical, repository.record(repository.records().single().recordId).payloadJson)
                repository.saveDraft("{unfinished draft}")
                assertEquals(originalWeight, rows(db.openHelper.writableDatabase, "weight_records"))
                assertTrue(rows(db.openHelper.writableDatabase, "meal_records").isEmpty())
                assertTrue(rows(db.openHelper.writableDatabase, "workout_records").isEmpty())
                val backup = LocalDataBackupService(RoomBackupDatabaseStorage(db), "owner", "owner")
                val bytes = ByteArrayOutputStream().also { backup.writeBackup(it) }.toByteArray()
                db.informationInboxDao().clearDraft("owner", profile.project)
                db.openHelper.writableDatabase.execSQL("DELETE FROM fitness_information_inbox")
                backup.restoreBackup(ByteArrayInputStream(bytes))
                assertEquals(canonical, repository.record(repository.records().single().recordId).payloadJson)
                assertEquals("{unfinished draft}", repository.draft())
            } finally { db.close() }
            db = open()
            try {
                val repository = InformationInboxRepository(db, profile)
                assertEquals(InformationRecordCodec.decode(payload).canonical,
                    InformationRecordCodec.decode(repository.export(repository.records().single().recordId)).canonical)
                assertEquals("reviewed", repository.records().single().status)
                assertEquals("{unfinished draft}", repository.draft())
            } finally { db.close() }
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    @Test fun healthExportPreservesLegacyRowsAndStableIdentityAcrossBackup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java)
            .openHelperFactory(FitnessRoomOpenHelperFactory()).build()
        try {
            val sql = db.openHelper.writableDatabase
            sql.execSQL("INSERT INTO nutrition_daily_checkins(id,user_id,date,water_ml,sleep_hours,energy_score,hunger_score,digestion_score,training_readiness_score,note,created_at,updated_at) " +
                "VALUES ('legacy-owner','owner','2026-11-01',1200,7.5,3,2,4,5,'기존 건강 메모','2026-11-01T12:00:00Z','2026-11-01T12:00:00Z')")
            sql.execSQL("INSERT INTO nutrition_daily_checkins(id,user_id,date,water_ml,note,created_at,updated_at) " +
                "VALUES ('foreign','other','2026-11-01',900,'다른 계정 비공개','created','updated')")
            val original = rows(sql, "nutrition_daily_checkins")
            val exporter = LegacyHealthExportRepository(db, InformationProfile("owner", "local"))
            val first = exporter.export()
            assertEquals(InformationOwner.HEALTHTRACE, first.owner)
            assertEquals(first.canonical, exporter.export().canonical)
            assertFalse(first.canonical.contains("water_ml"))
            assertFalse(first.canonical.contains("training_readiness_score"))
            assertFalse(first.canonical.contains("다른 계정 비공개"))
            assertTrue(first.canonical.contains("기존 건강 메모"))
            assertTrue(first.canonical.contains("2026-11-01T12:00:00Z"))
            assertEquals(original, rows(sql, "nutrition_daily_checkins"))
            assertTrue(rows(sql, "fitness_information_inbox").isEmpty())
            val backup = LocalDataBackupService(RoomBackupDatabaseStorage(db), "owner", "owner")
            val bytes = ByteArrayOutputStream().also { backup.writeBackup(it) }.toByteArray()
            sql.execSQL("DELETE FROM fitness_health_export_receipts")
            backup.restoreBackup(ByteArrayInputStream(bytes))
            assertEquals(first.canonical, exporter.export().canonical)
            assertEquals(first.id, exporter.export(exporter.rows().single()).id)
            File(context.getExternalFilesDir(null), "fitness-health-handoff-fixture.json").writeText(first.canonical)
        } finally { db.close() }
    }

    @Test fun oversizedHealthSnapshotFallsBackToIndividualRowsWithoutPartialReceipt() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java)
            .openHelperFactory(FitnessRoomOpenHelperFactory()).build()
        try {
            val sql = db.openHelper.writableDatabase
            for (index in 1..8) sql.execSQL(
                "INSERT INTO nutrition_daily_checkins(id,user_id,date,water_ml,note,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                arrayOf<Any>("large-$index", "owner", "2026-11-0$index", 0, "x".repeat(160000), "created", "updated"))
            val exporter = LegacyHealthExportRepository(db, InformationProfile("owner", "local"))
            assertThrows(IllegalArgumentException::class.java) { exporter.export() }
            assertTrue(rows(sql, "fitness_health_export_receipts").isEmpty())
            val single = exporter.export(exporter.rows().first())
            assertTrue(single.canonical.toByteArray(Charsets.UTF_8).size <= InformationRecordCodec.MAX_BYTES)
            assertEquals(single.canonical, InformationRecordCodec.decode(single.canonical, InformationOwner.HEALTHTRACE).canonical)
            assertEquals(8, rows(sql, "nutrition_daily_checkins").size)
            assertEquals(1, rows(sql, "fitness_health_export_receipts").size)
        } finally { db.close() }
    }

    private fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
        db.query("SELECT * FROM $table ORDER BY rowid").use { cursor -> buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
        } }
}
