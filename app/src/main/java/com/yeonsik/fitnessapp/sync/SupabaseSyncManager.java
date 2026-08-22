package com.yeonsik.fitnessapp.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SupabaseSyncManager {
    private static final String ANDROID_DEVICE_ID = "android-local";
    private static final int PAGE_SIZE = 500;
    private static final int RPC_BATCH_SIZE = 500;
    private static final int RPC_CONTRACT_VERSION = 1;
    private static final int MAX_RPC_CALLS = 1000;
    private static final String PULL_DIRECTION = "pull";
    private static final String PUSH_DIRECTION = "push";
    static final List<String> TABLES = Arrays.asList(
            "devices",
            "workout_records",
            "workout_exercises",
            "workout_sets",
            // The shared Personal OS project currently owns the legacy meal summary only.
            // Detailed meal snapshots remain local until their separately-owned remote
            // contract is deployed; requesting those tables makes every sync fail with 404.
            "meal_records",
            "weight_records"
    );

    private final FitnessDatabaseHelper dbHelper;

    public SupabaseSyncManager(FitnessDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public SyncResult manualSync(SupabaseConfig config) throws Exception {
        if (!config.isConfigured()) {
            throw new IllegalStateException("Supabase configuration is empty.");
        }
        try {
            return manualSyncRpc(config);
        } catch (RpcUnavailableException unavailable) {
            return manualSyncLegacy(config);
        }
    }

    private SyncResult manualSyncLegacy(SupabaseConfig config) throws Exception {
        if (!config.isConfigured()) {
            throw new IllegalStateException("Supabase 설정이 비어 있습니다.");
        }

        SQLiteDatabase database = dbHelper.getWritableDatabase();
        int pushedRows = 0;
        int pulledRows = 0;
        Map<String, JSONArray> remoteRows = new LinkedHashMap<>();

        for (String table : TABLES) {
            JSONArray rows = fetchTable(table, config);
            remoteRows.put(table, rows);
            pulledRows += applyRows(database, table, rows, config.effectiveUserId());
        }

        FitnessRepository repository = new FitnessRepository(dbHelper, config.effectiveUserId());
        repository.reconcileSharedWorkoutSummaries();

        for (String table : TABLES) {
            pushedRows += pushTable(database, table, config, remoteRows.get(table));
        }

        // A conditional PATCH can lose a race without overwriting either side. Pull once more so
        // this device immediately observes the winning remote row.
        for (String table : TABLES) {
            JSONArray rows = fetchTable(table, config);
            pulledRows += applyRows(database, table, rows, config.effectiveUserId());
        }

        return new SyncResult(pushedRows, pulledRows, OffsetDateTime.now().toString());
    }

    private SyncResult manualSyncRpc(SupabaseConfig config) throws Exception {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        String userId = config.effectiveUserId();
        String scopeKey = config.supabaseUrl + "|" + userId;
        FitnessRepository repository = new FitnessRepository(dbHelper, userId);
        repository.reconcileSharedWorkoutSummaries();

        Map<String, SyncCursor> pullCursors = loadPullCursors(database, scopeKey);
        int pushedRows = 0;
        int pulledRows = 0;
        int rpcCalls = 0;
        String syncedAt = OffsetDateTime.now().toString();

        // Parent tables are fully uploaded before their children. This keeps the first
        // bootstrap safe even when the remote database has no Fitness rows yet.
        for (String table : TABLES) {
            while (true) {
                SyncCursor pushCursor = loadCursor(
                        database,
                        scopeKey,
                        table,
                        PUSH_DIRECTION
                );
                JSONArray changes = tableRowsToJson(
                        database,
                        table,
                        userId,
                        pushCursor,
                        RPC_BATCH_SIZE
                );
                if (changes.length() == 0) {
                    break;
                }

                JSONObject payload = new JSONObject();
                payload.put(table, changes);
                RpcResponse response = invokeSyncRpc(config, payload, pullCursors);
                rpcCalls = checkedRpcCalls(rpcCalls + 1);
                pulledRows += applyRpcResponse(database, response, userId);
                savePullCursors(
                        database,
                        scopeKey,
                        pullCursors,
                        response.nextCursors,
                        userId
                );

                SyncCursor nextPushCursor = cursorFromLastRow(table, changes);
                saveCursor(
                        database,
                        scopeKey,
                        table,
                        PUSH_DIRECTION,
                        nextPushCursor
                );
                pushedRows += response.pushedRows;
                syncedAt = response.serverTime;
            }
        }

        // Drain remote keyset pages after all local batches have committed. At least one
        // empty call is required when this device has no local changes.
        boolean hasMore;
        do {
            RpcResponse response = invokeSyncRpc(config, new JSONObject(), pullCursors);
            rpcCalls = checkedRpcCalls(rpcCalls + 1);
            pulledRows += applyRpcResponse(database, response, userId);
            savePullCursors(
                    database,
                    scopeKey,
                    pullCursors,
                    response.nextCursors,
                    userId
            );
            pushedRows += response.pushedRows;
            syncedAt = response.serverTime;
            hasMore = response.hasMore();
        } while (hasMore);

        repository.reconcileSharedWorkoutSummaries();
        return new SyncResult(pushedRows, pulledRows, syncedAt);
    }

    private int checkedRpcCalls(int rpcCalls) throws IOException {
        if (rpcCalls > MAX_RPC_CALLS) {
            throw new IOException("Supabase sync stopped: RPC pagination did not converge.");
        }
        return rpcCalls;
    }

    private RpcResponse invokeSyncRpc(
            SupabaseConfig config,
            JSONObject changes,
            Map<String, SyncCursor> pullCursors
    ) throws Exception {
        String endpoint = joinUrl(config.supabaseUrl, "/rest/v1/rpc/sync_fitness_data_v1");
        HttpURLConnection connection = openConnection(endpoint, "POST", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        JSONObject request = new JSONObject();
        request.put("p_changes", changes);
        request.put("p_cursors", cursorsToJson(pullCursors));
        request.put("p_limit", RPC_BATCH_SIZE);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        if (statusCode == 200) {
            String body = readStream(connection.getInputStream());
            return RpcResponse.fromJson(new JSONObject(body));
        }

        String error = readStream(connection.getErrorStream());
        if (isRpcUnavailable(statusCode, error)) {
            throw new RpcUnavailableException();
        }
        throw new IOException("Supabase sync RPC failed (" + statusCode + "): " + error);
    }

    static boolean isRpcUnavailable(int statusCode, String body) {
        if (statusCode != 404 || body == null) {
            return false;
        }
        return body.contains("PGRST202");
    }

    private int applyRpcResponse(
            SQLiteDatabase database,
            RpcResponse response,
            String userId
    ) throws JSONException {
        int applied = 0;
        for (String table : TABLES) {
            JSONArray echo = response.echoRows.optJSONArray(table);
            if (echo != null) {
                applied += applyRows(database, table, echo, userId);
            }
            JSONArray rows = response.rows.optJSONArray(table);
            if (rows != null) {
                applied += applyRows(database, table, rows, userId);
            }
        }
        return applied;
    }

    private Map<String, SyncCursor> loadPullCursors(
            SQLiteDatabase database,
            String scopeKey
    ) {
        Map<String, SyncCursor> cursors = new LinkedHashMap<>();
        for (String table : TABLES) {
            SyncCursor cursor = loadCursor(database, scopeKey, table, PULL_DIRECTION);
            if (cursor.version != null) {
                cursors.put(table, cursor);
            }
        }
        return cursors;
    }

    private SyncCursor loadCursor(
            SQLiteDatabase database,
            String scopeKey,
            String table,
            String direction
    ) {
        try (Cursor cursor = database.query(
                "sync_state",
                new String[]{"cursor_version", "cursor_id"},
                "scope_key = ? AND table_name = ? AND direction = ?",
                new String[]{scopeKey, table, direction},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                String version = cursor.isNull(0) ? null : cursor.getString(0);
                String id = cursor.isNull(1) ? "" : cursor.getString(1);
                return new SyncCursor(version, id);
            }
        }
        return SyncCursor.empty();
    }

    private void saveCursor(
            SQLiteDatabase database,
            String scopeKey,
            String table,
            String direction,
            SyncCursor cursor
    ) {
        if (cursor == null || cursor.version == null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("scope_key", scopeKey);
        values.put("table_name", table);
        values.put("direction", direction);
        values.put("cursor_version", cursor.version);
        values.put("cursor_id", cursor.id);
        values.put("updated_at", OffsetDateTime.now().toString());
        database.insertWithOnConflict(
                "sync_state",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    private void savePullCursors(
            SQLiteDatabase database,
            String scopeKey,
            Map<String, SyncCursor> pullCursors,
            JSONObject nextCursors,
            String userId
    ) throws RpcUnavailableException {
        // A cursor is only safe after the row at its boundary is present locally.
        // Otherwise a malformed/partial RPC response can permanently skip rows.
        for (String table : TABLES) {
            JSONObject object = nextCursors.optJSONObject(table);
            if (object == null) {
                continue;
            }
            String version = nullableString(object, "version");
            if (version == null) {
                continue;
            }
            SyncCursor cursor = new SyncCursor(version, object.optString("id", ""));
            if (!isPullCursorApplied(database, table, userId, cursor)) {
                throw new RpcCursorMismatchException();
            }
        }

        for (String table : TABLES) {
            JSONObject object = nextCursors.optJSONObject(table);
            if (object == null) {
                continue;
            }
            String version = nullableString(object, "version");
            if (version == null) {
                continue;
            }
            SyncCursor cursor = new SyncCursor(version, object.optString("id", ""));
            saveCursor(database, scopeKey, table, PULL_DIRECTION, cursor);
            pullCursors.put(table, cursor);
        }
    }

    private boolean isPullCursorApplied(
            SQLiteDatabase database,
            String table,
            String userId,
            SyncCursor cursor
    ) {
        if (cursor == null || cursor.version == null || cursor.id.isEmpty()) {
            return false;
        }
        try (Cursor rows = database.query(
                table,
                new String[]{"id"},
                "id = ? AND user_id = ?",
                new String[]{cursor.id, userId},
                null,
                null,
                null,
                "1"
        )) {
            return rows.moveToFirst();
        }
    }

    private JSONObject cursorsToJson(Map<String, SyncCursor> cursors) throws JSONException {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, SyncCursor> entry : cursors.entrySet()) {
            JSONObject cursor = new JSONObject();
            cursor.put("version", entry.getValue().version);
            cursor.put("id", entry.getValue().id);
            object.put(entry.getKey(), cursor);
        }
        return object;
    }

    private SyncCursor cursorFromLastRow(String table, JSONArray rows) throws JSONException {
        JSONObject last = rows.getJSONObject(rows.length() - 1);
        return new SyncCursor(
                nullableString(last, versionColumn(table)),
                last.optString("id", "")
        );
    }


    private int pushTable(
            SQLiteDatabase database,
            String table,
            SupabaseConfig config,
            JSONArray remoteRows
    ) throws Exception {
        JSONArray payload = tableRowsToJson(
                database,
                table,
                config.effectiveUserId()
        );

        Map<String, JSONObject> remoteById = rowsById(remoteRows);
        int pushed = 0;
        String versionColumn = versionColumn(table);
        for (int index = 0; index < payload.length(); index++) {
            JSONObject local = payload.getJSONObject(index);
            String id = local.optString("id", "");
            if (id.isEmpty()) {
                continue;
            }
            JSONObject remote = remoteById.get(id);
            if (remote == null) {
                pushed += insertIfAbsent(table, local, config);
                continue;
            }
            String localVersion = nullableString(local, versionColumn);
            String remoteVersion = nullableString(remote, versionColumn);
            if (compareVersions(localVersion, remoteVersion) > 0) {
                pushed += patchIfUnchanged(
                        table,
                        local,
                        remoteVersion,
                        versionColumn,
                        config
                );
            }
        }
        return pushed;
    }

    private JSONArray fetchTable(String table, SupabaseConfig config) throws Exception {
        String queryUserId = URLEncoder.encode(config.effectiveUserId(), StandardCharsets.UTF_8.name());
        JSONArray allRows = new JSONArray();
        int offset = 0;

        while (true) {
            String endpoint = joinUrl(config.supabaseUrl,
                    "/rest/v1/" + table
                            + "?user_id=eq." + queryUserId
                            + "&select=*"
                            + "&order=id.asc"
                            + "&limit=" + PAGE_SIZE
                            + "&offset=" + offset);
            HttpURLConnection connection = openConnection(endpoint, "GET", config);
            connection.setRequestProperty("Accept", "application/json");

            String body = readResponseOrThrow(connection, 200, 206);
            JSONArray page = body.isEmpty() ? new JSONArray() : new JSONArray(body);
            for (int index = 0; index < page.length(); index++) {
                allRows.put(page.get(index));
            }
            if (page.length() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }
        return allRows;
    }

    private JSONArray tableRowsToJson(
            SQLiteDatabase database,
            String table,
            String userId
    ) throws JSONException {
        return tableRowsToJson(database, table, userId, SyncCursor.empty(), 0);
    }

    private JSONArray tableRowsToJson(
            SQLiteDatabase database,
            String table,
            String userId,
            SyncCursor syncCursor,
            int limit
    ) throws JSONException {
        List<String> columns = tableColumns(database, table);
        JSONArray rows = new JSONArray();
        String versionColumn = versionColumn(table);
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table).append(" WHERE ");
        List<String> arguments = new ArrayList<>();
        if ("devices".equals(table)) {
            sql.append("id = ? AND user_id = ?");
            arguments.add(ANDROID_DEVICE_ID);
            arguments.add(userId);
        } else {
            sql.append("device_id = ? AND user_id = ?");
            arguments.add(ANDROID_DEVICE_ID);
            arguments.add(userId);
        }
        if (syncCursor != null && syncCursor.version != null) {
            sql.append(" AND (").append(versionColumn).append(" > ? OR (")
                    .append(versionColumn).append(" = ? AND id > ?))");
            arguments.add(syncCursor.version);
            arguments.add(syncCursor.version);
            arguments.add(syncCursor.id);
        }
        sql.append(" ORDER BY ").append(versionColumn).append(", id");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }

        try (Cursor cursor = database.rawQuery(
                sql.toString(),
                arguments.toArray(new String[0])
        )) {
            while (cursor.moveToNext()) {
                JSONObject object = new JSONObject();
                for (String column : columns) {
                    if (!shouldSyncColumn(table, column)) {
                        continue;
                    }
                    int index = cursor.getColumnIndexOrThrow(column);
                    if (cursor.isNull(index)) {
                        object.put(column, JSONObject.NULL);
                        continue;
                    }

                    if ("is_completed".equals(column) || "is_backfilled".equals(column)) {
                        object.put(column, cursor.getInt(index) == 1);
                        continue;
                    }

                    if ("metadata".equals(column) && cursor.getType(index) == Cursor.FIELD_TYPE_STRING) {
                        object.put(column, new JSONObject(cursor.getString(index)));
                        continue;
                    }

                    switch (cursor.getType(index)) {
                        case Cursor.FIELD_TYPE_INTEGER:
                            object.put(column, cursor.getLong(index));
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            object.put(column, cursor.getDouble(index));
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            object.put(column, cursor.getString(index));
                            break;
                        default:
                            object.put(column, cursor.getString(index));
                            break;
                    }
                }
                rows.put(object);
            }
        }

        return rows;
    }

    /**
     * Pulls newer remote values without replacing the whole local row.
     * Remote schemas can lag behind the local SQLite schema; omitted local columns must survive.
     */
    int applyRows(
            SQLiteDatabase database,
            String table,
            JSONArray rows,
            String userId
    ) throws JSONException {
        Set<String> localColumns = new HashSet<>(tableColumns(database, table));
        String versionColumn = versionColumn(table);
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject object = rows.getJSONObject(index);
                String id = object.optString("id", "");
                if (id.isEmpty()) {
                    continue;
                }
                String remoteVersion = nullableString(object, versionColumn);
                String localVersion = localVersion(
                        database,
                        table,
                        id,
                        userId,
                        versionColumn
                );
                if (localVersion != null && compareVersions(remoteVersion, localVersion) <= 0) {
                    continue;
                }
                ContentValues values = new ContentValues();

                JSONArray names = object.names();
                if (names == null) {
                    continue;
                }

                for (int nameIndex = 0; nameIndex < names.length(); nameIndex++) {
                    String name = names.getString(nameIndex);
                    if (!localColumns.contains(name)) {
                        continue;
                    }
                    Object value = object.get(name);
                    putJsonValue(values, name, value);
                    if ("meal_records".equals(table) && "metadata".equals(name)) {
                        applyMealRecordMetadataColumns(values, value);
                    }
                }

                int updated = database.update(
                        table,
                        values,
                        "id = ? AND user_id = ?",
                        new String[]{id, userId}
                );
                if (updated == 0) {
                    long inserted = database.insertWithOnConflict(
                            table,
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_IGNORE
                    );
                    if (inserted == -1L) {
                        continue;
                    }
                }
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private String localVersion(
            SQLiteDatabase database,
            String table,
            String id,
            String userId,
            String versionColumn
    ) {
        try (Cursor cursor = database.rawQuery(
                "SELECT " + versionColumn + " FROM " + table +
                        " WHERE id = ? AND user_id = ? LIMIT 1",
                new String[]{id, userId}
        )) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getString(0);
            }
        }
        return null;
    }

    private int insertIfAbsent(
            String table,
            JSONObject row,
            SupabaseConfig config
    ) throws Exception {
        String endpoint = joinUrl(
                config.supabaseUrl,
                "/rest/v1/" + table + "?on_conflict=" + conflictTarget(table)
        );
        HttpURLConnection connection = openConnection(endpoint, "POST", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty(
                "Prefer",
                "resolution=ignore-duplicates,return=representation"
        );
        connection.setDoOutput(true);
        JSONArray payload = new JSONArray();
        payload.put(row);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200, 201);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private int patchIfUnchanged(
            String table,
            JSONObject row,
            String expectedRemoteVersion,
            String versionColumn,
            SupabaseConfig config
    ) throws Exception {
        String id = row.getString("id");
        String userId = row.getString("user_id");
        String versionFilter = expectedRemoteVersion == null
                ? "is.null"
                : "eq." + encode(expectedRemoteVersion);
        String endpoint = joinUrl(
                config.supabaseUrl,
                "/rest/v1/" + table
                        + "?id=eq." + encode(id)
                        + "&user_id=eq." + encode(userId)
                        + "&" + versionColumn + "=" + versionFilter
        );
        HttpURLConnection connection = openConnection(endpoint, "PATCH", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "return=representation");
        connection.setDoOutput(true);

        JSONObject patch = new JSONObject(row.toString());
        patch.remove("id");
        patch.remove("user_id");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(patch.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private Map<String, JSONObject> rowsById(JSONArray rows) throws JSONException {
        Map<String, JSONObject> indexed = new LinkedHashMap<>();
        if (rows == null) {
            return indexed;
        }
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String id = row.optString("id", "");
            if (!id.isEmpty()) {
                indexed.put(id, row);
            }
        }
        return indexed;
    }

    private String versionColumn(String table) {
        return "devices".equals(table) ? "last_seen_at" : "updated_at";
    }

    /**
     * Keeps locally-added columns out of the shared payload until the additive remote
     * migration is verified. The same values remain in the shared {@code metadata} JSON,
     * so this compatibility path does not discard dining-out identity data.
    */
    static boolean shouldSyncColumn(String table, String column) {
        // The deployed shared project still exposes the pre-contract-version schema.
        // Keep this local migration marker out of every REST payload until that remote
        // contract is actually deployed.
        if ("contract_version".equals(column)) {
            return false;
        }
        // The local shared-consumption allocation contract is not deployed in the remote
        // meal summary schema yet. Keep the marker local until that migration is verified.
        if ("meal_records".equals(table)
                && "nutrition_calculation_contract".equals(column)) {
            return false;
        }
        // The connected Personal OS project has not exposed this local workout
        // aggregate in its PostgREST schema cache yet. Keep manual sync usable
        // until the corresponding remote migration is deployed; the value
        // remains available in the local Fitness database.
        if ("workout_records".equals(table) && "total_volume_kg".equals(column)) {
            return false;
        }
        // The deployed shared project has not exposed this local workout-set aggregate
        // in its PostgREST schema cache. Keep it local until that migration is deployed.
        if ("workout_sets".equals(table) && "volume_kg".equals(column)) {
            return false;
        }
        // The common Personal OS dining-out identity migration is not part of the tracked
        // deployed contract yet. Keep these columns local and rely on metadata until the
        // remote schema is confirmed to expose them.
        if ("meal_records".equals(table)
                && ("meal_kind".equals(column)
                || "store_name".equals(column)
                || "branch_name".equals(column)
                || "menu_name".equals(column)
                || "restaurant_id".equals(column)
                || "restaurant_location_id".equals(column)
                || "restaurant_menu_id".equals(column)
                || "catalog_product_id".equals(column))) {
            return false;
        }
        if ("meal_record_items".equals(table) && "brand_snapshot".equals(column)) {
            return false;
        }
        // Composition template references are local definitions. The shared meal-summary
        // contract does not expose these columns yet; keep the local link out of REST payloads
        // until a separately-owned remote migration is deployed.
        if ("meal_records".equals(table)
                && ("composition_template_id".equals(column)
                || "composition_template_revision".equals(column))) {
            return false;
        }
        return true;
    }

    private static void applyMealRecordMetadataColumns(ContentValues values, Object rawMetadata) {
        if (rawMetadata == null || rawMetadata == JSONObject.NULL) {
            return;
        }
        try {
            JSONObject metadata = rawMetadata instanceof JSONObject
                    ? (JSONObject) rawMetadata
                    : new JSONObject(String.valueOf(rawMetadata));
            putOptionalMetadataValue(values, "meal_kind", metadata.optString("meal_kind", ""));
            putOptionalMetadataValue(values, "store_name", metadata.optString("store_name", ""));
            putOptionalMetadataValue(values, "branch_name", metadata.optString("branch_name", ""));
            putOptionalMetadataValue(values, "menu_name", metadata.optString("menu_name", ""));
            putOptionalMetadataValue(values, "restaurant_id", metadata.optString("restaurant_id", ""));
            putOptionalMetadataValue(
                    values,
                    "restaurant_location_id",
                    metadata.optString("restaurant_location_id", "")
            );
            putOptionalMetadataValue(
                    values,
                    "restaurant_menu_id",
                    metadata.optString("restaurant_menu_id", "")
            );
            putOptionalMetadataValue(
                    values,
                    "catalog_product_id",
                    metadata.optString("catalog_product_id", "")
            );
        } catch (JSONException ignored) {
            // Keep the legacy row usable if an older client stored non-JSON metadata.
        }
    }

    private static void putOptionalMetadataValue(
            ContentValues values,
            String column,
            String value
    ) {
        if (value == null
                || value.trim().isEmpty()
                || "null".equalsIgnoreCase(value.trim())) {
            return;
        }
        values.put(column, value.trim());
    }

    static int compareVersions(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        try {
            return OffsetDateTime.parse(left).toInstant()
                    .compareTo(OffsetDateTime.parse(right).toInstant());
        } catch (Exception ignored) {
            return left.compareTo(right);
        }
    }

    private static String nullableString(JSONObject row, String key) {
        if (row == null || !row.has(key) || row.isNull(key)) {
            return null;
        }
        String value = row.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private List<String> tableColumns(SQLiteDatabase database, String table) {
        List<String> columns = new ArrayList<>();
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
        }
        return columns;
    }

    private String conflictTarget(String table) {
        if ("devices".equals(table)) {
            return "user_id,id";
        }
        return "id";
    }

    private HttpURLConnection openConnection(String endpoint, String method, SupabaseConfig config) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", config.supabaseAnonKey);
        connection.setRequestProperty("Authorization", "Bearer " + config.accessToken);
        return connection;
    }

    private String readResponseOrThrow(HttpURLConnection connection, int... okCodes) throws IOException {
        int statusCode = connection.getResponseCode();
        for (int okCode : okCodes) {
            if (statusCode == okCode) {
                return readStream(connection.getInputStream());
            }
        }

        String error = readStream(connection.getErrorStream());
        throw new IOException("Supabase sync failed (" + statusCode + "): " + error);
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }

    private void putJsonValue(ContentValues values, String name, Object value) {
        if (value == null || value == JSONObject.NULL) {
            values.putNull(name);
            return;
        }

        if (value instanceof Boolean) {
            values.put(name, (Boolean) value ? 1 : 0);
            return;
        }

        if (value instanceof Integer) {
            values.put(name, (Integer) value);
            return;
        }

        if (value instanceof Long) {
            values.put(name, (Long) value);
            return;
        }

        if (value instanceof Double) {
            values.put(name, (Double) value);
            return;
        }

        if (value instanceof Float) {
            values.put(name, (Float) value);
            return;
        }

        if (value instanceof Number) {
            values.put(name, ((Number) value).doubleValue());
            return;
        }

        values.put(name, String.valueOf(value));
    }

    private static final class SyncCursor {
        final String version;
        final String id;

        SyncCursor(String version, String id) {
            this.version = version;
            this.id = id == null ? "" : id;
        }

        static SyncCursor empty() {
            return new SyncCursor(null, "");
        }
    }

    private static final class RpcResponse {
        final int pushedRows;
        final JSONObject rows;
        final JSONObject echoRows;
        final JSONObject nextCursors;
        final JSONObject hasMore;
        final String serverTime;

        RpcResponse(
                int pushedRows,
                JSONObject rows,
                JSONObject echoRows,
                JSONObject nextCursors,
                JSONObject hasMore,
                String serverTime
        ) {
            this.pushedRows = pushedRows;
            this.rows = rows;
            this.echoRows = echoRows;
            this.nextCursors = nextCursors;
            this.hasMore = hasMore;
            this.serverTime = serverTime;
        }

        static RpcResponse fromJson(JSONObject object) throws JSONException {
            int contractVersion = object.optInt("contract_version", 0);
            if (contractVersion != RPC_CONTRACT_VERSION) {
                throw new JSONException("Unsupported Fitness sync RPC contract: " + contractVersion);
            }
            JSONObject pushed = object.optJSONObject("pushed");
            int pushedRows = 0;
            if (pushed != null) {
                for (String table : TABLES) {
                    pushedRows += pushed.optInt(table, 0);
                }
            }
            return new RpcResponse(
                    pushedRows,
                    requireObject(object, "rows"),
                    requireObject(object, "echo_rows"),
                    requireObject(object, "next_cursors"),
                    requireObject(object, "has_more"),
                    object.optString("server_time", OffsetDateTime.now().toString())
            );
        }

        boolean hasMore() {
            for (String table : TABLES) {
                if (hasMore.optBoolean(table, false)) {
                    return true;
                }
            }
            return false;
        }

        private static JSONObject requireObject(JSONObject parent, String key) throws JSONException {
            JSONObject object = parent.optJSONObject(key);
            if (object == null) {
                throw new JSONException("Fitness sync RPC response is missing " + key);
            }
            return object;
        }
    }

    private static class RpcUnavailableException extends IOException {
    }

    private static final class RpcCursorMismatchException extends RpcUnavailableException {
        RpcCursorMismatchException() {
            super();
        }
    }


    public static final class SyncResult {
        public final int pushedRows;
        public final int pulledRows;
        public final String syncedAt;

        public SyncResult(int pushedRows, int pulledRows, String syncedAt) {
            this.pushedRows = pushedRows;
            this.pulledRows = pulledRows;
            this.syncedAt = syncedAt;
        }
    }
}
