package com.yeonsik.fitnessapp.ui;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.yeonsik.fitnessapp.R;
import com.yeonsik.fitnessapp.data.FitnessRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 식단 기록과 기기 로컬 저장 메뉴의 추가·불러오기·삭제 흐름을 담당한다. */
public final class MealDialogController {
    private final ScreenHost host;
    private final FitnessUi ui;
    private final FitnessRepository repository;

    public MealDialogController(ScreenHost host) {
        this.host = host;
        this.ui = host.ui();
        this.repository = host.repository();
    }

    public void show() {
        LinearLayout form = ui.form();
        String todayMealDate = host.today();
        String yesterdayMealDate = LocalDate.parse(todayMealDate).minusDays(1).toString();
        String[] selectedMealDate = {todayMealDate};
        Button mealDay = ui.button("식사일: 오늘 (탭하여 전날로 변경)", false, null);
        mealDay.setOnClickListener(v -> {
            boolean isToday = todayMealDate.equals(selectedMealDate[0]);
            selectedMealDate[0] = isToday ? yesterdayMealDate : todayMealDate;
            mealDay.setText(isToday
                    ? "식사일: 전날 (탭하여 오늘로 변경)"
                    : "식사일: 오늘 (탭하여 전날로 변경)");
        });

        EditText menu = ui.input("식단 내용", "닭가슴살 샐러드");
        EditText calories = ui.numberInput("칼로리 kcal (선택)", "");
        EditText protein = ui.decimalInput("단백질 g (선택)", "");
        EditText carbs = ui.decimalInput("탄수화물 g (선택)", "");
        EditText fat = ui.decimalInput("지방 g (선택)", "");
        Button loadPreset = ui.button("", false, null);
        updatePresetButton(loadPreset);
        loadPreset.setOnClickListener(v -> showPresetPicker(
                loadPreset, menu, calories, protein, carbs, fat));
        Button savePreset = ui.button("현재 입력을 메뉴로 저장", false, null);
        savePreset.setOnClickListener(v -> savePreset(
                loadPreset, menu, calories, protein, carbs, fat));

        ui.addAll(form, mealDay, loadPreset, menu, calories, protein, carbs, fat, savePreset);
        ui.sheet("식단 기록", form,
                "저장", () -> {
                    repository.addMeal(selectedMealDate[0], FitnessUi.inputText(menu),
                            FitnessUi.optionalInt(calories),
                            FitnessUi.optionalDouble(protein), FitnessUi.optionalDouble(carbs),
                            FitnessUi.optionalDouble(fat));
                    host.rerender();
                }, null, null);
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
        new AlertDialog.Builder(host.activity())
                .setTitle("저장된 메뉴 불러오기")
                .setItems(labels, (dialog, which) -> {
                    FitnessRepository.MealMenuPreset preset = presets.get(which);
                    menu.setText(preset.name);
                    calories.setText(preset.calories == null ? "" : String.valueOf(preset.calories));
                    protein.setText(nullableNumber(preset.proteinGrams));
                    carbs.setText(nullableNumber(preset.carbsGrams));
                    fat.setText(nullableNumber(preset.fatGrams));
                    host.toast("저장된 메뉴를 입력칸에 적용했습니다.");
                })
                .setNeutralButton("메뉴 관리", (dialog, which) ->
                        loadButton.post(() -> showPresetManager(loadButton)))
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showPresetManager(Button loadButton) {
        List<FitnessRepository.MealMenuPreset> presets = repository.mealMenuPresets();
        if (presets.isEmpty()) {
            host.toast("관리할 저장 메뉴가 없습니다.");
            return;
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("저장 메뉴 관리")
                .setItems(labels(presets), (dialog, which) ->
                        confirmDeletePreset(loadButton, presets.get(which)))
                .setNegativeButton("닫기", null)
                .show();
    }

    private void confirmDeletePreset(Button loadButton, FitnessRepository.MealMenuPreset preset) {
        new AlertDialog.Builder(host.activity())
                .setTitle("저장 메뉴 삭제")
                .setMessage(preset.name + " 메뉴를 삭제하시겠습니까? 과거 식단 기록은 유지됩니다.")
                .setPositiveButton("삭제", (dialog, which) -> {
                    if (repository.deleteMealMenuPreset(preset.id)) {
                        updatePresetButton(loadButton);
                        host.toast("저장 메뉴를 삭제했습니다.");
                    }
                })
                .setNegativeButton("취소", null)
                .show();
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
