package com.yeonsik.fitnessapp.ui;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.state.WorkoutSessionState;

import java.util.List;

/**
 * 운동 세션 화면: 경과시간 히어로 + 메트릭 스트립 + 종목 진행 카드.
 */
public final class WorkoutSessionScreen extends BaseScreen {

    public WorkoutSessionScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String recordId = host.sessionState().activeRecordId();
        if (recordId == null) {
            host.navigate(FitnessScreen.WORKOUT);
            return;
        }

        FitnessUi ui = ui();
        FitnessRepository.SessionInfo info = repository().sessionInfo(recordId);
        FitnessRepository.SessionMetrics metrics = repository().sessionMetrics(recordId);
        boolean inProgress = !"completed".equals(info.status);

        screenHeader("WORKOUT SESSION", info.title.isEmpty() ? "운동 중" : info.title);

        LinearLayout status = ui.card();
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.addView(ui.caption("경과 시간", FitnessUi.COLOR_MUTED));
        TextView elapsedView = new TextView(host.activity());
        elapsedView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        elapsedView.setTextSize(42);
        elapsedView.setTextColor(FitnessUi.COLOR_TEXT);
        elapsedView.setGravity(Gravity.CENTER);
        elapsedView.setPadding(0, ui.dp(6), 0, 0);
        elapsedView.setText("00:00:00");
        status.addView(elapsedView, ui.fullWidthParams(0));

        View line = ui.hairline(FitnessUi.COLOR_BORDER);
        LinearLayout.LayoutParams lineParams = ui.fullWidthParams(ui.dp(14));
        lineParams.height = ui.dp(1);
        status.addView(line, lineParams);

        LinearLayout strip = new LinearLayout(host.activity());
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(0, ui.dp(12), 0, 0);
        TextView volumeView = sessionMetricCell(strip, "총 볼륨", true);
        TextView completedSetsView = sessionMetricCell(strip, "완료 세트", false);
        TextView startView = sessionMetricCell(strip, "시작", false);
        strip.setGravity(Gravity.CENTER);
        status.addView(strip, ui.fullWidthParams(0));

        volumeView.setText(FitnessUi.formatVolume(metrics.totalVolumeKg) + "kg");
        completedSetsView.setText(metrics.setCount + "개");
        startView.setText(FitnessUi.formatStartTime(info.startedAt));
        add(status);

        if (inProgress) {
            startElapsedTicker(elapsedView, info.startedAt);
        } else {
            elapsedView.setText(info.durationSeconds > 0
                    ? FitnessUi.formatElapsed(info.durationSeconds) : "--:--:--");
        }

        add(volumeTrendCard("최근 4회 총 볼륨",
                repository().recentSessionVolumes(recordId, 4), metrics.totalVolumeKg));

        section("운동 구성");
        List<FitnessRepository.SessionExerciseEntry> exercises = repository().sessionExerciseEntries(recordId);
        if (exercises.isEmpty()) {
            emptyState("아직 종목이 없습니다.", "종목 추가 버튼으로 시작하세요.");
            return;
        }

        for (FitnessRepository.SessionExerciseEntry exercise : exercises) {
            workoutExerciseCard(recordId, exercise);
        }
    }

    private void openExercisePicker() {
        String recordId = host.currentWorkoutRecordId();
        if (recordId == null) {
            host.toast("먼저 운동을 시작하세요.");
            return;
        }
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
        card.setOnClickListener(v -> {
            host.sessionState().setActiveExerciseId(exercise.id);
            host.navigate(FitnessScreen.WORKOUT_EXERCISE_DETAIL);
        });

        LinearLayout headerRow = new LinearLayout(host.activity());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.addView(ui.compactOrderBadge(exercise.orderIndex));
        LinearLayout titleColumn = new LinearLayout(host.activity());
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setPadding(ui.dp(8), 0, 0, 0);
        titleColumn.addView(ui.text(exercise.name, 13, FitnessUi.COLOR_TEXT, true));
        TextView meta = ui.text(exercise.uiPart + (exercise.equipment.isEmpty() ? "" : " · " + exercise.equipment),
                10, FitnessUi.COLOR_MUTED, false);
        meta.setPadding(0, ui.dp(2), 0, 0);
        titleColumn.addView(meta);
        headerRow.addView(titleColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
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
        repository().finishSession(recordId);
        host.toast("운동을 완료했습니다.");
        host.navigate(FitnessScreen.WORKOUT_SUMMARY);
    }

    private void showLeaveSessionDialog() {
        String recordId = host.sessionState().activeRecordId();
        new AlertDialog.Builder(host.activity())
                .setTitle("운동 나가기")
                .setItems(new String[]{"계속 운동하기", "임시 저장하고 나가기", "기록 삭제하고 나가기"}, (dialog, which) -> {
                    if (which == 1) {
                        host.navigate(FitnessScreen.WORKOUT);
                        host.toast("임시 저장했습니다. 진행 중 운동에서 이어할 수 있습니다.");
                    } else if (which == 2 && recordId != null) {
                        host.confirmDeleteSession(recordId);
                    }
                })
                .show();
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
