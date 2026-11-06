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
            .addMigrations(FitnessRoomMigrations.V57_TO_V58).build()
        val profile = InformationProfile("owner", "https://project.example")
        try {
            helper.writableDatabase.execSQL("INSERT INTO weight_records(id,user_id,date,weight_kg,created_at,is_backfilled,updated_at,device_id,source_app,scope,metadata) " +
                "VALUES ('weight','owner','2026-11-01',72.25,'created',0,'updated','device','fitness','fitness','{\"memo\":\"원본\"}')")
            val originalWeight = rows(helper.writableDatabase, "weight_records")
            helper.close()
            var db = open()
            try {
                assertEquals(58, db.openHelper.writableDatabase.version)
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

    private fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
        db.query("SELECT * FROM $table ORDER BY rowid").use { cursor -> buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
        } }
}
