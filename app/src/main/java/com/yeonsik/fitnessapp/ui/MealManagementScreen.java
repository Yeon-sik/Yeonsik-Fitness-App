package com.yeonsik.fitnessapp.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
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
import com.yeonsik.fitnessapp.data.DiningOutIdentity;
import com.yeonsik.fitnessapp.data.DiningOutOption;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.MealEntryPolicy;
import com.yeonsik.fitnessapp.data.MealMenuSelection;
import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionCalculator;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionTotals;
import com.yeonsik.fitnessapp.data.NutritionUnit;
import com.yeonsik.fitnessapp.data.ProductNutritionLink;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.data.RestaurantMenuReadV1Client;
import com.yeonsik.fitnessapp.data.VerifiedFoodCatalogSeed;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 피트니스 하위의 식단 작업 공간.
 * 날짜별 섭취 요약, 식사 기록, 음식 카탈로그 검색, 구성 메뉴 저장을 한 흐름으로 제공한다.
 */
public final class MealManagementScreen extends BaseScreen {
    private static final int MEAL_ENTRY_MODE_FOOD = 0;
    private static final int MEAL_ENTRY_MODE_DINING_OUT = 1;
    private static final int CATALOG_MODE_SEARCH = 0;
    private static final int CATALOG_MODE_SINGLE_FOOD = 1;
    private static final int CATALOG_MODE_FINISHED_PRODUCT = 2;
    private static final String CATALOG_FILTER_ALL = "all";
    private static final int VERIFIED_SINGLE_FOOD_RESULT_LIMIT = 8;
    private static final String VERIFIED_SINGLE_FOOD_SEARCH_TAG =
            "verified-single-food-search-input";
    private static final String VERIFIED_SINGLE_FOOD_RESULTS_TAG =
            "verified-single-food-results";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private String selectedDate;
    private String draftMealTime = currentMealTime();
    private String catalogQuery = "";
    private String verifiedSingleFoodQuery = "";
    private final List<MealMenuSelection> draftMenus = new ArrayList<>();
    private final List<MealCompositionItem> draftIngredients = new ArrayList<>();
    private String draftMenuName = "";
    private String draftDiningOutStoreName = "";
    private String draftDiningOutBranchName = "";
    private String draftDiningOutMenuName = "";
    private String draftDiningOutRestaurantId = "";
    private String draftDiningOutRestaurantLocationId = "";
    private String draftDiningOutRestaurantMenuId = "";
    private String draftDiningOutCatalogProductId = "";
    private String linkedDiningOutStoreName = "";
    private String linkedDiningOutBranchName = "";
    private String linkedDiningOutMenuName = "";
    private final List<DiningOutOptionDraft> draftDiningOutOptions = new ArrayList<>();
    private String draftDiningOutCarbs = "";
    private String draftDiningOutProtein = "";
    private String draftDiningOutFat = "";
    private String draftDiningOutCalories = "";
    private String draftDiningOutSodium = "";
    private String draftDiningOutSugars = "";
    private String draftDiningOutSaturatedFat = "";

    private Button mealTimeButton;
    private EditText menuNameInput;
    private EditText diningOutStoreInput;
    private EditText diningOutBranchInput;
    private EditText diningOutMenuInput;
    private LinearLayout diningOutOptionsContainer;
    private final List<EditText> diningOutOptionInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionCaloriesInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionProteinInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionCarbsInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionFatInputs = new ArrayList<>();
    private EditText diningOutCarbsInput;
    private EditText diningOutProteinInput;
    private EditText diningOutFatInput;
    private EditText diningOutCaloriesInput;
    private EditText diningOutSodiumInput;
    private EditText diningOutSugarsInput;
    private EditText diningOutSaturatedFatInput;
    private EditText catalogSearchInput;
    private EditText verifiedSingleFoodSearchInput;
    private LinearLayout compositionRows;
    private LinearLayout catalogResults;
    private LinearLayout verifiedSingleFoodResults;
    private LinearLayout compositionTotalBox;
    private LinearLayout menuBuilderTotalBox;
    private final List<EditText> menuQuantityInputs = new ArrayList<>();
    private final List<EditText> ingredientQuantityInputs = new ArrayList<>();
    private int catalogMode = CATALOG_MODE_SEARCH;
    private String catalogKindFilter = CATALOG_FILTER_ALL;
    private boolean initialSyncRequested;
    private boolean catalogSyncing;
    private String syncMessage = "기기와 원격 카탈로그를 함께 검색합니다.";
    private final ProductNutritionLinkDialogController productLinkController;
    private boolean savedMenusVisible;
    private boolean mealWorkspaceVisible;
    private int mealEntryMode = MEAL_ENTRY_MODE_FOOD;
    private boolean menuBuilderVisible;
    private boolean recoveryDetailsVisible;
    private boolean nutritionAnalysisVisible;
    private FitnessScreen returnScreen = FitnessScreen.WORKOUT;

    public MealManagementScreen(ScreenHost host) {
        super(host);
        selectedDate = host.today();
        productLinkController = new ProductNutritionLinkDialogController(host);
    }

    /** 오늘 화면을 유지 중인 경우에만 자정 rollover를 새 날짜에 반영한다. */
    public void onDateChanged(String previousDate, String currentDate) {
        if (selectedDate.equals(previousDate)) {
            selectedDate = currentDate;
        }
    }

    public void selectDate(String date) {
        selectedDate = LocalDate.parse(date).toString();
    }

    public void setReturnScreen(FitnessScreen returnScreen) {
        this.returnScreen = returnScreen == null ? FitnessScreen.WORKOUT : returnScreen;
    }

