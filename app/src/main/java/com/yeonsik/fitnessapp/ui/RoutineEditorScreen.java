package com.yeonsik.fitnessapp.ui;

import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.exercise.BodyPart;
import com.yeonsik.fitnessapp.exercise.EquipmentType;
import com.yeonsik.fitnessapp.exercise.ExerciseCategory;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter;
import com.yeonsik.fitnessapp.exercise.WeightExercise;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseFamily;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePicker;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * 루틴 상세(ROUTINE_DETAIL)와 운동 픽커(ROUTINE_ADD: 루틴에 추가,
 * WORKOUT_EXERCISE_ADD: 진행 중 세션에 추가)를 담당한다.
 * 두 픽커는 대상만 다르고 검색/필터/선택 UI가 같아 하나의 렌더러를 공유한다.
 */
public final class RoutineEditorScreen extends BaseScreen {
    private final ExerciseIllustrationPreview exerciseIllustrationPreview;
    private final ExerciseCardRenderer exerciseCardRenderer;

    public RoutineEditorScreen(ScreenHost host) {
        super(host);
        exerciseIllustrationPreview = new ExerciseIllustrationPreview(host.activity(), host.ui());
        exerciseCardRenderer = new ExerciseCardRenderer(
                host.activity(),
                host.ui(),
                exerciseIllustrationPreview
        );
    }

    @Override
    public void render() {
        FitnessScreen screen = host.currentScreen();
        if (screen == FitnessScreen.ROUTINE_DETAIL) {
            renderDetail();
        } else if (screen == FitnessScreen.ROUTINE_ADD) {
            renderRuntimePicker(true);
        } else {
            renderRuntimePicker(false);
        }
    }

    // ── 루틴 상세 ─────────────────────────────────────────────────────

    private void renderDetail() {
        FitnessUi ui = ui();
        List<RoutineExerciseInstance> routineExercises = host.routineRepository().defaultRoutineExercises();
        String routineName = host.routineRepository().defaultRoutineName();

        add(ui.textAction("‹ 무산소로", FitnessUi.COLOR_MUTED,
                () -> host.navigate(FitnessScreen.STRENGTH)), ui.fullWidthParams(0));

        TextView eyebrowView = ui.caption("루틴", FitnessUi.COLOR_MUTED);
        eyebrowView.setPadding(0, ui.dp(16), 0, 0);
        add(eyebrowView);
        add(ui.titleView(routineName));

        LinearLayout summary = ui.card();
        LinearLayout summaryRow = new LinearLayout(host.activity());
        summaryRow.setOrientation(LinearLayout.HORIZONTAL);
        summaryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout summaryColumn = new LinearLayout(host.activity());
        summaryColumn.setOrientation(LinearLayout.VERTICAL);
        summaryColumn.addView(ui.caption("구성", FitnessUi.COLOR_MUTED));
        TextView countView = ui.num(routineExercises.size() + "개 종목", 20, FitnessUi.COLOR_TEXT, true);
        countView.setPadding(0, ui.dp(4), 0, 0);
        summaryColumn.addView(countView);
        summaryRow.addView(summaryColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        summary.addView(summaryRow);
        add(summary);

        add(ui.button("이 루틴으로 운동 시작", true, v -> host.startRoutineWorkout(routineExercises)),
                ui.fullWidthParams(ui.dp(4)));

        section("세부 운동 종목", "종목 추가", () -> host.navigate(FitnessScreen.ROUTINE_ADD));
        if (routineExercises.isEmpty()) {
            emptyState("루틴에 추가된 운동 종목이 없습니다.", "종목 추가로 시작하세요.");
            return;
        }

        List<View> rows = new ArrayList<>();
        for (RoutineExerciseInstance exercise : routineExercises) {
            rows.add(routineExerciseRow(exercise));
        }
        add(ui.rowsCard(rows));
    }

    private View routineExerciseRow(RoutineExerciseInstance exercise) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(ui.dp(56));
        row.setPadding(0, ui.dp(10), 0, ui.dp(10));

        row.addView(ui.orderBadge(exercise.order, false));
        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(ui.dp(12), 0, 0, 0);
        column.addView(ui.text(exercise.nameKo, 15, FitnessUi.COLOR_TEXT, true));
        TextView meta = ui.text(exercise.uiPart + " · " + exercise.primarySubPart + " · " + exercise.equipment,
                12, FitnessUi.COLOR_MUTED, false);
        meta.setPadding(0, ui.dp(2), 0, 0);
        column.addView(meta);
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView recordType = ui.text(exercise.recordType, 12, FitnessUi.COLOR_TERTIARY, false);
        recordType.setPadding(ui.dp(8), 0, 0, 0);
        row.addView(recordType);
        ImageView preview = exerciseIllustrationPreview.create(exercise.exerciseId);
        if (preview != null) {
            row.addView(preview, exercisePreviewParams(ui));
        }
        return row;
    }

