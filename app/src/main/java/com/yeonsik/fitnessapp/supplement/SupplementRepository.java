package com.yeonsik.fitnessapp.supplement;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.SupplementEffectCheckinsRoomEntity;
import com.yeonsik.fitnessapp.core.database.SupplementIntakeRecordsRoomEntity;
import com.yeonsik.fitnessapp.core.database.SupplementItemsRoomEntity;
import com.yeonsik.fitnessapp.core.database.SupplementRoomDao;
import com.yeonsik.fitnessapp.core.database.SupplementScheduleSlotsRoomEntity;
import com.yeonsik.fitnessapp.core.database.SupplementSchedulesRoomEntity;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Local-first supplement plans, effective-dated schedules and intake snapshots. */
public final class SupplementRepository implements com.yeonsik.fitnessapp.feature.supplement.api.SupplementRepositoryApi {
    public static final List<String> DOSE_UNITS = Collections.unmodifiableList(Arrays.asList(
            "mg", "g", "µg", "IU", "mL", "정", "캡슐", "포", "스쿱", "방울", "CFU", "억 CFU"));
    public static final List<String> TIMING_LABELS = Collections.unmodifiableList(Arrays.asList(
            "아침", "점심", "저녁", "취침 전", "운동 전", "운동 후", "식사와 함께", "상관없음"));
    public static final List<String> PRODUCT_FORMS = Collections.unmodifiableList(Arrays.asList(
            "정제", "캡슐", "분말", "액상", "구미", "기타"));
    public static final List<String> PURPOSE_CODES = Collections.unmodifiableList(Arrays.asList(
            "general_health", "performance", "recovery", "sleep", "digestion", "deficiency", "other"));
    public static final List<String> PURPOSE_LABELS = Collections.unmodifiableList(Arrays.asList(
            "일반 건강", "운동 수행", "회복", "수면", "소화", "결핍 보충", "기타"));
    public static final String STATUS_TAKEN = "taken";
    public static final String STATUS_SKIPPED = "skipped";
    private static final String DEVICE_ID = "android-local";

    private final FitnessRoomDatabase roomDatabase;
    private final SupplementRoomDao supplementDao;
    private String userId;

