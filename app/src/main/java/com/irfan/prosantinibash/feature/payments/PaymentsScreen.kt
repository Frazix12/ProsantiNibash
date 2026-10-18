package com.irfan.prosantinibash.feature.payments

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.feature.book.*
import java.time.YearMonth
import java.time.LocalDate

@Composable
fun PaymentsScreen(state: BookUiState, model: BookViewModel, onPayment: (String) -> Unit, onDetails: (String) -> Unit, onRecordPayment: () -> Unit = {}, onAddTenant: () -> Unit = {}) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now(BuildingDates.zone).toString()) }
    var filter by rememberSaveable { mutableStateOf("All") }
    val month = YearMonth.parse(monthText)
    val today = LocalDate.now(BuildingDates.zone)
    val periods = state.book.rent_periods.filter { it.period_date.startsWith(monthText) && it.kind != "opening_due" }
    val oldThrough = minOf(month.minusMonths(1), YearMonth.from(today).minusMonths(1))
    val entries = periods.groupBy { it.tenancy_id }.toMutableMap()
    state.book.tenancies.filter { BookMath.due(state.book, it.id, oldThrough) > 0 }.forEach { entries.putIfAbsent(it.id, emptyList()) }
    BookListPage(state, model::refresh) {
        item { PaymentMonthSummary(state, month, filter, { monthText = it.toString() }, { filter = it }, onRecordPayment, onAddTenant) }
        entries.forEach { (id, charges) ->
            val amount = BookMath.sum(charges.map { it.amount_paisa })
            val collected = BookMath.sum(charges.map { BookMath.paid(state.book, it.id) })
            val status = if (charges.isEmpty()) "Due" else if (amount == 0L) "No charge" else if (collected >= amount) "Paid" else if (collected > 0) "Partial" else "Due"
            val overdue = BookMath.due(state.book, id, oldThrough) > 0 ||
                (amount > collected && BuildingDates.isOverdue(month, today))
            if (filter == "All" || filter == status || (filter == "Overdue" && overdue)) item(key = "tenancy-$id") {
                TenantPaymentBalance(state, id, collected, amount, overdue, status, charges.isEmpty(), oldThrough, onPayment)

            }
        }
        PaymentHistory(state, monthText, onDetails)
    }
}

private fun LazyListScope.PaymentHistory(state: BookUiState, month: String, onDetails: (String) -> Unit) {
    item { SectionHeading(AppText.get(R.string.payment_history)) }
    val payments = state.book.payments.filter { it.payment_date.startsWith(month) }.sortedByDescending { it.payment_date }
    if (payments.isEmpty()) item { EmptyState(AppText.get(R.string.no_payments_this_month), AppText.get(R.string.recorded_payments_will_appear_here_with_their_receipt_and_amount)) }
    items(payments, key = { "payment-${it.id}" }) { payment ->
        val tenancy = state.book.tenancies.firstOrNull { it.id == payment.tenancy_id }
        SectionCard {
            ActionRow(tenancy?.let { "${state.book.flat(it)?.flatNumber.orEmpty()} · ${state.book.tenant(it)?.full_name.orEmpty()}" } ?: payment.receipt_number,
                { onDetails(payment.id) }, R.drawable.ic_receipt,
                "${DisplayLocale.date(payment.payment_date)} · ${appLabel(payment.payment_method)} · ${payment.receipt_number}")
            MoneyRow(paymentKindLabel(payment.kind), payment.amount_paisa, true)
            if (payment.voided) StatusBadge(AppText.get(R.string.voided)) else if (payment.revision > 1) StatusBadge(AppText.get(R.string.corrected))
        }
    }
}

