package com.yeonsik.fitnessapp.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Renders the existing style-4 front/back model and its generated muscle layers.
 *
 * <p>The renderer only accepts exact primarySubPart keys from the runtime catalog. It does not
 * infer a muscle from a name or a body-part label.</p>
 */
public final class ExerciseMuscleModelRenderer {
    private static final String ASSET_ROOT = "exercise_muscle/";
    private static final String MAPPING_ASSET = ASSET_ROOT + "muscle-layers.json";
    // 앞·뒤 모델 표시 영역을 기존 190dp에서 1.5배로 확대한다.
    private static final int MODEL_HEIGHT_DP = 285;
    private static final int DECODE_SAMPLE_SIZE = 2;
    private static final int BITMAP_CACHE_KB = 12 * 1024;
    private static final int COMPOSITE_CACHE_KB = 12 * 1024;
    // Analysis uses a separate red scale; picker selection always uses the Pastel Blue token.
    private static final int ANALYSIS_MUSCLE_RED = Color.rgb(239, 68, 68);
    private static final double MIN_LAYER_ALPHA = 0.18d;

    private final Activity activity;
    private final FitnessUi ui;
    private final android.util.LruCache<String, Bitmap> bitmapCache =
            new android.util.LruCache<String, Bitmap>(BITMAP_CACHE_KB) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getByteCount() / 1024);
                }
            };
    private final android.util.LruCache<String, Bitmap> compositeCache =
            new android.util.LruCache<String, Bitmap>(COMPOSITE_CACHE_KB) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return Math.max(1, value.getByteCount() / 1024);
                }
            };
    private MuscleLayerSpec spec;

    public ExerciseMuscleModelRenderer(Activity activity, FitnessUi ui) {
        this.activity = activity;
        this.ui = ui;
    }

    /** Builds a two-column front/back model for the selected primarySubPart keys. */
    public LinearLayout render(Iterable<String> primarySubParts) {
        Set<String> selected = new HashSet<>();
        if (primarySubParts != null) {
            for (String primarySubPart : primarySubParts) {
                if (primarySubPart != null && !primarySubPart.trim().isEmpty()) {
                    selected.add(primarySubPart.trim());
                }
            }
        }

        return renderContainer(selected, null);
    }

    /**
     * Builds the same front/back model using summed effective sets per anatomical layer ID.
     * Layer IDs come from the existing exercise_muscle asset mapping.
     */
    public LinearLayout renderScores(Map<String, Double> effectiveSetsByLayer) {
        Map<String, Double> scores = new LinkedHashMap<>();
        if (effectiveSetsByLayer != null) {
            for (Map.Entry<String, Double> entry : effectiveSetsByLayer.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                Double value = entry.getValue();
                if (!key.isEmpty() && value != null && Double.isFinite(value) && value > 0d) {
                    scores.put(key, value);
                }
            }
        }
        return renderContainer(null, scores);
    }

    /** Returns the existing asset's muscle-group-to-anatomical-layer mapping for pure analytics. */
    public Map<String, List<String>> anatomicalLayerIdsByMuscleGroup() {
        return spec().exerciseGroups;
    }

    private LinearLayout renderContainer(
            Set<String> selected,
            Map<String, Double> effectiveSetsByLayer
    ) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(android.view.Gravity.CENTER);
        container.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6));
        container.setBackground(ui.flatSurfaceDrawable(ui.dp(16)));
        ui.applyDepth(container, FitnessUi.DEPTH_EMPHASIS_DP);

        container.addView(modelColumn("앞", "front", selected, effectiveSetsByLayer),
                new LinearLayout.LayoutParams(0, ui.dp(MODEL_HEIGHT_DP), 1f));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                0,
                ui.dp(MODEL_HEIGHT_DP),
                1f
        );
        backParams.setMargins(ui.dp(6), 0, 0, 0);
        container.addView(modelColumn("뒤", "back", selected, effectiveSetsByLayer), backParams);
        return container;
    }

    private LinearLayout modelColumn(
            String label,
            String side,
            Set<String> selected,
            Map<String, Double> effectiveSetsByLayer
    ) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        android.widget.TextView labelView = ui.caption(label, FitnessUi.COLOR_MUTED);
        labelView.setGravity(android.view.Gravity.CENTER);
        column.addView(labelView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ui.dp(22)
        ));

        boolean scoreMode = effectiveSetsByLayer != null;
        Bitmap model = scoreMode
                ? compositeScores(side, effectiveSetsByLayer)
                : composite(side, selected);
        if (model != null) {
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(true);
            image.setImageBitmap(model);
            image.setContentDescription(scoreMode
                    ? label + " 근육 자극 분포 모델"
                    : label + " 인체 근육 모델");
            image.setFocusable(false);
            column.addView(image, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ui.dp(MODEL_HEIGHT_DP - 22)
            ));
        }
        return column;
    }

    private Bitmap composite(String side, Set<String> selected) {
        MuscleLayerSpec currentSpec = spec();
        List<String> layerIds = currentSpec.layerIdsFor(side, selected);
        int selectionColor = ui.pastelBlue();
        String cacheKey = side + "|selection|" + selectionColor + "|" + join(layerIds);
        Bitmap cached = compositeCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        Bitmap base = bitmap(assetPath("source/" + side + "-master.png"));
        if (base == null) {
            return null;
        }
        if (layerIds.isEmpty()) {
            compositeCache.put(cacheKey, base);
            return base;
        }

        Bitmap result;
        try {
            result = Bitmap.createBitmap(
                    base.getWidth(),
                    base.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
        } catch (IllegalArgumentException error) {
            return base;
        }
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG);
        Rect target = new Rect(0, 0, base.getWidth(), base.getHeight());
        canvas.drawBitmap(base, null, target, paint);
        // The generated PNGs are alpha contours. Replace only their RGB color and preserve the
        // contour so picker selection has the same meaning in light and dark themes.
        paint.setColorFilter(new PorterDuffColorFilter(selectionColor, PorterDuff.Mode.SRC_IN));
        for (String layerId : layerIds) {
            Bitmap layer = bitmap(assetPath("layers/" + side + "/" + layerId + ".png"));
            if (layer != null) {
                canvas.drawBitmap(layer, null, target, paint);
            }
        }
        paint.setColorFilter(null);
        compositeCache.put(cacheKey, result);
        return result;
    }

    private Bitmap compositeScores(String side, Map<String, Double> effectiveSetsByLayer) {
        MuscleLayerSpec currentSpec = spec();
        Map<String, Double> layerScores = new LinkedHashMap<>();
        if (effectiveSetsByLayer != null) {
            for (Map.Entry<String, Double> entry : effectiveSetsByLayer.entrySet()) {
                String layerId = entry.getKey() == null ? "" : entry.getKey().trim();
                Double score = entry.getValue();
                if (!layerId.isEmpty() && score != null && Double.isFinite(score)
                        && score > 0d && side.equals(currentSpec.layerViews.get(layerId))) {
                    layerScores.put(layerId, score);
                }
            }
        }
        String cacheKey = side + "|scores|" + joinScores(layerScores);
        Bitmap cached = compositeCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        Bitmap base = bitmap(assetPath("source/" + side + "-master.png"));
        if (base == null) {
            return null;
        }
        if (layerScores.isEmpty()) {
            compositeCache.put(cacheKey, base);
            return base;
        }

        Bitmap result;
        try {
            result = Bitmap.createBitmap(
                    base.getWidth(),
                    base.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
        } catch (IllegalArgumentException error) {
            return base;
        }
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG
                | Paint.DITHER_FLAG);
        Rect target = new Rect(0, 0, base.getWidth(), base.getHeight());
        canvas.drawBitmap(base, null, target, paint);
        for (Map.Entry<String, Double> entry : layerScores.entrySet()) {
            Bitmap layer = bitmap(assetPath("layers/" + side + "/" + entry.getKey() + ".png"));
            if (layer == null) {
                continue;
            }
            double intensity = intensityForEffectiveSets(entry.getValue());
            int color = lerpColor(Color.WHITE, ANALYSIS_MUSCLE_RED, intensity);
            int alpha = (int) Math.round(255d * (MIN_LAYER_ALPHA
                    + (1d - MIN_LAYER_ALPHA) * intensity));
            // Existing red PNGs are alpha masks. SRC_IN replaces their RGB while preserving
            // the generated alpha contour, so no white asset variant is needed.
            paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            paint.setAlpha(alpha);
            canvas.drawBitmap(layer, null, target, paint);
        }
        paint.setColorFilter(null);
        paint.setAlpha(255);
        compositeCache.put(cacheKey, result);
        return result;
    }

    /** Absolute effective-set saturation: equal sessions remain comparable across days. */
    static double intensityForEffectiveSets(double effectiveSets) {
        return WorkoutSummaryAnalytics.intensityForEffectiveSets(effectiveSets);
    }

    private static int lerpColor(int from, int to, double amount) {
        double clamped = Math.max(0d, Math.min(1d, amount));
        int red = (int) Math.round(Color.red(from)
                + (Color.red(to) - Color.red(from)) * clamped);
        int green = (int) Math.round(Color.green(from)
                + (Color.green(to) - Color.green(from)) * clamped);
        int blue = (int) Math.round(Color.blue(from)
                + (Color.blue(to) - Color.blue(from)) * clamped);
        return Color.rgb(red, green, blue);
    }

    private Bitmap bitmap(String path) {
        Bitmap cached = bitmapCache.get(path);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        options.inSampleSize = DECODE_SAMPLE_SIZE;
        try (InputStream stream = activity.getAssets().open(path)) {
            Bitmap decoded = BitmapFactory.decodeStream(stream, null, options);
            if (decoded != null) {
                bitmapCache.put(path, decoded);
            }
            return decoded;
        } catch (Exception ignored) {
            return null;
        }
    }

    private MuscleLayerSpec spec() {
        MuscleLayerSpec current = spec;
        if (current == null) {
            synchronized (this) {
                current = spec;
                if (current == null) {
                    current = MuscleLayerSpec.load(activity);
                    spec = current;
                }
            }
        }
        return current;
    }

    private String assetPath(String relativePath) {
        return ASSET_ROOT + relativePath;
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(value);
        }
        return result.toString();
    }

    private static String joinScores(Map<String, Double> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(entry.getKey()).append('=')
                    .append(Math.round(entry.getValue() * 1000d) / 1000d);
        }
        return result.toString();
    }

    static final class MuscleLayerSpec {
        private static final MuscleLayerSpec EMPTY = new MuscleLayerSpec(
                Collections.emptyMap(),
                Collections.emptyMap()
        );

        private final Map<String, List<String>> exerciseGroups;
        private final Map<String, String> layerViews;

        private MuscleLayerSpec(
                Map<String, List<String>> exerciseGroups,
                Map<String, String> layerViews
        ) {
            this.exerciseGroups = exerciseGroups;
            this.layerViews = layerViews;
        }

        static MuscleLayerSpec load(Activity activity) {
            try (InputStream stream = activity.getAssets().open(MAPPING_ASSET)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                byte[] bytes = output.toByteArray();
                if (bytes.length == 0) {
                    return EMPTY;
                }
                JSONObject document = new JSONObject(
                        new String(bytes, StandardCharsets.UTF_8)
                );
                Map<String, String> layerViews = new HashMap<>();
                JSONArray layers = document.optJSONArray("layers");
                if (layers != null) {
                    for (int index = 0; index < layers.length(); index += 1) {
                        JSONObject layer = layers.optJSONObject(index);
                        if (layer == null) {
                            continue;
                        }
                        String id = layer.optString("id", "").trim();
                        String view = layer.optString("view", "").trim();
                        if (!id.isEmpty() && ("front".equals(view) || "back".equals(view))) {
                            layerViews.put(id, view);
                        }
                    }
                }

                Map<String, List<String>> exerciseGroups = new HashMap<>();
                JSONObject groups = document.optJSONObject("exerciseGroups");
                if (groups != null) {
                    Iterator<String> keys = groups.keys();
                    while (keys.hasNext()) {
                        String groupId = keys.next();
                        JSONArray groupLayers = groups.optJSONArray(groupId);
                        if (groupLayers == null) {
                            continue;
                        }
                        List<String> ids = new ArrayList<>();
                        for (int index = 0; index < groupLayers.length(); index += 1) {
                            String layerId = groupLayers.optString(index, "").trim();
                            if (!layerId.isEmpty() && layerViews.containsKey(layerId)) {
                                ids.add(layerId);
                            }
                        }
                        exerciseGroups.put(groupId, Collections.unmodifiableList(ids));
                    }
                }
                return new MuscleLayerSpec(
                        Collections.unmodifiableMap(exerciseGroups),
                        Collections.unmodifiableMap(layerViews)
                );
            } catch (Exception ignored) {
                return EMPTY;
            }
        }

        List<String> layerIdsFor(String side, Set<String> primarySubParts) {
            Set<String> ids = new TreeSet<>();
            for (String primarySubPart : primarySubParts) {
                List<String> groupLayers = exerciseGroups.get(primarySubPart);
                if (groupLayers == null) {
                    continue;
                }
                for (String layerId : groupLayers) {
                    if (side.equals(layerViews.get(layerId))) {
                        ids.add(layerId);
                    }
                }
            }
            return new ArrayList<>(ids);
        }

    }
}
