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

        renderThemeCard();
        renderDataImportCard();

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
        configCard.addView(ui.fieldLabel("Supabase URL"));
        configCard.addView(supabaseUrlInput, ui.fullWidthParams(0));
        configCard.addView(ui.fieldLabel("Anon key"));
        configCard.addView(supabaseAnonInput, ui.fullWidthParams(0));
        Button saveButton = ui.button("Save config", true, v -> host.saveSupabaseConfig(
                FitnessUi.inputText(supabaseUrlInput),
                FitnessUi.inputText(supabaseAnonInput)
        ));
        configCard.addView(saveButton, ui.fullWidthParams(ui.dp(16)));
        add(configCard);

        LinearLayout authCard = ui.card();
        ui.cardHeader(
                authCard,
                "Supabase Auth",
                host.supabaseConfig().isConfigured() ? "authenticated" : "login required"
        );
        if (host.supabaseConfig().isConfigured()) {
            authCard.addView(ui.keyValue("Account", host.supabaseConfig().email));
            Button signOutButton = ui.button("Sign out", false, v -> host.signOutFromSupabase());
            authCard.addView(signOutButton, ui.fullWidthParams(ui.dp(12)));
        } else {
            EditText emailInput = ui.input("Email", host.supabaseConfig().email);
            emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            EditText passwordInput = ui.input("Password", "");
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            authCard.addView(ui.fieldLabel("Email"));
            authCard.addView(emailInput, ui.fullWidthParams(0));
            authCard.addView(ui.fieldLabel("Password"));
            authCard.addView(passwordInput, ui.fullWidthParams(0));
            Button signInButton = ui.button("Sign in", true, v -> host.signInToSupabase(
                    FitnessUi.inputText(emailInput),
                    FitnessUi.inputText(passwordInput)
            ));
            authCard.addView(signInButton, ui.fullWidthParams(ui.dp(16)));
        }
        add(authCard);
    }

    private void renderDataImportCard() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "데이터 가져오기", host.isDataImporting() ? "가져오는 중" : "FLEEK CSV");

        TextView description = ui.text(
                "FLEEK에서 내보낸 CSV의 날짜·운동·중량·횟수를 로컬 기록으로 가져옵니다. "
                        + "맨몸·보조 운동은 앱 기록 유형으로 변환하고, 같은 파일을 다시 선택하면 기존 세션은 건너뜁니다.",
                13,
                FitnessUi.COLOR_MUTED,
                false
        );
        card.addView(description);

        if (!host.dataImportDetail().isEmpty()) {
            TextView detail = ui.text(host.dataImportDetail(), 12, FitnessUi.COLOR_TERTIARY, false);
            detail.setPadding(0, ui.dp(10), 0, 0);
            card.addView(detail);
        }

        Button importButton = ui.button(
                host.isDataImporting() ? "가져오는 중" : "FLEEK CSV 선택",
                true,
                v -> host.openFleekDataImport()
        );
        importButton.setEnabled(!host.isDataImporting());
        card.addView(importButton, ui.fullWidthParams(ui.dp(14)));
        add(card);
    }

    /** 화면 모드: 화이트(기본) / 다크 / 시스템 설정. 선택은 반전 칩으로 표현한다. */
    private void renderThemeCard() {
        FitnessUi ui = ui();
        LinearLayout themeCard = ui.card();
        ui.cardHeader(themeCard, "화면 모드", themeModeLabel(host.themeMode()));

        String[] modes = {"light", "dark", "system"};
        LinearLayout chipRow = new LinearLayout(host.activity());
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            Button chip = ui.filterButton(themeModeLabel(mode));
            ui.styleFilterButton(chip, mode.equals(host.themeMode()));
            chip.setOnClickListener(v -> host.setThemeMode(mode));
            chipRow.addView(chip, ui.pickerCellParams(i == 0));
        }
        themeCard.addView(chipRow, ui.fullWidthParams(ui.dp(12)));

        TextView hint = ui.text("시스템 설정은 기기의 다크 모드 설정을 따릅니다.", 12, FitnessUi.COLOR_TERTIARY, false);
        hint.setPadding(0, ui.dp(10), 0, 0);
        themeCard.addView(hint);
        add(themeCard);
    }

    private String themeModeLabel(String mode) {
        if ("dark".equals(mode)) {
            return "다크";
        }
        if ("system".equals(mode)) {
            return "시스템 설정";
        }
        return "화이트";
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
