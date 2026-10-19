package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Maintenance(
    val id: String,
    val title: String,
    val maintenance_date: String,
    val amount_paisa: Long = 0,
    val flat_id: String? = null,
    val description: String? = null,
    val vendor: String? = null,
    val receipt_path: String? = null,
    val notes: String? = null,
    val expense_id: String? = null,
    val reminder_date: String? = null,
    val voided: Boolean = false,
    val revision: Long = 1,
)
