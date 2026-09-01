package com.yeonsik.fitnessapp.ui;

import android.app.Dialog;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog;
import com.yeonsik.fitnessapp.exercise.ExercisePrimaryMuscleLabel;
import com.yeonsik.fitnessapp.exercise.BodyPart;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseFamily;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Family 안의 운동 variant를 선택하는 공통 팝업.
 *
 * <p>루틴/세션 종목 추가는 여러 variant를, 진행 중 운동 교체는 하나의 variant를
 * 선택할 수 있지만 카드와 이미지 표현은 동일하게 유지한다.</p>
 */
public final class ExerciseVariantPickerDialog {
    private static final int VARIANT_IMAGE_SIZE_DP = 56;
    private static final int POPUP_MAX_HEIGHT_DP = 420;

    private final android.app.Activity activity;
    private final FitnessUi ui;
    private final ExerciseIllustrationPreview illustrationPreview;

    public ExerciseVariantPickerDialog(
            android.app.Activity activity,
            FitnessUi ui,
            ExerciseIllustrationPreview illustrationPreview
    ) {
        this.activity = activity;
        this.ui = ui;
        this.illustrationPreview = illustrationPreview;
    }

    public void show(
            RuntimeExerciseFamily family,
            List<RuntimeExercisePreset> presets,
            List<RuntimeExercisePreset> initiallySelected,
            boolean singleChoice,
            OnConfirmed listener
    ) {
        if (family == null || presets == null || presets.isEmpty() || listener == null) {
            return;
        }

        List<RuntimeExercisePreset> available = new ArrayList<>(presets);
        boolean[] checked = new boolean[available.size()];
        for (int index = 0; index < available.size(); index++) {
            checked[index] = contains(initiallySelected, available.get(index));
        }

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));

        List<VariantRow> renderedRows = new ArrayList<>();
        for (int index = 0; index < available.size(); index++) {
            final int rowIndex = index;
            VariantRow row = new VariantRow(available.get(index), checked[index]);
            renderedRows.add(row);
            row.view.setOnClickListener(v -> {
                if (singleChoice) {
                    for (int item = 0; item < checked.length; item++) {
                        checked[item] = item == rowIndex;
                        renderedRows.get(item).applySelection(checked[item]);
                    }
                } else {
                    checked[rowIndex] = !checked[rowIndex];
                    row.applySelection(checked[rowIndex]);
                }
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, index == 0 ? 0 : ui.dp(8), 0, 0);
            rows.addView(row.view, rowParams);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(rows, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(10), 0, ui.dp(10), ui.dp(6));
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int maxHeight = Math.max(ui.dp(180), screenHeight - ui.dp(260));
        int popupHeight = Math.min(ui.dp(POPUP_MAX_HEIGHT_DP), maxHeight);
        body.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                popupHeight
        ));

        Dialog dialog = ui.sheetWithSecondary(
                family.displayName() + " · 운동 종류 선택",
                body,
                singleChoice ? "선택" : "적용",
                () -> {
                    List<RuntimeExercisePreset> selected = new ArrayList<>();
                    for (int index = 0; index < available.size(); index++) {
                        if (checked[index]) {
                            selected.add(available.get(index));
                        }
                    }
                    listener.onConfirmed(Collections.unmodifiableList(selected));
                },
                "취소",
                () -> { }
        );
    }

    private final class VariantRow {
        final LinearLayout view;
        final TextView name;
        final TextView muscle;
        final TextView meta;
        final TextView check;
        final RuntimeExercisePreset preset;

        VariantRow(RuntimeExercisePreset preset, boolean selected) {
            this.preset = preset;
            view = new LinearLayout(activity);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setMinimumHeight(ui.dp(76));
            view.setPadding(ui.dp(14), ui.dp(10), ui.dp(10), ui.dp(10));
            view.setClickable(true);
            view.setFocusable(true);

            LinearLayout textColumn = new LinearLayout(activity);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            LinearLayout nameRow = new LinearLayout(activity);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            name = ui.text(preset.displayName(), 15, FitnessUi.COLOR_TEXT, true);
            nameRow.addView(name, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));
            String muscleLabel = ExercisePrimaryMuscleLabel.forPreset(preset);
            muscle = ui.text(muscleLabel, 12, FitnessUi.COLOR_MUTED, false);
            if (muscleLabel.isEmpty()) {
                muscle.setVisibility(android.view.View.GONE);
            }
            muscle.setPadding(ui.dp(8), 0, 0, 0);
            nameRow.addView(muscle);
            meta = ui.text(variantBodyPart(preset), 12, FitnessUi.COLOR_MUTED, false);
            meta.setPadding(0, ui.dp(4), 0, 0);
            textColumn.addView(nameRow);
            textColumn.addView(meta);
            view.addView(textColumn, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            ImageView image = illustrationPreview.createExact(
                    ExerciseFamilyCatalog.empty().identityForPreset(preset));
            if (image != null) {
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                        ui.dp(VARIANT_IMAGE_SIZE_DP),
                        ui.dp(VARIANT_IMAGE_SIZE_DP)
                );
                imageParams.setMargins(ui.dp(8), 0, 0, 0);
                view.addView(image, imageParams);
            }

            check = ui.text("", 16, ui.inkMuted(), true);
            check.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                    ui.dp(28), ui.dp(28)
            );
            checkParams.setMargins(ui.dp(8), 0, 0, 0);
            view.addView(check, checkParams);
            view.setContentDescription(accessibilityText(false));
            ui.pressFeedback(view);
            applySelection(selected);
        }

        void applySelection(boolean selected) {
            ui.styleSelection(view, selected, ui.dp(16));
            name.setTextColor(selected ? FitnessUi.COLOR_INVERSE_TEXT : FitnessUi.COLOR_TEXT);
            muscle.setTextColor(selected ? FitnessUi.COLOR_INVERSE_MUTED : FitnessUi.COLOR_MUTED);
            meta.setTextColor(selected ? FitnessUi.COLOR_INVERSE_MUTED : FitnessUi.COLOR_MUTED);
            check.setText(selected ? "✓" : "");
            check.setTextColor(selected ? FitnessUi.COLOR_INVERSE_TEXT : ui.inkMuted());
            check.setBackground(selected
                    ? ui.borderDrawable(ui.chipOnAccent(), ui.chipOnAccent(), ui.dp(999))
                    : ui.borderDrawable(ui.surface(), ui.border(), ui.dp(999)));
            view.setContentDescription(accessibilityText(selected));
        }

        private String accessibilityText(boolean selected) {
            String muscleLabel = ExercisePrimaryMuscleLabel.forPreset(preset);
            return preset.displayName()
                    + (muscleLabel.isEmpty() ? "" : ", 대표 부위 " + muscleLabel)
                    + ", " + variantBodyPart(preset)
                    + (selected ? ", 선택됨" : ", 선택 안 됨");
        }
    }

    private String variantBodyPart(RuntimeExercisePreset preset) {
        if (preset.primarySubPartNameKo != null
                && !preset.primarySubPartNameKo.trim().isEmpty()) {
            return preset.primarySubPartNameKo;
        }
        if (preset.primarySubPart != null && !preset.primarySubPart.trim().isEmpty()) {
            return preset.primarySubPart;
        }
        BodyPart bodyPart = BodyPart.fromId(preset.defaultUiPart);
        if (bodyPart != null) {
            return bodyPart.labelKo();
        }
        return "운동 부위 없음";
    }

    private static boolean contains(
            List<RuntimeExercisePreset> presets,
            RuntimeExercisePreset candidate
    ) {
        if (presets == null || candidate == null) {
            return false;
        }
        for (RuntimeExercisePreset preset : presets) {
            if (preset != null && preset.identityId().equals(candidate.identityId())) {
                return true;
            }
        }
        return false;
    }

    public interface OnConfirmed {
        void onConfirmed(List<RuntimeExercisePreset> selectedPresets);
    }
}
