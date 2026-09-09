package com.yeonsik.fitnessapp.feature.workout.data;

import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.WorkoutRecordsRoomEntity;
import com.yeonsik.fitnessapp.core.database.WorkoutRoomDao;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.data.FitnessSummaryProjectionV2;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutSummaryApi;

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

/** Room-backed local reconciliation for the Personal OS summary contract. */
public final class WorkoutSummaryRepository implements WorkoutSummaryApi {
    private static final String DEVICE_ID = "android-local";

    private final WorkoutRoomDao workoutDao;

    public WorkoutSummaryRepository(FitnessRoomDatabase roomDatabase) {
        if (roomDatabase == null) {
            throw new IllegalArgumentException("Fitness Room database is required.");
        }
        this.workoutDao = roomDatabase.workoutRoomDao();
    }

    @Override
    public int reconcileSharedWorkoutSummaries(String ownerId) {
        String normalizedOwnerId = requireOwner(ownerId);
        int changedCount = 0;
        for (String recordId : workoutDao.sharedSummaryRecordIds(normalizedOwnerId)) {
            if (updateSharedWorkoutSummary(normalizedOwnerId, recordId, true)) {
                changedCount++;
            }
        }
        return changedCount;
    }

    @Override
    public boolean updateSharedWorkoutSummary(
            String ownerId,
            String recordId,
            boolean publishToOs
    ) {
        String normalizedOwnerId = requireOwner(ownerId);
        if (isBlank(recordId)) {
            return false;
        }

        WorkoutRecordsRoomEntity record = workoutDao.visibleRecord(recordId, normalizedOwnerId);
        if (record == null || !"fitness".equals(record.getSourceApp())) {
            return false;
        }

        String workoutType = record.getWorkoutType();
        String currentCategory = record.getCategory();
        String currentMetadata = record.getMetadata();
        String currentScope = record.getScope();
        List<String> categories = new ArrayList<>();
        if ("cardio".equals(workoutType)) {
            categories.add(sharedCardioCategory(currentCategory));
        } else if ("strength".equals(workoutType)) {
            for (String uiPart : workoutDao.visibleUiParts(recordId, normalizedOwnerId)) {
                String category = personalOsStrengthCategory(uiPart);
                if (!category.isEmpty() && !categories.contains(category)) {
                    categories.add(category);
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
            metadata = isBlank(currentMetadata)
                    ? new JSONObject()
                    : new JSONObject(currentMetadata);
        } catch (Exception ignored) {
            metadata = new JSONObject();
        }
        try {
            metadata.put("os_categories", new JSONArray(categories));
            metadata.put("category_codes", FitnessRecordContract.categoryCodes(categories));
            metadata.put("contract_version", FitnessRecordContract.VERSION);
        } catch (Exception ignored) {
            // Keep the category column as the v1 fallback.
        }

        String nextCategory = categories.get(0);
        String nextMetadata = metadata.toString();
        String nextScope = publishToOs ? "both" : currentScope;
        if (nextCategory.equals(currentCategory)
                && nextMetadata.equals(currentMetadata)
                && nextScope.equals(currentScope)) {
            return false;
        }

        return workoutDao.updateSharedSummary(
                recordId,
                normalizedOwnerId,
                nextCategory,
                nextMetadata,
                nextScope,
                OffsetDateTime.now().toString()
        ) > 0;
    }

    @Override
    public List<FitnessSummaryProjectionV2> completedFitnessSummaryProjectionsV2(
            String ownerId
    ) {
        String normalizedOwnerId = requireOwner(ownerId);
        List<FitnessSummaryProjectionV2> projections = new ArrayList<>();
        for (WorkoutRecordsRoomEntity record
                : workoutDao.completedSummaryRecords(normalizedOwnerId)) {
            String metadata = record.getMetadata();
            if (!"completed".equals(metadataValue(metadata, "status", ""))) {
                continue;
            }
            Integer totalDurationSeconds = record.getDurationSeconds() == null
                    ? null
                    : safeInt(record.getDurationSeconds());
            if (totalDurationSeconds == null) {
                int derivedDuration = resolvedDurationSeconds(
                        record.getDate(),
                        metadata
                );
                totalDurationSeconds = derivedDuration > 0 ? derivedDuration : null;
            }
            Map<String, Integer> completedSets = completedSetCountsByPart(
                    normalizedOwnerId,
                    record.getId()
            );
            Integer cardioDurationSeconds = "cardio".equals(record.getWorkoutType())
                    ? totalDurationSeconds
                    : null;
            String updatedAt = record.getUpdatedAt();
            String createdAt = record.getCreatedAt() == null
                    ? updatedAt
                    : record.getCreatedAt();
            projections.add(new FitnessSummaryProjectionV2(
                    record.getId(),
                    record.getId(),
                    record.getDate(),
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
                    record.isBackfilled() == 1L,
                    record.getBackfilledAt(),
                    record.getBackfillReason(),
                    updatedAt,
                    record.getDeletedAt(),
                    DEVICE_ID
            ));
        }
        return projections;
    }

    private Map<String, Integer> completedSetCountsByPart(String ownerId, String recordId) {
        Map<String, Integer> counts = new HashMap<>();
        for (WorkoutRoomDao.SummarySetCount row
                : workoutDao.summarySetCounts(recordId, ownerId)) {
            String part = summaryPartKey(row.getUiPart());
            if ("arms".equals(part)) {
                part = summaryPartKey(row.getPrimarySubPart());
            }
            if (part != null) {
                counts.put(part, counts.getOrDefault(part, 0) + row.getSetCount());
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
            case "가슴":
            case "chest":
                return "chest";
            case "등":
            case "back":
                return "back";
            case "하체":
            case "legs":
                return "legs";
            case "어깨":
            case "shoulders":
                return "shoulders";
            case "복부":
            case "복근":
            case "abs":
                return "abs";
            case "삼두":
            case "triceps":
                return "triceps";
            case "이두":
            case "biceps":
                return "biceps";
            case "팔":
            case "arms":
                return "arms";
            default:
                return null;
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
            case "가슴":
            case "가슴운동":
            case "chest":
                return "가슴";
            case "등":
            case "등운동":
            case "back":
                return "등";
            case "하체":
            case "하체운동":
            case "legs":
                return "하체";
            case "어깨":
            case "어깨운동":
            case "shoulders":
                return "어깨";
            case "복부":
            case "복부운동":
            case "복근":
            case "복근운동":
            case "abs":
                return "복부";
            case "삼두":
            case "삼두운동":
            case "triceps":
                return "삼두";
            case "이두":
            case "이두운동":
            case "biceps":
                return "이두";
            case "팔":
            case "팔운동":
            case "arms":
                return "팔";
            default:
                return "";
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
            LocalDate recordDate = LocalDate.parse(
                    isBlank(date) ? LocalDate.now().toString() : date
            );
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
            LocalDate recordDate = LocalDate.parse(
                    isBlank(date) ? LocalDate.now().toString() : date
            );
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

    private static String requireOwner(String ownerId) {
        String normalized = ownerId == null ? "" : ownerId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Summary 계정 식별자가 필요합니다.");
        }
        return normalized;
    }
}
