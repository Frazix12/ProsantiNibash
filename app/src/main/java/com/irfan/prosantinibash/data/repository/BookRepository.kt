package com.irfan.prosantinibash.data.repository

import com.irfan.prosantinibash.core.util.BookMath
import com.irfan.prosantinibash.data.model.Audit
import com.irfan.prosantinibash.data.model.Book
import com.irfan.prosantinibash.data.model.Payment
import com.irfan.prosantinibash.data.model.validateBuilding
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Reads one consistent cloud snapshot; all financial changes execute in database transactions. */
class BookRepository(private val client: SupabaseClient) {
    private val mutex = Mutex()
    @Volatile private var current = Book()
    private var version = 0L
    private var incrementalAvailable: Boolean? = null

    suspend fun restoreCache(book: Book) = mutex.withLock {
        if (current.flats.isEmpty()) {
            current = book
            version = book.syncVersion
        }
    }

    suspend fun sync(): Book = withContext(Dispatchers.IO) {
        mutex.withLock { update(null) }
    }

    suspend fun commit(action: String, data: JsonObject, requestId: String): Book = withContext(Dispatchers.IO) {
        mutex.withLock { update(command(action, data, requestId)) }
    }

    private suspend fun update(command: JsonObject?): Book {
        if (incrementalAvailable == false) return legacyUpdate(command)
        val delta = try {
            client.postgrest.rpc("pn_sync", buildJsonObject {
                put("p_after", version)
                command?.let { put("p_command", it) }
            }).decodeAs<BookDelta>()
        } catch (error: RestException) {
            if (!error.message.orEmpty().contains("PGRST202")) throw error
            incrementalAvailable = false
            return legacyUpdate(command)
        }
        incrementalAvailable = true
        if (!delta.full && delta.version == version) return current
        val next = mergeBook(current, delta)
        validateSnapshot(next)
        current = next
        version = delta.version
        return next
    }

    private suspend fun legacyUpdate(command: JsonObject?): Book {
        command?.let { client.postgrest.rpc("pn_execute", buildJsonObject { put("p_command", it) }) }
        return load().also { current = it }
    }

    suspend fun receipt(id: String): Payment = if (incrementalAvailable == false) {
        current.payments.firstOrNull { it.id == id } ?: load().payments.first { it.id == id }
    } else {
        client.postgrest.rpc("pn_receipt", buildJsonObject { put("p_id", id) }).decodeAs()
    }

    suspend fun auditPage(before: Audit? = null): List<Audit> = if (incrementalAvailable == false) {
        load().audit_logs.sortedWith(compareByDescending<Audit> { it.created_at }.thenByDescending { it.id })
            .filter { before == null || it.created_at < before.created_at ||
                (it.created_at == before.created_at && it.id < before.id) }
            .take(50)
    } else {
        client.postgrest.rpc("pn_audit_page", buildJsonObject {
            before?.let {
                put("p_before_time", it.created_at)
                put("p_before_id", it.id)
            }
        }).decodeAs()
    }

    suspend fun auditDetail(id: String): Audit = if (incrementalAvailable == false) {
        load().audit_logs.first { it.id == id }
    } else {
        client.postgrest.rpc("pn_audit_detail", buildJsonObject { put("p_id", id) }).decodeAs()
    }

    suspend fun reminderSummary(): JsonObject? = try {
        client.postgrest.rpc("pn_reminders").decodeAs<JsonObject>()
    } catch (error: RestException) {
        if (!error.message.orEmpty().contains("PGRST202")) throw error
        null
    }

    suspend fun load(): Book = client.postgrest.rpc("pn_snapshot").decodeAs<Book>().also {
        validateSnapshot(it)
    }

    suspend fun execute(action: String, data: JsonObject, requestId: String) {
        client.postgrest.rpc("pn_execute", buildJsonObject {
            put("p_command", command(action, data, requestId))
        })
    }

    private fun command(action: String, data: JsonObject, requestId: String) = buildJsonObject {
        put("action", action)
        put("request_id", requestId)
        put("data", data)
    }

    /** Rejects invalid structure and overflowing totals before publishing either snapshot format. */
    private fun validateSnapshot(book: Book) {
        validateBuilding(book.flats)
        book.index
        BookMath.sum(book.rent_periods.map { it.amount_paisa })
        BookMath.sum(book.payments.map { it.amount_paisa } + book.expenses.map { it.amount_paisa })
    }
}
