package com.yeonsik.fitnessapp.integration.personalos;

import android.content.ContentValues;
import android.database.Cursor;

import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.data.FitnessSummaryProjectionV2;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local projection store for the immutable Personal OS summary v1/v2 contracts. */
public final class FitnessSummaryStore {
    private static final String DEVICE_ID = "android-local";

    private final FitnessDatabaseConnection database;

    public FitnessSummaryStore(FitnessDatabaseConnection database) {
        if (database == null) {
            throw new IllegalArgumentException("Fitness database connection is required.");
        }
        this.database = database;
    }

    public int reconcileSharedWorkoutSummaries(String ownerId) {
        List<String> recordIds = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT id FROM workout_records WHERE source_app = 'fitness' " +
                        "AND user_id = ? AND deleted_at IS NULL " +
                        "AND device_id = ? " +
                        "AND metadata LIKE '%\"status\":\"completed\"%'",
                new String[]{ownerId, DEVICE_ID})) {
            while (cursor.moveToNext()) {
                recordIds.add(cursor.getString(0));
            }
        }
        int changedCount = 0;
        for (String recordId : recordIds) {
            if (updateSharedWorkoutSummary(ownerId, recordId, true)) {
                changedCount += 1;
            }
        }
        return changedCount;
    }

    public boolean updateSharedWorkoutSummary(
            String ownerId,
            String recordId,
            boolean publishToOs
    ) {
        if (isBlank(recordId)) {
            return false;
        }

        String workoutType;
        String currentCategory;
        String currentMetadata;
        String sourceApp;
        String currentScope;
        try (Cursor cursor = database.rawQuery(
                "SELECT workout_type, category, metadata, source_app, scope FROM workout_records " +
                        "WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
                new String[]{recordId, ownerId})) {
            if (!cursor.moveToFirst()) {
                return false;
            }
            workoutType = cursor.getString(0);
            currentCategory = cursor.getString(1);
            currentMetadata = cursor.getString(2);
            sourceApp = cursor.getString(3);
            currentScope = cursor.getString(4);
        }
        if (!"fitness".equals(sourceApp)) {
            return false;
        }

        List<String> categories = new ArrayList<>();
        if ("cardio".equals(workoutType)) {
            categories.add(sharedCardioCategory(currentCategory));
        } else if ("strength".equals(workoutType)) {
            try (Cursor cursor = database.rawQuery(
                    "SELECT ui_part FROM workout_exercises " +
                            "WHERE record_id = ? AND user_id = ? " +
                            "AND deleted_at IS NULL ORDER BY order_index",
                    new String[]{recordId, ownerId})) {
                while (cursor.moveToNext()) {
                    String category = personalOsStrengthCategory(cursor.getString(0));
                    if (!category.isEmpty() && !categories.contains(category)) {
                        categories.add(category);
                    }
                }
            }
            if (categories.isEmpty()) {
                String existingCategory = personalOsStrengthCategory(currentCategory);
                if (!existingCategory.isEmpty()) {
                    categories.add(existingCategory);
                }
            }
        } else {
            categories.add("기타");
        }
        if (categories.isEmpty()) {
            categories.add("기타");
        }

        JSONObject metadata;
        try {
            metadata = isBlank(currentMetadata) ? new JSONObject() : new JSONObject(currentMetadata);
        } catch (Exception ignored) {
            metadata = new JSONObject();
        }
        try {
            metadata.put("os_categories", new JSONArray(categories));
            metadata.put("category_codes", FitnessRecordContract.categoryCodes(categories));
            metadata.put("contract_version", FitnessRecordContract.VERSION);
        } catch (Exception ignored) {
            // The category column remains the v1 fallback.
        }

        String nextCategory = categories.get(0);
        String nextMetadata = metadata.toString();
        String nextScope = publishToOs ? "both" : currentScope;
        if (nextCategory.equals(currentCategory)
                && nextMetadata.equals(currentMetadata)
                && nextScope.equals(currentScope)) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put("category", nextCategory);
        values.put("metadata", nextMetadata);
        values.put("scope", nextScope);
        values.put("updated_at", OffsetDateTime.now().toString());
        database.update(
                "workout_records",
                values,
                "id = ? AND user_id = ?",
                new String[]{recordId, ownerId}
        );
        return true;
    }

    public List<FitnessSummaryProjectionV2> completedFitnessSummaryProjectionsV2(
            String ownerId
    ) {
        List<FitnessSummaryProjectionV2> projections = new ArrayList<>();
        String sql = "SELECT id, date, workout_type, duration_seconds, metadata, " +
                "created_at, is_backfilled, backfilled_at, backfill_reason, " +
                "updated_at, deleted_at FROM workout_records " +
                "WHERE source_app = 'fitness' AND user_id = ? " +
                "AND metadata LIKE '%\"status\":\"completed\"%' " +
                "ORDER BY date DESC, updated_at DESC, id ASC";
        try (Cursor cursor = database.rawQuery(sql, new String[]{ownerId})) {
            while (cursor.moveToNext()) {
                String metadata = cursor.getString(4);
                if (!"completed".equals(metadataValue(metadata, "status", ""))) {
                    continue;
                }
                String recordId = cursor.getString(0);
                String workoutType = cursor.getString(2);
                Integer totalDurationSeconds = cursor.isNull(3) ? null : cursor.getInt(3);
                if (totalDurationSeconds == null) {
                    int derivedDuration = resolvedDurationSeconds(cursor.getString(1), metadata);
                    totalDurationSeconds = derivedDuration > 0 ? derivedDuration : null;
                }
                Map<String, Integer> completedSets = completedSetCountsByPart(ownerId, recordId);
                Integer cardioDurationSeconds = "cardio".equals(workoutType)
                        ? totalDurationSeconds : null;
                String updatedAt = cursor.getString(9);
                String createdAt = cursor.isNull(5) ? updatedAt : cursor.getString(5);
                projections.add(new FitnessSummaryProjectionV2(
                        recordId,
                        recordId,
                        cursor.getString(1),
                        "completed",
                        setCount(completedSets, "chest"),
                        setCount(completedSets, "back"),
                        setCount(completedSets, "legs"),
                        setCount(completedSets, "shoulders"),
                        setCount(completedSets, "abs"),
                        setCount(completedSets, "triceps"),
                        setCount(completedSets, "biceps"),
                        totalDurationSeconds,
                        cardioDurationSeconds,
                        createdAt,
                        cursor.getInt(6) == 1,
                        cursor.isNull(7) ? null : cursor.getString(7),
                        cursor.isNull(8) ? null : cursor.getString(8),
                        updatedAt,
                        cursor.isNull(10) ? null : cursor.getString(10),
                        DEVICE_ID
                ));
            }
        }
        return projections;
    }

    private Map<String, Integer> completedSetCountsByPart(String ownerId, String recordId) {
        Map<String, Integer> counts = new HashMap<>();
        String sql = "SELECT we.ui_part, we.primary_sub_part_snapshot, COUNT(ws.id) FROM workout_sets ws " +
                "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id " +
                "WHERE we.record_id = ? AND we.user_id = ? AND ws.user_id = ? " +
                "AND we.deleted_at IS NULL AND ws.deleted_at IS NULL " +
                "AND ws.is_completed = 1 " +
                "GROUP BY we.ui_part, we.primary_sub_part_snapshot";
        try (Cursor cursor = database.rawQuery(sql, new String[]{recordId, ownerId, ownerId})) {
            while (cursor.moveToNext()) {
                String part = summaryPartKey(cursor.isNull(0) ? null : cursor.getString(0));
                if ("arms".equals(part)) {
                    part = summaryPartKey(cursor.isNull(1) ? null : cursor.getString(1));
                }
                if (part != null) {
                    counts.put(part, counts.getOrDefault(part, 0) + cursor.getInt(2));
                }
            }
        }
        return counts;
    }

    private static int setCount(Map<String, Integer> counts, String part) {
        Integer count = counts.get(part);
        return count == null ? 0 : Math.max(count, 0);
    }

    private static String summaryPartKey(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        switch (normalized) {
            case "가슴": case "chest": return "chest";
            case "등": case "back": return "back";
            case "하체": case "legs": return "legs";
            case "어깨": case "shoulders": return "shoulders";
            case "복부": case "복근": case "abs": return "abs";
            case "삼두": case "triceps": return "triceps";
            case "이두": case "biceps": return "biceps";
            case "팔": case "arms": return "arms";
            default: return null;
        }
    }

    private static String sharedCardioCategory(String category) {
        String value = category == null ? "" : category.trim();
        if (value.isEmpty() || "cardio".equalsIgnoreCase(value) || "유산소".equals(value)) {
            return "유산소";
        }
        return value;
    }

    private static String personalOsStrengthCategory(String category) {
        String value = category == null ? "" : category.trim().toLowerCase();
        switch (value) {
            case "가슴": case "가슴운동": case "chest": return "가슴";
            case "등": case "등운동": case "back": return "등";
            case "하체": case "하체운동": case "legs": return "하체";
            case "어깨": case "어깨운동": case "shoulders": return "어깨";
            case "복부": case "복부운동": case "복근": case "복근운동": case "abs": return "복부";
            case "삼두": case "삼두운동": case "triceps": return "삼두";
            case "이두": case "이두운동": case "biceps": return "이두";
            case "팔": case "팔운동": case "arms": return "팔";
            default: return "";
        }
    }

    private static int resolvedDurationSeconds(String date, String metadata) {
        String startedAt = metadataValue(metadata, "started_at", "");
        String endedAt = metadataValue(metadata, "ended_at", "");
        try {
            if (!isBlank(endedAt)) {
                Integer completed = computeDurationSeconds(date, startedAt, endedAt);
                return completed == null ? 0 : completed;
            }
            OffsetDateTime startedDateTime = parseOffsetDateTime(startedAt);
            if (startedDateTime != null) {
                long seconds = Duration.between(startedDateTime, OffsetDateTime.now()).getSeconds();
                return seconds < 0 ? 0 : safeInt(seconds);
            }
            LocalTime startedTime = parseLocalTime(startedAt);
            if (startedTime == null) {
                return 0;
            }
            LocalDate recordDate = LocalDate.parse(isBlank(date) ? LocalDate.now().toString() : date);
            long seconds = Duration.between(
                    LocalDateTime.of(recordDate, startedTime),
                    LocalDateTime.of(LocalDate.now(), LocalTime.now())
            ).getSeconds();
            return seconds < 0 ? 0 : safeInt(seconds);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static Integer computeDurationSeconds(String date, String startedAt, String endedAt) {
        OffsetDateTime startedDateTime = parseOffsetDateTime(startedAt);
        OffsetDateTime endedDateTime = parseOffsetDateTime(endedAt);
        if (startedDateTime != null && endedDateTime != null) {
            long seconds = Duration.between(startedDateTime, endedDateTime).getSeconds();
            return seconds < 0 ? null : safeInt(seconds);
        }
        LocalTime startedTime = parseLocalTime(startedAt);
        LocalTime endedTime = parseLocalTime(endedAt);
        if (startedTime == null || endedTime == null) {
            return null;
        }
        try {
            LocalDate recordDate = LocalDate.parse(isBlank(date) ? LocalDate.now().toString() : date);
            long seconds = Duration.between(
                    LocalDateTime.of(recordDate, startedTime),
                    LocalDateTime.of(recordDate, endedTime)
            ).getSeconds();
            return seconds < 0 ? null : safeInt(seconds);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        try {
            return isBlank(value) ? null : OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalTime parseLocalTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        for (String pattern : new String[]{"H:mm", "HH:mm", "H:mm:ss", "HH:mm:ss"}) {
            try {
                return LocalTime.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // Try the next supported legacy format.
            }
        }
        return null;
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static String metadataValue(String metadata, String key, String fallback) {
        try {
            JSONObject object = isBlank(metadata) ? new JSONObject() : new JSONObject(metadata);
            String value = object.optString(key, fallback);
            return isBlank(value) ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
