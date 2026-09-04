package com.yeonsik.fitnessapp.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Fitness 앱의 semantic token과 공통 View를 제공하는 UI 팩토리.
 * 색 토큰, 타이포그래피, 표면(카드/타일), 버튼/칩, 입력창, 리스트 행, 포맷터를 담당한다.
 * 화면 상태를 소유하지 않으며, 다크 테마 여부는 생성 시 주입된 supplier로 판단한다.
 * 일반 컴포넌트는 평면 surface와 Pastel Blue semantic을 사용하고, Hero만 tonal blue gradient를 사용한다.
 */
public final class FitnessUi {
    // ── Light semantic tokens ─────────────────────────────────────────
    public static final int COLOR_BACKGROUND = 0xFFF7F9FC;
    public static final int COLOR_SURFACE = 0xFFFFFFFF;
    public static final int COLOR_SUBTLE = 0xFFF0F5F9;
    public static final int COLOR_TEXT = 0xFF111827;
    public static final int COLOR_MUTED = 0xFF667085;
    // Legacy tertiary text is kept as an alias so old callers use the same semantic secondary token.
    public static final int COLOR_TERTIARY = COLOR_MUTED;
    public static final int COLOR_BORDER = 0xFFDCE5EC;
    public static final int COLOR_PASTEL_BLUE = 0xFFA9D6F5;
    public static final int COLOR_BLUE_CONTAINER = 0xFFEAF6FF;
    public static final int COLOR_BLUE_INK = 0xFF173B55;

    // Compatibility names for the previous API. New code should use pastelBlue()/blueContainer().
    public static final int COLOR_PRIMARY = COLOR_PASTEL_BLUE;
    public static final int COLOR_INVERSE_TEXT = Color.WHITE;
    public static final int COLOR_INVERSE_MUTED = 0xE6FFFFFF;

    // Status colors intentionally remain separate from brand/selection colors.
    public static final int COLOR_POSITIVE = 0xFF2E7D5B;
    public static final int COLOR_NEGATIVE = 0xFFC0453E;
    // Amber chosen to keep normal-size warning text above WCAG AA on light surfaces.
    public static final int COLOR_WARNING = 0xFF8A5A00;

    // Chart semantic colors are independent from interaction/selection state.
    public static final int COLOR_CHART_CALORIES = 0xFF2F6F9F;
    public static final int COLOR_CHART_CARBS = 0xFF2B7A78;
    public static final int COLOR_CHART_PROTEIN = 0xFF8B5E3C;
    public static final int COLOR_CHART_FAT = 0xFF8A5A83;

    public static final int COLOR_RIPPLE_LIGHT = 0x18111827;
    public static final int COLOR_RIPPLE_DARK = 0x24F5F8FA;
    public static final int COLOR_BAR_MUTED = 0x38111827;
    public static final int COLOR_BAR_EMPTY = 0x16111827;
    public static final int COLOR_TRACK_LIGHT = 0x12111827;
    public static final int COLOR_TRACK_DARK = 0x2EF5F8FA;
    public static final int COLOR_INVERSE_CHIP = 0x1EFFFFFF;
    public static final int COLOR_INVERSE_LINE = 0x1AFFFFFF;

    // ── Dark semantic tokens ──────────────────────────────────────────
    public static final int COLOR_D_BACKGROUND = 0xFF0E141A;
    public static final int COLOR_D_SURFACE = 0xFF151C23;
    public static final int COLOR_D_SUBTLE = 0xFF1B2530;
    public static final int COLOR_D_TEXT = 0xFFF5F8FA;
    public static final int COLOR_D_MUTED = 0xFFA6B0BA;
    public static final int COLOR_D_TERTIARY = COLOR_D_MUTED;
    public static final int COLOR_D_BORDER = 0xFF2A3742;
    public static final int COLOR_D_PASTEL_BLUE = 0xFF8FC8EE;
    public static final int COLOR_D_BLUE_CONTAINER = 0xFF18384D;
    // Verified against COLOR_D_BLUE_CONTAINER: contrast is above WCAG AA for normal text.
    public static final int COLOR_D_BLUE_INK = 0xFFD9F0FF;
    public static final int COLOR_D_ON_PASTEL_BLUE = 0xFF0E2938;
    public static final int COLOR_D_HERO_END = 0xFF214A63;
    // Dark Hero gradient range (#18384D -> #214A63): minimum normal-text contrast is 4.78:1.
    public static final int COLOR_D_HERO_MUTED = 0xFFAFBAC4;
    public static final int COLOR_D_HERO_BORDER = 0xFF2A526A;
    public static final int COLOR_D_POSITIVE = 0xFF69D39E;
    public static final int COLOR_D_NEGATIVE = 0xFFFF8A80;
    public static final int COLOR_D_WARNING = 0xFFFFCA68;

    // Compatibility names for the previous API.
    public static final int COLOR_D_ACCENT = COLOR_D_PASTEL_BLUE;
    public static final int COLOR_D_ON_ACCENT_MUTED = 0xB80E2938;
    public static final int COLOR_D_CHIP_ON_ACCENT = 0x1E0E2938;
    public static final int COLOR_D_LINE_ON_ACCENT = 0x1E0E2938;
    public static final int COLOR_D_TRACK_ON_ACCENT = 0x300E2938;
    public static final int COLOR_D_BAR_MUTED = 0x78F5F8FA;
    public static final int COLOR_D_BAR_EMPTY = 0x1AF5F8FA;

    // ── Shape/depth tokens ─────────────────────────────────────────────
    public static final int CARD_RADIUS_DP = 16;
    public static final int HERO_RADIUS_DP = 24;
    public static final int INPUT_RADIUS_DP = 12;
    public static final int BUTTON_RADIUS_DP = 12;
    public static final int CHIP_RADIUS_DP = 999;
    public static final int SHEET_RADIUS_DP = 24;
    public static final int DEPTH_FLAT_DP = 0;
    public static final int DEPTH_SURFACE_DP = 1;
    public static final int DEPTH_EMPHASIS_DP = 3;
    private static final int COLOR_SHADOW_LIGHT = 0x26000000;
    private static final int COLOR_SHADOW_DARK = 0x66000000;

    // ── Layout/spacing tokens ─────────────────────────────────────────
    // Keep shell rhythm in one place so device review changes do not require
    // editing every screen renderer.
    public static final int PAGE_HORIZONTAL_PADDING_DP = 20;
    public static final int PAGE_TOP_PADDING_DP = 40;
    public static final int PAGE_BOTTOM_PADDING_DP = 28;
    public static final int SCREEN_TITLE_TOP_SPACING_DP = 4;
    public static final int SCREEN_TITLE_BOTTOM_SPACING_DP = 18;
    public static final int SECTION_TOP_SPACING_DP = 26;
    public static final int SECTION_BOTTOM_SPACING_DP = 10;
    public static final int CARD_GAP_DP = 12;
    public static final int FIELD_LABEL_GAP_DP = 6;
    public static final int FORM_ITEM_GAP_DP = 8;
    public static final int BUTTON_GAP_DP = 5;

