package com.yeonsik.fitnessapp.config;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the app theme choice used by the Settings feature and window adapter. */
public final class ThemeModePreferences {
    public static final String PREFERENCES_NAME = "fitness_ui_prefs";
    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String DEFAULT_THEME_MODE = "light";

    private final SharedPreferences preferences;

    public ThemeModePreferences(Context context) {
        Context applicationContext = context == null ? null : context.getApplicationContext();
        Context effectiveContext = applicationContext == null ? context : applicationContext;
        if (effectiveContext == null) {
            throw new IllegalArgumentException("테마 설정 저장소에 사용할 Context가 없습니다.");
        }
        preferences = effectiveContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public String themeMode() {
        return preferences.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE);
    }

    public String getThemeMode() {
        return themeMode();
    }

    public void setThemeMode(String mode) {
        preferences.edit()
                .putString(KEY_THEME_MODE, mode == null ? DEFAULT_THEME_MODE : mode)
                .apply();
    }
}
