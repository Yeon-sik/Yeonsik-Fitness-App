package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.development.PaperAdvice;
import com.yeonsik.fitnessapp.development.PaperAdviceInput;
import com.yeonsik.fitnessapp.feature.development.application.PaperAdviceSnapshotAssembler;
import com.yeonsik.fitnessapp.feature.body.data.BodyMetricsReadRepository;
import com.yeonsik.fitnessapp.feature.development.data.DevelopmentReadRepository;
import com.yeonsik.fitnessapp.feature.meal.data.MealReadRepository;
import com.yeonsik.fitnessapp.feature.workout.data.WorkoutReadRepository;

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
        FitnessRoomDatabase roomDatabase = FitnessRoomDatabaseProvider.get(context);
        SupportSQLiteDatabase database = roomDatabase.getOpenHelper().getReadableDatabase();
        PaperAdviceSnapshotAssembler adapter = new PaperAdviceSnapshotAssembler(
                new WorkoutReadRepository(roomDatabase, context),
                new MealReadRepository(roomDatabase),
                new BodyMetricsReadRepository(roomDatabase),
                new DevelopmentReadRepository(roomDatabase),
                config.effectiveUserId()
        );
        PaperAdviceInput input = adapter.assemble(LocalDate.now());
        List<PaperAdvice> advice = adapter.evaluate(LocalDate.now());

        assertNotNull(input);
        assertNotNull(advice);
        Log.i(TAG, "read_only_smoke "
                + "schema_version=" + database.getVersion()
                + " goal_configured=" + !input.goal.isEmpty()
                + " recent_data_days=" + input.recentDataDays
                + " protein_days=" + input.proteinRecordedDays
                + " config_identity_present=" + !config.userId.isEmpty()
                + " config_session_configured=" + config.isConfigured()
                + " owner_goals=" + countRows(database, "development_goals", config.effectiveUserId())
                + " owner_workouts=" + countRows(database, "workout_records", config.effectiveUserId())
                + " owner_meals=" + countRows(database, "meal_records", config.effectiveUserId())
                + " owner_weights=" + countRows(database, "weight_records", config.effectiveUserId())
                + " owner_checkins=" + countRows(database, "nutrition_daily_checkins", config.effectiveUserId())
                + " all_goals=" + countAllRows(database, "development_goals")
                + " all_workouts=" + countAllRows(database, "workout_records")
                + " all_meals=" + countAllRows(database, "meal_records")
                + " all_weights=" + countAllRows(database, "weight_records")
                + " all_checkins=" + countAllRows(database, "nutrition_daily_checkins")
                + " advice_count=" + advice.size()
                + " advice_ids=" + adviceIds(advice)
                + " source=local_sqlite");
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
            SupportSQLiteDatabase database,
            String table,
            String userId
    ) {
        try (Cursor cursor = database.query(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?",
                new Object[]{userId}
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private static int countAllRows(SupportSQLiteDatabase database, String table) {
        try (Cursor cursor = database.query("SELECT COUNT(*) FROM " + table)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }
}
