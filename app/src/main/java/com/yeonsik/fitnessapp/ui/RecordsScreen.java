package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
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

    /** 자정 전까지 오늘을 보고 있었다면 새 날짜로 자연스럽게 이동한다. */
    public void onDateChanged(String previousDate, String currentDate) {
        if (selectedDate.equals(previousDate)) {
            selectedDate = currentDate;
            visibleMonth = LocalDate.parse(currentDate).withDayOfMonth(1);
        }
    }

    @Override
    public void render() {
        FitnessUi ui = ui();
        monthSummaries = repository().calendarSummaries(
                visibleMonth.toString(),
                visibleMonth.withDayOfMonth(visibleMonth.lengthOfMonth()).toString());
        screenHeader("활동 아카이브", "기록");

        add(calendar());

        String dateHeading = LocalDate.parse(selectedDate)
                .format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN));
        section(dateHeading);
        List<FitnessRepository.SessionRecordEntry> sessions = repository().sessionEntriesForDate(selectedDate);
        List<FitnessRepository.BodyMetricEntry> bodyMetrics =
                repository().bodyMetricEntriesForDate(selectedDate);
        List<FitnessRepository.MealEntry> meals = repository().mealEntriesForDate(selectedDate);
        if (sessions.isEmpty() && bodyMetrics.isEmpty() && meals.isEmpty()) {
            emptyState("선택한 날짜의 기록이 없습니다.", "운동, 체중, 식사를 기록하면 여기에 모입니다.");
            return;
        }

        if (!sessions.isEmpty()) {
            section("운동");
            renderSessionRecords(sessions);
        }

        if (!bodyMetrics.isEmpty()) {
            section("체중");
            List<View> weightRows = new ArrayList<>();
            for (FitnessRepository.BodyMetricEntry metric : bodyMetrics) {
                weightRows.add(weightRow(metric));
            }
            add(ui.rowsCard(weightRows));
        }

        if (!meals.isEmpty()) {
            section("식단", "상세 보기", () ->
                    host.openMealManagement(selectedDate, FitnessScreen.RECORDS));
            List<View> mealRows = new ArrayList<>();
            for (FitnessRepository.MealEntry meal : meals) {
                String mealTitle = meal.isDiningOut()
                        ? meal.storeName + " - " + meal.menuName
                        : meal.previewTitle;
                String accessibilityLabel = meal.isDiningOut()
                        ? mealTitle + ", " + meal.previewSubtitle()
                        : meal.previewAccessibilityLabel();
                View row = ui.recordListRow(
                        "식",
                        mealTitle,
                        meal.previewSubtitle(),
                        v -> host.openMealManagement(selectedDate, FitnessScreen.RECORDS)
                );
                row.setContentDescription(accessibilityLabel
                        + ". 탭하여 식단 관리를 엽니다.");
                mealRows.add(row);
            }
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
        previous.setContentDescription("이전 달");
        TextView title = ui.text(visibleMonth.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)),
                18, FitnessUi.COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        monthHeader.addView(previous, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        monthHeader.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LocalDate currentMonth = LocalDate.parse(host.today()).withDayOfMonth(1);
        boolean canMoveNext = visibleMonth.isBefore(currentMonth);
        TextView next = ui.textAction("›", FitnessUi.COLOR_MUTED, () -> {
            if (canMoveNext) {
                visibleMonth = visibleMonth.plusMonths(1);
                host.rerender();
            }
        });
        next.setContentDescription("다음 달");
        next.setEnabled(canMoveNext);
        next.setAlpha(canMoveNext ? 1f : 0.35f);
        monthHeader.addView(next, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
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
        cell.setSelected(selected);
        List<String> recordTypes = new ArrayList<>();
        if (summary.hasWorkout) {
            recordTypes.add("운동");
        }
        if (summary.hasMeal) {
            recordTypes.add("식사");
        }
        if (summary.hasWeight) {
            recordTypes.add("체중");
        }
        String recordDescription = recordTypes.isEmpty()
                ? "기록 없음"
                : String.join(", ", recordTypes) + " 기록";
        if (!summary.muscles.isEmpty()) {
            recordDescription += ", " + summary.muscles;
        }
        cell.setContentDescription(
                date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
                        + ", " + recordDescription + (selected ? ", 선택됨" : "")
        );
        ui.styleSelection(cell, selected, ui.dp(10));
        ui.pressFeedback(cell);
        TextView day = ui.num(String.valueOf(date.getDayOfMonth()), 14,
                selected ? ui.selectedInk() : FitnessUi.COLOR_TEXT, true);
        day.setGravity(Gravity.CENTER);
        cell.addView(day, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(21)));

        LinearLayout markers = new LinearLayout(host.activity());
        markers.setOrientation(LinearLayout.HORIZONTAL);
        markers.setGravity(Gravity.CENTER);
        addMarker(markers, summary.hasMeal, "●",
                selected ? ui.selectedInk() : ui.chartColor(3));
        addMarker(markers, summary.hasWeight, "■",
                selected ? ui.selectedInk() : ui.chartColor(2));
        addMarker(markers, summary.hasWorkout, "▲",
                selected ? ui.selectedInk() : ui.chartColor(0));
        cell.addView(markers, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dp(12)
        ));

        TextView muscle = ui.text(summary.muscles, 9,
                selected ? ui.selectedInk() : FitnessUi.COLOR_MUTED, false);
        muscle.setGravity(Gravity.CENTER);
        muscle.setMaxLines(1);
        cell.addView(muscle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(15)));
        cell.setOnClickListener(v -> {
            selectedDate = date.toString();
            host.rerender();
        });
        return cell;
    }

    private void addMarker(LinearLayout row, boolean visible, String symbol, int color) {
        if (!visible) {
            return;
        }
        TextView marker = ui().text(symbol, 9, color, true);
        marker.setGravity(Gravity.CENTER);
        marker.setIncludeFontPadding(false);
        row.addView(marker, new LinearLayout.LayoutParams(ui().dp(10), ui().dp(12)));
        View gap = new View(host.activity());
        row.addView(gap, new LinearLayout.LayoutParams(ui().dp(2), ui().dp(1)));
    }

    private void addCalendarLegend(LinearLayout card) {
        FitnessUi ui = ui();
        LinearLayout legend = new LinearLayout(host.activity());
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setPadding(0, ui.dp(12), 0, 0);
        legend.addView(ui.text("● 식단", 11, ui.chartColor(3), false), ui.fieldCellParams(true));
        legend.addView(ui.text("■ 체중", 11, ui.chartColor(2), false), ui.fieldCellParams(false));
        legend.addView(ui.text("▲ 운동", 11, ui.chartColor(0), false), ui.fieldCellParams(false));
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
        return new DaySummary(!sessions.isEmpty(), repository().mealCountForDate(value) > 0,
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
    private void renderSessionRecords(List<FitnessRepository.SessionRecordEntry> sessions) {
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
        boolean cardioRecord = "cardio".equals(session.workoutType);
        CardioRepository.SessionSnapshot cardio = cardioRecord
                ? host.cardioRepository().session(session.id)
                : null;
        Double averageHeartRateBpm = cardio == null
                ? session.averageHeartRateBpm
                : cardio.averageHeartRateBpm;
        String heartRateSuffix = CardioMetrics.hasAverageHeartRate(averageHeartRateBpm)
                ? " · 평균 " + CardioMetrics.formatAverageHeartRate(averageHeartRateBpm) + "bpm"
                : "";

        LinearLayout card = ui.card();
        if (!personalOsRecord) {
            card.setClickable(true);
            card.setFocusable(true);
            ui.pressFeedback(card);
            card.setOnClickListener(v -> {
                if (cardio != null) {
                    host.openCardioSummary(session.id);
                    return;
                }
                host.sessionState().setActiveRecordId(session.id);
                host.sessionState().setActiveExerciseId(null);
                host.navigate(FitnessScreen.WORKOUT_SUMMARY);
            });
        }

        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(ui.tonalGlyphCircle(cardioRecord ? "유" : "운"));

        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(ui.dp(12), 0, 0, 0);
        column.addView(ui.text(routineName, 16, FitnessUi.COLOR_TEXT, true));
        String metaText;
        if (personalOsRecord && cardioRecord) {
            metaText = "Personal OS · " + CardioMetrics.formatElapsed(session.durationSeconds)
                    + heartRateSuffix;
        } else if (personalOsRecord) {
            metaText = "Personal OS에서 생성된 요약 기록";
        } else if (cardioRecord) {
            double distanceMeters = cardio == null
                    ? metrics.totalDistanceMeters
                    : cardio.distanceMeters;
            int elapsedSeconds = cardio == null
                    ? session.durationSeconds
                    : cardio.elapsedSeconds(System.currentTimeMillis());
            metaText = "거리 " + CardioMetrics.formatDistanceKilometers(distanceMeters)
                    + "km · " + CardioMetrics.formatElapsed(elapsedSeconds)
                    + heartRateSuffix
                    + (cardio == null ? " · 경로 없음" : "");
        } else {
            metaText = "총 볼륨 " + FitnessUi.formatVolume(metrics.totalVolumeKg)
                    + "kg · " + metrics.setCount + "세트";
        }
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
