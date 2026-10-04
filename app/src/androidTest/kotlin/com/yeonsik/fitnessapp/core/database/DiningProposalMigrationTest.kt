package com.yeonsik.fitnessapp.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class DiningProposalMigrationTest {
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
