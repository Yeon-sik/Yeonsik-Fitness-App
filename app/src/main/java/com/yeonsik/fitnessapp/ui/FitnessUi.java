package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * 모노크롬 디자인 시스템의 공통 UI 팩토리.
 * 색 토큰, 타이포그래피, 표면(카드/타일), 버튼/칩, 입력창, 리스트 행, 포맷터를 담당한다.
 * 화면 상태를 소유하지 않으며, 다크 반전 여부는 생성 시 주입된 supplier로 판단한다.
 */
public final class FitnessUi {
    public static final int COLOR_BACKGROUND = Color.rgb(236, 238, 241);
    public static final int COLOR_SURFACE = Color.WHITE;
    public static final int COLOR_TEXT = Color.rgb(21, 22, 26);
    public static final int COLOR_MUTED = Color.rgb(106, 110, 118);
    public static final int COLOR_TERTIARY = Color.rgb(162, 166, 174);
    public static final int COLOR_BORDER = Color.argb(20, 21, 22, 26);
    public static final int COLOR_PRIMARY = Color.rgb(17, 17, 20);
    public static final int COLOR_PRIMARY_HI = Color.rgb(28, 28, 32);
    public static final int COLOR_SUBTLE = Color.rgb(245, 246, 248);
    public static final int COLOR_INVERSE_TEXT = Color.WHITE;
    public static final int COLOR_INVERSE_MUTED = Color.argb(163, 255, 255, 255);
    public static final int COLOR_POSITIVE = Color.rgb(46, 125, 91);
    public static final int COLOR_NEGATIVE = Color.rgb(192, 69, 62);
    public static final int COLOR_WARNING = Color.rgb(168, 118, 31);
    public static final int COLOR_RIPPLE_LIGHT = Color.argb(24, 21, 22, 26);
    public static final int COLOR_RIPPLE_DARK = Color.argb(36, 255, 255, 255);
    public static final int COLOR_BAR_MUTED = Color.argb(56, 21, 22, 26);
    public static final int COLOR_BAR_EMPTY = Color.argb(22, 21, 22, 26);
    public static final int COLOR_TRACK_LIGHT = Color.argb(18, 21, 22, 26);
    public static final int COLOR_TRACK_DARK = Color.argb(46, 255, 255, 255);
    public static final int COLOR_INVERSE_CHIP = Color.argb(30, 255, 255, 255);
    public static final int COLOR_INVERSE_LINE = Color.argb(26, 255, 255, 255);

    private final Activity activity;
    private final BooleanSupplier inverseSupplier;

    public FitnessUi(Activity activity, BooleanSupplier inverseSupplier) {
        this.activity = activity;
        this.inverseSupplier = inverseSupplier;
    }

    public boolean inverse() {
        return inverseSupplier.getAsBoolean();
    }

    public int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    // ── 타이포그래피 ──────────────────────────────────────────────────

    public TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(mappedTextColor(color));
        view.setIncludeFontPadding(true);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    public TextView num(String value, int sp, int color, boolean bold) {
        TextView view = text(value, sp, color, bold);
        view.setFontFeatureSettings("tnum");
        return view;
    }

    public TextView caption(String value, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(11);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(0.08f);
        return view;
    }

    public int mappedTextColor(int color) {
        if (!inverse()) {
            return color;
        }
        if (color == COLOR_TEXT) {
            return COLOR_INVERSE_TEXT;
        }
        if (color == COLOR_MUTED || color == COLOR_TERTIARY) {
            return COLOR_INVERSE_MUTED;
        }
        return color;
    }

    public TextView labelView(String value) {
        return caption(value, inverse() ? COLOR_INVERSE_MUTED : COLOR_MUTED);
    }

    public TextView titleView(String value) {
        TextView view = text(value, 27, inverse() ? COLOR_INVERSE_TEXT : COLOR_TEXT, true);
        view.setPadding(0, dp(4), 0, dp(18));
        return view;
    }

    public View sectionHeader(String labelText, String actionText, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(26), 0, dp(10));

