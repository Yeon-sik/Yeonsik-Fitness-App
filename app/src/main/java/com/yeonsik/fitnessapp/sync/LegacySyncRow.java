package com.yeonsik.fitnessapp.sync;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Transport-neutral snapshot of one legacy sync row. */
public final class LegacySyncRow {
    private final Map<String, Object> values;

    public LegacySyncRow(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public Object value(String column) {
        return values.get(column);
    }

    public boolean contains(String column) {
        return values.containsKey(column);
    }

    public Set<String> columns() {
        return values.keySet();
    }

    public Map<String, Object> values() {
        return values;
    }
}
