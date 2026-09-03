package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Shared form composition layer for Android View screens.
 *
 * <p>{@link FitnessUi} owns the theme primitives. This class owns the form grammar: labels,
 * controls, helper/error copy, compact nutrient rows, selectors and the bottom action. Keeping
 * this layer small avoids introducing a second UI framework while making form states consistent.</p>
 */
public final class FormSystem {
    private final FitnessUi ui;
    private final Activity activity;
    /*
     * Android View uses object identity for equals/hashCode, so WeakHashMap preserves that
     * lookup while the snapshot below keeps only weak View references.
     */
    private final WeakHashMap<View, ViewStateSnapshot> disabledStates = new WeakHashMap<>();
    private final WeakHashMap<View, ViewStateSnapshot> loadingStates = new WeakHashMap<>();

    public FormSystem(FitnessUi ui, Activity activity) {
        if (ui == null || activity == null) {
            throw new IllegalArgumentException("FormSystem requires a UI factory and activity.");
        }
        this.ui = ui;
        this.activity = activity;
    }

    public LinearLayout column() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    /** Section title with the shared form rhythm. */
    public TextView sectionTitle(String title) {
        TextView view = ui.text(title, 14, FitnessUi.COLOR_TEXT, true);
        view.setPadding(0, ui.dp(12), 0, ui.dp(6));
        return view;
    }

    /** Label used above a non-row control. */
    public TextView fieldLabel(String label) {
        TextView view = ui.text(label, 12, FitnessUi.COLOR_MUTED, true);
        view.setPadding(0, 0, 0, ui.dp(FitnessUi.FIELD_LABEL_GAP_DP));
        return view;
    }

