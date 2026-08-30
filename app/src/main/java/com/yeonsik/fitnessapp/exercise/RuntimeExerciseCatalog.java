package com.yeonsik.fitnessapp.exercise;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runtime projection of the generated exercise family mapping.
 *
 * <p>This class consumes only generated catalog data. It does not create or infer exercises,
 * families, presets, or variants.</p>
 */
public final class RuntimeExerciseCatalog {
    private static final RuntimeExerciseCatalog EMPTY = new RuntimeExerciseCatalog(
            Collections.emptyList(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyList()
    );

    public final List<RuntimeExerciseFamily> families;
    public final Map<String, RuntimeExerciseFamily> familyById;
    public final Map<String, RuntimeExercisePreset> presetsById;
    public final Map<String, RuntimeExercisePreset> presetsByLegacyId;
    public final List<LoadState> allowedLoadStates;

    private RuntimeExerciseCatalog(
            List<RuntimeExerciseFamily> families,
            Map<String, RuntimeExerciseFamily> familyById,
            Map<String, RuntimeExercisePreset> presetsById,
            Map<String, RuntimeExercisePreset> presetsByLegacyId,
            List<LoadState> allowedLoadStates
    ) {
        this.families = immutableList(families);
        this.familyById = Collections.unmodifiableMap(new LinkedHashMap<>(familyById));
        this.presetsById = Collections.unmodifiableMap(new LinkedHashMap<>(presetsById));
        this.presetsByLegacyId = Collections.unmodifiableMap(new LinkedHashMap<>(presetsByLegacyId));
        this.allowedLoadStates = immutableList(allowedLoadStates);
    }

    public static RuntimeExerciseCatalog empty() {
        return EMPTY;
    }

    public static RuntimeExerciseCatalog fromJson(JSONObject document) {
        if (document == null || document.optInt("schemaVersion", 0) != 1) {
            return EMPTY;
        }

        Map<String, FamilySpec> familySpecs = parseFamilySpecs(document.optJSONObject("families"));
        Map<String, MergeSpec> mergesByLegacyId = parseAliasMerges(document.optJSONArray("canonicalAliasMerges"));
        Map<String, PresetBuilder> builders = new LinkedHashMap<>();
        Map<String, String> presetIdByLegacyId = new LinkedHashMap<>();

        JSONArray legacyExercises = document.optJSONArray("legacyExercises");
        if (legacyExercises != null) {
            for (int index = 0; index < legacyExercises.length(); index += 1) {
                JSONObject item = legacyExercises.optJSONObject(index);
                if (item == null || !"mapped".equals(item.optString("status"))) {
                    continue;
                }
                String legacyId = nullableString(item, "legacyExerciseId");
                String familyId = nullableString(item, "familyId");
                String runtimePresetId = nullableString(item, "canonicalPresetId");
                if (legacyId == null || familyId == null || runtimePresetId == null
                        || !familySpecs.containsKey(familyId)) {
                    continue;
                }
                MergeSpec merge = mergesByLegacyId.get(legacyId);
                PresetBuilder builder = builders.get(runtimePresetId);
                if (builder == null) {
                    FamilySpec family = familySpecs.get(familyId);
                    builder = PresetBuilder.forLegacy(
                            runtimePresetId,
                            item,
                            family,
                            merge
                    );
                    builders.put(runtimePresetId, builder);
                }
                builder.addLegacy(legacyId);
                presetIdByLegacyId.put(legacyId, runtimePresetId);
            }
        }

        JSONArray approvedPresets = document.optJSONArray("approvedPresets");
        if (approvedPresets != null) {
            for (int index = 0; index < approvedPresets.length(); index += 1) {
                JSONObject item = approvedPresets.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                String presetId = nullableString(item, "presetId");
                String familyId = nullableString(item, "familyId");
                if (presetId == null || familyId == null || !familySpecs.containsKey(familyId)) {
                    continue;
                }
                if (builders.containsKey(presetId)) {
                    // A contract collision is not resolved by choosing one entry.
                    continue;
                }
                builders.put(
                        presetId,
                        PresetBuilder.forApproved(presetId, item, familySpecs.get(familyId))
                );
            }
        }

        addSearchAliases(document.optJSONArray("searchPresetAliases"), builders, presetIdByLegacyId);

        Map<String, RuntimeExercisePreset> presetsById = new LinkedHashMap<>();
        Map<String, List<RuntimeExercisePreset>> presetsByFamily = new LinkedHashMap<>();
        Map<String, RuntimeExercisePreset> presetsByLegacyId = new LinkedHashMap<>();
        for (PresetBuilder builder : builders.values()) {
            RuntimeExercisePreset preset = builder.build();
            if (preset == null || preset.presetId == null || presetsById.containsKey(preset.presetId)) {
                continue;
            }
            presetsById.put(preset.presetId, preset);
            presetsByFamily.computeIfAbsent(preset.familyId, ignored -> new ArrayList<>()).add(preset);
            for (String legacyId : preset.legacyIds) {
                presetsByLegacyId.put(legacyId, preset);
            }
        }

        Map<String, RuntimeExerciseFamily> familyById = new LinkedHashMap<>();
        List<RuntimeExerciseFamily> families = new ArrayList<>();
        for (Map.Entry<String, FamilySpec> entry : familySpecs.entrySet()) {
            FamilySpec spec = entry.getValue();
            List<RuntimeExercisePreset> presets = presetsByFamily.getOrDefault(
                    spec.familyId,
                    Collections.emptyList()
            );
            RuntimeExerciseFamily family = new RuntimeExerciseFamily(
                    spec.familyId,
                    spec.nameKo,
                    spec.nameEn,
                    spec.defaultUiPart,
                    spec.allowedVariantDimensions,
                    spec.allowedLoadStates,
                    presets
            );
            families.add(family);
            familyById.put(family.familyId, family);
        }

        return new RuntimeExerciseCatalog(
                families,
                familyById,
                presetsById,
                presetsByLegacyId,
                parseLoadStates(document.optJSONArray("allowedLoadStates") == null
                        ? document.optJSONArray("loadStates")
                        : document.optJSONArray("allowedLoadStates"))
        );
    }

    public List<RuntimeExerciseFamily> getFamilies() {
        return families;
    }

    public RuntimeExerciseFamily family(String familyId) {
        return familyId == null ? null : familyById.get(familyId.trim());
    }

    public RuntimeExercisePreset preset(String presetId) {
        return presetId == null ? null : presetsById.get(presetId.trim());
    }

    public RuntimeExercisePreset presetForLegacyId(String legacyExerciseId) {
        return legacyExerciseId == null
                ? null
                : presetsByLegacyId.get(legacyExerciseId.trim());
    }

    public RuntimeExercisePreset presetForStorageExerciseId(String storageExerciseId) {
        if (storageExerciseId == null) {
            return null;
        }
        String normalized = storageExerciseId.trim();
        RuntimeExercisePreset byLegacy = presetsByLegacyId.get(normalized);
        return byLegacy == null ? presetsById.get(normalized) : byLegacy;
    }

    public int familyCount() {
        return families.size();
    }

    public int presetCount() {
        return presetsById.size();
    }

    private static Map<String, FamilySpec> parseFamilySpecs(JSONObject object) {
        Map<String, FamilySpec> result = new LinkedHashMap<>();
        if (object == null) {
            return result;
        }
        JSONArray names = object.names();
        if (names == null) {
            return result;
        }
        for (int index = 0; index < names.length(); index += 1) {
            String familyId = names.optString(index, null);
            JSONObject item = familyId == null ? null : object.optJSONObject(familyId);
            FamilySpec spec = FamilySpec.fromJson(familyId, item);
            if (spec != null) {
                result.put(familyId, spec);
            }
        }
        return result;
    }

    private static Map<String, MergeSpec> parseAliasMerges(JSONArray array) {
        Map<String, MergeSpec> result = new LinkedHashMap<>();
        if (array == null) {
            return result;
        }
        for (int index = 0; index < array.length(); index += 1) {
            JSONObject item = array.optJSONObject(index);
            MergeSpec merge = MergeSpec.fromJson(item);
            if (merge == null) {
                continue;
            }
            for (String legacyId : merge.legacyIds) {
                result.put(legacyId, merge);
            }
        }
        return result;
    }

    private static void addSearchAliases(
            JSONArray array,
            Map<String, PresetBuilder> builders,
            Map<String, String> presetIdByLegacyId
    ) {
        if (array == null) {
            return;
        }
        for (int index = 0; index < array.length(); index += 1) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String targetPreset = nullableString(item, "targetPreset");
            String alias = nullableString(item, "alias");
            if (targetPreset == null || alias == null) {
                continue;
            }
            String resolvedPresetId = builders.containsKey(targetPreset)
                    ? targetPreset
                    : presetIdByLegacyId.get(targetPreset);
            if (resolvedPresetId == null) {
                String legacyId = nullableString(item, "legacyId");
                resolvedPresetId = legacyId == null ? null : presetIdByLegacyId.get(legacyId);
            }
            PresetBuilder builder = resolvedPresetId == null ? null : builders.get(resolvedPresetId);
            if (builder != null) {
                builder.addAlias(alias);
            }
        }
    }

