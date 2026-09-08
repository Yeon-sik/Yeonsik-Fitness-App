package com.yeonsik.fitnessapp.feature.body.application;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.data.BodyMetricEntry;
import com.yeonsik.fitnessapp.data.BodyMetricsRepository;

/**
 * Application boundary for body-metric editor use cases.
 *
 * The editor needs a date lookup, an upsert/update decision, and deletion. Keeping that
 * decision here prevents a screen host from becoming the body repository's transaction API.
 */
public final class BodyMetricsApplicationService {
    private final BodyMetricsRepository repository;
    private volatile String ownerId;

    public BodyMetricsApplicationService(BodyMetricsRepository repository, String ownerId) {
        if (repository == null) {
            throw new IllegalArgumentException("체중 저장소가 필요합니다.");
        }
        this.repository = repository;
        this.ownerId = requireOwner(ownerId);
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = requireOwner(ownerId);
    }

    public Editor load(AccountScope scope, String date, String recordId) {
        requireScope(scope);
        BodyMetricEntry entry = recordId == null
                ? repository.bodyMetricForDate(date)
                : repository.bodyMetricEntryById(recordId);
        return entry == null
                ? Editor.empty(date)
                : new Editor(entry.id, entry.date, entry.weightKg, entry.memo);
    }

    public String save(
            AccountScope scope,
            String recordId,
            String date,
            double weightKg,
            String memo
    ) {
        requireScope(scope);
        if (recordId == null || recordId.trim().isEmpty()) {
            return repository.addBodyMetric(date, weightKg, memo);
        }
        repository.updateBodyMetric(recordId, date, weightKg, memo);
        return recordId;
    }

    public void delete(AccountScope scope, String recordId) {
        requireScope(scope);
        repository.deleteBodyMetric(recordId);
    }

    private void requireScope(AccountScope scope) {
        if (scope == null || !ownerId.equals(scope.getOwnerId())) {
            throw new IllegalStateException("계정이 변경된 뒤 체중 작업이 도착했습니다.");
        }
    }

    private static String requireOwner(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("체중 계정 식별자가 필요합니다.");
        }
        return normalized;
    }

    public static final class Editor {
        public final String recordId;
        public final String date;
        public final double weightKg;
        public final String memo;

        private Editor(String recordId, String date, double weightKg, String memo) {
            this.recordId = recordId;
            this.date = date == null ? "" : date;
            this.weightKg = weightKg;
            this.memo = memo == null ? "" : memo;
        }

        private static Editor empty(String date) {
            return new Editor(null, date, 0d, "");
        }

        public boolean exists() {
            return recordId != null && !recordId.trim().isEmpty();
        }
    }
}
