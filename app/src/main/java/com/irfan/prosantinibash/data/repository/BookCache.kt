package com.irfan.prosantinibash.data.repository

import android.content.Context
import android.util.AtomicFile
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.irfan.prosantinibash.data.model.Book
import com.irfan.prosantinibash.data.model.validateBuilding
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Encrypted, backup-excluded confirmed records; never stores pending financial commands. */
class BookCache(context: Context, backend: String) {
    private val namespace = MessageDigest.getInstance("SHA-256").digest(backend.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(context.noBackupFilesDir, "book-$namespace.json"))
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): Book? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) return@withContext null
        runCatching {
            val bytes=file.openRead().use { it.readBytes() }
            val plaintext=if (bytes.firstOrNull()=='{'.code.toByte() && !hasKey()) bytes else decrypt(bytes)
            val stored=try { json.parseToJsonElement(plaintext.decodeToString()).jsonObject } finally { plaintext.fill(0); bytes.fill(0) }
            val book=if ("book" in stored) json.decodeFromJsonElement<Book>(stored.getValue("book"))
                .copy(syncVersion=stored.getValue("version").jsonPrimitive.long)
                else json.decodeFromJsonElement<Book>(stored)
            book.also { validateBuilding(it.flats); it.index }
        }.getOrElse { android.util.Log.w("BookCache", "Discarding unreadable confirmed-record cache"); file.delete(); null }
    }

    suspend fun write(book: Book): Boolean = withContext(Dispatchers.IO) {
        val output = runCatching { file.startWrite() }.getOrNull() ?: return@withContext false
        try {
            val stored=buildJsonObject { put("version",book.syncVersion);put("book",json.encodeToJsonElement(Book.serializer(),book)) }
            val plaintext=stored.toString().toByteArray()
            try { output.write(encrypt(plaintext)) } finally { plaintext.fill(0) }
            file.finishWrite(output)
            true
        } catch (_: Exception) {
            file.failWrite(output)
            android.util.Log.w("BookCache", "Confirmed records could not be cached; cloud confirmation is unaffected")
            false
        }
    }
    private fun hasKey() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias("pn_book_cache_$namespace")
    private fun key(create: Boolean): SecretKey {
        val alias="pn_book_cache_$namespace"
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        check(create) { "Cache key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key(true)) }
        return byteArrayOf(80,78,67,2) + cipher.iv + cipher.doFinal(plaintext)
    }
    private fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size >= 33 && encrypted.take(4) == listOf<Byte>(80,78,67,2))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(false),GCMParameterSpec(128,encrypted.copyOfRange(4,16)))
        }
        return cipher.doFinal(encrypted,16,encrypted.size-16)
    }

}
