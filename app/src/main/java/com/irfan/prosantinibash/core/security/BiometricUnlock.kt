package com.irfan.prosantinibash.core.security

import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.AtomicFile
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
import java.io.DataInputStream
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Per-use strong biometric authentication proves access by decrypting a Keystore-encrypted marker. */
class BiometricUnlock(private val activity: FragmentActivity) {
    private val markerFile = AtomicFile(File(activity.noBackupFilesDir, "security/biometric.bin"))
    private var prompt: BiometricPrompt? = null
    private var generation = 0

    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) { markerFile.baseFile.exists() &&
        runCatching { keyStore().containsAlias(KEY_ALIAS) }.getOrDefault(false) }

    /** Caller must verify the current PIN immediately before requesting enrollment. */
    fun enableAfterPinVerification(onResult: (Boolean, String?) -> Unit) {
        if (!available()) { onResult(false, AppText.get(R.string.strong_biometrics_are_unavailable_use_your_pin)); return }
        cancel()
        val request = generation
        activity.lifecycleScope.launch {
        try {
            val cipher = withContext(Dispatchers.IO) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
                .setInvalidatedByBiometricEnrollment(true).build())
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, generator.generateKey()) }
            }
            if (request != generation) return@launch
            showPrompt(cipher, AppText.get(R.string.enable_biometric_unlock), onFailure = { onResult(false, it) }) { authenticated ->
                val authenticatedRequest = generation
                activity.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { writeMarker(authenticated.iv + authenticated.doFinal(MARKER)) }
                    if (authenticatedRequest != generation) return@launch
                    onResult(true, null)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { onResult(false, AppText.get(R.string.biometric_setup_failed_your_pin_still_works)) }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (request == generation) onResult(false, AppText.get(R.string.biometric_setup_is_unavailable_use_your_pin)) }
        }
    }

    fun authenticate(onUnlocked: () -> Unit, onFallback: (String?) -> Unit) {
        cancel()
        val request = generation
        activity.lifecycleScope.launch {
            try {
                if (!available() || !isEnabled()) {
                    if (request == generation) onFallback(AppText.get(R.string.use_your_pin_to_unlock))
                    return@launch
                }
                val (encrypted, cipher) = withContext(Dispatchers.IO) { prepareAuthentication() }
                if (request != generation) { encrypted.fill(0); return@launch }
                showPrompt(cipher, AppText.get(R.string.unlock_prasanti_nibash), onFallback) { authenticated ->
                    completeAuthentication(authenticated, encrypted, onUnlocked, onFallback)
                }
            } catch (e: CancellationException) { throw e }
            catch (error: Exception) {
                if (request != generation) return@launch
                if (error is KeyPermanentlyInvalidatedException || error is IOException || error is IllegalArgumentException)
                    invalidateEnrollment(onFallback)
                else onFallback(AppText.get(R.string.biometrics_are_unavailable_use_your_pin))
            }
        }
    }

    private fun prepareAuthentication(): Pair<ByteArray, Cipher> {
        val encrypted = markerFile.openRead().use { input ->
            ByteArray(12 + MARKER.size + 16).also { bytes ->
                DataInputStream(input).readFully(bytes); require(input.read() == -1)
            }
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyStore().getKey(KEY_ALIAS, null) as SecretKey,
                GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
            return encrypted to cipher
        } catch (error: Exception) { encrypted.fill(0); throw error }
    }

    private fun completeAuthentication(cipher: Cipher, encrypted: ByteArray,
        onUnlocked: () -> Unit, onFallback: (String?) -> Unit) {
        val request = generation
        activity.lifecycleScope.launch {
            var plaintext: ByteArray? = null
            try {
                plaintext = withContext(Dispatchers.IO) { cipher.doFinal(encrypted, 12, encrypted.size - 12) }
                if (request != generation) return@launch
                if (MessageDigest.isEqual(plaintext, MARKER)) onUnlocked() else invalidateEnrollment(onFallback)
            } catch (e: CancellationException) { throw e }
            catch (error: Exception) {
                if (request != generation) return@launch
                if (error is KeyPermanentlyInvalidatedException || error is AEADBadTagException) invalidateEnrollment(onFallback)
                else onFallback(AppText.get(R.string.biometrics_are_unavailable_use_your_pin))
            } finally { plaintext?.fill(0); encrypted.fill(0) }
        }
    }

    private suspend fun invalidateEnrollment(onFallback: (String?) -> Unit) {
        try {
            disable()
            onFallback(AppText.get(R.string.biometrics_changed_use_your_pin_then_enable_biometrics_again))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            onFallback(AppText.get(R.string.biometric_settings_could_not_be_reset_use_your_pin))
        }
    }

    suspend fun disable() {
        cancel()
        withContext(Dispatchers.IO) { markerFile.delete(); keyStore().deleteEntry(KEY_ALIAS) }
    }

    fun cancel() {
        generation++
        prompt?.cancelAuthentication()
        prompt = null
    }

    private fun available(): Boolean = BiometricManager.from(activity)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun showPrompt(cipher: Cipher, title: String, onFailure: (String?) -> Unit, onSuccess: (Cipher) -> Unit) {
        cancel()
        val request = generation
        prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (request != generation) return
                    generation++
                    prompt = null
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated == null) onFailure(AppText.get(R.string.use_your_pin_to_unlock)) else onSuccess(authenticated)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (request != generation) return
                    generation++
                    prompt = null
                    onFailure(AppText.get(R.string.use_your_pin_to_unlock))
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(AppText.get(R.string.use_pin)).build()
        prompt!!.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun writeMarker(encrypted: ByteArray) {
        val directory = markerFile.baseFile.parentFile!!
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot save biometric marker.")
        val output = markerFile.startWrite()
        try { output.write(encrypted); markerFile.finishWrite(output)
        } catch (error: Exception) { markerFile.failWrite(output); throw error
        } finally { encrypted.fill(0) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY_ALIAS = "pn_biometric_v1"
        private val MARKER = "pn-biometric-proof-v1".toByteArray(Charsets.UTF_8)
    }
}