    // ── 운동 픽커 (루틴 추가 / 세션 종목 추가 공용) ─────────────────────

    private void renderRuntimePicker(boolean routineMode) {
        FitnessUi ui = ui();
        String recordId = routineMode
                ? null
                : (host.sessionState().activeRecordId() != null
                        ? host.sessionState().activeRecordId()
                        : host.currentWorkoutRecordId());
        RuntimeExerciseCatalog catalog = host.exerciseMasterRepository().runtimeCatalog();
        RuntimeExercisePicker picker = new RuntimeExercisePicker(catalog);
        List<RuntimeExercisePreset> selectedPresets = new ArrayList<>();
        EditText routineNameInput = routineMode ? ui.input("루틴 이름", "") : null;

        if (routineMode) {
            add(ui.textAction("‹ 무산소로", FitnessUi.COLOR_MUTED,
                    () -> host.navigate(FitnessScreen.STRENGTH)), ui.fullWidthParams(0));
        } else {
            add(ui.textAction("‹ 운동으로", FitnessUi.COLOR_MUTED, () -> {
                if (host.sessionState().activeRecordId() != null) {
                    host.navigate(FitnessScreen.WORKOUT_SESSION);
                } else {
                    host.navigate(FitnessScreen.STRENGTH);
                }
            }), ui.fullWidthParams(0));
        }

        TextView eyebrowView = ui.caption(routineMode ? "루틴 구성" : "진행 중 운동", FitnessUi.COLOR_MUTED);
        eyebrowView.setPadding(0, ui.dp(16), 0, 0);
        add(eyebrowView);
        add(ui.titleView(routineMode ? "루틴 추가" : "운동 종목 추가"));
        if (routineMode) {
            add(ui.fieldLabel("루틴 이름"));
            add(routineNameInput, ui.fullWidthParams(0));
        }

        EditText searchInput = ui.searchField("운동명, 영문명, Family 검색");
        add(searchInput, ui.fullWidthParams(routineMode ? ui.dp(12) : 0));
        TextView selectedCount = ui.text("선택한 운동 0개", 13, FitnessUi.COLOR_MUTED, true);
        selectedCount.setPadding(0, ui.dp(14), 0, 0);
        add(selectedCount);

        String addLabel = routineMode ? "선택한 운동 추가" : "선택한 종목 추가";
        Button addButton = ui.button(addLabel, true, v -> {
            if (routineMode) {
                String routineName = FitnessUi.inputText(routineNameInput).trim();
                if (routineName.isEmpty()) {
                    host.toast("루틴 이름을 입력하세요.");
                    return;
                }
                if (selectedPresets.isEmpty()) {
                    host.toast("추가할 운동을 선택하세요.");
                    return;
                }
                if (!host.routineRepository().canCreateRoutine()) {
                    host.toast("루틴은 최대 5개까지 저장할 수 있습니다.");
                    return;
                }
                List<com.yeonsik.fitnessapp.exercise.RoutineExercise> routineExercises = new ArrayList<>();
                for (RuntimeExercisePreset preset : selectedPresets) {
                    routineExercises.add(ExerciseMasterAdapter.toRoutineExercise(preset));
                }
                String createdRoutineId = host.routineRepository().createRoutine(routineName, routineExercises);
                if (createdRoutineId == null) {
                    host.toast("루틴은 최대 5개까지 저장할 수 있습니다.");
                    return;
                }
                host.routineRepository().selectRoutine(createdRoutineId);
                host.toast("루틴을 저장했습니다. (" + host.routineRepository().routines().size() + "/5)");
                host.navigate(FitnessScreen.ROUTINE_DETAIL);
                return;
            }
            if (recordId == null) {
                host.toast("먼저 운동을 시작하세요.");
                return;
            }
            if (selectedPresets.isEmpty()) {
                host.toast("추가할 운동을 선택하세요.");
                return;
            }
            for (RuntimeExercisePreset preset : selectedPresets) {
                repository().addExerciseFromMaster(recordId, ExerciseMasterAdapter.toRoutineExercise(preset));
            }
            host.toast(selectedPresets.size() + "개 종목을 운동에 추가했습니다.");
            host.navigate(host.sessionState().activeRecordId() != null
                    ? FitnessScreen.WORKOUT_SESSION
                    : FitnessScreen.STRENGTH);
        });
        addButton.setEnabled(false);
        add(addButton, ui.fullWidthParams(ui.dp(10)));

        Runnable updateSelectionSummary = () -> setRuntimeSelectionSummary(
                selectedCount, addButton, addLabel, routineMode, selectedPresets);
        LinearLayout listArea = new LinearLayout(host.activity());
        listArea.setOrientation(LinearLayout.VERTICAL);
        Runnable refresh = () -> {
            updateSelectionSummary.run();
            renderRuntimePickerList(
                    listArea,
                    picker.search(FitnessUi.inputText(searchInput)),
                    selectedPresets,
                    updateSelectionSummary
            );
        };
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        section("운동 Family");
        add(listArea, ui.fullWidthParams(0));
        refresh.run();
    }

