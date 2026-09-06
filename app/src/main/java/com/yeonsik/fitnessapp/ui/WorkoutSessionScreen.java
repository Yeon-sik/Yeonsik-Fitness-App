package com.yeonsik.fitnessapp.ui;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MassFormatter;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionExercise;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSessionSnapshot;
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutVolumePoint;
import com.yeonsik.fitnessapp.feature.workout.ui.WorkoutSessionUiState;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.List;
import java.util.Arrays;

/**
 * 운동 세션 화면: 현재 운동 진입 + 세트 진행 + compact 세션 요약 + 종목 분석.
 */
public final class WorkoutSessionScreen extends BaseScreen {
    private final ExerciseCardRenderer exerciseCardRenderer;

    public WorkoutSessionScreen(ScreenHost host) {
        super(host);
        exerciseCardRenderer = new ExerciseCardRenderer(
                host.activity(),
                host.ui(),
                new ExerciseIllustrationPreview(host.activity(), host.ui())
        );
    }

    @Override
    public void render() {
        String recordId = host.sessionState().activeRecordId();
        if (recordId == null) {
            host.replace(FitnessScreen.STRENGTH);
            return;
        }
        WorkoutSessionUiState state = host.workoutSessionViewModel().getUiState().getValue();
        if (!(state instanceof WorkoutSessionUiState.Ready)) {
            emptyState("운동 기록을 불러오는 중입니다.", "저장된 세션을 확인하고 있습니다.");
            return;
        }
        WorkoutSessionUiState.Ready ready = (WorkoutSessionUiState.Ready) state;
        WorkoutSessionSnapshot session = ready.getSession();
        if (!host.currentOwnerId().equals(ready.getOwnerId())
                || !recordId.equals(session.getRecordId())) {
            emptyState("운동 기록을 불러오는 중입니다.", "저장된 세션을 확인하고 있습니다.");
            return;
        }

        FitnessUi ui = ui();
        MassUnit displayUnit = MassUnit.orDefault(host.preferredMassUnit());
        List<WorkoutSessionExercise> exercises = session.getExercises();
        boolean inProgress = !"completed".equals(session.getStatus());
        boolean manualEntry = inProgress && session.getDurationSeconds() > 0;

        screenHeader(manualEntry ? "수동 등록" : "진행 중",
                session.getTitle().isEmpty() ? "운동 중" : session.getTitle());
        if (inProgress) {
            add(sessionInputMassUnitControl(), ui.fullWidthParams(ui.dp(4)));
        }

        WorkoutSessionExercise currentExercise = findActiveExercise(
                exercises,
                host.sessionState().activeExerciseId()
        );
        if (currentExercise != null) {
            currentExerciseCard(recordId, currentExercise);
        }

        LinearLayout sessionSummary = ui.card();
        sessionSummary.setGravity(Gravity.CENTER_HORIZONTAL);
        sessionSummary.addView(ui.caption("세션 요약", FitnessUi.COLOR_MUTED));
        TextView elapsedLabel = ui.caption("경과 시간", FitnessUi.COLOR_MUTED);
        elapsedLabel.setPadding(0, ui.dp(4), 0, 0);
        sessionSummary.addView(elapsedLabel);
        TextView elapsedView = new TextView(host.activity());
        elapsedView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        elapsedView.setTextSize(28);
        elapsedView.setTextColor(ui.mappedTextColor(FitnessUi.COLOR_TEXT));
        elapsedView.setFontFeatureSettings("tnum");
        elapsedView.setGravity(Gravity.CENTER);
        elapsedView.setPadding(0, ui.dp(2), 0, 0);
        elapsedView.setText("00:00:00");
        sessionSummary.addView(elapsedView, ui.fullWidthParams(0));

        View line = ui.hairline(FitnessUi.COLOR_BORDER);
        LinearLayout.LayoutParams lineParams = ui.fullWidthParams(ui.dp(14));
        lineParams.height = ui.dp(1);
        sessionSummary.addView(line, lineParams);

        LinearLayout strip = new LinearLayout(host.activity());
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, ui.dp(12), 0, 0);
        TextView volumeView = sessionMetricCell(strip, "총 볼륨", true);
        TextView completedSetsView = sessionMetricCell(strip, "완료 세트", false);
        TextView startView = sessionMetricCell(strip, "시작", false);
        strip.setGravity(Gravity.CENTER);
        sessionSummary.addView(strip, ui.fullWidthParams(0));

