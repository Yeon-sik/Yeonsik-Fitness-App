package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionCalculator;
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
    private final Activity activity;
    private final FormSystem forms;
    private final Map<String, EditText> requiredInputs = new LinkedHashMap<>();
    private final Map<String, EditText> optionalInputs = new LinkedHashMap<>();
    private final Map<String, TextView> errorViews = new LinkedHashMap<>();
    private final LinearLayout container;

    NutritionInputSection(FitnessUi ui, Activity activity) {
        this.ui = ui;
        this.activity = activity;
        this.forms = new FormSystem(ui, activity);
        this.container = column();
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
        for (Map.Entry<String, EditText> entry : requiredInputs.entrySet()) {
            String key = entry.getKey();
            String raw = FitnessUi.inputText(entry.getValue()).trim();
            try {
                if (raw.isEmpty()) {
                    throw new IllegalArgumentException(
                            NutritionRow.displayLabel(key) + "은(는) 필수 입력입니다."
                    );
                }
                builder.value(key, parse(key, raw));
            } catch (IllegalArgumentException error) {
                showError(key, error.getMessage());
                focusInput(key);
                throw error;
            }
        }
        for (Map.Entry<String, EditText> entry : optionalInputs.entrySet()) {
            String key = entry.getKey();
            String raw = FitnessUi.inputText(entry.getValue()).trim();
            if (raw.isEmpty()) {
                // 비워 두면 키를 넣지 않는다. 0으로 채우면 "모름"이 사라진다.
                continue;
            }
            try {
                builder.value(key, parse(key, raw));
            } catch (IllegalArgumentException error) {
                showError(key, error.getMessage());
                focusInput(key);
                throw error;
            }
        }
        return builder.build();
    }

    void addChangeListener(Runnable listener) {
        if (listener == null) {
            return;
        }
        List<EditText> inputs = new ArrayList<>();
        inputs.addAll(requiredInputs.values());
        inputs.addAll(optionalInputs.values());
        for (EditText input : inputs) {
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    listener.run();
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
        }
    }

    void applyProfile(NutritionProfile profile) {
        if (profile == null) {
            return;
        }
        for (Map.Entry<String, EditText> entry : requiredInputs.entrySet()) {
            setInputValue(entry.getValue(), profile.value(entry.getKey()));
        }
        for (Map.Entry<String, EditText> entry : optionalInputs.entrySet()) {
            setInputValue(entry.getValue(), profile.value(entry.getKey()));
        }
    }

    /** Returns the raw values so an unfinished form can be restored without validating it. */
    Map<String, String> inputValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, EditText> entry : requiredInputs.entrySet()) {
            values.put(entry.getKey(), FitnessUi.inputText(entry.getValue()));
        }
        for (Map.Entry<String, EditText> entry : optionalInputs.entrySet()) {
            values.put(entry.getKey(), FitnessUi.inputText(entry.getValue()));
        }
        return values;
    }

    /** Restores raw values without changing the required/optional validation policy. */
    void applyInputValues(Map<String, String> values) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, EditText> entry : requiredInputs.entrySet()) {
            setRawInputValue(entry.getValue(), values.get(entry.getKey()));
        }
        for (Map.Entry<String, EditText> entry : optionalInputs.entrySet()) {
            setRawInputValue(entry.getValue(), values.get(entry.getKey()));
        }
    }

    private void setRawInputValue(EditText input, String value) {
        input.setText(value == null ? "" : value);
    }

    private void setInputValue(EditText input, Double value) {
        input.setText(value == null ? "" : NutritionCalculator.trim(value));
    }

    private void build() {
        ui.addAll(container, forms.sectionTitle("필수 영양성분"));
        addFieldRows(container, NutritionProfile.PRIMARY_DISPLAY_ORDER, requiredInputs, true);

        ui.addAll(container, forms.helper(
                "권고 영양성분 · 비워 두면 0이 아니라 '모름'으로 저장됩니다"
        ));
        addFieldRows(container, NutritionProfile.RECOMMENDED_TYPED_KEYS, optionalInputs, false);

        LinearLayout micronutrients = column();
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
            Map<String, EditText> target,
            boolean required
    ) {
        for (String key : keys) {
            NutritionRow row = forms.nutrientInputRow(
                    NutritionRow.displayLabel(key) + (required ? " *" : ""),
                    NutritionRow.displayUnit(key),
                    ""
            );
            EditText input = row.inputField();
            input.setContentDescription(
                    NutritionRow.displayLabel(key) + (required ? " 필수" : " 선택")
            );
            target.put(key, input);
            LinearLayout field = forms.column();
            field.addView(row.view(), ui.fullWidthParams(0));
            TextView error = forms.error("");
            error.setPadding(0, 0, 0, ui.dp(2));
            errorViews.put(key, error);
            final String errorKey = key;
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                }
                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    clearError(errorKey);
                }
                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
            field.addView(error, ui.fullWidthParams(0));
            parent.addView(field, ui.fullWidthParams(0));
        }
    }

    private void showError(String key, String message) {
        TextView error = errorViews.get(key);
        if (error != null) {
            forms.showError(error, message);
        }
    }
    private void clearError(String key) {
        TextView error = errorViews.get(key);
        if (error != null) {
            forms.clearError(error);
        }
    }
    private void clearErrors() {
        for (TextView error : errorViews.values()) {
            forms.clearError(error);
        }
    }
    private void focusInput(String key) {
        EditText input = requiredInputs.get(key);
        if (input == null) {
            input = optionalInputs.get(key);
        }
        if (input != null) {
            input.requestFocus();
        }
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static double parse(String key, String raw) {
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    NutritionRow.displayLabel(key) + " 값이 숫자가 아닙니다."
            );
        }
        if (value < 0) {
            throw new IllegalArgumentException(
                    NutritionRow.displayLabel(key) + "은(는) 음수가 될 수 없습니다."
            );
        }
        return value;
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
