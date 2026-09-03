package com.yeonsik.fitnessapp.ui;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;

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

        FitnessUi ui = ui();
        FitnessRepository.SessionInfo info = repository().sessionInfo(recordId);
        FitnessRepository.SessionMetrics metrics = repository().sessionMetrics(recordId);
        List<FitnessRepository.SessionExerciseEntry> exercises =
                repository().sessionExerciseEntries(recordId);
        boolean inProgress = !"completed".equals(info.status);
        boolean manualEntry = inProgress && info.durationSeconds > 0;

        screenHeader(manualEntry ? "수동 등록" : "진행 중",
                info.title.isEmpty() ? "운동 중" : info.title);

        FitnessRepository.SessionExerciseEntry currentExercise = exercises.isEmpty()
                ? null
                : WorkoutSessionState.findActiveExercise(
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

        volumeView.setText(FitnessUi.formatVolume(metrics.totalVolumeKg) + "kg");
        completedSetsView.setText(metrics.setCount + "개");
        startView.setText(FitnessUi.formatStartTime(info.startedAt));
        add(sessionSummary);

        if (inProgress && !manualEntry) {
            startElapsedTicker(elapsedView, info.startedAt);
        } else {
            elapsedView.setText(info.durationSeconds > 0
                    ? FitnessUi.formatElapsed(info.durationSeconds) : "--:--:--");
        }

        if (exercises.isEmpty()) {
            section("운동 구성");
            emptyState("아직 종목이 없습니다.", "종목 추가 버튼으로 시작하세요.");
            add(volumeTrendCard(
                    "최근 4회 총 볼륨",
                    repository().recentSessionVolumes(recordId, 4),
                    metrics.totalVolumeKg,
                    false
            ));
            return;
        }

        section("운동 구성");
        for (FitnessRepository.SessionExerciseEntry exercise : exercises) {
            workoutExerciseCard(recordId, exercise);
        }

        add(volumeTrendCard(
                "최근 4회 총 볼륨",
                repository().recentSessionVolumes(recordId, 4),
                metrics.totalVolumeKg,
                metrics.setCount > 0
        ));
    }

    private void currentExerciseCard(
            String recordId,
            FitnessRepository.SessionExerciseEntry exercise
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

        TextView name = ui.text(exercise.name, 20, ui.tonalInk(), true);
        name.setPadding(0, ui.dp(8), 0, 0);
        card.addView(name);

        List<FitnessRepository.SessionSetEntry> sets = repository().setsForExercise(exercise.id);
        int completed = WorkoutSessionState.completedSetCount(sets);
        String progress = sets.isEmpty()
                ? "첫 세트를 기록하세요"
                : completed < sets.size()
                        ? "세트 " + (completed + 1) + " 기록 · " + completed + "/" + sets.size() + " 완료"
                        : "모든 세트 완료 · 다음 종목을 선택하세요";
        TextView progressView = ui.text(progress, 13, ui.tonalInk(), false);
        progressView.setPadding(0, ui.dp(3), 0, 0);
        card.addView(progressView);

        card.addView(ui.tonalButton("세트 기록 열기", v -> {
                    host.sessionState().setActiveExerciseId(exercise.id);
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

    private void workoutExerciseCard(String recordId, FitnessRepository.SessionExerciseEntry exercise) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(12), ui.dp(7), ui.dp(12), ui.dp(7));
        card.setClickable(true);
        card.setFocusable(true);
        ui.pressFeedback(card);
        card.setOnClickListener(v -> {
            host.sessionState().setActiveExerciseId(exercise.id);
            host.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL);
        });

        LinearLayout headerRow = new LinearLayout(host.activity());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        ExerciseCardRenderer.Content content =
                ExerciseCardRenderer.Content.fromSessionExercise(
                        exercise,
                        host.exerciseMasterRepository().getExerciseById(exercise.exerciseId)
                );
        exerciseCardRenderer.addContent(headerRow, content, false, false);
        TextView chevron = ui.text("›", 16, FitnessUi.COLOR_TERTIARY, false);
        headerRow.addView(chevron);
        card.addView(headerRow);

        List<FitnessRepository.SessionSetEntry> summarySets = repository().setsForExercise(exercise.id);
        int completed = WorkoutSessionState.completedSetCount(summarySets);

        LinearLayout progressRow = new LinearLayout(host.activity());
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressRow.setPadding(0, ui.dp(4), 0, 0);
        double ratio = summarySets.isEmpty() ? 0 : (double) completed / summarySets.size();
        View progress = ui.progressBar(ratio, false);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(0, ui.dp(6), 1f);
        progressRow.addView(progress, progressParams);
        TextView progressText = ui.num(summarySets.isEmpty()
                ? "세트 없음"
                : completed + "/" + summarySets.size() + " 세트", 10, FitnessUi.COLOR_MUTED, true);
        progressText.setPadding(ui.dp(8), 0, 0, 0);
        progressRow.addView(progressText);
        card.addView(progressRow);

        add(card);
    }

    private void finishActiveWorkout() {
        String recordId = host.sessionState().activeRecordId();
        if (recordId == null) {
            host.toast("진행 중인 운동을 찾지 못했습니다.");
            return;
        }
        if (!repository().hasCompletedWorkout(recordId)) {
            host.toast("완료된 세트가 1개 이상 필요합니다.");
            return;
        }
        repository().finishSession(recordId);
        host.toast("운동을 완료했습니다.");
        host.replace(FitnessScreen.WORKOUT_SUMMARY);
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
