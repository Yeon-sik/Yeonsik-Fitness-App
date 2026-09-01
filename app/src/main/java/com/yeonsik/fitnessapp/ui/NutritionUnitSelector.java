package com.yeonsik.fitnessapp.ui;

import android.widget.Button;

import com.yeonsik.fitnessapp.data.NutritionUnit;

import java.util.Arrays;
import java.util.Locale;

/** List-backed selector that prevents unsupported nutrition basis-unit input. */
final class NutritionUnitSelector {
    private NutritionUnitSelector() {
    }

    static Button create(FitnessUi ui, android.app.Activity activity, String initialValue) {
        Button selector = ui.button("", false, null);
        setValue(selector, initialValue);
        selector.setOnClickListener(v -> showChoices(ui, selector));
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

    private static void showChoices(FitnessUi ui, Button selector) {
        String[] options = NutritionUnit.options();
        int checkedIndex = 0;
        String selected = value(selector);
        for (int index = 0; index < options.length; index++) {
            if (options[index].equals(selected)) {
                checkedIndex = index;
                break;
            }
        }
        ui.choiceSheet("기준 단위 선택", Arrays.asList(options), checkedIndex, which -> {
                    setValue(selector, options[which]);
                });
    }
}
