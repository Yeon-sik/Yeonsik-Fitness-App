package com.yeonsik.fitnessapp.development;

import com.yeonsik.fitnessapp.core.database.BodyProfileEntity;
import com.yeonsik.fitnessapp.core.database.BodyRoomDao;
import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.DevelopmentGoalsRoomEntity;
import com.yeonsik.fitnessapp.core.database.DevelopmentRoomDao;
import com.yeonsik.fitnessapp.core.database.MealRoomDao;
import com.yeonsik.fitnessapp.core.database.WorkoutRoomDao;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DevelopmentRepository implements com.yeonsik.fitnessapp.feature.development.api.DevelopmentRepositoryApi {
    private static final List<String> REPORT_BODY_PARTS = Arrays.asList(
            DevelopmentGoal.BODY_PART_CHEST,
            DevelopmentGoal.BODY_PART_BACK,
            DevelopmentGoal.BODY_PART_LEGS,
            DevelopmentGoal.BODY_PART_SHOULDERS,
            DevelopmentGoal.BODY_PART_ARMS,
            DevelopmentGoal.BODY_PART_ABS
    );
    private static final String SCOPE_FILTER = "scope IN ('fitness', 'both')";
    private static final String COMPLETED_OR_OS_WORKOUT =
            "(source_app = 'os' OR metadata LIKE '%\"status\":\"completed\"%')";
    private static final String COMPLETED_FITNESS_STRENGTH =
            "wr.source_app = 'fitness' AND wr.metadata LIKE '%\"status\":\"completed\"%' " +
                    "AND wr.workout_type = 'strength'";

    private final FitnessRoomDatabase roomDatabase;
    private final BodyRoomDao bodyDao;
    private final DevelopmentRoomDao developmentDao;
    private final WorkoutRoomDao workoutDao;
    private final MealRoomDao mealDao;
    /** Temporary compatibility dependency for PaperAdviceSnapshotAssembler; removed in D2. */
    private final FitnessDatabaseConnection database;
    private String userId;

    public DevelopmentRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this(legacyRoom(dbHelper), legacyConnection(dbHelper), userId);
    }

    public DevelopmentRepository(FitnessDatabaseConnection database, String userId) {
        this(roomFromLegacy(database), database, userId);
    }

    public DevelopmentRepository(FitnessRoomDatabase roomDatabase, android.content.Context context, String userId) {
        this(roomDatabase, FitnessDatabaseConnection.fromRoom(roomDatabase, context), userId);
    }

    private DevelopmentRepository(
            FitnessRoomDatabase roomDatabase,
            FitnessDatabaseConnection database,
            String userId
    ) {
        if (roomDatabase == null) {
            throw new IllegalArgumentException("DevelopmentRepository에는 Room 데이터베이스가 필요합니다.");
        }
        this.roomDatabase = roomDatabase;
        this.bodyDao = roomDatabase.bodyRoomDao();
        this.developmentDao = roomDatabase.developmentRoomDao();
        this.workoutDao = roomDatabase.workoutRoomDao();
        this.mealDao = roomDatabase.mealRoomDao();
        this.database = database;
        this.userId = normalizeUserId(userId);
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

    @Override
    public DevelopmentReport buildReport(LocalDate referenceDate) {
        LocalDate safeReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        LocalDate weekStart = safeReferenceDate.with(DayOfWeek.MONDAY);
        LocalDate recentWindowEnd = safeReferenceDate;
        LocalDate recentWindowStart = recentWindowEnd.minusDays(13);

        BodyProfile profile = bodyProfile();
        DevelopmentGoal goal = developmentGoal();
        Double latestWeightKg = latestWeightOnOrBefore(safeReferenceDate);
        WeekProgress weekProgress = weekProgress(weekStart, safeReferenceDate);
        Map<String, Integer> setsByBodyPart = recentStrengthSetsByBodyPart(recentWindowStart, recentWindowEnd);
        String focusPart = goal.isConfigured() ? goal.focusBodyPart : null;
        int focusSets = focusPart == null ? 0 : valueOrZero(setsByBodyPart.get(focusPart));
        String focusLastTrainedDate = focusPart == null ? null : latestDetailedTrainingDateForBodyPart(focusPart, safeReferenceDate);
        int mealRecordedDays = recentMealRecordedDays(recentWindowStart, recentWindowEnd);
        CheckInStats checkInStats = recentCheckInStats(recentWindowStart, recentWindowEnd);
        int recentWorkoutRecordedDays = recentWorkoutRecordedDays(recentWindowStart, recentWindowEnd);
        int recentWeightRecordedDays = recentWeightRecordedDays(recentWindowStart, recentWindowEnd);
        int daysWithAnyData = recentDaysWithAnyData(recentWindowStart, recentWindowEnd);
        DevelopmentReport.DataCoverage coverage = new DevelopmentReport.DataCoverage(
                14,
                daysWithAnyData,
                recentWorkoutRecordedDays,
                mealRecordedDays,
                checkInStats.recordedDays,
                recentWeightRecordedDays
        );
        List<DevelopmentInsight> insights = DevelopmentInsightRules.build(
                new DevelopmentInsightRules.Input(
                        safeReferenceDate,
                        recentWindowStart,
                        recentWindowEnd,
                        goal.isConfigured(),
                        goal.weeklySessionsTarget == null ? 0 : goal.weeklySessionsTarget,
                        weekProgress.completedDays,
                        weekProgress.completedSessions,
                        goal.focusBodyPartLabelKo(),
                        focusSets,
                        focusLastTrainedDate,
                        mealRecordedDays,
                        checkInStats.lowEnergyOrReadinessDays,
                        daysWithAnyData,
                        checkInStats.recordedDays,
                        recentWeightRecordedDays,
                        latestWeightKg
                )
        );
        return new DevelopmentReport(
                safeReferenceDate,
                weekStart,
                recentWindowStart,
                recentWindowEnd,
                profile,
                goal,
                latestWeightKg,
                weekProgress.completedDays,
                weekProgress.completedSessions,
                setsByBodyPart,
                focusSets,
                focusLastTrainedDate,
                mealRecordedDays,
                checkInStats.recordedDays,
                checkInStats.lowEnergyOrReadinessDays,
                coverage,
                insights
        );
    }

    /** 발전 화면에서 사용하는 읽기 전용 논문 근거 평가. 원천 기록을 변경하지 않는다. */
    public PaperAdviceAssessment buildPaperAdviceAssessment(LocalDate referenceDate) {
        return new PaperAdviceSnapshotAssembler(database, this).assess(referenceDate);
    }

    private Double latestWeightOnOrBefore(LocalDate referenceDate) {
        com.yeonsik.fitnessapp.core.database.WeightRecordEntity record =
                bodyDao.latestVisibleWeightOnOrBefore(userId, referenceDate.toString());
        return record == null ? null : record.getWeightKg();
    }

    private WeekProgress weekProgress(LocalDate weekStart, LocalDate referenceDate) {
        WorkoutRoomDao.WeekProgress progress = workoutDao.completedWeekProgress(
                userId,
                weekStart.toString(),
                referenceDate.toString()
        );
        return progress == null
                ? new WeekProgress(0, 0)
                : new WeekProgress(progress.getCompletedSessions(), progress.getCompletedDays());
    }

    private Map<String, Integer> recentStrengthSetsByBodyPart(LocalDate startDate, LocalDate endDate) {
        LinkedHashMap<String, Integer> counts = emptyBodyPartCounts();
        for (WorkoutRoomDao.BodyPartSetCount row : workoutDao.recentStrengthSetsByBodyPart(
                userId, startDate.toString(), endDate.toString()
        )) {
            String normalizedPart = normalizeReportBodyPart(row.getUiPart());
            if (normalizedPart != null) {
                counts.put(normalizedPart, counts.get(normalizedPart) + row.getSetCount());
            }
        }
        return counts;
    }

    private String latestDetailedTrainingDateForBodyPart(String bodyPart, LocalDate referenceDate) {
        return workoutDao.latestDetailedTrainingDateForBodyPart(
                userId,
                referenceDate.toString(),
                Arrays.asList(bodyPartAliases(bodyPart))
        );
    }

    private int recentMealRecordedDays(LocalDate startDate, LocalDate endDate) {
        return mealDao.visibleMealRecordedDays(userId, startDate.toString(), endDate.toString());
    }

    private CheckInStats recentCheckInStats(LocalDate startDate, LocalDate endDate) {
        DevelopmentRoomDao.CheckInStats stats = developmentDao.recentCheckInStats(
                userId, startDate.toString(), endDate.toString()
        );
        return stats == null
                ? new CheckInStats(0, 0)
                : new CheckInStats(stats.getRecordedDays(), stats.getLowEnergyOrReadinessDays());
    }

    private int recentWorkoutRecordedDays(LocalDate startDate, LocalDate endDate) {
        return workoutDao.completedWorkoutRecordedDays(userId, startDate.toString(), endDate.toString());
    }

    private int recentWeightRecordedDays(LocalDate startDate, LocalDate endDate) {
        return bodyDao.visibleWeightRecordedDays(userId, startDate.toString(), endDate.toString());
    }

    private int recentDaysWithAnyData(LocalDate startDate, LocalDate endDate) {
        Set<String> dates = new HashSet<>();
        dates.addAll(workoutDao.completedWorkoutDates(userId, startDate.toString(), endDate.toString()));
        dates.addAll(mealDao.visibleMealDates(userId, startDate.toString(), endDate.toString()));
        dates.addAll(bodyDao.visibleWeightDates(userId, startDate.toString(), endDate.toString()));
        dates.addAll(developmentDao.checkInDates(userId, startDate.toString(), endDate.toString()));
        return dates.size();
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

    private static FitnessRoomDatabase legacyRoom(FitnessDatabaseHelper helper) {
        if (helper == null) {
            throw new IllegalArgumentException("DevelopmentRepository에는 데이터베이스 헬퍼가 필요합니다.");
        }
        return FitnessRoomDatabaseProvider.get(FitnessDatabaseConnection.fromLegacy(helper).applicationContext());
    }

    private static FitnessDatabaseConnection legacyConnection(FitnessDatabaseHelper helper) {
        if (helper == null) {
            throw new IllegalArgumentException("DevelopmentRepository에는 데이터베이스 헬퍼가 필요합니다.");
        }
        return FitnessDatabaseConnection.fromLegacy(helper);
    }

    private static FitnessRoomDatabase roomFromLegacy(FitnessDatabaseConnection database) {
        if (database == null) {
            throw new IllegalArgumentException("DevelopmentRepository에는 데이터베이스 연결이 필요합니다.");
        }
        return FitnessRoomDatabaseProvider.get(database.applicationContext());
    }

    private static LinkedHashMap<String, Integer> emptyBodyPartCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (String bodyPart : REPORT_BODY_PARTS) {
            counts.put(bodyPart, 0);
        }
        return counts;
    }

    static String normalizeReportBodyPart(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("가슴".equals(normalized) || "chest".equals(normalized)) return DevelopmentGoal.BODY_PART_CHEST;
        if ("등".equals(normalized) || "back".equals(normalized)) return DevelopmentGoal.BODY_PART_BACK;
        if ("하체".equals(normalized) || "legs".equals(normalized)) return DevelopmentGoal.BODY_PART_LEGS;
        if ("어깨".equals(normalized) || "shoulders".equals(normalized)) return DevelopmentGoal.BODY_PART_SHOULDERS;
        if ("복근".equals(normalized) || "복부".equals(normalized) || "abs".equals(normalized)) {
            return DevelopmentGoal.BODY_PART_ABS;
        }
        if ("팔".equals(normalized)
                || "이두".equals(normalized)
                || "삼두".equals(normalized)
                || "arms".equals(normalized)
                || "biceps".equals(normalized)
                || "triceps".equals(normalized)) {
            return DevelopmentGoal.BODY_PART_ARMS;
        }
        return null;
    }

    private static String[] bodyPartAliases(String bodyPart) {
        if (DevelopmentGoal.BODY_PART_ARMS.equals(bodyPart)) {
            return new String[]{"arms", "팔", "biceps", "이두", "triceps", "삼두"};
        }
        if (DevelopmentGoal.BODY_PART_CHEST.equals(bodyPart)) {
            return new String[]{"chest", "가슴"};
        }
        if (DevelopmentGoal.BODY_PART_BACK.equals(bodyPart)) {
            return new String[]{"back", "등"};
        }
        if (DevelopmentGoal.BODY_PART_LEGS.equals(bodyPart)) {
            return new String[]{"legs", "하체"};
        }
        if (DevelopmentGoal.BODY_PART_SHOULDERS.equals(bodyPart)) {
            return new String[]{"shoulders", "어깨"};
        }
        if (DevelopmentGoal.BODY_PART_ABS.equals(bodyPart)) {
            return new String[]{"abs", "복근", "복부"};
        }
        return new String[]{bodyPart == null ? "" : bodyPart.toLowerCase(Locale.US)};
    }

    private static String normalizeUserId(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : trimmed;
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    static LocalDate requireReferenceDate(String value) {
        String normalized = value == null ? "" : value.trim();
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("참조일은 YYYY-MM-DD 형식이어야 합니다.", error);
        }
    }

    private static final class WeekProgress {
        final int completedSessions;
        final int completedDays;

        WeekProgress(int completedSessions, int completedDays) {
            this.completedSessions = completedSessions;
            this.completedDays = completedDays;
        }
    }

    private static final class CheckInStats {
        final int recordedDays;
        final int lowEnergyOrReadinessDays;

        CheckInStats(int recordedDays, int lowEnergyOrReadinessDays) {
            this.recordedDays = recordedDays;
            this.lowEnergyOrReadinessDays = lowEnergyOrReadinessDays;
        }
    }
}
