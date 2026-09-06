package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.SupabaseConfig;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite implementation for locally owned body-weight records. */
public final class BodyMetricsRepository {
    private static final String DEVICE_ID = "android-local";

    private final FitnessDatabaseHelper dbHelper;
    private String userId;

    public BodyMetricsRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public String addBodyMetric(String date, double weightKg, String memo) {
        String recordDate = requireRecordDate(date);
        double validatedWeight = requireBodyWeight(weightKg);
        BodyMetricEntry existing = bodyMetricForDate(recordDate);
        if (existing != null) {
            updateBodyMetric(existing.id, recordDate, validatedWeight, memo);
            return existing.id;
        }

        String id = newId();
        String now = now();
        ContentValues values = baseValues(id, now);
        values.put("date", recordDate);
        values.put("weight_kg", validatedWeight);
        values.put("is_backfilled", 0);
        values.putNull("backfilled_at");
        values.putNull("backfill_reason");
        values.put("source_app", "fitness");
        values.put("scope", "fitness");
        values.put("metadata", bodyWeightMetadata(memo));
        db().insertOrThrow("weight_records", null, values);
        return id;
    }

    public BodyMetricEntry bodyMetricForDate(String date) {
        List<BodyMetricEntry> entries = bodyMetricEntriesForDate(date);
        return entries.isEmpty() ? null : entries.get(0);
    }

    public BodyMetricEntry bodyMetricEntryById(String id) {
        if (emptyToNull(id) == null) {
            return null;
        }
        String sql = "SELECT id, date, weight_kg, metadata FROM weight_records "
                + "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1";
        try (Cursor cursor = db().rawQuery(sql, new String[]{id, userId})) {
            if (cursor.moveToFirst()) {
                return bodyMetricEntry(cursor);
            }
        }
        return null;
    }

    public List<BodyMetricEntry> bodyMetricEntriesForDate(String date) {
        List<BodyMetricEntry> rows = new ArrayList<>();
        String sql = "SELECT id, date, weight_kg, metadata FROM weight_records "
                + "WHERE user_id = ? AND deleted_at IS NULL AND scope IN ('fitness', 'both')";
        String[] args = new String[]{userId};
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{userId, emptyToToday(date)};
        }
        sql += " ORDER BY date DESC, updated_at DESC LIMIT 20";
        try (Cursor cursor = db().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                rows.add(bodyMetricEntry(cursor));
            }
        }
        return rows;
    }

    public void updateBodyMetric(String id, String date, double weightKg, String memo) {
        if (emptyToNull(id) == null) {
            return;
        }
        String recordDate = requireRecordDate(date);
        double validatedWeight = requireBodyWeight(weightKg);
        ContentValues values = new ContentValues();
        values.put("date", recordDate);
        values.put("weight_kg", validatedWeight);
        values.put("metadata", bodyWeightMetadata(memo));
        values.put("updated_at", now());
        db().update(
                "weight_records",
                values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{id, userId}
        );
    }

    public void deleteBodyMetric(String id) {
        if (emptyToNull(id) == null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("deleted_at", now());
        values.put("updated_at", now());
        db().update(
                "weight_records",
                values,
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{id, userId}
        );
    }

    /** Returns the newest visible entry on or before the requested date. */
    public BodyMetricEntry latestBodyMetricOnOrBefore(String date) {
        String sql = "SELECT id, date, weight_kg, metadata FROM weight_records "
                + "WHERE user_id = ? AND deleted_at IS NULL AND scope IN ('fitness', 'both') "
                + "AND date <= ? ORDER BY date DESC, updated_at DESC LIMIT 1";
        try (Cursor cursor = db().rawQuery(sql, new String[]{userId, emptyToToday(date)})) {
            return cursor.moveToFirst() ? bodyMetricEntry(cursor) : null;
        }
    }

    public List<String> bodyMetrics() {
        return bodyMetricsForDate(null);
    }

    public List<String> bodyMetricsForDate(String date) {
        List<String> rows = new ArrayList<>();
        String sql = "SELECT date, weight_kg FROM weight_records "
                + "WHERE user_id = ? AND deleted_at IS NULL AND scope IN ('fitness', 'both')";
        String[] args = new String[]{userId};
        if (date != null) {
            sql += " AND date = ?";
            args = new String[]{userId, emptyToToday(date)};
        }
        sql += " ORDER BY date DESC LIMIT 20";
        try (Cursor cursor = db().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                rows.add(formatDate(cursor.getString(0)) + "  "
                        + trimDouble(cursor.getDouble(1)) + "kg");
            }
        }
        return rows;
    }

    private BodyMetricEntry bodyMetricEntry(Cursor cursor) {
        return new BodyMetricEntry(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getDouble(2),
                metadataValue(cursor.getString(3), "memo", "")
        );
    }

    private SQLiteDatabase db() {
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        ensureDevice(database);
        return database;
    }

    private ContentValues baseValues(String id, String now) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("user_id", userId);
        values.put("device_id", DEVICE_ID);
        values.put("created_at", now);
        values.put("updated_at", now);
        values.putNull("deleted_at");
        return values;
    }

    private void ensureDevice(SQLiteDatabase database) {
        ContentValues values = new ContentValues();
        values.put("id", DEVICE_ID);
        values.put("user_id", userId);
        values.put("name", "Fitness Android");
        values.put("last_seen_at", now());
        values.put("app_version", "0.1.0");
        database.insertWithOnConflict("devices", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static String bodyWeightMetadata(String memo) {
        return json("item_type", "body_weight", "memo", emptyToDefault(memo, ""));
    }

    private static String metadataValue(String metadata, String key, String fallback) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return fallback;
        }
        try {
            JSONObject object = new JSONObject(metadata);
            Object value = object.opt(key);
            if (value == null || value == JSONObject.NULL) {
                return fallback;
            }
            String normalized = value.toString().trim();
            return TextValuePolicy.isMissing(normalized) ? fallback : normalized;
        } catch (Exception exception) {
            return fallback;
        }
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }

    private static String emptyToToday(String value) {
        return emptyToDefault(value, LocalDate.now().toString());
    }

    private static String requireRecordDate(String value) {
        String normalized = emptyToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("날짜를 입력하세요.");
        }
        try {
            return LocalDate.parse(normalized).toString();
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("날짜를 YYYY-MM-DD 형식으로 입력하세요.");
        }
    }

    private static double requireBodyWeight(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException("체중은 0보다 큰 숫자여야 합니다.");
        }
        return value;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String json(String... pairs) {
        StringBuilder builder = new StringBuilder("{");
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append("\"").append(escapeJson(pairs[index])).append("\":");
            String value = pairs[index + 1] == null ? "" : pairs[index + 1];
            if ("true".equals(value) || "false".equals(value)) {
                builder.append(value);
            } else {
                builder.append("\"").append(escapeJson(value)).append("\"");
            }
        }
        builder.append("}");
        return builder.toString();
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    private static String formatDate(String date) {
        return date == null ? "" : date.replace("-", ". ");
    }

    private static String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
