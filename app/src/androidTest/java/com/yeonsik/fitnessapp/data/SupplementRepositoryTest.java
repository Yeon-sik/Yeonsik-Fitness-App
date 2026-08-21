package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.supplement.SupplementPlan;
import com.yeonsik.fitnessapp.supplement.SupplementRepository;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class SupplementRepositoryTest {
    private static final String DATABASE_PREFIX = "supplement_repository_";
    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    public void recordsSnapshotsAndKeepsHistoryAfterPlanEditAndArchive() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(context);
            SupplementRepository repository = new SupplementRepository(helper, USER_ID);
            String date = LocalDate.now().toString();

            repository.savePlan(null, "creatine", "테스트 브랜드", 3, "g", 2,
                    "운동 후", "물과 함께");
            SupplementPlan original = repository.activePlans(date).get(0);
            repository.recordNextDose(original.scheduleId, date, SupplementRepository.STATUS_TAKEN);
            repository.recordNextDose(original.scheduleId, date, SupplementRepository.STATUS_SKIPPED);

            SupplementPlan recorded = repository.activePlans(date).get(0);
            assertEquals(1, recorded.takenCount);
            assertEquals(1, recorded.skippedCount);
            repository.undoLatestRecord(recorded.scheduleId, date);
            assertEquals(1, repository.activePlans(date).get(0).recordedCount());

            repository.savePlan(original, "creatine", "새 브랜드", 5, "g", 2,
                    "아침", "수정된 계획");
            List<SupplementRepository.HistoryEntry> history = repository.history(
                    LocalDate.parse(date), 7);
            assertEquals(1, history.size());
            assertEquals("테스트 브랜드", history.get(0).brandName);
            assertEquals(3, history.get(0).doseAmount, 0.001);
            assertEquals("운동 후", history.get(0).timingLabel);

            // There was already a record today, so the revised schedule starts tomorrow.
            SupplementPlan preservedToday = repository.activePlans(date).get(0);
            assertEquals("테스트 브랜드", preservedToday.brandName);
            assertEquals(3, preservedToday.doseAmount, 0.001);
            SupplementPlan edited = repository.activePlans(LocalDate.now().plusDays(1).toString()).get(0);
            assertEquals("새 브랜드", edited.brandName);
            assertEquals(5, edited.doseAmount, 0.001);
            repository.archivePlan(edited.itemId);
            assertEquals("테스트 브랜드", repository.activePlans(date).get(0).brandName);
            assertEquals(1, repository.history(LocalDate.parse(date), 7).size());
        } finally {
            if (helper != null) helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void preservesEffectiveDatesSlotsSnapshotsAndUserEffectCheckin() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = null;
        try {
            helper = new FitnessDatabaseHelper(context);
            SupplementRepository repository = new SupplementRepository(helper, USER_ID);
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);

            SupplementRepository.PlanSaveResult initial = repository.savePlan(null,
                    "probiotics", "브랜드 A", "캡슐", "digestion", 1, "캡슐",
                    100d, "억 CFU", "10종 혼합", Arrays.asList("아침", "취침 전"), "식후");
            helper.getWritableDatabase().execSQL(
                    "UPDATE supplement_schedules SET effective_from=? WHERE id=?",
                    new Object[]{yesterday.toString(), initial.scheduleId});

            SupplementPlan oldPlan = repository.activePlans(yesterday.toString()).get(0);
            assertEquals(Arrays.asList("아침", "취침 전"), oldPlan.timingLabels);
            assertEquals("digestion", oldPlan.purposeCode);
            assertEquals(100d, oldPlan.activeIngredientAmount, 0.001);
            repository.recordNextDose(oldPlan.scheduleId, yesterday.toString(),
                    SupplementRepository.STATUS_TAKEN);
            SupplementRepository.HistoryEntry backfill = repository.history(today, 7).get(0);
            assertEquals("backfill", backfill.recordSource);
            assertEquals("아침", backfill.timingLabel);
            assertEquals(100d, backfill.activeIngredientAmount, 0.001);

            SupplementRepository.PlanSaveResult revised = repository.savePlan(oldPlan,
                    "probiotics", "브랜드 B", "분말", "digestion", 1, "포",
                    50d, "억 CFU", "단일 균주", Arrays.asList("저녁"), "물과 함께");
            assertEquals(today.toString(), revised.effectiveFrom);
            assertEquals("브랜드 A", repository.activePlans(yesterday.toString()).get(0).brandName);
            assertEquals("브랜드 B", repository.activePlans(today.toString()).get(0).brandName);

            repository.updateRecordStatus(backfill.id, SupplementRepository.STATUS_SKIPPED);
            assertEquals(SupplementRepository.STATUS_SKIPPED, repository.history(today, 7).get(0).status);
            repository.saveEffectCheckin(revised.itemId, today.toString(), 2, "속 불편", "복용 후 관찰");
            SupplementRepository.EffectCheckin effect = repository.latestEffectCheckin(revised.itemId);
            assertEquals(2, effect.effectScore);
            assertEquals("속 불편", effect.adverseEffects);
            repository.deleteRecord(backfill.id);
            assertTrue(repository.history(today, 7).isEmpty());
        } finally {
            if (helper != null) helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void upgradesVersion30SupplementRowsWithoutLosingDoseHistory() {
        Context context = new IsolatedDatabaseContext(ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = null;
        try {
            SQLiteDatabase legacy = context.openOrCreateDatabase(FitnessDatabaseHelper.DATABASE_NAME, 0, null);
            legacy.execSQL("CREATE TABLE supplement_items (id TEXT PRIMARY KEY,user_id TEXT NOT NULL," +
                    "supplement_type_code TEXT NOT NULL,supplement_type_name TEXT NOT NULL,brand_name TEXT NOT NULL DEFAULT ''," +
                    "is_active INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,deleted_at TEXT,device_id TEXT NOT NULL)");
            legacy.execSQL("CREATE TABLE supplement_schedules (id TEXT PRIMARY KEY,user_id TEXT NOT NULL,supplement_item_id TEXT NOT NULL," +
                    "dose_amount REAL NOT NULL,dose_unit TEXT NOT NULL,times_per_day INTEGER NOT NULL,timing_label TEXT NOT NULL," +
                    "instructions TEXT NOT NULL DEFAULT '',is_active INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,deleted_at TEXT,device_id TEXT NOT NULL)");
            legacy.execSQL("CREATE TABLE supplement_intake_records (id TEXT PRIMARY KEY,user_id TEXT NOT NULL,schedule_id TEXT NOT NULL," +
                    "supplement_item_id TEXT NOT NULL,date TEXT NOT NULL,dose_index INTEGER NOT NULL,status TEXT NOT NULL,taken_at TEXT," +
                    "type_code_snapshot TEXT NOT NULL,type_name_snapshot TEXT NOT NULL,brand_name_snapshot TEXT NOT NULL," +
                    "dose_amount_snapshot REAL NOT NULL,dose_unit_snapshot TEXT NOT NULL,timing_label_snapshot TEXT NOT NULL," +
                    "instructions_snapshot TEXT NOT NULL DEFAULT '',created_at TEXT NOT NULL,updated_at TEXT NOT NULL,deleted_at TEXT,device_id TEXT NOT NULL)");
            legacy.execSQL("INSERT INTO supplement_items VALUES ('i',?,'creatine','크레아틴','브랜드',1,'2026-08-18T09:00:00+09:00','2026-08-18T09:00:00+09:00',NULL,'d')", new Object[]{USER_ID});
            legacy.execSQL("INSERT INTO supplement_schedules VALUES ('s',?,'i',3,'g',2,'운동 후','메모',1,'2026-08-18T09:00:00+09:00','2026-08-18T09:00:00+09:00',NULL,'d')", new Object[]{USER_ID});
            legacy.execSQL("INSERT INTO supplement_intake_records VALUES ('r',?,'s','i','2026-08-18',1,'taken','2026-08-18T09:01:00+09:00','creatine','크레아틴','브랜드',3,'g','운동 후','메모','2026-08-18T09:01:00+09:00','2026-08-18T09:01:00+09:00',NULL,'d')", new Object[]{USER_ID});
            legacy.setVersion(30);
            legacy.close();

            helper = new FitnessDatabaseHelper(context);
            SQLiteDatabase upgraded = helper.getWritableDatabase();
            try (Cursor cursor = upgraded.rawQuery("SELECT serving_amount,serving_unit,effective_from,type_name_snapshot FROM supplement_schedules WHERE id='s'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(3d, cursor.getDouble(0), 0.001);
                assertEquals("g", cursor.getString(1));
                assertEquals("2026-08-18", cursor.getString(2));
                assertEquals("크레아틴", cursor.getString(3));
            }
            try (Cursor cursor = upgraded.rawQuery("SELECT COUNT(*) FROM supplement_schedule_slots WHERE schedule_id='s'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(2, cursor.getInt(0));
            }
            try (Cursor cursor = upgraded.rawQuery("SELECT serving_amount_snapshot,record_source FROM supplement_intake_records WHERE id='r'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(3d, cursor.getDouble(0), 0.001);
                assertEquals("on_date", cursor.getString(1));
            }
        } finally {
            if (helper != null) helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private IsolatedDatabaseContext(Context base) { super(base); }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(DATABASE_PREFIX + name);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
            return SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory,
                    SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler);
        }

        @Override
        public boolean deleteDatabase(String name) {
            return SQLiteDatabase.deleteDatabase(getDatabasePath(name));
        }
    }
}
