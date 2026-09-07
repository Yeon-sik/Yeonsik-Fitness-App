package com.yeonsik.fitnessapp.core.database

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Converts historical SQLite inline nullable primary keys to Room-compatible NOT NULL
 * primary keys without accepting unknown schemas or lossy copies.
 */
internal object FitnessPrimaryKeyCompatibility {
    private data class Column(
        val name: String,
        val notNull: Boolean,
        val primaryKeyPosition: Int
    )

    private data class Table(
        val name: String,
        val createSql: String,
        val columns: List<Column>
    ) {
        val primaryKeys: List<Column>
            get() = columns.filter { it.primaryKeyPosition > 0 }.sortedBy { it.primaryKeyPosition }
        val nullablePrimaryKeys: List<Column>
            get() = primaryKeys.filterNot { it.notNull }
    }

    fun normalizeNullablePrimaryKeys(database: SupportSQLiteDatabase) {
        val tables = loadTables(database)
        val known = FitnessDatabaseContract.tableNames +
            FitnessDatabaseContract.optionalLegacyTableNames
        check(tables.all { it.name in known }) {
            "Refusing primary-key normalization for unknown tables: ${tables.map { it.name }.toSet() - known}"
        }

        database.beginTransaction()
        try {
            tables.filter { it.nullablePrimaryKeys.isNotEmpty() }
                .forEach { rebuild(database, it) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun loadTables(database: SupportSQLiteDatabase): List<Table> {
        val tables = mutableListOf<Table>()
        database.query(
            SimpleSQLiteQuery(
                "SELECT name, sql FROM sqlite_master WHERE type = 'table' " +
                    "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name"
            )
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                val createSql = cursor.getString(1)
                    ?: error("Table $name has no CREATE TABLE SQL.")
                tables += Table(name, createSql, columns(database, name))
            }
        }
        return tables
    }

    private fun columns(database: SupportSQLiteDatabase, table: String): List<Column> {
        val result = mutableListOf<Column>()
        database.query(SimpleSQLiteQuery("PRAGMA table_info(${identifier(table)})")).use { cursor ->
            while (cursor.moveToNext()) {
                result += Column(
                    name = cursor.getString(1),
                    notNull = cursor.getInt(3) != 0,
                    primaryKeyPosition = cursor.getInt(5)
                )
            }
        }
        check(result.isNotEmpty()) { "No columns found for $table." }
        return result
    }

    private fun rebuild(database: SupportSQLiteDatabase, table: Table) {
        table.nullablePrimaryKeys.forEach { key ->
            val nullRows = scalar(
                database,
                "SELECT COUNT(*) FROM ${identifier(table.name)} " +
                    "WHERE ${identifier(key.name)} IS NULL"
            )
            check(nullRows == 0L) {
                "Refusing Room migration: ${table.name}.${key.name} has $nullRows NULL primary-key rows."
            }
        }

        val source = table.name
        val target = "${source}__room_v51_pk_target"
        check(!exists(database, target)) { "Temporary table collision: $target" }

        val countBefore = scalar(database, "SELECT COUNT(*) FROM ${identifier(source)}")
        val sequence = sequence(database, source)
        val indexes = schemaSql(database, "index", source)
        val triggers = schemaSql(database, "trigger", source)
        database.execSQL(rewriteCreateSql(table, target))

        val names = table.columns.joinToString(", ") { identifier(it.name) }
        database.execSQL(
            "INSERT INTO ${identifier(target)} ($names) " +
                "SELECT $names FROM ${identifier(source)}"
        )
        val countAfter = scalar(database, "SELECT COUNT(*) FROM ${identifier(target)}")
        check(countBefore == countAfter) {
            "Row count changed while rebuilding $source: $countBefore -> $countAfter"
        }

        val differences = scalar(
            database,
            "SELECT COUNT(*) FROM (" +
                "SELECT $names FROM ${identifier(source)} EXCEPT " +
                "SELECT $names FROM ${identifier(target)} UNION ALL " +
                "SELECT $names FROM ${identifier(target)} EXCEPT " +
                "SELECT $names FROM ${identifier(source)})"
        )
        check(differences == 0L) {
            "Value verification failed for $source: $differences differing rows."
        }

        database.execSQL("DROP TABLE ${identifier(source)}")
        database.execSQL("ALTER TABLE ${identifier(target)} RENAME TO ${identifier(source)}")
        indexes.forEach(database::execSQL)
        triggers.forEach(database::execSQL)
        restoreSequence(database, source, sequence)

        val rebuilt = columns(database, source)
        table.primaryKeys.forEach { primaryKey ->
            val actual = rebuilt.singleOrNull { it.name == primaryKey.name }
            check(actual != null && actual.notNull &&
                actual.primaryKeyPosition == primaryKey.primaryKeyPosition) {
                "Primary-key normalization failed for ${table.name}.${primaryKey.name}."
            }
        }
    }

    private fun rewriteCreateSql(table: Table, target: String): String {
        val tablePattern = Regex(
            "^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?\\\"?${Regex.escape(table.name)}\\\"?",
            RegexOption.IGNORE_CASE
        )
        var sql = table.createSql.replaceFirst(tablePattern, "CREATE TABLE ${identifier(target)}")
        check(sql != table.createSql) { "Cannot rewrite CREATE TABLE for ${table.name}." }

        table.nullablePrimaryKeys.forEach { key ->
            val inlinePk = Regex(
                "(?i)(\\b${Regex.escape(key.name)}\\b\\s+[^,()]+?)\\s+PRIMARY\\s+KEY\\b"
            )
            val match = inlinePk.find(sql) ?: error(
                "Cannot safely make ${table.name}.${key.name} NOT NULL: non-inline primary key."
            )
            check(!Regex("\\bNOT\\s+NULL\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(match.groupValues[1])) {
                "Unexpected NOT NULL state for ${table.name}.${key.name}."
            }
            sql = sql.replaceRange(match.range, match.groupValues[1] + " NOT NULL PRIMARY KEY")
        }
        return sql
    }

    private fun schemaSql(
        database: SupportSQLiteDatabase,
        type: String,
        table: String
    ): List<String> {
        val sql = mutableListOf<String>()
        database.query(
            SimpleSQLiteQuery(
                "SELECT sql FROM sqlite_master WHERE type = ? AND tbl_name = ? " +
                    "AND sql IS NOT NULL ORDER BY name",
                arrayOf(type, table)
            )
        ).use { cursor ->
            while (cursor.moveToNext()) sql += cursor.getString(0)
        }
        return sql
    }

    private fun sequence(database: SupportSQLiteDatabase, table: String): Long? {
        if (!exists(database, "sqlite_sequence")) return null
        database.query(
            SimpleSQLiteQuery("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table))
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun restoreSequence(database: SupportSQLiteDatabase, table: String, value: Long?) {
        if (value == null) return
        database.execSQL(
            "INSERT OR REPLACE INTO sqlite_sequence(name, seq) VALUES (?, ?)",
            arrayOf<Any>(table, value)
        )
    }

    private fun exists(database: SupportSQLiteDatabase, table: String): Boolean =
        scalar(
            database,
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ) > 0L

    private fun scalar(
        database: SupportSQLiteDatabase,
        sql: String,
        bindArgs: Array<Any?> = emptyArray()
    ): Long {
        database.query(SimpleSQLiteQuery(sql, bindArgs)).use { cursor ->
            check(cursor.moveToFirst()) { "Expected scalar result for: $sql" }
            return cursor.getLong(0)
        }
    }

    private fun identifier(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""
}

