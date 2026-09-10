package com.yeonsik.fitnessapp.test;

import android.content.Context;

import androidx.room.Room;

import com.yeonsik.fitnessapp.core.database.FitnessDatabaseContract;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomMigrations;

/** Opens the Room view of the isolated SQLite file prepared by an instrumentation fixture. */
public final class FitnessRoomTestDatabase {
    private FitnessRoomTestDatabase() {
    }

    public static FitnessRoomDatabase open(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Test database context is required.");
        }
        return Room.databaseBuilder(
                        context,
                        FitnessRoomDatabase.class,
                        FitnessDatabaseContract.NAME
                )
                .addMigrations(FitnessRoomMigrations.all(context))
                .build();
    }
}
