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

    public SupabaseAuthManager(SupabaseConfigStore configStore) {
        this.configStore = configStore;
    }

    public SupabaseConfig signIn(
            SupabaseConfig config,
            String email,
            String password
    ) throws Exception {
        if (!config.isConnectionConfigured()) {
            throw new IllegalStateException("Supabase URL과 anon key를 먼저 저장하세요.");
        }
        String normalizedEmail = normalize(email);
        if (normalizedEmail.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("이메일과 비밀번호를 입력하세요.");
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

    public SupabaseConfig refresh(SupabaseConfig config) throws Exception {
        if (!config.isConfigured() || config.refreshToken.isEmpty()) {
            throw new IllegalStateException("로그인이 필요합니다.");
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
        String accessToken = response.optString("access_token", "");
        String refreshToken = response.optString("refresh_token", "");
        JSONObject user = response.optJSONObject("user");
        String userId = user == null ? "" : user.optString("id", "");
        String email = user == null ? fallbackEmail : user.optString("email", fallbackEmail);
        if (accessToken.isEmpty() || refreshToken.isEmpty() || userId.isEmpty()) {
            throw new IOException("Supabase 인증 응답에 필수 세션 값이 없습니다.");
        }
        if (!config.userId.isEmpty() && !config.userId.equals(userId)) {
            throw new IllegalStateException(
                    "이 기기의 로컬 데이터는 다른 계정에 연결되어 있습니다. 계정 전환에는 별도 데이터 이전이 필요합니다."
            );
        }
        return configStore.saveSession(userId, email, accessToken, refreshToken);
    }

    private JSONObject post(
            SupabaseConfig config,
            String path,
            JSONObject body
    ) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                joinUrl(config.supabaseUrl, path)
        ).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", config.supabaseAnonKey);
        connection.setRequestProperty("Content-Type", "application/json");
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
            throw new IOException("Supabase authentication failed (" + statusCode + ").");
        }
        return new JSONObject(responseBody);
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
}
