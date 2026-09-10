package com.yeonsik.fitnessapp.integration.personalos;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.sync.LegacyFitnessSyncStore;
import com.yeonsik.fitnessapp.sync.LegacySyncCursor;
import com.yeonsik.fitnessapp.sync.LegacySyncRow;
import com.yeonsik.fitnessapp.sync.RoomLegacyFitnessSyncStore;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy Fitness sync protocol adapter.
 *
 * <p>HTTP/RPC policy and wire mapping stay here. Local persistence is supplied through
 * {@link LegacyFitnessSyncStore}, so this integration boundary does not open SQLite directly.</p>
 */
public final class LegacyFitnessSyncAdapter {
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
            "meal_records",
            "weight_records"
    );

    private final LegacyFitnessSyncStore store;

    public LegacyFitnessSyncAdapter(FitnessRoomDatabase roomDatabase) {
        this(new RoomLegacyFitnessSyncStore(roomDatabase));
    }

    public LegacyFitnessSyncAdapter(LegacyFitnessSyncStore store) {
        if (store == null) {
            throw new IllegalArgumentException("Legacy sync store is required.");
        }
        this.store = store;
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
        int pushedRows = 0;
        int pulledRows = 0;
        Map<String, JSONArray> remoteRows = new LinkedHashMap<>();

        for (String table : TABLES) {
            JSONArray rows = fetchTable(table, config);
            remoteRows.put(table, rows);
            pulledRows += applyRows(table, rows, config.effectiveUserId());
        }

        for (String table : TABLES) {
            pushedRows += pushTable(table, config, remoteRows.get(table));
        }

        // A conditional PATCH can lose a race without overwriting either side. Pull once more so
        // this device immediately observes the winning remote row.
        for (String table : TABLES) {
            JSONArray rows = fetchTable(table, config);
            pulledRows += applyRows(table, rows, config.effectiveUserId());
        }

        return new SyncResult(pushedRows, pulledRows, OffsetDateTime.now().toString());
    }

    private SyncResult manualSyncRpc(SupabaseConfig config) throws Exception {
        String userId = config.effectiveUserId();
        String scopeKey = config.supabaseUrl + "|" + userId;
        Map<String, LegacySyncCursor> pullCursors = loadPullCursors(scopeKey);
        int pushedRows = 0;
        int pulledRows = 0;
        int rpcCalls = 0;
        String syncedAt = OffsetDateTime.now().toString();

        // Parent tables are fully uploaded before their children. This keeps the first
        // bootstrap safe even when the remote database has no Fitness rows yet.
        for (String table : TABLES) {
            while (true) {
                LegacySyncCursor pushCursor = loadCursor(scopeKey, table, PUSH_DIRECTION);
                JSONArray changes = tableRowsToJson(table, userId, pushCursor, RPC_BATCH_SIZE);
                if (changes.length() == 0) {
                    break;
                }

                JSONObject payload = new JSONObject();
                payload.put(table, changes);
                RpcResponse response = invokeSyncRpc(config, payload, pullCursors);
                rpcCalls = checkedRpcCalls(rpcCalls + 1);
                pulledRows += applyRpcResponse(response, userId);
                savePullCursors(scopeKey, pullCursors, response.nextCursors, userId);

                LegacySyncCursor nextPushCursor = cursorFromLastRow(table, changes);
                saveCursor(scopeKey, table, PUSH_DIRECTION, nextPushCursor);
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
            pulledRows += applyRpcResponse(response, userId);
            savePullCursors(scopeKey, pullCursors, response.nextCursors, userId);
            pushedRows += response.pushedRows;
            syncedAt = response.serverTime;
            hasMore = response.hasMore();
        } while (hasMore);

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
            Map<String, LegacySyncCursor> pullCursors
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
        return statusCode == 404 && body != null && body.contains("PGRST202");
    }

    private int applyRpcResponse(RpcResponse response, String userId) throws JSONException {
        int applied = 0;
        for (String table : TABLES) {
            JSONArray merged = new JSONArray();
            JSONArray echo = response.echoRows.optJSONArray(table);
            if (echo != null) {
                appendRows(merged, echo);
            }
            JSONArray rows = response.rows.optJSONArray(table);
            if (rows != null) {
                appendRows(merged, rows);
            }
            if (merged.length() > 0) {
                applied += applyRows(table, merged, userId);
            }
        }
        return applied;
    }

    private void appendRows(JSONArray target, JSONArray source) throws JSONException {
        for (int index = 0; index < source.length(); index++) {
            target.put(source.get(index));
        }
    }

    private Map<String, LegacySyncCursor> loadPullCursors(String scopeKey) {
        Map<String, LegacySyncCursor> cursors = new LinkedHashMap<>();
        for (String table : TABLES) {
            LegacySyncCursor cursor = loadCursor(scopeKey, table, PULL_DIRECTION);
            if (cursor.getVersion() != null) {
                cursors.put(table, cursor);
            }
        }
        return cursors;
    }

    private LegacySyncCursor loadCursor(String scopeKey, String table, String direction) {
        return store.cursor(scopeKey, table, direction);
    }

    private void saveCursor(
            String scopeKey,
            String table,
            String direction,
            LegacySyncCursor cursor
    ) {
        store.saveCursor(scopeKey, table, direction, cursor);
    }

    private void savePullCursors(
            String scopeKey,
            Map<String, LegacySyncCursor> pullCursors,
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
            LegacySyncCursor cursor = new LegacySyncCursor(version, object.optString("id", ""));
            if (!store.hasRow(table, cursor.getId(), userId)) {
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
            LegacySyncCursor cursor = new LegacySyncCursor(version, object.optString("id", ""));
            saveCursor(scopeKey, table, PULL_DIRECTION, cursor);
            pullCursors.put(table, cursor);
        }
    }

    private JSONObject cursorsToJson(Map<String, LegacySyncCursor> cursors) throws JSONException {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, LegacySyncCursor> entry : cursors.entrySet()) {
            JSONObject cursor = new JSONObject();
            cursor.put("version", entry.getValue().getVersion());
            cursor.put("id", entry.getValue().getId());
            object.put(entry.getKey(), cursor);
        }
        return object;
    }

    private LegacySyncCursor cursorFromLastRow(String table, JSONArray rows) throws JSONException {
        JSONObject last = rows.getJSONObject(rows.length() - 1);
        return new LegacySyncCursor(
                nullableString(last, versionColumn(table)),
                last.optString("id", "")
        );
    }

    private int pushTable(String table, SupabaseConfig config, JSONArray remoteRows) throws Exception {
        JSONArray payload = tableRowsToJson(table, config.effectiveUserId());
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
                pushed += patchIfUnchanged(table, local, remoteVersion, versionColumn, config);
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

    private JSONArray tableRowsToJson(String table, String userId) throws JSONException {
        return tableRowsToJson(table, userId, LegacySyncCursor.empty(), 0);
    }

    private JSONArray tableRowsToJson(
            String table,
            String userId,
            LegacySyncCursor cursor,
            int limit
    ) throws JSONException {
        JSONArray rows = new JSONArray();
        List<LegacySyncRow> localRows = store.rows(
                table,
                userId,
                ANDROID_DEVICE_ID,
                cursor,
                limit
        );
        for (LegacySyncRow row : localRows) {
            JSONObject object = new JSONObject();
            for (String column : row.columns()) {
                if (!shouldSyncColumn(table, column)) {
                    continue;
                }
                putJsonValue(object, column, row.value(column));
            }
            rows.put(object);
        }
        return rows;
    }

    /** Pulls newer remote values while preserving omitted local-only columns in the store. */
    int applyRows(String table, JSONArray rows, String userId) throws JSONException {
        List<LegacySyncRow> accepted = new ArrayList<>();
        Map<String, String> acceptedVersions = new LinkedHashMap<>();
        String versionColumn = versionColumn(table);
        for (int index = 0; index < rows.length(); index++) {
            JSONObject object = rows.getJSONObject(index);
            String id = object.optString("id", "");
            if (id.isEmpty()) {
                continue;
            }
            String remoteVersion = nullableString(object, versionColumn);
            String localVersion = store.localVersion(table, id, userId, versionColumn);
            if (localVersion != null && compareVersions(remoteVersion, localVersion) <= 0) {
                continue;
            }
            if (acceptedVersions.containsKey(id)
                    && compareVersions(remoteVersion, acceptedVersions.get(id)) <= 0) {
                continue;
            }
            acceptedVersions.put(id, remoteVersion);
            accepted.add(fromJson(object));
        }
        store.applyRows(table, accepted, userId);
        return accepted.size();
    }

    private LegacySyncRow fromJson(JSONObject object) throws JSONException {
        Map<String, Object> values = new LinkedHashMap<>();
        JSONArray names = object.names();
        if (names == null) {
            return new LegacySyncRow(values);
        }
        for (int index = 0; index < names.length(); index++) {
            String name = names.getString(index);
            Object value = object.get(name);
            if (value == JSONObject.NULL) {
                value = null;
            } else if (value instanceof JSONObject || value instanceof JSONArray) {
                value = value.toString();
            }
            values.put(name, value);
        }
        return new LegacySyncRow(values);
    }

    private int insertIfAbsent(String table, JSONObject row, SupabaseConfig config) throws Exception {
        String endpoint = joinUrl(
                config.supabaseUrl,
                "/rest/v1/" + table + "?on_conflict=" + conflictTarget(table)
        );
        HttpURLConnection connection = openConnection(endpoint, "POST", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "resolution=ignore-duplicates,return=representation");
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

    /** Keeps local-only columns out of the shared payload until their remote schema is verified. */
    static boolean shouldSyncColumn(String table, String column) {
        if ("contract_version".equals(column)) {
            return false;
        }
        if ("meal_records".equals(table)
                && "nutrition_calculation_contract".equals(column)) {
            return false;
        }
        if ("workout_records".equals(table) && "total_volume_kg".equals(column)) {
            return false;
        }
        if ("workout_sets".equals(table)
                && ("volume_kg".equals(column) || "rir".equals(column))) {
            return false;
        }
        if ("workout_exercises".equals(table)
                && ("family_id".equals(column)
                || "preset_id".equals(column)
                || "canonical_variant_key".equals(column)
                || "visual_variant_key".equals(column))) {
            return false;
        }
        if ("workout_sets".equals(table)
                && ("load_state".equals(column)
                || "input_load_value".equals(column)
                || "input_load_unit".equals(column))) {
            return false;
        }
        if ("meal_records".equals(table)
                && ("meal_kind".equals(column)
                || "store_name".equals(column)
                || "branch_name".equals(column)
                || "menu_name".equals(column)
                || "restaurant_id".equals(column)
                || "restaurant_location_id".equals(column)
                || "restaurant_menu_id".equals(column)
                || "catalog_product_id".equals(column)
                || "composition_template_id".equals(column)
                || "composition_template_revision".equals(column))) {
            return false;
        }
        return !("meal_record_items".equals(table) && "brand_snapshot".equals(column));
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

    private static String conflictTarget(String table) {
        return "devices".equals(table) ? "user_id,id" : "id";
    }

    private static void putJsonValue(JSONObject object, String name, Object value) throws JSONException {
        if (value == null) {
            object.put(name, JSONObject.NULL);
            return;
        }
        if ("metadata".equals(name) && value instanceof String) {
            try {
                object.put(name, new JSONObject((String) value));
                return;
            } catch (JSONException ignored) {
                // Preserve older non-JSON metadata as a scalar string.
            }
        }
        object.put(name, value);
    }

    private HttpURLConnection openConnection(String endpoint, String method, SupabaseConfig config)
            throws IOException {
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
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
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
