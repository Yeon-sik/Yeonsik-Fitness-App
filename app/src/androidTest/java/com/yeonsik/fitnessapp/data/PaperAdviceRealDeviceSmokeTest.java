package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.development.DevelopmentRepository;
import com.yeonsik.fitnessapp.development.PaperAdvice;
import com.yeonsik.fitnessapp.development.PaperAdviceInput;
import com.yeonsik.fitnessapp.development.PaperAdviceSnapshotAssembler;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * 현재 설치된 앱의 실제 로컬 DB를 변경하지 않고 조언 경로만 확인한다.
 *
 * 주의: connectedDebugAndroidTest 종료 시 Gradle이 대상 앱을 제거할 수 있다.
 * 개인 기록이 있는 실기기에서는 실행하지 말고, 별도 테스트 기기나 에뮬레이터를 사용한다.
 */
@RunWith(AndroidJUnit4.class)
public final class PaperAdviceRealDeviceSmokeTest {
    private static final String TAG = "PaperAdviceRealDevice";

    @Test
    public void readsCurrentAppDatabaseWithoutMutatingUserRows() {
        Context context = ApplicationProvider.getApplicationContext();
        SupabaseConfig config = new SupabaseConfigStore(context).load();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            DevelopmentRepository development = new DevelopmentRepository(
                    helper,
                    config.effectiveUserId()
            );
            PaperAdviceSnapshotAssembler adapter = new PaperAdviceSnapshotAssembler(
                    helper,
                    development
            );
            PaperAdviceInput input = adapter.assemble(LocalDate.now());
            List<PaperAdvice> advice = adapter.evaluate(LocalDate.now());

            assertNotNull(input);
            assertNotNull(advice);
            Log.i(TAG, "read_only_smoke "
                    + "goal_configured=" + !input.goal.isEmpty()
                    + " recent_data_days=" + input.recentDataDays
                    + " protein_days=" + input.proteinRecordedDays
                    + " config_identity_present=" + !config.userId.isEmpty()
                    + " config_session_configured=" + config.isConfigured()
                    + " owner_goals=" + countRows(helper, "development_goals", config.effectiveUserId())
                    + " owner_workouts=" + countRows(helper, "workout_records", config.effectiveUserId())
                    + " owner_meals=" + countRows(helper, "meal_records", config.effectiveUserId())
                    + " owner_weights=" + countRows(helper, "weight_records", config.effectiveUserId())
                    + " owner_checkins=" + countRows(helper, "nutrition_daily_checkins", config.effectiveUserId())
                    + " all_goals=" + countAllRows(helper, "development_goals")
                    + " all_workouts=" + countAllRows(helper, "workout_records")
                    + " all_meals=" + countAllRows(helper, "meal_records")
                    + " all_weights=" + countAllRows(helper, "weight_records")
                    + " all_checkins=" + countAllRows(helper, "nutrition_daily_checkins")
                    + " advice_count=" + advice.size()
                    + " advice_ids=" + adviceIds(advice)
                    + " source=local_sqlite");
        } finally {
            helper.close();
        }
    }

    private static String adviceIds(List<PaperAdvice> advice) {
        StringBuilder result = new StringBuilder();
        for (PaperAdvice item : advice) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(item.adviceId).append(':').append(item.status.name());
        }
        return result.toString();
    }

    private static int countRows(
            FitnessDatabaseHelper helper,
            String table,
            String userId
    ) {
        SQLiteDatabase database = helper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?",
                new String[]{userId}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static int countAllRows(FitnessDatabaseHelper helper, String table) {
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + table,
                null
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }
}
