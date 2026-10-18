package com.irfan.prosantinibash.core.security

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PinStoreTest {
    @Test fun fastVerifierRejectsWrongPinAndSurvivesStoreReload() = runBlocking<Unit> {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "fast-pin-test-${System.nanoTime()}")
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir(): File = directory }
        try {
            val store = PinStore(context)
            val input = "2468".toCharArray()
            val record = store.createFastVerifier(input)
            assertArrayEquals(CharArray(4), input)
            store.write(record)
            val restored = PinStore(context).read()!!
            val start = System.nanoTime()
            assertTrue(store.verifyFast("2468".toCharArray(), restored))
            android.util.Log.i("PinPerformance", "Keystore verification: ${(System.nanoTime()-start)/1_000_000} ms")
            assertFalse(store.verifyFast("1357".toCharArray(), restored))
            repeat(5) { store.recordFailure() }
            assertTrue(store.readCooldown().remainingMillis(store.elapsedMillis(), store.bootCount()) > 0)
            store.clearFailures()
            assertEquals(0L, store.readCooldown().remainingMillis(store.elapsedMillis(), store.bootCount()))
        } finally { directory.deleteRecursively() }
    }

    @Test fun legacyUnlockMigratesOnlyAfterSuccessfulVerification() = runBlocking<Unit> {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "pin-migration-test-${System.nanoTime()}")
        val context = object : ContextWrapper(target) { override fun getNoBackupFilesDir(): File = directory }
        try {
            val store = PinStore(context)
            store.write(PinVerifier.create("2468".toCharArray()))
            val session = com.irfan.prosantinibash.feature.lock.LockSession()
            val model = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                com.irfan.prosantinibash.feature.lock.LockViewModel(store, session)
            }
            kotlinx.coroutines.withTimeout(30_000) {
                model.uiState.first { it.stage == com.irfan.prosantinibash.feature.lock.LockStage.LOCKED }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { model.submitPin("2468".toCharArray()) }
            kotlinx.coroutines.withTimeout(30_000) { model.uiState.first { it.stage == com.irfan.prosantinibash.feature.lock.LockStage.UNLOCKED } }
            assertEquals(2, store.read()!!.version)
            assertFalse(session.isLocked.value)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { session.lock() }
            kotlinx.coroutines.withTimeout(2_000) { model.uiState.first { it.stage == com.irfan.prosantinibash.feature.lock.LockStage.LOCKED && !it.busy } }
            val start = System.nanoTime()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { model.submitPin("2468".toCharArray()) }
            kotlinx.coroutines.withTimeout(5_000) { model.uiState.first { it.stage == com.irfan.prosantinibash.feature.lock.LockStage.UNLOCKED } }
            android.util.Log.i("PinPerformance", "Migrated unlock: ${(System.nanoTime()-start)/1_000_000} ms")
        } finally { directory.deleteRecursively() }
    }

    @Test fun encryptedStoreRoundTripsAndCorruptionNeverLooksLikeNewSetup() = runBlocking<Unit> {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "pin-store-test-${System.nanoTime()}")
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        try {
            val store = PinStore(context)
            assertNull(store.read())
            val record = PinVerifier.create("2468".toCharArray())
            store.write(record)
            assertArrayEquals(record.hash, store.read()!!.hash)
            repeat(5) { store.recordFailure() }
            assertTrue(store.readCooldown().remainingMillis(store.elapsedMillis(), store.bootCount()) > 0)
            val persisted = File(directory, "security/pin.bin")
            assertFalse(persisted.readBytes().toList().windowed(32).any { it == record.hash.toList() })
            persisted.writeBytes(byteArrayOf(1, 2, 3))
            try {
                store.read()
                fail("Corruption was treated as setup")
            } catch (_: SecurityStorageException) {
                // A damaged verifier must keep the app locked.
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
