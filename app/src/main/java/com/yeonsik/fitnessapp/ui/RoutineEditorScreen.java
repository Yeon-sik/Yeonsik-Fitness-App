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

import com.yeonsik.fitnessapp.data.FitnessRepository;
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
import com.yeonsik.fitnessapp.exercise.UiEquipmentCategory;
import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 루틴 상세(ROUTINE_DETAIL)와 운동 픽커(ROUTINE_ADD: 루틴에 추가,
 * WORKOUT_EXERCISE_ADD: 진행 중 세션에 추가 또는 기존 종목 교체)를 담당한다.
 * 두 픽커는 대상만 다르고 검색/필터/선택 UI가 같아 하나의 렌더러를 공유한다.
 */
public final class RoutineEditorScreen extends BaseScreen {
    private final ExerciseIllustrationPreview exerciseIllustrationPreview;
    private final ExerciseCardRenderer exerciseCardRenderer;
    private final ExerciseVariantPickerDialog exerciseVariantPickerDialog;

    public RoutineEditorScreen(ScreenHost host) {
        super(host);
        exerciseIllustrationPreview = new ExerciseIllustrationPreview(host.activity(), host.ui());
        exerciseCardRenderer = new ExerciseCardRenderer(
                host.activity(),
                host.ui(),
                exerciseIllustrationPreview
        );
        exerciseVariantPickerDialog = new ExerciseVariantPickerDialog(
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
        String replacementExerciseId = routineMode
                ? null
                : host.sessionState().replacementExerciseId();
        boolean replacementMode = !routineMode && replacementExerciseId != null;
        RuntimeExerciseCatalog catalog = host.exerciseMasterRepository().runtimeCatalog();
        RuntimeExercisePicker picker = new RuntimeExercisePicker(catalog);
        List<RuntimeExercisePreset> selectedPresets = new ArrayList<>();
        String replacementFamilyId = replacementMode ? "" : null;
        if (replacementMode && recordId != null) {
            for (FitnessRepository.SessionExerciseEntry entry
                    : repository().sessionExerciseEntries(recordId)) {
                if (!replacementExerciseId.equals(entry.id)) {
                    continue;
                }
                RuntimeExercisePreset currentPreset = catalog.presetForStorageExerciseId(
                        entry.exerciseId);
                if (currentPreset != null) {
                    replacementFamilyId = currentPreset.familyId;
                    selectedPresets.add(currentPreset);
                } else if (entry.familyIdentity != null
                        && entry.familyIdentity.familyId != null) {
                    replacementFamilyId = entry.familyIdentity.familyId;
                    RuntimeExercisePreset identityPreset = catalog.preset(
                            entry.familyIdentity.presetId);
                    if (identityPreset != null) {
                        selectedPresets.add(identityPreset);
                    }
                }
                break;
            }
        }
        final String fixedReplacementFamilyId = replacementFamilyId;
        BodyPart[] selectedBodyPart = new BodyPart[]{null};
        String[] selectedSubPart = new String[]{null};
        UiEquipmentCategory[] selectedEquipment = new UiEquipmentCategory[]{null};
        RuntimeExercisePicker.SortOrder[] selectedSort =
                new RuntimeExercisePicker.SortOrder[]{RuntimeExercisePicker.SortOrder.RECENT};
        Map<String, String> lastPerformedAt = repository().lastPerformedAtByCanonicalPreset();
        Set<String> favoritePresetIds = routineMode
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(repository().favoriteExercisePickerPresetIds());
        EditText routineNameInput = routineMode ? ui.input("루틴 이름", "") : null;

        if (routineMode) {
            add(ui.textAction("‹ 무산소로", FitnessUi.COLOR_MUTED,
                    () -> host.navigate(FitnessScreen.STRENGTH)), ui.fullWidthParams(0));
        } else {
            add(ui.textAction("‹ 운동으로", FitnessUi.COLOR_MUTED, () -> {
                host.sessionState().clearExerciseReplacement();
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
        add(ui.titleView(routineMode
                ? "루틴 추가"
                : (replacementMode ? "운동 종목 교체" : "운동 종목 추가")));
        if (replacementMode) {
            add(ui.text("같은 운동 Family의 variant를 하나 선택하면 기존 세트 기록을 유지한 채 종목만 교체합니다.",
                    13, FitnessUi.COLOR_MUTED, false), ui.fullWidthParams(ui.dp(4)));
        }
        if (routineMode) {
            add(ui.fieldLabel("루틴 이름"));
            add(routineNameInput, ui.fullWidthParams(0));
        }

        EditText searchInput = ui.searchField("운동명, 영문명, Family 검색");
        add(searchInput, ui.fullWidthParams(routineMode ? ui.dp(12) : 0));
        Runnable[] refreshHolder = new Runnable[1];

        Button bodyPartButton = ui.filterButton("부위: 전체");
        bodyPartButton.setOnClickListener(v -> showRuntimeBodyPartDialog(
                selectedBodyPart, bodyPartButton, selectedSubPart, refreshHolder));
        add(bodyPartButton, ui.fullWidthParams(ui.dp(8)));
        Button subPartButton = ui.filterButton("세부 부위: 전체");
        subPartButton.setOnClickListener(v -> showRuntimeSubPartDialog(
                catalog, selectedBodyPart[0], selectedSubPart, subPartButton, refreshHolder));
        add(subPartButton, ui.fullWidthParams(ui.dp(6)));
        Button equipmentButton = ui.filterButton("장비: 전체");
        equipmentButton.setOnClickListener(v -> showRuntimeEquipmentDialog(
                selectedEquipment, equipmentButton, refreshHolder));
        add(equipmentButton, ui.fullWidthParams(ui.dp(6)));
        Button sortButton = ui.filterButton("정렬: 최근 사용");
        sortButton.setOnClickListener(v -> showRuntimeSortDialog(
                selectedSort, sortButton, !routineMode, refreshHolder));
        add(sortButton, ui.fullWidthParams(ui.dp(6)));
        TextView selectedCount = ui.text("선택한 운동 0개", 13, FitnessUi.COLOR_MUTED, true);
        selectedCount.setPadding(0, ui.dp(14), 0, 0);
        add(selectedCount);

        String addLabel = routineMode
                ? "선택한 운동 추가"
                : (replacementMode ? "선택한 운동으로 교체" : "선택한 종목 추가");
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
                host.toast(replacementMode ? "교체할 운동을 선택하세요." : "추가할 운동을 선택하세요.");
                return;
            }
            if (replacementMode) {
                if (selectedPresets.size() != 1) {
                    host.toast("교체할 운동은 하나만 선택하세요.");
                    return;
                }
                RuntimeExercisePreset replacement = selectedPresets.get(0);
                boolean replaced = repository().replaceExerciseFromMaster(
                        recordId,
                        replacementExerciseId,
                        ExerciseMasterAdapter.toRoutineExercise(replacement)
                );
                if (!replaced) {
                    host.toast("교체할 운동을 찾지 못했습니다.");
                    return;
                }
                host.sessionState().clearExerciseReplacement();
                host.sessionState().setActiveExerciseId(replacementExerciseId);
                host.toast(replacement.displayName() + "으로 운동 종목을 교체했습니다.");
                host.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL);
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
                selectedCount, addButton, addLabel, routineMode, replacementMode, selectedPresets);
        LinearLayout listArea = new LinearLayout(host.activity());
        listArea.setOrientation(LinearLayout.VERTICAL);
        Runnable refresh = () -> {
            bodyPartButton.setText("부위: " + (selectedBodyPart[0] == null
                    ? "전체" : selectedBodyPart[0].labelKo()));
            subPartButton.setText("세부 부위: " + (selectedSubPart[0] == null
                    ? "전체" : selectedSubPart[0]));
            equipmentButton.setText("장비: " + (selectedEquipment[0] == null
                    ? "전체" : selectedEquipment[0].labelKo()));
            sortButton.setText("정렬: " + sortLabel(selectedSort[0]));
            updateSelectionSummary.run();
            renderRuntimePickerList(
                    listArea,
                    picker.search(new RuntimeExercisePicker.Filter(
                            FitnessUi.inputText(searchInput),
                            selectedBodyPart[0],
                            selectedSubPart[0],
                            selectedEquipment[0],
                            selectedSort[0],
                            lastPerformedAt,
                            favoritePresetIds
                    )),
                    selectedPresets,
                    favoritePresetIds,
                    replacementMode,
                    fixedReplacementFamilyId,
                    !routineMode,
                    !routineMode,
                    refreshHolder[0]
            );
        };
        refreshHolder[0] = refresh;
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
            Set<String> favoritePresetIds,
            boolean replacementMode,
            String replacementFamilyId,
            boolean showFamilyImages,
            boolean showFavorites,
            Runnable onSelectionChanged
    ) {
        FitnessUi ui = ui();
        listArea.removeAllViews();
        List<RuntimeExercisePicker.FamilyResult> visibleResults = new ArrayList<>();
        for (RuntimeExercisePicker.FamilyResult result : results) {
            if (replacementMode
                    && !replacementFamilyId.equals(result.family.familyId)) {
                continue;
            }
            visibleResults.add(result);
        }
        if (visibleResults.isEmpty()) {
            LinearLayout empty = ui.card();
            empty.addView(ui.text(
                    replacementMode
                            ? "현재 운동 Family의 variant가 없습니다."
                            : "조건에 맞는 운동 Family가 없습니다.",
                    14,
                    FitnessUi.COLOR_MUTED,
                    false
            ));
            listArea.addView(empty);
            return;
        }
        for (RuntimeExercisePicker.FamilyResult result : visibleResults) {
            RuntimeExerciseFamily family = result.family;
            boolean selected = hasSelectedFamily(family.familyId, selectedPresets);
            LinearLayout card = ui.card();
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(ui.dp(14), ui.dp(10), ui.dp(10), ui.dp(10));
            card.setClickable(true);
            card.setFocusable(true);
            card.setBackground(selected
                    ? ui.vibrantRippleDrawable("family-" + family.familyId, ui.dp(16))
                    : ui.flatSurfaceRippleDrawable(ui.dp(16)));
            ui.applyDepth(card, selected ? 7 : 4);

            LinearLayout info = new LinearLayout(host.activity());
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = ui.text(family.displayName(), 15,
                    selected ? FitnessUi.COLOR_INVERSE_TEXT : FitnessUi.COLOR_TEXT, true);
            info.addView(name);
            TextView meta = ui.text(
                    family.presets.size() == 1
                            ? family.presets.get(0).displayName()
                            : family.presets.size() + "개 variant · " + presetNames(result.presets),
                    12,
                    selected ? FitnessUi.COLOR_INVERSE_MUTED : FitnessUi.COLOR_MUTED,
                    false
            );
            meta.setPadding(0, ui.dp(4), 0, 0);
            info.addView(meta);
            card.addView(info, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            if (showFavorites) {
                TextView favorite = ui.text(
                        result.favorite ? "★" : "☆",
                        18,
                        result.favorite ? FitnessUi.COLOR_TERTIARY : FitnessUi.COLOR_MUTED,
                        true
                );
                favorite.setGravity(Gravity.CENTER);
                favorite.setContentDescription("즐겨찾기 " + family.displayName());
                favorite.setClickable(true);
                favorite.setFocusable(true);
                favorite.setOnClickListener(v -> {
                    if (family.presets.size() == 1) {
                        toggleFavorite(family.presets.get(0), favoritePresetIds);
                        onSelectionChanged.run();
                    } else {
                        showRuntimeFavoritePicker(result.presets, favoritePresetIds, onSelectionChanged);
                    }
                });
                card.addView(favorite, new LinearLayout.LayoutParams(
                        ui.dp(36), ui.dp(36)
                ));
            }
            if (showFamilyImages && !result.presets.isEmpty()) {
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
                    if (replacementMode) {
                        selectReplacementPreset(result.presets.get(0), selectedPresets);
                    } else {
                        toggleRuntimePreset(result.presets.get(0), selectedPresets);
                    }
                    onSelectionChanged.run();
                } else {
                    showRuntimeVariantPicker(
                            family,
                            result.presets,
                            selectedPresets,
                            replacementMode,
                            onSelectionChanged
                    );
                }
            });
            listArea.addView(card, ui.fullWidthParams(listArea.getChildCount() == 0 ? 0 : ui.dp(8)));
        }
    }

    private void showRuntimeVariantPicker(
            RuntimeExerciseFamily family,
            List<RuntimeExercisePreset> presets,
            List<RuntimeExercisePreset> selectedPresets,
            boolean replacementMode,
            Runnable onSelectionChanged
    ) {
        exerciseVariantPickerDialog.show(
                family,
                presets,
                selectedPresets,
                replacementMode,
                selected -> {
                    if (replacementMode) {
                        selectedPresets.clear();
                        selectedPresets.addAll(selected);
                    } else {
                        for (RuntimeExercisePreset preset : presets) {
                            if (containsRuntimePreset(selected, preset)) {
                                addRuntimePreset(preset, selectedPresets);
                            } else {
                                removeRuntimePreset(preset, selectedPresets);
                            }
                        }
                    }
                    onSelectionChanged.run();
                }
        );
    }

    private void showRuntimeFavoritePicker(
            List<RuntimeExercisePreset> presets,
            Set<String> favoritePresetIds,
            Runnable onChanged
    ) {
        String[] labels = new String[presets.size()];
        boolean[] checked = new boolean[presets.size()];
        for (int index = 0; index < presets.size(); index++) {
            RuntimeExercisePreset preset = presets.get(index);
            checked[index] = favoritePresetIds.contains(preset.identityId());
            labels[index] = (checked[index] ? "★ " : "☆ ") + preset.displayName();
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("즐겨찾기 preset")
                .setMultiChoiceItems(labels, checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", (dialog, which) -> {
                    for (int index = 0; index < presets.size(); index++) {
                        RuntimeExercisePreset preset = presets.get(index);
                        if (checked[index]) {
                            favoritePresetIds.add(preset.identityId());
                        } else {
                            favoritePresetIds.remove(preset.identityId());
                        }
                        repository().setExercisePickerFavorite(
                                preset.identityId(), checked[index]);
                    }
                    onChanged.run();
                })
                .show();
    }

    private void toggleFavorite(
            RuntimeExercisePreset preset,
            Set<String> favoritePresetIds
    ) {
        boolean next = !favoritePresetIds.contains(preset.identityId());
        if (next) {
            favoritePresetIds.add(preset.identityId());
        } else {
            favoritePresetIds.remove(preset.identityId());
        }
        repository().setExercisePickerFavorite(preset.identityId(), next);
    }

    private void showRuntimeBodyPartDialog(
            BodyPart[] selectedBodyPart,
            Button button,
            String[] selectedSubPart,
            Runnable[] refreshHolder
    ) {
        String[] labels = new String[BodyPart.values().length + 1];
        labels[0] = "전체";
        for (int index = 0; index < BodyPart.values().length; index++) {
            labels[index + 1] = BodyPart.values()[index].labelKo();
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("부위 선택")
                .setItems(labels, (dialog, which) -> {
                    selectedBodyPart[0] = which == 0 ? null : BodyPart.values()[which - 1];
                    selectedSubPart[0] = null;
                    refreshHolder[0].run();
                })
                .show();
    }

    private void showRuntimeSubPartDialog(
            RuntimeExerciseCatalog catalog,
            BodyPart selectedBodyPart,
            String[] selectedSubPart,
            Button button,
            Runnable[] refreshHolder
    ) {
        List<RuntimeSubPart> subParts = runtimeSubParts(catalog, selectedBodyPart);
        String[] labels = new String[subParts.size() + 1];
        labels[0] = "전체";
        for (int index = 0; index < subParts.size(); index++) {
            labels[index + 1] = subParts.get(index).name;
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("세부 부위 선택")
                .setItems(labels, (dialog, which) -> {
                    selectedSubPart[0] = which == 0 ? null : subParts.get(which - 1).id;
                    refreshHolder[0].run();
                })
                .show();
    }

    private void showRuntimeEquipmentDialog(
            UiEquipmentCategory[] selectedEquipment,
            Button button,
            Runnable[] refreshHolder
    ) {
        String[] labels = new String[UiEquipmentCategory.values().length + 1];
        labels[0] = "전체";
        for (int index = 0; index < UiEquipmentCategory.values().length; index++) {
            labels[index + 1] = UiEquipmentCategory.values()[index].labelKo();
        }
        new AlertDialog.Builder(host.activity())
                .setTitle("장비 대분류 선택")
                .setItems(labels, (dialog, which) -> {
                    selectedEquipment[0] = which == 0
                            ? null : UiEquipmentCategory.values()[which - 1];
                    refreshHolder[0].run();
                })
                .show();
    }

    private void showRuntimeSortDialog(
            RuntimeExercisePicker.SortOrder[] selectedSort,
            Button button,
            boolean includeFavorites,
            Runnable[] refreshHolder
    ) {
        String[] labels = includeFavorites
                ? new String[]{"최근 사용", "즐겨찾기", "가나다순"}
                : new String[]{"최근 사용", "가나다순"};
        new AlertDialog.Builder(host.activity())
                .setTitle("정렬")
                .setItems(labels, (dialog, which) -> {
                    selectedSort[0] = includeFavorites
                            ? RuntimeExercisePicker.SortOrder.values()[which]
                            : (which == 0
                                    ? RuntimeExercisePicker.SortOrder.RECENT
                                    : RuntimeExercisePicker.SortOrder.NAME);
                    refreshHolder[0].run();
                })
                .show();
    }

    private List<RuntimeSubPart> runtimeSubParts(
            RuntimeExerciseCatalog catalog,
            BodyPart selectedBodyPart
    ) {
        Map<String, RuntimeSubPart> byId = new java.util.LinkedHashMap<>();
        for (RuntimeExerciseFamily family : catalog.families) {
            if (selectedBodyPart != null
                    && !selectedBodyPart.id().equals(family.defaultUiPart)) {
                continue;
            }
            for (RuntimeExercisePreset preset : family.presets) {
                if (preset.primarySubPart == null || preset.primarySubPart.trim().isEmpty()) {
                    continue;
                }
                String name = preset.primarySubPartNameKo == null
                        || preset.primarySubPartNameKo.trim().isEmpty()
                        ? preset.primarySubPart
                        : preset.primarySubPartNameKo;
                byId.putIfAbsent(preset.primarySubPart, new RuntimeSubPart(preset.primarySubPart, name));
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static String sortLabel(RuntimeExercisePicker.SortOrder sortOrder) {
        if (sortOrder == RuntimeExercisePicker.SortOrder.FAVORITES) {
            return "즐겨찾기";
        }
        if (sortOrder == RuntimeExercisePicker.SortOrder.NAME) {
            return "가나다순";
        }
        return "최근 사용";
    }

    private static final class RuntimeSubPart {
        final String id;
        final String name;

        RuntimeSubPart(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private void setRuntimeSelectionSummary(
            TextView selectedCount,
            Button addButton,
            String addLabel,
            boolean routineMode,
            boolean replacementMode,
            List<RuntimeExercisePreset> selectedPresets
    ) {
        selectedCount.setText(replacementMode
                ? "교체할 운동 " + selectedPresets.size() + "개"
                : "선택한 운동 " + selectedPresets.size() + "개");
        String selectedAction = addLabel;
        if (!selectedPresets.isEmpty()) {
            if (replacementMode) {
                selectedAction = "선택한 운동으로 교체";
            } else if (routineMode) {
                selectedAction = "선택한 운동 " + selectedPresets.size() + "개 추가";
            } else {
                selectedAction = "선택한 종목 " + selectedPresets.size() + "개 추가";
            }
        }
        addButton.setText(selectedAction);
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

    private static void selectReplacementPreset(
            RuntimeExercisePreset preset,
            List<RuntimeExercisePreset> selectedPresets
    ) {
        selectedPresets.clear();
        addRuntimePreset(preset, selectedPresets);
    }

    private LinearLayout.LayoutParams exercisePreviewParams(FitnessUi ui) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ui.dp(ExerciseIllustrationPreview.SIZE_DP),
                ui.dp(ExerciseIllustrationPreview.SIZE_DP)
        );
        params.setMargins(ui.dp(8), 0, 0, 0);
        return params;
    }

}
