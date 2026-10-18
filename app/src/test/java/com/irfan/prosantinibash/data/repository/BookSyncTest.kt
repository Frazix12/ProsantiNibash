package com.irfan.prosantinibash.data.repository

import com.irfan.prosantinibash.data.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BookSyncTest {
    @Test fun voidingPaymentRemovesAllOldAllocationsForItsTenancyOnly() {
        val previous = Book(payments = listOf(payment("p1", "t1"), payment("p2", "t2")),
            payment_allocations = listOf(Allocation("p1", "r1", 100), Allocation("p2", "r2", 200)))
        val changed = payment("p1", "t1").copy(voided = true, revision = 2)
        val delta = BookDelta(2, false, buildJsonObject {
            put("payments", Json.encodeToJsonElement(listOf(changed)))
            put("payment_allocations", JsonArray(emptyList()))
        }, allocation_tenancies = listOf("t1"))
        val next = mergeBook(previous, delta)
        assertEquals(listOf(Allocation("p2", "r2", 200)), next.payment_allocations)
        assertTrue(next.payments.first { it.id == "p1" }.voided)
        assertEquals(0L, com.irfan.prosantinibash.core.util.BookMath.paid(next, "r1"))
        assertEquals(200L, com.irfan.prosantinibash.core.util.BookMath.paid(next, "r2"))
    }

    @Test fun tombstonesRemoveDocumentsAndUnchangedSyncPreservesBuilding() {
        val previous = Book(building = Building(name = "Confirmed name"),
            tenant_documents = listOf(TenantDocument("d", "t", "PDF", "path", "file", "application/pdf", "date")))
        val next = mergeBook(previous, BookDelta(3, false, buildJsonObject {
            put("tenant_documents", JsonArray(emptyList()))
        }, deleted = mapOf("tenant_documents" to listOf("d"))))
        assertTrue(next.tenant_documents.isEmpty())
        assertEquals(previous.building, next.building)
    }

    private fun payment(id: String, tenancy: String) = Payment(id, tenancy, 200, "2026-01-01", "2026-01-01", receipt_number = id)
}