    // Bottom navigation keeps a 48dp touch target but no longer renders each
    // tab as a prominent pill/card.
    public static final int NAV_ITEM_RADIUS_DP = 12;
    public static final int NAV_ITEM_MIN_HEIGHT_DP = 48;
    public static final int NAV_BAR_HORIZONTAL_PADDING_DP = 8;
    public static final int NAV_BAR_TOP_PADDING_DP = 8;
    public static final int NAV_BAR_BOTTOM_PADDING_DP = 12;
    public static final int NAV_ITEM_GAP_DP = 4;
    public static final int NAV_MARKER_SLOT_HEIGHT_DP = 14;
    public static final int NAV_ACTIVE_MARKER_WIDTH_DP = 24;
    public static final int NAV_ACTIVE_MARKER_HEIGHT_DP = 4;
    public static final int NAV_PROGRESS_MARKER_SIZE_DP = 6;

    private final Activity activity;
    private final BooleanSupplier inverseSupplier;
    private final List<Dialog> dialogStack = new ArrayList<>();
    private Dialog activeDialog;

    public FitnessUi(Activity activity, BooleanSupplier inverseSupplier) {
        this.activity = activity;
        this.inverseSupplier = inverseSupplier;
    }

    /** 다크 테마 활성 여부. 이름은 반전 문법("다크 = 라이트의 반전 매핑")에서 온다. */
    public boolean inverse() {
        return inverseSupplier.getAsBoolean();
    }

    public boolean dark() {
        return inverse();
    }

    // ── Semantic accessors ─────────────────────────────────────────────

    public int pageBg() {
        return dark() ? COLOR_D_BACKGROUND : COLOR_BACKGROUND;
    }

    public int background() {
        return pageBg();
    }

    public int surface() {
        return dark() ? COLOR_D_SURFACE : COLOR_SURFACE;
    }

    public int subtle() {
        return dark() ? COLOR_D_SUBTLE : COLOR_SUBTLE;
    }

    public int pastelBlue() {
        return dark() ? COLOR_D_PASTEL_BLUE : COLOR_PASTEL_BLUE;
    }

    public int blueContainer() {
        return dark() ? COLOR_D_BLUE_CONTAINER : COLOR_BLUE_CONTAINER;
    }

    public int blueInk() {
        return dark() ? COLOR_D_BLUE_INK : COLOR_BLUE_INK;
    }

    /** Pastel Blue 표면 위에서 사용하는 대비 잉크. */
    public int onPastelBlue() {
        return dark() ? COLOR_D_ON_PASTEL_BLUE : COLOR_BLUE_INK;
    }

    /** 선택 상태의 배경과 전경은 테마별 Blue Container/Blue Ink 쌍으로 고정한다. */
    public int selectedSurface() {
        return blueContainer();
    }

    public int selectedInk() {
        return blueInk();
    }

    public int tonalSurface() {
        return blueContainer();
    }

    public int tonalInk() {
        return blueInk();
    }

    /** Hero에서만 사용하는 정적 tonal blue gradient의 전경색. */
    public int heroInk() {
        return dark() ? COLOR_D_TEXT : COLOR_BLUE_INK;
    }

    public int heroMuted() {
        return dark() ? COLOR_D_HERO_MUTED : COLOR_BLUE_INK;
    }

    public int heroBorder() {
        return dark() ? COLOR_D_HERO_BORDER : COLOR_BORDER;
    }

    /** 강조 표면은 더 이상 흑백 반전이 아니라 Pastel Blue semantic이다. */
    public int accent() {
        return pastelBlue();
    }

    public int onAccent() {
        return onPastelBlue();
    }

    public int onAccentMuted() {
        return dark() ? COLOR_D_ON_ACCENT_MUTED : 0xB8173B55;
    }

    /** @deprecated 일반 surface에는 hologram 전경색을 사용하지 않는다. */
    @Deprecated
    public int onVibrant() {
        return onAccent();
    }

    /** @deprecated 일반 surface에는 hologram 전경색을 사용하지 않는다. */
    @Deprecated
    public int onVibrantMuted() {
        return onAccentMuted();
    }

    /** @deprecated tonalSurfaceDrawable() 또는 chip()을 사용한다. */
    @Deprecated
    public int chipOnVibrant() {
        return withAlpha(onAccent(), 30);
    }

    /** @deprecated tonal surface 전용 progress API를 사용한다. */
    @Deprecated
    public int trackOnVibrant() {
        return withAlpha(onAccent(), 46);
    }

    public int ink() {
        return dark() ? COLOR_D_TEXT : COLOR_TEXT;
    }

    public int inkMuted() {
        return dark() ? COLOR_D_MUTED : COLOR_MUTED;
    }

    public int inkTertiary() {
        return dark() ? COLOR_D_TERTIARY : COLOR_TERTIARY;
    }

    public int border() {
        return dark() ? COLOR_D_BORDER : COLOR_BORDER;
    }

    public int chipOnAccent() {
        return withAlpha(onAccent(), 30);
    }

    public int trackOnSurface() {
        return dark() ? COLOR_TRACK_DARK : COLOR_TRACK_LIGHT;
    }

    public int trackOnAccent() {
        return withAlpha(onAccent(), 46);
    }

    public int rippleOnSurface() {
        return dark() ? COLOR_RIPPLE_DARK : COLOR_RIPPLE_LIGHT;
    }

    public int rippleOnAccent() {
        return dark() ? COLOR_RIPPLE_LIGHT : COLOR_RIPPLE_DARK;
    }

    public int barMuted() {
        return dark() ? COLOR_D_BAR_MUTED : COLOR_BAR_MUTED;
    }

    public int barEmpty() {
        return dark() ? COLOR_D_BAR_EMPTY : COLOR_BAR_EMPTY;
    }

    public int statusColor(int color) {
        if (color == COLOR_MUTED || color == COLOR_TERTIARY) {
            return dark() ? COLOR_D_MUTED : COLOR_MUTED;
        }
        if (color == COLOR_POSITIVE) {
            return dark() ? COLOR_D_POSITIVE : COLOR_POSITIVE;
        }
        if (color == COLOR_NEGATIVE) {
            return dark() ? COLOR_D_NEGATIVE : COLOR_NEGATIVE;
        }
        if (color == COLOR_WARNING) {
            return dark() ? COLOR_D_WARNING : COLOR_WARNING;
        }
        return color;
    }

    public int onStatus(int color) {
        return dark() ? COLOR_D_BACKGROUND : COLOR_SURFACE;
    }

    /** 차트 계열은 상호작용 상태와 분리된 semantic 색상이다. */
    public int chartColor(int variant) {
        switch (Math.floorMod(variant, 4)) {
            case 1:
                return dark() ? 0xFF6CD5C7 : COLOR_CHART_CARBS;
            case 2:
                return dark() ? 0xFFF2B880 : COLOR_CHART_PROTEIN;
            case 3:
                return dark() ? 0xFFD6A5D8 : COLOR_CHART_FAT;
            default:
                return dark() ? COLOR_D_PASTEL_BLUE : COLOR_CHART_CALORIES;
        }
    }

