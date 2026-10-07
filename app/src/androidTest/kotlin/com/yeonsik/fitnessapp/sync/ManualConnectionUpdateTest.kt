package com.yeonsik.fitnessapp.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.config.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run phase=write, update with adb install -r, then phase=verify. Only test-prefixed data is touched. */
class ManualConnectionUpdateTest {
    @Test fun retainsAllThreeConnectionsAcrossAnAppUpdate() {
        val context = AuthTestContext(ApplicationProvider.getApplicationContext(), "update-retention")
        val stores = listOf(SupabaseConfigStore(context), NutritionSupabaseConfigStore(context), PriceTraceSupabaseConfigStore(context))
        val phase = InstrumentationRegistry.getArguments().getString("phase")
        assumeTrue("Update verification needs an explicit write/verify phase", phase == "write" || phase == "verify")
        if (phase == "write") {
            stores.forEachIndexed { index, store ->
                store.saveConnection("https://db-$index.example.com", "sb_publishable_update-$index")
                store.saveSessionForConnection(store.load(), "owner-$index", "p$index@example.com", "access-$index", "refresh-$index")
            }
        } else if (phase == "verify") {
            try {
                stores.forEachIndexed { index, store ->
                    val config = store.load()
                    assertEquals("https://db-$index.example.com", config.supabaseUrl)
                    assertEquals("sb_publishable_update-$index", config.supabaseAnonKey)
                    assertEquals("owner-$index", config.userId)
                    assertEquals("access-$index", config.accessToken)
                    assertEquals("refresh-$index", config.refreshToken)
                    assertTrue(config.isConfigured)
                }
            } finally { context.clean() }
        }
    }
}
