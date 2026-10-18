package com.irfan.prosantinibash

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.irfan.prosantinibash.core.design.ProsantiTheme
import com.irfan.prosantinibash.feature.lock.*
import com.irfan.prosantinibash.navigation.MainNavigation

/** Main content is absent until both local storage and the process session permit it. */
@Composable
fun ProsantiApp(
    container: AppContainer,
    lock: LockViewModel,
    securityError: String?,
    biometricAvailable: Boolean,
    onBiometricUnlock: () -> Unit,
    onEnableBiometric: (CharArray) -> Unit,
    onDisableBiometric: () -> Unit,
) {
    val preferences by container.preferences.state.collectAsStateWithLifecycle()
    val protection by lock.uiState.collectAsStateWithLifecycle()
    val locked by container.session.isLocked.collectAsState()
    val navigation = rememberNavController()
    val savedForms = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    ProsantiTheme(preferences.preferences) {
        Surface(Modifier.fillMaxSize()) {
            when {
                preferences.loading || preferences.error != null ->
                    PreferencesGate(preferences.loading, preferences.error, container.preferences::reload)
                protection.stage == LockStage.UNLOCKED && !locked -> savedForms.SaveableStateProvider("main") { MainNavigation(
                    container, navigation, lock, preferences.preferences, biometricAvailable,
                    securityError ?: protection.error, onEnableBiometric, onDisableBiometric) }
                else -> Column(Modifier.safeDrawingPadding()) {
                    Text(AppText.get(R.string.prosanti_nibash), Modifier.padding(24.dp),
                        style = MaterialTheme.typography.titleMedium)
                    securityError?.let { Text(appLabel(it), Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.error) }
                    LockScreen(lock, if (preferences.preferences.biometricsEnabled) onBiometricUnlock else null)
                }
            }
        }
    }
}

@Composable
private fun PreferencesGate(loading: Boolean, error: String?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(AppText.get(R.string.prosanti_nibash), style = MaterialTheme.typography.headlineMedium)
        if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
        else {
            Text(appLabel(error ?: AppText.get(R.string.settings_unavailable)), Modifier.padding(vertical = 16.dp))
            Button(onClick = onRetry) { Text(AppText.get(R.string.try_again)) }
        }
    }
}
