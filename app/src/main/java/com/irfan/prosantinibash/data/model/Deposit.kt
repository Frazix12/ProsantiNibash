package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Deposit(
    val id: String,
    val tenancy_id: String,
    val type: String,
    val amount_paisa: Long,
    val transaction_date: String,
    val description: String? = null,
)
