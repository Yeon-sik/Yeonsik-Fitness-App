package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 메인 탭: 오늘의 판단 히어로 카드 + 주간 볼륨 차트 + 빠른 기록 타일 + 오늘 기록.
 */
public final class HomeScreen extends BaseScreen {

    public HomeScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String today = host.today();
        List<String> todaySessions = repository().sessionsForDate(today);
        host.routineRepository().activeRoutineId();
        List<RoutineRepository.RoutineSummary> routines = host.routineRepository().routines();
        FitnessRepository.DayWorkoutMetrics todayMetrics = repository().dayWorkoutMetrics(today);
        String inProgressSessionId = repository().latestInProgressSessionId();

        screenHeader(todayEyebrow(), "오늘의 훈련");
        heroJudgmentCard(todaySessions, todayMetrics, inProgressSessionId != null);

        section("루틴 빠른 시작");
        if (!routines.isEmpty()) {
            for (RoutineRepository.RoutineSummary routine : routines) {
                List<RoutineExerciseInstance> exercises = host.routineRepository().routineExercises(routine.id);
                add(ui().quickStartRoutineCard(routine.name, routine.exerciseCount,
                        () -> {
                            host.routineRepository().selectRoutine(routine.id);
                            host.startRoutineWorkout(exercises);
                        },
                        () -> {
                            host.routineRepository().selectRoutine(routine.id);
                            host.navigate(FitnessScreen.ROUTINE_DETAIL);
                        }));
            }
        } else {
            add(ui().button("루틴 없이 운동 시작", true, v -> host.startEmptyWorkout()),
                    ui().fullWidthParams(0));
        }

        section("이번 주");
        weeklyVolumeCard();

        section("빠른 기록");
        LinearLayout quickTop = ui().tileRow();
        quickTop.addView(ui().statTile("체중", todayWeightValue(), "오늘", false,
                v -> host.showBodyMetricDialog()), ui().tileParams(true));
        quickTop.addView(ui().statTile("식사", repository().mealsForDate(today).size() + "건", "오늘", false,
                v -> host.showMealDialog()), ui().tileParams(false));
        add(quickTop, ui().fullWidthParams(0));
        LinearLayout quickBottom = ui().tileRow();
        quickBottom.addView(ui().statTile("진행 중 운동", inProgressSessionId != null ? "있음" : "없음",
                inProgressSessionId != null ? "탭하여 이어하기" : "대기", inProgressSessionId != null,
                v -> host.continueWorkoutIfAvailable()), ui().tileParams(true));
        quickBottom.addView(ui().statTile("기록", "달력", "전체 보기", false,
                v -> host.navigate(FitnessScreen.RECORDS)), ui().tileParams(false));
        add(quickBottom, ui().fullWidthParams(ui().dp(10)));

