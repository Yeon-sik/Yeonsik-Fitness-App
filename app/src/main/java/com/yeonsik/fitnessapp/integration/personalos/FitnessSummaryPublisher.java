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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Publishes the Personal OS Summary Projection v2 contract, including tombstones. */
public final class FitnessSummaryPublisher {
    private static final String SUMMARY_PROJECTION_V2_RPC =
            "/rest/v1/rpc/upsert_fitness_summary_projection_v2";
    private static final int ACK_BATCH_SIZE = 50;
    private final SummaryPublicationStore publicationStore;
    private final ConnectionFactory connections;

    public interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public FitnessSummaryPublisher() {
        this(new SummaryPublicationStore() {
            private final Map<String, String> fingerprints = new HashMap<>();
            @Override public String fingerprint(String scope, String id) {
                return fingerprints.get(scope + ":" + id);
            }
            @Override public void acknowledge(String scope, Map<String, String> rows) {
                rows.forEach((id, fingerprint) -> fingerprints.put(scope + ":" + id, fingerprint));
            }
        });
    }

    public FitnessSummaryPublisher(SummaryPublicationStore publicationStore) {
        this(publicationStore, url -> (HttpURLConnection) url.openConnection());
    }

    public FitnessSummaryPublisher(SummaryPublicationStore publicationStore, ConnectionFactory connections) {
        if (publicationStore == null || connections == null) {
            throw new IllegalArgumentException("Summary publication dependencies are required.");
        }
        this.publicationStore = publicationStore;
        this.connections = connections;
    }

    public int publish(SupabaseConfig config, WorkoutSummaryApi summaryStore) throws Exception {
        return publish(config, summaryStore, (completed, total) -> {});
    }

    public synchronized int publish(
            SupabaseConfig config,
            WorkoutSummaryApi summaryStore,
            BiConsumer<Integer, Integer> progress
    ) throws Exception {
        if (config == null || !config.isConfigured()) {
            throw new IllegalStateException("Supabase 설정이 비어 있습니다.");
        }
        if (summaryStore == null) {
            throw new IllegalArgumentException("Summary store is required.");
        }

        String scopeKey = SummaryPublicationFingerprint.scope(config.supabaseUrl, config.effectiveUserId());
        Map<FitnessSummaryProjectionV2, String> pending = new LinkedHashMap<>();
        for (FitnessSummaryProjectionV2 projection
                : summaryStore.completedFitnessSummaryProjectionsV2(config.effectiveUserId())) {
            String fingerprint = SummaryPublicationFingerprint.of(projection);
            if (!fingerprint.equals(publicationStore.fingerprint(scopeKey, projection.sourceFitnessSessionId))) {
                pending.put(projection, fingerprint);
            }
        }

        int published = 0;
        int processed = 0;
        Map<String, String> acknowledged = new LinkedHashMap<>();
        Exception failure = null;
        try {
            progress.accept(0, pending.size());
            for (Map.Entry<FitnessSummaryProjectionV2, String> entry : pending.entrySet()) {
                FitnessSummaryProjectionV2 projection = entry.getKey();
                String endpoint = joinUrl(config.supabaseUrl, SUMMARY_PROJECTION_V2_RPC);
                HttpURLConnection connection = openConnection(endpoint, config);
                try {
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
                        acknowledged.put(projection.sourceFitnessSessionId, entry.getValue());
                        published++;
                        if (acknowledged.size() >= ACK_BATCH_SIZE) {
                            publicationStore.acknowledge(scopeKey, acknowledged);
                            acknowledged.clear();
                        }
                    }
                } finally {
                    connection.disconnect();
                }
                progress.accept(++processed, pending.size());
            }
            return published;
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            try {
                if (!acknowledged.isEmpty()) publicationStore.acknowledge(scopeKey, acknowledged);
            } catch (IOException persistenceError) {
                if (failure != null) failure.addSuppressed(persistenceError);
                else throw persistenceError;
            }
        }
    }

    private HttpURLConnection openConnection(String endpoint, SupabaseConfig config)
            throws IOException {
        HttpURLConnection connection = connections.open(new URL(endpoint));
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
