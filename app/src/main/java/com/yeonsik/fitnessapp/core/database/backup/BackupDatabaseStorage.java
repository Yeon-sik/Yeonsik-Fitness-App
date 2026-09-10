package com.yeonsik.fitnessapp.core.database.backup;

import android.content.ContentValues;
import android.database.Cursor;

/**
 * Allowlisted database surface for the local backup format.
 *
 * <p>The backup format preserves rows from schema versions that may not have a current Room
 * entity yet, so this boundary intentionally exposes cursor/schema inspection and dynamic row
 * insertion. No feature repository should depend on it.</p>
 */
public interface BackupDatabaseStorage {
    Cursor rawQuery(String sql, String[] selectionArgs);

    Cursor query(
            String table,
            String[] columns,
            String selection,
            String[] selectionArgs,
            String groupBy,
            String having,
            String orderBy
    );

    long longForQuery(String sql, String[] selectionArgs);

    long insertOrThrow(String table, String nullColumnHack, ContentValues values);

    long insertWithOnConflict(
            String table,
            String nullColumnHack,
            ContentValues values,
            int conflictAlgorithm
    );

    void beginTransaction();

    void setTransactionSuccessful();

    void endTransaction();

    int getVersion();
}
