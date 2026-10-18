package com.irfan.prosantinibash.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.irfan.prosantinibash.core.security.PinCooldown
import com.irfan.prosantinibash.core.security.PinRecord
import com.irfan.prosantinibash.core.security.PinStore
import com.irfan.prosantinibash.core.security.PinVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LockStage { LOADING, CREATE_PIN, CONFIRM_PIN, LOCKED, UNLOCKED, ERROR }
data class LockUiState(
    val stage: LockStage = LockStage.LOADING,
    val busy: Boolean = false,
    val error: String? = null,
    val retryAfterSeconds: Long = 0,
)

/** Holds only a verifier between setup steps; raw PINs are cleared after each operation. */
class LockViewModel(private val store: PinStore, private val session: LockSession) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = mutableUiState.asStateFlow()
    private var record: PinRecord? = null
    private var pendingRecord: PinRecord? = null
    private var cooldown = PinCooldown()
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            try {
                record = store.read()
                cooldown = if (record != null) store.readCooldown() else PinCooldown()
                val stage = if (record == null) LockStage.CREATE_PIN
                    else if (session.isLocked.value) LockStage.LOCKED else LockStage.UNLOCKED
                mutableUiState.value = LockUiState(stage)
                updateCooldown()
            } catch (error: Exception) { storageError(error) }
        }
        viewModelScope.launch {
            session.isLocked.collect { locked ->
                val current = mutableUiState.value
                if (current.stage in listOf(LockStage.LOCKED, LockStage.UNLOCKED)) {
                    mutableUiState.value = current.copy(stage = if (locked) LockStage.LOCKED else LockStage.UNLOCKED)
                }
            }
        }
    }

    fun submitPin(pin: CharArray) {
        val state = mutableUiState.value
        if (state.busy || state.retryAfterSeconds > 0 || state.stage !in
            listOf(LockStage.CREATE_PIN, LockStage.CONFIRM_PIN, LockStage.LOCKED)) {
            pin.fill('\u0000')
            return
        }
        mutableUiState.value = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                PinVerifier.requireValidPin(pin)
                when (state.stage) {
                    LockStage.CREATE_PIN -> createPin(pin)
                    LockStage.CONFIRM_PIN -> confirmPin(pin)
                    LockStage.LOCKED -> unlockWithPin(pin)
                    else -> Unit
                }
            } catch (error: IllegalArgumentException) { showError(error.message ?: "Invalid PIN.")
            } catch (error: Exception) { storageError(error)
            } finally {
                pin.fill('\u0000')
                mutableUiState.value = mutableUiState.value.copy(busy = false)
            }
        }
    }

    private suspend fun verifyPin(pin: CharArray, verifier: PinRecord): Boolean =
        if (verifier.version == 2) store.verifyFast(pin, verifier)
        else withContext(Dispatchers.Default) { PinVerifier.verify(pin, verifier) }

    private suspend fun createPin(pin: CharArray) {
        pendingRecord = store.createFastVerifier(pin)
        mutableUiState.value = LockUiState(LockStage.CONFIRM_PIN, busy = true)
    }

    private suspend fun confirmPin(pin: CharArray) {
        val candidate = pendingRecord ?: throw IllegalStateException("PIN setup must be restarted.")
        val matches = verifyPin(pin, candidate)
        if (!matches) { showError("PINs do not match. Confirm your PIN again."); return }
        store.write(candidate)
        record = candidate
        pendingRecord = null
        session.unlock()
        mutableUiState.value = LockUiState(if (session.isLocked.value) LockStage.LOCKED else LockStage.UNLOCKED)
    }

    private suspend fun unlockWithPin(pin: CharArray) {
        cooldown = store.readCooldown()
        if (remainingCooldown() > 0) { updateCooldown(); return }
        val existing = requireNotNull(record)
        val migrationPin = if (existing.version == 1) pin.copyOf() else null
        val matches = try { verifyPin(pin, existing) }
            catch (error: Exception) { migrationPin?.fill('\u0000'); throw error }
        if (matches && migrationPin != null) {
            val replacement = try { store.createFastVerifier(migrationPin) } finally { migrationPin.fill('\u0000') }
            store.write(replacement)
            record = replacement
        } else migrationPin?.fill('\u0000')
        if (!matches) {
            cooldown = store.recordFailure()
            showError("Incorrect PIN.")
            updateCooldown()
            return
        }
        store.clearFailures()
        cooldown = PinCooldown()
        session.unlock()
        mutableUiState.value = LockUiState(if (session.isLocked.value) LockStage.LOCKED else LockStage.UNLOCKED)
    }

    fun changePin(currentPin: CharArray, newPin: CharArray) {
        if (mutableUiState.value.busy || mutableUiState.value.stage != LockStage.UNLOCKED) {
            currentPin.fill('\u0000'); newPin.fill('\u0000'); return
        }
        mutableUiState.value = mutableUiState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                PinVerifier.requireValidPin(newPin)
                cooldown = store.readCooldown()
                check(remainingCooldown() == 0L) { "Wait before trying your PIN again." }
                val matches = verifyPin(currentPin, requireNotNull(record))
                if (!matches) {
                    cooldown = store.recordFailure()
                    showError("Incorrect current PIN.")
                    updateCooldown()
                } else {
                    val replacement = store.createFastVerifier(newPin)
                    store.write(replacement)
                    record = replacement
                    cooldown = PinCooldown()
                }
            } catch (error: IllegalArgumentException) { showError(error.message ?: "Invalid PIN.")
            } catch (error: IllegalStateException) { showError(error.message ?: "PIN change unavailable.")
            } catch (error: Exception) { storageError(error)
            } finally {
                currentPin.fill('\u0000'); newPin.fill('\u0000')
                mutableUiState.value = mutableUiState.value.copy(busy = false)
            }
        }
    }

    /** Invoke only after BiometricUnlock has authenticated and decrypted its Keystore marker. */
    fun biometricUnlocked() {
        if (mutableUiState.value.stage != LockStage.LOCKED || mutableUiState.value.busy || record == null) return
        session.unlock()
        mutableUiState.value = LockUiState(if (session.isLocked.value) LockStage.LOCKED else LockStage.UNLOCKED)
    }

    fun verifyPinForBiometric(pin: CharArray, onVerified: () -> Unit) {
        if (mutableUiState.value.busy || mutableUiState.value.stage != LockStage.UNLOCKED) {
            pin.fill('\u0000'); return
        }
        mutableUiState.value = mutableUiState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                cooldown = store.readCooldown()
                if (remainingCooldown() > 0) {
                    showError("Wait before trying your PIN again.")
                    updateCooldown()
                    return@launch
                }
                val matches = verifyPin(pin, requireNotNull(record))
                if (matches) {
                    store.clearFailures()
                    cooldown = PinCooldown()
                    if (!session.isLocked.value) onVerified()
                } else {
                    cooldown = store.recordFailure()
                    showError("Incorrect PIN.")
                    updateCooldown()
                }
            } catch (error: IllegalArgumentException) { showError(error.message ?: "Invalid PIN.")
            } catch (error: Exception) { storageError(error)
            } finally {
                pin.fill('\u0000')
                mutableUiState.value = mutableUiState.value.copy(busy = false)
            }
        }
    }

    fun restartSetup() {
        if (mutableUiState.value.busy || mutableUiState.value.stage != LockStage.CONFIRM_PIN) return
        pendingRecord?.hash?.fill(0)
        pendingRecord = null
        mutableUiState.value = LockUiState(LockStage.CREATE_PIN)
    }

    private fun remainingCooldown(): Long = cooldown.remainingMillis(store.elapsedMillis(), store.bootCount())

    private fun updateCooldown() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            try {
                do {
                    val seconds = (remainingCooldown() + 999) / 1000
                    mutableUiState.value = mutableUiState.value.copy(retryAfterSeconds = seconds)
                    if (seconds == 0L) break
                    delay(500)
                } while (true)
            } catch (error: Exception) { storageError(error) }
        }
    }

    private fun showError(message: String) {
        mutableUiState.value = mutableUiState.value.copy(error = message)
    }

    private fun storageError(error: Exception) {
        if (error is CancellationException) throw error
        session.lock()
        mutableUiState.value = LockUiState(LockStage.ERROR,
            error = "Local security data could not be read or saved. The app remains locked.")
    }

    override fun onCleared() {
        pendingRecord?.hash?.fill(0)
        pendingRecord?.salt?.fill(0)
        super.onCleared()
    }
}
