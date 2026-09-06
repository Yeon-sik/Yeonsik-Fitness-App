package com.yeonsik.fitnessapp.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration entry points owned by Room. Historical v1-v50 SQL moves here before Room opens
 * the production file; a SQLiteOpenHelper must never manage the same file afterward.
 */
object FitnessRoomMigrations {
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
        }
    }
}
