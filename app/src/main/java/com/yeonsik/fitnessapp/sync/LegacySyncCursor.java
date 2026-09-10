package com.yeonsik.fitnessapp.sync;

/** Keyset cursor used by the legacy Fitness sync protocol. */
public final class LegacySyncCursor {
    private final String version;
    private final String id;

    public LegacySyncCursor(String version, String id) {
        this.version = version;
        this.id = id == null ? "" : id;
    }

    public static LegacySyncCursor empty() {
        return new LegacySyncCursor(null, "");
    }

    public String getVersion() {
        return version;
    }

    public String getId() {
        return id;
    }
}
