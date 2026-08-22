package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.supplement.SupplementRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 피트니스 허브: 무산소·유산소·식단 하위 화면과 신체 기록 진입을 소유한다.
 */
public final class WorkoutScreen extends BaseScreen {

    public WorkoutScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String today = host.today();
        String inProgressSessionId = repository().latestInProgressSessionId();

        screenHeader("운동 선택", "피트니스");

        if (inProgressSessionId != null) {
            continueBanner();
        }

        section("피트니스 메뉴");
        LinearLayout workoutTypeRow = ui().tileRow();
        workoutTypeRow.addView(ui().hologramStatTile(
                "무산소", "근력·루틴", "탭하여 들어가기",
                v -> host.navigate(FitnessScreen.STRENGTH)), ui().tileParams(true));
        workoutTypeRow.addView(ui().hologramStatTile(
                "유산소", "GPS", "탭하여 들어가기",
                v -> host.navigate(FitnessScreen.CARDIO)), ui().tileParams(false));
        add(workoutTypeRow, ui().fullWidthParams(0));

        add(ui().button("지난 운동 수동 등록", false, v -> host.showPastWorkoutDialog()),
                ui().fullWidthParams(ui().dp(10)));

        add(ui().hologramStatTile(
                "식단",
                "오늘 " + repository().mealCountForDate(today) + "건",
                "음식·메뉴·영양 관리",
                v -> host.openMealManagement()
        ), ui().fullWidthParams(ui().dp(10)));

        SupplementRepository.Progress supplementProgress = host.supplementRepository().progress(today);
        add(ui().hologramStatTile(
                "영양제",
                supplementProgress.planned == 0
                        ? "복용 계획 없음"
                        : "오늘 " + supplementProgress.taken + " / " + supplementProgress.planned + "회",
                supplementProgress.skipped == 0
                        ? "복용 계획·매일 기록"
                        : "건너뜀 " + supplementProgress.skipped + "회 · 계획 관리",
                v -> host.navigate(FitnessScreen.SUPPLEMENTS)
        ), ui().fullWidthParams(ui().dp(10)));

        section("신체 기록");
        add(ui().hologramStatTile(
                "체중",
                todayWeightValue(),
                "탭하여 기록",
                v -> host.showBodyMetricDialog()
        ), ui().fullWidthParams(0));

        List<View> conditionRows = new ArrayList<>();
        for (String metric : repository().bodyMetricsForDate(today)) {
            conditionRows.add(ui().recordListRow("체", FitnessUi.stripLeadingDate(metric), "체중", null));
        }
        for (FitnessRepository.MealEntry meal : repository().mealEntriesForDate(today)) {
            View row = ui().recordListRow(
                    "식",
                    meal.previewTitle,
                    meal.previewSubtitle(),
                    v -> host.openMealManagement(today, FitnessScreen.WORKOUT)
            );
            row.setContentDescription(meal.previewAccessibilityLabel() + ". 탭하여 식단 관리를 엽니다.");
            conditionRows.add(row);
        }
        if (!conditionRows.isEmpty()) {
            add(ui().rowsCard(conditionRows));
        }

    }

    private void continueBanner() {
        FitnessUi ui = ui();
        LinearLayout banner = new LinearLayout(host.activity());
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16));
        banner.setBackground(ui.vibrantRippleDrawable("workout-in-progress", ui.dp(18)));
        ui.applyDepth(banner, 9);
        banner.setClickable(true);
        banner.setFocusable(true);
        ui.pressFeedback(banner);
        banner.setOnClickListener(v -> host.continueWorkoutIfAvailable());

        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(ui.caption("IN PROGRESS", FitnessUi.COLOR_INVERSE_MUTED));
        TextView title = ui.text("진행 중인 운동이 있습니다", 16, FitnessUi.COLOR_INVERSE_TEXT, true);
        title.setPadding(0, ui.dp(3), 0, 0);
        column.addView(title);
        banner.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = ui.text("이어하기 ›", 14, FitnessUi.COLOR_INVERSE_TEXT, true);
        banner.addView(chevron);

        LinearLayout.LayoutParams params = ui.fullWidthParams(0);
        params.setMargins(0, 0, 0, ui.dp(4));
        add(banner, params);
    }

    private String todayWeightValue() {
        List<String> rows = repository().bodyMetricsForDate(host.today());
        if (rows.isEmpty()) {
            return "—";
        }
        String first = rows.get(0);
        int split = first.lastIndexOf("  ");
        return split >= 0 ? first.substring(split + 2) : first;
    }
}