    @Override
    public void render() {
        syncDraftFromViews();

        add(ui().textAction("‹ 이전 화면", FitnessUi.COLOR_MUTED,
                () -> host.navigate(returnScreen)), ui().fullWidthParams(0));
        screenHeader("섭취와 회복", "식단 관리");

        add(dateNavigator());
        add(dailySummary());

        section("끼니 기록", mealWorkspaceVisible ? "입력 닫기" : "입력 열기", () -> {
            toggleMealWorkspace();
        });
        if (mealWorkspaceVisible) {
            add(mealWorkspace());
        } else {
            add(ui().button("새 끼니 기록", true, v -> {
                openMealWorkspace();
            }), ui().fullWidthParams(0));
        }
        renderMealEntries();

        section("회복 상태", recoveryDetailsVisible ? "간단히" : "상세 보기", () -> {
            recoveryDetailsVisible = !recoveryDetailsVisible;
            host.rerender();
        });
        add(athleteCheckInCard());

        section("영양 분석", nutritionAnalysisVisible ? "접기" : "보기", () -> {
            nutritionAnalysisVisible = !nutritionAnalysisVisible;
            host.rerender();
        });
        if (nutritionAnalysisVisible) {
            add(proteinDistributionCard());
            add(detailedNutrientsCard());
        }

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
        previous.setContentDescription("이전 날짜");
        row.addView(previous, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));

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
        next.setContentDescription("다음 날짜");
        row.addView(next, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        card.addView(row);
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

    private void toggleMealWorkspace() {
        if (mealWorkspaceVisible) {
            mealWorkspaceVisible = false;
        } else {
            openMealWorkspace();
            return;
        }
        host.rerender();
    }

    private void openMealWorkspace() {
        if (draftMenus.isEmpty()) {
            draftMealTime = currentMealTime();
        }
        catalogMode = CATALOG_MODE_SEARCH;
        catalogKindFilter = CATALOG_FILTER_ALL;
        mealWorkspaceVisible = true;
        host.rerender();
    }

    private void showDraftMealTimePicker() {
        showMealTimePicker("먹은 시간", draftMealTime, selected -> {
            draftMealTime = selected;
            if (mealTimeButton != null) {
                mealTimeButton.setText(selected);
                mealTimeButton.setContentDescription("먹은 시간 " + selected + ". 탭하여 변경합니다.");
            }
        });
    }

    private void showRecordedMealTimePicker(FitnessRepository.MealEntry entry) {
        showMealTimePicker("끼니 시간 수정", entry.mealTime, selected -> {
            if (repository().updateMealTime(entry.id, selected)) {
                host.toast("끼니 시간을 " + selected + "으로 수정했습니다.");
                host.rerender();
            } else {
                host.toast("끼니 시간을 수정하지 못했습니다.");
            }
        });
    }

    private void showMealTimePicker(String title, String value, Consumer<String> onSelected) {
        LocalTime initial = parseMealTimeOrNow(value);
        TimePickerDialog picker = new TimePickerDialog(
                host.activity(),
                (view, hourOfDay, minute) -> onSelected.accept(
                        LocalTime.of(hourOfDay, minute).format(TIME_FORMAT)
                ),
                initial.getHour(),
                initial.getMinute(),
                true
        );
        picker.setTitle(title);
        picker.show();
    }

    private static LocalTime parseMealTimeOrNow(String value) {
        try {
            return LocalTime.parse(MealEntryPolicy.requireMealTime(value), TIME_FORMAT);
        } catch (Exception ignored) {
            return LocalTime.now();
        }
    }

    private static String currentMealTime() {
        return LocalTime.now().format(TIME_FORMAT);
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

        if (!recoveryDetailsVisible) {
            LinearLayout summaryRow = ui.tileRow();
            summaryRow.addView(ui.inlineStat("수면", formatSleep(checkIn.sleepHours), false),
                    ui.tileParams(true));
            summaryRow.addView(ui.inlineStat(
                    "훈련 준비도",
                    formatScore(checkIn.trainingReadinessScore),
                    false
            ), ui.tileParams(false));
            card.addView(summaryRow, ui.fullWidthParams(ui.dp(12)));
        } else {
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
        }

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
                    meal.mealTime + " · " + meal.previewTitle,
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
            emptyState("아직 기록된 끼니가 없습니다.", "식단 또는 외식 탭에서 오늘 먹은 것을 추가하세요.");
        } else {
            List<View> rows = new ArrayList<>();
            for (FitnessRepository.MealEntry entry : entries) {
                rows.add(mealRow(entry));
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

    private View mealRow(FitnessRepository.MealEntry entry) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(72));
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        row.addView(ui.glyphCircle(entry.isDiningOut() ? "외" : "식", false));

        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(ui.dp(12), 0, ui.dp(8), 0);
        TextView title = ui.text(entry.previewTitle, 15, FitnessUi.COLOR_TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(title);
        TextView preview = ui.text(entry.previewSubtitle(), 12, FitnessUi.COLOR_MUTED, false);
        preview.setPadding(0, ui.dp(3), 0, 0);
        column.addView(preview);
        if (entry.timeEditable) {
            column.setClickable(true);
            column.setFocusable(true);
            column.setContentDescription(
                    entry.previewAccessibilityLabel() + ". 탭하여 시간을 수정합니다."
            );
            column.setOnClickListener(v -> showRecordedMealTimePicker(entry));
            ui.pressFeedback(column);
        } else {
            column.setContentDescription(entry.previewAccessibilityLabel());
        }
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout actions = new LinearLayout(host.activity());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(ui.textAction("상세", FitnessUi.COLOR_TERTIARY,
                () -> showRecordedMealDetails(entry)));
        actions.addView(ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE,
                () -> confirmDeleteMeal(entry)));
        row.addView(actions);
        return row;
    }

    private void showRecordedMealDetails(FitnessRepository.MealEntry entry) {
        FitnessUi ui = ui();
        LinearLayout body = ui.form();
        body.addView(ui.text(
                entry.previewSubtitle(),
                13,
                FitnessUi.COLOR_MUTED,
                false
        ));

        if (entry.isDiningOut()) {
            body.addView(ui.caption("가게 명", FitnessUi.COLOR_TERTIARY),
                    ui.fullWidthParams(ui.dp(10)));
            body.addView(ui.text(entry.storeName, 16, FitnessUi.COLOR_TEXT, true),
                    ui.fullWidthParams(ui.dp(2)));
            if (!entry.branchName.isEmpty()) {
                body.addView(ui.caption("선택 지점", FitnessUi.COLOR_TERTIARY),
                        ui.fullWidthParams(ui.dp(12)));
                body.addView(ui.text(entry.branchName, 16, FitnessUi.COLOR_TEXT, true),
                        ui.fullWidthParams(ui.dp(2)));
            }
            body.addView(ui.caption("먹은 메뉴", FitnessUi.COLOR_TERTIARY),
                    ui.fullWidthParams(ui.dp(12)));
            body.addView(ui.text(entry.menuName, 16, FitnessUi.COLOR_TEXT, true),
                    ui.fullWidthParams(ui.dp(2)));
            List<FitnessRepository.MealItemEntry> menuItems = repository().mealItemsForRecord(entry.id);
            if (!menuItems.isEmpty()) {
                List<FitnessRepository.MealComponentEntry> options =
                        repository().mealComponentsForItem(menuItems.get(0).id);
                if (!options.isEmpty()) {
                    body.addView(ui.caption("메뉴 옵션", FitnessUi.COLOR_TERTIARY),
                            ui.fullWidthParams(ui.dp(12)));
                    for (FitnessRepository.MealComponentEntry option : options) {
                        body.addView(ui.text(
                                "· " + option.foodName,
                                13,
                                FitnessUi.COLOR_TEXT,
                                false
                        ), ui.fullWidthParams(ui.dp(4)));
                    }
                }
            }
            if (entry.hasEstimatedNutrition()) {
                List<FitnessRepository.MealItemEntry> nutritionItems =
                        repository().mealItemsForRecord(entry.id);
                NutritionProfile nutrition = nutritionItems.isEmpty()
                        ? null
                        : nutritionItems.get(0).profile;
                body.addView(ui.text(
                        "칼로리 " + entry.calories + "kcal · "
                                + "탄수화물 " + NutritionCalculator.trim(entry.carbsGrams) + "g · "
                                + "단백질 " + NutritionCalculator.trim(entry.proteinGrams) + "g · "
                                + "지방 " + NutritionCalculator.trim(entry.fatGrams) + "g",
                        14,
                        FitnessUi.COLOR_TEXT,
                        true
                ), ui.fullWidthParams(ui.dp(12)));
                body.addView(ui.text(
                        "나트륨 " + NutritionCalculator.trimNullable(
                                nutrition == null ? null : nutrition.sodiumMg()
                        ) + "mg · 당류 " + NutritionCalculator.trimNullable(
                                nutrition == null ? null : nutrition.sugarsGrams()
                        ) + "g · 포화지방 " + NutritionCalculator.trimNullable(
                                nutrition == null ? null : nutrition.saturatedFatGrams()
                        ) + "g",
                        13,
                        FitnessUi.COLOR_TEXT,
                        false
                ), ui.fullWidthParams(ui.dp(4)));
                body.addView(ui.text(
                        "칼로리·탄·단·지·나트륨·당류·포화지방은 직접 입력한 추정치입니다.",
                        13,
                        FitnessUi.COLOR_MUTED,
                        false
                ), ui.fullWidthParams(ui.dp(14)));
            } else {
                body.addView(ui.text(
                        "영양 정보는 아직 입력되지 않은 외식 기록입니다.",
                        13,
                        FitnessUi.COLOR_MUTED,
                        false
                ), ui.fullWidthParams(ui.dp(14)));
            }
        } else {
            List<FitnessRepository.MealItemEntry> menus = repository().mealItemsForRecord(entry.id);
            if (menus.isEmpty()) {
                body.addView(ui.text(
                        "이전 형식의 기록이라 메뉴 상세가 없습니다.",
                        13,
                        FitnessUi.COLOR_TERTIARY,
                        false
                ), ui.fullWidthParams(ui.dp(12)));
            } else {
                for (FitnessRepository.MealItemEntry menu : menus) {
                    LinearLayout menuCard = new LinearLayout(host.activity());
                    menuCard.setOrientation(LinearLayout.VERTICAL);
                    menuCard.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
                    menuCard.setBackground(ui.flatSurfaceDrawable(ui.dp(12)));
                    menuCard.addView(ui.text(menu.foodName, 14, FitnessUi.COLOR_TEXT, true));
                    menuCard.addView(ui.text(
                            NutritionCalculator.trim(menu.quantity)
                                    + NutritionUnit.display(menu.unit)
                                    + " · " + Math.round(menu.profile.calories()) + "kcal",
                            12,
                            FitnessUi.COLOR_MUTED,
                            false
                    ));
                    List<FitnessRepository.MealComponentEntry> components =
                            repository().mealComponentsForItem(menu.id);
                    if (!components.isEmpty()) {
                        menuCard.addView(ui.caption(
                                "재료 " + components.size() + "개",
                                FitnessUi.COLOR_TERTIARY
                        ), ui.fullWidthParams(ui.dp(8)));
                        for (FitnessRepository.MealComponentEntry component : components) {
                            menuCard.addView(ui.text(
                                    "↳ " + component.label(),
                                    12,
                                    FitnessUi.COLOR_MUTED,
                                    false
                            ), ui.fullWidthParams(ui.dp(4)));
                        }
                    }
                    body.addView(menuCard, ui.fullWidthParams(ui.dp(10)));
                }
            }
        }

        ScrollView scroll = new ScrollView(host.activity());
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        AlertDialog.Builder builder = new AlertDialog.Builder(host.activity())
                .setTitle(entry.previewTitle)
                .setView(scroll)
                .setNegativeButton("닫기", null);
        if (entry.timeEditable) {
            builder.setNeutralButton("시간 수정", (dialog, which) ->
                    showRecordedMealTimePicker(entry));
        }
        builder.show();
    }

    private View mealWorkspace() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "끼니 입력", "날짜와 시간 · "
                + (selectedDate.equals(host.today()) ? "오늘" : dateLabel()));

        mealTimeButton = ui.button(draftMealTime, false, v -> showDraftMealTimePicker());
        mealTimeButton.setContentDescription("먹은 시간 " + draftMealTime + ". 탭하여 변경합니다.");
        card.addView(ui.labeledFieldColumn("먹은 시간", mealTimeButton),
                ui.fullWidthParams(ui.dp(12)));

        LinearLayout entryTabs = new LinearLayout(host.activity());
        entryTabs.setOrientation(LinearLayout.HORIZONTAL);
        addMealEntryModeTab(entryTabs, "식단", MEAL_ENTRY_MODE_FOOD);
        addMealEntryModeTab(entryTabs, "외식", MEAL_ENTRY_MODE_DINING_OUT);
        card.addView(entryTabs, ui.fullWidthParams(ui.dp(10)));

