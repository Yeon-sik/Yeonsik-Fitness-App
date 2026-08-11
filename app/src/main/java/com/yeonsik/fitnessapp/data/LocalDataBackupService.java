package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LocalDataBackupService {
    private static final String BACKUP_FORMAT = "fitness-os.local-backup";
    private static final int BACKUP_FORMAT_VERSION = 1;
    private static final int MAX_BACKUP_BYTES = 32 * 1024 * 1024;
    private static final int MAX_BACKUP_ROWS = 250_000;
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String OWNER_SCOPE_KEY = "_ownerScope";
    private static final String OWNER_SCOPE_CURRENT = "current";
    private static final String OWNER_SCOPE_PUBLIC = "public";

    private static final List<String> TABLE_ORDER = createTableOrder();
    private static final Set<String> PUBLIC_SCOPE_TABLES = createPublicScopeTables();

    private final FitnessDatabaseHelper dbHelper;
    private final String recordUserId;
    private final String nutritionOwnerId;

    public LocalDataBackupService(
            FitnessDatabaseHelper dbHelper,
            String recordUserId,
            String nutritionOwnerId
    ) {
        this.dbHelper = Objects.requireNonNull(dbHelper, "dbHelper");
        this.recordUserId = requireIdentity(recordUserId, "recordUserId");
        this.nutritionOwnerId = requireIdentity(nutritionOwnerId, "nutritionOwnerId");
    }

    public BackupPreview writeBackup(OutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream");

        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String exportedAt = Instant.now().toString();
        ExportPlan exportPlan;
        database.beginTransaction();
        try {
            exportPlan = buildExportPlan(database);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

        JSONObject root = new JSONObject();
        JSONObject tables = new JSONObject();
        try {
            root.put("format", BACKUP_FORMAT);
            root.put("formatVersion", BACKUP_FORMAT_VERSION);
            root.put("databaseVersion", database.getVersion());
            root.put("exportedAt", exportedAt);
            for (String table : TABLE_ORDER) {
                JSONArray rows = new JSONArray();
                for (JSONObject row : exportPlan.rowsByTable.get(table)) {
                    rows.put(row);
                }
                tables.put(table, rows);
            }
            root.put("tables", tables);
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to build local backup JSON.", e);
        }

        byte[] payload = root.toString().getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_BACKUP_BYTES) {
            throw new IllegalStateException("Local backup exceeds the 32 MiB size limit.");
        }
        outputStream.write(payload);
        outputStream.flush();
        return new BackupPreview(exportPlan.totalRows, exportedAt, database.getVersion());
    }

    public BackupPreview previewBackup(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        BackupDocument document = parseBackup(readUtf8(inputStream), database);
        int totalRows = 0;
        for (JSONArray rows : document.tables.values()) {
            totalRows += rows.length();
        }
        return new BackupPreview(totalRows, document.exportedAt, document.databaseVersion);
    }

    public RestoreResult restoreBackup(InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "inputStream");

        SQLiteDatabase database = dbHelper.getWritableDatabase();
        BackupDocument document = parseBackup(readUtf8(inputStream), database);

        int importedRows = 0;
        int skippedRows = 0;
        database.beginTransaction();
        try {
            for (String table : TABLE_ORDER) {
                TableSchema schema = schemaFor(database, table);
                JSONArray rows = document.tables.get(table);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    if ("cardio_route_points".equals(table)) {
                        if (routePointExists(
                                database,
                                row.getString("record_id"),
                                row.getLong("captured_at_epoch_ms")
                        )) {
                            skippedRows++;
                            continue;
                        }
                        long result = database.insertOrThrow(
                                table,
                                null,
                                toContentValues(row, schema)
                        );
                        if (result == -1L) {
                            skippedRows++;
                        } else {
                            importedRows++;
                        }
                        continue;
                    }

                    long result = database.insertWithOnConflict(
                            table,
                            null,
                            toContentValues(row, schema),
                            SQLiteDatabase.CONFLICT_IGNORE
                    );
                    if (result == -1L) {
                        skippedRows++;
                    } else {
                        importedRows++;
                    }
                }
            }
            // A pre-v18 backup may contain the former cooked official catalog. Reconcile inside
            // the same transaction so restored meal snapshots remain immutable while only the
            // current curated, preparation-specific catalog stays searchable.
            dbHelper.reconcileVerifiedFoodCatalog(database);
            database.setTransactionSuccessful();
            return new RestoreResult(importedRows, skippedRows);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Backup JSON row is invalid.", e);
        } finally {
            database.endTransaction();
        }
    }

    public void writeRecordsSummaryCsv(OutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream");
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        outputStream.write(UTF8_BOM);
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            writeCsvHeader(
                    writer,
                    "\uAE30\uB85D \uC720\uD615",
                    "\uB0A0\uC9DC",
                    "\uC774\uB984",
                    "\uBD84\uB958",
                    "\uC9C0\uC18D \uC2DC\uAC04(\uCD08)",
                    "\uC6B4\uB3D9\uB7C9(kg)",
                    "\uCCB4\uC911(kg)",
                    "\uCE7C\uB85C\uB9AC(kcal)",
                    "\uB2E8\uBC31\uC9C8(g)",
                    "\uD0C4\uC218\uD654\uBB3C(g)",
                    "\uC9C0\uBC29(g)",
                    "\uD3C9\uADE0 \uC2EC\uBC15\uC218"
            );
            writeWorkoutSummaryRows(database, writer);
            writeMealSummaryRows(database, writer);
            writeWeightSummaryRows(database, writer);
        }
    }

    private ExportPlan buildExportPlan(SQLiteDatabase database) {
        Map<String, List<JSONObject>> rowsByTable = new LinkedHashMap<>();
        PublicNutritionClosure closure = resolvePublicNutritionClosure(database);
        int totalRows = 0;

        for (String table : TABLE_ORDER) {
            List<JSONObject> rows;
            if ("nutrition_foods".equals(table)) {
                rows = exportNutritionFoods(database, closure);
            } else if ("nutrition_food_nutrients".equals(table)) {
                rows = exportNutritionFoodNutrients(database, closure);
            } else if ("nutrition_food_components".equals(table)) {
                rows = exportNutritionFoodComponents(database, closure);
            } else {
                rows = exportOwnerRows(database, schemaFor(database, table), null);
            }

            totalRows += rows.size();
            if (totalRows > MAX_BACKUP_ROWS) {
                throw new IllegalStateException("Local backup exceeds the 250000 row limit.");
            }
            rowsByTable.put(table, rows);
        }

        return new ExportPlan(rowsByTable, totalRows);
    }

    private List<JSONObject> exportNutritionFoods(
            SQLiteDatabase database,
            PublicNutritionClosure closure
    ) {
        TableSchema schema = schemaFor(database, "nutrition_foods");
        List<JSONObject> rows = new ArrayList<>(exportOwnerRows(database, schema, OWNER_SCOPE_CURRENT));
        if (closure.publicFoodIds.isEmpty()) {
            return rows;
        }

        try (Cursor cursor = database.query(
                "nutrition_foods",
                null,
                "owner_id IS NULL AND deleted_at IS NULL",
                null,
                null,
                null,
                "rowid ASC"
        )) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
                if (closure.publicFoodIds.contains(id)) {
                    rows.add(exportPublicScopedRow(cursor, schema));
                }
            }
        }
        return rows;
    }

    private List<JSONObject> exportNutritionFoodNutrients(
            SQLiteDatabase database,
            PublicNutritionClosure closure
    ) {
        TableSchema schema = schemaFor(database, "nutrition_food_nutrients");
        List<JSONObject> rows = new ArrayList<>(exportOwnerRows(database, schema, OWNER_SCOPE_CURRENT));
        if (closure.publicFoodIds.isEmpty()) {
            return rows;
        }

        try (Cursor cursor = database.query(
                "nutrition_food_nutrients",
                null,
                "owner_id IS NULL AND deleted_at IS NULL",
                null,
                null,
                null,
                "rowid ASC"
        )) {
            while (cursor.moveToNext()) {
                String foodId = cursor.getString(cursor.getColumnIndexOrThrow("food_id"));
                if (closure.publicFoodIds.contains(foodId)) {
                    rows.add(exportPublicScopedRow(cursor, schema));
                }
            }
        }
        return rows;
    }

    private List<JSONObject> exportNutritionFoodComponents(
            SQLiteDatabase database,
            PublicNutritionClosure closure
    ) {
        TableSchema schema = schemaFor(database, "nutrition_food_components");
        List<JSONObject> rows = new ArrayList<>(exportOwnerRows(database, schema, OWNER_SCOPE_CURRENT));
        if (closure.publicComponentIds.isEmpty()) {
            return rows;
        }

        try (Cursor cursor = database.query(
                "nutrition_food_components",
                null,
                "owner_id IS NULL AND deleted_at IS NULL",
                null,
                null,
                null,
                "rowid ASC"
        )) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
                if (closure.publicComponentIds.contains(id)) {
                    rows.add(exportPublicScopedRow(cursor, schema));
                }
            }
        }
        return rows;
    }

    private List<JSONObject> exportOwnerRows(
            SQLiteDatabase database,
            TableSchema schema,
            String ownerScope
    ) {
        List<JSONObject> rows = new ArrayList<>();
        try (Cursor cursor = database.query(
                schema.tableName,
                null,
                schema.ownershipColumn + " = ?",
                new String[]{ownerValueFor(schema)},
                null,
                null,
                "rowid ASC"
        )) {
            while (cursor.moveToNext()) {
                rows.add(exportRow(cursor, schema, ownerScope));
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to export " + schema.tableName + ".", e);
        }
        return rows;
    }

    private PublicNutritionClosure resolvePublicNutritionClosure(SQLiteDatabase database) {
        Set<String> visitedFoodIds = new LinkedHashSet<>();
        Set<String> publicFoodIds = new LinkedHashSet<>();
        Set<String> publicComponentIds = new LinkedHashSet<>();
        Map<String, NutritionFoodRef> foodCache = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();

        try (Cursor cursor = database.rawQuery(
                "SELECT id FROM nutrition_foods " +
                        "WHERE owner_id = ? AND deleted_at IS NULL ORDER BY rowid ASC",
                new String[]{nutritionOwnerId}
        )) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                if (visitedFoodIds.add(id)) {
                    queue.addLast(id);
                }
            }
        }

        while (!queue.isEmpty()) {
            String parentFoodId = queue.removeFirst();
            try (Cursor cursor = database.query(
                    "nutrition_food_components",
                    new String[]{"id", "owner_id", "child_food_id"},
                    "parent_food_id = ? AND deleted_at IS NULL AND (owner_id = ? OR owner_id IS NULL)",
                    new String[]{parentFoodId, nutritionOwnerId},
                    null,
                    null,
                    "rowid ASC"
            )) {
                while (cursor.moveToNext()) {
                    String componentId = cursor.getString(cursor.getColumnIndexOrThrow("id"));
                    String componentOwnerId = cursor.isNull(cursor.getColumnIndexOrThrow("owner_id"))
                            ? null
                            : cursor.getString(cursor.getColumnIndexOrThrow("owner_id"));
                    String childFoodId = cursor.getString(cursor.getColumnIndexOrThrow("child_food_id"));
                    if (componentOwnerId == null) {
                        publicComponentIds.add(componentId);
                    }

                    NutritionFoodRef childRef = loadNutritionFoodRef(database, foodCache, childFoodId);
                    if (!childRef.active) {
                        continue;
                    }
                    if (childRef.ownerId == null) {
                        if (publicFoodIds.add(childFoodId) && visitedFoodIds.add(childFoodId)) {
                            queue.addLast(childFoodId);
                        }
                    } else if (nutritionOwnerId.equals(childRef.ownerId)
                            && visitedFoodIds.add(childFoodId)) {
                        queue.addLast(childFoodId);
                    }
                }
            }
        }

        return new PublicNutritionClosure(publicFoodIds, publicComponentIds);
    }

    private NutritionFoodRef loadNutritionFoodRef(
            SQLiteDatabase database,
            Map<String, NutritionFoodRef> cache,
            String foodId
    ) {
        NutritionFoodRef cached = cache.get(foodId);
        if (cached != null) {
            return cached;
        }

        NutritionFoodRef resolved = NutritionFoodRef.missing();
        try (Cursor cursor = database.rawQuery(
                "SELECT owner_id, deleted_at FROM nutrition_foods WHERE id = ? LIMIT 1",
                new String[]{foodId}
        )) {
            if (cursor.moveToFirst()) {
                String ownerId = cursor.isNull(0) ? null : cursor.getString(0);
                boolean active = cursor.isNull(1);
                resolved = new NutritionFoodRef(ownerId, active);
            }
        }
        cache.put(foodId, resolved);
        return resolved;
    }

    private static String requireIdentity(String value, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return trimmed;
    }

    private String ownerValueFor(TableSchema schema) {
        return "owner_id".equals(schema.ownershipColumn) ? nutritionOwnerId : recordUserId;
    }

    private JSONObject exportRow(Cursor cursor, TableSchema schema, String ownerScope) throws JSONException {
        JSONObject row = new JSONObject();
        for (String column : schema.exportColumns) {
            int index = cursor.getColumnIndexOrThrow(column);
            switch (cursor.getType(index)) {
                case Cursor.FIELD_TYPE_NULL:
                    row.put(column, JSONObject.NULL);
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    row.put(column, cursor.getLong(index));
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    row.put(column, cursor.getDouble(index));
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    row.put(column, cursor.getString(index));
                    break;
                default:
                    throw new IllegalStateException(
                            "BLOB columns are not supported in local backups: " + schema.tableName
                    );
            }
        }
        if (schema.supportsOwnerScopeMarker && ownerScope != null) {
            row.put(OWNER_SCOPE_KEY, ownerScope);
        }
        return row;
    }

    private JSONObject exportPublicScopedRow(Cursor cursor, TableSchema schema) {
        try {
            return exportRow(cursor, schema, OWNER_SCOPE_PUBLIC);
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to export " + schema.tableName + ".", e);
        }
    }

    private BackupDocument parseBackup(String payload, SQLiteDatabase database) {
        JSONObject root;
        try {
            root = new JSONObject(stripUtf8Bom(payload));
        } catch (JSONException e) {
            throw new IllegalArgumentException("Backup JSON is malformed.", e);
        }

        try {
            if (!BACKUP_FORMAT.equals(root.getString("format"))) {
                throw new IllegalArgumentException("Backup format is not supported.");
            }
            if (root.getInt("formatVersion") != BACKUP_FORMAT_VERSION) {
                throw new IllegalArgumentException("Backup format version is not supported.");
            }

            int databaseVersion = root.getInt("databaseVersion");
            if (databaseVersion > FitnessDatabaseHelper.DATABASE_VERSION) {
                throw new IllegalArgumentException("Backup database version is newer than this app.");
            }

            String exportedAt = root.getString("exportedAt");
            if (exportedAt.trim().isEmpty()) {
                throw new IllegalArgumentException("Backup exportedAt is required.");
            }

            JSONObject tablesObject = root.getJSONObject("tables");
            validateCompatibleTables(tablesObject, databaseVersion);

            Map<String, JSONArray> tables = new LinkedHashMap<>();
            int totalRows = 0;
            for (String table : TABLE_ORDER) {
                TableSchema schema = schemaFor(database, table);
                JSONArray rows = tablesObject.optJSONArray(table);
                if (rows == null) {
                    rows = new JSONArray();
                }
                validateRows(rows, schema);
                totalRows += rows.length();
                if (totalRows > MAX_BACKUP_ROWS) {
                    throw new IllegalArgumentException("Backup exceeds the 250000 row import limit.");
                }
                tables.put(table, rows);
            }
            validateNutritionDocumentScopes(tables, database);
            return new BackupDocument(exportedAt, databaseVersion, tables);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Backup JSON shape is invalid.", e);
        }
    }

    private void validateCompatibleTables(JSONObject tablesObject, int databaseVersion) {
        Set<String> actual = new LinkedHashSet<>();
        for (String key : jsonKeys(tablesObject)) {
            actual.add(key);
        }
        Set<String> expected = new LinkedHashSet<>(TABLE_ORDER);
        Set<String> required = new LinkedHashSet<>(expected);
        if (databaseVersion < 15) {
            required.remove("meal_record_item_components");
            required.remove("meal_record_item_component_nutrients");
        }
        if (databaseVersion < 16) {
            required.remove("body_profiles");
            required.remove("development_goals");
        }
        if (!required.equals(actual)) {
            throw new IllegalArgumentException("Backup table set is not supported.");
        }
    }

    private void validateRows(JSONArray rows, TableSchema schema) throws JSONException {
        for (int i = 0; i < rows.length(); i++) {
            Object raw = rows.get(i);
            if (!(raw instanceof JSONObject)) {
                throw new IllegalArgumentException(schema.tableName + " contains a non-object row.");
            }
            JSONObject row = (JSONObject) raw;
            for (String key : jsonKeys(row)) {
                if (!schema.allowedBackupColumns.contains(key)) {
                    throw new IllegalArgumentException(
                            "Unsupported column in backup table " + schema.tableName + ": " + key
                    );
                }
            }

            String ownerScope = backupOwnerScope(row, schema);
            if (OWNER_SCOPE_PUBLIC.equals(ownerScope) && !schema.supportsOwnerScopeMarker) {
                throw new IllegalArgumentException(
                        "Public owner scope is not supported for " + schema.tableName
                );
            }

            for (String required : schema.requiredBackupColumns) {
                if (!row.has(required) || row.isNull(required)) {
                    throw new IllegalArgumentException(
                            "Missing required backup column " + schema.tableName + "." + required
                    );
                }
            }
        }
    }

    private void validateNutritionDocumentScopes(
            Map<String, JSONArray> tables,
            SQLiteDatabase database
    ) throws JSONException {
        TableSchema foodSchema = schemaFor(database, "nutrition_foods");
        TableSchema nutrientSchema = schemaFor(database, "nutrition_food_nutrients");
        TableSchema componentSchema = schemaFor(database, "nutrition_food_components");

        Map<String, String> foodScopes = new LinkedHashMap<>();
        Set<String> publicChildReferencedFoodIds = new LinkedHashSet<>();

        JSONArray foodRows = tables.get("nutrition_foods");
        for (int i = 0; i < foodRows.length(); i++) {
            JSONObject row = foodRows.getJSONObject(i);
            String foodId = row.getString("id");
            String scope = backupOwnerScope(row, foodSchema);
            if (foodScopes.put(foodId, scope) != null) {
                throw new IllegalArgumentException("Duplicate nutrition food row in backup: " + foodId);
            }
            if (OWNER_SCOPE_PUBLIC.equals(scope)) {
                if (!"public".equals(row.optString("visibility", ""))) {
                    throw new IllegalArgumentException(
                            "Public nutrition food must keep visibility=public: " + foodId
                    );
                }
                if (row.has("deleted_at") && !row.isNull("deleted_at")) {
                    throw new IllegalArgumentException(
                            "Public nutrition food must be active: " + foodId
                    );
                }
            }
        }

        JSONArray nutrientRows = tables.get("nutrition_food_nutrients");
        for (int i = 0; i < nutrientRows.length(); i++) {
            JSONObject row = nutrientRows.getJSONObject(i);
            String foodId = row.getString("food_id");
            String scope = backupOwnerScope(row, nutrientSchema);
            String foodScope = foodScopes.get(foodId);
            if (foodScope == null) {
                throw new IllegalArgumentException(
                        "Nutrition nutrient references a missing food row: " + foodId
                );
            }
            if (OWNER_SCOPE_PUBLIC.equals(scope)) {
                if (!OWNER_SCOPE_PUBLIC.equals(foodScope)) {
                    throw new IllegalArgumentException(
                            "Public nutrition nutrient must reference a public food: " + foodId
                    );
                }
                if (row.has("deleted_at") && !row.isNull("deleted_at")) {
                    throw new IllegalArgumentException(
                            "Public nutrition nutrient must be active: " + row.getString("id")
                    );
                }
            } else if (!OWNER_SCOPE_CURRENT.equals(foodScope)) {
                throw new IllegalArgumentException(
                        "Current nutrition nutrient must reference a current food: " + foodId
                );
            }
        }

        JSONArray componentRows = tables.get("nutrition_food_components");
        for (int i = 0; i < componentRows.length(); i++) {
            JSONObject row = componentRows.getJSONObject(i);
            String parentFoodId = row.getString("parent_food_id");
            String childFoodId = row.getString("child_food_id");
            String scope = backupOwnerScope(row, componentSchema);
            String parentScope = foodScopes.get(parentFoodId);
            String childScope = foodScopes.get(childFoodId);
            if (parentScope == null || childScope == null) {
                throw new IllegalArgumentException(
                        "Nutrition component references a missing food row: " + row.getString("id")
                );
            }
            publicChildReferencedFoodIds.add(childFoodId);
            if (OWNER_SCOPE_PUBLIC.equals(scope)) {
                if (!OWNER_SCOPE_PUBLIC.equals(parentScope)
                        || !OWNER_SCOPE_PUBLIC.equals(childScope)) {
                    throw new IllegalArgumentException(
                            "Public nutrition component must stay inside public food scope: "
                                    + row.getString("id")
                    );
                }
                if (row.has("deleted_at") && !row.isNull("deleted_at")) {
                    throw new IllegalArgumentException(
                            "Public nutrition component must be active: " + row.getString("id")
                    );
                }
            } else {
                if (!OWNER_SCOPE_CURRENT.equals(parentScope)) {
                    throw new IllegalArgumentException(
                            "Current nutrition component must reference a current parent food: "
                                    + row.getString("id")
                    );
                }
            }
        }

        for (Map.Entry<String, String> entry : foodScopes.entrySet()) {
            if (OWNER_SCOPE_PUBLIC.equals(entry.getValue())
                    && !publicChildReferencedFoodIds.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "Public nutrition food must be required by a component closure: "
                                + entry.getKey()
                );
            }
        }
    }

    private ContentValues toContentValues(JSONObject row, TableSchema schema) throws JSONException {
        ContentValues values = new ContentValues();
        if (schema.injectsOwnerId) {
            if (OWNER_SCOPE_PUBLIC.equals(backupOwnerScope(row, schema))) {
                values.putNull("owner_id");
            } else {
                values.put("owner_id", nutritionOwnerId);
            }
        }
        if (schema.injectsUserId) {
            values.put("user_id", recordUserId);
        }

        for (String columnName : schema.exportColumns) {
            if (!row.has(columnName)) {
                continue;
            }
            if (row.isNull(columnName)) {
                values.putNull(columnName);
                continue;
            }

            ColumnInfo column = schema.columnsByName.get(columnName);
            Object value = row.get(columnName);
            String affinity = column.affinity();
            if ("INTEGER".equals(affinity)) {
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException(
                            "Expected integer for " + schema.tableName + "." + columnName
                    );
                }
                values.put(columnName, ((Number) value).longValue());
            } else if ("REAL".equals(affinity) || "NUMERIC".equals(affinity)) {
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException(
                            "Expected number for " + schema.tableName + "." + columnName
                    );
                }
                values.put(columnName, ((Number) value).doubleValue());
            } else {
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(
                            "Expected text for " + schema.tableName + "." + columnName
                    );
                }
                values.put(columnName, (String) value);
            }
        }
        return values;
    }

    private String backupOwnerScope(JSONObject row, TableSchema schema) throws JSONException {
        if (!row.has(OWNER_SCOPE_KEY)) {
            return OWNER_SCOPE_CURRENT;
        }
        if (!schema.supportsOwnerScopeMarker) {
            throw new IllegalArgumentException(
                    "Owner scope marker is not allowed for " + schema.tableName
            );
        }
        String scope = row.getString(OWNER_SCOPE_KEY);
        if (!OWNER_SCOPE_CURRENT.equals(scope) && !OWNER_SCOPE_PUBLIC.equals(scope)) {
            throw new IllegalArgumentException("Owner scope marker is invalid for " + schema.tableName);
        }
        return scope;
    }

    private boolean routePointExists(SQLiteDatabase database, String recordId, long capturedAtEpochMs) {
        return DatabaseUtils.longForQuery(
                database,
                "SELECT COUNT(*) FROM cardio_route_points " +
                        "WHERE record_id = ? AND captured_at_epoch_ms = ?",
                new String[]{recordId, Long.toString(capturedAtEpochMs)}
        ) > 0L;
    }

    private TableSchema schemaFor(SQLiteDatabase database, String table) {
        List<ColumnInfo> columns = new ArrayList<>();
        Map<String, ColumnInfo> columnsByName = new LinkedHashMap<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                ColumnInfo column = new ColumnInfo(
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("type")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 1,
                        cursor.isNull(cursor.getColumnIndexOrThrow("dflt_value"))
                                ? null
                                : cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                );
                columns.add(column);
                columnsByName.put(column.name, column);
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("Backup schema table is missing: " + table);
        }

        boolean injectsUserId = columnsByName.containsKey("user_id");
        boolean injectsOwnerId = columnsByName.containsKey("owner_id");
        String ownershipColumn = injectsOwnerId ? "owner_id" : (injectsUserId ? "user_id" : null);
        if (ownershipColumn == null) {
            throw new IllegalStateException("Backup table has no owner column: " + table);
        }

        List<String> exportColumns = new ArrayList<>();
        List<String> requiredColumns = new ArrayList<>();
        for (ColumnInfo column : columns) {
            if ("user_id".equals(column.name) || "owner_id".equals(column.name)) {
                continue;
            }
            if ("cardio_route_points".equals(table) && "id".equals(column.name)) {
                continue;
            }
            exportColumns.add(column.name);
            if (column.isRequiredForBackup()) {
                requiredColumns.add(column.name);
            }
        }

        Set<String> allowedColumns = new LinkedHashSet<>(exportColumns);
        boolean supportsOwnerScopeMarker = PUBLIC_SCOPE_TABLES.contains(table);
        if (supportsOwnerScopeMarker) {
            allowedColumns.add(OWNER_SCOPE_KEY);
        }

        return new TableSchema(
                table,
                ownershipColumn,
                injectsUserId,
                injectsOwnerId,
                supportsOwnerScopeMarker,
                Collections.unmodifiableList(exportColumns),
                Collections.unmodifiableSet(allowedColumns),
                Collections.unmodifiableList(requiredColumns),
                Collections.unmodifiableMap(columnsByName)
        );
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BACKUP_BYTES) {
                throw new IllegalArgumentException("Backup exceeds the 32 MiB import limit.");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String stripUtf8Bom(String payload) {
        if (!payload.isEmpty() && payload.charAt(0) == '\uFEFF') {
            return payload.substring(1);
        }
        return payload;
    }

    private static void writeCsvHeader(Writer writer, String... headers) throws IOException {
        writeCsvRow(writer, (Object[]) headers);
    }

    private static void writeCsvRow(Writer writer, Object... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCsv(fields[i]));
        }
        writer.write("\r\n");
        writer.flush();
    }

    private static String escapeCsv(Object field) {
        if (field == null) {
            return "";
        }
        String value = String.valueOf(field);
        boolean needsQuotes = value.contains(",")
                || value.contains("\"")
                || value.contains("\r")
                || value.contains("\n");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static Long nullableLong(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getLong(index);
    }

    private static Double nullableDouble(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getDouble(index);
    }

    private void writeWorkoutSummaryRows(SQLiteDatabase database, Writer writer) throws IOException {
        try (Cursor cursor = database.rawQuery(
                "SELECT date, workout_type, category, exercise_name, duration_seconds, " +
                        "total_volume_kg, average_heart_rate " +
                        "FROM workout_records WHERE user_id = ? AND deleted_at IS NULL " +
                        "ORDER BY date ASC, created_at ASC",
                new String[]{recordUserId}
        )) {
            while (cursor.moveToNext()) {
                writeCsvRow(
                        writer,
                        "\uC6B4\uB3D9",
                        cursor.getString(0),
                        cursor.getString(3),
                        cursor.getString(1) + "/" + cursor.getString(2),
                        nullableLong(cursor, 4),
                        nullableDouble(cursor, 5),
                        null,
                        null,
                        null,
                        null,
                        null,
                        nullableDouble(cursor, 6)
                );
            }
        }
    }

    private void writeMealSummaryRows(SQLiteDatabase database, Writer writer) throws IOException {
        try (Cursor cursor = database.rawQuery(
                "SELECT date, menu, calories, protein_grams, carbs_grams, fat_grams " +
                        "FROM meal_records WHERE user_id = ? AND deleted_at IS NULL " +
                        "ORDER BY date ASC, created_at ASC",
                new String[]{recordUserId}
        )) {
            while (cursor.moveToNext()) {
                writeCsvRow(
                        writer,
                        "\uC2DD\uB2E8",
                        cursor.getString(0),
                        cursor.getString(1),
                        null,
                        null,
                        null,
                        null,
                        nullableLong(cursor, 2),
                        nullableDouble(cursor, 3),
                        nullableDouble(cursor, 4),
                        nullableDouble(cursor, 5),
                        null
                );
            }
        }
    }

    private void writeWeightSummaryRows(SQLiteDatabase database, Writer writer) throws IOException {
        try (Cursor cursor = database.rawQuery(
                "SELECT date, weight_kg FROM weight_records " +
                        "WHERE user_id = ? AND deleted_at IS NULL " +
                        "ORDER BY date ASC, created_at ASC",
                new String[]{recordUserId}
        )) {
            while (cursor.moveToNext()) {
                writeCsvRow(
                        writer,
                        "\uCCB4\uC911",
                        cursor.getString(0),
                        null,
                        null,
                        null,
                        null,
                        nullableDouble(cursor, 1),
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }
        }
    }

    private static Iterable<String> jsonKeys(JSONObject object) {
        List<String> keys = new ArrayList<>();
        for (java.util.Iterator<String> iterator = object.keys(); iterator.hasNext(); ) {
            keys.add(iterator.next());
        }
        return keys;
    }

    private static List<String> createTableOrder() {
        List<String> tables = new ArrayList<>();
        tables.add("workout_records");
        tables.add("workout_exercises");
        tables.add("workout_sets");
        tables.add("meal_records");
        tables.add("weight_records");
        tables.add("routines");
        tables.add("routine_exercises");
        tables.add("cardio_sessions");
        tables.add("cardio_route_points");
        tables.add("meal_menu_presets");
        tables.add("nutrition_foods");
        tables.add("nutrition_food_nutrients");
        tables.add("nutrition_food_components");
        tables.add("meal_record_items");
        tables.add("meal_record_item_nutrients");
        tables.add("meal_record_item_components");
        tables.add("meal_record_item_component_nutrients");
        tables.add("product_nutrition_links");
        tables.add("nutrition_goals");
        tables.add("nutrition_daily_checkins");
        tables.add("body_profiles");
        tables.add("development_goals");
        return Collections.unmodifiableList(tables);
    }

    private static Set<String> createPublicScopeTables() {
        Set<String> tables = new LinkedHashSet<>();
        tables.add("nutrition_foods");
        tables.add("nutrition_food_nutrients");
        tables.add("nutrition_food_components");
        return Collections.unmodifiableSet(tables);
    }

    public static final class BackupPreview {
        private final int totalRows;
        private final String exportedAt;
        private final int databaseVersion;

        public BackupPreview(int totalRows, String exportedAt, int databaseVersion) {
            this.totalRows = totalRows;
            this.exportedAt = exportedAt;
            this.databaseVersion = databaseVersion;
        }

        public int getTotalRows() {
            return totalRows;
        }

        public String getExportedAt() {
            return exportedAt;
        }

        public int getDatabaseVersion() {
            return databaseVersion;
        }
    }

    public static final class RestoreResult {
        private final int importedRows;
        private final int skippedRows;

        public RestoreResult(int importedRows, int skippedRows) {
            this.importedRows = importedRows;
            this.skippedRows = skippedRows;
        }

        public int getImportedRows() {
            return importedRows;
        }

        public int getSkippedRows() {
            return skippedRows;
        }
    }

    private static final class ExportPlan {
        private final Map<String, List<JSONObject>> rowsByTable;
        private final int totalRows;

        private ExportPlan(Map<String, List<JSONObject>> rowsByTable, int totalRows) {
            this.rowsByTable = rowsByTable;
            this.totalRows = totalRows;
        }
    }

    private static final class BackupDocument {
        private final String exportedAt;
        private final int databaseVersion;
        private final Map<String, JSONArray> tables;

        private BackupDocument(String exportedAt, int databaseVersion, Map<String, JSONArray> tables) {
            this.exportedAt = exportedAt;
            this.databaseVersion = databaseVersion;
            this.tables = tables;
        }
    }

    private static final class TableSchema {
        private final String tableName;
        private final String ownershipColumn;
        private final boolean injectsUserId;
        private final boolean injectsOwnerId;
        private final boolean supportsOwnerScopeMarker;
        private final List<String> exportColumns;
        private final Set<String> allowedBackupColumns;
        private final List<String> requiredBackupColumns;
        private final Map<String, ColumnInfo> columnsByName;

        private TableSchema(
                String tableName,
                String ownershipColumn,
                boolean injectsUserId,
                boolean injectsOwnerId,
                boolean supportsOwnerScopeMarker,
                List<String> exportColumns,
                Set<String> allowedBackupColumns,
                List<String> requiredBackupColumns,
                Map<String, ColumnInfo> columnsByName
        ) {
            this.tableName = tableName;
            this.ownershipColumn = ownershipColumn;
            this.injectsUserId = injectsUserId;
            this.injectsOwnerId = injectsOwnerId;
            this.supportsOwnerScopeMarker = supportsOwnerScopeMarker;
            this.exportColumns = exportColumns;
            this.allowedBackupColumns = allowedBackupColumns;
            this.requiredBackupColumns = requiredBackupColumns;
            this.columnsByName = columnsByName;
        }
    }

    private static final class ColumnInfo {
        private final String name;
        private final String declaredType;
        private final boolean notNull;
        private final String defaultValue;
        private final int primaryKeyOrder;

        private ColumnInfo(
                String name,
                String declaredType,
                boolean notNull,
                String defaultValue,
                int primaryKeyOrder
        ) {
            this.name = name;
            this.declaredType = declaredType == null ? "" : declaredType;
            this.notNull = notNull;
            this.defaultValue = defaultValue;
            this.primaryKeyOrder = primaryKeyOrder;
        }

        private boolean isRequiredForBackup() {
            return primaryKeyOrder > 0 || (notNull && defaultValue == null);
        }

        private String affinity() {
            String upper = declaredType.toUpperCase(Locale.US);
            if (upper.contains("INT")) {
                return "INTEGER";
            }
            if (upper.contains("REAL") || upper.contains("FLOA") || upper.contains("DOUB")) {
                return "REAL";
            }
            if (upper.contains("CHAR") || upper.contains("CLOB") || upper.contains("TEXT")) {
                return "TEXT";
            }
            return "NUMERIC";
        }
    }

    private static final class PublicNutritionClosure {
        private final Set<String> publicFoodIds;
        private final Set<String> publicComponentIds;

        private PublicNutritionClosure(Set<String> publicFoodIds, Set<String> publicComponentIds) {
            this.publicFoodIds = publicFoodIds;
            this.publicComponentIds = publicComponentIds;
        }
    }

    private static final class NutritionFoodRef {
        private final String ownerId;
        private final boolean active;

        private NutritionFoodRef(String ownerId, boolean active) {
            this.ownerId = ownerId;
            this.active = active;
        }

        private static NutritionFoodRef missing() {
            return new NutritionFoodRef("__missing__", false);
        }
    }
}
