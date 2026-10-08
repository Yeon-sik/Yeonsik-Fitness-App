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
            add(V51_TO_V52)
            add(V52_TO_V53)
            add(V53_TO_V54)
            add(V54_TO_V55)
        }.toTypedArray()
    }

    /**
     * The initial Room handoff is deliberately schema-preserving. It validates all v50 tables
     * and advances only the database version marker, retaining IDs, ownership, snapshots,
     * tombstones, units, and sync cursors byte-for-byte.
     */
    val V50_TO_V51: Migration = object : Migration(
        FitnessDatabaseContract.LEGACY_VERSION,
        FitnessDatabaseContract.ROOM_HANDOFF_VERSION
    ) {
        override fun migrate(db: SupportSQLiteDatabase) {
            FitnessDatabaseContract.requireV50Schema(db)
            // The legacy v50 helper created this index with an extra user_id/deleted_at
            // suffix. CREATE INDEX IF NOT EXISTS cannot repair an existing index with the
            // same name, so replace it explicitly before Room validates the v51 schema.
            db.execSQL("DROP INDEX IF EXISTS meal_record_item_components_meal_idx")
            db.execSQL(
                "CREATE INDEX meal_record_item_components_meal_idx " +
                    "ON meal_record_item_components(meal_record_id, meal_record_item_id)"
            )
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

    /** Additive migration: existing GPS sessions remain outdoor and every recorded value survives. */
    val V51_TO_V52: Migration = object : Migration(51, 52) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE cardio_sessions ADD COLUMN environment TEXT NOT NULL DEFAULT 'outdoor'")
            db.execSQL("ALTER TABLE cardio_sessions ADD COLUMN manual_distance_meters REAL")
        }
    }

    /** Proposal IDs never replace verified menu IDs or alter any existing food/meal row. */
    val V52_TO_V53: Migration = object : Migration(52, 53) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Historical SQLite migrations already created this index. Declare/preserve it in
            // Room too; fresh v51/v52 installations need it added before v53 validation.
            db.execSQL("CREATE INDEX IF NOT EXISTS meal_records_dining_out_identity_idx " +
                "ON meal_records(user_id, restaurant_id, restaurant_location_id, restaurant_menu_id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS dining_identity_proposals (" +
                "owner_id TEXT NOT NULL, nutrition_food_id TEXT NOT NULL, kind TEXT NOT NULL, " +
                "remote_scope TEXT NOT NULL, idempotency_key TEXT NOT NULL, request_json TEXT NOT NULL, " +
                "candidate_id TEXT, status TEXT NOT NULL, restaurant_id TEXT, restaurant_location_id TEXT, " +
                "restaurant_menu_id TEXT, catalog_product_id TEXT, review_note TEXT, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, " +
                "PRIMARY KEY(owner_id, nutrition_food_id, kind, remote_scope))")
        }
    }
    /** Only link metadata is new; all original workout snapshots and sets stay byte-for-byte intact. */
    val V54_TO_V55: Migration = object : Migration(54, 55) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE workout_manual_exercise_links (" +
                "id TEXT NOT NULL PRIMARY KEY, user_id TEXT NOT NULL, canonical_preset_id TEXT NOT NULL, " +
                "family_id TEXT NOT NULL, canonical_variant_key TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL)")
            db.execSQL("CREATE INDEX workout_manual_links_identity_idx ON " +
                "workout_manual_exercise_links(user_id, family_id, canonical_variant_key)")
        }
    }

    /** Preserve every v53 request as version 1, then allow append-only request history. */
    val V53_TO_V54: Migration = object : Migration(53, 54) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE dining_identity_proposals_v54 (" +
                "owner_id TEXT NOT NULL, nutrition_food_id TEXT NOT NULL, kind TEXT NOT NULL, " +
                "remote_scope TEXT NOT NULL, idempotency_key TEXT NOT NULL, request_json TEXT NOT NULL, " +
                "candidate_id TEXT, status TEXT NOT NULL, restaurant_id TEXT, restaurant_location_id TEXT, " +
                "restaurant_menu_id TEXT, catalog_product_id TEXT, review_note TEXT, " +
                "created_at TEXT NOT NULL, updated_at TEXT NOT NULL, request_version INTEGER NOT NULL DEFAULT 1, " +
                "PRIMARY KEY(owner_id, nutrition_food_id, kind, remote_scope, request_version))")
            db.execSQL("INSERT INTO dining_identity_proposals_v54 SELECT *, 1 FROM dining_identity_proposals")
            db.execSQL("DROP TABLE dining_identity_proposals")
            db.execSQL("ALTER TABLE dining_identity_proposals_v54 RENAME TO dining_identity_proposals")
        }
    }
}
