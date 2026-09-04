package com.yeonsik.fitnessapp.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
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
        return volumeTrendCard(title, "최근 4회 + 현재", history, currentVolume, true);
    }

    protected View volumeTrendCard(String title, List<FitnessRepository.VolumePoint> history,
                                   double currentVolume, boolean includeCurrentPoint) {
        return volumeTrendCard(title, "최근 4회 + 현재", history, currentVolume,
                includeCurrentPoint);
    }

    protected View volumeTrendCard(String title, String metaLabel,
                                   List<FitnessRepository.VolumePoint> history, double currentVolume) {
        return volumeTrendCard(title, metaLabel, history, currentVolume, true);
    }

    /**
     * Renders persisted history separately from the current in-progress calculation.
     * An unfinished session must not become a false zero-valued historical point.
     */
    protected View volumeTrendCard(String title, String metaLabel,
                                   List<FitnessRepository.VolumePoint> history,
                                   double currentVolume,
                                   boolean includeCurrentPoint) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        List<Double> values = new ArrayList<>();
        if (history != null) {
            for (FitnessRepository.VolumePoint point : history) {
                if (point != null) {
                    values.add(point.volumeKg);
                }
            }
        }
        int currentPointIndex = -1;
        if (includeCurrentPoint) {
            currentPointIndex = values.size();
            values.add(currentVolume);
        }
        if (RecordsAnalysis.hasEnoughTrendPoints(values.size())) {
            String displayMeta = includeCurrentPoint
                    ? metaLabel
                    : "완료 기록 " + values.size() + "회 · 현재 진행 중 · kg";
            ui.cardHeader(card, title, displayMeta);
            card.addView(ui.volumeTrendChart(values, currentPointIndex), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(116)));
            return card;
        }

        ui.cardHeader(card, title, "추세 차트 대기");
        List<View> rows = new ArrayList<>();
        if (includeCurrentPoint) {
            rows.add(ui.recordListRow(
                    "현",
                    FitnessUi.formatVolume(currentVolume) + "kg",
                    "현재 완료 기록",
                    null
            ));
        } else {
            rows.add(ui.recordListRow(
                    "진",
                    "현재 세션",
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
                        FitnessUi.formatVolume(point.volumeKg) + "kg",
                        "완료 기록 · " + date,
                        null
                ));
            }
        }
        card.addView(ui.rowsCard(rows), ui.fullWidthParams(ui.dp(8)));
        int requiredCompletedRecords = RecordsAnalysis.MIN_TREND_POINTS
                - (includeCurrentPoint ? 1 : 0);
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
