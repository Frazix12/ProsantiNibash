package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Audit(
    val id: String,
    val action: String,
    val entity_type: String,
    val entity_id: String,
    val created_at: String,
    val old_data: JsonObject? = null,
    val new_data: JsonObject? = null,
)
