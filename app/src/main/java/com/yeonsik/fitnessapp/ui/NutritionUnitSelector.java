package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;

import com.yeonsik.fitnessapp.data.NutritionUnit;

import java.util.Locale;

/** List-backed selector that prevents unsupported nutrition basis-unit input. */
final class NutritionUnitSelector {
    private NutritionUnitSelector() {
    }

    static Button create(FitnessUi ui, Activity activity, String initialValue) {
        Button selector = ui.button("", false, null);
        setValue(selector, initialValue);
        selector.setOnClickListener(v -> showChoices(activity, selector));
        return selector;
    }

    static String value(Button selector) {
        Object tag = selector.getTag();
        return NutritionUnit.requireSupported(tag == null ? "" : String.valueOf(tag));
    }

    static void setValue(Button selector, String rawValue) {
        String normalized = NutritionUnit.requireSupported(rawValue);
        selector.setTag(normalized);
        selector.setText(String.format(
                Locale.KOREAN,
                "기준 단위: %s ▾",
                NutritionUnit.display(normalized)
        ));
        selector.setContentDescription("기준 단위 선택: " + NutritionUnit.display(normalized));
    }

    private static void showChoices(Activity activity, Button selector) {
        String[] options = NutritionUnit.options();
        int checkedIndex = 0;
        String selected = value(selector);
        for (int index = 0; index < options.length; index++) {
            if (options[index].equals(selected)) {
                checkedIndex = index;
                break;
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle("기준 단위 선택")
                .setSingleChoiceItems(options, checkedIndex, (dialog, which) -> {
                    setValue(selector, options[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("취소", null)
                .show();
    }
}
