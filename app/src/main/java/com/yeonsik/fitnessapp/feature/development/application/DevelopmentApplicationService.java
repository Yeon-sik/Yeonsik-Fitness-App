package com.yeonsik.fitnessapp.feature.development.application;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.development.BodyProfile;
import com.yeonsik.fitnessapp.development.DevelopmentGoal;
import com.yeonsik.fitnessapp.feature.development.data.DevelopmentRepository;
import com.yeonsik.fitnessapp.feature.body.application.BodyMetricsApplicationService;

/**
 * Coordinates the two records edited by the Development surface: body profile and today's
 * weight. Goal editing remains in the same feature boundary so MainActivity only owns dialogs
 * and navigation, not repository sequencing.
 */
public final class DevelopmentApplicationService {
    private final DevelopmentRepository developmentRepository;
    private final BodyMetricsApplicationService bodyMetrics;
    private volatile String ownerId;

    public DevelopmentApplicationService(
            DevelopmentRepository developmentRepository,
            BodyMetricsApplicationService bodyMetrics,
            String ownerId
    ) {
        if (developmentRepository == null || bodyMetrics == null) {
            throw new IllegalArgumentException("발전 기능 저장소가 필요합니다.");
        }
        this.developmentRepository = developmentRepository;
        this.bodyMetrics = bodyMetrics;
        this.ownerId = requireOwner(ownerId);
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = requireOwner(ownerId);
    }

    public ProfileEditor loadProfileEditor(AccountScope scope, String date) {
        requireScope(scope);
        BodyMetricsApplicationService.Editor weight = bodyMetrics.load(scope, date, null);
        return new ProfileEditor(
                developmentRepository.bodyProfile(),
                weight.exists() ? weight.recordId : null,
                weight.exists() ? weight.weightKg : null,
                weight.exists() ? weight.memo : ""
        );
    }

    public void saveProfileAndWeight(
            AccountScope scope,
            BodyProfile profile,
            String weightRecordId,
            String weightDate,
            Double weightKg,
            String weightMemo
    ) {
        requireScope(scope);
        if (profile != null) {
            developmentRepository.saveBodyProfile(profile);
        }
        if (weightKg != null) {
            bodyMetrics.save(scope, weightRecordId, weightDate, weightKg, weightMemo);
        }
    }

    public DevelopmentGoal loadGoal(AccountScope scope) {
        requireScope(scope);
        return developmentRepository.developmentGoal();
    }

    public void saveGoal(AccountScope scope, DevelopmentGoal goal) {
        requireScope(scope);
        developmentRepository.saveDevelopmentGoal(goal);
    }

    private void requireScope(AccountScope scope) {
        if (scope == null || !ownerId.equals(scope.getOwnerId())) {
            throw new IllegalStateException("계정이 변경된 뒤 발전 작업이 도착했습니다.");
        }
    }

    private static String requireOwner(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("발전 계정 식별자가 필요합니다.");
        }
        return normalized;
    }

    public static final class ProfileEditor {
        public final BodyProfile profile;
        public final String weightRecordId;
        public final Double weightKg;
        public final String weightMemo;

        private ProfileEditor(
                BodyProfile profile,
                String weightRecordId,
                Double weightKg,
                String weightMemo
        ) {
            this.profile = profile;
            this.weightRecordId = weightRecordId;
            this.weightKg = weightKg;
            this.weightMemo = weightMemo == null ? "" : weightMemo;
        }
    }
}
