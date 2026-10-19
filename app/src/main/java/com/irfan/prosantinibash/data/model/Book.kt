package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

/** Consistent database snapshot; wire names and ISO business dates match the SQL contract. */
@Serializable
data class Book(
    val flats: List<Flat> = emptyList(),
    val tenants: List<Tenant> = emptyList(),
    val tenancies: List<Tenancy> = emptyList(),
    val rent_periods: List<RentPeriod> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val payment_allocations: List<Allocation> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val maintenance: List<Maintenance> = emptyList(),
    val deposit_transactions: List<Deposit> = emptyList(),
    val recurring_expenses: List<RecurringExpense> = emptyList(),
    val tenant_documents: List<TenantDocument> = emptyList(),
    val audit_logs: List<Audit> = emptyList(),
    val building: Building = Building(),
    @kotlinx.serialization.Transient val syncVersion: Long = 0,
) {
    val index by lazy { BookIndex(this) }
    fun tenant(tenancy: Tenancy) = index.tenants[tenancy.tenant_id]
    fun flat(tenancy: Tenancy) = index.flats[tenancy.flat_id]
    fun active(flatId: String) = index.active[flatId]
}