    private static List<LoadState> parseLoadStates(JSONArray array) {
        List<LoadState> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (int index = 0; index < array.length(); index += 1) {
            LoadState loadState = LoadState.fromId(array.optString(index, null));
            if (loadState != null && !result.contains(loadState)) {
                result.add(loadState);
            }
        }
        return result;
    }

    private static String nullableString(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return null;
        }
        String value = object.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static List<String> stringList(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int index = 0; index < array.length(); index += 1) {
            String value = array.optString(index, null);
            if (value != null && !value.trim().isEmpty() && !values.contains(value.trim())) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private static Map<String, String> stringMap(JSONObject object) {
        Map<String, String> values = new LinkedHashMap<>();
        if (object == null) {
            return values;
        }
        JSONArray names = object.names();
        if (names == null) {
            return values;
        }
        for (int index = 0; index < names.length(); index += 1) {
            String key = names.optString(index, null);
            String value = nullableString(object, key);
            if (key != null && value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    private static List<LoadState> parseLoadStates(JSONArray array, boolean ignored) {
        return parseLoadStates(array);
    }

    private static <T> List<T> immutableList(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null
                ? Collections.emptyList()
                : values));
    }

    private static final class FamilySpec {
        final String familyId;
        final String nameKo;
        final String nameEn;
        final String defaultUiPart;
        final List<String> allowedVariantDimensions;
        final List<LoadState> allowedLoadStates;

        private FamilySpec(
                String familyId,
                String nameKo,
                String nameEn,
                String defaultUiPart,
                List<String> allowedVariantDimensions,
                List<LoadState> allowedLoadStates
        ) {
            this.familyId = familyId;
            this.nameKo = nameKo;
            this.nameEn = nameEn;
            this.defaultUiPart = defaultUiPart;
            this.allowedVariantDimensions = allowedVariantDimensions;
            this.allowedLoadStates = allowedLoadStates;
        }

        static FamilySpec fromJson(String familyId, JSONObject item) {
            if (familyId == null || item == null) {
                return null;
            }
            return new FamilySpec(
                    familyId,
                    nullableString(item, "nameKo"),
                    nullableString(item, "nameEn"),
                    nullableString(item, "defaultUiPart"),
                    stringList(item.optJSONArray("allowedVariantDimensions")),
                    parseLoadStates(item.optJSONArray("allowedLoadStates"))
            );
        }
    }

    private static final class MergeSpec {
        final String canonicalPresetId;
        final String familyId;
        final String nameKo;
        final List<String> aliases;
        final List<String> legacyIds;

        private MergeSpec(
                String canonicalPresetId,
                String familyId,
                String nameKo,
                List<String> aliases,
                List<String> legacyIds
        ) {
            this.canonicalPresetId = canonicalPresetId;
            this.familyId = familyId;
            this.nameKo = nameKo;
            this.aliases = aliases;
            this.legacyIds = legacyIds;
        }

        static MergeSpec fromJson(JSONObject item) {
            if (item == null) {
                return null;
            }
            String canonicalPresetId = nullableString(item, "canonicalPresetId");
            String familyId = nullableString(item, "familyId");
            if (canonicalPresetId == null || familyId == null) {
                return null;
            }
            return new MergeSpec(
                    canonicalPresetId,
                    familyId,
                    nullableString(item, "nameKo"),
                    stringList(item.optJSONArray("aliases")),
                    stringList(item.optJSONArray("legacyIds"))
            );
        }
    }

    private static final class PresetBuilder {
        final String presetId;
        final String canonicalPresetId;
        final String familyId;
        final String familyNameKo;
        final String familyNameEn;
        final String nameKo;
        final String nameEn;
        final String legacyNameKo;
        final String legacyNameEn;
        final String defaultUiPart;
        final String equipmentVariantId;
        final String recordType;
        final LoadState defaultLoadState;
        final List<LoadState> allowedLoadStates;
        final String canonicalVariantKey;
        final String visualVariantKey;
        final String illustrationKey;
        final List<String> legacyIds = new ArrayList<>();
        final Set<String> aliases = new LinkedHashSet<>();
        final Map<String, String> variant;
        final boolean aliasMerged;
        final boolean approvedNewPreset;

        private PresetBuilder(
                String presetId,
                String canonicalPresetId,
                String familyId,
                String familyNameKo,
                String familyNameEn,
                String nameKo,
                String nameEn,
                String legacyNameKo,
                String legacyNameEn,
                String defaultUiPart,
                String equipmentVariantId,
                String recordType,
                LoadState defaultLoadState,
                List<LoadState> allowedLoadStates,
                String canonicalVariantKey,
                String visualVariantKey,
                String illustrationKey,
                Map<String, String> variant,
                boolean aliasMerged,
                boolean approvedNewPreset
        ) {
            this.presetId = presetId;
            this.canonicalPresetId = canonicalPresetId;
            this.familyId = familyId;
            this.familyNameKo = familyNameKo;
            this.familyNameEn = familyNameEn;
            this.nameKo = nameKo;
            this.nameEn = nameEn;
            this.legacyNameKo = legacyNameKo;
            this.legacyNameEn = legacyNameEn;
            this.defaultUiPart = defaultUiPart;
            this.equipmentVariantId = equipmentVariantId;
            this.recordType = recordType;
            this.defaultLoadState = defaultLoadState;
            this.allowedLoadStates = allowedLoadStates;
            this.canonicalVariantKey = canonicalVariantKey;
            this.visualVariantKey = visualVariantKey;
            this.illustrationKey = illustrationKey;
            this.variant = variant;
            this.aliasMerged = aliasMerged;
            this.approvedNewPreset = approvedNewPreset;
        }

        static PresetBuilder forLegacy(
                String presetId,
                JSONObject item,
                FamilySpec family,
                MergeSpec merge
        ) {
            String canonicalNameKo = nullableString(item, "presetNameKo");
            String canonicalNameEn = nullableString(item, "presetNameEn");
            String legacyNameKo = nullableString(item, "nameKo");
            String legacyNameEn = nullableString(item, "nameEn");
            if (merge != null && merge.nameKo != null) {
                canonicalNameKo = merge.nameKo;
            }
            PresetBuilder builder = new PresetBuilder(
                    presetId,
                    nullableString(item, "canonicalPresetId"),
                    family.familyId,
                    family.nameKo,
                    family.nameEn,
                    canonicalNameKo,
                    canonicalNameEn,
                    legacyNameKo,
                    legacyNameEn,
                    family.defaultUiPart,
                    nullableString(item, "legacyEquipment"),
                    nullableString(item, "legacyRecordType"),
                    LoadState.fromId(nullableString(item, "defaultLoadState")),
                    family.allowedLoadStates,
                    nullableString(item, "canonicalVariantKey"),
                    nullableString(item, "visualVariantKey"),
                    nullableString(item, "illustrationKey"),
                    stringMap(item.optJSONObject("variant")),
                    merge != null,
                    false
            );
            builder.addAlias(legacyNameKo);
            builder.addAlias(legacyNameEn);
            builder.addAlias(canonicalNameKo);
            builder.addAlias(canonicalNameEn);
            builder.addAlias(presetId);
            if (merge != null) {
                for (String alias : merge.aliases) builder.addAlias(alias);
                builder.addAlias(merge.canonicalPresetId);
            }
            return builder;
        }

        static PresetBuilder forApproved(String presetId, JSONObject item, FamilySpec family) {
            Map<String, String> variant = stringMap(item.optJSONObject("variant"));
            String nameKo = nullableString(item, "nameKo");
            String nameEn = nullableString(item, "nameEn");
            PresetBuilder builder = new PresetBuilder(
                    presetId,
                    presetId,
                    family.familyId,
                    family.nameKo,
                    family.nameEn,
                    nameKo,
                    nameEn,
                    null,
                    null,
                    family.defaultUiPart,
                    variant.get("equipment"),
                    nullableString(item, "recordType"),
                    LoadState.fromId(nullableString(item, "defaultLoadState")),
                    family.allowedLoadStates,
                    nullableString(item, "canonicalVariantKey"),
                    nullableString(item, "visualVariantKey"),
                    nullableString(item, "illustrationKey"),
                    variant,
                    false,
                    true
            );
            builder.addAlias(nameKo);
            builder.addAlias(nameEn);
            builder.addAlias(presetId);
            for (String alias : stringList(item.optJSONArray("aliases"))) builder.addAlias(alias);
            return builder;
        }

        void addLegacy(String legacyId) {
            if (legacyId != null && !legacyIds.contains(legacyId)) {
                legacyIds.add(legacyId);
            }
        }

        void addAlias(String alias) {
            if (alias != null && !alias.trim().isEmpty()) {
                aliases.add(alias.trim());
            }
        }

        RuntimeExercisePreset build() {
            if (presetId == null || familyId == null) {
                return null;
            }
            String storageExerciseId = legacyIds.isEmpty() ? presetId : legacyIds.get(0);
            return new RuntimeExercisePreset(
                    presetId,
                    canonicalPresetId == null ? presetId : canonicalPresetId,
                    storageExerciseId,
                    familyId,
                    familyNameKo,
                    familyNameEn,
                    nameKo,
                    nameEn,
                    legacyNameKo,
                    legacyNameEn,
                    defaultUiPart,
                    equipmentVariantId,
                    recordType,
                    defaultLoadState,
                    allowedLoadStates,
                    canonicalVariantKey,
                    visualVariantKey,
                    illustrationKey,
                    legacyIds,
                    new ArrayList<>(aliases),
                    variant,
                    aliasMerged,
                    approvedNewPreset
            );
        }
    }
}
