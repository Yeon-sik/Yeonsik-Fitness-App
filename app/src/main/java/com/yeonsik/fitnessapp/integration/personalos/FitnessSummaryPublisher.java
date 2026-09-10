package com.yeonsik.fitnessapp.integration.personalos;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.FitnessSummaryProjectionV2;
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutSummaryApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Publishes the Personal OS Summary Projection v2 contract, including tombstones. */
public final class FitnessSummaryPublisher {
    private static final String SUMMARY_PROJECTION_V2_RPC =
            "/rest/v1/rpc/upsert_fitness_summary_projection_v2";

    public int publish(SupabaseConfig config, WorkoutSummaryApi summaryStore) throws Exception {
        if (config == null || !config.isConfigured()) {
            throw new IllegalStateException("Supabase 설정이 비어 있습니다.");
        }
        if (summaryStore == null) {
            throw new IllegalArgumentException("Summary store is required.");
        }

        int published = 0;
        for (FitnessSummaryProjectionV2 projection
                : summaryStore.completedFitnessSummaryProjectionsV2(config.effectiveUserId())) {
            String endpoint = joinUrl(config.supabaseUrl, SUMMARY_PROJECTION_V2_RPC);
            HttpURLConnection connection = openConnection(endpoint, config);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            JSONObject request = new JSONObject();
            request.put("p_projection", projection.toRpcJson());
            try (OutputStream output = connection.getOutputStream()) {
                output.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }

            String body = readResponseOrThrow(connection);
            if (!body.isEmpty() && new JSONArray(body).length() > 0) {
                published += 1;
            }
        }
        return published;
    }

    private HttpURLConnection openConnection(String endpoint, SupabaseConfig config)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", config.supabaseAnonKey);
        connection.setRequestProperty("Authorization", "Bearer " + config.accessToken);
        return connection;
    }

    private String readResponseOrThrow(HttpURLConnection connection) throws IOException {
        int statusCode = connection.getResponseCode();
        if (statusCode == 200) {
            return readStream(connection.getInputStream());
        }
        throw new IOException("Summary Projection v2 publish failed (" + statusCode + "): "
                + readStream(connection.getErrorStream()));
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }
}
