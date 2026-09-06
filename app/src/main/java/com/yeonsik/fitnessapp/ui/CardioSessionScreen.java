package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot;
import com.yeonsik.fitnessapp.feature.cardio.ui.CardioSessionUiState;
import com.yeonsik.fitnessapp.state.FitnessScreen;

/** GPS 유산소 진행 화면. 값은 서비스가 저장한 SQLite 상태를 1초마다 읽어 표시한다. */
public final class CardioSessionScreen extends BaseScreen {
    private TextView elapsedValue;
    private TextView distanceValue;
    private TextView performanceValue;
    private TextView gpsValue;
    private String renderedRecordId;
    private String renderedStatus;

    public CardioSessionScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String recordId = host.sessionState().activeRecordId();
        CardioSessionUiState state = host.cardioSessionViewModel().getUiState().getValue();
        if (!(state instanceof CardioSessionUiState.Ready)) {
            clearRenderedSnapshot();
            screenHeader("실시간 기록", "유산소 기록");
            emptyState("진행 중인 GPS 유산소 기록을 불러오는 중입니다.", null);
            return;
        }
        CardioSessionUiState.Ready ready = (CardioSessionUiState.Ready) state;
        CardioSessionSnapshot snapshot = ready.getSession();
        if (!host.currentOwnerId().equals(ready.getOwnerId())
                || !snapshot.getRecordId().equals(recordId)) {
            clearRenderedSnapshot();
            screenHeader("실시간 기록", "유산소 기록");
            emptyState("진행 중인 GPS 유산소 기록을 불러오는 중입니다.", null);
            return;
        }
        renderedRecordId = snapshot.getRecordId();
        renderedStatus = snapshot.getStatus();

        screenHeader("실시간 기록", snapshot.getActivityLabel());
        add(statusCard(snapshot));

        LinearLayout firstRow = ui().tileRow();
        elapsedValue = metricValue("00:00:00");
        distanceValue = metricValue("0.00");
        firstRow.addView(metricTile("운동 시간", elapsedValue, "일시정지 제외"), ui().tileParams(true));
        firstRow.addView(metricTile("이동 거리", distanceValue, "km"), ui().tileParams(false));
        add(firstRow, ui().fullWidthParams(0));

        LinearLayout secondRow = ui().tileRow();
        performanceValue = metricValue("--:--");
        gpsValue = metricValue("GPS 찾는 중");
        String performanceLabel = "cycling".equals(snapshot.getActivityId())
                ? "평균 속도" : "평균 페이스";
        String performanceUnit = "cycling".equals(snapshot.getActivityId())
                ? "km/h" : "분/km";
        secondRow.addView(metricTile(performanceLabel, performanceValue, performanceUnit),
                ui().tileParams(true));
        secondRow.addView(metricTile("위치 상태", gpsValue, "정확도 35m 이내 반영"),
                ui().tileParams(false));
        add(secondRow, ui().fullWidthParams(ui().dp(10)));

        section("평균 심박수");
        emptyState(
                "운동을 완료할 때 평균 심박수(bpm)를 직접 입력합니다.",
                "시계·밴드 측정값이 없으면 비워두고 나중에 결과 화면에서 입력할 수 있습니다."
        );

        section("운동 제어");
        boolean paused = CardioSessionSnapshot.STATUS_PAUSED.equals(snapshot.getStatus());
        buttonRow(
                ui().button(paused ? "GPS 추적 재개" : "일시정지", false,
                        v -> {
                            if (paused) {
                                host.resumeCardioWorkout();
                            } else {
                                host.pauseCardioWorkout();
                            }
                        }),
                ui().button("운동 완료", true, v -> host.finishCardioWorkout()),
                0
        );
        add(ui().button("기록 취소", false, v -> host.cancelCardioWorkout()),
                ui().fullWidthParams(ui().dp(8)));
        add(ui().button("유산소 화면 보기 · 추적은 계속", false,
                        v -> host.navigate(FitnessScreen.CARDIO)),
                ui().fullWidthParams(ui().dp(8)));

