package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RentPeriod(
    val id: String,
    val tenancy_id: String,
    val period_date: String,
    val amount_paisa: Long,
    val kind: String = "monthly",
    val notes: String? = null,
    val revision: Long = 1,
)
