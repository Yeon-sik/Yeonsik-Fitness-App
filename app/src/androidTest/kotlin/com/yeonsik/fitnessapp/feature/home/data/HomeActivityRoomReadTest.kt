package com.yeonsik.fitnessapp.feature.home.data

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsReadRepository
import com.yeonsik.fitnessapp.feature.meal.data.MealReadRepository
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutReadRepository
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Exercises the production DAO/repository queries on isolated Room data, never the app database. */
class HomeActivityRoomReadTest {
    @Test fun workoutEarliestMatchesCompletedDatesIncludingLegacyOsVisibility() = withDatabase { room, context ->
        val db = room.openHelper.writableDatabase
        insert(db, "workout_records", "other", "2025-01-01", owner = "b")
        insert(db, "workout_records", "deleted", "2025-01-02", deleted = true)
        insert(db, "workout_records", "hidden-scope", "2025-01-03", scope = "os")
        insert(db, "workout_records", "active", "2025-01-04", metadata = "{\"status\":\"in_progress\"}")
        insert(db, "workout_records", "os-completed-fact", "2026-06-15", source = "os", metadata = "{}", scope = "both")
        insert(db, "workout_records", "strength", "2026-07-01")
        insert(db, "workout_records", "cardio", "2026-07-01", workoutType = "cardio")
        val api = WorkoutReadRepository(room, context)
        val dates = api.completedDates(AccountScope("a"), "2020-01-01", "2026-10-01")
        assertEquals(listOf("2026-06-15", "2026-07-01"), dates)
        assertEquals(dates.first(), api.earliestCompletedDate(AccountScope("a")))
        assertEquals("2025-01-01", api.earliestCompletedDate(AccountScope("b")))
        assertNull(api.earliestCompletedDate(AccountScope("empty")))
        assertTrue(api.completedDates(AccountScope("empty"), "2020-01-01", "2026-10-01").isEmpty())
        db.execSQL("UPDATE workout_records SET deleted_at='2026-10-01' WHERE id='os-completed-fact'")
        assertEquals("2026-07-01", api.earliestCompletedDate(AccountScope("a")))
    }

    @Test fun bodyAndMealEarliestMatchTheirDatesOwnerDeletionAndScopePolicies() = withDatabase { room, _ ->
        val db = room.openHelper.writableDatabase
        listOf("weight_records", "meal_records").forEach { table ->
            insert(db, table, "$table-other", "2025-01-01", owner = "b")
            insert(db, table, "$table-deleted", "2025-01-02", deleted = true)
            insert(db, table, "$table-hidden", "2025-01-03", scope = "os")
            insert(db, table, "$table-both", "2026-06-15", scope = "both")
            insert(db, table, "$table-fitness", "2026-07-01")
        }
        val body = BodyMetricsReadRepository(room)
        val meal = MealReadRepository(room)
        val owner = AccountScope("a")
        val expected = listOf("2026-06-15", "2026-07-01")
        assertEquals(expected, body.dates(owner, "2020-01-01", "2026-10-01"))
        assertEquals(expected, meal.dates(owner, "2020-01-01", "2026-10-01"))
        assertEquals(expected.first(), body.earliestRecordedDate(owner))
        assertEquals(expected.first(), meal.earliestRecordedDate(owner))
        assertEquals("2025-01-01", body.earliestRecordedDate(AccountScope("b")))
        assertEquals("2025-01-01", meal.earliestRecordedDate(AccountScope("b")))
        assertNull(body.earliestRecordedDate(AccountScope("empty")))
        assertNull(meal.earliestRecordedDate(AccountScope("empty")))
        listOf("weight_records", "meal_records").forEach { table ->
            db.execSQL("UPDATE $table SET deleted_at='2026-10-01' WHERE id='$table-both'")
        }
        assertEquals("2026-07-01", body.earliestRecordedDate(owner))
        assertEquals("2026-07-01", meal.earliestRecordedDate(owner))
    }

    @Test fun realFeatureSourcesDeduplicateStrengthCardioMealsAndWeightsPerOwner() = withDatabase { room, context ->
        val db = room.openHelper.writableDatabase
        insert(db, "workout_records", "strength", "2026-09-30")
        insert(db, "workout_records", "cardio", "2026-09-30", workoutType = "cardio")
        insert(db, "workout_records", "unfinished", "2026-09-29", metadata = "{\"status\":\"in_progress\"}")
        insert(db, "workout_records", "deleted", "2026-09-28", deleted = true)
        repeat(2) { insert(db, "weight_records", "weight-$it", "2026-09-30") }
        repeat(5) { insert(db, "meal_records", "meal-$it", "2026-09-30") }
        val repository = HomeActivityHistoryRepository(listOf(
            WorkoutHomeActivityReadSource(WorkoutReadRepository(room, context)),
            BodyHomeActivityReadSource(BodyMetricsReadRepository(room)),
            MealHomeActivityReadSource(MealReadRepository(room))
        ))
        assertEquals("2026-09-30", repository.firstRecordedDate(AccountScope("a")))
        assertEquals(mapOf("2026-09-30" to HomeActivityKind.entries.toSet()),
            repository.recordedKindsByDate(AccountScope("a"), "2026-07-06", "2026-10-01"))
        assertTrue(repository.recordedKindsByDate(AccountScope("b"), "2026-07-06", "2026-10-01").isEmpty())
    }

    private fun withDatabase(block: (FitnessRoomDatabase, Context) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build()
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit { block(room, context) }.get(30, TimeUnit.SECONDS)
        } finally {
            room.close()
            executor.shutdownNow()
        }
    }

    private fun insert(
        db: SupportSQLiteDatabase, table: String, id: String, date: String,
        owner: String = "a", scope: String = "fitness", source: String = "fitness",
        metadata: String = "{\"status\":\"completed\"}", deleted: Boolean = false, workoutType: String = "strength"
    ) {
        val values = ContentValues().apply {
            put("id", id); put("user_id", owner); put("date", date)
            put("created_at", "2026-10-01T00:00:00Z"); put("updated_at", "2026-10-01T00:00:00Z")
            put("is_backfilled", 0); put("device_id", "test-device"); put("source_app", source)
            put("scope", scope); put("metadata", metadata)
            if (deleted) put("deleted_at", "2026-10-01T00:00:00Z")
            when (table) {
                "workout_records" -> { put("workout_type", workoutType); put("category", "resistance"); put("exercise_name", "test") }
                "weight_records" -> put("weight_kg", 80.0)
                "meal_records" -> { put("menu", "test"); put("calories", 0); put("protein_grams", 0.0) }
                else -> error("Unexpected fixture table")
            }
        }
        db.insert(table, 0, values)
    }
}
