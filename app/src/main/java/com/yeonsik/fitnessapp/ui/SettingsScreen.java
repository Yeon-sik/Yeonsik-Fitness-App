package com.yeonsik.fitnessapp.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 설정 탭: 연결 상태 카드(시맨틱 도트) + Supabase config 폼.
 * 동기화 실행과 config 저장은 host가 소유한다.
 */
public final class SettingsScreen extends BaseScreen {

    public SettingsScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        FitnessUi ui = ui();
        screenHeader("SETTINGS", "설정");

        LinearLayout statusCard = ui.card();
        LinearLayout statusHeader = new LinearLayout(host.activity());
        statusHeader.setOrientation(LinearLayout.HORIZONTAL);
        statusHeader.setGravity(Gravity.CENTER_VERTICAL);
        statusHeader.addView(ui.text("연결 상태", 17, FitnessUi.COLOR_TEXT, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusHeader.addView(ui.statusDotBadge(host.syncLabel(), syncStatusColor(), false));
        statusCard.addView(statusHeader);

        View line = ui.hairline(FitnessUi.COLOR_BORDER);
        LinearLayout.LayoutParams lineParams = ui.fullWidthParams(ui.dp(12));
        lineParams.height = ui.dp(1);
        statusCard.addView(line, lineParams);

        statusCard.addView(ui.keyValue("Supabase",
                host.supabaseConfig().isConfigured() ? "configured" : "local-only"));
        statusCard.addView(ui.keyValue("동기화", host.syncLabel()));
        statusCard.addView(ui.keyValue("사용자", host.repositoryUserLabel()));
        Button syncButton = ui.button(host.isManualSyncing() ? "수동 동기화 중" : "수동 동기화", false,
                v -> host.runManualSync());
        syncButton.setEnabled(!host.isManualSyncing());
        statusCard.addView(syncButton, ui.fullWidthParams(ui.dp(14)));
        if (!host.syncDetail().isEmpty()) {
            TextView detail = ui.text(host.syncDetail(), 12, FitnessUi.COLOR_MUTED, false);
            detail.setPadding(0, ui.dp(10), 0, 0);
            statusCard.addView(detail);
        }
        add(statusCard);

        LinearLayout configCard = ui.card();
        ui.cardHeader(configCard, "Supabase config", host.supabaseConfig().sourceLabel);
        EditText supabaseUrlInput = ui.input("Supabase URL", host.supabaseConfig().supabaseUrl);
        EditText supabaseAnonInput = ui.input("Anon key", host.supabaseConfig().supabaseAnonKey);
        supabaseAnonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText userIdInput = ui.input("User ID", host.supabaseConfig().userId);
        configCard.addView(ui.fieldLabel("Supabase URL"));
        configCard.addView(supabaseUrlInput, ui.fullWidthParams(0));
        configCard.addView(ui.fieldLabel("Anon key"));
        configCard.addView(supabaseAnonInput, ui.fullWidthParams(0));
        configCard.addView(ui.fieldLabel("User ID"));
        configCard.addView(userIdInput, ui.fullWidthParams(0));
        Button saveButton = ui.button("Save config", true, v -> host.saveSupabaseConfig(
                FitnessUi.inputText(supabaseUrlInput),
                FitnessUi.inputText(supabaseAnonInput),
                FitnessUi.inputText(userIdInput)
        ));
        configCard.addView(saveButton, ui.fullWidthParams(ui.dp(16)));
        add(configCard);
    }

    private int syncStatusColor() {
        String label = host.syncLabel();
        if ("synced".equals(label) || "configured".equals(label)) {
            return FitnessUi.COLOR_POSITIVE;
        }
        if ("sync failed".equals(label)) {
            return FitnessUi.COLOR_NEGATIVE;
        }
        if ("syncing".equals(label)) {
            return FitnessUi.COLOR_WARNING;
        }
        return FitnessUi.COLOR_TERTIARY;
    }
}