    /** @deprecated 차트는 chartColor()를 사용하며 seed로 색을 정하지 않는다. */
    @Deprecated
    public int vibrantColor(int variant) {
        return chartColor(variant);
    }

    /** @deprecated 차트는 chartColor()를 사용한다. */
    @Deprecated
    public int hologramAccentColor(int variant) {
        return chartColor(variant);
    }

    /** @deprecated 일반 상태에는 tonal surface를 사용한다. */
    @Deprecated
    public Drawable vibrantBackground(int variant, int radius) {
        return tonalSurfaceDrawable(radius);
    }

    /** @deprecated seed 기반 색상 선택을 제거했다. */
    @Deprecated
    public Drawable vibrantRippleDrawable(String seed, int radius) {
        return tonalRippleDrawable(radius);
    }

    /** @deprecated 일반 상태에는 tonal surface를 사용한다. */
    @Deprecated
    public Drawable vibrantRippleDrawable(int variant, int radius) {
        return tonalRippleDrawable(radius);
    }

    public Drawable selectedStateRippleDrawable(int radius) {
        return tonalRippleDrawable(radius);
    }

    /** 선택 가능한 행/박스에 공통 선택 표면과 깊이를 적용한다. */
    public void styleSelection(View view, boolean selected, int radius) {
        if (view == null) {
            return;
        }
        view.setSelected(selected);
        view.setBackground(selected
                ? selectedStateRippleDrawable(radius)
                : outlinedSurfaceRippleDrawable(radius));
        applyDepth(view, selected ? DEPTH_SURFACE_DP : DEPTH_FLAT_DP);
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(selected ? selectedInk() : inkMuted());
        }
    }

    public Drawable flatSurfaceDrawable(int radius) {
        return borderDrawable(surface(), Color.TRANSPARENT, radius);
    }

    public Drawable tonalSurfaceDrawable(int radius) {
        return borderDrawable(tonalSurface(), Color.TRANSPARENT, radius);
    }

    private Drawable selectedSurfaceDrawable(int radius) {
        return borderDrawable(selectedSurface(), pastelBlue(), radius);
    }

    public Drawable flatSurfaceRippleDrawable(int radius) {
        return rippleFor(flatSurfaceDrawable(radius), radius, rippleOnSurface());
    }

    public Drawable outlinedSurfaceRippleDrawable(int radius) {
        return rippleFor(borderDrawable(surface(), border(), radius), radius, rippleOnSurface());
    }

    public Drawable tonalRippleDrawable(int radius) {
        return rippleFor(selectedSurfaceDrawable(radius), radius, rippleOnAccent());
    }

    private Drawable rippleFor(Drawable background, int radius, int rippleColor) {
        GradientDrawable mask = borderDrawable(Color.WHITE, Color.TRANSPARENT, radius);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), background, mask);
    }

    /** 일반 배경을 적용한다. 애니메이션 listener를 등록하지 않는다. */
    public void setComponentBackground(View view, Drawable background) {
        if (view != null) {
            view.setBackground(background);
        }
    }

    /** @deprecated 일반 상태의 hologram/glow를 제거했으며 semantic 배경만 적용한다. */
    @Deprecated
    public void setHologramBackground(View view, Drawable background, int radius) {
        setComponentBackground(view, background);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(255, alpha)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    public int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** Applies the common page rhythm to a newly created scroll content host. */
    public void applyPageContentPadding(View view) {
        if (view == null) {
            return;
        }
        view.setPadding(
                dp(PAGE_HORIZONTAL_PADDING_DP),
                dp(PAGE_TOP_PADDING_DP),
                dp(PAGE_HORIZONTAL_PADDING_DP),
                dp(PAGE_BOTTOM_PADDING_DP)
        );
    }

    /** 공통 깊이 토큰. 다크 모드도 검은 그림자만 사용해 과도한 밝기를 피한다. */
    public void applyDepth(View view, int elevationDp) {
        if (view == null) {
            return;
        }
        int clampedElevation = Math.max(DEPTH_FLAT_DP,
                Math.min(DEPTH_EMPHASIS_DP, elevationDp));
        view.setElevation(dp(clampedElevation));
        view.setTranslationZ(0f);
        view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        view.setClipToOutline(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int shadow = dark() ? COLOR_SHADOW_DARK : COLOR_SHADOW_LIGHT;
            view.setOutlineAmbientShadowColor(shadow);
            view.setOutlineSpotShadowColor(shadow);
        }
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
        view.setTextColor(mappedTextColor(color));
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(0.08f);
        return view;
    }

    /**
     * 라이트 토큰으로 지정된 텍스트 색을 현재 테마 색으로 변환한다.
     * INVERSE_* 계열은 "강조 표면 위 텍스트"를 의미하므로 다크에서는 다크 잉크가 된다.
     */
    public int mappedTextColor(int color) {
        if (dark()) {
            if (color == COLOR_TEXT) {
                return COLOR_D_TEXT;
            }
            if (color == COLOR_MUTED || color == COLOR_TERTIARY) {
                return COLOR_D_MUTED;
            }
            if (color == COLOR_INVERSE_TEXT) {
                return COLOR_D_BLUE_INK;
            }
            if (color == COLOR_INVERSE_MUTED) {
                return COLOR_D_ON_ACCENT_MUTED;
            }
            if (color == COLOR_POSITIVE) {
                return COLOR_D_POSITIVE;
            }
            if (color == COLOR_NEGATIVE) {
                return COLOR_D_NEGATIVE;
            }
            if (color == COLOR_WARNING) {
                return COLOR_D_WARNING;
            }
        }
        return color;
    }

    public TextView labelView(String value) {
        return caption(value, COLOR_MUTED);
    }

    public TextView titleView(String value) {
        TextView view = text(value, 27, COLOR_TEXT, true);
        view.setLetterSpacing(-0.02f);
        view.setPadding(0, dp(SCREEN_TITLE_TOP_SPACING_DP), 0,
                dp(SCREEN_TITLE_BOTTOM_SPACING_DP));
        return view;
    }

    /** Shared page heading with an optional, Korean-first eyebrow. */
    public LinearLayout screenHeader(String eyebrow, String title) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        if (eyebrow != null && !eyebrow.trim().isEmpty()) {
            header.addView(labelView(eyebrow), fullWidthParams(0));
        }
        header.addView(titleView(title), fullWidthParams(0));
        return header;
    }

    public View sectionHeader(String labelText, String actionText, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(SECTION_TOP_SPACING_DP), 0, dp(SECTION_BOTTOM_SPACING_DP));

        TextView labelView = caption(labelText, COLOR_MUTED);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (actionText != null && action != null) {
            TextView actionView = text(actionText + " ›", 13, COLOR_TERTIARY, true);
            actionView.setMinWidth(dp(48));
            actionView.setMinimumWidth(dp(48));
            actionView.setMinHeight(dp(48));
            actionView.setMinimumHeight(dp(48));
            actionView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            actionView.setPadding(dp(12), 0, 0, 0);
            actionView.setClickable(true);
            actionView.setFocusable(true);
            actionView.setOnClickListener(v -> action.run());
            pressFeedback(actionView);
            row.addView(actionView);
        }

        return row;
    }

    public TextView textAction(String value, int color, Runnable action) {
        TextView view = text(value, 14, color, true);
        view.setMinWidth(dp(48));
        view.setMinimumWidth(dp(48));
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(8), 0, dp(12), 0);
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> action.run());
        pressFeedback(view);
        return view;
    }

    // ── 표면 (surface) ────────────────────────────────────────────────

    public LinearLayout card() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(flatSurfaceDrawable(dp(CARD_RADIUS_DP)));
        applyDepth(card, DEPTH_SURFACE_DP);
        card.setLayoutParams(fullWidthParams(dp(CARD_GAP_DP)));
        return card;
    }

    /** 기본 카드보다 outline 의미가 필요한 surface. */
    public LinearLayout outlinedCard() {
        LinearLayout card = card();
        card.setBackground(borderDrawable(surface(), border(), dp(CARD_RADIUS_DP)));
        applyDepth(card, DEPTH_FLAT_DP);
        return card;
    }

    public LinearLayout heroCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(heroBackground());
        applyDepth(card, DEPTH_SURFACE_DP);
        card.setLayoutParams(fullWidthParams(dp(CARD_GAP_DP)));
        return card;
    }

    private GradientDrawable heroBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark()
                        ? new int[]{COLOR_D_BLUE_CONTAINER, COLOR_D_HERO_END}
                        : new int[]{COLOR_BLUE_CONTAINER, COLOR_PASTEL_BLUE}
        );
        background.setCornerRadius(dp(HERO_RADIUS_DP));
        background.setStroke(dp(1), heroBorder());
        return background;
    }

    public GradientDrawable borderDrawable(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public Drawable rippleDrawable(int fill, int stroke, int radius, int rippleColor) {
        return rippleFor(borderDrawable(fill, stroke, radius), radius, rippleColor);
    }

    public View hairline(int color) {
        if (dark()) {
            if (color == COLOR_BORDER) {
                color = COLOR_D_BORDER;
            } else if (color == COLOR_INVERSE_LINE) {
                color = COLOR_D_LINE_ON_ACCENT;
            }
        }
        View line = new View(activity);
        line.setBackgroundColor(color);
        return line;
    }

    // ── 버튼 / 칩 ─────────────────────────────────────────────────────

    private Button buildButton(String text, int fill, int stroke, int textColor,
                               int radius, int depth, int rippleColor,
                               View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setStateListAnimator(null);
        button.setTextColor(textColor);
        button.setBackground(rippleDrawable(fill, stroke, dp(radius), rippleColor));
        applyDepth(button, depth);
        button.setOnClickListener(listener);
        pressFeedback(button);
        return button;
    }

    public Button primaryButton(String text, View.OnClickListener listener) {
        return buildButton(text, pastelBlue(), Color.TRANSPARENT, onPastelBlue(),
                BUTTON_RADIUS_DP, DEPTH_SURFACE_DP, rippleOnAccent(), listener);
    }

    public Button secondaryButton(String text, View.OnClickListener listener) {
        return buildButton(text, surface(), border(), ink(),
                BUTTON_RADIUS_DP, DEPTH_FLAT_DP, rippleOnSurface(), listener);
    }

    public Button tonalButton(String text, View.OnClickListener listener) {
        return buildButton(text, tonalSurface(), pastelBlue(), tonalInk(),
                BUTTON_RADIUS_DP, DEPTH_SURFACE_DP, rippleOnAccent(), listener);
    }

    public Button textButton(String text, View.OnClickListener listener) {
        return buildButton(text, Color.TRANSPARENT, Color.TRANSPARENT, blueInk(),
                BUTTON_RADIUS_DP, DEPTH_FLAT_DP, rippleOnSurface(), listener);
    }

    /** @deprecated 신규 코드는 primaryButton()/secondaryButton()을 사용한다. */
    @Deprecated
    public Button button(String text, boolean primary, View.OnClickListener listener) {
        return primary ? primaryButton(text, listener) : secondaryButton(text, listener);
    }

    public Button chip(String text, boolean selected, View.OnClickListener listener) {
        Button button = buildButton(text,
                selected ? selectedSurface() : surface(),
                selected ? pastelBlue() : border(),
                selected ? selectedInk() : inkMuted(),
                CHIP_RADIUS_DP, selected ? DEPTH_SURFACE_DP : DEPTH_FLAT_DP,
                selected ? rippleOnAccent() : rippleOnSurface(), listener);
        button.setTextSize(13);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setContentDescription(text + (selected ? ", 선택됨" : ""));
        return button;
    }

    public Button chip(String text, boolean selected) {
        return chip(text, selected, null);
    }

    public Button filterButton(String text) {
        return chip(text, false, null);
    }

    public void styleFilterButton(Button button, boolean active) {
        button.setSelected(active);
        button.setContentDescription(button.getText() + (active ? ", 선택됨" : ""));
        button.setTextColor(active ? selectedInk() : inkMuted());
        button.setBackground(active
                ? selectedStateRippleDrawable(dp(CHIP_RADIUS_DP))
                : outlinedSurfaceRippleDrawable(dp(CHIP_RADIUS_DP)));
        applyDepth(button, active ? DEPTH_SURFACE_DP : DEPTH_FLAT_DP);
    }

    public View buttonRow(View first, View second) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.setMargins(0, 0, dp(BUTTON_GAP_DP), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        right.setMargins(dp(BUTTON_GAP_DP), 0, 0, 0);
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
        input.setTextColor(ink());
        input.setHintTextColor(inkTertiary());
        input.setMinHeight(dp(48));
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        input.setBackground(borderDrawable(surface(), border(), dp(INPUT_RADIUS_DP)));
        applyDepth(input, DEPTH_FLAT_DP);
        return input;
    }

    public EditText searchInput(String hint) {
        EditText input = input(hint, "");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(borderDrawable(surface(), border(), dp(INPUT_RADIUS_DP)));
        return input;
    }

    /** @deprecated 신규 코드는 searchInput()을 사용한다. */
    @Deprecated
    public EditText searchField(String hint) {
        return searchInput(hint);
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
        label.setPadding(0, dp(14), 0, dp(FIELD_LABEL_GAP_DP));
        return label;
    }

    public View labeledFieldColumn(String label, View field) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = caption(label, COLOR_MUTED);
        labelView.setPadding(0, 0, 0, dp(FIELD_LABEL_GAP_DP));
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
            params.setMargins(0, dp(FORM_ITEM_GAP_DP), 0, 0);
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

    public View inlineStat(String label, String value, boolean onAccentSurface) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(caption(label, onAccentSurface ? onAccentMuted() : COLOR_MUTED));
        TextView valueView = num(value, 16, onAccentSurface ? onAccent() : COLOR_TEXT, true);
        valueView.setPadding(0, dp(3), 0, 0);
        cell.addView(valueView);
        return cell;
    }

    public View statusDotBadge(String labelText, int dotColor, boolean onAccentSurface) {
        LinearLayout badge = new LinearLayout(activity);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackground(onAccentSurface
                ? tonalSurfaceDrawable(dp(CHIP_RADIUS_DP))
                : borderDrawable(subtle(), border(), dp(CHIP_RADIUS_DP)));
        badge.setPadding(dp(10), dp(5), dp(12), dp(5));
        applyDepth(badge, DEPTH_FLAT_DP);

        View dot = new View(activity);
        dot.setBackground(borderDrawable(statusColor(dotColor), Color.TRANSPARENT,
                dp(CHIP_RADIUS_DP)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.setMargins(0, 0, dp(6), 0);
        badge.addView(dot, dotParams);

        badge.addView(text(labelText, 12,
                onAccentSurface ? tonalInk() : COLOR_TEXT, true));
        return badge;
    }

    public View statusBadge(String labelText, int dotColor) {
        return statusDotBadge(labelText, dotColor, false);
    }

    public View statusBadge(String labelText, int dotColor, boolean onAccentSurface) {
        return statusDotBadge(labelText, dotColor, onAccentSurface);
    }

    /** Hero 전용 상태 배지. Hero 이외의 상태 표현에는 statusBadge()를 사용한다. */
    public View heroStatusBadge(String labelText, int dotColor) {
        LinearLayout badge = new LinearLayout(activity);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackground(borderDrawable(
                withAlpha(heroInk(), 18), withAlpha(heroInk(), 52), dp(CHIP_RADIUS_DP)));
        badge.setPadding(dp(10), dp(5), dp(12), dp(5));
        applyDepth(badge, DEPTH_FLAT_DP);

        View dot = new View(activity);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dot.setBackground(borderDrawable(statusColor(dotColor), Color.TRANSPARENT,
                dp(CHIP_RADIUS_DP)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.setMargins(0, 0, dp(6), 0);
        badge.addView(dot, dotParams);
        badge.addView(text(labelText, 12, heroInk(), true));
        return badge;
    }

    /** Hero 내부 지표 셀. blur나 glow 없이 Hero tonal surface만 사용한다. */
    public View heroMetric(String label, String value) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setMinimumHeight(dp(64));
        cell.setPadding(dp(8), dp(10), dp(8), dp(10));
        cell.setBackground(borderDrawable(
                withAlpha(heroInk(), 18), withAlpha(heroInk(), 52), dp(14)));
        applyDepth(cell, DEPTH_FLAT_DP);

        TextView labelView = caption(label, heroMuted());
        labelView.setGravity(Gravity.CENTER);
        cell.addView(labelView);

        TextView valueView = num(value, 15, heroInk(), true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(0, dp(3), 0, 0);
        cell.addView(valueView);
        return cell;
    }

    /** @deprecated Hero CTA도 primaryButton()의 semantic을 사용한다. */
    @Deprecated
    public View flowStatusBadge(String labelText, int dotColor) {
        return heroStatusBadge(labelText, dotColor);
    }

    /** @deprecated Hero 지표는 heroMetric()을 사용한다. */
    @Deprecated
    public View flowMetric(String label, String value) {
        return heroMetric(label, value);
    }

    /** @deprecated 신규 코드는 primaryButton()을 사용한다. */
    @Deprecated
    public Button flowHeroButton(String text, View.OnClickListener listener) {
        return primaryButton(text, listener);
    }

    public View statTile(String label, String value, String meta, boolean inverseTile, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(16), dp(14), dp(16), dp(14));
        tile.setMinimumHeight(dp(92));
        if (listener != null) {
            tile.setBackground(inverseTile
                    ? tonalRippleDrawable(dp(14))
                    : flatSurfaceRippleDrawable(dp(14)));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setOnClickListener(listener);
            pressFeedback(tile);
        } else {
            tile.setBackground(inverseTile
                    ? tonalSurfaceDrawable(dp(14))
                    : flatSurfaceDrawable(dp(14)));
        }
        applyDepth(tile, inverseTile ? DEPTH_SURFACE_DP : DEPTH_FLAT_DP);

        TextView labelView = caption(label, inverseTile ? tonalInk() : COLOR_MUTED);
        tile.addView(labelView);
        TextView valueView = num(value, 21, inverseTile ? tonalInk() : COLOR_TEXT, true);
        valueView.setPadding(0, dp(7), 0, 0);
        tile.addView(valueView);
        if (meta != null) {
            TextView metaView = text(meta, 11, inverseTile ? tonalInk() : COLOR_TERTIARY, false);
            metaView.setPadding(0, dp(3), 0, 0);
            tile.addView(metaView);
        }
        return tile;
    }

    public View tonalStatTile(String label, String value, String meta, View.OnClickListener listener) {
        return statTile(label, value, meta, true, listener);
    }

    /** @deprecated 선택 가능한 강조 타일은 tonal surface로 표시한다. */
    @Deprecated
    public View hologramStatTile(String label, String value, String meta, View.OnClickListener listener) {
        return statTile(label, value, meta, true, listener);
    }

    public View glyphCircle(String glyph, boolean onAccentSurface) {
        TextView circle = text(glyph, 14, onAccentSurface ? tonalInk() : COLOR_MUTED, true);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(onAccentSurface
                ? tonalSurfaceDrawable(dp(CHIP_RADIUS_DP))
                : flatSurfaceDrawable(dp(CHIP_RADIUS_DP)));
        applyDepth(circle, DEPTH_FLAT_DP);
        circle.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return circle;
    }

    public View tonalGlyphCircle(String glyph) {
        TextView circle = text(glyph, 14, tonalInk(), true);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(tonalSurfaceDrawable(dp(CHIP_RADIUS_DP)));
        applyDepth(circle, DEPTH_FLAT_DP);
        circle.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return circle;
    }

    /** @deprecated seed 기반 강조를 제거했으며 tonalGlyphCircle()을 사용한다. */
    @Deprecated
    public View vibrantGlyphCircle(String glyph, String seed) {
        return tonalGlyphCircle(glyph);
    }

    public View orderBadge(int order, boolean onAccentSurface) {
        TextView badge = num(String.valueOf(order), 13,
                onAccentSurface ? tonalInk() : COLOR_TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(onAccentSurface
                ? tonalSurfaceDrawable(dp(CHIP_RADIUS_DP))
                : flatSurfaceDrawable(dp(CHIP_RADIUS_DP)));
        applyDepth(badge, DEPTH_FLAT_DP);
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        return badge;
    }

    public View compactOrderBadge(int order) {
        TextView badge = num(String.valueOf(order), 11, COLOR_TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(flatSurfaceDrawable(dp(CHIP_RADIUS_DP)));
        applyDepth(badge, DEPTH_FLAT_DP);
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
            pressFeedback(row);
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
                View line = hairline(border());
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

    public View progressBar(double ratio, boolean onAccentSurface) {
        float clamped = (float) Math.max(0, Math.min(1, ratio));
        LinearLayout track = new LinearLayout(activity);
        track.setOrientation(LinearLayout.HORIZONTAL);
        int trackColor = onAccentSurface ? trackOnAccent() : trackOnSurface();
        track.setBackground(borderDrawable(trackColor, trackColor, dp(999)));

        int fillColor = onAccentSurface ? onAccent() : accent();
        View fill = new View(activity);
        fill.setBackground(borderDrawable(fillColor, fillColor, dp(999)));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(6), clamped));
        View rest = new View(activity);
        track.addView(rest, new LinearLayout.LayoutParams(0, dp(6), 1f - clamped));
        return track;
    }

    public View volumeTrendChart(List<Double> values) {
        return trendChart(values, "kg", -1, "이전 기록 없음");
    }

    /**
     * Draws a volume trend and optionally marks the final point as the current session.
     * A negative currentPointIndex means every value is persisted history.
     */
    public View volumeTrendChart(List<Double> values, int currentPointIndex) {
        return trendChart(values, "kg", currentPointIndex, "이전 기록 없음");
    }

    public View trendChart(List<Double> values, String unit) {
        return trendChart(values, unit, -1, "추세를 표시할 기록이 없습니다.");
    }

    /** Draws a labeled numeric trend without assuming that the values are workout volume. */
    public View trendChart(
            List<Double> values,
            String unit,
            int currentPointIndex,
            String emptyLabel
    ) {
        final List<Double> points = values == null ? java.util.Collections.emptyList() : new java.util.ArrayList<>(values);
        final int markedCurrentPoint = currentPointIndex >= 0 && currentPointIndex < points.size()
                ? currentPointIndex
                : -1;
        final String displayUnit = unit == null ? "" : unit;
        final String displayEmptyLabel = emptyLabel == null || emptyLabel.trim().isEmpty()
                ? "추세를 표시할 기록이 없습니다."
                : emptyLabel;
        final int axisColor = border();
        final int mutedColor = inkMuted();
        final int strokeColor = accent();
        return new View(activity) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int left = dp(12);
                int right = getWidth() - dp(12);
                // Reserve a small band above the plot so the value labels stay inside the chart.
                int top = dp(28);
                int bottom = getHeight() - dp(14);
                paint.setStrokeWidth(dp(1));
                paint.setColor(axisColor);
                canvas.drawLine(left, bottom, right, bottom, paint);
                if (points.isEmpty()) {
                    paint.setTextSize(dp(12));
                    paint.setColor(mutedColor);
                    paint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(displayEmptyLabel, left, top + dp(14), paint);
                    return;
                }

                double max = 1;
                for (Double value : points) {
                    if (value != null) {
                        max = Math.max(max, value);
                    }
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(mutedColor);
                paint.setTextSize(dp(10));
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText("0" + displayUnit, left, bottom + dp(11), paint);

                paint.setColor(strokeColor);
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
                    if (index == markedCurrentPoint) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(dp(2));
                        paint.setColor(blueInk());
                        canvas.drawCircle(x, y, dp(6), paint);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(strokeColor);
                        canvas.drawCircle(x, y, dp(3), paint);
                    } else {
                        paint.setColor(strokeColor);
                        canvas.drawCircle(x, y, dp(4), paint);
                    }
                }

                paint.setColor(mutedColor);
                paint.setTextSize(dp(10));
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fontMetrics = paint.getFontMetrics();
                for (int index = 0; index < points.size(); index++) {
                    double value = points.get(index) == null ? 0 : points.get(index);
                    float x = points.size() == 1
                            ? (left + right) / 2f
                            : left + (right - left) * index / (float) (points.size() - 1);
                    float y = bottom - (float) ((bottom - top) * value / max);
                    String label = (index == markedCurrentPoint ? "현재 " : "")
                            + valueLabel(value);
                    float halfLabelWidth = paint.measureText(label) / 2f;
                    float labelX = Math.max(left + halfLabelWidth,
                            Math.min(right - halfLabelWidth, x));
                    float labelY = Math.max(-fontMetrics.top, y - dp(7));
                    if (index == markedCurrentPoint) {
                        paint.setColor(blueInk());
                    }
                    canvas.drawText(label, labelX, labelY, paint);
                    paint.setColor(mutedColor);
                }
            }

            private String valueLabel(double value) {
                String number = "kg".equals(displayUnit)
                        ? formatVolume(value)
                        : trimDouble(value);
                return number + displayUnit;
            }
        };
    }

    // ── 모션 ─────────────────────────────────────────────────────────

    /** 버튼/타일 공통 프레스 스케일 피드백. 클릭 이벤트를 소비하지 않는다. */
    @SuppressLint("ClickableViewAccessibility")
    public void pressFeedback(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    float pressedDepth = Math.min(v.getElevation(), dp(4));
                    v.animate()
                            .scaleX(0.97f).scaleY(0.97f)
                            .translationY(dp(2)).translationZ(-pressedDepth)
                            .setDuration(90)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .scaleX(1f).scaleY(1f)
                            .translationY(0f).translationZ(0f)
                            .setDuration(180)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** 화면 진입 모션: 아래에서 살짝 떠오르며 페이드인. 탭 전환 시에만 사용한다. */
    public void screenEnter(View content) {
        content.setAlpha(0f);
        content.setTranslationY(dp(14));
        content.animate().alpha(1f).translationY(0f)
                .setDuration(230)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /** 완료 스탬프 팝: 반전 전환 직후 1회만 실행되는 오버슛 스케일. */
    public void stampPop(View view) {
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);
        view.animate().scaleX(1f).scaleY(1f)
                .setDuration(180)
                .setInterpolator(new OvershootInterpolator(2.2f))
                .start();
    }

    /** 핵심 숫자 카운트업. 600ms 감속, tabular 숫자 전제. */
    public void animateCount(TextView view, double target, String suffixOrNull) {
        if (target <= 0) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) target);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator(1.8f));
        animator.addUpdateListener(animation -> {
            double current = (float) animation.getAnimatedValue();
            String value = formatVolume(target == Math.rint(target) ? Math.rint(current) : current);
            view.setText(suffixOrNull == null ? value : value + suffixOrNull);
        });
        animator.start();
    }

    /** 막대 성장 모션: 바닥 기준 스케일, index 순서대로 스태거. */
    public void growBar(View bar, int index) {
        bar.setScaleY(0f);
        bar.post(() -> {
            bar.setPivotY(bar.getHeight());
            bar.animate().scaleY(1f)
                    .setDuration(340)
                    .setStartDelay(40L * index)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        });
    }

    // ── 바텀시트 ──────────────────────────────────────────────────────

    /** 현재 열려 있는 앱 소유 시트를 닫는다. 시스템 권한 창에는 관여하지 않는다. */
    public boolean dismissActiveDialog() {
        for (int index = dialogStack.size() - 1; index >= 0; index--) {
            Dialog dialog = dialogStack.get(index);
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
                activeDialog = topShowingDialog();
                return true;
            }
            dialogStack.remove(index);
        }
        activeDialog = null;
        return false;
    }

    /** 공통 바텀시트. 상단 라운드 24dp와 하단 고정 Primary CTA를 사용한다. */
    public Dialog bottomSheet(String title, View body,
                              String primaryText, Runnable onPrimary,
                              String dangerText, Runnable onDanger) {
        return buildSheet(
                title,
                body,
                primaryText,
                () -> {
                    onPrimary.run();
                    return true;
                },
                dangerText,
                onDanger,
                null,
                null
        );
    }

    /** @deprecated 신규 코드는 bottomSheet()를 사용한다. */
    @Deprecated
    public Dialog sheet(String title, View body,
                        String primaryText, Runnable onPrimary,
                        String dangerText, Runnable onDanger) {
        return bottomSheet(title, body, primaryText, onPrimary, dangerText, onDanger);
    }

    /** Primary CTA와 중립적인 보조 액션을 함께 제공하는 공통 시트. */
    public Dialog sheetWithSecondary(
            String title,
            View body,
            String primaryText,
            Runnable onPrimary,
            String secondaryText,
            Runnable onSecondary
    ) {
        return buildSheet(
                title,
                body,
                primaryText,
                () -> {
                    onPrimary.run();
                    return true;
                },
                null,
                null,
                secondaryText,
                onSecondary
        );
    }

    /** 입력 검증에 실패하면 닫히지 않는 바텀시트. */
    public Dialog validatedSheet(
            String title,
            View body,
            String primaryText,
            BooleanSupplier onPrimary
    ) {
        return buildSheet(title, body, primaryText, onPrimary, null, null, null, null);
    }

    /** 입력 검증과 삭제 동작을 함께 제공하는 편집용 바텀시트. */
    public Dialog validatedSheet(
            String title,
            View body,
            String primaryText,
            BooleanSupplier onPrimary,
            String dangerText,
            Runnable onDanger
    ) {
        return buildSheet(title, body, primaryText, onPrimary,
                dangerText, onDanger, null, null);
    }

    private Dialog buildSheet(
            String title,
            View body,
            String primaryText,
            BooleanSupplier onPrimary,
            String dangerText,
            Runnable onDanger,
            String secondaryText,
            Runnable onSecondary
    ) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(24));
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface());
        background.setStroke(dp(1), border());
        float r = dp(24);
        background.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(background);
        applyDepth(sheet, DEPTH_EMPHASIS_DP);

        View handle = new View(activity);
        handle.setBackground(borderDrawable(inkTertiary(), inkTertiary(), dp(999)));
        handle.setAlpha(0.45f);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(16));
        sheet.addView(handle, handleParams);

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(20);
        titleView.setTextColor(ink());
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setLetterSpacing(-0.02f);
        sheet.addView(titleView);

        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.setMargins(0, dp(6), 0, 0);
        sheet.addView(body, bodyParams);

        Button primary = sheetPrimaryButton(primaryText, () -> {
            if (onPrimary.getAsBoolean()) {
                dialog.dismiss();
            }
        });
        sheet.addView(primary, fullWidthParams(dp(20)));

        if (dangerText != null && onDanger != null) {
            TextView danger = new TextView(activity);
            danger.setText(dangerText);
            danger.setTextSize(14);
            danger.setTextColor(statusColor(COLOR_NEGATIVE));
            danger.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            danger.setGravity(Gravity.CENTER);
            danger.setPadding(0, dp(14), 0, dp(2));
            danger.setClickable(true);
            danger.setFocusable(true);
            danger.setOnClickListener(v -> {
                onDanger.run();
                dialog.dismiss();
            });
            sheet.addView(danger, fullWidthParams(dp(2)));
        } else if (secondaryText != null && onSecondary != null) {
            TextView secondary = text(secondaryText, 14, COLOR_MUTED, true);
            secondary.setGravity(Gravity.CENTER);
            secondary.setPadding(0, dp(14), 0, dp(2));
            secondary.setClickable(true);
            secondary.setFocusable(true);
            secondary.setOnClickListener(v -> {
                onSecondary.run();
                dialog.dismiss();
            });
            sheet.addView(secondary, fullWidthParams(dp(2)));
        }

        dialog.setContentView(sheet);
        trackDialog(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(android.R.style.Animation_InputMethod);
            window.setDimAmount(0.42f);
        }
        dialog.show();
        return dialog;
    }

    /** 단일 선택용 앱 공통 선택 시트. 행을 고르면 즉시 적용하고 시트를 닫는다. */
    public Dialog choiceSheet(
            String title,
            List<String> options,
            int selectedIndex,
            OnChoiceSelected listener
    ) {
        return choiceSheet(title, options, selectedIndex, null, null, listener);
    }

    /** 선택 행과 중립적인 보조 액션을 함께 제공하는 공통 선택 시트. */
    public Dialog choiceSheet(
            String title,
            List<String> options,
            int selectedIndex,
            String secondaryText,
            Runnable onSecondary,
            OnChoiceSelected listener
    ) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(2), dp(2), dp(2), dp(2));
        LinearLayout optionsBody = new LinearLayout(activity);
        optionsBody.setOrientation(LinearLayout.VERTICAL);
        List<String> safeOptions = options == null
                ? new ArrayList<>()
                : new ArrayList<>(options);
        Dialog[] dialogHolder = new Dialog[1];
        for (int index = 0; index < safeOptions.size(); index++) {
            final int optionIndex = index;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(52));
            row.setPadding(dp(14), dp(9), dp(12), dp(9));
            boolean selected = index == selectedIndex;
            styleSelection(row, selected, dp(16));

            TextView label = text(safeOptions.get(index), 14,
                    selected ? selectedInk() : ink(), true);
            label.setTextColor(selected ? selectedInk() : ink());
            row.addView(label, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView check = text(selected ? "✓" : "", 16,
                    selected ? selectedInk() : inkMuted(), true);
            check.setTextColor(selected ? selectedInk() : inkMuted());
            check.setGravity(Gravity.CENTER);
            row.addView(check, new LinearLayout.LayoutParams(dp(28), dp(28)));
            row.setContentDescription(safeOptions.get(index) + (selected ? ", 선택됨" : ""));
            row.setClickable(true);
            row.setFocusable(true);
            pressFeedback(row);
            row.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChoice(optionIndex);
                }
                Dialog dialog = dialogHolder[0];
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
            optionsBody.addView(row, fullWidthParams(index == 0 ? 0 : dp(8)));
        }
        ScrollView optionsScroll = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxHeight = choiceSheetMaxOptionsHeight();
                int cappedHeightSpec = MeasureSpec.makeMeasureSpec(
                        maxHeight,
                        MeasureSpec.AT_MOST
                );
                super.onMeasure(widthMeasureSpec, cappedHeightSpec);
            }
        };
        optionsScroll.setFillViewport(false);
        optionsScroll.setClipToPadding(false);
        optionsScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        optionsScroll.addView(optionsBody, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        body.addView(optionsScroll, fullWidthParams(0));
        Dialog dialog = secondaryText == null || onSecondary == null
                ? bottomSheet(title, body, "닫기", () -> { }, null, null)
                : sheetWithSecondary(title, body, "닫기", () -> { },
                        secondaryText, onSecondary);
        dialogHolder[0] = dialog;
        return dialog;
    }

    private int choiceSheetMaxOptionsHeight() {
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        if (screenHeight <= 0) {
            return dp(360);
        }
        return Math.max(dp(180), screenHeight - dp(260));
    }

    private void trackDialog(Dialog dialog) {
        dialogStack.remove(dialog);
        dialogStack.add(dialog);
        activeDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            dialogStack.remove(dialog);
            activeDialog = topShowingDialog();
        });
    }

    private Dialog topShowingDialog() {
        for (int index = dialogStack.size() - 1; index >= 0; index--) {
            Dialog dialog = dialogStack.get(index);
            if (dialog != null && dialog.isShowing()) {
                return dialog;
            }
        }
        return null;
    }

    public interface OnChoiceSelected {
        void onChoice(int index);
    }

    /** 파괴적 행동 확인 시트. 결과 문장은 sem.negative로 명시하고 CTA는 공통 Primary를 사용한다. */
    public void confirmSheet(String title, String message, String consequence,
                             String actionText, Runnable onConfirm) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView messageView = new TextView(activity);
        messageView.setText(message);
        messageView.setTextSize(15);
        messageView.setTextColor(inkMuted());
        messageView.setLineSpacing(dp(3), 1f);
        messageView.setPadding(0, dp(6), 0, 0);
        body.addView(messageView);
        if (consequence != null) {
            TextView consequenceView = new TextView(activity);
            consequenceView.setText(consequence);
            consequenceView.setTextSize(12);
            consequenceView.setTextColor(statusColor(COLOR_NEGATIVE));
            consequenceView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            consequenceView.setPadding(0, dp(10), 0, 0);
            body.addView(consequenceView);
        }
        bottomSheet(title, body, actionText, onConfirm, null, null);
    }

    /** 시트 전용 Primary 버튼도 전역 Pastel Blue 버튼 토큰을 사용한다. */
    private Button sheetPrimaryButton(String text, Runnable action) {
        return primaryButton(text, v -> action.run());
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

    /** 루틴 카드: 전체 탭은 상세로 이동하고 운동 시작은 보조 메뉴에서 선택한다. */
    public View routineCard(String routineName, int exerciseCount, String latestWorkoutDate,
                            Runnable onStart, Runnable onDetail, Runnable onMenu) {
        LinearLayout card = card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(routineName + " 루틴 상세 보기");
        card.setOnClickListener(v -> {
            if (onDetail != null) {
                onDetail.run();
            } else if (onStart != null) {
                onStart.run();
            }
        });
        applyDepth(card, DEPTH_SURFACE_DP);
        pressFeedback(card);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.addView(glyphCircle("루", false));
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), 0, 0, 0);
        column.addView(text(routineName, 16, COLOR_TEXT, true));
        TextView meta = text(exerciseCount + "개 종목 · 탭하여 상세 보기", 12, COLOR_MUTED, false);
        meta.setPadding(0, dp(2), 0, 0);
        column.addView(meta);
        column.addView(text(recentWorkoutText(latestWorkoutDate), 11, COLOR_TERTIARY, false));
        headerRow.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (onMenu != null) {
            TextView menu = text("⋯", 22, COLOR_MUTED, true);
            menu.setGravity(Gravity.CENTER);
            menu.setMinWidth(dp(48));
            menu.setMinimumWidth(dp(48));
            menu.setMinHeight(dp(48));
            menu.setMinimumHeight(dp(48));
            menu.setContentDescription(routineName + " 루틴 관리");
            menu.setClickable(true);
            menu.setFocusable(true);
            menu.setOnClickListener(v -> onMenu.run());
            pressFeedback(menu);
            headerRow.addView(menu);
        } else {
            TextView chevron = text("›", 20, COLOR_TERTIARY, false);
            headerRow.addView(chevron);
        }
        card.addView(headerRow);
        return card;
    }

    /**
     * @deprecated 전체 카드 탭이 상세 보기이므로 showDetailAction은 더 이상 의미가 없다.
     *             새 호출부는 routineCard(..., latestWorkoutDate, ..., onMenu)를 사용한다.
     */
    @Deprecated
    public View routineCard(String routineName, int exerciseCount, boolean showDetailAction,
                            String latestWorkoutDate,
                            Runnable onStart, Runnable onDetail) {
        return routineCard(routineName, exerciseCount, latestWorkoutDate,
                onStart, onDetail, null);
    }

    /** @deprecated showDetailAction은 호환성만 유지하며 무시한다. */
    @Deprecated
    public View routineCard(String routineName, int exerciseCount, boolean showDetailAction,
                            String latestWorkoutDate,
                            Runnable onStart, Runnable onDetail, Runnable onMenu) {
        return routineCard(routineName, exerciseCount, latestWorkoutDate,
                onStart, onDetail, onMenu);
    }

    public View quickStartRoutineCard(String routineName, int exerciseCount, String latestWorkoutDate,
                                      Runnable onStart, Runnable onDetail) {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(flatSurfaceRippleDrawable(dp(CARD_RADIUS_DP)));
        applyDepth(card, DEPTH_SURFACE_DP);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onStart.run());
        pressFeedback(card);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView routineGlyph = text("루", 12, pastelBlue(), true);
        routineGlyph.setGravity(Gravity.CENTER);
        routineGlyph.setBackground(borderDrawable(withAlpha(pastelBlue(), 38),
                Color.TRANSPARENT, dp(CHIP_RADIUS_DP)));
        routineGlyph.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        headerRow.addView(routineGlyph);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(9), 0, 0, 0);
        TextView nameView = text(routineName, 14, ink(), true);
        column.addView(nameView);
        TextView meta = text(exerciseCount + "개 종목 · 탭하여 시작", 10, inkMuted(), false);
        meta.setPadding(0, dp(2), 0, 0);
        column.addView(meta);
        TextView recentView = text(recentWorkoutText(latestWorkoutDate), 10, inkTertiary(), false);
        column.addView(recentView);
        headerRow.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = text("›", 16, inkTertiary(), false);
        chevron.setTextColor(inkTertiary());
        headerRow.addView(chevron);
        card.addView(headerRow);
        return card;
    }

    private String recentWorkoutText(String date) {
        if (date == null || date.trim().isEmpty()) {
            return "최근 운동 기록 없음";
        }
        try {
            LocalDate workoutDate = LocalDate.parse(date);
            long daysAgo = ChronoUnit.DAYS.between(workoutDate, LocalDate.now());
            String relative = daysAgo <= 0 ? "오늘" : daysAgo + "일 전";
            return "최근 운동 " + workoutDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                    + " · " + relative;
        } catch (Exception ignored) {
            return "최근 운동 " + date;
        }
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
