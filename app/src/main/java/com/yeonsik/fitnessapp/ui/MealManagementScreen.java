package com.yeonsik.fitnessapp.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn;
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal;
import com.yeonsik.fitnessapp.data.AthleteNutritionPolicy;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionCalculator;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionTotals;
import com.yeonsik.fitnessapp.data.NutritionUnit;
import com.yeonsik.fitnessapp.data.ProductNutritionLink;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 피트니스 하위의 식단 작업 공간.
 * 날짜별 섭취 요약, 식사 기록, 음식 카탈로그 검색, 구성 메뉴 저장을 한 흐름으로 제공한다.
 */
public final class MealManagementScreen extends BaseScreen {
    private static final int CATALOG_MODE_NUTRIENTS = 0;
    private static final int CATALOG_MODE_INGREDIENT = 1;
    private static final int CATALOG_MODE_MENU = 2;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);

    private String selectedDate;
    private String draftName = "";
    private String catalogQuery = "";
    private final List<MealCompositionItem> draftItems = new ArrayList<>();

    private EditText mealNameInput;
    private EditText catalogSearchInput;
    private LinearLayout compositionRows;
    private LinearLayout catalogResults;
    private LinearLayout compositionTotalBox;
    private final List<EditText> quantityInputs = new ArrayList<>();
    private int catalogMode = CATALOG_MODE_NUTRIENTS;
    private boolean initialSyncRequested;
    private boolean catalogSyncing;
    private String syncMessage = "기기와 원격 카탈로그를 함께 검색합니다.";
    private final ProductNutritionLinkDialogController productLinkController;
    private boolean savedMenusVisible;

    public MealManagementScreen(ScreenHost host) {
        super(host);
        selectedDate = host.today();
        productLinkController = new ProductNutritionLinkDialogController(host);
    }

    @Override
    public void render() {
        syncDraftFromViews();

        add(ui().textAction("‹ 피트니스", FitnessUi.COLOR_MUTED,
                () -> host.navigate(FitnessScreen.WORKOUT)), ui().fullWidthParams(0));
        screenHeader("NUTRITION", "식단 관리");
        add(ui().text(
                "먹은 음식과 나만의 메뉴를 한 곳에서 기록하고, 영양 흐름을 확인하세요.",
                14,
                FitnessUi.COLOR_MUTED,
                false
        ), ui().fullWidthParams(0));

        add(dateNavigator());
        add(dailySummary());

        section("선수 체크인", "기록", this::showAthleteCheckInDialog);
        add(athleteCheckInCard());

        section("기록된 끼니");
        renderMealEntries();

        section("영양 분석");
        add(proteinDistributionCard());
        add(detailedNutrientsCard());

        section("새 끼니 + 영양 카탈로그");
        add(mealWorkspace());

        if (!initialSyncRequested) {
            initialSyncRequested = true;
            syncCatalog(false);
        }
    }

    private View dateNavigator() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView previous = ui.textAction("‹", FitnessUi.COLOR_MUTED, () -> {
            selectedDate = LocalDate.parse(selectedDate).minusDays(1).toString();
            host.rerender();
        });
        row.addView(previous, new LinearLayout.LayoutParams(ui.dp(42), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout dateColumn = new LinearLayout(host.activity());
        dateColumn.setOrientation(LinearLayout.VERTICAL);
        dateColumn.setGravity(Gravity.CENTER);
        TextView date = ui.textAction(dateLabel(), FitnessUi.COLOR_TEXT, this::showDatePicker);
        date.setTextSize(17);
        date.setGravity(Gravity.CENTER);
        dateColumn.addView(date);
        TextView helper = ui.text(
                isToday() ? "오늘 · 탭하여 지난날 선택" : "지난날 기록 · 탭하여 날짜 변경",
                11,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        helper.setGravity(Gravity.CENTER);
        dateColumn.addView(helper);
        row.addView(dateColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (!isToday()) {
            row.addView(ui.textAction("오늘", FitnessUi.COLOR_TERTIARY, () -> {
                selectedDate = host.today();
                host.rerender();
            }));
        }

        boolean canMoveForward = !isToday();
        TextView next = ui.textAction("›", FitnessUi.COLOR_MUTED, () -> {
            if (canMoveForward) {
                selectedDate = LocalDate.parse(selectedDate).plusDays(1).toString();
                host.rerender();
            }
        });
        next.setEnabled(canMoveForward);
        next.setAlpha(canMoveForward ? 1f : 0.35f);
        row.addView(next, new LinearLayout.LayoutParams(ui.dp(42), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row);
        card.addView(ui.button(
                isToday() ? "지난날 끼니 기록하기" : "기록 날짜 다시 선택",
                false,
                v -> showDatePicker()
        ), ui.fullWidthParams(ui.dp(12)));
        return card;
    }

    private void showDatePicker() {
        LocalDate current = LocalDate.parse(selectedDate);
        DatePickerDialog picker = new DatePickerDialog(
                host.activity(),
                (view, year, month, dayOfMonth) -> {
                    selectedDate = LocalDate.of(year, month + 1, dayOfMonth).toString();
                    host.rerender();
                },
                current.getYear(),
                current.getMonthValue() - 1,
                current.getDayOfMonth()
        );
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.setTitle("끼니를 기록할 날짜");
        picker.show();
    }

    private View dailySummary() {
        FitnessUi ui = ui();
        FitnessRepository.MealNutritionSummary summary = repository().mealNutritionForDate(selectedDate);
        AthleteNutritionGoal goal = repository().nutritionGoal();
        FitnessRepository.BodyMetricEntry weight = repository().latestBodyMetricOnOrBefore(selectedDate);
        LinearLayout card = ui.heroCard();

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.caption("DAILY NUTRITION", FitnessUi.COLOR_FLOW_MUTED),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View goalBadge = ui.flowStatusBadge(
                goal == null ? "목표 설정" : goal.phaseLabel(),
                goal == null ? FitnessUi.COLOR_WARNING : FitnessUi.COLOR_POSITIVE
        );
        goalBadge.setClickable(true);
        goalBadge.setFocusable(true);
        goalBadge.setOnClickListener(v -> showNutritionGoalDialog());
        header.addView(goalBadge);
        card.addView(header);

        LinearLayout caloriesRow = new LinearLayout(host.activity());
        caloriesRow.setOrientation(LinearLayout.HORIZONTAL);
        caloriesRow.setGravity(Gravity.BOTTOM);
        caloriesRow.setPadding(0, ui.dp(14), 0, ui.dp(2));
        caloriesRow.addView(ui.num(String.valueOf(Math.round(summary.calories)), 38,
                FitnessUi.COLOR_FLOW_TEXT, true));
        String calorieUnit = goal == null
                ? " kcal"
                : " / " + Math.round(goal.caloriesKcal) + " kcal";
        TextView unit = ui.text(calorieUnit, 16, FitnessUi.COLOR_FLOW_MUTED, true);
        unit.setPadding(0, 0, 0, ui.dp(6));
        caloriesRow.addView(unit);
        caloriesRow.addView(ui.text("  ·  " + summary.mealCount + "끼 기록", 13,
                FitnessUi.COLOR_FLOW_MUTED, false));
        card.addView(caloriesRow);

        if (goal == null) {
            LinearLayout firstMacroRow = ui.tileRow();
            firstMacroRow.addView(ui.flowMetric(
                    "단백질",
                    NutritionCalculator.trim(summary.proteinGrams) + "g"
            ), ui.tileParams(true));
            firstMacroRow.addView(ui.flowMetric(
                    "탄수화물",
                    NutritionCalculator.trim(summary.carbsGrams) + "g"
            ), ui.tileParams(false));
            card.addView(firstMacroRow, ui.fullWidthParams(ui.dp(10)));

            LinearLayout secondMacroRow = ui.tileRow();
            secondMacroRow.addView(ui.flowMetric(
                    "지방",
                    NutritionCalculator.trim(summary.fatGrams) + "g"
            ), ui.tileParams(true));
            secondMacroRow.addView(ui.flowMetric(
                    "상태",
                    summary.mealCount == 0 ? "기록 시작" : "목표 미설정"
            ), ui.tileParams(false));
            card.addView(secondMacroRow, ui.fullWidthParams(ui.dp(6)));
            card.addView(ui.flowHeroButton("일일 영양 목표 설정", v -> showNutritionGoalDialog()),
                    ui.fullWidthParams(ui.dp(14)));
        } else {
            addGoalProgress(card, "열량", summary.calories, goal.caloriesKcal, "kcal");
            addGoalProgress(card, "단백질", summary.proteinGrams, goal.proteinGrams, "g");
            addGoalProgress(card, "탄수화물", summary.carbsGrams, goal.carbsGrams, "g");
            addGoalProgress(card, "지방", summary.fatGrams, goal.fatGrams, "g");
        }

        Double gramsPerKg = AthleteNutritionPolicy.proteinGramsPerKg(
                summary.proteinGrams,
                weight == null ? null : weight.weightKg
        );
        String weightLine = weight == null
                ? "체중을 기록하면 단백질 g/kg를 표시합니다.  ›"
                : "체중 " + NutritionCalculator.trim(weight.weightKg) + "kg 기준 · 단백질 "
                + NutritionCalculator.trim(gramsPerKg) + "g/kg";
        TextView weightView = ui.text(weightLine, 12, FitnessUi.COLOR_FLOW_MUTED, false);
        weightView.setPadding(0, ui.dp(14), 0, 0);
        if (weight == null) {
            weightView.setClickable(true);
            weightView.setFocusable(true);
            weightView.setOnClickListener(v -> host.showBodyMetricDialog(selectedDate, null));
        }
        card.addView(weightView);
        if (weight != null) {
            TextView reference = ui.text(
                    "일반 운동인 참고 1.4–2.0g/kg/일 · 개인 목표가 우선",
                    11,
                    FitnessUi.COLOR_FLOW_MUTED,
                    false
            );
            reference.setPadding(0, ui.dp(3), 0, 0);
            card.addView(reference);
        }
        return card;
    }

    private void addGoalProgress(
            LinearLayout card,
            String label,
            double consumed,
            double target,
            String unit
    ) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView labelView = ui.text(label, 12, FitnessUi.COLOR_FLOW_MUTED, true);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        double exceeded = AthleteNutritionPolicy.exceeded(consumed, target);
        String detail = NutritionCalculator.trim(consumed) + " / "
                + NutritionCalculator.trim(target) + unit + " · "
                + (exceeded > 0
                ? NutritionCalculator.trim(exceeded) + unit + " 초과"
                : NutritionCalculator.trim(AthleteNutritionPolicy.remaining(consumed, target))
                + unit + " 남음");
        row.addView(ui.text(detail, 12, FitnessUi.COLOR_FLOW_TEXT, true));
        card.addView(row, ui.fullWidthParams(ui.dp(9)));
        card.addView(
                ui.progressBar(AthleteNutritionPolicy.progressRatio(consumed, target), true),
                ui.fullWidthParams(ui.dp(5))
        );
    }

    private View athleteCheckInCard() {
        FitnessUi ui = ui();
        AthleteDailyCheckIn checkIn = repository().athleteCheckInForDate(selectedDate);
        AthleteNutritionGoal goal = repository().nutritionGoal();
        LinearLayout card = ui.card();

        ui.cardHeader(
                card,
                "수분 · 회복 상태",
                checkIn.hasWellnessData() ? "기록됨" : "컨디션 미기록"
        );

        String waterValue = NutritionCalculator.trim(checkIn.waterMl) + "ml";
        if (goal != null) {
            waterValue += " / " + goal.waterMl + "ml";
        }
        card.addView(ui.keyValue("수분", waterValue), ui.fullWidthParams(ui.dp(8)));
        if (goal != null) {
            card.addView(
                    ui.progressBar(
                            AthleteNutritionPolicy.progressRatio(checkIn.waterMl, goal.waterMl),
                            false
                    ),
                    ui.fullWidthParams(ui.dp(7))
            );
        }

        LinearLayout waterActions = new LinearLayout(host.activity());
        waterActions.setOrientation(LinearLayout.HORIZONTAL);
        waterActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        waterActions.addView(ui.textAction("+250ml", FitnessUi.COLOR_MUTED,
                () -> addWater(250)));
        waterActions.addView(ui.textAction("+500ml", FitnessUi.COLOR_MUTED,
                () -> addWater(500)));
        waterActions.addView(ui.textAction("수정", FitnessUi.COLOR_TEXT,
                this::showAthleteCheckInDialog));
        card.addView(waterActions, ui.fullWidthParams(ui.dp(5)));

        LinearLayout firstRow = ui.tileRow();
        firstRow.addView(ui.inlineStat("수면", formatSleep(checkIn.sleepHours), false),
                ui.tileParams(true));
        firstRow.addView(ui.inlineStat("에너지", formatScore(checkIn.energyScore), false),
                ui.tileParams(false));
        card.addView(firstRow, ui.fullWidthParams(ui.dp(12)));

        LinearLayout secondRow = ui.tileRow();
        secondRow.addView(ui.inlineStat("허기", formatScore(checkIn.hungerScore), false),
                ui.tileParams(true));
        secondRow.addView(ui.inlineStat("소화", formatScore(checkIn.digestionScore), false),
                ui.tileParams(false));
        card.addView(secondRow, ui.fullWidthParams(ui.dp(10)));

        LinearLayout thirdRow = ui.tileRow();
        thirdRow.addView(ui.inlineStat(
                "훈련 준비도",
                formatScore(checkIn.trainingReadinessScore),
                false
        ), ui.tileParams(true));
        thirdRow.addView(ui.inlineStat(
                "메모",
                checkIn.note.isEmpty() ? "—" : checkIn.note,
                false
        ), ui.tileParams(false));
        card.addView(thirdRow, ui.fullWidthParams(ui.dp(10)));

        if (isLowScore(checkIn.energyScore) || isLowScore(checkIn.trainingReadinessScore)) {
            TextView warning = ui.text(
                    "낮은 상태가 반복되면 섭취량·훈련량을 점검하고 스포츠의학 전문가와 상담하세요.",
                    12,
                    FitnessUi.COLOR_WARNING,
                    true
            );
            warning.setPadding(0, ui.dp(12), 0, 0);
            card.addView(warning);
        }
        return card;
    }

    private View proteinDistributionCard() {
        FitnessUi ui = ui();
        List<FitnessRepository.MealEntry> meals = repository().mealEntriesForDate(selectedDate);
        FitnessRepository.BodyMetricEntry weight = repository().latestBodyMetricOnOrBefore(selectedDate);
        LinearLayout card = ui.card();
        ui.cardHeader(card, "단백질 분배", meals.size() + "끼");

        Double perMealReference = AthleteNutritionPolicy.perMealProteinReference(
                weight == null ? null : weight.weightKg
        );
        String reference = perMealReference == null
                ? "1회 20–40g 또는 체중×0.25g, 3–4시간 간격 참고"
                : "체중 기준 1회 약 " + NutritionCalculator.trim(perMealReference)
                + "g · 일반 참고 20–40g";
        TextView referenceView = ui.text(reference, 12, FitnessUi.COLOR_MUTED, false);
        referenceView.setPadding(0, ui.dp(7), 0, ui.dp(3));
        card.addView(referenceView);

        if (meals.isEmpty()) {
            card.addView(ui.text("끼니를 기록하면 식사별 단백질을 비교합니다.",
                    13, FitnessUi.COLOR_TERTIARY, false), ui.fullWidthParams(ui.dp(10)));
            return card;
        }
        for (FitnessRepository.MealEntry meal : meals) {
            card.addView(ui.keyValue(
                    meal.mealLabel + " · " + meal.menu,
                    NutritionCalculator.trim(meal.proteinGrams) + "g"
            ));
        }
        return card;
    }

    private View detailedNutrientsCard() {
        FitnessUi ui = ui();
        NutritionTotals totals = repository().mealNutritionTotalsForDate(selectedDate);
        AthleteNutritionGoal goal = repository().nutritionGoal();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "주요 영양성분", "미상은 0으로 계산하지 않음");

        addNutrientPair(
                card,
                "식이섬유",
                formatNutrientTotal(totals, NutritionProfile.FIBER_GRAMS,
                        goal == null ? null : goal.fiberGrams),
                "나트륨",
                formatNutrientTotal(totals, NutritionProfile.SODIUM_MG,
                        goal == null ? null : goal.sodiumMg)
        );
        addNutrientPair(card,
                "칼륨", formatNutrientTotal(totals, NutrientCode.POTASSIUM, null),
                "마그네슘", formatNutrientTotal(totals, NutrientCode.MAGNESIUM, null));
        addNutrientPair(card,
                "칼슘", formatNutrientTotal(totals, NutrientCode.CALCIUM, null),
                "철", formatNutrientTotal(totals, NutrientCode.IRON, null));
        addNutrientPair(card,
                "아연", formatNutrientTotal(totals, NutrientCode.ZINC, null),
                "비타민 D", formatNutrientTotal(totals, NutrientCode.VITAMIN_D, null));
        addNutrientPair(card,
                "엽산", formatNutrientTotal(totals, NutrientCode.VITAMIN_B9, null),
                "비타민 B12", formatNutrientTotal(totals, NutrientCode.VITAMIN_B12, null));
        return card;
    }

    private void addNutrientPair(
            LinearLayout card,
            String firstLabel,
            String firstValue,
            String secondLabel,
            String secondValue
    ) {
        FitnessUi ui = ui();
        LinearLayout row = ui.tileRow();
        row.addView(ui.inlineStat(firstLabel, firstValue, false), ui.tileParams(true));
        row.addView(ui.inlineStat(secondLabel, secondValue, false), ui.tileParams(false));
        card.addView(row, ui.fullWidthParams(ui.dp(12)));
    }

    private String formatNutrientTotal(NutritionTotals totals, String key, Double target) {
        String value = NutritionCalculator.describeTotal(totals.total(key));
        String unit = NutritionProfile.unitOf(key);
        if (target == null) {
            return value + ("?".equals(value) ? "" : unit);
        }
        return value + ("?".equals(value) ? "" : unit)
                + " / " + NutritionCalculator.trim(target) + unit;
    }

    private void addWater(int amountMl) {
        try {
            repository().addWaterForDate(selectedDate, amountMl);
            host.rerender();
        } catch (IllegalArgumentException error) {
            host.toast(error.getMessage());
        }
    }

    private static String formatSleep(Double sleepHours) {
        return sleepHours == null ? "—" : NutritionCalculator.trim(sleepHours) + "시간";
    }

    private static String formatScore(Integer score) {
        return score == null ? "—" : score + "/5";
    }

    private static boolean isLowScore(Integer score) {
        return score != null && score <= 2;
    }

    private void showNutritionGoalDialog() {
        FitnessUi ui = ui();
        AthleteNutritionGoal current = repository().nutritionGoal();
        String[] phaseCodes = AthleteNutritionGoal.PHASES.toArray(new String[0]);
        String[] phaseLabels = new String[phaseCodes.length];
        for (int index = 0; index < phaseCodes.length; index++) {
            phaseLabels[index] = AthleteNutritionGoal.phaseLabel(phaseCodes[index]);
        }
        int[] phaseIndex = new int[]{current == null
                ? AthleteNutritionGoal.PHASES.indexOf(AthleteNutritionGoal.PHASE_MAINTENANCE)
                : AthleteNutritionGoal.PHASES.indexOf(current.phase)};

        LinearLayout form = ui.form();
        TextView guidance = ui.text(
                "자동 처방값이 아닙니다. 코치·영양사와 정한 하루 목표를 입력하세요.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        form.addView(guidance);

        Button phase = ui.button(phaseLabels[phaseIndex[0]], false, null);
        phase.setOnClickListener(v -> new AlertDialog.Builder(host.activity())
                .setTitle("현재 단계")
                .setItems(phaseLabels, (dialog, which) -> {
                    phaseIndex[0] = which;
                    phase.setText(phaseLabels[which]);
                })
                .show());
        form.addView(ui.labeledFieldColumn("현재 단계", phase), ui.fullWidthParams(ui.dp(12)));

        EditText calories = ui.decimalInput("kcal", goalValue(current, GoalField.CALORIES));
        EditText protein = ui.decimalInput("g", goalValue(current, GoalField.PROTEIN));
        EditText carbs = ui.decimalInput("g", goalValue(current, GoalField.CARBS));
        EditText fat = ui.decimalInput("g", goalValue(current, GoalField.FAT));
        EditText fiber = ui.decimalInput("g", goalValue(current, GoalField.FIBER));
        EditText sodium = ui.decimalInput("mg", goalValue(current, GoalField.SODIUM));
        EditText water = ui.numberInput("ml", current == null ? "" : String.valueOf(current.waterMl));

        form.addView(pairedFields("열량", calories, "단백질", protein),
                ui.fullWidthParams(ui.dp(10)));
        form.addView(pairedFields("탄수화물", carbs, "지방", fat),
                ui.fullWidthParams(ui.dp(10)));
        form.addView(pairedFields("식이섬유", fiber, "나트륨", sodium),
                ui.fullWidthParams(ui.dp(10)));
        form.addView(ui.labeledFieldColumn("수분", water), ui.fullWidthParams(ui.dp(10)));

        ui.validatedSheet("일일 영양 목표", form, "목표 저장", () -> {
            try {
                Double caloriesValue = FitnessUi.optionalDouble(calories);
                Double proteinValue = FitnessUi.optionalDouble(protein);
                Double carbsValue = FitnessUi.optionalDouble(carbs);
                Double fatValue = FitnessUi.optionalDouble(fat);
                Double fiberValue = FitnessUi.optionalDouble(fiber);
                Double sodiumValue = FitnessUi.optionalDouble(sodium);
                Integer waterValue = FitnessUi.optionalInt(water);
                if (caloriesValue == null || proteinValue == null || carbsValue == null
                        || fatValue == null || fiberValue == null || sodiumValue == null
                        || waterValue == null) {
                    throw new IllegalArgumentException("모든 목표값을 입력하세요.");
                }
                repository().saveNutritionGoal(new AthleteNutritionGoal(
                        phaseCodes[phaseIndex[0]],
                        caloriesValue,
                        proteinValue,
                        carbsValue,
                        fatValue,
                        fiberValue,
                        sodiumValue,
                        waterValue
                ));
                host.rerender();
                return true;
            } catch (IllegalArgumentException error) {
                host.toast(error.getMessage());
                return false;
            }
        });
    }

    private void showAthleteCheckInDialog() {
        FitnessUi ui = ui();
        AthleteDailyCheckIn current = repository().athleteCheckInForDate(selectedDate);
        LinearLayout form = ui.form();

        TextView guidance = ui.text(
                "점수는 진단이 아니라 변화 관찰용입니다. 허기는 5가 가장 강하고, 나머지는 5가 가장 좋습니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        form.addView(guidance);

        EditText water = ui.numberInput("ml", String.valueOf(current.waterMl));
        EditText sleep = ui.decimalInput(
                "시간",
                current.sleepHours == null ? "" : NutritionCalculator.trim(current.sleepHours)
        );
        form.addView(pairedFields("수분 섭취", water, "수면", sleep),
                ui.fullWidthParams(ui.dp(10)));

        int[] scores = new int[]{
                nullableScore(current.energyScore),
                nullableScore(current.hungerScore),
                nullableScore(current.digestionScore),
                nullableScore(current.trainingReadinessScore)
        };
        Button energy = scoreButton(
                "에너지",
                scores,
                0,
                new String[]{"미기록", "1 · 매우 낮음", "2 · 낮음", "3 · 보통", "4 · 좋음", "5 · 매우 좋음"}
        );
        Button hunger = scoreButton(
                "허기",
                scores,
                1,
                new String[]{"미기록", "1 · 거의 없음", "2 · 약함", "3 · 보통", "4 · 강함", "5 · 매우 강함"}
        );
        Button digestion = scoreButton(
                "소화",
                scores,
                2,
                new String[]{"미기록", "1 · 매우 불편", "2 · 불편", "3 · 보통", "4 · 편안", "5 · 매우 편안"}
        );
        Button readiness = scoreButton(
                "훈련 준비도",
                scores,
                3,
                new String[]{"미기록", "1 · 준비 안 됨", "2 · 낮음", "3 · 보통", "4 · 좋음", "5 · 매우 좋음"}
        );
        form.addView(pairedFields("에너지", energy, "허기", hunger),
                ui.fullWidthParams(ui.dp(10)));
        form.addView(pairedFields("소화", digestion, "훈련 준비도", readiness),
                ui.fullWidthParams(ui.dp(10)));

        EditText note = ui.input("특이사항", current.note);
        note.setSingleLine(false);
        note.setMinLines(2);
        note.setMaxLines(3);
        note.setGravity(Gravity.TOP | Gravity.START);
        note.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        form.addView(ui.labeledFieldColumn("메모", note), ui.fullWidthParams(ui.dp(10)));

        ui.validatedSheet("선수 체크인", form, "기록 저장", () -> {
            try {
                Integer waterValue = FitnessUi.optionalInt(water);
                if (waterValue == null) {
                    throw new IllegalArgumentException("수분 섭취량을 입력하세요.");
                }
                repository().saveAthleteCheckIn(new AthleteDailyCheckIn(
                        current.id,
                        selectedDate,
                        waterValue,
                        FitnessUi.optionalDouble(sleep),
                        scoreOrNull(scores[0]),
                        scoreOrNull(scores[1]),
                        scoreOrNull(scores[2]),
                        scoreOrNull(scores[3]),
                        FitnessUi.inputText(note)
                ));
                host.rerender();
                return true;
            } catch (IllegalArgumentException error) {
                host.toast(error.getMessage());
                return false;
            }
        });
    }

    private Button scoreButton(String title, int[] scores, int scoreIndex, String[] options) {
        FitnessUi ui = ui();
        Button button = ui.button(scoreButtonText(scores[scoreIndex]), false, null);
        button.setOnClickListener(v -> new AlertDialog.Builder(host.activity())
                .setTitle(title)
                .setItems(options, (dialog, which) -> {
                    scores[scoreIndex] = which;
                    button.setText(scoreButtonText(which));
                })
                .show());
        return button;
    }

    private View pairedFields(String firstLabel, View first, String secondLabel, View second) {
        FitnessUi ui = ui();
        LinearLayout row = ui.tileRow();
        row.addView(ui.labeledFieldColumn(firstLabel, first), ui.fieldCellParams(true));
        row.addView(ui.labeledFieldColumn(secondLabel, second), ui.fieldCellParams(false));
        return row;
    }

    private static String scoreButtonText(int score) {
        return score <= 0 ? "미기록" : score + " / 5";
    }

    private static int nullableScore(Integer score) {
        return score == null ? 0 : score;
    }

    private static Integer scoreOrNull(int score) {
        return score <= 0 ? null : score;
    }

    private static String goalValue(AthleteNutritionGoal goal, GoalField field) {
        if (goal == null) {
            return "";
        }
        switch (field) {
            case CALORIES:
                return NutritionCalculator.trim(goal.caloriesKcal);
            case PROTEIN:
                return NutritionCalculator.trim(goal.proteinGrams);
            case CARBS:
                return NutritionCalculator.trim(goal.carbsGrams);
            case FAT:
                return NutritionCalculator.trim(goal.fatGrams);
            case FIBER:
                return NutritionCalculator.trim(goal.fiberGrams);
            case SODIUM:
                return NutritionCalculator.trim(goal.sodiumMg);
            default:
                return "";
        }
    }

    private enum GoalField {
        CALORIES,
        PROTEIN,
        CARBS,
        FAT,
        FIBER,
        SODIUM
    }

    private void renderMealEntries() {
        List<FitnessRepository.MealEntry> entries = repository().mealEntriesForDate(selectedDate);
        if (entries.isEmpty()) {
            emptyState("아직 기록된 끼니가 없습니다.", "아래에서 메뉴를 검색해 1끼를 추가하세요.");
        } else {
            List<View> rows = new ArrayList<>();
            for (int index = 0; index < entries.size(); index++) {
                rows.add(mealRow(entries.get(index), index));
            }
            add(ui().rowsCard(rows));
        }
        renderSavedMenusSection();
    }

    private void renderSavedMenusSection() {
        FitnessUi ui = ui();
        LinearLayout section = new LinearLayout(host.activity());
        section.setOrientation(LinearLayout.VERTICAL);

        Button toggle = ui.button(
                savedMenusVisible ? "저장된 메뉴 닫기" : "저장된 메뉴 보기",
                false,
                v -> {
                    savedMenusVisible = !savedMenusVisible;
                    host.rerender();
                }
        );
        section.addView(toggle, ui.fullWidthParams(ui.dp(8)));

        if (savedMenusVisible) {
            List<NutritionFood> recipes = host.nutritionCatalogRepository().savedRecipes();
            if (recipes.isEmpty()) {
                section.addView(ui.text(
                        "저장된 메뉴가 없습니다. 메뉴 등록에서 먼저 저장하세요.",
                        12,
                        FitnessUi.COLOR_TERTIARY,
                        false
                ), ui.fullWidthParams(ui.dp(8)));
            } else {
                for (NutritionFood recipe : recipes) {
                    section.addView(savedMenuRow(recipe), ui.fullWidthParams(ui.dp(6)));
                }
            }
        }

        add(section, ui.fullWidthParams(ui.dp(2)));
    }

    private View savedMenuRow(NutritionFood recipe) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(58));
        row.setPadding(ui.dp(12), ui.dp(6), ui.dp(10), ui.dp(6));
        row.setBackground(ui.flatSurfaceRippleDrawable(ui.dp(12)));
        row.setClickable(true);
        row.setFocusable(true);
        ui.pressFeedback(row);
        row.setOnClickListener(v -> showSavedMenuDialog(recipe));

        row.addView(ui.glyphCircle("M", false));
        TextView label = ui.text(
                recipe.displayName() + "  ·  " + recipe.basisLabel()
                        + "  ·  " + recipe.nutritionLabel(),
                13,
                FitnessUi.COLOR_TEXT,
                true
        );
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(ui.dp(10), 0, ui.dp(8), 0);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = ui.text("›", 22, FitnessUi.COLOR_TERTIARY, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(ui.dp(24), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void showSavedMenuDialog(NutritionFood recipe) {
        FitnessUi ui = ui();
        LinearLayout body = ui.form();
        body.addView(ui.text(
                recipe.basisLabel() + " 기준  ·  " + recipe.nutritionLabel(),
                13,
                FitnessUi.COLOR_MUTED,
                false
        ));
        body.addView(ui.text(
                "메뉴 구성 식품의 단위 영양",
                12,
                FitnessUi.COLOR_TERTIARY,
                true
        ), ui.fullWidthParams(ui.dp(14)));

        List<NutritionCatalogRepository.RecipeComponent> components =
                host.nutritionCatalogRepository().recipeComponents(recipe.id);
        if (components.isEmpty()) {
            body.addView(ui.text(
                    "구성 식품 정보를 찾을 수 없습니다.",
                    13,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ), ui.fullWidthParams(ui.dp(8)));
        } else {
            for (NutritionCatalogRepository.RecipeComponent component : components) {
                body.addView(savedMenuComponentRow(component), ui.fullWidthParams(ui.dp(8)));
            }
        }

        ScrollView scroll = new ScrollView(host.activity());
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(host.activity())
                .setTitle(recipe.displayName())
                .setView(scroll)
                .setNegativeButton("닫기", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() == null) {
                return;
            }
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.dimAmount = 0.62f;
            dialog.getWindow().setAttributes(params);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        });
        dialog.show();
    }

    private View savedMenuComponentRow(NutritionCatalogRepository.RecipeComponent component) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        row.setBackground(ui.flatSurfaceDrawable(ui.dp(12)));

        String amount = NutritionCalculator.trim(component.quantity)
                + NutritionUnit.display(component.unit);
        row.addView(ui.text(component.food.displayName(), 14, FitnessUi.COLOR_TEXT, true));
        row.addView(ui.text(
                "구성량 " + amount + "  ·  기준 " + component.food.basisLabel(),
                11,
                FitnessUi.COLOR_TERTIARY,
                false
        ));
        row.addView(ui.text(
                component.food.unitNutritionLabel(),
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        return row;
    }

    private View mealRow(FitnessRepository.MealEntry entry, int mealIndex) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(72));
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        row.addView(ui.glyphCircle(String.valueOf(mealIndex + 1), false));

        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(ui.dp(12), 0, ui.dp(8), 0);
        column.addView(ui.text(entry.mealLabel + "  ·  " + entry.menu, 15,
                FitnessUi.COLOR_TEXT, true));
        String composition = entry.compositionCount > 0
                ? "구성 " + entry.compositionCount + "개"
                : "직접 입력";
        column.addView(ui.text(
                composition + "  ·  P " + NutritionCalculator.trim(entry.proteinGrams) +
                        "g  C " + NutritionCalculator.trim(entry.carbsGrams) +
                        "g  F " + NutritionCalculator.trim(entry.fatGrams) + "g",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        if (entry.compositionCount > 0) {
            column.addView(ui.text(
                    snapshotSummary(entry.id),
                    11,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
        }
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout trailing = new LinearLayout(host.activity());
        trailing.setOrientation(LinearLayout.VERTICAL);
        trailing.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        trailing.addView(ui.num(entry.calories + " kcal", 15, FitnessUi.COLOR_TEXT, true));
        trailing.addView(ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE,
                () -> confirmDeleteMeal(entry)));
        row.addView(trailing);
        return row;
    }

    /**
     * 기록된 식사의 구성과 확장 영양소를 보여 준다.
     *
     * <p>값은 카탈로그를 다시 조회하지 않고 섭취 당시 스냅샷에서만 읽는다. 그래서 나중에
     * 음식 DB를 고쳐도 이 줄은 그대로 남는다.</p>
     */
    private String snapshotSummary(String mealRecordId) {
        List<FitnessRepository.MealItemEntry> items = repository().mealItemsForRecord(mealRecordId);
        if (items.isEmpty()) {
            return "구성 스냅샷 없음";
        }

        List<String> names = new ArrayList<>();
        NutritionTotals.Builder totals = NutritionTotals.builder();
        for (FitnessRepository.MealItemEntry item : items) {
            names.add(item.foodName + " " + NutritionCalculator.trim(item.quantity) + item.unit);
            totals.add(item.profile);
        }
        NutritionTotals total = totals.build();
        return String.join(", ", names)
                + "\n나트륨 " + NutritionCalculator.describeTotal(
                        total.total(NutritionProfile.SODIUM_MG)) + "mg  ·  포화지방 "
                + NutritionCalculator.describeTotal(
                        total.total(NutritionProfile.SATURATED_FAT_GRAMS)) + "g  ·  당류 "
                + NutritionCalculator.describeTotal(
                        total.total(NutritionProfile.SUGARS_GRAMS)) + "g";
    }

    private View mealWorkspace() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        String nextMealLabel = repository().nextMealLabelForDate(selectedDate);
        ui.cardHeader(card, "새 끼니 구성", nextMealLabel + " · "
                + (selectedDate.equals(host.today()) ? "오늘" : dateLabel()));

        mealNameInput = ui.input("메뉴 이름 (예: 닭갈비, 햄버거)", draftName);
        card.addView(ui.labeledFieldColumn("이번 끼니의 이름", mealNameInput),
                ui.fullWidthParams(ui.dp(12)));

        LinearLayout compositionHeader = new LinearLayout(host.activity());
        compositionHeader.setOrientation(LinearLayout.HORIZONTAL);
        compositionHeader.setGravity(Gravity.CENTER_VERTICAL);
        compositionHeader.addView(ui.caption("현재 구성", FitnessUi.COLOR_MUTED),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        compositionHeader.addView(ui.textAction("초기화", FitnessUi.COLOR_TERTIARY, () -> {
            draftItems.clear();
            host.rerender();
        }));
        card.addView(compositionHeader, ui.fullWidthParams(ui.dp(16)));

        compositionRows = new LinearLayout(host.activity());
        compositionRows.setOrientation(LinearLayout.VERTICAL);
        card.addView(compositionRows, ui.fullWidthParams(ui.dp(4)));
        renderCompositionRows();

        compositionTotalBox = new LinearLayout(host.activity());
        compositionTotalBox.setOrientation(LinearLayout.VERTICAL);
        compositionTotalBox.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(8));
        compositionTotalBox.setBackground(ui.flatSurfaceDrawable(ui.dp(14)));
        card.addView(compositionTotalBox, ui.fullWidthParams(ui.dp(12)));
        updateCompositionTotal();

        Button saveMeal = ui.button(nextMealLabel + " 기록하기", true, v -> saveMeal());
        Button saveRecipe = ui.button("메뉴 카탈로그에 저장", false, v -> saveRecipe());
        card.addView(ui.buttonRow(saveMeal, saveRecipe), ui.fullWidthParams(ui.dp(16)));
        card.addView(ui.hairline(ui.border()), ui.fullWidthParams(ui.dp(20)));
        appendCatalogSection(card);
        return card;
    }

    private void renderCompositionRows() {
        if (compositionRows == null) {
            return;
        }
        FitnessUi ui = ui();
        compositionRows.removeAllViews();
        quantityInputs.clear();
        if (draftItems.isEmpty()) {
            compositionRows.addView(ui.text(
                    "카탈로그에서 음식이나 메뉴를 선택하면 여기에 구성됩니다.",
                    13,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
            return;
        }

        for (int index = 0; index < draftItems.size(); index++) {
            final int itemIndex = index;
            MealCompositionItem item = draftItems.get(index);
            LinearLayout row = new LinearLayout(host.activity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, ui.dp(5), 0, ui.dp(5));
            row.addView(ui.glyphCircle(mealGlyph(item.food.kind), false));

            LinearLayout details = new LinearLayout(host.activity());
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(ui.dp(10), 0, ui.dp(8), 0);
            details.addView(ui.text(item.food.displayName(), 14, FitnessUi.COLOR_TEXT, true));
            details.addView(ui.text(item.food.categoryCookingLabel(), 11, FitnessUi.COLOR_TERTIARY, false));
            details.addView(ui.text(
                    Math.round(item.calories) + " kcal  ·  "
                            + NutritionCalculator.trim(item.quantity)
                            + NutritionUnit.display(item.food.basisUnit),
                    11,
                    FitnessUi.COLOR_MUTED,
                    false
            ));
            row.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            EditText quantity = ui.decimalInput(
                    "섭취량 (" + NutritionUnit.display(item.food.basisUnit) + ")",
                    NutritionCalculator.trim(item.quantity)
            );
            quantity.setSelectAllOnFocus(true);
            quantityInputs.add(quantity);
            LinearLayout.LayoutParams quantityParams = new LinearLayout.LayoutParams(ui.dp(76), ui.dp(48));
            quantityParams.setMargins(0, 0, ui.dp(6), 0);
            row.addView(quantity, quantityParams);
            row.addView(ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE, () -> {
                syncDraftFromViews();
                draftItems.remove(itemIndex);
                host.rerender();
            }));
            quantity.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    updateCompositionTotal();
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
            compositionRows.addView(row);
        }
    }

    private void appendCatalogSection(LinearLayout card) {
        FitnessUi ui = ui();
        ui.cardHeader(card, "영양 카탈로그", catalogSyncing ? "동기화 중" : "로컬 + 원격");

        LinearLayout modeTabs = new LinearLayout(host.activity());
        modeTabs.setOrientation(LinearLayout.HORIZONTAL);
        addCatalogModeTab(modeTabs, "성분 입력", CATALOG_MODE_NUTRIENTS);
        addCatalogModeTab(modeTabs, "재료 등록", CATALOG_MODE_INGREDIENT);
        addCatalogModeTab(modeTabs, "메뉴 등록", CATALOG_MODE_MENU);
        card.addView(modeTabs, ui.fullWidthParams(ui.dp(12)));
        card.addView(ui.text(catalogModeHelper(), 11, FitnessUi.COLOR_TERTIARY, false),
                ui.fullWidthParams(ui.dp(4)));

        if (!syncMessage.trim().isEmpty()) {
            TextView catalogStatus = ui.text(syncMessage, 12, FitnessUi.COLOR_MUTED, false);
            catalogStatus.setPadding(0, ui.dp(5), 0, 0);
            card.addView(catalogStatus);
        }

        if (catalogMode == CATALOG_MODE_NUTRIENTS) {
            card.addView(directFoodForm(false), ui.fullWidthParams(ui.dp(10)));
        } else if (catalogMode == CATALOG_MODE_INGREDIENT) {
            card.addView(directFoodForm(true), ui.fullWidthParams(ui.dp(10)));
        }

        appendCatalogSearch(card);
    }

    private void addCatalogModeTab(LinearLayout tabs, String label, int mode) {
        FitnessUi ui = ui();
        Button tab = ui.filterButton(label);
        ui.styleFilterButton(tab, catalogMode == mode);
        tab.setOnClickListener(v -> {
            if (catalogMode != mode) {
                catalogMode = mode;
                host.rerender();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(ui.dp(2), 0, ui.dp(2), 0);
        tabs.addView(tab, params);
    }

    private String catalogModeHelper() {
        switch (catalogMode) {
            case CATALOG_MODE_INGREDIENT:
                return "직접 만든 재료를 범주·종류·조리 방식으로 저장합니다. 저장만 하거나 현재 끼니에 함께 넣을 수 있습니다.";
            case CATALOG_MODE_MENU:
                return "위에서 재료를 조합한 뒤 ‘메뉴 카탈로그에 저장’을 누릅니다. ‘끼니 기록’과는 별도 동작입니다.";
            default:
                return "외부 메뉴의 브랜드·상품명을 입력하거나 PriceTrace에서 정확한 상품을 불러온 뒤 영양성분을 등록합니다.";
        }
    }

    private void appendCatalogSearch(LinearLayout card) {
        FitnessUi ui = ui();
        ui.cardHeader(card, "카탈로그에서 현재 끼니에 추가", "항목 선택");

        catalogSearchInput = ui.searchField("재료·메뉴 이름 검색");
        catalogSearchInput.setText(catalogQuery);
        catalogSearchInput.setSelection(catalogSearchInput.length());
        catalogSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                catalogQuery = text == null ? "" : text.toString();
                renderCatalogResults();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        card.addView(catalogSearchInput, ui.fullWidthParams(ui.dp(12)));

        catalogResults = new LinearLayout(host.activity());
        catalogResults.setOrientation(LinearLayout.VERTICAL);
        card.addView(catalogResults);
        renderCatalogResults();

        Button sync = ui.button("원격 카탈로그 새로고침", false, v -> syncCatalog(true));
        card.addView(sync, ui.fullWidthParams(ui.dp(14)));
    }

    private void renderCatalogResults() {
        if (catalogResults == null) {
            return;
        }
        FitnessUi ui = ui();
        catalogResults.removeAllViews();
        List<NutritionFood> foods = host.nutritionCatalogRepository().searchFoods(catalogQuery);
        if (foods.isEmpty()) {
            catalogResults.addView(ui.text(
                    "검색 결과가 없습니다. 직접 등록하거나 다른 이름으로 검색하세요.",
                    13,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
            return;
        }

        int limit = Math.min(12, foods.size());
        for (int index = 0; index < limit; index++) {
            NutritionFood food = foods.get(index);
            catalogResults.addView(catalogFoodRow(food));
            if (index < limit - 1) {
                catalogResults.addView(ui.hairline(ui.border()),
                        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));
            }
        }
        if (foods.size() > limit) {
            catalogResults.addView(ui.text("검색어를 더 입력하면 결과를 좁힐 수 있습니다.",
                    11, FitnessUi.COLOR_TERTIARY, false), ui.fullWidthParams(ui.dp(8)));
        }
    }

    private View catalogFoodRow(NutritionFood food) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(66));
        row.setPadding(0, ui.dp(7), 0, ui.dp(7));
        row.setBackground(ui.flatSurfaceRippleDrawable(ui.dp(12)));
        row.setClickable(true);
        row.setFocusable(true);
        ui.pressFeedback(row);
        row.setOnClickListener(v -> addFoodToDraft(food));

        row.addView(ui.glyphCircle(mealGlyph(food.kind), false));
        LinearLayout details = new LinearLayout(host.activity());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(ui.dp(10), 0, ui.dp(8), 0);
        details.addView(ui.text(food.displayName(), 14, FitnessUi.COLOR_TEXT, true));
        details.addView(ui.text(food.categoryCookingLabel(), 11, FitnessUi.COLOR_TERTIARY, false));
        details.addView(ui.text(food.extendedNutritionLabel() + " / " + food.basisLabel(),
                11,
                FitnessUi.COLOR_MUTED,
                false));
        details.addView(ui.text(food.unitNutritionLabel(),
                11,
                FitnessUi.COLOR_TERTIARY,
                false));
        String missingNotice = food.missingRequiredNotice();
        if (missingNotice != null) {
            details.addView(ui.text(missingNotice, 11, FitnessUi.COLOR_TERTIARY, false));
        }
        ProductNutritionLink approved = host.nutritionCatalogRepository()
                .approvedProductLink(food.id);
        List<ProductNutritionLink> suggestions = host.nutritionCatalogRepository()
                .pendingProductLinkSuggestions(food.id);
        if (approved != null) {
            details.addView(ui.text(
                    "PriceTrace · " + approved.displayLabel(),
                    11,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
        } else if (!suggestions.isEmpty()) {
            details.addView(ui.text(
                    "PriceTrace 제안 " + suggestions.size() + "건 · 승인 필요",
                    11,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout actions = new LinearLayout(host.activity());
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.END);
        actions.addView(ui.text("추가 ›", 12, FitnessUi.COLOR_TERTIARY, true));
        actions.addView(ui.textAction(
                !suggestions.isEmpty() ? "제안 확인"
                        : (approved == null ? "상품 연결" : "연결 관리"),
                approved == null ? FitnessUi.COLOR_TERTIARY : FitnessUi.COLOR_MUTED,
                () -> productLinkController.show(food)
        ));
        row.addView(actions);
        return row;
    }

    private View directFoodForm(boolean ingredientMode) {
        FitnessUi ui = ui();
        LinearLayout form = ui.form();
        EditText name = ui.input(
                ingredientMode ? "종류 이름 (예: 오겹살)" : "외부 메뉴 이름",
                ""
        );
        EditText brand = ingredientMode ? null : ui.input("브랜드 (예: 버거킹)", "");
        String[] selectedCategory = {
                ingredientMode
                        ? NutritionFood.CATEGORY_OTHER
                        : NutritionFood.CATEGORY_PROCESSED
        };
        Button categoryButton = ui.button(
                categoryButtonLabel(selectedCategory[0]),
                false,
                null
        );
        categoryButton.setOnClickListener(v -> showFoodChoiceDialog(
                "식품 범주 선택",
                NutritionFood.categoryOptions(),
                selectedCategory,
                categoryButton,
                true
        ));
        String[] selectedCookingMethod = {NutritionFood.COOKING_METHOD_UNSPECIFIED};
        Button cookingMethodButton = ui.button(
                cookingMethodButtonLabel(selectedCookingMethod[0]),
                false,
                null
        );
        cookingMethodButton.setOnClickListener(v -> showFoodChoiceDialog(
                "조리 방식 선택",
                NutritionFood.cookingMethodOptions(),
                selectedCookingMethod,
                cookingMethodButton,
                false
        ));
        EditText basisAmount = ui.decimalInput(
                "기준 수량",
                ingredientMode ? "100" : "1"
        );
        Button basisUnit = NutritionUnitSelector.create(
                ui,
                host.activity(),
                ingredientMode ? "g" : "serving"
        );
        NutritionInputSection nutrients = new NutritionInputSection(ui, host.activity());
        TextView unitNutritionPreview = ui.text(
                "단위 영양성분: 기준량과 필수 영양성분을 입력하면 자동 계산됩니다.",
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        NutritionUnitPreview.bind(unitNutritionPreview, basisAmount, basisUnit, nutrients);
        ProductReadV1[] selectedProduct = {null};
        LinearLayout priceTraceResults = new LinearLayout(host.activity());
        priceTraceResults.setOrientation(LinearLayout.VERTICAL);
        EditText priceTraceQuery = ui.searchField("PriceTrace 상품명 검색");
        Button priceTraceSearch = ui.button("PriceTrace 상품 불러오기", false, null);
        TextView priceTraceSelection = ui.text(
                "선택한 PriceTrace 상품 없음 · 상품 연결 없이도 영양 메뉴를 저장할 수 있습니다.",
                11,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        priceTraceSearch.setOnClickListener(v -> {
            String query = FitnessUi.inputText(priceTraceQuery).trim();
            if (query.isEmpty()) {
                host.toast("PriceTrace에서 검색할 상품명을 입력하세요.");
                return;
            }
            priceTraceSearch.setEnabled(false);
            priceTraceSelection.setText("PriceTrace 상품을 조회하는 중입니다.");
            host.searchPriceTraceProducts(query, new ScreenHost.ProductSearchCallback() {
                @Override
                public void onComplete(List<ProductReadV1> products) {
                    host.activity().runOnUiThread(() -> {
                        priceTraceSearch.setEnabled(true);
                        renderPriceTraceChoices(
                                priceTraceResults,
                                priceTraceSelection,
                                products,
                                name,
                                brand,
                                basisAmount,
                                basisUnit,
                                selectedProduct
                        );
                    });
                }

                @Override
                public void onError(Exception error) {
                    host.activity().runOnUiThread(() -> {
                        priceTraceSearch.setEnabled(true);
                        priceTraceSelection.setText("PriceTrace 조회 실패: " +
                                (error.getMessage() == null ? "연결을 확인하세요." : error.getMessage()));
                    });
                }
            });
        });
        ui.addAll(form, name);
        if (brand != null) {
            ui.addAll(form, brand);
        }
        ui.addAll(
                form,
                categoryButton,
                cookingMethodButton,
                basisAmount,
                basisUnit,
                ui.text("아래 값은 모두 위 기준 수량에 대한 값입니다.", 11, FitnessUi.COLOR_MUTED, false),
                nutrients.view(),
                unitNutritionPreview
        );
        if (!ingredientMode) {
            ui.addAll(form, priceTraceQuery, priceTraceSearch, priceTraceSelection, priceTraceResults);
        }
        Button saveOnly = ui.button(
                ingredientMode ? "재료만 카탈로그에 저장" : "카탈로그에 저장",
                false,
                v -> saveDirectFood(
                        name, brand, selectedCategory[0], selectedCookingMethod[0],
                        basisAmount, basisUnit, nutrients,
                        ingredientMode, selectedProduct[0], false
                )
        );
        Button saveAndAdd = ui.button(
                "저장 후 현재 끼니에 추가",
                true,
                v -> saveDirectFood(
                        name, brand, selectedCategory[0], selectedCookingMethod[0],
                        basisAmount, basisUnit, nutrients,
                        ingredientMode, selectedProduct[0], true
                )
        );
        form.addView(ui.buttonRow(saveOnly, saveAndAdd), ui.fullWidthParams(ui.dp(10)));
        return form;
    }

    private void renderPriceTraceChoices(
            LinearLayout results,
            TextView selection,
            List<ProductReadV1> products,
            EditText name,
            EditText brand,
            EditText basisAmount,
            Button basisUnit,
            ProductReadV1[] selectedProduct
    ) {
        FitnessUi ui = ui();
        results.removeAllViews();
        if (products == null || products.isEmpty()) {
            selection.setText("일치하는 PriceTrace 상품이 없습니다. 다른 상품명을 검색하세요.");
            return;
        }
        selection.setText(products.size() + "개 표준상품 후보 · 브랜드와 상품 이름만 표시합니다.");
        for (ProductReadV1 product : products) {
            Button choice = ui.button(product.standardProductLabel(), false, v -> {
                selectedProduct[0] = product;
                name.setText(product.name);
                lockPriceTraceLoadedField(name);
                if (brand != null) {
                    brand.setText(product.brand == null ? "" : product.brand);
                    lockPriceTraceLoadedField(brand);
                }
                if (product.contentAmount != null && product.contentAmount > 0) {
                    basisAmount.setText(NutritionCalculator.trim(product.contentAmount));
                }
                if (product.contentUnit != null && NutritionUnit.isSupported(product.contentUnit)) {
                    NutritionUnitSelector.setValue(basisUnit, product.contentUnit);
                } else if (product.contentUnit != null && !product.contentUnit.trim().isEmpty()) {
                    NutritionUnitSelector.setValue(basisUnit, NutritionUnit.SERVING);
                }
                selection.setText("선택됨 · " + product.standardProductLabel());
                results.removeAllViews();
            });
            choice.setAllCaps(false);
            choice.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            results.addView(choice, ui.fullWidthParams(ui.dp(7)));
        }
    }

    /** Reuses the animated border shown on the selected date in the records calendar. */
    private void lockPriceTraceLoadedField(EditText field) {
        FitnessUi ui = ui();
        field.setEnabled(false);
        field.setCursorVisible(false);
        field.setLongClickable(false);
        field.setTextIsSelectable(false);
        field.setContentDescription(field.getText() + " · PriceTrace 선택값, 수정 불가");
        ui.setHologramBackground(field, ui.flatSurfaceDrawable(ui.dp(12)), ui.dp(12));
        ui.applyDepth(field, 5);
    }

    private void saveDirectFood(
            EditText name,
            EditText brand,
            String category,
            String cookingMethod,
            EditText basisAmount,
            Button basisUnit,
            NutritionInputSection nutrients,
            boolean ingredientMode,
            ProductReadV1 selectedProduct,
            boolean addToMeal
    ) {
        try {
            syncDraftFromViews();
            double basis = positiveNumber(basisAmount, "기준 수량");
            String productBrand = brand == null ? null : FitnessUi.inputText(brand);
            if (selectedProduct != null && (productBrand == null || productBrand.trim().isEmpty())) {
                productBrand = selectedProduct.brand;
            }
            String sourceReference = "";
            String sourceType = "manual";
            if (selectedProduct != null) {
                sourceType = "pricetrace_manual";
                sourceReference = "catalogProductId:" + selectedProduct.catalogProductId;
            }
            NutritionFood saved = host.nutritionCatalogRepository().saveFood(
                    FitnessUi.inputText(name),
                    productBrand,
                    ingredientMode ? NutritionFood.KIND_INGREDIENT : NutritionFood.KIND_EXTERNAL_MENU,
                    category,
                    basis,
                    NutritionUnitSelector.value(basisUnit),
                    cookingMethod,
                    nutrients.profile(),
                    sourceType,
                    sourceReference,
                    ""
            );
            if (selectedProduct != null) {
                host.nutritionCatalogRepository().linkProduct(saved.id, selectedProduct);
            }
            if (addToMeal) {
                draftItems.add(MealCompositionItem.from(saved, saved.basisAmount));
            }
            syncCatalog(true);
            host.toast(
                    (ingredientMode ? "재료" : "외부 메뉴") + "를 저장했습니다."
                            + (selectedProduct == null ? "" : " PriceTrace 상품도 연결했습니다.")
                            + (addToMeal ? " 현재 끼니에 추가되었습니다." : "")
            );
            host.rerender();
        } catch (Exception error) {
            host.toast(error.getMessage() == null ? "영양 정보를 확인하세요." : error.getMessage());
        }
    }

    private void showFoodChoiceDialog(
            String title,
            String[] options,
            String[] selected,
            Button target,
            boolean category
    ) {
        String[] labels = new String[options.length];
        for (int index = 0; index < options.length; index++) {
            labels[index] = category
                    ? NutritionFood.categoryLabel(options[index])
                    : NutritionFood.cookingMethodLabel(options[index]);
        }
        new AlertDialog.Builder(host.activity())
                .setTitle(title)
                .setItems(labels, (dialog, which) -> {
                    selected[0] = options[which];
                    target.setText(category
                            ? categoryButtonLabel(selected[0])
                            : cookingMethodButtonLabel(selected[0]));
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private static String categoryButtonLabel(String category) {
        return "식품 범주: " + NutritionFood.categoryLabel(category);
    }

    private static String cookingMethodButtonLabel(String cookingMethod) {
        return "조리 방식: " + NutritionFood.cookingMethodLabel(cookingMethod);
    }

    private void addFoodToDraft(NutritionFood food) {
        syncDraftFromViews();
        draftItems.add(MealCompositionItem.from(food, food.basisAmount));
        host.rerender();
    }

    private void saveMeal() {
        syncDraftFromViews();
        if (draftItems.isEmpty()) {
            host.toast("식사에 추가할 음식이나 메뉴를 먼저 선택하세요.");
            return;
        }
        String mealLabel = repository().nextMealLabelForDate(selectedDate);
        String name = draftName.trim();
        if (name.isEmpty()) {
            name = draftItems.size() == 1 ? draftItems.get(0).food.name : mealLabel + " 식사";
        }
        NutritionTotals total = NutritionCalculator.sum(draftItems);
        repository().addMeal(
                selectedDate,
                mealLabel,
                name,
                (int) Math.round(total.calories()),
                total.proteinGrams(),
                total.carbsGrams(),
                total.fatGrams(),
                draftItems
        );
        draftItems.clear();
        draftName = "";
        host.toast(mealLabel + "를 " + dateLabel() + "에 기록했습니다.");
        host.rerender();
    }

    private void saveRecipe() {
        syncDraftFromViews();
        if (draftItems.isEmpty()) {
            host.toast("메뉴로 저장할 음식이나 재료를 먼저 선택하세요.");
            return;
        }
        String name = draftName.trim();
        if (name.isEmpty()) {
            host.toast("저장할 메뉴 이름을 입력하세요.");
            return;
        }
        try {
            host.nutritionCatalogRepository().saveRecipe(name, draftItems);
            syncCatalog(true);
            host.toast("구성 메뉴를 저장했습니다. 다음 검색에서 사용할 수 있습니다.");
        } catch (Exception error) {
            host.toast(error.getMessage() == null ? "구성 메뉴 저장에 실패했습니다." : error.getMessage());
        }
    }

    private void syncCatalog(boolean userInitiated) {
        if (catalogSyncing) {
            return;
        }
        catalogSyncing = true;
        syncMessage = "원격 카탈로그를 확인하는 중입니다.";
        host.syncNutritionCatalog(new NutritionCatalogRepository.SyncCallback() {
            @Override
            public void onComplete(int pushedRows, int pulledRows) {
                host.activity().runOnUiThread(() -> {
                    catalogSyncing = false;
                    syncMessage = pulledRows > 0
                            ? "원격 메뉴 " + pulledRows + "개를 최신 상태로 반영했습니다."
                            : (pushedRows > 0 ? "새 메뉴를 원격 카탈로그에 저장했습니다." :
                            "");
                    host.rerender();
                });
            }

            @Override
            public void onError(Exception error) {
                host.activity().runOnUiThread(() -> {
                    catalogSyncing = false;
                    syncMessage = "원격 연결에 실패했습니다. 기기 저장은 유지됩니다.";
                    if (userInitiated) {
                        host.toast("원격 카탈로그 동기화에 실패했습니다.");
                    }
                    host.rerender();
                });
            }
        });
    }

    private void syncDraftFromViews() {
        if (mealNameInput != null) {
            draftName = FitnessUi.inputText(mealNameInput);
        }
        if (catalogSearchInput != null) {
            catalogQuery = FitnessUi.inputText(catalogSearchInput);
        }
        if (quantityInputs.size() != draftItems.size()) {
            return;
        }
        for (int index = 0; index < quantityInputs.size(); index++) {
            MealCompositionItem current = draftItems.get(index);
            double quantity = FitnessUi.parseDouble(quantityInputs.get(index), current.quantity);
            if (quantity > 0) {
                draftItems.set(index, MealCompositionItem.from(current.food, quantity));
            }
        }
    }

    private void updateCompositionTotal() {
        if (compositionTotalBox == null) {
            return;
        }
        syncDraftFromViews();
        NutritionTotals total = NutritionCalculator.sum(draftItems);
        FitnessUi ui = ui();
        compositionTotalBox.removeAllViews();
        compositionTotalBox.addView(ui.text(
                "끼니 구성 총합계",
                14,
                FitnessUi.COLOR_TEXT,
                true
        ));
        addNutritionSummaryRow(
                compositionTotalBox,
                nutritionSummaryCell("칼로리", NutritionCalculator.trim(total.calories()) + " kcal"),
                nutritionSummaryCell("탄수화물", NutritionCalculator.trim(total.carbsGrams()) + " g")
        );
        addNutritionSummaryRow(
                compositionTotalBox,
                nutritionSummaryCell("단백질", NutritionCalculator.trim(total.proteinGrams()) + " g"),
                nutritionSummaryCell("지방", NutritionCalculator.trim(total.fatGrams()) + " g")
        );
        addNutritionSummaryRow(
                compositionTotalBox,
                nutritionSummaryCell(
                        "나트륨",
                        NutritionCalculator.describeTotal(total.total(NutritionProfile.SODIUM_MG)) + " mg"
                ),
                nutritionSummaryCell(
                        "포화지방",
                        NutritionCalculator.describeTotal(
                                total.total(NutritionProfile.SATURATED_FAT_GRAMS)
                        ) + " g"
                )
        );
        addNutritionSummaryRow(
                compositionTotalBox,
                nutritionSummaryCell(
                        "당류",
                        NutritionCalculator.describeTotal(total.total(NutritionProfile.SUGARS_GRAMS)) + " g"
                ),
                null
        );
    }

    private void addNutritionSummaryRow(LinearLayout parent, View first, View second) {
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(first, nutritionSummaryCellParams(true));
        row.addView(
                second == null ? new View(host.activity()) : second,
                nutritionSummaryCellParams(false)
        );
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private View nutritionSummaryCell(String label, String value) {
        FitnessUi ui = ui();
        LinearLayout cell = new LinearLayout(host.activity());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setMinimumHeight(ui.dp(64));
        cell.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(7));
        cell.setBackground(ui.flatSurfaceDrawable(ui.dp(10)));
        cell.addView(ui.text(label, 11, FitnessUi.COLOR_MUTED, true));
        TextView valueView = ui.text(value, 16, FitnessUi.COLOR_TEXT, true);
        valueView.setPadding(0, ui.dp(5), 0, 0);
        cell.addView(valueView);
        return cell;
    }

    private LinearLayout.LayoutParams nutritionSummaryCellParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(first ? 0 : ui().dp(4), ui().dp(4), first ? ui().dp(4) : 0, 0);
        return params;
    }

    private void confirmDeleteMeal(FitnessRepository.MealEntry entry) {
        ui().confirmSheet(
                "끼니 기록 삭제",
                entry.mealLabel + " · " + entry.menu + " 기록을 삭제할까요?",
                "삭제하면 선택한 날짜의 식단 합계에서도 빠집니다.",
                "기록 삭제",
                () -> {
                    repository().deleteMeal(entry.id);
                    host.rerender();
                }
        );
    }

    private String dateLabel() {
        return LocalDate.parse(selectedDate).format(DATE_FORMAT);
    }

    private boolean isToday() {
        return selectedDate.equals(host.today());
    }

    private String mealGlyph(String kind) {
        if (NutritionFood.KIND_RECIPE.equals(kind)) {
            return "메";
        }
        if (NutritionFood.KIND_INGREDIENT.equals(kind)) {
            return "재";
        }
        return "외";
    }

    private double positiveNumber(EditText input, String label) {
        String value = FitnessUi.inputText(input).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(label + "을 입력하세요.");
        }
        double parsed = Double.parseDouble(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + "은 0보다 커야 합니다.");
        }
        return parsed;
    }
}
