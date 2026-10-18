package com.irfan.prosantinibash.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Flat(
    val id: String,
    val floor: Int,
    @SerialName("flat_number") val flatNumber: String,
    @SerialName("default_rent_paisa") val defaultRentPaisa: Long,
)

/** Rejects missing or altered building structure rather than inventing cloud records. */
fun validateBuilding(flats: List<Flat>) {
    if (flats.isEmpty()) return
    val expected = (1..7).flatMap { listOf("${it}A", "${it}B") }.toSet()
    require(flats.size == 14 && flats.map { it.flatNumber }.toSet() == expected)
    require(flats.map { it.id }.toSet().size == 14)
    require(flats.all { it.floor in 1..7 && it.flatNumber in listOf("${it.floor}A", "${it.floor}B") && it.defaultRentPaisa >= 0 })
}
