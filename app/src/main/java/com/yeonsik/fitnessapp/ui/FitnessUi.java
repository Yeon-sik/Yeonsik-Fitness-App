package com.yeonsik.fitnessapp.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
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
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * 테마 메인 색상과 Flowstate 팔레트를 결합한 공통 UI 팩토리.
 * 색 토큰, 타이포그래피, 표면(카드/타일), 버튼/칩, 입력창, 리스트 행, 포맷터를 담당한다.
 * 화면 상태를 소유하지 않으며, 다크 테마 여부는 생성 시 주입된 supplier로 판단한다.
 * 다크 테마에서도 "반전 = 강조" 문법을 유지하되, 홀로그램 팔레트는 라이트 테마 색상을 고정 사용한다.
 */
public final class FitnessUi {
    // ── 라이트 토큰 (design-system §2.1) ─────────────────────────────
    public static final int COLOR_BACKGROUND = Color.WHITE;
    public static final int COLOR_SURFACE = Color.WHITE;
    public static final int COLOR_TEXT = Color.rgb(21, 22, 26);
    public static final int COLOR_MUTED = Color.rgb(106, 110, 118);
    public static final int COLOR_TERTIARY = Color.rgb(162, 166, 174);
    public static final int COLOR_BORDER = Color.BLACK;
    public static final int COLOR_PRIMARY = Color.BLACK;
    public static final int COLOR_SUBTLE = Color.WHITE;
    public static final int COLOR_INVERSE_TEXT = Color.WHITE;
    public static final int COLOR_INVERSE_MUTED = Color.argb(230, 255, 255, 255);
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

    // ── 홈 Flowstate에서 출발한 전역 컬러 팔레트 ──────────────────────
    // 히어로는 원색 glow를, 일반 컴포넌트는 테마 메인 색상과 혼합한 파생색을 사용한다.
    public static final int COLOR_FLOW_BASE = Color.rgb(4, 5, 12);
    public static final int COLOR_FLOW_CYAN = Color.rgb(0, 216, 255);
    public static final int COLOR_FLOW_VIOLET = Color.rgb(91, 70, 255);
    public static final int COLOR_FLOW_MAGENTA = Color.rgb(242, 54, 255);
    public static final int COLOR_FLOW_TEXT = Color.rgb(238, 240, 246);
    public static final int COLOR_FLOW_MUTED = Color.rgb(185, 190, 207);
    public static final int COLOR_FLOW_GLASS_FILL = Color.argb(20, 255, 255, 255);
    public static final int COLOR_FLOW_GLASS_BORDER = Color.argb(41, 255, 255, 255);

    // ── 다크 토큰 (design-system §2.3: 다크에서는 화이트가 강조 표면) ──
    public static final int COLOR_D_BACKGROUND = Color.BLACK;
    public static final int COLOR_D_SURFACE = Color.BLACK;
    public static final int COLOR_D_SUBTLE = Color.BLACK;
    public static final int COLOR_D_ACCENT = Color.WHITE;
    public static final int COLOR_D_TEXT = Color.rgb(237, 238, 240);
    public static final int COLOR_D_MUTED = Color.rgb(154, 158, 166);
    public static final int COLOR_D_TERTIARY = Color.rgb(110, 114, 128);
    public static final int COLOR_D_BORDER = Color.WHITE;
    public static final int COLOR_D_ON_ACCENT_MUTED = Color.argb(230, 21, 22, 26);
    public static final int COLOR_D_CHIP_ON_ACCENT = Color.argb(20, 21, 22, 26);
    public static final int COLOR_D_LINE_ON_ACCENT = Color.argb(30, 21, 22, 26);
    public static final int COLOR_D_TRACK_ON_ACCENT = Color.argb(30, 21, 22, 26);
    public static final int COLOR_D_BAR_MUTED = Color.argb(120, 237, 238, 240);
    public static final int COLOR_D_BAR_EMPTY = Color.argb(26, 255, 255, 255);

    private final Activity activity;
    private final BooleanSupplier inverseSupplier;
    private final Map<View, AnimationBinding> animationBindings = new WeakHashMap<>();

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

    // ── 테마 시맨틱 접근자 ────────────────────────────────────────────

    public int pageBg() {
        return dark() ? COLOR_D_BACKGROUND : COLOR_BACKGROUND;
    }

