package com.irfan.prosantinibash.feature.reports

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.*
import com.irfan.prosantinibash.data.model.*
import androidx.compose.material3.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import com.irfan.prosantinibash.core.util.BuildingDates
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.Money
import com.irfan.prosantinibash.feature.book.BookUiState

private data class SearchResults(val tenancies:List<Tenancy> = emptyList(),val payments:List<Payment> = emptyList(),
    val expenses:List<Expense> = emptyList(),val term:String="")

private suspend fun search(book:Book,term:String):SearchResults = withContext(Dispatchers.Default) {
    val context=currentCoroutineContext()
    if(term.isBlank()) SearchResults(term=term) else SearchResults(
        book.tenancies.filter { tenancy ->
            context.ensureActive()
            val tenant=book.tenant(tenancy)
            listOf(tenant?.full_name,tenant?.phone,book.flat(tenancy)?.flatNumber).any { it?.contains(term,true)==true }
        }, book.payments.filter { context.ensureActive(); it.receipt_number.contains(term,true) || it.reference?.contains(term,true)==true },
        book.expenses.filter { context.ensureActive(); it.description.contains(term,true) || (it.category.contains(term,true) || appLabel(it.category).contains(term,true)) },term)
}

@Composable fun SearchScreen(state: BookUiState, onTenant: (String) -> Unit, onPayment: (String) -> Unit, onExpense: (String) -> Unit, onRefresh: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val term = DisplayLocale.asciiDigits(query.trim())
    val results by produceState(SearchResults(),state.book,term) { value=search(state.book,term) }
    val searching=results.term!=term
    val tenancies=if(searching) emptyList() else results.tenancies
    val payments=if(searching) emptyList() else results.payments
    val expenses=if(searching) emptyList() else results.expenses
    BookListPage(state, onRefresh) {
        item { TextEntry(AppText.get(R.string.search_records), query) { query = it }
            Text(AppText.get(R.string.tenant_phone_flat_receipt_reference_or_expense), style = MaterialTheme.typography.bodySmall) }
        if (term.isBlank()) item { EmptyState(AppText.get(R.string.find_a_record), AppText.get(R.string.search_across_tenant_history_receipts_and_expenses)) }
        if (searching) item { LinearProgressIndicator() }
        if (term.isNotEmpty() && !searching) {
            if (tenancies.isNotEmpty()) item { SectionHeading(AppText.get(R.string.tenants_value, tenancies.size)) }
            items(tenancies, key = { "tenancy/${it.id}" }) { tenancy -> SectionCard {
                ActionRow(state.book.tenant(tenancy)?.full_name.orEmpty(), { onTenant(tenancy.id) }, R.drawable.ic_person, "${state.book.flat(tenancy)?.flatNumber} · ${AppText.get(if (tenancy.status == "active") R.string.current_tenant else R.string.previous_tenant)}")
            } }
            if (payments.isNotEmpty()) item { SectionHeading(AppText.get(R.string.payments_value, payments.size)) }
            items(payments, key = { "payment/${it.id}" }) { payment -> SectionCard {
                ActionRow(payment.receipt_number, { onPayment(payment.id) }, R.drawable.ic_receipt, "${DisplayLocale.date(payment.payment_date)} · ${appLabel(payment.payment_method)}")
                MoneyRow(AppText.get(R.string.amount), payment.amount_paisa, true)
                if (payment.voided) StatusBadge(AppText.get(R.string.voided))
            } }
            if (expenses.isNotEmpty()) item { SectionHeading(AppText.get(R.string.expenses_value, expenses.size)) }
            items(expenses, key = { "expense/${it.id}" }) { expense -> SectionCard {
                ActionRow(expense.description, { onExpense(expense.id) }, R.drawable.ic_document, "${DisplayLocale.date(expense.expense_date)} · ${appLabel(expense.category)}")
                MoneyRow(AppText.get(R.string.amount), expense.amount_paisa, true)
                if (expense.voided) StatusBadge(AppText.get(R.string.voided))
            } }
            if (tenancies.isEmpty() && payments.isEmpty() && expenses.isEmpty()) item { EmptyState(AppText.get(R.string.no_matching_records), AppText.get(R.string.try_a_tenant_name_flat_number_or_receipt_reference)) }
        }
    }
}

@Composable fun AuditScreen(state: BookUiState, history: AuditState, model: AuditViewModel) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    BookListPage(state.copy(loading = history.loading, error = history.error), { model.load(true) }) {
        item { Text(AppText.get(R.string.changes_are_retained_for_both_owners_no_individual_owner_identity_is_record)) }
        if (history.entries.isEmpty()) item { EmptyState(AppText.get(R.string.no_changes_yet), AppText.get(R.string.saved_changes_will_appear_here_for_both_owners)) }
        items(history.entries, key = { it.id }) { entry ->
            SectionCard {
            ActionRow(appLabel(entry.action), { expanded = if (expanded == entry.id) null else entry.id; if (expanded != null) model.detail(entry.id) }, R.drawable.ic_info, "${auditDate(entry.created_at)} · ${appLabel(entry.entity_type)}")
            if (expanded == entry.id) {
                Text(AppText.get(R.string.record_value, entry.entity_id))
                if (entry.id !in history.details) { LinearProgressIndicator(); TextButton(onClick = { model.detail(entry.id) }) { Text(AppText.get(R.string.load_details)) } }
                history.details[entry.id]?.old_data?.let { values -> Text(AppText.get(R.string.before)); values.forEach { (key, value) -> Text("${appLabel(key)}: ${auditValue(key, value)}") } }
                history.details[entry.id]?.new_data?.let { values -> Text(AppText.get(R.string.after)); values.forEach { (key, value) -> Text("${appLabel(key)}: ${auditValue(key, value)}") } }
            }
            }
        }
        if (history.hasMore) item { TextButton(onClick = { model.load() }, enabled = !history.loading) { Text(AppText.get(R.string.load_more_changes)) } }
    }
}

private fun auditDate(value: String): String = runCatching { OffsetDateTime.parse(value).atZoneSameInstant(BuildingDates.zone).format(DisplayLocale.formatter("d MMM yyyy · h:mm a")) }.getOrDefault(value)

internal fun auditValue(key: String, value: kotlinx.serialization.json.JsonElement): String {
    if (value is kotlinx.serialization.json.JsonObject) return value.entries.joinToString("\n") {
        "${appLabel(it.key)}: ${auditValue(it.key, it.value)}"
    }
    if (value is kotlinx.serialization.json.JsonArray) return value.joinToString("\n") { auditValue(key, it) }
    val primitive = value as? kotlinx.serialization.json.JsonPrimitive ?: return value.toString()
    val text = primitive.content
    return when {
        key.endsWith("_date") || key == "next_date" -> DisplayLocale.date(text)
        key.endsWith("_at") -> auditDate(text)
        !primitive.isString -> DisplayLocale.digits(appLabel(text))
        key in setOf("status", "kind", "type", "frequency", "category", "document_type", "payment_method") -> appLabel(text)
        else -> text
    }
}
