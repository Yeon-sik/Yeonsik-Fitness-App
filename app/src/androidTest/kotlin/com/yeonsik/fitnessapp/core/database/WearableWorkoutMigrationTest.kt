package com.yeonsik.fitnessapp.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutDay
import com.yeonsik.fitness.shared.feature.dailyworkout.projectDailyWorkouts
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsRepository
import com.yeonsik.fitnessapp.feature.cardio.data.CardioRepository
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutDailyReadRepository
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutRepositoryImplementation
import com.yeonsik.fitnessapp.integration.wearable.WearableWorkoutRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WearableWorkoutMigrationTest {
    @Test fun migrationAndImportPreserveWorkoutsAndEnforceIdentityAndOwner() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "wearable-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open(
            "com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase/61.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(61) {
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
        val tables = listOf("workout_records", "workout_exercises", "workout_sets", "weight_records")
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
                val insertWeight = "INSERT INTO weight_records " +
                    "(id,user_id,date,weight_kg,created_at,is_backfilled,updated_at,device_id,source_app,scope,metadata) " +
                    "VALUES (?,?,'2000-01-02',?,'created',0,'updated','device','fitness','fitness','{}')"
                db.execSQL(insertWeight, arrayOf<Any>("weight-a", "owner", 70.25))
                db.execSQL(insertWeight, arrayOf<Any>("weight-b", "other-owner", 81.5))
                tables.associateWith { table -> rows(db, table) }
            }
            val room = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, name)
                .addMigrations(*FitnessRoomMigrations.all(context)).build()
            try {
                val db = room.openHelper.writableDatabase
                assertEquals(FitnessDatabaseContract.ROOM_VERSION, db.version)
                for (table in tables) assertEquals(table, before[table], rows(db, table))
                val repository = WearableWorkoutRepository(room)
                val start = java.time.Instant.parse("2000-01-02T00:00:00Z")
                val row = com.yeonsik.fitnessapp.integration.wearable.WearableWorkout("origin", "provider", "외부 운동",
                    start, start.plusSeconds(60), 70, listOf(com.yeonsik.fitnessapp.integration.wearable.MeasuredPulse(start, 100)))
                val zone = java.time.ZoneOffset.UTC
                repository.save("owner", row, "record", zone) { true }
                repository.save("owner", row, null, zone) { true }
                assertEquals(1, repository.list("owner").size)
                assertEquals("record", repository.list("owner").single().linkedRecordId)
                assertEquals(row.samples, repository.list("owner").single().samples)
                assertTrue(repository.list("other-owner").isEmpty())
                assertThrows(IllegalStateException::class.java) { repository.save("other-owner", row, "record", zone) { true } }
                assertThrows(IllegalStateException::class.java) { repository.save("owner", row, null, zone) { false } }
                assertTrue(repository.list("other-owner").isEmpty())
                for (table in tables) assertEquals(table, before[table], rows(db, table))
            } finally { room.close() }
            val reopened = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, name).build()
            try {
                for (table in tables) assertEquals(table, before[table], rows(reopened.openHelper.readableDatabase, table))
                val owner = AccountScope("owner")
                val other = AccountScope("other-owner")
                val weights = BodyMetricsRepository(reopened, owner.ownerId)
                val otherWeights = BodyMetricsRepository(reopened, other.ownerId)
                val added = weights.addBodyMetric(owner, "2000-01-03", 72.5, "")
                assertEquals(72.5, checkNotNull(weights.bodyMetricEntryById(owner, added)).weightKg, 0.0)
                assertEquals(70.25, checkNotNull(weights.bodyMetricEntryById(owner, "weight-a")).weightKg, 0.0)
                assertEquals(81.5, checkNotNull(otherWeights.bodyMetricEntryById(other, "weight-b")).weightKg, 0.0)
                assertNull(weights.bodyMetricEntryById(owner, "weight-b"))
                assertNull(otherWeights.bodyMetricEntryById(other, added))
                val day = WorkoutDay(LocalDate.parse("2000-01-02"), TimeZone.UTC)
                fun reader(owner: String) = WorkoutDailyReadRepository(reopened, WorkoutRepositoryImplementation(reopened, context),
                    CardioRepository(reopened, owner), AccountScope(owner))
                val ownReader = reader("owner")
                val local = ownReader.read(day)
                assertEquals(day.date.toString(), ownReader.findRecord("record")?.date)
                assertNull(reader("other-owner").findRecord("record"))
                val report = projectDailyWorkouts(day, local.workouts, emptyList(), emptyList(), local.linkedWorkoutKeys, local.samplesByWorkout)
                val session = report.sessions.single()
                assertEquals(0, report.pendingCount)
                assertEquals(2, session.sources.size)
                assertTrue(report.decisions.isEmpty())
                val set = session.details!!.exercises.single().sets.single()
                assertEquals(12.5, set.weightKg!!, 0.001)
                assertEquals(10, set.reps)
                assertEquals(100.0, session.heartRate!!.averageBpm, 0.001)
                assertTrue(reader("other-owner").read(day).workouts.isEmpty())
            } finally { reopened.close() }
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
