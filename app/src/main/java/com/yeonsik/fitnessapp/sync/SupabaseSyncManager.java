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
    static final List<String> TABLES = Arrays.asList(
            "devices",
            "workout_records",
            "workout_exercises",
            "workout_sets",
            "meal_records",
            "meal_record_items",
            "meal_record_item_nutrients",
            "weight_records"
    );

    private final FitnessDatabaseHelper dbHelper;

    public SupabaseSyncManager(FitnessDatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public SyncResult manualSync(SupabaseConfig config) throws Exception {
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
        List<String> columns = tableColumns(database, table);
        JSONArray rows = new JSONArray();
        String sql = "devices".equals(table)
                ? "SELECT * FROM devices WHERE id = ? AND user_id = ?"
                : "SELECT * FROM " + table + " WHERE device_id = ? AND user_id = ?";

        try (Cursor cursor = database.rawQuery(
                sql,
                new String[]{ANDROID_DEVICE_ID, userId}
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

    private int applyRows(
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
                }

                database.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE);
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
     * The shared Personal OS database is owned outside this repository. Keep additive local
     * snapshot fields out of its payload until that separately managed contract is migrated.
     */
    static boolean shouldSyncColumn(String table, String column) {
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
