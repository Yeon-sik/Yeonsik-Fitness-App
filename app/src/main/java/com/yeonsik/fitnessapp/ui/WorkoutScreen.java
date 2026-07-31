package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * 피트니스 탭: 진행 중 배너 + 운동 시작 + 루틴 관리 + 오늘 컨디션.
 */
public final class WorkoutScreen extends BaseScreen {

    public WorkoutScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String today = host.today();
        host.routineRepository().activeRoutineId();
        List<RoutineRepository.RoutineSummary> routines = host.routineRepository().routines();
        String inProgressSessionId = repository().latestInProgressSessionId();

        screenHeader("FITNESS", "피트니스");

        if (inProgressSessionId != null) {
            continueBanner(inProgressSessionId);
        }

        section("운동 시작");
        add(ui().button("루틴 없이 운동 시작", true, v -> host.startEmptyWorkout()), ui().fullWidthParams(0));
        if (routines.isEmpty()) {
            emptyState("만들어진 루틴이 없습니다.", "아래에서 루틴을 추가하세요.");
        } else {
            for (RoutineRepository.RoutineSummary routine : routines) {
                List<RoutineExerciseInstance> exercises = host.routineRepository().routineExercises(routine.id);
                add(ui().routineCard(routine.name, routine.exerciseCount, true,
                        repository().latestCompletedWorkoutDateForRoutine(routine.id, routine.name),
                        () -> {
                            host.routineRepository().selectRoutine(routine.id);
                            host.startRoutineWorkout(exercises);
                        },
                        () -> {
                            host.routineRepository().selectRoutine(routine.id);
                            host.navigate(FitnessScreen.ROUTINE_DETAIL);
                        }));
            }
        }

        section("루틴 관리 (" + routines.size() + "/" + RoutineRepository.MAX_ROUTINES + ")");
        add(ui().button("루틴 추가", false, v -> host.navigate(FitnessScreen.ROUTINE_ADD)), ui().fullWidthParams(0));

        section("오늘 컨디션");
        LinearLayout conditionRow = ui().tileRow();
        conditionRow.addView(ui().statTile("체중", todayWeightValue(), "탭하여 기록", false,
                v -> host.showBodyMetricDialog()), ui().tileParams(true));
        conditionRow.addView(ui().statTile("식사", repository().mealsForDate(today).size() + "건", "탭하여 기록", false,
                v -> host.showMealDialog()), ui().tileParams(false));
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

    private void continueBanner(String inProgressSessionId) {
        FitnessUi ui = ui();
        LinearLayout banner = new LinearLayout(host.activity());
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16));
        banner.setBackground(ui.rippleDrawable(ui.accent(), ui.accent(),
                ui.dp(18), ui.rippleOnAccent()));
        banner.setElevation(ui.dp(6));
        banner.setClickable(true);
        banner.setFocusable(true);
        banner.setOnClickListener(v -> host.openWorkoutSession(inProgressSessionId));

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