        section("오늘 기록", "전체 보기", () -> host.navigate(FitnessScreen.RECORDS));
        renderTodayRecordRows(todaySessions);
    }

    private void heroJudgmentCard(List<String> todaySessions, FitnessRepository.DayWorkoutMetrics metrics, boolean inProgress) {
        FitnessUi ui = ui();
        LinearLayout card = ui.heroCard();

        LinearLayout headerRow = new LinearLayout(host.activity());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.addView(ui.caption("TODAY", FitnessUi.COLOR_INVERSE_MUTED),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(ui.statusDotBadge(inProgress
                        ? "진행 중"
                        : (todaySessions.isEmpty() ? "운동 전" : "완료"),
                inProgress ? FitnessUi.COLOR_WARNING
                        : (todaySessions.isEmpty() ? FitnessUi.COLOR_TERTIARY : FitnessUi.COLOR_POSITIVE), true));
        card.addView(headerRow);

        String statusText;
        if (inProgress) {
            statusText = "운동이 진행 중입니다";
        } else if (todaySessions.isEmpty()) {
            statusText = "오늘 운동 전입니다";
        } else {
            statusText = "오늘 " + todaySessions.size() + "회 운동했습니다";
        }
        TextView status = ui.text(statusText, 21, FitnessUi.COLOR_INVERSE_TEXT, true);
        status.setPadding(0, ui.dp(10), 0, 0);
        card.addView(status);

        LinearLayout volumeRow = new LinearLayout(host.activity());
        volumeRow.setOrientation(LinearLayout.HORIZONTAL);
        volumeRow.setGravity(Gravity.BOTTOM);
        volumeRow.setPadding(0, ui.dp(14), 0, 0);
        TextView volume = ui.num(FitnessUi.formatVolume(metrics.totalVolumeKg), 40, FitnessUi.COLOR_INVERSE_TEXT, true);
        volume.setIncludeFontPadding(false);
        TextView unit = ui.text("kg", 15, FitnessUi.COLOR_INVERSE_MUTED, true);
        unit.setPadding(ui.dp(6), 0, 0, ui.dp(5));
        volumeRow.addView(volume);
        volumeRow.addView(unit);
        card.addView(volumeRow);
        TextView volumeLabel = ui.text("오늘 총 볼륨", 12, FitnessUi.COLOR_INVERSE_MUTED, false);
        volumeLabel.setPadding(0, ui.dp(2), 0, 0);
        card.addView(volumeLabel);

        View line = ui.hairline(FitnessUi.COLOR_INVERSE_LINE);
        LinearLayout.LayoutParams lineParams = ui.fullWidthParams(ui.dp(16));
        lineParams.height = ui.dp(1);
        card.addView(line, lineParams);

        LinearLayout metaRow = new LinearLayout(host.activity());
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setPadding(0, ui.dp(12), 0, 0);
        metaRow.addView(heroMetaCell("세션", metrics.sessionCount + "회"), ui.metaCellParams(true));
        metaRow.addView(heroMetaCell("세트", metrics.totalSetCount + "개"), ui.metaCellParams(false));
        metaRow.addView(heroMetaCell("시간", metrics.totalDurationSeconds > 0
                ? FitnessUi.formatDuration(metrics.totalDurationSeconds) : "—"), ui.metaCellParams(false));
        card.addView(metaRow);

        add(card);
    }

    private View heroMetaCell(String label, String value) {
        FitnessUi ui = ui();
        LinearLayout cell = new LinearLayout(host.activity());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(ui.caption(label, FitnessUi.COLOR_INVERSE_MUTED));
        TextView valueView = ui.num(value, 15, FitnessUi.COLOR_INVERSE_TEXT, true);
        valueView.setPadding(0, ui.dp(3), 0, 0);
        cell.addView(valueView);
        return cell;
    }

    private void weeklyVolumeCard() {
        FitnessUi ui = ui();
        LocalDate now = LocalDate.now();
        double[] values = new double[7];
        String[] labels = new String[7];
        double weekVolume = 0;
        int workoutDays = 0;
        int weekSets = 0;
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN);
        for (int i = 0; i < 7; i++) {
            LocalDate date = now.minusDays(6 - i);
            FitnessRepository.DayWorkoutMetrics metrics = repository().dayWorkoutMetrics(date.toString());
            values[i] = metrics.totalVolumeKg;
            labels[i] = date.format(dayFormatter);
            weekVolume += metrics.totalVolumeKg;
            weekSets += metrics.totalSetCount;
            if (metrics.sessionCount > 0) {
                workoutDays += 1;
            }
        }

        LinearLayout card = ui.card();

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        LinearLayout titleColumn = new LinearLayout(host.activity());
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(ui.caption("주간 볼륨", FitnessUi.COLOR_MUTED));
        LinearLayout valueRow = new LinearLayout(host.activity());
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);
        valueRow.setPadding(0, ui.dp(4), 0, 0);
        valueRow.addView(ui.num(FitnessUi.formatVolume(weekVolume), 24, FitnessUi.COLOR_TEXT, true));
        TextView unit = ui.text("kg", 13, FitnessUi.COLOR_MUTED, true);
        unit.setPadding(ui.dp(4), 0, 0, ui.dp(3));
        valueRow.addView(unit);
        titleColumn.addView(valueRow);
        header.addView(titleColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView meta = ui.text("운동 " + workoutDays + "일 · 세트 " + weekSets + "개", 12, FitnessUi.COLOR_TERTIARY, false);
        header.addView(meta);
        card.addView(header);

        card.addView(weeklyBarChart(values, labels), ui.fullWidthParams(ui.dp(16)));
        add(card);
    }

    private View weeklyBarChart(double[] values, String[] labels) {
        FitnessUi ui = ui();
        double max = 1;
        boolean hasData = false;
        for (double value : values) {
            if (value > max) {
                max = value;
            }
            if (value > 0) {
                hasData = true;
            }
        }

        LinearLayout chart = new LinearLayout(host.activity());
        chart.setOrientation(LinearLayout.HORIZONTAL);
        int chartHeight = hasData ? ui.dp(88) : ui.dp(28);
        for (int i = 0; i < values.length; i++) {
            boolean isToday = i == values.length - 1;
            LinearLayout column = new LinearLayout(host.activity());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            int barHeight = values[i] <= 0
                    ? ui.dp(4)
                    : Math.max(ui.dp(10), (int) Math.round(values[i] / max * chartHeight));
            int barColor = values[i] <= 0
                    ? FitnessUi.COLOR_BAR_EMPTY
                    : (isToday ? FitnessUi.COLOR_PRIMARY : FitnessUi.COLOR_BAR_MUTED);
            View bar = new View(host.activity());
            bar.setBackground(ui.borderDrawable(barColor, barColor, ui.dp(999)));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(ui.dp(12), barHeight);
            barParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(bar, barParams);

            TextView day = ui.text(labels[i], 11, isToday ? FitnessUi.COLOR_TEXT : FitnessUi.COLOR_TERTIARY, isToday);
            day.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            dayParams.setMargins(0, ui.dp(8), 0, 0);
            dayParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(day, dayParams);

            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, chartHeight + ui.dp(28), 1f);
            chart.addView(column, columnParams);
        }
        return chart;
    }

    private String todayEyebrow() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN));
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

    private void renderTodayRecordRows(List<String> todaySessions) {
        FitnessUi ui = ui();
        List<View> rows = new ArrayList<>();
        for (String session : todaySessions) {
            rows.add(ui.recordListRow("운", FitnessUi.stripLeadingDate(session), "운동", null));
        }
        for (String metric : repository().bodyMetricsForDate(host.today())) {
            rows.add(ui.recordListRow("체", FitnessUi.stripLeadingDate(metric), "체중", null));
        }
        for (String meal : repository().mealsForDate(host.today())) {
            rows.add(ui.recordListRow("식", FitnessUi.stripLeadingDate(meal), "식단", null));
        }

        if (rows.isEmpty()) {
            emptyState("오늘 기록이 없습니다.", "운동, 체중, 식사를 기록해 보세요.");
            return;
        }
        add(ui.rowsCard(rows));
    }
}
