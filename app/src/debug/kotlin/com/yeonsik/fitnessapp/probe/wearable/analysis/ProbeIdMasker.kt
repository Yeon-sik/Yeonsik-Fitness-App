package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Masks Health Connect record ids with a device-local random salt. The same id gives the same
 * mask on this install, so a later query can be compared, but the mask cannot be reversed or
 * matched on another device. Raw ids are never written to the report or to probe storage.
 */
class ProbeIdMasker(salt: ByteArray) {
    private val salt: ByteArray = salt.copyOf()

    init {
        require(salt.size >= MIN_SALT_BYTES) { "The masking salt is too short." }
    }

    fun mask(rawId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(rawId.toByteArray(Charsets.UTF_8))
        return digest.digest()
            .take(MASK_BYTES)
            .joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    companion object {
        const val MIN_SALT_BYTES: Int = 16
        private const val SALT_BYTES: Int = 32
        private const val MASK_BYTES: Int = 6

        fun newSalt(random: SecureRandom = SecureRandom()): ByteArray =
            ByteArray(SALT_BYTES).also(random::nextBytes)
    }
}