    public int surface() {
        return dark() ? COLOR_D_SURFACE : COLOR_SURFACE;
    }

    public int subtle() {
        return dark() ? COLOR_D_SUBTLE : COLOR_SUBTLE;
    }

    /** 강조(반전) 표면: 라이트=블랙, 다크=화이트. */
    public int accent() {
        return dark() ? COLOR_D_ACCENT : COLOR_PRIMARY;
    }

    /** 강조 표면 위 텍스트. */
    public int onAccent() {
        return dark() ? COLOR_TEXT : COLOR_INVERSE_TEXT;
    }

    public int onAccentMuted() {
        return dark() ? COLOR_D_ON_ACCENT_MUTED : COLOR_INVERSE_MUTED;
    }

    /** 테마와 무관하게 라이트 홀로그램 표면 위에서 사용하는 고정 전경색. */
    public int onVibrant() {
        return COLOR_INVERSE_TEXT;
    }

    public int onVibrantMuted() {
        return COLOR_INVERSE_MUTED;
    }

    public int chipOnVibrant() {
        return COLOR_INVERSE_CHIP;
    }

    public int trackOnVibrant() {
        return COLOR_TRACK_DARK;
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
        return dark() ? COLOR_D_CHIP_ON_ACCENT : COLOR_INVERSE_CHIP;
    }

    public int trackOnSurface() {
        return dark() ? COLOR_TRACK_DARK : COLOR_TRACK_LIGHT;
    }