        section("측정 기준");
        emptyState(
                "정확도 35m 이내의 GPS 위치만 거리로 계산합니다.",
                "작은 흔들림과 운동 유형에 맞지 않는 순간 이동은 자동 제외됩니다."
        );

        updateMetrics(snapshot);
        startTicker(recordId);
    }

    public boolean hasRenderedSnapshot(String recordId, String status) {
        return recordId.equals(renderedRecordId) && status.equals(renderedStatus);
    }

    private void clearRenderedSnapshot() {
        renderedRecordId = null;
        renderedStatus = null;
    }

    private LinearLayout statusCard(CardioSessionSnapshot snapshot) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(ui.caption(
                CardioSessionSnapshot.STATUS_PAUSED.equals(snapshot.getStatus()) ? "일시정지" : "기록 중",
                CardioSessionSnapshot.STATUS_PAUSED.equals(snapshot.getStatus())
                        ? FitnessUi.COLOR_WARNING : FitnessUi.COLOR_POSITIVE
        ));
        TextView title = ui.text(
                CardioSessionSnapshot.STATUS_PAUSED.equals(snapshot.getStatus())
                        ? "기록이 일시정지되었습니다" : "GPS로 이동 거리를 기록 중입니다",
                18,
                FitnessUi.COLOR_TEXT,
                true
        );
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, ui.dp(8), 0, 0);
        card.addView(title);
        return card;
    }

    private LinearLayout metricTile(String label, TextView value, String hint) {
        FitnessUi ui = ui();
        LinearLayout tile = new LinearLayout(host.activity());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        tile.setBackground(ui.borderDrawable(ui.surface(), ui.border(), ui.dp(16)));
        ui.applyDepth(tile, FitnessUi.DEPTH_EMPHASIS_DP);
        tile.addView(ui.caption(label, FitnessUi.COLOR_MUTED));
        value.setPadding(0, ui.dp(6), 0, 0);
        tile.addView(value);
        TextView hintView = ui.text(hint, 11, FitnessUi.COLOR_MUTED, false);
        hintView.setPadding(0, ui.dp(4), 0, 0);
        tile.addView(hintView);
        return tile;
    }

    private TextView metricValue(String initialValue) {
        return ui().num(initialValue, 21, FitnessUi.COLOR_TEXT, true);
    }

    private void updateMetrics(CardioSessionSnapshot snapshot) {
        if (elapsedValue == null || distanceValue == null
                || performanceValue == null || gpsValue == null) {
            return;
        }
        int elapsedSeconds = snapshot.elapsedSeconds(System.currentTimeMillis());
        elapsedValue.setText(CardioMetrics.formatElapsed(elapsedSeconds));
        distanceValue.setText(CardioMetrics.formatDistanceKilometers(snapshot.getDistanceMeters()));
        performanceValue.setText("cycling".equals(snapshot.getActivityId())
                ? CardioMetrics.formatAverageSpeed(elapsedSeconds, snapshot.getDistanceMeters())
                : CardioMetrics.formatAveragePace(elapsedSeconds, snapshot.getDistanceMeters()));
        gpsValue.setText(CardioMetrics.gpsStatusLabel(snapshot.getGpsStatus()));
    }

    private void startTicker(String recordId) {
        int generation = host.sessionState().generation();
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (generation != host.sessionState().generation()
                        || host.currentScreen() != FitnessScreen.CARDIO_SESSION) {
                    return;
                }
                CardioSessionUiState state = host.cardioSessionViewModel().getUiState().getValue();
                if (!(state instanceof CardioSessionUiState.Ready)) {
                    return;
                }
                CardioSessionSnapshot snapshot =
                        ((CardioSessionUiState.Ready) state).getSession();
                if (CardioSessionSnapshot.STATUS_COMPLETED.equals(snapshot.getStatus())) {
                    host.openCardioSummary(recordId);
                    return;
                }
                updateMetrics(snapshot);
                host.cardioSessionViewModel().refresh(
                        new AccountScope(host.currentOwnerId()),
                        recordId
                );
                content().postDelayed(this, 1_000L);
            }
        };
        tick.run();
    }
}
