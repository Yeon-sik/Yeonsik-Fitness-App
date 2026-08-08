package com.yeonsik.fitnessapp.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.config.SupabaseConfig;

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

        SupabaseConfig config = host.supabaseConfig();
        String accountStatus = config.isConfigured()
                ? "로그인됨"
                : config.isConnectionConfigured() ? "로그인 필요" : "설정 없음";

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

        statusCard.addView(ui.keyValue("Personal OS DB", accountStatus));
        statusCard.addView(ui.keyValue(
                "프로젝트",
                config.projectRef().isEmpty() ? "미설정" : config.projectRef()
        ));
        statusCard.addView(ui.keyValue(
                "운동·식사 기록",
                config.isConfigured() ? "공통 계정 적용" : "기기 저장"
        ));
        statusCard.addView(ui.keyValue(
                "영양 카탈로그",
                config.isConfigured()
                        ? "같은 계정 적용"
                        : config.isConnectionConfigured() ? "공개 항목만" : "기기 저장"
        ));
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
        String sharedConfigStatus = host.isSharedSupabaseConnectionManaged()
                ? "빌드 설정"
                : config.isConnectionConfigured()
                        ? "수동 대체 연결"
                        : "설정 필요";
        ui.cardHeader(configCard, "Personal OS 공통 DB 연결", sharedConfigStatus);
        TextView sharedConfigHint = ui.text(
                host.isSharedSupabaseConnectionManaged()
                        ? "CashOS·PersonalOSApp과 같은 Supabase 프로젝트를 사용합니다. 운동 기록과 영양 카탈로그 모두 이 연결을 공유합니다."
                        : "개발용 수동 대체 연결입니다. 이 값 하나가 운동 기록과 영양 카탈로그에 함께 적용됩니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        configCard.addView(sharedConfigHint);
        if (host.isSharedSupabaseConnectionManaged()) {
            configCard.addView(ui.keyValue("연결 원본", config.sourceLabel));
            configCard.addView(ui.keyValue("프로젝트", config.projectRef()));
        } else {
            EditText supabaseUrlInput = ui.input(
                    "Personal OS DB URL",
                    config.supabaseUrl
            );
            EditText supabaseAnonInput = ui.input(
                    "Personal OS DB anon key",
                    config.supabaseAnonKey
            );
            supabaseAnonInput.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            );
            configCard.addView(ui.fieldLabel("Personal OS DB URL"));
            configCard.addView(supabaseUrlInput, ui.fullWidthParams(0));
            configCard.addView(ui.fieldLabel("Personal OS DB anon key"));
            configCard.addView(supabaseAnonInput, ui.fullWidthParams(0));
            Button saveButton = ui.button(
                    "수동 대체 연결 저장",
                    true,
                    v -> host.saveSupabaseConfig(
                            FitnessUi.inputText(supabaseUrlInput),
                            FitnessUi.inputText(supabaseAnonInput)
                    )
            );
            configCard.addView(saveButton, ui.fullWidthParams(ui.dp(16)));
        }
        add(configCard);

        LinearLayout authCard = ui.card();
        ui.cardHeader(authCard, "Personal OS 공통 계정", accountStatus);
        TextView authHint = ui.text(
                "앱별 로그인 상태는 기기에 따로 저장됩니다. CashOS·PersonalOSApp과 같은 이메일로 로그인하면 같은 auth.uid()로 접근합니다. 영양 카탈로그용 추가 로그인은 없습니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        authCard.addView(authHint);
        if (config.isConfigured()) {
            authCard.addView(ui.keyValue("계정", config.email));
            Button signOutButton = ui.button("로그아웃", false, v -> host.signOutFromSupabase());
            authCard.addView(signOutButton, ui.fullWidthParams(ui.dp(12)));
        } else if (!config.isConnectionConfigured()) {
            authCard.addView(ui.text(
                    "공통 DB 설정이 없습니다. 개발 환경에서는 scripts/configure-shared-supabase.ps1을 실행하거나 위 수동 연결을 저장하세요.",
                    12,
                    FitnessUi.COLOR_MUTED,
                    false
            ));
        } else {
            EditText emailInput = ui.input("이메일", config.email);
            emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            EditText passwordInput = ui.input("비밀번호", "");
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            authCard.addView(ui.fieldLabel("이메일"));
            authCard.addView(emailInput, ui.fullWidthParams(0));
            authCard.addView(ui.fieldLabel("비밀번호"));
            authCard.addView(passwordInput, ui.fullWidthParams(0));
            Button signInButton = ui.button("로그인", true, v -> host.signInToSupabase(
                    FitnessUi.inputText(emailInput),
                    FitnessUi.inputText(passwordInput)
            ));
            authCard.addView(signInButton, ui.fullWidthParams(ui.dp(16)));
            Button signUpButton = ui.button("계정 만들기", false, v -> host.signUpToSupabase(
                    FitnessUi.inputText(emailInput),
                    FitnessUi.inputText(passwordInput)
            ));
            authCard.addView(signUpButton, ui.fullWidthParams(ui.dp(12)));
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
