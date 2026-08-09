package com.yeonsik.fitnessapp.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.NutritionCalculator;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;

/** Binds a live per-unit nutrition preview to the registration form. */
final class NutritionUnitPreview {
    private NutritionUnitPreview() {
    }

    static void bind(
            TextView preview,
            EditText basisAmount,
            EditText basisUnit,
            NutritionInputSection nutrients
    ) {
        Runnable refresh = () -> refresh(preview, basisAmount, basisUnit, nutrients);
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                refresh.run();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        };
        basisAmount.addTextChangedListener(watcher);
        basisUnit.addTextChangedListener(watcher);
        nutrients.addChangeListener(refresh);
        refresh.run();
    }

    private static void refresh(
            TextView preview,
            EditText basisAmount,
            EditText basisUnit,
            NutritionInputSection nutrients
    ) {
        String amountText = FitnessUi.inputText(basisAmount).trim();
        String unitText = FitnessUi.inputText(basisUnit).trim();
        if (amountText.isEmpty() || unitText.isEmpty()) {
            preview.setText("단위 영양성분: 기준량과 단위를 입력하면 자동 계산됩니다.");
            return;
        }
        try {
            double amount = Double.parseDouble(amountText);
            if (amount <= 0) {
                throw new IllegalArgumentException("기준량은 0보다 커야 합니다.");
            }
            String unit = NutritionUnit.requireSupported(unitText);
            NutritionProfile profile = nutrients.profile();
            preview.setText(NutritionCalculator.unitNutritionLabel(profile, amount, unit));
        } catch (Exception error) {
            preview.setText("단위 영양성분: 필수 영양성분과 지원 단위를 모두 입력하면 표시됩니다.");
        }
    }
}
