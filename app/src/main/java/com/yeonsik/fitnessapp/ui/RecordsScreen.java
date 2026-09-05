package com.yeonsik.fitnessapp.ui;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MassFormatter;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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
        boolean selectedDateEmpty = sessions.isEmpty() && bodyMetrics.isEmpty() && meals.isEmpty();
        if (selectedDateEmpty) {
            emptyState("선택한 날짜에 기록이 없습니다.", "달력에서 다른 날짜를 선택하세요.");
        } else {
            if (!sessions.isEmpty()) {
                section("운동 기록");
                renderSessionRecords(sessions);
            }

            if (!bodyMetrics.isEmpty()) {
                section("체중 기록");
                List<View> weightRows = new ArrayList<>();
                for (FitnessRepository.BodyMetricEntry metric : bodyMetrics) {
                    weightRows.add(weightRow(metric));
                }
                add(ui.rowsCard(weightRows));
            }

            if (!meals.isEmpty()) {
                section("식사 기록", "식단 열기", () ->
                        host.openMealManagement(selectedDate, FitnessScreen.RECORDS));
                List<View> mealRows = new ArrayList<>();
                for (FitnessRepository.MealEntry meal : meals) {
                    String mealTitle = meal.isDiningOut()
                            ? meal.storeName + " - " + meal.menuName
                            : meal.previewTitle;
                    String accessibilityLabel = meal.isDiningOut()
                            ? mealTitle + ", " + meal.previewSubtitle()
                            : meal.previewAccessibilityLabel();
                    String editState = meal.timeEditable
                            ? "시간 수정 가능"
                            : "시간 읽기 전용";
                    View row = ui.recordListRow(
                            "식",
                            mealTitle,
                            meal.previewSubtitle() + " · " + editState,
                            v -> host.openMealManagement(selectedDate, FitnessScreen.RECORDS)
                    );
                    row.setContentDescription(accessibilityLabel + ". " + editState
                            + ". 탭하여 식단 관리를 엽니다.");
                    mealRows.add(row);
                }
                add(ui.rowsCard(mealRows));
            }
        }

        renderWeightTrend(selectedDateEmpty);
    }

    private void renderWeightTrend(boolean selectedDateEmpty) {
        FitnessUi ui = ui();
        MassUnit displayUnit = MassUnit.orDefault(host.preferredMassUnit());
        List<FitnessRepository.BodyMetricEntry> entries = new ArrayList<>(
                repository().bodyMetricEntriesForDate(null)
        );
        Collections.reverse(entries);
        if (entries.isEmpty()) {
            if (!selectedDateEmpty) {
                section("체중 변화");
                emptyState("체중 기록이 없습니다.", "체중을 기록하면 날짜별 목록과 변화를 확인할 수 있습니다.");
            }
            return;
        }

        section("체중 변화");
        FitnessRepository.BodyMetricEntry first = entries.get(0);
        FitnessRepository.BodyMetricEntry latest = entries.get(entries.size() - 1);
        List<String> weightDates = new ArrayList<>();
        for (FitnessRepository.BodyMetricEntry entry : entries) {
            weightDates.add(entry.date);
        }
        String period = RecordsAnalysis.trendPeriodLabel(weightDates, null);
        boolean hasTrend = RecordsAnalysis.hasEnoughTrendPoints(
                entries.size(),
                RecordsAnalysis.TrendCurrentState.NONE
        );
        LinearLayout card = ui.card();
        ui.cardHeader(
                card,
                "체중 추이",
                period.isEmpty()
                        ? "최근 " + entries.size() + "회 · " + displayUnit.symbol()
                        : period + " · " + displayUnit.symbol()
        );

        if (hasTrend) {
            List<Double> values = new ArrayList<>();
            for (FitnessRepository.BodyMetricEntry entry : entries) {
                values.add(MassUnit.fromKg(entry.weightKg, displayUnit));
            }
            card.addView(
                    ui.trendChart(
                            values,
                            displayUnit.symbol(),
                            RecordsAnalysis.TrendScalePolicy.RANGE_PADDED
                    ),
                    ui.trendChartParams(ui.dp(10))
            );
        } else {
            List<View> rows = new ArrayList<>();
            for (FitnessRepository.BodyMetricEntry entry : entries) {
                rows.add(ui.recordListRow(
                        "체",
                        MassFormatter.withUnit(entry.weightKg, displayUnit),
                        entry.date,
                        null
                ));
            }
            card.addView(ui.rowsCard(rows), ui.fullWidthParams(ui.dp(10)));
        }

        card.addView(ui.keyValue("기간", period.isEmpty() ? "기록 날짜 없음" : period));
        card.addView(ui.keyValue(
                entries.size() < 2 ? "변화" : "첫 기록 → 최근",
                entries.size() < 2
                        ? "이전 기록 없음"
                        : RecordsAnalysis.formatSignedDelta(
                                MassUnit.fromKg(first.weightKg, displayUnit),
                                MassUnit.fromKg(latest.weightKg, displayUnit),
                                displayUnit.symbol()
                        )
        ));
        TextView helper = ui.text(
                hasTrend
                        ? "최근 저장 기록만으로 계산한 단순 변화입니다."
                        : "체중 기록이 3개 이상 쌓이면 추세 차트를 표시합니다. 현재는 숫자 목록을 우선합니다.",
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        helper.setPadding(0, ui.dp(10), 0, 0);
        helper.setLineSpacing(ui.dp(3), 1f);
        card.addView(helper);
        add(card, ui.fullWidthParams(ui.dp(10)));
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
        boolean today = date.toString().equals(host.today());
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
                        + ", " + recordDescription
                        + (today ? ", 오늘" : "")
                        + (selected ? ", 선택됨" : "")
        );
        int strokeColor = selected
                ? ui.pastelBlue()
                : today ? ui.border() : Color.TRANSPARENT;
        cell.setBackground(ui.rippleDrawable(
                selected ? ui.selectedSurface() : Color.TRANSPARENT,
                strokeColor,
                ui.dp(10),
                ui.rippleOnSurface()
        ));
        ui.applyDepth(cell, selected ? FitnessUi.DEPTH_SURFACE_DP : FitnessUi.DEPTH_FLAT_DP);
        ui.pressFeedback(cell);
        TextView day = ui.num(String.valueOf(date.getDayOfMonth()), 14,
                selected ? ui.selectedInk() : FitnessUi.COLOR_TEXT, true);
        day.setGravity(Gravity.CENTER);
        cell.addView(day, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(21)));

        LinearLayout markers = new LinearLayout(host.activity());
        markers.setOrientation(LinearLayout.HORIZONTAL);
        markers.setGravity(Gravity.CENTER);
        int markerLimit = RecordsAnalysis.markerCount(
                summary.hasWorkout,
                summary.hasWeight,
                summary.hasMeal
        );
        int renderedMarkers = 0;
        renderedMarkers = addMarker(markers, summary.hasMeal, "●",
                selected ? ui.selectedInk() : ui.chartColor(3), renderedMarkers, markerLimit);
        renderedMarkers = addMarker(markers, summary.hasWeight, "■",
                selected ? ui.selectedInk() : ui.chartColor(2), renderedMarkers, markerLimit);
        renderedMarkers = addMarker(markers, summary.hasWorkout, "▲",
                selected ? ui.selectedInk() : ui.chartColor(0), renderedMarkers, markerLimit);
        cell.addView(markers, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dp(12)
        ));

        TextView muscle = ui.text(summary.muscles, 9,
                selected ? ui.selectedInk() : FitnessUi.COLOR_MUTED, false);
        muscle.setGravity(Gravity.CENTER);
        muscle.setMaxLines(1);
        muscle.setEllipsize(TextUtils.TruncateAt.END);
        cell.addView(muscle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(15)));
        cell.setOnClickListener(v -> {
            selectedDate = date.toString();
            host.rerender();
        });
        return cell;
    }

    private int addMarker(
            LinearLayout row,
            boolean visible,
            String symbol,
            int color,
            int renderedCount,
            int markerLimit
    ) {
        if (!visible || renderedCount >= markerLimit) {
            return renderedCount;
        }
        TextView marker = ui().text(symbol, 9, color, true);
        marker.setGravity(Gravity.CENTER);
        marker.setIncludeFontPadding(false);
        row.addView(marker, new LinearLayout.LayoutParams(ui().dp(10), ui().dp(12)));
        View gap = new View(host.activity());
        row.addView(gap, new LinearLayout.LayoutParams(ui().dp(2), ui().dp(1)));
        return renderedCount + 1;
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
        MassUnit displayUnit = MassUnit.orDefault(host.preferredMassUnit());
        column.addView(ui.text(
                MassFormatter.withUnit(metric.weightKg, displayUnit),
                16,
                FitnessUi.COLOR_TEXT,
                true
        ));
        if (!metric.memo.isEmpty()) {
            column.addView(ui.text(metric.memo, 12, FitnessUi.COLOR_MUTED, false));
        }
        column.addView(ui.text("저장된 값 · 수정 가능", 11, FitnessUi.COLOR_TERTIARY, false));
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.setContentDescription(
                MassFormatter.withUnit(metric.weightKg, displayUnit) + ", 저장된 값, 수정 가능"
        );
        row.addView(ui.textAction("수정", FitnessUi.COLOR_TERTIARY,
                () -> host.showBodyMetricDialog(metric.date, metric.id)));
        return row;
    }

    /**
     * 세션은 루틴 이름만 컴팩트하게 보여준다. 카드 탭 = 운동 요약으로 이동,
     * 종목·세트 상세는 요약 화면에서 확인한다. 삭제는 카드 우측 액션으로 유지한다.
     */
    private void renderSessionRecords(List<FitnessRepository.SessionRecordEntry> sessions) {
        List<View> rows = new ArrayList<>();
        for (FitnessRepository.SessionRecordEntry session : sessions) {
            rows.add(sessionSummaryCard(session));
        }
        add(ui().rowsCard(rows));
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

        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(72));
        row.setPadding(0, ui.dp(9), 0, ui.dp(9));
        if (!personalOsRecord) {
            row.setClickable(true);
            row.setFocusable(true);
            ui.pressFeedback(row);
            row.setOnClickListener(v -> {
                if (cardio != null) {
                    host.openCardioSummary(session.id);
                    return;
                }
                host.sessionState().setActiveRecordId(session.id);
                host.sessionState().setActiveExerciseId(null);
                host.navigate(FitnessScreen.WORKOUT_SUMMARY);
            });
        }

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
            MassUnit displayUnit = MassUnit.orDefault(host.preferredMassUnit());
            metaText = "총 볼륨 " + MassFormatter.withUnit(metrics.totalVolumeKg, displayUnit)
                    + " · " + metrics.setCount + "세트";
        }
        metaText += personalOsRecord ? " · 읽기 전용" : " · 저장된 기록";
        TextView meta = ui.text(metaText, 12, FitnessUi.COLOR_MUTED, false);
        meta.setPadding(0, ui.dp(2), 0, 0);
        column.addView(meta);
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.setContentDescription(
                routineName + ", " + metaText
                        + (personalOsRecord ? "" : ". 탭하여 상세 기록을 엽니다.")
        );

        if (!personalOsRecord) {
            row.addView(ui.textAction("삭제", FitnessUi.COLOR_TERTIARY,
                    () -> host.confirmDeleteSession(session.id)));
            TextView chevron = ui.text("›", 20, FitnessUi.COLOR_TERTIARY, false);
            chevron.setPadding(ui.dp(4), 0, 0, 0);
            row.addView(chevron);
        }

        return row;
    }

}
