package com.yeonsik.fitnessapp.exercise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One user-selectable canonical exercise preset in the runtime catalog. */
public final class RuntimeExercisePreset {
    public final String presetId;
    public final String canonicalPresetId;
    public final String storageExerciseId;
    public final String familyId;
    public final String familyNameKo;
    public final String familyNameEn;
    public final String nameKo;
    public final String nameEn;
    public final String legacyNameKo;
    public final String legacyNameEn;
    public final String defaultUiPart;
    public final String equipmentVariantId;
    public final String primarySubPart;
    public final String primarySubPartNameKo;
    public final List<String> secondarySubParts;
    public final List<String> secondarySubPartNamesKo;
    public final String equipmentNameKo;
    public final UiEquipmentCategory uiEquipmentCategory;
    public final String recordType;
    public final LoadState defaultLoadState;
    public final List<LoadState> allowedLoadStates;
    public final String canonicalVariantKey;
    public final String visualVariantKey;
    public final String illustrationKey;
    public final List<String> legacyIds;
    public final List<String> searchAliases;
    public final Map<String, LoadState> searchAliasLoadStates;
    public final Map<String, String> variant;
    public final boolean aliasMerged;
    public final boolean approvedNewPreset;

    RuntimeExercisePreset(
            String presetId,
            String canonicalPresetId,
            String storageExerciseId,
            String familyId,
            String familyNameKo,
            String familyNameEn,
            String nameKo,
            String nameEn,
            String legacyNameKo,
            String legacyNameEn,
            String defaultUiPart,
            String equipmentVariantId,
            String primarySubPart,
            String primarySubPartNameKo,
            List<String> secondarySubParts,
            List<String> secondarySubPartNamesKo,
            String equipmentNameKo,
            String recordType,
            LoadState defaultLoadState,
            List<LoadState> allowedLoadStates,
            String canonicalVariantKey,
            String visualVariantKey,
            String illustrationKey,
            List<String> legacyIds,
            List<String> searchAliases,
            Map<String, LoadState> searchAliasLoadStates,
            Map<String, String> variant,
            boolean aliasMerged,
            boolean approvedNewPreset
    ) {
        this.presetId = presetId;
        this.canonicalPresetId = canonicalPresetId;
        this.storageExerciseId = storageExerciseId;
        this.familyId = familyId;
        this.familyNameKo = familyNameKo;
        this.familyNameEn = familyNameEn;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.legacyNameKo = legacyNameKo;
        this.legacyNameEn = legacyNameEn;
        this.defaultUiPart = defaultUiPart;
        this.equipmentVariantId = equipmentVariantId;
        this.primarySubPart = primarySubPart;
        this.primarySubPartNameKo = primarySubPartNameKo;
        this.secondarySubParts = immutableList(secondarySubParts);
        this.secondarySubPartNamesKo = immutableList(secondarySubPartNamesKo);
        this.equipmentNameKo = equipmentNameKo;
        this.uiEquipmentCategory = UiEquipmentCategory.fromEquipmentVariant(equipmentVariantId);
        this.recordType = recordType;
        this.defaultLoadState = defaultLoadState;
        this.allowedLoadStates = immutableList(allowedLoadStates);
        this.canonicalVariantKey = canonicalVariantKey;
        this.visualVariantKey = visualVariantKey;
        this.illustrationKey = illustrationKey;
        this.legacyIds = immutableList(legacyIds);
        this.searchAliases = immutableList(searchAliases);
        this.searchAliasLoadStates = Collections.unmodifiableMap(new LinkedHashMap<>(
                searchAliasLoadStates == null ? Collections.emptyMap() : searchAliasLoadStates
        ));
        this.variant = Collections.unmodifiableMap(new LinkedHashMap<>(variant == null
                ? Collections.emptyMap()
                : variant));
        this.aliasMerged = aliasMerged;
        this.approvedNewPreset = approvedNewPreset;
    }

    public String displayName() {
        if (nameKo != null && !nameKo.trim().isEmpty()) {
            return nameKo;
        }
        return nameEn == null ? "운동" : nameEn;
    }

    public String identityId() {
        return canonicalPresetId == null ? presetId : canonicalPresetId;
    }

    public boolean supportsLoadState(LoadState loadState) {
        return loadState != null && allowedLoadStates.contains(loadState);
    }

    public boolean hasLegacyId(String legacyId) {
        return legacyId != null && legacyIds.contains(legacyId.trim());
    }

    public LoadState searchAliasLoadState(String alias) {
        return alias == null ? null : searchAliasLoadStates.get(alias.trim());
    }

    public String equipmentVariantIdOrEmpty() {
        return equipmentVariantId == null ? "" : equipmentVariantId;
    }

    private static <T> List<T> immutableList(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null
                ? Collections.emptyList()
                : values));
    }
}
