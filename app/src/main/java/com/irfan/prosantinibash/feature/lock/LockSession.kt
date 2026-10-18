package com.irfan.prosantinibash.feature.lock

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-scoped session. Call background/foreground from ProcessLifecycleOwner. */
class LockSession(
    timeoutMillis: Long = 60_000,
    private val elapsedMillis: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val mutableLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = mutableLocked.asStateFlow()
    private var timeout = timeoutMillis.also { require(it >= 0) }
    private var authenticated = false
    private var foreground = true
    private var backgroundAt = 0L

    fun setTimeoutMillis(value: Long) {
        require(value in listOf(0L, 60_000L, 300_000L, 900_000L))
        timeout = value
    }

    fun unlock() {
        authenticated = true
        mutableLocked.value = !foreground
    }

    fun lock() {
        authenticated = false
        mutableLocked.value = true
    }

    fun onBackground() {
        if (!foreground) return
        foreground = false
        backgroundAt = elapsedMillis()
        mutableLocked.value = true
    }

    fun onForeground() {
        if (!foreground) {
            val elapsed = elapsedMillis() - backgroundAt
            if (elapsed < 0 || elapsed >= timeout) authenticated = false
        }
        foreground = true
        mutableLocked.value = !authenticated
    }
}
