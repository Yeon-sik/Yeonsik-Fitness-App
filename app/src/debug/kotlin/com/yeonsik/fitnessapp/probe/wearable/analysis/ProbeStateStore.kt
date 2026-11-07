package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Properties

/**
 * Small probe-only state file. On Android it lives under `noBackupFilesDir`, so it is excluded
 * from device backup and is never part of the Fitness Room database or its local backup.
 *
 * It holds the masking salt, the masked ids of the last complete query per window (keyed by a
 * masked window key, not by readable times) and the current heart-rate changes token.
 */
class ProbeStateStore(private val file: File) {
    private val properties = Properties()

    init {
        if (file.isFile) {
            file.inputStream().use { properties.load(it) }
        }
    }

    fun saltOrCreate(random: SecureRandom = SecureRandom()): ByteArray {
        properties.getProperty(KEY_SALT)?.let { stored ->
            return Base64.getDecoder().decode(stored)
        }
        val salt = ProbeIdMasker.newSalt(random)
        properties.setProperty(KEY_SALT, Base64.getEncoder().encodeToString(salt))
        save()
        return salt
    }

    fun previousQuery(windowKey: String): PreviousQuery? {
        val queriedAt = properties.getProperty("$PREFIX_WINDOW$windowKey.queriedAt") ?: return null
        val ids = properties.getProperty("$PREFIX_WINDOW$windowKey.ids").orEmpty()
        val samples = properties.getProperty("$PREFIX_WINDOW$windowKey.samples")?.toIntOrNull() ?: 0
        return PreviousQuery(
            queriedAt = Instant.ofEpochMilli(queriedAt.toLong()),
            maskedIds = ids.split(',').filter { it.isNotBlank() }.toSet(),
            sampleCount = samples,
        )
    }

    fun recordQuery(windowKey: String, queriedAt: Instant, maskedIds: Set<String>, sampleCount: Int) {
        properties.setProperty("$PREFIX_WINDOW$windowKey.queriedAt", queriedAt.toEpochMilli().toString())
        properties.setProperty("$PREFIX_WINDOW$windowKey.ids", maskedIds.sorted().joinToString(","))
        properties.setProperty("$PREFIX_WINDOW$windowKey.samples", sampleCount.toString())
        properties.setProperty(KEY_LAST_QUERY_IDS, maskedIds.sorted().joinToString(","))
        save()
    }

    /** Masked ids from the most recent complete query of any window. */
    fun lastQueryMaskedIds(): Set<String> =
        properties.getProperty(KEY_LAST_QUERY_IDS).orEmpty().split(',').filter { it.isNotBlank() }.toSet()

    fun changesToken(): StoredChangesToken? {
        val token = properties.getProperty(KEY_CHANGES_TOKEN) ?: return null
        val issuedAt = properties.getProperty(KEY_CHANGES_ISSUED_AT)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        return StoredChangesToken(token, issuedAt)
    }

    fun saveChangesToken(token: String, issuedAt: Instant) {
        properties.setProperty(KEY_CHANGES_TOKEN, token)
        properties.setProperty(KEY_CHANGES_ISSUED_AT, issuedAt.toEpochMilli().toString())
        save()
    }

    /** Keeps the original issue time when the token only advances after a read. */
    fun advanceChangesToken(token: String) {
        properties.setProperty(KEY_CHANGES_TOKEN, token)
        save()
    }

    fun clearChangesToken() {
        properties.remove(KEY_CHANGES_TOKEN)
        properties.remove(KEY_CHANGES_ISSUED_AT)
        save()
    }

    private fun save() {
        val directory = file.parentFile
        directory?.mkdirs()
        val temporary = File(directory, "${file.name}.tmp")
        temporary.outputStream().use { properties.store(it, null) }
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not save probe state." }
        }
    }

    data class StoredChangesToken(val token: String, val issuedAt: Instant?)

    private companion object {
        const val KEY_SALT = "salt"
        const val KEY_CHANGES_TOKEN = "changes.heartRate.token"
        const val KEY_CHANGES_ISSUED_AT = "changes.heartRate.issuedAt"
        const val KEY_LAST_QUERY_IDS = "lastQuery.ids"
        const val PREFIX_WINDOW = "window."
    }
}
