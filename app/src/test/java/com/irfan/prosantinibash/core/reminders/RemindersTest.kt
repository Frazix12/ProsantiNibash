package com.irfan.prosantinibash.core.reminders

import com.irfan.prosantinibash.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RemindersTest {
    @Test fun previousDebtRemainsOverdueBeforeCurrentRentDueDate() {
        val tenancy=Tenancy("t","f","person",10000,"2026-09-01","2026-09-01",status="active")
        val current=RentPeriod("current","t","2026-10-01",10000)
        val old=RentPeriod("old","t","2026-09-01",5000)
        val day=LocalDate.of(2026,10,2)
        assertTrue(Reminders.items(Book(tenancies=listOf(tenancy),rent_periods=listOf(current)),day).isEmpty())
        val book=Book(tenancies=listOf(tenancy),rent_periods=listOf(old,current))
        assertEquals(listOf("Outstanding rent needs attention"),Reminders.items(book,day))
        assertTrue(Reminders.items(book.copy(payment_allocations=listOf(Allocation("p","old",5000))),day).isEmpty())
        assertEquals(listOf("Rent due today"),Reminders.items(
            book.copy(payment_allocations=listOf(Allocation("p","old",5000))),day.withDayOfMonth(6)))
    }
}
