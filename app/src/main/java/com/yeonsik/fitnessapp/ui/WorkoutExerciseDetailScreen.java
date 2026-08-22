package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 종목 세부 화면: 종목 칩 탭 + 진행 요약 + 세트 편집 그리드(이전 값 참조) + 기록 분석.
 * 세트 입력이 최우선이므로 그리드가 위, 분석(이전 기록/개인 기록/볼륨 추이)이 아래다.
 */
public final class WorkoutExerciseDetailScreen extends BaseScreen {
    private static final int DEFAULT_REST_SECONDS = 90;
    private static final int REST_STEP_SECONDS = 15;

    /** 이번 종목의 기본 휴식(초). 스탬프 시 타이머와 세트 기록에 쓰인다. */
    private final int[] defaultRestSeconds = {DEFAULT_REST_SECONDS};

    public WorkoutExerciseDetailScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String recordId = host.sessionState().activeRecordId();
        if (recordId == null) {
            host.navigate(FitnessScreen.WORKOUT_SESSION);
            return;
        }

        List<FitnessRepository.SessionExerciseEntry> exercises = repository().sessionExerciseEntries(recordId);
        if (exercises.isEmpty()) {
            host.navigate(FitnessScreen.WORKOUT_SESSION);
            return;
        }

        FitnessUi ui = ui();
        FitnessRepository.SessionExerciseEntry activeExercise =
                WorkoutSessionState.findActiveExercise(exercises, host.sessionState().activeExerciseId());
        host.sessionState().setActiveExerciseId(activeExercise.id);
        List<FitnessRepository.SessionSetEntry> sets = repository().setsForExercise(activeExercise.id);
        if (sets.isEmpty()) {
            repository().addTypedSet(
                    recordId,
                    activeExercise.id,
                    1,
                    emptySetInput(false, null)
            );
            sets = repository().setsForExercise(activeExercise.id);
        }
        boolean allCompleted = WorkoutSessionState.allSetsCompleted(sets);
        defaultRestSeconds[0] = resolveDefaultRest(sets);

        FitnessRepository.ExerciseHistory lastHistory = repository().lastExerciseHistory(
                activeExercise.exerciseId, activeExercise.name, recordId);
        FitnessRepository.ExerciseBests bests = repository().exerciseBests(
                activeExercise.exerciseId, activeExercise.name, recordId);

        LinearLayout topRow = new LinearLayout(host.activity());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView backAction = ui.textAction("‹ 세션으로", FitnessUi.COLOR_MUTED,
                () -> host.navigate(FitnessScreen.WORKOUT_SESSION));
        topRow.addView(backAction, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(ui.textAction("종목 삭제", FitnessUi.COLOR_MUTED,
                () -> confirmDeleteExercise(recordId, activeExercise)));
        add(topRow, ui.fullWidthParams(0));

        add(ui.titleView(activeExercise.name));

        renderExerciseTabs(exercises, activeExercise, allCompleted);
        renderProgressSummaryCard(activeExercise, sets);

        section("세트 기록");
        renderExerciseSetEditorCard(recordId, activeExercise, sets, lastHistory);

        section("기록 분석");
        if (supportsLoadRepAnalytics(activeExercise.recordType)) {
            renderPersonalRecordCard(bests, sets);
            add(volumeTrendCard("볼륨 추이", "최근 8회 + 현재",
                    repository().recentExerciseVolumes(
                            activeExercise.exerciseId,
                            activeExercise.name,
                            recordId,
                            8
                    ),
                    currentExerciseVolume(sets)));
        }
        renderLastHistoryCard(activeExercise.recordType, lastHistory);
    }

    // ── 진행 요약 ─────────────────────────────────────────────────────

