package com.irfan.prosantinibash.data.repository

import com.irfan.prosantinibash.data.model.Flat
import com.irfan.prosantinibash.data.model.validateBuilding
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

/** Reads and validates permanent units independently of the full financial snapshot. */
class FlatsRepository(private val client: SupabaseClient) {
    suspend fun loadFlats(): List<Flat> {
        val result = client.from("flats").select(
            columns = Columns.list("id", "floor", "flat_number", "default_rent_paisa")
        ).decodeList<Flat>()
        validateBuilding(result)
        return result
    }
}
