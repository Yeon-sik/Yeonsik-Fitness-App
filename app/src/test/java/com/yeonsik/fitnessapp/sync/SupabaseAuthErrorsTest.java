package com.yeonsik.fitnessapp.sync;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.Test;
import static org.junit.Assert.*;

public class SupabaseAuthErrorsTest {
    @Test public void invalidCredentialsAndUnconfirmedEmailHaveActionableMessages() {
        assertTrue(SupabaseAuthErrors.responseFailure(400, "invalid_credentials", "").getMessage().contains("이메일 또는 비밀번호"));
        assertTrue(SupabaseAuthErrors.responseFailure(400, "email_not_confirmed", "").getMessage().contains("이메일 인증"));
    }

    @Test public void legacyGatewayErrorsAndRateLimitsAreMapped() {
        assertEquals("invalid_api_key", SupabaseAuthErrors.responseFailure(401, "", "Invalid API key").code);
        assertEquals("invalid_credentials", SupabaseAuthErrors.responseFailure(400, "", "Invalid login credentials").code);
        assertTrue(SupabaseAuthErrors.responseFailure(429, "", "").getMessage().contains("잠시 후"));
        assertTrue(SupabaseAuthErrors.responseFailure(503, "", "").getMessage().contains("일시적인 오류"));
    }

    @Test public void arbitraryServerAndExceptionTextNeverReachTheUser() {
        String secret = "password=secret access_token=private sb_secret_hidden";
        SupabaseAuthErrors.Failure failure = SupabaseAuthErrors.responseFailure(400, secret, secret);
        assertEquals("authentication_failed", failure.code);
        assertFalse(failure.getMessage().contains(secret));
        assertFalse(SupabaseAuthErrors.messageFor(new IOException(secret)).contains(secret));
        assertFalse(SupabaseAuthErrors.messageFor(new IllegalStateException(secret)).contains(secret));
    }

    @Test public void timeoutAndDnsFailuresExplainTheNextStep() {
        assertTrue(SupabaseAuthErrors.messageFor(new SocketTimeoutException()).contains("지연"));
        assertTrue(SupabaseAuthErrors.messageFor(new UnknownHostException()).contains("URL과 네트워크"));
    }
}
