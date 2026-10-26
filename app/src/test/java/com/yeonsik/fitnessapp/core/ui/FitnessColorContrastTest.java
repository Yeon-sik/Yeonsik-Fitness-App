package com.yeonsik.fitnessapp.core.ui;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure WCAG relative-luminance checks; no Android graphics implementation is needed. */
public final class FitnessColorContrastTest {
    @Test
    public void foregroundBlueIsReadableOnEveryLightSurface() {
        for (int surface : new int[]{
                FitnessUiTokens.COLOR_SURFACE, FitnessUiTokens.COLOR_BACKGROUND,
                FitnessUiTokens.COLOR_SUBTLE, FitnessUiTokens.COLOR_BLUE_CONTAINER
        }) {
            assertContrast(FitnessUiTokens.COLOR_BRAND_BLUE_TEXT, surface, 4.5);
        }
        assertContrast(FitnessUiTokens.COLOR_ON_BRAND_BLUE_TEXT,
                FitnessUiTokens.COLOR_BRAND_BLUE_TEXT, 4.5);
    }

    @Test
    public void brandFillAndDarkForegroundKeepReadableContent() {
        assertContrast(FitnessUiTokens.COLOR_BLUE_INK, FitnessUiTokens.COLOR_BRAND_BLUE, 4.5);
        assertContrast(FitnessUiTokens.COLOR_D_ON_PASTEL_BLUE, FitnessUiTokens.COLOR_BRAND_BLUE, 4.5);
        assertContrast(FitnessUiTokens.COLOR_BRAND_BLUE, FitnessUiTokens.COLOR_D_SURFACE, 4.5);
        assertContrast(FitnessUiTokens.COLOR_BRAND_BLUE, FitnessUiTokens.COLOR_D_SUBTLE, 4.5);
    }

    @Test
    public void heroTextIsReadableAcrossBothThemesAndBothGradientEnds() {
        for (int foreground : new int[]{
                FitnessUiTokens.COLOR_TEXT, FitnessUiTokens.COLOR_MUTED,
                FitnessUiTokens.COLOR_POSITIVE, FitnessUiTokens.COLOR_NEGATIVE
        }) {
            assertContrast(foreground, FitnessUiTokens.COLOR_HERO_SURFACE, 4.5);
        }
        for (int background : new int[]{
                FitnessUiTokens.COLOR_D_HERO_SURFACE_TOP,
                FitnessUiTokens.COLOR_D_HERO_SURFACE_BOTTOM
        }) {
            for (int foreground : new int[]{
                    FitnessUiTokens.COLOR_D_BLUE_INK, FitnessUiTokens.COLOR_D_HERO_MUTED,
                    FitnessUiTokens.COLOR_D_POSITIVE, FitnessUiTokens.COLOR_D_NEGATIVE
            }) {
                assertContrast(foreground, background, 4.5);
            }
        }
    }

    private static void assertContrast(int foreground, int background, double minimum) {
        double a = luminance(foreground);
        double b = luminance(background);
        double ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
        assertTrue(String.format("#%06X on #%06X: %.2f < %.1f",
                foreground & 0xffffff, background & 0xffffff, ratio, minimum), ratio >= minimum);
    }

    private static double luminance(int color) {
        return 0.2126 * linear((color >>> 16) & 0xff)
                + 0.7152 * linear((color >>> 8) & 0xff)
                + 0.0722 * linear(color & 0xff);
    }

    private static double linear(int channel) {
        double srgb = channel / 255.0;
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }
}
