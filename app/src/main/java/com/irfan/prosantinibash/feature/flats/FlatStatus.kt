package com.irfan.prosantinibash.feature.flats

import com.irfan.prosantinibash.core.util.BookMath
import com.irfan.prosantinibash.core.util.BuildingDates
import com.irfan.prosantinibash.data.model.Book
import com.irfan.prosantinibash.data.model.Flat
import java.time.LocalDate
import java.time.YearMonth

internal fun flatStatus(book: Book, flat: Flat, month: YearMonth): String {
    val tenancy = book.active(flat.id) ?: return "Vacant"
    val periods = book.rent_periods.filter { it.tenancy_id == tenancy.id &&
        it.period_date.startsWith(month.toString()) && it.kind != "opening_due" }
    val charged = BookMath.sum(periods.map { it.amount_paisa })
    val paid = BookMath.sum(periods.map { BookMath.paid(book, it.id) })
    val oldDue = BookMath.due(book, tenancy.id, month.minusMonths(1))
    if (oldDue > 0 || (charged > paid && BuildingDates.isOverdue(month, LocalDate.now(BuildingDates.zone)))) return "Overdue"
    return if (charged == 0L) "No charge" else if (paid >= charged) "Paid" else if (paid > 0) "Partial" else "Due"
}
