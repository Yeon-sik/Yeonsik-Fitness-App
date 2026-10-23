package com.yeonsik.fitnessapp.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
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

class PriceTraceBrandMigrationTest {
    @Test fun v56ToV57PreservesFoodMealProductLinksAndWorkoutAndMatchesFreshSchema() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "brand-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open(
            "com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase/56.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = helper(context, name, 56) { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i); val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        }
        val tables = listOf("nutrition_foods", "nutrition_food_nutrients", "meal_records", "meal_record_items",
            "product_nutrition_links", "workout_records", "workout_exercises", "workout_sets")
        try {
            val before = helper.use {
                val db = it.writableDatabase
                tables.forEach { table -> seed(db, table) }
                tables.associateWith { table -> rows(db, table, columns(db, table)) }
            }
            val room = Room.databaseBuilder(context, FitnessRoomDatabase::class.java, name)
                .addMigrations(FitnessRoomMigrations.V56_TO_V57).build()
            room.useRoom {
                val db = it.openHelper.writableDatabase
                assertEquals(57, db.version)
                tables.forEach { table ->
                    val oldColumns = before[table]!!.first
                    assertEquals(table, before[table], rows(db, table, oldColumns))
                }
                val added = columns(db, "nutrition_foods").filter { it.startsWith("pricetrace_") }
                assertEquals(4, added.size)
                db.query("SELECT ${added.joinToString(",")} FROM nutrition_foods").use { cursor ->
                    assertTrue(cursor.moveToFirst()); (0 until 4).forEach { i -> assertTrue(cursor.isNull(i)) }
                }
                Room.inMemoryDatabaseBuilder(context, FitnessRoomDatabase::class.java).build().useRoom { fresh ->
                    assertEquals(columnShape(db), columnShape(fresh.openHelper.writableDatabase))
                }
                val freshName = "brand-fresh-${UUID.randomUUID()}.db"
                try {
                    helper(context, freshName, 57, FitnessRoomFreshSchema::createBeforeRoom).use { fresh ->
                        assertEquals(columnShape(db), columnShape(fresh.writableDatabase))
                    }
                } finally { context.deleteDatabase(freshName) }
            }
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    private inline fun <T> FitnessRoomDatabase.useRoom(block: (FitnessRoomDatabase) -> T): T = try { block(this) } finally { close() }

    private fun helper(context: Context, name: String, version: Int, create: (SupportSQLiteDatabase) -> Unit) =
        FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = create(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> = db.query("PRAGMA table_info($table)").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
    }
    private fun rows(db: SupportSQLiteDatabase, table: String, columns: List<String>): Pair<List<String>, List<List<String?>>> =
        columns to db.query("SELECT ${columns.joinToString(",")} FROM $table").use { cursor ->
            buildList { while (cursor.moveToNext()) add(columns.indices.map { i -> if (cursor.isNull(i)) null else cursor.getString(i) }) }
        }
    private fun columnShape(db: SupportSQLiteDatabase) = db.query("PRAGMA table_info(nutrition_foods)").use { cursor ->
        buildSet { while (cursor.moveToNext()) add((1..5).map { i -> if (cursor.isNull(i)) null else cursor.getString(i) }) }
    }
    private fun seed(db: SupportSQLiteDatabase, table: String) {
        val values = ContentValues()
        db.query("PRAGMA table_info($table)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1); val type = cursor.getString(2)
                if (name == "id") values.put(name, table + "-fixture")
                else if (name in listOf("owner_id", "user_id")) values.put(name, "owner")
                else if (cursor.getInt(3) == 1 && cursor.isNull(4)) {
                    if (type == "TEXT") values.put(name, "snapshot-$name") else values.put(name, 1)
                }
            }
        }
        if (table == "nutrition_foods") {
            values.put("kind", "external_menu"); values.put("name", "기존 라벨 식품")
            values.put("brand", "기존 브랜드"); values.put("basis_unit", "g")
            values.put("manufacturer_name", "기존 제조사"); values.put("brand_name", "기존 대표 브랜드")
        }
        db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
    }
}
