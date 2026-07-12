package com.yeonsik.fitnessapp.ui;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;

import java.util.List;

/**
 * 종목 세부 화면: 종목 칩 탭 + 진행 요약 + 세트 편집 카드.
 */
public final class WorkoutExerciseDetailScreen extends BaseScreen {

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
            repository().addSet(recordId, activeExercise.id, 1, 0, 0, null, null, false);
            sets = repository().setsForExercise(activeExercise.id);
        }
        boolean allCompleted = WorkoutSessionState.allSetsCompleted(sets);

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
        add(volumeTrendCard("이 종목 최근 4회 볼륨",
                repository().recentExerciseVolumes(activeExercise.exerciseId, recordId, 4),
                currentExerciseVolume(sets)));

        section("세트 편집");
        renderExerciseSetEditorCard(recordId, activeExercise, sets);

    }

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
                tabButton.setTextColor(FitnessUi.COLOR_MUTED);
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

    private void renderExerciseSetEditorCard(
            String recordId,
            FitnessRepository.SessionExerciseEntry activeExercise,
            List<FitnessRepository.SessionSetEntry> sets
    ) {
        FitnessUi ui = ui();
        LinearLayout setCard = ui.card();

        LinearLayout headerRow = new LinearLayout(host.activity());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView setTitle = ui.text("세트 기록", 16, FitnessUi.COLOR_TEXT, true);
        headerRow.addView(setTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        setCard.addView(headerRow);

        LinearLayout columnHeader = new LinearLayout(host.activity());
        columnHeader.setOrientation(LinearLayout.HORIZONTAL);
        columnHeader.setGravity(Gravity.CENTER_VERTICAL);
        addColumnHeader(columnHeader, "무게", ui.fieldCellParams(true));
        addColumnHeader(columnHeader, "횟수", ui.fieldCellParams(false));
        addColumnHeader(columnHeader, "휴식", ui.fieldCellParams(false));
        addColumnHeader(columnHeader, "완료 여부", new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.35f));
        TextView deleteHeader = ui.caption("세트 삭제", FitnessUi.COLOR_MUTED);
        deleteHeader.setGravity(Gravity.CENTER);
        columnHeader.addView(deleteHeader, new LinearLayout.LayoutParams(ui.dp(48),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setCard.addView(columnHeader, ui.fullWidthParams(ui.dp(12)));
        for (FitnessRepository.SessionSetEntry set : sets) {
            renderSetRow(setCard, recordId, set);
        }
        setCard.addView(ui.button("+", false, v -> addSet(recordId, activeExercise, sets)),
                ui.fullWidthParams(ui.dp(12)));
        add(setCard);
    }

    private void addColumnHeader(LinearLayout row, String label, LinearLayout.LayoutParams params) {
        TextView header = ui().caption(label, FitnessUi.COLOR_MUTED);
        header.setGravity(Gravity.CENTER);
        row.addView(header, params);
    }

    private void renderSetRow(LinearLayout setCard, String recordId,
                              FitnessRepository.SessionSetEntry set) {
        FitnessUi ui = ui();
        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, ui.dp(12), 0, 0);

        EditText weightInput = ui.decimalInput("", set.weightKg == 0 ? "" : FitnessUi.trimDouble(set.weightKg));
        EditText repsInput = ui.numberInput("", set.actualReps == 0 ? "" : String.valueOf(set.actualReps));
        EditText restInput = ui.numberInput("", set.restSeconds == null ? "" : String.valueOf(set.restSeconds));
        CheckBox completedBox = new CheckBox(host.activity());
        completedBox.setChecked(set.isCompleted);
        completedBox.setGravity(Gravity.CENTER);
        completedBox.setTextColor(FitnessUi.COLOR_TEXT);
        completedBox.setOnCheckedChangeListener((buttonView, isChecked) -> saveSet(recordId, set, weightInput, repsInput, restInput, isChecked));

        row.addView(weightInput, ui.fieldCellParams(true));
        row.addView(repsInput, ui.fieldCellParams(false));
        row.addView(restInput, ui.fieldCellParams(false));
        LinearLayout completedCell = new LinearLayout(host.activity());
        completedCell.setGravity(Gravity.CENTER);
        completedCell.addView(completedBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(completedCell, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.35f));
        if (set.setIndex > 1) {
            row.addView(ui.button("−", false, v -> {
                repository().deleteSet(recordId, set.id);
                host.rerender();
            }), new LinearLayout.LayoutParams(ui.dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            TextView spacer = new TextView(host.activity());
            row.addView(spacer, new LinearLayout.LayoutParams(ui.dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        setCard.addView(row, ui.fullWidthParams(0));
    }

    private void saveSet(String recordId, FitnessRepository.SessionSetEntry set,
                         EditText weightInput, EditText repsInput, EditText restInput,
                         boolean completed) {
        repository().updateSet(recordId, set.id,
                FitnessUi.parseDouble(weightInput, 0),
                Math.max(0, FitnessUi.parseInt(repsInput, 0)),
                null,
                FitnessUi.optionalInt(restInput),
                completed);
        if (completed && WorkoutSessionState.canMoveToNextExercise(repository(), recordId,
                host.sessionState().activeExerciseId())) {
            FitnessRepository.SessionExerciseEntry next = WorkoutSessionState.nextExercise(
                    repository().sessionExerciseEntries(recordId), host.sessionState().activeExerciseId());
            if (next != null) {
                host.sessionState().setActiveExerciseId(next.id);
            }
        }
        host.rerender();
    }

    private double currentExerciseVolume(List<FitnessRepository.SessionSetEntry> sets) {
        double volume = 0;
        for (FitnessRepository.SessionSetEntry set : sets) {
            if (set.isCompleted) {
                volume += set.weightKg * set.actualReps;
            }
        }
        return volume;
    }

    private void addSet(String recordId, FitnessRepository.SessionExerciseEntry exercise,
                        List<FitnessRepository.SessionSetEntry> sets) {
        FitnessRepository.SessionSetEntry last = sets.isEmpty() ? null : sets.get(sets.size() - 1);
        double weight = last == null ? 0 : last.weightKg;
        int reps = last == null ? 0 : last.actualReps;
        Integer restSeconds = last == null ? null : last.restSeconds;
        int nextIndex = last == null ? 1 : last.setIndex + 1;
        repository().addSet(recordId, exercise.id, nextIndex, weight, reps, null, restSeconds, false);
        host.rerender();
    }

    private void confirmDeleteExercise(String recordId, FitnessRepository.SessionExerciseEntry exercise) {
        new AlertDialog.Builder(host.activity())
                .setTitle("종목 삭제")
                .setMessage("\"" + exercise.name + "\" 종목과 해당 세트를 삭제 표시합니다.")
                .setPositiveButton("삭제", (dialog, which) -> {
                    repository().deleteExercise(recordId, exercise.id);
                    host.rerender();
                })
                .setNegativeButton("취소", null)
                .show();
    }
}
