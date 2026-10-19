package com.irfan.prosantinibash.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.irfan.prosantinibash.R
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.data.model.Flat
import com.irfan.prosantinibash.feature.book.BookUiState
import com.irfan.prosantinibash.feature.book.BookViewModel
import com.irfan.prosantinibash.feature.flats.flatStatus
import java.time.YearMonth

@Composable
fun HomeScreen(state: BookUiState, model: BookViewModel, onFlat: (String) -> Unit,
    onPayment: () -> Unit, onExpense: () -> Unit, onOutstanding: () -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now(BuildingDates.zone).toString()) }
    val month = YearMonth.parse(monthText)
    val totals = remember(state.book, month) { BookMath.summary(state.book, month) }
    val attention = remember(state.book, month) { state.book.tenancies.filter { BookMath.due(state.book, it.id, month) > 0 } }
    BookListPage(state, model::refresh) {
        item { MonthPicker(month) { monthText = it.toString() } }
        item { HomeSummary(totals) }
        item {
            Button(onClick = onPayment, modifier = Modifier.fillMaxWidth()) { Text(AppText.get(R.string.record_payment)) }
            OutlinedButton(onClick = onExpense, modifier = Modifier.fillMaxWidth()) { Text(AppText.get(R.string.add_expense)) }
        }
        if (attention.isNotEmpty()) {
            item { SectionHeading(AppText.get(R.string.needs_attention), AppText.get(R.string.view_all), onOutstanding) }
            attention.take(3).forEach { tenancy -> item(key = "attention-${tenancy.id}") {
                ActionRow("${state.book.flat(tenancy)?.flatNumber.orEmpty()} · ${state.book.tenant(tenancy)?.full_name.orEmpty()}",
                    { onFlat(tenancy.flat_id) }, R.drawable.ic_warning,
                    AppText.get(R.string.value_outstanding, Money.format(BookMath.due(state.book, tenancy.id, month))))
            } }
        }
        item { SectionHeading(AppText.get(R.string.your_building)) }
        state.book.flats.sortedWith(compareBy<Flat> { it.floor }.thenBy { it.flatNumber }).forEach { flat ->
            item(key = flat.id) {
                val tenancy = state.book.active(flat.id)
                ActionRow(AppText.get(R.string.flat_value, flat.flatNumber), { onFlat(flat.id) }, R.drawable.ic_flats,
                    if (tenancy == null) AppText.get(R.string.vacant) else "${state.book.tenant(tenancy)?.full_name.orEmpty()} · ${appLabel(flatStatus(state.book, flat, month))}")
            }
        }
    }
}

@Composable
private fun HomeSummary(totals: Summary) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard {
        Text(AppText.get(R.string.rent_cash_collected), style = MaterialTheme.typography.labelLarge)
        Text(Money.format(totals.collected), style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary)
        MoneyRow(AppText.get(R.string.outstanding_dues), Math.addExact(totals.currentDue, totals.oldDue), emphasis = true)
        MoneyRow(AppText.get(R.string.net_cash), totals.net, emphasis = true)
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) AppText.get(R.string.hide_breakdown) else AppText.get(R.string.show_breakdown))
        }
        if (expanded) {
            HorizontalDivider()
            MoneyRow(AppText.get(R.string.expected_rent), totals.expected)
            MoneyRow(AppText.get(R.string.current_month_due), totals.currentDue)
            MoneyRow(AppText.get(R.string.older_dues), totals.oldDue)
            MoneyRow(AppText.get(R.string.expenses), totals.expenses)
            Text(AppText.get(R.string.value_allocated_to_this_month_s_rent, Money.format(totals.allocated)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