        if (mealEntryMode == MEAL_ENTRY_MODE_DINING_OUT) {
            card.addView(diningOutForm(), ui.fullWidthParams(ui.dp(12)));
        } else {
            LinearLayout compositionHeader = new LinearLayout(host.activity());
            compositionHeader.setOrientation(LinearLayout.HORIZONTAL);
            compositionHeader.setGravity(Gravity.CENTER_VERTICAL);
            compositionHeader.addView(ui.caption("먹은 메뉴", FitnessUi.COLOR_MUTED),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            compositionHeader.addView(ui.textAction("초기화", FitnessUi.COLOR_TERTIARY, () -> {
                draftMenus.clear();
                draftIngredients.clear();
                draftMenuName = "";
                menuBuilderVisible = false;
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

            Button buildMenu = ui.button(
                    menuBuilderVisible ? "메뉴 만들기 접기" : "직접 만든 메뉴 추가",
                    false,
                    v -> toggleMenuBuilder()
            );
            Button saveMeal = ui.button("끼니 기록하기", true, v -> saveMeal());
            card.addView(ui.buttonRow(buildMenu, saveMeal), ui.fullWidthParams(ui.dp(16)));
            if (menuBuilderVisible) {
                card.addView(menuBuilder(), ui.fullWidthParams(ui.dp(16)));
            }
            card.addView(ui.hairline(ui.border()), ui.fullWidthParams(ui.dp(20)));
            appendCatalogSection(card);
        }
        return card;
    }

    private void addMealEntryModeTab(LinearLayout tabs, String label, int mode) {
        FitnessUi ui = ui();
        Button tab = ui.filterButton(label);
        ui.styleFilterButton(tab, mealEntryMode == mode);
        tab.setOnClickListener(v -> {
            syncDraftFromViews();
            if (mealEntryMode != mode) {
                mealEntryMode = mode;
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

    private View diningOutForm() {
        FitnessUi ui = ui();
        LinearLayout form = ui.form();

        form.addView(ui.text(
                "외식은 가게와 먹은 메뉴를 별도 기록합니다. 메뉴 저장하고 기록하면 재사용 메뉴와 끼니 스냅샷을 함께 남깁니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(4)));

        diningOutStoreInput = ui.input("가게 명을 입력하세요", draftDiningOutStoreName);
        diningOutStoreInput.setContentDescription("가게 명");
        form.addView(ui.labeledFieldColumn("가게 명", diningOutStoreInput),
                ui.fullWidthParams(ui.dp(12)));

        Button selectPriceTraceDiningOut = ui.button(
                "PriceTrace 식당·지점·메뉴 선택",
                false,
                v -> showPriceTraceDiningOutPicker()
        );
        form.addView(selectPriceTraceDiningOut, ui.fullWidthParams(ui.dp(4)));

        diningOutBranchInput = ui.input("선택 지점 (선택)", draftDiningOutBranchName);
        diningOutBranchInput.setContentDescription("선택 지점");
        form.addView(ui.labeledFieldColumn("선택 지점", diningOutBranchInput),
                ui.fullWidthParams(ui.dp(12)));

        diningOutMenuInput = ui.input("먹은 메뉴를 입력하세요", draftDiningOutMenuName);
        diningOutMenuInput.setContentDescription("먹은 메뉴");
        form.addView(ui.labeledFieldColumn("먹은 메뉴", diningOutMenuInput),
                ui.fullWidthParams(ui.dp(4)));

        form.addView(diningOutOptionsSection(), ui.fullWidthParams(ui.dp(12)));

        form.addView(ui.text(
                "탄단지(탄수화물·단백질·지방)는 필수 입력입니다. 칼로리·나트륨·당류·포화지방은 선택 입력이며, 입력을 시작하면 해당 영양값을 모두 입력하세요.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(12)));

        diningOutCaloriesInput = ui.numberInput("kcal", draftDiningOutCalories);
        diningOutCaloriesInput.setContentDescription("칼로리");
        diningOutSodiumInput = ui.decimalInput("mg", draftDiningOutSodium);
        diningOutSodiumInput.setContentDescription("나트륨");
        diningOutSugarsInput = ui.decimalInput("g", draftDiningOutSugars);
        diningOutSugarsInput.setContentDescription("당류");
        diningOutSaturatedFatInput = ui.decimalInput("g", draftDiningOutSaturatedFat);
        diningOutSaturatedFatInput.setContentDescription("포화지방");
        form.addView(
                pairedFields("칼로리 (kcal)", diningOutCaloriesInput, "나트륨 (mg)", diningOutSodiumInput),
                ui.fullWidthParams(ui.dp(10))
        );
        form.addView(
                pairedFields("당류 (g)", diningOutSugarsInput, "포화지방 (g)", diningOutSaturatedFatInput),
                ui.fullWidthParams(ui.dp(10))
        );

        diningOutCarbsInput = ui.decimalInput("g", draftDiningOutCarbs);
        diningOutCarbsInput.setContentDescription("탄수화물");
        diningOutProteinInput = ui.decimalInput("g", draftDiningOutProtein);
        diningOutProteinInput.setContentDescription("단백질");
        diningOutFatInput = ui.decimalInput("g", draftDiningOutFat);
        diningOutFatInput.setContentDescription("지방");
        LinearLayout macroRow = ui.tileRow();
        macroRow.addView(ui.labeledFieldColumn("탄수화물 (g) *", diningOutCarbsInput),
                ui.fieldCellParams(true));
        macroRow.addView(ui.labeledFieldColumn("단백질 (g) *", diningOutProteinInput),
                ui.fieldCellParams(false));
        macroRow.addView(ui.labeledFieldColumn("지방 (g) *", diningOutFatInput),
                ui.fieldCellParams(false));
        form.addView(macroRow, ui.fullWidthParams(ui.dp(12)));

        Button recordDiningOut = ui.button("외식만 기록", false, v -> saveMeal(false));
        Button saveMenuAndRecord = ui.button(
                "메뉴 저장하고 기록",
                true,
                v -> saveMeal(true)
        );
        form.addView(ui.buttonRow(recordDiningOut, saveMenuAndRecord),
                ui.fullWidthParams(ui.dp(14)));
        return form;
    }

    private View diningOutOptionsSection() {
        FitnessUi ui = ui();
        LinearLayout section = new LinearLayout(host.activity());
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.text("메뉴 옵션", 14, FitnessUi.COLOR_TEXT, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(ui.textAction("옵션 추가", FitnessUi.COLOR_TERTIARY, () -> {
            syncDraftFromViews();
            draftDiningOutOptions.add(new DiningOutOptionDraft());
            host.rerender();
        }));
        section.addView(header);

        section.addView(ui.text(
                "예: 면 추가, 고기 추가 · 탄단지는 외식 전체 입력값으로 기록합니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(4)));

        diningOutOptionsContainer = new LinearLayout(host.activity());
        diningOutOptionsContainer.setOrientation(LinearLayout.VERTICAL);
        renderDiningOutOptionRows();
        section.addView(diningOutOptionsContainer, ui.fullWidthParams(ui.dp(4)));
        return section;
    }

    /** Selects the exact PriceTrace restaurant -> location -> menu identity chain. */
    private void showPriceTraceDiningOutPicker() {
        syncDraftFromViews();
        FitnessUi ui = ui();
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(8));

        panel.addView(ui.text(
                "식당명과 지점은 이름으로 연결하지 않습니다. PriceTrace의 식당·지점·메뉴 ID를 선택해 기록합니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(8)));
        EditText query = ui.searchField("식당명 또는 지점명 검색");
        panel.addView(query, ui.fullWidthParams(ui.dp(6)));
        TextView status = ui.text("1. 식당을 검색하세요.", 12, FitnessUi.COLOR_TERTIARY, false);
        panel.addView(status, ui.fullWidthParams(ui.dp(6)));
        LinearLayout restaurants = new LinearLayout(host.activity());
        restaurants.setOrientation(LinearLayout.VERTICAL);
        panel.addView(restaurants, ui.fullWidthParams(ui.dp(4)));
        LinearLayout detail = new LinearLayout(host.activity());
        detail.setOrientation(LinearLayout.VERTICAL);
        panel.addView(detail, ui.fullWidthParams(ui.dp(8)));

        AlertDialog dialog = new AlertDialog.Builder(host.activity())
                .setTitle("외식 3단 구조 선택")
                .setView(panel)
                .setNegativeButton("취소", null)
                .create();
        Button search = ui.button("식당 검색", true, v -> {
            String text = FitnessUi.inputText(query).trim();
            status.setText("식당 목록을 조회하는 중입니다.");
            restaurants.removeAllViews();
            detail.removeAllViews();
            host.searchPriceTraceRestaurants(text, new ScreenHost.RestaurantSearchCallback() {
                @Override
                public void onComplete(List<RestaurantMenuReadV1Client.RestaurantSummary> values) {
                    host.activity().runOnUiThread(() -> {
                        restaurants.removeAllViews();
                        status.setText(values.isEmpty()
                                ? "일치하는 검증 식당이 없습니다."
                                : "1. 식당을 선택하세요.");
                        for (RestaurantMenuReadV1Client.RestaurantSummary value : values) {
                            Button restaurantButton = ui.button(
                                    value.restaurantName,
                                    false,
                                    ignored -> loadPriceTraceDiningOutDetail(
                                            value.restaurantId,
                                            status,
                                            detail,
                                            dialog
                                    )
                            );
                            restaurants.addView(restaurantButton, ui.fullWidthParams(ui.dp(4)));
                        }
                    });
                }

                @Override
                public void onError(Exception error) {
                    host.activity().runOnUiThread(() -> status.setText(
                            "PriceTrace 식당 목록을 불러오지 못했습니다. 설정과 연결을 확인하세요."
                    ));
                }
            });
        });
        panel.addView(search, 1, ui.fullWidthParams(ui.dp(6)));
        dialog.setOnShowListener(ignored -> search.performClick());
        dialog.show();
    }

    private void loadPriceTraceDiningOutDetail(
            String restaurantId,
            TextView status,
            LinearLayout detail,
            AlertDialog dialog
    ) {
        FitnessUi ui = ui();
        status.setText("2. 지점과 메뉴를 불러오는 중입니다.");
        detail.removeAllViews();
        host.loadPriceTraceRestaurant(restaurantId, new ScreenHost.RestaurantLoadCallback() {
            @Override
            public void onComplete(RestaurantMenuReadV1Client.RestaurantDetail value) {
                host.activity().runOnUiThread(() -> {
                    detail.removeAllViews();
                    detail.addView(ui.text(
                            "2. 선택 지점",
                            13,
                            FitnessUi.COLOR_TEXT,
                            true
                    ));
                    LinearLayout locationRows = new LinearLayout(host.activity());
                    locationRows.setOrientation(LinearLayout.VERTICAL);
                    detail.addView(locationRows, ui.fullWidthParams(ui.dp(4)));
                    final RestaurantMenuReadV1Client.RestaurantLocation[] selectedLocation =
                            new RestaurantMenuReadV1Client.RestaurantLocation[1];
                    LinearLayout menuRows = new LinearLayout(host.activity());
                    menuRows.setOrientation(LinearLayout.VERTICAL);
                    detail.addView(ui.text(
                            "3. 선택 메뉴",
                            13,
                            FitnessUi.COLOR_TEXT,
                            true
                    ), ui.fullWidthParams(ui.dp(8)));
                    detail.addView(menuRows, ui.fullWidthParams(ui.dp(4)));
                    for (RestaurantMenuReadV1Client.RestaurantLocation location : value.locations) {
                        String label = location.branchName == null
                                ? "지점명 미기록"
                                : location.branchName;
                        Button locationButton = ui.button(label, false, ignored -> {
                            selectedLocation[0] = location;
                            status.setText("지점 선택 완료 · 메뉴를 선택하세요.");
                            for (int index = 0; index < locationRows.getChildCount(); index++) {
                                View child = locationRows.getChildAt(index);
                                if (child instanceof Button) {
                                    child.setSelected(child == ignored);
                                }
                            }
                        });
                        locationRows.addView(locationButton, ui.fullWidthParams(ui.dp(4)));
                    }
                    for (RestaurantMenuReadV1Client.RestaurantMenu menu : value.menus) {
                        Button menuButton = ui.button(menu.menuName, false, ignored -> {
                            RestaurantMenuReadV1Client.RestaurantLocation location = selectedLocation[0];
                            if (location == null) {
                                host.toast("먼저 선택 지점을 고르세요.");
                                return;
                            }
                            draftDiningOutStoreName = value.restaurantName;
                            draftDiningOutBranchName = location.branchName == null
                                    ? "" : location.branchName;
                            draftDiningOutMenuName = menu.menuName;
                            draftDiningOutRestaurantId = value.restaurantId;
                            draftDiningOutRestaurantLocationId = location.restaurantLocationId;
                            draftDiningOutRestaurantMenuId = menu.restaurantMenuId;
                            draftDiningOutCatalogProductId = menu.catalogProductId;
                            linkedDiningOutStoreName = draftDiningOutStoreName;
                            linkedDiningOutBranchName = draftDiningOutBranchName;
                            linkedDiningOutMenuName = draftDiningOutMenuName;
                            if (diningOutStoreInput != null) {
                                diningOutStoreInput.setText(draftDiningOutStoreName);
                            }
                            if (diningOutBranchInput != null) {
                                diningOutBranchInput.setText(draftDiningOutBranchName);
                            }
                            if (diningOutMenuInput != null) {
                                diningOutMenuInput.setText(draftDiningOutMenuName);
                            }
                            host.toast("PriceTrace 식당·지점·메뉴를 정확히 연결했습니다.");
                            dialog.dismiss();
                        });
                        menuRows.addView(menuButton, ui.fullWidthParams(ui.dp(4)));
                    }
                    status.setText("2. 지점을 선택한 뒤 3. 메뉴를 선택하세요.");
                });
            }

            @Override
            public void onError(Exception error) {
                host.activity().runOnUiThread(() -> status.setText(
                        "PriceTrace 식당 상세를 불러오지 못했습니다."
                ));
            }
        });
    }

    private void renderDiningOutOptionRows() {
        if (diningOutOptionsContainer == null) {
            return;
        }
        FitnessUi ui = ui();
        diningOutOptionsContainer.removeAllViews();
        diningOutOptionInputs.clear();
        diningOutOptionCaloriesInputs.clear();
        diningOutOptionProteinInputs.clear();
        diningOutOptionCarbsInputs.clear();
        diningOutOptionFatInputs.clear();
        if (draftDiningOutOptions.isEmpty()) {
            diningOutOptionsContainer.addView(ui.text(
                    "추가 옵션 없음",
                    12,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
            return;
        }

        for (int index = 0; index < draftDiningOutOptions.size(); index++) {
            final int optionIndex = index;
            DiningOutOptionDraft draft = draftDiningOutOptions.get(index);
            LinearLayout row = new LinearLayout(host.activity());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            EditText input = ui.input(
                    "옵션명 (예: 면 추가)",
                    draft.name
            );
            input.setSingleLine(true);
            input.setContentDescription("외식 옵션 " + (index + 1));
            diningOutOptionInputs.add(input);
            row.addView(input, ui.fullWidthParams(ui.dp(2)));
            LinearLayout nutritionRow = ui.tileRow();
            EditText calories = ui.decimalInput("kcal", draft.calories);
            EditText protein = ui.decimalInput("g", draft.protein);
            EditText carbs = ui.decimalInput("g", draft.carbs);
            EditText fat = ui.decimalInput("g", draft.fat);
            diningOutOptionCaloriesInputs.add(calories);
            diningOutOptionProteinInputs.add(protein);
            diningOutOptionCarbsInputs.add(carbs);
            diningOutOptionFatInputs.add(fat);
            nutritionRow.addView(ui.labeledFieldColumn("kcal", calories), ui.fieldCellParams(true));
            nutritionRow.addView(ui.labeledFieldColumn("P (g)", protein), ui.fieldCellParams(false));
            nutritionRow.addView(ui.labeledFieldColumn("C (g)", carbs), ui.fieldCellParams(false));
            nutritionRow.addView(ui.labeledFieldColumn("F (g)", fat), ui.fieldCellParams(false));
            row.addView(nutritionRow, ui.fullWidthParams(ui.dp(2)));
            row.addView(ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE, () -> {
                syncDraftFromViews();
                if (optionIndex < draftDiningOutOptions.size()) {
                    draftDiningOutOptions.remove(optionIndex);
                }
                host.rerender();
            }));
            diningOutOptionsContainer.addView(row, ui.fullWidthParams(ui.dp(6)));
        }
    }

    private List<DiningOutOption> parsedDiningOutOptions() {
        List<DiningOutOption> options = new ArrayList<>();
        for (DiningOutOptionDraft draft : draftDiningOutOptions) {
            String name = draft.name == null ? "" : draft.name.trim();
            if (name.isEmpty()) {
                continue;
            }
            Integer calories = MealEntryPolicy.optionalDiningOutCalories(draft.calories);
            Double protein = MealEntryPolicy.optionalDiningOutMacro(draft.protein, "옵션 단백질");
            Double carbs = MealEntryPolicy.optionalDiningOutMacro(draft.carbs, "옵션 탄수화물");
            Double fat = MealEntryPolicy.optionalDiningOutMacro(draft.fat, "옵션 지방");
            boolean hasNutrition = calories != null || protein != null || carbs != null || fat != null;
            if (!hasNutrition) {
                options.add(DiningOutOption.descriptive(name));
                continue;
            }
            if (protein == null || carbs == null || fat == null) {
                throw new IllegalArgumentException(
                        "옵션 영양성분을 입력할 때는 탄수화물·단백질·지방을 모두 입력하세요."
                );
            }
            int resolvedCalories = calories == null
                    ? MealEntryPolicy.estimatedDiningOutCalories(carbs, protein, fat)
                    : calories;
            options.add(DiningOutOption.withProfile(
                    name,
                    NutritionProfile.ofMacros(resolvedCalories, protein, carbs, fat)
            ));
        }
        return options;
    }

    private List<DiningOutOption> saveDiningOutOptions(
            boolean saveToCatalog,
            DiningOutIdentity identity
    ) {
        List<DiningOutOption> options = parsedDiningOutOptions();
        if (!saveToCatalog) {
            return options;
        }
        List<DiningOutOption> saved = new ArrayList<>();
        for (DiningOutOption option : options) {
            if (!option.hasNutrition()) {
                saved.add(option);
                continue;
            }
            NutritionFood food = host.nutritionCatalogRepository().saveDiningOutOption(
                    draftDiningOutStoreName,
                    draftDiningOutMenuName,
                    identity,
                    option
            );
            saved.add(DiningOutOption.fromFood(food));
        }
        return saved;
    }

    private void renderCompositionRows() {
        if (compositionRows == null) {
            return;
        }
        FitnessUi ui = ui();
        compositionRows.removeAllViews();
        menuQuantityInputs.clear();
        if (draftMenus.isEmpty()) {
            compositionRows.addView(ui.text(
                    "아래 카탈로그에서 메뉴를 추가하거나 직접 만들어 보세요.",
                    13,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
            return;
        }

        for (int index = 0; index < draftMenus.size(); index++) {
            final int menuIndex = index;
            MealMenuSelection selection = draftMenus.get(index);
            MealCompositionItem item = selection.menu;

            LinearLayout cell = new LinearLayout(host.activity());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
            cell.setBackground(ui.flatSurfaceDrawable(ui.dp(14)));

            LinearLayout header = new LinearLayout(host.activity());
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(ui.glyphCircle(mealGlyph(item.food.kind), false));

            LinearLayout details = new LinearLayout(host.activity());
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(ui.dp(10), 0, ui.dp(8), 0);
            details.addView(ui.text(
                    "메뉴 " + (index + 1) + " · " + mealMenuTypeLabel(selection),
                    11,
                    FitnessUi.COLOR_TERTIARY,
                    true
            ));
            details.addView(ui.text(item.food.displayName(), 15, FitnessUi.COLOR_TEXT, true));
            details.addView(ui.text(
                    item.food.categoryCookingLabel(),
                    11,
                    FitnessUi.COLOR_MUTED,
                    false
            ));
            header.addView(details, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            TextView delete = ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE, () -> {
                syncDraftFromViews();
                draftMenus.remove(menuIndex);
                host.rerender();
            });
            delete.setContentDescription(item.food.displayName() + " 메뉴 삭제");
            header.addView(delete);
            cell.addView(header);

            if (!selection.components.isEmpty()) {
                TextView components = ui.text(
                        "구성 · " + componentPreview(selection),
                        11,
                        FitnessUi.COLOR_MUTED,
                        false
                );
                components.setPadding(ui.dp(48), ui.dp(8), 0, 0);
                cell.addView(components);
            }

            View divider = ui.hairline(ui.border());
            LinearLayout.LayoutParams dividerParams = ui.fullWidthParams(ui.dp(10));
            dividerParams.height = ui.dp(1);
            cell.addView(divider, dividerParams);

            LinearLayout footer = new LinearLayout(host.activity());
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.setGravity(Gravity.BOTTOM);

            LinearLayout nutrition = new LinearLayout(host.activity());
            nutrition.setOrientation(LinearLayout.VERTICAL);
            nutrition.addView(ui.caption("현재 영양", FitnessUi.COLOR_MUTED));
            TextView nutritionSummary = ui.text(
                    mealMenuNutritionLabel(item),
                    12,
                    FitnessUi.COLOR_TEXT,
                    true
            );
            nutritionSummary.setPadding(0, ui.dp(4), ui.dp(8), 0);
            nutrition.addView(nutritionSummary);
            footer.addView(nutrition, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            LinearLayout quantityBlock = new LinearLayout(host.activity());
            quantityBlock.setOrientation(LinearLayout.VERTICAL);
            TextView quantityLabel = ui.caption("섭취량", FitnessUi.COLOR_MUTED);
            quantityLabel.setGravity(Gravity.END);
            quantityBlock.addView(quantityLabel);

            LinearLayout quantityRow = new LinearLayout(host.activity());
            quantityRow.setOrientation(LinearLayout.HORIZONTAL);
            quantityRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

            EditText quantity = ui.decimalInput(
                    "섭취량 (" + NutritionUnit.display(item.food.basisUnit) + ")",
                    NutritionCalculator.trim(item.quantity)
            );
            quantity.setSelectAllOnFocus(true);
            quantity.setContentDescription(
                    item.food.displayName() + " 섭취량, 단위 "
                            + NutritionUnit.display(item.food.basisUnit)
            );
            menuQuantityInputs.add(quantity);
            quantityRow.addView(quantity, new LinearLayout.LayoutParams(ui.dp(82), ui.dp(48)));
            TextView unit = ui.text(
                    NutritionUnit.display(item.food.basisUnit),
                    12,
                    FitnessUi.COLOR_MUTED,
                    true
            );
            unit.setPadding(ui.dp(6), 0, 0, 0);
            quantityRow.addView(unit);
            quantityBlock.addView(quantityRow);
            footer.addView(quantityBlock);
            cell.addView(footer, ui.fullWidthParams(ui.dp(10)));

            quantity.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    updateCompositionTotal();
                    if (menuIndex < draftMenus.size()) {
                        nutritionSummary.setText(mealMenuNutritionLabel(
                                draftMenus.get(menuIndex).menu
                        ));
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
            compositionRows.addView(cell, ui.fullWidthParams(index == 0 ? 0 : ui.dp(8)));
        }
    }

    private String mealMenuTypeLabel(MealMenuSelection selection) {
        if (selection != null && !selection.components.isEmpty()) {
            return selection.menu.food.id == null ? "이번 끼니 메뉴" : "저장 메뉴";
        }
        String kind = selection == null || selection.menu == null
                ? NutritionFood.KIND_EXTERNAL_MENU
                : NutritionFood.normalizeKind(selection.menu.food.kind);
        return NutritionFood.kindLabel(kind);
    }

    private String mealMenuNutritionLabel(MealCompositionItem item) {
        return Math.round(item.calories) + " kcal · 탄 "
                + NutritionCalculator.trim(item.carbsGrams) + "g · 단 "
                + NutritionCalculator.trim(item.proteinGrams) + "g · 지 "
                + NutritionCalculator.trim(item.fatGrams) + "g";
    }

    private void toggleMenuBuilder() {
        syncDraftFromViews();
        menuBuilderVisible = !menuBuilderVisible;
        catalogMode = CATALOG_MODE_SEARCH;
        catalogKindFilter = CATALOG_FILTER_ALL;
        catalogQuery = "";
        host.rerender();
    }

    private View menuBuilder() {
        FitnessUi ui = ui();
        LinearLayout panel = ui.form();
        panel.setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14));
        panel.setBackground(ui.flatSurfaceDrawable(ui.dp(14)));
        ui.cardHeader(panel, "직접 만든 메뉴", "메뉴 이름과 재료 구성");

        menuNameInput = ui.input("메뉴 이름 (예: 계란 볶음밥)", draftMenuName);
        panel.addView(ui.labeledFieldColumn("메뉴 이름", menuNameInput));
        panel.addView(ui.text(
                "재료는 아래 카탈로그에서 추가합니다. 수량은 실제 사용량으로 입력하세요.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(10)));

        ingredientQuantityInputs.clear();
        if (draftIngredients.isEmpty()) {
            panel.addView(ui.text(
                    "아직 재료가 없습니다.",
                    13,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ), ui.fullWidthParams(ui.dp(10)));
        } else {
            for (int index = 0; index < draftIngredients.size(); index++) {
                panel.addView(menuBuilderIngredientRow(index));
            }
        }

        menuBuilderTotalBox = new LinearLayout(host.activity());
        menuBuilderTotalBox.setOrientation(LinearLayout.VERTICAL);
        panel.addView(menuBuilderTotalBox, ui.fullWidthParams(ui.dp(10)));
        updateMenuBuilderTotal();

        Button saveAndAdd = ui.button(
                "저장하고 추가",
                false,
                v -> completeBuiltMenu(true)
        );
        Button addOnce = ui.button(
                "이 끼니에만 추가",
                true,
                v -> completeBuiltMenu(false)
        );
        panel.addView(ui.buttonRow(saveAndAdd, addOnce), ui.fullWidthParams(ui.dp(12)));
        panel.addView(ui.textAction("작성 취소", FitnessUi.COLOR_NEGATIVE, () -> {
            draftIngredients.clear();
            draftMenuName = "";
            menuBuilderVisible = false;
            catalogMode = CATALOG_MODE_SEARCH;
            catalogKindFilter = CATALOG_FILTER_ALL;
            host.rerender();
        }), ui.fullWidthParams(ui.dp(8)));
        return panel;
    }

    private View menuBuilderIngredientRow(int index) {
        FitnessUi ui = ui();
        MealCompositionItem ingredient = draftIngredients.get(index);
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, ui.dp(5), 0, ui.dp(5));

        LinearLayout details = new LinearLayout(host.activity());
        details.setOrientation(LinearLayout.VERTICAL);
        details.addView(ui.text(
                ingredient.food.displayName(),
                13,
                FitnessUi.COLOR_TEXT,
                true
        ));
        details.addView(ui.text(
                Math.round(ingredient.calories) + " kcal",
                11,
                FitnessUi.COLOR_MUTED,
                false
        ));
        row.addView(details, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        EditText quantity = ui.decimalInput(
                "사용량 (" + NutritionUnit.display(ingredient.food.basisUnit) + ")",
                NutritionCalculator.trim(ingredient.quantity)
        );
        quantity.setSelectAllOnFocus(true);
        ingredientQuantityInputs.add(quantity);
        LinearLayout.LayoutParams quantityParams = new LinearLayout.LayoutParams(
                ui.dp(82),
                ui.dp(48)
        );
        quantityParams.setMargins(0, 0, ui.dp(6), 0);
        row.addView(quantity, quantityParams);
        row.addView(ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE, () -> {
            syncDraftFromViews();
            draftIngredients.remove(index);
            host.rerender();
        }));
        quantity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateMenuBuilderTotal();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        return row;
    }

    private void updateMenuBuilderTotal() {
        if (menuBuilderTotalBox == null) {
            return;
        }
        syncDraftFromViews();
        NutritionTotals total = NutritionCalculator.sum(draftIngredients);
        menuBuilderTotalBox.removeAllViews();
        menuBuilderTotalBox.addView(ui().text(
                "메뉴 합계 · " + Math.round(total.calories()) + " kcal · 탄 "
                        + NutritionCalculator.trim(total.carbsGrams()) + "g · 단 "
                        + NutritionCalculator.trim(total.proteinGrams()) + "g · 지 "
                        + NutritionCalculator.trim(total.fatGrams()) + "g",
                12,
                FitnessUi.COLOR_MUTED,
                true
        ));
    }

    private void completeBuiltMenu(boolean saveToCatalog) {
        syncDraftFromViews();
        String name = draftMenuName.trim();
        if (name.isEmpty()) {
            if (menuNameInput != null) {
                menuNameInput.setError("메뉴 이름을 입력하세요.");
            }
            return;
        }
        if (draftIngredients.isEmpty()) {
            host.toast("메뉴에 들어간 재료를 하나 이상 추가하세요.");
            return;
        }

        try {
            List<MealCompositionItem> components = new ArrayList<>(draftIngredients);
            NutritionFood menuFood = saveToCatalog
                    ? host.nutritionCatalogRepository().saveRecipe(name, components)
                    : host.nutritionCatalogRepository().buildRecipeForMeal(name, components);
            draftMenus.add(MealMenuSelection.composed(
                    MealCompositionItem.from(menuFood, menuFood.basisAmount),
                    components
            ));
            draftIngredients.clear();
            draftMenuName = "";
            menuBuilderVisible = false;
            catalogMode = CATALOG_MODE_SEARCH;
            catalogKindFilter = CATALOG_FILTER_ALL;
            catalogQuery = "";
            if (saveToCatalog) {
                syncCatalog(true);
            }
            host.toast(saveToCatalog
                    ? "메뉴를 저장하고 현재 끼니에 추가했습니다."
                    : "현재 끼니에 직접 만든 메뉴를 추가했습니다.");
            host.rerender();
        } catch (Exception error) {
            host.toast(error.getMessage() == null
                    ? "메뉴 구성을 저장하지 못했습니다."
                    : error.getMessage());
        }
    }

    private String componentPreview(MealMenuSelection selection) {
        StringBuilder preview = new StringBuilder();
        int limit = Math.min(2, selection.components.size());
        for (int index = 0; index < limit; index++) {
            MealCompositionItem component = selection.components.get(index);
            if (preview.length() > 0) {
                preview.append(" · ");
            }
            preview.append(component.food.displayName())
                    .append(' ')
                    .append(NutritionCalculator.trim(component.quantity))
                    .append(NutritionUnit.display(component.food.basisUnit));
        }
        int remaining = selection.components.size() - limit;
        if (remaining > 0) {
            preview.append(" 외 ").append(remaining).append("개");
        }
        return preview.toString();
    }

    private void appendCatalogSection(LinearLayout card) {
        FitnessUi ui = ui();
        ui.cardHeader(
                card,
                menuBuilderVisible ? "메뉴에 넣을 재료" : "끼니에 넣을 항목",
                catalogSyncing ? "동기화 중" : "유형별로 찾거나 새로 등록"
        );

        LinearLayout modeTabs = new LinearLayout(host.activity());
        modeTabs.setOrientation(LinearLayout.HORIZONTAL);
        addCatalogModeTab(modeTabs, "찾기", CATALOG_MODE_SEARCH);
        addCatalogModeTab(modeTabs, "단일 식품", CATALOG_MODE_SINGLE_FOOD);
        addCatalogModeTab(modeTabs, "완제품", CATALOG_MODE_FINISHED_PRODUCT);
        card.addView(modeTabs, ui.fullWidthParams(ui.dp(12)));

        if (!syncMessage.trim().isEmpty()) {
            TextView catalogStatus = ui.text(syncMessage, 12, FitnessUi.COLOR_MUTED, false);
            catalogStatus.setPadding(0, ui.dp(5), 0, 0);
            card.addView(catalogStatus);
        }

        if (catalogMode == CATALOG_MODE_SINGLE_FOOD) {
            appendVerifiedSingleFoodSearch(card);
            card.addView(ui.hairline(ui.border()), ui.fullWidthParams(ui.dp(18)));
            ui.cardHeader(
                    card,
                    "직접 등록",
                    "공식 DB에 없거나 조리 방식이 다른 식품만 직접 입력하세요."
            );
            card.addView(directFoodForm(true), ui.fullWidthParams(ui.dp(10)));
        } else if (catalogMode == CATALOG_MODE_FINISHED_PRODUCT) {
            card.addView(directFoodForm(false), ui.fullWidthParams(ui.dp(10)));
        } else {
            appendCatalogKindFilters(card);
            appendCatalogSearch(card);
        }
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

    private void appendCatalogKindFilters(LinearLayout card) {
        FitnessUi ui = ui();
        LinearLayout filters = new LinearLayout(host.activity());
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, ui.dp(4), 0, 0);
        addCatalogKindFilter(filters, "전체", CATALOG_FILTER_ALL);
        if (!menuBuilderVisible) {
            addCatalogKindFilter(filters, "저장 메뉴", NutritionFood.KIND_RECIPE);
        }
        addCatalogKindFilter(filters, "단일 식품", NutritionFood.KIND_INGREDIENT);
        addCatalogKindFilter(filters, "완제품", NutritionFood.KIND_EXTERNAL_MENU);
        card.addView(filters, ui.fullWidthParams(ui.dp(4)));
    }

    private void addCatalogKindFilter(LinearLayout filters, String label, String kind) {
        FitnessUi ui = ui();
        Button filter = ui.filterButton(label);
        ui.styleFilterButton(filter, kind.equals(catalogKindFilter));
        filter.setOnClickListener(v -> {
            if (!kind.equals(catalogKindFilter)) {
                catalogKindFilter = kind;
                host.rerender();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(ui.dp(2), 0, ui.dp(2), 0);
        filters.addView(filter, params);
    }

    private void appendCatalogSearch(LinearLayout card) {
        FitnessUi ui = ui();
        catalogSearchInput = ui.searchField("식품명 또는 상품명 검색");
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
        card.addView(catalogSearchInput, ui.fullWidthParams(ui.dp(10)));

        catalogResults = new LinearLayout(host.activity());
        catalogResults.setOrientation(LinearLayout.VERTICAL);
        card.addView(catalogResults, ui.fullWidthParams(ui.dp(4)));
        renderCatalogResults();

        Button sync = ui.button("원격 카탈로그 새로고침", false, v -> syncCatalog(true));
        card.addView(sync, ui.fullWidthParams(ui.dp(10)));
    }

    private void appendVerifiedSingleFoodSearch(LinearLayout card) {
        FitnessUi ui = ui();
        ui.cardHeader(
                card,
                "검증 식품 불러오기",
                "식약처·농촌진흥청 공식 · 항목별 표기 조리상태의 가식부 100g 기준 "
                        + "(회는 생것 기준, 구이는 구이 후, 곡물은 취사 전 마른 원곡) · "
                        + "선택하면 현재 끼니(또는 메뉴 재료)에 바로 추가"
        );

        verifiedSingleFoodSearchInput = ui.searchField("닭가슴살, 연어, 현미, 브로콜리 검색");
        verifiedSingleFoodSearchInput.setTag(VERIFIED_SINGLE_FOOD_SEARCH_TAG);
        verifiedSingleFoodSearchInput.setContentDescription("검증 식품 불러오기 검색");
        verifiedSingleFoodSearchInput.setText(verifiedSingleFoodQuery);
        verifiedSingleFoodSearchInput.setSelection(verifiedSingleFoodSearchInput.length());
        verifiedSingleFoodSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                verifiedSingleFoodQuery = text == null ? "" : text.toString();
                renderVerifiedSingleFoodResults();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        card.addView(verifiedSingleFoodSearchInput, ui.fullWidthParams(ui.dp(12)));

        verifiedSingleFoodResults = new LinearLayout(host.activity());
        verifiedSingleFoodResults.setOrientation(LinearLayout.VERTICAL);
        verifiedSingleFoodResults.setTag(VERIFIED_SINGLE_FOOD_RESULTS_TAG);
        card.addView(verifiedSingleFoodResults);
        renderVerifiedSingleFoodResults();
    }

    private void renderCatalogResults() {
        if (catalogResults == null) {
            return;
        }
        FitnessUi ui = ui();
        catalogResults.removeAllViews();
        List<NutritionFood> foods = new ArrayList<>();
        for (NutritionFood food : host.nutritionCatalogRepository().searchFoods(catalogQuery)) {
            if (isCatalogFoodVisible(food)) {
                foods.add(food);
            }
        }
        if (foods.isEmpty()) {
            catalogResults.addView(ui.text(
                    "선택한 분류에 검색 결과가 없습니다. 직접 등록하거나 다른 이름으로 검색하세요.",
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

    private void renderVerifiedSingleFoodResults() {
        if (verifiedSingleFoodResults == null) {
            return;
        }
        FitnessUi ui = ui();
        verifiedSingleFoodResults.removeAllViews();

        List<NutritionFood> foods = new ArrayList<>();
        List<NutritionFood> verifiedMatches = host.nutritionCatalogRepository()
                .searchVerifiedFoods(
                        verifiedSingleFoodQuery,
                        VERIFIED_SINGLE_FOOD_RESULT_LIMIT + 1
                );
        for (NutritionFood food : verifiedMatches) {
            if (isVerifiedSingleFoodVisible(food)) {
                foods.add(food);
            }
        }
        if (foods.isEmpty()) {
            verifiedSingleFoodResults.addView(ui.text(
                    "공식 검증 식품 결과가 없습니다. 아래 직접 등록으로 이어서 입력할 수 있습니다.",
                    13,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
            return;
        }

        int limit = Math.min(VERIFIED_SINGLE_FOOD_RESULT_LIMIT, foods.size());
        for (int index = 0; index < limit; index++) {
            NutritionFood food = foods.get(index);
            verifiedSingleFoodResults.addView(catalogFoodRow(food));
            if (index < limit - 1) {
                verifiedSingleFoodResults.addView(ui.hairline(ui.border()),
                        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1)));
            }
        }
        if (foods.size() > limit) {
            verifiedSingleFoodResults.addView(ui.text(
                    "검색어를 더 입력하면 공식 검증 식품 결과를 더 좁힐 수 있습니다.",
                    11,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ), ui.fullWidthParams(ui.dp(8)));
        }
    }

    private boolean isVerifiedSingleFoodVisible(NutritionFood food) {
        return food != null
                && NutritionFood.KIND_INGREDIENT.equals(NutritionFood.normalizeKind(food.kind))
                && isVerifiedCatalogSeedFood(food);
    }

    private boolean isVerifiedCatalogSeedFood(NutritionFood food) {
        return VerifiedFoodCatalogSeed.isVerifiedSeedFood(food);
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
        row.setOnClickListener(v -> addCatalogFood(food));
        row.setContentDescription(
                NutritionFood.kindLabel(food.kind) + " " + food.name
                        + ", " + (menuBuilderVisible ? "재료로 추가" : "끼니에 추가")
        );

        LinearLayout details = new LinearLayout(host.activity());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(ui.dp(10), 0, ui.dp(8), 0);
        details.addView(ui.text(
                NutritionFood.kindLabel(food.kind),
                11,
                FitnessUi.COLOR_TERTIARY,
                true
        ));
        details.addView(ui.text(food.name, 14, FitnessUi.COLOR_TEXT, true));
        boolean finishedProduct = NutritionFood.KIND_EXTERNAL_MENU.equals(
                NutritionFood.normalizeKind(food.kind)
        );
        ProductNutritionLink approved = null;
        List<ProductNutritionLink> suggestions = new ArrayList<>();
        if (finishedProduct) {
            approved = host.nutritionCatalogRepository().approvedProductLink(food.id);
            suggestions = host.nutritionCatalogRepository().pendingProductLinkSuggestions(food.id);
        }
        row.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout actions = new LinearLayout(host.activity());
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.END);
        actions.addView(ui.text(
                menuBuilderVisible ? "재료로 추가 ›" : "끼니에 추가 ›",
                12,
                FitnessUi.COLOR_TERTIARY,
                true
        ));
        if (finishedProduct) {
            ProductNutritionLink approvedLink = approved;
            boolean hasSuggestions = !suggestions.isEmpty();
            actions.addView(ui.textAction(
                    hasSuggestions ? "제안 확인"
                            : (approvedLink == null ? "상품 연결" : "연결 관리"),
                    approvedLink == null ? FitnessUi.COLOR_TERTIARY : FitnessUi.COLOR_MUTED,
                    () -> productLinkController.show(food)
            ));
        }
        row.addView(actions);
        return row;
    }

    private View directFoodForm(boolean ingredientMode) {
        FitnessUi ui = ui();
        LinearLayout form = ui.form();
        form.addView(ui.text(
                ingredientMode
                        ? "브랜드가 없는 기본 식품입니다. 조리 방식이 다르면 별도 식품으로 등록하세요."
                        : "브랜드와 포장 단위가 있는 상품입니다. PriceTrace에서 불러오거나 직접 입력하세요.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        EditText name = ui.input(
                ingredientMode
                        ? "단일 식품 이름 (예: 현미밥, 구운 닭가슴살)"
                        : "완제품 이름 (예: 닭가슴살 소시지)",
                ""
        );
        EditText brand = ingredientMode ? null : ui.input("브랜드 (예: CJ)", "");
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
        String[] manualName = {""};
        String[] manualBrand = {""};
        String[] manualBasisAmount = {""};
        String[] manualBasisUnit = {NutritionUnitSelector.value(basisUnit)};
        LinearLayout priceTraceResults = new LinearLayout(host.activity());
        priceTraceResults.setOrientation(LinearLayout.VERTICAL);
        EditText priceTraceQuery = ui.searchField("PriceTrace 상품명 검색");
        Button priceTraceSearch = ui.button("PriceTrace 상품 불러오기", false, null);
        TextView priceTraceSelection = ui.text(
                "",
                11,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        priceTraceSelection.setVisibility(View.GONE);
        Button clearPriceTraceSelection = ui.button(
                "PriceTrace 선택 해제 · 직접 입력",
                false,
                null
        );
        clearPriceTraceSelection.setVisibility(View.GONE);
        clearPriceTraceSelection.setOnClickListener(v -> {
            selectedProduct[0] = null;
            name.setText(manualName[0]);
            unlockPriceTraceLoadedField(name);
            if (brand != null) {
                brand.setText(manualBrand[0]);
                unlockPriceTraceLoadedField(brand);
            }
            basisAmount.setText(manualBasisAmount[0]);
            NutritionUnitSelector.setValue(basisUnit, manualBasisUnit[0]);
            priceTraceResults.removeAllViews();
            priceTraceSelection.setVisibility(View.VISIBLE);
            priceTraceSelection.setText("직접 입력으로 전환했습니다. 상품 연결 없이 저장할 수 있습니다.");
            clearPriceTraceSelection.setVisibility(View.GONE);
        });
        boolean priceTraceConfigured = host.priceTraceSupabaseConfig().isConnectionConfigured();
        if (!priceTraceConfigured) {
            priceTraceQuery.setEnabled(false);
            priceTraceSearch.setEnabled(false);
            priceTraceSelection.setVisibility(View.VISIBLE);
            priceTraceSelection.setText("설정에서 PriceTrace 읽기 전용 DB를 연결하면 상품을 불러올 수 있습니다.");
        }
        priceTraceSearch.setOnClickListener(v -> {
            String query = FitnessUi.inputText(priceTraceQuery).trim();
            if (query.isEmpty()) {
                host.toast("PriceTrace에서 검색할 상품명을 입력하세요.");
                return;
            }
            priceTraceSearch.setEnabled(false);
            priceTraceSelection.setVisibility(View.VISIBLE);
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
                                 nutrients,
                                 name,
                                 brand,
                                 basisAmount,
                                 basisUnit,
                                 selectedProduct,
                                 clearPriceTraceSelection,
                                 manualName,
                                 manualBrand,
                                 manualBasisAmount,
                                 manualBasisUnit
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
            ui.addAll(form, priceTraceQuery, priceTraceSearch, priceTraceSelection,
                    clearPriceTraceSelection, priceTraceResults);
        }
        Button saveOnly = ui.button(
                ingredientMode ? "단일 식품으로 저장" : "완제품으로 저장",
                false,
                v -> saveDirectFood(
                        name, brand, selectedCategory[0], selectedCookingMethod[0],
                        basisAmount, basisUnit, nutrients,
                        ingredientMode, selectedProduct[0], false
                )
        );
        Button saveAndAdd = ui.button(
                menuBuilderVisible ? "저장 후 재료로 추가" : "저장 후 끼니에 추가",
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
            NutritionInputSection nutrients,
            EditText name,
            EditText brand,
            EditText basisAmount,
            Button basisUnit,
            ProductReadV1[] selectedProduct,
            Button clearSelection,
            String[] manualName,
            String[] manualBrand,
            String[] manualBasisAmount,
            String[] manualBasisUnit
    ) {
        FitnessUi ui = ui();
        results.removeAllViews();
        if (products == null || products.isEmpty()) {
            selection.setText("일치하는 PriceTrace 상품이 없습니다. 다른 상품명을 검색하세요.");
            return;
        }
        selection.setText(products.size() + "개 표준상품 · 브랜드와 상품명만 표시합니다.");
        for (ProductReadV1 product : products) {
            Button choice = ui.button(product.standardProductLabel(), false, v -> {
                if (selectedProduct[0] == null) {
                    manualName[0] = FitnessUi.inputText(name);
                    manualBrand[0] = brand == null ? "" : FitnessUi.inputText(brand);
                    manualBasisAmount[0] = FitnessUi.inputText(basisAmount);
                    manualBasisUnit[0] = NutritionUnitSelector.value(basisUnit);
                }
                selectedProduct[0] = product;
                name.setText(product.name);
                lockPriceTraceLoadedField(name);
                if (brand != null) {
                    brand.setText(product.brand == null ? "" : product.brand);
                    lockPriceTraceLoadedField(brand);
                }
                if (product.isExactCatalogProduct()
                        && product.contentAmount != null
                        && product.contentAmount > 0) {
                    basisAmount.setText(NutritionCalculator.trim(product.contentAmount));
                }
                if (product.isExactCatalogProduct()
                        && product.contentUnit != null
                        && NutritionUnit.isSupported(product.contentUnit)) {
                    NutritionUnitSelector.setValue(basisUnit, product.contentUnit);
                } else if (product.isExactCatalogProduct()
                        && product.contentUnit != null
                        && !product.contentUnit.trim().isEmpty()) {
                    NutritionUnitSelector.setValue(basisUnit, NutritionUnit.SERVING);
                }
                selection.setText("선택됨 · " + product.standardProductLabel());
                clearSelection.setVisibility(View.VISIBLE);
                results.removeAllViews();
                loadPublicProductNutrition(product, nutrients, selectedProduct, selection);
            });
            choice.setAllCaps(false);
            choice.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            results.addView(choice, ui.fullWidthParams(ui.dp(7)));
        }
    }

    private boolean isCatalogFoodVisible(NutritionFood food) {
        String kind = NutritionFood.normalizeKind(food == null ? null : food.kind);
        if (menuBuilderVisible && !NutritionFood.canBeRecipeComponent(kind)) {
            return false;
        }
        return CATALOG_FILTER_ALL.equals(catalogKindFilter)
                || catalogKindFilter.equals(kind);
    }

    private void loadPublicProductNutrition(
            ProductReadV1 product,
            NutritionInputSection nutrients,
            ProductReadV1[] selectedProduct,
            TextView selection
    ) {
        if (!product.isExactCatalogProduct()) {
            selection.setText("PriceTrace 표준상품을 선택했습니다. 정확한 규격을 선택하면 공개 영양정보를 불러옵니다.");
            return;
        }
        selection.setText("선택됨 · 공개 영양정보를 불러오는 중입니다.");
        host.loadPublicProductNutrition(
                product.catalogProductId,
                new ScreenHost.PublicNutritionCallback() {
                    @Override
                    public void onComplete(
                            NutritionCatalogRepository.PublicProductNutrition nutrition
                    ) {
                        host.activity().runOnUiThread(() -> {
                            if (selectedProduct[0] != product) {
                                return;
                            }
                            if (nutrition == null) {
                                selection.setText("선택됨 · 공개 영양정보가 없어 직접 입력하세요.");
                                return;
                            }
                            if (!nutrition.hasRequiredNutrition()) {
                                selection.setText("선택됨 · 공개 영양정보가 불완전해 직접 입력하세요.");
                                return;
                            }
                            nutrients.applyProfile(nutrition.profile);
                            selection.setText("선택됨 · 공개 영양정보를 불러왔습니다. 필요하면 수정할 수 있습니다.");
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        host.activity().runOnUiThread(() -> {
                            if (selectedProduct[0] != product) {
                                return;
                            }
                            selection.setText("선택됨 · 공개 영양정보를 불러오지 못해 직접 입력하세요.");
                        });
                    }
                }
        );
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

    private void unlockPriceTraceLoadedField(EditText field) {
        FitnessUi ui = ui();
        field.setEnabled(true);
        field.setCursorVisible(true);
        field.setLongClickable(true);
        field.setTextIsSelectable(false);
        field.setContentDescription(null);
        field.setBackground(ui.flatSurfaceDrawable(ui.dp(12)));
        ui.applyDepth(field, 3);
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
            String selectedBasisUnit = NutritionUnitSelector.value(basisUnit);
            ProductReadV1 exactProduct = selectedProduct == null
                    ? null
                    : selectedProduct.exactVariantForBasis(basis, selectedBasisUnit);
            String productBrand = brand == null ? null : FitnessUi.inputText(brand);
            if (selectedProduct != null && (productBrand == null || productBrand.trim().isEmpty())) {
                productBrand = selectedProduct.brand;
            }
            String sourceReference = "";
            String sourceType = "manual";
            if (selectedProduct != null) {
                sourceType = exactProduct == null
                        ? "pricetrace_standard"
                        : "pricetrace_manual";
                sourceReference = exactProduct == null
                        ? "standardProductId:" + selectedProduct.standardProductId
                        : "catalogProductId:" + exactProduct.catalogProductId;
            }
            NutritionFood saved = host.nutritionCatalogRepository().saveFood(
                    FitnessUi.inputText(name),
                    productBrand,
                    ingredientMode ? NutritionFood.KIND_INGREDIENT : NutritionFood.KIND_EXTERNAL_MENU,
                    category,
                    basis,
                    selectedBasisUnit,
                    cookingMethod,
                    nutrients.profile(),
                    sourceType,
                    sourceReference,
                    ""
            );
            if (exactProduct != null) {
                host.nutritionCatalogRepository().linkProduct(saved.id, exactProduct);
            }
            if (addToMeal) {
                if (menuBuilderVisible) {
                    draftIngredients.add(MealCompositionItem.from(saved, saved.basisAmount));
                } else {
                    draftMenus.add(menuSelectionForFood(saved));
                }
            }
            syncCatalog(true);
            host.toast(
                    (ingredientMode ? "단일 식품" : "완제품") + "을 저장했습니다."
                            + (selectedProduct == null
                            ? ""
                            : (exactProduct == null
                            ? " PriceTrace 표준상품을 적용했으며 임의의 규격 연결은 만들지 않았습니다."
                            : " PriceTrace 상품도 연결했습니다."))
                            + (addToMeal
                            ? (menuBuilderVisible
                            ? " 현재 메뉴의 재료로 추가되었습니다."
                            : " 현재 끼니에 추가되었습니다.")
                            : "")
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

    private void addCatalogFood(NutritionFood food) {
        syncDraftFromViews();
        if (menuBuilderVisible) {
            if (!NutritionFood.canBeRecipeComponent(food.kind)) {
                host.toast("저장 메뉴는 다른 메뉴의 재료로 넣을 수 없습니다.");
                return;
            }
            draftIngredients.add(MealCompositionItem.from(food, food.basisAmount));
        } else {
            draftMenus.add(menuSelectionForFood(food));
        }
        host.rerender();
    }

    private MealMenuSelection menuSelectionForFood(NutritionFood food) {
        MealCompositionItem menu = MealCompositionItem.from(food, food.basisAmount);
        if (!NutritionFood.KIND_RECIPE.equals(food.kind)) {
            return MealMenuSelection.standalone(menu);
        }
        List<NutritionCatalogRepository.RecipeComponent> savedComponents =
                host.nutritionCatalogRepository().recipeComponents(food.id);
        if (savedComponents.isEmpty()) {
            return MealMenuSelection.standalone(menu);
        }
        List<MealCompositionItem> components = new ArrayList<>();
        for (NutritionCatalogRepository.RecipeComponent component : savedComponents) {
            components.add(MealCompositionItem.from(
                    component.food,
                    component.quantity,
                    component.unit
            ));
        }
        return MealMenuSelection.composed(menu, components);
    }

    private void saveMeal() {
        saveMeal(false);
    }

    private DiningOutIdentity selectedDiningOutIdentity() {
        boolean anyIdentity = !draftDiningOutRestaurantId.trim().isEmpty()
                || !draftDiningOutRestaurantLocationId.trim().isEmpty()
                || !draftDiningOutRestaurantMenuId.trim().isEmpty()
                || !draftDiningOutCatalogProductId.trim().isEmpty();
        if (!anyIdentity) {
            return null;
        }
        if (draftDiningOutRestaurantId.trim().isEmpty()
                || draftDiningOutRestaurantLocationId.trim().isEmpty()
                || draftDiningOutRestaurantMenuId.trim().isEmpty()
                || draftDiningOutCatalogProductId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "PriceTrace 식당·지점·메뉴를 모두 선택하거나 연결 없이 직접 입력하세요."
            );
        }
        return DiningOutIdentity.fromPriceTrace(
                draftDiningOutRestaurantId,
                draftDiningOutStoreName,
                draftDiningOutRestaurantLocationId,
                draftDiningOutBranchName,
                draftDiningOutRestaurantMenuId,
                draftDiningOutMenuName,
                draftDiningOutCatalogProductId
        );
    }

    private void saveMeal(boolean saveDiningOutMenu) {
        syncDraftFromViews();
        String recordedMealTime = draftMealTime;
        if (mealEntryMode == MEAL_ENTRY_MODE_DINING_OUT) {
            try {
                Double carbsGrams = MealEntryPolicy.requireDiningOutMacro(
                        draftDiningOutCarbs,
                        "탄수화물"
                );
                Double proteinGrams = MealEntryPolicy.requireDiningOutMacro(
                        draftDiningOutProtein,
                        "단백질"
                );
                Double fatGrams = MealEntryPolicy.requireDiningOutMacro(
                        draftDiningOutFat,
                        "지방"
                );
                Integer calories = MealEntryPolicy.optionalDiningOutCalories(
                        draftDiningOutCalories
                );
                Double sodiumMg = MealEntryPolicy.optionalDiningOutMacro(
                        draftDiningOutSodium,
                        "나트륨"
                );
                Double sugarsGrams = MealEntryPolicy.optionalDiningOutMacro(
                        draftDiningOutSugars,
                        "당류"
                );
                Double saturatedFatGrams = MealEntryPolicy.optionalDiningOutMacro(
                        draftDiningOutSaturatedFat,
                        "포화지방"
                );
                DiningOutIdentity diningOutIdentity = selectedDiningOutIdentity();
                List<DiningOutOption> optionSnapshots = saveDiningOutOptions(
                        saveDiningOutMenu,
                        diningOutIdentity
                );
                boolean hasExtendedNutrition = calories != null
                        || sodiumMg != null
                        || sugarsGrams != null
                        || saturatedFatGrams != null;
                NutritionFood savedMenu;
                if (hasExtendedNutrition) {
                    savedMenu = saveDiningOutMenu
                            ? host.nutritionCatalogRepository().saveDiningOutMenuWithNutrition(
                                    draftDiningOutStoreName,
                                    draftDiningOutMenuName,
                                    calories,
                                    proteinGrams,
                                    carbsGrams,
                                    fatGrams,
                                    sodiumMg,
                                    sugarsGrams,
                                    saturatedFatGrams
                            )
                            : null;
                    if (diningOutIdentity == null) {
                        repository().addDiningOutMealAtTimeWithNutritionAndOptionNutrition(
                                selectedDate,
                                recordedMealTime,
                                draftDiningOutStoreName,
                                draftDiningOutMenuName,
                                calories,
                                proteinGrams,
                                carbsGrams,
                                fatGrams,
                                sodiumMg,
                                sugarsGrams,
                                saturatedFatGrams,
                                savedMenu == null
                                        ? null
                                        : MealCompositionItem.from(savedMenu, savedMenu.basisAmount),
                                optionSnapshots
                        );
                    } else {
                        repository().addDiningOutMealAtTimeWithIdentityAndNutritionAndOptionNutrition(
                                selectedDate,
                                recordedMealTime,
                                diningOutIdentity,
                                calories,
                                proteinGrams,
                                carbsGrams,
                                fatGrams,
                                sodiumMg,
                                sugarsGrams,
                                saturatedFatGrams,
                                savedMenu == null
                                        ? null
                                        : MealCompositionItem.from(savedMenu, savedMenu.basisAmount),
                                optionSnapshots
                        );
                    }
                } else {
                    savedMenu = saveDiningOutMenu
                            ? host.nutritionCatalogRepository().saveDiningOutMenu(
                                    draftDiningOutStoreName,
                                    draftDiningOutMenuName,
                                    carbsGrams,
                                    proteinGrams,
                                    fatGrams
                            )
                            : null;
                    if (diningOutIdentity == null) {
                        repository().addDiningOutMealAtTimeWithOptionNutrition(
                                selectedDate,
                                recordedMealTime,
                                draftDiningOutStoreName,
                                draftDiningOutMenuName,
                                carbsGrams,
                                proteinGrams,
                                fatGrams,
                                savedMenu == null
                                        ? null
                                        : MealCompositionItem.from(savedMenu, savedMenu.basisAmount),
                                optionSnapshots
                        );
                    } else {
                        repository().addDiningOutMealAtTimeWithIdentityAndOptionNutrition(
                                selectedDate,
                                recordedMealTime,
                                diningOutIdentity,
                                carbsGrams,
                                proteinGrams,
                                fatGrams,
                                savedMenu == null
                                        ? null
                                        : MealCompositionItem.from(savedMenu, savedMenu.basisAmount),
                                optionSnapshots
                        );
                    }
                }
                if (saveDiningOutMenu) {
                    syncCatalog(false);
                }
            } catch (IllegalArgumentException error) {
                host.toast(error.getMessage());
                return;
            }
            draftMenus.clear();
            draftIngredients.clear();
            draftMenuName = "";
            draftDiningOutStoreName = "";
            draftDiningOutBranchName = "";
            draftDiningOutMenuName = "";
            draftDiningOutRestaurantId = "";
            draftDiningOutRestaurantLocationId = "";
            draftDiningOutRestaurantMenuId = "";
            draftDiningOutCatalogProductId = "";
            linkedDiningOutStoreName = "";
            linkedDiningOutBranchName = "";
            linkedDiningOutMenuName = "";
            draftDiningOutOptions.clear();
            draftDiningOutCarbs = "";
            draftDiningOutProtein = "";
            draftDiningOutFat = "";
            draftDiningOutCalories = "";
            draftDiningOutSodium = "";
            draftDiningOutSugars = "";
            draftDiningOutSaturatedFat = "";
            diningOutStoreInput = null;
            diningOutBranchInput = null;
            diningOutMenuInput = null;
            diningOutOptionsContainer = null;
            diningOutOptionInputs.clear();
            diningOutCarbsInput = null;
            diningOutProteinInput = null;
            diningOutFatInput = null;
            diningOutCaloriesInput = null;
            diningOutSodiumInput = null;
            diningOutSugarsInput = null;
            diningOutSaturatedFatInput = null;
            mealEntryMode = MEAL_ENTRY_MODE_FOOD;
            draftMealTime = currentMealTime();
            mealWorkspaceVisible = false;
            host.toast(saveDiningOutMenu
                    ? "외식 메뉴를 저장하고 " + dateLabel() + " " + recordedMealTime + "에 기록했습니다."
                    : "외식을 " + dateLabel() + " " + recordedMealTime + "에 기록했습니다.");
            host.rerender();
            return;
        }

        if (draftMenus.isEmpty()) {
            host.toast("끼니에 먹은 메뉴를 하나 이상 추가하세요.");
            return;
        }
        NutritionTotals total = NutritionCalculator.sum(
                MealMenuSelection.menuItems(draftMenus)
        );
        repository().addMealMenusAtTime(
                selectedDate,
                recordedMealTime,
                (int) Math.round(total.calories()),
                total.proteinGrams(),
                total.carbsGrams(),
                total.fatGrams(),
                draftMenus
        );
        draftMenus.clear();
        draftIngredients.clear();
        draftMenuName = "";
        draftDiningOutStoreName = "";
        draftDiningOutMenuName = "";
        draftDiningOutOptions.clear();
        draftDiningOutCarbs = "";
        draftDiningOutProtein = "";
        draftDiningOutFat = "";
        draftDiningOutCalories = "";
        draftDiningOutSodium = "";
        draftDiningOutSugars = "";
        draftDiningOutSaturatedFat = "";
        diningOutStoreInput = null;
        diningOutMenuInput = null;
        diningOutOptionsContainer = null;
        diningOutOptionInputs.clear();
        diningOutCarbsInput = null;
        diningOutProteinInput = null;
        diningOutFatInput = null;
        diningOutCaloriesInput = null;
        diningOutSodiumInput = null;
        diningOutSugarsInput = null;
        diningOutSaturatedFatInput = null;
        mealEntryMode = MEAL_ENTRY_MODE_FOOD;
        menuBuilderVisible = false;
        draftMealTime = currentMealTime();
        mealWorkspaceVisible = false;
        host.toast("끼니를 " + dateLabel() + " " + recordedMealTime + "에 기록했습니다.");
        host.rerender();
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
                    syncMessage = "기기 저장 사용 중 · 원격 동기화 대기";
                    if (userInitiated) {
                        host.toast("원격 카탈로그 동기화에 실패했습니다.");
                    }
                    host.rerender();
                });
            }
        });
    }

    private void syncDraftFromViews() {
        if (catalogSearchInput != null) {
            catalogQuery = FitnessUi.inputText(catalogSearchInput);
        }
        if (verifiedSingleFoodSearchInput != null) {
            verifiedSingleFoodQuery = FitnessUi.inputText(verifiedSingleFoodSearchInput);
        }
        if (menuNameInput != null) {
            draftMenuName = FitnessUi.inputText(menuNameInput);
        }
        if (diningOutStoreInput != null) {
            draftDiningOutStoreName = FitnessUi.inputText(diningOutStoreInput);
        }
        if (diningOutBranchInput != null) {
            draftDiningOutBranchName = FitnessUi.inputText(diningOutBranchInput);
        }
        if (diningOutMenuInput != null) {
            draftDiningOutMenuName = FitnessUi.inputText(diningOutMenuInput);
        }
        if (!draftDiningOutRestaurantId.trim().isEmpty()
                && (!draftDiningOutStoreName.equals(linkedDiningOutStoreName)
                || !draftDiningOutBranchName.equals(linkedDiningOutBranchName)
                || !draftDiningOutMenuName.equals(linkedDiningOutMenuName))) {
            draftDiningOutRestaurantId = "";
            draftDiningOutRestaurantLocationId = "";
            draftDiningOutRestaurantMenuId = "";
            draftDiningOutCatalogProductId = "";
            linkedDiningOutStoreName = "";
            linkedDiningOutBranchName = "";
            linkedDiningOutMenuName = "";
        }
        if (!diningOutOptionInputs.isEmpty()) {
            draftDiningOutOptions.clear();
            for (int index = 0; index < diningOutOptionInputs.size(); index++) {
                DiningOutOptionDraft draft = new DiningOutOptionDraft();
                draft.name = FitnessUi.inputText(diningOutOptionInputs.get(index)).trim();
                draft.calories = FitnessUi.inputText(diningOutOptionCaloriesInputs.get(index));
                draft.protein = FitnessUi.inputText(diningOutOptionProteinInputs.get(index));
                draft.carbs = FitnessUi.inputText(diningOutOptionCarbsInputs.get(index));
                draft.fat = FitnessUi.inputText(diningOutOptionFatInputs.get(index));
                if (!draft.name.isEmpty()) {
                    draftDiningOutOptions.add(draft);
                }
            }
        }
        if (diningOutCarbsInput != null) {
            draftDiningOutCarbs = FitnessUi.inputText(diningOutCarbsInput);
        }
        if (diningOutProteinInput != null) {
            draftDiningOutProtein = FitnessUi.inputText(diningOutProteinInput);
        }
        if (diningOutFatInput != null) {
            draftDiningOutFat = FitnessUi.inputText(diningOutFatInput);
        }
        if (diningOutCaloriesInput != null) {
            draftDiningOutCalories = FitnessUi.inputText(diningOutCaloriesInput);
        }
        if (diningOutSodiumInput != null) {
            draftDiningOutSodium = FitnessUi.inputText(diningOutSodiumInput);
        }
        if (diningOutSugarsInput != null) {
            draftDiningOutSugars = FitnessUi.inputText(diningOutSugarsInput);
        }
        if (diningOutSaturatedFatInput != null) {
            draftDiningOutSaturatedFat = FitnessUi.inputText(diningOutSaturatedFatInput);
        }
        if (menuQuantityInputs.size() == draftMenus.size()) {
            for (int index = 0; index < menuQuantityInputs.size(); index++) {
                MealMenuSelection current = draftMenus.get(index);
                double quantity = FitnessUi.parseDouble(
                        menuQuantityInputs.get(index),
                        current.menu.quantity
                );
                if (quantity > 0) {
                    draftMenus.set(index, current.withQuantity(quantity));
                }
            }
        }
        if (ingredientQuantityInputs.size() == draftIngredients.size()) {
            for (int index = 0; index < ingredientQuantityInputs.size(); index++) {
                MealCompositionItem current = draftIngredients.get(index);
                double quantity = FitnessUi.parseDouble(
                        ingredientQuantityInputs.get(index),
                        current.quantity
                );
                if (quantity > 0) {
                    draftIngredients.set(index, MealCompositionItem.from(current.food, quantity));
                }
            }
        }
    }

    private void updateCompositionTotal() {
        if (compositionTotalBox == null) {
            return;
        }
        syncDraftFromViews();
        NutritionTotals total = NutritionCalculator.sum(
                MealMenuSelection.menuItems(draftMenus)
        );
        FitnessUi ui = ui();
        compositionTotalBox.removeAllViews();
        compositionTotalBox.addView(ui.text(
                "끼니 메뉴 총합계",
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
                entry.previewTitle + " 기록을 삭제할까요?",
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

    private static final class DiningOutOptionDraft {
        private String name = "";
        private String calories = "";
        private String protein = "";
        private String carbs = "";
        private String fat = "";
    }
}
