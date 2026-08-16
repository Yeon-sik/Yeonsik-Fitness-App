package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.AthleteNutritionGoal;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.NutritionCalculator;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionTotals;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 메인 탭: 오늘의 판단 히어로 카드 + 주간 볼륨 차트 + 빠른 기록 타일 + 오늘 기록.
 */
public final class HomeScreen extends BaseScreen {
    private static final int HOME_ROUTINE_LIMIT = 2;
    private static final int TODAY_RECORD_LIMIT = 3;

    public HomeScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String today = host.today();
        List<String> todaySessions = repository().sessionsForDate(today);
        String activeRoutineId = host.routineRepository().activeRoutineId();
        List<RoutineRepository.RoutineSummary> routines = host.routineRepository().routines();
        List<RoutineRepository.RoutineSummary> quickRoutines = quickStartRoutines(
                routines,
                activeRoutineId
        );
        FitnessRepository.DayWorkoutMetrics todayMetrics = repository().dayWorkoutMetrics(today);
        String inProgressSessionId = repository().latestInProgressSessionId();

        screenHeader(todayEyebrow(), "오늘의 훈련");
        heroJudgmentCard(todaySessions, todayMetrics, inProgressSessionId != null);

        if (routines.size() > HOME_ROUTINE_LIMIT) {
            section("루틴 빠른 시작", "전체 보기", () -> host.navigate(FitnessScreen.WORKOUT));
        } else {
            section("루틴 빠른 시작");
        }
        if (!routines.isEmpty()) {
            for (RoutineRepository.RoutineSummary routine : quickRoutines) {
                List<RoutineExerciseInstance> exercises = host.routineRepository().routineExercises(routine.id);
                add(ui().quickStartRoutineCard(routine.name, routine.exerciseCount,
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
        } else {
            add(ui().button("루틴 없이 운동 시작", true, v -> host.startEmptyWorkout()),
                    ui().fullWidthParams(0));
        }

        section("이번 주");
        weeklyVolumeCard();
        weeklyMealCard();
        weeklyNutritionTrendCard();

        section("빠른 기록");
        LinearLayout quickTop = ui().tileRow();
        quickTop.addView(ui().statTile("체중", todayWeightValue(), "오늘", false,
                v -> host.showBodyMetricDialog()), ui().tileParams(true));
        quickTop.addView(ui().statTile("식사", repository().mealCountForDate(today) + "끼", "오늘", false,
                v -> host.openMealManagement()), ui().tileParams(false));
        add(quickTop, ui().fullWidthParams(0));

        section("오늘 기록", "전체 보기", () -> host.navigate(FitnessScreen.RECORDS));
        renderTodayRecordRows(todaySessions);
    }

    private List<RoutineRepository.RoutineSummary> quickStartRoutines(
            List<RoutineRepository.RoutineSummary> routines,
            String activeRoutineId
    ) {
        List<RoutineRepository.RoutineSummary> result = new ArrayList<>();
        if (activeRoutineId != null) {
            for (RoutineRepository.RoutineSummary routine : routines) {
                if (activeRoutineId.equals(routine.id)) {
                    result.add(routine);
                    break;
                }
            }
        }
        for (RoutineRepository.RoutineSummary routine : routines) {
            if (result.size() >= HOME_ROUTINE_LIMIT) {
                break;
            }
            if (!result.isEmpty() && result.get(0).id.equals(routine.id)) {
                continue;
            }
            result.add(routine);
        }
        return result;
    }

    private void heroJudgmentCard(List<String> todaySessions, FitnessRepository.DayWorkoutMetrics metrics, boolean inProgress) {
        FitnessUi ui = ui();
        LinearLayout card = ui.heroCard();
        if (inProgress) {
            ui.setHologramBackground(card, card.getBackground(), ui.dp(24));
        }

        LinearLayout headerRow = new LinearLayout(host.activity());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.addView(ui.caption("TODAY", FitnessUi.COLOR_FLOW_MUTED),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(ui.flowStatusBadge(inProgress
                        ? "진행 중"
                        : (todaySessions.isEmpty() ? "운동 전" : "완료"),
                inProgress ? FitnessUi.COLOR_WARNING
                        : (todaySessions.isEmpty() ? FitnessUi.COLOR_TERTIARY : FitnessUi.COLOR_POSITIVE)));
        card.addView(headerRow);

        String statusText;
        if (inProgress) {
            statusText = "운동이 진행 중입니다";
        } else if (todaySessions.isEmpty()) {
            statusText = "오늘 운동 전입니다";
        } else {
            statusText = "오늘 " + todaySessions.size() + "회 운동했습니다";
        }
        TextView status = ui.text(statusText, 21, FitnessUi.COLOR_FLOW_TEXT, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, ui.dp(10), 0, 0);
        card.addView(status);

        LinearLayout volumeRow = new LinearLayout(host.activity());
        volumeRow.setOrientation(LinearLayout.HORIZONTAL);
        volumeRow.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        volumeRow.setPadding(0, ui.dp(14), 0, 0);
        TextView volume = ui.num(FitnessUi.formatVolume(metrics.totalVolumeKg), 40, FitnessUi.COLOR_FLOW_TEXT, true);
        volume.setIncludeFontPadding(false);
        volume.setLetterSpacing(-0.02f);
        ui.animateCount(volume, metrics.totalVolumeKg, null);
        TextView unit = ui.text("kg", 15, FitnessUi.COLOR_FLOW_MUTED, true);
        unit.setPadding(ui.dp(6), 0, 0, ui.dp(5));
        volumeRow.addView(volume);
        volumeRow.addView(unit);
        card.addView(volumeRow);
        TextView volumeLabel = ui.text("오늘 총 볼륨", 12, FitnessUi.COLOR_FLOW_MUTED, false);
        volumeLabel.setGravity(Gravity.CENTER);
        volumeLabel.setPadding(0, ui.dp(2), 0, 0);
        card.addView(volumeLabel);

        View line = ui.hairline(FitnessUi.COLOR_BORDER);
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

        card.addView(ui.flowHeroButton(
                        inProgress ? "운동 이어가기" : "피트니스 보기",
                        v -> {
                            if (inProgress) {
                                host.continueWorkoutIfAvailable();
                            } else {
                                host.navigate(FitnessScreen.WORKOUT);
                            }
                        }),
                ui.fullWidthParams(ui.dp(18)));

        add(card);
    }

    private View heroMetaCell(String label, String value) {
        return ui().flowMetric(label, value);
    }

    private void weeklyVolumeCard() {
        FitnessUi ui = ui();
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        double[] values = new double[7];
        double[] previousValues = new double[7];
        String[] labels = new String[7];
        double weekVolume = 0;
        int workoutDays = 0;
        int weekSets = 0;
        double previousWeekVolume = 0;
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            FitnessRepository.DayWorkoutMetrics metrics = repository().dayWorkoutMetrics(date.toString());
            values[i] = metrics.totalVolumeKg;
            labels[i] = date.format(dayFormatter);
            weekVolume += metrics.totalVolumeKg;
            weekSets += metrics.totalSetCount;
            if (metrics.sessionCount > 0) {
                workoutDays += 1;
            }
            FitnessRepository.DayWorkoutMetrics previousMetrics = repository()
                    .dayWorkoutMetrics(previousWeekStart.plusDays(i).toString());
            previousValues[i] = previousMetrics.totalVolumeKg;
            previousWeekVolume += previousMetrics.totalVolumeKg;
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

        TextView comparison = ui.text(weeklyComparison(weekVolume, previousWeekVolume),
                12, weekVolume >= previousWeekVolume ? FitnessUi.COLOR_POSITIVE : FitnessUi.COLOR_NEGATIVE, true);
        comparison.setPadding(0, ui.dp(10), 0, 0);
        card.addView(comparison);

        LinearLayout legend = new LinearLayout(host.activity());
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        legend.setPadding(0, ui.dp(10), 0, 0);
        legend.addView(ui.text("● 이번 주", 10, ui.hologramAccentColor(0), true));
        TextView previousLegend = ui.text("● 지난주", 10, FitnessUi.COLOR_MUTED, true);
        previousLegend.setPadding(ui.dp(14), 0, 0, 0);
        legend.addView(previousLegend);
        card.addView(legend);

        card.addView(weeklyBarChart(
                values,
                previousValues,
                labels,
                "주간 운동 볼륨",
                "kg"
        ), ui.fullWidthParams(ui.dp(8)));
        add(card);
    }

    private void weeklyMealCard() {
        FitnessUi ui = ui();
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        double[] values = new double[7];
        double[] previousValues = new double[7];
        String[] labels = new String[7];
        int weekMeals = 0;
        int mealDays = 0;
        int previousWeekMeals = 0;
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            int mealCount = repository().mealCountForDate(date.toString());
            values[i] = mealCount;
            labels[i] = date.format(dayFormatter);
            weekMeals += mealCount;
            if (mealCount > 0) {
                mealDays++;
            }

            int previousMealCount = repository().mealCountForDate(
                    previousWeekStart.plusDays(i).toString());
            previousValues[i] = previousMealCount;
            previousWeekMeals += previousMealCount;
        }

        LinearLayout card = ui.card();
        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        LinearLayout titleColumn = new LinearLayout(host.activity());
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(ui.caption("주간 식사", FitnessUi.COLOR_MUTED));
        LinearLayout valueRow = new LinearLayout(host.activity());
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);
        valueRow.setPadding(0, ui.dp(4), 0, 0);
        valueRow.addView(ui.num(String.valueOf(weekMeals), 24, FitnessUi.COLOR_TEXT, true));
        TextView unit = ui.text("끼", 13, FitnessUi.COLOR_MUTED, true);
        unit.setPadding(ui.dp(4), 0, 0, ui.dp(3));
        valueRow.addView(unit);
        titleColumn.addView(valueRow);
        header.addView(titleColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(ui.text("기록 " + mealDays + "일", 12, FitnessUi.COLOR_TERTIARY, false));
        card.addView(header);

        TextView comparison = ui.text(weeklyMealComparison(weekMeals, previousWeekMeals),
                12, weekMeals >= previousWeekMeals
                        ? FitnessUi.COLOR_POSITIVE : FitnessUi.COLOR_NEGATIVE, true);
        comparison.setPadding(0, ui.dp(10), 0, 0);
        card.addView(comparison);

        LinearLayout legend = new LinearLayout(host.activity());
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        legend.setPadding(0, ui.dp(10), 0, 0);
        legend.addView(ui.text("이번 주", 10, ui.hologramAccentColor(0), true));
        TextView previousLegend = ui.text("지난주", 10, FitnessUi.COLOR_MUTED, true);
        previousLegend.setPadding(ui.dp(14), 0, 0, 0);
        legend.addView(previousLegend);
        card.addView(legend);

        card.addView(weeklyBarChart(
                values,
                previousValues,
                labels,
                "주간 식사 기록",
                "끼"
        ), ui.fullWidthParams(ui.dp(8)));
        add(card);
    }

    private void weeklyNutritionTrendCard() {
        FitnessUi ui = ui();
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        double[] calories = new double[7];
        double[] carbs = new double[7];
        double[] protein = new double[7];
        double[] fat = new double[7];
        boolean[] caloriesAvailable = new boolean[7];
        boolean[] carbsAvailable = new boolean[7];
        boolean[] proteinAvailable = new boolean[7];
        boolean[] fatAvailable = new boolean[7];
        String[] labels = new String[7];
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN);
        StringBuilder description = new StringBuilder("주간 영양 추이. ");

        for (int index = 0; index < 7; index++) {
            LocalDate date = weekStart.plusDays(index);
            labels[index] = date.format(dayFormatter);
            NutritionTotals totals = repository().mealNutritionTotalsForDate(date.toString());
            NutritionTotals.Total caloriesTotal = totals.total(NutritionProfile.CALORIES_KCAL);
            NutritionTotals.Total carbsTotal = totals.total(NutritionProfile.CARBS_GRAMS);
            NutritionTotals.Total proteinTotal = totals.total(NutritionProfile.PROTEIN_GRAMS);
            NutritionTotals.Total fatTotal = totals.total(NutritionProfile.FAT_GRAMS);
            caloriesAvailable[index] = caloriesTotal.isComplete();
            carbsAvailable[index] = carbsTotal.isComplete();
            proteinAvailable[index] = proteinTotal.isComplete();
            fatAvailable[index] = fatTotal.isComplete();
            calories[index] = caloriesAvailable[index] ? caloriesTotal.knownSum() : 0d;
            carbs[index] = carbsAvailable[index] ? carbsTotal.knownSum() : 0d;
            protein[index] = proteinAvailable[index] ? proteinTotal.knownSum() : 0d;
            fat[index] = fatAvailable[index] ? fatTotal.knownSum() : 0d;
            if (index > 0) {
                description.append(", ");
            }
            description.append(labels[index])
                    .append(" kcal ").append(NutritionCalculator.describeTotal(caloriesTotal))
                    .append(", C ").append(NutritionCalculator.describeTotal(carbsTotal)).append("g")
                    .append(", P ").append(NutritionCalculator.describeTotal(proteinTotal)).append("g")
                    .append(", F ").append(NutritionCalculator.describeTotal(fatTotal)).append("g");
        }

        LinearLayout card = ui.card();
        card.addView(ui.caption("칼로리 / 탄단지 변화 추이", FitnessUi.COLOR_MUTED));
        card.addView(ui.text(
                "총합이 아닌 날짜별 변화 · 일평균 "
                        + averageNutrition(calories, caloriesAvailable, "kcal")
                        + " · C " + averageNutrition(carbs, carbsAvailable, "g")
                        + " · P " + averageNutrition(protein, proteinAvailable, "g")
                        + " · F " + averageNutrition(fat, fatAvailable, "g"),
                13,
                FitnessUi.COLOR_TEXT,
                true
        ));
        AthleteNutritionGoal nutritionGoal = repository().nutritionGoal();
        card.addView(ui.text(
                nutritionReferenceText(nutritionGoal),
                11,
                FitnessUi.COLOR_TERTIARY,
                false
        ));
        if (nutritionGoal == null) {
            card.addView(ui.text(
                    "영양소별 목표를 설정하면 7일 달성률 그래프가 표시됩니다.",
                    12,
                    FitnessUi.COLOR_TEXT,
                    false
            ), ui.fullWidthParams(ui.dp(10)));
            card.addView(ui.flowHeroButton(
                    "영양 목표 설정",
                    v -> host.openMealManagement(LocalDate.now().toString(), FitnessScreen.HOME)
            ), ui.fullWidthParams(ui.dp(10)));
            add(card);
            return;
        }

        LinearLayout legend = new LinearLayout(host.activity());
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        legend.setPadding(0, ui.dp(10), 0, 0);
        legend.addView(ui.text("칼로리", 10, ui.vibrantColor(0), true));
        TextView carbsLegend = ui.text("탄수화물", 10, ui.hologramAccentColor(0), true);
        carbsLegend.setPadding(ui.dp(12), 0, 0, 0);
        legend.addView(carbsLegend);
        TextView proteinLegend = ui.text("단백질", 10, ui.hologramAccentColor(1), true);
        proteinLegend.setPadding(ui.dp(12), 0, 0, 0);
        legend.addView(proteinLegend);
        TextView fatLegend = ui.text("지방", 10, ui.hologramAccentColor(2), true);
        fatLegend.setPadding(ui.dp(12), 0, 0, 0);
        legend.addView(fatLegend);
        card.addView(legend);
        card.addView(weeklyNutritionBarChart(
                calories,
                carbs,
                protein,
                fat,
                caloriesAvailable,
                carbsAvailable,
                proteinAvailable,
                fatAvailable,
                labels,
                description.toString(),
                nutritionGoal
        ), ui.fullWidthParams(ui.dp(8)));
        add(card);
    }

    private String averageNutrition(double[] values, boolean[] available, String unit) {
        double sum = 0d;
        int count = 0;
        for (int index = 0; index < values.length; index++) {
            if (available[index]) {
                sum += values[index];
                count++;
            }
        }
        return count == 0
                ? "?"
                : NutritionCalculator.trim(sum / count) + unit;
    }

    private String nutritionReferenceText(AthleteNutritionGoal goal) {
        if (goal == null) {
            return "기준 미설정 · 영양 목표에서 영양소별 기준값을 설정하세요";
        }
        return "기준 · kcal " + NutritionCalculator.trim(goal.caloriesKcal)
                + " · C " + NutritionCalculator.trim(goal.carbsGrams) + "g"
                + " · P " + NutritionCalculator.trim(goal.proteinGrams) + "g"
                + " · F " + NutritionCalculator.trim(goal.fatGrams) + "g";
    }

    private View weeklyNutritionBarChart(
            double[] calories,
            double[] carbs,
            double[] protein,
            double[] fat,
            boolean[] caloriesAvailable,
            boolean[] carbsAvailable,
            boolean[] proteinAvailable,
            boolean[] fatAvailable,
            String[] labels,
            String description,
            AthleteNutritionGoal nutritionGoal
    ) {
        FitnessUi ui = ui();
        boolean hasData = false;
        for (int index = 0; index < labels.length; index++) {
            hasData = hasData
                    || (caloriesAvailable[index] && calories[index] > 0d)
                    || (carbsAvailable[index] && carbs[index] > 0d)
                    || (proteinAvailable[index] && protein[index] > 0d)
                    || (fatAvailable[index] && fat[index] > 0d);
        }

        LinearLayout wrapper = new LinearLayout(host.activity());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setContentDescription(description + " 각 날짜에 칼로리, 탄수화물, 단백질, 지방 4개 막대"
                + ", 영양소별 일일 목표 대비 달성률");
        int chartHeight = hasData ? ui.dp(104) : ui.dp(28);
        TextView scaleLabel = ui.text(
                "목표 달성률 0%  ·  기준 100%  ·  초과 125%+",
                10,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        scaleLabel.setGravity(Gravity.END);
        wrapper.addView(scaleLabel);
        LinearLayout bars = new LinearLayout(host.activity());
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.BOTTOM);
        for (int index = 0; index < labels.length; index++) {
            LinearLayout dayGroup = new LinearLayout(host.activity());
            dayGroup.setOrientation(LinearLayout.HORIZONTAL);
            dayGroup.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            addTrendBar(dayGroup, labels[index] + " 칼로리", calories[index], nutritionGoal.caloriesKcal,
                    caloriesAvailable[index], ui.vibrantColor(0), "kcal");
            addTrendBar(dayGroup, labels[index] + " 탄수화물", carbs[index], nutritionGoal.carbsGrams,
                    carbsAvailable[index], ui.hologramAccentColor(0), "g");
            addTrendBar(dayGroup, labels[index] + " 단백질", protein[index], nutritionGoal.proteinGrams,
                    proteinAvailable[index], ui.hologramAccentColor(1), "g");
            addTrendBar(dayGroup, labels[index] + " 지방", fat[index], nutritionGoal.fatGrams,
                    fatAvailable[index], ui.hologramAccentColor(2), "g");
            bars.addView(dayGroup, new LinearLayout.LayoutParams(
                    0, chartHeight, 1f));
        }
        wrapper.addView(bars, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, chartHeight));
        wrapper.addView(ui.hairline(FitnessUi.COLOR_BORDER), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));
        LinearLayout labelsRow = new LinearLayout(host.activity());
        labelsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : labels) {
            TextView day = ui.text(label, 11, FitnessUi.COLOR_TERTIARY, false);
            day.setGravity(Gravity.CENTER);
            labelsRow.addView(day, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelsParams.setMargins(0, ui.dp(8), 0, 0);
        wrapper.addView(labelsRow, labelsParams);
        return wrapper;
    }

    private void addTrendBar(
            LinearLayout parent,
            String label,
            double value,
            double reference,
            boolean available,
            int color,
            String unit
    ) {
        FitnessUi ui = ui();
        int height = !available || value <= 0d
                ? ui.dp(4)
                : Math.max(ui.dp(10), (int) Math.round(
                        Math.min(value / reference, 1.25d) / 1.25d * ui.dp(96)));
        View bar = new View(host.activity());
        int barColor = available && value > 0d ? color : ui.barEmpty();
        bar.setBackground(ui.borderDrawable(barColor, barColor, ui.dp(999)));
        String ratio = available
                ? NutritionCalculator.trim(value / reference * 100d) + "%"
                : "영양 정보 없음";
        bar.setContentDescription(label + " " + NutritionCalculator.trim(value) + unit
                + " / 기준 " + NutritionCalculator.trim(reference) + unit + " · " + ratio);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(5), height);
        params.setMargins(ui.dp(1), 0, ui.dp(1), 0);
        parent.addView(bar, params);
    }

    private View weeklySingleSeriesBarChart(
            double[] values,
            boolean[] available,
            String[] labels,
            String unit,
            String descriptionLabel,
            int color
    ) {
        FitnessUi ui = ui();
        double max = 1d;
        boolean hasData = false;
        StringBuilder description = new StringBuilder(descriptionLabel).append(". ");
        for (int index = 0; index < values.length; index++) {
            if (available[index]) {
                max = Math.max(max, values[index]);
                hasData = hasData || values[index] > 0d;
            }
            if (index > 0) {
                description.append(", ");
            }
            description.append(labels[index]).append(" ")
                    .append(NutritionCalculator.trim(values[index])).append(unit);
        }
        LinearLayout wrapper = new LinearLayout(host.activity());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setContentDescription(description.toString());
        int chartHeight = hasData ? ui.dp(80) : ui.dp(28);
        LinearLayout bars = new LinearLayout(host.activity());
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.BOTTOM);
        for (int index = 0; index < values.length; index++) {
            int height = !available[index] || values[index] <= 0d
                    ? ui.dp(4)
                    : Math.max(ui.dp(10), (int) Math.round(values[index] / max * chartHeight));
            View bar = new View(host.activity());
            int barColor = available[index] && values[index] > 0d ? color : ui.barEmpty();
            bar.setBackground(ui.borderDrawable(barColor, barColor, ui.dp(999)));
            bars.addView(bar, new LinearLayout.LayoutParams(
                    0, height, 1f));
        }
        wrapper.addView(bars, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, chartHeight));
        wrapper.addView(ui.hairline(FitnessUi.COLOR_BORDER), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));
        LinearLayout labelsRow = new LinearLayout(host.activity());
        labelsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : labels) {
            TextView day = ui.text(label, 11, FitnessUi.COLOR_TERTIARY, false);
            day.setGravity(Gravity.CENTER);
            labelsRow.addView(day, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelsParams.setMargins(0, ui.dp(8), 0, 0);
        wrapper.addView(labelsRow, labelsParams);
        return wrapper;
    }

    private void weeklyMacroCard() {
        FitnessUi ui = ui();
        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        double[] carbs = new double[7];
        double[] protein = new double[7];
        double[] fat = new double[7];
        boolean[] carbsAvailable = new boolean[7];
        boolean[] proteinAvailable = new boolean[7];
        boolean[] fatAvailable = new boolean[7];
        String[] labels = new String[7];
        MacroSummary carbsSummary = new MacroSummary();
        MacroSummary proteinSummary = new MacroSummary();
        MacroSummary fatSummary = new MacroSummary();
        int mealDays = 0;
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN);
        StringBuilder chartDescription = new StringBuilder("주간 탄단지 그래프. ");

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            labels[i] = date.format(dayFormatter);
            NutritionTotals totals = repository().mealNutritionTotalsForDate(date.toString());
            NutritionTotals.Total carbsTotal = totals.total(NutritionProfile.CARBS_GRAMS);
            NutritionTotals.Total proteinTotal = totals.total(NutritionProfile.PROTEIN_GRAMS);
            NutritionTotals.Total fatTotal = totals.total(NutritionProfile.FAT_GRAMS);
            carbsSummary.add(carbsTotal);
            proteinSummary.add(proteinTotal);
            fatSummary.add(fatTotal);

            carbsAvailable[i] = carbsTotal.isComplete();
            proteinAvailable[i] = proteinTotal.isComplete();
            fatAvailable[i] = fatTotal.isComplete();
            carbs[i] = carbsAvailable[i] ? carbsTotal.knownSum() : 0;
            protein[i] = proteinAvailable[i] ? proteinTotal.knownSum() : 0;
            fat[i] = fatAvailable[i] ? fatTotal.knownSum() : 0;
            if (totals.itemCount() > 0) {
                mealDays++;
            }

            if (i > 0) {
                chartDescription.append(", ");
            }
            chartDescription.append(labels[i])
                    .append(" 탄수화물 ").append(NutritionCalculator.describeTotal(carbsTotal)).append("g")
                    .append(", 단백질 ").append(NutritionCalculator.describeTotal(proteinTotal)).append("g")
                    .append(", 지방 ").append(NutritionCalculator.describeTotal(fatTotal)).append("g");
        }

        LinearLayout card = ui.card();
        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        LinearLayout titleColumn = new LinearLayout(host.activity());
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(ui.caption("주간 탄단지", FitnessUi.COLOR_MUTED));
        titleColumn.addView(ui.text(
                "탄수화물 " + carbsSummary.display() + "g · 단백질 "
                        + proteinSummary.display() + "g · 지방 " + fatSummary.display() + "g",
                13,
                FitnessUi.COLOR_TEXT,
                true
        ));
        header.addView(titleColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(ui.text("기록 " + mealDays + "일", 12, FitnessUi.COLOR_TERTIARY, false));
        card.addView(header);

        LinearLayout legend = new LinearLayout(host.activity());
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        legend.setPadding(0, ui.dp(10), 0, 0);
        legend.addView(ui.text("탄수화물", 10, ui.hologramAccentColor(0), true));
        TextView proteinLegend = ui.text("단백질", 10, ui.hologramAccentColor(1), true);
        proteinLegend.setPadding(ui.dp(12), 0, 0, 0);
        legend.addView(proteinLegend);
        TextView fatLegend = ui.text("지방", 10, ui.hologramAccentColor(2), true);
        fatLegend.setPadding(ui.dp(12), 0, 0, 0);
        legend.addView(fatLegend);
        card.addView(legend);

        card.addView(weeklyMacroBarChart(
                carbs,
                protein,
                fat,
                carbsAvailable,
                proteinAvailable,
                fatAvailable,
                labels,
                chartDescription.toString()
        ), ui.fullWidthParams(ui.dp(8)));
        add(card);
    }

    private View weeklyMacroBarChart(
            double[] carbs,
            double[] protein,
            double[] fat,
            boolean[] carbsAvailable,
            boolean[] proteinAvailable,
            boolean[] fatAvailable,
            String[] labels,
            String description
    ) {
        FitnessUi ui = ui();
        double max = 1;
        boolean hasData = false;
        for (int i = 0; i < labels.length; i++) {
            if (carbsAvailable[i]) {
                max = Math.max(max, carbs[i]);
                hasData = hasData || carbs[i] > 0;
            }
            if (proteinAvailable[i]) {
                max = Math.max(max, protein[i]);
                hasData = hasData || protein[i] > 0;
            }
            if (fatAvailable[i]) {
                max = Math.max(max, fat[i]);
                hasData = hasData || fat[i] > 0;
            }
        }

        LinearLayout wrapper = new LinearLayout(host.activity());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setContentDescription(description);
        int chartHeight = hasData ? ui.dp(96) : ui.dp(28);

        LinearLayout barsRow = new LinearLayout(host.activity());
        barsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout dayGroup = new LinearLayout(host.activity());
            dayGroup.setOrientation(LinearLayout.HORIZONTAL);
            dayGroup.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            addMacroBar(dayGroup, carbs[i], max, carbsAvailable[i], ui.hologramAccentColor(0));
            addMacroBar(dayGroup, protein[i], max, proteinAvailable[i], ui.hologramAccentColor(1));
            addMacroBar(dayGroup, fat[i], max, fatAvailable[i], ui.hologramAccentColor(2));
            barsRow.addView(dayGroup, new LinearLayout.LayoutParams(
                    0, chartHeight, 1f));
        }
        wrapper.addView(barsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, chartHeight));

        View baseline = ui.hairline(FitnessUi.COLOR_BORDER);
        wrapper.addView(baseline, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));

        LinearLayout labelsRow = new LinearLayout(host.activity());
        labelsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : labels) {
            TextView day = ui.text(label, 11, FitnessUi.COLOR_TERTIARY, false);
            day.setGravity(Gravity.CENTER);
            labelsRow.addView(day, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelsParams.setMargins(0, ui.dp(8), 0, 0);
        wrapper.addView(labelsRow, labelsParams);
        return wrapper;
    }

    private void addMacroBar(
            LinearLayout parent,
            double value,
            double max,
            boolean available,
            int color
    ) {
        FitnessUi ui = ui();
        int height = !available || value <= 0
                ? ui.dp(4)
                : Math.max(ui.dp(10), (int) Math.round(value / max * ui.dp(96)));
        View bar = new View(host.activity());
        int barColor = available && value > 0 ? color : ui.barEmpty();
        bar.setBackground(ui.borderDrawable(barColor, barColor, ui.dp(999)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(6), height);
        params.setMargins(ui.dp(1), 0, ui.dp(1), 0);
        parent.addView(bar, params);
    }

    private static final class MacroSummary {
        private double knownSum;
        private boolean hasKnown;
        private boolean hasUnknown;

        private void add(NutritionTotals.Total total) {
            if (total.knownCount() > 0) {
                knownSum += total.knownSum();
                hasKnown = true;
            }
            hasUnknown = hasUnknown || total.missingCount() > 0;
        }

        private String display() {
            if (!hasKnown) {
                return "?";
            }
            return (hasUnknown ? "≥" : "") + NutritionCalculator.trim(knownSum);
        }
    }

    private String weeklyMealComparison(int currentMeals, int previousMeals) {
        int difference = currentMeals - previousMeals;
        if (difference == 0) {
            return "지난주와 동일한 식사 기록";
        }
        String direction = difference > 0 ? "증가" : "감소";
        return "지난주 대비 " + Math.abs(difference) + "끼 " + direction;
    }

    private String weeklyComparison(double currentVolume, double previousVolume) {
        double difference = currentVolume - previousVolume;
        if (Math.abs(difference) < 0.01) {
            return "지난주와 동일한 볼륨";
        }
        String direction = difference > 0 ? "증가" : "감소";
        String amount = FitnessUi.formatVolume(Math.abs(difference)) + "kg " + direction;
        if (previousVolume <= 0.01) {
            return "지난주 대비 " + amount;
        }
        double percent = Math.abs(difference) / previousVolume * 100.0;
        return "지난주 대비 " + amount + " (" + FitnessUi.formatVolume(percent) + "%)";
    }

    private View weeklyBarChart(
            double[] values,
            double[] previousValues,
            String[] labels,
            String descriptionLabel,
            String unit
    ) {
        FitnessUi ui = ui();
        double max = 1;
        boolean hasData = false;
        for (int i = 0; i < values.length; i++) {
            max = Math.max(max, Math.max(values[i], previousValues[i]));
            if (values[i] > 0 || previousValues[i] > 0) {
                hasData = true;
            }
        }

        LinearLayout wrapper = new LinearLayout(host.activity());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        StringBuilder chartDescription = new StringBuilder(descriptionLabel).append(". ");
        for (int index = 0; index < labels.length; index++) {
            if (index > 0) {
                chartDescription.append(", ");
            }
            chartDescription.append(labels[index])
                    .append(" 이번 주 ")
                    .append(FitnessUi.formatVolume(values[index]))
                    .append(unit)
                    .append(", 지난주 ")
                    .append(FitnessUi.formatVolume(previousValues[index]))
                    .append(unit);
        }
        wrapper.setContentDescription(chartDescription.toString());
        int chartHeight = hasData ? ui.dp(88) : ui.dp(28);

        LinearLayout barsRow = new LinearLayout(host.activity());
        barsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < values.length; i++) {
            LinearLayout pair = new LinearLayout(host.activity());
            pair.setOrientation(LinearLayout.HORIZONTAL);
            pair.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

            int currentBarHeight = values[i] <= 0
                    ? ui.dp(4)
                    : Math.max(ui.dp(10), (int) Math.round(values[i] / max * chartHeight));
            View currentBar = new View(host.activity());
            currentBar.setBackground(values[i] <= 0
                    ? ui.borderDrawable(ui.barEmpty(), ui.barEmpty(), ui.dp(999))
                    : ui.vibrantBackground(i, ui.dp(999)));
            LinearLayout.LayoutParams currentBarParams = new LinearLayout.LayoutParams(ui.dp(8), currentBarHeight);
            currentBarParams.setMargins(0, 0, ui.dp(2), 0);
            pair.addView(currentBar, currentBarParams);
            if (values[i] > 0) {
                ui.growBar(currentBar, i);
            }

            int previousBarHeight = previousValues[i] <= 0
                    ? ui.dp(4)
                    : Math.max(ui.dp(10), (int) Math.round(previousValues[i] / max * chartHeight));
            View previousBar = new View(host.activity());
            int previousBarColor = previousValues[i] <= 0 ? ui.barEmpty() : ui.barMuted();
            previousBar.setBackground(ui.borderDrawable(previousBarColor, previousBarColor, ui.dp(999)));
            LinearLayout.LayoutParams previousBarParams = new LinearLayout.LayoutParams(ui.dp(8), previousBarHeight);
            previousBarParams.setMargins(ui.dp(2), 0, 0, 0);
            pair.addView(previousBar, previousBarParams);
            if (previousValues[i] > 0) {
                ui.growBar(previousBar, i);
            }

            barsRow.addView(pair, new LinearLayout.LayoutParams(0, chartHeight, 1f));
        }
        wrapper.addView(barsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, chartHeight));

        // 기준선: 막대가 서 있는 바닥을 hairline으로 명시한다 (무드 차트 최소한의 축).
        View baseline = ui.hairline(FitnessUi.COLOR_BORDER);
        wrapper.addView(baseline, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));

        LinearLayout labelsRow = new LinearLayout(host.activity());
        labelsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            boolean isToday = i == LocalDate.now().getDayOfWeek().getValue() - 1;
            TextView day = ui.text(labels[i], 11,
                    isToday ? ui.ink() : FitnessUi.COLOR_TERTIARY, isToday);
            day.setGravity(Gravity.CENTER);
            labelsRow.addView(day, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelsParams.setMargins(0, ui.dp(8), 0, 0);
        wrapper.addView(labelsRow, labelsParams);
        return wrapper;
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
        for (FitnessRepository.MealEntry meal : repository().mealEntriesForDate(host.today())) {
            View row = ui.recordListRow(
                    "식",
                    meal.previewTitle,
                    meal.previewSubtitle(),
                    v -> host.openMealManagement(host.today(), FitnessScreen.HOME)
            );
            row.setContentDescription(meal.previewAccessibilityLabel() + ". 탭하여 식단 관리를 엽니다.");
            rows.add(row);
        }

        if (rows.isEmpty()) {
            emptyState("오늘 기록이 없습니다.", "운동, 체중, 식사를 기록해 보세요.");
            return;
        }
        int visibleCount = Math.min(TODAY_RECORD_LIMIT, rows.size());
        add(ui.rowsCard(new ArrayList<>(rows.subList(0, visibleCount))));
        if (rows.size() > visibleCount) {
            add(ui.textAction(
                    (rows.size() - visibleCount) + "개 기록 더 보기",
                    FitnessUi.COLOR_TERTIARY,
                    () -> host.navigate(FitnessScreen.RECORDS)
            ), ui.fullWidthParams(0));
        }
    }
}
