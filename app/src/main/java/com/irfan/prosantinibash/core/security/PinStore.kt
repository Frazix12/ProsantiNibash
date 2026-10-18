package com.irfan.prosantinibash.core.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.Mac
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SecurityStorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Keeps the verifier and attempt counter in one authenticated, atomic, backup-excluded file. */
class PinStore(private val context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "security/pin.bin"))
    private val mutex = Mutex()
    private data class StoredPin(val record: PinRecord, val cooldown: PinCooldown)

    suspend fun read(): PinRecord? = access { readStored()?.record }

    suspend fun write(record: PinRecord) = access {
        PinVerifier.validateRecord(record)
        writeStored(StoredPin(record, PinCooldown()))
    }

    suspend fun readCooldown(): PinCooldown = access {
        val stored = readStored() ?: throw SecurityStorageException("PIN has not been configured.")
        val previous = stored.cooldown
        if (previous.durationMillis > 0 && previous.bootCount != bootCount()) {
            val restarted = previous.copy(startedElapsedMillis = elapsedMillis(), bootCount = bootCount())
            writeStored(stored.copy(cooldown = restarted))
            restarted
        } else previous
    }

    suspend fun recordFailure(): PinCooldown = access {
        val stored = readStored() ?: throw SecurityStorageException("PIN has not been configured.")
        val failed = stored.cooldown.failed(elapsedMillis(), bootCount())
        writeStored(stored.copy(cooldown = failed))
        failed
    }

    suspend fun clearFailures() = access {
        val stored = readStored() ?: throw SecurityStorageException("PIN has not been configured.")
        if (stored.cooldown != PinCooldown()) writeStored(stored.copy(cooldown = PinCooldown()))
    }

    /** Device-bound verifier; loss of its non-exportable key fails closed. */
    suspend fun createFastVerifier(pin: CharArray): PinRecord = access {
        try {
            PinVerifier.requireValidPin(pin)
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            PinRecord(2, salt, 0, fastHash(pin, salt, true))
        } finally { pin.fill('\u0000') }
    }

    suspend fun verifyFast(pin: CharArray, record: PinRecord): Boolean = access {
        var candidate: ByteArray? = null
        try {
            PinVerifier.requireValidPin(pin)
            PinVerifier.validateRecord(record)
            require(record.version == 2)
            candidate = fastHash(pin, record.salt, false)
            MessageDigest.isEqual(candidate, record.hash)
        } finally { candidate?.fill(0); pin.fill('\u0000') }
    }

    private fun fastHash(pin: CharArray, salt: ByteArray, create: Boolean): ByteArray {
        val namespace = MessageDigest.getInstance("SHA-256").digest(file.baseFile.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val alias = "pn_pin_hmac_v2_$namespace"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey(alias, null) as? SecretKey ?: run {
            check(create) { "PIN verification key unavailable." }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256).setKeySize(256).build())
            }.generateKey()
        }
        val bytes = ByteArray(pin.size) { pin[it].code.toByte() }
        return try {
            Mac.getInstance("HmacSHA256").apply { init(key); update("pn-pin-v2".toByteArray()); update(salt) }.doFinal(bytes)
        } finally { bytes.fill(0) }
    }

    fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
    fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)

    private suspend fun <T> access(action: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { action() } catch (error: Exception) {
                if (error is SecurityStorageException) throw error
                throw SecurityStorageException("Local security storage is unavailable.", error)
            }
        }
    }

    private fun readStored(): StoredPin? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val encrypted = file.openRead().use { input ->
            val bytes = ByteArray(4097)
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                count += read
            }
            require(count in 29..4096) { "Invalid encrypted PIN file." }
            bytes.copyOf(count)
        }
        var plaintext: ByteArray? = null
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
            plaintext = cipher.doFinal(encrypted, 12, encrypted.size - 12)
            return decode(plaintext)
        } finally {
            plaintext?.fill(0)
            encrypted.fill(0)
        }
    }

    private fun writeStored(stored: StoredPin) {
        val plaintext = encode(stored)
        var encrypted: ByteArray? = null
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val newSetup = !file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()
            cipher.init(Cipher.ENCRYPT_MODE, key(newSetup))
            encrypted = cipher.iv + cipher.doFinal(plaintext)
            val directory = file.baseFile.parentFile!!
            if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create security directory.")
            val output = file.startWrite()
            try {
                output.write(encrypted)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        } finally {
            plaintext.fill(0)
            encrypted?.fill(0)
        }
    }

    private fun key(create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing != null) return existing as SecretKey
        if (!create) throw SecurityStorageException("PIN encryption key is missing. Restore access is required.")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey()
    }

    private fun encode(stored: StoredPin): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            val record = stored.record
            output.writeInt(record.version)
            output.write(record.salt)
            output.writeInt(record.iterations)
            output.write(record.hash)
            output.writeInt(stored.cooldown.failures)
            output.writeLong(stored.cooldown.durationMillis)
            output.writeLong(stored.cooldown.startedElapsedMillis)
            output.writeInt(stored.cooldown.bootCount)
        }
        bytes.toByteArray()
    }

    private fun decode(plaintext: ByteArray): StoredPin = DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
        val record = PinRecord(input.readInt(), ByteArray(16).also(input::readFully),
            input.readInt(), ByteArray(32).also(input::readFully))
        PinVerifier.validateRecord(record)
        val cooldown = PinCooldown(input.readInt(), input.readLong(), input.readLong(), input.readInt())
        require(cooldown.failures in 0..35 && cooldown.durationMillis in 0..900_000 &&
            cooldown.startedElapsedMillis >= 0 && input.available() == 0) { "Invalid PIN cooldown." }
        StoredPin(record, cooldown)
    }

    companion object { private const val KEY_ALIAS = "pn_pin_store_v1" }
}
