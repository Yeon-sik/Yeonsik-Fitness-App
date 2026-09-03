package com.yeonsik.fitnessapp.ui;

import android.app.Dialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yeonsik.fitnessapp.R;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionCalculator;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionTotals;
import com.yeonsik.fitnessapp.data.NutritionUnit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 식단 기록, 음식 구성, 기기 로컬 저장 메뉴의 입력 흐름을 담당한다. */
public final class MealDialogController {
    private final ScreenHost host;
    private final FitnessUi ui;
    private final FormSystem forms;
    private final FitnessRepository repository;
    private final NutritionCatalogRepository catalogRepository;

    public MealDialogController(ScreenHost host) {
        this.host = host;
        this.ui = host.ui();
        this.forms = new FormSystem(ui, host.activity());
        this.repository = host.repository();
        this.catalogRepository = host.nutritionCatalogRepository();
    }

    public void show() {
        LinearLayout form = forms.column();
        form.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        String todayMealDate = host.today();
        String yesterdayMealDate = LocalDate.parse(todayMealDate).minusDays(1).toString();
        String[] selectedMealDate = {todayMealDate};
        Button mealDay = forms.selector("식사일: 오늘 (탭하여 전날로 변경)", null);
        mealDay.setOnClickListener(v -> {
            boolean isToday = todayMealDate.equals(selectedMealDate[0]);
            selectedMealDate[0] = isToday ? yesterdayMealDate : todayMealDate;
            mealDay.setText(isToday
                    ? "식사일: 전날 (탭하여 오늘로 변경)"
                    : "식사일: 오늘 (탭하여 전날로 변경)");
        });

        EditText menu = forms.textInput("식단 내용", "닭가슴살 샐러드");
        EditText calories = forms.numberInput("입력", "");
        EditText protein = forms.decimalInput("입력", "");
        EditText carbs = forms.decimalInput("입력", "");
        EditText fat = forms.decimalInput("입력", "");
        NutritionRow caloriesRow = forms.nutrientInputRow("칼로리 (선택)", "kcal", calories);
        NutritionRow proteinRow = forms.nutrientInputRow("단백질 (선택)", "g", protein);
        NutritionRow carbsRow = forms.nutrientInputRow("탄수화물 (선택)", "g", carbs);
        NutritionRow fatRow = forms.nutrientInputRow("지방 (선택)", "g", fat);
        Button loadPreset = ui.secondaryButton("", null);
        updatePresetButton(loadPreset);
        loadPreset.setOnClickListener(v -> showPresetPicker(
                loadPreset, menu, calories, protein, carbs, fat));
        Button savePreset = ui.secondaryButton("현재 입력을 메뉴로 저장", null);
        savePreset.setOnClickListener(v -> savePreset(
                loadPreset, menu, calories, protein, carbs, fat));

        List<MealCompositionItem> compositionItems = new ArrayList<>();
        LinearLayout compositionRows = new LinearLayout(host.activity());
        compositionRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout compositionTotal = forms.column();
        Button addFood = ui.secondaryButton("음식/재료 추가", null);
        addFood.setOnClickListener(v -> showFoodPicker(
                compositionItems,
                compositionRows,
                compositionTotal,
                calories,
                protein,
                carbs,
                fat
        ));
        Button saveRecipe = ui.secondaryButton("현재 구성을 메뉴로 저장", null);
        saveRecipe.setOnClickListener(v -> saveRecipe(menu, compositionItems));

        ui.addAll(
                form,
                forms.field("식사일", mealDay),
                loadPreset,
                forms.field("메뉴명", menu),
                addFood,
                forms.sectionTitle("구성 합계"),
                compositionTotal,
                compositionRows,
                forms.sectionTitle("영양정보 (선택)"),
                caloriesRow.view(),
                carbsRow.view(),
                proteinRow.view(),
                fatRow.view(),
                saveRecipe,
                savePreset
        );
        renderComposition(
                compositionItems,
                compositionRows,
                compositionTotal,
                calories,
                protein,
                carbs,
                fat
        );
        ui.bottomSheet("식단 기록", form,
                "저장", () -> {
                    if (compositionItems.isEmpty()) {
                        repository.addMeal(selectedMealDate[0], FitnessUi.inputText(menu),
                                FitnessUi.optionalInt(calories), FitnessUi.optionalDouble(protein),
                                FitnessUi.optionalDouble(carbs), FitnessUi.optionalDouble(fat));
                    } else {
                        NutritionTotals total = NutritionCalculator.sum(compositionItems);
                        repository.addMeal(
                                selectedMealDate[0],
                                FitnessUi.inputText(menu),
                                (int) Math.round(total.calories()),
                                total.proteinGrams(),
                                total.carbsGrams(),
                                total.fatGrams(),
                                compositionItems
                        );
                    }
                    host.rerender();
                }, null, null);
    }

