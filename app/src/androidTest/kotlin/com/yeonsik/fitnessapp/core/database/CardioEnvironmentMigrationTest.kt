package com.yeonsik.fitnessapp.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class CardioEnvironmentMigrationTest {
    @Test fun migrationKeepsLegacyGpsValuesAndOwnersAndAddsOnlyNullableEquipmentDistance() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(51) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE cardio_sessions (record_id TEXT PRIMARY KEY, user_id TEXT NOT NULL, " +
                            "activity_type TEXT NOT NULL, status TEXT NOT NULL, distance_meters REAL NOT NULL, " +
                            "accepted_point_count INTEGER NOT NULL, started_at_epoch_ms INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO cardio_sessions VALUES ('old-a','owner-a','running','completed'," +
                            "1234.5,37,1000),('old-b','owner-b','cycling','paused',0,0,2000)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.use {
            val db = it.writableDatabase
            FitnessRoomMigrations.V51_TO_V52.migrate(db)
            db.query("SELECT record_id,user_id,distance_meters,accepted_point_count,environment," +
                "manual_distance_meters FROM cardio_sessions ORDER BY record_id").use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("old-a", cursor.getString(0)); assertEquals("owner-a", cursor.getString(1))
                assertEquals(1234.5, cursor.getDouble(2), 0.0); assertEquals(37, cursor.getInt(3))
                assertEquals("outdoor", cursor.getString(4)); assertTrue(cursor.isNull(5))
                assertTrue(cursor.moveToNext())
                assertEquals("owner-b", cursor.getString(1)); assertEquals(0.0, cursor.getDouble(2), 0.0)
                assertEquals("outdoor", cursor.getString(4)); assertTrue(cursor.isNull(5))
            }
        }
    }
}
