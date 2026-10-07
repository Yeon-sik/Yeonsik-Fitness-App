package com.yeonsik.fitnessapp.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ManualExerciseLinkMigrationTest {
    @Test fun v54ToV55OnlyAddsLinkMetadataAndKeepsAllWorkoutSnapshotColumns() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "manual-link-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open(
            "com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase/54.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(54) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            entity.optJSONArray("indices")?.let { indices ->
                                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j)
                                    .getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        val queries = schema.getJSONArray("setupQueries")
                        for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        val tables = listOf("workout_records", "workout_exercises", "workout_sets")
        try {
            val before = helper.use {
                val db = it.writableDatabase
                db.execSQL("INSERT INTO workout_records (id,user_id,date,workout_type,category,exercise_name,is_backfilled,created_at," +
                    "updated_at,device_id,source_app,scope,metadata) VALUES ('record','owner','2000-01-02','strength'," +
                    "'strength','당시 세션',0,'created','updated','device','fitness','fitness','{\"status\":\"completed\"}')")
                db.execSQL("INSERT INTO workout_exercises (id,user_id,record_id,order_index,exercise_id,exercise_name_snapshot," +
                    "ui_part,record_type,created_at,updated_at,device_id) VALUES ('exercise','owner','record',1,'manual'," +
                    "'당시 운동명','팔','weight_reps','created','updated','device')")
                db.execSQL("INSERT INTO workout_sets (id,user_id,workout_exercise_id,set_index,weight_kg,actual_reps,rir," +
                    "is_completed,volume_kg,created_at,updated_at,device_id) VALUES ('set','owner','exercise',1,12.5,10,2,1," +
                    "125,'created','updated','device')")
                tables.associateWith { table -> rows(db, table) }
            }
            val room = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, name)
                .addMigrations(FitnessRoomMigrations.V54_TO_V55).build()
            try {
                val db = room.openHelper.writableDatabase
                assertEquals(55, db.version)
                for (table in tables) assertEquals(table, before[table], rows(db, table))
                assertTrue(room.workoutRoomDao().manualExerciseLinks("record", "owner").isEmpty())
            } finally { room.close() }
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    private fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
        db.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { index ->
                    if (cursor.isNull(index)) null else cursor.getString(index)
                })
            }
        }
}
