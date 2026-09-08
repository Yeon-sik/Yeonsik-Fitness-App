package com.yeonsik.fitnessapp.core.database;

import androidx.annotation.NonNull;

import java.util.concurrent.Callable;

/**
 * Shared Room transaction boundary for application-level composed writes.
 *
 * <p>The runner reuses an already active transaction so a repository operation
 * remains atomic when it is called from a cross-feature use case. Direct
 * callers still receive the same transaction protection when no transaction is
 * active.</p>
 */
public final class RoomTransactionRunner {
    private final FitnessRoomDatabase database;

    public RoomTransactionRunner(@NonNull FitnessRoomDatabase database) {
        this.database = database;
    }

    public void run(@NonNull Runnable work) {
        if (isInTransaction()) {
            work.run();
            return;
        }
        database.runInTransaction(work);
    }

    public <T> T call(@NonNull Callable<T> work) {
        if (isInTransaction()) {
            try {
                return work.call();
            } catch (Exception error) {
                throw propagate(error);
            }
        }
        try {
            return database.runInTransaction(work);
        } catch (Exception error) {
            throw propagate(error);
        }
    }

    private boolean isInTransaction() {
        return database.getOpenHelper().getWritableDatabase().inTransaction();
    }

    private static RuntimeException propagate(Exception error) {
        if (error instanceof RuntimeException) {
            return (RuntimeException) error;
        }
        return new IllegalStateException("Room transaction failed.", error);
    }
}
