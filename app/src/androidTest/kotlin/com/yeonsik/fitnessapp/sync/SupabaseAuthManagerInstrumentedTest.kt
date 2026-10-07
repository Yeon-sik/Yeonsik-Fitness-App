package com.yeonsik.fitnessapp.sync

import androidx.test.core.app.ApplicationProvider
import com.yeonsik.fitnessapp.config.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SupabaseAuthManagerInstrumentedTest {
    private lateinit var context: AuthTestContext
    private lateinit var store: SupabaseConfigStore

    @Before fun prepare() {
        context = AuthTestContext(ApplicationProvider.getApplicationContext())
        store = SupabaseConfigStore(context)
        store.saveConnection("https://shared.example.com", "sb_publishable_test-shared")
    }
    @After fun clean() { context.clean() }

    @Test fun loginUsesTheSavedProjectAndPersistsEncryptedSessionAcrossStoreRecreation() {
        lateinit var request: AuthTestConnection
        val manager = SupabaseAuthManager(store) { url ->
            AuthTestConnection(url, 200, sessionResponse()).also { request = it }
        }
        val loggedIn = manager.signIn(store.load(), " person@example.com ", " password with spaces ")
        assertTrue(loggedIn.isConfigured)
        assertEquals("/auth/v1/token", request.url.path)
        assertEquals("grant_type=password", request.url.query)
        assertEquals("POST", request.requestMethod)
        assertEquals("sb_publishable_test-shared", request.getRequestProperty("apikey"))
        assertFalse(request.instanceFollowRedirects)
        val body = JSONObject(request.sentBody.toString("UTF-8"))
        assertEquals("person@example.com", body.getString("email"))
        assertEquals(" password with spaces ", body.getString("password"))
        assertTrue(request.disconnected)
        val restored = SupabaseConfigStore(context).load()
        assertEquals(loggedIn.supabaseUrl, restored.supabaseUrl)
        assertEquals(loggedIn.supabaseAnonKey, restored.supabaseAnonKey)
        assertEquals("owner-a", restored.userId)
        assertEquals("test-access", restored.accessToken)
        assertEquals("test-refresh", restored.refreshToken)
        assertEquals("owner-a", store.saveConnection(restored.supabaseUrl, restored.supabaseAnonKey).userId)
    }

    @Test fun authenticationErrorsAreControlledAndDoNotCreateASession() {
        listOf("invalid_credentials", "email_not_confirmed").forEach { code ->
            val manager = SupabaseAuthManager(store) { url ->
                AuthTestConnection(url, 400, """{"error_code":"$code","msg":"private server detail"}""")
            }
            val failure = runCatching { manager.signIn(store.load(), "person@example.com", "password") }.exceptionOrNull()
            assertTrue(failure is SupabaseAuthErrors.Failure)
            assertEquals(code, (failure as SupabaseAuthErrors.Failure).code)
            assertFalse(failure.message!!.contains("private server detail"))
            assertFalse(store.load().isConfigured)
        }
    }

    @Test fun signupAcceptsRootUserAndNestedUserWhenEmailConfirmationIsRequired() {
        listOf(
            """{"id":"new-owner","email":"person@example.com"}""",
            """{"access_token":null,"refresh_token":null,"user":{"id":"new-owner","email":"person@example.com"}}"""
        ).forEach { response ->
            val manager = SupabaseAuthManager(store) { url -> AuthTestConnection(url, 200, response) }
            val result = manager.signUp(store.load(), "person@example.com", "password123")
            assertTrue(result.emailConfirmationRequired)
            assertEquals("person@example.com", result.email)
            assertFalse(store.load().isConfigured)
            assertEquals("", store.load().userId)
        }
    }

    @Test fun signupWithASessionAndRefreshRetainTheAuthenticatedOwner() {
        val manager = SupabaseAuthManager(store) { url -> AuthTestConnection(url, 200, sessionResponse()) }
        val result = manager.signUp(store.load(), "person@example.com", "password123")
        assertFalse(result.emailConfirmationRequired)
        assertEquals("owner-a", manager.refresh(result.config).userId)
    }

    @Test fun malformedAndNullTokenResponsesNeverCreateASession() {
        listOf("invalid JSON", "{}", """{"access_token":null,"refresh_token":42,"user":{"id":"owner-a"}}""").forEach { response ->
            val manager = SupabaseAuthManager(store) { url -> AuthTestConnection(url, 200, response) }
            val failure = runCatching { manager.signIn(store.load(), "person@example.com", "password") }.exceptionOrNull()
            assertEquals("invalid_response", (failure as SupabaseAuthErrors.Failure).code)
            assertFalse(store.load().isConfigured)
        }
    }

    @Test fun lateAuthenticationResponseCannotAttachToAnotherRemoteProject() {
        val original = store.load()
        val manager = SupabaseAuthManager(store) { url ->
            AuthTestConnection(url, 200, sessionResponse()) {
                store.saveConnection("https://replacement.example.com", "sb_publishable_replacement")
            }
        }
        val failure = runCatching { manager.signIn(original, "person@example.com", "password") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(SupabaseAuthErrors.messageFor(failure as Exception).contains("DB 연결이 변경"))
        assertEquals("https://replacement.example.com", store.load().supabaseUrl)
        assertEquals("", store.load().accessToken)
        assertEquals("", store.load().userId)
    }

    @Test fun anotherAccountDoesNotReplaceAnExistingOwnerOrToken() {
        store.saveSessionForConnection(store.load(), "owner-a", "person@example.com", "original-access", "original-refresh")
        val manager = SupabaseAuthManager(store) { url -> AuthTestConnection(url, 200, sessionResponse("owner-b")) }
        val failure = runCatching { manager.signIn(store.load(), "other@example.com", "password") }.exceptionOrNull()
        assertEquals("account_mismatch", (failure as SupabaseAuthErrors.Failure).code)
        assertEquals("owner-a", store.load().userId)
        assertEquals("original-access", store.load().accessToken)
    }

    @Test fun allThreeConnectionsAndSessionsStayIndependent() {
        val nutrition = NutritionSupabaseConfigStore(context)
        val priceTrace = PriceTraceSupabaseConfigStore(context)
        nutrition.saveConnection("https://nutrition.example.com", "sb_publishable_nutrition")
        priceTrace.saveConnection("https://price.example.com", "sb_publishable_price")
        nutrition.saveSessionForConnection(nutrition.load(), "nutrition-owner", "n@example.com", "n-access", "n-refresh")
        priceTrace.saveSessionForConnection(priceTrace.load(), "price-owner", "p@example.com", "p-access", "p-refresh")
        store.saveSessionForConnection(store.load(), "shared-owner", "s@example.com", "s-access", "s-refresh")
        store.saveConnection("https://replacement.example.com", "sb_publishable_replacement")
        assertFalse(store.load().isConfigured)
        assertEquals("nutrition-owner", NutritionSupabaseConfigStore(context).load().userId)
        assertEquals("n-access", NutritionSupabaseConfigStore(context).load().accessToken)
        assertEquals("price-owner", PriceTraceSupabaseConfigStore(context).load().userId)
        nutrition.clearSession()
        assertEquals("price-owner", priceTrace.load().userId)
    }

    @Test fun incompleteUnsafeOrApiPathSettingsDoNotReplaceASavedConnection() {
        listOf(
            "https://wrong.example.com" to "",
            "http://wrong.example.com" to "public-key",
            "https://wrong.example.com/auth/v1" to "public-key",
            "https://wrong.example.com/rest/v1" to "public-key",
            "https://wrong.example.com" to "sb_secret_disallowed",
            "https://wrong.example.com?token=hidden" to "public-key"
        ).forEach { (url, key) ->
            assertTrue(runCatching { store.saveConnection(url, key) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals("https://shared.example.com", store.load().supabaseUrl)
        }
    }
}
