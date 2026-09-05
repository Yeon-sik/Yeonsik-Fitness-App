package com.yeonsik.fitnessapp.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MassFormatter;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * 화면 렌더러 공통 베이스. host/ui 접근과 반복되는 조립 패턴만 제공한다.
 */
public abstract class BaseScreen {
    protected final ScreenHost host;

    protected BaseScreen(ScreenHost host) {
        this.host = host;
    }

    protected FitnessUi ui() {
        return host.ui();
    }

    protected LinearLayout content() {
        return host.content();
    }

    protected FitnessRepository repository() {
        return host.repository();
    }

    protected void add(View view) {
        host.content().addView(view);
    }

    protected void add(View view, ViewGroup.LayoutParams params) {
        host.content().addView(view, params);
    }

    protected void screenHeader(String eyebrow, String title) {
        add(ui().screenHeader(eyebrow, title));
    }

    protected void section(String label) {
        add(ui().sectionHeader(label, null, null));
    }

    protected void section(String label, String actionText, Runnable action) {
        add(ui().sectionHeader(label, actionText, action));
    }

    protected void emptyState(String message, String hint) {
        add(ui().emptyStateCard(message, hint));
    }

    protected void buttonRow(View first, View second, int topMargin) {
        add(ui().buttonRow(first, second), ui().fullWidthParams(topMargin));
    }

    /** Returns to the previous history entry, retaining a safe destination for fallback hosts. */
    protected void backOr(FitnessScreen fallback) {
        if (!host.back() && fallback != null) {
            host.replace(fallback);
        }
    }

    protected View volumeTrendCard(String title, List<FitnessRepository.VolumePoint> history, double currentVolume) {
        return volumeTrendCard(
                title,
                "최근 4회 + 현재",
                history,
                currentVolume,
                RecordsAnalysis.TrendCurrentState.COMPLETED,
                null
        );
    }

    protected View volumeTrendCard(String title, List<FitnessRepository.VolumePoint> history,
                                   double currentVolume, boolean includeCurrentPoint) {
        return volumeTrendCard(
                title,
                "최근 4회 + 현재",
                history,
                currentVolume,
                includeCurrentPoint
                        ? RecordsAnalysis.TrendCurrentState.COMPLETED
                        : RecordsAnalysis.TrendCurrentState.IN_PROGRESS,
                null
        );
    }

    protected View volumeTrendCard(String title, List<FitnessRepository.VolumePoint> history,
                                   double currentVolume,
                                   RecordsAnalysis.TrendCurrentState currentState) {
        return volumeTrendCard(title, null, history, currentVolume, currentState, null);
    }

    protected View volumeTrendCard(String title, List<FitnessRepository.VolumePoint> history,
                                   double currentVolume,
                                   RecordsAnalysis.TrendCurrentState currentState,
                                   MassUnit displayUnit) {
        return volumeTrendCard(
                title,
                null,
                history,
                currentVolume,
                currentState,
                null,
                displayUnit
        );
    }

    protected View volumeTrendCard(String title, String metaLabel,
                                   List<FitnessRepository.VolumePoint> history, double currentVolume) {
        return volumeTrendCard(
                title,
                metaLabel,
                history,
                currentVolume,
                RecordsAnalysis.TrendCurrentState.COMPLETED,
                null
        );
    }

    /**
     * Keeps completed history, a saved completed current record, and an in-progress value
     * semantically separate. The latter is never appended to the persisted polyline.
     */
    protected View volumeTrendCard(String title, String metaLabel,
                                   List<FitnessRepository.VolumePoint> history,
                                   double currentVolume,
                                   RecordsAnalysis.TrendCurrentState currentState,
                                   String currentDate) {
        return volumeTrendCard(
                title,
                metaLabel,
                history,
                currentVolume,
                currentState,
                currentDate,
                MassUnit.KG
        );
    }

