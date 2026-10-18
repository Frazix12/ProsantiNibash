package com.irfan.prosantinibash.feature.settings

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.irfan.prosantinibash.core.settings.*
import com.irfan.prosantinibash.core.ui.SectionCard
import com.irfan.prosantinibash.core.ui.ActionRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Device-only settings. Failed writes stay visible and do not claim success. */
@Composable
fun SettingsScreen(
    repository: PreferencesRepository,
    preferences: AppPreferences,
    onLock: () -> Unit,
    onChangePin: (CharArray, CharArray) -> Unit,
    onEnableBiometric: (CharArray) -> Unit,
    onDisableBiometric: () -> Unit,
    biometricAvailable: Boolean,
    securityError: String?,
) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    fun save(action: suspend () -> Unit) {
        scope.launch {
            try { action(); error = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = AppText.get(R.string.settings_could_not_be_saved_try_again) }
        }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionCard(AppText.get(R.string.language)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en" to "English", "bn" to "বাংলা").forEach { (tag, label) ->
                    FilterChip(preferences.language == tag,
                        onClick = { save { repository.setLanguage(tag) } }, label = { Text(label) })
                }
            }
        } }
        item { SectionCard(AppText.get(R.string.appearance)) { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(preferences.themeMode == mode,
                    onClick = { save { repository.setThemeMode(mode) } },
                    label = { Text(appLabel(mode.name.lowercase().replaceFirstChar { it.uppercase() })) })
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            SettingSwitch(AppText.get(R.string.dynamic_color), preferences.dynamicColor) {
                save { repository.setDynamicColor(it) }
            }
        }
        } }
        item { SectionCard(AppText.get(R.string.security)) { AutoLockOptions(preferences.autoLockMinutes) { save { repository.setAutoLockMinutes(it) } }
        PinSettings(onChangePin, onEnableBiometric, onDisableBiometric,
            preferences.biometricsEnabled, biometricAvailable)
        ActionRow(AppText.get(R.string.lock_app), onLock, R.drawable.ic_lock)
        Text(AppText.get(R.string.forgot_pin_reset_local_app_data_or_reinstall_cloud_records_remain),
            style = MaterialTheme.typography.bodySmall) } }
        (error ?: securityError)?.let { message -> item { Text(appLabel(message), color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AutoLockOptions(selected: Int, onChange: (Int) -> Unit) {
    Column {
        Text(AppText.get(R.string.auto_lock), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0, 1, 5, 15).forEach { minutes ->
                FilterChip(selected == minutes, onClick = { onChange(minutes) },
                    label = { Text(if (minutes == 0) AppText.get(R.string.now) else AppText.get(R.string.value_min, minutes)) })
            }
        }
    }
}

@Composable
private fun PinSettings(
    onChangePin: (CharArray, CharArray) -> Unit,
    onEnableBiometric: (CharArray) -> Unit,
    onDisableBiometric: () -> Unit,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    ClearPinOnStop { current = ""; replacement = ""; confirmation = "" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(AppText.get(R.string.change_pin)) }
        if (expanded || (!biometricEnabled && biometricAvailable)) PinField(AppText.get(R.string.current_pin), current) { current = it }
        if (expanded) {
            PinField(AppText.get(R.string.new_pin), replacement) { replacement = it }
            PinField(AppText.get(R.string.confirm_pin), confirmation) { confirmation = it }
            Button(onClick = {
                if (replacement != confirmation) validation = AppText.get(R.string.pins_do_not_match)
                else {
                    onChangePin(current.toCharArray(), replacement.toCharArray())
                    current = ""; replacement = ""; confirmation = ""; validation = null
                }
            }) { Text(AppText.get(R.string.save_pin)) }
        }
        if (biometricEnabled) TextButton(onClick = onDisableBiometric) { Text(AppText.get(R.string.disable_biometrics)) }
        else if (biometricAvailable) TextButton(onClick = {
            onEnableBiometric(current.toCharArray()); current = ""
        }) { Text(AppText.get(R.string.enable_biometrics)) }
        validation?.let { Text(appLabel(it), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange = { val digits = DisplayLocale.asciiDigits(it); if (digits.length <= 6 && digits.all { c -> c in '0'..'9' }) onChange(digits) },
        label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
}

/** Clears sensitive form text even when the background grace period retains the screen. */
@Composable
private fun ClearPinOnStop(clear: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val clearCurrent by rememberUpdatedState(clear)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) clearCurrent()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
