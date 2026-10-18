package com.irfan.prosantinibash.feature.lock

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** PIN text lives only in this screen's current composition and is cleared on submit or background. */
@Composable
fun LockScreen(viewModel: LockViewModel, onBiometricUnlock: (() -> Unit)? = null) {
    val state by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(state.stage) { pin = "" }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) pin = ""
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); pin = "" }
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_lock), null, Modifier.size(48.dp).padding(bottom = 12.dp), tint = MaterialTheme.colorScheme.primary)
        Text(AppText.get(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        val title = when (state.stage) {
            LockStage.CREATE_PIN -> AppText.get(R.string.create_a_4_or_6_digit_pin)
            LockStage.CONFIRM_PIN -> AppText.get(R.string.confirm_your_pin)
            LockStage.LOCKED -> AppText.get(R.string.enter_your_pin)
            LockStage.ERROR -> AppText.get(R.string.security_data_unavailable)
            else -> AppText.get(R.string.opening)
        }
        Text(title, Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleMedium)
        if (state.stage == LockStage.LOADING) CircularProgressIndicator()
        if (state.stage in listOf(LockStage.CREATE_PIN, LockStage.CONFIRM_PIN, LockStage.LOCKED)) {
            PinEntry(pin, state, onChange = { pin = it }, onSubmit = {
                val submitted = pin.toCharArray()
                pin = ""
                viewModel.submitPin(submitted)
            })
            if (state.stage == LockStage.CONFIRM_PIN) {
                TextButton(onClick = viewModel::restartSetup, enabled = !state.busy) { Text(AppText.get(R.string.choose_another_pin)) }
            }
            if (state.stage == LockStage.LOCKED && onBiometricUnlock != null) {
                TextButton(onClick = onBiometricUnlock, enabled = !state.busy) { Text(AppText.get(R.string.use_biometrics)) }
            }
        } else state.error?.let { Text(appLabel(it), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PinEntry(pin: String, state: LockUiState, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val blocked = state.busy || state.retryAfterSeconds > 0
    OutlinedTextField(value = pin,
        onValueChange = { value -> onChange(DisplayLocale.asciiDigits(value).filter { it in '0'..'9' }.take(6)) },
        modifier = Modifier.fillMaxWidth(), label = { Text(AppText.get(R.string.label_pin)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        enabled = !blocked, isError = state.error != null,
        supportingText = {
            if (state.retryAfterSeconds > 0) Text(AppText.get(R.string.try_again_in_value_seconds, state.retryAfterSeconds))
            else state.error?.let { Text(appLabel(it)) }
        })
    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp),
        enabled = !blocked && pin.length in listOf(4, 6)) {
        Text(if (state.busy) AppText.get(R.string.please_wait) else if (state.stage == LockStage.LOCKED) AppText.get(R.string.unlock) else AppText.get(R.string.continue_action))
    }
}
