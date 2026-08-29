package com.yeonsik.fitnessapp.exercise;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runtime reader for the generated JSON projection of EXERCISE_FAMILY_CATALOG_V1.yaml. */
public final class ExerciseFamilyCatalog {
    public static final String ASSET_FILE_NAME = "exercise_family_mapping_v1.json";

    private static final ExerciseFamilyCatalog EMPTY = new ExerciseFamilyCatalog(
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap()
    );

    private final Map<String, ExerciseFamilyIdentity> identitiesByLegacyId;
    private final Map<String, ImageAssetRef> imageVariantsByKey;
    private final Map<String, ImageAssetRef> familyDefaultsByFamilyId;

    private ExerciseFamilyCatalog(
            Map<String, ExerciseFamilyIdentity> identitiesByLegacyId,
            Map<String, ImageAssetRef> imageVariantsByKey,
            Map<String, ImageAssetRef> familyDefaultsByFamilyId
    ) {
        this.identitiesByLegacyId = Collections.unmodifiableMap(new LinkedHashMap<>(identitiesByLegacyId));
        this.imageVariantsByKey = Collections.unmodifiableMap(new LinkedHashMap<>(imageVariantsByKey));
        this.familyDefaultsByFamilyId = Collections.unmodifiableMap(new LinkedHashMap<>(familyDefaultsByFamilyId));
    }

    public static ExerciseFamilyCatalog empty() {
        return EMPTY;
    }

    public static ExerciseFamilyCatalog load(Context context) {
        if (context == null) {
            return EMPTY;
        }
        Context applicationContext = context.getApplicationContext() == null
                ? context
                : context.getApplicationContext();
        try (InputStream inputStream = applicationContext.getAssets().open(ASSET_FILE_NAME);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return fromJson(new JSONObject(outputStream.toString(StandardCharsets.UTF_8.name())));
        } catch (Exception ignored) {
            // Legacy exercise IDs remain usable when the generated projection is unavailable.
            // No family is inferred here.
            return EMPTY;
        }
    }

    public static ExerciseFamilyCatalog fromJson(JSONObject document) {
        if (document == null || document.optInt("schemaVersion", 0) != 1) {
            return EMPTY;
        }
        Map<String, ExerciseFamilyIdentity> identities = new LinkedHashMap<>();
        JSONArray exercises = document.optJSONArray("legacyExercises");
        if (exercises != null) {
            for (int index = 0; index < exercises.length(); index += 1) {
                JSONObject item = exercises.optJSONObject(index);
                if (item == null || !"mapped".equals(item.optString("status"))) {
                    continue;
                }
                String legacyId = nullableString(item, "legacyExerciseId");
                if (legacyId == null) {
                    continue;
                }
                identities.put(legacyId, new ExerciseFamilyIdentity(
                        legacyId,
                        nullableString(item, "familyId"),
                        nullableString(item, "presetId"),
                        nullableString(item, "canonicalPresetId"),
                        nullableString(item, "presetNameKo"),
                        nullableString(item, "presetNameEn"),
                        nullableString(item, "nameKo"),
                        nullableString(item, "nameEn"),
                        nullableString(item, "defaultUiPart"),
                        nullableString(item, "canonicalVariantKey"),
                        nullableString(item, "visualVariantKey"),
                        nullableString(item, "illustrationKey"),
                        nullableString(item, "defaultLoadState"),
                        nullableString(item, "legacyRecordType"),
                        item.optJSONObject("variant") == null ? null : item.optJSONObject("variant").toString()
                ));
            }
        }

        Map<String, ImageAssetRef> imageVariants = new LinkedHashMap<>();
        Map<String, ImageAssetRef> familyDefaults = new LinkedHashMap<>();
        JSONObject imageIdentity = document.optJSONObject("imageIdentity");
        if (imageIdentity != null) {
            JSONArray variants = imageIdentity.optJSONArray("imageVariants");
            if (variants != null) {
                for (int index = 0; index < variants.length(); index += 1) {
                    JSONObject item = variants.optJSONObject(index);
                    ImageAssetRef ref = ImageAssetRef.fromJson(item);
                    if (ref != null && ref.familyId != null && ref.visualVariantKey != null) {
                        imageVariants.put(ref.familyId + "\n" + ref.visualVariantKey, ref);
                    }
                }
            }
            JSONArray defaults = imageIdentity.optJSONArray("familyDefaults");
            if (defaults != null) {
                for (int index = 0; index < defaults.length(); index += 1) {
                    JSONObject item = defaults.optJSONObject(index);
                    ImageAssetRef ref = ImageAssetRef.fromJson(item);
                    if (ref != null && ref.familyId != null) {
                        familyDefaults.put(ref.familyId, ref);
                    }
                }
            }
        }
        return new ExerciseFamilyCatalog(identities, imageVariants, familyDefaults);
    }

