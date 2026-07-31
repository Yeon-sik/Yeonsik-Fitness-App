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
import java.util.List;
import java.util.Set;

public final class SupabaseSyncManager {
    private static final String ANDROID_DEVICE_ID = "android-local";
    private static final List<String> TABLES = Arrays.asList(
            "devices",
            "workout_records",
            "workout_exercises",
            "workout_sets",
            "meal_records",
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
        updateLocalUserId(database, config.effectiveUserId());

        int pushedRows = 0;
        int pulledRows = 0;

        for (String table : TABLES) {
            pushedRows += pushTable(database, table, config);
        }

        for (String table : TABLES) {
            pulledRows += pullTable(database, table, config);
        }

        FitnessRepository repository = new FitnessRepository(dbHelper, config.effectiveUserId());
        if (repository.reconcileSharedWorkoutSummaries() > 0) {
            pushedRows += pushTable(database, "workout_records", config);
        }

        return new SyncResult(pushedRows, pulledRows, OffsetDateTime.now().toString());
    }

    private int pushTable(SQLiteDatabase database, String table, SupabaseConfig config) throws Exception {
        JSONArray payload = tableRowsToJson(database, table);

        if (payload.length() == 0) {
            return 0;
        }

        String endpoint = joinUrl(config.supabaseUrl,
                "/rest/v1/" + table + "?on_conflict=" + conflictTarget(table));
        HttpURLConnection connection = openConnection(endpoint, "POST", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");
        connection.setDoOutput(true);

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        readResponseOrThrow(connection, 200, 201, 204);
        return payload.length();
    }

    private int pullTable(SQLiteDatabase database, String table, SupabaseConfig config) throws Exception {
        String queryUserId = URLEncoder.encode(config.effectiveUserId(), StandardCharsets.UTF_8.name());
        String endpoint = joinUrl(config.supabaseUrl,
                "/rest/v1/" + table + "?user_id=eq." + queryUserId + "&select=*");
        HttpURLConnection connection = openConnection(endpoint, "GET", config);
        connection.setRequestProperty("Accept", "application/json");

        String body = readResponseOrThrow(connection, 200);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        applyRows(database, table, rows);
        return rows.length();
    }

    private JSONArray tableRowsToJson(SQLiteDatabase database, String table) throws JSONException {
        List<String> columns = tableColumns(database, table);
        JSONArray rows = new JSONArray();
        String sql = "devices".equals(table)
                ? "SELECT * FROM devices WHERE id = ?"
                : "SELECT * FROM " + table + " WHERE device_id = ?";

        try (Cursor cursor = database.rawQuery(sql, new String[]{ANDROID_DEVICE_ID})) {
            while (cursor.moveToNext()) {
                JSONObject object = new JSONObject();
                for (String column : columns) {
                    int index = cursor.getColumnIndexOrThrow(column);
                    if (cursor.isNull(index)) {
                        object.put(column, JSONObject.NULL);
                        continue;
                    }

                    if ("is_completed".equals(column)) {
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

    private void applyRows(SQLiteDatabase database, String table, JSONArray rows) throws JSONException {
        Set<String> localColumns = new HashSet<>(tableColumns(database, table));
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject object = rows.getJSONObject(index);
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
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private void updateLocalUserId(SQLiteDatabase database, String userId) {
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        for (String table : TABLES) {
            database.update(table, values, null, null);
        }
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
