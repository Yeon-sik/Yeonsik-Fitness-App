package com.yeonsik.fitnessapp.integration.personalos;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.Map;

/** Separate from record backups and auth preferences; no database or remote schema change. */
public final class PreferencesSummaryPublicationStore implements SummaryPublicationStore {
    private final SharedPreferences preferences;

    public PreferencesSummaryPublicationStore(Context context) {
        preferences = context.getSharedPreferences("fitness_summary_publications_v2", Context.MODE_PRIVATE);
    }

    @Override
    public String fingerprint(String scopeKey, String sessionId) {
        return preferences.getString(key(scopeKey, sessionId), null);
    }

    @Override
    public void acknowledge(String scopeKey, Map<String, String> fingerprints) throws IOException {
        if (fingerprints.isEmpty()) return;
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, String> entry : fingerprints.entrySet()) {
            editor.putString(key(scopeKey, entry.getKey()), entry.getValue());
        }
        // Bounded checkpoints on the sync worker, including after a partial network failure.
        if (!editor.commit()) {
            throw new IOException("완료 요약의 전송 상태를 저장하지 못했습니다.");
        }
    }

    private static String key(String scopeKey, String sessionId) {
        return scopeKey + ":" + sessionId;
    }
}
