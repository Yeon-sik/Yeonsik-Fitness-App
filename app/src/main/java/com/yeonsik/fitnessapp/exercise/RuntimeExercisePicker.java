package com.yeonsik.fitnessapp.exercise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Family-first search projection for the runtime exercise picker. */
public final class RuntimeExercisePicker {
    private final RuntimeExerciseCatalog catalog;

    public RuntimeExercisePicker(RuntimeExerciseCatalog catalog) {
        this.catalog = catalog == null ? RuntimeExerciseCatalog.empty() : catalog;
    }

    public List<FamilyResult> search(String query) {
        String normalizedQuery = normalize(query);
        List<FamilyResult> results = new ArrayList<>();
        for (RuntimeExerciseFamily family : catalog.families) {
            List<PresetResult> matchingPresets = new ArrayList<>();
            for (RuntimeExercisePreset preset : family.presets) {
                int score = presetScore(preset, normalizedQuery);
                if (normalizedQuery.isEmpty() || score > 0) {
                    matchingPresets.add(new PresetResult(preset, score));
                }
            }

            int familyScore = familyScore(family, normalizedQuery);
            if (!normalizedQuery.isEmpty() && familyScore > 0 && matchingPresets.isEmpty()) {
                for (RuntimeExercisePreset preset : family.presets) {
                    matchingPresets.add(new PresetResult(preset, familyScore));
                }
            }
            if (matchingPresets.isEmpty()) {
                continue;
            }

            matchingPresets.sort((left, right) -> {
                int byScore = Integer.compare(right.score, left.score);
                return byScore == 0
                        ? displayName(left.preset).compareToIgnoreCase(displayName(right.preset))
                        : byScore;
            });
            int resultScore = matchingPresets.get(0).score;
            results.add(new FamilyResult(
                    family,
                    toPresets(matchingPresets),
                    resultScore
            ));
        }

        results.sort((left, right) -> {
            int byScore = Integer.compare(right.matchScore, left.matchScore);
            return byScore == 0
                    ? displayName(left.family).compareToIgnoreCase(displayName(right.family))
                    : byScore;
        });
        return Collections.unmodifiableList(results);
    }

    public List<RuntimeExercisePreset> presetsForFamily(String familyId, String query) {
        for (FamilyResult result : search(query)) {
            if (result.family.familyId.equals(familyId)) {
                return result.presets;
            }
        }
        return Collections.emptyList();
    }

    /** A one-preset family can be selected without opening a variant dialog. */
    public RuntimeExercisePreset directPresetForFamily(String familyId, String query) {
        List<RuntimeExercisePreset> presets = presetsForFamily(familyId, query);
        return presets.size() == 1 ? presets.get(0) : null;
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static String compactKey(String value) {
        return normalize(value).replace(" ", "").replace("-", "").replace("_", "");
    }

    private int presetScore(RuntimeExercisePreset preset, String query) {
        if (query.isEmpty()) {
            return 0;
        }
        Set<String> names = new LinkedHashSet<>();
        names.add(preset.nameKo);
        names.add(preset.nameEn);
        names.addAll(preset.searchAliases);
        int best = 0;
        for (String name : names) {
            String normalizedName = normalize(name);
            if (normalizedName.isEmpty()) {
                continue;
            }
            String compactName = compactKey(normalizedName);
            String compactQuery = compactKey(query);
            if (normalizedName.equals(query) || compactName.equals(compactQuery)) {
                best = Math.max(best, 4);
            } else if (normalizedName.startsWith(query) || compactName.startsWith(compactQuery)) {
                best = Math.max(best, 3);
            } else if (normalizedName.contains(query) || compactName.contains(compactQuery)) {
                best = Math.max(best, 2);
            }
        }
        return best;
    }

    private int familyScore(RuntimeExerciseFamily family, String query) {
        if (query.isEmpty()) {
            return 0;
        }
        String nameKo = normalize(family.nameKo);
        String nameEn = normalize(family.nameEn);
        String compactQuery = compactKey(query);
        if (nameKo.equals(query) || nameEn.equals(query)
                || compactKey(nameKo).equals(compactQuery)
                || compactKey(nameEn).equals(compactQuery)) {
            return 1;
        }
        if (nameKo.startsWith(query) || nameEn.startsWith(query)
                || compactKey(nameKo).startsWith(compactQuery)
                || compactKey(nameEn).startsWith(compactQuery)) {
            return 1;
        }
        return nameKo.contains(query) || nameEn.contains(query)
                || compactKey(nameKo).contains(compactQuery)
                || compactKey(nameEn).contains(compactQuery) ? 1 : 0;
    }

    private static List<RuntimeExercisePreset> toPresets(List<PresetResult> results) {
        List<RuntimeExercisePreset> presets = new ArrayList<>();
        for (PresetResult result : results) {
            if (!presets.contains(result.preset)) {
                presets.add(result.preset);
            }
        }
        return Collections.unmodifiableList(presets);
    }

    private static String displayName(RuntimeExerciseFamily family) {
        String name = family.displayName();
        return name == null ? "" : name;
    }

    private static String displayName(RuntimeExercisePreset preset) {
        String name = preset.displayName();
        return name == null ? "" : name;
    }

    private static final class PresetResult {
        final RuntimeExercisePreset preset;
        final int score;

        PresetResult(RuntimeExercisePreset preset, int score) {
            this.preset = preset;
            this.score = score;
        }
    }

    public static final class FamilyResult {
        public final RuntimeExerciseFamily family;
        public final List<RuntimeExercisePreset> presets;
        public final int matchScore;

        private FamilyResult(
                RuntimeExerciseFamily family,
                List<RuntimeExercisePreset> presets,
                int matchScore
        ) {
            this.family = family;
            this.presets = presets;
            this.matchScore = matchScore;
        }

        public boolean hasSinglePreset() {
            return presets.size() == 1;
        }
    }
}
