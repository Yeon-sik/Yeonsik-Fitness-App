package com.yeonsik.fitnessapp.integration.pricetrace

import com.yeonsik.fitnessapp.config.SupabaseConfig
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, anonymous, read-only check through the exact production transport and strict parser. */
class BrandCatalogLiveSmokeTest {
    @Test fun publicCatalogUsesProductionClientWithoutAnyUserSession() {
        assumeTrue(System.getenv("PRICETRACE_LIVE_SMOKE") == "1")
        val config = SupabaseConfig(System.getenv("PRICETRACE_SMOKE_URL"), System.getenv("PRICETRACE_SMOKE_ANON_KEY"), "", "", "", "", "live-public")
        val client = BrandCatalogV1Client()
        val page = client.search(config, null, 25, 0)
        assertTrue("Public catalog must contain a brand for the live smoke", page.isNotEmpty())
        val found = client.search(config, page.first().selection.canonicalName, 25, 0)
        assertTrue(found.any { it.selection.brandId == page.first().selection.brandId })
        println("Brand production-client anonymous smoke PASS: page=${page.size}, exactCanonicalQuery=true, signedIn=false")
    }
}
