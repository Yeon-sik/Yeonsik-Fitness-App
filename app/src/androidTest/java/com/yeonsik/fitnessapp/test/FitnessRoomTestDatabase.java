package com.yeonsik.fitnessapp.test;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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

    public static void createVersionEightFixture(SQLiteDatabase database) {
        StringBuilder sql = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("database/fitness-v8.sql"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().startsWith("--")) sql.append(line).append('\n');
            }
        } catch (IOException error) {
            throw new AssertionError("Cannot read the shipped v8 schema fixture", error);
        }
        for (String statement : sql.toString().split(";")) {
            if (!statement.trim().isEmpty()) database.execSQL(statement);
        }
    }

}