        TextView labelView = caption(labelText, inverse() ? COLOR_INVERSE_MUTED : COLOR_MUTED);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (actionText != null && action != null) {
            TextView actionView = text(actionText + " ›", 13, inverse() ? COLOR_INVERSE_MUTED : COLOR_TERTIARY, true);
            actionView.setPadding(dp(12), dp(4), 0, dp(4));
            actionView.setClickable(true);
            actionView.setFocusable(true);
            actionView.setOnClickListener(v -> action.run());
            row.addView(actionView);
        }

        return row;
    }

    public TextView textAction(String value, int color, Runnable action) {
        TextView view = text(value, 14, color, true);
        view.setPadding(dp(4), dp(8), dp(12), dp(8));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    // ── 표면 (surface) ────────────────────────────────────────────────

    public LinearLayout card() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        boolean inverseScreen = inverse();
        card.setBackground(borderDrawable(inverseScreen ? COLOR_PRIMARY_HI : COLOR_SURFACE,
                inverseScreen ? COLOR_INVERSE_LINE : COLOR_BORDER, dp(16)));
        card.setElevation(inverseScreen ? dp(0) : dp(2));
        card.setLayoutParams(fullWidthParams(dp(12)));
        return card;
    }

    public LinearLayout heroCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setBackground(heroBackground());
        card.setElevation(dp(8));
        card.setLayoutParams(fullWidthParams(dp(12)));
        return card;
    }

    private Drawable heroBackground() {
        GradientDrawable base = new GradientDrawable();
        base.setColor(COLOR_PRIMARY);
        base.setCornerRadius(dp(24));

        GradientDrawable gloss = new GradientDrawable();
        gloss.setShape(GradientDrawable.RECTANGLE);
        gloss.setCornerRadius(dp(24));
        gloss.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gloss.setGradientCenter(0.88f, 0.02f);
        gloss.setGradientRadius(dp(280));
        gloss.setColors(new int[]{Color.argb(24, 255, 255, 255), Color.argb(0, 255, 255, 255)});

        return new LayerDrawable(new Drawable[]{base, gloss});
    }

    public GradientDrawable borderDrawable(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public Drawable rippleDrawable(int fill, int stroke, int radius, int rippleColor) {
        GradientDrawable background = borderDrawable(fill, stroke, radius);
        GradientDrawable mask = borderDrawable(Color.WHITE, Color.WHITE, radius);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), background, mask);
    }

    public View hairline(int color) {
        View line = new View(activity);
        line.setBackgroundColor(color);
        return line;
    }

    // ── 버튼 / 칩 ─────────────────────────────────────────────────────

    public Button button(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        boolean inverseScreen = inverse();
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setStateListAnimator(null);
        int fill = primary ? (inverseScreen ? COLOR_SURFACE : COLOR_PRIMARY)
                : (inverseScreen ? COLOR_PRIMARY_HI : COLOR_SURFACE);
        int stroke = primary ? fill : (inverseScreen ? COLOR_RIPPLE_DARK : COLOR_BORDER);
        int textColor;
        if (primary) {
            textColor = inverseScreen ? COLOR_TEXT : COLOR_INVERSE_TEXT;
        } else {
            textColor = inverseScreen ? COLOR_INVERSE_TEXT : COLOR_TEXT;
        }
        button.setTextColor(textColor);
        int rippleColor = (primary && !inverseScreen) || (!primary && inverseScreen)
                ? COLOR_RIPPLE_DARK
                : COLOR_RIPPLE_LIGHT;
        button.setBackground(rippleDrawable(fill, stroke, dp(999), rippleColor));
        button.setOnClickListener(listener);
        return button;
    }

    public Button filterButton(String text) {
        Button button = button(text, false, null);
        button.setTextSize(13);
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        return button;
    }

    public void styleFilterButton(Button button, boolean active) {
        boolean inverseScreen = inverse();
        if (inverseScreen) {
            button.setTextColor(active ? COLOR_TEXT : COLOR_INVERSE_MUTED);
            int fill = active ? COLOR_SURFACE : COLOR_INVERSE_CHIP;
            button.setBackground(rippleDrawable(fill, fill, dp(999), COLOR_RIPPLE_LIGHT));
            return;
        }
        button.setTextColor(active ? COLOR_INVERSE_TEXT : COLOR_MUTED);
        int fill = active ? COLOR_PRIMARY : COLOR_SUBTLE;
        button.setBackground(rippleDrawable(fill, fill, dp(999), active ? COLOR_RIPPLE_DARK : COLOR_RIPPLE_LIGHT));
    }

    public View buttonRow(View first, View second) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        right.setMargins(dp(5), 0, 0, 0);
        row.addView(first, left);
        row.addView(second, right);
        return row;
    }

    // ── 입력 (input) ──────────────────────────────────────────────────

    public EditText input(String hint, String value) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTextSize(15);
        boolean inverseScreen = inverse();
        input.setTextColor(inverseScreen ? COLOR_INVERSE_TEXT : COLOR_TEXT);
        input.setHintTextColor(inverseScreen ? COLOR_INVERSE_MUTED : COLOR_TERTIARY);
        input.setMinHeight(dp(48));
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        input.setBackground(borderDrawable(inverseScreen ? COLOR_PRIMARY : COLOR_SUBTLE,
                inverseScreen ? COLOR_RIPPLE_DARK : COLOR_SUBTLE, dp(12)));
        return input;
    }

    public EditText searchField(String hint) {
        EditText input = input(hint, "");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(borderDrawable(inverse() ? COLOR_PRIMARY : COLOR_SUBTLE,
                inverse() ? COLOR_RIPPLE_DARK : COLOR_SUBTLE, dp(999)));
        return input;
    }

    public EditText numberInput(String hint, String value) {
        EditText input = input(hint, value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFontFeatureSettings("tnum");
        return input;
    }

    public EditText decimalInput(String hint, String value) {
        EditText input = input(hint, value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setFontFeatureSettings("tnum");
        return input;
    }

    public TextView fieldLabel(String value) {
        TextView label = caption(value, COLOR_MUTED);
        label.setPadding(0, dp(14), 0, dp(6));
        return label;
    }

    public View labeledFieldColumn(String label, View field) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = caption(label, inverse() ? COLOR_INVERSE_MUTED : COLOR_MUTED);
        labelView.setPadding(0, 0, 0, dp(6));
        column.addView(labelView);
        column.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return column;
    }

    public LinearLayout form() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        return form;
    }

    public void addAll(LinearLayout form, View... views) {
        for (View view : views) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, dp(8), 0, 0);
            form.addView(view, params);
        }
    }

    // ── 컴포넌트 ──────────────────────────────────────────────────────

    public View keyValue(String key, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(9), 0, 0);

        TextView keyView = text(key, 14, COLOR_MUTED, false);
        TextView valueView = num(value, 14, COLOR_TEXT, true);
        valueView.setGravity(Gravity.END);

        row.addView(keyView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView);
        return row;
    }

    public View inlineStat(String label, String value, boolean onDark) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(caption(label, onDark ? COLOR_INVERSE_MUTED : COLOR_MUTED));
        TextView valueView = num(value, 16, onDark ? COLOR_INVERSE_TEXT : COLOR_TEXT, true);
        valueView.setPadding(0, dp(3), 0, 0);
        cell.addView(valueView);
        return cell;
    }

    public View statusDotBadge(String labelText, int dotColor, boolean onDark) {
        LinearLayout badge = new LinearLayout(activity);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        int fill = onDark ? COLOR_INVERSE_CHIP : COLOR_SUBTLE;
        badge.setBackground(borderDrawable(fill, fill, dp(999)));
        badge.setPadding(dp(10), dp(5), dp(12), dp(5));

        View dot = new View(activity);
        dot.setBackground(borderDrawable(dotColor, dotColor, dp(999)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.setMargins(0, 0, dp(6), 0);
        badge.addView(dot, dotParams);

        badge.addView(text(labelText, 12, onDark ? COLOR_INVERSE_TEXT : COLOR_TEXT, true));
        return badge;
    }

    public View statTile(String label, String value, String meta, boolean inverseTile, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(16), dp(14), dp(16), dp(14));
        tile.setMinimumHeight(dp(92));
        int fill = inverseTile ? COLOR_PRIMARY : COLOR_SURFACE;
        int stroke = inverseTile ? COLOR_PRIMARY : COLOR_BORDER;
        if (listener != null) {
            tile.setBackground(rippleDrawable(fill, stroke, dp(14), inverseTile ? COLOR_RIPPLE_DARK : COLOR_RIPPLE_LIGHT));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setOnClickListener(listener);
        } else {
            tile.setBackground(borderDrawable(fill, stroke, dp(14)));
        }
        tile.setElevation(inverseTile ? dp(5) : dp(2));

        tile.addView(caption(label, inverseTile ? COLOR_INVERSE_MUTED : COLOR_MUTED));
        TextView valueView = num(value, 21, inverseTile ? COLOR_INVERSE_TEXT : COLOR_TEXT, true);
        valueView.setPadding(0, dp(7), 0, 0);
        tile.addView(valueView);
        if (meta != null) {
            TextView metaView = text(meta, 11, inverseTile ? COLOR_INVERSE_MUTED : COLOR_TERTIARY, false);
            metaView.setPadding(0, dp(3), 0, 0);
            tile.addView(metaView);
        }
        return tile;
    }

    public View glyphCircle(String glyph, boolean onDark) {
        TextView circle = text(glyph, 14, onDark ? COLOR_INVERSE_TEXT : COLOR_MUTED, true);
        circle.setGravity(Gravity.CENTER);
        int fill = onDark ? COLOR_INVERSE_CHIP : COLOR_SUBTLE;
        circle.setBackground(borderDrawable(fill, fill, dp(999)));
        circle.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return circle;
    }

    public View orderBadge(int order, boolean onDark) {
        TextView badge = num(String.valueOf(order), 13, onDark ? COLOR_INVERSE_TEXT : COLOR_TEXT, true);
        badge.setGravity(Gravity.CENTER);
        int fill = onDark ? COLOR_INVERSE_CHIP : COLOR_SUBTLE;
        badge.setBackground(borderDrawable(fill, fill, dp(999)));
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        return badge;
    }

    public View compactOrderBadge(int order) {
        TextView badge = num(String.valueOf(order), 11, COLOR_TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(borderDrawable(COLOR_SUBTLE, COLOR_SUBTLE, dp(999)));
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        return badge;
    }

    public View recordListRow(String glyph, String primaryText, String captionText, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(0, dp(9), 0, dp(9));
        if (listener != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(listener);
        }

        row.addView(glyphCircle(glyph, false));
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), 0, 0, 0);
        TextView primary = text(primaryText, 14, COLOR_TEXT, true);
        primary.setLineSpacing(dp(2), 1f);
        column.addView(primary);
        if (captionText != null) {
            TextView captionView = text(captionText, 12, COLOR_TERTIARY, false);
            captionView.setPadding(0, dp(2), 0, 0);
            column.addView(captionView);
        }
        row.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    public LinearLayout rowsCard(List<View> rows) {
        LinearLayout card = card();
        card.setPadding(dp(18), dp(8), dp(18), dp(8));
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                View line = hairline(COLOR_BORDER);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                lineParams.setMargins(dp(52), 0, 0, 0);
                card.addView(line, lineParams);
            }
            card.addView(rows.get(i), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }
        return card;
    }

    public View progressBar(double ratio, boolean onDark) {
        float clamped = (float) Math.max(0, Math.min(1, ratio));
        LinearLayout track = new LinearLayout(activity);
        track.setOrientation(LinearLayout.HORIZONTAL);
        int trackColor = onDark ? COLOR_TRACK_DARK : COLOR_TRACK_LIGHT;
        track.setBackground(borderDrawable(trackColor, trackColor, dp(999)));

        int fillColor = onDark ? COLOR_INVERSE_TEXT : COLOR_PRIMARY;
        View fill = new View(activity);
        fill.setBackground(borderDrawable(fillColor, fillColor, dp(999)));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(6), clamped));
        View rest = new View(activity);
        track.addView(rest, new LinearLayout.LayoutParams(0, dp(6), 1f - clamped));
        return track;
    }

    public View volumeTrendChart(List<Double> values) {
        final List<Double> points = values == null ? java.util.Collections.emptyList() : new java.util.ArrayList<>(values);
        return new View(activity) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int left = dp(12);
                int right = getWidth() - dp(12);
                int top = dp(12);
                int bottom = getHeight() - dp(14);
                paint.setStrokeWidth(dp(1));
                paint.setColor(COLOR_BORDER);
                canvas.drawLine(left, bottom, right, bottom, paint);
                if (points.isEmpty()) {
                    paint.setTextSize(dp(12));
                    paint.setColor(COLOR_MUTED);
                    canvas.drawText("이전 기록 없음", left, top + dp(14), paint);
                    return;
                }

                double max = 1;
                for (Double value : points) {
                    if (value != null) {
                        max = Math.max(max, value);
                    }
                }
                paint.setColor(COLOR_PRIMARY);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                float previousX = 0;
                float previousY = 0;
                for (int index = 0; index < points.size(); index++) {
                    double value = points.get(index) == null ? 0 : points.get(index);
                    float x = points.size() == 1
                            ? (left + right) / 2f
                            : left + (right - left) * index / (float) (points.size() - 1);
                    float y = bottom - (float) ((bottom - top) * value / max);
                    if (index > 0) {
                        canvas.drawLine(previousX, previousY, x, y, paint);
                    }
                    previousX = x;
                    previousY = y;
                }
                paint.setStyle(Paint.Style.FILL);
                for (int index = 0; index < points.size(); index++) {
                    double value = points.get(index) == null ? 0 : points.get(index);
                    float x = points.size() == 1
                            ? (left + right) / 2f
                            : left + (right - left) * index / (float) (points.size() - 1);
                    float y = bottom - (float) ((bottom - top) * value / max);
                    canvas.drawCircle(x, y, dp(4), paint);
                }
            }
        };
    }

    public View emptyStateCard(String message, String hint) {
        LinearLayout card = card();
        card.addView(text(message, 14, COLOR_MUTED, false));
        if (hint != null) {
            TextView hintView = text(hint, 12, COLOR_TERTIARY, false);
            hintView.setPadding(0, dp(5), 0, 0);
            card.addView(hintView);
        }
        return card;
    }

    public void cardHeader(LinearLayout card, String title, String meta) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = text(title, 17, COLOR_TEXT, true);
        header.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (meta != null) {
            TextView metaView = text(meta, 12, COLOR_TERTIARY, false);
            header.addView(metaView);
        }

        card.addView(header);
    }

    /** 루틴 카드: 탭하면 운동 시작, 선택적으로 "세부 보기" 버튼 표시. */
    public View routineCard(String routineName, int exerciseCount, boolean showDetailAction,
                            Runnable onStart, Runnable onDetail) {
        LinearLayout card = card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onStart.run());

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.addView(glyphCircle("루", false));
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), 0, 0, 0);
        column.addView(text(routineName, 16, COLOR_TEXT, true));
        TextView meta = text(exerciseCount + "개 종목 · 탭하여 시작", 12, COLOR_MUTED, false);
        meta.setPadding(0, dp(2), 0, 0);
        column.addView(meta);
        headerRow.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = text("›", 20, COLOR_TERTIARY, false);
        headerRow.addView(chevron);
        card.addView(headerRow);

        if (showDetailAction) {
            Button detailButton = button("세부 보기", false, v -> onDetail.run());
            card.addView(detailButton, fullWidthParams(dp(14)));
        }
        return card;
    }

    public View quickStartRoutineCard(String routineName, int exerciseCount, Runnable onStart, Runnable onDetail) {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(rippleDrawable(COLOR_PRIMARY, COLOR_PRIMARY, dp(16), COLOR_RIPPLE_DARK));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onStart.run());

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView routineGlyph = text("루", 12, COLOR_INVERSE_TEXT, true);
        routineGlyph.setGravity(Gravity.CENTER);
        routineGlyph.setBackground(borderDrawable(COLOR_INVERSE_CHIP, COLOR_INVERSE_CHIP, dp(999)));
        routineGlyph.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        headerRow.addView(routineGlyph);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(9), 0, 0, 0);
        column.addView(text(routineName, 14, COLOR_INVERSE_TEXT, true));
        TextView meta = text(exerciseCount + "개 종목 · 탭하여 시작", 10, COLOR_INVERSE_MUTED, false);
        meta.setPadding(0, dp(2), 0, 0);
        column.addView(meta);
        headerRow.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        headerRow.addView(text("›", 16, COLOR_INVERSE_MUTED, false));
        card.addView(headerRow);
        return card;
    }

    /** 운동 상세 문자열(종목 행 + "   " 들여쓰기 세트 행)을 구조화된 행으로 렌더링한다. */
    public void appendSessionDetailRows(LinearLayout card, List<String> details) {
        for (String detail : details) {
            if (detail.startsWith("   ")) {
                LinearLayout setRow = new LinearLayout(activity);
                setRow.setOrientation(LinearLayout.HORIZONTAL);
                setRow.setGravity(Gravity.CENTER_VERTICAL);
                setRow.setPadding(dp(14), dp(5), 0, dp(5));
                TextView setText = num(detail.trim(), 13, COLOR_MUTED, false);
                setRow.addView(setText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                card.addView(setRow);
            } else {
                TextView exerciseText = text(detail, 14, COLOR_TEXT, true);
                exerciseText.setPadding(0, dp(10), 0, dp(2));
                card.addView(exerciseText);
            }
        }
    }

    // ── 레이아웃 파라미터 ─────────────────────────────────────────────

    public LinearLayout.LayoutParams fullWidthParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, topMargin, 0, 0);
        return params;
    }

    public LinearLayout tileRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    public LinearLayout.LayoutParams tileParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(first ? 0 : dp(10), 0, 0, 0);
        return params;
    }

    public LinearLayout.LayoutParams metaCellParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (!first) {
            params.setMargins(dp(12), 0, 0, 0);
        }
        return params;
    }

    public LinearLayout.LayoutParams fieldCellParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(first ? 0 : dp(10), 0, 0, 0);
        return params;
    }

    public LinearLayout pickerRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    public LinearLayout.LayoutParams pickerCellParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(first ? 0 : dp(6), 0, 0, 0);
        return params;
    }

    // ── 입력값 파싱 ───────────────────────────────────────────────────

    public static String inputText(EditText input) {
        return input.getText().toString();
    }

    public static int parseInt(EditText input, int fallback) {
        try {
            return Integer.parseInt(inputText(input).trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    public static double parseDouble(EditText input, double fallback) {
        try {
            return Double.parseDouble(inputText(input).trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    public static Integer optionalInt(EditText input) {
        try {
            String value = inputText(input).trim();
            return value.isEmpty() ? null : Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    public static Double optionalDouble(EditText input) {
        try {
            String value = inputText(input).trim();
            return value.isEmpty() ? null : Double.parseDouble(value);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    // ── 포맷터 ────────────────────────────────────────────────────────

    public static String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    public static String formatVolume(double kg) {
        if (kg == Math.rint(kg)) {
            return String.format(Locale.KOREAN, "%,d", (long) kg);
        }
        return String.format(Locale.KOREAN, "%,.1f", kg);
    }

    public static String formatDuration(int durationSeconds) {
        if (durationSeconds <= 0) {
            return "미기록";
        }
        int hours = durationSeconds / 3600;
        int minutes = (durationSeconds % 3600) / 60;
        if (hours > 0) {
            return minutes > 0 ? hours + "시간 " + minutes + "분" : hours + "시간";
        }
        if (minutes > 0) {
            return minutes + "분";
        }
        return durationSeconds + "초";
    }

    public static String formatElapsed(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainder = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }

    public static String formatStartTime(String startedAt) {
        if (startedAt == null || startedAt.trim().isEmpty()) {
            return "미기록";
        }
        try {
            return OffsetDateTime.parse(startedAt.trim()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception error) {
            return startedAt;
        }
    }

    /** "YYYY. MM. DD  본문" 형태 문자열에서 앞 날짜 토큰을 제거한다. */
    public static String stripLeadingDate(String value) {
        int split = value.indexOf("  ");
        return split > 0 ? value.substring(split + 2) : value;
    }
}
