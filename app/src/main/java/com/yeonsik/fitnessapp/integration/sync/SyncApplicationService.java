package com.yeonsik.fitnessapp.integration.sync;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService;
import com.yeonsik.fitnessapp.sync.SupabaseAuthManager;
import com.yeonsik.fitnessapp.integration.personalos.SupabaseSyncManager;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Coordinates the shared Summary-v2 sync and optional Nutrition catalog sync. */
public final class SyncApplicationService implements ManualSyncRunner {
    private final SupabaseAuthManager sharedAuth;
    private final SupabaseSyncManager sharedSync;
    private final NutritionIntegrationService nutrition;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SyncApplicationService(
            SupabaseAuthManager sharedAuth,
            SupabaseSyncManager sharedSync,
            NutritionIntegrationService nutrition
    ) {
        if (sharedAuth == null || sharedSync == null || nutrition == null) {
            throw new IllegalArgumentException("동기화 서비스 의존성이 없습니다.");
        }
        this.sharedAuth = sharedAuth;
        this.sharedSync = sharedSync;
        this.nutrition = nutrition;
    }

    public Result run(SupabaseConfig sharedConfig, SupabaseConfig nutritionConfig) throws Exception {
        return run(sharedConfig, nutritionConfig, ignored -> {});
    }

    @Override
    public Result run(
            SupabaseConfig sharedConfig, SupabaseConfig nutritionConfig, Consumer<String> progress
    ) throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 동기화가 진행 중입니다.");
        }
        try {
            return execute(sharedConfig, nutritionConfig, progress);
        } finally {
            running.set(false);
        }
    }

    private Result execute(
            SupabaseConfig sharedConfig, SupabaseConfig nutritionConfig, Consumer<String> progress
    ) throws Exception {
        if (sharedConfig == null || !sharedConfig.isConfigured()) {
            throw new IllegalStateException("Supabase 연결 설정을 저장하고 계정에 로그인하세요.");
        }

        progress.accept("계정 연결을 확인하고 있습니다.");
        SupabaseConfig activeShared = sharedAuth.refresh(sharedConfig);
        SupabaseSyncManager.SyncResult sharedResult = sharedSync.manualSync(activeShared, progress);
        SupabaseConfig activeNutrition = nutritionConfig;
        NutritionIntegrationService.SyncResult nutritionResult = null;
        boolean nutritionFailed = false;
        String nutritionStatus;

        if (nutritionConfig == null || !nutritionConfig.isConnectionConfigured()) {
            nutritionStatus = "영양 DB 연결 없음";
        } else {
            try {
                progress.accept("영양 카탈로그를 확인하고 변경분을 동기화하고 있습니다.");
                nutritionResult = nutrition.syncCatalog(nutritionConfig);
                activeNutrition = nutritionResult.nutritionConfig;
                nutritionStatus = "영양 DB push " + nutritionResult.pushedRows
                        + "건 · pull " + nutritionResult.pulledRows + "건";
            } catch (Exception error) {
                nutritionFailed = true;
                nutritionStatus = "영양 DB 실패: "
                        + (error.getMessage() == null ? "원격 동기화 오류" : error.getMessage());
            }
        }

        return new Result(
                activeShared,
                activeNutrition,
                SharedResult.from(sharedResult),
                nutritionFailed,
                nutritionStatus
        );
    }

    public static final class Result {
        public final SupabaseConfig sharedConfig;
        public final SupabaseConfig nutritionConfig;
        public final SharedResult sharedResult;
        public final boolean nutritionFailed;
        public final String nutritionStatus;

        private Result(
                SupabaseConfig sharedConfig,
                SupabaseConfig nutritionConfig,
                SharedResult sharedResult,
                boolean nutritionFailed,
                String nutritionStatus
        ) {
            this.sharedConfig = sharedConfig;
            this.nutritionConfig = nutritionConfig;
            this.sharedResult = sharedResult;
            this.nutritionFailed = nutritionFailed;
            this.nutritionStatus = nutritionStatus;
        }
    }

    public static final class SharedResult {
        public final int pushedRows;
        public final int pulledRows;
        public final String syncedAt;
        public final int summaryPublishedRows;
        public final boolean summaryPublicationSucceeded;
        public final String summaryPublicationError;

        private SharedResult(
                int pushedRows,
                int pulledRows,
                String syncedAt,
                int summaryPublishedRows,
                boolean summaryPublicationSucceeded,
                String summaryPublicationError
        ) {
            this.pushedRows = pushedRows;
            this.pulledRows = pulledRows;
            this.syncedAt = syncedAt;
            this.summaryPublishedRows = summaryPublishedRows;
            this.summaryPublicationSucceeded = summaryPublicationSucceeded;
            this.summaryPublicationError = summaryPublicationError;
        }

        private static SharedResult from(SupabaseSyncManager.SyncResult result) {
            return new SharedResult(
                    result.pushedRows,
                    result.pulledRows,
                    result.syncedAt,
                    result.summaryPublishedRows,
                    result.summaryPublicationSucceeded,
                    result.summaryPublicationError
            );
        }
    }
}
