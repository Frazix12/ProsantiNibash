package com.irfan.prosantinibash.feature.lock

import org.junit.Assert.*
import org.junit.Test

class LockSessionTest {
    @Test fun backgroundHidesContentAndUnexpiredForegroundRestoresSession() {
        var now = 0L
        val session = LockSession(60_000) { now }
        assertTrue(session.isLocked.value)
        session.unlock()
        session.onBackground()
        assertTrue(session.isLocked.value)
        now = 59_999
        session.onForeground()
        assertFalse(session.isLocked.value)
        session.onBackground()
        now += 60_000
        session.onForeground()
        assertTrue(session.isLocked.value)
    }

    @Test fun immediateTimeoutAndExplicitLockNeverRestoreSession() {
        val session = LockSession(0) { 0 }
        session.unlock()
        session.onBackground()
        session.onForeground()
        assertTrue(session.isLocked.value)
        session.unlock()
        session.lock()
        session.onForeground()
        assertTrue(session.isLocked.value)
    }
    @Test fun selectedTimeoutsExpireAtTheirBoundary() {
        listOf(60_000L, 300_000L, 900_000L).forEach { timeout ->
            var now = 0L
            val session = LockSession(timeout) { now }
            session.unlock()
            session.onBackground()
            now = timeout - 1
            session.onForeground()
            assertFalse(session.isLocked.value)
            session.onBackground()
            now += timeout
            session.onForeground()
            assertTrue(session.isLocked.value)
        }
    }

}
