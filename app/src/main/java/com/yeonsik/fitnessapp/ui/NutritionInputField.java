package com.yeonsik.fitnessapp.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.NutritionCalculator;

/**
 * One editable nutrition value with the shared row, parsing and accessibility contract.
 *
 * <p>Meal-domain forms decide whether a field is required and how the parsed value is used;
 * this primitive owns the repeated input mechanics so product and dining-out forms cannot
 * drift apart.</p>
 */
final class NutritionInputField {
    private final FitnessUi ui;
    private final FormSystem forms;
    private final String label;
    private final boolean required;
    private final NutritionRow row;
    private final EditText input;
    private final TextView errorView;
    private final LinearLayout container;

    NutritionInputField(
            FitnessUi ui,
            FormSystem forms,
            String key,
            String label,
            String value,
            boolean required,
            String contentDescription,
            boolean wholeNumber
    ) {
        if (ui == null || forms == null) {
            throw new IllegalArgumentException("NutritionInputField requires shared form UI.");
        }
        this.ui = ui;
        this.forms = forms;
        this.label = label == null || label.trim().isEmpty()
                ? NutritionRow.displayLabel(key)
                : label.trim();
        this.required = required;
        EditText createdInput = wholeNumber
                ? forms.numberInput("입력", value)
                : forms.decimalInput("입력", value);
        this.row = forms.nutrientInputRow(
                this.label + (required ? " *" : ""),
                NutritionRow.displayUnit(key),
                createdInput
        );
        this.input = row.inputField();
        this.input.setContentDescription(contentDescription == null
                ? this.label + (required ? " 필수" : " 선택")
                : contentDescription);
        this.errorView = forms.error("");
        this.errorView.setPadding(0, 0, 0, ui.dp(2));
        this.container = forms.column();
        this.container.addView(row.view(), ui.fullWidthParams(0));
        this.container.addView(errorView, ui.fullWidthParams(0));
        this.input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                clearError();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    View view() {
        return container;
    }

    EditText input() {
        return input;
    }

    String rawValue() {
        return FitnessUi.inputText(input);
    }

    void setRawValue(String value) {
        input.setText(value == null ? "" : value);
    }

    void setNumericValue(Double value) {
        setRawValue(value == null ? "" : NutritionCalculator.trim(value));
    }

    void addChangeListener(Runnable listener) {
        if (listener == null) {
            return;
        }
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

    /** Parses the field while preserving the form's required/optional policy. */
    Double parse() {
        String raw = rawValue().trim();
        if (raw.isEmpty()) {
            if (required) {
                throw invalid(label + "은(는) 필수 입력입니다.");
            }
            return null;
        }
        final double parsed;
        try {
            parsed = Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            throw invalid(label + " 값이 숫자가 아닙니다.");
        }
        if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < 0d) {
            throw invalid(label + "은(는) 음수가 될 수 없습니다.");
        }
        return parsed;
    }

    void showError(String message) {
        forms.showError(errorView, message);
    }

    void clearError() {
        forms.clearError(errorView);
    }

    private IllegalArgumentException invalid(String message) {
        showError(message);
        input.requestFocus();
        return new IllegalArgumentException(message);
    }
}