    /** Compatibility fixture constructor; runtime storage still goes through Room DAOs. */
    @Deprecated
    public SupplementRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this(FitnessRoomDatabaseProvider.get(dbHelper.applicationContext()), userId, dbHelper.applicationContext());
    }

    public SupplementRepository(FitnessRoomDatabase roomDatabase, String userId, android.content.Context context) {
        if (roomDatabase == null || context == null) {
            throw new IllegalArgumentException("SupplementRepository requires Room and context.");
        }
        this.roomDatabase = roomDatabase;
        this.supplementDao = roomDatabase.supplementRoomDao();
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        if (AccountOwnerPolicy.shouldClaimLocalRows(this.userId, nextUserId)) {
            roomDatabase.runInTransaction(() -> {
                supplementDao.claimItems(SupabaseConfig.DEFAULT_USER_ID, nextUserId);
                supplementDao.claimSchedules(SupabaseConfig.DEFAULT_USER_ID, nextUserId);
                supplementDao.claimSlots(SupabaseConfig.DEFAULT_USER_ID, nextUserId);
                supplementDao.claimIntakeRecords(SupabaseConfig.DEFAULT_USER_ID, nextUserId);
                supplementDao.claimEffectCheckins(SupabaseConfig.DEFAULT_USER_ID, nextUserId);
            });
        }
        this.userId = nextUserId;
    }

    /** Returns the schedule version effective on the requested date, including ended plans. */
    public List<SupplementPlan> activePlans(String date) {
        requireDate(date);
        List<SupplementPlan> result = new ArrayList<>();
        for (SupplementRoomDao.ActivePlanRow row : supplementDao.activePlans(userId, date)) {
            List<String> timings = timingLabels(row.getScheduleId(), (int) row.getTimesPerDay(), row.getTimingLabel());
            result.add(new SupplementPlan(
                    row.getItemId(), row.getScheduleId(), row.getTypeCode(), row.getTypeName(),
                    row.getBrandName(), row.getProductForm(), row.getPurposeCode(), row.getServingAmount(),
                    row.getServingUnit(), row.getActiveIngredientAmount(), row.getActiveIngredientUnit(),
                    row.getIngredientDetails(), (int) row.getTimesPerDay(), row.getTimingLabel(), timings,
                    row.getEffectiveFrom(), row.getEffectiveTo(), row.getCurrentlyActive() == 1,
                    (int) row.getTakenCount(), (int) row.getSkippedCount()
            ));
        }
        return result;
    }

    public Progress progress(String date) {
        int planned = 0;
        int taken = 0;
        int skipped = 0;
        for (SupplementPlan plan : activePlans(date)) {
            planned += plan.timesPerDay;
            taken += plan.takenCount;
            skipped += plan.skippedCount;
        }
        return new Progress(planned, taken, skipped);
    }

    public AdherenceSummary adherence(LocalDate endDate, int days) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("조회 기간은 1~90일이어야 합니다.");
        int planned = 0;
        int taken = 0;
        int skipped = 0;
        for (int offset = days - 1; offset >= 0; offset--) {
            Progress progress = progress(endDate.minusDays(offset).toString());
            planned += progress.planned;
            taken += progress.taken;
            skipped += progress.skipped;
        }
        return new AdherenceSummary(planned, taken, skipped);
    }

    @Override
    public com.yeonsik.fitnessapp.feature.supplement.model.SupplementProgress loadProgress(String date) {
        Progress value = progress(date);
        return new com.yeonsik.fitnessapp.feature.supplement.model.SupplementProgress(
                value.planned, value.taken, value.skipped);
    }

    @Override
    public com.yeonsik.fitnessapp.feature.supplement.model.SupplementAdherence loadAdherence(
            LocalDate endDate, int days
    ) {
        AdherenceSummary value = adherence(endDate, days);
        return new com.yeonsik.fitnessapp.feature.supplement.model.SupplementAdherence(
                value.planned, value.taken, value.skipped);
    }

    /** Compatibility overload for existing callers and v30 records. */
    public String savePlan(SupplementPlan existing, String typeCode, String brandName,
                           double doseAmount, String doseUnit, int timesPerDay,
                           String timingLabel, String instructions) {
        List<String> timings = new ArrayList<>();
        for (int i = 0; i < timesPerDay; i++) timings.add(timingLabel);
        return savePlan(existing, typeCode, brandName, "기타", "general_health", doseAmount,
                doseUnit, null, "", "", timings, instructions).itemId;
    }

    public PlanSaveResult savePlan(SupplementPlan existing, String typeCode, String brandName,
                                   String productForm, String purposeCode,
                                   double servingAmount, String servingUnit,
                                   Double activeIngredientAmount, String activeIngredientUnit,
                                   String ingredientDetails, List<String> timingLabels,
                                   String instructions) {
        SupplementCatalog.Kind kind = SupplementCatalog.require(typeCode);
        String brand = requiredText(brandName, "브랜드를 입력하세요.");
        if (!PRODUCT_FORMS.contains(productForm)) throw new IllegalArgumentException("제품 형태를 선택하세요.");
        if (!PURPOSE_CODES.contains(purposeCode)) throw new IllegalArgumentException("복용 목적을 선택하세요.");
        validateAmount(servingAmount, "1회 섭취량");
        if (!DOSE_UNITS.contains(servingUnit)) throw new IllegalArgumentException("지원하지 않는 섭취량 단위입니다.");
        if (activeIngredientAmount != null) {
            validateAmount(activeIngredientAmount, "주요 성분량");
            if (!DOSE_UNITS.contains(activeIngredientUnit)) throw new IllegalArgumentException("주요 성분 단위를 선택하세요.");
        }
        if (timingLabels == null || timingLabels.isEmpty() || timingLabels.size() > 6) {
            throw new IllegalArgumentException("하루 횟수는 1~6회로 입력하세요.");
        }
        for (String timing : timingLabels) {
            if (!TIMING_LABELS.contains(timing)) throw new IllegalArgumentException("지원하지 않는 복용 시점입니다.");
        }

        String now = now();
        LocalDate today = LocalDate.now();
        String itemId = existing == null ? newId() : existing.itemId;
        String newScheduleId = newId();
        LocalDate effectiveFrom = today;
        boolean startsTomorrow = false;
        int revision = 1;
        final PlanSaveResult[] result = new PlanSaveResult[1];
        effectiveFromHolder.value = today;
        startsTomorrowHolder.value = false;
        revisionHolder.value = revision;

        roomDatabase.runInTransaction(() -> {
            if (existing == null) {
                supplementDao.insertItem(new SupplementItemsRoomEntity(
                        itemId, userId, kind.code, kind.name, brand, productForm, purposeCode,
                        1, now, now, null, DEVICE_ID
                ));
            } else {
                requireOwnedPlan(itemId, existing.scheduleId);
                LocalDate existingStart = LocalDate.parse(existing.effectiveFrom);
                effectiveFromHolder.value = existingStart.isAfter(today)
                        ? existingStart
                        : hasRecords(existing.scheduleId, today.toString()) ? today.plusDays(1) : today;
                startsTomorrowHolder.value = effectiveFromHolder.value.isAfter(today);
                supplementDao.closeSchedule(
                        existing.scheduleId, userId, effectiveFromHolder.value.minusDays(1).toString(), now);
                supplementDao.refreshItem(
                        itemId, userId, kind.code, kind.name, brand, productForm, purposeCode, now);
                revisionHolder.value = (int) supplementDao.maxRevision(userId, itemId) + 1;
            }
            LocalDate effective = effectiveFromHolder.value;
            supplementDao.insertSchedule(new SupplementSchedulesRoomEntity(
                    newScheduleId, userId, itemId, servingAmount, servingUnit, servingAmount, servingUnit,
                    activeIngredientAmount, activeIngredientAmount == null ? "" : activeIngredientUnit,
                    trim(ingredientDetails), timingLabels.size(), String.join(" / ", timingLabels),
                    effective.toString(), null, kind.code, kind.name, brand, productForm, purposeCode,
                    revisionHolder.value, "", 1, now, now, null, DEVICE_ID
            ));
            insertSlots(newScheduleId, timingLabels, now);
            result[0] = new PlanSaveResult(
                    itemId, newScheduleId, effective.toString(), startsTomorrowHolder.value);
        });
        return result[0];
    }

    private final MutableDateHolder effectiveFromHolder = new MutableDateHolder();
    private final MutableBooleanHolder startsTomorrowHolder = new MutableBooleanHolder();
    private final MutableIntHolder revisionHolder = new MutableIntHolder();

    public void recordNextDose(String scheduleId, String date, String status) {
        requireDate(date);
        if (LocalDate.parse(date).isAfter(LocalDate.now())) throw new IllegalArgumentException("미래 날짜는 기록할 수 없습니다.");
        if (!STATUS_TAKEN.equals(status) && !STATUS_SKIPPED.equals(status)) {
            throw new IllegalArgumentException("지원하지 않는 복용 상태입니다.");
        }
        roomDatabase.runInTransaction(() -> {
            PlanSnapshot snapshot = requireSnapshot(scheduleId, date);
            int doseIndex = nextDoseIndex(scheduleId, date, snapshot.timesPerDay);
            Slot slot = slot(scheduleId, doseIndex, snapshot.timingLabel);
            String now = now();
            supplementDao.insertIntakeRecord(new SupplementIntakeRecordsRoomEntity(
                    newId(), userId, scheduleId, snapshot.itemId, slot.id, date, doseIndex,
                    status, STATUS_TAKEN.equals(status) ? now : null,
                    snapshot.typeCode, snapshot.typeName, snapshot.brandName,
                    snapshot.servingAmount, snapshot.servingUnit, snapshot.servingAmount,
                    snapshot.servingUnit, snapshot.activeIngredientAmount,
                    snapshot.activeIngredientUnit, snapshot.ingredientDetails, slot.timingLabel, "",
                    LocalDate.now().toString().equals(date) ? "on_date" : "backfill",
                    now, now, null, DEVICE_ID
            ));
        });
    }

    public void undoLatestRecord(String scheduleId, String date) {
        requireDate(date);
        String id = supplementDao.latestRecordId(userId, scheduleId, date);
        if (id == null) throw new IllegalArgumentException("취소할 복용 기록이 없습니다.");
        deleteRecord(id);
    }

    public void updateRecordStatus(String recordId, String status) {
        if (!STATUS_TAKEN.equals(status) && !STATUS_SKIPPED.equals(status)) {
            throw new IllegalArgumentException("지원하지 않는 복용 상태입니다.");
        }
        String now = now();
        if (supplementDao.updateRecordStatus(
                recordId, userId, status, STATUS_TAKEN.equals(status) ? now : null, now) == 0) {
            throw new IllegalArgumentException("복용 기록을 찾을 수 없습니다.");
        }
    }

    public void deleteRecord(String recordId) {
        if (supplementDao.deleteRecord(recordId, userId) == 0) {
            throw new IllegalArgumentException("복용 기록을 찾을 수 없습니다.");
        }
    }

    public void archivePlan(String itemId) {
        String now = now();
        String today = LocalDate.now().toString();
        roomDatabase.runInTransaction(() -> {
            if (supplementDao.archiveItem(itemId, userId, now, now) == 0) {
                throw new IllegalArgumentException("복용 계획을 찾을 수 없습니다.");
            }
            supplementDao.archiveSchedules(itemId, userId, today, now);
        });
    }

    public List<HistoryEntry> history(LocalDate endDate, int days) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("조회 기간은 1~90일이어야 합니다.");
        List<HistoryEntry> result = new ArrayList<>();
        for (SupplementIntakeRecordsRoomEntity row : supplementDao.history(
                userId, endDate.minusDays(days - 1L).toString(), endDate.toString())) {
            result.add(new HistoryEntry(
                    row.getId(), row.getDate(), row.getStatus(), row.getTypeNameSnapshot(),
                    row.getBrandNameSnapshot(), row.getServingAmountSnapshot() == null
                            ? row.getDoseAmountSnapshot() : row.getServingAmountSnapshot(),
                    row.getServingUnitSnapshot() == null ? row.getDoseUnitSnapshot() : row.getServingUnitSnapshot(),
                    row.getTimingLabelSnapshot(), (int) row.getDoseIndex(), row.getTakenAt(), row.getRecordSource(),
                    row.getCreatedAt(), row.getActiveIngredientAmountSnapshot(),
                    row.getActiveIngredientUnitSnapshot() == null ? "" : row.getActiveIngredientUnitSnapshot(),
                    row.getIngredientDetailsSnapshot() == null ? "" : row.getIngredientDetailsSnapshot()
            ));
        }
        return result;
    }

    public void saveEffectCheckin(String itemId, String date, int effectScore,
                                  String adverseEffects, String note) {
        requireDate(date);
        if (LocalDate.parse(date).isAfter(LocalDate.now())) throw new IllegalArgumentException("미래 날짜에는 경과를 기록할 수 없습니다.");
        if (effectScore < 1 || effectScore > 5) throw new IllegalArgumentException("체감 점수는 1~5점입니다.");
        requireOwnedItem(itemId);
        String now = now();
        String existingId = supplementDao.effectCheckinId(userId, itemId, date);
        if (existingId == null) {
            supplementDao.insertEffectCheckin(new SupplementEffectCheckinsRoomEntity(
                    newId(), userId, itemId, date, effectScore, trim(adverseEffects), trim(note),
                    now, now, null, DEVICE_ID
            ));
        } else {
            supplementDao.updateEffectCheckin(
                    existingId, userId, itemId, date, effectScore, trim(adverseEffects), trim(note), now);
        }
    }

    public EffectCheckin latestEffectCheckin(String itemId) {
        SupplementEffectCheckinsRoomEntity row = supplementDao.latestEffectCheckin(userId, itemId);
        return row == null ? null : new EffectCheckin(
                row.getDate(), (int) row.getEffectScore(), row.getAdverseEffects(), row.getNote());
    }

    private PlanSnapshot requireSnapshot(String scheduleId, String date) {
        SupplementRoomDao.PlanSnapshotRow row = supplementDao.planSnapshot(scheduleId, userId, date);
        if (row == null) throw new IllegalArgumentException("선택한 날짜에 적용되는 복용 계획이 아닙니다.");
        return new PlanSnapshot(
                row.getItemId(), row.getTypeCode(), row.getTypeName(), row.getBrandName(),
                row.getServingAmount(), row.getServingUnit(), row.getActiveIngredientAmount(),
                row.getActiveIngredientUnit(), row.getIngredientDetails(), (int) row.getTimesPerDay(),
                row.getTimingLabel()
        );
    }

    private void requireOwnedPlan(String itemId, String scheduleId) {
        if (supplementDao.ownedActivePlan(itemId, scheduleId, userId) == null) {
            throw new IllegalArgumentException("수정할 활성 복용 계획을 찾을 수 없습니다.");
        }
    }

    private void requireOwnedItem(String itemId) {
        if (supplementDao.ownedItem(itemId, userId) == null) {
            throw new IllegalArgumentException("영양제 항목을 찾을 수 없습니다.");
        }
    }

    private List<String> timingLabels(String scheduleId, int total, String fallback) {
        List<String> result = new ArrayList<>();
        for (SupplementScheduleSlotsRoomEntity row : supplementDao.visibleSlots(userId, scheduleId)) {
            result.add(row.getTimingLabel());
        }
        while (result.size() < total) result.add(fallback);
        return result;
    }

    private Slot slot(String scheduleId, int index, String fallback) {
        SupplementScheduleSlotsRoomEntity row = supplementDao.slot(userId, scheduleId, index);
        return row == null ? new Slot(null, fallback) : new Slot(row.getId(), row.getTimingLabel());
    }

    private int nextDoseIndex(String scheduleId, String date, int total) {
        boolean[] used = new boolean[total + 1];
        for (Long value : supplementDao.usedDoseIndexes(userId, scheduleId, date)) {
            int index = value.intValue();
            if (index >= 1 && index <= total) used[index] = true;
        }
        for (int index = 1; index <= total; index++) if (!used[index]) return index;
        throw new IllegalStateException("이 날짜의 예정 횟수를 모두 기록했습니다.");
    }

    private boolean hasRecords(String scheduleId, String date) {
        return supplementDao.hasRecords(userId, scheduleId, date) != null;
    }

    private void insertSlots(String scheduleId, List<String> timings, String now) {
        for (int index = 0; index < timings.size(); index++) {
            supplementDao.insertSlot(new SupplementScheduleSlotsRoomEntity(
                    newId(), userId, scheduleId, index + 1, timings.get(index), null,
                    now, now, null, DEVICE_ID
            ));
        }
    }

    private static String newId() { return UUID.randomUUID().toString(); }
    private static String now() { return OffsetDateTime.now().toString(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String normalizeUserId(String value) {
        return value == null || value.trim().isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : value.trim();
    }
    private static String requiredText(String value, String message) {
        String text = trim(value);
        if (text.isEmpty()) throw new IllegalArgumentException(message);
        return text;
    }
    private static void validateAmount(double amount, String label) {
        if (!Double.isFinite(amount) || amount <= 0 || amount > 1_000_000_000d) {
            throw new IllegalArgumentException(label + "은 0보다 큰 값으로 입력하세요.");
        }
    }
    private static void requireDate(String date) {
        try { LocalDate.parse(date); } catch (Exception error) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다.");
        }
    }
    public static String formatDose(double amount, String unit) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString() + " " + unit;
    }
    public static String purposeLabel(String code) {
        int index = PURPOSE_CODES.indexOf(code);
        return index < 0 ? "기타" : PURPOSE_LABELS.get(index);
    }

    public static final class Progress {
        public final int planned, taken, skipped;
        public Progress(int planned, int taken, int skipped) { this.planned = planned; this.taken = taken; this.skipped = skipped; }
        public int unrecorded() { return Math.max(0, planned - taken - skipped); }
    }
    public static final class AdherenceSummary {
        public final int planned, taken, skipped, unrecorded;
        public AdherenceSummary(int planned, int taken, int skipped) {
            this.planned = planned; this.taken = taken; this.skipped = skipped; this.unrecorded = Math.max(0, planned - taken - skipped);
        }
        public int adherencePercent() { return planned == 0 ? 0 : Math.round(taken * 100f / planned); }
    }
    public static final class PlanSaveResult {
        public final String itemId, scheduleId, effectiveFrom;
        public final boolean startsTomorrow;
        public PlanSaveResult(String itemId, String scheduleId, String effectiveFrom, boolean startsTomorrow) {
            this.itemId = itemId; this.scheduleId = scheduleId; this.effectiveFrom = effectiveFrom; this.startsTomorrow = startsTomorrow;
        }
    }
    public static final class EffectCheckin {
        public final String date, adverseEffects, note;
        public final int effectScore;
        public EffectCheckin(String date, int effectScore, String adverseEffects, String note) {
            this.date = date; this.effectScore = effectScore; this.adverseEffects = adverseEffects; this.note = note;
        }
    }
    public static final class HistoryEntry {
        public final String id, date, status, typeName, brandName, doseUnit, timingLabel, takenAt, recordSource, createdAt, activeIngredientUnit, ingredientDetails;
        public final double doseAmount;
        public final int doseIndex;
        public final Double activeIngredientAmount;
        public HistoryEntry(String id, String date, String status, String typeName, String brandName,
                            double doseAmount, String doseUnit, String timingLabel, int doseIndex,
                            String takenAt, String recordSource, String createdAt, Double activeIngredientAmount,
                            String activeIngredientUnit, String ingredientDetails) {
            this.id = id; this.date = date; this.status = status; this.typeName = typeName; this.brandName = brandName;
            this.doseAmount = doseAmount; this.doseUnit = doseUnit; this.timingLabel = timingLabel; this.doseIndex = doseIndex;
            this.takenAt = takenAt; this.recordSource = recordSource; this.createdAt = createdAt;
            this.activeIngredientAmount = activeIngredientAmount; this.activeIngredientUnit = activeIngredientUnit;
            this.ingredientDetails = ingredientDetails;
        }
    }
    private static final class Slot { final String id, timingLabel; Slot(String id, String timingLabel) { this.id = id; this.timingLabel = timingLabel; } }
    private static final class PlanSnapshot {
        final String itemId, typeCode, typeName, brandName, servingUnit, activeIngredientUnit, ingredientDetails, timingLabel;
        final double servingAmount;
        final Double activeIngredientAmount;
        final int timesPerDay;
        PlanSnapshot(String itemId, String typeCode, String typeName, String brandName, double servingAmount,
                     String servingUnit, Double activeIngredientAmount, String activeIngredientUnit,
                     String ingredientDetails, int timesPerDay, String timingLabel) {
            this.itemId = itemId; this.typeCode = typeCode; this.typeName = typeName; this.brandName = brandName;
            this.servingAmount = servingAmount; this.servingUnit = servingUnit; this.activeIngredientAmount = activeIngredientAmount;
            this.activeIngredientUnit = activeIngredientUnit; this.ingredientDetails = ingredientDetails;
            this.timesPerDay = timesPerDay; this.timingLabel = timingLabel;
        }
    }
    private static final class MutableDateHolder { LocalDate value = LocalDate.now(); }
    private static final class MutableBooleanHolder { boolean value; }
    private static final class MutableIntHolder { int value = 1; }
}
