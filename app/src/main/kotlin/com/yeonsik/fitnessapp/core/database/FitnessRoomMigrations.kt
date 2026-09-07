package com.yeonsik.fitnessapp.core.database

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper

/**
 * Migration entry points owned by Room. Historical v8-v50 SQL moves here before Room opens
 * the production file; a SQLiteOpenHelper must never manage the same file afterward.
 */
object FitnessRoomMigrations {
    const val MINIMUM_SUPPORTED_LEGACY_VERSION = 8

    /**
     * One Room entry point is registered for every shipped legacy version. Each entry executes
     * the helper's original conditional SQL through v50, after which the schema-preserving Room
     * handoff advances it to v51. No intermediate APK is required.
     */
    @JvmStatic
    fun all(context: Context): Array<Migration> {
        val applicationContext = context.applicationContext ?: context
        return buildList {
            for (version in MINIMUM_SUPPORTED_LEGACY_VERSION until FitnessDatabaseContract.LEGACY_VERSION) {
                add(object : Migration(version, FitnessDatabaseContract.LEGACY_VERSION) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        FitnessDatabaseHelper.migrateHistoricalSchema(
                            applicationContext,
                            db,
                            version
                        )
                    }
                })
            }
            add(V50_TO_V51)
        }.toTypedArray()
    }

    /**
     * The initial Room handoff is deliberately schema-preserving. It validates all v50 tables
     * and advances only the database version marker, retaining IDs, ownership, snapshots,
     * tombstones, units, and sync cursors byte-for-byte.
     */
    val V50_TO_V51: Migration = object : Migration(
        FitnessDatabaseContract.LEGACY_VERSION,
        FitnessDatabaseContract.ROOM_VERSION
    ) {
        override fun migrate(db: SupportSQLiteDatabase) {
            FitnessDatabaseContract.requireV50Schema(db)
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS dining_out_menu_add_on_links (" +
                    "id TEXT NOT NULL PRIMARY KEY, user_id TEXT NOT NULL, menu_food_id TEXT NOT NULL, " +
                    "add_on_food_id TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, " +
                    "deleted_at TEXT, device_id TEXT NOT NULL, " +
                    "UNIQUE(user_id, menu_food_id, add_on_food_id))"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS dining_out_menu_add_on_links_menu_idx " +
                    "ON dining_out_menu_add_on_links(user_id, menu_food_id, deleted_at)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS dining_out_menu_add_on_links_add_on_idx " +
                    "ON dining_out_menu_add_on_links(user_id, add_on_food_id, deleted_at)"
            )
            FitnessPrimaryKeyCompatibility.normalizeNullablePrimaryKeys(db)
        }
    }
}
