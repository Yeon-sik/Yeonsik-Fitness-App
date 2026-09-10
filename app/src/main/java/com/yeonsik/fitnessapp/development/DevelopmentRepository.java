package com.yeonsik.fitnessapp.development;

import com.yeonsik.fitnessapp.core.database.BodyProfileEntity;
import com.yeonsik.fitnessapp.core.database.BodyRoomDao;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.DevelopmentGoalsRoomEntity;
import com.yeonsik.fitnessapp.core.database.DevelopmentRoomDao;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public final class DevelopmentRepository {
    private final FitnessRoomDatabase roomDatabase;
    private final BodyRoomDao bodyDao;
    private final DevelopmentRoomDao developmentDao;
    private String userId;

    public DevelopmentRepository(FitnessRoomDatabase roomDatabase, String userId) {
        if (roomDatabase == null) {
            throw new IllegalArgumentException("DevelopmentRepository에는 Room 데이터베이스가 필요합니다.");
        }
        this.roomDatabase = roomDatabase;
        this.bodyDao = roomDatabase.bodyRoomDao();
        this.developmentDao = roomDatabase.developmentRoomDao();
        this.userId = normalizeUserId(userId);
    }

    public DevelopmentRepository(FitnessRoomDatabase roomDatabase, android.content.Context context, String userId) {
        this(roomDatabase, userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public String currentUserId() {
        return userId;
    }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        if (AccountOwnerPolicy.shouldClaimLocalRows(this.userId, nextUserId)) {
            roomDatabase.runInTransaction(() -> {
                claimBodyProfile(nextUserId);
                claimDevelopmentGoal(nextUserId);
            });
        }
        this.userId = nextUserId;
    }

    public BodyProfile bodyProfile() {
        BodyProfileEntity profile = bodyDao.bodyProfile(userId);
        if (profile != null) {
            return new BodyProfile(
                    profile.getHeightCm(),
                    profile.getCreatedAt(),
                    profile.getUpdatedAt()
            );
        }
        return BodyProfile.empty();
    }

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

    public DevelopmentGoal developmentGoal() {
        DevelopmentGoalsRoomEntity goal = developmentDao.goal(userId);
        if (goal != null) {
            return new DevelopmentGoal(
                    goal.getObjective(),
                    (int) goal.getWeeklySessionsTarget(),
                    goal.getFocusBodyPart(),
                    goal.getEffectiveFrom(),
                    goal.getCreatedAt(),
                    goal.getUpdatedAt()
            );
        }
        return DevelopmentGoal.empty();
    }

    public void saveDevelopmentGoal(DevelopmentGoal goal) {
        if (goal == null) {
            throw new IllegalArgumentException("발전 목표가 필요합니다.");
        }
        if (!goal.isConfigured()) {
            developmentDao.deleteGoal(userId);
            return;
        }
        String now = now();
        DevelopmentGoalsRoomEntity existing = developmentDao.goal(userId);
        developmentDao.replaceGoal(new DevelopmentGoalsRoomEntity(
                userId,
                goal.objective,
                goal.weeklySessionsTarget,
                goal.focusBodyPart,
                goal.effectiveFrom,
                existing == null ? now : existing.getCreatedAt(),
                now
        ));
    }

    private void claimBodyProfile(String nextUserId) {
        BodyProfileEntity source = bodyDao.bodyProfile(SupabaseConfig.DEFAULT_USER_ID);
        BodyProfileEntity target = bodyDao.bodyProfile(nextUserId);
        if (source != null && (target == null || isLater(source.getUpdatedAt(), target.getUpdatedAt()))) {
            bodyDao.replaceBodyProfile(new BodyProfileEntity(
                    nextUserId,
                    source.getHeightCm(),
                    source.getCreatedAt(),
                    source.getUpdatedAt()
            ));
        }
        bodyDao.deleteBodyProfile(SupabaseConfig.DEFAULT_USER_ID);
    }

    private void claimDevelopmentGoal(String nextUserId) {
        DevelopmentGoalsRoomEntity source = developmentDao.goal(SupabaseConfig.DEFAULT_USER_ID);
        DevelopmentGoalsRoomEntity target = developmentDao.goal(nextUserId);
        if (source != null && (target == null || isLater(source.getUpdatedAt(), target.getUpdatedAt()))) {
            developmentDao.replaceGoal(new DevelopmentGoalsRoomEntity(
                    nextUserId,
                    source.getObjective(),
                    source.getWeeklySessionsTarget(),
                    source.getFocusBodyPart(),
                    source.getEffectiveFrom(),
                    source.getCreatedAt(),
                    source.getUpdatedAt()
            ));
        }
        developmentDao.deleteGoal(SupabaseConfig.DEFAULT_USER_ID);
    }

    private static boolean isLater(String candidate, String current) {
        try {
            return OffsetDateTime.parse(candidate).isAfter(OffsetDateTime.parse(current));
        } catch (RuntimeException ignored) {
            return candidate != null && current != null && candidate.compareTo(current) > 0;
        }
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }

    /** Compatibility utility retained for stable development model tests. */
    public static String normalizeReportBodyPart(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) return null;
        if ("가슴".equals(normalized) || "chest".equals(normalized)) return DevelopmentGoal.BODY_PART_CHEST;
        if ("등".equals(normalized) || "back".equals(normalized)) return DevelopmentGoal.BODY_PART_BACK;
        if ("하체".equals(normalized) || "legs".equals(normalized)) return DevelopmentGoal.BODY_PART_LEGS;
        if ("어깨".equals(normalized) || "shoulders".equals(normalized)) return DevelopmentGoal.BODY_PART_SHOULDERS;
        if ("복근".equals(normalized) || "복부".equals(normalized) || "abs".equals(normalized)) {
            return DevelopmentGoal.BODY_PART_ABS;
        }
        if ("팔".equals(normalized) || "이두".equals(normalized) || "삼두".equals(normalized)
                || "arms".equals(normalized) || "biceps".equals(normalized) || "triceps".equals(normalized)) {
            return DevelopmentGoal.BODY_PART_ARMS;
        }
        return null;
    }

    /** Compatibility date validator retained for callers that use the development model utility. */
    public static LocalDate requireReferenceDate(String value) {
        String normalized = value == null ? "" : value.trim();
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("참조일은 YYYY-MM-DD 형식이어야 합니다.", error);
        }
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

}
