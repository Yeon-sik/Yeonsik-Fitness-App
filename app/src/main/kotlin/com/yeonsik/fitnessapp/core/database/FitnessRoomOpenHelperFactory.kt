package com.yeonsik.fitnessapp.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Supplies the exact v51 schema only while Android creates a brand-new file.
 * Existing files delegate directly to Room's callback and are never recreated here.
 */
internal class FitnessRoomOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    private val delegate = FrameworkSQLiteOpenHelperFactory()

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val roomCallback = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(roomCallback.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                roomCallback.onConfigure(db)
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                FitnessRoomFreshSchema.createBeforeRoom(db)
                roomCallback.onCreate(db)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                roomCallback.onUpgrade(db, oldVersion, newVersion)
            }

            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                roomCallback.onDowngrade(db, oldVersion, newVersion)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                roomCallback.onOpen(db)
            }
        }
        return delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(callback)
                .build()
        )
    }
}

