package com.irfan.prosantinibash.core.util

import com.irfan.prosantinibash.data.model.*
import java.time.YearMonth

/** Totals use exact integer arithmetic and distinguish cash from brought-forward credit. */
object BookMath {
    fun sum(values: Iterable<Long>): Long = values.fold(0L, Math::addExact)
    fun paid(book: Book, periodId: String) = book.index.paidByPeriod[periodId] ?: 0L
    fun due(book: Book, tenancyId: String, throughMonth: YearMonth? = null): Long = sum(
        book.index.periods[tenancyId].orEmpty().filter { (throughMonth == null ||
            YearMonth.parse(it.period_date.take(7)) <= throughMonth) }.map { Math.subtractExact(it.amount_paisa, paid(book, it.id)) })
    fun credit(book: Book, tenancyId: String): Long {
        val payments = book.index.payments[tenancyId].orEmpty()
        val received = sum(payments.map { if (it.kind == "credit_refund") -it.amount_paisa else it.amount_paisa })
        val allocated = sum(payments.map { book.index.allocatedByPayment[it.id] ?: 0L })
        return Math.subtractExact(received, allocated)
    }
    fun deposit(book: Book, tenancyId: String) = sum(book.index.deposits[tenancyId].orEmpty()
        .map { if (it.type in listOf("deduction", "refund")) -it.amount_paisa else it.amount_paisa })
    fun summary(book: Book, month: YearMonth): Summary {
        val periods = book.rent_periods.filter { it.period_date.startsWith(month.toString()) && it.kind != "opening_due" }
        val expected = sum(periods.map { it.amount_paisa })
        val allocated = sum(periods.map { paid(book, it.id) })
        val cash = sum(book.payments.filter { !it.voided && it.payment_date.startsWith(month.toString()) &&
            it.kind != "opening_credit" }.map { if (it.kind == "credit_refund") -it.amount_paisa else it.amount_paisa })
        val expense = sum(book.expenses.filter { !it.voided && it.expense_date.startsWith(month.toString()) }.map { it.amount_paisa })
        val old = sum(book.rent_periods.filter { it.period_date.take(7) < month.toString() ||
            (it.period_date.startsWith(month.toString()) && it.kind == "opening_due") }.map { it.amount_paisa - paid(book, it.id) })
        return Summary(expected, cash, expected - allocated, old, expense, Math.subtractExact(cash, expense), allocated)
    }
}
data class Summary(val expected: Long, val collected: Long, val currentDue: Long, val oldDue: Long,
    val expenses: Long, val net: Long, val allocated: Long)
