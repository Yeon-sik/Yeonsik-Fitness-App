package com.yeonsik.fitnessapp.core.database.backup;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;

/** Room-owned implementation of the allowlisted backup database surface. */
public final class RoomBackupDatabaseStorage implements BackupDatabaseStorage {
    private final SupportSQLiteDatabase database;

    public RoomBackupDatabaseStorage(FitnessRoomDatabase roomDatabase) {
        if (roomDatabase == null) {
            throw new IllegalArgumentException("FitnessRoomDatabase is required.");
        }
        this.database = roomDatabase.getOpenHelper().getWritableDatabase();
    }

    @Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return database.query(new SimpleSQLiteQuery(sql, selectionArgs));
    }

    @Override
    public Cursor query(
            String table,
            String[] columns,
            String selection,
            String[] selectionArgs,
            String groupBy,
            String having,
            String orderBy
    ) {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (columns == null || columns.length == 0) {
            sql.append('*');
        } else {
            for (int index = 0; index < columns.length; index++) {
                if (index > 0) {
                    sql.append(", ");
                }
                sql.append(columns[index]);
            }
        }
        sql.append(" FROM ").append(table);
        if (selection != null && !selection.isEmpty()) {
            sql.append(" WHERE ").append(selection);
        }
        if (groupBy != null && !groupBy.isEmpty()) {
            sql.append(" GROUP BY ").append(groupBy);
        }
        if (having != null && !having.isEmpty()) {
            sql.append(" HAVING ").append(having);
        }
        if (orderBy != null && !orderBy.isEmpty()) {
            sql.append(" ORDER BY ").append(orderBy);
        }
        return database.query(new SimpleSQLiteQuery(sql.toString(), selectionArgs));
    }

    @Override
    public long longForQuery(String sql, String[] selectionArgs) {
        try (Cursor cursor = rawQuery(sql, selectionArgs)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    @Override
    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) {
        return database.insert(table, SQLiteDatabase.CONFLICT_ABORT, values);
    }

    @Override
    public long insertWithOnConflict(
            String table,
            String nullColumnHack,
            ContentValues values,
            int conflictAlgorithm
    ) {
        return database.insert(table, conflictAlgorithm, values);
    }

    @Override public void beginTransaction() { database.beginTransaction(); }
    @Override public void setTransactionSuccessful() { database.setTransactionSuccessful(); }
    @Override public void endTransaction() { database.endTransaction(); }
    @Override public int getVersion() { return database.getVersion(); }

}
