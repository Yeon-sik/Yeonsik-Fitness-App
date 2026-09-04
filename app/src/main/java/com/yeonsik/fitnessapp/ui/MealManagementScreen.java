package com.yeonsik.fitnessapp.ui;

import android.app.DatePickerDialog;
import android.app.Dialog;
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
import com.yeonsik.fitnessapp.data.DiningOutFulfillmentMode;
import com.yeonsik.fitnessapp.data.DiningOutProvisionType;
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
import java.util.Arrays;
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
    private final List<DiningOutMenuDraft> draftDiningOutMenus = new ArrayList<>();
    private int activeDiningOutMenuIndex;
    private String draftDiningOutNominalServings = "1";
    private String draftDiningOutDinerCount = "1";
    private String draftDiningOutConsumedPercent = "";
    private String draftDiningOutFulfillmentMode;

    private Button mealTimeButton;
    private EditText menuNameInput;
    private TextView diningOutSelectionSummary;
    private TextView diningOutValidationError;
    private EditText diningOutStoreInput;
    private EditText diningOutBranchInput;
    private EditText diningOutNominalServingsInput;
    private EditText diningOutDinerCountInput;
    private EditText diningOutConsumedPercentInput;
    private LinearLayout diningOutOptionsContainer;
    private LinearLayout diningOutMenusContainer;
    private final List<EditText> diningOutMenuNameInputs = new ArrayList<>();
    private final List<EditText> diningOutMenuCaloriesInputs = new ArrayList<>();
    private final List<EditText> diningOutMenuProteinInputs = new ArrayList<>();
    private final List<EditText> diningOutMenuCarbsInputs = new ArrayList<>();
    private final List<EditText> diningOutMenuFatInputs = new ArrayList<>();
    private final List<EditText> diningOutMenuSodiumInputs = new ArrayList<>();
    private final List<EditText> diningOutMenuSugarsInputs = new ArrayList<>();
    private final List<EditText> diningOutMenuSaturatedFatInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionInputs = new ArrayList<>();
    private final List<Button> diningOutOptionProvisionInputs = new ArrayList<>();
    private final List<Button> diningOutOptionGroupInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionCaloriesInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionProteinInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionCarbsInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionFatInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionSodiumInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionSugarsInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionSaturatedFatInputs = new ArrayList<>();
    private final List<EditText> diningOutOptionConsumedPercentInputs = new ArrayList<>();
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
    private final FormSystem formSystem;
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
        formSystem = new FormSystem(host.ui(), host.activity());
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
                () -> backOr(returnScreen)), ui().fullWidthParams(0));
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
        header.addView(ui.caption("오늘 영양", ui.heroMuted()),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        View goalBadge = ui.heroStatusBadge(
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
                ui.heroInk(), true));
        String calorieUnit = goal == null
                ? " kcal"
                : " / " + Math.round(goal.caloriesKcal) + " kcal";
        TextView unit = ui.text(calorieUnit, 16, ui.heroMuted(), true);
        unit.setPadding(0, 0, 0, ui.dp(6));
        caloriesRow.addView(unit);
        caloriesRow.addView(ui.text("  ·  " + summary.mealCount + "끼 기록", 13,
                ui.heroMuted(), false));
        card.addView(caloriesRow);

        if (goal == null) {
            LinearLayout firstMacroRow = ui.tileRow();
            firstMacroRow.addView(ui.heroMetric(
                    "단백질",
                    NutritionCalculator.trim(summary.proteinGrams) + "g"
            ), ui.tileParams(true));
            firstMacroRow.addView(ui.heroMetric(
                    "탄수화물",
                    NutritionCalculator.trim(summary.carbsGrams) + "g"
            ), ui.tileParams(false));
            card.addView(firstMacroRow, ui.fullWidthParams(ui.dp(10)));

            LinearLayout secondMacroRow = ui.tileRow();
            secondMacroRow.addView(ui.heroMetric(
                    "지방",
                    NutritionCalculator.trim(summary.fatGrams) + "g"
            ), ui.tileParams(true));
            secondMacroRow.addView(ui.heroMetric(
                    "상태",
                    summary.mealCount == 0 ? "기록 시작" : "목표 미설정"
            ), ui.tileParams(false));
            card.addView(secondMacroRow, ui.fullWidthParams(ui.dp(6)));
            card.addView(ui.primaryButton("일일 영양 목표 설정", v -> showNutritionGoalDialog()),
                    ui.fullWidthParams(ui.dp(14)));
        } else {
            addGoalProgress(card, "칼로리", summary.calories, goal.caloriesKcal, "kcal");
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
        TextView weightView = ui.text(weightLine, 12, ui.heroMuted(), false);
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
                    ui.heroMuted(),
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
        TextView labelView = ui.text(label, 12, ui.heroMuted(), true);
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
        row.addView(ui.text(detail, 12, ui.heroInk(), true));
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
        ui.cardHeader(card, "영양성분 상세", "미상은 0으로 계산하지 않음");

        card.addView(formSystem.sectionTitle("1차 정보"), ui.fullWidthParams(ui.dp(2)));
        for (String key : new String[]{
                NutritionProfile.CALORIES_KCAL,
                NutritionProfile.CARBS_GRAMS,
                NutritionProfile.PROTEIN_GRAMS,
                NutritionProfile.FAT_GRAMS
        }) {
            addNutritionTotalRow(card, totals, key, null);
        }

        card.addView(formSystem.sectionTitle("2차 정보"), ui.fullWidthParams(ui.dp(2)));
        addNutritionTotalRow(card, totals, NutritionProfile.SUGARS_GRAMS, null);
        addNutritionTotalRow(card, totals, NutritionProfile.SATURATED_FAT_GRAMS, null);
        addNutritionTotalRow(card, totals, NutritionProfile.SODIUM_MG,
                goal == null ? null : goal.sodiumMg);

        card.addView(formSystem.sectionTitle("추가 정보"), ui.fullWidthParams(ui.dp(2)));
        addNutritionTotalRow(card, totals, NutritionProfile.FIBER_GRAMS,
                goal == null ? null : goal.fiberGrams);
        for (String key : new String[]{
                NutrientCode.POTASSIUM,
                NutrientCode.MAGNESIUM,
                NutrientCode.CALCIUM,
                NutrientCode.IRON,
                NutrientCode.ZINC,
                NutrientCode.VITAMIN_D,
                NutrientCode.VITAMIN_B9,
                NutrientCode.VITAMIN_B12
        }) {
            addNutritionTotalRow(card, totals, key, null);
        }
        return card;
    }

    private void addNutritionTotalRow(
            LinearLayout card,
            NutritionTotals totals,
            String key,
            Double target
    ) {
        card.addView(
                formSystem.nutrientRow(
                        key,
                        formatNutrientTotal(totals, key, target)
                ),
                ui().fullWidthParams(0)
        );
    }

    /** Adds a recorded menu's values with the same rows used by editable menus and totals. */
    private void addProfileNutritionRows(LinearLayout parent, NutritionProfile profile) {
        for (String key : new String[]{
                NutritionProfile.CALORIES_KCAL,
                NutritionProfile.CARBS_GRAMS,
                NutritionProfile.PROTEIN_GRAMS,
                NutritionProfile.FAT_GRAMS
        }) {
            parent.addView(
                    formSystem.nutrientRow(key, nutritionValue(profile, key)),
                    ui().fullWidthParams(0)
            );
        }
        parent.addView(formSystem.sectionTitle("2차 정보"), ui().fullWidthParams(ui().dp(2)));
        for (String key : new String[]{
                NutritionProfile.SUGARS_GRAMS,
                NutritionProfile.SATURATED_FAT_GRAMS,
                NutritionProfile.SODIUM_MG
        }) {
            parent.addView(
                    formSystem.nutrientRow(key, nutritionValue(profile, key)),
                    ui().fullWidthParams(0)
            );
        }
    }

    private String formatNutrientTotal(NutritionTotals totals, String key, Double target) {
        String value = NutritionCalculator.describeTotal(totals.total(key));
        if (target == null) {
            return value;
        }
        return value + " / " + NutritionCalculator.trim(target);
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

        LinearLayout form = formSystem.column();
        form.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(8));
        TextView guidance = formSystem.helper(
                "자동 처방값이 아닙니다. 코치·영양사와 정한 하루 목표를 입력하세요."
        );
        form.addView(guidance);

        Button phase = formSystem.selector(phaseLabels[phaseIndex[0]], null);
        phase.setOnClickListener(v -> ui.choiceSheet(
                "현재 단계",
                java.util.Arrays.asList(phaseLabels),
                phaseIndex[0],
                which -> {
                    phaseIndex[0] = which;
                    phase.setText(phaseLabels[which]);
                }));
        form.addView(formSystem.field("현재 단계", phase), ui.fullWidthParams(ui.dp(12)));

        EditText calories = ui.decimalInput("입력", goalValue(current, GoalField.CALORIES));
        EditText protein = ui.decimalInput("입력", goalValue(current, GoalField.PROTEIN));
        EditText carbs = ui.decimalInput("입력", goalValue(current, GoalField.CARBS));
        EditText fat = ui.decimalInput("입력", goalValue(current, GoalField.FAT));
        EditText fiber = ui.decimalInput("입력", goalValue(current, GoalField.FIBER));
        EditText sodium = ui.decimalInput("입력", goalValue(current, GoalField.SODIUM));
        EditText water = ui.numberInput("ml", current == null ? "" : String.valueOf(current.waterMl));
        for (NutritionRow row : new NutritionRow[]{
                formSystem.nutrientInputRow("칼로리", "kcal", calories),
                formSystem.nutrientInputRow("탄수화물", "g", carbs),
                formSystem.nutrientInputRow("단백질", "g", protein),
                formSystem.nutrientInputRow("지방", "g", fat),
                formSystem.nutrientInputRow("식이섬유", "g", fiber),
                formSystem.nutrientInputRow("나트륨", "mg", sodium)
        }) {
            form.addView(row.view(), ui.fullWidthParams(0));
        }
        form.addView(formSystem.field("수분", water), ui.fullWidthParams(ui.dp(10)));

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
        LinearLayout form = formSystem.column();
        form.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(8));

        TextView guidance = formSystem.helper(
                "점수는 진단이 아니라 변화 관찰용입니다. 허기는 5가 가장 강하고, 나머지는 5가 가장 좋습니다."
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

        EditText note = formSystem.textInput("특이사항", current.note);
        note.setSingleLine(false);
        note.setMinLines(2);
        note.setMaxLines(3);
        note.setGravity(Gravity.TOP | Gravity.START);
        note.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        form.addView(formSystem.field("메모", note), ui.fullWidthParams(ui.dp(10)));

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
        Button button = formSystem.selector(scoreButtonText(scores[scoreIndex]), null);
        button.setOnClickListener(v -> ui.choiceSheet(
                title,
                java.util.Arrays.asList(options),
                scores[scoreIndex],
                which -> {
                    scores[scoreIndex] = which;
                    button.setText(scoreButtonText(which));
                }));
        return button;
    }

    private View pairedFields(String firstLabel, View first, String secondLabel, View second) {
        FitnessUi ui = ui();
        LinearLayout row = ui.tileRow();
        row.addView(formSystem.field(firstLabel, first), ui.fieldCellParams(true));
        row.addView(formSystem.field(secondLabel, second), ui.fieldCellParams(false));
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
        LinearLayout body = formSystem.column();
        body.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(8));
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

        ui.bottomSheet(recipe.displayName(), scroll, "닫기", () -> { }, null, null);
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
        row.addView(formSystem.sectionTitle("단위 영양성분"),
                ui.fullWidthParams(ui.dp(4)));
        addProfileNutritionRows(row, component.food.profile);
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

    static String diningOutComponentDisplayLabel(
            FitnessRepository.MealComponentEntry component
    ) {
        String label = "· " + component.label();
        if (component.hasExplicitConsumedFraction()) {
            label += " · 내 섭취 " + Math.round(component.percentage()) + "%";
        }
        String provisionLabel = component.provisionDisplayLabel();
        if (!provisionLabel.isEmpty()) {
            label += " · " + provisionLabel;
        }
        return label;
    }

    private void showRecordedMealDetails(FitnessRepository.MealEntry entry) {
        FitnessUi ui = ui();
        LinearLayout body = formSystem.column();
        body.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(16));
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
            FitnessRepository.DiningOutConsumptionEntry consumption =
                    repository().diningOutConsumptionForRecord(entry.id);
            if (consumption != null) {
                body.addView(ui.caption("공유 섭취", FitnessUi.COLOR_TERTIARY),
                        ui.fullWidthParams(ui.dp(12)));
                body.addView(ui.text(
                        "새 계산 방식 · " + consumption.dinerCount + "명 · 내 몫 "
                                + Math.round(consumption.percentage()) + "% · "
                                + (consumption.isEqualSplit() ? "균등 추정" : "직접 입력"),
                        14,
                        FitnessUi.COLOR_TEXT,
                        true
                ), ui.fullWidthParams(ui.dp(2)));
            }
            List<FitnessRepository.MealItemEntry> menuItems =
                    repository().mealItemsForRecord(entry.id);
            if (menuItems.isEmpty()) {
                body.addView(ui.text(
                        entry.menuName,
                        16,
                        FitnessUi.COLOR_TEXT,
                        true
                ), ui.fullWidthParams(ui.dp(2)));
            } else {
                body.addView(ui.caption(
                        "먹은 메뉴 " + menuItems.size() + "개",
                        FitnessUi.COLOR_TERTIARY
                ), ui.fullWidthParams(ui.dp(8)));
                for (int index = 0; index < menuItems.size(); index++) {
                    FitnessRepository.MealItemEntry menu = menuItems.get(index);
                    LinearLayout menuCard = new LinearLayout(host.activity());
                    menuCard.setOrientation(LinearLayout.VERTICAL);
                    menuCard.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
                    menuCard.setBackground(ui.flatSurfaceDrawable(ui.dp(12)));
                    menuCard.addView(ui.text(
                            "메뉴 " + (index + 1) + " · " + menu.foodName,
                            14,
                            FitnessUi.COLOR_TEXT,
                            true
                    ));
                    menuCard.addView(ui.text(
                            NutritionCalculator.trim(menu.quantity)
                                    + NutritionUnit.display(menu.unit),
                            12,
                            FitnessUi.COLOR_MUTED,
                            false
                    ));
                    menuCard.addView(formSystem.sectionTitle("영양성분"),
                            ui.fullWidthParams(ui.dp(2)));
                    addProfileNutritionRows(menuCard, menu.profile);
                    List<FitnessRepository.MealComponentEntry> components =
                            repository().mealComponentsForItem(menu.id);
                    if (!components.isEmpty()) {
                        menuCard.addView(ui.caption(
                                "옵션/component " + components.size() + "개",
                                FitnessUi.COLOR_TERTIARY
                        ), ui.fullWidthParams(ui.dp(8)));
                        for (FitnessRepository.MealComponentEntry component : components) {
                            String componentLabel = diningOutComponentDisplayLabel(component);
                            menuCard.addView(ui.text(
                                    componentLabel,
                                    12,
                                    FitnessUi.COLOR_MUTED,
                                    false
                            ), ui.fullWidthParams(ui.dp(4)));
                        }
                    }
                    body.addView(menuCard, ui.fullWidthParams(ui.dp(10)));
                }
            }
            if (entry.hasEstimatedNutrition()) {
                NutritionTotals.Builder totalsBuilder = NutritionTotals.builder();
                if (menuItems.isEmpty()) {
                    totalsBuilder.add(NutritionProfile.ofMacros(
                            entry.calories,
                            entry.proteinGrams,
                            entry.carbsGrams,
                            entry.fatGrams
                    ));
                }
                for (FitnessRepository.MealItemEntry nutritionItem : menuItems) {
                    totalsBuilder.add(consumption == null
                            ? nutritionItem.profile
                            : nutritionItem.profile.scaled(consumption.consumedFraction));
                    if (consumption != null) {
                        for (FitnessRepository.MealComponentEntry component :
                                repository().mealComponentsForItem(nutritionItem.id)) {
                            if (!component.hasExplicitConsumedFraction()) {
                                continue;
                            }
                            totalsBuilder.add(component.profile.scaled(component.consumedFraction()));
                        }
                    } else {
                        for (FitnessRepository.MealComponentEntry component :
                                repository().mealComponentsForItem(nutritionItem.id)) {
                            totalsBuilder.add(component.profile);
                        }
                    }
                }
                NutritionTotals totals = totalsBuilder.build();
                body.addView(formSystem.sectionTitle("실제 섭취 영양 합계"),
                        ui.fullWidthParams(ui.dp(12)));
                for (String key : new String[]{
                        NutritionProfile.CALORIES_KCAL,
                        NutritionProfile.CARBS_GRAMS,
                        NutritionProfile.PROTEIN_GRAMS,
                        NutritionProfile.FAT_GRAMS
                }) {
                    addNutritionTotalRow(body, totals, key, null);
                }
                body.addView(formSystem.sectionTitle("2차 정보"),
                        ui.fullWidthParams(ui.dp(2)));
                for (String key : new String[]{
                        NutritionProfile.SUGARS_GRAMS,
                        NutritionProfile.SATURATED_FAT_GRAMS,
                        NutritionProfile.SODIUM_MG
                }) {
                    addNutritionTotalRow(body, totals, key, null);
                }
                body.addView(formSystem.helper(
                        "칼로리·탄수화물·단백질·지방·나트륨·당류·포화지방은 직접 입력한 추정치입니다."
                ), ui.fullWidthParams(ui.dp(10)));
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
                                    + NutritionUnit.display(menu.unit),
                            12,
                            FitnessUi.COLOR_MUTED,
                            false
                    ));
                    menuCard.addView(formSystem.sectionTitle("영양성분"),
                            ui.fullWidthParams(ui.dp(2)));
                    addProfileNutritionRows(menuCard, menu.profile);
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

        LinearLayout actions = new LinearLayout(host.activity());
        actions.setOrientation(LinearLayout.VERTICAL);
        if (entry.timeEditable) {
            Button editTime = ui.button("시간 수정", false, v -> {
                ui.dismissActiveDialog();
                showRecordedMealTimePicker(entry);
            });
            actions.addView(editTime, ui.fullWidthParams(ui.dp(6)));
        }
        if (!entry.isDiningOut() && !repository().mealItemsForRecord(entry.id).isEmpty()) {
            Button editMenus = ui.button("메뉴 수정", false, v -> {
                ui.dismissActiveDialog();
                showMealMenuEditDialog(entry);
            });
            actions.addView(editMenus, ui.fullWidthParams(ui.dp(6)));
        }
        if (actions.getChildCount() > 0) {
            body.addView(actions, ui.fullWidthParams(ui.dp(12)));
        }

        ScrollView scroll = new ScrollView(host.activity());
        scroll.setFillViewport(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        ui.bottomSheet(entry.previewTitle, scroll, "닫기", () -> { }, null, null);
    }

    /** Allows a recorded meal's top-level menu names and quantities to be corrected in place. */
    private void showMealMenuEditDialog(FitnessRepository.MealEntry entry) {
        List<FitnessRepository.MealItemEntry> menus = repository().mealItemsForRecord(entry.id);
        if (menus.isEmpty()) {
            host.toast("이전 형식의 기록이라 수정할 메뉴가 없습니다.");
            return;
        }

        FitnessUi ui = ui();
        LinearLayout body = formSystem.column();
        body.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(8));
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
            body.addView(formSystem.field("메뉴명", nameInput),
                    ui.fullWidthParams(ui.dp(4)));

            EditText quantityInput = ui.decimalInput(
                    menu.unit,
                    NutritionCalculator.trim(menu.quantity)
            );
            quantityInput.setContentDescription("메뉴 " + (index + 1) + " 섭취량");
            quantityInputs.add(quantityInput);
            body.addView(formSystem.field("섭취량 (" + menu.unit + ")", quantityInput),
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
        ui.validatedSheet("끼니 메뉴 수정", scroll, "저장", () -> {
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
                    return false;
                }
                host.toast("끼니 메뉴를 수정했습니다.");
                host.rerender();
                return true;
            } catch (IllegalArgumentException error) {
                host.toast(error.getMessage());
                return false;
            }
        });
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
        card.addView(formSystem.field("먹은 시간", mealTimeButton),
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
                boolean leavingOrEnteringDiningOut =
                        mealEntryMode == MEAL_ENTRY_MODE_DINING_OUT
                                || mode == MEAL_ENTRY_MODE_DINING_OUT;
                mealEntryMode = mode;
                if (leavingOrEnteringDiningOut) {
                    rerenderDiningOutFromDraft();
                } else {
                    host.rerender();
                }
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
        LinearLayout form = formSystem.column();
        form.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));

        form.addView(formSystem.sectionTitle("외식 입력"), ui.fullWidthParams(0));
        form.addView(formSystem.helper(
                "입력 순서: 식당 → 메뉴 → 옵션 → 섭취량 → 저장. 한 화면에서 필요한 단계만 펼쳐 입력합니다.\n"
                        + "PT 검색으로 식당·지점·메뉴를 채우거나 직접 등록할 수 있습니다. 검색 결과의 값도 수정할 수 있습니다."
        ), ui.fullWidthParams(ui.dp(4)));

        Button selectPriceTraceDiningOut = ui.primaryButton(
                "PT 검색 · 식당·지점·메뉴 선택",
                v -> showPriceTraceDiningOutPicker()
        );
        selectPriceTraceDiningOut.setContentDescription("PT 식당·지점·메뉴 검색");
        Button directDiningOut = ui.secondaryButton(
                "직접 등록하기",
                v -> {
                    syncDraftFromViews();
                    clearDiningOutPriceTraceIdentity();
                    activeDiningOutMenu().catalogFoodId = "";
                    host.toast("식당·지점·메뉴를 직접 입력하세요.");
                    rerenderDiningOutFromDraft();
                }
        );
        directDiningOut.setContentDescription("외식 직접 등록");
        form.addView(
                ui.buttonRow(selectPriceTraceDiningOut, directDiningOut),
                ui.fullWidthParams(ui.dp(6))
        );
        Button reuseDiningOut = ui.secondaryButton(
                "내 저장 외식 메뉴 불러오기",
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
                formSystem.field("가게 명", diningOutStoreInput),
                ui.fieldCellParams(true)
        );
        restaurantFields.addView(
                formSystem.field("지점 (선택)", diningOutBranchInput),
                ui.fieldCellParams(false)
        );
        form.addView(restaurantFields, ui.fullWidthParams(ui.dp(8)));

        form.addView(ui.text(
                "이번 식사 이용 방식",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(3)));
        LinearLayout fulfillmentRow = ui.tileRow();
        for (DiningOutFulfillmentMode mode : DiningOutFulfillmentMode.values()) {
            Button modeButton = ui.filterButton(mode.label());
            ui.styleFilterButton(
                    modeButton,
                    mode.value().equals(draftDiningOutFulfillmentMode)
            );
            modeButton.setContentDescription("외식 이용 방식 " + mode.label());
            modeButton.setOnClickListener(ignored -> {
                syncDraftFromViews();
                draftDiningOutFulfillmentMode = mode.value();
                rerenderDiningOutFromDraft();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            params.setMargins(ui.dp(2), 0, ui.dp(2), 0);
            fulfillmentRow.addView(modeButton, params);
        }
        form.addView(fulfillmentRow, ui.fullWidthParams(ui.dp(5)));

        LinearLayout menuHeader = new LinearLayout(host.activity());
        menuHeader.setOrientation(LinearLayout.HORIZONTAL);
        menuHeader.setGravity(Gravity.CENTER_VERTICAL);
        menuHeader.addView(ui.caption("메뉴", FitnessUi.COLOR_MUTED),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        menuHeader.addView(ui.textAction("메뉴 추가", FitnessUi.COLOR_TERTIARY,
                this::addDiningOutMenuDraft));
        form.addView(menuHeader, ui.fullWidthParams(ui.dp(4)));
        diningOutMenusContainer = new LinearLayout(host.activity());
        diningOutMenusContainer.setOrientation(LinearLayout.VERTICAL);
        renderDiningOutMenuCards();
        form.addView(diningOutMenusContainer, ui.fullWidthParams(ui.dp(8)));

        diningOutNominalServingsInput = ui.decimalInput(
                "인분",
                draftDiningOutNominalServings
        );
        diningOutNominalServingsInput.setContentDescription("메뉴 제공 인분");
        diningOutDinerCountInput = ui.numberInput("명", draftDiningOutDinerCount);
        diningOutDinerCountInput.setContentDescription("함께 먹은 인원");
        LinearLayout sharingRow = ui.tileRow();
        sharingRow.addView(
                formSystem.field("메뉴 제공 인분", diningOutNominalServingsInput),
                ui.fieldCellParams(true)
        );
        sharingRow.addView(
                formSystem.field("함께 먹은 인원", diningOutDinerCountInput),
                ui.fieldCellParams(false)
        );
        form.addView(sharingRow, ui.fullWidthParams(ui.dp(8)));

        diningOutConsumedPercentInput = ui.decimalInput(
                "%",
                draftDiningOutConsumedPercent
        );
        diningOutConsumedPercentInput.setContentDescription("내 섭취 비율");
        form.addView(
                formSystem.field("내 섭취 비율 (선택, %)", diningOutConsumedPercentInput),
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
                "각 메뉴는 독립적인 메뉴와 옵션 구성 정보로 기록됩니다. 칼로리·탄수화물·단백질·지방은 메뉴별 필수이며, 당류·포화지방·나트륨은 선택 입력입니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(12)));
        diningOutValidationError = formSystem.error("");
        diningOutValidationError.setPadding(0, ui.dp(4), 0, 0);
        form.addView(diningOutValidationError, ui.fullWidthParams(ui.dp(8)));
        watchDiningOutValidationInputs();

        Button recordDiningOut = ui.secondaryButton("외식만 기록", v -> saveMeal(false));
        Button saveMenuAndRecord = ui.primaryButton(
                "메뉴 저장하고 기록",
                v -> saveMeal(true)
        );
        form.addView(ui.buttonRow(recordDiningOut, saveMenuAndRecord),
                ui.fullWidthParams(ui.dp(14)));
        return form;
    }

    private void watchDiningOutValidationInputs() {
        watchDiningOutValidationInput(diningOutStoreInput);
        watchDiningOutValidationInput(diningOutBranchInput);
        watchDiningOutValidationInput(diningOutNominalServingsInput);
        watchDiningOutValidationInput(diningOutDinerCountInput);
        watchDiningOutValidationInput(diningOutConsumedPercentInput);
        watchDiningOutValidationInputs(diningOutMenuNameInputs);
        watchDiningOutValidationInputs(diningOutMenuCaloriesInputs);
        watchDiningOutValidationInputs(diningOutMenuProteinInputs);
        watchDiningOutValidationInputs(diningOutMenuCarbsInputs);
        watchDiningOutValidationInputs(diningOutMenuFatInputs);
        watchDiningOutValidationInputs(diningOutMenuSodiumInputs);
        watchDiningOutValidationInputs(diningOutMenuSugarsInputs);
        watchDiningOutValidationInputs(diningOutMenuSaturatedFatInputs);
        watchDiningOutValidationInputs(diningOutOptionInputs);
        watchDiningOutValidationInputs(diningOutOptionCaloriesInputs);
        watchDiningOutValidationInputs(diningOutOptionProteinInputs);
        watchDiningOutValidationInputs(diningOutOptionCarbsInputs);
        watchDiningOutValidationInputs(diningOutOptionFatInputs);
        watchDiningOutValidationInputs(diningOutOptionSodiumInputs);
        watchDiningOutValidationInputs(diningOutOptionSugarsInputs);
        watchDiningOutValidationInputs(diningOutOptionSaturatedFatInputs);
        watchDiningOutValidationInputs(diningOutOptionConsumedPercentInputs);
    }

    private void watchDiningOutValidationInputs(List<EditText> inputs) {
        for (EditText input : inputs) {
            watchDiningOutValidationInput(input);
        }
    }

    private void watchDiningOutValidationInput(EditText input) {
        if (input == null) {
            return;
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                clearDiningOutValidationError();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void showDiningOutValidationError(String message) {
        if (diningOutValidationError == null) {
            return;
        }
        String copy = message == null || message.trim().isEmpty()
                ? "입력값을 확인하세요."
                : message;
        formSystem.showError(diningOutValidationError, copy);
    }

    private void clearDiningOutValidationError() {
        if (diningOutValidationError != null) {
            formSystem.clearError(diningOutValidationError);
        }
    }
    private void addDiningOutMenuDraft() {
        syncDraftFromViews();
        draftDiningOutMenus.add(new DiningOutMenuDraft());
        activeDiningOutMenuIndex = draftDiningOutMenus.size() - 1;
        rerenderDiningOutFromDraft();
    }

    private void renderDiningOutMenuCards() {
        if (diningOutMenusContainer == null) {
            return;
        }
        if (draftDiningOutMenus.isEmpty()) {
            draftDiningOutMenus.add(new DiningOutMenuDraft());
        }
        activeDiningOutMenuIndex = Math.max(
                0,
                Math.min(activeDiningOutMenuIndex, draftDiningOutMenus.size() - 1)
        );
        diningOutMenuNameInputs.clear();
        diningOutMenuCaloriesInputs.clear();
        diningOutMenuProteinInputs.clear();
        diningOutMenuCarbsInputs.clear();
        diningOutMenuFatInputs.clear();
        diningOutMenuSodiumInputs.clear();
        diningOutMenuSugarsInputs.clear();
        diningOutMenuSaturatedFatInputs.clear();
        diningOutMenusContainer.removeAllViews();
        for (int index = 0; index < draftDiningOutMenus.size(); index++) {
            diningOutMenusContainer.addView(
                    diningOutMenuCard(index),
                    ui().fullWidthParams(ui().dp(8))
            );
        }
    }

    private View diningOutMenuCard(int index) {
        FitnessUi ui = ui();
        DiningOutMenuDraft menu = draftDiningOutMenus.get(index);
        LinearLayout card = ui.card();
        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.text(
                "메뉴 " + (index + 1),
                14,
                FitnessUi.COLOR_TEXT,
                true
        ), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (draftDiningOutMenus.size() > 1) {
            header.addView(ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE, () -> {
                syncDraftFromViews();
                draftDiningOutMenus.remove(index);
                activeDiningOutMenuIndex = Math.min(
                        activeDiningOutMenuIndex,
                        Math.max(0, draftDiningOutMenus.size() - 1)
                );
                rerenderDiningOutFromDraft();
            }));
        }
        card.addView(header, ui.fullWidthParams(ui.dp(4)));

        EditText name = ui.input("먹은 메뉴", menu.name);
        name.setSingleLine(true);
        name.setContentDescription("외식 메뉴 " + (index + 1) + " 이름");
        diningOutMenuNameInputs.add(name);
        card.addView(formSystem.field("메뉴명 *", name), ui.fullWidthParams(ui.dp(6)));

        LinearLayout identityActions = new LinearLayout(host.activity());
        identityActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView priceTraceAction = ui.textAction(
                "PT 메뉴 선택",
                FitnessUi.COLOR_TERTIARY,
                () -> {
                    syncDraftFromViews();
                    activeDiningOutMenuIndex = index;
                    showPriceTraceDiningOutPicker();
                }
        );
        priceTraceAction.setContentDescription(
                "외식 메뉴 " + (index + 1) + " PT 메뉴 선택"
        );
        identityActions.addView(priceTraceAction);
        TextView savedMenuAction = ui.textAction(
                "저장 메뉴 불러오기",
                FitnessUi.COLOR_TERTIARY,
                () -> {
                    syncDraftFromViews();
                    activeDiningOutMenuIndex = index;
                    showSavedDiningOutPicker();
                }
        );
        savedMenuAction.setContentDescription(
                "외식 메뉴 " + (index + 1) + " 저장 메뉴 불러오기"
        );
        identityActions.addView(savedMenuAction);
        card.addView(identityActions, ui.fullWidthParams(ui.dp(4)));
        if (menu.hasExactIdentity()) {
            card.addView(ui.text(
                    "PriceTrace 연결 정보 확인됨",
                    11,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ), ui.fullWidthParams(ui.dp(2)));
        }

        EditText calories = ui.numberInput("kcal", menu.calories);
        EditText carbs = ui.decimalInput("g", menu.carbs);
        EditText protein = ui.decimalInput("g", menu.protein);
        EditText fat = ui.decimalInput("g", menu.fat);
        EditText sugars = ui.decimalInput("g", menu.sugars);
        EditText saturatedFat = ui.decimalInput("g", menu.saturatedFat);
        EditText sodium = ui.decimalInput("mg", menu.sodium);
        calories.setContentDescription("외식 메뉴 " + (index + 1) + " 칼로리");
        carbs.setContentDescription("외식 메뉴 " + (index + 1) + " 탄수화물");
        protein.setContentDescription("외식 메뉴 " + (index + 1) + " 단백질");
        fat.setContentDescription("외식 메뉴 " + (index + 1) + " 지방");
        sugars.setContentDescription("외식 메뉴 " + (index + 1) + " 당류");
        saturatedFat.setContentDescription("외식 메뉴 " + (index + 1) + " 포화지방");
        sodium.setContentDescription("외식 메뉴 " + (index + 1) + " 나트륨");
        diningOutMenuCaloriesInputs.add(calories);
        diningOutMenuCarbsInputs.add(carbs);
        diningOutMenuProteinInputs.add(protein);
        diningOutMenuFatInputs.add(fat);
        diningOutMenuSugarsInputs.add(sugars);
        diningOutMenuSaturatedFatInputs.add(saturatedFat);
        diningOutMenuSodiumInputs.add(sodium);
        card.addView(formSystem.sectionTitle("메뉴 영양성분"), ui.fullWidthParams(ui.dp(2)));
        card.addView(formSystem.nutrientInputRow("칼로리 *", "kcal", calories).view(),
                ui.fullWidthParams(ui.dp(2)));
        card.addView(formSystem.nutrientInputRow("탄수화물 *", "g", carbs).view(),
                ui.fullWidthParams(0));
        card.addView(formSystem.nutrientInputRow("단백질 *", "g", protein).view(),
                ui.fullWidthParams(0));
        card.addView(formSystem.nutrientInputRow("지방 *", "g", fat).view(),
                ui.fullWidthParams(0));

        LinearLayout secondaryNutrition = formSystem.column();
        boolean showSecondary = hasAnyText(menu.sugars, menu.saturatedFat, menu.sodium);
        Button secondaryToggle = ui.secondaryButton(
                        showSecondary
                                ? "추가 영양정보 접기" : "추가 영양정보 펼치기",
                null);
        card.addView(formSystem.sectionTitle("추가 영양성분"), ui.fullWidthParams(ui.dp(2)));
        card.addView(secondaryToggle, ui.fullWidthParams(ui.dp(2)));
        secondaryNutrition.setVisibility(showSecondary ? View.VISIBLE : View.GONE);
        secondaryToggle.setOnClickListener(v -> {
            boolean opening = secondaryNutrition.getVisibility() == View.GONE;
            secondaryNutrition.setVisibility(opening ? View.VISIBLE : View.GONE);
            secondaryToggle.setText(opening ? "추가 영양정보 접기" : "추가 영양정보 펼치기");
        });
        secondaryNutrition.addView(
                formSystem.nutrientInputRow("당류", "g", sugars).view(),
                ui.fullWidthParams(0));
        secondaryNutrition.addView(
                formSystem.nutrientInputRow("포화지방", "g", saturatedFat).view(),
                ui.fullWidthParams(0));
        secondaryNutrition.addView(
                formSystem.nutrientInputRow("나트륨", "mg", sodium).view(),
                ui.fullWidthParams(0));
        card.addView(secondaryNutrition, ui.fullWidthParams(0));
        card.addView(ui.textAction(
                "옵션 " + menu.options.size() + "개 편집",
                activeDiningOutMenuIndex == index
                        ? FitnessUi.COLOR_TEXT
                        : FitnessUi.COLOR_TERTIARY,
                () -> {
                    syncDraftFromViews();
                    activeDiningOutMenuIndex = index;
                    rerenderDiningOutFromDraft();
                }
        ), ui.fullWidthParams(ui.dp(4)));
        return card;
    }

    private MealMenuSelection diningOutMenuSelection(
            NutritionFood savedMenu,
            String menuName,
            Integer calories,
            Double protein,
            Double carbs,
            Double fat,
            Double sodium,
            Double sugars,
            Double saturatedFat,
            NutritionProfile profile,
            List<DiningOutOption> options,
            DiningOutIdentity identity
    ) {
        NutritionFood menuFood = savedMenu == null
                ? manualDiningOutMenuFood(
                        menuName,
                        calories,
                        protein,
                        carbs,
                        fat,
                        sodium,
                        sugars,
                        saturatedFat,
                        profile,
                        identity
                )
                : savedMenu;
        return MealMenuSelection.diningOut(
                MealCompositionItem.from(menuFood, menuFood.basisAmount),
                repository().currentUserId(),
                draftDiningOutStoreName,
                options
        );
    }

    private NutritionFood manualDiningOutMenuFood(
            String menuName,
            Integer calories,
            Double protein,
            Double carbs,
            Double fat,
            Double sodium,
            Double sugars,
            Double saturatedFat,
            NutritionProfile baseProfile,
            DiningOutIdentity identity
    ) {
        NutritionProfile profile = diningOutMenuProfile(
                baseProfile,
                calories,
                protein,
                carbs,
                fat,
                sodium,
                sugars,
                saturatedFat
        );
        return NutritionFood.builder()
                .id(null)
                .ownerId(repository().currentUserId())
                .name(MealEntryPolicy.requireDiningOutMenuName(menuName))
                .brand(draftDiningOutStoreName)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1.0, NutritionUnit.SERVING)
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(profile)
                .source(
                        "manual_estimate",
                        identity == null ? "dining_out" : identity.metadataJson()
                )
                .dataVersion(profile.hasAllRequired()
                        ? NutritionFood.DATA_VERSION_REQUIRED_SEVEN
                        : NutritionFood.DATA_VERSION_MACROS_ONLY)
                .build();
    }

    private NutritionProfile diningOutMenuProfile(
            NutritionProfile baseProfile,
            Integer calories,
            Double protein,
            Double carbs,
            Double fat,
            Double sodium,
            Double sugars,
            Double saturatedFat
    ) {
        Double resolvedCalories = calories == null ? null : calories.doubleValue();
        return NutritionProfile.builder()
                .from(baseProfile)
                .value(NutritionProfile.PROTEIN_GRAMS, protein)
                .value(NutritionProfile.CARBS_GRAMS, carbs)
                .value(NutritionProfile.FAT_GRAMS, fat)
                .value(NutritionProfile.SODIUM_MG, sodium)
                .value(NutritionProfile.SUGARS_GRAMS, sugars)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, saturatedFat)
                .value(NutritionProfile.CALORIES_KCAL, resolvedCalories)
                .build();
    }

    private View diningOutOptionsSection() {
        FitnessUi ui = ui();
        LinearLayout section = new LinearLayout(host.activity());
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.text(
                "메뉴 " + (activeDiningOutMenuIndex + 1) + " 옵션",
                14,
                FitnessUi.COLOR_TEXT,
                true
        ),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(ui.textAction("템플릿 불러오기", FitnessUi.COLOR_TERTIARY, () -> {
            showDiningOutTemplatePicker();
        }));
        header.addView(ui.textAction("구성 추가", FitnessUi.COLOR_TERTIARY, () -> {
            showDiningOutOptionGroupPicker();
        }));
        section.addView(header);

        LinearLayout menuSelector = new LinearLayout(host.activity());
        menuSelector.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < draftDiningOutMenus.size(); index++) {
            final int menuIndex = index;
            Button menuButton = ui.filterButton("메뉴 " + (index + 1));
            ui.styleFilterButton(menuButton, activeDiningOutMenuIndex == index);
            menuButton.setOnClickListener(ignored -> {
                syncDraftFromViews();
                activeDiningOutMenuIndex = menuIndex;
                rerenderDiningOutFromDraft();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            params.setMargins(ui.dp(2), 0, ui.dp(2), 0);
            menuSelector.addView(menuButton, params);
        }
        section.addView(menuSelector, ui.fullWidthParams(ui.dp(4)));

        section.addView(ui.text(
                "구성품마다 종류·제공 방식과 내 섭취 비율을 지정할 수 있습니다. 제공 방식은 이번 기록에만 적용됩니다.",
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
        LinearLayout body = new LinearLayout(host.activity());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(ui.text(
                "고정 메뉴와 그룹별 선택지를 불러옵니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(8)));
        body.addView(rows, ui.fullWidthParams(0));
        ui.bottomSheet("외식 구성 템플릿", body, "닫기", () -> { }, null, null);
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
            List<Button> memberButtons = new ArrayList<>();
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
                    } else {
                        if (selected.contains(member)) {
                            selected.remove(member);
                        } else {
                            selected.add(member);
                        }
                    }
                    for (int index = 0; index < memberButtons.size(); index++) {
                        Button candidate = memberButtons.get(index);
                        CompositionMember candidateMember = group.members.get(index);
                        boolean candidateSelected = selected.contains(candidateMember);
                        candidate.setText(candidateSelected
                                ? "✓ " + candidateMember.name
                                : candidateMember.name + " · "
                                + Math.round(candidateMember.profile.calories()) + "kcal");
                        ui.styleSelection(candidate, candidateSelected, ui.dp(12));
                    }
                });
                memberButtons.add(memberButton);
                panel.addView(memberButton, ui.fullWidthParams(ui.dp(3)));
            }
        }
        ui.validatedSheet(template.name, panel, "적용", () -> {
                    for (CompositionGroup group : template.groups) {
                        List<CompositionMember> selected = selectedByGroup.get(group.key);
                        int count = selected == null ? 0 : selected.size();
                        if (count < group.minSelected || count > group.maxSelected) {
                            host.toast(group.label + " 선택을 확인하세요.");
                            return false;
                        }
                    }
                    applyDiningOutTemplate(template, selectedByGroup);
                    return true;
                });
    }

    private void applyDiningOutTemplate(
            CompositionTemplate template,
            Map<String, List<CompositionMember>> selectedByGroup
    ) {
        DiningOutMenuDraft menu = activeDiningOutMenu();
        NutritionFood rootFood = template.rootFoodId == null
                ? null
                : host.nutritionCatalogRepository().findFoodById(template.rootFoodId);
        String templateStoreName;
        String templateMenuName;
        NutritionProfile templateProfile;
        if (rootFood != null) {
            templateStoreName = rootFood.brand == null ? "" : rootFood.brand;
            templateMenuName = rootFood.name;
            templateProfile = rootFood.profile;
        } else {
            String[] parts = template.name.split(" · ", 2);
            templateStoreName = parts.length > 1 ? parts[0] : template.name;
            templateMenuName = parts.length > 1 ? parts[1] : template.name;
            templateProfile = NutritionProfile.empty();
        }
        String identitySourceReference = rootFood == null
                ? template.sourceReference
                : rootFood.sourceReference;
        String candidateStoreName = diningOutSourceValue(
                identitySourceReference,
                "restaurant_name",
                templateStoreName
        );
        String candidateBranchName = diningOutSourceValue(
                identitySourceReference,
                "branch_name",
                ""
        );
        DiningOutIdentity templateIdentity;
        try {
            templateIdentity = diningOutIdentityFromSourceReference(
                    identitySourceReference,
                    candidateStoreName,
                    candidateBranchName,
                    templateMenuName
            );
        } catch (IllegalArgumentException error) {
            host.toast(error.getMessage());
            return;
        }
        try {
            applyDiningOutRestaurantScope(
                    menu,
                    templateIdentity,
                    candidateStoreName,
                    candidateBranchName
            );
        } catch (IllegalArgumentException error) {
            host.toast(error.getMessage());
            return;
        }
        menu.name = templateMenuName;
        applyDiningOutNutrition(menu, templateProfile);
        applyDiningOutIdentity(menu, templateIdentity);
        menu.options.clear();
        for (CompositionGroup group : template.groups) {
            List<CompositionMember> selected = selectedByGroup.get(group.key);
            if (selected == null) {
                continue;
            }
            for (CompositionMember member : selected) {
                DiningOutOptionDraft draft = new DiningOutOptionDraft();
                draft.profile = member.profile == null
                        ? NutritionProfile.empty()
                        : member.profile;
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
                draft.provisionType = DiningOutProvisionType.defaultProvisionForGroup(
                        draft.groupType
                ).value();
                draft.sodium = knownNumber(member.profile, NutritionProfile.SODIUM_MG);
                draft.sugars = knownNumber(member.profile, NutritionProfile.SUGARS_GRAMS);
                draft.saturatedFat = knownNumber(
                        member.profile,
                        NutritionProfile.SATURATED_FAT_GRAMS
                );
                menu.options.add(draft);
            }
        }
        updateDiningOutSelectionSummary();
        rerenderDiningOutFromDraft();
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

    private DiningOutIdentity diningOutIdentityFromSourceReference(
            String sourceReference,
            String fallbackStoreName,
            String fallbackBranchName,
            String fallbackMenuName
    ) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject source = new JSONObject(sourceReference);
            String restaurantId = jsonValue(source, "restaurant_id");
            String locationId = jsonValue(source, "restaurant_location_id");
            String menuId = jsonValue(source, "restaurant_menu_id");
            String productId = jsonValue(source, "catalog_product_id");
            boolean hasAnyIdentity = !restaurantId.isEmpty()
                    || !locationId.isEmpty()
                    || !menuId.isEmpty()
                    || !productId.isEmpty();
            boolean hasCompleteIdentity = !restaurantId.isEmpty()
                    && !locationId.isEmpty()
                    && !menuId.isEmpty()
                    && !productId.isEmpty();
            if (!hasAnyIdentity) {
                return null;
            }
            if (!hasCompleteIdentity) {
                throw new IllegalArgumentException(
                        "저장된 외식 메뉴의 PriceTrace 연결 정보가 일부만 있습니다."
                );
            }
            String sourceNamespace = jsonValue(source, "namespace");
            if (sourceNamespace.isEmpty()) {
                sourceNamespace = jsonValue(source, "source_namespace");
            }
            if (sourceNamespace.isEmpty()) {
                sourceNamespace = DiningOutIdentity.NAMESPACE;
            }
            return DiningOutIdentity.fromPriceTrace(
                    restaurantId,
                    diningOutSourceValue(source, "restaurant_name", fallbackStoreName),
                    locationId,
                    sourceNamespace,
                    jsonValue(source, "source_location_code"),
                    diningOutSourceValue(source, "branch_name", fallbackBranchName),
                    menuId,
                    diningOutSourceValue(source, "menu_name", fallbackMenuName),
                    productId
            );
        } catch (org.json.JSONException ignored) {
            // Legacy or malformed references are local, identity-less selections.
            return null;
        }
    }

    private String diningOutSourceValue(
            String sourceReference,
            String key,
            String fallback
    ) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return fallback == null ? "" : fallback.trim();
        }
        try {
            return diningOutSourceValue(
                    new JSONObject(sourceReference),
                    key,
                    fallback
            );
        } catch (Exception ignored) {
            return fallback == null ? "" : fallback.trim();
        }
    }

    private String diningOutSourceValue(
            JSONObject source,
            String key,
            String fallback
    ) {
        String value = jsonValue(source, key);
        return value.isEmpty() ? (fallback == null ? "" : fallback.trim()) : value;
    }

    private void applyDiningOutIdentity(
            DiningOutMenuDraft menu,
            DiningOutIdentity identity
    ) {
        clearDiningOutPriceTraceIdentity(menu);
        if (identity == null) {
            return;
        }
        menu.restaurantId = identity.restaurantId;
        menu.restaurantLocationId = identity.restaurantLocationId;
        menu.sourceNamespace = identity.sourceNamespace == null
                ? "" : identity.sourceNamespace;
        menu.sourceLocationCode = identity.sourceLocationCode == null
                ? "" : identity.sourceLocationCode;
        menu.restaurantMenuId = identity.restaurantMenuId;
        menu.catalogProductId = identity.catalogProductId;
    }

    private void applyDiningOutRestaurantScope(
            DiningOutMenuDraft target,
            DiningOutIdentity candidate,
            String candidateStoreName,
            String candidateBranchName
    ) {
        DiningOutIdentity existingScope = diningOutRestaurantScopeExcluding(target);
        validateDiningOutIdentityScope(existingScope, candidate);
        if (existingScope != null) {
            return;
        }
        if (candidate != null) {
            draftDiningOutStoreName = candidate.restaurantName;
            draftDiningOutBranchName = candidate.branchName == null
                    ? "" : candidate.branchName;
        } else {
            draftDiningOutStoreName = candidateStoreName == null
                    ? "" : candidateStoreName.trim();
            draftDiningOutBranchName = candidateBranchName == null
                    ? "" : candidateBranchName.trim();
        }
    }

    private DiningOutIdentity diningOutRestaurantScopeExcluding(
            DiningOutMenuDraft excludedMenu
    ) {
        DiningOutIdentity scope = null;
        for (DiningOutMenuDraft existing : draftDiningOutMenus) {
            if (existing == excludedMenu || !existing.hasAnyIdentity()) {
                continue;
            }
            DiningOutIdentity identity = selectedDiningOutIdentity(existing);
            if (identity == null) {
                continue;
            }
            validateDiningOutIdentityScope(scope, identity);
            if (scope == null) {
                scope = identity;
            }
        }
        return scope;
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

    private NutritionProfile diningOutOptionProfile(
            DiningOutOptionDraft draft,
            boolean strict
    ) {
        NutritionProfile base = draft == null || draft.profile == null
                ? NutritionProfile.empty()
                : draft.profile;
        return NutritionProfile.builder()
                .from(base)
                .value(NutritionProfile.CALORIES_KCAL, optionNutrientValue(
                        draft == null ? null : draft.calories,
                        "칼로리",
                        strict
                ))
                .value(NutritionProfile.CARBS_GRAMS, optionNutrientValue(
                        draft == null ? null : draft.carbs,
                        "탄수화물",
                        strict
                ))
                .value(NutritionProfile.PROTEIN_GRAMS, optionNutrientValue(
                        draft == null ? null : draft.protein,
                        "단백질",
                        strict
                ))
                .value(NutritionProfile.FAT_GRAMS, optionNutrientValue(
                        draft == null ? null : draft.fat,
                        "지방",
                        strict
                ))
                .value(NutritionProfile.SUGARS_GRAMS, optionNutrientValue(
                        draft == null ? null : draft.sugars,
                        "당류",
                        strict
                ))
                .value(NutritionProfile.SATURATED_FAT_GRAMS, optionNutrientValue(
                        draft == null ? null : draft.saturatedFat,
                        "포화지방",
                        strict
                ))
                .value(NutritionProfile.SODIUM_MG, optionNutrientValue(
                        draft == null ? null : draft.sodium,
                        "나트륨",
                        strict
                ))
                .build();
    }

    private Double optionNutrientValue(String raw, String label, boolean strict) {
        if (strict) {
            return MealEntryPolicy.optionalDiningOutMacro(raw, label);
        }
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(normalized);
            return Double.isNaN(value) || Double.isInfinite(value) || value < 0d
                    ? null
                    : value;
        } catch (NumberFormatException ignored) {
            return null;
        }
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
                "저장 메뉴의 영양정보를 이번 기록의 기본 영양성분으로 사용합니다. 이번에 선택한 옵션만 별도로 합산됩니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.fullWidthParams(ui.dp(8)));

        final Dialog[] dialogHolder = new Dialog[1];
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
        Dialog dialog = ui.bottomSheet("내 외식 불러오기", scroll, "닫기", () -> { }, null, null);
        dialogHolder[0] = dialog;
    }

    private String savedDiningOutMenuLabel(NutritionFood menu) {
        String storeName = menu.brand == null ? "가게 미기록" : menu.brand;
        return storeName + " · " + menu.name + "\n" + menu.nutritionLabel();
    }

    private void applySavedDiningOutMenu(NutritionFood menu) {
        if (!applyDiningOutMenuFood(menu)) {
            return;
        }
        updateDiningOutSelectionSummary();
        host.toast("저장한 외식 메뉴를 불러왔습니다. 섭취량을 확인한 뒤 기록하세요.");
        rerenderDiningOutFromDraft();
    }

    /** Applies one saved dining-out menu to the existing meal-specific editor draft. */
    private boolean applyDiningOutMenuFood(NutritionFood menu) {
        if (menu == null || !menu.isDiningOutMenu()) {
            host.toast("외식 메뉴만 외식 입력으로 불러올 수 있습니다.");
            return false;
        }
        DiningOutMenuDraft draft = activeDiningOutMenu();
        String fallbackStoreName = menu.brand == null ? "" : menu.brand;
        String candidateStoreName = diningOutSourceValue(
                menu.sourceReference,
                "restaurant_name",
                fallbackStoreName
        );
        String candidateBranchName = diningOutSourceValue(
                menu.sourceReference,
                "branch_name",
                ""
        );
        DiningOutIdentity savedIdentity;
        try {
            savedIdentity = diningOutIdentityFromSourceReference(
                    menu.sourceReference,
                    candidateStoreName,
                    candidateBranchName,
                    menu.name
            );
        } catch (IllegalArgumentException error) {
            host.toast(error.getMessage());
            return false;
        }
        try {
            applyDiningOutRestaurantScope(
                    draft,
                    savedIdentity,
                    candidateStoreName,
                    candidateBranchName
            );
        } catch (IllegalArgumentException error) {
            host.toast(error.getMessage());
            return false;
        }
        applyDiningOutIdentity(draft, savedIdentity);
        draft.name = menu.name;
        draft.catalogFoodId = menu.id == null ? "" : menu.id;
        draft.options.clear();
        for (NutritionFood component : host.nutritionCatalogRepository()
                .diningOutComponentsForMenu(menu.id)) {
            draft.options.add(savedDiningOutOptionDraft(component));
        }
        applyDiningOutNutrition(draft, menu.profile);
        return true;
    }

    /** Routes a catalog discovery click into the dining-out semantic write workflow. */
    private void openDiningOutMenuFromCatalog(NutritionFood menu) {
        syncDraftFromViews();
        if (!applyDiningOutMenuFood(menu)) {
            return;
        }
        mealEntryMode = MEAL_ENTRY_MODE_DINING_OUT;
        mealWorkspaceVisible = true;
        updateDiningOutSelectionSummary();
        host.toast("식단 카탈로그의 식당 메뉴를 외식 입력으로 불러왔습니다.");
        rerenderDiningOutFromDraft();
    }

    private void applyDiningOutNutrition(
            DiningOutMenuDraft menu,
            NutritionProfile profile
    ) {
        menu.profile = profile == null ? NutritionProfile.empty() : profile;
        menu.calories = knownNumber(profile, NutritionProfile.CALORIES_KCAL);
        menu.protein = knownNumber(profile, NutritionProfile.PROTEIN_GRAMS);
        menu.carbs = knownNumber(profile, NutritionProfile.CARBS_GRAMS);
        menu.fat = knownNumber(profile, NutritionProfile.FAT_GRAMS);
        menu.sodium = knownNumber(profile, NutritionProfile.SODIUM_MG);
        menu.sugars = knownNumber(profile, NutritionProfile.SUGARS_GRAMS);
        menu.saturatedFat = knownNumber(
                profile,
                NutritionProfile.SATURATED_FAT_GRAMS
        );
    }

    private DiningOutOptionDraft savedDiningOutOptionDraft(NutritionFood component) {
        DiningOutOptionDraft draft = new DiningOutOptionDraft();
        if (component == null) {
            return draft;
        }
        draft.profile = component.profile == null
                ? NutritionProfile.empty()
                : component.profile;
        draft.groupType = savedDiningOutOptionGroupType(component.sourceReference);
        draft.groupKey = savedDiningOutOptionGroupKey(component.sourceReference);
        draft.name = component.name == null ? "" : component.name;
        draft.catalogFoodId = component.id == null ? "" : component.id;
        draft.sourceReference = component.sourceReference == null
                ? "" : component.sourceReference;
        draft.calories = knownNumber(draft.profile, NutritionProfile.CALORIES_KCAL);
        draft.protein = knownNumber(draft.profile, NutritionProfile.PROTEIN_GRAMS);
        draft.carbs = knownNumber(draft.profile, NutritionProfile.CARBS_GRAMS);
        draft.fat = knownNumber(draft.profile, NutritionProfile.FAT_GRAMS);
        draft.sodium = knownNumber(draft.profile, NutritionProfile.SODIUM_MG);
        draft.sugars = knownNumber(draft.profile, NutritionProfile.SUGARS_GRAMS);
        draft.saturatedFat = knownNumber(
                draft.profile,
                NutritionProfile.SATURATED_FAT_GRAMS
        );
        // Provision is an actual-meal snapshot property, never a reusable component default.
        draft.provisionType = DiningOutProvisionType.defaultProvisionForGroup(
                draft.groupType
        ).value();
        return draft;
    }

    private void showDiningOutOptionGroupPicker() {
        final CompositionGroupType[] types = CompositionGroupType.values();
        final String[] labels = new String[types.length];
        for (int index = 0; index < types.length; index++) {
            labels[index] = CompositionGroupType.labelOf(types[index].value());
        }
        ui().choiceSheet("종류 선택", Arrays.asList(labels), -1, which -> {
            if (which >= 0 && which < types.length) {
                showDiningOutOptionPicker(-1, types[which].value());
            }
        });
    }

    private void showDiningOutOptionPicker(int replacementIndex) {
        DiningOutMenuDraft menu = activeDiningOutMenu();
        String groupType = replacementIndex >= 0 && replacementIndex < menu.options.size()
                ? menu.options.get(replacementIndex).groupType : CompositionGroupType.OTHER.value();
        showDiningOutOptionPicker(replacementIndex, groupType);
    }

    private void showDiningOutOptionPicker(int replacementIndex, String requestedGroupType) {
        syncDraftFromViews();
        FitnessUi ui = ui();
        DiningOutMenuDraft menu = activeDiningOutMenu();
        String selectedGroupType = replacementIndex >= 0
                && replacementIndex < menu.options.size()
                ? CompositionGroupType.normalize(
                        menu.options.get(replacementIndex).groupType
                )
                : CompositionGroupType.normalize(requestedGroupType);
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
        EditText query = ui.searchInput("저장 옵션 이름 검색");
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

        final Dialog[] dialogHolder = new Dialog[1];
        Button manualInput = ui.button(
                replacementIndex >= 0 ? "직접 입력으로 변경" : "저장 옵션 없이 직접 입력",
                false,
                null
        );
        manualInput.setOnClickListener(v -> {
            DiningOutOptionDraft manualDraft = new DiningOutOptionDraft();
            manualDraft.groupType = selectedGroupType;
            manualDraft.provisionType = DiningOutProvisionType.defaultProvisionForGroup(
                    selectedGroupType
            ).value();
            if (replacementIndex >= 0 && replacementIndex < menu.options.size()) {
                menu.options.set(replacementIndex, manualDraft);
            } else {
                menu.options.add(manualDraft);
            }
            if (dialogHolder[0] != null) {
                dialogHolder[0].dismiss();
            }
            rerenderDiningOutFromDraft();
        });
        panel.addView(manualInput, ui.fullWidthParams(ui.dp(6)));
        Button directInputAlias = ui.button("직접 입력", false, v -> manualInput.performClick());
        directInputAlias.setContentDescription("직접 입력");
        panel.addView(directInputAlias, ui.fullWidthParams(ui.dp(6)));


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
                String normalizedQuery = FitnessUi.inputText(query).trim().toLowerCase(Locale.ROOT);
                List<NutritionFood> options = new ArrayList<>();
                if (!menu.catalogFoodId.trim().isEmpty()) {
                    List<NutritionFood> linked = host.nutritionCatalogRepository()
                            .diningOutComponentsForMenu(menu.catalogFoodId, selectedGroupType);
                    for (NutritionFood option : linked) {
                        if (matchesDiningOutComponentQuery(option, normalizedQuery)
                                && !containsFood(options, option.id)) {
                            options.add(option);
                        }
                    }
                }
                List<NutritionFood> saved = host.nutritionCatalogRepository()
                        .savedDiningOutComponents(
                                currentStoreName,
                                identity,
                                selectedGroupType,
                                normalizedQuery,
                                SAVED_DINING_OUT_OPTION_RESULT_LIMIT
                        );
                for (NutritionFood option : saved) {
                    if (!containsFood(options, option.id)) {
                        options.add(option);
                    }
                }

                // A saved component is reusable nutrition data, not a future selection
                // instruction. The actual-meal default follows its selected group.
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
                                    selectedGroupType,
                                    dialogHolder[0]
                            )
                    );
                    optionButton.setContentDescription(option.name + " 저장 옵션 선택");
                    results.addView(optionButton, ui.fullWidthParams(ui.dp(4)));
                }
            } catch (Exception error) {
                status.setText("저장 옵션을 불러오지 못했습니다. 직접 입력할 수 있습니다.");
            }
        });

        Dialog dialog = ui.bottomSheet(
                replacementIndex >= 0
                        ? "외식 옵션 변경 · " + CompositionGroupType.labelOf(selectedGroupType)
                        : "외식 옵션 추가",
                panel,
                "닫기",
                () -> { },
                null,
                null
        );
        dialogHolder[0] = dialog;
        query.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            );
        }
        // The group was selected before this dialog, so an empty query lists only that group.
        search.performClick();
    }

    private void selectDiningOutOption(
            int replacementIndex,
            NutritionFood food,
            String requestedGroupType,
            Dialog dialog
    ) {
        DiningOutMenuDraft menu = activeDiningOutMenu();
        DiningOutOptionDraft selectedDraft = new DiningOutOptionDraft();
        selectedDraft.profile = food.profile;
        selectedDraft.groupType = savedDiningOutOptionGroupType(
                food.sourceReference,
                requestedGroupType
        );
        selectedDraft.groupKey = savedDiningOutOptionGroupKey(food.sourceReference);
        selectedDraft.name = food.name;
        selectedDraft.catalogFoodId = food.id;
        selectedDraft.sourceReference = food.sourceReference;
        selectedDraft.calories = knownNumber(food.profile, NutritionProfile.CALORIES_KCAL);
        selectedDraft.protein = knownNumber(food.profile, NutritionProfile.PROTEIN_GRAMS);
        selectedDraft.carbs = knownNumber(food.profile, NutritionProfile.CARBS_GRAMS);
        selectedDraft.fat = knownNumber(food.profile, NutritionProfile.FAT_GRAMS);
        // A saved component is reusable nutrition data. Its new actual-meal default follows the
        // selected group; the user can change the provision type afterward.
        selectedDraft.provisionType = DiningOutProvisionType.defaultProvisionForGroup(
                selectedDraft.groupType
        ).value();
        selectedDraft.sodium = knownNumber(food.profile, NutritionProfile.SODIUM_MG);
        selectedDraft.sugars = knownNumber(food.profile, NutritionProfile.SUGARS_GRAMS);
        selectedDraft.saturatedFat = knownNumber(
                food.profile,
                NutritionProfile.SATURATED_FAT_GRAMS
        );
        if (replacementIndex >= 0 && replacementIndex < menu.options.size()) {
            menu.options.set(replacementIndex, selectedDraft);
        } else {
            menu.options.add(selectedDraft);
        }
        dialog.dismiss();
        rerenderDiningOutFromDraft();
    }

    private boolean matchesDiningOutComponentQuery(NutritionFood food, String normalizedQuery) {
        if (food == null || food.name == null) {
            return false;
        }
        return normalizedQuery == null
                || normalizedQuery.isEmpty()
                || food.name.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private boolean containsFood(List<NutritionFood> foods, String foodId) {
        for (NutritionFood food : foods) {
            if (food != null && food.id != null && food.id.equals(foodId)) {
                return true;
            }
        }
        return false;
    }

    private String savedDiningOutOptionGroupType(String sourceReference) {
        return savedDiningOutOptionGroupType(sourceReference, CompositionGroupType.OTHER.value());
    }

    private String savedDiningOutOptionGroupType(
            String sourceReference,
            String fallbackGroupType
    ) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return CompositionGroupType.normalize(fallbackGroupType);
        }
        try {
            JSONObject source = new JSONObject(sourceReference);
            String type = jsonValue(source, "composition_group_type");
            if (!type.isEmpty()) {
                return CompositionGroupType.normalize(type);
            }
            String label = jsonValue(source, "composition_group_label");
            return label.isEmpty()
                    ? CompositionGroupType.normalize(fallbackGroupType)
                    : CompositionGroupType.normalize(label);
        } catch (Exception ignored) {
            return CompositionGroupType.normalize(fallbackGroupType);
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
        EditText query = ui.searchInput("식당명 또는 지점명 검색");
        panel.addView(query, ui.fullWidthParams(ui.dp(6)));
        TextView status = ui.text("1. 식당을 검색하세요.", 12, FitnessUi.COLOR_TERTIARY, false);
        panel.addView(status, ui.fullWidthParams(ui.dp(6)));
        LinearLayout restaurants = new LinearLayout(host.activity());
        restaurants.setOrientation(LinearLayout.VERTICAL);
        panel.addView(restaurants, ui.fullWidthParams(ui.dp(4)));
        LinearLayout detail = new LinearLayout(host.activity());
        detail.setOrientation(LinearLayout.VERTICAL);
        panel.addView(detail, ui.fullWidthParams(ui.dp(8)));

        final Dialog[] dialogHolder = new Dialog[1];
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
                                            dialogHolder[0]
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
        Dialog dialog = ui.bottomSheet("외식 3단 구조 선택", panel, "닫기", () -> { }, null, null);
        dialogHolder[0] = dialog;
        search.performClick();
    }

    private void loadPriceTraceDiningOutDetail(
            String restaurantId,
            TextView status,
            LinearLayout detail,
            Dialog dialog
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
                                    ui.styleSelection(child, child == ignored, ui.dp(12));
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
                            DiningOutMenuDraft selectedMenu = activeDiningOutMenu();
                            DiningOutIdentity selectedIdentity = DiningOutIdentity.fromPriceTrace(
                                    value.restaurantId,
                                    value.restaurantName,
                                    location.restaurantLocationId,
                                    location.sourceNamespace == null ? DiningOutIdentity.NAMESPACE
                                            : location.sourceNamespace,
                                    location.sourceLocationCode,
                                    location.branchName,
                                    menu.restaurantMenuId,
                                    menu.menuName,
                                    menu.catalogProductId
                            );
                            for (DiningOutMenuDraft existing : draftDiningOutMenus) {
                                if (existing == selectedMenu || !existing.hasExactIdentity()) {
                                    continue;
                                }
                                DiningOutIdentity existingIdentity = selectedDiningOutIdentity(existing);
                                if (existingIdentity != null
                                        && !existingIdentity.hasSameRestaurantLocation(selectedIdentity)) {
                                    host.toast("한 외식 기록에는 같은 식당·지점의 메뉴만 추가할 수 있습니다.");
                                    return;
                                }
                            }
                            draftDiningOutStoreName = value.restaurantName;
                            draftDiningOutBranchName = location.branchName == null
                                    ? "" : location.branchName;
                            selectedMenu.name = menu.menuName;
                            selectedMenu.restaurantId = value.restaurantId;
                            selectedMenu.restaurantLocationId = location.restaurantLocationId;
                            selectedMenu.sourceNamespace = location.sourceNamespace == null
                                    ? "" : location.sourceNamespace;
                            selectedMenu.sourceLocationCode = location.sourceLocationCode == null
                                    ? "" : location.sourceLocationCode;
                            selectedMenu.restaurantMenuId = menu.restaurantMenuId;
                            selectedMenu.catalogProductId = menu.catalogProductId;
                            updateDiningOutSelectionSummary();
                            host.toast("PriceTrace 식당·지점·메뉴를 정확히 연결했습니다.");
                            dialog.dismiss();
                            rerenderDiningOutFromDraft();
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

    private void renderDiningOutOptionRows() {
        if (diningOutOptionsContainer == null) {
            return;
        }
        List<DiningOutOptionDraft> options = activeDiningOutMenu().options;
        FitnessUi ui = ui();
        diningOutOptionsContainer.removeAllViews();
        diningOutOptionInputs.clear();
        diningOutOptionGroupInputs.clear();
        diningOutOptionCaloriesInputs.clear();
        diningOutOptionProteinInputs.clear();
        diningOutOptionCarbsInputs.clear();
        diningOutOptionFatInputs.clear();
        diningOutOptionSodiumInputs.clear();
        diningOutOptionSugarsInputs.clear();
        diningOutOptionSaturatedFatInputs.clear();
        diningOutOptionConsumedPercentInputs.clear();
        diningOutOptionProvisionInputs.clear();
        if (options.isEmpty()) {
            diningOutOptionsContainer.addView(ui.text(
                    "추가 옵션 없음",
                    12,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
            return;
        }

        for (int index = 0; index < options.size(); index++) {
            final int optionIndex = index;
            DiningOutOptionDraft draft = options.get(index);
            LinearLayout row = new LinearLayout(host.activity());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Button group = formSystem.selector(
                    "구성 그룹: " + CompositionGroupType.labelOf(draft.groupType),
                    ignored -> showDiningOutGroupTypePicker(optionIndex)
            );
            group.setContentDescription("외식 옵션 그룹 " + (index + 1));
            diningOutOptionGroupInputs.add(group);
            row.addView(group, ui.fullWidthParams(ui.dp(2)));
            Button provision = formSystem.selector(
                    "제공 방식: " + DiningOutProvisionType.labelOf(draft.provisionType),
                    ignored -> showDiningOutProvisionTypePicker(optionIndex)
            );
            provision.setContentDescription("외식 구성품 제공 방식 " + (index + 1));
            diningOutOptionProvisionInputs.add(provision);
            row.addView(provision, ui.fullWidthParams(ui.dp(2)));

            EditText input = ui.input(
                    "옵션명 (예: 면 추가)",
                    draft.name
            );
            input.setSingleLine(true);
            input.setContentDescription("외식 옵션 " + (index + 1));
            diningOutOptionInputs.add(input);
            row.addView(formSystem.field("옵션명", input), ui.fullWidthParams(ui.dp(2)));

            EditText calories = ui.decimalInput("kcal", draft.calories);
            EditText carbs = ui.decimalInput("g", draft.carbs);
            EditText protein = ui.decimalInput("g", draft.protein);
            EditText fat = ui.decimalInput("g", draft.fat);
            EditText sugars = ui.decimalInput("g", draft.sugars);
            EditText saturatedFat = ui.decimalInput("g", draft.saturatedFat);
            EditText sodium = ui.decimalInput("mg", draft.sodium);
            calories.setContentDescription("외식 옵션 " + (index + 1) + " 칼로리");
            carbs.setContentDescription("외식 옵션 " + (index + 1) + " 탄수화물");
            protein.setContentDescription("외식 옵션 " + (index + 1) + " 단백질");
            fat.setContentDescription("외식 옵션 " + (index + 1) + " 지방");
            sugars.setContentDescription("외식 옵션 " + (index + 1) + " 당류");
            saturatedFat.setContentDescription("외식 옵션 " + (index + 1) + " 포화지방");
            sodium.setContentDescription("외식 옵션 " + (index + 1) + " 나트륨");
            diningOutOptionCaloriesInputs.add(calories);
            diningOutOptionCarbsInputs.add(carbs);
            diningOutOptionProteinInputs.add(protein);
            diningOutOptionFatInputs.add(fat);
            diningOutOptionSugarsInputs.add(sugars);
            diningOutOptionSaturatedFatInputs.add(saturatedFat);
            diningOutOptionSodiumInputs.add(sodium);
            row.addView(ui.text(
                    "구성 상태: " + DiningOutProvisionType.labelOf(draft.provisionType),
                    12,
                    FitnessUi.COLOR_TEXT,
                    true
            ), ui.fullWidthParams(ui.dp(3)));

            LinearLayout nutritionFields = formSystem.column();
            boolean showNutrition = hasAnyText(
                    draft.calories,
                    draft.carbs,
                    draft.protein,
                    draft.fat,
                    draft.sugars,
                    draft.saturatedFat,
                    draft.sodium
            );
            Button nutritionToggle = ui.secondaryButton(
                    showNutrition ? "영양정보 접기" : "영양정보 입력 열기",
                    null
            );
            nutritionFields.setVisibility(showNutrition ? View.VISIBLE : View.GONE);
            nutritionToggle.setOnClickListener(ignored -> {
                boolean opening = nutritionFields.getVisibility() == View.GONE;
                nutritionFields.setVisibility(opening ? View.VISIBLE : View.GONE);
                nutritionToggle.setText(opening ? "영양정보 접기" : "영양정보 입력 열기");
            });
            row.addView(nutritionToggle, ui.fullWidthParams(ui.dp(3)));
            nutritionFields.addView(
                    formSystem.nutrientInputRow("칼로리", "kcal", calories).view(),
                    ui.fullWidthParams(0));
            nutritionFields.addView(
                    formSystem.nutrientInputRow("탄수화물", "g", carbs).view(),
                    ui.fullWidthParams(0));
            nutritionFields.addView(
                    formSystem.nutrientInputRow("단백질", "g", protein).view(),
                    ui.fullWidthParams(0));
            nutritionFields.addView(
                    formSystem.nutrientInputRow("지방", "g", fat).view(),
                    ui.fullWidthParams(0));
            nutritionFields.addView(
                    formSystem.nutrientInputRow("당류", "g", sugars).view(),
                    ui.fullWidthParams(0));
            nutritionFields.addView(
                    formSystem.nutrientInputRow("포화지방", "g", saturatedFat).view(),
                    ui.fullWidthParams(0));
            nutritionFields.addView(
                    formSystem.nutrientInputRow("나트륨", "mg", sodium).view(),
                    ui.fullWidthParams(0));
            row.addView(nutritionFields, ui.fullWidthParams(0));
            EditText consumedPercent = ui.decimalInput("%", draft.consumedPercent);
            consumedPercent.setContentDescription("외식 옵션 내 섭취 비율 " + (index + 1));
            diningOutOptionConsumedPercentInputs.add(consumedPercent);
            row.addView(
                    formSystem.field("내 섭취 비율 (%)", consumedPercent),
                    ui.fullWidthParams(ui.dp(2))
            );
            LinearLayout actions = new LinearLayout(host.activity());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(ui.textAction("저장 옵션 검색", FitnessUi.COLOR_TERTIARY, () -> {
                syncDraftFromViews();
                showDiningOutOptionPicker(optionIndex);
            }));
            TextView deleteOption = ui.textAction("삭제", FitnessUi.COLOR_NEGATIVE, () -> {
                syncDraftFromViews();
                if (optionIndex < activeDiningOutMenu().options.size()) {
                    activeDiningOutMenu().options.remove(optionIndex);
                }
                rerenderDiningOutFromDraft();
            });
            deleteOption.setContentDescription("외식 옵션 " + (optionIndex + 1) + " 삭제");
            actions.addView(deleteOption);
            row.addView(actions, ui.fullWidthParams(ui.dp(2)));
            diningOutOptionsContainer.addView(row, ui.fullWidthParams(ui.dp(6)));
        }
    }

    private void showDiningOutGroupTypePicker(int optionIndex) {
        syncDraftFromViews();
        List<DiningOutOptionDraft> options = activeDiningOutMenu().options;
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        CompositionGroupType[] types = CompositionGroupType.values();
        String[] labels = CompositionGroupType.labels();
        ui().choiceSheet("외식 구성 그룹", Arrays.asList(labels), -1, which -> {
            if (which >= 0 && which < types.length
                    && optionIndex < activeDiningOutMenu().options.size()) {
                DiningOutOptionDraft draft = activeDiningOutMenu().options.get(optionIndex);
                draft.groupType = types[which].value();
                // A changed type starts a new generated group key. Template-provided
                // keys remain intact when a member is merely reloaded.
                draft.groupKey = "";
                rerenderDiningOutFromDraft();
            }
        });
    }
    private void showDiningOutProvisionTypePicker(int optionIndex) {
        syncDraftFromViews();
        List<DiningOutOptionDraft> options = activeDiningOutMenu().options;
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        DiningOutProvisionType[] types = DiningOutProvisionType.values();
        String[] labels = DiningOutProvisionType.labels();
        ui().choiceSheet("제공 방식 선택", Arrays.asList(labels), -1, which -> {
            if (which >= 0 && which < types.length
                    && optionIndex < activeDiningOutMenu().options.size()) {
                activeDiningOutMenu().options.get(optionIndex).provisionType =
                        types[which].value();
                rerenderDiningOutFromDraft();
            }
        });
    }


    private List<DiningOutOption> parsedDiningOutOptions() {
        return parsedDiningOutOptions(activeDiningOutMenu());
    }

    private List<DiningOutOption> parsedDiningOutOptions(DiningOutMenuDraft menu) {
        List<DiningOutOption> options = new ArrayList<>();
        Map<String, String> generatedGroupKeys = new LinkedHashMap<>();
        for (DiningOutOptionDraft draft : menu.options) {
            String name = draft.name == null ? "" : draft.name.trim();
            if (name.isEmpty()) {
                continue;
            }
            NutritionProfile enteredProfile = diningOutOptionProfile(draft, true);
            double consumedFraction = diningOutOptionConsumedFractionValue(draft.consumedPercent);
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
            options.add(DiningOutOption.grouped(
                    name,
                    enteredProfile,
                    emptyToNull(draft.catalogFoodId),
                    emptyToNull(draft.sourceReference),
                    groupKey,
                    groupType,
                    groupLabel,
                    DiningOutOption.DEFAULT_ROLE,
                    emptyToNull(draft.memberId),
                    DiningOutProvisionType.normalize(draft.provisionType),
                    consumedFraction
            ));
        }
        return options;
    }

    private List<DiningOutOption> saveDiningOutOptions(
            DiningOutMenuDraft menu,
            boolean saveToCatalog,
            DiningOutIdentity identity
    ) {
        List<DiningOutOption> options = parsedDiningOutOptions(menu);
        if (!saveToCatalog) {
            return options;
        }
        List<DiningOutOption> saved = new ArrayList<>();
        for (DiningOutOption option : options) {
            NutritionFood food = host.nutritionCatalogRepository().saveDiningOutComponent(
                    draftDiningOutStoreName,
                    menu.name,
                    identity,
                    option.asComponent()
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
                    option.provisionType,
                    option.consumedFraction
            ));
        }
        return saved;
    }

    private String emptyToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean hasAnyText(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static boolean supportsServingPercentage(NutritionFood food) {
        if (food == null || !NutritionUnit.SERVING.equals(
                NutritionUnit.normalize(food.basisUnit)
        )) {
            return false;
        }
        String kind = NutritionFood.normalizeKind(food.kind);
        return NutritionFood.KIND_RECIPE.equals(kind) || food.isPackagedFood();
    }

    static double quantityForServingPercent(NutritionFood food, double percent) {
        if (food == null || food.basisAmount <= 0d
                || Double.isNaN(percent) || Double.isInfinite(percent) || percent <= 0d) {
            throw new IllegalArgumentException("섭취 비율은 0보다 큰 숫자여야 합니다.");
        }
        return food.basisAmount * percent / 100d;
    }

    static double servingPercentForQuantity(NutritionFood food, double quantity) {
        if (food == null || food.basisAmount <= 0d
                || Double.isNaN(quantity) || Double.isInfinite(quantity) || quantity <= 0d) {
            throw new IllegalArgumentException("섭취량은 0보다 큰 숫자여야 합니다.");
        }
        return quantity * 100d / food.basisAmount;
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
            List<NutritionRow> nutritionRows = new ArrayList<>();
            List<String> nutritionKeys = new ArrayList<>();
            String[] primaryKeys = {
                    NutritionProfile.CALORIES_KCAL,
                    NutritionProfile.CARBS_GRAMS,
                    NutritionProfile.PROTEIN_GRAMS,
                    NutritionProfile.FAT_GRAMS
            };
            for (String key : primaryKeys) {
                NutritionRow nutritionRow = NutritionRow.readOnly(
                        ui,
                        host.activity(),
                        NutritionRow.displayLabel(key),
                        nutritionValue(item.profile, key),
                        NutritionRow.displayUnit(key)
                );
                nutritionRows.add(nutritionRow);
                nutritionKeys.add(key);
                nutrition.addView(nutritionRow.view(), ui.fullWidthParams(0));
            }
            String[] secondaryKeys = {
                    NutritionProfile.SUGARS_GRAMS,
                    NutritionProfile.SATURATED_FAT_GRAMS,
                    NutritionProfile.SODIUM_MG
            };
            LinearLayout secondaryRows = formSystem.column();
            boolean showSecondaryRows = hasKnownNutrition(item.profile, secondaryKeys);
            secondaryRows.setVisibility(showSecondaryRows ? View.VISIBLE : View.GONE);
            Button secondaryRowsToggle = ui.secondaryButton(
                    showSecondaryRows ? "추가 영양정보 접기" : "추가 영양정보 보기",
                    null
            );
            secondaryRowsToggle.setOnClickListener(v -> {
                boolean opening = secondaryRows.getVisibility() == View.GONE;
                secondaryRows.setVisibility(opening ? View.VISIBLE : View.GONE);
                secondaryRowsToggle.setText(opening ? "추가 영양정보 접기" : "추가 영양정보 보기");
            });
            nutrition.addView(secondaryRowsToggle, ui.fullWidthParams(ui.dp(2)));
            for (String key : secondaryKeys) {
                NutritionRow nutritionRow = NutritionRow.readOnly(
                        ui,
                        host.activity(),
                        NutritionRow.displayLabel(key),
                        nutritionValue(item.profile, key),
                        NutritionRow.displayUnit(key)
                );
                nutritionRows.add(nutritionRow);
                nutritionKeys.add(key);
                secondaryRows.addView(nutritionRow.view(), ui.fullWidthParams(0));
            }
            nutrition.addView(secondaryRows, ui.fullWidthParams(0));
            footer.addView(nutrition, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            LinearLayout quantityBlock = new LinearLayout(host.activity());
            quantityBlock.setOrientation(LinearLayout.VERTICAL);
            final EditText servingPercentInput;
            if (supportsServingPercentage(item.food)) {
                servingPercentInput = ui.decimalInput(
                        "%",
                        NutritionCalculator.trim(servingPercentForQuantity(
                                item.food,
                                item.quantity
                        ))
                );
                servingPercentInput.setSelectAllOnFocus(true);
                servingPercentInput.setContentDescription(
                        item.food.displayName() + " 섭취 비율 (%)"
                );
                quantityBlock.addView(
                        formSystem.field("섭취 비율 (%)", servingPercentInput),
                        ui.fullWidthParams(ui.dp(2))
                );
            } else {
                servingPercentInput = null;
            }
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

            final boolean[] syncingServingPercent = {false};
            quantity.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    updateCompositionTotal();
                    if (menuIndex < draftMenus.size()) {
                        MealCompositionItem current = draftMenus.get(menuIndex).menu;
                        for (int nutritionIndex = 0;
                             nutritionIndex < nutritionRows.size();
                             nutritionIndex++) {
                            nutritionRows.get(nutritionIndex).setValue(
                                    nutritionValue(current.profile, nutritionKeys.get(nutritionIndex))
                            );
                        }
                    }
                    if (servingPercentInput != null && !syncingServingPercent[0]) {
                        try {
                            double nextQuantity = Double.parseDouble(text.toString().trim());
                            if (nextQuantity > 0d && !Double.isInfinite(nextQuantity)
                                    && !Double.isNaN(nextQuantity)) {
                                syncingServingPercent[0] = true;
                                servingPercentInput.setText(NutritionCalculator.trim(
                                        servingPercentForQuantity(item.food, nextQuantity)
                                ));
                            }
                        } catch (NumberFormatException ignored) {
                            // Keep the last derived percentage while the quantity is incomplete.
                        } finally {
                            syncingServingPercent[0] = false;
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
            if (servingPercentInput != null) {
                servingPercentInput.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence text,
                            int start,
                            int count,
                            int after
                    ) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence text,
                            int start,
                            int before,
                            int count
                    ) {
                        if (syncingServingPercent[0]) {
                            return;
                        }
                        try {
                            double percent = Double.parseDouble(text.toString().trim());
                            if (percent <= 0d || Double.isNaN(percent)
                                    || Double.isInfinite(percent)) {
                                return;
                            }
                            double nextQuantity = quantityForServingPercent(item.food, percent);
                            syncingServingPercent[0] = true;
                            if (menuIndex < draftMenus.size()) {
                                draftMenus.set(
                                        menuIndex,
                                        draftMenus.get(menuIndex).withQuantity(nextQuantity)
                                );
                            }
                            quantity.setText(NutritionCalculator.trim(nextQuantity));
                            quantity.setSelection(quantity.length());
                        } catch (NumberFormatException ignored) {
                            // Keep the current quantity while the percentage is incomplete.
                        } finally {
                            syncingServingPercent[0] = false;
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                    }
                });
            }
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

    private String nutritionValue(NutritionProfile profile, String key) {
        if (profile == null || !profile.isKnown(key)) {
            return "?";
        }
        return NutritionCalculator.trim(profile.value(key));
    }

    private boolean hasKnownNutrition(NutritionProfile profile, String[] keys) {
        if (profile == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            if (profile.isKnown(key)) {
                return true;
            }
        }
        return false;
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
        LinearLayout panel = formSystem.column();
        panel.setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14));
        panel.setBackground(ui.flatSurfaceDrawable(ui.dp(14)));
        ui.cardHeader(panel, "직접 만든 메뉴", "메뉴 이름과 재료 구성");

        menuNameInput = ui.input("메뉴 이름 (예: 계란 볶음밥)", draftMenuName);
        panel.addView(formSystem.field("메뉴 이름", menuNameInput));
        panel.addView(formSystem.helper(
                "재료는 아래 카탈로그에서 추가합니다. 수량은 실제 사용량으로 입력하세요."
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

        Button saveAndAdd = ui.secondaryButton(
                "저장하고 추가",
                v -> completeBuiltMenu(true)
        );
        Button addOnce = ui.primaryButton(
                "이 끼니에만 추가",
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

        EditText quantity = formSystem.decimalInput(
                "사용량 (" + NutritionUnit.display(ingredient.food.basisUnit) + ")",
                NutritionCalculator.trim(ingredient.quantity)
        );
        quantity.setSelectAllOnFocus(true);
        ingredientQuantityInputs.add(quantity);
        View quantityField = formSystem.field(
                "사용량 (" + NutritionUnit.display(ingredient.food.basisUnit) + ")",
                quantity
        );
        row.addView(quantityField, new LinearLayout.LayoutParams(
                ui.dp(142),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
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
        if (draftIngredients.isEmpty()) {
            menuBuilderTotalBox.addView(formSystem.helper(
                    "재료를 추가하면 메뉴 합계를 계산합니다."
            ));
            return;
        }
        menuBuilderTotalBox.addView(formSystem.sectionTitle("메뉴 합계"));
        for (String key : NutritionProfile.PRIMARY_DISPLAY_ORDER) {
            menuBuilderTotalBox.addView(
                    formSystem.nutrientRow(
                            key,
                            NutritionCalculator.describeTotal(total.total(key))
                    ),
                    ui().fullWidthParams(0)
            );
        }
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
        catalogSearchInput = ui.searchInput("식품명 또는 상품명 검색");
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

        verifiedSingleFoodSearchInput = ui.searchInput("닭가슴살, 연어, 현미, 브로콜리 검색");
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
        List<NutritionFood> matches = NutritionFood.KIND_EXTERNAL_MENU.equals(catalogKindFilter)
                ? host.nutritionCatalogRepository().searchPackagedFoods(catalogQuery, 13)
                : host.nutritionCatalogRepository().searchFoods(catalogQuery);
        for (NutritionFood food : matches) {
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
        row.setOnClickListener(v -> {
            if (food.isDiningOutMenu()) {
                openDiningOutMenuFromCatalog(food);
            } else if (food.isPackagedFood()) {
                selectPackagedProduct(food);
            } else {
                addCatalogFood(food);
            }
        });
        row.setContentDescription(
                catalogFoodTypeLabel(food) + " " + catalogFoodTitle(food)
                        + ", " + (food.isDiningOutMenu()
                        ? "외식 입력으로 이동"
                        : (menuBuilderVisible ? "재료로 추가" : "끼니에 추가"))
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
        details.addView(ui.text(catalogFoodTitle(food), 14, FitnessUi.COLOR_TEXT, true));
        if (food.isPackagedFood()) {
            details.addView(ui.text(
                    "대표 포장 · " + food.packagedVariantLabel(),
                    11,
                    FitnessUi.COLOR_MUTED,
                    false
            ));
        }
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
                food.isDiningOutMenu()
                        ? "외식으로 입력 ›"
                        : (menuBuilderVisible ? "재료로 추가 ›" : "끼니에 추가 ›"),
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

    private String catalogFoodTitle(NutritionFood food) {
        return food != null && food.isPackagedFood()
                ? food.packagedProductLabel()
                : (food == null ? "" : food.name);
    }

    private void selectPackagedProduct(NutritionFood product) {
        List<NutritionFood> variants = host.nutritionCatalogRepository()
                .packagedFoodVariants(product);
        if (variants.isEmpty()) {
            addCatalogFood(product);
            return;
        }
        if (variants.size() == 1) {
            addCatalogFood(variants.get(0));
            return;
        }
        String[] labels = new String[variants.size()];
        for (int index = 0; index < variants.size(); index++) {
            NutritionFood variant = variants.get(index);
            labels[index] = variant.packagedVariantLabel()
                    + " · " + variant.extendedNutritionLabel();
        }
        ui().choiceSheet(
                product.packagedProductLabel() + " · 포장 선택",
                Arrays.asList(labels),
                -1,
                which -> {
                    if (which >= 0 && which < variants.size()) {
                        addCatalogFood(variants.get(which));
                    }
                }
        );
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
        LinearLayout form = formSystem.column();
        form.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        form.addView(formSystem.sectionTitle(ingredientMode ? "단일 식품 정보" : "완제품 정보"),
                ui.fullWidthParams(0));
        form.addView(formSystem.helper(
                ingredientMode
                        ? "브랜드가 없는 기본 식품입니다. 조리 방식이 다르면 별도 식품으로 등록하세요."
                        : "브랜드와 포장 단위가 있는 상품입니다. PriceTrace에서 불러오거나 직접 입력하세요."
        ), ui.fullWidthParams(ui.dp(4)));
        EditText name = formSystem.textInput(
                ingredientMode
                        ? "단일 식품 이름 (예: 현미밥, 구운 닭가슴살)"
                        : "예: 포카칩 오리지널",
                ""
        );
        EditText manufacturer = ingredientMode
                ? null
                : formSystem.textInput("예: 오리온, CJ제일제당", "");
        EditText brand = ingredientMode ? null : formSystem.textInput("예: 포카칩", "");
        EditText subBrand = ingredientMode
                ? null : formSystem.textInput("예: 오!감자, 솥반", "");
        EditText packageAmount = ingredientMode
                ? null
                : formSystem.decimalInput("포장 용량 (선택)", "");
        Button packageUnit = ingredientMode
                ? null
                : NutritionUnitSelector.create(ui, host.activity(), NutritionUnit.GRAM);
        EditText packageCount = ingredientMode
                ? null
                : formSystem.decimalInput("포장 개수 (선택)", "");
        String[] selectedCategory = {
                ingredientMode
                        ? NutritionFood.CATEGORY_OTHER
                        : NutritionFood.CATEGORY_PROCESSED
        };
        Button categoryButton = formSystem.selector(
                categoryButtonLabel(selectedCategory[0]),
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
        Button cookingMethodButton = formSystem.selector(
                cookingMethodButtonLabel(selectedCookingMethod[0]),
                null
        );
        cookingMethodButton.setOnClickListener(v -> showFoodChoiceDialog(
                "조리 방식 선택",
                NutritionFood.cookingMethodOptions(),
                selectedCookingMethod,
                cookingMethodButton,
                false
        ));
        EditText basisAmount = formSystem.decimalInput(
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
        String[] manualManufacturer = {""};
        String[] manualName = {""};
        String[] manualBrand = {""};
        String[] manualSubBrand = {""};
        String[] manualBasisAmount = {""};
        String[] manualBasisUnit = {NutritionUnitSelector.value(basisUnit)};
        LinearLayout priceTraceResults = new LinearLayout(host.activity());
        priceTraceResults.setOrientation(LinearLayout.VERTICAL);
        EditText priceTraceQuery = ui.searchInput("PriceTrace 상품명 검색");
        Button priceTraceSearch = ui.secondaryButton("PriceTrace 상품 불러오기", null);
        TextView priceTraceSelection = ui.text(
                "",
                11,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        priceTraceSelection.setVisibility(View.GONE);
        Button clearPriceTraceSelection = ui.secondaryButton(
                "PriceTrace 선택 해제 · 직접 입력",
                null
        );
        clearPriceTraceSelection.setVisibility(View.GONE);
        clearPriceTraceSelection.setOnClickListener(v -> {
            selectedProduct[0] = null;
            if (manufacturer != null) {
                manufacturer.setText(manualManufacturer[0]);
                unlockPriceTraceLoadedField(manufacturer);
            }
            name.setText(manualName[0]);
            unlockPriceTraceLoadedField(name);
            if (brand != null) {
                brand.setText(manualBrand[0]);
                unlockPriceTraceLoadedField(brand);
            }
            if (subBrand != null) {
                subBrand.setText(manualSubBrand[0]);
                unlockPriceTraceLoadedField(subBrand);
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
            formSystem.disabled(priceTraceQuery, true);
            formSystem.disabled(priceTraceSearch, true);
            priceTraceSelection.setVisibility(View.VISIBLE);
            priceTraceSelection.setText("설정에서 PriceTrace 읽기 전용 DB를 연결하면 상품을 불러올 수 있습니다.");
        }
        priceTraceSearch.setOnClickListener(v -> {
            String query = FitnessUi.inputText(priceTraceQuery).trim();
            if (query.isEmpty()) {
                host.toast("PriceTrace에서 검색할 상품명을 입력하세요.");
                return;
            }
            formSystem.loading(priceTraceSearch, true, "PriceTrace 상품을 불러오는 중");
            priceTraceSelection.setVisibility(View.VISIBLE);
            priceTraceSelection.setText("PriceTrace 상품을 조회하는 중입니다.");
            host.searchPriceTraceProducts(query, new ScreenHost.ProductSearchCallback() {
                @Override
                public void onComplete(List<ProductReadV1> products) {
                    host.activity().runOnUiThread(() -> {
                        formSystem.loading(priceTraceSearch, false, null);
                        renderPriceTraceChoices(
                                 priceTraceResults,
                                 priceTraceSelection,
                                  products,
                                  nutrients,
                                  name,
                                  brand,
                                  manufacturer,
                                  subBrand,
                                  basisAmount,
                                  basisUnit,
                                  selectedProduct,
                                  clearPriceTraceSelection,
                                  manualManufacturer,
                                  manualName,
                                  manualBrand,
                                  manualSubBrand,
                                  manualBasisAmount,
                                 manualBasisUnit
                         );
                    });
                }

                @Override
                public void onError(Exception error) {
                    host.activity().runOnUiThread(() -> {
                        formSystem.loading(priceTraceSearch, false, null);
                        priceTraceSelection.setText("PriceTrace 조회 실패: " +
                                (error.getMessage() == null ? "연결을 확인하세요." : error.getMessage()));
                    });
                }
            });
        });
        if (ingredientMode) {
            form.addView(formSystem.field("식품명 *", name), ui.fullWidthParams(ui.dp(6)));
        } else {
            form.addView(formSystem.field("상품명 *", name), ui.fullWidthParams(ui.dp(6)));
            form.addView(formSystem.field("브랜드 (선택)", brand), ui.fullWidthParams(ui.dp(6)));
            form.addView(formSystem.field("서브 브랜드 (선택)", subBrand),
                    ui.fullWidthParams(ui.dp(6)));
            form.addView(formSystem.field("제조회사 (선택)", manufacturer),
                    ui.fullWidthParams(ui.dp(6)));
        }
        form.addView(formSystem.sectionTitle("분류와 기준량"), ui.fullWidthParams(ui.dp(4)));
        form.addView(formSystem.field("식품 범주", categoryButton), ui.fullWidthParams(0));
        form.addView(formSystem.field("조리 방식", cookingMethodButton),
                ui.fullWidthParams(ui.dp(6)));
        form.addView(formSystem.field("기준 수량", basisAmount), ui.fullWidthParams(0));
        form.addView(formSystem.field("기준 단위", basisUnit),
                ui.fullWidthParams(ui.dp(6)));
        form.addView(formSystem.helper("아래 영양값은 모두 위 기준 수량에 대한 값입니다."),
                ui.fullWidthParams(ui.dp(4)));
        form.addView(nutrients.view(), ui.fullWidthParams(0));
        form.addView(unitNutritionPreview, ui.fullWidthParams(ui.dp(4)));
        if (packageAmount != null) {
            form.addView(formSystem.sectionTitle("포장 정보 (선택)"), ui.fullWidthParams(ui.dp(4)));
            form.addView(formSystem.field("포장 용량", packageAmount),
                    ui.fullWidthParams(0));
            form.addView(formSystem.field("포장 단위", packageUnit),
                    ui.fullWidthParams(ui.dp(6)));
            form.addView(formSystem.field("포장 개수", packageCount),
                    ui.fullWidthParams(ui.dp(6)));
        }
        if (!ingredientMode) {
            form.addView(formSystem.sectionTitle("PriceTrace 읽기"),
                    ui.fullWidthParams(ui.dp(4)));
            form.addView(formSystem.helper(
                    "PriceTrace 값은 읽기 전용으로 불러오며, 연결이 없으면 직접 입력할 수 있습니다."
            ), ui.fullWidthParams(0));
            form.addView(formSystem.field("상품명 검색", priceTraceQuery),
                    ui.fullWidthParams(ui.dp(6)));
            form.addView(priceTraceSearch, ui.fullWidthParams(0));
            form.addView(priceTraceSelection, ui.fullWidthParams(ui.dp(4)));
            form.addView(clearPriceTraceSelection, ui.fullWidthParams(ui.dp(4)));
            form.addView(priceTraceResults, ui.fullWidthParams(0));
        }
        Button saveOnly = ui.secondaryButton(
                ingredientMode ? "단일 식품으로 저장" : "완제품으로 저장",
                v -> saveDirectFood(
                        name, brand, selectedCategory[0], selectedCookingMethod[0],
                        basisAmount, basisUnit, nutrients,
                        ingredientMode, selectedProduct[0], false,
                        manufacturer, subBrand, packageAmount, packageUnit, packageCount
                )
        );
        Button saveAndAdd = ui.primaryButton(
                menuBuilderVisible ? "저장 후 재료로 추가" : "저장 후 끼니에 추가",
                v -> saveDirectFood(
                        name, brand, selectedCategory[0], selectedCookingMethod[0],
                        basisAmount, basisUnit, nutrients,
                        ingredientMode, selectedProduct[0], true,
                        manufacturer, subBrand, packageAmount, packageUnit, packageCount
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
            EditText manufacturer,
            EditText subBrand,
            EditText basisAmount,
            Button basisUnit,
            ProductReadV1[] selectedProduct,
            Button clearSelection,
            String[] manualManufacturer,
            String[] manualName,
            String[] manualBrand,
            String[] manualSubBrand,
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
                    manualManufacturer[0] = manufacturer == null
                            ? "" : FitnessUi.inputText(manufacturer);
                    manualName[0] = FitnessUi.inputText(name);
                    manualBrand[0] = brand == null ? "" : FitnessUi.inputText(brand);
                    manualSubBrand[0] = subBrand == null ? "" : FitnessUi.inputText(subBrand);
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
                if (manufacturer != null && product.manufacturerName != null) {
                    manufacturer.setText(product.manufacturerName);
                    lockPriceTraceLoadedField(manufacturer);
                }
                if (subBrand != null && product.subBrandName != null) {
                    subBrand.setText(product.subBrandName);
                    lockPriceTraceLoadedField(subBrand);
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
        if (food == null || food.isDiningOutComponent()) {
            return false;
        }
        String kind = NutritionFood.normalizeKind(food == null ? null : food.kind);
        if (menuBuilderVisible && (food.isDiningOutMenu()
                || !NutritionFood.canBeRecipeComponent(kind))) {
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

    /** Keeps a PriceTrace-loaded value in the neutral input surface. */
    private void lockPriceTraceLoadedField(EditText field) {
        FitnessUi ui = ui();
        field.setEnabled(false);
        field.setCursorVisible(false);
        field.setLongClickable(false);
        field.setTextIsSelectable(false);
        field.setContentDescription(field.getText() + " · PriceTrace 선택값, 수정 불가");
        ui.setComponentBackground(field, ui.borderDrawable(
                ui.surface(), ui.border(), ui.dp(FitnessUi.INPUT_RADIUS_DP)));
        ui.applyDepth(field, FitnessUi.DEPTH_FLAT_DP);
    }

    private void unlockPriceTraceLoadedField(EditText field) {
        FitnessUi ui = ui();
        field.setEnabled(true);
        field.setCursorVisible(true);
        field.setLongClickable(true);
        field.setTextIsSelectable(false);
        field.setContentDescription(null);
        field.setBackground(ui.borderDrawable(
                ui.surface(), ui.border(), ui.dp(FitnessUi.INPUT_RADIUS_DP)));
        ui.applyDepth(field, FitnessUi.DEPTH_FLAT_DP);
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
            boolean addToMeal,
            EditText manufacturer,
            EditText subBrand,
            EditText packageAmount,
            Button packageUnit,
            EditText packageCount
    ) {
        try {
            syncDraftFromViews();
            double basis = positiveNumber(basisAmount, "기준 수량");
            String selectedBasisUnit = NutritionUnitSelector.value(basisUnit);
            ProductReadV1 exactProduct = selectedProduct == null
                    ? null
                    : selectedProduct.exactVariantForBasis(basis, selectedBasisUnit);
            String productBrand = brand == null ? null : FitnessUi.inputText(brand);
            String productManufacturer = manufacturer == null
                    ? null : FitnessUi.inputText(manufacturer);
            String productSubBrand = subBrand == null ? null : FitnessUi.inputText(subBrand);
            if (selectedProduct != null && (productBrand == null || productBrand.trim().isEmpty())) {
                productBrand = selectedProduct.brand;
            }
            if (selectedProduct != null
                    && (productManufacturer == null || productManufacturer.trim().isEmpty())) {
                productManufacturer = selectedProduct.manufacturerName;
            }
            if (selectedProduct != null
                    && (productSubBrand == null || productSubBrand.trim().isEmpty())) {
                productSubBrand = selectedProduct.subBrandName;
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
            NutritionFood saved;
            if (ingredientMode) {
                saved = host.nutritionCatalogRepository().saveFood(
                        FitnessUi.inputText(name),
                        productBrand,
                        NutritionFood.KIND_INGREDIENT,
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
            } else {
                Double packageAmountValue = optionalPositiveNumber(packageAmount, "포장 용량");
                Integer packageCountValue = optionalPositiveInteger(packageCount, "포장 개수");
                saved = host.nutritionCatalogRepository().savePackagedFood(
                        productManufacturer,
                        productBrand,
                        productSubBrand,
                        FitnessUi.inputText(name),
                        packageAmountValue,
                        packageAmountValue == null || packageUnit == null
                                ? null : NutritionUnitSelector.value(packageUnit),
                        packageCountValue,
                        basis,
                        selectedBasisUnit,
                        cookingMethod,
                        nutrients.profile(),
                        sourceType,
                        sourceReference,
                        "",
                        exactProduct
                );
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
        ui().choiceSheet(title, Arrays.asList(labels), -1, which -> {
            if (which >= 0 && which < options.length) {
                selected[0] = options[which];
                target.setText(category
                        ? categoryButtonLabel(selected[0])
                        : cookingMethodButtonLabel(selected[0]));
            }
        });
    }

    private static String categoryButtonLabel(String category) {
        return "식품 범주: " + NutritionFood.categoryLabel(category);
    }

    private static String cookingMethodButtonLabel(String cookingMethod) {
        return "조리 방식: " + NutritionFood.cookingMethodLabel(cookingMethod);
    }

    private void addCatalogFood(NutritionFood food) {
        syncDraftFromViews();
        if (food != null && food.isDiningOutMenu()) {
            openDiningOutMenuFromCatalog(food);
            return;
        }
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
        return activeDiningOutMenu().hasAnyIdentity();
    }

    private boolean hasCompleteDiningOutIdentity() {
        return activeDiningOutMenu().hasExactIdentity();
    }

    private void clearDiningOutPriceTraceIdentity() {
        clearDiningOutPriceTraceIdentity(activeDiningOutMenu());
    }

    private void clearDiningOutPriceTraceIdentity(DiningOutMenuDraft menu) {
        menu.restaurantId = "";
        menu.restaurantLocationId = "";
        menu.sourceNamespace = "";
        menu.sourceLocationCode = "";
        menu.restaurantMenuId = "";
        menu.catalogProductId = "";
    }

    private void updateDiningOutSelectionSummary() {
        if (diningOutSelectionSummary == null) {
            return;
        }
        String storeName = draftDiningOutStoreName.trim();
        String menuName = draftDiningOutMenus.isEmpty()
                ? ""
                : draftDiningOutMenus.get(0).name.trim();
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
        return selectedDiningOutIdentity(activeDiningOutMenu());
    }

    private DiningOutIdentity selectedDiningOutIdentity(DiningOutMenuDraft menu) {
        if (!menu.hasAnyIdentity()) {
            return null;
        }
        if (!menu.hasExactIdentity()) {
            throw new IllegalArgumentException(
                    "검색한 식당·지점·메뉴를 모두 선택하거나 직접 등록으로 전환하세요."
            );
        }
        if (!menu.sourceNamespace.trim().isEmpty()
                && !menu.sourceLocationCode.trim().isEmpty()) {
            return DiningOutIdentity.fromPriceTrace(
                    menu.restaurantId,
                    draftDiningOutStoreName,
                    menu.restaurantLocationId,
                    menu.sourceNamespace,
                    menu.sourceLocationCode,
                    draftDiningOutBranchName,
                    menu.restaurantMenuId,
                    menu.name,
                    menu.catalogProductId
            );
        }
        return DiningOutIdentity.fromPriceTrace(
                menu.restaurantId,
                draftDiningOutStoreName,
                menu.restaurantLocationId,
                draftDiningOutBranchName,
                menu.restaurantMenuId,
                menu.name,
                menu.catalogProductId
        );
    }

    private void saveMeal(boolean saveDiningOutMenu) {
        syncDraftFromViews();
        String recordedMealTime = draftMealTime;
        if (mealEntryMode == MEAL_ENTRY_MODE_DINING_OUT) {
            try {
                if (draftDiningOutMenus.isEmpty()) {
                    throw new IllegalArgumentException("외식 메뉴를 하나 이상 추가하세요.");
                }
                String fulfillmentMode = DiningOutFulfillmentMode.require(
                        draftDiningOutFulfillmentMode
                );
                double nominalServings = diningOutNominalServingsValue();
                int dinerCount = diningOutDinerCountValue();
                Double consumedFraction = diningOutConsumedFractionValue();
                DiningOutConsumption consumption = DiningOutConsumption.resolve(
                        dinerCount,
                        consumedFraction
                );
                List<MealMenuSelection> diningOutMenus = new ArrayList<>();
                DiningOutIdentity firstIdentity = null;
                DiningOutIdentity restaurantScopeIdentity = null;
                for (DiningOutMenuDraft menu : draftDiningOutMenus) {
                    String menuName = MealEntryPolicy.requireDiningOutMenuName(menu.name);
                    Integer calories = MealEntryPolicy.requireDiningOutCaloriesInput(menu.calories);
                    Double carbsGrams = MealEntryPolicy.requireDiningOutMacro(
                            menu.carbs, menuName + " 탄수화물");
                    Double proteinGrams = MealEntryPolicy.requireDiningOutMacro(
                            menu.protein, menuName + " 단백질");
                    Double fatGrams = MealEntryPolicy.requireDiningOutMacro(
                            menu.fat, menuName + " 지방");
                    Double sodiumMg = MealEntryPolicy.optionalDiningOutMacro(menu.sodium, "나트륨");
                    Double sugarsGrams = MealEntryPolicy.optionalDiningOutMacro(menu.sugars, "당류");
                    Double saturatedFatGrams = MealEntryPolicy.optionalDiningOutMacro(
                            menu.saturatedFat, "포화지방");
                    MealEntryPolicy.requireDiningOutMenuNutrition(
                            calories,
                            proteinGrams,
                            carbsGrams,
                            fatGrams,
                            sodiumMg,
                            sugarsGrams,
                            saturatedFatGrams
                    );
                    NutritionProfile menuProfile = diningOutMenuProfile(
                            menu.profile,
                            calories,
                            proteinGrams,
                            carbsGrams,
                            fatGrams,
                            sodiumMg,
                            sugarsGrams,
                            saturatedFatGrams
                    );
                    DiningOutIdentity identity = selectedDiningOutIdentity(menu);
                    if (diningOutMenus.isEmpty()) {
                        firstIdentity = identity;
                    }
                    validateDiningOutIdentityScope(restaurantScopeIdentity, identity);
                    if (restaurantScopeIdentity == null && identity != null) {
                        restaurantScopeIdentity = identity;
                    }
                    List<DiningOutOption> optionSnapshots = saveDiningOutOptions(
                            menu, saveDiningOutMenu, identity);
                    NutritionFood savedMenu = null;
                    if (saveDiningOutMenu) {
                        savedMenu = host.nutritionCatalogRepository().saveDiningOutMenuWithNutrition(
                                draftDiningOutStoreName,
                                menuName,
                                menuProfile,
                                draftDiningOutBranchName,
                                identity
                        );
                    }
                    diningOutMenus.add(diningOutMenuSelection(
                            savedMenu,
                            menuName,
                            calories,
                            proteinGrams,
                            carbsGrams,
                            fatGrams,
                            sodiumMg,
                            sugarsGrams,
                            saturatedFatGrams,
                            menuProfile,
                            optionSnapshots,
                            identity
                    ));
                    String relationshipMenuId = savedMenu == null
                            ? menu.catalogFoodId
                            : savedMenu.id;
                    if (relationshipMenuId != null && !relationshipMenuId.trim().isEmpty()) {
                        for (DiningOutOption option : optionSnapshots) {
                            if (option.catalogFoodId != null) {
                                host.nutritionCatalogRepository().linkDiningOutComponentToMenu(
                                        relationshipMenuId,
                                        option.catalogFoodId,
                                        option.groupType
                                );
                            }
                        }
                    }
                }
                repository().addDiningOutMealAtTimeWithMenusAndConsumption(
                        selectedDate,
                        recordedMealTime,
                        draftDiningOutStoreName,
                        draftDiningOutBranchName,
                        firstIdentity,
                        fulfillmentMode,
                        diningOutMenus,
                        nominalServings,
                        consumption
                );
                if (saveDiningOutMenu) {
                    syncCatalog(false);
                }
            } catch (IllegalArgumentException error) {
                showDiningOutValidationError(error.getMessage());
                host.toast(error.getMessage());
                return;
            }
            draftMenus.clear();
            draftIngredients.clear();
            draftMenuName = "";
            resetDiningOutEditor();
            draftDiningOutNominalServings = "1";
            draftDiningOutDinerCount = "1";
            draftDiningOutConsumedPercent = "";
            diningOutSelectionSummary = null;
            diningOutStoreInput = null;
            diningOutBranchInput = null;
            diningOutNominalServingsInput = null;
            diningOutDinerCountInput = null;
            diningOutConsumedPercentInput = null;
            diningOutOptionsContainer = null;
            diningOutValidationError = null;
            diningOutOptionInputs.clear();
            diningOutOptionGroupInputs.clear();
            diningOutOptionProvisionInputs.clear();
            diningOutOptionCaloriesInputs.clear();
            diningOutOptionProteinInputs.clear();
            diningOutOptionCarbsInputs.clear();
            diningOutOptionFatInputs.clear();
            diningOutOptionSodiumInputs.clear();
            diningOutOptionSugarsInputs.clear();
            diningOutOptionSaturatedFatInputs.clear();
            diningOutOptionConsumedPercentInputs.clear();
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
        resetDiningOutEditor();
        draftDiningOutNominalServings = "1";
        draftDiningOutDinerCount = "1";
        draftDiningOutConsumedPercent = "";
        diningOutSelectionSummary = null;
        diningOutStoreInput = null;
        diningOutBranchInput = null;
        diningOutNominalServingsInput = null;
        diningOutDinerCountInput = null;
        diningOutConsumedPercentInput = null;
        diningOutOptionsContainer = null;
        diningOutValidationError = null;
        diningOutOptionInputs.clear();
        diningOutOptionProvisionInputs.clear();
        diningOutOptionGroupInputs.clear();
        diningOutOptionCaloriesInputs.clear();
        diningOutOptionProteinInputs.clear();
        diningOutOptionCarbsInputs.clear();
        diningOutOptionFatInputs.clear();
        diningOutOptionSodiumInputs.clear();
        diningOutOptionSugarsInputs.clear();
        diningOutOptionSaturatedFatInputs.clear();
        diningOutOptionConsumedPercentInputs.clear();
        mealEntryMode = MEAL_ENTRY_MODE_FOOD;
        menuBuilderVisible = false;
        draftMealTime = currentMealTime();
        mealWorkspaceVisible = false;
        host.toast("끼니를 " + dateLabel() + " " + recordedMealTime + "에 기록했습니다.");
        host.rerender();
    }

    private DiningOutMenuDraft activeDiningOutMenu() {
        if (draftDiningOutMenus.isEmpty()) {
            draftDiningOutMenus.add(new DiningOutMenuDraft());
        }
        activeDiningOutMenuIndex = Math.max(
                0,
                Math.min(activeDiningOutMenuIndex, draftDiningOutMenus.size() - 1)
        );
        return draftDiningOutMenus.get(activeDiningOutMenuIndex);
    }

    private void validateDiningOutIdentityScope(DiningOutIdentity expected, DiningOutIdentity candidate) {
        if (expected != null && candidate != null
                && !expected.hasSameRestaurantLocation(candidate)) {
            throw new IllegalArgumentException("한 외식 기록에는 같은 식당·지점의 메뉴만 저장할 수 있습니다.");
        }
    }

    private void resetDiningOutEditor() {
        draftDiningOutStoreName = "";
        draftDiningOutBranchName = "";
        draftDiningOutFulfillmentMode = null;
        draftDiningOutMenus.clear();
        activeDiningOutMenuIndex = 0;
        diningOutStoreInput = null;
        diningOutBranchInput = null;
        diningOutNominalServingsInput = null;
        diningOutDinerCountInput = null;
        diningOutConsumedPercentInput = null;
        diningOutOptionsContainer = null;
        diningOutValidationError = null;
        diningOutMenusContainer = null;
        diningOutMenuNameInputs.clear();
        diningOutMenuCaloriesInputs.clear();
        diningOutMenuProteinInputs.clear();
        diningOutMenuCarbsInputs.clear();
        diningOutMenuFatInputs.clear();
        diningOutMenuSodiumInputs.clear();
        diningOutMenuSugarsInputs.clear();
        diningOutMenuSaturatedFatInputs.clear();
        diningOutOptionProvisionInputs.clear();
        diningOutOptionInputs.clear();
        diningOutOptionGroupInputs.clear();
        diningOutOptionCaloriesInputs.clear();
        diningOutOptionProteinInputs.clear();
        diningOutOptionCarbsInputs.clear();
        diningOutOptionFatInputs.clear();
        diningOutOptionSodiumInputs.clear();
        diningOutOptionSugarsInputs.clear();
        diningOutOptionSaturatedFatInputs.clear();
        diningOutOptionConsumedPercentInputs.clear();
    }

    /**
     * The rendered controls are a one-way projection of the draft.  Any action that changes
     * the draft directly must discard those controls before rerendering, otherwise a later
     * syncDraftFromViews() could write old EditText values back over a PT/saved selection.
     */
    private void rerenderDiningOutFromDraft() {
        diningOutStoreInput = null;
        diningOutBranchInput = null;
        diningOutNominalServingsInput = null;
        diningOutDinerCountInput = null;
        diningOutConsumedPercentInput = null;
        diningOutMenusContainer = null;
        diningOutOptionsContainer = null;
        diningOutValidationError = null;
        diningOutMenuNameInputs.clear();
        diningOutMenuCaloriesInputs.clear();
        diningOutMenuProteinInputs.clear();
        diningOutMenuCarbsInputs.clear();
        diningOutMenuFatInputs.clear();
        diningOutMenuSodiumInputs.clear();
        diningOutMenuSugarsInputs.clear();
        diningOutOptionProvisionInputs.clear();
        diningOutMenuSaturatedFatInputs.clear();
        diningOutOptionInputs.clear();
        diningOutOptionGroupInputs.clear();
        diningOutOptionCaloriesInputs.clear();
        diningOutOptionProteinInputs.clear();
        diningOutOptionCarbsInputs.clear();
        diningOutOptionFatInputs.clear();
        diningOutOptionConsumedPercentInputs.clear();
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
        syncDiningOutMenuInputs();
        if (!diningOutOptionInputs.isEmpty()) {
            DiningOutMenuDraft menu = activeDiningOutMenu();
            List<DiningOutOptionDraft> pendingOptions = new ArrayList<>(menu.options);
            menu.options.clear();
            for (int index = 0; index < diningOutOptionInputs.size(); index++) {
                DiningOutOptionDraft previous = index < pendingOptions.size()
                        ? pendingOptions.get(index)
                        : null;
                DiningOutOptionDraft draft = new DiningOutOptionDraft();
                draft.name = FitnessUi.inputText(diningOutOptionInputs.get(index)).trim();
                draft.profile = previous == null || previous.profile == null
                        ? NutritionProfile.empty()
                        : previous.profile;
                draft.consumedPercent = FitnessUi.inputText(
                        diningOutOptionConsumedPercentInputs.get(index)
                );
                if (previous != null) {
                    draft.groupType = previous.groupType;
                    draft.groupKey = previous.groupKey;
                    draft.catalogFoodId = previous.catalogFoodId;
                    draft.sourceReference = previous.sourceReference;
                    draft.memberId = previous.memberId;
                    draft.provisionType = previous.provisionType;
                }
                draft.calories = FitnessUi.inputText(
                        diningOutOptionCaloriesInputs.get(index)
                );
                draft.protein = FitnessUi.inputText(
                        diningOutOptionProteinInputs.get(index)
                );
                draft.carbs = FitnessUi.inputText(
                        diningOutOptionCarbsInputs.get(index)
                );
                draft.fat = FitnessUi.inputText(
                        diningOutOptionFatInputs.get(index)
                );
                draft.sodium = FitnessUi.inputText(
                        diningOutOptionSodiumInputs.get(index)
                );
                draft.sugars = FitnessUi.inputText(
                        diningOutOptionSugarsInputs.get(index)
                );
                draft.saturatedFat = FitnessUi.inputText(
                        diningOutOptionSaturatedFatInputs.get(index)
                );
                draft.profile = diningOutOptionProfile(draft, false);
                menu.options.add(draft);
            }
            // An add action can create a new draft row before render() has created its views.
            // Keep those trailing drafts so repeated "옵션 추가" clicks do not collapse to one row.
            for (int index = diningOutOptionInputs.size(); index < pendingOptions.size(); index++) {
                menu.options.add(pendingOptions.get(index));
            }
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

    private void syncDiningOutMenuInputs() {
        if (diningOutMenuNameInputs.size() != draftDiningOutMenus.size()) {
            return;
        }
        for (int index = 0; index < draftDiningOutMenus.size(); index++) {
            DiningOutMenuDraft menu = draftDiningOutMenus.get(index);
            String nextName = FitnessUi.inputText(diningOutMenuNameInputs.get(index));
            boolean nameChanged = !TextUtils.equals(menu.name, nextName);
            menu.name = nextName;
            menu.calories = FitnessUi.inputText(diningOutMenuCaloriesInputs.get(index));
            menu.protein = FitnessUi.inputText(diningOutMenuProteinInputs.get(index));
            menu.carbs = FitnessUi.inputText(diningOutMenuCarbsInputs.get(index));
            menu.fat = FitnessUi.inputText(diningOutMenuFatInputs.get(index));
            menu.sodium = FitnessUi.inputText(diningOutMenuSodiumInputs.get(index));
            menu.sugars = FitnessUi.inputText(diningOutMenuSugarsInputs.get(index));
            menu.saturatedFat = FitnessUi.inputText(
                    diningOutMenuSaturatedFatInputs.get(index)
            );
            if (nameChanged) {
                menu.catalogFoodId = "";
                clearDiningOutPriceTraceIdentity(menu);
                for (DiningOutOptionDraft option : menu.options) {
                    option.sourceReference = withoutCompositionTemplateReference(
                            option.sourceReference
                    );
                }
            }
        }
    }

    private void syncDiningOutTextInputs() {
        if (diningOutStoreInput == null || diningOutBranchInput == null) {
            return;
        }
        String nextStoreName = FitnessUi.inputText(diningOutStoreInput);
        String nextBranchName = FitnessUi.inputText(diningOutBranchInput);
        boolean searchedValueChanged = !TextUtils.equals(
                draftDiningOutStoreName,
                nextStoreName
        ) || !TextUtils.equals(
                draftDiningOutBranchName,
                nextBranchName
        );
        draftDiningOutStoreName = nextStoreName;
        draftDiningOutBranchName = nextBranchName;
        if (searchedValueChanged) {
            for (DiningOutMenuDraft menu : draftDiningOutMenus) {
                if (menu.hasAnyIdentity()) {
                    // A changed restaurant/branch is no longer guaranteed to describe any
                    // selected PriceTrace menu. Keep the local entries, but remove links.
                    clearDiningOutPriceTraceIdentity(menu);
                    menu.catalogFoodId = "";
                }
                for (DiningOutOptionDraft draft : menu.options) {
                    draft.sourceReference = withoutCompositionTemplateReference(
                            draft.sourceReference
                    );
                }
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
        for (String key : NutritionProfile.PRIMARY_DISPLAY_ORDER) {
            compositionTotalBox.addView(
                    formSystem.nutrientRow(key, mealNutritionTotalValue(total, key)),
                    ui.fullWidthParams(0)
            );
        }
    }

    static String mealNutrientDisplayLabel(String key) {
        return NutritionRow.displayLabel(key);
    }

    private String mealNutritionTotalValue(NutritionTotals total, String key) {
        return NutritionCalculator.describeTotal(total.total(key));
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

    private Double optionalPositiveNumber(EditText input, String label) {
        if (input == null) {
            return null;
        }
        String value = FitnessUi.inputText(input).trim();
        if (value.isEmpty()) {
            return null;
        }
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed) || parsed <= 0d) {
            throw new IllegalArgumentException(label + "은 0보다 큰 숫자로 입력하세요.");
        }
        return parsed;
    }

    private Integer optionalPositiveInteger(EditText input, String label) {
        if (input == null) {
            return null;
        }
        String value = FitnessUi.inputText(input).trim();
        if (value.isEmpty()) {
            return null;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "은 정수로 입력하세요.");
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + "은 0보다 커야 합니다.");
        }
        return parsed;
    }

    private static final class DiningOutMenuDraft {
        private String name = "";
        private NutritionProfile profile = NutritionProfile.empty();
        private String calories = "";
        private String carbs = "";
        private String protein = "";
        private String fat = "";
        private String sodium = "";
        private String sugars = "";
        private String saturatedFat = "";
        private String restaurantId = "";
        private String restaurantLocationId = "";
        private String sourceNamespace = "";
        private String sourceLocationCode = "";
        private String restaurantMenuId = "";
        private String catalogProductId = "";
        private String catalogFoodId = "";
        private final List<DiningOutOptionDraft> options = new ArrayList<>();

        private boolean hasAnyIdentity() {
            return !restaurantId.trim().isEmpty()
                    || !restaurantLocationId.trim().isEmpty()
                    || !restaurantMenuId.trim().isEmpty()
                    || !catalogProductId.trim().isEmpty();
        }

        private boolean hasExactIdentity() {
            return !restaurantId.trim().isEmpty()
                    && !restaurantLocationId.trim().isEmpty()
                    && !restaurantMenuId.trim().isEmpty()
                    && !catalogProductId.trim().isEmpty();
        }
    }

    private static final class DiningOutOptionDraft {
        private String groupType = CompositionGroupType.OTHER.value();
        private String provisionType = DiningOutProvisionType.defaultProvisionForGroup(
                CompositionGroupType.OTHER.value()
        ).value();
        private NutritionProfile profile = NutritionProfile.empty();
        private String groupKey = "";
        private String name = "";
        private String calories = "";
        private String protein = "";
        private String carbs = "";
        private String fat = "";
        private String sodium = "";
        private String sugars = "";
        private String saturatedFat = "";
        private String consumedPercent = "100";
        private String catalogFoodId = "";
        private String sourceReference = "";
        private String memberId = "";
    }
}