    protected View volumeTrendCard(String title, String metaLabel,
                                   List<FitnessRepository.VolumePoint> history,
                                   double currentVolume,
                                   RecordsAnalysis.TrendCurrentState currentState,
                                   String currentDate,
                                   MassUnit displayUnit) {
        FitnessUi ui = ui();
        MassUnit unit = MassUnit.orDefault(displayUnit);
        LinearLayout card = ui.card();
        List<Double> values = new ArrayList<>();
        List<String> historyDates = new ArrayList<>();
        if (history != null) {
            for (FitnessRepository.VolumePoint point : history) {
                if (point != null) {
                    values.add(MassUnit.fromKg(point.volumeKg, unit));
                    historyDates.add(point.date);
                }
            }
        }
        RecordsAnalysis.TrendCurrentState state = currentState == null
                ? RecordsAnalysis.TrendCurrentState.NONE
                : currentState;
        boolean includeCompletedCurrent = state == RecordsAnalysis.TrendCurrentState.COMPLETED;
        int completedHistoryCount = values.size();
        int currentPointIndex = -1;
        if (includeCompletedCurrent) {
            currentPointIndex = values.size();
            values.add(MassUnit.fromKg(currentVolume, unit));
        }
        if (RecordsAnalysis.hasEnoughTrendPoints(completedHistoryCount, state)) {
            String period = RecordsAnalysis.trendPeriodLabel(
                    historyDates,
                    includeCompletedCurrent ? currentDate : null
            );
            String stateLabel = state == RecordsAnalysis.TrendCurrentState.IN_PROGRESS
                    ? " · 현재 진행 중"
                    : "";
            String displayMeta = period.isEmpty()
                    ? "완료 기록 " + values.size() + "회 · " + unit.symbol() + stateLabel
                    : period + " · " + unit.symbol() + stateLabel;
            ui.cardHeader(card, title, displayMeta);
            card.addView(
                    unit == MassUnit.KG
                            ? ui.volumeTrendChart(values, currentPointIndex)
                            : ui.trendChart(
                                    values,
                                    unit.symbol(),
                                    RecordsAnalysis.TrendScalePolicy.ZERO_BASED,
                                    currentPointIndex,
                                    "추세를 표시할 기록이 없습니다."
                            ),
                    ui.trendChartParams(0)
            );
            if (state == RecordsAnalysis.TrendCurrentState.IN_PROGRESS) {
                card.addView(ui.recordListRow(
                        "진",
                        MassFormatter.withUnit(currentVolume, unit),
                        "진행 중 · 저장된 추세에는 포함하지 않음",
                        null
                ), ui.fullWidthParams(ui.dp(8)));
            }
            return card;
        }

        ui.cardHeader(card, title, "추세 차트 대기");
        List<View> rows = new ArrayList<>();
        if (state == RecordsAnalysis.TrendCurrentState.COMPLETED) {
            rows.add(ui.recordListRow(
                    "현",
                    MassFormatter.withUnit(currentVolume, unit),
                    "현재 완료 기록",
                    null
            ));
        } else if (state == RecordsAnalysis.TrendCurrentState.IN_PROGRESS) {
            rows.add(ui.recordListRow(
                    "진",
                    MassFormatter.withUnit(currentVolume, unit),
                    "진행 중 · 저장된 추세에는 포함하지 않음",
                    null
            ));
        }
        if (history != null) {
            for (int index = history.size() - 1; index >= 0; index--) {
                FitnessRepository.VolumePoint point = history.get(index);
                if (point == null) {
                    continue;
                }
                String date = point.date == null || point.date.trim().isEmpty()
                        ? "날짜 없음"
                        : point.date;
                rows.add(ui.recordListRow(
                    "완",
                    MassFormatter.withUnit(point.volumeKg, unit),
                        "완료 기록 · " + date,
                        null
                ));
            }
        }
        if (rows.isEmpty()) {
            rows.add(ui.recordListRow(
                    "—",
                    "완료 기록 없음",
                    "저장된 완료 기록이 없습니다.",
                    null
            ));
        }
        card.addView(ui.rowsCard(rows), ui.fullWidthParams(ui.dp(8)));
        int requiredCompletedRecords = RecordsAnalysis.requiredCompletedHistoryPoints(state);
        TextView helper = ui.text(
                "완료 기록이 " + requiredCompletedRecords
                        + "회 이상 쌓이면 기간과 단위가 표시된 추세 차트를 보여줍니다.",
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        helper.setPadding(0, ui.dp(10), 0, 0);
        helper.setLineSpacing(ui.dp(3), 1f);
        card.addView(helper);
        return card;
    }

    /** 현재 화면 상태에 맞춰 content에 뷰를 그린다. content는 이미 비워져 있다. */
    public abstract void render();

    /** 화면이 현재 content에 연결된 직후 호출된다. */
    public void onVisible() {
    }

    /** 다른 화면으로 전환되기 직전에 호출된다. */
    public void onHidden() {
    }

    /** Activity가 재개될 때 호출된다. */
    public void onResume() {
    }

    /** Activity가 일시정지될 때 호출된다. */
    public void onPause() {
    }

    /** Activity가 파괴될 때 호출된다. */
    public void onDestroy() {
    }

    /** 시스템 메모리 부족 알림을 전달한다. */
    public void onLowMemory() {
    }
}
