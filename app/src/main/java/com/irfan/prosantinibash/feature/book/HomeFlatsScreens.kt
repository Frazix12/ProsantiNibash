package com.irfan.prosantinibash.feature.book

import com.irfan.prosantinibash.core.i18n.appLabel
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.data.model.*
import java.time.LocalDate
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
                    if (tenancy == null) AppText.get(R.string.vacant) else "${state.book.tenant(tenancy)?.full_name.orEmpty()} · ${appLabel(status(state.book, flat, month))}")
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

@Composable
fun BookFlatsScreen(state: BookUiState, model: BookViewModel, onFlat: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val month = YearMonth.now(BuildingDates.zone)
    val flats = state.book.flats.filter { flat ->
        val tenant = state.book.active(flat.id)?.let(state.book::tenant)
        "${flat.flatNumber} ${tenant?.full_name.orEmpty()} ${tenant?.phone.orEmpty()}".contains(query, true) &&
            (filter == "All" || status(state.book, flat, month) == filter)
    }
    BookListPage(state, model::refresh) {
        item { TextEntry(AppText.get(R.string.search_flat_tenant_or_phone), query) { query = it } }
        item { ChoiceEntry(AppText.get(R.string.status), listOf("All", "Paid", "Partial", "Due", "Overdue", "Vacant", "No charge"), filter) { filter = it } }
        if (flats.isEmpty()) item { EmptyState(AppText.get(R.string.no_matching_flats), AppText.get(R.string.try_another_name_flat_number_or_status)) }
        floorTiles(state.book, flats, month, onFlat)
    }
}

private fun LazyListScope.floorTiles(book: Book, flats: List<Flat>, month: YearMonth, onFlat: (String) -> Unit) {
    flats.groupBy { it.floor }.toSortedMap().forEach { (floor, rows) ->
        item(key = "floor-$floor") { SectionHeading(AppText.get(R.string.floor_value, floor)) }
        item(key = "floor-tiles-$floor") { FloorTiles(book, rows.sortedBy { it.flatNumber }, month, onFlat) }
    }
}

@Composable
private fun FloorTiles(book: Book, flats: List<Flat>, month: YearMonth, onFlat: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                flats.forEach { FlatTile(book, it, month, Modifier.fillMaxWidth()) { onFlat(it.id) } }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            flats.forEach { FlatTile(book, it, month, Modifier.weight(1f)) { onFlat(it.id) } }
            if (flats.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FlatTile(book: Book, flat: Flat, month: YearMonth, modifier: Modifier, onClick: () -> Unit) {
    val tenancy = book.active(flat.id)
    OutlinedCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(flat.flatNumber, style = MaterialTheme.typography.titleLarge)
            StatusBadge(status(book, flat, month))
            tenancy?.let {
                Text(book.tenant(it)?.full_name.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                val due = BookMath.due(book, it.id)
                if (due > 0) Text(AppText.get(R.string.value_outstanding, Money.format(due)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun status(book: Book, flat: Flat, month: YearMonth): String {
    val tenancy = book.active(flat.id) ?: return "Vacant"
    val periods = book.rent_periods.filter { it.tenancy_id == tenancy.id &&
        it.period_date.startsWith(month.toString()) && it.kind != "opening_due" }
    val charged = BookMath.sum(periods.map { it.amount_paisa })
    val paid = BookMath.sum(periods.map { BookMath.paid(book, it.id) })
    val oldDue = BookMath.due(book, tenancy.id, month.minusMonths(1))
    if (oldDue > 0 || (charged > paid && BuildingDates.isOverdue(month, LocalDate.now(BuildingDates.zone)))) return "Overdue"
    return if (charged == 0L) "No charge" else if (paid >= charged) "Paid" else if (paid > 0) "Partial" else "Due"
}

@Composable
fun FlatDetailsScreen(state: BookUiState, model: BookViewModel, flatId: String, onTenant: (String) -> Unit,
    onCreate: () -> Unit, onPayment: (String) -> Unit, onMaintenance: () -> Unit) {
    val flat = state.book.flats.firstOrNull { it.id == flatId }
    val active = state.book.active(flatId)
    BookPage(state, model::refresh) {
        if (flat == null) EmptyState(AppText.get(R.string.flat_unavailable), AppText.get(R.string.refresh_the_building_to_try_again)) else {
            SectionCard {
                Text(AppText.get(R.string.flat_value, flat.flatNumber), style = MaterialTheme.typography.headlineSmall)
                StatusBadge(status(state.book, flat, YearMonth.now(BuildingDates.zone)))
                if (active != null) {
                    Text(state.book.tenant(active)?.full_name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    MoneyRow(AppText.get(R.string.monthly_rent), active.monthly_rent_paisa)
                    MoneyRow(AppText.get(R.string.outstanding), BookMath.due(state.book, active.id), emphasis = true)
                    MoneyRow(AppText.get(R.string.credit), BookMath.credit(state.book, active.id))
                    MoneyRow(AppText.get(R.string.deposit), BookMath.deposit(state.book, active.id))
                } else Text(AppText.get(R.string.ready_for_a_new_tenant), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { if (active == null) onCreate() else onPayment(active.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (active == null) AppText.get(R.string.add_tenant) else AppText.get(R.string.record_payment))
            }
            active?.let {
                ActionRow(AppText.get(R.string.tenant_details_and_ledger), { onTenant(it.id) }, R.drawable.ic_person)
                state.book.payments.filter { payment -> payment.tenancy_id == it.id && !payment.voided }
                    .maxByOrNull { payment -> payment.payment_date }?.let { payment ->
                        SectionCard(AppText.get(R.string.last_payment)) { MoneyRow(DisplayLocale.date(payment.payment_date), payment.amount_paisa) }
                    }
            }
            ActionRow(AppText.get(R.string.maintenance_history), onMaintenance, R.drawable.ic_maintenance)
            PreviousTenants(state.book, flatId, onTenant)
        }
    }
}

@Composable
private fun PreviousTenants(book: Book, flatId: String, onTenant: (String) -> Unit) {
    val history = book.tenancies.filter { it.flat_id == flatId && it.status == "moved_out" }
    SectionCard(AppText.get(R.string.previous_tenants)) {
        if (history.isEmpty()) Text(AppText.get(R.string.no_previous_tenants), color = MaterialTheme.colorScheme.onSurfaceVariant)
        history.forEach { tenancy -> ActionRow(book.tenant(tenancy)?.full_name.orEmpty(), { onTenant(tenancy.id) },
            R.drawable.ic_person, "${DisplayLocale.date(tenancy.move_in_date)} – ${DisplayLocale.date(tenancy.move_out_date.orEmpty())}") }
    }
}

@Composable
fun SelectPaymentScreen(state: BookUiState, model: BookViewModel, onAddTenant: () -> Unit = {}, onPayment: (String) -> Unit) {
    BookListPage(state, model::refresh) {
        if (state.book.tenancies.isEmpty()) item { EmptyState(AppText.get(R.string.no_tenants_yet), AppText.get(R.string.add_a_tenant_before_recording_rent), AppText.get(R.string.add_tenant), onAddTenant) }
        state.book.tenancies.sortedBy { it.status != "active" }.forEach { tenancy -> item(key = tenancy.id) {
            ActionRow("${state.book.flat(tenancy)?.flatNumber.orEmpty()} · ${state.book.tenant(tenancy)?.full_name.orEmpty()}",
                { onPayment(tenancy.id) }, R.drawable.ic_person,
                if (tenancy.status == "active") AppText.get(R.string.current_tenant) else AppText.get(R.string.previous_tenant))
        } }
    }
}
