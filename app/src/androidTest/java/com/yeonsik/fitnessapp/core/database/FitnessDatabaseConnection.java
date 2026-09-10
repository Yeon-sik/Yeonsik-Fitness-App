package com.yeonsik.fitnessapp.core.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.VerifiedFoodCatalogSeed;

/**
 * Test-only SQL compatibility surface backed by the one Room-owned connection.
 *
 * <p>Repositories still use their established SQL and transaction boundaries while DAO migration
 * proceeds incrementally. The legacy constructor exists only for old tests and migration tooling;
 * application code must use Room repositories and DAOs.</p>
 */
public final class FitnessDatabaseConnection {
    private final SupportSQLiteDatabase database;
    private final android.content.Context context;

    private FitnessDatabaseConnection(
            SupportSQLiteDatabase database,
            android.content.Context context
    ) {
        if (database == null) {
            throw new IllegalArgumentException("Room database connection is required.");
        }
        this.database = database;
        this.context = context;
    }

    public static FitnessDatabaseConnection fromRoom(FitnessRoomDatabase roomDatabase) {
        return fromRoom(roomDatabase, null);
    }

    public static FitnessDatabaseConnection fromRoom(
            FitnessRoomDatabase roomDatabase,
            android.content.Context context
    ) {
        if (roomDatabase == null) {
            throw new IllegalArgumentException("FitnessRoomDatabase is required.");
        }
        return new FitnessDatabaseConnection(
                roomDatabase.getOpenHelper().getWritableDatabase(),
                context == null ? null : context.getApplicationContext()
        );
    }

    /** Compatibility bridge for existing repository tests; never used by production assembly. */
    public static FitnessDatabaseConnection fromLegacy(FitnessDatabaseHelper helper) {
        if (helper == null) {
            throw new IllegalArgumentException("FitnessDatabaseHelper is required.");
        }
        // Compatibility-only path for pre-Room tests/callers: let the historical helper create
        // and upgrade the file before attaching the SQL facade. Production code never calls this.
        helper.getWritableDatabase();
        return new FitnessDatabaseConnection(
                new androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
                        .create(androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                                helper.applicationContext())
                                .name(FitnessDatabaseContract.NAME)
                                .callback(new androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(
                                        FitnessDatabaseHelper.DATABASE_VERSION) {
                                    @Override public void onConfigure(SupportSQLiteDatabase db) { }
                                    @Override public void onCreate(SupportSQLiteDatabase db) { }
                                    @Override public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) { }
                                    @Override public void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) { }
                                    @Override public void onOpen(SupportSQLiteDatabase db) { }
                                }).build())
                        .getWritableDatabase(),
                helper.applicationContext()
        );
    }

    public void execSQL(String sql) { database.execSQL(sql); }
    public void execSQL(String sql, Object[] bindArgs) { database.execSQL(sql, bindArgs); }
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return database.query(new SimpleSQLiteQuery(sql, selectionArgs));
    }
    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs,
                        String groupBy, String having, String orderBy) {
        return query(table, columns, selection, selectionArgs, groupBy, having, orderBy, null);
    }
    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs,
                        String groupBy, String having, String orderBy, String limit) {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (columns == null || columns.length == 0) sql.append("*");
        else {
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
        if (limit != null && !limit.isEmpty()) sql.append(" LIMIT ").append(limit);
        return database.query(new SimpleSQLiteQuery(sql.toString(), selectionArgs));
    }

    public long longForQuery(String query, String[] selectionArgs) {
        try (Cursor cursor = rawQuery(query, selectionArgs)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }
    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) {
        return database.insert(table, SQLiteDatabase.CONFLICT_ABORT, values);
    }
    public long insertWithOnConflict(String table, String nullColumnHack, ContentValues values,
                                      int conflictAlgorithm) {
        return database.insert(table, conflictAlgorithm, values);
    }
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return database.update(table, SQLiteDatabase.CONFLICT_NONE, values, whereClause, whereArgs);
    }
    public int delete(String table, String whereClause, String[] whereArgs) {
        return database.delete(table, whereClause, whereArgs);
    }
    public boolean inTransaction() { return database.inTransaction(); }
    public void beginTransaction() { database.beginTransaction(); }
    public void setTransactionSuccessful() { database.setTransactionSuccessful(); }
    public void endTransaction() { database.endTransaction(); }

    public android.content.Context applicationContext() { return context; }
    public int getVersion() { return database.getVersion(); }

    public void reconcileVerifiedFoodCatalog() {
        if (context == null) return;
        VerifiedFoodCatalogSeed.seedWithSupport(context, database);
    }
}
