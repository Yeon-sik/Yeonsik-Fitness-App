package com.yeonsik.fitnessapp.ui;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.config.SupabaseConfig;

/** 설정, 두 Supabase 연결, 두 인증 세션을 한 화면에서 명확히 보여 준다. */
public final class SettingsScreen extends BaseScreen {
    private boolean advancedConnectionsVisible;

    public SettingsScreen(ScreenHost host) {
        super(host);
    }

    public void showAdvancedConnections() {
        advancedConnectionsVisible = true;
    }

    @Override
    public void render() {
        screenHeader("앱 환경", "설정");
        renderThemeCard();
        renderDataSafetyCard();
        renderDataImportCard();

        SupabaseConfig sharedConfig = host.supabaseConfig();
        SupabaseConfig nutritionConfig = host.nutritionSupabaseConfig();
        SupabaseConfig priceTraceConfig = host.priceTraceSupabaseConfig();
        renderConnectionStatus(sharedConfig, nutritionConfig, priceTraceConfig);
        section("연결 및 계정", advancedConnectionsVisible ? "접기" : "고급 설정", () -> {
            advancedConnectionsVisible = !advancedConnectionsVisible;
            host.rerender();
        });
        if (advancedConnectionsVisible) {
            renderSharedConnectionCard(sharedConfig);
            renderNutritionConnectionCard(nutritionConfig);
            renderPriceTraceConnectionCard(priceTraceConfig);
            renderSharedAuthCard(sharedConfig);
            renderNutritionAuthCard(nutritionConfig);
        }
    }

    private void renderConnectionStatus(
            SupabaseConfig sharedConfig,
            SupabaseConfig nutritionConfig,
            SupabaseConfig priceTraceConfig
    ) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.text("DB 연결 구조", 17, FitnessUi.COLOR_TEXT, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(ui.statusDotBadge(
                syncStatusLabel(host.syncLabel()),
                syncStatusColor(),
                false
        ));
        card.addView(header);

        View line = ui.hairline(FitnessUi.COLOR_BORDER);
        LinearLayout.LayoutParams lineParams = ui.fullWidthParams(ui.dp(12));
        lineParams.height = ui.dp(1);
        card.addView(line, lineParams);

        card.addView(ui.keyValue("Personal OS 공통 DB",
                accountStatus(sharedConfig) + " · " + projectLabel(sharedConfig)));
        card.addView(ui.keyValue("영양 전용 DB",
                nutritionStatus(nutritionConfig) + " · " + projectLabel(nutritionConfig)));
        card.addView(ui.keyValue("PriceTrace 상품 조회", priceTraceConfig.isConnectionConfigured()
                ? "읽기 전용 · " + projectLabel(priceTraceConfig)
                : "연결 없음"));
        card.addView(ui.keyValue("공통 계정", sharedConfig.email.isEmpty()
                ? SupabaseConfig.DEFAULT_USER_ID
                : sharedConfig.email));
        card.addView(ui.keyValue("영양 DB 계정", nutritionConfig.email.isEmpty()
                ? (nutritionConfig.isConnectionConfigured() ? "공개 항목만" : "연결 없음")
                : nutritionConfig.email));

        Button syncButton = ui.button(
                host.isManualSyncing() ? "수동 동기화 중" : "두 DB 수동 동기화",
                false,
                v -> host.runManualSync()
        );
        syncButton.setEnabled(!host.isManualSyncing());
        card.addView(syncButton, ui.fullWidthParams(ui.dp(14)));
        if (!host.syncDetail().isEmpty()) {
            TextView detail = ui.text(host.syncDetail(), 12, FitnessUi.COLOR_MUTED, false);
            detail.setPadding(0, ui.dp(10), 0, 0);
            card.addView(detail);
        }
        add(card);
    }

