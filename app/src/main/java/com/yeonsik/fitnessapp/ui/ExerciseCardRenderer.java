package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;
import com.yeonsik.fitnessapp.exercise.WeightExercise;

/**
 * 운동 카드의 공통 헤더를 생성한다.
 * 루틴 추가와 운동 구성 화면은 이 렌더러를 통해 이름, 메타 정보, 대표 이미지 위치를 공유한다.
 */
public final class ExerciseCardRenderer {

    public static final class Content {
        public final String exerciseId;
        public final String name;
        public final String primarySubPart;
        public final String equipment;
        public final String recordType;
        public final ExerciseFamilyIdentity familyIdentity;

        public Content(
                String exerciseId,
                String name,
                    String primarySubPart,
                    String equipment,
                    String recordType
            ) {
            this(exerciseId, name, primarySubPart, equipment, recordType, null);
        }

        public Content(
                String exerciseId,
                String name,
                String primarySubPart,
                String equipment,
                String recordType,
                ExerciseFamilyIdentity familyIdentity
            ) {
            this.exerciseId = valueOrDefault(exerciseId, "");
            this.name = valueOrDefault(name, "운동");
            this.primarySubPart = valueOrDefault(primarySubPart, "세부 부위 없음");
            this.equipment = valueOrDefault(equipment, "기타");
            this.recordType = valueOrDefault(recordType, "기록 방식 없음");
            this.familyIdentity = familyIdentity;
        }

        public static Content fromWeightExercise(WeightExercise exercise) {
            return new Content(
                    exercise.id,
                    exercise.displayName(),
                    exercise.primarySubPartNameKo,
                    exercise.equipmentNameKo,
                    displayRecordType(exercise)
            );
        }

        public static Content fromRuntimePreset(RuntimeExercisePreset preset) {
            if (preset == null) {
                return new Content("", "운동", "세부 부위 없음", "기타", "기록 방식 없음");
            }
            String equipment = isBlank(preset.equipmentNameKo)
                    ? preset.uiEquipmentCategory.labelKo()
                    : preset.equipmentNameKo;
            return new Content(
                    preset.storageExerciseId,
                    preset.displayName(),
                    preset.primarySubPartNameKo,
                    equipment,
                    displayRecordType(preset.recordType),
                    ExerciseFamilyIdentityForPreset.identity(preset)
            );
        }

        public static Content fromSessionExercise(
                FitnessRepository.SessionExerciseEntry exercise,
                WeightExercise master
        ) {
            String primarySubPart = master == null
                    ? exercise.uiPart
                    : master.primarySubPartNameKo;
            String equipment = isBlank(exercise.equipment)
                    ? (master == null ? "" : master.equipmentNameKo)
                    : exercise.equipment;
            String recordType = master == null
                    ? displayRecordType(exercise.recordType)
                    : displayRecordType(master);
            return new Content(
                    exercise.exerciseId,
                    exercise.name,
                    primarySubPart,
                    equipment,
                    recordType
            );
        }

        private String metaText() {
            return primarySubPart + " · " + equipment + " · " + recordType;
        }

        private String accessibilityText(boolean selectable, boolean selected) {
            if (!selectable) {
                return name + ", " + metaText();
            }
            return name + ", " + metaText() + (selected ? ", 선택됨" : ", 선택 안 됨");
        }
    }

    public static final class Binding {
        private final LinearLayout row;
        private final TextView name;
        private final TextView meta;
        private final TextView check;
        private final Content content;
        private final FitnessUi ui;
        private final boolean selectable;

        private Binding(
                LinearLayout row,
                TextView name,
                TextView meta,
                TextView check,
                Content content,
                FitnessUi ui,
                boolean selectable
        ) {
            this.row = row;
            this.name = name;
            this.meta = meta;
            this.check = check;
            this.content = content;
            this.ui = ui;
            this.selectable = selectable;
        }

        public void applySelection(boolean selected) {
            if (selectable) {
                String cardSeed = "exercise-" + content.name;
                row.setBackground(selected
                        ? ui.vibrantRippleDrawable(cardSeed, ui.dp(16))
                        : ui.flatSurfaceRippleDrawable(ui.dp(16)));
                ui.applyDepth(row, selected ? 7 : 4);
            }
            row.setSelected(selected);
            row.setContentDescription(content.accessibilityText(selectable, selected));
            name.setTextColor(selected ? FitnessUi.COLOR_INVERSE_TEXT : FitnessUi.COLOR_TEXT);
            meta.setTextColor(selected ? FitnessUi.COLOR_INVERSE_MUTED : FitnessUi.COLOR_MUTED);
            if (check != null) {
                check.setText(selected ? "✓" : "");
                check.setTextColor(selected ? FitnessUi.COLOR_INVERSE_TEXT : ui.inkMuted());
                check.setBackground(selected
                        ? ui.borderDrawable(ui.chipOnAccent(), ui.chipOnAccent(), ui.dp(999))
                        : ui.borderDrawable(ui.surface(), ui.border(), ui.dp(999)));
            }
        }
    }

