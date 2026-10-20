package com.yeonsik.fitnessapp.integration.personalos;

import java.io.IOException;
import java.util.Map;

/** Local acknowledgements only; losing them causes a safe replay of the existing v2 RPC. */
public interface SummaryPublicationStore {
    String fingerprint(String scopeKey, String sessionId);

    void acknowledge(String scopeKey, Map<String, String> fingerprints) throws IOException;
}