    private void renderSharedConnectionCard(SupabaseConfig config) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "Personal OS 공통 DB 연결", connectionStatus(
                config,
                host.isSharedSupabaseConnectionManaged()
        ));
        card.addView(ui.text(
                "CashOS·FitnessApp·PersonalOSApp이 같은 프로젝트를 사용합니다. "
                        + "이 연결에는 운동·신체·식사 기록과 공통 계정만 들어갑니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        renderConnectionFields(
                card,
                config,
                host.isSharedSupabaseConnectionManaged(),
                "Personal OS DB URL",
                "Personal OS DB anon key",
                "공통 DB 연결 저장",
                host::saveSupabaseConfig
        );
        add(card);
    }

    private void renderNutritionConnectionCard(SupabaseConfig config) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "영양 전용 DB 연결", connectionStatus(
                config,
                host.isNutritionSupabaseConnectionManaged()
        ));
        card.addView(ui.text(
                "FitnessApp만 사용하는 별도 Supabase 프로젝트입니다. 음식·메뉴·영양성분만 "
                        + "저장하며 공통 DB와 URL·키·로그인 세션을 공유하지 않습니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        renderConnectionFields(
                card,
                config,
                host.isNutritionSupabaseConnectionManaged(),
                "영양 DB URL",
                "영양 DB anon key",
                "영양 DB 연결 저장",
                host::saveNutritionSupabaseConfig
        );
        add(card);
    }

    private void renderPriceTraceConnectionCard(SupabaseConfig config) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "PriceTrace 상품 조회", connectionStatus(
                config,
                host.isPriceTraceSupabaseConnectionManaged()
        ));
        card.addView(ui.text(
                "PriceTrace의 검증된 표준상품을 product-read.v1로 읽는 별도 연결입니다. "
                        + "표준상품 후보를 검색하고 사용자가 선택한 항목을 영양 정보와 연결합니다. "
                        + "쓰기 권한과 로그인은 사용하지 않습니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        renderConnectionFields(
                card,
                config,
                host.isPriceTraceSupabaseConnectionManaged(),
                "PriceTrace DB URL",
                "PriceTrace DB anon key",
                "PriceTrace 읽기 연결 저장",
                host::savePriceTraceSupabaseConfig
        );
        add(card);
    }

    private void renderConnectionFields(
            LinearLayout card,
            SupabaseConfig config,
            boolean managed,
            String urlLabel,
            String keyLabel,
            String saveLabel,
            ConnectionSaver saver
    ) {
        FitnessUi ui = ui();
        if (managed) {
            card.addView(ui.text(
                    "빌드 기본값으로 자동 연결되었습니다. 아래에서 다른 Supabase DB로 변경할 수 있습니다.",
                    12,
                    FitnessUi.COLOR_TERTIARY,
                    false
            ));
            card.addView(ui.keyValue("프로젝트", projectLabel(config)));
        } else {
            card.addView(ui.keyValue("연결 원본", config.sourceLabel));
        }

        EditText urlInput = ui.input(urlLabel, config.supabaseUrl);
        EditText anonInput = ui.input(keyLabel, config.supabaseAnonKey);
        anonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(ui.fieldLabel(urlLabel));
        card.addView(urlInput, ui.fullWidthParams(0));
        card.addView(ui.fieldLabel(keyLabel));
        card.addView(anonInput, ui.fullWidthParams(0));
        Button saveButton = ui.button(saveLabel, true, v -> saver.save(
                FitnessUi.inputText(urlInput),
                FitnessUi.inputText(anonInput)
        ));
        card.addView(saveButton, ui.fullWidthParams(ui.dp(16)));
    }

    private void renderSharedAuthCard(SupabaseConfig config) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "Personal OS 공통 계정", accountStatus(config));
        card.addView(ui.text(
                "세 앱에서 같은 이메일 계정을 사용합니다. 이 로그인은 공통 DB의 "
                        + "운동·신체·식사 기록에만 적용됩니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        renderAuthControls(
                card,
                config,
                "공통 DB 연결을 먼저 설정하세요.",
                host::signInToSupabase,
                host::signUpToSupabase,
                host::signOutFromSupabase
        );
        add(card);
    }

    private void renderNutritionAuthCard(SupabaseConfig config) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "영양 DB 계정", nutritionStatus(config));
        card.addView(ui.text(
                "공개 영양 항목은 로그인 없이 읽을 수 있습니다. 내 비공개 음식·메뉴를 "
                        + "저장하려면 영양 DB에 별도로 로그인하세요. 같은 이메일을 써도 "
                        + "공통 DB와는 다른 Supabase 계정입니다.",
                12,
                FitnessUi.COLOR_MUTED,
                false
        ));
        renderAuthControls(
                card,
                config,
                "영양 DB 연결을 먼저 설정하세요.",
                host::signInToNutritionSupabase,
                host::signUpToNutritionSupabase,
                host::signOutFromNutritionSupabase
        );
        add(card);
    }

    private void renderAuthControls(
            LinearLayout card,
            SupabaseConfig config,
            String missingConnectionMessage,
            AuthAction signIn,
            AuthAction signUp,
            Runnable signOut
    ) {
        FitnessUi ui = ui();
        if (config.isConfigured()) {
            card.addView(ui.keyValue("계정", config.email));
            card.addView(ui.button("로그아웃", false, v -> signOut.run()),
                    ui.fullWidthParams(ui.dp(12)));
            return;
        }
        if (!config.isConnectionConfigured()) {
            card.addView(ui.text(missingConnectionMessage, 12, FitnessUi.COLOR_MUTED, false));
            return;
        }

        EditText emailInput = ui.input("이메일", config.email);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText passwordInput = ui.input("비밀번호", "");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(ui.fieldLabel("이메일"));
        card.addView(emailInput, ui.fullWidthParams(0));
        card.addView(ui.fieldLabel("비밀번호"));
        card.addView(passwordInput, ui.fullWidthParams(0));
        card.addView(ui.button("로그인", true, v -> signIn.run(
                FitnessUi.inputText(emailInput),
                FitnessUi.inputText(passwordInput)
        )), ui.fullWidthParams(ui.dp(16)));
        card.addView(ui.button("계정 만들기", false, v -> signUp.run(
                FitnessUi.inputText(emailInput),
                FitnessUi.inputText(passwordInput)
        )), ui.fullWidthParams(ui.dp(12)));
    }

    private void renderDataImportCard() {
        FitnessUi ui = ui();
        boolean busy = host.isDataImporting() || host.isDataTransferInProgress();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "데이터 가져오기", busy ? "처리 중" : "FLEEK CSV");
        card.addView(ui.text(
                "FLEEK에서 내보낸 CSV의 날짜·운동·중량·횟수를 로컬 기록으로 가져옵니다. "
                        + "같은 파일을 다시 선택하면 기존 세션은 건너뜁니다.",
                13,
                FitnessUi.COLOR_MUTED,
                false
        ));
        if (!host.dataImportDetail().isEmpty()) {
            TextView detail = ui.text(host.dataImportDetail(), 12, FitnessUi.COLOR_TERTIARY, false);
            detail.setPadding(0, ui.dp(10), 0, 0);
            card.addView(detail);
        }
        Button importButton = ui.button(
                busy ? "처리 중" : "FLEEK CSV 선택",
                true,
                v -> host.openFleekDataImport()
        );
        importButton.setEnabled(!busy);
        card.addView(importButton, ui.fullWidthParams(ui.dp(14)));
        add(card);
    }

    private void renderDataSafetyCard() {
        FitnessUi ui = ui();
        boolean busy = host.isDataTransferInProgress() || host.isDataImporting();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "내 데이터 보관", busy ? "처리 중" : "로컬 파일");
        card.addView(ui.text(
                "운동·식단·체중·루틴 기록을 백업합니다. 로그인 정보와 DB 주소·API 키는 포함하지 않습니다.",
                13,
                FitnessUi.COLOR_MUTED,
                false
        ));
        card.addView(ui.text(
                "복원은 기존 기록을 지우지 않으며 같은 항목은 건너뜁니다.",
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        ), ui.fullWidthParams(ui.dp(8)));
        if (!host.dataTransferDetail().isEmpty()) {
            TextView detail = ui.text(
                    host.dataTransferDetail(),
                    12,
                    FitnessUi.COLOR_TERTIARY,
                    false
            );
            card.addView(detail, ui.fullWidthParams(ui.dp(10)));
        }

        Button backup = ui.button(
                busy ? "처리 중" : "전체 백업 저장 (JSON)",
                true,
                v -> host.createLocalBackup()
        );
        backup.setEnabled(!busy);
        card.addView(backup, ui.fullWidthParams(ui.dp(14)));

        Button restore = ui.button("백업 복원", false, v -> host.restoreLocalBackup());
        restore.setEnabled(!busy);
        card.addView(restore, ui.fullWidthParams(ui.dp(10)));

        Button csv = ui.button(
                "기록 요약 내보내기 (CSV)",
                false,
                v -> host.exportRecordsCsv()
        );
        csv.setEnabled(!busy);
        card.addView(csv, ui.fullWidthParams(ui.dp(10)));
        add(card);
    }

    private void renderThemeCard() {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "화면 모드", themeModeLabel(host.themeMode()));
        String[] modes = {"light", "dark", "system"};
        LinearLayout chipRow = new LinearLayout(host.activity());
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < modes.length; index++) {
            final String mode = modes[index];
            Button chip = ui.filterButton(themeModeLabel(mode));
            ui.styleFilterButton(chip, mode.equals(host.themeMode()));
            chip.setOnClickListener(v -> host.setThemeMode(mode));
            chipRow.addView(chip, ui.pickerCellParams(index == 0));
        }
        card.addView(chipRow, ui.fullWidthParams(ui.dp(12)));
        TextView hint = ui.text("시스템 설정은 기기의 다크 모드 설정을 따릅니다.",
                12, FitnessUi.COLOR_TERTIARY, false);
        hint.setPadding(0, ui.dp(10), 0, 0);
        card.addView(hint);
        add(card);
    }

    private String accountStatus(SupabaseConfig config) {
        if (config.isConfigured()) {
            return "로그인됨";
        }
        return config.isConnectionConfigured() ? "로그인 필요" : "연결 없음";
    }

    private String nutritionStatus(SupabaseConfig config) {
        if (config.isConfigured()) {
            return "로그인됨";
        }
        return config.isConnectionConfigured() ? "공개 항목 연결" : "연결 없음";
    }

    private String connectionStatus(SupabaseConfig config, boolean managed) {
        if (!config.isConnectionConfigured()) {
            return "설정 필요";
        }
        return managed ? "빌드 설정" : "기기 저장 연결";
    }

    private String projectLabel(SupabaseConfig config) {
        String projectRef = config.projectRef();
        return projectRef.isEmpty() ? "미설정" : projectRef;
    }

    private String themeModeLabel(String mode) {
        if ("dark".equals(mode)) {
            return "다크";
        }
        if ("system".equals(mode)) {
            return "시스템 설정";
        }
        return "라이트";
    }

    private int syncStatusColor() {
        return syncStatusColorForLabel(host.syncLabel());
    }

    static int syncStatusColorForLabel(String label) {
        if ("synced".equals(label) || "configured".equals(label)) {
            return FitnessUi.COLOR_POSITIVE;
        }
        if ("sync failed".equals(label) || "authentication failed".equals(label)
                || "local ownership failed".equals(label)
                || "nutrition ownership failed".equals(label)) {
            return FitnessUi.COLOR_NEGATIVE;
        }
        if ("syncing".equals(label) || "authenticating".equals(label)
                || "partial".equals(label)
                || "login required".equals(label)
                || "confirmation required".equals(label)) {
            return FitnessUi.COLOR_WARNING;
        }
        return FitnessUi.COLOR_TERTIARY;
    }

    static String syncStatusLabel(String label) {
        if ("synced".equals(label)) {
            return "동기화 완료";
        }
        if ("configured".equals(label)) {
            return "연결됨";
        }
        if ("syncing".equals(label)) {
            return "동기화 중";
        }
        if ("authenticating".equals(label)) {
            return "로그인 중";
        }
        if ("partial".equals(label)) {
            return "일부 완료";
        }
        if ("login required".equals(label)) {
            return "로그인 필요";
        }
        if ("confirmation required".equals(label)) {
            return "가입 확인 필요";
        }
        if ("authentication failed".equals(label)) {
            return "로그인 실패";
        }
        if ("local ownership failed".equals(label)
                || "nutrition ownership failed".equals(label)) {
            return "소유권 확인 실패";
        }
        if ("sync failed".equals(label)) {
            return "동기화 실패";
        }
        return "로컬 전용";
    }

    private interface ConnectionSaver {
        void save(String url, String anonKey);
    }

    private interface AuthAction {
        void run(String email, String password);
    }
}
