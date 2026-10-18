package com.irfan.prosantinibash.core.security

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.irfan.prosantinibash.data.model.*
import com.irfan.prosantinibash.data.repository.BookCache
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BookCacheTest {
    @Test fun encryptedConfirmedSnapshotRetainsCursorAndRejectsCorruption() = runBlocking<Unit> {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(target.cacheDir,"book-cache-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object: ContextWrapper(target) { override fun getNoBackupFilesDir()=directory }
        val backend="https://cache-test-${UUID.randomUUID()}.invalid"
        try {
            val book=Book(flats=(1..7).flatMap { floor -> listOf("A","B").map { suffix ->
                Flat("$floor$suffix",floor,"$floor$suffix",10000) } },
                building=Building(name="Private confirmed fixture"),syncVersion=123)
            assertTrue(BookCache(context,backend).write(book))
            val start=System.nanoTime()
            assertEquals(book,BookCache(context,backend).read())
            android.util.Log.i("PinPerformance","Confirmed cache read: ${(System.nanoTime()-start)/1_000_000} ms")
            val stored=directory.listFiles()!!.single()
            assertFalse(stored.readBytes().decodeToString().contains("Private confirmed fixture"))
            val corrupted=stored.readBytes();corrupted[corrupted.lastIndex]=(corrupted.last().toInt() xor 1).toByte()
            stored.writeBytes(corrupted)
            assertNull(BookCache(context,backend).read())
        } finally { directory.deleteRecursively() }
    }
}