    private void renderRuntimePickerList(
            LinearLayout listArea,
            List<RuntimeExercisePicker.FamilyResult> results,
            List<RuntimeExercisePreset> selectedPresets,
            Runnable onSelectionChanged
    ) {
        FitnessUi ui = ui();
        listArea.removeAllViews();
        if (results.isEmpty()) {
            LinearLayout empty = ui.card();
            empty.addView(ui.text("조건에 맞는 운동 Family가 없습니다.", 14, FitnessUi.COLOR_MUTED, false));
            listArea.addView(empty);
            return;
        }
        for (RuntimeExercisePicker.FamilyResult result : results) {
            RuntimeExerciseFamily family = result.family;
            boolean selected = hasSelectedFamily(family.familyId, selectedPresets);
            LinearLayout card = ui.card();
            card.setClickable(true);
            card.setFocusable(true);
            card.setBackground(selected
                    ? ui.vibrantRippleDrawable("family-" + family.familyId, ui.dp(16))
                    : ui.flatSurfaceRippleDrawable(ui.dp(16)));
            ui.applyDepth(card, selected ? 7 : 4);

            TextView name = ui.text(family.displayName(), 15,
                    selected ? FitnessUi.COLOR_INVERSE_TEXT : FitnessUi.COLOR_TEXT, true);
            card.addView(name);
            TextView meta = ui.text(
                    family.presets.size() == 1
                            ? family.presets.get(0).displayName()
                            : family.presets.size() + "개 variant · " + presetNames(result.presets),
                    12,
                    selected ? FitnessUi.COLOR_INVERSE_MUTED : FitnessUi.COLOR_MUTED,
                    false
            );
            meta.setPadding(0, ui.dp(4), 0, 0);
            card.addView(meta);
            if (!result.presets.isEmpty()) {
                RuntimeExercisePreset previewPreset = result.presets.get(0);
                ImageView image = exerciseIllustrationPreview.create(
                        ExerciseFamilyCatalog.empty().identityForPreset(previewPreset));
                if (image != null) {
                    card.addView(image, exercisePreviewParams(ui));
                }
            }
            TextView check = ui.text(selected ? "✓" : "", 16,
                    selected ? FitnessUi.COLOR_INVERSE_TEXT : ui.inkMuted(), true);
            check.setGravity(Gravity.CENTER);
            card.addView(check, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
            ui.pressFeedback(card);
            card.setOnClickListener(v -> {
                if (family.presets.size() == 1) {
                    toggleRuntimePreset(result.presets.get(0), selectedPresets);
                    onSelectionChanged.run();
                } else {
                    showRuntimeVariantPicker(result.presets, selectedPresets, onSelectionChanged);
                }
            });
            listArea.addView(card, ui.fullWidthParams(listArea.getChildCount() == 0 ? 0 : ui.dp(8)));
        }
    }

    private void showRuntimeVariantPicker(
            List<RuntimeExercisePreset> presets,
            List<RuntimeExercisePreset> selectedPresets,
            Runnable onSelectionChanged
    ) {
        String[] labels = new String[presets.size()];
        boolean[] checked = new boolean[presets.size()];
        for (int index = 0; index < presets.size(); index++) {
            labels[index] = presets.get(index).displayName();
            checked[index] = containsRuntimePreset(selectedPresets, presets.get(index));
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("Variant 선택")
                .setMultiChoiceItems(labels, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", (dialog, which) -> {
                    for (int index = 0; index < presets.size(); index++) {
                        if (checked[index]) {
                            addRuntimePreset(presets.get(index), selectedPresets);
                        } else {
                            removeRuntimePreset(presets.get(index), selectedPresets);
                        }
                    }
                    onSelectionChanged.run();
                })
                .show();
    }

    private void setRuntimeSelectionSummary(
            TextView selectedCount,
            Button addButton,
            String addLabel,
            boolean routineMode,
            List<RuntimeExercisePreset> selectedPresets
    ) {
        selectedCount.setText("선택한 운동 " + selectedPresets.size() + "개");
        addButton.setText(selectedPresets.isEmpty()
                ? addLabel
                : (routineMode
                        ? "선택한 운동 " + selectedPresets.size() + "개 추가"
                        : "선택한 종목 " + selectedPresets.size() + "개 추가"));
        addButton.setEnabled(!selectedPresets.isEmpty());
    }

    private static String presetNames(List<RuntimeExercisePreset> presets) {
        StringBuilder names = new StringBuilder();
        int max = Math.min(3, presets.size());
        for (int index = 0; index < max; index++) {
            if (index > 0) {
                names.append(", ");
            }
            names.append(presets.get(index).displayName());
        }
        if (presets.size() > max) {
            names.append(" …");
        }
        return names.toString();
    }

    private static boolean hasSelectedFamily(String familyId, List<RuntimeExercisePreset> selectedPresets) {
        for (RuntimeExercisePreset preset : selectedPresets) {
            if (familyId.equals(preset.familyId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRuntimePreset(
            List<RuntimeExercisePreset> selectedPresets,
            RuntimeExercisePreset candidate
    ) {
        for (RuntimeExercisePreset preset : selectedPresets) {
            if (preset.identityId().equals(candidate.identityId())) {
                return true;
            }
        }
        return false;
    }

    private static void addRuntimePreset(
            RuntimeExercisePreset preset,
            List<RuntimeExercisePreset> selectedPresets
    ) {
        if (!containsRuntimePreset(selectedPresets, preset)) {
            selectedPresets.add(preset);
        }
    }

    private static void removeRuntimePreset(
            RuntimeExercisePreset preset,
            List<RuntimeExercisePreset> selectedPresets
    ) {
        for (int index = selectedPresets.size() - 1; index >= 0; index--) {
            if (selectedPresets.get(index).identityId().equals(preset.identityId())) {
                selectedPresets.remove(index);
            }
        }
    }

    private static void toggleRuntimePreset(
            RuntimeExercisePreset preset,
            List<RuntimeExercisePreset> selectedPresets
    ) {
        if (containsRuntimePreset(selectedPresets, preset)) {
            removeRuntimePreset(preset, selectedPresets);
        } else {
            addRuntimePreset(preset, selectedPresets);
        }
    }

    private LinearLayout.LayoutParams exercisePreviewParams(FitnessUi ui) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ui.dp(ExerciseIllustrationPreview.SIZE_DP),
                ui.dp(ExerciseIllustrationPreview.SIZE_DP)
        );
        params.setMargins(ui.dp(8), 0, 0, 0);
        return params;
    }

    private void renderPicker(boolean routineMode) {
        FitnessUi ui = ui();
        String recordId = routineMode
                ? null
                : (host.sessionState().activeRecordId() != null
                        ? host.sessionState().activeRecordId()
                        : host.currentWorkoutRecordId());

        BodyPart[] selectedBodyPart = new BodyPart[]{null};
        String[] selectedSubPart = new String[]{null};
        EquipmentType[] selectedEquipment = new EquipmentType[]{null};
        List<String> selectedExerciseIds = new ArrayList<>();
        List<WeightExercise> selectedExercises = new ArrayList<>();
        List<Button> bodyButtons = new ArrayList<>();
        Runnable[] refresh = new Runnable[1];
        EditText routineNameInput = routineMode
                ? ui.input("루틴 이름", "")
                : null;

        if (routineMode) {
            add(ui.textAction("‹ 무산소로", FitnessUi.COLOR_MUTED,
                    () -> host.navigate(FitnessScreen.STRENGTH)), ui.fullWidthParams(0));
        } else {
            add(ui.textAction("‹ 운동으로", FitnessUi.COLOR_MUTED, () -> {
                if (host.sessionState().activeRecordId() != null) {
                    host.navigate(FitnessScreen.WORKOUT_SESSION);
                } else {
                    host.navigate(FitnessScreen.STRENGTH);
                }
            }), ui.fullWidthParams(0));
        }

        TextView eyebrowView = ui.caption(routineMode ? "루틴 구성" : "진행 중 운동", FitnessUi.COLOR_MUTED);
        eyebrowView.setPadding(0, ui.dp(16), 0, 0);
        add(eyebrowView);
        add(ui.titleView(routineMode ? "루틴 추가" : "운동 종목 추가"));

        if (routineMode) {
            add(ui.fieldLabel("루틴 이름"));
            add(routineNameInput, ui.fullWidthParams(0));
        }

        EditText searchInput = ui.searchField("운동명, 영문명, 장비 검색");
        add(searchInput, ui.fullWidthParams(routineMode ? ui.dp(12) : 0));

        LinearLayout bodyRowTop = ui.pickerRow();
        LinearLayout bodyRowBottom = ui.pickerRow();
        Button allBodyButton = ui.filterButton("전체");
        allBodyButton.setOnClickListener(v -> {
            selectedBodyPart[0] = null;
            selectedSubPart[0] = null;
            refresh[0].run();
        });
        bodyRowTop.addView(allBodyButton, ui.pickerCellParams(true));
        BodyPart[] bodyParts = BodyPart.values();
        for (int index = 0; index < bodyParts.length; index++) {
            BodyPart bodyPart = bodyParts[index];
            Button filterButton = ui.filterButton(bodyPart.labelKo());
            filterButton.setOnClickListener(v -> {
                selectedBodyPart[0] = bodyPart;
                selectedSubPart[0] = null;
                refresh[0].run();
            });
            bodyButtons.add(filterButton);
            if (index < 3) {
                bodyRowTop.addView(filterButton, ui.pickerCellParams(false));
            } else {
                bodyRowBottom.addView(filterButton, ui.pickerCellParams(index == 3));
            }
        }
        add(bodyRowTop, ui.fullWidthParams(ui.dp(10)));
        add(bodyRowBottom, ui.fullWidthParams(ui.dp(6)));

        LinearLayout subPartArea = new LinearLayout(host.activity());
        subPartArea.setOrientation(LinearLayout.VERTICAL);
        subPartArea.setVisibility(View.GONE);
        add(subPartArea, ui.fullWidthParams(ui.dp(6)));

        Button equipmentButton = ui.filterButton("장비: 전체");
        equipmentButton.setOnClickListener(v ->
                showEquipmentFilterDialog(selectedEquipment, equipmentButton, refresh[0]));
        add(equipmentButton, ui.fullWidthParams(ui.dp(10)));

        TextView selectedCount = ui.text("선택한 운동 0개", 13, FitnessUi.COLOR_MUTED, true);
        selectedCount.setPadding(0, ui.dp(14), 0, 0);
        add(selectedCount);

        String addLabel = routineMode ? "선택한 운동 추가" : "선택한 종목 추가";
        Button addButton = ui.button(addLabel, true, v -> {
            if (routineMode) {
                String routineName = FitnessUi.inputText(routineNameInput).trim();
                if (routineName.isEmpty()) {
                    host.toast("루틴 이름을 입력하세요.");
                    return;
                }
                if (selectedExercises.isEmpty()) {
                    host.toast("추가할 운동을 선택하세요.");
                    return;
                }

                if (!host.routineRepository().canCreateRoutine()) {
                    host.toast("루틴은 최대 5개까지 저장할 수 있습니다.");
                    return;
                }
                List<com.yeonsik.fitnessapp.exercise.RoutineExercise> routineExercises = new ArrayList<>();
                for (WeightExercise exercise : selectedExercises) {
                    routineExercises.add(ExerciseMasterAdapter.toRoutineExercise(exercise));
                }
                String createdRoutineId = host.routineRepository().createRoutine(routineName, routineExercises);
                if (createdRoutineId == null) {
                    host.toast("루틴은 최대 5개까지 저장할 수 있습니다.");
                    return;
                }
                host.routineRepository().selectRoutine(createdRoutineId);
                host.toast("루틴을 저장했습니다. (" + host.routineRepository().routines().size() + "/5)");
                host.navigate(FitnessScreen.ROUTINE_DETAIL);
                return;
            }

            if (recordId == null) {
                host.toast("먼저 운동을 시작하세요.");
                return;
            }
            if (selectedExercises.isEmpty()) {
                host.toast("추가할 운동을 선택하세요.");
                return;
            }

            for (WeightExercise exercise : selectedExercises) {
                repository().addExerciseFromMaster(recordId, ExerciseMasterAdapter.toRoutineExercise(exercise));
            }
            host.toast(selectedExercises.size() + "개 종목을 운동에 추가했습니다.");
            if (host.sessionState().activeRecordId() != null) {
                host.navigate(FitnessScreen.WORKOUT_SESSION);
            } else {
                host.navigate(FitnessScreen.STRENGTH);
            }
        });
        addButton.setEnabled(false);
        add(addButton, ui.fullWidthParams(ui.dp(10)));

        Runnable updateSelectionSummary = () -> setSelectionSummary(
                selectedCount,
                addButton,
                addLabel,
                routineMode,
                selectedExercises
        );

        LinearLayout listArea = new LinearLayout(host.activity());
        listArea.setOrientation(LinearLayout.VERTICAL);

        refresh[0] = () -> {
            ui.styleFilterButton(allBodyButton, selectedBodyPart[0] == null);
            for (int index = 0; index < bodyButtons.size(); index++) {
                ui.styleFilterButton(bodyButtons.get(index), bodyParts[index] == selectedBodyPart[0]);
            }
            renderSubPartFilters(
                    subPartArea,
                    selectedBodyPart[0],
                    selectedSubPart,
                    refresh[0]
            );
            updateSelectionSummary.run();
            renderPickerList(
                    listArea,
                    filteredWeightExercises(
                            FitnessUi.inputText(searchInput),
                            selectedBodyPart[0],
                            selectedSubPart[0],
                            selectedEquipment[0]
                    ),
                    selectedExerciseIds,
                    selectedExercises,
                    updateSelectionSummary
            );
        };

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && !s.toString().trim().isEmpty()) {
                    selectedBodyPart[0] = null;
                    selectedSubPart[0] = null;
                }
                refresh[0].run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        section("운동 목록");
        add(listArea, ui.fullWidthParams(0));
        refresh[0].run();
    }

    private void renderSubPartFilters(
            LinearLayout area,
            BodyPart bodyPart,
            String[] selectedSubPart,
            Runnable refresh
    ) {
        FitnessUi ui = ui();
        area.removeAllViews();
        if (bodyPart == null) {
            selectedSubPart[0] = null;
            area.setVisibility(View.GONE);
            return;
        }

        List<ExerciseCategory.SubPart> subParts = availableSubParts(bodyPart);
        if (subParts.isEmpty()) {
            selectedSubPart[0] = null;
            area.setVisibility(View.GONE);
            return;
        }

        area.setVisibility(View.VISIBLE);
        TextView label = ui.caption(bodyPart.labelKo() + " 세부 부위", FitnessUi.COLOR_MUTED);
        label.setPadding(0, ui.dp(4), 0, ui.dp(6));
        area.addView(label);

        List<ExerciseCategory.SubPart> filters = new ArrayList<>();
        filters.add(new ExerciseCategory.SubPart(null, "전체"));
        filters.addAll(subParts);
        for (int start = 0; start < filters.size(); start += 3) {
            LinearLayout row = ui.pickerRow();
            int end = Math.min(start + 3, filters.size());
            for (int index = start; index < end; index++) {
                ExerciseCategory.SubPart subPart = filters.get(index);
                Button button = ui.filterButton(subPart.nameKo);
                ui.styleFilterButton(
                        button,
                        subPart.id == null
                                ? selectedSubPart[0] == null
                                : subPart.id.equals(selectedSubPart[0])
                );
                button.setOnClickListener(v -> {
                    selectedSubPart[0] = subPart.id;
                    refresh.run();
                });
                row.addView(button, ui.pickerCellParams(index == start));
            }
            area.addView(row, ui.fullWidthParams(start == 0 ? 0 : ui.dp(6)));
        }
    }

    private List<ExerciseCategory.SubPart> availableSubParts(BodyPart bodyPart) {
        List<WeightExercise> exercises =
                host.exerciseMasterRepository().getExercisesByBodyPart(bodyPart);
        List<ExerciseCategory.SubPart> results = new ArrayList<>();
        for (ExerciseCategory category :
                host.exerciseMasterRepository().getExerciseCategories()) {
            if (category.bodyPart != bodyPart) {
                continue;
            }
            for (ExerciseCategory.SubPart subPart : category.subParts) {
                for (WeightExercise exercise : exercises) {
                    if (subPart.id.equals(exercise.primarySubPart)) {
                        results.add(subPart);
                        break;
                    }
                }
            }
            break;
        }
        return results;
    }

    private void showEquipmentFilterDialog(EquipmentType[] selectedEquipment, Button equipmentButton, Runnable refresh) {
        String[] labels = new String[EquipmentType.values().length + 1];
        labels[0] = "전체";
        for (int index = 0; index < EquipmentType.values().length; index++) {
            labels[index + 1] = EquipmentType.values()[index].labelKo();
        }

        new AlertDialog.Builder(host.activity())
                .setTitle("장비 선택")
                .setItems(labels, (dialog, which) -> {
                    selectedEquipment[0] = which == 0 ? null : EquipmentType.values()[which - 1];
                    equipmentButton.setText("장비: "
                            + (selectedEquipment[0] == null ? "전체" : selectedEquipment[0].labelKo()));
                    refresh.run();
                })
                .show();
    }

    private void renderPickerList(
            LinearLayout listArea,
            List<WeightExercise> exercises,
            List<String> selectedExerciseIds,
            List<WeightExercise> selectedExercises,
            Runnable onSelectionChanged
    ) {
        FitnessUi ui = ui();
        listArea.removeAllViews();
        if (exercises.isEmpty()) {
            LinearLayout empty = ui.card();
            empty.addView(ui.text("조건에 맞는 운동이 없습니다.", 14, FitnessUi.COLOR_MUTED, false));
            listArea.addView(empty);
            return;
        }

        for (WeightExercise exercise : exercises) {
            boolean selected = selectedExerciseIds.contains(exercise.id);
            LinearLayout card = new LinearLayout(host.activity());
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));

            ExerciseCardRenderer.Content content =
                    ExerciseCardRenderer.Content.fromWeightExercise(exercise);
            ExerciseCardRenderer.Binding cardBinding =
                    exerciseCardRenderer.addContent(card, content, true, selected);

            card.setClickable(true);
            card.setFocusable(true);
            ui.pressFeedback(card);
            card.setOnClickListener(v -> {
                boolean selectedNow;
                if (selectedExerciseIds.contains(exercise.id)) {
                    removeSelectedExercise(exercise.id, selectedExerciseIds, selectedExercises);
                    selectedNow = false;
                } else {
                    selectedExerciseIds.add(exercise.id);
                    selectedExercises.add(exercise);
                    selectedNow = true;
                }
                cardBinding.applySelection(selectedNow);
                onSelectionChanged.run();
            });
            LinearLayout.LayoutParams cardParams = ui.fullWidthParams(listArea.getChildCount() == 0 ? 0 : ui.dp(8));
            listArea.addView(card, cardParams);
        }
    }

