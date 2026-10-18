package com.irfan.prosantinibash.feature.deposits

import com.irfan.prosantinibash.data.model.*
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class SettlementTest {
    @Test fun backdatedDepartureReleasesFutureMonthlyAllocationsButKeepsManualCharges() {
        val book = Book(
            rent_periods = listOf(RentPeriod("a", "t", "2026-09-01", 10000), RentPeriod("b", "t", "2026-10-01", 10000),
                RentPeriod("c", "t", "2026-10-02", 2000, "historical")),
            payments = listOf(Payment("p", "t", 15000, "2026-09-01", "2026-10-01", receipt_number = "PN-1")),
            payment_allocations = listOf(Allocation("p", "a", 10000), Allocation("p", "b", 5000)),
            deposit_transactions = listOf(Deposit("d", "t", "received", 3000, "2026-09-01")))
        val result = settlementPreview(book, "t", "2026-09-30", 1000, -500)
        assertEquals(2000L, result.due); assertEquals(5000L, result.credit); assertEquals(3000L, result.deposit)
        assertEquals(BigInteger.valueOf(-5500), result.net)
    }
    @Test fun signedAdjustmentsRemainExactAndRejectOverflow() {
        assertEquals(-101L, signedAmount("-1.01")); assertEquals(101L, signedAmount("1.01"))
        assertNull(signedAmount("--1")); assertNull(signedAmount("1.001")); assertNull(signedAmount("92233720368547758.08"))
    }
}
