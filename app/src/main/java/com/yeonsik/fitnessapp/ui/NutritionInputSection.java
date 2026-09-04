package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 음식 영양성분 입력 묶음.
 *
 * <p>필수 7종은 반드시 채워야 하고, 권고 영양소와 미네랄·비타민은 비워 두면 0이 아니라
 * "모름"으로 저장된다. 그 차이를 입력 화면에서부터 분명히 하려고 힌트에 명시한다.</p>
 */
final class NutritionInputSection {
    private final FitnessUi ui;
    private final FormSystem forms;
    private final Map<String, NutritionInputField> requiredInputs = new LinkedHashMap<>();
    private final Map<String, NutritionInputField> optionalInputs = new LinkedHashMap<>();
    private final LinearLayout container;

    NutritionInputSection(FitnessUi ui, Activity activity) {
        this.ui = ui;
        this.forms = new FormSystem(ui, activity);
        this.container = forms.column();
        build();
    }

    View view() {
        return container;
    }

    /**
     * 입력값을 영양성분 묶음으로 바꾼다.
     *
     * @throws IllegalArgumentException 필수값이 비었거나 숫자가 아닐 때
     */
    NutritionProfile profile() {
        NutritionProfile.Builder builder = NutritionProfile.builder();
        clearErrors();
        for (Map.Entry<String, NutritionInputField> entry : requiredInputs.entrySet()) {
            builder.value(entry.getKey(), entry.getValue().parse());
        }
        for (Map.Entry<String, NutritionInputField> entry : optionalInputs.entrySet()) {
            Double parsed = entry.getValue().parse();
            if (parsed != null) {
                builder.value(entry.getKey(), parsed);
            }
        }
        return builder.build();
    }

    void addChangeListener(Runnable listener) {
        if (listener == null) {
            return;
        }
        for (NutritionInputField field : allFields()) {
            field.addChangeListener(listener);
        }
    }

    void applyProfile(NutritionProfile profile) {
        if (profile == null) {
            return;
        }
        for (Map.Entry<String, NutritionInputField> entry : requiredInputs.entrySet()) {
            entry.getValue().setNumericValue(profile.value(entry.getKey()));
        }
        for (Map.Entry<String, NutritionInputField> entry : optionalInputs.entrySet()) {
            entry.getValue().setNumericValue(profile.value(entry.getKey()));
        }
    }

    /** Returns the raw values so an unfinished form can be restored without validating it. */
    Map<String, String> inputValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, NutritionInputField> entry : requiredInputs.entrySet()) {
            values.put(entry.getKey(), entry.getValue().rawValue());
        }
        for (Map.Entry<String, NutritionInputField> entry : optionalInputs.entrySet()) {
            values.put(entry.getKey(), entry.getValue().rawValue());
        }
        return values;
    }

    /** Restores raw values without changing the required/optional validation policy. */
    void applyInputValues(Map<String, String> values) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, NutritionInputField> entry : requiredInputs.entrySet()) {
            entry.getValue().setRawValue(values.get(entry.getKey()));
        }
        for (Map.Entry<String, NutritionInputField> entry : optionalInputs.entrySet()) {
            entry.getValue().setRawValue(values.get(entry.getKey()));
        }
    }

    private void build() {
        ui.addAll(container, forms.sectionTitle("필수 영양성분"));
        addFieldRows(container, NutritionProfile.PRIMARY_DISPLAY_ORDER, requiredInputs, true);

        ui.addAll(container, forms.helper(
                "권고 영양성분 · 비워 두면 0이 아니라 '모름'으로 저장됩니다"
        ));
        addFieldRows(container, NutritionProfile.RECOMMENDED_TYPED_KEYS, optionalInputs, false);

        LinearLayout micronutrients = forms.column();
        micronutrients.setVisibility(View.GONE);
        Button toggle = ui.secondaryButton("미네랄·비타민 입력 열기", null);
        toggle.setOnClickListener(v -> {
            boolean opening = micronutrients.getVisibility() == View.GONE;
            micronutrients.setVisibility(opening ? View.VISIBLE : View.GONE);
            toggle.setText(opening ? "미네랄·비타민 입력 닫기" : "미네랄·비타민 입력 열기");
        });
        ui.addAll(container, toggle, micronutrients);

        addMicronutrientGroup(micronutrients, NutrientCode.GROUP_MINERAL, "미네랄");
        addMicronutrientGroup(micronutrients, NutrientCode.GROUP_VITAMIN, "비타민");
    }

    private void addMicronutrientGroup(LinearLayout parent, String group, String title) {
        ui.addAll(parent, forms.sectionTitle(title));
        List<String> keys = new ArrayList<>();
        for (NutrientCode nutrient : NutrientCode.group(group)) {
            keys.add(nutrient.code);
        }
        addFieldRows(parent, keys, optionalInputs, false);
    }

    /** Adds one compact row per nutrient so the form is vertically scannable. */
    private void addFieldRows(
            LinearLayout parent,
            List<String> keys,
            Map<String, NutritionInputField> target,
            boolean required
    ) {
        for (String key : keys) {
            String label = NutritionRow.displayLabel(key);
            NutritionInputField field = new NutritionInputField(
                    ui,
                    forms,
                    key,
                    label,
                    "",
                    required,
                    label + (required ? " 필수" : " 선택"),
                    false
            );
            target.put(key, field);
            parent.addView(field.view(), ui.fullWidthParams(0));
        }
    }

    private List<NutritionInputField> allFields() {
        List<NutritionInputField> fields = new ArrayList<>();
        fields.addAll(requiredInputs.values());
        fields.addAll(optionalInputs.values());
        return fields;
    }

    private void clearErrors() {
        for (NutritionInputField field : allFields()) {
            field.clearError();
        }
    }

    /** 화면 안내용 필수 영양소 이름 목록. */
    static List<String> requiredLabels() {
        List<String> labels = new ArrayList<>();
        for (String key : NutritionProfile.PRIMARY_DISPLAY_ORDER) {
            labels.add(NutritionRow.displayLabel(key));
        }
        return labels;
    }
}