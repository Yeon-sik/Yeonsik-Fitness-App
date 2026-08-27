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
import com.yeonsik.fitnessapp.data.CompositionGroup;
import com.yeonsik.fitnessapp.data.CompositionGroupType;
import com.yeonsik.fitnessapp.data.CompositionMember;
import com.yeonsik.fitnessapp.data.CompositionTemplate;
import com.yeonsik.fitnessapp.data.CompositionTemplateRepository;
import com.yeonsik.fitnessapp.data.DiningOutConsumption;
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

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String CATALOG_FILTER_RESTAURANT_MENU = "restaurant_menu";
    private static final int VERIFIED_SINGLE_FOOD_RESULT_LIMIT = 8;
    private static final String VERIFIED_SINGLE_FOOD_SEARCH_TAG =
            "verified-single-food-search-input";
    private static final String VERIFIED_SINGLE_FOOD_RESULTS_TAG =
            "verified-single-food-results";
    private static final int SAVED_DINING_OUT_OPTION_RESULT_LIMIT = 20;

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
    private String draftDiningOutSourceNamespace = "";
    private String draftDiningOutSourceLocationCode = "";
    private String draftDiningOutRestaurantMenuId = "";
    private String draftDiningOutCatalogProductId = "";
    private final List<DiningOutOptionDraft> draftDiningOutOptions = new ArrayList<>();
    private String draftDiningOutCarbs = "";
    private String draftDiningOutProtein = "";
    private String draftDiningOutFat = "";
    private String draftDiningOutCalories = "";
    private String draftDiningOutSodium = "";
    private String draftDiningOutSugars = "";
    private String draftDiningOutSaturatedFat = "";
    private String draftDiningOutNominalServings = "1";
    private String draftDiningOutDinerCount = "1";
    private String draftDiningOutConsumedPercent = "";

    private Button mealTimeButton;
    private EditText menuNameInput;
    private TextView diningOutSelectionSummary;
    private EditText diningOutStoreInput;
    private EditText diningOutBranchInput;
    private EditText diningOutMenuInput;
    private EditText diningOutNominalServingsInput;
    private EditText diningOutDinerCountInput;
    private EditText diningOutConsumedPercentInput;
    private LinearLayout diningOutOptionsContainer;
    private final List<EditText> diningOutOptionInputs = new ArrayList<>();
    private final List<Button> diningOutOptionGroupInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionCaloriesInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionProteinInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionCarbsInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionFatInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionConsumedPercentInputs = new ArrayList<>();
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
        catalogMode = CATALOG_MODE_SINGLE_FOOD;
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
        if (!entry.isDiningOut()) {
            actions.addView(ui.textAction("수정", FitnessUi.COLOR_TERTIARY,
                    () -> showMealMenuEditDialog(entry)));
        }
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
            FitnessRepository.DiningOutConsumptionEntry consumption =
                    repository().diningOutConsumptionForRecord(entry.id);
            if (consumption != null) {
                body.addView(ui.caption("공유 섭취", FitnessUi.COLOR_TERTIARY),
                        ui.fullWidthParams(ui.dp(12)));
                body.addView(ui.text(
                        "새 계산 계약 · " + consumption.dinerCount + "명 · 내 몫 "
                                + Math.round(consumption.percentage()) + "% · "
                                + (consumption.isEqualSplit() ? "균등 추정" : "직접 입력"),
                        14,
                        FitnessUi.COLOR_TEXT,
                        true
                ), ui.fullWidthParams(ui.dp(2)));
            }
            List<FitnessRepository.MealItemEntry> menuItems = repository().mealItemsForRecord(entry.id);
            if (!menuItems.isEmpty()) {
                List<FitnessRepository.MealComponentEntry> options =
                        repository().mealComponentsForItem(menuItems.get(0).id);
                if (!options.isEmpty()) {
                    body.addView(ui.caption("메뉴 옵션", FitnessUi.COLOR_TERTIARY),
                            ui.fullWidthParams(ui.dp(12)));
                    for (FitnessRepository.MealComponentEntry option : options) {
                        String optionLabel = "· " + option.label();
                        if (option.hasExplicitConsumedFraction()) {
                            optionLabel += " · 내 섭취 "
                                    + Math.round(option.percentage()) + "%";
                        }
                        body.addView(ui.text(
                                optionLabel,
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
                if (consumption != null && nutrition != null) {
                    nutrition = nutrition.scaled(consumption.consumedFraction);
                }
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
                body.addView(ui.caption("먹은 메뉴 " + menus.size() + "개", FitnessUi.COLOR_TERTIARY),
                        ui.fullWidthParams(ui.dp(10)));
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
        if (!entry.isDiningOut() && !repository().mealItemsForRecord(entry.id).isEmpty()) {
            builder.setPositiveButton("메뉴 수정", (dialog, which) ->
                    showMealMenuEditDialog(entry));
        }
        builder.show();
    }

    /** Allows a recorded meal's top-level menu names and quantities to be corrected in place. */
    private void showMealMenuEditDialog(FitnessRepository.MealEntry entry) {
        List<FitnessRepository.MealItemEntry> menus = repository().mealItemsForRecord(entry.id);
        if (menus.isEmpty()) {
            host.toast("이전 형식의 기록이라 수정할 메뉴가 없습니다.");
            return;
        }

        FitnessUi ui = ui();
        LinearLayout body = ui.form();
        List<EditText> nameInputs = new ArrayList<>();
        List<EditText> quantityInputs = new ArrayList<>();
        for (int index = 0; index < menus.size(); index++) {
            FitnessRepository.MealItemEntry menu = menus.get(index);
            body.addView(ui.caption("메뉴 " + (index + 1), FitnessUi.COLOR_TERTIARY),
                    ui.fullWidthParams(ui.dp(6)));
            EditText nameInput = ui.input("메뉴명", menu.foodName);
            nameInput.setSingleLine(true);
            nameInput.setContentDescription("메뉴 " + (index + 1) + " 이름");
            nameInputs.add(nameInput);
            body.addView(ui.labeledFieldColumn("메뉴명", nameInput),
                    ui.fullWidthParams(ui.dp(4)));

            EditText quantityInput = ui.decimalInput(
                    menu.unit,
                    NutritionCalculator.trim(menu.quantity)
            );
            quantityInput.setContentDescription("메뉴 " + (index + 1) + " 섭취량");
            quantityInputs.add(quantityInput);
            body.addView(ui.labeledFieldColumn("섭취량 (" + menu.unit + ")", quantityInput),
                    ui.fullWidthParams(ui.dp(8)));
        }
        body.addView(ui.text(
                "메뉴명과 섭취량을 수정하면 기록 당시 영양 스냅샷도 섭취량에 맞춰 갱신됩니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(8)));

        ScrollView scroll = new ScrollView(host.activity());
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        AlertDialog dialog = new AlertDialog.Builder(host.activity())
                .setTitle("끼니 메뉴 수정")
                .setView(scroll)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    List<FitnessRepository.MealMenuEdit> edits = new ArrayList<>();
                    try {
                        for (int index = 0; index < menus.size(); index++) {
                            String name = FitnessUi.inputText(nameInputs.get(index)).trim();
                            if (name.isEmpty()) {
                                throw new IllegalArgumentException(
                                        "메뉴 " + (index + 1) + "의 이름을 입력하세요."
                                );
                            }
                            String quantityText = FitnessUi.inputText(quantityInputs.get(index)).trim();
                            double quantity;
                            try {
                                quantity = Double.parseDouble(quantityText);
                            } catch (NumberFormatException error) {
                                throw new IllegalArgumentException(
                                        "메뉴 " + (index + 1) + "의 섭취량을 숫자로 입력하세요."
                                );
                            }
                            if (!Double.isFinite(quantity) || quantity <= 0d) {
                                throw new IllegalArgumentException(
                                        "메뉴 " + (index + 1) + "의 섭취량은 0보다 커야 합니다."
                                );
                            }
                            edits.add(new FitnessRepository.MealMenuEdit(
                                    menus.get(index).id,
                                    name,
                                    quantity
                            ));
                        }
                        if (!repository().updateMealMenus(entry.id, edits)) {
                            host.toast("끼니 메뉴를 수정하지 못했습니다.");
                            return;
                        }
                        dialog.dismiss();
                        host.toast("끼니 메뉴를 수정했습니다.");
                        host.rerender();
                    } catch (IllegalArgumentException error) {
                        host.toast(error.getMessage());
                    }
                }));
        dialog.show();
    }

    private View mealWorkspace() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        LinearLayout workspace = new LinearLayout(host.activity());
        workspace.setOrientation(LinearLayout.VERTICAL);
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
        }
        workspace.addView(card, ui.fullWidthParams(0));
        if (mealEntryMode == MEAL_ENTRY_MODE_FOOD) {
            LinearLayout catalogSection = new LinearLayout(host.activity());
            catalogSection.setOrientation(LinearLayout.VERTICAL);
            appendCatalogSection(catalogSection);
            workspace.addView(catalogSection, ui.fullWidthParams(ui.dp(12)));
        }
        return workspace;
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
                "PT 검색으로 식당·지점·메뉴를 채우거나 직접 등록할 수 있습니다. 검색 결과의 값도 수정할 수 있습니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(4)));

        Button selectPriceTraceDiningOut = ui.button(
                "PT 검색 · 식당·지점·메뉴 선택",
                true,
                v -> showPriceTraceDiningOutPicker()
        );
        selectPriceTraceDiningOut.setContentDescription("PT 식당·지점·메뉴 검색");
        Button directDiningOut = ui.button(
                "직접 등록하기",
                false,
                v -> {
                    syncDraftFromViews();
                    clearDiningOutPriceTraceIdentity();
                    host.toast("식당·지점·메뉴를 직접 입력하세요.");
                    host.rerender();
                }
        );
        directDiningOut.setContentDescription("외식 직접 등록");
        form.addView(
                ui.buttonRow(selectPriceTraceDiningOut, directDiningOut),
                ui.fullWidthParams(ui.dp(6))
        );
        Button reuseDiningOut = ui.button(
                "내 저장 외식 메뉴 불러오기",
                false,
                v -> showSavedDiningOutPicker()
        );
        reuseDiningOut.setContentDescription("내 저장 외식 메뉴 불러오기");
        form.addView(reuseDiningOut, ui.fullWidthParams(ui.dp(6)));

        diningOutStoreInput = ui.input("가게 명", draftDiningOutStoreName);
        diningOutStoreInput.setSingleLine(true);
        diningOutStoreInput.setContentDescription("가게 명");
        diningOutBranchInput = ui.input("지점 (선택)", draftDiningOutBranchName);
        diningOutBranchInput.setSingleLine(true);
        diningOutBranchInput.setContentDescription("지점");
        LinearLayout restaurantFields = ui.tileRow();
        restaurantFields.addView(
                ui.labeledFieldColumn("가게 명", diningOutStoreInput),
                ui.fieldCellParams(true)
        );
        restaurantFields.addView(
                ui.labeledFieldColumn("지점 (선택)", diningOutBranchInput),
                ui.fieldCellParams(false)
        );
        form.addView(restaurantFields, ui.fullWidthParams(ui.dp(8)));

        diningOutMenuInput = ui.input("먹은 메뉴", draftDiningOutMenuName);
        diningOutMenuInput.setSingleLine(true);
        diningOutMenuInput.setContentDescription("먹은 메뉴");
        form.addView(
                ui.labeledFieldColumn("먹은 메뉴", diningOutMenuInput),
                ui.fullWidthParams(ui.dp(8))
        );

        diningOutNominalServingsInput = ui.decimalInput(
                "인분",
                draftDiningOutNominalServings
        );
        diningOutNominalServingsInput.setContentDescription("메뉴 제공 인분");
        diningOutDinerCountInput = ui.numberInput("명", draftDiningOutDinerCount);
        diningOutDinerCountInput.setContentDescription("함께 먹은 인원");
        LinearLayout sharingRow = ui.tileRow();
        sharingRow.addView(
                ui.labeledFieldColumn("메뉴 제공 인분", diningOutNominalServingsInput),
                ui.fieldCellParams(true)
        );
        sharingRow.addView(
                ui.labeledFieldColumn("함께 먹은 인원", diningOutDinerCountInput),
                ui.fieldCellParams(false)
        );
        form.addView(sharingRow, ui.fullWidthParams(ui.dp(8)));

        diningOutConsumedPercentInput = ui.decimalInput(
                "%",
                draftDiningOutConsumedPercent
        );
        diningOutConsumedPercentInput.setContentDescription("내 섭취 비율");
        form.addView(
                ui.labeledFieldColumn("내 섭취 비율 (선택, %)", diningOutConsumedPercentInput),
                ui.fullWidthParams(ui.dp(8))
        );
        form.addView(ui.text(
                "메뉴 제공 인분과 함께 먹은 인원은 다른 값입니다. 내 섭취 비율을 비워두면 균등 분배합니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(8)));

        LinearLayout selectionCard = ui.card();
        selectionCard.addView(ui.text("식당 입력 결과", 14, FitnessUi.COLOR_TEXT, true),
                ui.fullWidthParams(ui.dp(2)));
        diningOutSelectionSummary = ui.text("", 13, FitnessUi.COLOR_TEXT, false);
        diningOutSelectionSummary.setContentDescription("PT 선택 결과");
        selectionCard.addView(diningOutSelectionSummary, ui.fullWidthParams(ui.dp(2)));
        form.addView(selectionCard, ui.fullWidthParams(ui.dp(8)));
        updateDiningOutSelectionSummary();

        form.addView(diningOutOptionsSection(), ui.fullWidthParams(ui.dp(12)));

        form.addView(ui.text(
                "메인 메뉴의 영양값은 해당 메뉴 전체 기준으로 입력하세요. 추가 옵션은 옵션별 영양값과 내 섭취 비율을 따로 입력합니다. 탄단지는 필수이며, 칼로리·나트륨·당류·포화지방은 선택 입력입니다.",
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
        header.addView(ui.textAction("템플릿 불러오기", FitnessUi.COLOR_TERTIARY, () -> {
            showDiningOutTemplatePicker();
        }));
        header.addView(ui.textAction("옵션 추가", FitnessUi.COLOR_TERTIARY, () -> {
            showDiningOutOptionPicker(-1);
        }));
        section.addView(header);

        section.addView(ui.text(
                "옵션마다 고정 구성 그룹과 내 섭취 비율을 지정할 수 있습니다. 옵션 비율은 기본 100%이며, 함께 나눠 먹은 옵션은 직접 수정하세요.",
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

    private void showDiningOutTemplatePicker() {
        syncDraftFromViews();
        FitnessUi ui = ui();
        List<CompositionTemplate> templates = repository().compositionTemplates()
                .list(CompositionTemplate.KIND_DINING_OUT);
        if (templates.isEmpty()) {
            host.toast("저장된 외식 구성 템플릿이 없습니다. 메뉴 저장 후 생성됩니다.");
            return;
        }
        LinearLayout rows = new LinearLayout(host.activity());
        rows.setOrientation(LinearLayout.VERTICAL);
        for (CompositionTemplate template : templates) {
            Button templateButton = ui.button(
                    template.name + " · 그룹 " + template.groups.size(),
                    false,
                    ignored -> showDiningOutTemplateSelection(template)
            );
            templateButton.setContentDescription(template.name + " 외식 템플릿 선택");
            rows.addView(templateButton, ui.fullWidthParams(ui.dp(5)));
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("외식 구성 템플릿")
                .setMessage("고정 메뉴와 그룹별 선택지를 불러옵니다.")
                .setView(rows)
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showDiningOutTemplateSelection(CompositionTemplate template) {
        FitnessUi ui = ui();
        Map<String, List<CompositionMember>> selectedByGroup = new LinkedHashMap<>();
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(8));
        panel.addView(ui.text(
                "그룹별로 이번에 먹은 구성원을 선택하세요. 선택 결과만 기록되고 템플릿 정의는 바뀌지 않습니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(8)));
        for (CompositionGroup group : template.groups) {
            panel.addView(ui.text(
                    group.label + " (" + group.groupType + ", " + group.key + ") · "
                            + group.selectionMode,
                    13,
                    FitnessUi.COLOR_TEXT,
                    true
            ), ui.fullWidthParams(ui.dp(5)));
            if (group.members.isEmpty()) {
                panel.addView(ui.text(
                        "구성원이 없습니다.",
                        12,
                        FitnessUi.COLOR_TERTIARY,
                        false
                ), ui.fullWidthParams(ui.dp(3)));
                continue;
            }
            for (CompositionMember member : group.members) {
                Button memberButton = ui.button(
                        member.name + " · " + Math.round(member.profile.calories()) + "kcal",
                        false,
                        null
                );
                memberButton.setOnClickListener(ignored -> {
                    List<CompositionMember> selected = selectedByGroup.get(group.key);
                    if (selected == null) {
                        selected = new ArrayList<>();
                        selectedByGroup.put(group.key, selected);
                    }
                    if (CompositionGroup.MODE_EXACTLY_ONE.equals(group.selectionMode)
                            || CompositionGroup.MODE_ZERO_OR_ONE.equals(group.selectionMode)) {
                        selected.clear();
                        selected.add(member);
                        memberButton.setText("✓ " + member.name);
                    } else if (selected.contains(member)) {
                        selected.remove(member);
                        memberButton.setText(member.name + " · "
                                + Math.round(member.profile.calories()) + "kcal");
                    } else {
                        selected.add(member);
                        memberButton.setText("✓ " + member.name);
                    }
                });
                panel.addView(memberButton, ui.fullWidthParams(ui.dp(3)));
            }
        }
        new AlertDialog.Builder(host.activity())
                .setTitle(template.name)
                .setView(panel)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", (dialog, which) -> {
                    for (CompositionGroup group : template.groups) {
                        List<CompositionMember> selected = selectedByGroup.get(group.key);
                        int count = selected == null ? 0 : selected.size();
                        if (count < group.minSelected || count > group.maxSelected) {
                            host.toast(group.label + " 선택을 확인하세요.");
                            return;
                        }
                    }
                    applyDiningOutTemplate(template, selectedByGroup);
                })
                .show();
    }

    private void applyDiningOutTemplate(
            CompositionTemplate template,
            Map<String, List<CompositionMember>> selectedByGroup
    ) {
        NutritionFood rootFood = template.rootFoodId == null
                ? null
                : host.nutritionCatalogRepository().findFoodById(template.rootFoodId);
        if (rootFood != null) {
            draftDiningOutStoreName = rootFood.brand == null ? "" : rootFood.brand;
            draftDiningOutMenuName = rootFood.name;
            applyDiningOutNutrition(rootFood.profile);
        } else {
            String[] parts = template.name.split(" · ", 2);
            draftDiningOutStoreName = parts.length > 1 ? parts[0] : template.name;
            draftDiningOutMenuName = parts.length > 1 ? parts[1] : template.name;
            // A legacy template without a root catalog row has no trusted base profile.
            // Do not carry a previous meal's entered nutrition into this new selection.
            applyDiningOutNutrition(NutritionProfile.empty());
        }
        String identitySourceReference = rootFood == null
                ? template.sourceReference
                : rootFood.sourceReference;
        applyDiningOutTemplateIdentity(identitySourceReference);
        draftDiningOutOptions.clear();
        for (CompositionGroup group : template.groups) {
            List<CompositionMember> selected = selectedByGroup.get(group.key);
            if (selected == null) {
                continue;
            }
            for (CompositionMember member : selected) {
                DiningOutOptionDraft draft = new DiningOutOptionDraft();
                draft.groupType = group.groupType;
                draft.groupKey = group.key;
                draft.name = member.name;
                draft.catalogFoodId = member.nutritionFoodId == null
                        ? "" : member.nutritionFoodId;
                draft.sourceReference = compositionTemplateSourceReference(
                        member.sourceReference,
                        template
                );
                draft.memberId = member.id;
                draft.calories = knownNumber(member.profile, NutritionProfile.CALORIES_KCAL);
                draft.protein = knownNumber(member.profile, NutritionProfile.PROTEIN_GRAMS);
                draft.carbs = knownNumber(member.profile, NutritionProfile.CARBS_GRAMS);
                draft.fat = knownNumber(member.profile, NutritionProfile.FAT_GRAMS);
                draftDiningOutOptions.add(draft);
            }
        }
        updateDiningOutInputViews();
        updateDiningOutSelectionSummary();
        host.rerender();
        host.toast("템플릿 선택 결과를 외식 입력에 적용했습니다.");
    }

    private String compositionTemplateSourceReference(
            String sourceReference,
            CompositionTemplate template
    ) {
        try {
            JSONObject source = sourceReference == null || sourceReference.trim().isEmpty()
                    ? new JSONObject()
                    : new JSONObject(sourceReference);
            source.put("composition_contract", CompositionTemplate.CONTRACT_VERSION);
            source.put("composition_template_id", template.id);
            source.put("composition_template_revision", template.revision);
            return source.toString();
        } catch (Exception ignored) {
            return "{\"composition_contract\":\"composition-template.v1\","
                    + "\"composition_template_id\":\"" + template.id + "\","
                    + "\"composition_template_revision\":" + template.revision + "}";
        }
    }

    private void applyDiningOutTemplateIdentity(String sourceReference) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            clearDiningOutPriceTraceIdentity();
            return;
        }
        try {
            JSONObject source = new JSONObject(sourceReference);
            String restaurantId = jsonValue(source, "restaurant_id");
            String locationId = jsonValue(source, "restaurant_location_id");
            String menuId = jsonValue(source, "restaurant_menu_id");
            String productId = jsonValue(source, "catalog_product_id");
            draftDiningOutBranchName = jsonValue(source, "branch_name");
            if (restaurantId.isEmpty() || locationId.isEmpty()
                    || menuId.isEmpty() || productId.isEmpty()) {
                clearDiningOutPriceTraceIdentity();
                return;
            }
            draftDiningOutRestaurantId = restaurantId;
            draftDiningOutRestaurantLocationId = locationId;
            draftDiningOutSourceNamespace = jsonValue(source, "namespace");
            if (draftDiningOutSourceNamespace.isEmpty()) {
                draftDiningOutSourceNamespace = jsonValue(source, "source_namespace");
            }
            draftDiningOutSourceLocationCode = jsonValue(source, "source_location_code");
            draftDiningOutRestaurantMenuId = menuId;
            draftDiningOutCatalogProductId = productId;
        } catch (Exception ignored) {
            clearDiningOutPriceTraceIdentity();
        }
    }

    private String jsonValue(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return "";
        }
        return object.optString(key, "").trim();
    }

    private String knownNumber(NutritionProfile profile, String key) {
        Double value = profile == null ? null : profile.value(key);
        return value == null ? "" : NutritionCalculator.trim(value);
    }

    private void showSavedDiningOutPicker() {
        syncDraftFromViews();
        FitnessUi ui = ui();
        List<NutritionFood> savedMenus = host.nutritionCatalogRepository()
                .savedDiningOutMenus();
        if (savedMenus.isEmpty()) {
            host.toast("저장된 외식 메뉴가 없습니다. 메뉴를 저장한 뒤 다시 시도하세요.");
            return;
        }

        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(8));
        panel.addView(ui.text(
                "저장 메뉴의 NutritionFood.profile을 이번 기록의 기본 영양성분으로 사용합니다. 이번에 선택한 옵션만 별도로 합산됩니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(8)));

        final AlertDialog[] dialogHolder = new AlertDialog[1];
        if (!savedMenus.isEmpty()) {
            panel.addView(ui.text("내 저장 외식 메뉴", 14, FitnessUi.COLOR_TEXT, true),
                    ui.fullWidthParams(ui.dp(5)));
            for (NutritionFood menu : savedMenus) {
                Button menuButton = ui.button(
                        savedDiningOutMenuLabel(menu),
                        false,
                        ignored -> {
                            applySavedDiningOutMenu(menu);
                            if (dialogHolder[0] != null) {
                                dialogHolder[0].dismiss();
                            }
                        }
                );
                menuButton.setContentDescription(menu.displayName() + " 저장 외식 메뉴 불러오기");
                panel.addView(menuButton, ui.fullWidthParams(ui.dp(4)));
            }
        }

        ScrollView scroll = new ScrollView(host.activity());
        scroll.setFillViewport(true);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        AlertDialog dialog = new AlertDialog.Builder(host.activity())
                .setTitle("내 외식 불러오기")
                .setView(scroll)
                .setNegativeButton("취소", null)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private String savedDiningOutMenuLabel(NutritionFood menu) {
        String storeName = menu.brand == null ? "가게 미기록" : menu.brand;
        return storeName + " · " + menu.name + "\n" + menu.nutritionLabel();
    }

    private void applySavedDiningOutMenu(NutritionFood menu) {
        draftDiningOutStoreName = menu.brand == null ? "" : menu.brand;
        draftDiningOutMenuName = menu.name;
        clearDiningOutPriceTraceIdentity();
        applyDiningOutTemplateIdentity(menu.sourceReference);
        draftDiningOutOptions.clear();
        applyDiningOutNutrition(menu.profile);
        updateDiningOutInputViews();
        updateDiningOutSelectionSummary();
        host.toast("저장한 외식 메뉴를 불러왔습니다. 섭취량을 확인한 뒤 기록하세요.");
        host.rerender();
    }

    private void applyDiningOutNutrition(NutritionProfile profile) {
        draftDiningOutCalories = knownNumber(profile, NutritionProfile.CALORIES_KCAL);
        draftDiningOutProtein = knownNumber(profile, NutritionProfile.PROTEIN_GRAMS);
        draftDiningOutCarbs = knownNumber(profile, NutritionProfile.CARBS_GRAMS);
        draftDiningOutFat = knownNumber(profile, NutritionProfile.FAT_GRAMS);
        draftDiningOutSodium = knownNumber(profile, NutritionProfile.SODIUM_MG);
        draftDiningOutSugars = knownNumber(profile, NutritionProfile.SUGARS_GRAMS);
        draftDiningOutSaturatedFat = knownNumber(
                profile,
                NutritionProfile.SATURATED_FAT_GRAMS
        );
    }

    private void showDiningOutOptionPicker(int replacementIndex) {
        syncDraftFromViews();
        FitnessUi ui = ui();
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(ui.dp(16), ui.dp(4), ui.dp(16), ui.dp(8));

        String storeName = draftDiningOutStoreName.trim();
        panel.addView(ui.text(
                storeName.isEmpty()
                        ? "식당명을 입력하거나 PT 검색으로 식당을 선택하면 저장 옵션을 검색할 수 있습니다."
                        : storeName + "의 저장 옵션을 검색합니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(8)));
        EditText query = ui.searchField("저장 옵션 이름 검색");
        query.setSingleLine(true);
        query.setContentDescription("외식 저장 옵션 검색");
        panel.addView(query, ui.fullWidthParams(ui.dp(6)));
        Button search = ui.button("검색", true, null);
        panel.addView(search, ui.fullWidthParams(ui.dp(6)));
        TextView status = ui.text(
                storeName.isEmpty()
                        ? "식당명을 입력하거나 PT 검색으로 식당을 선택하세요."
                        : "저장 옵션을 검색하는 중입니다.",
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        panel.addView(status, ui.fullWidthParams(ui.dp(6)));
        LinearLayout results = new LinearLayout(host.activity());
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView resultScroll = new ScrollView(host.activity());
        resultScroll.setFillViewport(true);
        resultScroll.addView(results, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(resultScroll, ui.fullWidthParams(ui.dp(220)));

        AlertDialog dialog = new AlertDialog.Builder(host.activity())
                .setTitle(replacementIndex >= 0 ? "외식 옵션 변경" : "외식 옵션 추가")
                .setView(panel)
                .setNegativeButton("취소", null)
                .create();
        Button manualInput = ui.button(
                replacementIndex >= 0 ? "직접 입력으로 변경" : "저장 옵션 없이 직접 입력",
                false,
                null
        );
        manualInput.setOnClickListener(v -> {
            DiningOutOptionDraft manualDraft = new DiningOutOptionDraft();
            if (replacementIndex >= 0 && replacementIndex < draftDiningOutOptions.size()) {
                draftDiningOutOptions.set(replacementIndex, manualDraft);
            } else {
                draftDiningOutOptions.add(manualDraft);
            }
            dialog.dismiss();
            host.rerender();
        });
        panel.addView(manualInput, ui.fullWidthParams(ui.dp(6)));

        search.setOnClickListener(v -> {
            results.removeAllViews();
            String currentStoreName = draftDiningOutStoreName.trim();
            if (currentStoreName.isEmpty()) {
                status.setText("식당명을 입력하거나 PT 검색으로 식당을 선택하세요. 저장 옵션 없이 직접 입력할 수 있습니다.");
                return;
            }

            DiningOutIdentity identity;
            try {
                identity = selectedDiningOutIdentity();
            } catch (IllegalArgumentException error) {
                status.setText(error.getMessage());
                return;
            }
            try {
                List<NutritionFood> options = host.nutritionCatalogRepository()
                        .savedDiningOutOptions(
                                currentStoreName,
                                identity,
                                FitnessUi.inputText(query),
                                SAVED_DINING_OUT_OPTION_RESULT_LIMIT
                        );
                if (options.isEmpty()) {
                    status.setText("일치하는 저장 옵션이 없습니다. 직접 입력할 수 있습니다.");
                    return;
                }
                status.setText("저장 옵션 " + options.size() + "개");
                for (NutritionFood option : options) {
                    Button optionButton = ui.button(
                            option.name + " · " + option.nutritionLabel(),
                            false,
                            ignored -> selectDiningOutOption(
                                    replacementIndex,
                                    option,
                                    dialog
                            )
                    );
                    optionButton.setContentDescription(option.name + " 저장 옵션 선택");
                    results.addView(optionButton, ui.fullWidthParams(ui.dp(4)));
                }
            } catch (Exception error) {
                status.setText("저장 옵션을 불러오지 못했습니다. 직접 입력할 수 있습니다.");
            }
        });

        dialog.setOnShowListener(ignored -> {
            query.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                );
            }
            // Search is the default mode and an empty query lists this store's saved options.
            search.performClick();
        });
        dialog.show();
    }

    private void selectDiningOutOption(
            int replacementIndex,
            NutritionFood food,
            AlertDialog dialog
    ) {
        DiningOutOptionDraft selectedDraft = new DiningOutOptionDraft();
        selectedDraft.groupType = savedDiningOutOptionGroupType(food.sourceReference);
        selectedDraft.groupKey = savedDiningOutOptionGroupKey(food.sourceReference);
        selectedDraft.name = food.name;
        selectedDraft.catalogFoodId = food.id;
        selectedDraft.sourceReference = food.sourceReference;
        selectedDraft.calories = NutritionCalculator.trim(food.calories);
        selectedDraft.protein = NutritionCalculator.trim(food.proteinGrams);
        selectedDraft.carbs = NutritionCalculator.trim(food.carbsGrams);
        selectedDraft.fat = NutritionCalculator.trim(food.fatGrams);
        if (replacementIndex >= 0 && replacementIndex < draftDiningOutOptions.size()) {
            draftDiningOutOptions.set(replacementIndex, selectedDraft);
        } else {
            draftDiningOutOptions.add(selectedDraft);
        }
        dialog.dismiss();
        host.rerender();
    }

    private String savedDiningOutOptionGroupType(String sourceReference) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return CompositionGroupType.OTHER.value();
        }
        try {
            JSONObject source = new JSONObject(sourceReference);
            String type = jsonValue(source, "composition_group_type");
            if (!type.isEmpty()) {
                return CompositionGroupType.normalize(type);
            }
            return CompositionGroupType.normalize(jsonValue(source, "composition_group_label"));
        } catch (Exception ignored) {
            return CompositionGroupType.OTHER.value();
        }
    }

    private String savedDiningOutOptionGroupKey(String sourceReference) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject source = new JSONObject(sourceReference);
            return jsonValue(source, "composition_group_key");
        } catch (Exception ignored) {
            return "";
        }
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
                    List<Button> menuButtons = new ArrayList<>();
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
                            for (int index = 0; index < menuButtons.size()
                                    && index < value.menus.size(); index++) {
                                String menuLabel = MealEntryPolicy.previewDiningOutTitle(
                                        value.restaurantName,
                                        location.branchName,
                                        value.menus.get(index).menuName
                                );
                                menuButtons.get(index).setText(menuLabel);
                                menuButtons.get(index).setContentDescription(menuLabel + " 선택");
                            }
                        });
                        locationRows.addView(locationButton, ui.fullWidthParams(ui.dp(4)));
                    }
                    for (RestaurantMenuReadV1Client.RestaurantMenu menu : value.menus) {
                        String menuLabel = MealEntryPolicy.previewDiningOutTitle(
                                value.restaurantName,
                                "지점 미선택",
                                menu.menuName
                        );
                        Button menuButton = ui.button(menuLabel, false, ignored -> {
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
                            draftDiningOutSourceNamespace = location.sourceNamespace == null
                                    ? "" : location.sourceNamespace;
                            draftDiningOutSourceLocationCode = location.sourceLocationCode == null
                                    ? "" : location.sourceLocationCode;
                            draftDiningOutRestaurantMenuId = menu.restaurantMenuId;
                            draftDiningOutCatalogProductId = menu.catalogProductId;
                            updateDiningOutInputViews();
                            updateDiningOutSelectionSummary();
                            host.toast("PriceTrace 식당·지점·메뉴를 정확히 연결했습니다.");
                            dialog.dismiss();
                            host.rerender();
                        });
                        menuButton.setContentDescription(menuLabel + " 선택");
                        menuButtons.add(menuButton);
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

    private void updateDiningOutInputViews() {
        if (diningOutStoreInput != null) {
            diningOutStoreInput.setText(draftDiningOutStoreName);
        }
        if (diningOutBranchInput != null) {
            diningOutBranchInput.setText(draftDiningOutBranchName);
        }
        if (diningOutMenuInput != null) {
            diningOutMenuInput.setText(draftDiningOutMenuName);
        }
        if (diningOutNominalServingsInput != null) {
            diningOutNominalServingsInput.setText(draftDiningOutNominalServings);
        }
        if (diningOutDinerCountInput != null) {
            diningOutDinerCountInput.setText(draftDiningOutDinerCount);
        }
        if (diningOutConsumedPercentInput != null) {
            diningOutConsumedPercentInput.setText(draftDiningOutConsumedPercent);
        }
    }

    private void renderDiningOutOptionRows() {
        if (diningOutOptionsContainer == null) {
            return;
        }
        FitnessUi ui = ui();
        diningOutOptionsContainer.removeAllViews();
        diningOutOptionInputs.clear();
        diningOutOptionGroupInputs.clear();
        diningOutOptionCaloriesInputs.clear();
        diningOutOptionProteinInputs.clear();
        diningOutOptionCarbsInputs.clear();
        diningOutOptionFatInputs.clear();
        diningOutOptionConsumedPercentInputs.clear();
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
            Button group = ui.button(
                    "구성 그룹: " + CompositionGroupType.labelOf(draft.groupType),
                    false,
                    ignored -> showDiningOutGroupTypePicker(optionIndex)
            );
            group.setContentDescription("외식 옵션 그룹 " + (index + 1));
            diningOutOptionGroupInputs.add(group);
            row.addView(group, ui.fullWidthParams(ui.dp(2)));
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
            EditText carbs = ui.decimalInput("g", draft.carbs);
            EditText protein = ui.decimalInput("g", draft.protein);
            EditText fat = ui.decimalInput("g", draft.fat);
            diningOutOptionCaloriesInputs.add(calories);
            diningOutOptionProteinInputs.add(protein);
            diningOutOptionCarbsInputs.add(carbs);
            diningOutOptionFatInputs.add(fat);
            nutritionRow.addView(ui.labeledFieldColumn("칼로리 (kcal)", calories), ui.fieldCellParams(true));
            nutritionRow.addView(ui.labeledFieldColumn("탄수화물 (g)", carbs), ui.fieldCellParams(false));
            nutritionRow.addView(ui.labeledFieldColumn("단백질 (g)", protein), ui.fieldCellParams(false));
            nutritionRow.addView(ui.labeledFieldColumn("지방 (g)", fat), ui.fieldCellParams(false));
            row.addView(nutritionRow, ui.fullWidthParams(ui.dp(2)));
            EditText consumedPercent = ui.decimalInput("%", draft.consumedPercent);
            consumedPercent.setContentDescription("외식 옵션 내 섭취 비율 " + (index + 1));
            diningOutOptionConsumedPercentInputs.add(consumedPercent);
            row.addView(
                    ui.labeledFieldColumn("내 섭취 비율 (%)", consumedPercent),
                    ui.fullWidthParams(ui.dp(2))
            );
            LinearLayout actions = new LinearLayout(host.activity());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(ui.textAction("저장 옵션 검색", FitnessUi.COLOR_TERTIARY, () -> {
                syncDraftFromViews();
                showDiningOutOptionPicker(optionIndex);
            }));
            actions.addView(ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE, () -> {
                syncDraftFromViews();
                if (optionIndex < draftDiningOutOptions.size()) {
                    draftDiningOutOptions.remove(optionIndex);
                }
                host.rerender();
            }));
            row.addView(actions, ui.fullWidthParams(ui.dp(2)));
            diningOutOptionsContainer.addView(row, ui.fullWidthParams(ui.dp(6)));
        }
    }

    private void showDiningOutGroupTypePicker(int optionIndex) {
        syncDraftFromViews();
        if (optionIndex < 0 || optionIndex >= draftDiningOutOptions.size()) {
            return;
        }
        CompositionGroupType[] types = CompositionGroupType.values();
        String[] labels = CompositionGroupType.labels();
        new AlertDialog.Builder(host.activity())
                .setTitle("외식 구성 그룹")
                .setItems(labels, (dialog, which) -> {
                    if (which >= 0 && which < types.length
                            && optionIndex < draftDiningOutOptions.size()) {
                        DiningOutOptionDraft draft = draftDiningOutOptions.get(optionIndex);
                        draft.groupType = types[which].value();
                        // A changed type starts a new generated group key. Template-provided
                        // keys remain intact when a member is merely reloaded.
                        draft.groupKey = "";
                        host.rerender();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private List<DiningOutOption> parsedDiningOutOptions() {
        List<DiningOutOption> options = new ArrayList<>();
        Map<String, String> generatedGroupKeys = new LinkedHashMap<>();
        for (DiningOutOptionDraft draft : draftDiningOutOptions) {
            String name = draft.name == null ? "" : draft.name.trim();
            if (name.isEmpty()) {
                continue;
            }
            Integer calories = MealEntryPolicy.optionalDiningOutCalories(draft.calories);
            Double protein = MealEntryPolicy.optionalDiningOutMacro(draft.protein, "옵션 단백질");
            Double carbs = MealEntryPolicy.optionalDiningOutMacro(draft.carbs, "옵션 탄수화물");
            Double fat = MealEntryPolicy.optionalDiningOutMacro(draft.fat, "옵션 지방");
            double consumedFraction = diningOutOptionConsumedFractionValue(draft.consumedPercent);
            boolean hasNutrition = calories != null || protein != null || carbs != null || fat != null;
            String groupType = CompositionGroupType.normalize(draft.groupType);
            String groupLabel = CompositionGroupType.labelOf(groupType);
            String groupKey = emptyToNull(draft.groupKey);
            if (groupKey == null) {
                groupKey = generatedGroupKeys.get(groupType);
                if (groupKey == null) {
                    groupKey = groupType + "_1";
                    generatedGroupKeys.put(groupType, groupKey);
                }
            }
            if (!hasNutrition) {
                options.add(DiningOutOption.grouped(
                        name,
                        NutritionProfile.empty(),
                        emptyToNull(draft.catalogFoodId),
                        emptyToNull(draft.sourceReference),
                        groupKey,
                        groupType,
                        groupLabel,
                        DiningOutOption.DEFAULT_ROLE,
                        emptyToNull(draft.memberId),
                        consumedFraction
                ));
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
            options.add(DiningOutOption.grouped(
                    name,
                    NutritionProfile.ofMacros(resolvedCalories, protein, carbs, fat),
                    emptyToNull(draft.catalogFoodId),
                    emptyToNull(draft.sourceReference),
                    groupKey,
                    groupType,
                    groupLabel,
                    DiningOutOption.DEFAULT_ROLE,
                    emptyToNull(draft.memberId),
                    consumedFraction
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
            saved.add(DiningOutOption.grouped(
                    food.name,
                    food.profile,
                    food.id,
                    food.sourceReference,
                    option.groupKey,
                    option.groupType,
                    option.groupLabel,
                    option.role,
                    option.memberId,
                    option.consumedFraction
            ));
        }
        return saved;
    }

    /** Saves the current dining-out definition as a generic root menu + grouped members. */
    private void saveDiningOutCompositionTemplate(
            NutritionFood savedMenu,
            List<DiningOutOption> options,
            DiningOutIdentity identity
    ) {
        if (savedMenu == null) {
            return;
        }
        String templateId = "dining-out-template:" + savedMenu.id;
        Map<String, List<DiningOutOption>> optionsByGroup = new LinkedHashMap<>();
        Map<String, String> groupLabels = new LinkedHashMap<>();
        Map<String, String> groupTypes = new LinkedHashMap<>();
        if (options != null) {
            for (DiningOutOption option : options) {
                List<DiningOutOption> group = optionsByGroup.get(option.groupKey);
                if (group == null) {
                    group = new ArrayList<>();
                    optionsByGroup.put(option.groupKey, group);
                }
                group.add(option);
                groupLabels.put(option.groupKey, option.groupLabel);
                groupTypes.put(option.groupKey, option.groupType);
            }
        }

        List<CompositionGroup> groups = new ArrayList<>();
        int groupIndex = 0;
        for (Map.Entry<String, List<DiningOutOption>> entry : optionsByGroup.entrySet()) {
            List<CompositionMember> members = new ArrayList<>();
            int memberIndex = 0;
            for (DiningOutOption option : entry.getValue()) {
                String memberId = option.memberId == null
                        ? CompositionTemplateRepository.newId()
                        : option.memberId;
                members.add(new CompositionMember(
                        memberId,
                        option.catalogFoodId,
                        option.name,
                        savedMenu.brand,
                        1,
                        NutritionUnit.SERVING,
                        false,
                        memberIndex++,
                        option.sourceReference,
                        option.profile
                ));
            }
            groups.add(CompositionGroup.optionalMany(
                    CompositionTemplateRepository.newId(),
                    entry.getKey(),
                    groupTypes.get(entry.getKey()),
                    groupLabels.get(entry.getKey()),
                    groupIndex++,
                    members
            ));
        }

        String sourceReference = identity == null
                ? "{\"schema_version\":\"composition-template.v1\",\"source\":\"fitnessapp\"}"
                : identity.metadataJson();
        repository().compositionTemplates().save(new CompositionTemplate(
                templateId,
                repository().currentUserId(),
                savedMenu.brand == null
                        ? savedMenu.name
                        : savedMenu.brand + " · " + savedMenu.name,
                CompositionTemplate.KIND_DINING_OUT,
                savedMenu.id,
                sourceReference,
                1,
                groups
        ));
    }

    private String emptyToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
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
            catalogMode = CATALOG_MODE_SINGLE_FOOD;
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
            catalogMode = CATALOG_MODE_SINGLE_FOOD;
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
        LinearLayout searchCard = ui.card();
        ui.cardHeader(
                searchCard,
                menuBuilderVisible ? "메뉴에 넣을 재료" : "끼니에 넣을 항목",
                catalogSyncing ? "동기화 중" : "유형별로 찾거나 새로 등록"
        );

        if (!syncMessage.trim().isEmpty()) {
            TextView catalogStatus = ui.text(syncMessage, 12, FitnessUi.COLOR_MUTED, false);
            catalogStatus.setPadding(0, ui.dp(5), 0, 0);
            searchCard.addView(catalogStatus);
        }

        appendCatalogSearchSection(searchCard);
        card.addView(searchCard, ui.fullWidthParams(0));
        if (menuBuilderVisible) {
            return;
        }

        LinearLayout registrationCard = ui.card();
        ui.cardHeader(
                registrationCard,
                "직접 등록",
                "공식 DB에 없거나 조리 방식이 다른 항목만 입력하세요."
        );
        LinearLayout registrationTabs = new LinearLayout(host.activity());
        registrationTabs.setOrientation(LinearLayout.HORIZONTAL);
        addCatalogModeTab(registrationTabs, "단일 식품", CATALOG_MODE_SINGLE_FOOD);
        addCatalogModeTab(registrationTabs, "완제품", CATALOG_MODE_FINISHED_PRODUCT);
        registrationCard.addView(registrationTabs, ui.fullWidthParams(ui.dp(12)));

        if (catalogMode == CATALOG_MODE_FINISHED_PRODUCT) {
            registrationCard.addView(directFoodForm(false), ui.fullWidthParams(ui.dp(10)));
        } else {
            registrationCard.addView(directFoodForm(true), ui.fullWidthParams(ui.dp(10)));
        }
        card.addView(registrationCard, ui.fullWidthParams(ui.dp(12)));
    }

    private void appendCatalogSearchSection(LinearLayout card) {
        FitnessUi ui = ui();
        ui.cardHeader(
                card,
                "찾기",
                "전체·단일 식품·완제품·식당을 한 곳에서 검색합니다."
        );
        appendCatalogKindFilters(card);
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

    private void appendCatalogKindFilters(LinearLayout card) {
        FitnessUi ui = ui();
        LinearLayout filters = new LinearLayout(host.activity());
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setPadding(0, ui.dp(4), 0, 0);
        addCatalogKindFilter(filters, "전체", CATALOG_FILTER_ALL);
        addCatalogKindFilter(filters, "단일 식품", NutritionFood.KIND_INGREDIENT);
        addCatalogKindFilter(filters, "완제품", NutritionFood.KIND_EXTERNAL_MENU);
        addCatalogKindFilter(filters, "식당", CATALOG_FILTER_RESTAURANT_MENU);
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
                catalogFoodTypeLabel(food) + " " + food.name
                        + ", " + (menuBuilderVisible ? "재료로 추가" : "끼니에 추가")
        );

        LinearLayout details = new LinearLayout(host.activity());
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(ui.dp(10), 0, ui.dp(8), 0);
        details.addView(ui.text(
                catalogFoodTypeLabel(food),
                11,
                FitnessUi.COLOR_TERTIARY,
                true
        ));
        details.addView(ui.text(food.name, 14, FitnessUi.COLOR_TEXT, true));
        boolean finishedProduct = NutritionFood.KIND_EXTERNAL_MENU.equals(
                NutritionFood.normalizeKind(food.kind)
        );
        boolean diningOutMenu = food.isDiningOutMenu();
        ProductNutritionLink approved = null;
        List<ProductNutritionLink> suggestions = new ArrayList<>();
        if (finishedProduct && !diningOutMenu) {
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
        if (diningOutMenu) {
            boolean isPublic = host.nutritionCatalogRepository().isFoodPublic(food.id);
            actions.addView(ui.textAction(
                    isPublic ? "PT 공개 관리" : "PT 공개",
                    isPublic ? FitnessUi.COLOR_MUTED : FitnessUi.COLOR_TERTIARY,
                    () -> productLinkController.showDiningOutPublication(food)
            ));
        } else if (finishedProduct) {
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

    private String catalogFoodTypeLabel(NutritionFood food) {
        if (food != null && food.isDiningOutMenu()) {
            String restaurantName = food.brand == null ? "" : food.brand.trim();
            return "식당 : " + (restaurantName.isEmpty() ? "가게명 미기록" : restaurantName);
        }
        return NutritionFood.kindLabel(food == null ? "" : food.kind);
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
        if (CATALOG_FILTER_ALL.equals(catalogKindFilter)) {
            return true;
        }
        if (CATALOG_FILTER_RESTAURANT_MENU.equals(catalogKindFilter)) {
            return food != null && food.isDiningOutMenu();
        }
        if (NutritionFood.KIND_EXTERNAL_MENU.equals(kind)) {
            return NutritionFood.KIND_EXTERNAL_MENU.equals(catalogKindFilter)
                    && food != null
                    && !food.isDiningOutMenu();
        }
        return catalogKindFilter.equals(kind);
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

    private boolean hasAnyDiningOutIdentity() {
        return !draftDiningOutRestaurantId.trim().isEmpty()
                || !draftDiningOutRestaurantLocationId.trim().isEmpty()
                || !draftDiningOutRestaurantMenuId.trim().isEmpty()
                || !draftDiningOutCatalogProductId.trim().isEmpty();
    }

    private boolean hasCompleteDiningOutIdentity() {
        return !draftDiningOutRestaurantId.trim().isEmpty()
                && !draftDiningOutRestaurantLocationId.trim().isEmpty()
                && !draftDiningOutRestaurantMenuId.trim().isEmpty()
                && !draftDiningOutCatalogProductId.trim().isEmpty();
    }

    private void clearDiningOutPriceTraceIdentity() {
        draftDiningOutRestaurantId = "";
        draftDiningOutRestaurantLocationId = "";
        draftDiningOutSourceNamespace = "";
        draftDiningOutSourceLocationCode = "";
        draftDiningOutRestaurantMenuId = "";
        draftDiningOutCatalogProductId = "";
    }

    private void updateDiningOutSelectionSummary() {
        if (diningOutSelectionSummary == null) {
            return;
        }
        String storeName = draftDiningOutStoreName.trim();
        String menuName = draftDiningOutMenuName.trim();
        if (storeName.isEmpty() || menuName.isEmpty()) {
            diningOutSelectionSummary.setText(
                    "PT 검색으로 채우거나 식당명·메뉴를 직접 입력하세요."
            );
            return;
        }
        String branchName = draftDiningOutBranchName.trim().isEmpty()
                ? "지점명 미기록"
                : draftDiningOutBranchName.trim();
        diningOutSelectionSummary.setText(
                (hasCompleteDiningOutIdentity() ? "PT 연결 결과" : "직접 등록")
                        + "\n식당: " + storeName
                        + "\n지점: " + branchName
                        + "\n메뉴: " + menuName
        );
    }

    private DiningOutIdentity selectedDiningOutIdentity() {
        if (!hasAnyDiningOutIdentity()) {
            return null;
        }
        if (!hasCompleteDiningOutIdentity()) {
            throw new IllegalArgumentException(
                    "검색한 식당·지점·메뉴를 모두 선택하거나 직접 등록으로 전환하세요."
            );
        }
        if (!draftDiningOutSourceNamespace.trim().isEmpty()
                && !draftDiningOutSourceLocationCode.trim().isEmpty()) {
            return DiningOutIdentity.fromPriceTrace(
                    draftDiningOutRestaurantId,
                    draftDiningOutStoreName,
                    draftDiningOutRestaurantLocationId,
                    draftDiningOutSourceNamespace,
                    draftDiningOutSourceLocationCode,
                    draftDiningOutBranchName,
                    draftDiningOutRestaurantMenuId,
                    draftDiningOutMenuName,
                    draftDiningOutCatalogProductId
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
                double nominalServings = diningOutNominalServingsValue();
                int dinerCount = diningOutDinerCountValue();
                Double consumedFraction = diningOutConsumedFractionValue();
                DiningOutConsumption consumption = DiningOutConsumption.resolve(
                        dinerCount,
                        consumedFraction
                );
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
                                    saturatedFatGrams,
                                    draftDiningOutBranchName,
                                    diningOutIdentity
                            )
                            : null;
                } else {
                    savedMenu = saveDiningOutMenu
                            ? host.nutritionCatalogRepository().saveDiningOutMenu(
                                    draftDiningOutStoreName,
                                    draftDiningOutMenuName,
                                    carbsGrams,
                                    proteinGrams,
                                    fatGrams,
                                    draftDiningOutBranchName,
                                    diningOutIdentity
                            )
                            : null;
                }
                if (saveDiningOutMenu && savedMenu != null) {
                    saveDiningOutCompositionTemplate(
                            savedMenu,
                            optionSnapshots,
                            diningOutIdentity
                    );
                }
                repository().addDiningOutMealAtTimeWithConsumption(
                        selectedDate,
                        recordedMealTime,
                        draftDiningOutStoreName,
                        draftDiningOutBranchName,
                        draftDiningOutMenuName,
                        calories,
                        proteinGrams,
                        carbsGrams,
                        fatGrams,
                        sodiumMg,
                        sugarsGrams,
                        saturatedFatGrams,
                        diningOutIdentity,
                        savedMenu == null
                                ? null
                                : MealCompositionItem.from(savedMenu, savedMenu.basisAmount),
                        optionSnapshots,
                        nominalServings,
                        consumption,
                        hasExtendedNutrition
                );
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
            draftDiningOutSourceNamespace = "";
            draftDiningOutSourceLocationCode = "";
            draftDiningOutRestaurantMenuId = "";
            draftDiningOutCatalogProductId = "";
            draftDiningOutOptions.clear();
            draftDiningOutCarbs = "";
            draftDiningOutProtein = "";
            draftDiningOutFat = "";
            draftDiningOutCalories = "";
            draftDiningOutSodium = "";
            draftDiningOutSugars = "";
            draftDiningOutSaturatedFat = "";
            draftDiningOutNominalServings = "1";
            draftDiningOutDinerCount = "1";
            draftDiningOutConsumedPercent = "";
            diningOutSelectionSummary = null;
            diningOutStoreInput = null;
            diningOutBranchInput = null;
            diningOutMenuInput = null;
            diningOutNominalServingsInput = null;
            diningOutDinerCountInput = null;
            diningOutConsumedPercentInput = null;
            diningOutOptionsContainer = null;
            diningOutOptionInputs.clear();
            diningOutOptionGroupInputs.clear();
            diningOutOptionConsumedPercentInputs.clear();
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
        draftDiningOutBranchName = "";
        draftDiningOutMenuName = "";
        draftDiningOutRestaurantId = "";
        draftDiningOutRestaurantLocationId = "";
        draftDiningOutSourceNamespace = "";
        draftDiningOutSourceLocationCode = "";
        draftDiningOutRestaurantMenuId = "";
        draftDiningOutCatalogProductId = "";
        draftDiningOutOptions.clear();
        draftDiningOutCarbs = "";
        draftDiningOutProtein = "";
        draftDiningOutFat = "";
        draftDiningOutCalories = "";
        draftDiningOutSodium = "";
        draftDiningOutSugars = "";
        draftDiningOutSaturatedFat = "";
        draftDiningOutNominalServings = "1";
        draftDiningOutDinerCount = "1";
        draftDiningOutConsumedPercent = "";
        diningOutSelectionSummary = null;
        diningOutStoreInput = null;
        diningOutBranchInput = null;
        diningOutMenuInput = null;
        diningOutNominalServingsInput = null;
        diningOutDinerCountInput = null;
        diningOutConsumedPercentInput = null;
        diningOutOptionsContainer = null;
        diningOutOptionInputs.clear();
        diningOutOptionGroupInputs.clear();
        diningOutOptionConsumedPercentInputs.clear();
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
        syncDiningOutTextInputs();
        if (diningOutNominalServingsInput != null) {
            draftDiningOutNominalServings = FitnessUi.inputText(diningOutNominalServingsInput);
        }
        if (diningOutDinerCountInput != null) {
            draftDiningOutDinerCount = FitnessUi.inputText(diningOutDinerCountInput);
        }
        if (diningOutConsumedPercentInput != null) {
            draftDiningOutConsumedPercent = FitnessUi.inputText(diningOutConsumedPercentInput);
        }
        if (menuNameInput != null) {
            draftMenuName = FitnessUi.inputText(menuNameInput);
        }
        if (!diningOutOptionInputs.isEmpty()) {
            List<DiningOutOptionDraft> pendingOptions = new ArrayList<>(draftDiningOutOptions);
            draftDiningOutOptions.clear();
            for (int index = 0; index < diningOutOptionInputs.size(); index++) {
                DiningOutOptionDraft previous = index < pendingOptions.size()
                        ? pendingOptions.get(index)
                        : null;
                DiningOutOptionDraft draft = new DiningOutOptionDraft();
                draft.name = FitnessUi.inputText(diningOutOptionInputs.get(index)).trim();
                draft.calories = FitnessUi.inputText(diningOutOptionCaloriesInputs.get(index));
                draft.protein = FitnessUi.inputText(diningOutOptionProteinInputs.get(index));
                draft.carbs = FitnessUi.inputText(diningOutOptionCarbsInputs.get(index));
                draft.fat = FitnessUi.inputText(diningOutOptionFatInputs.get(index));
                draft.consumedPercent = FitnessUi.inputText(
                        diningOutOptionConsumedPercentInputs.get(index)
                );
                if (previous != null) {
                    draft.groupType = previous.groupType;
                    draft.groupKey = previous.groupKey;
                    draft.catalogFoodId = previous.catalogFoodId;
                    draft.sourceReference = previous.sourceReference;
                    draft.memberId = previous.memberId;
                }
                draftDiningOutOptions.add(draft);
            }
            // An add action can create a new draft row before render() has created its views.
            // Keep those trailing drafts so repeated "옵션 추가" clicks do not collapse to one row.
            for (int index = diningOutOptionInputs.size(); index < pendingOptions.size(); index++) {
                draftDiningOutOptions.add(pendingOptions.get(index));
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

    private void syncDiningOutTextInputs() {
        if (diningOutStoreInput == null
                || diningOutBranchInput == null
                || diningOutMenuInput == null) {
            return;
        }
        String nextStoreName = FitnessUi.inputText(diningOutStoreInput);
        String nextBranchName = FitnessUi.inputText(diningOutBranchInput);
        String nextMenuName = FitnessUi.inputText(diningOutMenuInput);
        boolean hasSelectedIdentity = hasAnyDiningOutIdentity();
        boolean searchedValueChanged = !TextUtils.equals(
                draftDiningOutStoreName,
                nextStoreName
        ) || !TextUtils.equals(
                draftDiningOutBranchName,
                nextBranchName
        ) || !TextUtils.equals(
                draftDiningOutMenuName,
                nextMenuName
        );
        draftDiningOutStoreName = nextStoreName;
        draftDiningOutBranchName = nextBranchName;
        draftDiningOutMenuName = nextMenuName;
        if (searchedValueChanged) {
            if (hasSelectedIdentity) {
                // A manually changed name is no longer guaranteed to describe the selected
                // PriceTrace location/menu. Keep the local entry, but remove the cross-app link.
                clearDiningOutPriceTraceIdentity();
            }
            for (DiningOutOptionDraft draft : draftDiningOutOptions) {
                draft.sourceReference = withoutCompositionTemplateReference(
                        draft.sourceReference
                );
            }
        }
    }

    private String withoutCompositionTemplateReference(String sourceReference) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return "";
        }
        try {
            JSONObject source = new JSONObject(sourceReference);
            source.remove("composition_template_id");
            source.remove("composition_template_revision");
            return source.toString();
        } catch (Exception ignored) {
            return sourceReference;
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

    private double diningOutNominalServingsValue() {
        String value = draftDiningOutNominalServings == null
                ? ""
                : draftDiningOutNominalServings.trim();
        if (value.isEmpty()) {
            return 1d;
        }
        try {
            return requirePositiveFiniteDiningValue(value, "메뉴 제공 인분", 100d);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("메뉴 제공 인분은 숫자로 입력하세요.");
        }
    }

    private int diningOutDinerCountValue() {
        String value = draftDiningOutDinerCount == null
                ? ""
                : draftDiningOutDinerCount.trim();
        if (value.isEmpty()) {
            return 1;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("함께 먹은 인원은 정수로 입력하세요.");
        }
        // DiningOutConsumption owns the shared 1..100 range validation.
        DiningOutConsumption.equalByDiners(parsed);
        return parsed;
    }

    private Double diningOutConsumedFractionValue() {
        String value = draftDiningOutConsumedPercent == null
                ? ""
                : draftDiningOutConsumedPercent.trim();
        if (value.isEmpty()) {
            return null;
        }
        return diningOutConsumedFractionValue(value, "내 섭취 비율");
    }

    private double diningOutOptionConsumedFractionValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return 1d;
        }
        return diningOutConsumedFractionValue(value, "옵션 내 섭취 비율");
    }

    private double diningOutConsumedFractionValue(String value, String label) {
        final double percent;
        try {
            percent = Double.parseDouble(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "은 숫자로 입력하세요.");
        }
        double normalized = requirePositiveFiniteDiningValue(value, label, 100d);
        return normalized / 100d;
    }

    private double requirePositiveFiniteDiningValue(
            String value,
            String label,
            double maximum
    ) {
        double parsed = Double.parseDouble(value);
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)
                || parsed <= 0d || parsed > maximum) {
            throw new IllegalArgumentException(
                    label + "은 0보다 크고 " + maximum + " 이하로 입력하세요."
            );
        }
        return parsed;
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
        private String groupType = CompositionGroupType.OTHER.value();
        private String groupKey = "";
        private String name = "";
        private String calories = "";
        private String protein = "";
        private String carbs = "";
        private String fat = "";
        private String consumedPercent = "100";
        private String catalogFoodId = "";
        private String sourceReference = "";
        private String memberId = "";
    }
}
