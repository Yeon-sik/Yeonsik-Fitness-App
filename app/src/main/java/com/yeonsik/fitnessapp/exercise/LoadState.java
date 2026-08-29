package com.yeonsik.fitnessapp.exercise;

import java.util.Locale;

/**
 * Resistance semantics for one recorded set. This is deliberately not an exercise identity.
 */
public enum LoadState {
    BODYWEIGHT("bodyweight"),
    EXTERNAL_LOAD("external_load"),
    ADDED_WEIGHT("added_weight"),
    ASSISTED("assisted"),
    BAND_ASSISTED("band_assisted"),
    BAND_RESISTED("band_resisted");

    private final String id;

    LoadState(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static LoadState fromId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (LoadState state : values()) {
            if (state.id.equals(normalized)) {
                return state;
            }
        }
        return null;
    }

    public boolean isBodyweightCapable() {
        return this == BODYWEIGHT
                || this == ADDED_WEIGHT
                || this == ASSISTED
                || this == BAND_ASSISTED;
    }

    public boolean isBandSemantics() {
        return this == BAND_ASSISTED || this == BAND_RESISTED;
    }
}
