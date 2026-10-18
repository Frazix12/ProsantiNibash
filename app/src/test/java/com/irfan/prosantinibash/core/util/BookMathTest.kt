package com.irfan.prosantinibash.core.util

import com.irfan.prosantinibash.data.model.*
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class BookMathTest {
    @Test fun cashMonthOldDuesOpeningCreditAndDepositsStayDistinct() {
        val book = Book(rent_periods = listOf(RentPeriod("old", "t", "2026-09-01", 8000),
            RentPeriod("new", "t", "2026-10-01", 10000)), payments = listOf(
            Payment("p", "t", 9000, "2026-10-05", "2026-10-01", receipt_number = "PN-1"),
            Payment("o", "t", 1000, "2026-10-01", "2026-10-01", receipt_number = "PN-2", kind = "opening_credit")),
            payment_allocations = listOf(Allocation("p", "old", 8000),Allocation("p", "new",1000),Allocation("o","new",1000)),
            expenses = listOf(Expense("e",1000,"2026-10-01","Repair","Pump")),
            deposit_transactions = listOf(Deposit("d","t","received",30000,"2026-10-01"),Deposit("r","t","refund",5000,"2026-10-02")))
        val totals = BookMath.summary(book,YearMonth.of(2026,10))
        assertEquals(9000L,totals.collected);assertEquals(8000L,totals.currentDue)
        assertEquals(0L,totals.oldDue);assertEquals(8000L,totals.net)
        assertEquals(8000L,BookMath.due(book,"t"));assertEquals(0L,BookMath.credit(book,"t"))
        assertEquals(25000L,BookMath.deposit(book,"t"))
    }
    @Test fun overflowNeverWrapsFinancialTotals() {
        assertThrows(ArithmeticException::class.java) { BookMath.sum(listOf(Long.MAX_VALUE,1L)) }
    }
}
