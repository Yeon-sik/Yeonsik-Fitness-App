package com.yeonsik.fitnessapp.integration.pricetrace;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.BrandReadV1;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only consumer using the same PriceTrace configuration as product lookup. */
public final class BrandReadV1Client {
    public static final String RPC = "/rest/v1/rpc/get_brand_read_v1";
    public interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }
    private volatile SupabaseConfig config;
    private final ConnectionFactory connections;

    public BrandReadV1Client(SupabaseConfig config) { this(config, url -> (HttpURLConnection) url.openConnection()); }
    public BrandReadV1Client(SupabaseConfig config, ConnectionFactory connections) {
        this.connections = connections;
        setConfig(config);
    }
    public void setConfig(SupabaseConfig config) { this.config = config == null ? SupabaseConfig.empty() : config; }

    public List<BrandReadV1> searchBrands(String query) throws Exception {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) return Collections.emptyList();
        SupabaseConfig active = config;
        if (!active.isConnectionConfigured()) throw new IOException("PriceTrace 연결 없이도 입력한 브랜드로 저장할 수 있습니다.");
        BrandCatalogV1Client.requirePublicKey(active.supabaseAnonKey);
        HttpURLConnection connection = connections.open(new URL(active.supabaseUrl.replaceAll("/+$", "") + RPC));
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("apikey", active.supabaseAnonKey);
            if (!active.supabaseAnonKey.startsWith("sb_publishable_"))
                connection.setRequestProperty("Authorization", "Bearer " + active.supabaseAnonKey);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-Contract-Version", BrandReadV1.CONTRACT_VERSION);
            connection.setDoOutput(true);
            JSONObject args = new JSONObject().put("p_query", text).put("p_limit", 20);
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(args.toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("PriceTrace 브랜드 조회에 실패했습니다. (HTTP " + status + ")");
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) != -1;) {
                    if (output.size() + read > 1_048_576) throw new IOException("PriceTrace 브랜드 응답이 너무 큽니다.");
                    output.write(buffer, 0, read);
                }
                return parse(output.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }

    public static List<BrandReadV1> parse(String body) throws IOException {
        try {
            JSONObject payload = new JSONObject(body);
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("contract_version", payload.opt("contract_version"));
            JSONArray rows = payload.getJSONArray("brands");
            List<Map<String, Object>> brands = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                Map<String, Object> brand = new LinkedHashMap<>();
                for (String key : new String[]{"brand_id", "canonical_name", "matched_alias"}) {
                    Object value = row.opt(key);
                    brand.put(key, value == JSONObject.NULL ? null : value);
                }
                brands.add(brand);
            }
            values.put("brands", brands);
            return BrandReadV1.fromContractMap(values);
        } catch (Exception error) {
            throw new IOException("PriceTrace 브랜드 응답 계약을 확인하지 못했습니다.", error);
        }
    }
}
