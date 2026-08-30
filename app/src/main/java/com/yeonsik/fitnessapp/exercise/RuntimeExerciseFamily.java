package com.yeonsik.fitnessapp.exercise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime family grouping used by the family-first exercise picker. */
public final class RuntimeExerciseFamily {
    public final String familyId;
    public final String nameKo;
    public final String nameEn;
    public final String defaultUiPart;
    public final List<String> allowedVariantDimensions;
    public final List<LoadState> allowedLoadStates;
    public final List<RuntimeExercisePreset> presets;
    public final Map<String, RuntimeExercisePreset> presetById;

    RuntimeExerciseFamily(
            String familyId,
            String nameKo,
            String nameEn,
            String defaultUiPart,
            List<String> allowedVariantDimensions,
            List<LoadState> allowedLoadStates,
            List<RuntimeExercisePreset> presets
    ) {
        this.familyId = familyId;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.defaultUiPart = defaultUiPart;
        this.allowedVariantDimensions = immutableList(allowedVariantDimensions);
        this.allowedLoadStates = immutableList(allowedLoadStates);
        this.presets = immutableList(presets);
        Map<String, RuntimeExercisePreset> byId = new LinkedHashMap<>();
        for (RuntimeExercisePreset preset : this.presets) {
            if (preset != null && preset.presetId != null) {
                byId.put(preset.presetId, preset);
            }
        }
        this.presetById = Collections.unmodifiableMap(byId);
    }

    public String displayName() {
        return nameKo == null || nameKo.trim().isEmpty() ? nameEn : nameKo;
    }

    public RuntimeExercisePreset preset(String presetId) {
        return presetId == null ? null : presetById.get(presetId.trim());
    }

    public boolean supportsLoadState(LoadState loadState) {
        return loadState != null && allowedLoadStates.contains(loadState);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null
                ? Collections.emptyList()
                : values));
    }
}
