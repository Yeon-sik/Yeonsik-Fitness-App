package com.yeonsik.fitnessapp.exercise;

import android.content.Context;

/**
 * Family-aware illustration lookup with legacy exerciseId compatibility.
 *
 * <p>The normative lookup order is exact visual variant, family default, then placeholder.
 * Existing exerciseId lookup is retained as an explicit compatibility projection for legacy
 * callers. No image is generated here.</p>
 */
public final class ExerciseIllustrationLookup {
    private static volatile ExerciseFamilyCatalog cachedFamilyCatalog;

    private ExerciseIllustrationLookup() {
    }

    public static int listPreviewDrawableFor(Context context, String legacyExerciseId) {
        return resolve(context, legacyExerciseId).listDrawable();
    }

    public static int[] detailDrawablesFor(Context context, String legacyExerciseId) {
        return resolve(context, legacyExerciseId).drawables;
    }

    public static int[] frameDurationsMsFor(Context context, String legacyExerciseId) {
        return resolve(context, legacyExerciseId).durationsMs;
    }

    public static int preferredHeightDp(Context context, String legacyExerciseId) {
        IllustrationResolution resolution = resolve(context, legacyExerciseId);
        return resolution.preferredHeightDp;
    }

    public static IllustrationResolution resolve(Context context, String storageExerciseId) {
        ExerciseFamilyCatalog catalog = familyCatalog(context);
        ExerciseFamilyIdentity identity = catalog.identityForStorageExerciseId(storageExerciseId);
        if (identity == null) {
            return legacyResolution(storageExerciseId, "legacy_exercise_id_compatibility");
        }

        return resolve(context, identity);
    }

    /** Long-term lookup entry point: family + visual variant identity, not a legacy ID. */
    public static IllustrationResolution resolve(
            Context context,
            ExerciseFamilyIdentity identity
    ) {
        if (identity == null) {
            return IllustrationResolution.placeholder(null);
        }
        ExerciseFamilyCatalog catalog = familyCatalog(context);

        ExerciseFamilyCatalog.ImageAssetRef exact = catalog.imageVariantFor(identity);
        IllustrationResolution exactResolution = refResolution(exact, "exact_visual_variant");
        if (exactResolution != null) {
            return exactResolution;
        }

        ExerciseFamilyCatalog.ImageAssetRef familyDefault = catalog.familyDefaultFor(identity.familyId);
        IllustrationResolution defaultResolution = refResolution(familyDefault, "family_default");
        if (defaultResolution != null) {
            return defaultResolution;
        }

        return IllustrationResolution.placeholder(identity.legacyExerciseId);
    }

    /** Lookup without requiring callers to construct a legacy exercise object. */
    public static IllustrationResolution resolve(
            Context context,
            String familyId,
            String visualVariantKey
    ) {
        ExerciseFamilyCatalog catalog = familyCatalog(context);
        IllustrationResolution exact = refResolution(
                catalog.imageVariantFor(familyId, visualVariantKey),
                "exact_visual_variant"
        );
        if (exact != null) {
            return exact;
        }
        IllustrationResolution familyDefault = refResolution(
                catalog.familyDefaultFor(familyId),
                "family_default"
        );
        return familyDefault == null
                ? IllustrationResolution.placeholder(null)
                : familyDefault;
    }

    private static IllustrationResolution refResolution(
            ExerciseFamilyCatalog.ImageAssetRef ref,
            String source
    ) {
        if (ref == null) {
            return null;
        }
        String lookupKey = ref.illustrationKey;
        if (lookupKey == null || lookupKey.trim().isEmpty()) {
            lookupKey = ref.legacyExerciseId;
        }
        if (lookupKey == null || lookupKey.trim().isEmpty()) {
            return null;
        }
        int[] drawables = ExerciseIllustrationCatalog.detailDrawablesFor(lookupKey);
        if (drawables.length == 0) {
            return null;
        }
        return new IllustrationResolution(
                source,
                ref.illustrationKey,
                drawables,
                ExerciseIllustrationCatalog.frameDurationsMsFor(lookupKey),
                ExerciseIllustrationCatalog.preferredHeightDp(lookupKey),
                null
        );
    }

    private static IllustrationResolution legacyResolution(String legacyExerciseId, String source) {
        int[] drawables = ExerciseIllustrationCatalog.detailDrawablesFor(legacyExerciseId);
        if (drawables.length == 0) {
            return IllustrationResolution.placeholder(legacyExerciseId);
        }
        return new IllustrationResolution(
                source,
                null,
                drawables,
                ExerciseIllustrationCatalog.frameDurationsMsFor(legacyExerciseId),
                ExerciseIllustrationCatalog.preferredHeightDp(legacyExerciseId),
                legacyExerciseId
        );
    }

    private static ExerciseFamilyCatalog familyCatalog(Context context) {
        ExerciseFamilyCatalog catalog = cachedFamilyCatalog;
        if (catalog == null) {
            synchronized (ExerciseIllustrationLookup.class) {
                catalog = cachedFamilyCatalog;
                if (catalog == null) {
                    catalog = ExerciseFamilyCatalog.load(context);
                    cachedFamilyCatalog = catalog;
                }
            }
        }
        return catalog;
    }

    public static final class IllustrationResolution {
        public final String source;
        public final String illustrationKey;
        public final int[] drawables;
        public final int[] durationsMs;
        public final int preferredHeightDp;
        public final String legacyExerciseId;

        private IllustrationResolution(
                String source,
                String illustrationKey,
                int[] drawables,
                int[] durationsMs,
                int preferredHeightDp,
                String legacyExerciseId
        ) {
            this.source = source;
            this.illustrationKey = illustrationKey;
            this.drawables = drawables;
            this.durationsMs = durationsMs;
            this.preferredHeightDp = preferredHeightDp;
            this.legacyExerciseId = legacyExerciseId;
        }

        private static IllustrationResolution placeholder(String legacyExerciseId) {
            return new IllustrationResolution("placeholder", "placeholder", new int[0], new int[0], 280, legacyExerciseId);
        }

        public boolean isPlaceholder() {
            return drawables.length == 0;
        }

        private int listDrawable() {
            return drawables.length == 0 ? 0 : drawables[0];
        }
    }
}
