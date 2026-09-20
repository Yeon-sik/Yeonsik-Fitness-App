package com.yeonsik.fitnessapp.feature.recovery.data;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitness.shared.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.NutritionDailyCheckinsRoomEntity;
import com.yeonsik.fitnessapp.core.database.NutritionGoalsRoomEntity;
import com.yeonsik.fitnessapp.core.database.RecoveryRoomDao;
import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn;
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal;
import com.yeonsik.fitnessapp.feature.recovery.api.RecoveryRepositoryApi;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Owns account-scoped writes for recovery facts; Development consumes its read projection. */
public final class RecoveryRepository implements RecoveryRepositoryApi {
    private final RecoveryRoomDao recoveryDao;
    private String userId;

    public RecoveryRepository(FitnessRoomDatabase roomDatabase, String userId) {
        if (roomDatabase == null) {
            throw new IllegalArgumentException("RecoveryRepository에는 Room 데이터베이스가 필요합니다.");
        }
        recoveryDao = roomDatabase.recoveryRoomDao();
        userId = normalizeUserId(userId);
        this.userId = userId;
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    @Override
    public AthleteNutritionGoal nutritionGoal(AccountScope scope) {
        requireScope(scope);
        NutritionGoalsRoomEntity goal = recoveryDao.nutritionGoal(userId);
        if (goal == null) {
            return null;
        }
        return new AthleteNutritionGoal(
                goal.getPhase(),
                goal.getCaloriesKcal(),
                goal.getProteinGrams(),
                goal.getCarbsGrams(),
                goal.getFatGrams(),
                goal.getFiberGrams(),
                goal.getSodiumMg(),
                (int) goal.getWaterMl()
        );
    }

    @Override
    public void saveNutritionGoal(AccountScope scope, AthleteNutritionGoal goal) {
        requireScope(scope);
        if (goal == null) {
            throw new IllegalArgumentException("영양 목표가 필요합니다.");
        }
        NutritionGoalsRoomEntity existing = recoveryDao.nutritionGoal(userId);
        String timestamp = now();
        recoveryDao.replaceNutritionGoal(new NutritionGoalsRoomEntity(
                userId,
                goal.phase,
                goal.caloriesKcal,
                goal.proteinGrams,
                goal.carbsGrams,
                goal.fatGrams,
                goal.fiberGrams,
                goal.sodiumMg,
                goal.waterMl,
                existing == null ? timestamp : existing.getCreatedAt(),
                timestamp
        ));
    }

    @Override
    public AthleteDailyCheckIn checkIn(AccountScope scope, String date) {
        requireScope(scope);
        String normalizedDate = normalizeDate(date);
        NutritionDailyCheckinsRoomEntity row = recoveryDao.checkIn(userId, normalizedDate);
        if (row == null) {
            return AthleteDailyCheckIn.empty(normalizedDate);
        }
        return toModel(row);
    }

    @Override
    public void saveCheckIn(AccountScope scope, AthleteDailyCheckIn checkIn) {
        requireScope(scope);
        if (checkIn == null) {
            throw new IllegalArgumentException("선수 체크인 기록이 필요합니다.");
        }
        NutritionDailyCheckinsRoomEntity existing = recoveryDao.checkIn(userId, checkIn.date);
        if (checkIn.isEmpty()) {
            recoveryDao.deleteCheckIn(userId, checkIn.date);
            return;
        }
        String timestamp = now();
        String recordId = existing == null
                ? UUID.randomUUID().toString()
                : existing.getId();
        recoveryDao.replaceCheckIn(new NutritionDailyCheckinsRoomEntity(
                recordId,
                userId,
                checkIn.date,
                checkIn.waterMl,
                checkIn.sleepHours,
                toLong(checkIn.energyScore),
                toLong(checkIn.hungerScore),
                toLong(checkIn.digestionScore),
                toLong(checkIn.trainingReadinessScore),
                checkIn.note,
                existing == null ? timestamp : existing.getCreatedAt(),
                timestamp
        ));
    }

    private AthleteDailyCheckIn toModel(NutritionDailyCheckinsRoomEntity row) {
        return new AthleteDailyCheckIn(
                row.getId(),
                row.getDate(),
                (int) row.getWaterMl(),
                row.getSleepHours(),
                row.getEnergyScore() == null ? null : row.getEnergyScore().intValue(),
                row.getHungerScore() == null ? null : row.getHungerScore().intValue(),
                row.getDigestionScore() == null ? null : row.getDigestionScore().intValue(),
                row.getTrainingReadinessScore() == null
                        ? null : row.getTrainingReadinessScore().intValue(),
                row.getNote()
        );
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private void requireScope(AccountScope scope) {
        if (scope == null || !userId.equals(scope.getOwnerId())) {
            throw new IllegalStateException("The account changed while the recovery operation was pending.");
        }
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }

    private static String normalizeDate(String value) {
        try {
            return LocalDate.parse(value == null ? "" : value.trim()).toString();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("체크인 날짜 형식이 올바르지 않습니다.", error);
        }
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }
}
