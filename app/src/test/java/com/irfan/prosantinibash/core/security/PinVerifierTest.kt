package com.irfan.prosantinibash.core.security

import org.junit.Assert.*
import org.junit.Test

class PinVerifierTest {
    @Test fun saltedVerifierAcceptsOnlyMatchingPinAndClearsInput() {
        val input = "123456".toCharArray()
        val record = PinVerifier.create(input)
        assertArrayEquals(CharArray(6), input)
        assertEquals(1_300_000, record.iterations)
        assertEquals(16, record.salt.size)
        assertEquals(32, record.hash.size)
        val correct = "123456".toCharArray()
        assertTrue(PinVerifier.verify(correct, record))
        assertArrayEquals(CharArray(6), correct)
        assertFalse(PinVerifier.verify("654321".toCharArray(), record))
    }

    @Test fun malformedPinIsRejectedAndCleared() {
        val input = "12a4".toCharArray()
        try {
            PinVerifier.create(input)
            fail("Invalid PIN accepted")
        } catch (_: IllegalArgumentException) {
            assertArrayEquals(CharArray(4), input)
        }
    }
}
