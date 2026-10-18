package com.irfan.prosantinibash.data.repository

import com.irfan.prosantinibash.data.model.Flat
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Exercises real SELECT/serialization against local HTTP responses; never contacts a project. */
@OptIn(SupabaseInternal::class)
class FlatsRepositoryTest {
    private val flats = (1..7).flatMap { floor ->
        listOf("A", "B").map { suffix -> Flat("$floor$suffix", floor, "$floor$suffix", Long.MAX_VALUE) }
    }

    @Test fun selectsAndDecodesTheCompleteBuilding() = runTest {
        val result = load(Json.encodeToString(flats))
        assertEquals(flats, result)
        assertEquals(14, result.size)
        assertEquals(Long.MAX_VALUE, result.first().defaultRentPaisa)
    }

    @Test fun malformedAndIncompleteResponsesFail() = runTest {
        assertTrue(runCatching { load("{invalid json") }.exceptionOrNull() is SerializationException)
        assertTrue(runCatching { load(Json.encodeToString(flats.dropLast(1))) }
            .exceptionOrNull() is IllegalArgumentException)
        val invalid = flats.mapIndexed { index, flat -> if (index == 0) flat.copy(floor = 8) else flat }
        assertTrue(runCatching { load(Json.encodeToString(invalid)) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun deniedSelectThrowsInsteadOfInventingFlats() = runTest {
        val denied = """{"code":"42501","message":"permission denied","details":null,"hint":null}"""
        assertTrue(runCatching { load(denied, HttpStatusCode.Forbidden) }.exceptionOrNull() is RestException)
    }

    private suspend fun load(body: String, status: HttpStatusCode = HttpStatusCode.OK): List<Flat> {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/rest/v1/flats", request.url.encodedPath)
            assertEquals("id,floor,flat_number,default_rent_paisa", request.url.parameters["select"])
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createSupabaseClient("https://foundation-test.invalid", "sb_publishable_fake") {
            install(Postgrest)
            httpEngine = engine
        }
        return try { FlatsRepository(client).loadFlats() }
        finally { client.close() }
    }
}