    public ExerciseFamilyIdentity identityForLegacyId(String legacyExerciseId) {
        if (legacyExerciseId == null) {
            return null;
        }
        return identitiesByLegacyId.get(legacyExerciseId.trim());
    }

    public List<String> legacyIdsFor(ExerciseFamilyIdentity identity, LoadState loadState) {
        // LoadState belongs to each set, not to the legacy exercise identity. Keep the old
        // signature for callers that already pass it, but never use a default state to exclude
        // an otherwise comparable legacy alias.
        return legacyIdsForVariant(identity);
    }

    public List<String> legacyIdsForVariant(ExerciseFamilyIdentity identity) {
        if (identity == null || identity.familyId == null || identity.canonicalVariantKey == null) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (ExerciseFamilyIdentity candidate : identitiesByLegacyId.values()) {
            if (identity.familyId.equals(candidate.familyId)
                    && identity.canonicalVariantKey.equals(candidate.canonicalVariantKey)) {
                ids.add(candidate.legacyExerciseId);
            }
        }
        return Collections.unmodifiableList(ids);
    }

    public ImageAssetRef imageVariantFor(ExerciseFamilyIdentity identity) {
        return identity == null
                ? null
                : imageVariantFor(identity.familyId, identity.visualVariantKey);
    }

    public ImageAssetRef imageVariantFor(String familyId, String visualVariantKey) {
        if (familyId == null || visualVariantKey == null) {
            return null;
        }
        return imageVariantsByKey.get(familyId + "\n" + visualVariantKey);
    }

    public ImageAssetRef familyDefaultFor(String familyId) {
        return familyId == null ? null : familyDefaultsByFamilyId.get(familyId);
    }

    public int size() {
        return identitiesByLegacyId.size();
    }

    private static String nullableString(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return null;
        }
        String value = object.optString(key, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public static final class ImageAssetRef {
        public final String familyId;
        public final String visualVariantKey;
        public final String illustrationKey;
        public final String legacyExerciseId;
        public final String sceneFile;
        public final String frameFileA;
        public final String frameFileB;
        public final Map<String, String> equipmentViews;

        private ImageAssetRef(
                String familyId,
                String visualVariantKey,
                String illustrationKey,
                String legacyExerciseId,
                String sceneFile,
                String frameFileA,
                String frameFileB,
                Map<String, String> equipmentViews
        ) {
            this.familyId = familyId;
            this.visualVariantKey = visualVariantKey;
            this.illustrationKey = illustrationKey;
            this.legacyExerciseId = legacyExerciseId;
            this.sceneFile = sceneFile;
            this.frameFileA = frameFileA;
            this.frameFileB = frameFileB;
            this.equipmentViews = Collections.unmodifiableMap(new LinkedHashMap<>(equipmentViews));
        }

        private static ImageAssetRef fromJson(JSONObject item) {
            if (item == null) {
                return null;
            }
            Map<String, String> equipmentViews = new LinkedHashMap<>();
            JSONObject equipmentViewsObject = item.optJSONObject("equipmentViews");
            if (equipmentViewsObject != null) {
                JSONArray names = equipmentViewsObject.names();
                if (names != null) {
                    for (int index = 0; index < names.length(); index += 1) {
                        String key = names.optString(index, null);
                        String value = nullableString(equipmentViewsObject, key);
                        if (key != null && value != null) {
                            equipmentViews.put(key, value);
                        }
                    }
                }
            }
            JSONObject frameFiles = item.optJSONObject("frameFiles");
            return new ImageAssetRef(
                    nullableString(item, "familyId"),
                    nullableString(item, "visualVariantKey"),
                    nullableString(item, "illustrationKey"),
                    nullableString(item, "legacyExerciseId"),
                    nullableString(item, "sceneFile"),
                    nullableString(frameFiles, "A"),
                    nullableString(frameFiles, "B"),
                    equipmentViews
            );
        }
    }
}
