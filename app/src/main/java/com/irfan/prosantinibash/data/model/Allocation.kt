package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Allocation(
    val payment_id: String,
    val rent_period_id: String,
    val amount_paisa: Long,
)
