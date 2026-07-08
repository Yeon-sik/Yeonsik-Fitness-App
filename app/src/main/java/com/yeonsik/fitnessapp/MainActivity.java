package com.yeonsik.fitnessapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.sync.SupabaseSyncManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int COLOR_BACKGROUND = Color.rgb(246, 246, 243);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(18, 18, 18);
    private static final int COLOR_MUTED = Color.rgb(105, 105, 105);
    private static final int COLOR_BORDER = Color.rgb(224, 224, 220);
    private static final int COLOR_PRIMARY = Color.rgb(18, 18, 18);
    private static final int COLOR_SUBTLE = Color.rgb(237, 237, 232);
    private static final int COLOR_ACTIVE_TAB = Color.rgb(18, 18, 18);

    private enum Tab {
        HOME,
        RECORDS,
        SETTINGS
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String today = LocalDate.now().toString();

    private FitnessDatabaseHelper databaseHelper;
    private FitnessRepository repository;
    private SupabaseConfigStore configStore;
    private SupabaseSyncManager syncManager;
    private SupabaseConfig supabaseConfig;

    private LinearLayout content;
    private LinearLayout homeTabArea;
    private LinearLayout recordsTabArea;
    private LinearLayout settingsTabArea;
    private View homeTabIndicator;
    private View recordsTabIndicator;
    private View settingsTabIndicator;
    private TextView homeTabLabel;
    private TextView recordsTabLabel;
    private TextView settingsTabLabel;

    private Tab activeTab = Tab.HOME;
    private String selectedDate = today;
    private boolean isManualSyncing = false;
    private String syncLabel = "local-only";
    private String syncDetail = "로컬 전용 모드";
    private String lastSyncedAt = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configStore = new SupabaseConfigStore(this);
        supabaseConfig = configStore.load();
        databaseHelper = new FitnessDatabaseHelper(this);
        repository = new FitnessRepository(databaseHelper, supabaseConfig.effectiveUserId());
        syncManager = new SupabaseSyncManager(databaseHelper);
        applySyncStatusFromConfig();
        configureWindow();
        setContentView(buildRootView());
        renderActiveTab();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private View buildRootView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(44), dp(20), dp(20));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scrollView, scrollParams);
        root.addView(buildBottomNav(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(COLOR_SURFACE);
        nav.setPadding(0, 0, 0, dp(8));

        homeTabArea = navArea("HOME", Tab.HOME);
        recordsTabArea = navArea("기록", Tab.RECORDS);
        settingsTabArea = navArea("설정", Tab.SETTINGS);

        nav.addView(homeTabArea, navParams());
        nav.addView(divider());
        nav.addView(recordsTabArea, navParams());
        nav.addView(divider());
        nav.addView(settingsTabArea, navParams());
        return nav;
    }

    private LinearLayout.LayoutParams navParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        return params;
    }

    private LinearLayout navArea(String label, Tab tab) {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setGravity(Gravity.CENTER);
        area.setMinimumHeight(dp(62));
        area.setClickable(true);
        area.setFocusable(true);
        area.setBackgroundColor(COLOR_SURFACE);
        area.setOnClickListener(v -> {
            activeTab = tab;
            renderActiveTab();
        });

        View indicator = new View(this);
        indicator.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextSize(13);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(0, dp(16), 0, dp(12));

        area.addView(indicator);
        area.addView(textView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (tab == Tab.HOME) {
            homeTabIndicator = indicator;
            homeTabLabel = textView;
        } else if (tab == Tab.RECORDS) {
            recordsTabIndicator = indicator;
            recordsTabLabel = textView;
        } else {
            settingsTabIndicator = indicator;
            settingsTabLabel = textView;
        }

        return area;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(dp(1), dp(42)));
        return divider;
    }

    private void renderActiveTab() {
        content.removeAllViews();
        refreshNavState();

        if (activeTab == Tab.HOME) {
            renderHomeTab();
            return;
        }

        if (activeTab == Tab.RECORDS) {
            renderRecordsTab();
            return;
        }

        renderSettingsTab();
    }

    private void refreshNavState() {
        styleNavArea(homeTabArea, homeTabIndicator, homeTabLabel, activeTab == Tab.HOME);
        styleNavArea(recordsTabArea, recordsTabIndicator, recordsTabLabel, activeTab == Tab.RECORDS);
        styleNavArea(settingsTabArea, settingsTabIndicator, settingsTabLabel, activeTab == Tab.SETTINGS);
    }

    private void styleNavArea(LinearLayout area, View indicator, TextView label, boolean active) {
        area.setBackgroundColor(COLOR_SURFACE);
        indicator.setBackgroundColor(active ? COLOR_ACTIVE_TAB : COLOR_SURFACE);
        label.setTextColor(active ? COLOR_TEXT : COLOR_MUTED);
        label.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void renderHomeTab() {
        List<String> todaySessions = repository.sessionsForDate(today);
        String latestSessionId = repository.latestSessionId();
        List<String> latestDetails = latestSessionId == null ? Collections.emptyList() : repository.sessionDetails(latestSessionId);

        label("FITNESS OS");
        title("오늘 운동");
        judgmentCard(todaySessions, latestDetails);

        primaryButton("운동 시작", v -> showSessionDialog());
        actionGrid(
                button("종목 추가", false, v -> showExerciseDialog()),
                button("세트 추가", false, v -> showSetDialog()),
                button("체중 기록", false, v -> showBodyMetricDialog()),
                button("식단 기록", false, v -> showMealDialog())
        );

        section("오늘 운동");
        lines(todaySessions, "오늘 운동 기록이 없습니다.", "운동 시작");

        section("최근 세션 상세");
        if (latestSessionId == null) {
            emptyState("먼저 운동을 시작하세요.", null);
        } else {
            lines(latestDetails, "아직 종목이 없습니다.", "종목 추가");
        }

        section("오늘 신체 기록");
        lines(repository.bodyMetricsForDate(today), "오늘 체중 기록이 없습니다.", "체중 기록");

        section("오늘 식단");
        lines(repository.mealsForDate(today), "오늘 식단 기록이 없습니다.", "식단 기록");
    }

    private void renderRecordsTab() {
        label("RECORDS");
        title("기록 확인");

        CalendarView calendarView = new CalendarView(this);
        calendarView.setDate(toEpochMillis(selectedDate), false, true);
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth).toString();
            renderActiveTab();
        });
        content.addView(calendarView, fullWidthParams(dp(8)));

        cardRow("선택 날짜", displayDate(selectedDate));

        section("운동");
        lines(repository.sessionsForDate(selectedDate), "선택한 날짜의 운동 기록이 없습니다.", null);

        section("체중");
        lines(repository.bodyMetricsForDate(selectedDate), "선택한 날짜의 체중 기록이 없습니다.", null);

        section("식단");
        lines(repository.mealsForDate(selectedDate), "선택한 날짜의 식단 기록이 없습니다.", null);
    }

    private void renderSettingsTab() {
        label("SETTINGS");
        title("설정");

        LinearLayout statusCard = card();
        cardHeader(statusCard, "연결 상태", null);
        statusCard.addView(keyValue("Supabase", supabaseConfig.isConfigured() ? "configured" : "local-only"));
        statusCard.addView(keyValue("동기화", syncLabel));
        statusCard.addView(keyValue("사용자", repositoryUserLabel()));
        Button syncButton = button(isManualSyncing ? "수동 동기화 중" : "수동 동기화", false, v -> runManualSync());
        syncButton.setEnabled(!isManualSyncing);
        LinearLayout.LayoutParams syncParams = fullWidthParams(dp(0));
        syncParams.setMargins(0, dp(12), 0, 0);
        statusCard.addView(syncButton, syncParams);
        if (!syncDetail.isEmpty()) {
            TextView detail = text(syncDetail, 13, COLOR_MUTED, false);
            detail.setPadding(0, dp(8), 0, 0);
            statusCard.addView(detail);
        }
        content.addView(statusCard);

        LinearLayout configCard = card();
        cardHeader(configCard, "Supabase config", supabaseConfig.sourceLabel);
        EditText supabaseUrlInput = input("Supabase URL", supabaseConfig.supabaseUrl);
        EditText supabaseAnonInput = input("Anon key", supabaseConfig.supabaseAnonKey);
        supabaseAnonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText userIdInput = input("User ID", supabaseConfig.userId);
        configCard.addView(fieldLabel("Supabase URL"));
        configCard.addView(supabaseUrlInput, fullWidthParams(dp(8)));
        configCard.addView(fieldLabel("Anon key"));
        configCard.addView(supabaseAnonInput, fullWidthParams(dp(8)));
        configCard.addView(fieldLabel("User ID"));
        configCard.addView(userIdInput, fullWidthParams(dp(8)));
        Button saveButton = button("Save config", true, v -> saveSupabaseConfig(
                supabaseUrlInput,
                supabaseAnonInput,
                userIdInput
        ));
        configCard.addView(saveButton, fullWidthParams(dp(12)));
        content.addView(configCard);
    }

    private void saveSupabaseConfig(EditText urlInput, EditText anonInput, EditText userIdInput) {
        supabaseConfig = configStore.save(
                text(urlInput),
                text(anonInput),
                text(userIdInput)
        );
        repository.normalizeLocalUserId(supabaseConfig.effectiveUserId());
        applySyncStatusFromConfig();
        toast("설정을 저장했습니다.");
        renderActiveTab();
    }

    private void runManualSync() {
        if (!supabaseConfig.isConfigured()) {
            toast("Supabase URL, anon key, user ID를 먼저 저장하세요.");
            return;
        }

        isManualSyncing = true;
        syncLabel = "syncing";
        syncDetail = "Supabase와 수동 동기화 중입니다.";
        renderActiveTab();

        executor.execute(() -> {
            try {
                SupabaseSyncManager.SyncResult result = syncManager.manualSync(supabaseConfig);
                repository.setUserId(supabaseConfig.effectiveUserId());
                lastSyncedAt = result.syncedAt;
                runOnUiThread(() -> {
                    isManualSyncing = false;
                    syncLabel = "synced";
                    syncDetail = "push " + result.pushedRows + "건 · pull " + result.pulledRows + "건";
                    toast("수동 동기화를 완료했습니다.");
                    renderActiveTab();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    isManualSyncing = false;
                    syncLabel = "sync failed";
                    syncDetail = error.getMessage() == null ? "동기화에 실패했습니다." : error.getMessage();
                    toast("수동 동기화에 실패했습니다.");
                    renderActiveTab();
                });
            }
        });
    }

    private void applySyncStatusFromConfig() {
        if (supabaseConfig.isConfigured()) {
            syncLabel = lastSyncedAt.isEmpty() ? "configured" : "synced";
            syncDetail = lastSyncedAt.isEmpty()
                    ? "Supabase 설정이 저장되었습니다."
                    : "마지막 동기화 " + lastSyncedAt;
            return;
        }

        syncLabel = "local-only";
        syncDetail = "Supabase 설정이 없어 로컬 전용 모드입니다.";
    }

    private String repositoryUserLabel() {
        if (!supabaseConfig.userId.isEmpty()) {
            return supabaseConfig.userId;
        }
        return SupabaseConfig.DEFAULT_USER_ID;
    }

    private void showSessionDialog() {
        LinearLayout form = form();
        EditText date = input("날짜 (YYYY-MM-DD)", today);
        EditText title = input("운동 이름", "근력 운동");
        EditText startedAt = input("시작 시간 (선택)", "");
        EditText endedAt = input("종료 시간 (선택)", "");
        EditText memo = input("메모 (선택)", "");
        addAll(form, date, title, startedAt, endedAt, memo);
        new AlertDialog.Builder(this)
                .setTitle("운동 시작")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    repository.createSession(text(date), text(title), "strength", text(memo), text(startedAt), text(endedAt));
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showExerciseDialog() {
        String sessionId = repository.latestSessionId();
        if (sessionId == null) {
            toast("먼저 운동을 시작하세요.");
            return;
        }

        LinearLayout form = form();
        EditText name = input("운동 종목", "벤치프레스");
        EditText category = input("부위 (가슴/등/하체/이두/삼두/어깨/복근)", "가슴");
        EditText order = numberInput("순서", "1");
        EditText memo = input("메모 (선택)", "");
        addAll(form, name, category, order, memo);
        new AlertDialog.Builder(this)
                .setTitle("종목 추가")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    repository.addExercise(sessionId, text(name), text(category), parseInt(order, 1), text(memo));
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showSetDialog() {
        String sessionId = repository.latestSessionId();
        if (sessionId == null || repository.sessionDetails(sessionId).isEmpty()) {
            toast("운동과 종목을 먼저 추가하세요.");
            return;
        }

        LinearLayout form = form();
        EditText setIndex = numberInput("세트", "1");
        EditText weight = decimalInput("무게 kg", "60");
        EditText reps = numberInput("횟수", "10");
        CheckBox completed = new CheckBox(this);
        completed.setText("완료");
        completed.setTextColor(COLOR_TEXT);
        completed.setTextSize(16);
        completed.setMinHeight(dp(52));
        completed.setChecked(true);
        addAll(form, setIndex, weight, reps, completed);
        new AlertDialog.Builder(this)
                .setTitle("세트 추가")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    String latestExerciseId = latestExerciseId(sessionId);
                    if (latestExerciseId == null) {
                        toast("운동 종목을 찾지 못했습니다.");
                        return;
                    }
                    repository.addSet(sessionId, latestExerciseId, parseInt(setIndex, 1),
                            parseDouble(weight, 0), parseInt(reps, 0), completed.isChecked());
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showBodyMetricDialog() {
        LinearLayout form = form();
        EditText date = input("날짜 (YYYY-MM-DD)", today);
        EditText weight = decimalInput("체중 kg", "73.0");
        EditText memo = input("메모 (선택)", "");
        addAll(form, date, weight, memo);
        new AlertDialog.Builder(this)
                .setTitle("체중 기록")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    repository.addBodyMetric(text(date), parseDouble(weight, 0), text(memo));
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showMealDialog() {
        LinearLayout form = form();
        EditText date = input("날짜 (YYYY-MM-DD)", today);
        EditText type = input("식사 구분 (breakfast/lunch/dinner/snack)", "lunch");
        EditText menu = input("식단 내용", "닭가슴살 샐러드");
        EditText calories = numberInput("칼로리 kcal (선택)", "");
        EditText protein = decimalInput("단백질 g (선택)", "");
        addAll(form, date, type, menu, calories, protein);
        new AlertDialog.Builder(this)
                .setTitle("식단 기록")
                .setView(form)
                .setPositiveButton("저장", (dialog, which) -> {
                    repository.addMeal(text(date), text(type), text(menu), optionalInt(calories), optionalDouble(protein));
                    renderActiveTab();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private String latestExerciseId(String sessionId) {
        android.database.Cursor cursor = databaseHelper.getReadableDatabase().rawQuery(
                "SELECT id FROM workout_exercises WHERE session_id = ? AND deleted_at IS NULL ORDER BY order_index DESC, updated_at DESC LIMIT 1",
                new String[]{sessionId});
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    private void judgmentCard(List<String> todaySessions, List<String> latestDetails) {
        LinearLayout card = card();
        TextView status = text(todaySessions.isEmpty() ? "오늘 운동 미기록" : "오늘 운동 기록됨", 22, COLOR_TEXT, true);
        TextView summary = text(todaySessions.isEmpty()
                ? "지금 필요한 행동은 운동 세션 시작입니다."
                : todaySessions.size() + "개 세션이 기록되었습니다.", 15, COLOR_MUTED, false);
        TextView metric = text(latestDetails.isEmpty()
                ? "세트 기록 없음"
                : latestDetails.size() + "개 항목 기록", 13, COLOR_MUTED, false);
        card.addView(status);
        card.addView(summary);
        card.addView(metric);
        content.addView(card);
    }

    private void lines(List<String> rows, String empty, String emptyAction) {
        if (rows.isEmpty()) {
            emptyState(empty, emptyAction);
            return;
        }

        for (String row : rows) {
            recordCard(row);
        }
    }

    private void recordCard(String rowText) {
        LinearLayout card = card();
        TextView row = text(rowText, 15, COLOR_TEXT, false);
        row.setLineSpacing(dp(2), 1f);
        card.addView(row);
        content.addView(card);
    }

    private void emptyState(String message, String action) {
        LinearLayout card = card();
        card.addView(text(message, 15, COLOR_MUTED, false));
        if (action != null) {
            TextView hint = text(action + " 버튼을 사용하세요.", 13, COLOR_MUTED, false);
            hint.setPadding(0, dp(6), 0, 0);
            card.addView(hint);
        }
        content.addView(card);
    }

    private void cardHeader(LinearLayout card, String title, String meta) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = text(title, 18, COLOR_TEXT, true);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (meta != null) {
            TextView metaView = text(meta, 12, COLOR_MUTED, false);
            header.addView(metaView);
        }

        card.addView(header);
    }

    private View keyValue(String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView keyView = text(key, 14, COLOR_MUTED, false);
        TextView valueView = text(value, 14, COLOR_TEXT, true);
        valueView.setGravity(Gravity.END);

        row.addView(keyView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView);
        return row;
    }

    private void cardRow(String key, String value) {
        LinearLayout card = card();
        card.addView(keyValue(key, value));
        content.addView(card);
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 13, COLOR_MUTED, false);
        label.setPadding(0, dp(12), 0, dp(6));
        return label;
    }

    private Button button(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? Color.WHITE : COLOR_TEXT);
        button.setMinHeight(dp(54));
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(borderDrawable(primary ? COLOR_PRIMARY : COLOR_SUBTLE,
                primary ? COLOR_PRIMARY : COLOR_BORDER, dp(8)));
        button.setOnClickListener(listener);
        return button;
    }

    private void primaryButton(String text, View.OnClickListener listener) {
        Button button = button(text, true, listener);
        content.addView(button, fullWidthParams(dp(14)));
    }

    private void actionGrid(Button first, Button second, Button third, Button fourth) {
        row(first, second, dp(6));
        row(third, fourth, dp(6));
    }

    private void row(View first, View second, int topMargin) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = fullWidthParams(topMargin);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        right.setMargins(dp(5), 0, 0, 0);
        row.addView(first, left);
        row.addView(second, right);
        content.addView(row, rowParams);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(borderDrawable(COLOR_SURFACE, COLOR_BORDER, dp(8)));
        card.setLayoutParams(fullWidthParams(dp(8)));
        return card;
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        return form;
    }

    private EditText input(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setMinHeight(dp(54));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(borderDrawable(COLOR_SURFACE, COLOR_BORDER, dp(8)));
        return input;
    }

    private EditText numberInput(String hint, String value) {
        EditText input = input(hint, value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private EditText decimalInput(String hint, String value) {
        EditText input = input(hint, value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return input;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private void label(String text) {
        TextView view = text(text, 12, COLOR_MUTED, true);
        view.setLetterSpacing(0.08f);
        content.addView(view);
    }

    private void title(String text) {
        TextView view = text(text, 30, COLOR_TEXT, true);
        view.setPadding(0, 0, 0, dp(16));
        content.addView(view);
    }

    private void section(String text) {
        TextView view = text(text, 18, COLOR_TEXT, true);
        view.setPadding(0, dp(20), 0, dp(10));
        content.addView(view);
    }

    private GradientDrawable borderDrawable(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWidthParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    private void addAll(LinearLayout form, View... views) {
        for (View view : views) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(8), 0, 0);
            form.addView(view, params);
        }
    }

    private String text(EditText input) {
        return input.getText().toString();
    }

    private int parseInt(EditText input, int fallback) {
        try {
            return Integer.parseInt(text(input).trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private double parseDouble(EditText input, double fallback) {
        try {
            return Double.parseDouble(text(input).trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private Integer optionalInt(EditText input) {
        try {
            String value = text(input).trim();
            return value.isEmpty() ? null : Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private Double optionalDouble(EditText input) {
        try {
            String value = text(input).trim();
            return value.isEmpty() ? null : Double.parseDouble(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private String displayDate(String date) {
        return date == null ? "" : date.replace("-", ". ");
    }

    private long toEpochMillis(String date) {
        LocalDate localDate = LocalDate.parse(date);
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
