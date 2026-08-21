package com.yeonsik.fitnessapp.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureTokenStore {
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";

    private final String keyAlias;
    private final SharedPreferences preferences;

    SecureTokenStore(Context context) {
        this(context, "fitnessapp_supabase_session_v1", "fitnessapp:secure-session:v1");
    }

    SecureTokenStore(Context context, String keyAlias, String preferencesName) {
        this.keyAlias = keyAlias;
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
    }

    String accessToken() {
        return decrypt(preferences.getString(KEY_ACCESS, ""));
    }

    String refreshToken() {
        return decrypt(preferences.getString(KEY_REFRESH, ""));
    }

    void save(String accessToken, String refreshToken) {
        boolean saved = preferences.edit()
                .putString(KEY_ACCESS, encrypt(accessToken))
                .putString(KEY_REFRESH, encrypt(refreshToken))
                .commit();
        if (!saved) {
            throw new IllegalStateException("보안 세션을 저장하지 못했습니다.");
        }
    }

    void clear() {
        preferences.edit().clear().commit();
    }

    private String encrypt(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + "."
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception exception) {
            throw new IllegalStateException("보안 세션을 저장하지 못했습니다.", exception);
        }
    }

    private String decrypt(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            String[] parts = value.split("\\.", 2);
            if (parts.length != 2) {
                return "";
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            );
            return new String(
                    cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                    StandardCharsets.UTF_8
            );
        } catch (Exception exception) {
            clear();
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(keyAlias)) {
            return (SecretKey) keyStore.getKey(keyAlias, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }
}