    public int trackOnAccent() {
        return dark() ? COLOR_D_TRACK_ON_ACCENT : COLOR_TRACK_DARK;
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

    /**
     * 히어로 팔레트를 라이트 테마와 동일한 검정 베이스에 섞은 강조색.
     * 테마가 바뀌어도 홀로그램의 cyan/violet/magenta 색상은 변하지 않는다.
     */
    public int vibrantColor(int variant) {
        return mix(COLOR_PRIMARY, rawFlowColor(variant), 0.50f);
    }

    /** 어두운 표면에서는 원색을, 밝은 표면에서는 대비를 높인 홀로그램 색을 반환한다. */
    public int hologramAccentColor(int variant) {
        return dark() ? rawFlowColor(variant) : vibrantColor(variant);
    }

    public Drawable vibrantBackground(int variant, int radius) {
        int normalized = normalizeVariant(variant);
        GradientDrawable gradient = new GradientDrawable(
                gradientOrientation(normalized),
                new int[]{vibrantColor(normalized), vibrantColor(normalized + 1)}
        );
        gradient.setCornerRadius(radius);

        GradientDrawable border = borderDrawable(Color.TRANSPARENT, border(), radius);
        return new LayerDrawable(new Drawable[]{gradient, border});
    }

    public Drawable vibrantRippleDrawable(String seed, int radius) {
        int variant = variantFor(seed);
        GradientDrawable mask = borderDrawable(Color.WHITE, Color.WHITE, radius);
        return new RippleDrawable(
                ColorStateList.valueOf(COLOR_RIPPLE_DARK),
                vibrantBackground(variant, radius),
                mask
        );
    }

    public Drawable vibrantRippleDrawable(int variant, int radius) {
        GradientDrawable mask = borderDrawable(Color.WHITE, Color.WHITE, radius);
        return new RippleDrawable(
                ColorStateList.valueOf(COLOR_RIPPLE_DARK),
                vibrantBackground(variant, radius),
                mask
        );
    }

    public Drawable flatSurfaceDrawable(int radius) {
        return borderDrawable(surface(), Color.TRANSPARENT, radius);
    }

    public Drawable flatSurfaceRippleDrawable(int radius) {
        GradientDrawable mask = borderDrawable(Color.WHITE, Color.WHITE, radius);
        return new RippleDrawable(
                ColorStateList.valueOf(rippleOnSurface()),
                flatSurfaceDrawable(radius),
                mask
        );
    }

    /** 일반 배경으로 돌아갈 때 기존 홀로그램 애니메이션과 attach listener를 함께 정리한다. */
    public void setComponentBackground(View view, Drawable background) {
        clearAnimationBinding(view);
        view.setBackground(background);
    }

    /** 기존 배경 위에 회전하는 cyan/violet/magenta 홀로그램 테두리를 겹친다. */
    public void setHologramBackground(View view, Drawable background, int radius) {
        clearAnimationBinding(view);
        if (view.getBackground() == background) {
            view.setBackground(null);
        }
        HologramBorderDrawable hologram = new HologramBorderDrawable(
                background, radius, dp(3));
        bindAnimatedBackground(view, hologram, hologram);
    }

    private void bindAnimatedBackground(View view, Drawable background, Animatable animatable) {
        AnimationBinding binding = new AnimationBinding(animatable);
        animationBindings.put(view, binding);
        view.addOnAttachStateChangeListener(binding);
        view.setBackground(background);
        if (view.isAttachedToWindow() && view.isShown()) {
            animatable.start();
        }
    }

    private void clearAnimationBinding(View view) {
        AnimationBinding binding = animationBindings.remove(view);
        if (binding == null) {
            return;
        }
        view.removeOnAttachStateChangeListener(binding);
        binding.drawable.stop();
    }

    private static final class AnimationBinding implements View.OnAttachStateChangeListener {
        private final Animatable drawable;

        private AnimationBinding(Animatable drawable) {
            this.drawable = drawable;
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            if (view.isShown()) {
                drawable.start();
            }
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            drawable.stop();
        }
    }

    private static final class HologramBorderDrawable extends Drawable
            implements Animatable, Drawable.Callback {
        private final Drawable content;
        private final float radius;
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF borderRect = new RectF();
        private final Matrix gradientMatrix = new Matrix();
        private final ValueAnimator animator;
        private SweepGradient glowGradient;
        private SweepGradient edgeGradient;
        private float rotation;

        private HologramBorderDrawable(Drawable content, float radius, float edgeWidth) {
            this.content = (content == null ? new ColorDrawable(Color.TRANSPARENT) : content).mutate();
            this.content.setCallback(this);
            this.radius = radius;

            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeWidth(edgeWidth * 3f);
            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(edgeWidth);

            animator = ValueAnimator.ofFloat(0f, 360f);
            animator.setDuration(2200L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(valueAnimator -> {
                rotation = (float) valueAnimator.getAnimatedValue();
                invalidateSelf();
            });
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            content.setBounds(bounds);
            float inset = edgePaint.getStrokeWidth() / 2f;
            borderRect.set(bounds.left + inset, bounds.top + inset,
                    bounds.right - inset, bounds.bottom - inset);
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            float[] stops = new float[]{0f, 0.22f, 0.48f, 0.72f, 1f};
            glowGradient = new SweepGradient(centerX, centerY, new int[]{
                    Color.argb(105, 0, 216, 255),
                    Color.argb(95, 91, 70, 255),
                    Color.argb(110, 242, 54, 255),
                    Color.argb(190, 255, 255, 255),
                    Color.argb(105, 0, 216, 255)
            }, stops);
            edgeGradient = new SweepGradient(centerX, centerY, new int[]{
                    COLOR_FLOW_CYAN,
                    COLOR_FLOW_VIOLET,
                    COLOR_FLOW_MAGENTA,
                    Color.WHITE,
                    COLOR_FLOW_CYAN
            }, stops);
        }

        @Override
        public void draw(Canvas canvas) {
            content.draw(canvas);
            if (glowGradient == null || edgeGradient == null || borderRect.isEmpty()) {
                return;
            }
            gradientMatrix.setRotate(rotation, borderRect.centerX(), borderRect.centerY());
            glowGradient.setLocalMatrix(gradientMatrix);
            edgeGradient.setLocalMatrix(gradientMatrix);
            glowPaint.setShader(glowGradient);
            edgePaint.setShader(edgeGradient);
            float cornerRadius = Math.max(0f, radius - edgePaint.getStrokeWidth() / 2f);
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, glowPaint);
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, edgePaint);
        }

        @Override
        public void start() {
            if (content instanceof Animatable) {
                ((Animatable) content).start();
            }
            if (!animator.isStarted()) {
                animator.start();
            }
        }

        @Override
        public void stop() {
            animator.cancel();
            if (content instanceof Animatable) {
                ((Animatable) content).stop();
            }
        }

        @Override
        public boolean isRunning() {
            return animator.isRunning()
                    || (content instanceof Animatable && ((Animatable) content).isRunning());
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            boolean changed = super.setVisible(visible, restart);
            content.setVisible(visible, restart);
            if (!visible) {
                stop();
            } else if (getCallback() != null && (restart || changed)) {
                start();
            }
            return changed;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean changed = content.setState(state);
            if (changed) {
                invalidateSelf();
            }
            return changed;
        }

        @Override
        protected boolean onLevelChange(int level) {
            return content.setLevel(level);
        }

        @Override
        public boolean isStateful() {
            return content.isStateful();
        }

        @Override
        public boolean getPadding(Rect padding) {
            return content.getPadding(padding);
        }

        @Override
        public void getOutline(Outline outline) {
            Rect bounds = getBounds();
            float outlineRadius = Math.min(radius,
                    Math.min(bounds.width(), bounds.height()) / 2f);
            outline.setRoundRect(bounds, outlineRadius);
            outline.setAlpha(1f);
        }

        @Override
        public void setHotspot(float x, float y) {
            content.setHotspot(x, y);
        }

        @Override
        public void setHotspotBounds(int left, int top, int right, int bottom) {
            content.setHotspotBounds(left, top, right, bottom);
        }

        @Override
        public void setAlpha(int alpha) {
            content.setAlpha(alpha);
            glowPaint.setAlpha(alpha);
            edgePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            content.setColorFilter(colorFilter);
            glowPaint.setColorFilter(colorFilter);
            edgePaint.setColorFilter(colorFilter);
        }

        @Override
        public void invalidateDrawable(Drawable drawable) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(Drawable drawable, Runnable runnable, long when) {
            scheduleSelf(runnable, when);
        }

        @Override
        public void unscheduleDrawable(Drawable drawable, Runnable runnable) {
            unscheduleSelf(runnable);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** 홈 히어로의 cyan/violet/magenta 팔레트를 움직임 없는 정적 면으로 그린다. */
    private static final class HeroBackgroundDrawable extends Drawable {
        private final float radius;
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF surfaceRect = new RectF();
        private final RectF borderRect = new RectF();
        private SweepGradient colorGradient;

        private HeroBackgroundDrawable(float radius, float borderWidth, int borderColor) {
            this.radius = radius;
            basePaint.setColor(COLOR_FLOW_BASE);
            colorPaint.setAlpha(210);
            scrimPaint.setColor(Color.rgb(4, 5, 12));
            scrimPaint.setAlpha(92);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(borderWidth);
            borderPaint.setColor(borderColor);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            surfaceRect.set(bounds);
            float borderInset = borderPaint.getStrokeWidth() / 2f;
            borderRect.set(bounds.left + borderInset, bounds.top + borderInset,
                    bounds.right - borderInset, bounds.bottom - borderInset);
            colorGradient = new SweepGradient(
                    bounds.exactCenterX(),
                    bounds.exactCenterY(),
                    new int[]{
                            COLOR_FLOW_CYAN,
                            COLOR_FLOW_VIOLET,
                            COLOR_FLOW_MAGENTA,
                            COLOR_FLOW_CYAN,
                            COLOR_FLOW_VIOLET,
                            COLOR_FLOW_MAGENTA,
                            COLOR_FLOW_CYAN
                    },
                    new float[]{0f, 0.17f, 0.34f, 0.5f, 0.67f, 0.84f, 1f}
            );
            colorPaint.setShader(colorGradient);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawRoundRect(surfaceRect, radius, radius, basePaint);
            if (colorGradient != null) {
                canvas.drawRoundRect(surfaceRect, radius, radius, colorPaint);
            }
            canvas.drawRoundRect(surfaceRect, radius, radius, scrimPaint);
            float borderRadius = Math.max(0f, radius - borderPaint.getStrokeWidth() / 2f);
            canvas.drawRoundRect(borderRect, borderRadius, borderRadius, borderPaint);
        }

        @Override
        public void getOutline(Outline outline) {
            Rect bounds = getBounds();
            float outlineRadius = Math.min(radius,
                    Math.min(bounds.width(), bounds.height()) / 2f);
            outline.setRoundRect(bounds, outlineRadius);
            outline.setAlpha(1f);
        }

        @Override
        public void setAlpha(int alpha) {
            basePaint.setAlpha(alpha);
            colorPaint.setAlpha(Math.round(alpha * 0.82f));
            scrimPaint.setAlpha(Math.round(alpha * 0.36f));
            borderPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            basePaint.setColorFilter(colorFilter);
            colorPaint.setColorFilter(colorFilter);
            scrimPaint.setColorFilter(colorFilter);
            borderPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    private int rawFlowColor(int variant) {
        switch (normalizeVariant(variant)) {
            case 1:
                return COLOR_FLOW_VIOLET;
            case 2:
                return COLOR_FLOW_MAGENTA;
            default:
                return COLOR_FLOW_CYAN;
        }
    }

    private int variantFor(String seed) {
        return Math.floorMod(seed == null ? 0 : seed.hashCode(), 3);
    }

    private int normalizeVariant(int variant) {
        return Math.floorMod(variant, 3);
    }

    private GradientDrawable.Orientation gradientOrientation(int variant) {
        switch (normalizeVariant(variant)) {
            case 1:
                return GradientDrawable.Orientation.TL_BR;
            case 2:
                return GradientDrawable.Orientation.BL_TR;
            default:
                return GradientDrawable.Orientation.LEFT_RIGHT;
        }
    }

    private int mix(int base, int color, float colorWeight) {
        float weight = Math.max(0f, Math.min(1f, colorWeight));
        float baseWeight = 1f - weight;
        return Color.rgb(
                Math.round(Color.red(base) * baseWeight + Color.red(color) * weight),
                Math.round(Color.green(base) * baseWeight + Color.green(color) * weight),
                Math.round(Color.blue(base) * baseWeight + Color.blue(color) * weight)
        );
    }

    public int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    /** 공통 깊이 토큰. 다크 모드에서는 흰 그림자를 사용해 검은 배경에서도 층위를 유지한다. */
    public void applyDepth(View view, int elevationDp) {
        view.setElevation(dp(elevationDp));
        view.setTranslationZ(0f);
        view.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        view.setClipToOutline(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int ambient = dark()
                    ? Color.argb(72, 255, 255, 255)
                    : Color.argb(72, 0, 0, 0);
            int spot = dark()
                    ? Color.argb(132, 255, 255, 255)
                    : Color.argb(138, 0, 0, 0);
            view.setOutlineAmbientShadowColor(ambient);
            view.setOutlineSpotShadowColor(spot);
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
        if (!dark()) {
            return color;
        }
        if (color == COLOR_TEXT) {
            return COLOR_D_TEXT;
        }
        if (color == COLOR_MUTED) {
            return COLOR_D_MUTED;
        }
        if (color == COLOR_TERTIARY) {
            return COLOR_D_TERTIARY;
        }
        if (color == COLOR_INVERSE_TEXT) {
            return COLOR_TEXT;
        }
        if (color == COLOR_INVERSE_MUTED) {
            return COLOR_D_ON_ACCENT_MUTED;
        }
        return color;
    }

    public TextView labelView(String value) {
        return caption(value, COLOR_MUTED);
    }

    public TextView titleView(String value) {
        TextView view = text(value, 27, COLOR_TEXT, true);
        view.setLetterSpacing(-0.02f);
        view.setPadding(0, dp(4), 0, dp(18));
        return view;
    }

    public View sectionHeader(String labelText, String actionText, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(26), 0, dp(10));

        TextView labelView = caption(labelText, COLOR_MUTED);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (actionText != null && action != null) {
            TextView actionView = text(actionText + " ›", 13, COLOR_TERTIARY, true);
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
        card.setBackground(borderDrawable(surface(), Color.TRANSPARENT, dp(16)));
        applyDepth(card, 5);
        card.setLayoutParams(fullWidthParams(dp(12)));
        return card;
    }

    public LinearLayout heroCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        card.setBackground(heroBackground());
        applyDepth(card, 12);
        card.setLayoutParams(fullWidthParams(dp(12)));
        return card;
    }

    private HeroBackgroundDrawable heroBackground() {
        return new HeroBackgroundDrawable(dp(24), dp(1), border());
    }

    public GradientDrawable borderDrawable(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        // 기존 호출부 호환을 위해 stroke 인자는 유지하고 실제 테두리는 테마 색으로 통일한다.
        drawable.setStroke(dp(1), border());
        drawable.setCornerRadius(radius);
        return drawable;
    }

    public Drawable rippleDrawable(int fill, int stroke, int radius, int rippleColor) {
        GradientDrawable background = borderDrawable(fill, stroke, radius);
        GradientDrawable mask = borderDrawable(Color.WHITE, Color.WHITE, radius);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), background, mask);
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

    public Button button(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setStateListAnimator(null);
        button.setTextColor(primary ? onVibrant() : ink());
        button.setBackground(primary
                ? vibrantRippleDrawable(text, dp(999))
                : flatSurfaceRippleDrawable(dp(999)));
        applyDepth(button, primary ? 7 : 4);
        button.setOnClickListener(listener);
        pressFeedback(button);
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
        button.setTextColor(active ? onVibrant() : inkMuted());
        String seed = String.valueOf(button.getText());
        button.setBackground(active
                ? vibrantRippleDrawable(seed, dp(999))
                : flatSurfaceRippleDrawable(dp(999)));
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
        input.setTextColor(ink());
        input.setHintTextColor(inkTertiary());
        input.setMinHeight(dp(48));
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        input.setBackground(flatSurfaceDrawable(dp(12)));
        applyDepth(input, 3);
        return input;
    }

    public EditText searchField(String hint) {
        EditText input = input(hint, "");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(flatSurfaceDrawable(dp(999)));
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
        TextView labelView = caption(label, COLOR_MUTED);
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

    public View inlineStat(String label, String value, boolean onAccentSurface) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.addView(caption(label, onAccentSurface ? COLOR_INVERSE_MUTED : COLOR_MUTED));
        TextView valueView = num(value, 16, onAccentSurface ? COLOR_INVERSE_TEXT : COLOR_TEXT, true);
        valueView.setPadding(0, dp(3), 0, 0);
        cell.addView(valueView);
        return cell;
    }

    public View statusDotBadge(String labelText, int dotColor, boolean onAccentSurface) {
        LinearLayout badge = new LinearLayout(activity);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        if (onAccentSurface) {
            badge.setBackground(borderDrawable(chipOnAccent(), chipOnAccent(), dp(999)));
        } else {
            badge.setBackground(flatSurfaceDrawable(dp(999)));
        }
        badge.setPadding(dp(10), dp(5), dp(12), dp(5));
        applyDepth(badge, 2);

        View dot = new View(activity);
        dot.setBackground(borderDrawable(dotColor, dotColor, dp(999)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.setMargins(0, 0, dp(6), 0);
        badge.addView(dot, dotParams);

        badge.addView(text(labelText, 12, onAccentSurface ? COLOR_INVERSE_TEXT : COLOR_TEXT, true));
        return badge;
    }

    /** 고정 다크 히어로 위 상태 배지. 앱의 light/dark 반전 매핑을 적용하지 않는다. */
    public View flowStatusBadge(String labelText, int dotColor) {
        LinearLayout badge = new LinearLayout(activity);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setBackground(borderDrawable(
                COLOR_FLOW_GLASS_FILL, COLOR_FLOW_GLASS_BORDER, dp(999)));
        badge.setPadding(dp(10), dp(5), dp(12), dp(5));
        applyDepth(badge, 3);

        View dot = new View(activity);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dot.setBackground(borderDrawable(dotColor, dotColor, dp(999)));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.setMargins(0, 0, dp(6), 0);
        badge.addView(dot, dotParams);
        badge.addView(text(labelText, 12, COLOR_FLOW_TEXT, true));
        return badge;
    }

    /** 고정 다크 히어로의 지표 셀. 실제 blur 대신 반투명 fill과 border만 사용한다. */
    public View flowMetric(String label, String value) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setMinimumHeight(dp(64));
        cell.setPadding(dp(8), dp(10), dp(8), dp(10));
        cell.setBackground(borderDrawable(
                COLOR_FLOW_GLASS_FILL, COLOR_FLOW_GLASS_BORDER, dp(14)));
        applyDepth(cell, 4);

        TextView labelView = caption(label, COLOR_FLOW_MUTED);
        labelView.setGravity(Gravity.CENTER);
        cell.addView(labelView);

        TextView valueView = num(value, 15, COLOR_FLOW_TEXT, true);
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(0, dp(3), 0, 0);
        cell.addView(valueView);
        return cell;
    }

    /** Flowstate의 white pill을 기존 화면 이동에 연결하는 홈 전용 CTA. */
    public Button flowHeroButton(String text, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(20), 0, dp(20), 0);
        button.setStateListAnimator(null);
        button.setTextColor(Color.rgb(47, 47, 51));
        button.setBackground(rippleDrawable(
                Color.WHITE, Color.WHITE, dp(999), Color.argb(36, 47, 47, 51)));
        applyDepth(button, 7);
        button.setOnClickListener(listener);
        pressFeedback(button);
        return button;
    }

    public View statTile(String label, String value, String meta, boolean inverseTile, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(16), dp(14), dp(16), dp(14));
        tile.setMinimumHeight(dp(92));
        if (listener != null) {
            tile.setBackground(inverseTile
                    ? vibrantRippleDrawable(label, dp(14))
                    : flatSurfaceRippleDrawable(dp(14)));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setOnClickListener(listener);
            pressFeedback(tile);
        } else {
            tile.setBackground(inverseTile
                    ? vibrantBackground(variantFor(label), dp(14))
                    : flatSurfaceDrawable(dp(14)));
        }
        applyDepth(tile, inverseTile ? 7 : 4);

        TextView labelView = caption(label, inverseTile ? COLOR_INVERSE_MUTED : COLOR_MUTED);
        if (inverseTile) {
            labelView.setTextColor(onVibrantMuted());
        }
        tile.addView(labelView);
        TextView valueView = num(value, 21, inverseTile ? COLOR_INVERSE_TEXT : COLOR_TEXT, true);
        if (inverseTile) {
            valueView.setTextColor(onVibrant());
        }
        valueView.setPadding(0, dp(7), 0, 0);
        tile.addView(valueView);
        if (meta != null) {
            TextView metaView = text(meta, 11, inverseTile ? COLOR_INVERSE_MUTED : COLOR_TERTIARY, false);
            if (inverseTile) {
                metaView.setTextColor(onVibrantMuted());
            }
            metaView.setPadding(0, dp(3), 0, 0);
            tile.addView(metaView);
        }
        return tile;
    }

    /**
     * 기본 표면과 텍스트 대비는 유지하고, 탭 가능한 상태 카드의 외곽만 홀로그램으로 강조한다.
     */
    public View hologramStatTile(String label, String value, String meta, View.OnClickListener listener) {
        View tile = statTile(label, value, meta, false, listener);
        setHologramBackground(tile, tile.getBackground(), dp(14));
        applyDepth(tile, 7);
        return tile;
    }

    public View glyphCircle(String glyph, boolean onAccentSurface) {
        TextView circle = text(glyph, 14, onAccentSurface ? COLOR_INVERSE_TEXT : COLOR_MUTED, true);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(onAccentSurface
                ? borderDrawable(chipOnAccent(), chipOnAccent(), dp(999))
                : flatSurfaceDrawable(dp(999)));
        applyDepth(circle, 2);
        circle.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return circle;
    }

    /** 데이터 기록의 시작점을 작고 선명한 홀로그램 배지로 표시한다. */
    public View vibrantGlyphCircle(String glyph, String seed) {
        TextView circle = text(glyph, 14, COLOR_INVERSE_TEXT, true);
        circle.setTextColor(onVibrant());
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(vibrantBackground(variantFor(seed), dp(999)));
        applyDepth(circle, 4);
        circle.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        return circle;
    }

    public View orderBadge(int order, boolean onAccentSurface) {
        TextView badge = num(String.valueOf(order), 13, onAccentSurface ? COLOR_INVERSE_TEXT : COLOR_TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(onAccentSurface
                ? borderDrawable(chipOnAccent(), chipOnAccent(), dp(999))
                : flatSurfaceDrawable(dp(999)));
        applyDepth(badge, 2);
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(28)));
        return badge;
    }

    public View compactOrderBadge(int order) {
        TextView badge = num(String.valueOf(order), 11, COLOR_TEXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(flatSurfaceDrawable(dp(999)));
        applyDepth(badge, 2);
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
        final List<Double> points = values == null ? java.util.Collections.emptyList() : new java.util.ArrayList<>(values);
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
                int top = dp(12);
                int bottom = getHeight() - dp(14);
                paint.setStrokeWidth(dp(1));
                paint.setColor(axisColor);
                canvas.drawLine(left, bottom, right, bottom, paint);
                if (points.isEmpty()) {
                    paint.setTextSize(dp(12));
                    paint.setColor(mutedColor);
                    canvas.drawText("이전 기록 없음", left, top + dp(14), paint);
                    return;
                }

                double max = 1;
                for (Double value : points) {
                    if (value != null) {
                        max = Math.max(max, value);
                    }
                }
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
                    canvas.drawCircle(x, y, dp(4), paint);
                }
            }
        };
    }

    // ── 모션 ─────────────────────────────────────────────────────────

    /** 버튼/타일 공통 프레스 스케일 피드백. 클릭 이벤트를 소비하지 않는다. */
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

    /**
     * 브랜드 바텀시트. 현재 테마의 카드 표면을 따르며,
     * 상단 라운드 24dp + 드래그 핸들 + 하단 고정 Primary CTA, 슬라이드업 진입.
     */
    public Dialog sheet(String title, View body,
                        String primaryText, Runnable onPrimary,
                        String dangerText, Runnable onDanger) {
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
        applyDepth(sheet, 16);

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
            onPrimary.run();
            dialog.dismiss();
        });
        sheet.addView(primary, fullWidthParams(dp(20)));

        if (dangerText != null && onDanger != null) {
            TextView danger = new TextView(activity);
            danger.setText(dangerText);
            danger.setTextSize(14);
            danger.setTextColor(COLOR_NEGATIVE);
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
        }

        dialog.setContentView(sheet);
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

    /** 파괴적 행동 확인 시트. 결과 문장은 sem.negative로 명시하고 CTA는 블랙 필을 유지한다. */
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
            consequenceView.setTextColor(COLOR_NEGATIVE);
            consequenceView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            consequenceView.setPadding(0, dp(10), 0, 0);
            body.addView(consequenceView);
        }
        sheet(title, body, actionText, onConfirm, null, null);
    }

    /** 시트 전용 Primary 버튼: 현재 테마의 강조 필(라이트=블랙, 다크=화이트). */
    private Button sheetPrimaryButton(String text, Runnable action) {
        Button button = new Button(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(18), 0, dp(18), 0);
        button.setStateListAnimator(null);
        button.setTextColor(onVibrant());
        button.setBackground(vibrantRippleDrawable("sheet-primary-" + text, dp(999)));
        button.setOnClickListener(v -> action.run());
        pressFeedback(button);
        return button;
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
                            String latestWorkoutDate,
                            Runnable onStart, Runnable onDetail) {
        LinearLayout card = card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onStart.run());
        applyDepth(card, 6);
        pressFeedback(card);

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
        column.addView(text(recentWorkoutText(latestWorkoutDate), 11, COLOR_TERTIARY, false));
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

    public View quickStartRoutineCard(String routineName, int exerciseCount, String latestWorkoutDate,
                                      Runnable onStart, Runnable onDetail) {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(vibrantRippleDrawable("routine-" + routineName, dp(16)));
        applyDepth(card, 9);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> onStart.run());
        pressFeedback(card);

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView routineGlyph = text("루", 12, COLOR_INVERSE_TEXT, true);
        routineGlyph.setTextColor(onVibrant());
        routineGlyph.setGravity(Gravity.CENTER);
        routineGlyph.setBackground(borderDrawable(chipOnVibrant(), chipOnVibrant(), dp(999)));
        routineGlyph.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(30)));
        headerRow.addView(routineGlyph);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(9), 0, 0, 0);
        TextView nameView = text(routineName, 14, COLOR_INVERSE_TEXT, true);
        nameView.setTextColor(onVibrant());
        column.addView(nameView);
        TextView meta = text(exerciseCount + "개 종목 · 탭하여 시작", 10, COLOR_INVERSE_MUTED, false);
        meta.setTextColor(onVibrantMuted());
        meta.setPadding(0, dp(2), 0, 0);
        column.addView(meta);
        TextView recentView = text(recentWorkoutText(latestWorkoutDate), 10, COLOR_INVERSE_MUTED, false);
        recentView.setTextColor(onVibrantMuted());
        column.addView(recentView);
        headerRow.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chevron = text("›", 16, COLOR_INVERSE_MUTED, false);
        chevron.setTextColor(onVibrantMuted());
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
