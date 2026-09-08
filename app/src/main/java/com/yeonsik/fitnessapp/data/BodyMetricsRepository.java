package com.yeonsik.fitnessapp.data;

import androidx.annotation.NonNull;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.database.BodyProfileEntity;
import com.yeonsik.fitnessapp.core.database.BodyRoomDao;
import com.yeonsik.fitnessapp.core.database.DevicesRoomEntity;
import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.WeightRecordEntity;
import com.yeonsik.fitnessapp.development.BodyProfile;
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsRepositoryApi;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Room DAO implementation for locally owned body metrics and body profile data. */
public final class BodyMetricsRepository implements BodyMetricsRepositoryApi {
    private static final String DEVICE_ID = "android-local";

    private final FitnessRoomDatabase roomDatabase;
    private final BodyRoomDao bodyDao;
    private String userId;

    public BodyMetricsRepository(@NonNull FitnessRoomDatabase roomDatabase, String userId) {
        this.roomDatabase = roomDatabase;
        this.bodyDao = roomDatabase.bodyRoomDao();
        this.userId = normalizeUserId(userId);
    }

    /** Temporary source-compatible adapter for legacy repository tests. */
    @Deprecated
    public BodyMetricsRepository(FitnessDatabaseConnection legacyDatabase, String userId) {
        this(FitnessRoomDatabaseProvider.get(legacyDatabase.applicationContext()), userId);
    }

    @Override
    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    @Override
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
        ensureDevice(now);
        bodyDao.insert(new WeightRecordEntity(
                id, userId, recordDate, validatedWeight, now, false,
                null, null, now, null, DEVICE_ID, "fitness", "fitness",
                bodyWeightMetadata(memo), 1
        ));
        return id;
    }

    @Override
    public BodyMetricEntry bodyMetricForDate(String date) {
        List<BodyMetricEntry> entries = bodyMetricEntriesForDate(date);
        return entries.isEmpty() ? null : entries.get(0);
    }

    @Override
    public BodyMetricEntry bodyMetricEntryById(String id) {
        String normalized = emptyToNull(id);
        return normalized == null ? null : toEntry(bodyDao.visibleWeight(normalized, userId));
    }

    @Override
    public List<BodyMetricEntry> bodyMetricEntriesForDate(String date) {
        List<WeightRecordEntity> records = date == null
                ? bodyDao.visibleWeights(userId, 20)
                : bodyDao.visibleWeightsForDate(userId, emptyToToday(date));
        List<BodyMetricEntry> entries = new ArrayList<>();
        for (WeightRecordEntity record : records) {
            entries.add(toEntry(record));
        }
        return entries;
    }

    @Override
    public void updateBodyMetric(String id, String date, double weightKg, String memo) {
        if (emptyToNull(id) == null) {
            return;
        }
        bodyDao.updateVisibleWeight(
                id,
                userId,
                requireRecordDate(date),
                requireBodyWeight(weightKg),
                bodyWeightMetadata(memo),
                now()
        );
    }

    @Override
    public void deleteBodyMetric(String id) {
        if (emptyToNull(id) == null) {
            return;
        }
        String now = now();
        bodyDao.tombstoneVisibleWeight(id, userId, now, now);
    }

    /** Returns the newest visible entry on or before the requested date. */
    @Override
    public BodyMetricEntry latestBodyMetricOnOrBefore(String date) {
        return toEntry(bodyDao.latestVisibleWeightOnOrBefore(userId, emptyToToday(date)));
    }

    public List<String> bodyMetrics() {
        return bodyMetricsForDate(null);
    }

    @Override
    public List<String> bodyMetricsForDate(String date) {
        List<String> rows = new ArrayList<>();
        for (BodyMetricEntry entry : bodyMetricEntriesForDate(date)) {
            rows.add(formatDate(entry.date) + "  " + trimDouble(entry.weightKg) + "kg");
        }
        return rows;
    }

    @Override
    public BodyProfile bodyProfile() {
        BodyProfileEntity entity = bodyDao.bodyProfile(userId);
        return entity == null
                ? BodyProfile.empty()
                : new BodyProfile(entity.getHeightCm(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    @Override
    public void saveBodyProfile(BodyProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("신체 프로필이 필요합니다.");
        }
        if (!profile.isConfigured()) {
            bodyDao.deleteBodyProfile(userId);
            return;
        }
        String now = now();
        BodyProfileEntity existing = bodyDao.bodyProfile(userId);
        bodyDao.replaceBodyProfile(new BodyProfileEntity(
                userId,
                profile.heightCm,
                existing == null ? now : existing.getCreatedAt(),
                now
        ));
    }

    private BodyMetricEntry toEntry(WeightRecordEntity record) {
        return record == null ? null : new BodyMetricEntry(
                record.getId(),
                record.getDate(),
                record.getWeightKg(),
                metadataValue(record.getMetadata(), "memo", "")
        );
    }

    private void ensureDevice(String now) {
        roomDatabase.deviceRoomDao().upsert(new DevicesRoomEntity(
                DEVICE_ID, userId, "Fitness Android", now, "0.1.0"
        ));
    }

    private static String bodyWeightMetadata(String memo) {
        return json("item_type", "body_weight", "memo", emptyToDefault(memo, ""));
    }

    private static String metadataValue(String metadata, String key, String fallback) {
        if (metadata == null || metadata.trim().isEmpty()) return fallback;
        try {
            Object value = new JSONObject(metadata).opt(key);
            if (value == null || value == JSONObject.NULL) return fallback;
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
        if (normalized == null) throw new IllegalArgumentException("날짜를 입력하세요.");
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
            if (index > 0) builder.append(",");
            builder.append("\"").append(escapeJson(pairs[index])).append("\":");
            String value = pairs[index + 1] == null ? "" : pairs[index + 1];
            if ("true".equals(value) || "false".equals(value)) builder.append(value);
            else builder.append("\"").append(escapeJson(value)).append("\"");
        }
        return builder.append("}").toString();
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
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
