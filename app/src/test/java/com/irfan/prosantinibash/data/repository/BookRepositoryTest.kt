package com.irfan.prosantinibash.data.repository

import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Checks the actual RPC wire contract with local responses, without contacting Supabase. */
@OptIn(SupabaseInternal::class)
class BookRepositoryTest {
    @Test fun persistedCursorSkipsUnchangedSnapshotReconstruction() = runTest {
        val cached=Json { ignoreUnknownKeys=true }.decodeFromString<com.irfan.prosantinibash.data.model.Book>(snapshot()).copy(syncVersion=14)
        val engine=MockEngine { request ->
            assertEquals("/rest/v1/rpc/pn_sync",request.url.encodedPath)
            val body=(request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertEquals(14L,Json.parseToJsonElement(body).jsonObject.getValue("p_after").jsonPrimitive.long)
            respond("""{"version":14,"full":false,"book":{}}""",headers=headersOf(HttpHeaders.ContentType,"application/json"))
        }
        val client=client(engine)
        try {
            val repository=BookRepository(client)
            repository.restoreCache(cached)
            assertSame(cached,repository.sync())
        } finally { client.close() }
    }

    @Test fun snapshotDecodesSqlRowsIncludingUnmodeledColumns() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/rest/v1/rpc/pn_snapshot", request.url.encodedPath)
            respond(snapshot(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = client(engine)
        try {
            val book = BookRepository(client).load()
            assertEquals(14, book.flats.size)
            assertEquals("7B", book.flats.last().flatNumber)
            assertEquals("Test tenant", book.tenants.single().full_name)
            assertEquals("Settled on departure", book.tenancies.single().settlement_notes)
            assertEquals(12500L, book.payments.single().amount_paisa)
            assertEquals("cash", book.payments.single().kind)
            assertEquals("Test tenant", book.payments.single().receipt_snapshot["tenant_name"]?.jsonPrimitive?.content)
        } finally { client.close() }
    }

    @Test fun executeSendsOriginalUuidAndExactDataInsideCommand() = runTest {
        val requestId = "89aab0fe-3d53-4cc6-9394-4b1e77f6b8c9"
        val data = buildJsonObject { put("amount_paisa", 12500L); put("notes", JsonNull) }
        var requests = 0
        val engine = MockEngine { request ->
            requests++
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/rest/v1/rpc/pn_execute", request.url.encodedPath)
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            val command = Json.parseToJsonElement(body).jsonObject.getValue("p_command").jsonObject
            assertEquals(setOf("action", "request_id", "data"), command.keys)
            assertEquals("payment_save", command.getValue("action").jsonPrimitive.content)
            assertEquals(requestId, command.getValue("request_id").jsonPrimitive.content)
            assertEquals(data, command.getValue("data"))
            respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = client(engine)
        try {
            BookRepository(client).execute("payment_save", data, requestId)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    private fun client(engine: MockEngine) = createSupabaseClient("https://book-test.invalid", "sb_publishable_fake") {
        install(Postgrest)
        httpEngine = engine
    }

    private fun snapshot(): String {
        val flats = (1..7).flatMap { floor -> listOf("A", "B").map { suffix ->
            """{"id":"$floor$suffix","floor":$floor,"flat_number":"$floor$suffix","default_rent_paisa":100000,"created_at":"2026-01-01T00:00:00Z","revision":1}"""
        } }.joinToString(",")
        return """{
            "flats":[$flats],
            "tenants":[{"id":"tenant","full_name":"Test tenant","created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-01T00:00:00Z"}],
            "tenancies":[{"id":"tenancy","flat_id":"1A","tenant_id":"tenant","monthly_rent_paisa":100000,"move_in_date":"2026-01-01","ledger_start_date":"2026-01-01","status":"moved_out","settlement_notes":"Settled on departure","created_at":"2026-01-01T00:00:00Z"}],
            "payments":[{"id":"payment","tenancy_id":"tenancy","amount_paisa":12500,"payment_date":"2026-01-06","through_month":"2026-01-01","receipt_number":"PN-000001","settlement_credit":false,"created_at":"2026-01-06T00:00:00Z","receipt_snapshot":{"tenant_name":"Test tenant"}}],
            "building":{"id":1,"name":"Test building","revision":1,"updated_at":"2026-01-01T00:00:00Z"}
        }"""
    }
}
