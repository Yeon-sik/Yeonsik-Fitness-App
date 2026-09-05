package com.yeonsik.fitnessapp.config;

import android.content.Context;
import android.content.SharedPreferences;

import com.yeonsik.fitnessapp.data.MassUnit;

/** Persists the preferred display/input unit while keeping the local data contract in kg. */
public final class MassUnitPreferences {
    public static final String PREFERENCES_NAME = "fitness_ui_prefs";
    public static final String KEY_PREFERRED_MASS_UNIT = "preferred_mass_unit";
    public static final MassUnit DEFAULT_MASS_UNIT = MassUnit.KG;

    private final SharedPreferences preferences;

    public MassUnitPreferences(Context context) {
        Context applicationContext = context == null ? null : context.getApplicationContext();
        Context effectiveContext = applicationContext == null ? context : applicationContext;
        if (effectiveContext == null) {
            throw new IllegalArgumentException("설정 저장소에 사용할 Context가 없습니다.");
        }
        preferences = effectiveContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public MassUnit preferredMassUnit() {
        return MassUnit.orDefault(MassUnit.parse(preferences.getString(
                KEY_PREFERRED_MASS_UNIT,
                DEFAULT_MASS_UNIT.id()
        )));
    }

    public MassUnit getPreferredMassUnit() {
        return preferredMassUnit();
    }

    public void setPreferredMassUnit(MassUnit unit) {
        preferences.edit()
                .putString(KEY_PREFERRED_MASS_UNIT, MassUnit.orDefault(unit).id())
                .apply();
    }
}
