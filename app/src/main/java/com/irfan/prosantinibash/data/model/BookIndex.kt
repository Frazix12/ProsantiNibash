package com.irfan.prosantinibash.data.model

/** Snapshot-owned lookups avoid repeatedly scanning allocations while rendering financial rows. */
class BookIndex(book: Book) {
    val tenants = book.tenants.associateBy { it.id }
    val flats = book.flats.associateBy { it.id }
    val active = book.tenancies.filter { it.status == "active" }.associateBy { it.flat_id }
    val periods = book.rent_periods.groupBy { it.tenancy_id }
    val payments = book.payments.filter { !it.voided }.groupBy { it.tenancy_id }
    val deposits = book.deposit_transactions.groupBy { it.tenancy_id }
    val paidByPeriod = totals(book.payment_allocations) { it.rent_period_id }
    val allocatedByPayment = totals(book.payment_allocations) { it.payment_id }

    private fun totals(rows: List<Allocation>, key: (Allocation) -> String): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        rows.forEach { row -> result.merge(key(row), row.amount_paisa, Math::addExact) }
        return result
    }
}
