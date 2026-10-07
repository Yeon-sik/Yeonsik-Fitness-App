package com.yeonsik.fitnessapp.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.yeonsik.fitnessapp.integration.nutrition.DiningProposal
import org.json.JSONObject
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DiningProposalMigrationTest {
    @Test fun v53RoomUpgradePreservesWholeRequestsOwnersProjectsAndMealHistory() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "dining-v53-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open(
            "com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase/53.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(53) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (index in 0 until entities.length()) {
                            val entity = entities.getJSONObject(index)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices")
                            for (i in 0 until (indices?.length() ?: 0)) db.execSQL(indices!!.getJSONObject(i)
                                .getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        val old = DiningProposal("owner", "food", "menu", "project-a|pt-user", "old-key",
            "{\"p_menu_name\":\"Original\"}", "candidate", "rejected", reviewNote = "original rejection",
            createdAt = "created", updatedAt = "updated")
        try {
            helper.use {
                val db = it.writableDatabase
                for (proposal in listOf(old, old.copy(ownerId = "other-owner"), old.copy(remoteScope = "project-b|pt-user"))) {
                    db.execSQL("INSERT INTO dining_identity_proposals VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf(proposal.ownerId, proposal.nutritionFoodId, proposal.kind, proposal.remoteScope,
                            proposal.idempotencyKey, proposal.requestJson, proposal.candidateId, proposal.status,
                            null, null, null, null, proposal.reviewNote, proposal.createdAt, proposal.updatedAt))
                }
                db.execSQL("INSERT INTO meal_records (id,user_id,date,menu,calories,protein_grams,created_at," +
                    "is_backfilled,updated_at,device_id,source_app,scope,metadata,contract_version) " +
                    "VALUES ('meal-history','owner','2026-10-01','private meal',500,20,'created',0,'updated','device','fitness','fitness','{}',1)")
            }
            val room = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, name)
                .addMigrations(FitnessRoomMigrations.V53_TO_V54).build()
            try {
                val store = RoomDiningProposalStore(room.diningProposalRoomDao())
                assertEquals(listOf(old), store.list("owner").filter { it.remoteScope == old.remoteScope })
                assertEquals(1, store.list("other-owner").size)
                assertEquals(2, store.list("owner").size)
                store.reserve(old.copy(idempotencyKey = "new-key", status = "submitting", candidateId = null, requestVersion = 2))
                assertEquals(3, store.list("owner").size)
                assertEquals(old, store.list("owner").single { it.requestVersion == 1 && it.remoteScope == old.remoteScope })
                room.openHelper.writableDatabase.query("SELECT id,menu FROM meal_records").use { cursor ->
                    assertEquals(1, cursor.count); assertTrue(cursor.moveToFirst())
                    assertEquals("meal-history", cursor.getString(0)); assertEquals("private meal", cursor.getString(1))
                }
                assertEquals(54, room.openHelper.writableDatabase.version)
            } finally { room.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun v52MigrationAddsEmptyProposalStateAndRetainsMealIdentityAndOwner() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(52) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE meal_records(id TEXT PRIMARY KEY, user_id TEXT, " +
                            "restaurant_id TEXT, restaurant_location_id TEXT, restaurant_menu_id TEXT)")
                        db.execSQL("INSERT INTO meal_records VALUES ('meal','owner','restaurant','branch','menu')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        helper.use {
            val db = it.writableDatabase
            FitnessRoomMigrations.V52_TO_V53.migrate(db)
            db.query("SELECT * FROM meal_records").use { cursor ->
                assertEquals(1, cursor.count); assertTrue(cursor.moveToFirst())
                assertEquals("meal", cursor.getString(0)); assertEquals("owner", cursor.getString(1))
                assertEquals("restaurant", cursor.getString(2)); assertEquals("branch", cursor.getString(3))
                assertEquals("menu", cursor.getString(4))
            }
            db.query("SELECT * FROM dining_identity_proposals").use { cursor -> assertEquals(0, cursor.count) }
            db.query("PRAGMA index_info(meal_records_dining_out_identity_idx)").use { cursor -> assertEquals(4, cursor.count) }
        }
    }
}