    private void setSelectionSummary(
            TextView selectedCount,
            Button addButton,
            String addLabel,
            boolean routineMode,
            List<WeightExercise> selectedExercises
    ) {
        selectedCount.setText("선택한 운동 " + selectedExercises.size() + "개");
        addButton.setText(selectedExercises.isEmpty()
                ? addLabel
                : (routineMode
                        ? "선택한 운동 " + selectedExercises.size() + "개 추가"
                        : "선택한 종목 " + selectedExercises.size() + "개 추가"));
        addButton.setEnabled(!selectedExercises.isEmpty());
    }

    private void removeSelectedExercise(String exerciseId, List<String> selectedExerciseIds,
                                        List<WeightExercise> selectedExercises) {
        selectedExerciseIds.remove(exerciseId);
        for (int index = selectedExercises.size() - 1; index >= 0; index--) {
            if (exerciseId.equals(selectedExercises.get(index).id)) {
                selectedExercises.remove(index);
                return;
            }
        }
    }

    private List<WeightExercise> filteredWeightExercises(
            String query,
            BodyPart bodyPart,
            String primarySubPart,
            EquipmentType equipmentType
    ) {
        List<WeightExercise> source = host.exerciseMasterRepository().searchExercises(query);
        List<WeightExercise> results = new ArrayList<>();
        for (WeightExercise exercise : source) {
            if (bodyPart != null && exercise.bodyPart != bodyPart) {
                continue;
            }
            if (primarySubPart != null
                    && !primarySubPart.equals(exercise.primarySubPart)) {
                continue;
            }
            if (equipmentType != null && exercise.equipmentType != equipmentType) {
                continue;
            }
            results.add(exercise);
        }
        return results;
    }

}
