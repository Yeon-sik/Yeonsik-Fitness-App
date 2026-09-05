package com.yeonsik.fitnessapp.data;

import java.util.Locale;

/** Supported user-facing mass units. Stored workout mass remains canonical kilograms. */
public enum MassUnit {
    KG("kg", "킬로그램"),
    LB("lb", "파운드");

    public static final double KILOGRAMS_PER_POUND = 0.45359237d;

    private final String id;
    private final String labelKo;

    MassUnit(String id, String labelKo) {
        this.id = id;
        this.labelKo = labelKo;
    }

    public String id() {
        return id;
    }

    public String symbol() {
        return id;
    }

    public String labelKo() {
        return labelKo;
    }

    public static MassUnit parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (MassUnit unit : values()) {
            if (unit.id.equals(normalized)) {
                return unit;
            }
        }
        return null;
    }

    public static MassUnit orDefault(MassUnit unit) {
        return unit == null ? KG : unit;
    }

    public static double toKg(double value, MassUnit unit) {
        MassUnit effective = orDefault(unit);
        return effective == LB ? value * KILOGRAMS_PER_POUND : value;
    }

    public static double fromKg(double kilograms, MassUnit unit) {
        MassUnit effective = orDefault(unit);
        return effective == LB ? kilograms / KILOGRAMS_PER_POUND : kilograms;
    }

    public static double convert(double value, MassUnit from, MassUnit to) {
        return fromKg(toKg(value, from), to);
    }

    public double toKg(double value) {
        return toKg(value, this);
    }

    public double fromKg(double kilograms) {
        return fromKg(kilograms, this);
    }
}
