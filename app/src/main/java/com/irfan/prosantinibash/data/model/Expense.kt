package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    val id: String,
    val amount_paisa: Long,
    val expense_date: String,
    val category: String,
    val description: String,
    val flat_id: String? = null,
    val vendor: String? = null,
    val payment_method: String? = null,
    val receipt_path: String? = null,
    val notes: String? = null,
    val recurring_expense_id: String? = null,
    val voided: Boolean = false,
    val revision: Long = 1,
)
