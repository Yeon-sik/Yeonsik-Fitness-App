package com.yeonsik.fitnessapp.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.NutritionCalculator;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionTotals;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 식단의 독립 작업 공간.
 * 날짜별 섭취 요약, 식사 기록, 음식 카탈로그 검색, 구성 메뉴 저장을 한 흐름으로 제공한다.
 */
public final class MealManagementScreen extends BaseScreen {
    private static final String[] MEAL_TYPES = {"아침", "점심", "저녁", "간식"};
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);

    private String selectedDate;
    private String selectedMealType = MEAL_TYPES[0];
    private String draftName = "";
    private String catalogQuery = "";
    private final List<MealCompositionItem> draftItems = new ArrayList<>();

    private EditText mealNameInput;
    private EditText catalogSearchInput;
    private LinearLayout compositionRows;
    private LinearLayout catalogResults;
    private TextView compositionTotal;
    private TextView catalogStatus;
    private final List<EditText> quantityInputs = new ArrayList<>();
    private boolean showDirectFoodForm;
    private boolean initialSyncRequested;
    private boolean catalogSyncing;
    private String syncMessage = "기기와 원격 카탈로그를 함께 검색합니다.";

    public MealManagementScreen(ScreenHost host) {
        super(host);
        selectedDate = host.today();
    }

    @Override
    public void render() {
        syncDraftFromViews();

        screenHeader("NUTRITION", "식단 관리");
        add(ui().text(
                "먹은 음식과 나만의 메뉴를 한 곳에서 기록하고, 영양 흐름을 확인하세요.",
                14,
                FitnessUi.COLOR_MUTED,
                false
        ), ui().fullWidthParams(0));

        add(dateNavigator());
        add(dailySummary());

        section("기록된 식사");
        renderMealEntries();

        section("새 식사");
        add(mealComposer());

        section("음식·메뉴 카탈로그");
        add(catalogCard());

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
        TextView date = ui.text(dateLabel(), 17, FitnessUi.COLOR_TEXT, true);
        date.setGravity(Gravity.CENTER);
        dateColumn.addView(date);
        TextView helper = ui.text(
                isToday() ? "오늘의 식단" : "선택한 날짜의 식단",
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

        TextView next = ui.textAction("›", FitnessUi.COLOR_MUTED, () -> {
            selectedDate = LocalDate.parse(selectedDate).plusDays(1).toString();
            host.rerender();
        });
        row.addView(next, new LinearLayout.LayoutParams(ui.dp(42), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(row);
        return card;
    }

    private View dailySummary() {
        FitnessUi ui = ui();
        FitnessRepository.MealNutritionSummary summary = repository().mealNutritionForDate(selectedDate);
        LinearLayout card = ui.heroCard();

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.caption("DAILY NUTRITION", FitnessUi.COLOR_FLOW_MUTED),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(ui.flowStatusBadge(
                catalogSyncing ? "동기화 중" : (host.supabaseConfig().isConfigured() ? "원격 연결" : "기기 저장"),
                catalogSyncing ? FitnessUi.COLOR_WARNING : FitnessUi.COLOR_POSITIVE
        ));
        card.addView(header);

        LinearLayout caloriesRow = new LinearLayout(host.activity());
        caloriesRow.setOrientation(LinearLayout.HORIZONTAL);
        caloriesRow.setGravity(Gravity.BOTTOM);
        caloriesRow.setPadding(0, ui.dp(14), 0, ui.dp(2));
        caloriesRow.addView(ui.num(String.valueOf(Math.round(summary.calories)), 38,
                FitnessUi.COLOR_FLOW_TEXT, true));
        TextView unit = ui.text(" kcal", 16, FitnessUi.COLOR_FLOW_MUTED, true);
        unit.setPadding(0, 0, 0, ui.dp(6));
        caloriesRow.addView(unit);
        caloriesRow.addView(ui.text("  ·  " + summary.mealCount + "끼 기록", 13,
                FitnessUi.COLOR_FLOW_MUTED, false));
        card.addView(caloriesRow);

        LinearLayout firstMacroRow = ui.tileRow();
        firstMacroRow.addView(ui.flowMetric("단백질", NutritionCalculator.trim(summary.proteinGrams) + "g"),
                ui.tileParams(true));
        firstMacroRow.addView(ui.flowMetric("탄수화물", NutritionCalculator.trim(summary.carbsGrams) + "g"),
                ui.tileParams(false));
        card.addView(firstMacroRow, ui.fullWidthParams(ui.dp(10)));

        LinearLayout secondMacroRow = ui.tileRow();
        secondMacroRow.addView(ui.flowMetric("지방", NutritionCalculator.trim(summary.fatGrams) + "g"),
                ui.tileParams(true));
        secondMacroRow.addView(ui.flowMetric("상태", summary.mealCount == 0 ? "기록 시작" : "기록 유지"),
                ui.tileParams(false));
        card.addView(secondMacroRow, ui.fullWidthParams(ui.dp(6)));
        return card;
    }

    private void renderMealEntries() {
        List<FitnessRepository.MealEntry> entries = repository().mealEntriesForDate(selectedDate);
        if (entries.isEmpty()) {
            emptyState("아직 기록된 식사가 없습니다.", "아래에서 메뉴를 검색해 첫 식사를 추가하세요.");
            return;
        }

        List<View> rows = new ArrayList<>();
        for (FitnessRepository.MealEntry entry : entries) {
            rows.add(mealRow(entry));
        }
        add(ui().rowsCard(rows));
    }

    private View mealRow(FitnessRepository.MealEntry entry) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(72));
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        row.addView(ui.glyphCircle(mealGlyph(entry.mealType), false));

        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(ui.dp(12), 0, ui.dp(8), 0);
        column.addView(ui.text(entry.mealType + "  ·  " + entry.menu, 15,
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

    private View mealComposer() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "식사 구성 만들기", selectedDate.equals(host.today()) ? "오늘" : selectedDate);

        LinearLayout typeRow = ui.tileRow();
        for (int index = 0; index < MEAL_TYPES.length; index++) {
            String type = MEAL_TYPES[index];
            Button button = ui.filterButton(type);
            ui.styleFilterButton(button, type.equals(selectedMealType));
            button.setOnClickListener(v -> {
                syncDraftFromViews();
                selectedMealType = type;
                host.rerender();
            });
            typeRow.addView(button, ui.tileParams(index == 0));
        }
        card.addView(typeRow, ui.fullWidthParams(ui.dp(12)));

        mealNameInput = ui.input("메뉴 이름 (예: 닭갈비, 햄버거)", draftName);
        card.addView(ui.labeledFieldColumn("이번 식사의 이름", mealNameInput),
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

        compositionTotal = ui.text("", 13, FitnessUi.COLOR_TEXT, true);
        compositionTotal.setPadding(0, ui.dp(12), 0, 0);
        card.addView(compositionTotal);
        updateCompositionTotal();

        Button saveMeal = ui.button("이 식사 기록하기", true, v -> saveMeal());
        Button saveRecipe = ui.button("구성 메뉴로 저장", false, v -> saveRecipe());
        card.addView(ui.buttonRow(saveMeal, saveRecipe), ui.fullWidthParams(ui.dp(16)));
        card.addView(ui.text(
                "식사 기록은 날짜별 기록에 남고, 구성 메뉴는 다음 검색에서 다시 사용할 수 있습니다.",
                11,
                FitnessUi.COLOR_TERTIARY,
                false
        ), ui.fullWidthParams(ui.dp(9)));
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
            details.addView(ui.text(item.food.name, 14, FitnessUi.COLOR_TEXT, true));
            details.addView(ui.text(
                    Math.round(item.calories) + " kcal  ·  " + item.food.basisUnit,
                    11,
                    FitnessUi.COLOR_MUTED,
                    false
            ));
            row.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            EditText quantity = ui.decimalInput("수량", NutritionCalculator.trim(item.quantity));
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

    private View catalogCard() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "음식·메뉴 검색", catalogSyncing ? "동기화 중" : "로컬 + 원격");

        catalogStatus = ui.text(syncMessage, 12, FitnessUi.COLOR_MUTED, false);
        catalogStatus.setPadding(0, ui.dp(5), 0, 0);
        card.addView(catalogStatus);

        catalogSearchInput = ui.searchField("햄버거, 피자, 닭가슴살 검색");
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

        Button directFood = ui.button(
                showDirectFoodForm ? "직접 음식 입력 닫기" : "음식·외부 메뉴 직접 등록",
                false,
                v -> {
                    showDirectFoodForm = !showDirectFoodForm;
                    host.rerender();
                }
        );
        Button sync = ui.button("원격 카탈로그 새로고침", false, v -> syncCatalog(true));
        card.addView(ui.buttonRow(directFood, sync), ui.fullWidthParams(ui.dp(14)));

        if (showDirectFoodForm) {
            card.addView(directFoodForm(), ui.fullWidthParams(ui.dp(10)));
        }
        return card;
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
        details.addView(ui.text(food.name, 14, FitnessUi.COLOR_TEXT, true));
        details.addView(ui.text(food.extendedNutritionLabel() + " / " + food.basisLabel(),
                11,
                FitnessUi.COLOR_MUTED,
                false));
        String missingNotice = food.missingRequiredNotice();
        if (missingNotice != null) {
            details.addView(ui.text(missingNotice, 11, FitnessUi.COLOR_TERTIARY, false));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = ui.text("추가 ›", 12, FitnessUi.COLOR_TERTIARY, true);
        row.addView(add);
        return row;
    }

    private View directFoodForm() {
        FitnessUi ui = ui();
        LinearLayout form = ui.form();
        EditText name = ui.input("음식 또는 외부 메뉴 이름", "");
        Button kindButton = ui.button("종류: 외부 메뉴", false, null);
        String[] selectedKind = {NutritionFood.KIND_EXTERNAL_MENU};
        kindButton.setOnClickListener(v -> {
            boolean external = NutritionFood.KIND_EXTERNAL_MENU.equals(selectedKind[0]);
            selectedKind[0] = external ? NutritionFood.KIND_INGREDIENT : NutritionFood.KIND_EXTERNAL_MENU;
            kindButton.setText(external ? "종류: 재료" : "종류: 외부 메뉴");
        });
        EditText basisAmount = ui.decimalInput("기준 수량", "100");
        EditText basisUnit = ui.input("기준 단위 (g, ml, serving)", "g");
        Button prepStateButton = ui.button("", false, null);
        String[] selectedPrepState = {NutritionFood.PREP_UNSPECIFIED};
        prepStateButton.setText(prepStateLabel(selectedPrepState[0]));
        prepStateButton.setOnClickListener(v -> {
            selectedPrepState[0] = nextPrepState(selectedPrepState[0]);
            prepStateButton.setText(prepStateLabel(selectedPrepState[0]));
        });
        EditText source = ui.input("출처·메모 (선택)", "");
        EditText sourceVersion = ui.input("출처 버전 (선택, 예: MFDS 2024-03)", "");
        NutritionInputSection nutrients = new NutritionInputSection(ui, host.activity());
        ui.addAll(
                form,
                name,
                kindButton,
                basisAmount,
                basisUnit,
                prepStateButton,
                ui.text("아래 값은 모두 위 기준 수량에 대한 값입니다.", 11, FitnessUi.COLOR_MUTED, false),
                nutrients.view(),
                source,
                sourceVersion
        );
        form.addView(ui.button("저장 후 이번 식사에 추가", true, v -> {
            try {
                double basis = positiveNumber(basisAmount, "기준 수량");
                NutritionFood saved = host.nutritionCatalogRepository().saveFood(
                        FitnessUi.inputText(name),
                        selectedKind[0],
                        basis,
                        FitnessUi.inputText(basisUnit),
                        selectedPrepState[0],
                        nutrients.profile(),
                        "manual",
                        FitnessUi.inputText(source),
                        FitnessUi.inputText(sourceVersion)
                );
                draftItems.add(MealCompositionItem.from(saved, saved.basisAmount));
                showDirectFoodForm = false;
                syncCatalog(true);
                host.toast("음식이 저장되고 이번 식사에 추가되었습니다.");
                host.rerender();
            } catch (Exception error) {
                host.toast(error.getMessage() == null ? "음식 정보를 확인하세요." : error.getMessage());
            }
        }), ui.fullWidthParams(ui.dp(10)));
        return form;
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
        String name = draftName.trim();
        if (name.isEmpty()) {
            name = draftItems.size() == 1 ? draftItems.get(0).food.name : selectedMealType + " 식사";
        }
        NutritionTotals total = NutritionCalculator.sum(draftItems);
        repository().addMeal(
                selectedDate,
                selectedMealType,
                name,
                (int) Math.round(total.calories()),
                total.proteinGrams(),
                total.carbsGrams(),
                total.fatGrams(),
                draftItems
        );
        draftItems.clear();
        draftName = "";
        host.toast(selectedMealType + " 식사를 기록했습니다.");
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
        host.nutritionCatalogRepository().syncRemoteAsync(new NutritionCatalogRepository.SyncCallback() {
            @Override
            public void onComplete(int pushedRows, int pulledRows) {
                host.activity().runOnUiThread(() -> {
                    catalogSyncing = false;
                    syncMessage = pulledRows > 0
                            ? "원격 메뉴 " + pulledRows + "개를 최신 상태로 반영했습니다."
                            : (pushedRows > 0 ? "새 메뉴를 원격 카탈로그에 저장했습니다." :
                            "카탈로그가 최신 상태입니다.");
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
        if (compositionTotal == null) {
            return;
        }
        syncDraftFromViews();
        NutritionTotals total = NutritionCalculator.sum(draftItems);
        compositionTotal.setText(
                "합계  " + Math.round(total.calories()) + " kcal  ·  P " +
                        NutritionCalculator.trim(total.proteinGrams()) + "g  ·  C " +
                        NutritionCalculator.trim(total.carbsGrams()) + "g  ·  F " +
                        NutritionCalculator.trim(total.fatGrams()) + "g" +
                        "\n나트륨 " + NutritionCalculator.describeTotal(
                                total.total(NutritionProfile.SODIUM_MG)) + "mg  ·  포화지방 " +
                        NutritionCalculator.describeTotal(
                                total.total(NutritionProfile.SATURATED_FAT_GRAMS)) + "g  ·  당류 " +
                        NutritionCalculator.describeTotal(
                                total.total(NutritionProfile.SUGARS_GRAMS)) + "g"
        );
    }

    private static String nextPrepState(String prepState) {
        switch (NutritionFood.normalizePrepState(prepState)) {
            case NutritionFood.PREP_UNSPECIFIED:
                return NutritionFood.PREP_RAW;
            case NutritionFood.PREP_RAW:
                return NutritionFood.PREP_COOKED;
            case NutritionFood.PREP_COOKED:
                return NutritionFood.PREP_AS_SERVED;
            case NutritionFood.PREP_AS_SERVED:
                return NutritionFood.PREP_DRIED;
            case NutritionFood.PREP_DRIED:
                return NutritionFood.PREP_FROZEN;
            default:
                return NutritionFood.PREP_UNSPECIFIED;
        }
    }

    private static String prepStateLabel(String prepState) {
        return "조리 상태: " + NutritionFood.prepStateLabel(prepState);
    }

    private void confirmDeleteMeal(FitnessRepository.MealEntry entry) {
        ui().confirmSheet(
                "식사 기록 삭제",
                entry.mealType + " · " + entry.menu + " 기록을 삭제할까요?",
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
        if ("아침".equals(kind)) {
            return "아";
        }
        if ("점심".equals(kind)) {
            return "점";
        }
        if ("저녁".equals(kind)) {
            return "저";
        }
        if ("간식".equals(kind)) {
            return "간";
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
