package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * 피트니스 허브: 무산소·유산소 진입과 식단·체중 기록을 분리한다.
 */
public final class WorkoutScreen extends BaseScreen {

    public WorkoutScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String today = host.today();
        String inProgressSessionId = repository().latestInProgressSessionId();

        screenHeader("FITNESS", "피트니스");

        if (inProgressSessionId != null) {
            continueBanner();
        }

        section("운동");
        LinearLayout workoutTypeRow = ui().tileRow();
        workoutTypeRow.addView(ui().hologramStatTile(
                "무산소", "근력·루틴", "탭하여 들어가기",
                v -> host.navigate(FitnessScreen.STRENGTH)), ui().tileParams(true));
        workoutTypeRow.addView(ui().hologramStatTile(
                "유산소", "GPS", "탭하여 들어가기",
                v -> host.navigate(FitnessScreen.CARDIO)), ui().tileParams(false));
        add(workoutTypeRow, ui().fullWidthParams(0));

        section("식단 및 체중");
        LinearLayout conditionRow = ui().tileRow();
        conditionRow.addView(ui().hologramStatTile("체중", todayWeightValue(), "탭하여 기록",
                v -> host.showBodyMetricDialog()), ui().tileParams(true));
        conditionRow.addView(ui().hologramStatTile("식단", repository().mealsForDate(today).size() + "건", "탭하여 기록",
                v -> host.openMealManagement()), ui().tileParams(false));
        add(conditionRow, ui().fullWidthParams(0));

        List<View> conditionRows = new ArrayList<>();
        for (String metric : repository().bodyMetricsForDate(today)) {
            conditionRows.add(ui().recordListRow("체", FitnessUi.stripLeadingDate(metric), "체중", null));
        }
        for (String meal : repository().mealsForDate(today)) {
            conditionRows.add(ui().recordListRow("식", FitnessUi.stripLeadingDate(meal), "식단", null));
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
