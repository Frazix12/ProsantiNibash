package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TenantDocument(
    val id: String,
    val tenant_id: String,
    val document_type: String,
    val storage_path: String,
    val file_name: String,
    val mime_type: String,
    val created_at: String,
    val size_bytes: Long = 0,
    val revision: Long = 1,
)
