package com.irfan.prosantinibash.data.repository

import com.irfan.prosantinibash.data.model.Book
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable internal data class BookDelta(val version: Long, val full: Boolean, val book: JsonObject,
    val deleted: Map<String, List<String>> = emptyMap(), val allocation_tenancies: List<String> = emptyList())

/** Replaces complete allocation groups so voids and reallocations cannot leave stale balances. */
internal fun mergeBook(previous: Book, delta: BookDelta): Book {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val old = json.encodeToJsonElement(previous).jsonObject
    val merged = if (delta.full) delta.book.toMutableMap() else old.toMutableMap()
    delta.book.forEach { (table, incoming) ->
        if (table == "building" || table == "payment_allocations") return@forEach
        if (delta.full) { merged[table] = incoming; return@forEach }
        val removed = delta.deleted[table].orEmpty().toSet()
        val rows = old[table]?.jsonArray.orEmpty().associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }.toMutableMap()
        removed.forEach(rows::remove)
        incoming.jsonArray.forEach { row -> rows[row.jsonObject.getValue("id").jsonPrimitive.content] = row }
        merged[table] = JsonArray(rows.values.toList())
    }
    val affected = delta.allocation_tenancies.toSet()
    val paymentOwners = (previous.payments.associate { it.id to it.tenancy_id } +
        delta.book["payments"]?.jsonArray.orEmpty().associate {
            it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject.getValue("tenancy_id").jsonPrimitive.content
        })
    val retained = if (delta.full) emptyList() else old.getValue("payment_allocations").jsonArray.filter {
        paymentOwners[it.jsonObject.getValue("payment_id").jsonPrimitive.content] !in affected
    }
    merged["payment_allocations"] = JsonArray(retained + delta.book["payment_allocations"]?.jsonArray.orEmpty())
    delta.book["building"]?.let { merged["building"] = it }
    return json.decodeFromJsonElement<Book>(JsonObject(merged)).copy(syncVersion = delta.version)
}
