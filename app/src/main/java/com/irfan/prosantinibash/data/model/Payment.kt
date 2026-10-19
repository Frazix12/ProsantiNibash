package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Payment(
    val id: String,
    val tenancy_id: String,
    val amount_paisa: Long,
    val payment_date: String,
    val through_month: String,
    val payment_method: String = "Cash",
    val reference: String? = null,
    val notes: String? = null,
    val attachment_path: String? = null,
    val receipt_number: String,
    val kind: String = "cash",
    val voided: Boolean = false,
    val revision: Long = 1,
    val receipt_snapshot: JsonObject = JsonObject(emptyMap()),
)
