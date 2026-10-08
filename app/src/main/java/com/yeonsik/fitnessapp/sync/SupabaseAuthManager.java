package com.yeonsik.fitnessapp.sync;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.config.SupabaseConfigStore;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SupabaseAuthManager {
    private final SupabaseConfigStore configStore;
    private final ConnectionFactory connections;

    interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    public SupabaseAuthManager(SupabaseConfigStore configStore) {
        this(configStore, url -> (HttpURLConnection) url.openConnection());
    }

    SupabaseAuthManager(SupabaseConfigStore configStore, ConnectionFactory connections) {
        this.configStore = configStore;
        this.connections = connections;
    }

    public SupabaseConfig signIn(
            SupabaseConfig config,
            String email,
            String password
    ) throws Exception {
        if (!config.isConnectionConfigured()) {
            throw new SupabaseAuthErrors.Failure(0, "connection_missing");
        }
        String normalizedEmail = normalize(email);
        if (normalizedEmail.isEmpty() || password == null || password.isEmpty()) {
            throw new SupabaseAuthErrors.Failure(0, "input_missing");
        }

        JSONObject body = new JSONObject();
        body.put("email", normalizedEmail);
        body.put("password", password);
        JSONObject response = post(
                config,
                "/auth/v1/token?grant_type=password",
                body
        );
        return saveSession(config, response, normalizedEmail);
    }

    public SignUpResult signUp(
            SupabaseConfig config,
            String email,
            String password
    ) throws Exception {
        if (!config.isConnectionConfigured()) {
            throw new SupabaseAuthErrors.Failure(0, "connection_missing");
        }
        String normalizedEmail = normalize(email);
        if (normalizedEmail.isEmpty() || password == null || password.length() < 8) {
            throw new SupabaseAuthErrors.Failure(0, "password_too_short");
        }

        JSONObject body = new JSONObject();
        body.put("email", normalizedEmail);
        body.put("password", password);
        JSONObject response = post(config, "/auth/v1/signup", body);
        String accessToken = responseString(response, "access_token", "");
        String refreshToken = responseString(response, "refresh_token", "");
        JSONObject user = response.optJSONObject("user");
        // Email-confirmation signup returns the user at the root, without a session.
        if (user == null && response.has("id")) user = response;
        String userId = user == null ? "" : responseString(user, "id", "");
        String responseEmail = user == null
                ? normalizedEmail
                : responseString(user, "email", normalizedEmail);

        if (accessToken.isEmpty() && refreshToken.isEmpty()) {
            if (userId.isEmpty()) {
                throw new SupabaseAuthErrors.Failure(0, "invalid_response");
            }
            return new SignUpResult(config, true, responseEmail);
        }

        return new SignUpResult(
                saveSession(config, response, responseEmail),
                false,
                responseEmail
        );
    }

    public SupabaseConfig refresh(SupabaseConfig config) throws Exception {
        if (!config.isConfigured() || config.refreshToken.isEmpty()) {
            throw new SupabaseAuthErrors.Failure(0, "refresh_token_not_found");
        }
        JSONObject body = new JSONObject();
        body.put("refresh_token", config.refreshToken);
        JSONObject response = post(
                config,
                "/auth/v1/token?grant_type=refresh_token",
                body
        );
        return saveSession(config, response, config.email);
    }

    private SupabaseConfig saveSession(
            SupabaseConfig config,
            JSONObject response,
            String fallbackEmail
    ) throws Exception {
        String accessToken = responseString(response, "access_token", "");
        String refreshToken = responseString(response, "refresh_token", "");
        JSONObject user = response.optJSONObject("user");
        String userId = user == null ? "" : responseString(user, "id", "");
        String email = user == null ? fallbackEmail : responseString(user, "email", fallbackEmail);
        if (accessToken.isEmpty() || refreshToken.isEmpty() || userId.isEmpty()) {
            throw new SupabaseAuthErrors.Failure(0, "invalid_response");
        }
        if (!config.userId.isEmpty() && !config.userId.equals(userId)) {
            throw new SupabaseAuthErrors.Failure(0, "account_mismatch");
        }
        return configStore.saveSessionForConnection(config, userId, email, accessToken, refreshToken);
    }

    private JSONObject post(
            SupabaseConfig config,
            String path,
            JSONObject body
    ) throws Exception {
        HttpURLConnection connection = connections.open(new URL(joinUrl(config.supabaseUrl, path)));
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("apikey", config.supabaseAnonKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = connection.getResponseCode();
            String responseBody = readStream(
                    statusCode >= 200 && statusCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream()
            );
            if (statusCode < 200 || statusCode >= 300) {
                JSONObject error;
                try { error = new JSONObject(responseBody); }
                catch (Exception ignored) { error = new JSONObject(); }
                throw SupabaseAuthErrors.responseFailure(statusCode,
                        responseString(error, "error_code", responseString(error, "code", "")),
                        responseString(error, "msg", responseString(error, "error_description", responseString(error, "message", ""))));
            }
            try { return new JSONObject(responseBody); }
            catch (Exception invalidResponse) { throw new SupabaseAuthErrors.Failure(statusCode, "invalid_response"); }
        } finally {
            connection.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String responseString(JSONObject value, String key, String fallback) {
        Object field = value.opt(key);
        return field instanceof String && !((String) field).isEmpty() ? (String) field : fallback;
    }

    public static final class SignUpResult {
        public final SupabaseConfig config;
        public final boolean emailConfirmationRequired;
        public final String email;

        private SignUpResult(
                SupabaseConfig config,
                boolean emailConfirmationRequired,
                String email
        ) {
            this.config = config;
            this.emailConfirmationRequired = emailConfirmationRequired;
            this.email = email;
        }
    }
}
