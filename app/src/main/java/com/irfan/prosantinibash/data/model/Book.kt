package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Field names match the SQL snapshot; ISO business dates avoid device timezone conversion.
@Serializable data class Tenant(val id: String, val full_name: String, val phone: String? = null,
    val emergency_phone: String? = null, val occupation: String? = null, val permanent_address: String? = null,
    val profile_image_path: String? = null, val notes: String? = null, val revision: Long = 1)
@Serializable data class Tenancy(val id: String, val flat_id: String, val tenant_id: String,
    val monthly_rent_paisa: Long, val move_in_date: String, val ledger_start_date: String,
    val move_out_date: String? = null, val agreement_start_date: String? = null,
    val agreement_end_date: String? = null, val settlement_notes: String? = null, val status: String, val revision: Long = 1)
@Serializable data class RentPeriod(val id: String, val tenancy_id: String, val period_date: String,
    val amount_paisa: Long, val kind: String = "monthly", val notes: String? = null, val revision: Long = 1)
@Serializable data class Payment(val id: String, val tenancy_id: String, val amount_paisa: Long,
    val payment_date: String, val through_month: String, val payment_method: String = "Cash",
    val reference: String? = null, val notes: String? = null, val attachment_path: String? = null,
    val receipt_number: String, val kind: String = "cash", val voided: Boolean = false,
    val revision: Long = 1, val receipt_snapshot: JsonObject = JsonObject(emptyMap()))
@Serializable data class Allocation(val payment_id: String, val rent_period_id: String, val amount_paisa: Long)
@Serializable data class Expense(val id: String, val amount_paisa: Long, val expense_date: String,
    val category: String, val description: String, val flat_id: String? = null, val vendor: String? = null,
    val payment_method: String? = null, val receipt_path: String? = null, val notes: String? = null,
    val recurring_expense_id: String? = null, val voided: Boolean = false, val revision: Long = 1)
@Serializable data class Maintenance(val id: String, val title: String, val maintenance_date: String,
    val amount_paisa: Long = 0, val flat_id: String? = null, val description: String? = null,
    val vendor: String? = null, val receipt_path: String? = null, val notes: String? = null,
    val expense_id: String? = null, val reminder_date: String? = null, val voided: Boolean = false, val revision: Long = 1)
@Serializable data class Deposit(val id: String, val tenancy_id: String, val type: String,
    val amount_paisa: Long, val transaction_date: String, val description: String? = null)
@Serializable data class RecurringExpense(val id: String, val category: String, val description: String,
    val amount_paisa: Long, val next_date: String, val frequency: String = "monthly",
    val interval_days: Int = 30, val flat_id: String? = null, val active: Boolean = true, val revision: Long = 1)
@Serializable data class TenantDocument(val id: String, val tenant_id: String, val document_type: String,
    val storage_path: String, val file_name: String, val mime_type: String, val created_at: String,
    val size_bytes: Long = 0, val revision: Long = 1)
@Serializable data class Audit(val id: String, val action: String, val entity_type: String,
    val entity_id: String, val created_at: String, val old_data: JsonObject? = null, val new_data: JsonObject? = null)
@Serializable data class Building(val name: String = "Prosanti Nibash", val address: String = "",
    val owner_name: String = "", val owner_phone: String = "", val revision: Long = 1)
@Serializable data class Book(val flats: List<Flat> = emptyList(), val tenants: List<Tenant> = emptyList(),
    val tenancies: List<Tenancy> = emptyList(), val rent_periods: List<RentPeriod> = emptyList(),
    val payments: List<Payment> = emptyList(), val payment_allocations: List<Allocation> = emptyList(),
    val expenses: List<Expense> = emptyList(), val maintenance: List<Maintenance> = emptyList(),
    val deposit_transactions: List<Deposit> = emptyList(), val recurring_expenses: List<RecurringExpense> = emptyList(),
    val tenant_documents: List<TenantDocument> = emptyList(), val audit_logs: List<Audit> = emptyList(),
    val building: Building = Building(),
    @kotlinx.serialization.Transient val syncVersion: Long = 0) {
    val index by lazy { BookIndex(this) }
    fun tenant(tenancy: Tenancy) = index.tenants[tenancy.tenant_id]
    fun flat(tenancy: Tenancy) = index.flats[tenancy.flat_id]
    fun active(flatId: String) = index.active[flatId]
}
