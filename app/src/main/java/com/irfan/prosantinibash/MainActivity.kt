package com.irfan.prosantinibash

import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.irfan.prosantinibash.core.security.BiometricUnlock
import com.irfan.prosantinibash.feature.lock.LockViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), DefaultLifecycleObserver {
    private val container get() = (application as ProsantiApplication).container
    private lateinit var lock: LockViewModel
    private lateinit var biometric: BiometricUnlock
    private var supportsBiometric by mutableStateOf(false)
    private var securityError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super<AppCompatActivity>.onCreate(savedInstanceState)
        observeLanguage()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        biometric = BiometricUnlock(this)
        lock = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(LockViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return LockViewModel(container.pinStore, container.session) as T
            }
        })[LockViewModel::class.java]
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        setContent {
            ProsantiApp(container, lock, securityError, supportsBiometric,
                onBiometricUnlock = ::unlockBiometric,
                onEnableBiometric = ::enableBiometric,
                onDisableBiometric = ::disableBiometric)
        }
    }

    private fun observeLanguage() {
        lifecycleScope.launch {
            container.preferences.state.collect { state ->
                if (!state.loading && state.error == null) {
                    val locales = LocaleListCompat.forLanguageTags(state.preferences.language)
                    if (AppCompatDelegate.getApplicationLocales() != locales)
                        AppCompatDelegate.setApplicationLocales(locales)
                }
            }
        }
    }

    override fun onStart() {
        supportsBiometric = biometricAvailable()
        container.session.onForeground()
        super<AppCompatActivity>.onStart()
    }

    override fun onStop(owner: LifecycleOwner) { biometric.cancel() }

    override fun onDestroy() {
        biometric.cancel()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        super<AppCompatActivity>.onDestroy()
    }

    private fun biometricAvailable() = BiometricManager.from(this).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    private fun unlockBiometric() {
        biometric.authenticate(onUnlocked = lock::biometricUnlocked) { message ->
            securityError = message
            lifecycleScope.launch { if (!biometric.isEnabled()) saveBiometricPreference(false) }
        }
    }

    private fun enableBiometric(pin: CharArray) {
        securityError = null
        lock.verifyPinForBiometric(pin) {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@verifyPinForBiometric
            biometric.enableAfterPinVerification { enabled, message ->
                securityError = message
                if (enabled) saveBiometricPreference(true) else disableBiometric()
            }
        }
    }

    private fun disableBiometric() {
        lifecycleScope.launch {
        try { biometric.disable() }
        catch (_: Exception) { securityError = AppText.get(R.string.biometric_protection_could_not_be_removed_use_your_pin) }
        saveBiometricPreference(false)
        }
    }

    private fun saveBiometricPreference(enabled: Boolean) {
        lifecycleScope.launch {
            try { container.preferences.setBiometricsEnabled(enabled) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                securityError = AppText.get(R.string.biometric_settings_could_not_be_saved_your_pin_still_works)
                if (enabled) {
                    try { biometric.disable() }
                    catch (_: Exception) { securityError = AppText.get(R.string.biometric_settings_could_not_be_saved_use_your_pin) }
                }
            }
        }
    }
}
