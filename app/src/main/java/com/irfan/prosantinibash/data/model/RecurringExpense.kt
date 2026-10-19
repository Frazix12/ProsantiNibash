package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RecurringExpense(
    val id: String,
    val category: String,
    val description: String,
    val amount_paisa: Long,
    val next_date: String,
    val frequency: String = "monthly",
    val interval_days: Int = 30,
    val flat_id: String? = null,
    val active: Boolean = true,
    val revision: Long = 1,
)
