package com.yeonsik.fitnessapp.integration.transfer;

import com.yeonsik.fitnessapp.core.database.backup.BackupDatabaseStorage;
import com.yeonsik.fitnessapp.data.FleekCsvImporter;
import com.yeonsik.fitnessapp.data.LocalDataBackupService;
import com.yeonsik.fitnessapp.data.WorkoutTransferService;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterRepository;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogBackupApi;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutSummaryApi;
import com.yeonsik.fitnessapp.integration.workout.WorkoutInterchangeResult;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutInterchangeApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Application boundary for user-initiated local data movement.
 *
 * URI permission and progress UI stay in MainActivity. Backup/transfer construction, account
 * scoping, FLEEK matching, and Summary v2 reconciliation stay here so the host never reaches a
 * repository or interchange store directly.
 */
public final class LocalDataTransferApplicationService {
    private final BackupDatabaseStorage database;
    private final NutritionCatalogBackupApi nutritionCatalogBackupApi;
    private final WorkoutInterchangeApi workoutInterchangeStore;
    private final WorkoutSummaryApi fitnessSummaryStore;
    private final ExerciseMasterRepository exerciseMasterRepository;

    public LocalDataTransferApplicationService(
            BackupDatabaseStorage database,
            NutritionCatalogBackupApi nutritionCatalogBackupApi,
            WorkoutInterchangeApi workoutInterchangeStore,
            WorkoutSummaryApi fitnessSummaryStore,
            ExerciseMasterRepository exerciseMasterRepository
    ) {
        if (database == null || workoutInterchangeStore == null || fitnessSummaryStore == null
                || exerciseMasterRepository == null || nutritionCatalogBackupApi == null) {
            throw new IllegalArgumentException("로컬 데이터 전송 의존성이 없습니다.");
        }
        this.database = database;
        this.nutritionCatalogBackupApi = nutritionCatalogBackupApi;
        this.workoutInterchangeStore = workoutInterchangeStore;
        this.fitnessSummaryStore = fitnessSummaryStore;
        this.exerciseMasterRepository = exerciseMasterRepository;
    }

    public void reconcileSharedWorkoutSummaries(String ownerId) {
        fitnessSummaryStore.reconcileSharedWorkoutSummaries(requireOwner(ownerId));
    }

    public BackupPreview writeBackup(
            String recordOwnerId,
            String nutritionOwnerId,
            OutputStream output
    ) throws IOException {
        LocalDataBackupService service = backupService(recordOwnerId, nutritionOwnerId);
        LocalDataBackupService.BackupPreview preview = service.writeBackup(output);
        return new BackupPreview(preview.getTotalRows(), preview.getExportedAt(), preview.getDatabaseVersion());
    }

    public BackupPreview previewBackup(
            String recordOwnerId,
            String nutritionOwnerId,
            InputStream input
    ) throws IOException {
        LocalDataBackupService.BackupPreview preview =
                backupService(recordOwnerId, nutritionOwnerId).previewBackup(input);
        return new BackupPreview(preview.getTotalRows(), preview.getExportedAt(), preview.getDatabaseVersion());
    }

    public RestoreResult restoreBackup(
            String recordOwnerId,
            String nutritionOwnerId,
            InputStream input
    ) throws IOException {
        LocalDataBackupService.RestoreResult result =
                backupService(recordOwnerId, nutritionOwnerId).restoreBackup(input);
        reconcileSharedWorkoutSummaries(recordOwnerId);
        return new RestoreResult(result.getImportedRows(), result.getSkippedRows());
    }

    public void writeRecordsSummaryCsv(String recordOwnerId, String nutritionOwnerId, OutputStream output)
            throws IOException {
        backupService(recordOwnerId, nutritionOwnerId).writeRecordsSummaryCsv(output);
    }

    public void writeWorkoutTransfer(String ownerId, OutputStream output) throws IOException {
        new WorkoutTransferService(workoutInterchangeStore, requireOwner(ownerId)).writeJson(output);
    }

    public ImportResult importWorkoutTransfer(String ownerId, InputStream input) throws IOException {
        WorkoutInterchangeResult result = new WorkoutTransferService(
                workoutInterchangeStore,
                requireOwner(ownerId)
        ).importJson(input);
        reconcileSharedWorkoutSummaries(ownerId);
        return ImportResult.from(result);
    }

    public ImportResult importFleek(String ownerId, InputStream input) throws IOException {
        String normalizedOwnerId = requireOwner(ownerId);
        FleekCsvImporter.ImportPlan plan = FleekCsvImporter.parse(
                new InputStreamReader(input, StandardCharsets.UTF_8),
                exerciseMasterRepository.getAllWeightExercises()
        );
        WorkoutInterchangeResult result = workoutInterchangeStore.importFleek(
                normalizedOwnerId,
                plan
        );
        reconcileSharedWorkoutSummaries(normalizedOwnerId);
        return ImportResult.from(result);
    }

    private LocalDataBackupService backupService(String recordOwnerId, String nutritionOwnerId) {
        return new LocalDataBackupService(
                database,
                nutritionCatalogBackupApi,
                requireOwner(recordOwnerId),
                requireOwner(nutritionOwnerId)
        );
    }

    private static String requireOwner(String ownerId) {
        String normalized = ownerId == null ? "" : ownerId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("데이터 작업 계정 식별자가 필요합니다.");
        }
        return normalized;
    }

    public static final class BackupPreview {
        public final int totalRows;
        public final String exportedAt;
        public final int databaseVersion;

        private BackupPreview(int totalRows, String exportedAt, int databaseVersion) {
            this.totalRows = totalRows;
            this.exportedAt = exportedAt;
            this.databaseVersion = databaseVersion;
        }
    }

    public static final class RestoreResult {
        public final int importedRows;
        public final int skippedRows;

        private RestoreResult(int importedRows, int skippedRows) {
            this.importedRows = importedRows;
            this.skippedRows = skippedRows;
        }
    }

    public static final class ImportResult {
        public final int importedSessions;
        public final int importedExercises;
        public final int importedSets;
        public final int masterMatchedSets;
        public final int skippedDuplicateSessions;
        public final int skippedRows;

        private ImportResult(
                int importedSessions,
                int importedExercises,
                int importedSets,
                int masterMatchedSets,
                int skippedDuplicateSessions,
                int skippedRows
        ) {
            this.importedSessions = importedSessions;
            this.importedExercises = importedExercises;
            this.importedSets = importedSets;
            this.masterMatchedSets = masterMatchedSets;
            this.skippedDuplicateSessions = skippedDuplicateSessions;
            this.skippedRows = skippedRows;
        }

        private static ImportResult from(WorkoutInterchangeResult result) {
            return new ImportResult(
                    result.importedSessions,
                    result.importedExercises,
                    result.importedSets,
                    result.masterMatchedSets,
                    result.skippedDuplicateSessions,
                    result.skippedRows
            );
        }

        public String summary() {
            return "세션 " + importedSessions + "개 · 운동 " + importedExercises
                    + "개 · 세트 " + importedSets + "개 · 중복 세션 "
                    + skippedDuplicateSessions + "개 · 제외 행 " + skippedRows + "개";
        }
    }
}
