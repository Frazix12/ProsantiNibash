package com.irfan.prosantinibash.core.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MoneyDatesTest {
    @Test fun exactAmountsAndInvalidInput() {
        assertEquals(1_500_000L, Money.parsePaisa("15000"))
        assertEquals(1L, Money.parsePaisa("0.01"))
        assertEquals(123_456L, Money.parsePaisa("1234.56"))
        assertEquals(0L, Money.parsePaisa("0"))
        listOf("", "-1", "1.001", "NaN", "1e3", "1,000", "9223372036854775808")
            .forEach { assertNull(Money.parsePaisa(it)) }
        assertEquals("৳15,000.00", Money.format(1_500_000L))
        assertEquals("৳92,233,720,368,547,758.07", Money.format(Long.MAX_VALUE))
    }
    @Test fun overdueStartsAfterSixth() {
        val month = YearMonth.of(2026, 10)
        assertFalse(BuildingDates.isOverdue(month, LocalDate.of(2026, 10, 6)))
        assertTrue(BuildingDates.isOverdue(month, LocalDate.of(2026, 10, 7)))
        assertEquals("Asia/Dhaka", BuildingDates.zone.id)
    }
}
