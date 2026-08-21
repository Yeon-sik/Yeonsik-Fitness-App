package com.yeonsik.fitnessapp.supplement;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
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
public final class SupplementRepository {
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

    private final FitnessDatabaseHelper dbHelper;
    private String userId;

    public SupplementRepository(FitnessDatabaseHelper dbHelper, String userId) {
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) { this.userId = normalizeUserId(userId); }

    public void normalizeLocalUserId(String userId) {
        String nextUserId = normalizeUserId(userId);
        if (AccountOwnerPolicy.shouldClaimLocalRows(this.userId, nextUserId)) {
            SQLiteDatabase database = db();
            database.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put("user_id", nextUserId);
                for (String table : Arrays.asList("supplement_items", "supplement_schedules",
                        "supplement_schedule_slots", "supplement_intake_records", "supplement_effect_checkins")) {
                    database.update(table, values, "user_id = ?", new String[]{SupabaseConfig.DEFAULT_USER_ID});
                }
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
        }
        this.userId = nextUserId;
    }

    /** Returns the schedule version effective on the requested date, including ended plans. */
    public List<SupplementPlan> activePlans(String date) {
        requireDate(date);
        List<SupplementPlan> result = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT i.id,s.id,COALESCE(NULLIF(s.type_code_snapshot,''),i.supplement_type_code)," +
                        "COALESCE(NULLIF(s.type_name_snapshot,''),i.supplement_type_name)," +
                        "COALESCE(NULLIF(s.brand_name_snapshot,''),i.brand_name)," +
                        "COALESCE(NULLIF(s.product_form_snapshot,''),i.product_form)," +
                        "COALESCE(NULLIF(s.purpose_code_snapshot,''),i.purpose_code)," +
                        "COALESCE(s.serving_amount,s.dose_amount),COALESCE(s.serving_unit,s.dose_unit)," +
                        "s.active_ingredient_amount,COALESCE(s.active_ingredient_unit,'')," +
                        "COALESCE(s.ingredient_details,''),s.times_per_day,s.timing_label," +
                        "s.effective_from,s.effective_to," +
                        "CASE WHEN i.is_active=1 AND i.deleted_at IS NULL AND s.is_active=1 AND s.effective_to IS NULL THEN 1 ELSE 0 END," +
                        "COALESCE(SUM(CASE WHEN r.status='taken' THEN 1 ELSE 0 END),0)," +
                        "COALESCE(SUM(CASE WHEN r.status='skipped' THEN 1 ELSE 0 END),0) " +
                        "FROM supplement_schedules s INNER JOIN supplement_items i " +
                        "ON i.id=s.supplement_item_id AND i.user_id=s.user_id " +
                        "LEFT JOIN supplement_intake_records r ON r.schedule_id=s.id AND r.user_id=s.user_id AND r.date=? " +
                        "WHERE s.user_id=? AND s.deleted_at IS NULL AND s.effective_from<=? " +
                        "AND (s.effective_to IS NULL OR s.effective_to>=?) " +
                        "GROUP BY i.id,s.id ORDER BY s.created_at ASC",
                new String[]{date, userId, date, date})) {
            while (cursor.moveToNext()) {
                String scheduleId = cursor.getString(1);
                Double activeAmount = cursor.isNull(9) ? null : cursor.getDouble(9);
                List<String> timings = timingLabels(scheduleId, cursor.getInt(12), cursor.getString(13));
                result.add(new SupplementPlan(cursor.getString(0), scheduleId, cursor.getString(2),
                        cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6),
                        cursor.getDouble(7), cursor.getString(8), activeAmount, cursor.getString(10),
                        cursor.getString(11), cursor.getInt(12), cursor.getString(13), timings,
                        cursor.getString(14), cursor.getString(15), cursor.getInt(16) == 1,
                        cursor.getInt(17), cursor.getInt(18)));
            }
        }
        return result;
    }

    public Progress progress(String date) {
        int planned = 0, taken = 0, skipped = 0;
        for (SupplementPlan plan : activePlans(date)) {
            planned += plan.timesPerDay; taken += plan.takenCount; skipped += plan.skippedCount;
        }
        return new Progress(planned, taken, skipped);
    }

    public AdherenceSummary adherence(LocalDate endDate, int days) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("조회 기간은 1~90일이어야 합니다.");
        int planned = 0, taken = 0, skipped = 0;
        for (int offset = days - 1; offset >= 0; offset--) {
            Progress p = progress(endDate.minusDays(offset).toString());
            planned += p.planned; taken += p.taken; skipped += p.skipped;
        }
        return new AdherenceSummary(planned, taken, skipped);
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
        if (timingLabels == null || timingLabels.isEmpty() || timingLabels.size() > 6)
            throw new IllegalArgumentException("하루 횟수는 1~6회로 입력하세요.");
        for (String timing : timingLabels) if (!TIMING_LABELS.contains(timing))
            throw new IllegalArgumentException("지원하지 않는 복용 시점입니다.");

        String now = now();
        LocalDate today = LocalDate.now();
        String itemId = existing == null ? newId() : existing.itemId;
        String newScheduleId = newId();
        boolean startsTomorrow = false;
        LocalDate effectiveFrom = today;
        int revision = 1;
        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            if (existing == null) {
                ContentValues item = baseValues(itemId, now);
                putItem(item, kind, brand, productForm, purposeCode);
                item.put("is_active", 1);
                database.insertOrThrow("supplement_items", null, item);
            } else {
                requireOwnedPlan(database, itemId, existing.scheduleId);
                LocalDate existingStart = LocalDate.parse(existing.effectiveFrom);
                effectiveFrom = existingStart.isAfter(today) ? existingStart
                        : hasRecords(database, existing.scheduleId, today.toString())
                        ? today.plusDays(1) : today;
                startsTomorrow = effectiveFrom.isAfter(today);
                ContentValues previous = new ContentValues();
                previous.put("is_active", 0);
                previous.put("effective_to", effectiveFrom.minusDays(1).toString());
                previous.put("updated_at", now);
                database.update("supplement_schedules", previous, "id=? AND user_id=?",
                        new String[]{existing.scheduleId, userId});
                ContentValues item = new ContentValues();
                putItem(item, kind, brand, productForm, purposeCode);
                item.put("is_active", 1); item.putNull("deleted_at"); item.put("updated_at", now);
                database.update("supplement_items", item, "id=? AND user_id=?", new String[]{itemId, userId});
                revision = maxRevision(database, itemId) + 1;
            }
            ContentValues schedule = baseValues(newScheduleId, now);
            schedule.put("supplement_item_id", itemId);
            putSchedule(schedule, kind, brand, productForm, purposeCode, servingAmount, servingUnit,
                    activeIngredientAmount, activeIngredientUnit, ingredientDetails, timingLabels, instructions,
                    effectiveFrom.toString(), revision);
            database.insertOrThrow("supplement_schedules", null, schedule);
            insertSlots(database, newScheduleId, timingLabels, now);
            database.setTransactionSuccessful();
            return new PlanSaveResult(itemId, newScheduleId, effectiveFrom.toString(), startsTomorrow);
        } finally { database.endTransaction(); }
    }

    public void recordNextDose(String scheduleId, String date, String status) {
        requireDate(date);
        if (LocalDate.parse(date).isAfter(LocalDate.now())) throw new IllegalArgumentException("미래 날짜는 기록할 수 없습니다.");
        if (!STATUS_TAKEN.equals(status) && !STATUS_SKIPPED.equals(status))
            throw new IllegalArgumentException("지원하지 않는 복용 상태입니다.");
        SQLiteDatabase database = db();
        database.beginTransaction();
        try {
            PlanSnapshot snapshot = requireSnapshot(database, scheduleId, date);
            int doseIndex = nextDoseIndex(database, scheduleId, date, snapshot.timesPerDay);
            Slot slot = slot(database, scheduleId, doseIndex, snapshot.timingLabel);
            String now = now();
            ContentValues values = baseValues(newId(), now);
            values.put("schedule_id", scheduleId); values.put("supplement_item_id", snapshot.itemId);
            if (slot.id == null) values.putNull("schedule_slot_id"); else values.put("schedule_slot_id", slot.id);
            values.put("date", date); values.put("dose_index", doseIndex); values.put("status", status);
            if (STATUS_TAKEN.equals(status)) values.put("taken_at", now); else values.putNull("taken_at");
            values.put("type_code_snapshot", snapshot.typeCode); values.put("type_name_snapshot", snapshot.typeName);
            values.put("brand_name_snapshot", snapshot.brandName);
            values.put("dose_amount_snapshot", snapshot.servingAmount); values.put("dose_unit_snapshot", snapshot.servingUnit);
            values.put("serving_amount_snapshot", snapshot.servingAmount); values.put("serving_unit_snapshot", snapshot.servingUnit);
            if (snapshot.activeIngredientAmount == null) values.putNull("active_ingredient_amount_snapshot");
            else values.put("active_ingredient_amount_snapshot", snapshot.activeIngredientAmount);
            values.put("active_ingredient_unit_snapshot", snapshot.activeIngredientUnit);
            values.put("ingredient_details_snapshot", snapshot.ingredientDetails);
            values.put("timing_label_snapshot", slot.timingLabel); values.put("instructions_snapshot", "");
            values.put("record_source", LocalDate.now().toString().equals(date) ? "on_date" : "backfill");
            database.insertOrThrow("supplement_intake_records", null, values);
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    public void undoLatestRecord(String scheduleId, String date) {
        requireDate(date);
        String id = null;
        try (Cursor cursor = db().rawQuery("SELECT id FROM supplement_intake_records WHERE user_id=? AND schedule_id=? AND date=? ORDER BY dose_index DESC LIMIT 1",
                new String[]{userId, scheduleId, date})) { if (cursor.moveToFirst()) id = cursor.getString(0); }
        if (id == null) throw new IllegalArgumentException("취소할 복용 기록이 없습니다.");
        deleteRecord(id);
    }

    public void updateRecordStatus(String recordId, String status) {
        if (!STATUS_TAKEN.equals(status) && !STATUS_SKIPPED.equals(status)) throw new IllegalArgumentException("지원하지 않는 복용 상태입니다.");
        ContentValues values = new ContentValues();
        values.put("status", status); values.put("updated_at", now());
        if (STATUS_TAKEN.equals(status)) values.put("taken_at", now()); else values.putNull("taken_at");
        if (db().update("supplement_intake_records", values, "id=? AND user_id=?", new String[]{recordId, userId}) == 0)
            throw new IllegalArgumentException("복용 기록을 찾을 수 없습니다.");
    }

    public void deleteRecord(String recordId) {
        if (db().delete("supplement_intake_records", "id=? AND user_id=?", new String[]{recordId, userId}) == 0)
            throw new IllegalArgumentException("복용 기록을 찾을 수 없습니다.");
    }

    public void archivePlan(String itemId) {
        SQLiteDatabase database = db();
        String now = now(), today = LocalDate.now().toString();
        database.beginTransaction();
        try {
            ContentValues item = new ContentValues(); item.put("is_active", 0); item.put("updated_at", now); item.put("deleted_at", now);
            if (database.update("supplement_items", item, "id=? AND user_id=? AND deleted_at IS NULL", new String[]{itemId, userId}) == 0)
                throw new IllegalArgumentException("복용 계획을 찾을 수 없습니다.");
            ContentValues schedule = new ContentValues(); schedule.put("is_active", 0); schedule.put("effective_to", today); schedule.put("updated_at", now);
            database.update("supplement_schedules", schedule, "supplement_item_id=? AND user_id=? AND is_active=1 AND deleted_at IS NULL",
                    new String[]{itemId, userId});
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    public List<HistoryEntry> history(LocalDate endDate, int days) {
        if (days < 1 || days > 90) throw new IllegalArgumentException("조회 기간은 1~90일이어야 합니다.");
        List<HistoryEntry> result = new ArrayList<>();
        try (Cursor cursor = db().rawQuery(
                "SELECT id,date,status,type_name_snapshot,brand_name_snapshot," +
                        "COALESCE(serving_amount_snapshot,dose_amount_snapshot),COALESCE(serving_unit_snapshot,dose_unit_snapshot)," +
                        "timing_label_snapshot,dose_index,taken_at,record_source,created_at," +
                        "active_ingredient_amount_snapshot,COALESCE(active_ingredient_unit_snapshot,''),COALESCE(ingredient_details_snapshot,'') " +
                        "FROM supplement_intake_records WHERE user_id=? AND date BETWEEN ? AND ? " +
                        "ORDER BY date DESC,created_at DESC,dose_index DESC",
                new String[]{userId, endDate.minusDays(days - 1L).toString(), endDate.toString()})) {
            while (cursor.moveToNext()) result.add(new HistoryEntry(cursor.getString(0), cursor.getString(1),
                    cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getDouble(5),
                    cursor.getString(6), cursor.getString(7), cursor.getInt(8), cursor.getString(9),
                    cursor.getString(10), cursor.getString(11), cursor.isNull(12) ? null : cursor.getDouble(12),
                    cursor.getString(13), cursor.getString(14)));
        }
        return result;
    }

    public void saveEffectCheckin(String itemId, String date, int effectScore, String adverseEffects, String note) {
        requireDate(date);
        if (LocalDate.parse(date).isAfter(LocalDate.now()))
            throw new IllegalArgumentException("미래 날짜에는 경과를 기록할 수 없습니다.");
        if (effectScore < 1 || effectScore > 5) throw new IllegalArgumentException("체감 점수는 1~5점입니다.");
        requireOwnedItem(itemId);
        String existingId = null;
        try (Cursor cursor = db().rawQuery("SELECT id FROM supplement_effect_checkins WHERE user_id=? AND supplement_item_id=? AND date=?",
                new String[]{userId, itemId, date})) { if (cursor.moveToFirst()) existingId = cursor.getString(0); }
        String now = now();
        ContentValues values = existingId == null ? baseValues(newId(), now) : new ContentValues();
        values.put("supplement_item_id", itemId); values.put("date", date); values.put("effect_score", effectScore);
        values.put("adverse_effects", trim(adverseEffects)); values.put("note", trim(note)); values.put("updated_at", now);
        if (existingId == null) db().insertOrThrow("supplement_effect_checkins", null, values);
        else db().update("supplement_effect_checkins", values, "id=? AND user_id=?", new String[]{existingId, userId});
    }

    public EffectCheckin latestEffectCheckin(String itemId) {
        try (Cursor cursor = db().rawQuery("SELECT date,effect_score,adverse_effects,note FROM supplement_effect_checkins WHERE user_id=? AND supplement_item_id=? ORDER BY date DESC LIMIT 1",
                new String[]{userId, itemId})) {
            if (cursor.moveToFirst()) return new EffectCheckin(cursor.getString(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3));
        }
        return null;
    }

    private PlanSnapshot requireSnapshot(SQLiteDatabase database, String scheduleId, String date) {
        try (Cursor cursor = database.rawQuery(
                "SELECT s.supplement_item_id,COALESCE(NULLIF(s.type_code_snapshot,''),i.supplement_type_code)," +
                        "COALESCE(NULLIF(s.type_name_snapshot,''),i.supplement_type_name),COALESCE(NULLIF(s.brand_name_snapshot,''),i.brand_name)," +
                        "COALESCE(s.serving_amount,s.dose_amount),COALESCE(s.serving_unit,s.dose_unit),s.active_ingredient_amount," +
                        "COALESCE(s.active_ingredient_unit,''),COALESCE(s.ingredient_details,''),s.times_per_day,s.timing_label " +
                        "FROM supplement_schedules s JOIN supplement_items i ON i.id=s.supplement_item_id AND i.user_id=s.user_id " +
                        "WHERE s.id=? AND s.user_id=? AND s.deleted_at IS NULL AND s.effective_from<=? AND (s.effective_to IS NULL OR s.effective_to>=?) LIMIT 1",
                new String[]{scheduleId, userId, date, date})) {
            if (cursor.moveToFirst()) return new PlanSnapshot(cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getDouble(4), cursor.getString(5), cursor.isNull(6) ? null : cursor.getDouble(6),
                    cursor.getString(7), cursor.getString(8), cursor.getInt(9), cursor.getString(10));
        }
        throw new IllegalArgumentException("선택한 날짜에 적용되는 복용 계획이 아닙니다.");
    }

    private void requireOwnedPlan(SQLiteDatabase database, String itemId, String scheduleId) {
        try (Cursor cursor = database.rawQuery("SELECT 1 FROM supplement_items i JOIN supplement_schedules s ON s.supplement_item_id=i.id AND s.user_id=i.user_id " +
                "WHERE i.id=? AND s.id=? AND i.user_id=? AND i.is_active=1 AND i.deleted_at IS NULL AND s.is_active=1 AND s.deleted_at IS NULL LIMIT 1",
                new String[]{itemId, scheduleId, userId})) { if (cursor.moveToFirst()) return; }
        throw new IllegalArgumentException("수정할 활성 복용 계획을 찾을 수 없습니다.");
    }

    private void requireOwnedItem(String itemId) {
        try (Cursor cursor = db().rawQuery("SELECT 1 FROM supplement_items WHERE id=? AND user_id=? LIMIT 1", new String[]{itemId, userId})) { if (cursor.moveToFirst()) return; }
        throw new IllegalArgumentException("영양제 항목을 찾을 수 없습니다.");
    }

    private List<String> timingLabels(String scheduleId, int total, String fallback) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = db().rawQuery("SELECT timing_label FROM supplement_schedule_slots WHERE user_id=? AND schedule_id=? AND deleted_at IS NULL ORDER BY slot_index",
                new String[]{userId, scheduleId})) { while (cursor.moveToNext()) result.add(cursor.getString(0)); }
        while (result.size() < total) result.add(fallback);
        return result;
    }

    private Slot slot(SQLiteDatabase database, String scheduleId, int index, String fallback) {
        try (Cursor cursor = database.rawQuery("SELECT id,timing_label FROM supplement_schedule_slots WHERE user_id=? AND schedule_id=? AND slot_index=? LIMIT 1",
                new String[]{userId, scheduleId, String.valueOf(index)})) {
            if (cursor.moveToFirst()) return new Slot(cursor.getString(0), cursor.getString(1));
        }
        return new Slot(null, fallback);
    }

    private int nextDoseIndex(SQLiteDatabase database, String scheduleId, String date, int total) {
        boolean[] used = new boolean[total + 1];
        try (Cursor cursor = database.rawQuery("SELECT dose_index FROM supplement_intake_records WHERE user_id=? AND schedule_id=? AND date=?",
                new String[]{userId, scheduleId, date})) { while (cursor.moveToNext()) { int i=cursor.getInt(0); if(i>=1&&i<=total)used[i]=true; } }
        for(int i=1;i<=total;i++)if(!used[i])return i;
        throw new IllegalStateException("이 날짜의 예정 횟수를 모두 기록했습니다.");
    }

    private static void putItem(ContentValues v, SupplementCatalog.Kind kind, String brand, String form, String purpose) {
        v.put("supplement_type_code",kind.code);v.put("supplement_type_name",kind.name);v.put("brand_name",brand);v.put("product_form",form);v.put("purpose_code",purpose);
    }
    private static void putSchedule(ContentValues v, SupplementCatalog.Kind kind, String brand, String form, String purpose,
                                    double serving, String servingUnit, Double active, String activeUnit,
                                    String details, List<String> timings, String instructions, String effectiveFrom, int revision) {
        v.put("dose_amount",serving);v.put("dose_unit",servingUnit);v.put("serving_amount",serving);v.put("serving_unit",servingUnit);
        if(active==null)v.putNull("active_ingredient_amount");else v.put("active_ingredient_amount",active);
        v.put("active_ingredient_unit",active==null?"":activeUnit);v.put("ingredient_details",trim(details));
        v.put("times_per_day",timings.size());v.put("timing_label",String.join(" / ",timings));v.put("effective_from",effectiveFrom);v.putNull("effective_to");
        v.put("type_code_snapshot",kind.code);v.put("type_name_snapshot",kind.name);v.put("brand_name_snapshot",brand);v.put("product_form_snapshot",form);v.put("purpose_code_snapshot",purpose);v.put("revision",revision);
        // The legacy column remains for schema compatibility, but new plans do not collect memos.
        v.put("instructions", "");v.put("is_active",1);
    }
    private void insertSlots(SQLiteDatabase database,String scheduleId,List<String> timings,String now){for(int i=0;i<timings.size();i++){ContentValues v=baseValues(newId(),now);v.put("schedule_id",scheduleId);v.put("slot_index",i+1);v.put("timing_label",timings.get(i));v.putNull("scheduled_time");database.insertOrThrow("supplement_schedule_slots",null,v);}}
    private boolean hasRecords(SQLiteDatabase database,String scheduleId,String date){try(Cursor c=database.rawQuery("SELECT 1 FROM supplement_intake_records WHERE user_id=? AND schedule_id=? AND date=? LIMIT 1",new String[]{userId,scheduleId,date})){return c.moveToFirst();}}
    private int maxRevision(SQLiteDatabase database,String itemId){try(Cursor c=database.rawQuery("SELECT COALESCE(MAX(revision),0) FROM supplement_schedules WHERE user_id=? AND supplement_item_id=?",new String[]{userId,itemId})){return c.moveToFirst()?c.getInt(0):0;}}
    private ContentValues baseValues(String id,String now){ContentValues v=new ContentValues();v.put("id",id);v.put("user_id",userId);v.put("device_id",DEVICE_ID);v.put("created_at",now);v.put("updated_at",now);v.putNull("deleted_at");return v;}
    private SQLiteDatabase db(){return dbHelper.getWritableDatabase();}
    private static String newId(){return UUID.randomUUID().toString();}
    private static String now(){return OffsetDateTime.now().toString();}
    private static String trim(String value){return value==null?"":value.trim();}
    private static String normalizeUserId(String value){return value==null||value.trim().isEmpty()?SupabaseConfig.DEFAULT_USER_ID:value.trim();}
    private static String requiredText(String value,String message){String text=trim(value);if(text.isEmpty())throw new IllegalArgumentException(message);return text;}
    private static void validateAmount(double amount,String label){if(!Double.isFinite(amount)||amount<=0||amount>1_000_000_000d)throw new IllegalArgumentException(label+"은 0보다 큰 값으로 입력하세요.");}
    private static void requireDate(String date){try{LocalDate.parse(date);}catch(Exception e){throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다.");}}
    public static String formatDose(double amount,String unit){return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()+" "+unit;}
    public static String purposeLabel(String code){int i=PURPOSE_CODES.indexOf(code);return i<0?"기타":PURPOSE_LABELS.get(i);}

    public static final class Progress{public final int planned,taken,skipped;public Progress(int p,int t,int s){planned=p;taken=t;skipped=s;}public int unrecorded(){return Math.max(0,planned-taken-skipped);}}
    public static final class AdherenceSummary{public final int planned,taken,skipped,unrecorded;public AdherenceSummary(int p,int t,int s){planned=p;taken=t;skipped=s;unrecorded=Math.max(0,p-t-s);}public int adherencePercent(){return planned==0?0:Math.round(taken*100f/planned);}}
    public static final class PlanSaveResult{public final String itemId,scheduleId,effectiveFrom;public final boolean startsTomorrow;public PlanSaveResult(String i,String s,String e,boolean t){itemId=i;scheduleId=s;effectiveFrom=e;startsTomorrow=t;}}
    public static final class EffectCheckin{public final String date,adverseEffects,note;public final int effectScore;public EffectCheckin(String d,int s,String a,String n){date=d;effectScore=s;adverseEffects=a;note=n;}}
    public static final class HistoryEntry{
        public final String id,date,status,typeName,brandName,doseUnit,timingLabel,takenAt,recordSource,createdAt,activeIngredientUnit,ingredientDetails;public final double doseAmount;public final int doseIndex;public final Double activeIngredientAmount;
        public HistoryEntry(String id,String date,String status,String typeName,String brandName,double doseAmount,String doseUnit,String timingLabel,int doseIndex,String takenAt,String recordSource,String createdAt,Double activeIngredientAmount,String activeIngredientUnit,String ingredientDetails){this.id=id;this.date=date;this.status=status;this.typeName=typeName;this.brandName=brandName;this.doseAmount=doseAmount;this.doseUnit=doseUnit;this.timingLabel=timingLabel;this.doseIndex=doseIndex;this.takenAt=takenAt;this.recordSource=recordSource;this.createdAt=createdAt;this.activeIngredientAmount=activeIngredientAmount;this.activeIngredientUnit=activeIngredientUnit;this.ingredientDetails=ingredientDetails;}}
    private static final class Slot{final String id,timingLabel;Slot(String i,String t){id=i;timingLabel=t;}}
    private static final class PlanSnapshot{
        final String itemId,typeCode,typeName,brandName,servingUnit,activeIngredientUnit,ingredientDetails,timingLabel;final double servingAmount;final Double activeIngredientAmount;final int timesPerDay;
        PlanSnapshot(String i,String tc,String tn,String b,double sa,String su,Double aa,String au,String d,int t,String tl){itemId=i;typeCode=tc;typeName=tn;brandName=b;servingAmount=sa;servingUnit=su;activeIngredientAmount=aa;activeIngredientUnit=au;ingredientDetails=d;timesPerDay=t;timingLabel=tl;}}
}
