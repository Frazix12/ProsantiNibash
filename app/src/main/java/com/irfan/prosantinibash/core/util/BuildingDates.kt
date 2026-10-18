package com.irfan.prosantinibash.core.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Building dates use Dhaka time. Overdue applies only when an unpaid balance exists. */
object BuildingDates {
    val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    fun dueDate(month: YearMonth): LocalDate = month.atDay(6)
    fun isOverdue(month: YearMonth, today: LocalDate): Boolean = today.isAfter(dueDate(month))
}
