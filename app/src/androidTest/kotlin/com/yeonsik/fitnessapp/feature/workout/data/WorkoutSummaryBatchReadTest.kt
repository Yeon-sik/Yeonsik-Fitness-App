package com.yeonsik.fitnessapp.feature.workout.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/** Exercises the production Room repository with a disposable in-memory database. */
class WorkoutSummaryBatchReadTest {
    private lateinit var database: FitnessRoomDatabase
    private lateinit var repository: WorkoutSummaryRepository
    private val queries = CopyOnWriteArrayList<String>()

    @Before fun prepare() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FitnessRoomDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries.add(sql) }, Executor { it.run() }).build()
        repository = WorkoutSummaryRepository(database)
        repeat(20) { insert("a-$it", "owner-a") }
        insert("b", "owner-b")
        queries.clear()
    }

    @After fun clean() { database.close() }

    @Test fun completeProjectionReadUsesTwoQueriesForManySessionsAndKeepsOwnerIsolation() {
        val rows = repository.completedFitnessSummaryProjectionsV2("owner-a")
        assertEquals(20, rows.size)
        assertTrue(rows.all { it.id.startsWith("a-") && it.chestSets == 1 && it.bicepsSets == 1 })
        assertEquals(2, summaryReadCount())
    }

    @Test fun deletedAndIncompleteSetsDoNotCountAndTombstonesKeepTheirIdentity() {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("UPDATE workout_sets SET is_completed=0 WHERE id='a-0-chest-set'")
        sql.execSQL("UPDATE workout_exercises SET deleted_at=? WHERE id='a-0-arms'", arrayOf(TIME))
        sql.execSQL("UPDATE workout_records SET deleted_at=? WHERE id='a-0'", arrayOf(TIME))
        val row = repository.completedFitnessSummaryProjectionsV2("owner-a").single { it.id == "a-0" }
        assertEquals("a-0", row.sourceFitnessSessionId)
        assertEquals(TIME, row.deletedAt)
        assertEquals(0, row.chestSets)
        assertEquals(0, row.bicepsSets)
    }

    @Test fun reconciliationReadsTwoBatchesAndTheSecondRunDoesNotRewriteRecords() {
        assertEquals(20, repository.reconcileSharedWorkoutSummaries("owner-a"))
        queries.clear()
        assertEquals(0, repository.reconcileSharedWorkoutSummaries("owner-a"))
        assertEquals(2, summaryReadCount())
        assertFalse(queries.any { it.trimStart().startsWith("UPDATE workout_records", ignoreCase = true) })
        val untouched = database.workoutRoomDao().visibleRecord("b", "owner-b")!!
        assertEquals("fitness", untouched.scope)
        assertEquals("strength", untouched.category)
    }

    @Test fun aSummaryBatchCannotOverwriteAnEditMadeAfterItsRead() {
        val dao = database.workoutRoomDao()
        val before = dao.visibleRecord("a-0", "owner-a")!!
        val editedMetadata = "{\"status\":\"completed\",\"note\":\"new local note\"}"
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_records SET metadata=?,updated_at=? WHERE id='a-0'",
            arrayOf(editedMetadata, "2026-10-20T01:00:00Z"))
        assertEquals(0, dao.updateSharedSummaryIfUnchanged("a-0", "owner-a", "changed",
            before.metadata, "both", TIME, before.updatedAt, before.metadata, before.category, before.scope))
        val after = dao.visibleRecord("a-0", "owner-a")!!
        assertEquals(editedMetadata, after.metadata)
        assertEquals("fitness", after.scope)
    }

    private fun insert(id: String, owner: String) {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("INSERT INTO workout_records (id,user_id,date,workout_type,category,exercise_name,is_backfilled,created_at,updated_at," +
            "device_id,source_app,scope,metadata) VALUES (?,?,?,'strength','strength','Fixture',0,?,?,'android-local'," +
            "'fitness','fitness','{\"status\":\"completed\"}')", arrayOf(id, owner, "2026-10-20", TIME, TIME))
        listOf("chest" to "chest", "arms" to "biceps").forEachIndexed { index, (part, subPart) ->
            val exercise = "$id-$part"
            sql.execSQL("INSERT INTO workout_exercises (id,user_id,record_id,order_index,exercise_id," +
                "exercise_name_snapshot,ui_part,primary_sub_part_snapshot,record_type,created_at,updated_at,device_id) " +
                "VALUES (?,?,?,?,?,'Fixture',?,?,'sets_reps_weight',?,?,'android-local')",
                arrayOf<Any?>(exercise, owner, id, index, "master-$part", part, subPart, TIME, TIME))
            sql.execSQL("INSERT INTO workout_sets (id,user_id,workout_exercise_id,set_index,is_completed," +
                "created_at,updated_at,device_id) VALUES (?,?,?,1,1,?,?,'android-local')",
                arrayOf("$exercise-set", owner, exercise, TIME, TIME))
        }
    }

    private fun summaryReadCount() = queries.count {
        it.trimStart().startsWith("SELECT", ignoreCase = true) && it.contains("FROM workout_", ignoreCase = true)
    }

    companion object { private const val TIME = "2026-10-20T00:00:00Z" }
}
