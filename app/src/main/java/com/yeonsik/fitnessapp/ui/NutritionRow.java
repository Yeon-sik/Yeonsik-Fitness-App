package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionProfile;

/**
 * A compact nutrition value row used by both forms and read-only summaries.
 *
 * <p>The row deliberately has no card background. The label, value and unit stay on one
 * scan line so a menu total and an editable menu use the same visual grammar.</p>
 */
public final class NutritionRow {
    private final LinearLayout root;
    private final EditText input;
    private final TextView valueView;

    private NutritionRow(
            FitnessUi ui,
            Activity activity,
            String label,
            String unit,
            String value,
            boolean editable
    ) {
        this(ui, activity, label, unit, value, editable, null);
    }

    private NutritionRow(
            FitnessUi ui,
            Activity activity,
            String label,
            String unit,
            String value,
            boolean editable,
            EditText existingInput
    ) {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setMinimumHeight(ui.dp(FitnessUi.NUTRITION_ROW_MIN_HEIGHT_DP));
        root.setPadding(0, ui.dp(FitnessUi.NUTRITION_ROW_VERTICAL_PADDING_DP), 0,
                ui.dp(FitnessUi.NUTRITION_ROW_VERTICAL_PADDING_DP));
        root.setContentDescription(label + (editable ? " 입력" : " 값"));

        TextView labelView = ui.text(label, 14, FitnessUi.COLOR_TEXT, false);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        labelView.setMaxLines(FitnessUi.NUTRITION_LABEL_MAX_LINES);
        labelView.setHorizontallyScrolling(false);
        labelView.setLineSpacing(ui.dp(2), 1f);
        root.addView(labelView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        if (editable) {
            input = existingInput == null
                    ? ui.decimalInput("입력", value == null ? "" : value)
                    : existingInput;
            input.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            input.setSelectAllOnFocus(true);
            input.setContentDescription(label + " 입력값");
            root.addView(input, new LinearLayout.LayoutParams(
                    ui.dp(FitnessUi.NUTRITION_VALUE_WIDTH_DP),
                    ui.dp(FitnessUi.NUTRITION_INPUT_HEIGHT_DP)
            ));
            valueView = null;
        } else {
            input = null;
            valueView = ui.num(value == null || value.isEmpty() ? "—" : value,
                    14, FitnessUi.COLOR_TEXT, true);
            valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            valueView.setContentDescription(label + " "
                    + (value == null || value.isEmpty() ? "미기록" : value));
            root.addView(valueView, new LinearLayout.LayoutParams(
                    ui.dp(FitnessUi.NUTRITION_VALUE_WIDTH_DP),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        TextView unitView = ui.text(
                unit == null ? "" : unit,
                13,
                FitnessUi.COLOR_MUTED,
                true
        );
        unitView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        root.addView(unitView, new LinearLayout.LayoutParams(
                ui.dp(FitnessUi.NUTRITION_UNIT_WIDTH_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    /** Creates an editable row for a nutrient key. */
    public static NutritionRow input(
            FitnessUi ui,
            Activity activity,
            String label,
            String unit,
            String value
    ) {
        return new NutritionRow(ui, activity, label, unit, value, true);
    }

    /** Wraps an existing input so draft-backed forms can keep their field references. */
    public static NutritionRow input(
            FitnessUi ui,
            Activity activity,
            String label,
            String unit,
            EditText existingInput
    ) {
        return new NutritionRow(ui, activity, label, unit, "", true, existingInput);
    }

    /** Creates a read-only row for a nutrient key or a calculated total. */
    public static NutritionRow readOnly(
            FitnessUi ui,
            Activity activity,
            String label,
            String value,
            String unit
    ) {
        return new NutritionRow(ui, activity, label, unit, value, false);
    }

    public View view() {
        return root;
    }

    public EditText inputField() {
        return input;
    }

    public void setValue(String value) {
        if (valueView == null) {
            return;
        }
        String display = value == null || value.isEmpty() ? "—" : value;
        valueView.setText(display);
        valueView.setContentDescription(root.getContentDescription() + " " + display);
    }

    /** Returns the component label used by the product UI, not the storage key. */
    public static String displayLabel(String key) {
        return NutritionProfile.CALORIES_KCAL.equals(key)
                ? "칼로리"
                : NutritionProfile.labelOf(key);
    }

    /** Returns the display unit while keeping the stored unit contract unchanged. */
    public static String displayUnit(String key) {
        String unit = NutritionProfile.unitOf(key);
        return NutrientCode.displayUnit(unit == null ? "" : unit);
    }
}
