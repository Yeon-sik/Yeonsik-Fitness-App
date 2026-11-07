package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeIdMaskerAndStateStoreTest {
    private val directory: File = Files.createTempDirectory("wearable-probe-test").toFile()
    private val stateFile = File(directory, "wearable-probe/state.properties")

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun maskIsStableForOneSaltAndDiffersAcrossSalts() {
        val first = ProbeIdMasker(ByteArray(32) { 1 })
        val second = ProbeIdMasker(ByteArray(32) { 2 })
        val raw = "0b5d8f8e-raw-health-connect-id"
        assertEquals(first.mask(raw), first.mask(raw))
        assertNotEquals(first.mask(raw), second.mask(raw))
        assertNotEquals(first.mask(raw), first.mask("$raw-other"))
        assertTrue(Regex("[0-9a-f]{12}").matches(first.mask(raw)))
        assertFalse(first.mask(raw).contains("raw"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortSaltIsRejected() {
        ProbeIdMasker(ByteArray(8))
    }

    @Test
    fun saltPersistsAcrossStoreInstances() {
        val created = ProbeStateStore(stateFile).saltOrCreate()
        assertEquals(32, created.size)
        assertArrayEquals(created, ProbeStateStore(stateFile).saltOrCreate())
    }

    @Test
    fun previousQueryRoundTripsWithoutRawIdsOrTimes() {
        val store = ProbeStateStore(stateFile)
        assertNull(store.previousQuery("window-key"))
        val queriedAt = Instant.parse("2026-11-05T01:02:03Z")
        store.recordQuery("window-key", queriedAt, setOf("aaaaaaaaaaaa", "bbbbbbbbbbbb"), sampleCount = 42)

        val reloaded = ProbeStateStore(stateFile)
        val previous = reloaded.previousQuery("window-key")!!
        assertEquals(queriedAt, previous.queriedAt)
        assertEquals(setOf("aaaaaaaaaaaa", "bbbbbbbbbbbb"), previous.maskedIds)
        assertEquals(42, previous.sampleCount)
        assertEquals(setOf("aaaaaaaaaaaa", "bbbbbbbbbbbb"), reloaded.lastQueryMaskedIds())
        assertFalse(stateFile.readText().contains("2026-11-05"))
    }

    @Test
    fun changesTokenIsSavedAdvancedAndCleared() {
        val store = ProbeStateStore(stateFile)
        val issuedAt = Instant.parse("2026-11-05T00:00:00Z")
        store.saveChangesToken("token-1", issuedAt)
        store.advanceChangesToken("token-2")
        val stored = ProbeStateStore(stateFile).changesToken()!!
        assertEquals("token-2", stored.token)
        assertEquals(issuedAt, stored.issuedAt)

        store.clearChangesToken()
        assertNull(ProbeStateStore(stateFile).changesToken())
    }
}
