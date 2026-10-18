package com.irfan.prosantinibash.feature.tenants

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class RentFormTest {
    @Test fun financialPayloadUsesExactPaisaAndDoesNotSendUiState() {
        val payload = rentPayload(mapOf("amount_paisa" to "0.01", "revision" to "7",
            "agreement_end_date" to "", "full_name" to " Karim ", "request" to "local UUID", "error" to "local error"),
            setOf("amount_paisa"), setOf("revision"))
        assertEquals("1", payload.getValue("amount_paisa").jsonPrimitive.content)
        assertEquals("7", payload.getValue("revision").jsonPrimitive.content)
        assertEquals("Karim", payload.getValue("full_name").jsonPrimitive.content)
        assertEquals(kotlinx.serialization.json.JsonNull, payload["agreement_end_date"])
        assertFalse(payload.containsKey("request"))
        assertFalse(payload.containsKey("error"))
    }
    @Test fun tenancyRejectsOverlappingOpeningBalancesAndInvalidDates() {
        val valid = mapOf("full_name" to "Karim", "monthly_rent_paisa" to "10000",
            "move_in_date" to "2026-01-01", "ledger_start_date" to "2026-02-01",
            "opening_due_paisa" to "500", "opening_credit_paisa" to "0")
        assertEquals("", tenancyError(valid))
        assertEquals("Enter either opening due or credit.", tenancyError(valid + ("opening_credit_paisa" to "50")))
        assertEquals("Ledger start cannot precede move-in.", tenancyError(valid + ("ledger_start_date" to "2025-01-01")))
    }
}
