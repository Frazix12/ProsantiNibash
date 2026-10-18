package com.irfan.prosantinibash.core.security

import org.junit.Assert.*
import org.junit.Test

class PinCooldownTest {
    @Test fun fifthFailureStartsCooldownAndLaterFailuresDoubleToFifteenMinutes() {
        var state = PinCooldown()
        repeat(4) { state = state.failed(100, 1) }
        assertEquals(0, state.remainingMillis(100, 1))
        state = state.failed(100, 1)
        assertEquals(30_000, state.remainingMillis(100, 1))
        assertEquals(20_000, state.remainingMillis(10_100, 1))
        state = state.failed(30_100, 1)
        assertEquals(30_000, state.remainingMillis(30_100, 1))
        repeat(3) { state = state.failed(60_100, 1) }
        assertEquals(30_000, state.remainingMillis(60_100, 1))
        state = state.failed(90_100, 1)
        assertEquals(60_000, state.remainingMillis(90_100, 1))
        repeat(25) { state = state.failed(100_000, 1) }
        assertEquals(900_000, state.remainingMillis(100_000, 1))
    }

    @Test fun rebootAndClockRollbackCannotShortenCooldown() {
        val state = PinCooldown(5, 30_000, 10_000, 3)
        assertEquals(30_000, state.remainingMillis(1_000, 3))
        assertEquals(30_000, state.remainingMillis(20_000, 4))
    }
}
