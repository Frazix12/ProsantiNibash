package com.irfan.prosantinibash.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinRecord(val version: Int, val salt: ByteArray, val iterations: Int, val hash: ByteArray)

/** Creates salted PIN verifiers. Call on a worker dispatcher; supplied PIN arrays are cleared. */
object PinVerifier {
    private const val ITERATIONS = 1_300_000

    fun create(pin: CharArray): PinRecord {
        try {
            requireValidPin(pin)
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            return PinRecord(1, salt, ITERATIONS, derive(pin, salt, ITERATIONS))
        } finally {
            pin.fill('\u0000')
        }
    }

    fun verify(pin: CharArray, record: PinRecord): Boolean {
        var candidate: ByteArray? = null
        try {
            requireValidPin(pin)
            validateRecord(record)
            require(record.version == 1) { "Keystore verification required." }
            candidate = derive(pin, record.salt, record.iterations)
            return MessageDigest.isEqual(record.hash, candidate)
        } finally {
            candidate?.fill(0)
            pin.fill('\u0000')
        }
    }

    fun requireValidPin(pin: CharArray) {
        require((pin.size == 4 || pin.size == 6) && pin.all { it in '0'..'9' }) {
            "Enter a 4 or 6 digit PIN."
        }
    }

    fun validateRecord(record: PinRecord) {
        require(record.salt.size == 16 && record.hash.size == 32 &&
            ((record.version == 1 && record.iterations == ITERATIONS) ||
                (record.version == 2 && record.iterations == 0))) { "Invalid PIN verifier." }
    }

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val specification = PBEKeySpec(pin, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(specification).encoded
        } finally {
            specification.clearPassword()
        }
    }
}
