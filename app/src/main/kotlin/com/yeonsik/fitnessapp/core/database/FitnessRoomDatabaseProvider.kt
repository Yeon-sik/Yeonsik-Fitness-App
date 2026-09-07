package com.yeonsik.fitnessapp.core.database

import android.content.Context
import androidx.room.Room

/** App-scoped Room owner for fitness_mvp.db. Runtime clients must obtain this instance only. */
object FitnessRoomDatabaseProvider {
    @Volatile
    private var instance: FitnessRoomDatabase? = null

    @JvmStatic
    fun get(context: Context): FitnessRoomDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FitnessRoomDatabase::class.java,
                FitnessDatabaseContract.NAME
            )
                .openHelperFactory(FitnessRoomOpenHelperFactory())
                .addMigrations(*FitnessRoomMigrations.all(context.applicationContext))
                .build()
                .also { instance = it }
        }

    internal fun resetForTests() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}