    /** Wraps one control with the shared field label and spacing. */
    public View field(String label, View control) {
        LinearLayout wrapper = column();
        wrapper.addView(fieldLabel(label));
        if (control != null) {
            wrapper.addView(control, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
        return wrapper;
    }

    public EditText textInput(String hint, String value) {
        return ui.input(hint, value == null ? "" : value);
    }

    public EditText numberInput(String hint, String value) {
        return ui.numberInput(hint, value == null ? "" : value);
    }

    public EditText decimalInput(String hint, String value) {
        return ui.decimalInput(hint, value == null ? "" : value);
    }

    /** Places a unit suffix beside a value input without making the unit editable. */
    public View unitSuffix(View control, String unit) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (control != null) {
            row.addView(control, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));
        }
        TextView suffix = ui.text(unit == null ? "" : unit, 13, FitnessUi.COLOR_MUTED, true);
        suffix.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        suffix.setPadding(ui.dp(8), 0, 0, 0);
        row.addView(suffix, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    /** Selector surface. Selection state is supplied by the shared choice sheet. */
    public Button selector(String value, View.OnClickListener listener) {
        Button button = ui.secondaryButton(value == null ? "선택" : value, listener);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setContentDescription((value == null ? "선택" : value) + " 선택");
        return button;
    }

    /** Read-only field with a visible label/value distinction. */
    public View readOnlyField(String label, String value) {
        LinearLayout wrapper = column();
        wrapper.addView(fieldLabel(label));
        TextView valueView = ui.text(value == null || value.isEmpty() ? "미기록" : value,
                14, FitnessUi.COLOR_TEXT, true);
        valueView.setPadding(0, ui.dp(4), 0, ui.dp(4));
        wrapper.addView(valueView);
        return wrapper;
    }

    public TextView helper(String message) {
        TextView view = ui.text(message == null ? "" : message,
                12, FitnessUi.COLOR_MUTED, false);
        view.setLineSpacing(ui.dp(2), 1f);
        return view;
    }

    public TextView error(String message) {
        TextView view = ui.text(message == null ? "" : message,
                12, FitnessUi.COLOR_NEGATIVE, true);
        view.setLineSpacing(ui.dp(2), 1f);
        showError(view, message);
        return view;
    }
    /** Shows or clears a previously created inline error without changing form structure. */
    public void showError(TextView view, String message) {
        if (view == null) {
            return;
        }
        String normalized = message == null ? "" : message.trim();
        view.setText(normalized);
        view.setContentDescription(normalized.isEmpty() ? "오류" : "오류: " + normalized);
        view.setVisibility(normalized.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public void clearError(TextView view) {
        showError(view, null);
    }

    /** Common single-line read-only total row. */
    public View inlineTotalRow(String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(48));
        TextView labelView = ui.text(label, 13, FitnessUi.COLOR_MUTED, false);
        TextView valueView = ui.num(value == null ? "—" : value,
                14, FitnessUi.COLOR_TEXT, true);
        valueView.setGravity(Gravity.END);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView);
        return row;
    }

    /** The same row grammar used for menu values and menu totals. */
    public View nutrientRow(String key, String value) {
        return nutrientRow(NutritionRow.displayLabel(key), value,
                NutritionRow.displayUnit(key));
    }

    public View nutrientRow(String label, String value, String unit) {
        return NutritionRow.readOnly(ui, activity, label, value, unit).view();
    }

    /** Editable version of the shared nutrient row. */
    public NutritionRow nutrientInputRow(String key, String value) {
        return nutrientInputRow(NutritionRow.displayLabel(key),
                NutritionRow.displayUnit(key), value);
    }

    public NutritionRow nutrientInputRow(String label, String unit, String value) {
        return NutritionRow.input(ui, activity, label, unit, value);
    }

    public NutritionRow nutrientInputRow(String label, String unit, EditText existingInput) {
        return NutritionRow.input(ui, activity, label, unit, existingInput);
    }

    public void addNutrientRows(
            LinearLayout parent,
            List<String> keys,
            java.util.function.Function<String, String> valueProvider
    ) {
        if (parent == null || keys == null) {
            return;
        }
        for (String key : keys) {
            parent.addView(nutrientRow(key, valueProvider.apply(key)),
                    ui.fullWidthParams(0));
        }
    }

    /** The fixed bottom CTA used by validated sheets and full-screen forms. */
    public Button bottomAction(String text, View.OnClickListener listener) {
        return ui.primaryButton(text, listener);
    }

    /** Applies a non-colour-only disabled state to a form control. */
    public void disabled(View view, boolean disabled) {
        if (view == null) {
            return;
        }
        if (!disabled) {
            if (loadingStates.containsKey(view)) {
                ViewStateSnapshot snapshot = disabledStates.remove(view);
                if (snapshot != null) {
                    // If disabled was cleared during loading, let loading restore the
                    // state that existed before the disabled presentation.
                    loadingStates.put(view, snapshot);
                }
                return;
            }
            ViewStateSnapshot snapshot = disabledStates.remove(view);
            if (snapshot != null) {
                snapshot.restore();
                return;
            }
            // Without a snapshot, preserve descendant state so repeated calls stay idempotent.
            view.setEnabled(true);
            view.setAlpha(1f);
            return;
        }
        ViewStateSnapshot snapshot = disabledStates.get(view);
        if (snapshot == null) {
            snapshot = loadingStates.containsKey(view)
                    ? loadingStates.get(view)
                    : captureState(view);
            disabledStates.put(view, snapshot);
        }
        if (!loadingStates.containsKey(view)) {
            applyDisabledState(view, snapshot);
        }
    }

    /** Applies the shared loading state and restores the original accessibility label. */
    public void loading(View view, boolean loading, String message) {
        if (view == null) {
            return;
        }
        if (loading) {
            if (!loadingStates.containsKey(view)) {
                loadingStates.put(view, captureState(view));
            }
            setEnabledRecursively(view, false);
            view.setAlpha(0.58f);
            view.setContentDescription(message == null || message.trim().isEmpty()
                    ? "불러오는 중" : message);
        } else {
            ViewStateSnapshot snapshot = loadingStates.remove(view);
            if (snapshot != null) {
                snapshot.restore();
                ViewStateSnapshot disabledSnapshot = disabledStates.get(view);
                if (disabledSnapshot != null) {
                    applyDisabledState(view, disabledSnapshot);
                }
            }
        }
    }

    private void applyDisabledState(View view, ViewStateSnapshot snapshot) {
        setEnabledRecursively(view, false);
        view.setAlpha(0.48f);
        view.setContentDescription(appendState(
                snapshot.contentDescription(view),
                "사용할 수 없음"
        ));
    }

    private ViewStateSnapshot captureState(View root) {
        ViewStateSnapshot snapshot = new ViewStateSnapshot();
        captureState(root, snapshot);
        return snapshot;
    }

    private void captureState(View view, ViewStateSnapshot snapshot) {
        snapshot.states.add(new ViewState(
                view,
                view.isEnabled(),
                view.getAlpha(),
                view.getContentDescription()
        ));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                captureState(group.getChildAt(index), snapshot);
            }
        }
    }

    private void setEnabledRecursively(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                setEnabledRecursively(group.getChildAt(index), enabled);
            }
        }
    }

    private CharSequence appendState(CharSequence original, String state) {
        if (state == null || state.isEmpty()) {
            return original;
        }
        if (original == null || original.length() == 0) {
            return state;
        }
        String value = original.toString();
        return value.contains(state) ? original : value + " · " + state;
    }

    private static final class ViewStateSnapshot {
        private final List<ViewState> states = new ArrayList<>();

        private CharSequence contentDescription(View view) {
            for (ViewState state : states) {
                if (state.view() == view) {
                    return state.contentDescription;
                }
            }
            return view.getContentDescription();
        }

        private void restore() {
            for (ViewState state : states) {
                View view = state.view();
                if (view == null) {
                    continue;
                }
                view.setEnabled(state.enabled);
                view.setAlpha(state.alpha);
                view.setContentDescription(state.contentDescription);
            }
        }
    }

    private static final class ViewState {
        private final WeakReference<View> viewReference;
        private final boolean enabled;
        private final float alpha;
        private final CharSequence contentDescription;

        private ViewState(
                View view,
                boolean enabled,
                float alpha,
                CharSequence contentDescription
        ) {
            this.viewReference = new WeakReference<>(view);
            this.enabled = enabled;
            this.alpha = alpha;
            this.contentDescription = contentDescription;
        }

        private View view() {
            return viewReference.get();
        }
    }
}
