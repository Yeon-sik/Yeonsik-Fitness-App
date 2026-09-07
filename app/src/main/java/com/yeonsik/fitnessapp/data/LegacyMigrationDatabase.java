package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Small common surface for the historical schema migrations.
 *
 * <p>The legacy helper and Room receive different SQLite wrappers, but the migration SQL must
 * remain a single implementation. Keep this interface limited to operations used while changing
 * schema or repairing historical rows; it is not a repository database abstraction.</p>
 */
interface LegacyMigrationDatabase {
    void execSQL(String sql);

    void execSQL(String sql, Object[] bindArgs);

    Cursor query(
            String table,
            String[] columns,
            String selection,
            String[] selectionArgs,
            String groupBy,
            String having,
            String orderBy
    );

    Cursor rawQuery(String sql, String[] selectionArgs);

    long insertWithOnConflict(String table, String nullColumnHack, ContentValues values, int conflictAlgorithm);

    long insertOrThrow(String table, String nullColumnHack, ContentValues values);

    int update(String table, ContentValues values, String whereClause, String[] whereArgs);

    int delete(String table, String whereClause, String[] whereArgs);

    boolean inTransaction();

    void beginTransaction();

    void setTransactionSuccessful();

    void endTransaction();
}

final class AndroidLegacyMigrationDatabase implements LegacyMigrationDatabase {
    private final SQLiteDatabase database;

    AndroidLegacyMigrationDatabase(SQLiteDatabase database) {
        this.database = database;
    }

    @Override public void execSQL(String sql) { database.execSQL(sql); }
    @Override public void execSQL(String sql, Object[] bindArgs) { database.execSQL(sql, bindArgs); }
    @Override public Cursor query(String table, String[] columns, String selection, String[] args,
                                  String groupBy, String having, String orderBy) {
        return database.query(table, columns, selection, args, groupBy, having, orderBy);
    }
    @Override public Cursor rawQuery(String sql, String[] args) { return database.rawQuery(sql, args); }
    @Override public long insertWithOnConflict(String table, String nullColumnHack, ContentValues values,
                                                int conflictAlgorithm) {
        return database.insertWithOnConflict(table, nullColumnHack, values, conflictAlgorithm);
    }
    @Override public long insertOrThrow(String table, String nullColumnHack, ContentValues values) {
        return database.insertOrThrow(table, nullColumnHack, values);
    }
    @Override public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return database.update(table, values, whereClause, whereArgs);
    }
    @Override public int delete(String table, String whereClause, String[] whereArgs) {
        return database.delete(table, whereClause, whereArgs);
    }
    @Override public boolean inTransaction() { return database.inTransaction(); }
    @Override public void beginTransaction() { database.beginTransaction(); }
    @Override public void setTransactionSuccessful() { database.setTransactionSuccessful(); }
    @Override public void endTransaction() { database.endTransaction(); }
}

/** Adapter used exclusively from Room Migration callbacks. */
final class SupportLegacyMigrationDatabase implements LegacyMigrationDatabase {
    private final SupportSQLiteDatabase database;

    public SupportLegacyMigrationDatabase(SupportSQLiteDatabase database) {
        this.database = database;
    }

    @Override public void execSQL(String sql) { database.execSQL(sql); }
    @Override public void execSQL(String sql, Object[] bindArgs) { database.execSQL(sql, bindArgs); }

    @Override public Cursor query(String table, String[] columns, String selection, String[] args,
                                  String groupBy, String having, String orderBy) {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (columns == null || columns.length == 0) {
            sql.append("*");
        } else {
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(columns[i]);
            }
        }
        sql.append(" FROM ").append(table);
        if (selection != null && !selection.isEmpty()) sql.append(" WHERE ").append(selection);
        if (groupBy != null && !groupBy.isEmpty()) sql.append(" GROUP BY ").append(groupBy);
        if (having != null && !having.isEmpty()) sql.append(" HAVING ").append(having);
        if (orderBy != null && !orderBy.isEmpty()) sql.append(" ORDER BY ").append(orderBy);
        return database.query(new SimpleSQLiteQuery(sql.toString(), args));
    }

    @Override public Cursor rawQuery(String sql, String[] args) {
        return database.query(new SimpleSQLiteQuery(sql, args));
    }

    @Override public long insertWithOnConflict(String table, String nullColumnHack, ContentValues values,
                                                int conflictAlgorithm) {
        return database.insert(table, conflictAlgorithm, values);
    }
    @Override public long insertOrThrow(String table, String nullColumnHack, ContentValues values) {
        return database.insert(table, SQLiteDatabase.CONFLICT_ABORT, values);
    }
    @Override public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return database.update(table, SQLiteDatabase.CONFLICT_NONE, values, whereClause, whereArgs);
    }
    @Override public int delete(String table, String whereClause, String[] whereArgs) {
        return database.delete(table, whereClause, whereArgs);
    }
    @Override public boolean inTransaction() { return database.inTransaction(); }
    @Override public void beginTransaction() { database.beginTransaction(); }
    @Override public void setTransactionSuccessful() { database.setTransactionSuccessful(); }
    @Override public void endTransaction() { database.endTransaction(); }
}
