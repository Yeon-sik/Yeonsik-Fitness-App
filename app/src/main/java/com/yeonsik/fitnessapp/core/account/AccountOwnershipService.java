package com.yeonsik.fitnessapp.core.account;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.database.AccountOwnershipRoomDao;
import com.yeonsik.fitnessapp.core.database.DevicesRoomEntity;
import com.yeonsik.fitnessapp.core.database.DeviceRoomDao;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner;

import java.time.OffsetDateTime;

/** Owns the one-time claim of local rows when the shared account becomes authenticated. */
public final class AccountOwnershipService {
    private static final String DEVICE_ID = "android-local";

    private final AccountOwnershipRoomDao ownershipDao;
    private final DeviceRoomDao deviceDao;
    private final RoomTransactionRunner transactionRunner;
    private String ownerId;

    public AccountOwnershipService(FitnessRoomDatabase roomDatabase, String ownerId) {
        this(roomDatabase, new RoomTransactionRunner(roomDatabase), ownerId);
    }

    public AccountOwnershipService(
            FitnessRoomDatabase roomDatabase,
            RoomTransactionRunner transactionRunner,
            String ownerId
    ) {
        if (roomDatabase == null || transactionRunner == null) {
            throw new IllegalArgumentException("Fitness Room dependencies are required.");
        }
        ownershipDao = roomDatabase.accountOwnershipRoomDao();
        deviceDao = roomDatabase.deviceRoomDao();
        this.transactionRunner = transactionRunner;
        this.ownerId = normalizeUserId(ownerId);
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = normalizeUserId(ownerId);
    }

    public void claimLocalRows(String nextOwnerId) {
        String normalizedOwnerId = normalizeUserId(nextOwnerId);
        transactionRunner.run(() -> {
            if (AccountOwnerPolicy.shouldClaimLocalRows(ownerId, normalizedOwnerId)) {
                claimSimpleRows(normalizedOwnerId);
                ownershipDao.claimNutritionGoal(SupabaseConfig.DEFAULT_USER_ID, normalizedOwnerId);
                ownershipDao.deleteAnonymousNutritionGoal(SupabaseConfig.DEFAULT_USER_ID);
                ownershipDao.claimBodyProfile(SupabaseConfig.DEFAULT_USER_ID, normalizedOwnerId);
                ownershipDao.deleteAnonymousBodyProfile(SupabaseConfig.DEFAULT_USER_ID);
                ownershipDao.claimDevelopmentGoal(SupabaseConfig.DEFAULT_USER_ID, normalizedOwnerId);
                ownershipDao.deleteAnonymousDevelopmentGoal(SupabaseConfig.DEFAULT_USER_ID);
                ownershipDao.claimNutritionDailyCheckins(
                        SupabaseConfig.DEFAULT_USER_ID, normalizedOwnerId
                );
                ownershipDao.deleteAnonymousNutritionDailyCheckins(SupabaseConfig.DEFAULT_USER_ID);
                ownershipDao.claimMealMenuPresets(
                        SupabaseConfig.DEFAULT_USER_ID, normalizedOwnerId
                );
                ownershipDao.deleteAnonymousMealMenuPresets(SupabaseConfig.DEFAULT_USER_ID);
                ownershipDao.deleteAnonymousDevices(SupabaseConfig.DEFAULT_USER_ID);
            }
            ensureDevice(normalizedOwnerId);
        });
        ownerId = normalizedOwnerId;
    }

    private void claimSimpleRows(String nextOwnerId) {
        String sourceOwnerId = SupabaseConfig.DEFAULT_USER_ID;
        ownershipDao.claimWorkoutRecords(sourceOwnerId, nextOwnerId);
        ownershipDao.claimWorkoutExercises(sourceOwnerId, nextOwnerId);
        ownershipDao.claimWorkoutSets(sourceOwnerId, nextOwnerId);
        ownershipDao.claimMealRecords(sourceOwnerId, nextOwnerId);
        ownershipDao.claimDiningOutMenuComponentLinks(sourceOwnerId, nextOwnerId);
        ownershipDao.claimMealRecordItems(sourceOwnerId, nextOwnerId);
        ownershipDao.claimMealRecordItemNutrients(sourceOwnerId, nextOwnerId);
        ownershipDao.claimMealRecordItemComponents(sourceOwnerId, nextOwnerId);
        ownershipDao.claimMealRecordItemComponentNutrients(sourceOwnerId, nextOwnerId);
        ownershipDao.claimMealRecordItemConsumptions(sourceOwnerId, nextOwnerId);
        ownershipDao.claimWeightRecords(sourceOwnerId, nextOwnerId);
        ownershipDao.claimCardioSessions(sourceOwnerId, nextOwnerId);
        ownershipDao.claimCardioRoutePoints(sourceOwnerId, nextOwnerId);
        ownershipDao.claimRoutines(sourceOwnerId, nextOwnerId);
        ownershipDao.claimRoutineExercises(sourceOwnerId, nextOwnerId);
        ownershipDao.claimExercisePickerPreferences(sourceOwnerId, nextOwnerId);
        ownershipDao.claimCompositionTemplates(sourceOwnerId, nextOwnerId);
        ownershipDao.claimCompositionGroups(sourceOwnerId, nextOwnerId);
        ownershipDao.claimCompositionMembers(sourceOwnerId, nextOwnerId);
        ownershipDao.claimDiningOutMenuAddOnLinks(sourceOwnerId, nextOwnerId);
        ownershipDao.claimSupplementItems(sourceOwnerId, nextOwnerId);
        ownershipDao.claimSupplementSchedules(sourceOwnerId, nextOwnerId);
        ownershipDao.claimSupplementScheduleSlots(sourceOwnerId, nextOwnerId);
        ownershipDao.claimSupplementIntakeRecords(sourceOwnerId, nextOwnerId);
        ownershipDao.claimSupplementEffectCheckins(sourceOwnerId, nextOwnerId);
        ownershipDao.claimVerifiedReceiptItems(sourceOwnerId, nextOwnerId);
    }

    private void ensureDevice(String ownerId) {
        String now = OffsetDateTime.now().toString();
        deviceDao.upsert(new DevicesRoomEntity(
                DEVICE_ID,
                normalizeUserId(ownerId),
                "Fitness Android",
                now,
                "0.1.0"
        ));
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }
}
