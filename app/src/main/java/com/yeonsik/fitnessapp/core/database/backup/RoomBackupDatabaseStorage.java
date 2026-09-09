package com.yeonsik.fitnessapp.core.database.backup;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import com.yeonsik.fitnessapp.core.database.FitnessDatabaseContract;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;

/** Room-owned implementation of the allowlisted backup database surface. */
public final class RoomBackupDatabaseStorage implements BackupDatabaseStorage {
    private final SupportSQLiteDatabase database;

    public RoomBackupDatabaseStorage(FitnessRoomDatabase roomDatabase) {
        if (roomDatabase == null) {
            throw new IllegalArgumentException("FitnessRoomDatabase is required.");
        }
        this.database = roomDatabase.getOpenHelper().getWritableDatabase();
    }

    /**
     * Compatibility-only bridge for migration fixtures and old backup tests.
     * Application composition uses the Room constructor above.
     */
    public RoomBackupDatabaseStorage(FitnessDatabaseHelper helper) {
        if (helper == null) {
            throw new IllegalArgumentException("FitnessDatabaseHelper is required.");
        }
        helper.getWritableDatabase();
        SupportSQLiteOpenHelper openHelper = new FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(helper.applicationContext())
                        .name(FitnessDatabaseContract.NAME)
                        .callback(new SupportSQLiteOpenHelper.Callback(
                                FitnessDatabaseHelper.DATABASE_VERSION
                        ) {
                            @Override public void onConfigure(SupportSQLiteDatabase db) { }
                            @Override public void onCreate(SupportSQLiteDatabase db) { }
                            @Override public void onUpgrade(
                                    SupportSQLiteDatabase db,
                                    int oldVersion,
                                    int newVersion
                            ) { }
                            @Override public void onDowngrade(
                                    SupportSQLiteDatabase db,
                                    int oldVersion,
                                    int newVersion
                            ) { }
                            @Override public void onOpen(SupportSQLiteDatabase db) { }
                        })
                        .build()
        );
        this.database = openHelper.getWritableDatabase();
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

    /** Compatibility-only access used by the legacy catalog seed adapter. */
    public SupportSQLiteDatabase supportDatabase() {
        return database;
    }
}
