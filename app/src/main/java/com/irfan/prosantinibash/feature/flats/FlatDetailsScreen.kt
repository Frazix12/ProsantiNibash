package com.irfan.prosantinibash.feature.flats

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.irfan.prosantinibash.R
import com.irfan.prosantinibash.core.i18n.AppText
import com.irfan.prosantinibash.core.i18n.DisplayLocale
import com.irfan.prosantinibash.core.ui.*
import com.irfan.prosantinibash.core.util.*
import com.irfan.prosantinibash.data.model.Book
import com.irfan.prosantinibash.feature.book.BookUiState
import com.irfan.prosantinibash.feature.book.BookViewModel
import java.time.YearMonth

@Composable
fun FlatDetailsScreen(state: BookUiState, model: BookViewModel, flatId: String, onTenant: (String) -> Unit,
    onCreate: () -> Unit, onPayment: (String) -> Unit, onMaintenance: () -> Unit) {
    val flat = state.book.flats.firstOrNull { it.id == flatId }
    val active = state.book.active(flatId)
    BookPage(state, model::refresh) {
        if (flat == null) EmptyState(AppText.get(R.string.flat_unavailable), AppText.get(R.string.refresh_the_building_to_try_again)) else {
            SectionCard {
                Text(AppText.get(R.string.flat_value, flat.flatNumber), style = MaterialTheme.typography.headlineSmall)
                StatusBadge(flatStatus(state.book, flat, YearMonth.now(BuildingDates.zone)))
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
