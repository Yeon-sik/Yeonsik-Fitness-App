package com.yeonsik.fitnessapp.ui;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class ExerciseMuscleModelAssetTest {
    private static final String ROOT = "exercise_muscle/";

    @Test
    public void generatedAssetsContainEveryManifestLayerAndBaseModel() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assertAsset(context, ROOT + "source/front-master.png");
        assertAsset(context, ROOT + "source/back-master.png");

        JSONObject document = new JSONObject(readAsset(
                context,
                ROOT + "muscle-layers.json"
        ));
        Map<String, String> layerViews = new HashMap<>();
        JSONArray layers = document.getJSONArray("layers");
        for (int index = 0; index < layers.length(); index += 1) {
            JSONObject layer = layers.getJSONObject(index);
            layerViews.put(layer.getString("id"), layer.getString("view"));
        }

        JSONObject groups = document.getJSONObject("exerciseGroups");
        Iterator<String> groupKeys = groups.keys();
        while (groupKeys.hasNext()) {
            JSONArray groupLayers = groups.getJSONArray(groupKeys.next());
            for (int index = 0; index < groupLayers.length(); index += 1) {
                String layerId = groupLayers.getString(index);
                String view = layerViews.get(layerId);
                assertNotNull(layerId, view);
                assertTrue("front".equals(view) || "back".equals(view));
                assertAsset(context, ROOT + "layers/" + view + "/" + layerId + ".png");
            }
        }
    }

    private static void assertAsset(Context context, String path) throws Exception {
        try (InputStream ignored = context.getAssets().open(path)) {
        }
    }

    private static String readAsset(Context context, String path) throws Exception {
        try (InputStream stream = context.getAssets().open(path)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }
}
