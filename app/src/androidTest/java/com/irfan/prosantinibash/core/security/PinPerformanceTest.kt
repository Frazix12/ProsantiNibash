package com.irfan.prosantinibash.core.security

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.security.Security
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Measures only synthetic input; never opens the installed application's PIN store. */
@RunWith(AndroidJUnit4::class)
class PinPerformanceTest {
    @Test fun providerTiming() {
        Security.getProviders().forEach { provider ->
            val factory = runCatching { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1", provider) }.getOrNull()
                ?: return@forEach
            val spec = PBEKeySpec(charArrayOf('1','2','3','4'), ByteArray(16), 1_300_000, 256)
            val start = System.nanoTime()
            val result = factory.generateSecret(spec).encoded
            Log.i("PinPerformance", "${provider.name}: ${(System.nanoTime()-start)/1_000_000} ms, bytes=${result.size}")
            result.fill(0); spec.clearPassword()
        }
    }
}