    private void showFoodPicker(
            List<MealCompositionItem> compositionItems,
            LinearLayout compositionRows,
            LinearLayout compositionTotal,
            EditText calories,
            EditText protein,
            EditText carbs,
            EditText fat
    ) {
        LinearLayout body = forms.column();
        EditText search = ui.searchInput("음식 이름 검색");
        Button newFood = forms.bottomAction("새 음식/재료 직접 입력", null);
        LinearLayout results = new LinearLayout(host.activity());
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(host.activity());
        scroll.addView(results, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        ui.addAll(body, forms.sectionTitle("음식 선택"), forms.field("음식 검색", search), newFood, scroll);

        final Dialog[] pickerHolder = new Dialog[1];

        Runnable populate = () -> {
            results.removeAllViews();
            List<NutritionFood> foods = catalogRepository.searchFoods(
                    FitnessUi.inputText(search)
            );
            if (foods.isEmpty()) {
                results.addView(ui.text(
                        "검색 결과가 없습니다. 새 음식/재료를 직접 입력하세요.",
                        13,
                        FitnessUi.COLOR_MUTED,
                        false
                ));
                return;
            }
            for (NutritionFood food : foods) {
                Button result = ui.button(
                        NutritionFood.kindLabel(food.kind) + " · " + food.name,
                        false,
                        null
                );
                result.setOnClickListener(v -> {
                    if (pickerHolder[0] != null) {
                        pickerHolder[0].dismiss();
                    }
                    showQuantityDialog(
                            food,
                            compositionItems,
                            compositionRows,
                            compositionTotal,
                            calories,
                            protein,
                            carbs,
                            fat
                    );
                });
                results.addView(result, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                ));
            }
        };

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                populate.run();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        newFood.setOnClickListener(v -> {
            if (pickerHolder[0] != null) {
                pickerHolder[0].dismiss();
            }
            showNewFoodDialog(
                    compositionItems,
                    compositionRows,
                    compositionTotal,
                    calories,
                    protein,
                    carbs,
                    fat
            );
        });

        Dialog picker = ui.bottomSheet("먹은 음식 추가", body, "닫기", () -> { }, null, null);
        pickerHolder[0] = picker;
        populate.run();
        host.syncNutritionCatalog(new NutritionCatalogRepository.SyncCallback() {
            @Override
            public void onComplete(int pushedRows, int pulledRows) {
                host.activity().runOnUiThread(() -> {
                    if (picker.isShowing()) {
                        populate.run();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                host.activity().runOnUiThread(() -> {
                    if (picker.isShowing()) {
                        host.toast("원격 음식 목록을 불러오지 못했습니다. 로컬 목록을 표시합니다.");
                    }
                });
            }
        });
    }

    private void showQuantityDialog(
            NutritionFood food,
            List<MealCompositionItem> compositionItems,
            LinearLayout compositionRows,
            LinearLayout compositionTotal,
            EditText calories,
            EditText protein,
            EditText carbs,
            EditText fat
    ) {
        LinearLayout body = forms.column();
        EditText quantity = forms.decimalInput(
                "수량 (" + NutritionUnit.display(food.basisUnit) + ")",
                NutritionCalculator.trim(food.basisAmount)
        );
        ui.addAll(body, ui.text(
                food.displayName() + " · 기준 " + food.basisLabel() + " = " + food.extendedNutritionLabel(),
                13,
                FitnessUi.COLOR_MUTED,
                false
        ), ui.text(
                food.unitNutritionLabel(),
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        ), ui.text(
                micronutrientSummary(food),
                12,
                FitnessUi.COLOR_MUTED,
                false
        ), forms.field("섭취량 (" + NutritionUnit.display(food.basisUnit) + ")", quantity));

        ui.validatedSheet("섭취량 입력", body, "추가", () -> {
            try {
                double amount = Double.parseDouble(FitnessUi.inputText(quantity).trim());
                if (amount <= 0) {
                    throw new IllegalArgumentException("수량은 0보다 커야 합니다.");
                }
                compositionItems.add(MealCompositionItem.from(food, amount));
                renderComposition(
                        compositionItems,
                        compositionRows,
                        compositionTotal,
                        calories,
                        protein,
                        carbs,
                        fat
                );
                return true;
            } catch (Exception error) {
                host.toast(error.getMessage() == null
                        ? "수량을 확인하세요."
                        : error.getMessage());
                return false;
            }
        });
    }

    private void showNewFoodDialog(
            List<MealCompositionItem> compositionItems,
            LinearLayout compositionRows,
            LinearLayout compositionTotal,
            EditText calories,
            EditText protein,
            EditText carbs,
            EditText fat
    ) {
        LinearLayout body = forms.column();
        EditText name = forms.textInput("이름", "");
        Button kindButton = forms.selector("유형: 외부 메뉴 (탭하여 재료)", null);
        String[] selectedKind = {NutritionFood.KIND_EXTERNAL_MENU};
        kindButton.setOnClickListener(v -> {
            boolean ingredient = NutritionFood.KIND_EXTERNAL_MENU.equals(selectedKind[0]);
            selectedKind[0] = ingredient
                    ? NutritionFood.KIND_INGREDIENT
                    : NutritionFood.KIND_EXTERNAL_MENU;
            kindButton.setText(ingredient
                    ? "유형: 재료 (탭하여 외부 메뉴)"
                    : "유형: 외부 메뉴 (탭하여 재료)");
        });
        EditText basisAmount = forms.decimalInput("기준 수량", "100");
        Button basisUnit = NutritionUnitSelector.create(ui, host.activity(), NutritionUnit.GRAM);
        Button prepStateButton = forms.selector("", null);
        String[] selectedPrepState = {NutritionFood.PREP_UNSPECIFIED};
        prepStateButton.setText(prepStateButtonLabel(selectedPrepState[0]));
        prepStateButton.setOnClickListener(v -> {
            selectedPrepState[0] = nextPrepState(selectedPrepState[0]);
            prepStateButton.setText(prepStateButtonLabel(selectedPrepState[0]));
        });
        NutritionInputSection nutrients = new NutritionInputSection(ui, host.activity());
        TextView unitNutritionPreview = ui.text(
                "단위 영양성분: 기준량과 필수 영양성분을 입력하면 자동 계산됩니다.",
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        NutritionUnitPreview.bind(unitNutritionPreview, basisAmount, basisUnit, nutrients);
        ui.addAll(
                body,
                forms.sectionTitle("음식 정보"),
                forms.field("이름", name),
                forms.field("유형", kindButton),
                forms.sectionTitle("분류와 기준량"),
                forms.field("기준 수량", basisAmount),
                forms.field("기준 단위", basisUnit),
                forms.field("조리 상태", prepStateButton),
                ui.text(
                        "아래 값은 모두 위 기준 수량에 대한 값입니다.",
                        13,
                        FitnessUi.COLOR_MUTED,
                        false
                ),
                nutrients.view(),
                unitNutritionPreview
        );

        ScrollView bodyScroll = new ScrollView(host.activity());
        bodyScroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        ui.validatedSheet("음식/재료 저장", bodyScroll, "저장 후 추가", () -> {
            try {
                double basis = parseRequired(basisAmount, "기준 수량");
                NutritionFood saved = catalogRepository.saveFood(
                        FitnessUi.inputText(name),
                        selectedKind[0],
                        basis,
                        NutritionUnitSelector.value(basisUnit),
                        selectedPrepState[0],
                        nutrients.profile(),
                        "manual",
                        "",
                        ""
                );
                compositionItems.add(MealCompositionItem.from(saved, saved.basisAmount));
                renderComposition(
                        compositionItems,
                        compositionRows,
                        compositionTotal,
                        calories,
                        protein,
                        carbs,
                        fat
                );
                syncCatalogQuietly();
                host.toast("음식을 저장하고 식사 구성에 추가했습니다.");
                return true;
            } catch (Exception error) {
                host.toast(error.getMessage() == null
                        ? "음식 정보를 확인하세요."
                        : error.getMessage());
                return false;
            }
        });
    }

    private void renderComposition(
            List<MealCompositionItem> items,
            LinearLayout rows,
            LinearLayout totalView,
            EditText calories,
            EditText protein,
            EditText carbs,
            EditText fat
    ) {
        rows.removeAllViews();
        for (MealCompositionItem item : items) {
            LinearLayout row = new LinearLayout(host.activity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = ui.text(
                    item.label() + "\n" + item.detailLabel(),
                    14,
                    FitnessUi.COLOR_TEXT,
                    false
            );
            Button remove = ui.button("삭제", false, null);
            remove.setOnClickListener(v -> {
                items.remove(item);
                renderComposition(items, rows, totalView, calories, protein, carbs, fat);
            });
            row.addView(label, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));
            row.addView(remove, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            rows.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        NutritionTotals total = NutritionCalculator.sum(items);
        totalView.removeAllViews();
        if (items.isEmpty()) {
            totalView.addView(forms.helper("음식을 추가하면 영양값을 자동 계산합니다."));
        } else {
            for (String key : new String[]{
                    NutritionProfile.CALORIES_KCAL,
                    NutritionProfile.CARBS_GRAMS,
                    NutritionProfile.PROTEIN_GRAMS,
                    NutritionProfile.FAT_GRAMS,
                    NutritionProfile.SUGARS_GRAMS,
                    NutritionProfile.SATURATED_FAT_GRAMS,
                    NutritionProfile.SODIUM_MG
            }) {
                totalView.addView(forms.nutrientRow(
                        key,
                        NutritionCalculator.describeTotal(total.total(key))
                ), ui.fullWidthParams(0));
            }
            for (String code : total.knownMicronutrientCodes()) {
                totalView.addView(forms.nutrientRow(
                        NutrientCode.labelOf(code),
                        NutritionCalculator.describeTotal(total.total(code)),
                        NutrientCode.displayUnit(NutrientCode.unitOf(code))
                ), ui.fullWidthParams(0));
            }
        }
        if (!items.isEmpty()) {
            calories.setText(String.valueOf(Math.round(total.calories())));
            protein.setText(NutritionCalculator.trim(total.proteinGrams()));
            carbs.setText(NutritionCalculator.trim(total.carbsGrams()));
            fat.setText(NutritionCalculator.trim(total.fatGrams()));
        }
    }

    /** 선택한 음식이 아는 미네랄·비타민을 기준량 기준으로 보여 준다. */
    private String micronutrientSummary(NutritionFood food) {
        List<String> parts = new ArrayList<>();
        for (String code : food.profile.knownMicronutrientCodes()) {
            parts.add(NutrientCode.labelOf(code) + " "
                    + NutritionCalculator.trimNullable(food.profile.value(code))
                    + NutrientCode.displayUnit(NutrientCode.unitOf(code)));
        }
        return parts.isEmpty()
                ? "미네랄·비타민 정보 없음 (모름으로 기록됩니다)"
                : String.join(" · ", parts);
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

    private static String prepStateButtonLabel(String prepState) {
        return "조리 상태: " + NutritionFood.prepStateLabel(prepState) + " (탭하여 변경)";
    }

    private void saveRecipe(EditText menu, List<MealCompositionItem> items) {
        String name = FitnessUi.inputText(menu).trim();
        if (name.isEmpty()) {
            host.toast("저장할 메뉴 이름을 입력하세요.");
            return;
        }
        if (items.isEmpty()) {
            host.toast("메뉴로 저장할 음식을 먼저 추가하세요.");
            return;
        }
        try {
            catalogRepository.saveRecipe(name, items);
            syncCatalogQuietly();
            host.toast("구성 메뉴를 저장했습니다. 다음 식사에서 검색할 수 있습니다.");
        } catch (Exception error) {
            host.toast(error.getMessage() == null ? "메뉴 저장에 실패했습니다." : error.getMessage());
        }
    }

    private void syncCatalogQuietly() {
        host.syncNutritionCatalog(new NutritionCatalogRepository.SyncCallback() {
            @Override
            public void onComplete(int pushedRows, int pulledRows) {
            }

            @Override
            public void onError(Exception error) {
                host.activity().runOnUiThread(() -> host.toast(
                        "로컬에는 저장했지만 원격 음식 DB 동기화에 실패했습니다."
                ));
            }
        });
    }

    private double parseRequired(EditText input, String label) {
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

    private void savePreset(Button loadButton, EditText menu, EditText calories,
                            EditText protein, EditText carbs, EditText fat) {
        String name = FitnessUi.inputText(menu).trim();
        if (name.isEmpty()) {
            host.toast("저장할 메뉴 이름을 입력하세요.");
            return;
        }
        repository.saveMealMenuPreset(
                name,
                FitnessUi.optionalInt(calories),
                FitnessUi.optionalDouble(protein),
                FitnessUi.optionalDouble(carbs),
                FitnessUi.optionalDouble(fat)
        );
        updatePresetButton(loadButton);
        host.toast("메뉴를 저장했습니다. 같은 이름은 최신 값으로 갱신됩니다.");
    }

    private void updatePresetButton(Button button) {
        int count = repository.mealMenuPresets().size();
        button.setText(host.activity().getString(R.string.meal_preset_load_count, count));
    }

    private void showPresetPicker(Button loadButton, EditText menu, EditText calories,
                                  EditText protein, EditText carbs, EditText fat) {
        List<FitnessRepository.MealMenuPreset> presets = repository.mealMenuPresets();
        if (presets.isEmpty()) {
            host.toast("저장된 메뉴가 없습니다. 현재 입력을 먼저 메뉴로 저장하세요.");
            return;
        }

        String[] labels = labels(presets);
        ui.choiceSheet(
                "저장된 메뉴 불러오기",
                java.util.Arrays.asList(labels),
                -1,
                "메뉴 관리",
                () -> loadButton.post(() -> showPresetManager(loadButton)),
                which -> {
                    FitnessRepository.MealMenuPreset preset = presets.get(which);
                    menu.setText(preset.name);
                    calories.setText(preset.calories == null ? "" : String.valueOf(preset.calories));
                    protein.setText(nullableNumber(preset.proteinGrams));
                    carbs.setText(nullableNumber(preset.carbsGrams));
                    fat.setText(nullableNumber(preset.fatGrams));
                    host.toast("저장된 메뉴를 입력칸에 적용했습니다.");
                });
    }

    private void showPresetManager(Button loadButton) {
        List<FitnessRepository.MealMenuPreset> presets = repository.mealMenuPresets();
        if (presets.isEmpty()) {
            host.toast("관리할 저장 메뉴가 없습니다.");
            return;
        }
        ui.choiceSheet("저장 메뉴 관리", java.util.Arrays.asList(labels(presets)), -1,
                which -> confirmDeletePreset(loadButton, presets.get(which)));
    }

    private void confirmDeletePreset(Button loadButton, FitnessRepository.MealMenuPreset preset) {
        ui.confirmSheet(
                "저장 메뉴 삭제",
                preset.name + " 메뉴를 삭제하시겠습니까? 과거 식단 기록은 유지됩니다.",
                null,
                "삭제",
                () -> {
                    if (repository.deleteMealMenuPreset(preset.id)) {
                        updatePresetButton(loadButton);
                        host.toast("저장 메뉴를 삭제했습니다.");
                    }
                });
    }

    private String[] labels(List<FitnessRepository.MealMenuPreset> presets) {
        String[] labels = new String[presets.size()];
        for (int index = 0; index < presets.size(); index++) {
            labels[index] = label(presets.get(index));
        }
        return labels;
    }

    private String label(FitnessRepository.MealMenuPreset preset) {
        List<String> nutrition = new ArrayList<>();
        if (preset.calories != null) {
            nutrition.add(preset.calories + "kcal");
        }
        if (preset.proteinGrams != null) {
            nutrition.add(FitnessUi.trimDouble(preset.proteinGrams) + "g 단백질");
        }
        return nutrition.isEmpty()
                ? preset.name
                : preset.name + " · " + String.join(" · ", nutrition);
    }

    private String nullableNumber(Double value) {
        return value == null ? "" : FitnessUi.trimDouble(value);
    }
}
