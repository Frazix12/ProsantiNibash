package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Tenancy(
    val id: String,
    val flat_id: String,
    val tenant_id: String,
    val monthly_rent_paisa: Long,
    val move_in_date: String,
    val ledger_start_date: String,
    val move_out_date: String? = null,
    val agreement_start_date: String? = null,
    val agreement_end_date: String? = null,
    val settlement_notes: String? = null,
    val status: String,
    val revision: Long = 1,
)
