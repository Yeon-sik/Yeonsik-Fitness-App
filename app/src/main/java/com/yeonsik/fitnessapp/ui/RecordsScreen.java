package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * 기록 탭: 달력 + 선택 날짜의 운동/체중/식단 아카이브.
 * 선택 날짜는 이 화면의 로컬 상태로 유지된다.
 */
public final class RecordsScreen extends BaseScreen {
    private String selectedDate;
    private LocalDate visibleMonth;
    private Map<String, FitnessRepository.CalendarDaySummary> monthSummaries;

    public RecordsScreen(ScreenHost host) {
        super(host);
        this.selectedDate = host.today();
        this.visibleMonth = LocalDate.parse(host.today()).withDayOfMonth(1);
        this.monthSummaries = new java.util.HashMap<>();
    }

    @Override
    public void render() {
        FitnessUi ui = ui();
        monthSummaries = repository().calendarSummaries(
                visibleMonth.toString(),
                visibleMonth.withDayOfMonth(visibleMonth.lengthOfMonth()).toString());
        screenHeader("RECORDS", "기록");

        add(calendar());

        String dateHeading = LocalDate.parse(selectedDate)
                .format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN));
        section(dateHeading);
        renderSessionRecords(selectedDate);

        section("체중");
        List<View> weightRows = new ArrayList<>();
        for (FitnessRepository.BodyMetricEntry metric : repository().bodyMetricEntriesForDate(selectedDate)) {
            weightRows.add(weightRow(metric));
        }
        if (weightRows.isEmpty()) {
            emptyState("선택한 날짜의 체중 기록이 없습니다.", null);
        } else {
            add(ui.rowsCard(weightRows));
        }

        section("식단");
        List<View> mealRows = new ArrayList<>();
        for (String meal : repository().mealsForDate(selectedDate)) {
            mealRows.add(ui.recordListRow("식", FitnessUi.stripLeadingDate(meal), "식단", null));
        }
        if (mealRows.isEmpty()) {
            emptyState("선택한 날짜의 식단 기록이 없습니다.", null);
        } else {
            add(ui.rowsCard(mealRows));
        }
    }

    private View calendar() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();

        LinearLayout monthHeader = new LinearLayout(host.activity());
        monthHeader.setOrientation(LinearLayout.HORIZONTAL);
        monthHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView previous = ui.textAction("‹", FitnessUi.COLOR_MUTED, () -> {
            visibleMonth = visibleMonth.minusMonths(1);
            host.rerender();
        });
        TextView title = ui.text(visibleMonth.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)),
                18, FitnessUi.COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        monthHeader.addView(previous, new LinearLayout.LayoutParams(ui.dp(42), LinearLayout.LayoutParams.WRAP_CONTENT));
        monthHeader.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView next = ui.textAction("›", FitnessUi.COLOR_MUTED, () -> {
            visibleMonth = visibleMonth.plusMonths(1);
            host.rerender();
        });
        monthHeader.addView(next, new LinearLayout.LayoutParams(ui.dp(42), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(monthHeader);

        LinearLayout weekdays = new LinearLayout(host.activity());
        weekdays.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"월", "화", "수", "목", "금", "토", "일"};
        for (String label : labels) {
            TextView dayLabel = ui.caption(label, FitnessUi.COLOR_MUTED);
            dayLabel.setGravity(Gravity.CENTER);
            weekdays.addView(dayLabel, new LinearLayout.LayoutParams(0, ui.dp(28), 1f));
        }
        card.addView(weekdays, ui.fullWidthParams(ui.dp(8)));

        LinearLayout grid = new LinearLayout(host.activity());
        grid.setOrientation(LinearLayout.VERTICAL);
        LocalDate first = visibleMonth;
        int offset = first.getDayOfWeek().getValue() - 1;
        int daysInMonth = visibleMonth.lengthOfMonth();
        for (int rowIndex = 0; rowIndex < 6; rowIndex++) {
            LinearLayout week = new LinearLayout(host.activity());
            week.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 7; column++) {
                int index = rowIndex * 7 + column;
                int day = index - offset + 1;
                LocalDate date = day >= 1 && day <= daysInMonth ? visibleMonth.withDayOfMonth(day) : null;
                week.addView(calendarDayCell(date), new LinearLayout.LayoutParams(0, ui.dp(62), 1f));
            }
            grid.addView(week, ui.fullWidthParams(0));
        }
        card.addView(grid);
        addCalendarLegend(card);
        return card;
    }

    private View calendarDayCell(LocalDate date) {
        FitnessUi ui = ui();
        LinearLayout cell = new LinearLayout(host.activity());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(ui.dp(2), ui.dp(4), ui.dp(2), ui.dp(2));
        if (date == null) {
            return cell;
        }

        DaySummary summary = summarize(date);
        boolean selected = date.toString().equals(selectedDate);
        String daySeed = "calendar-" + date.getDayOfWeek();
        cell.setBackground(selected
                ? ui.vibrantRippleDrawable(daySeed, ui.dp(10))
                : ui.flatSurfaceRippleDrawable(ui.dp(10)));
        TextView day = ui.num(String.valueOf(date.getDayOfMonth()), 14,
                selected ? FitnessUi.COLOR_INVERSE_TEXT : FitnessUi.COLOR_TEXT, true);
        day.setGravity(Gravity.CENTER);
        cell.addView(day, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(21)));

        LinearLayout markers = new LinearLayout(host.activity());
        markers.setOrientation(LinearLayout.HORIZONTAL);
        markers.setGravity(Gravity.CENTER);
        addMarker(markers, summary.hasWorkout, selected ? ui.onAccent() : ui.accent());
        addMarker(markers, summary.hasMeal, selected ? ui.onAccent() : FitnessUi.COLOR_WARNING);
        addMarker(markers, summary.hasWeight, selected ? ui.onAccent() : FitnessUi.COLOR_POSITIVE);
        cell.addView(markers, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(9)));

        TextView muscle = ui.text(summary.muscles, 9,
                selected ? FitnessUi.COLOR_INVERSE_TEXT : FitnessUi.COLOR_MUTED, false);
        muscle.setGravity(Gravity.CENTER);
        muscle.setMaxLines(1);
        cell.addView(muscle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(15)));
        cell.setOnClickListener(v -> {
            selectedDate = date.toString();
            host.rerender();
        });
        return cell;
    }

    private void addMarker(LinearLayout row, boolean visible, int color) {
        if (!visible) {
            return;
        }
        View marker = new View(host.activity());
        marker.setBackground(ui().borderDrawable(color, color, ui().dp(999)));
        row.addView(marker, new LinearLayout.LayoutParams(ui().dp(5), ui().dp(5)));
        View gap = new View(host.activity());
        row.addView(gap, new LinearLayout.LayoutParams(ui().dp(3), ui().dp(1)));
    }

    private void addCalendarLegend(LinearLayout card) {
        FitnessUi ui = ui();
        LinearLayout legend = new LinearLayout(host.activity());
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setPadding(0, ui.dp(12), 0, 0);
        legend.addView(ui.text("● 운동", 11, ui.accent(), false), ui.fieldCellParams(true));
        legend.addView(ui.text("● 식사", 11, FitnessUi.COLOR_WARNING, false), ui.fieldCellParams(false));
        legend.addView(ui.text("● 체중", 11, FitnessUi.COLOR_POSITIVE, false), ui.fieldCellParams(false));
        card.addView(legend);
    }

    private DaySummary summarizeLegacy(LocalDate date) {
        String value = date.toString();
        List<FitnessRepository.SessionRecordEntry> sessions = repository().sessionEntriesForDate(value);
        Set<String> muscles = new LinkedHashSet<>();
        for (FitnessRepository.SessionRecordEntry session : sessions) {
            for (FitnessRepository.SessionExerciseEntry exercise : repository().sessionExerciseEntries(session.id)) {
                if (exercise.uiPart != null && !exercise.uiPart.isEmpty()) {
                    muscles.add(exercise.uiPart);
                }
            }
        }
        String muscleLabel = "";
        if (!muscles.isEmpty()) {
            muscleLabel = String.join("·", muscles).length() > 8
                    ? String.join("·", muscles).substring(0, 8)
                    : String.join("·", muscles);
        }
        return new DaySummary(!sessions.isEmpty(), !repository().mealsForDate(value).isEmpty(),
                !repository().bodyMetricEntriesForDate(value).isEmpty(), muscleLabel);
    }

    private DaySummary summarize(LocalDate date) {
        FitnessRepository.CalendarDaySummary summary = monthSummaries.get(date.toString());
        if (summary == null) {
            return new DaySummary(false, false, false, "");
        }
        String muscleLabel = summary.muscles.length() > 8
                ? summary.muscles.substring(0, 8) : summary.muscles;
        return new DaySummary(summary.hasWorkout, summary.hasMeal, summary.hasWeight, muscleLabel);
    }

    private static final class DaySummary {
        final boolean hasWorkout;
        final boolean hasMeal;
        final boolean hasWeight;
        final String muscles;

        DaySummary(boolean hasWorkout, boolean hasMeal, boolean hasWeight, String muscles) {
            this.hasWorkout = hasWorkout;
            this.hasMeal = hasMeal;
            this.hasWeight = hasWeight;
            this.muscles = muscles;
        }
    }

    private View weightRow(FitnessRepository.BodyMetricEntry metric) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(56));
        row.setPadding(0, ui.dp(9), 0, ui.dp(9));
        row.addView(ui.glyphCircle("체", false));

        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(ui.dp(12), 0, 0, 0);
        column.addView(ui.text(FitnessUi.trimDouble(metric.weightKg) + "kg", 16, FitnessUi.COLOR_TEXT, true));
        if (!metric.memo.isEmpty()) {
            column.addView(ui.text(metric.memo, 12, FitnessUi.COLOR_MUTED, false));
        }
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(ui.textAction("수정", FitnessUi.COLOR_TERTIARY,
                () -> host.showBodyMetricDialog(metric.date, metric.id)));
        return row;
    }

    /**
     * 세션은 루틴 이름만 컴팩트하게 보여준다. 카드 탭 = 운동 요약으로 이동,
     * 종목·세트 상세는 요약 화면에서 확인한다. 삭제는 카드 우측 액션으로 유지한다.
     */
    private void renderSessionRecords(String date) {
        FitnessUi ui = ui();
        List<FitnessRepository.SessionRecordEntry> sessions = repository().sessionEntriesForDate(date);
        if (sessions.isEmpty()) {
            emptyState("선택한 날짜의 운동 기록이 없습니다.", null);
            return;
        }

        for (FitnessRepository.SessionRecordEntry session : sessions) {
            add(sessionSummaryCard(session));
        }
    }

    private View sessionSummaryCard(FitnessRepository.SessionRecordEntry session) {
        FitnessUi ui = ui();
        String[] tokens = session.summary.split(" {2,}");
        String routineName = tokens.length > 1 ? tokens[1] : session.summary;
        FitnessRepository.SessionMetrics metrics = repository().sessionMetrics(session.id);
        boolean personalOsRecord = "os".equals(session.sourceApp);

        LinearLayout card = ui.card();
        if (!personalOsRecord) {
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> {
                host.sessionState().setActiveRecordId(session.id);
                host.sessionState().setActiveExerciseId(null);
                host.navigate(FitnessScreen.WORKOUT_SUMMARY);
            });
        }

        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(ui.glyphCircle("운", false));

        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(ui.dp(12), 0, 0, 0);
        column.addView(ui.text(routineName, 16, FitnessUi.COLOR_TEXT, true));
        String metaText = personalOsRecord
                ? "Personal OS에서 생성된 요약 기록"
                : "총 볼륨 " + FitnessUi.formatVolume(metrics.totalVolumeKg)
                + "kg · " + metrics.setCount + "세트";
        TextView meta = ui.text(metaText, 12, FitnessUi.COLOR_MUTED, false);
        meta.setPadding(0, ui.dp(2), 0, 0);
        column.addView(meta);
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (!personalOsRecord) {
            row.addView(ui.textAction("삭제", FitnessUi.COLOR_TERTIARY,
                    () -> host.confirmDeleteSession(session.id)));
            TextView chevron = ui.text("›", 20, FitnessUi.COLOR_TERTIARY, false);
            chevron.setPadding(ui.dp(4), 0, 0, 0);
            row.addView(chevron);
        }

        card.addView(row);
        return card;
    }

}