        volumeView.setText(MassFormatter.withUnit(session.getTotalVolumeKg(), displayUnit));
        completedSetsView.setText(session.getCompletedSetCount() + "개");
        startView.setText(FitnessUi.formatStartTime(session.getStartedAt()));
        add(sessionSummary);

        if (inProgress && !manualEntry) {
            startElapsedTicker(elapsedView, session.getStartedAt());
        } else {
            elapsedView.setText(session.getDurationSeconds() > 0
                    ? FitnessUi.formatElapsed(session.getDurationSeconds()) : "--:--:--");
        }

        if (exercises.isEmpty()) {
            section("운동 구성");
            emptyState("아직 종목이 없습니다.", "종목 추가 버튼으로 시작하세요.");
            add(volumeTrendCard(
                    "최근 4회 총 볼륨",
                    volumePoints(session.getRecentVolumes()),
                    session.getTotalVolumeKg(),
                    RecordsAnalysis.TrendCurrentState.IN_PROGRESS,
                    displayUnit
            ));
            return;
        }

        section("운동 구성");
        for (WorkoutSessionExercise exercise : exercises) {
            workoutExerciseCard(exercise);
        }

        add(volumeTrendCard(
                "최근 4회 총 볼륨",
                volumePoints(session.getRecentVolumes()),
                session.getTotalVolumeKg(),
                RecordsAnalysis.TrendCurrentState.IN_PROGRESS,
                displayUnit
        ));
    }

    private View sessionInputMassUnitControl() {
        FitnessUi ui = ui();
        MassUnit selectedUnit = host.sessionState().sessionInputMassUnit();
        if (selectedUnit == null) {
            selectedUnit = MassUnit.orDefault(host.preferredMassUnit());
            host.sessionState().setSessionInputMassUnit(selectedUnit);
        }
        final MassUnit[] selected = {selectedUnit};

        LinearLayout row = new LinearLayout(host.activity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = ui.caption("기본 단위", FitnessUi.COLOR_MUTED);
        row.addView(label, new LinearLayout.LayoutParams(
                0,
                ui.dp(36),
                1f
        ));
        for (MassUnit unit : MassUnit.values()) {
            TextView option = ui.text(
                    unit.symbol().toUpperCase(java.util.Locale.ROOT),
                    11,
                    unit == selected[0] ? ui.selectedInk() : FitnessUi.COLOR_TEXT,
                    true
            );
            option.setGravity(Gravity.CENTER);
            option.setContentDescription("새 세트 기본 단위: " + unit.symbol());
            option.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4));
            ui.styleSelection(option, unit == selected[0], ui.dp(8));
            option.setOnClickListener(view -> {
                selected[0] = unit;
                host.sessionState().setSessionInputMassUnit(unit);
                host.rerender();
            });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    ui.dp(54),
                    ui.dp(36)
            );
            if (unit != MassUnit.KG) {
                optionParams.setMargins(ui.dp(6), 0, 0, 0);
            }
            row.addView(option, optionParams);
        }
        return row;
    }

    private void currentExerciseCard(
            String recordId,
            WorkoutSessionExercise exercise
    ) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        card.setBackground(ui.tonalRippleDrawable(ui.dp(FitnessUi.CARD_RADIUS_DP)));
        ui.applyDepth(card, FitnessUi.DEPTH_SURFACE_DP);

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.caption("현재 운동", ui.tonalInk()),
                new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(ui.text("세트 기록 우선", 11, ui.tonalInk(), true));
        card.addView(header);

        TextView name = ui.text(exercise.getName(), 20, ui.tonalInk(), true);
        name.setPadding(0, ui.dp(8), 0, 0);
        card.addView(name);

        int completed = exercise.getCompletedSetCount();
        int total = exercise.getTotalSetCount();
        String progress = total == 0
                ? "첫 세트를 기록하세요"
                : completed < total
                        ? "세트 " + (completed + 1) + " 기록 · " + completed + "/" + total + " 완료"
                        : "모든 세트 완료 · 다음 종목을 선택하세요";
        TextView progressView = ui.text(progress, 13, ui.tonalInk(), false);
        progressView.setPadding(0, ui.dp(3), 0, 0);
        card.addView(progressView);

        card.addView(ui.tonalButton("세트 기록 열기", v -> {
                    host.sessionState().setActiveExerciseId(exercise.getId());
                    host.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL);
                }),
                ui.fullWidthParams(ui.dp(14)));
    }

    private void openExercisePicker() {
        String recordId = host.currentWorkoutRecordId();
        if (recordId == null) {
            host.toast("먼저 운동을 시작하세요.");
            return;
        }
        host.sessionState().clearExerciseReplacement();
        host.sessionState().setActiveRecordId(recordId);
        host.navigate(FitnessScreen.WORKOUT_EXERCISE_ADD);
    }

    private TextView sessionMetricCell(LinearLayout parent, String label, boolean first) {
        FitnessUi ui = ui();
        LinearLayout cell = new LinearLayout(host.activity());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.addView(ui.caption(label, FitnessUi.COLOR_MUTED));
        TextView valueView = ui.num("", 15, FitnessUi.COLOR_TEXT, true);
        valueView.setPadding(0, ui.dp(3), 0, 0);
        cell.addView(valueView);
        parent.addView(cell, ui.metaCellParams(first));
        return valueView;
    }

    private void workoutExerciseCard(WorkoutSessionExercise exercise) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(7));
        card.setClickable(true);
        card.setFocusable(true);
        ui.pressFeedback(card);
        card.setOnClickListener(v -> {
            host.sessionState().setActiveExerciseId(exercise.getId());
            host.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL);
        });

        LinearLayout headerRow = new LinearLayout(host.activity());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        ExerciseCardRenderer.Content content =
                new ExerciseCardRenderer.Content(
                        exercise.getExerciseId(),
                        exercise.getName(),
                        exercise.getUiPart(),
                        exercise.getEquipment(),
                        exercise.getRecordTypeLabel(),
                        exercise.getFamilyIdentity()
                );
        exerciseCardRenderer.addContent(headerRow, content, false, false);
        TextView chevron = ui.text("›", 16, FitnessUi.COLOR_TERTIARY, false);
        headerRow.addView(chevron);
        card.addView(headerRow);

        int completed = exercise.getCompletedSetCount();
        int total = exercise.getTotalSetCount();

        LinearLayout progressRow = new LinearLayout(host.activity());
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressRow.setPadding(0, ui.dp(4), 0, 0);
        double ratio = total == 0 ? 0 : (double) completed / total;
        View progress = ui.progressBar(ratio, false);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(0, ui.dp(6), 1f);
        progressRow.addView(progress, progressParams);
        TextView progressText = ui.num(total == 0
                ? "세트 없음"
                : completed + "/" + total + " 세트", 10, FitnessUi.COLOR_MUTED, true);
        progressText.setPadding(ui.dp(8), 0, 0, 0);
        progressRow.addView(progressText);
        card.addView(progressRow);

        add(card);
    }


    private static WorkoutSessionExercise findActiveExercise(
            List<WorkoutSessionExercise> exercises,
            String activeExerciseId
    ) {
        if (exercises == null || exercises.isEmpty()) {
            return null;
        }
        if (activeExerciseId != null) {
            for (WorkoutSessionExercise exercise : exercises) {
                if (activeExerciseId.equals(exercise.getId())) {
                    return exercise;
                }
            }
        }
        return exercises.get(0);
    }

    private static List<FitnessRepository.VolumePoint> volumePoints(
            List<WorkoutVolumePoint> points
    ) {
        java.util.ArrayList<FitnessRepository.VolumePoint> result = new java.util.ArrayList<>();
        for (WorkoutVolumePoint point : points) {
            result.add(new FitnessRepository.VolumePoint(
                    point.getDate(), point.getLabel(), point.getVolumeKg()
            ));
        }
        return result;
    }

    private void showLeaveSessionDialog() {
        String recordId = host.sessionState().activeRecordId();
        ui().choiceSheet("운동 나가기", Arrays.asList(
                "계속 운동하기", "임시 저장하고 나가기", "기록 삭제하고 나가기"
        ), -1, which -> {
                    if (which == 1) {
                        backOr(FitnessScreen.STRENGTH);
                        host.toast("임시 저장했습니다. 진행 중 운동에서 이어할 수 있습니다.");
                    } else if (which == 2 && recordId != null) {
                        host.confirmDeleteSession(recordId);
                    }
                });
    }

    private void startElapsedTicker(TextView elapsedView, String startedAt) {
        if (startedAt == null || startedAt.trim().isEmpty()) {
            elapsedView.setText("--:--:--");
            return;
        }

        final int generation = host.sessionState().generation();
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (generation != host.sessionState().generation()
                        || host.currentScreen() != FitnessScreen.WORKOUT_SESSION) {
                    return;
                }
                elapsedView.setText(FitnessUi.formatElapsed(FitnessRepository.elapsedSecondsFrom(startedAt)));
                elapsedView.postDelayed(this, 1000);
            }
        };
        tick.run();
    }
}
