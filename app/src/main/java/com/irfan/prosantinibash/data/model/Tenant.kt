package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Tenant(
    val id: String,
    val full_name: String,
    val phone: String? = null,
    val emergency_phone: String? = null,
    val occupation: String? = null,
    val permanent_address: String? = null,
    val profile_image_path: String? = null,
    val notes: String? = null,
    val revision: Long = 1,
)
