package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
    private final Activity activity;
    private final Map<String, EditText> requiredInputs = new LinkedHashMap<>();
    private final Map<String, EditText> optionalInputs = new LinkedHashMap<>();
    private final LinearLayout container;

    NutritionInputSection(FitnessUi ui, Activity activity) {
        this.ui = ui;
        this.activity = activity;
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
        for (Map.Entry<String, EditText> entry : requiredInputs.entrySet()) {
            String key = entry.getKey();
            String raw = FitnessUi.inputText(entry.getValue()).trim();
            if (raw.isEmpty()) {
                throw new IllegalArgumentException(
                        NutritionProfile.labelOf(key) + "은(는) 필수 입력입니다."
                );
            }
            builder.value(key, parse(key, raw));
        }
        for (Map.Entry<String, EditText> entry : optionalInputs.entrySet()) {
            String raw = FitnessUi.inputText(entry.getValue()).trim();
            if (raw.isEmpty()) {
                // 비워 두면 키를 넣지 않는다. 0으로 채우면 "모름"이 사라진다.
                continue;
            }
            builder.value(entry.getKey(), parse(entry.getKey(), raw));
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

    private void build() {
        ui.addAll(container, ui.text("필수 영양성분", 14, FitnessUi.COLOR_TEXT, true));
        for (String key : NutritionProfile.REQUIRED_KEYS) {
            requiredInputs.put(key, addField(container, key, "필수"));
        }

        ui.addAll(container, ui.text(
                "권고 영양성분 · 비워 두면 0이 아니라 '모름'으로 저장됩니다",
                13,
                FitnessUi.COLOR_MUTED,
                false
        ));
        for (String key : NutritionProfile.RECOMMENDED_TYPED_KEYS) {
            optionalInputs.put(key, addField(container, key, "선택"));
        }

        LinearLayout micronutrients = column();
        micronutrients.setVisibility(View.GONE);
        Button toggle = ui.button("미네랄·비타민 입력 열기", false, null);
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
        ui.addAll(parent, ui.text(title, 14, FitnessUi.COLOR_TEXT, true));
        for (NutrientCode nutrient : NutrientCode.group(group)) {
            optionalInputs.put(nutrient.code, addField(parent, nutrient.code, "선택"));
        }
    }

    private EditText addField(LinearLayout parent, String key, String requirement) {
        String unit = NutrientCode.displayUnit(NutritionProfile.unitOf(key));
        EditText input = ui.decimalInput(
                NutritionProfile.labelOf(key) + " " + unit + " (" + requirement + ")",
                ""
        );
        ui.addAll(parent, input);
        return input;
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
                    NutritionProfile.labelOf(key) + " 값이 숫자가 아닙니다."
            );
        }
        if (value < 0) {
            throw new IllegalArgumentException(
                    NutritionProfile.labelOf(key) + "은(는) 음수가 될 수 없습니다."
            );
        }
        return value;
    }

    /** 화면 안내용 필수 영양소 이름 목록. */
    static List<String> requiredLabels() {
        List<String> labels = new ArrayList<>();
        for (String key : NutritionProfile.REQUIRED_KEYS) {
            labels.add(NutritionProfile.labelOf(key));
        }
        return labels;
    }
}
