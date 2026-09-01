package com.yeonsik.fitnessapp.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

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
        add(ui().labelView(eyebrow));
        add(ui().titleView(title));
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
        return volumeTrendCard(title, "최근 4회 + 현재", history, currentVolume);
    }

    protected View volumeTrendCard(String title, String metaLabel,
                                   List<FitnessRepository.VolumePoint> history, double currentVolume) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, title, metaLabel);
        List<Double> values = new ArrayList<>();
        for (FitnessRepository.VolumePoint point : history) {
            values.add(point.volumeKg);
        }
        values.add(currentVolume);
        card.addView(ui.volumeTrendChart(values), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(116)));
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