    private final Activity activity;
    private final FitnessUi ui;
    private final ExerciseIllustrationPreview illustrationPreview;

    public ExerciseCardRenderer(
            Activity activity,
            FitnessUi ui,
            ExerciseIllustrationPreview illustrationPreview
    ) {
        this.activity = activity;
        this.ui = ui;
        this.illustrationPreview = illustrationPreview;
    }

    public Binding addContent(
            LinearLayout row,
            Content content,
            boolean selectable,
            boolean selected
    ) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView name = ui.text(content.name, 15, FitnessUi.COLOR_TEXT, true);
        TextView meta = ui.text(content.metaText(), 12, FitnessUi.COLOR_MUTED, false);
        meta.setPadding(0, ui.dp(4), 0, 0);
        column.addView(name);
        column.addView(meta);
        row.addView(column, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        ImageView preview = createPreview(content);
        row.addView(preview, previewParams());

        TextView check = null;
        if (selectable) {
            check = ui.text("", 16, ui.inkMuted(), true);
            check.setGravity(Gravity.CENTER);
            row.addView(check, checkParams());
        }

        Binding binding = new Binding(row, name, meta, check, content, ui, selectable);
        binding.applySelection(selected);
        return binding;
    }

    /**
     * 요약 화면처럼 이미지와 수행 내역만 보여주는 운동 박스를 생성한다.
     * 이름과 순번은 표시하지 않고, 대표 이미지의 접근성 설명만 유지한다.
     */
    public void addPreviewOnly(LinearLayout row, Content content) {
        ImageView preview = createPreview(content);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        preview.setContentDescription(content.name);
        LinearLayout.LayoutParams params = previewParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        row.addView(preview, params);
    }

    private ImageView createPreview(Content content) {
        ImageView preview = content.familyIdentity == null
                ? illustrationPreview.create(content.exerciseId)
                : illustrationPreview.create(content.familyIdentity);
        return preview == null ? emptyPreview() : preview;
    }

    private ImageView emptyPreview() {
        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(false);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.setFocusable(false);
        return preview;
    }

    private LinearLayout.LayoutParams previewParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ui.dp(ExerciseIllustrationPreview.SIZE_DP),
                ui.dp(ExerciseIllustrationPreview.SIZE_DP)
        );
        params.setMargins(ui.dp(8), 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams checkParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28));
        params.setMargins(ui.dp(8), 0, 0, 0);
        return params;
    }

    private static String displayRecordType(WeightExercise exercise) {
        if (!isBlank(exercise.recordTypeNameKo)) {
            return exercise.recordTypeNameKo;
        }
        return displayRecordType(exercise.recordType);
    }

    private static String displayRecordType(String recordType) {
        if (isBlank(recordType)) {
            return "기록 방식 없음";
        }
        switch (recordType) {
            case "weight_reps":
                return "무게 + 횟수";
            case "reps_only":
                return "횟수";
            case "time":
                return "시간";
            case "weight_time":
                return "무게 + 시간";
            case "assisted_weight_reps":
                return "보조 중량 + 횟수";
            case "bodyweight_added_weight_reps":
                return "체중 + 추가 중량 + 횟수";
            default:
                return recordType;
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ExerciseFamilyIdentityForPreset {
        private static ExerciseFamilyIdentity identity(RuntimeExercisePreset preset) {
            return new ExerciseFamilyIdentity(
                    preset.storageExerciseId,
                    preset.familyId,
                    preset.presetId,
                    preset.canonicalPresetId,
                    preset.nameKo,
                    preset.nameEn,
                    preset.legacyNameKo,
                    preset.legacyNameEn,
                    preset.defaultUiPart,
                    preset.canonicalVariantKey,
                    preset.visualVariantKey,
                    preset.illustrationKey,
                    preset.defaultLoadState == null ? null : preset.defaultLoadState.id(),
                    preset.recordType,
                    null
            );
        }
    }
}