    private void renderProgressSummaryCard(FitnessRepository.SessionExerciseEntry activeExercise,
                                           List<FitnessRepository.SessionSetEntry> sets) {
        FitnessUi ui = ui();
        LinearLayout summary = ui.card();
        LinearLayout summaryHeader = new LinearLayout(host.activity());
        summaryHeader.setOrientation(LinearLayout.HORIZONTAL);
        summaryHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout summaryColumn = new LinearLayout(host.activity());
        summaryColumn.setOrientation(LinearLayout.VERTICAL);
        summaryColumn.addView(ui.text(activeExercise.uiPart
                        + (activeExercise.equipment.isEmpty() ? "" : " · " + activeExercise.equipment),
                13, FitnessUi.COLOR_MUTED, false));
        TextView summaryProgressText = ui.num(sets.isEmpty()
                        ? "세트 없음"
                        : WorkoutSessionState.completedSetCount(sets) + "/" + sets.size() + " 세트 완료",
                16, FitnessUi.COLOR_TEXT, true);
        summaryProgressText.setPadding(0, ui.dp(4), 0, 0);
        summaryColumn.addView(summaryProgressText);
        summaryHeader.addView(summaryColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        summary.addView(summaryHeader);
        double ratio = sets.isEmpty() ? 0 : (double) WorkoutSessionState.completedSetCount(sets) / sets.size();
        View progress = ui.progressBar(ratio, false);
        LinearLayout.LayoutParams progressParams = ui.fullWidthParams(ui.dp(12));
        progressParams.height = ui.dp(6);
        summary.addView(progress, progressParams);
        add(summary);
    }

    // ── 종목 탭 ───────────────────────────────────────────────────────

    private void renderExerciseTabs(
            List<FitnessRepository.SessionExerciseEntry> exercises,
            FitnessRepository.SessionExerciseEntry activeExercise,
            boolean allowForwardMove
    ) {
        FitnessUi ui = ui();
        HorizontalScrollView scroller = new HorizontalScrollView(host.activity());
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(host.activity());
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (FitnessRepository.SessionExerciseEntry exercise : exercises) {
            Button tabButton = ui.filterButton(exercise.orderIndex + ". " + exercise.name);
            boolean isActive = exercise.id.equals(activeExercise.id);
            boolean canOpen = exercise.orderIndex <= activeExercise.orderIndex || allowForwardMove;
            ui.styleFilterButton(tabButton, isActive);
            tabButton.setEnabled(canOpen);
            if (!canOpen) {
                tabButton.setTextColor(ui.mappedTextColor(FitnessUi.COLOR_MUTED));
            }
            tabButton.setOnClickListener(v -> {
                host.sessionState().setActiveExerciseId(exercise.id);
                host.rerender();
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            chipParams.setMargins(chipRow.getChildCount() == 0 ? 0 : ui.dp(8), 0, 0, 0);
            chipRow.addView(tabButton, chipParams);
        }
        scroller.addView(chipRow);
        add(scroller, ui.fullWidthParams(ui.dp(4)));
    }

    // ── 세트 편집 그리드 ──────────────────────────────────────────────

    private void renderExerciseSetEditorCard(
            String recordId,
            FitnessRepository.SessionExerciseEntry activeExercise,
            List<FitnessRepository.SessionSetEntry> sets,
            FitnessRepository.ExerciseHistory lastHistory
    ) {
        FitnessUi ui = ui();
        LinearLayout setCard = ui.card();

        // 헤더: 제목 + 기본 휴식 스테퍼 (타이머·새 세트에 적용)
        LinearLayout headerRow = new LinearLayout(host.activity());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView setTitle = ui.text("세트", 16, FitnessUi.COLOR_TEXT, true);
        headerRow.addView(setTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(restStepper());
        setCard.addView(headerRow);

        // 이전 세션의 세트 인덱스별 참조값
        Map<Integer, FitnessRepository.SessionSetEntry> previousBySetIndex = new HashMap<>();
        if (lastHistory != null) {
            for (FitnessRepository.SessionSetEntry prev : lastHistory.sets) {
                previousBySetIndex.put(prev.setIndex, prev);
            }
        }

        LinearLayout columnHeader = new LinearLayout(host.activity());
        columnHeader.setOrientation(LinearLayout.HORIZONTAL);
        columnHeader.setGravity(Gravity.CENTER_VERTICAL);
        addColumnHeader(columnHeader, "이전", new LinearLayout.LayoutParams(ui.dp(56),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addColumnHeader(columnHeader, "무게 kg", ui.fieldCellParams(false));
        addColumnHeader(columnHeader, "횟수", ui.fieldCellParams(false));
        TextView stampHeader = ui.caption("완료", FitnessUi.COLOR_MUTED);
        stampHeader.setGravity(Gravity.CENTER);
        columnHeader.addView(stampHeader, new LinearLayout.LayoutParams(ui.dp(48),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView deleteHeader = ui.caption("삭제", FitnessUi.COLOR_MUTED);
        deleteHeader.setGravity(Gravity.CENTER);
        columnHeader.addView(deleteHeader, new LinearLayout.LayoutParams(ui.dp(32),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        addTypedColumnHeader(setCard, activeExercise.recordType);

        for (FitnessRepository.SessionSetEntry set : sets) {
            renderTypedSetRow(
                    setCard,
                    recordId,
                    activeExercise,
                    set,
                    previousBySetIndex.get(set.setIndex)
            );
        }
        setCard.addView(ui.button("+ 세트 추가", false, v -> addSet(recordId, activeExercise, sets)),
                ui.fullWidthParams(ui.dp(12)));
        add(setCard);
    }

    /** 기본 휴식 스테퍼: −/+ 15초. 세트 완료 시 타이머와 새 세트의 rest_seconds에 쓰인다. */
    private View restStepper() {
        FitnessUi ui = ui();
        LinearLayout stepper = new LinearLayout(host.activity());
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = ui.caption("휴식", FitnessUi.COLOR_MUTED);
        label.setPadding(0, 0, ui.dp(8), 0);
        stepper.addView(label);

        TextView minus = stepperButton("−");
        TextView valueView = ui.num(defaultRestSeconds[0] + "초", 14, FitnessUi.COLOR_TEXT, true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setMinWidth(ui.dp(48));
        TextView plus = stepperButton("＋");

        minus.setOnClickListener(v -> {
            defaultRestSeconds[0] = Math.max(REST_STEP_SECONDS, defaultRestSeconds[0] - REST_STEP_SECONDS);
            valueView.setText(defaultRestSeconds[0] + "초");
        });
        plus.setOnClickListener(v -> {
            defaultRestSeconds[0] = Math.min(600, defaultRestSeconds[0] + REST_STEP_SECONDS);
            valueView.setText(defaultRestSeconds[0] + "초");
        });

        stepper.addView(minus);
        stepper.addView(valueView);
        stepper.addView(plus);
        return stepper;
    }

    private TextView stepperButton(String glyph) {
        FitnessUi ui = ui();
        TextView button = ui.text(glyph, 16, FitnessUi.COLOR_TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(ui.borderDrawable(ui.subtle(), ui.subtle(), ui.dp(999)));
        button.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        button.setClickable(true);
        button.setFocusable(true);
        ui.applyDepth(button, 3);
        ui.pressFeedback(button);
        return button;
    }

    private void addColumnHeader(LinearLayout row, String label, LinearLayout.LayoutParams params) {
        TextView header = ui().caption(label, FitnessUi.COLOR_MUTED);
        header.setGravity(Gravity.CENTER);
        row.addView(header, params);
    }

    private void addTypedColumnHeader(LinearLayout card, String recordType) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addColumnHeader(row, "이전", new LinearLayout.LayoutParams(
                ui.dp(52),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addColumnHeader(row, primaryLabel(recordType), ui.fieldCellParams(false));
        addColumnHeader(row, secondaryLabel(recordType), ui.fieldCellParams(false));
        if (FitnessRecordContract.supportsRir(recordType)) {
            addColumnHeader(row, "RIR", ui.fieldCellParams(false));
        }
        addColumnHeader(row, "완료", new LinearLayout.LayoutParams(
                ui.dp(44),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addColumnHeader(row, "삭제", new LinearLayout.LayoutParams(
                ui.dp(30),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(row, ui.fullWidthParams(ui.dp(12)));
    }

    private void renderTypedSetRow(
            LinearLayout card,
            String recordId,
            FitnessRepository.SessionExerciseEntry exercise,
            FitnessRepository.SessionSetEntry set,
            FitnessRepository.SessionSetEntry previousSet
    ) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(6));
        if (set.isCompleted) {
            row.setBackground(ui.borderDrawable(ui.subtle(), ui.subtle(), ui.dp(12)));
            ui.applyDepth(row, 2);
        }

        TextView previous = ui.num(
                previousSet == null ? "--" : setSummary(exercise.recordType, previousSet),
                10,
                FitnessUi.COLOR_TERTIARY,
                true
        );
        previous.setGravity(Gravity.CENTER);
        previous.setMaxLines(2);
        row.addView(previous, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)));

        EditText primary = typedPrimaryInput(exercise.recordType, set);
        EditText secondary = typedSecondaryInput(exercise.recordType, set);
        EditText rir = FitnessRecordContract.supportsRir(exercise.recordType)
                ? ui.numberInput("", set.rir == null ? "" : String.valueOf(set.rir))
                : null;
        EditText[] effortInputs = rir == null
                ? new EditText[]{primary, secondary}
                : new EditText[]{primary, secondary, rir};
        for (EditText input : effortInputs) {
            input.setGravity(Gravity.CENTER);
            input.setPadding(ui.dp(6), ui.dp(9), ui.dp(6), ui.dp(9));
        }
        secondary.setEnabled(!secondaryLabel(exercise.recordType).isEmpty());
        row.addView(primary, ui.fieldCellParams(false));
        row.addView(secondary, ui.fieldCellParams(false));
        if (rir != null) {
            row.addView(rir, ui.fieldCellParams(false));
        }

        if (previousSet != null) {
            previous.setClickable(true);
            previous.setFocusable(true);
            previous.setOnClickListener(view -> {
                applyPrevious(exercise.recordType, previousSet, primary, secondary, rir);
                try {
                    repository().updateTypedSet(
                            recordId,
                            set.id,
                            typedSetInput(
                                    exercise.recordType,
                                    primary,
                                    secondary,
                                    rir,
                                    set.restSeconds,
                                    set.isCompleted
                            )
                    );
                } catch (IllegalArgumentException error) {
                    host.toast(error.getMessage());
                }
            });
        }

        LinearLayout stampCell = new LinearLayout(host.activity());
        stampCell.setGravity(Gravity.CENTER);
        TextView stamp = ui.num("✓", 16, FitnessUi.COLOR_TEXT, true);
        stamp.setGravity(Gravity.CENTER);
        styleStamp(stamp, set.isCompleted);
        stamp.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)));
        stampCell.addView(stamp);
        stampCell.setClickable(true);
        stampCell.setFocusable(true);
        stampCell.setOnClickListener(view -> {
            boolean completed = !set.isCompleted;
            try {
                repository().updateTypedSet(
                        recordId,
                        set.id,
                        typedSetInput(
                                exercise.recordType,
                                primary,
                                secondary,
                                rir,
                                completed ? defaultRestSeconds[0] : set.restSeconds,
                                completed
                        )
                );
                styleStamp(stamp, completed);
                ui.stampPop(stamp);
                if (completed) {
                    host.startRestTimer(defaultRestSeconds[0]);
                }
                host.content().postDelayed(host::rerender, 220);
            } catch (IllegalArgumentException error) {
                host.toast(error.getMessage());
                styleStamp(stamp, set.isCompleted);
            }
        });
        row.addView(stampCell, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(52)));

        if (set.setIndex > 1) {
            TextView delete = ui.num("×", 18, FitnessUi.COLOR_MUTED, true);
            delete.setGravity(Gravity.CENTER);
            delete.setClickable(true);
            delete.setFocusable(true);
            delete.setOnClickListener(view -> {
                repository().deleteSet(recordId, set.id);
                host.rerender();
            });
            row.addView(delete, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(52)));
        } else {
            row.addView(new TextView(host.activity()), new LinearLayout.LayoutParams(
                    ui.dp(30),
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
        card.addView(row, ui.fullWidthParams(ui.dp(6)));
    }

    private EditText typedPrimaryInput(
            String recordType,
            FitnessRepository.SessionSetEntry set
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            return ui().numberInput("", zeroToBlank(set.actualReps));
        }
        if (FitnessRecordContract.TIME.equals(type)) {
            return ui().numberInput("", zeroToBlank(set.durationSeconds));
        }
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            return ui().decimalInput("", zeroToBlank(set.assistedWeightKg));
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return ui().decimalInput("", zeroToBlank(set.addedWeightKg));
        }
        return ui().decimalInput("", zeroToBlank(set.weightKg));
    }

    private EditText typedSecondaryInput(
            String recordType,
            FitnessRepository.SessionSetEntry set
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            return ui().numberInput("", zeroToBlank(set.durationSeconds));
        }
        if (FitnessRecordContract.WEIGHT_REPS.equals(type)
                || FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)
                || FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return ui().numberInput("", zeroToBlank(set.actualReps));
        }
        return ui().numberInput("", "");
    }

    private FitnessRepository.SetInput typedSetInput(
            String recordType,
            EditText primary,
            EditText secondary,
            EditText rir,
            Integer restSeconds,
            boolean completed
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        Double weight = null;
        Integer reps = null;
        Integer duration = null;
        Double assisted = null;
        Double added = null;

        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            reps = FitnessUi.optionalInt(primary);
        } else if (FitnessRecordContract.TIME.equals(type)) {
            duration = FitnessUi.optionalInt(primary);
        } else if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            weight = FitnessUi.optionalDouble(primary);
            duration = FitnessUi.optionalInt(secondary);
        } else if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            assisted = FitnessUi.optionalDouble(primary);
            reps = FitnessUi.optionalInt(secondary);
        } else if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            added = FitnessUi.optionalDouble(primary);
            reps = FitnessUi.optionalInt(secondary);
        } else {
            weight = FitnessUi.optionalDouble(primary);
            reps = FitnessUi.optionalInt(secondary);
        }

        return new FitnessRepository.SetInput(
                weight,
                reps,
                duration,
                assisted,
                added,
                rir == null ? null : FitnessUi.optionalInt(rir),
                restSeconds,
                completed
        );
    }

    private void applyPrevious(
            String recordType,
            FitnessRepository.SessionSetEntry previous,
            EditText primary,
            EditText secondary,
            EditText rir
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            primary.setText(zeroToBlank(previous.actualReps));
        } else if (FitnessRecordContract.TIME.equals(type)) {
            primary.setText(zeroToBlank(previous.durationSeconds));
        } else if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            primary.setText(zeroToBlank(previous.weightKg));
            secondary.setText(zeroToBlank(previous.durationSeconds));
        } else if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            primary.setText(zeroToBlank(previous.assistedWeightKg));
            secondary.setText(zeroToBlank(previous.actualReps));
        } else if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            primary.setText(zeroToBlank(previous.addedWeightKg));
            secondary.setText(zeroToBlank(previous.actualReps));
        } else {
            primary.setText(zeroToBlank(previous.weightKg));
            secondary.setText(zeroToBlank(previous.actualReps));
        }
        if (rir != null) {
            rir.setText(previous.rir == null ? "" : String.valueOf(previous.rir));
        }
    }

    private static String primaryLabel(String recordType) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            return "횟수";
        }
        if (FitnessRecordContract.TIME.equals(type)) {
            return "초";
        }
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            return "보조 kg";
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return "추가 kg";
        }
        return "중량 kg";
    }

    private static String secondaryLabel(String recordType) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            return "초";
        }
        if (FitnessRecordContract.WEIGHT_REPS.equals(type)
                || FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)
                || FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return "횟수";
        }
        return "";
    }

    private static boolean supportsLoadRepAnalytics(String recordType) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        return FitnessRecordContract.WEIGHT_REPS.equals(type);
    }

    private static String setSummary(
            String recordType,
            FitnessRepository.SessionSetEntry set
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            return set.actualReps + "회";
        }
        if (FitnessRecordContract.TIME.equals(type)) {
            return set.durationSeconds + "초";
        }
        if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            return FitnessUi.trimDouble(set.weightKg) + "kg\n" + set.durationSeconds + "초";
        }
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            return "보조 " + FitnessUi.trimDouble(set.assistedWeightKg) + "kg\n" + set.actualReps + "회";
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return "추가 " + FitnessUi.trimDouble(set.addedWeightKg) + "kg\n" + set.actualReps + "회";
        }
        return FitnessUi.trimDouble(set.weightKg) + "kg\n" + set.actualReps + "회";
    }

    private static FitnessRepository.SetInput emptySetInput(
            boolean completed,
            Integer restSeconds
    ) {
        return new FitnessRepository.SetInput(
                null,
                null,
                null,
                null,
                null,
                null,
                restSeconds,
                completed
        );
    }

    private static String zeroToBlank(double value) {
        return value == 0 ? "" : FitnessUi.trimDouble(value);
    }

    private static String zeroToBlank(int value) {
        return value == 0 ? "" : String.valueOf(value);
    }

    private void renderSetRow(LinearLayout setCard, String recordId,
                              FitnessRepository.SessionSetEntry set,
                              FitnessRepository.SessionSetEntry previousSet) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(6));
        if (set.isCompleted) {
            row.setBackground(ui.borderDrawable(ui.subtle(), ui.subtle(), ui.dp(12)));
            ui.applyDepth(row, 2);
        }

        EditText weightInput = ui.decimalInput("", set.weightKg == 0 ? "" : FitnessUi.trimDouble(set.weightKg));
        EditText repsInput = ui.numberInput("", set.actualReps == 0 ? "" : String.valueOf(set.actualReps));
        weightInput.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
        repsInput.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10));
        weightInput.setGravity(Gravity.CENTER);
        repsInput.setGravity(Gravity.CENTER);

        // "이전" 참조 셀: 지난 세션 같은 세트의 값. 탭하면 이 세트에 즉시 적용된다.
        TextView prevCell = ui.num(previousSet == null
                        ? "—"
                        : FitnessUi.trimDouble(previousSet.weightKg) + "×" + previousSet.actualReps,
                11, FitnessUi.COLOR_TERTIARY, true);
        prevCell.setGravity(Gravity.CENTER);
        prevCell.setMaxLines(1);
        if (previousSet != null) {
            final FitnessRepository.SessionSetEntry prev = previousSet;
            prevCell.setClickable(true);
            prevCell.setFocusable(true);
            prevCell.setOnClickListener(v -> {
                weightInput.setText(prev.weightKg == 0 ? "" : FitnessUi.trimDouble(prev.weightKg));
                repsInput.setText(prev.actualReps == 0 ? "" : String.valueOf(prev.actualReps));
                repository().updateSet(recordId, set.id, prev.weightKg, prev.actualReps,
                        null, set.restSeconds, set.isCompleted);
            });
        }
        row.addView(prevCell, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(48)));

        row.addView(weightInput, ui.fieldCellParams(false));
        row.addView(repsInput, ui.fieldCellParams(false));

        // 완료 스탬프: 탭 1회 = 완료(반전 채움 + 팝), 재탭 = 해제. 시그니처 인터랙션.
        LinearLayout stampCell = new LinearLayout(host.activity());
        stampCell.setGravity(Gravity.CENTER);
        TextView stamp = ui.num("✓", 16, FitnessUi.COLOR_TEXT, true);
        stamp.setGravity(Gravity.CENTER);
        styleStamp(stamp, set.isCompleted);
        stamp.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        stampCell.addView(stamp);
        stampCell.setClickable(true);
        stampCell.setFocusable(true);
        stampCell.setOnClickListener(v -> {
            boolean nowCompleted = !set.isCompleted;
            styleStamp(stamp, nowCompleted);
            ui.stampPop(stamp);
            saveSet(recordId, set, weightInput, repsInput, nowCompleted);
        });
        row.addView(stampCell, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(52)));

        if (set.setIndex > 1) {
            TextView delete = ui.num("−", 18, FitnessUi.COLOR_MUTED, true);
            delete.setGravity(Gravity.CENTER);
            delete.setClickable(true);
            delete.setFocusable(true);
            delete.setOnClickListener(v -> {
                repository().deleteSet(recordId, set.id);
                host.rerender();
            });
            row.addView(delete, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(52)));
        } else {
            TextView spacer = new TextView(host.activity());
            row.addView(spacer, new LinearLayout.LayoutParams(ui.dp(32), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        setCard.addView(row, ui.fullWidthParams(ui.dp(6)));
    }

    /** 완료 = 히어로 팔레트 채움, 미완료 = 옅은 팔레트 링. */
    private void styleStamp(TextView stamp, boolean completed) {
        FitnessUi ui = ui();
        if (completed) {
            stamp.setTextColor(ui.onVibrant());
            stamp.setBackground(ui.vibrantBackground(0, ui.dp(999)));
        } else {
            stamp.setTextColor(ui.inkTertiary());
            stamp.setBackground(ui.flatSurfaceDrawable(ui.dp(999)));
        }
        ui.applyDepth(stamp, completed ? 5 : 2);
    }

    private void saveSet(String recordId, FitnessRepository.SessionSetEntry set,
                         EditText weightInput, EditText repsInput,
                         boolean completed) {
        repository().updateSet(recordId, set.id,
                FitnessUi.parseDouble(weightInput, 0),
                Math.max(0, FitnessUi.parseInt(repsInput, 0)),
                null,
                completed ? defaultRestSeconds[0] : set.restSeconds,
                completed);
        if (completed) {
            host.startRestTimer(defaultRestSeconds[0]);
        }
        if (completed && WorkoutSessionState.canMoveToNextExercise(repository(), recordId,
                host.sessionState().activeExerciseId())) {
            FitnessRepository.SessionExerciseEntry next = WorkoutSessionState.nextExercise(
                    repository().sessionExerciseEntries(recordId), host.sessionState().activeExerciseId());
            if (next != null) {
                host.sessionState().setActiveExerciseId(next.id);
            }
        }
        // 스탬프 팝 모션이 보이도록 rerender를 한 박자 늦춘다.
        host.content().postDelayed(host::rerender, 220);
    }

    // ── 기록 분석 ─────────────────────────────────────────────────────

    /** 개인 기록 카드: 역대 최고 무게 / 추정 1RM / 최고 세션 볼륨. 오늘 갱신 시 PR 뱃지. */
    private void renderPersonalRecordCard(FitnessRepository.ExerciseBests bests,
                                          List<FitnessRepository.SessionSetEntry> sets) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();

        double todayMaxWeight = 0;
        double todayBestE1rm = 0;
        for (FitnessRepository.SessionSetEntry set : sets) {
            if (!set.isCompleted) {
                continue;
            }
            todayMaxWeight = Math.max(todayMaxWeight, set.weightKg);
            todayBestE1rm = Math.max(todayBestE1rm, FitnessRepository.epleyE1rm(set.weightKg, set.actualReps));
        }
        double todayVolume = currentExerciseVolume(sets);
        boolean todayPr = bests.sessionCount > 0
                && (todayMaxWeight > bests.maxWeightKg
                || todayBestE1rm > bests.bestE1rmKg
                || todayVolume > bests.bestSessionVolumeKg);

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ui.text("개인 기록", 16, FitnessUi.COLOR_TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (todayPr) {
            header.addView(ui.statusDotBadge("오늘 PR", FitnessUi.COLOR_POSITIVE, false));
        } else if (bests.sessionCount > 0) {
            header.addView(ui.text(bests.sessionCount + "회 수행", 12, FitnessUi.COLOR_TERTIARY, false));
        }
        card.addView(header);

        if (bests.sessionCount == 0) {
            TextView empty = ui.text("이전 수행 기록이 없습니다. 오늘 기록이 기준이 됩니다.", 13, FitnessUi.COLOR_MUTED, false);
            empty.setPadding(0, ui.dp(10), 0, 0);
            card.addView(empty);
            add(card);
            return;
        }

        LinearLayout statRow = new LinearLayout(host.activity());
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.setPadding(0, ui.dp(12), 0, 0);
        statRow.addView(ui.inlineStat("최고 무게",
                FitnessUi.trimDouble(bests.maxWeightKg) + "kg × " + bests.repsAtMaxWeight + "회", false),
                ui.metaCellParams(true));
        statRow.addView(ui.inlineStat("추정 1RM",
                FitnessUi.formatVolume(round1(bests.bestE1rmKg)) + "kg", false),
                ui.metaCellParams(false));
        statRow.addView(ui.inlineStat("최고 볼륨",
                FitnessUi.formatVolume(bests.bestSessionVolumeKg) + "kg", false),
                ui.metaCellParams(false));
        card.addView(statRow);

        if (todayBestE1rm > 0) {
            TextView todayLine = ui.num("오늘 추정 1RM " + FitnessUi.formatVolume(round1(todayBestE1rm)) + "kg",
                    12, FitnessUi.COLOR_MUTED, false);
            todayLine.setPadding(0, ui.dp(10), 0, 0);
            card.addView(todayLine);
        }
        add(card);
    }

    /** 직전 세션의 같은 종목 수행 내역. 프로그레시브 오버로드의 기준점. */
    private void renderLastHistoryCard(
            String recordType,
            FitnessRepository.ExerciseHistory lastHistory
    ) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "이전 기록", lastHistory == null ? null : formatDate(lastHistory.date));

        if (lastHistory == null) {
            TextView empty = ui.text("이 종목의 이전 기록이 없습니다.", 13, FitnessUi.COLOR_MUTED, false);
            empty.setPadding(0, ui.dp(10), 0, 0);
            card.addView(empty);
            add(card);
            return;
        }

        for (FitnessRepository.SessionSetEntry set : lastHistory.sets) {
            card.addView(ui.keyValue(set.setIndex + "세트",
                    setSummary(recordType, set)));
        }
        if (supportsLoadRepAnalytics(recordType)) {
            View line = ui.hairline(ui.border());
            LinearLayout.LayoutParams lineParams = ui.fullWidthParams(ui.dp(10));
            lineParams.height = ui.dp(1);
            card.addView(line, lineParams);
            card.addView(ui.keyValue(
                    "외부 중량 볼륨",
                    FitnessUi.formatVolume(lastHistory.totalVolumeKg) + "kg"
            ));
        }
        add(card);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────

    private double currentExerciseVolume(List<FitnessRepository.SessionSetEntry> sets) {
        double volume = 0;
        for (FitnessRepository.SessionSetEntry set : sets) {
            if (set.isCompleted) {
                volume += set.weightKg * set.actualReps;
            }
        }
        return volume;
    }

    private int resolveDefaultRest(List<FitnessRepository.SessionSetEntry> sets) {
        for (int i = sets.size() - 1; i >= 0; i--) {
            Integer rest = sets.get(i).restSeconds;
            if (rest != null && rest > 0) {
                return rest;
            }
        }
        return DEFAULT_REST_SECONDS;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String formatDate(String date) {
        return date == null ? "" : date.replace("-", ". ");
    }

    private void addSet(String recordId, FitnessRepository.SessionExerciseEntry exercise,
                        List<FitnessRepository.SessionSetEntry> sets) {
        FitnessRepository.SessionSetEntry last = sets.isEmpty() ? null : sets.get(sets.size() - 1);
        int nextIndex = last == null ? 1 : last.setIndex + 1;
        repository().addTypedSet(
                recordId,
                exercise.id,
                nextIndex,
                new FitnessRepository.SetInput(
                        last == null || last.weightKg == 0 ? null : last.weightKg,
                        last == null || last.actualReps == 0 ? null : last.actualReps,
                        last == null || last.durationSeconds == 0 ? null : last.durationSeconds,
                        last == null || last.assistedWeightKg == 0 ? null : last.assistedWeightKg,
                        last == null || last.addedWeightKg == 0 ? null : last.addedWeightKg,
                        last == null ? null : last.rir,
                        defaultRestSeconds[0],
                        false
                )
        );
        host.rerender();
    }

    private void confirmDeleteExercise(String recordId, FitnessRepository.SessionExerciseEntry exercise) {
        ui().confirmSheet("종목 삭제",
                "\"" + exercise.name + "\" 종목과 해당 세트를 삭제 표시합니다.",
                null,
                "삭제", () -> {
                    repository().deleteExercise(recordId, exercise.id);
                    host.rerender();
                });
    }
}
