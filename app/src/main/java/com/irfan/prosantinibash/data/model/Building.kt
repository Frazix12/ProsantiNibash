package com.irfan.prosantinibash.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Building(
    val name: String = "Prosanti Nibash",
    val address: String = "",
    val owner_name: String = "",
    val owner_phone: String = "",
    val revision: Long = 1,
)
