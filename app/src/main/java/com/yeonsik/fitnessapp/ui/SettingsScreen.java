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

        String sharedDbStatus = host.supabaseConfig().isConfigured()
                ? "로그인됨"
                : host.supabaseConfig().isConnectionConfigured() ? "로그인 필요" : "연결 없음";
        String nutritionDbStatus = host.nutritionSupabaseConfig().isConfigured()
                ? "로그인됨"
                : host.nutritionSupabaseConfig().isConnectionConfigured()
                        ? "로그인 필요"
                        : "연결 없음";
        statusCard.addView(ui.keyValue("공통 DB", sharedDbStatus));
        statusCard.addView(ui.keyValue("Nutrition DB", nutritionDbStatus));
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
                ? "앱 관리"
                : host.supabaseConfig().isConnectionConfigured()
                        ? "수동 대체 연결"
                        : "설정 필요";
        ui.cardHeader(configCard, "공통 DB 연결", sharedConfigStatus);
        TextView sharedConfigHint = ui.text(
                host.isSharedSupabaseConnectionManaged()
                        ? "CashOS·PersonalOSApp과 공유하는 연결입니다. URL과 anon key는 앱 빌드 설정에서 관리되며 이 화면에서 변경할 수 없습니다."
                        : "이 빌드에 공통 DB 설정이 없을 때만 사용하는 수동 대체 연결입니다. 배포 빌드는 local.properties 또는 환경변수로 관리하세요.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        configCard.addView(sharedConfigHint);
        if (host.isSharedSupabaseConnectionManaged()) {
            configCard.addView(ui.keyValue("연결 설정", "빌드 설정 적용됨"));
        } else {
            EditText supabaseUrlInput = ui.input(
                    "공통 DB URL",
                    host.supabaseConfig().supabaseUrl
            );
            EditText supabaseAnonInput = ui.input(
                    "공통 DB anon key",
                    host.supabaseConfig().supabaseAnonKey
            );
            supabaseAnonInput.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            );
            configCard.addView(ui.fieldLabel("공통 DB URL"));
            configCard.addView(supabaseUrlInput, ui.fullWidthParams(0));
            configCard.addView(ui.fieldLabel("공통 DB anon key"));
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

        LinearLayout nutritionConfigCard = ui.card();
        ui.cardHeader(
                nutritionConfigCard,
                "Nutrition DB 연결",
                "FitnessApp 전용 · 수동 관리"
        );
        TextView nutritionHint = ui.text(
                "공통 DB와 별개인 영양 정보 저장소입니다. URL·anon key와 로그인 세션을 독립적으로 관리합니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        nutritionConfigCard.addView(nutritionHint);
        EditText nutritionUrlInput = ui.input(
                "Nutrition DB URL",
                host.nutritionSupabaseConfig().supabaseUrl
        );
        EditText nutritionAnonInput = ui.input(
                "Nutrition DB anon key",
                host.nutritionSupabaseConfig().supabaseAnonKey
        );
        nutritionAnonInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        nutritionConfigCard.addView(ui.fieldLabel("Nutrition DB URL"));
        nutritionConfigCard.addView(nutritionUrlInput, ui.fullWidthParams(0));
        nutritionConfigCard.addView(ui.fieldLabel("Nutrition DB anon key"));
        nutritionConfigCard.addView(nutritionAnonInput, ui.fullWidthParams(0));
        Button nutritionSaveButton = ui.button(
                "Nutrition DB 연결 저장",
                true,
                v -> host.saveNutritionSupabaseConfig(
                        FitnessUi.inputText(nutritionUrlInput),
                        FitnessUi.inputText(nutritionAnonInput)
                )
        );
        nutritionConfigCard.addView(nutritionSaveButton, ui.fullWidthParams(ui.dp(16)));
        add(nutritionConfigCard);

        LinearLayout authCard = ui.card();
        ui.cardHeader(
                authCard,
                "공통 DB 로그인",
                host.supabaseConfig().isConfigured()
                        ? "로그인됨"
                        : host.supabaseConfig().isConnectionConfigured()
                                ? "최초 로그인 필요"
                                : "연결 설정 필요"
        );
        if (host.supabaseConfig().isConfigured()) {
            authCard.addView(ui.keyValue("계정", host.supabaseConfig().email));
            Button signOutButton = ui.button("공통 DB 로그아웃", false, v -> host.signOutFromSupabase());
            authCard.addView(signOutButton, ui.fullWidthParams(ui.dp(12)));
        } else if (!host.supabaseConfig().isConnectionConfigured()) {
            authCard.addView(ui.text(
                    "공통 DB 연결을 먼저 설정해야 로그인할 수 있습니다.",
                    12,
                    FitnessUi.COLOR_MUTED,
                    false
            ));
        } else {
            EditText emailInput = ui.input("이메일", host.supabaseConfig().email);
            emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            EditText passwordInput = ui.input("비밀번호", "");
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            authCard.addView(ui.fieldLabel("이메일"));
            authCard.addView(emailInput, ui.fullWidthParams(0));
            authCard.addView(ui.fieldLabel("비밀번호"));
            authCard.addView(passwordInput, ui.fullWidthParams(0));
            Button signInButton = ui.button("공통 DB 로그인", true, v -> host.signInToSupabase(
                    FitnessUi.inputText(emailInput),
                    FitnessUi.inputText(passwordInput)
            ));
            authCard.addView(signInButton, ui.fullWidthParams(ui.dp(16)));
            Button signUpButton = ui.button("공통 DB 계정 만들기", false, v -> host.signUpToSupabase(
                    FitnessUi.inputText(emailInput),
                    FitnessUi.inputText(passwordInput)
            ));
            authCard.addView(signUpButton, ui.fullWidthParams(ui.dp(12)));
        }
        add(authCard);

        LinearLayout nutritionAuthCard = ui.card();
        String nutritionAuthStatus = host.nutritionSupabaseConfig().isConfigured()
                ? "로그인됨"
                : host.nutritionSupabaseConfig().isConnectionConfigured()
                        ? "로그인 필요"
                        : "연결 설정 필요";
        ui.cardHeader(nutritionAuthCard, "Nutrition DB 로그인", nutritionAuthStatus);
        if (host.nutritionSupabaseConfig().isConfigured()) {
            nutritionAuthCard.addView(ui.keyValue("계정", host.nutritionSupabaseConfig().email));
            Button nutritionSignOutButton = ui.button(
                    "Nutrition DB 로그아웃",
                    false,
                    v -> host.signOutFromNutritionSupabase()
            );
            nutritionAuthCard.addView(
                    nutritionSignOutButton,
                    ui.fullWidthParams(ui.dp(12))
            );
        } else if (!host.nutritionSupabaseConfig().isConnectionConfigured()) {
            nutritionAuthCard.addView(ui.text(
                    "Nutrition DB URL과 anon key를 먼저 저장해야 로그인할 수 있습니다.",
                    12,
                    FitnessUi.COLOR_MUTED,
                    false
            ));
        } else {
            EditText nutritionEmailInput = ui.input(
                    "Nutrition DB 이메일",
                    host.nutritionSupabaseConfig().email
            );
            nutritionEmailInput.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            );
            EditText nutritionPasswordInput = ui.input("Nutrition DB 비밀번호", "");
            nutritionPasswordInput.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            );
            nutritionAuthCard.addView(ui.fieldLabel("Nutrition DB 이메일"));
            nutritionAuthCard.addView(nutritionEmailInput, ui.fullWidthParams(0));
            nutritionAuthCard.addView(ui.fieldLabel("Nutrition DB 비밀번호"));
            nutritionAuthCard.addView(nutritionPasswordInput, ui.fullWidthParams(0));
            Button nutritionSignInButton = ui.button(
                    "Nutrition DB 로그인",
                    true,
                    v -> host.signInToNutritionSupabase(
                            FitnessUi.inputText(nutritionEmailInput),
                            FitnessUi.inputText(nutritionPasswordInput)
                    )
            );
            nutritionAuthCard.addView(
                    nutritionSignInButton,
                    ui.fullWidthParams(ui.dp(16))
            );
            Button nutritionSignUpButton = ui.button(
                    "Nutrition DB 계정 만들기",
                    false,
                    v -> host.signUpToNutritionSupabase(
                            FitnessUi.inputText(nutritionEmailInput),
                            FitnessUi.inputText(nutritionPasswordInput)
                    )
            );
            nutritionAuthCard.addView(
                    nutritionSignUpButton,
                    ui.fullWidthParams(ui.dp(12))
            );
        }
        add(nutritionAuthCard);
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
