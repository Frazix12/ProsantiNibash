package com.irfan.prosantinibash.core.security

/** Uses monotonic time; a reboot restarts the saved delay instead of trusting the wall clock. */
data class PinCooldown(
    val failures: Int = 0,
    val durationMillis: Long = 0,
    val startedElapsedMillis: Long = 0,
    val bootCount: Int = 0,
) {
    fun remainingMillis(nowElapsedMillis: Long, currentBootCount: Int): Long {
        if (currentBootCount != bootCount || nowElapsedMillis < startedElapsedMillis) {
            return durationMillis
        }
        return (durationMillis - (nowElapsedMillis - startedElapsedMillis)).coerceAtLeast(0)
    }

    fun failed(nowElapsedMillis: Long, currentBootCount: Int): PinCooldown {
        val attempts = (failures + 1).coerceAtMost(35)
        val duration = if (attempts < 5) 0 else
            (30_000L * (1L shl (attempts / 5 - 1).coerceAtMost(5))).coerceAtMost(900_000)
        return PinCooldown(attempts, duration, nowElapsedMillis, currentBootCount)
    }
}