@Composable
fun OutstandingScreen(state: BookUiState, onTenant: (String) -> Unit, onPayment: (String) -> Unit, onRefresh: () -> Unit) {
    val outstanding = state.book.tenancies.map { it to BookMath.due(state.book, it.id) }.filter { it.second > 0 }.sortedByDescending { it.second }
    BookListPage(state, onRefresh) {
        item {
        SectionCard { MoneyRow(AppText.get(R.string.total_outstanding), BookMath.sum(outstanding.map { it.second }), true) }
        if (outstanding.isEmpty()) EmptyState(AppText.get(R.string.all_caught_up), AppText.get(R.string.there_is_no_outstanding_rent_in_the_ledger))
        }
        items(outstanding, key = { it.first.id }) { (tenancy, due) ->
            SectionCard {
            ActionRow("${state.book.flat(tenancy)?.flatNumber.orEmpty()} · ${state.book.tenant(tenancy)?.full_name.orEmpty()}", { onTenant(tenancy.id) }, R.drawable.ic_person)
            MoneyRow(AppText.get(R.string.rent_due), due, true)
            if (tenancy.status != "active") StatusBadge(AppText.get(R.string.previous_tenant))
            val currentMonth = YearMonth.now(BuildingDates.zone)
            val oldDue = BookMath.due(state.book, tenancy.id, currentMonth.minusMonths(1))
            val months = state.book.rent_periods.filter { it.tenancy_id == tenancy.id && BookMath.paid(state.book, it.id) < it.amount_paisa }.map { it.period_date.take(7) }.distinct().size
            Text(AppText.get(R.string.value_unpaid_charge_months, months), style = MaterialTheme.typography.bodySmall)
            MoneyRow(AppText.get(R.string.earlier_due), oldDue)
            MoneyRow(AppText.get(R.string.current_due), (due - oldDue).coerceAtLeast(0))
            ActionRow(AppText.get(R.string.record_payment), { onPayment(tenancy.id) }, R.drawable.ic_add)
            }
        }
    }
}

@Composable
private fun PaymentMonthSummary(state: BookUiState, month: YearMonth, filter: String,
    onMonth: (YearMonth) -> Unit, onFilter: (String) -> Unit, onRecord: () -> Unit, onAddTenant: () -> Unit) {
    val summary = BookMath.summary(state.book, month)
    MonthPicker(month, onMonth)
    SectionCard(AppText.get(R.string.this_month)) {
        MoneyRow(AppText.get(R.string.rent_cash_received), summary.collected, true)
        MoneyRow(AppText.get(R.string.expected_rent), summary.expected)
        MoneyRow(AppText.get(R.string.current_month_due), summary.currentDue)
        LinearProgressIndicator(progress = { if (summary.expected > 0) (summary.allocated.toDouble() / summary.expected).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
        Text(AppText.get(R.string.progress_reflects_rent_allocated_to_this_month), style = MaterialTheme.typography.bodySmall)
    }
    if (state.book.tenancies.isNotEmpty()) Button(onClick = onRecord, modifier = Modifier.fillMaxWidth()) { Text(AppText.get(R.string.record_payment)) }
    else EmptyState(AppText.get(R.string.start_your_rent_book), AppText.get(R.string.add_a_tenant_to_record_rent_and_keep_payment_history), AppText.get(R.string.choose_a_flat), onAddTenant)
    ChoiceEntry(AppText.get(R.string.status), listOf("All", "Paid", "Partial", "Due", "Overdue", "No charge"), filter, onFilter)
    if (state.book.tenancies.isNotEmpty()) SectionHeading(AppText.get(R.string.tenant_balances))
}

@Composable
private fun TenantPaymentBalance(state: BookUiState, id: String, collected: Long, amount: Long,
    overdue: Boolean, status: String, earlierOnly: Boolean, oldThrough: YearMonth, onPayment: (String) -> Unit) {
    val tenancy = state.book.tenancies.firstOrNull { it.id == id }
    SectionCard {
        Text("${tenancy?.let(state.book::flat)?.flatNumber.orEmpty()} · ${tenancy?.let(state.book::tenant)?.full_name.orEmpty()}", style = MaterialTheme.typography.titleMedium)
        StatusBadge(if (overdue) AppText.get(R.string.overdue) else status)
        MoneyRow(AppText.get(R.string.allocated_this_month), collected)
        MoneyRow(AppText.get(R.string.charge_this_month), amount)
        if (earlierOnly) MoneyRow(AppText.get(R.string.earlier_outstanding), BookMath.due(state.book, id, oldThrough), true)
        if (tenancy?.status != "active") Text(AppText.get(R.string.previous_tenant), style = MaterialTheme.typography.bodySmall)
        ActionRow(AppText.get(R.string.record_payment), { onPayment(id) }, R.drawable.ic_add)
    }
}
